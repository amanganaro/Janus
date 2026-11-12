package insilico.janus.api.dto;

/**
 * One of the intermediate values a workflow exposes besides its own prediction (single model
 * predictions, experimental values, applied rules).
 *
 * @param name  the label of the value, as produced by the workflow
 * @param value the value, always rendered as text by the workflow itself
 */
public record AdditionalValueDto(String name, String value) {
}
