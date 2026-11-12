package insilico.janus.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The state of the calculation engine, returned by {@code GET /api/health}. Loading the models
 * takes a while at startup, so a client should wait for {@code ready} before submitting jobs.
 *
 * @param ready whether the engine finished loading the models
 * @param error the reason why the engine could not be initialized, if it failed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthDto(boolean ready, String error) {
}
