package insilico.janus.api.dto;

/**
 * The progress of a running job.
 *
 * <p>The engine runs the workflows one after the other over the whole dataset, so the progress
 * is reported per phase: {@code processedMolecules} restarts from zero on every phase
 * change.</p>
 *
 * @param phase              the phase being executed, e.g. {@code "PERSISTENCE workflow"}
 * @param processedMolecules the molecules processed so far within this phase
 * @param totalMolecules     the molecules this phase will process
 */
public record JobProgressDto(String phase, int processedMolecules, int totalMolecules) {
}
