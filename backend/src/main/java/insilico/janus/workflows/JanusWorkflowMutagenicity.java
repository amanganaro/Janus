package insilico.janus.workflows;

import insilico.core.exception.GenericFailureException;
import insilico.core.exception.InitFailureException;
import insilico.core.model.InsilicoModelConsensusOutput;
import insilico.core.model.InsilicoModelOutput;
import insilico.core.model.runner.InsilicoModelRunnerByMolecule;
import insilico.core.molecule.InsilicoMolecule;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusResult;
import insilico.janus.JanusWorkflowResult;
import insilico.janus.iJanusWorkflow;
import insilico.janus.workflows.tools.MutagenicitySmarts;
import insilico.janus.workflows.tools.POPClass;
import insilico.mutagenicity_bb.ismMutagenicityBB;
import insilico.mutagenicity_caesar.ismMutagenicityCaesar;
import insilico.mutagenicity_consensus.ismcMutagenicity;
import insilico.mutagenicity_knn.ismMutagenicityKnn;
import insilico.mutagenicity_sarpy.ismMutagenicitySarpy;

import java.util.ArrayList;

/**
 *
 * @author Alberto
 */
public class JanusWorkflowMutagenicity implements iJanusWorkflow {

    private final ismMutagenicityBB BB;
    private final ismMutagenicityCaesar Caesar;
    private final ismMutagenicityKnn KNN;
    private final ismMutagenicitySarpy Sarpy;
    private final ismcMutagenicity Cons;
    
    private final InsilicoModelRunnerByMolecule Runner;
    private final MutagenicitySmarts Smarts;
    
    private ExperimentalValues Experimental;
    
    
    public JanusWorkflowMutagenicity() throws InitFailureException {
        Experimental = null;
        
        try {
            Smarts = new MutagenicitySmarts();
        } catch (InitFailureException ex) {
            throw new InitFailureException("Unable to init Mutagenicity SMARTS - " + ex.getLocalizedMessage());            
        }
        
        BB = new ismMutagenicityBB();
        Caesar = new ismMutagenicityCaesar();
        KNN = new ismMutagenicityKnn();
        Sarpy = new ismMutagenicitySarpy();
        Cons = new ismcMutagenicity();
        
        // build the runner for multiple models
        Runner = new InsilicoModelRunnerByMolecule();
        Runner.AddModel(BB);
        Runner.AddModel(Caesar);
        Runner.AddModel(KNN);        
        Runner.AddModel(Sarpy);  
        Runner.AddModel(Cons);
    }
    
    
    @Override
    public JanusWorkflowResult Calculate(InsilicoMolecule mol) throws GenericFailureException {
        
        JanusWorkflowResult Results = new JanusWorkflowResult();
        
        
        ////// run needed models
        try {
            Runner.Run(mol);
        } catch (Throwable e) {
            if (e.getClass() == OutOfMemoryError.class) 
                throw new GenericFailureException("A memory error occurred. Please check the user's guide for troubleshooting about memory usage.");
            else 
                throw new GenericFailureException("An unexpected error has occurred: " + e.getLocalizedMessage());                    
        }                      
        InsilicoModelOutput OutBB = Runner.GetModelWrapper(ismMutagenicityBB.class).getResult().get(0);
        InsilicoModelOutput OutCaesar = Runner.GetModelWrapper(ismMutagenicityCaesar.class).getResult().get(0);
        InsilicoModelOutput OutKNN = Runner.GetModelWrapper(ismMutagenicityKnn.class).getResult().get(0);        
        InsilicoModelOutput OutSarpy = Runner.GetModelWrapper(ismMutagenicitySarpy.class).getResult().get(0);        
        InsilicoModelConsensusOutput OutCons = Runner.GetModelConsensusWrapper(ismcMutagenicity.class).getResult().get(0);
        
        ////// main workflow
        double muta_final_value = JanusResult.MISSING_VALUE;
        double muta_final_reliability = JanusResult.MISSING_VALUE;
        String SMARTS_matching = "not performed";

        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_MUTA))) {

            // user added exp        
            muta_final_value = Experimental.getProperty(ExperimentalValues.KEY_MUTA);
            muta_final_reliability = 1;
            
        } else {

            // check exp from models
            ArrayList<InsilicoModelOutput> bufRes = new ArrayList<>();
            bufRes.add(OutBB);
            bufRes.add(OutCaesar);
            bufRes.add(OutKNN);
            bufRes.add(OutSarpy);
            
            int nExp=0, nExpPOS=0, nExpNEG=0;
            for (InsilicoModelOutput o : bufRes) {
                if ( (o.getStatus() == InsilicoModelOutput.OUTPUT_OK) && (o.HasExperimental()) ) {
                    nExp++;
                    if (o.getExperimental() == 1) nExpPOS++;
                    else nExpNEG++;
                }
            }
            
            if (nExp > 0) {
                
                if ( (nExpPOS > 0) && (nExpNEG == 0) ) {
                    muta_final_value = 1;
                    muta_final_reliability = 1;
                } else if ( (nExpPOS > 0) && (nExpNEG > 0) ) {
                    muta_final_value = 1;
                    muta_final_reliability = 0.9;
                } else if ( (nExpPOS == 0) && (nExpNEG > 0) ) {
                    
                    // check SMARTS
                    boolean smarts_check = false;
                    String res = Smarts.Match(mol);
                    if (res != null) {
                        smarts_check = true;
                        SMARTS_matching = res;
                    }
                    
                    muta_final_value = 0;
                    if (smarts_check)
                        muta_final_reliability = 0.9;
                    else
                        muta_final_reliability = 1;
                }

            } else {
                
                // check predictions from consensus
                muta_final_value = (int)OutCons.getMainResultValue();
                double cons_score = muta_final_value == 1 ?
                        Double.valueOf(OutCons.getResults()[1]) :
                        Double.valueOf(OutCons.getResults()[2]);

                if (muta_final_value == 1) {
                    muta_final_reliability = NormalizeConsScore(cons_score);
                } else {
                    // check SMARTS
                    boolean smarts_check = false; 
                    String res = Smarts.Match(mol);
                    if (res != null) {
                        smarts_check = true;
                        SMARTS_matching = res;
                    }

                    if (smarts_check) 
                        muta_final_reliability = cons_score - 0.1;
                    else
                        muta_final_reliability = NormalizeConsScore(cons_score); 
                                        
                }
            }
        }            
       
        
        ////// calculation of score
        double muta_score;
        if (muta_final_value == 1)
            muta_score = 1;
        else
            muta_score = 0.1;
        
        ////// result object
        Results.Prediction = muta_final_value;
        Results.PredictionClass = muta_final_value == 0 ? "NON Mutagenic" : "MUTAGENIC";
        Results.Reliability = muta_final_reliability;
        Results.Score = JanusEngine.TransformScore(muta_score, Results.Reliability);
        Results.AdditionalValues.put("Mutagenicity classification model (Caesar) prediction", OutCaesar.getAssessment());
        Results.AdditionalValues.put("Mutagenicity classification model (Benigni-Bossa ruleset) prediction", OutBB.getAssessment());
        Results.AdditionalValues.put("Mutagenicity classification model (KNN) prediction", OutKNN.getAssessment());
        Results.AdditionalValues.put("Mutagenicity classification model (Sarpy) prediction", OutSarpy.getAssessment());
        Results.AdditionalValues.put("Mutagenicity consensus model prediction", OutCons.getAssessment());
        Results.AdditionalValues.put("Mutagenicity additional SMARTS matching", SMARTS_matching);        
        
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_MUTA))) 
            Results.AdditionalValues.put("User added experimental Mutagenicity Ames test", 
                    (Experimental.getProperty(ExperimentalValues.KEY_BCF))==1 ? "Mutagenic" : "NON mutagenic");
        
        
        return Results;
    }
    
    
    private double NormalizeConsScore(double score) {
        if (score >= 0.5) return 0.8;
        if (score >0.15) return 0.6;
        return 0.3;
    }
    

    @Override
    public void SetExperimentalValue(ExperimentalValues ExpVal) {
        this.Experimental = ExpVal;
    }

    @Override
    public void SetPOPClass(POPClass POP) {
        // do nothing - this workflow does not use POP screening
    }
    
    
}