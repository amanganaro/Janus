package insilico.janus.api.dto;

/**
 * The lifecycle of a job. Since the engine is not thread-safe, only one job runs at a time
 * and the others wait in the QUEUED state.
 */
public enum JobStatus {
    /** Accepted, waiting for the engine to be free. */
    QUEUED,
    /** Currently being calculated. */
    RUNNING,
    /** Finished, results available. */
    COMPLETED,
    /** Stopped by an error; see the error message of the job. */
    FAILED,
    /** Cancelled by the client before it completed. */
    CANCELLED
}
