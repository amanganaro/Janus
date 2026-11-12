package insilico.janus.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * One input molecule of a job.
 *
 * @param id                 an optional identifier; when absent the engine assigns {@code "Mol n"}
 * @param smiles             the SMILES of the molecule, mandatory
 * @param experimentalValues optional experimental values, keyed by the identifiers returned by
 *                           {@code GET /api/metadata} ({@code bcf}, {@code logp}, {@code p_soil},
 *                           {@code p_water}, {@code p_sediment}, {@code tox}, {@code carc},
 *                           {@code muta}, {@code repro}, {@code ed}). When a workflow finds an
 *                           experimental value for its property, it uses it instead of the
 *                           prediction and reports full reliability.
 */
public record MoleculeRequestDto(
        String id,
        @NotBlank(message = "smiles must not be empty") String smiles,
        Map<String, Double> experimentalValues) {
}
