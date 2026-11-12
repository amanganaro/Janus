package insilico.janus.api.dto;

import insilico.janus.JanusEngine;

/**
 * The seven assessment workflows of Janus, mapping the public API names to the numeric
 * keys used internally by {@link JanusEngine}.
 */
public enum JanusWorkflowType {

    /** Persistence (P). */
    PERSISTENCE(JanusEngine.WF_PERSISTENCE),
    /** Bioaccumulation (B). */
    BIOACCUMULATION(JanusEngine.WF_BCF),
    /** Aquatic toxicity (T). */
    TOXICITY(JanusEngine.WF_TOXICITY),
    /** Carcinogenicity (C). */
    CARCINOGENICITY(JanusEngine.WF_CARCINOGENICITY),
    /** Mutagenicity (M). */
    MUTAGENICITY(JanusEngine.WF_MUTAGENICITY),
    /** Reproductive and developmental toxicity (R). */
    REPRO_TOXICITY(JanusEngine.WF_REPROTOX),
    /** Endocrine disruption (ED). */
    ENDOCRINE(JanusEngine.WF_ED);

    private final short engineKey;

    JanusWorkflowType(short engineKey) {
        this.engineKey = engineKey;
    }

    /**
     * @return the key of this workflow in {@link JanusEngine#Workflows}
     */
    public short engineKey() {
        return engineKey;
    }
}
