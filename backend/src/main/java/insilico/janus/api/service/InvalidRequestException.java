package insilico.janus.api.service;

import java.util.List;

/**
 * Thrown when a request cannot be turned into a calculation, for instance because none of its
 * molecules could be read. It is reported as {@code 400 Bad Request}.
 */
public class InvalidRequestException extends RuntimeException {

    private final transient List<String> details;

    /**
     * @param message a short description of the problem
     * @param details the specific issues, one per offending item
     */
    public InvalidRequestException(String message, List<String> details) {
        super(message);
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    /**
     * @param message a short description of the problem
     */
    public InvalidRequestException(String message) {
        this(message, List.of());
    }

    /**
     * @return the specific issues
     */
    public List<String> getDetails() {
        return details;
    }
}
