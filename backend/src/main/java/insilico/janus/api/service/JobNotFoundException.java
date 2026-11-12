package insilico.janus.api.service;

/**
 * Thrown when a job id does not match any job of this session. It is reported as
 * {@code 404 Not Found}.
 */
public class JobNotFoundException extends RuntimeException {

    /**
     * @param jobId the unknown identifier
     */
    public JobNotFoundException(String jobId) {
        super("No job with id " + jobId);
    }
}
