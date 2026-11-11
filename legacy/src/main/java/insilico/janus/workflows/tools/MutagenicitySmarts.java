package insilico.janus.workflows.tools;

import insilico.core.exception.GenericFailureException;
import insilico.core.exception.InitFailureException;
import insilico.core.exception.InvalidMoleculeException;
import insilico.core.molecule.InsilicoMolecule;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.isomorphism.Pattern;
import org.openscience.cdk.smarts.SmartsPattern;

/**
 *
 * @author Alberto
 */
public class MutagenicitySmarts {
    
//    private CustomQueryMatcher Matcher;
    private Pattern[] SA;

    final static String[] SMARTS = {    
        "OC(=O)C(=C(C)C)Cl",
        "O=NN(C)Cc1cccc(c1)",
        "n1c2ccc(N)cc2nc(c1)",
        "n1cc(nc2ccc(N)c(c12)C)",
        "c1ccc2cc3cc(ccc3cc2c1)"
    };    
    
    
    public MutagenicitySmarts() throws InitFailureException {

        SA = new Pattern[SMARTS.length];
        int idx = 0;
        for (String s : SMARTS) {
            SA[idx] = SmartsPattern.create(s, DefaultChemObjectBuilder.getInstance()).setPrepare(false);
            idx++;
        }
    }
    
    
    public String Match(InsilicoMolecule mol) throws GenericFailureException {
        
        try {
//            Matcher = new CustomQueryMatcher(mol);
            
            for (int i=0; i<SA.length; i++) 
                if ((SA[i].matches(mol.GetStructure())))
                    return("Alert no. " + (i+1) + " defined by the SMARTS: " + SMARTS[i]);
            
        } catch (InvalidMoleculeException ex) {
            throw new GenericFailureException(ex.getMessage());
        }
        
        return null;
    }
    
}
