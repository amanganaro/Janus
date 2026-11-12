package insilico.janus.api.controller;

import insilico.janus.api.dto.JanusJobRequest;
import insilico.janus.api.dto.JobDto;
import insilico.janus.api.service.JanusJob;
import insilico.janus.api.service.JanusJobService;
import insilico.janus.api.service.JobEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import insilico.janus.api.dto.ErrorDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The calculation endpoints.
 *
 * <p>A calculation is always a job: {@code POST} returns immediately with an identifier, the
 * client then polls {@code GET /api/jobs/{id}} or follows the progress on
 * {@code GET /api/jobs/{id}/events}. Assessing a single SMILES is just a job with one
 * molecule.</p>
 */
@RestController
@RequestMapping(path = "/api/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Jobs", description = "Submitting calculations and collecting their results")
public class JanusJobController {

    private final JanusJobService jobService;
    private final JobEventPublisher publisher;

    /**
     * @param jobService the job service
     * @param publisher  the publisher of the progress events
     */
    public JanusJobController(JanusJobService jobService, JobEventPublisher publisher) {
        this.jobService = jobService;
        this.publisher = publisher;
    }

    /**
     * Submits a new calculation.
     *
     * @param request the molecules to assess and the options
     * @return {@code 202} with the state of the newly created job
     */
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "The job was queued"),
        @ApiResponse(responseCode = "400", description = "No molecule of the request could be read",
                content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @Operation(operationId = "submitJob")
    @PostMapping
    public ResponseEntity<JobDto> submit(@Valid @RequestBody JanusJobRequest request) {
        JanusJob job = jobService.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job.toDto(false));
    }

    /**
     * Returns the state of a job, including its results once it has completed.
     *
     * @param id the identifier of the job
     * @return the state of the job
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The state of the job"),
        @ApiResponse(responseCode = "404", description = "Unknown job",
                content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @Operation(operationId = "getJob")
    @GetMapping("/{id}")
    public JobDto get(@PathVariable String id) {
        return jobService.get(id).toDto(true);
    }

    /**
     * Returns every job of this session, without their results.
     *
     * @return the jobs, most recent first
     */
    @Operation(operationId = "listJobs")
    @GetMapping
    public List<JobDto> list() {
        return jobService.list().stream().map(job -> job.toDto(false)).toList();
    }

    /**
     * Streams the progress of a job as server-sent events.
     *
     * <p>A running calculation cannot actually be stopped, so the events of a cancelled job
     * simply end.</p>
     *
     * @param id the identifier of the job
     * @return the stream of events
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "The stream of progress events, each carrying the state of the job",
                content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = @Schema(implementation = JobDto.class))),
        @ApiResponse(responseCode = "404", description = "Unknown job",
                content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @Operation(operationId = "jobEvents")
    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id) {
        JanusJob job = jobService.get(id);
        SseEmitter emitter = publisher.subscribe(id);

        switch (job.getStatus()) {
            case COMPLETED, FAILED, CANCELLED -> publisher.complete(id, job.toDto(false));
            default -> publisher.publish(id, "status", job.toDto(false));
        }
        return emitter;
    }

    /**
     * Cancels a job and forgets it.
     *
     * @param id the identifier of the job
     * @return {@code 204}
     */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The job was cancelled and forgotten"),
        @ApiResponse(responseCode = "404", description = "Unknown job",
                content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @Operation(operationId = "cancelJob")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable String id) {
        jobService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
