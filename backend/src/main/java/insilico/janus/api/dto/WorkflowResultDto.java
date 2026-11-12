package insilico.janus.api.dto;

import java.util.List;

/**
 * The outcome of a single workflow for one molecule.
 *
 * <p>All numeric fields are {@code null} when the workflow could not produce a value: the
 * {@code -999999} sentinel used internally never reaches the API.</p>
 *
 * @param predictionClass  the qualitative assessment, or {@code "-"} when not available
 * @param prediction       the quantitative assessment, in the unit of the property
 * @param reliability      the reliability of the assessment, in [0, 1]
 * @param score            the normalized hazard score, in [0, 1]
 * @param additionalValues the intermediate values, in the order the workflow produced them
 */
public record WorkflowResultDto(
        String predictionClass,
        Double prediction,
        Double reliability,
        Double score,
        List<AdditionalValueDto> additionalValues) {
}
