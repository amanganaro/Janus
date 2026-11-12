package insilico.janus.api.controller;

import insilico.janus.ExperimentalValues;
import insilico.janus.api.config.JanusEngineHolder;
import insilico.janus.api.dto.HealthDto;
import insilico.janus.api.dto.JanusWorkflowType;
import insilico.janus.api.dto.MetadataDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The endpoints a client uses to discover what the service offers and whether it is ready.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Service", description = "What the service offers and whether it is ready")
public class JanusMetadataController {

    private static final List<MetadataDto.WorkflowInfo> WORKFLOWS = List.of(
            new MetadataDto.WorkflowInfo(JanusWorkflowType.PERSISTENCE, "Persistence"),
            new MetadataDto.WorkflowInfo(JanusWorkflowType.BIOACCUMULATION, "Bioaccumulation"),
            new MetadataDto.WorkflowInfo(JanusWorkflowType.TOXICITY, "Aquatic toxicity"),
            new MetadataDto.WorkflowInfo(JanusWorkflowType.CARCINOGENICITY, "Carcinogenicity"),
            new MetadataDto.WorkflowInfo(JanusWorkflowType.MUTAGENICITY, "Mutagenicity"),
            new MetadataDto.WorkflowInfo(JanusWorkflowType.REPRO_TOXICITY,
                    "Reproductive and developmental toxicity"),
            new MetadataDto.WorkflowInfo(JanusWorkflowType.ENDOCRINE, "Endocrine disruption"));

    private final JanusEngineHolder engineHolder;

    /**
     * @param engineHolder the holder of the calculation engine
     */
    public JanusMetadataController(JanusEngineHolder engineHolder) {
        this.engineHolder = engineHolder;
    }

    /**
     * Describes the workflows and the experimental values the service understands.
     *
     * @return the metadata of the service
     */
    @GetMapping("/metadata")
    public MetadataDto metadata() {
        List<MetadataDto.ExperimentalKeyInfo> keys = new ArrayList<>();
        for (String[] key : ExperimentalValues.EXP_KEYS) {
            keys.add(new MetadataDto.ExperimentalKeyInfo(key[0], key[1]));
        }
        return new MetadataDto(WORKFLOWS, keys);
    }

    /**
     * Tells whether the models are loaded. Jobs submitted before that are accepted but wait.
     *
     * @return the state of the engine
     */
    @GetMapping("/health")
    public HealthDto health() {
        return new HealthDto(engineHolder.isReady(), engineHolder.getError());
    }
}
