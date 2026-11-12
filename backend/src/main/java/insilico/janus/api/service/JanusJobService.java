package insilico.janus.api.service;

import insilico.core.molecule.InsilicoMolecule;
import insilico.core.molecule.conversion.SmilesMolecule;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusResult;
import insilico.janus.api.config.JanusEngineHolder;
import insilico.janus.api.dto.JanusJobRequest;
import insilico.janus.api.dto.JanusWorkflowType;
import insilico.janus.api.dto.JobStatus;
import insilico.janus.api.dto.MoleculeRequestDto;
import insilico.janus.api.mapper.JanusResultMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Accepts, runs and keeps track of the calculation jobs.
 *
 * <p>Jobs run on a <strong>single worker thread</strong>, because {@link JanusEngine} keeps the
 * state of the current calculation in shared mutable fields; a second concurrent job would
 * corrupt both. Submitting while another job runs is allowed and simply leaves the new job in
 * {@link JobStatus#QUEUED}.</p>
 *
 * <p>Jobs live in memory only: the service is meant to run locally, inside the desktop
 * application, for a single user.</p>
 */
@Service
public class JanusJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JanusJobService.class);

    /** How long a finished job is kept before being discarded. */
    private static final Duration RETENTION = Duration.ofHours(1);

    private final JanusEngineHolder engineHolder;
    private final JanusResultMapper mapper;
    private final JobEventPublisher publisher;

    private final Map<String, JanusJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "janus-worker");
        thread.setDaemon(true);
        return thread;
    });

    /** Guards the SMILES parsing, which is done outside the worker thread. */
    private final Object conversionLock = new Object();

    /**
     * @param engineHolder the holder of the calculation engine
     * @param mapper       the DTO mapper
     * @param publisher    the publisher of the progress events
     */
    public JanusJobService(JanusEngineHolder engineHolder, JanusResultMapper mapper,
            JobEventPublisher publisher) {
        this.engineHolder = engineHolder;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    /**
     * Validates a request and queues it for calculation.
     *
     * @param request the request
     * @return the newly created job
     * @throws InvalidRequestException if no molecule of the request could be read
     */
    public JanusJob submit(JanusJobRequest request) {

        PreparedDataset dataset = prepare(request);

        JanusJob job = new JanusJob(UUID.randomUUID().toString(), request);
        jobs.put(job.getId(), job);
        dataset.warnings().forEach(job::addLogMessage);

        job.setTask(worker.submit(() -> run(job, dataset)));
        return job;
    }

    /**
     * @param jobId the identifier of the job
     * @return the job
     * @throws JobNotFoundException if no such job exists
     */
    public JanusJob get(String jobId) {
        JanusJob job = jobs.get(jobId);
        if (job == null) {
            throw new JobNotFoundException(jobId);
        }
        return job;
    }

    /**
     * @return every job of this session, most recent first
     */
    public List<JanusJob> list() {
        List<JanusJob> all = new ArrayList<>(jobs.values());
        all.sort(Comparator.comparing((JanusJob job) -> job.toDto(false).createdAt()).reversed());
        return all;
    }

    /**
     * Cancels a job and forgets it.
     *
     * @param jobId the identifier of the job
     * @throws JobNotFoundException if no such job exists
     */
    public void cancel(String jobId) {
        JanusJob job = get(jobId);
        job.cancel();
        publisher.complete(job.getId(), job.toDto(false));
        jobs.remove(jobId);
    }

    /**
     * Converts the input molecules and rejects the request if none of them can be read.
     *
     * @param request the request
     * @return the dataset to hand over to the engine
     */
    private PreparedDataset prepare(JanusJobRequest request) {

        List<InsilicoMolecule> molecules = new ArrayList<>();
        List<ExperimentalValues> experimentalValues = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int index = 0;
        for (MoleculeRequestDto input : request.molecules()) {
            index++;

            InsilicoMolecule molecule;
            synchronized (conversionLock) {
                molecule = SmilesMolecule.Convert(input.smiles());
            }

            if (!molecule.IsValid()) {
                String problem = "Molecule " + index + " (" + input.smiles() + "): "
                        + molecule.GetErrors().GetMessages();
                problems.add(problem);
                // the engine silently skips invalid molecules, so warn about it in the job log
                warnings.add("Skipped - " + problem);
                continue;
            }

            String id = input.id() == null || input.id().isBlank()
                    ? "Mol " + (molecules.size() + 1)
                    : input.id();
            molecule.SetId(id);

            try {
                experimentalValues.add(mapper.toExperimentalValues(input));
            } catch (Exception e) {
                throw new InvalidRequestException(
                        "Unable to read the experimental values of molecule " + index,
                        List.of(String.valueOf(e.getMessage())));
            }
            molecules.add(molecule);
        }

        if (molecules.isEmpty()) {
            throw new InvalidRequestException("No valid molecule in the request", problems);
        }

        return new PreparedDataset(molecules, experimentalValues, warnings);
    }

    /**
     * Runs one job on the worker thread.
     *
     * @param job     the job
     * @param dataset the molecules to assess
     */
    private void run(JanusJob job, PreparedDataset dataset) {

        if (job.getStatus() == JobStatus.CANCELLED) {
            return;
        }

        job.markRunning();
        publisher.publish(job.getId(), "status", job.toDto(false));

        try {
            JanusEngine engine = engineHolder.awaitEngine();

            // the engine is shared: every setting has to be applied again for each job
            for (JanusWorkflowType workflow : JanusWorkflowType.values()) {
                engine.Workflows.put(workflow.engineKey(),
                        job.getRequest().requestedWorkflows().contains(workflow));
            }
            engine.setMetabolismCalculation(job.getRequest().metabolismEnabled());
            engine.SetMessenger(new JobProgressTracker(job, publisher));

            ArrayList<JanusResult> results = engine.Calculate(
                    new ArrayList<>(dataset.molecules()),
                    new ArrayList<>(dataset.experimentalValues()));

            if (job.getStatus() == JobStatus.CANCELLED) {
                LOGGER.info("Job {} was cancelled, discarding its results", job.getId());
                return;
            }

            job.markCompleted(mapper.toDto(results));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.markFailed("The calculation was interrupted");
        } catch (Throwable e) {
            LOGGER.error("Job {} failed", job.getId(), e);
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            job.markFailed(message);
        } finally {
            publisher.complete(job.getId(), job.toDto(false));
        }
    }

    /**
     * Discards the jobs that finished long ago, so that a long-lived process does not keep
     * every result it ever produced.
     */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    void purgeOldJobs() {
        Instant threshold = Instant.now().minus(RETENTION);
        jobs.values().removeIf(job -> job.getFinishedAt() != null
                && job.getFinishedAt().isBefore(threshold));
    }

    /**
     * Stops accepting work when the application shuts down.
     */
    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    /**
     * The input of a calculation, already converted.
     *
     * @param molecules          the valid molecules
     * @param experimentalValues their experimental values, in the same order
     * @param warnings           the messages about the molecules that were dropped
     */
    private record PreparedDataset(List<InsilicoMolecule> molecules,
            List<ExperimentalValues> experimentalValues, List<String> warnings) {
    }
}
