package insilico.janus.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The body returned for a request that could not be accepted.
 *
 * @param error   a short description of the problem
 * @param details the specific issues, e.g. one entry per invalid molecule
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDto(String error, List<String> details) {

    /**
     * @param error a short description of the problem
     */
    public ErrorDto(String error) {
        this(error, null);
    }
}
