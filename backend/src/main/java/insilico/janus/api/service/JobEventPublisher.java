package insilico.janus.api.service;

import insilico.janus.api.dto.JobDto;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes the progress of the jobs to the clients listening on
 * {@code GET /api/jobs/{id}/events}.
 *
 * <p>A client may connect at any moment, or not at all: the events are fire-and-forget and a
 * broken connection only removes the emitter.</p>
 */
@Component
public class JobEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobEventPublisher.class);

    /** No timeout: a job can legitimately run for a long time. */
    private static final long NO_TIMEOUT = 0L;

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Registers a new listener for a job.
     *
     * @param jobId the job to listen to
     * @return the emitter to return from the controller
     */
    public SseEmitter subscribe(String jobId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        List<SseEmitter> jobEmitters = emitters.computeIfAbsent(jobId, key -> new CopyOnWriteArrayList<>());
        jobEmitters.add(emitter);

        emitter.onCompletion(() -> jobEmitters.remove(emitter));
        emitter.onTimeout(() -> jobEmitters.remove(emitter));
        emitter.onError(e -> jobEmitters.remove(emitter));

        return emitter;
    }

    /**
     * Sends the current state of a job to its listeners.
     *
     * @param jobId the job
     * @param event the name of the event ({@code progress}, {@code log}, {@code status})
     * @param state the state to send
     */
    public void publish(String jobId, String event, JobDto state) {
        List<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters == null) {
            return;
        }
        for (SseEmitter emitter : jobEmitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(state));
            } catch (IOException | IllegalStateException e) {
                // the client is gone: drop the emitter and keep the calculation going
                LOGGER.debug("Dropping a disconnected listener of job {}", jobId);
                jobEmitters.remove(emitter);
            }
        }
    }

    /**
     * Sends a last event and closes the connections of a finished job.
     *
     * @param jobId the job
     * @param state the final state
     */
    public void complete(String jobId, JobDto state) {
        publish(jobId, "status", state);
        List<SseEmitter> jobEmitters = emitters.remove(jobId);
        if (jobEmitters == null) {
            return;
        }
        for (SseEmitter emitter : jobEmitters) {
            try {
                emitter.complete();
            } catch (RuntimeException e) {
                LOGGER.debug("Unable to close a listener of job {}", jobId);
            }
        }
    }
}
