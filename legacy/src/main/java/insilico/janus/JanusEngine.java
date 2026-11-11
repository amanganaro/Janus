package insilico.janus;

import insilico.core.exception.GenericFailureException;
import insilico.core.exception.InitFailureException;
import insilico.core.molecule.InsilicoMolecule;
import insilico.core.molecule.conversion.SmilesMolecule;

//import insilico.janus.eawag.EawagRule;
//import insilico.janus.eawag.RuleResult;
//import insilico.janus.eawag.bbd.EawagBBD;
import insilico.janus.eawag.EawagRule;
import insilico.janus.eawag.RuleResult;
import insilico.janus.eawag.bbd.EawagBBD;
import insilico.janus.gui.iJanusModelRunnerMessenger;
import insilico.janus.workflows.JanusWorkflowBCF;
import insilico.janus.workflows.JanusWorkflowCarcinogenicity;
import insilico.janus.workflows.JanusWorkflowReproTox;
import insilico.janus.workflows.JanusWorkflowEndocrine;
import insilico.janus.workflows.JanusWorkflowMutagenicity;
import insilico.janus.workflows.JanusWorkflowPersistence;
import insilico.janus.workflows.JanusWorkflowToxicity;
import insilico.janus.workflows.tools.POPClass;
import insilico.janus.workflows.tools.POPClassMatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList; 
import java.util.HashMap;

/**
 *
 * @author Alberto
 */
public class JanusEngine {

    public final static short WF_PERSISTENCE = 1;
    public final static short WF_BCF = 2;
    public final static short WF_TOXICITY = 3;
    public final static short WF_CARCINOGENICITY = 4;
    public final static short WF_MUTAGENICITY = 5;
    public final static short WF_REPROTOX = 6;
    public final static short WF_ED = 7;
    
    public HashMap<Short, Boolean> Workflows;
    
    private final static String EAWAG_PATH = "./EAWAG-BDD-0.6/EAWAG-BBD.jar";
    private final EawagBBD BBD;

    private iJanusModelRunnerMessenger Messenger;
    private final DecimalFormat Format_2_digits;
    private boolean CalculateMetabolism;
    private final boolean BatchByMoleculeMode;
    
    private JanusWorkflowPersistence wPers;
    private JanusWorkflowBCF wBCF;
    private JanusWorkflowToxicity wTox;
    private JanusWorkflowCarcinogenicity wCarc;
    private JanusWorkflowMutagenicity wMuta;
    private JanusWorkflowReproTox wDev;
    private JanusWorkflowEndocrine wEndo;
    
    private POPClassMatcher POPScreening;
        
    private class MetaboliteResult {
        public String SMILES;
        public String Rule;
    }
    
    
    /**
     * Constructor.
     * If is set with BatchByMoleculeMode TRUE, all the workflow are created
     * once, in the constructor (high memory usage, the calculate method should
     * be then called only with few molecules, several times). Otherwise, 
     * workflows are created and then freed in the calculate method (to be used 
     * when a big list of molecules is passed to the method only once).
     * 
     * @param BatchByMoleculeMode
     * @throws InitFailureException 
     */
    public JanusEngine(boolean BatchByMoleculeMode) throws InitFailureException {
        Format_2_digits = new DecimalFormat("00");
        CalculateMetabolism = false;
        
        this.BatchByMoleculeMode = BatchByMoleculeMode;
        
        Workflows = new HashMap<>();
        Workflows.put(WF_PERSISTENCE, true);
        Workflows.put(WF_BCF, true);
        Workflows.put(WF_TOXICITY, true);
        Workflows.put(WF_CARCINOGENICITY, true);
        Workflows.put(WF_MUTAGENICITY, true);
        Workflows.put(WF_REPROTOX, true);
        Workflows.put(WF_ED, true);
        
        if (this.BatchByMoleculeMode) {
            wPers = new JanusWorkflowPersistence();
            wBCF = new JanusWorkflowBCF();
            wTox = new JanusWorkflowToxicity();
            wCarc = new JanusWorkflowCarcinogenicity();
            wMuta = new JanusWorkflowMutagenicity();
            wDev = new JanusWorkflowReproTox();
            wEndo = new JanusWorkflowEndocrine();           
        }
        
        try {
            POPScreening = new POPClassMatcher();
        } catch (Exception e) {
            throw new InitFailureException("Unable to init POP Screening class");
        }
        
        try {
            BBD = new EawagBBD();
        } catch (InitFailureException | IOException e) {
            throw new InitFailureException("Unable to init metabolism engine (BBD): " + e.getMessage());
        }
    }
    
    
    public void setMetabolismCalculation(boolean status) {
        this.CalculateMetabolism = status;
    }
    
    
    public void SetMessenger (iJanusModelRunnerMessenger Messenger) {
        this.Messenger = Messenger;
    }
    
    
    public ArrayList<JanusResult> Calculate(ArrayList<InsilicoMolecule> Dataset, 
            ArrayList<ExperimentalValues> DatasetExp) throws 
            GenericFailureException, InitFailureException {
        
        // Calculate metabolism and updates dataset, also create results list
        ArrayList<InsilicoMolecule> DatasetWithMetabolites = new ArrayList<>();
        ArrayList<ExperimentalValues> DatasetExpWithMetabolites = new ArrayList<>();
        ArrayList<JanusResult> res = new ArrayList<>();
        
        this.Messenger.SetDatasetSize(Dataset.size());
        
        try {
            this.Messenger.SendMessage("Metabolism calculation");
            
            int origIdx = 0;
            int mainIdx = 0;
            for (InsilicoMolecule m : Dataset) {
                this.Messenger.UpdateProgress();
                
                // just skip invalid molecule
                if (!m.IsValid()) {
                    this.Messenger.SendLogMessage("* Invalid molecule found - no. " + (origIdx+1) + 
                            " - SMILES: " + m.GetSMILES() + " - Errors: " + m.GetErrors().GetMessages());
                    continue;      
                }
                
                DatasetWithMetabolites.add(m);
                DatasetExpWithMetabolites.add(DatasetExp.get(origIdx));
                res.add(new JanusResult());
                res.get(mainIdx).Num = (mainIdx+1);
                res.get(mainIdx).SMILES = m.GetSMILES();            
                res.get(mainIdx).Id = m.GetId();        
                mainIdx++;
                
                if (this.CalculateMetabolism) {
                    ArrayList<MetaboliteResult> Metabolites = ProcessMetabolism(m.GetSMILES());
                    int metaIdx=1;
                    int parentIndex = mainIdx;
                    for (MetaboliteResult curMeta : Metabolites) {
                        InsilicoMolecule newMol = SmilesMolecule.Convert(curMeta.SMILES);
                        newMol.SetId(m.GetId() + " [M-" + Format_2_digits.format(metaIdx) + "]"); 
                        DatasetWithMetabolites.add(newMol);
                        DatasetExpWithMetabolites.add(new ExperimentalValues(""));

                        res.add(new JanusResult());
                        res.get(mainIdx).Num = (mainIdx+1);
                        res.get(mainIdx).SMILES = newMol.GetSMILES();            
                        res.get(mainIdx).Id = newMol.GetId();    
                        res.get(mainIdx).isMetabolite = true;
                        res.get(mainIdx).ParentNum = parentIndex;
                        res.get(mainIdx).Notes = "Metabolite generated by the rule " + curMeta.Rule;                    
                        mainIdx++;

                        res.get(parentIndex - 1).Metabolites.add(mainIdx);

                        metaIdx++;
                    }                    
                }

                origIdx++;
            }
        } catch (Exception e) {
            throw new GenericFailureException("Unable to run metabolism module - " + e.getLocalizedMessage());
        }
        
        if (this.CalculateMetabolism)   
            this.Messenger.SendLogMessage("Metabolism analysis performed - new resulting set consists of " +
                    DatasetWithMetabolites.size() + " molecules.");
        this.Messenger.SetDatasetSize(DatasetWithMetabolites.size());
        
        int idx;
        
        // run P workflow
        if ( UseWorkflow(WF_PERSISTENCE) ) {
            idx = 0;        
            this.Messenger.SendMessage("PERSISTENCE workflow");
            if (!BatchByMoleculeMode)
                wPers = new JanusWorkflowPersistence();
            for (InsilicoMolecule m : DatasetWithMetabolites) {
                this.Messenger.UpdateProgress();
                try {
                    POPClass POP = POPScreening.Check(m);
                    wPers.SetPOPClass(POP);
                    wPers.SetExperimentalValue(DatasetExpWithMetabolites.get(idx));
                    res.get(idx).P = wPers.Calculate(m);
                } catch (Exception e) {
                    res.get(idx).P = new JanusWorkflowResult();
                    this.Messenger.SendLogMessage("- Molecule no. " + (idx+1) + ": error in the P workflow");
                }
                idx++;
            }
            if (!BatchByMoleculeMode) {
                wPers = null;
                System.gc();
            }        
        } 
        
        // run B workflow
        if ( UseWorkflow(WF_BCF) ) {
            idx = 0;        
            this.Messenger.SendMessage("BCF workflow");
            if (!BatchByMoleculeMode)
                wBCF = new JanusWorkflowBCF();
            for (InsilicoMolecule m : DatasetWithMetabolites) {
                this.Messenger.UpdateProgress();
                try {
                    POPClass POP = POPScreening.Check(m);
                    wBCF.SetPOPClass(POP);
                    wBCF.SetExperimentalValue(DatasetExpWithMetabolites.get(idx));
                    res.get(idx).B = wBCF.Calculate(m);
                } catch (Exception e) {
                    res.get(idx).B = new JanusWorkflowResult();
                    this.Messenger.SendLogMessage("- Molecule no. " + (idx+1) + ": error in the B workflow");
                }
                idx++;
            }
            if (!BatchByMoleculeMode) {
                wBCF = null;
                System.gc();
            }
        }
        
        // run T workflow
        if ( UseWorkflow(WF_TOXICITY) ) {
            idx = 0;        
            this.Messenger.SendMessage("TOXICITY workflow");
            if (!BatchByMoleculeMode)
                wTox = new JanusWorkflowToxicity();
            for (InsilicoMolecule m : DatasetWithMetabolites) {
                this.Messenger.UpdateProgress();
                try {
                    wTox.SetExperimentalValue(DatasetExpWithMetabolites.get(idx));
                    res.get(idx).T = wTox.Calculate(m);
                } catch (GenericFailureException e) {
                    res.get(idx).T = new JanusWorkflowResult();
                    this.Messenger.SendLogMessage("- Molecule no. " + (idx+1) + ": error in the T workflow");
                }
                idx++;
            }
            if (!BatchByMoleculeMode) {
                wTox = null;
                System.gc();
            }
        }
            
        // run C workflow
        if ( UseWorkflow(WF_CARCINOGENICITY) ) {
            idx = 0;        
            this.Messenger.SendMessage("CARCINOGENICITY workflow");
            if (!BatchByMoleculeMode)
                wCarc = new JanusWorkflowCarcinogenicity();
            for (InsilicoMolecule m : DatasetWithMetabolites) {
                this.Messenger.UpdateProgress();
                try {
                    wCarc.SetExperimentalValue(DatasetExpWithMetabolites.get(idx));
                    res.get(idx).C = wCarc.Calculate(m);
                } catch (GenericFailureException e) {
                    res.get(idx).C = new JanusWorkflowResult();
                    this.Messenger.SendLogMessage("- Molecule no. " + (idx+1) + ": error in the C workflow");
                }
                idx++;
            }
            if (!BatchByMoleculeMode) {
                wCarc = null;
                System.gc();
            }
        }
        
        // run M workflow
        if ( UseWorkflow(WF_MUTAGENICITY) ) {
            idx = 0;        
            this.Messenger.SendMessage("MUTAGENICITY workflow");
            if (!BatchByMoleculeMode)
                wMuta = new JanusWorkflowMutagenicity();
            for (InsilicoMolecule m : DatasetWithMetabolites) {
                this.Messenger.UpdateProgress();
                try {
                    wMuta.SetExperimentalValue(DatasetExpWithMetabolites.get(idx));
                    res.get(idx).M = wMuta.Calculate(m);
                } catch (GenericFailureException e) {
                    res.get(idx).M = new JanusWorkflowResult();
                    this.Messenger.SendLogMessage("- Molecule no. " + (idx+1) + ": error in the M workflow");
                }
                idx++;
            }
            if (!BatchByMoleculeMode) {
                wMuta = null;
                System.gc();
            }
        }
        
        // run R workflow
        if ( UseWorkflow(WF_REPROTOX)) {
            idx = 0;        
            this.Messenger.SendMessage("REPRODUCTIVE TOXICITY workflow");
            if (!BatchByMoleculeMode)
                wDev = new JanusWorkflowReproTox();
            for (InsilicoMolecule m : DatasetWithMetabolites) {
                this.Messenger.UpdateProgress();
                try {
                    wDev.SetExperimentalValue(DatasetExpWithMetabolites.get(idx));
                    res.get(idx).R = wDev.Calculate(m);
                } catch (GenericFailureException e) {
                    res.get(idx).R = new JanusWorkflowResult();
                    this.Messenger.SendLogMessage("- Molecule no. " + (idx+1) + ": error in the R workflow");
                }
                idx++;
            }
            if (!BatchByMoleculeMode) {
                wDev = null;
                System.gc();
            }
        }
        
        // run ED workflow
        if ( UseWorkflow(WF_ED) ) {
            idx = 0;        
            this.Messenger.SendMessage("ENDOCRINE DISRUPTOR workflow");
            if (!BatchByMoleculeMode)
                wEndo = new JanusWorkflowEndocrine();
            for (InsilicoMolecule m : DatasetWithMetabolites) {
                this.Messenger.UpdateProgress();
                try {
                    wEndo.SetExperimentalValue(DatasetExpWithMetabolites.get(idx));
                    res.get(idx).ED = wEndo.Calculate(m);
                } catch (GenericFailureException e) {
                    res.get(idx).ED = new JanusWorkflowResult();
                    this.Messenger.SendLogMessage("- Molecule no. " + (idx+1) + ": error in the ED workflow");
                }
                idx++;
            }
            if (!BatchByMoleculeMode) {
                wEndo = null;
                System.gc();
            }
        }
        
        // Calculate final indices
        for (int i=0; i<DatasetWithMetabolites.size(); i++) {
            res.get(i).S_vPvB = CalculatevPvB(res.get(i).P.Score, res.get(i).B.Score);
            res.get(i).S_SVHC = CalculateSVHC(res.get(i).C.Score, res.get(i).M.Score, res.get(i).R.Score, res.get(i).ED.Score);
            res.get(i).S_PBT = CalculatePBT(res.get(i).P.Score, res.get(i).B.Score, res.get(i).T.Score, 
                    res.get(i).C.Score, res.get(i).M.Score, res.get(i).R.Score, res.get(i).ED.Score);
        }
        
        return res;
    }
    
    
    private ArrayList<MetaboliteResult> ProcessMetabolism(String SMILES) throws Exception {

        ArrayList<RuleResult> BBDResults = BBD.Process(SMILES, EawagRule.LH_LIKELY);
        ArrayList<MetaboliteResult> Res = new ArrayList<>();

        for (RuleResult r : BBDResults) {

            if (r.Result.contains(".")) {
                for (String s : r.Result.split("\\.")) {
                    MetaboliteResult curRes = new MetaboliteResult();
                    curRes.SMILES = s;
                    curRes.Rule = r.AppliedRuleDescription;
                    Res.add(curRes);
                }
            } else {
                MetaboliteResult curRes = new MetaboliteResult();
                curRes.SMILES = r.Result;
                curRes.Rule = r.AppliedRuleDescription;
                Res.add(curRes);
            }
        }

        return Res;


    }
    
    
    /**
     * Calculation of the vPvB index based on the geometric mean of the P
     * and B properties, equally weighted.
     * 
     * @param P
     * @param B
     * @return 
     */
    private double CalculatevPvB(double P, double B) {
        
        if ( (P == JanusResult.MISSING_VALUE) || (B == JanusResult.MISSING_VALUE))
            return JanusResult.MISSING_VALUE;
        
        return Math.pow(P, 0.5) * Math.pow(B, 0.5);

    }
    
        
    /**
     * Calculation of the SVHC index (CMR+ED) based on the worst case plus
     * an additional coefficient for the other properties.
     * 
     * @param C
     * @param M
     * @param R
     * @param ED
     * @return 
     */
    private double CalculateSVHC(double C, double M, double R, double ED) {
        
        if ( (C == JanusResult.MISSING_VALUE) || (M == JanusResult.MISSING_VALUE)
            || (R == JanusResult.MISSING_VALUE) || (ED == JanusResult.MISSING_VALUE))
            return JanusResult.MISSING_VALUE;
        
        double[] val = new double[4];
        val[0] = C; val[1] = M; val[2] = R; val[3] = ED; 
        
        int max = 0;
        for (int i=0; i<4; i++)
            if (val[i] > val[max]) max = i;
        
        double ave_diff = 0;
        for (int i=0; i<4; i++)
            ave_diff += (val[max] - val[i]);
        ave_diff /= 3;
        
        double coeff = 0.15 * ave_diff;        
        double SVHC = val[max] - coeff;
        
        return SVHC;
    }
    
    
    /**
     * Calculation of the PBT index based on the geometric mean of the P, B 
     * and T properties, equally weighted. T is calculated as the worst case
     * (same algorithm of SVHC) of aquatic T + C + M + R + ED.
     * 
     * @param P
     * @param B
     * @param T
     * @param C
     * @param M
     * @param R
     * @param ED
     * @return 
     */
    private double CalculatePBT(double P, double B, double T, double C, 
            double M, double R, double ED) {
        
        if ( (C == JanusResult.MISSING_VALUE) || (M == JanusResult.MISSING_VALUE)
            || (R == JanusResult.MISSING_VALUE) || (ED == JanusResult.MISSING_VALUE)
                || (P == JanusResult.MISSING_VALUE) || (B == JanusResult.MISSING_VALUE)
                || (T == JanusResult.MISSING_VALUE))
            return JanusResult.MISSING_VALUE;
        
        double[] val = new double[5];
        val[0] = C; val[1] = M; val[2] = R; val[3] = ED; val[4] = T;
        
        int max = 0;
        for (int i=0; i<5; i++)
            if (val[i] > val[max]) max = i;
        
        double ave_diff = 0;
        for (int i=0; i<5; i++)
            ave_diff += (val[max] - val[i]);
        ave_diff /= 4;
        
        double coeff = 0.15 * ave_diff;        
        double T_overall = val[max] - coeff;
        
        double PBT = Math.pow(P, 0.334) * Math.pow(B, 0.333) * Math.pow(T_overall, 0.333);
        return PBT;
    }
    
    
    /**
     * Transformation for all scores [0,1] to final score that includes the
     * weight of the reliability.
     * 
     * @param Assessment
     * @param Reliability
     * @return 
     */
    public final static double TransformScore(double Assessment, double Reliability) {
        if (Assessment == JanusResult.MISSING_VALUE) return 0.5;
        if (Reliability == JanusResult.MISSING_VALUE) return 0.5;
        return Assessment + (0.5 - Assessment) * (1 - Math.pow(Reliability, 0.5));
    }
    
    
    public boolean UseWorkflow(short Key) {
        if (this.Workflows.containsKey(Key)) 
            if (this.Workflows.get(Key) == true)
                return true;
        return false;
    }
}
