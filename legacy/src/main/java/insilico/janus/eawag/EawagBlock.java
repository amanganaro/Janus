package insilico.janus.eawag;

import ambit2.smarts.SMIRKSManager;
import insilico.core.exception.InitFailureException;
import insilico.core.exception.MoleculeConversionException;
import insilico.core.molecule.InsilicoMolecule;
import insilico.core.molecule.InsilicoMoleculeMessages;
import insilico.core.molecule.conversion.SmilesMolecule;
import insilico.core.molecule.tools.Normalizer;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Alberto
 */
public class EawagBlock {
    
    protected ArrayList<EawagRule> Rules;
    protected final SMIRKSManager smrkMan;
    protected final static Logger LOGGER = Logger.getLogger(EawagBlock.class.getName());
    protected final Normalizer MolNorm;
    
    
    public EawagBlock() throws IOException, InitFailureException {

        Rules = new ArrayList<>();
        smrkMan = new SMIRKSManager(SilentChemObjectBuilder.getInstance());
        smrkMan.setFlagCheckAromaticityOnResultProcess(true);
        smrkMan.setFlagClearAromaticityBeforeResultProcess(true);

         MolNorm = new Normalizer();
        
        LOGGER.setLevel(Level.OFF);
    }
    
    
    protected void Init(InputStream SourceFile) throws IOException {
     
        DataInputStream in = new DataInputStream(SourceFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        Rules = new ArrayList<>();
        
        String CurLine = br.readLine();  // First line - just headers
        while ((CurLine = br.readLine()) != null) {
            try {
                String[] buf = CurLine.split("\t");
                EawagRule Rule = new EawagRule();
                Rule.setName(buf[1]);
                Rule.setDescription(buf[2]);
                Rule.setSMIRKS(buf[3]);
                Rule.setLikelihood(Integer.valueOf(buf[4]));
                Rules.add(Rule);
            } catch (Throwable e) {
                System.out.println("ERRORE: " + CurLine);
            }
        }
        
    }
    
    
    public ArrayList<RuleResult> Process(String SMILES, int LikelihoodThreshold) throws Exception {
        ArrayList<RuleResult> Res = new ArrayList<>();
        
        // parse input smiles
        InsilicoMolecule im = SmilesMolecule.Convert(SMILES);
        if (!im.IsValid())
            throw new MoleculeConversionException("invalid SMILES: " + im.GetErrors().GetMessages());
        
        for (EawagRule r : Rules) {
            if (r.getLikelihood() < LikelihoodThreshold)
                continue;
            
            IAtomContainer mol = (IAtomContainer)im.GetStructure().clone();
            if (smrkMan.applyTransformation(mol, r.getSMIRKSreaction())) {
                RuleResult curRes = new RuleResult();
                curRes.AppliedRule = r.getName();
                curRes.AppliedRuleDescription = r.getDescription();
                curRes.AppliedRuleLikelihood = r.getLikelihood();
                
                // create output molecule/results
                InsilicoMoleculeMessages warn = new InsilicoMoleculeMessages();
                mol = MolNorm.ConfigureMolecule( mol, warn);
                if (warn.GetSize() != 0)
                    curRes.Remarks = warn.GetMessages();
                curRes.Result = SmilesMolecule.GenerateSmiles(mol);

                Res.add(curRes);
            } 
        }
        
        return Res;
    }
    
    
    public ArrayList<PathwayResult> ProcessPathway(String SMILES, int LikelihoodThreshold, int MaxDepth) throws Exception {
        
        ArrayList<PathwayResult> Res = new ArrayList<>();
        RecursiveProcessPathway(SMILES, LikelihoodThreshold, new PathwayResult(), Res, MaxDepth);
        return Res;

    }

    
    private void RecursiveProcessPathway(String SMILES, int LikelihoodThreshold, PathwayResult prevPath, 
            ArrayList<PathwayResult> Results, int MaxDepth) throws Exception  {
        
        int AppliedRules = 0;
        
        int curDepth = (prevPath.AppliedRules.isEmpty()) ? 0 : prevPath.AppliedRules.split(">>").length;

        if (curDepth < MaxDepth) {
            
            ArrayList<RuleResult> curRes = Process(SMILES, LikelihoodThreshold);
            
            for (RuleResult r : curRes) {
                AppliedRules++;

                // Update pathway
                PathwayResult curPath = new PathwayResult();
                curPath.AppliedRules = prevPath.AppliedRules;
                curPath.Result = prevPath.Result;

                if (!curPath.AppliedRules.isEmpty())
                    curPath.AppliedRules += " >> ";
                curPath.AppliedRules += r.AppliedRule + " (" + EawagRule.LHtoString(r.AppliedRuleLikelihood) + ")";
                curPath.Result = r.Result;

                ArrayList<String> Mols = new ArrayList<>();

                // Check if result is a disconnected structure
                if (r.Result.contains(".")) {
                    for (String s : r.Result.split("\\.")) {
                        Mols.add(s);
                    }
                } else {
                    Mols.add(r.Result);
                }

                // Apply rules to obtained compound(s)
                for (String s : Mols) {
                    PathwayResult nextPath = new PathwayResult();
                    nextPath.AppliedRules = curPath.AppliedRules;
                    nextPath.Result = s;
                    RecursiveProcessPathway(s, LikelihoodThreshold, nextPath, Results, MaxDepth);
                }                
            }
        }
    
        if (AppliedRules == 0)
            if (!prevPath.AppliedRules.isEmpty())
                Results.add(prevPath);
        
    }
    
}
