package insilico.janus.workflows.tools;

import insilico.core.molecule.InsilicoMolecule;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.isomorphism.Pattern;
import org.openscience.cdk.smarts.SmartsPattern;

/**
 *
 * @author Alberto
 */
public class POPClassMatcher {
    
    private final String SMARTS_NAPTHALENE = "c1ccc2ccccc2c1";
    private final Pattern SA_Napthalene;
    
    private final String SMARTS_C_BRANCHED = "CC(C)C";
    private final Pattern SA_C_Branched;
    
    private final String SMARTS_POLYF = "C(F)(F)C(F)(F)C(F)(F)";
    private final Pattern SA_PolyF;
    
    
    public POPClassMatcher() throws Exception {
        SA_Napthalene = SmartsPattern.create(SMARTS_NAPTHALENE, DefaultChemObjectBuilder.getInstance()).setPrepare(false);
        SA_C_Branched = SmartsPattern.create(SMARTS_C_BRANCHED, DefaultChemObjectBuilder.getInstance()).setPrepare(false);
        SA_PolyF = SmartsPattern.create(SMARTS_POLYF, DefaultChemObjectBuilder.getInstance()).setPrepare(false);
    }
    
    
    public POPClass Check(InsilicoMolecule mol) throws Exception {
        POPClass res = new POPClass();
        

        boolean isNaphtalene = false;
        boolean hasBranchedC = false;
        boolean hasPolyFluorinatedTail = false;
        int nC = 0;
        int nCl = 0;
        int nF = 0;
        int nOther = 0;
        
        if ((SA_Napthalene.matches(mol.GetStructure())))
            isNaphtalene = true;
        if (SA_C_Branched.matches(mol.GetStructure()))
            hasBranchedC = true;
        if (SA_PolyF.matches(mol.GetStructure()))
            hasPolyFluorinatedTail = true;
        
        for (IAtom curAt : mol.GetStructure().atoms()) {
            if (curAt.getSymbol().equalsIgnoreCase("C"))
                nC++;
            else if (curAt.getSymbol().equalsIgnoreCase("Cl"))
                nCl++;
            else if (curAt.getSymbol().equalsIgnoreCase("F"))
                nF++;
            else
                nOther++;
        }
        
        
        // PCN
        if ( (isNaphtalene) && (nC == 10) && (nCl > 0) && (nF == 0) && (nOther == 0) )
            res.PCN = true;
        
        // SCCP
        if ( ((nC > 0) && (nCl > 0)) && ((nF==0) && (nOther==0))  ) {
            // Only C and Cl
            if ( (mol.GetSSSR().getAtomContainerCount() == 0) && (!hasBranchedC) ) {
                // no rings and only C chain, no branching
                if ( ( (nC >= 10) && (nC <= 13) ) && ( (nCl>=1) && (nCl<=13) ) )
                    res.SCCP = true;
            }
        }
        
        // PFAS
        if ( (mol.GetSSSR().getAtomContainerCount() == 0) && (hasPolyFluorinatedTail) ) 
            res.PFAS = true;        
        
        return res;
    }
    
    
}
