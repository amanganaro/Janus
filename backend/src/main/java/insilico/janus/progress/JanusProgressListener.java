package insilico.janus.progress;

import insilico.core.model.runner.iInsilicoModelRunnerMessenger;

/**
 * Callback used by {@link insilico.janus.JanusEngine} to report the progress of a
 * calculation. It replaces the Swing-oriented {@code iJanusModelRunnerMessenger} of the
 * legacy application, keeping exactly the same method set so that the engine code is
 * unchanged.
 *
 * <p>Beware of the semantics of the callbacks, which are dictated by the engine:
 * {@link #SetDatasetSize(int)} is called more than once (before and after the metabolism
 * step) and {@link #UpdateProgress()} is called once per molecule <em>for every
 * workflow</em>, so the progress is only meaningful within the phase announced by the last
 * {@link #SendMessage(String)}.</p>
 */
public interface JanusProgressListener extends iInsilicoModelRunnerMessenger {

    /**
     * Declares the number of molecules that the current phase will iterate over.
     *
     * @param size the number of molecules
     */
    void SetDatasetSize(int size);

    /**
     * Reports a diagnostic message (invalid molecules, workflow failures).
     *
     * @param msg the message
     */
    void SendLogMessage(String msg);
}
