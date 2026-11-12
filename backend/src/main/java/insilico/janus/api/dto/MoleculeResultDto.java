package insilico.janus.api.dto;

import java.util.List;

/**
 * The complete Janus assessment of one molecule: the seven workflow results plus the three
 * aggregated regulatory indices.
 *
 * <p>When the metabolism calculation is enabled, the results of a job also contain the
 * metabolites of the input molecules; those are linked to their parent through
 * {@code parentNum} and {@code metabolites}, which refer to the {@code num} field.</p>
 *
 * @param num             the 1-based position of this result within the job
 * @param id              the identifier of the molecule
 * @param smiles          the SMILES of the molecule, as read by the engine
 * @param valid           whether the molecule could be processed
 * @param metabolite      whether this result describes a metabolite of another molecule
 * @param parentNum       the {@code num} of the parent molecule, {@code null} if not a metabolite
 * @param metabolites     the {@code num} of the metabolites generated from this molecule
 * @param notes           remarks produced during the calculation
 * @param persistence     persistence (P) result
 * @param bioaccumulation bioaccumulation (B) result
 * @param toxicity        aquatic toxicity (T) result
 * @param carcinogenicity carcinogenicity (C) result
 * @param mutagenicity    mutagenicity (M) result
 * @param reproToxicity   reproductive and developmental toxicity (R) result
 * @param endocrine       endocrine disruption (ED) result
 * @param scoreVPvB       the vPvB index, {@code null} when it could not be calculated
 * @param scorePBT        the PBT index, {@code null} when it could not be calculated
 * @param scoreSVHC       the SVHC index, {@code null} when it could not be calculated
 */
public record MoleculeResultDto(
        int num,
        String id,
        String smiles,
        boolean valid,
        boolean metabolite,
        Integer parentNum,
        List<Integer> metabolites,
        String notes,
        WorkflowResultDto persistence,
        WorkflowResultDto bioaccumulation,
        WorkflowResultDto toxicity,
        WorkflowResultDto carcinogenicity,
        WorkflowResultDto mutagenicity,
        WorkflowResultDto reproToxicity,
        WorkflowResultDto endocrine,
        Double scoreVPvB,
        Double scorePBT,
        Double scoreSVHC) {
}
