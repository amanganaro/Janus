package insilico.janus.api.service;

import insilico.janus.api.dto.JanusJobRequest;
import insilico.janus.api.dto.JobDto;
import insilico.janus.api.dto.JobProgressDto;
import insilico.janus.api.dto.JobStatus;
import insilico.janus.api.dto.MoleculeResultDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * The mutable state of one submitted calculation.
 *
 * <p>It is written by the worker thread and read by the HTTP threads, so every field is either
 * volatile or guarded by the instance lock.</p>
 */
public class JanusJob {

    private final String id;
    private final JanusJobRequest request;
    private final Instant createdAt = Instant.now();
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());

    private volatile JobStatus status = JobStatus.QUEUED;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile JobProgressDto progress;
    private volatile List<MoleculeResultDto> results;
    private volatile String error;
    private volatile Future<?> task;

    /**
     * @param id      the identifier of the job
     * @param request the request that created it
     */
    public JanusJob(String id, JanusJobRequest request) {
        this.id = id;
        this.request = request;
    }

    /**
     * @return the identifier of the job
     */
    public String getId() {
        return id;
    }

    /**
     * @return the request that created the job
     */
    public JanusJobRequest getRequest() {
        return request;
    }

    /**
     * @return the current status
     */
    public JobStatus getStatus() {
        return status;
    }

    /**
     * @return when the job finished, {@code null} if it is not finished yet
     */
    public Instant getFinishedAt() {
        return finishedAt;
    }

    /**
     * @return the current progress, {@code null} before the calculation starts
     */
    public JobProgressDto getProgress() {
        return progress;
    }

    /**
     * Records the handle of the running task, so that the job can be cancelled.
     *
     * @param task the submitted task
     */
    public void setTask(Future<?> task) {
        this.task = task;
    }

    /**
     * Marks the job as started.
     */
    public void markRunning() {
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * Marks the job as completed and stores its results.
     *
     * @param results the results of the calculation
     */
    public void markCompleted(List<MoleculeResultDto> results) {
        this.results = results;
        this.status = JobStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    /**
     * Marks the job as failed.
     *
     * @param error the reason of the failure
     */
    public void markFailed(String error) {
        this.error = error;
        this.status = JobStatus.FAILED;
        this.finishedAt = Instant.now();
    }

    /**
     * Marks the job as cancelled.
     */
    public void markCancelled() {
        this.status = JobStatus.CANCELLED;
        this.finishedAt = Instant.now();
    }

    /**
     * Cancels the job. A job that is already running cannot be interrupted: the engine does not
     * check for interruption, so it keeps going until the end and its results are discarded.
     *
     * @return whether the job was still active
     */
    public boolean cancel() {
        if (status == JobStatus.COMPLETED || status == JobStatus.FAILED
                || status == JobStatus.CANCELLED) {
            return false;
        }
        if (task != null) {
            task.cancel(false);
        }
        markCancelled();
        return true;
    }

    /**
     * Updates the progress of the calculation.
     *
     * @param progress the new progress
     */
    public void setProgress(JobProgressDto progress) {
        this.progress = progress;
    }

    /**
     * Appends a diagnostic message.
     *
     * @param message the message
     */
    public void addLogMessage(String message) {
        log.add(message);
    }

    /**
     * Builds the API representation of this job.
     *
     * @param includeResults whether to include the results and the log, which can be large
     * @return the DTO of the job
     */
    public JobDto toDto(boolean includeResults) {
        List<String> logCopy;
        synchronized (log) {
            logCopy = List.copyOf(log);
        }
        return new JobDto(
                id,
                status,
                progress,
                createdAt,
                startedAt,
                finishedAt,
                includeResults ? results : null,
                includeResults ? logCopy : null,
                error);
    }
}
