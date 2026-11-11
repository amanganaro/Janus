package insilico.janus;

import java.util.ArrayList;

/**
 *
 * @author Alberto
 */
public class JanusResult {
    
    public final static int MISSING_VALUE = -999999;
    
    public boolean Valid;
    
    public int Num;
    public String Id;
    public String SMILES;
    
    public boolean isMetabolite;
    public int ParentNum;
    
    public ArrayList<Integer> Metabolites;
    
    public String Notes;
    
    public JanusWorkflowResult P;
    public JanusWorkflowResult B;
    public JanusWorkflowResult T;
    
    public JanusWorkflowResult C;
    public JanusWorkflowResult M;
    public JanusWorkflowResult R;

    public JanusWorkflowResult ED;
    
    public double S_vPvB;
    public double S_PBT;
    public double S_SVHC;
    
    
    public JanusResult() {
        Valid = true;
        Num = -1;
        Id = "";
        SMILES = "";
        isMetabolite = false;
        ParentNum = -1;
        Metabolites = new ArrayList<>();
        Notes = "";
        P = new JanusWorkflowResult();
        B = new JanusWorkflowResult();
        T = new JanusWorkflowResult();
        C = new JanusWorkflowResult();
        M = new JanusWorkflowResult();
        R = new JanusWorkflowResult();
        ED = new JanusWorkflowResult();
        S_vPvB = 0.5;
        S_PBT = 0.5;
        S_SVHC = 0.5;
    }
    
}
