package insilico.janus.api;

import insilico.core.molecule.InsilicoMolecule;
import insilico.core.molecule.conversion.SmilesMolecule;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusResult;
import insilico.janus.JanusWorkflowResult;
import insilico.janus.gui.iJanusModelRunnerMessenger;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Alberto
 */
public class JanusJsonAPI implements iJanusJsonAPI {

    private JanusResult RunJanusEngine(String SMILES, short Workflow) 
            throws Exception {
        
        // Messenger for progress 
        iJanusModelRunnerMessenger Messenger = new iJanusModelRunnerMessenger() {
            @Override
            public void SendMessage(String msg) {
                System.out.println(msg);
            }
            @Override
            public void UpdateProgress() {} 
            @Override
            public void SetDatasetSize(int size) {}
            @Override
            public void SendLogMessage(String msg) {
                System.out.println(msg);
            }
        }; 
        
        JanusEngine engine = new JanusEngine(false);
        engine.SetMessenger(Messenger);
        engine.setMetabolismCalculation(false);

        HashMap<Short, Boolean> Workflows = new HashMap<>();
        Workflows.put(Workflow, true);        
        engine.Workflows = Workflows;

        ArrayList<InsilicoMolecule> DataSet = new ArrayList();
        DataSet.add(SmilesMolecule.Convert(SMILES));
        ArrayList<ExperimentalValues> DataSetExperimentalValues = new ArrayList();
        DataSetExperimentalValues.add(new ExperimentalValues(""));

        ArrayList<JanusResult> Results = engine.Calculate(DataSet, DataSetExperimentalValues);
        return Results.get(0);
    }
    
    
    private String FormatWorkflowOutput(String SMILES, JanusWorkflowResult w) {
        String res = "{\n";
        res += "\"SMILES\": \"" + SMILES + "\",\n";
        res += "\"Prediction\": \"" + w.Prediction + "\",\n";
        res += "\"PredictionLabel\": \"" + w.PredictionClass + "\",\n";
        res += "\"Reliability\": \"" + w.Reliability + "\"\n";
        res += "}";
        return res;        
    }
    
    
    @Override
    public String GetWorkflowCarcinogenicity(String SMILES) {        
        try {
            JanusWorkflowResult w = RunJanusEngine(SMILES, JanusEngine.WF_CARCINOGENICITY).C;
            return FormatWorkflowOutput(SMILES, w);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return "";
        }        
    }

    @Override
    public String GetWorkflowMutagenicity(String SMILES) {
        try {
            JanusWorkflowResult w = RunJanusEngine(SMILES, JanusEngine.WF_MUTAGENICITY).M;
            return FormatWorkflowOutput(SMILES, w);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return "";
        }        
    }

    @Override
    public String GetWorkflowReprotox(String SMILES) {
        try {
            JanusWorkflowResult w = RunJanusEngine(SMILES, JanusEngine.WF_REPROTOX).R;
            return FormatWorkflowOutput(SMILES, w);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return "";
        }        
    }
    
}
