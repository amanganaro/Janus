package insilico.janus.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * The request body of {@code POST /api/jobs}.
 *
 * @param molecules the molecules to assess; at least one
 * @param metabolism whether to generate and assess the metabolites of the input molecules.
 *                   Defaults to {@code false}: the metabolism module is currently broken, see
 *                   the README.
 * @param workflows  the workflows to run; when {@code null} or empty, all of them are run
 */
public record JanusJobRequest(
        @NotEmpty(message = "at least one molecule is required")
        @Valid List<MoleculeRequestDto> molecules,
        Boolean metabolism,
        List<JanusWorkflowType> workflows) {

    /**
     * @return whether the metabolism calculation was requested, {@code false} by default
     */
    public boolean metabolismEnabled() {
        return Boolean.TRUE.equals(metabolism);
    }

    /**
     * @return the requested workflows, or all of them when none was specified
     */
    public List<JanusWorkflowType> requestedWorkflows() {
        if (workflows == null || workflows.isEmpty()) {
            return List.of(JanusWorkflowType.values());
        }
        return workflows;
    }
}
