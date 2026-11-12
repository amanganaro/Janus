package insilico.janus.eawag;

import ambit2.smarts.SMIRKSManager;
import ambit2.smarts.SMIRKSReaction;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Alberto
 */
public class EawagRule {

    protected final static Logger LOGGER = Logger.getLogger(EawagRule.class.getName());
    
    public final static int LH_NOT_SET = -1;
    public final static int LH_VERY_UNLIKELY = 0;
    public final static int LH_UNLIKELY = 1;
    public final static int LH_POSSIBLE = 2;
    public final static int LH_LIKELY = 3;
    public final static int LH_VERY_LIKELY = 4;
    
    private String Name;
    private String Description;
    private String SMIRKS;
    private SMIRKSReaction SMIRKSreaction;
    private int Likelihood;

    
    public final static String LHtoString(int LH) {
        String res = "";
        switch (LH) {
            case -1: res = "not set"; break;
            case 0: res = "very unlikely"; break;
            case 1: res = "unlikely"; break;
            case 2: res = "possible"; break;
            case 3: res = "likely"; break;
            case 4: res = "very likely"; break;
        }
        return res;
    }
    
    
    public EawagRule() {
        Name="";
        Description="";
        SMIRKS="";
        Likelihood = -1;
        SMIRKSreaction = null;
        LOGGER.setLevel(Level.OFF);
    }
        
    /**
     * @return the Name
     */
    public String getName() {
        return Name;
    }

    /**
     * @param Name the Name to set
     */
    public void setName(String Name) {
        this.Name = Name;
    }

    /**
     * @return the Description
     */
    public String getDescription() {
        return Description;
    }

    /**
     * @param Description the Description to set
     */
    public void setDescription(String Description) {
        this.Description = Description;
    }

    /**
     * @return the SMIRKS
     */
    public String getSMIRKS() {
        return SMIRKS;
    }

    /**
     * @param SMIRKS the SMIRKS to set
     */
    public void setSMIRKS(String SMIRKS) {
        this.SMIRKS = SMIRKS;
        
        SMIRKSManager smrkMan = new SMIRKSManager(SilentChemObjectBuilder.getInstance());
        smrkMan.setFlagCheckAromaticityOnResultProcess(true);
        smrkMan.setFlagClearAromaticityBeforeResultProcess(true);
        SMIRKSreaction = smrkMan.parse(SMIRKS);
        if (!smrkMan.getErrors().equals(""))
            SMIRKSreaction = null;        
    }

    /**
     * @return the Likelihood
     */
    public int getLikelihood() {
        return Likelihood;
    }

    /**
     * @param Likelihood the Likelihood to set
     */
    public void setLikelihood(int Likelihood) {
        this.Likelihood = Likelihood;
    }

    /**
     * @return the SMIRKSreaction
     */
    public SMIRKSReaction getSMIRKSreaction() {
        return SMIRKSreaction;
    }

}
