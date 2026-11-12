package insilico.janus.api.dto;

import java.util.List;

/**
 * The static description of what the service can calculate, returned by
 * {@code GET /api/metadata} so that a client does not have to hardcode it.
 *
 * @param workflows         the available workflows
 * @param experimentalKeys  the accepted keys for the experimental values of a molecule
 */
public record MetadataDto(List<WorkflowInfo> workflows, List<ExperimentalKeyInfo> experimentalKeys) {

    /**
     * @param id    the value to use in {@link JanusJobRequest#workflows()}
     * @param label the human readable name of the workflow
     */
    public record WorkflowInfo(JanusWorkflowType id, String label) {
    }

    /**
     * @param key         the key to use in {@link MoleculeRequestDto#experimentalValues()}
     * @param description what the value is, including its unit
     */
    public record ExperimentalKeyInfo(String key, String description) {
    }
}
