package insilico.janus.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The state of a job, as returned by {@code POST /api/jobs} and {@code GET /api/jobs/{id}}.
 * Results and log are only populated once the job has finished.
 *
 * @param jobId      the identifier of the job
 * @param status     the current status
 * @param progress   the current progress, {@code null} before the calculation starts
 * @param createdAt  when the job was accepted
 * @param startedAt  when the calculation started, {@code null} while queued
 * @param finishedAt when the calculation ended, {@code null} while queued or running
 * @param results    the assessment of every molecule, {@code null} until the job completes
 * @param log        the diagnostic messages produced during the calculation
 * @param error      the reason of the failure, only set when the status is {@code FAILED}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobDto(
        String jobId,
        JobStatus status,
        JobProgressDto progress,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        List<MoleculeResultDto> results,
        List<String> log,
        String error) {
}
