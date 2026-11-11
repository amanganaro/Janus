package insilico.janus.gui;

import insilico.core.model.runner.iInsilicoModelRunnerMessenger;

/**
 *
 * @author Alberto
 */
public interface iJanusModelRunnerMessenger extends iInsilicoModelRunnerMessenger {
    public void SetDatasetSize(int size);
    public void SendLogMessage(String msg);
}
