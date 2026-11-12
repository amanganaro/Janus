package insilico.janus.api.mapper;

import insilico.janus.ExperimentalValues;
import insilico.janus.JanusResult;
import insilico.janus.JanusWorkflowResult;
import insilico.janus.api.dto.AdditionalValueDto;
import insilico.janus.api.dto.MoleculeRequestDto;
import insilico.janus.api.dto.MoleculeResultDto;
import insilico.janus.api.dto.WorkflowResultDto;
import insilico.core.exception.GenericFailureException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Translates between the API DTOs and the legacy domain objects.
 *
 * <p>Its main job is to hide the conventions of the legacy code: the {@code -999999} sentinel
 * for missing numbers, the {@code -1} sentinel for the parent of a molecule that is not a
 * metabolite, and the tab separated string the experimental values are parsed from.</p>
 */
@Component
public class JanusResultMapper {

    /**
     * Builds the experimental values of a molecule.
     *
     * <p>{@link ExperimentalValues} only knows how to parse a line of the legacy input file,
     * that is {@code key:value} pairs separated by tabs, so the map is rendered back into that
     * format. Unknown keys are ignored by the parser.</p>
     *
     * @param molecule the input molecule
     * @return the experimental values, empty when the molecule carries none
     * @throws GenericFailureException if a value cannot be parsed
     */
    public ExperimentalValues toExperimentalValues(MoleculeRequestDto molecule)
            throws GenericFailureException {

        Map<String, Double> values = molecule.experimentalValues();
        if (values == null || values.isEmpty()) {
            return new ExperimentalValues("");
        }

        StringBuilder line = new StringBuilder();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (line.length() > 0) {
                line.append('\t');
            }
            line.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return new ExperimentalValues(line.toString());
    }

    /**
     * Converts the results of a calculation.
     *
     * @param results the results produced by the engine
     * @return the API representation of the results
     */
    public List<MoleculeResultDto> toDto(List<JanusResult> results) {
        List<MoleculeResultDto> dtos = new ArrayList<>(results.size());
        for (JanusResult result : results) {
            dtos.add(toDto(result));
        }
        return dtos;
    }

    /**
     * Converts the result of a single molecule.
     *
     * @param result the result produced by the engine
     * @return the API representation of the result
     */
    public MoleculeResultDto toDto(JanusResult result) {
        return new MoleculeResultDto(
                result.Num,
                result.Id,
                result.SMILES,
                result.Valid,
                result.isMetabolite,
                result.ParentNum == -1 ? null : result.ParentNum,
                List.copyOf(result.Metabolites),
                result.Notes,
                toDto(result.P),
                toDto(result.B),
                toDto(result.T),
                toDto(result.C),
                toDto(result.M),
                toDto(result.R),
                toDto(result.ED),
                value(result.S_vPvB),
                value(result.S_PBT),
                value(result.S_SVHC));
    }

    private WorkflowResultDto toDto(JanusWorkflowResult result) {
        List<AdditionalValueDto> additional = new ArrayList<>(result.AdditionalValues.size());
        // the map is a LinkedHashMap: the insertion order is meaningful and is preserved here
        result.AdditionalValues.forEach((name, value) -> additional.add(new AdditionalValueDto(name, value)));

        return new WorkflowResultDto(
                result.PredictionClass,
                value(result.Prediction),
                value(result.Reliability),
                value(result.Score),
                additional);
    }

    /**
     * Maps the legacy sentinel for a missing number to {@code null}.
     *
     * @param value the value produced by a workflow
     * @return the value, or {@code null} when it is missing
     */
    private Double value(Double value) {
        if (value == null || value == JanusResult.MISSING_VALUE) {
            return null;
        }
        return value;
    }
}
