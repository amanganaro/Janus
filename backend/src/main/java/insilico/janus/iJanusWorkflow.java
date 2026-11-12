package insilico.janus;

import insilico.core.exception.GenericFailureException;
import insilico.core.molecule.InsilicoMolecule;
import insilico.janus.workflows.tools.POPClass;

/**
 *
 * @author Alberto
 */
public interface iJanusWorkflow {
    
    public JanusWorkflowResult Calculate(InsilicoMolecule mol) throws GenericFailureException;
    public void SetExperimentalValue(ExperimentalValues ExpVal);
    public void SetPOPClass(POPClass POP);
    
}
