package insilico.janus.workflows;

import insilico.core.exception.GenericFailureException;
import insilico.core.exception.InitFailureException;
import insilico.core.model.InsilicoModelOutput;
import insilico.core.model.runner.InsilicoModelRunnerByMolecule;
import insilico.core.molecule.InsilicoMolecule;
import insilico.devtox_caesar.ismDevtoxCaesar;
import insilico.devtox_pg.ismDevToxPG;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusResult;
import insilico.janus.JanusWorkflowResult;
import insilico.janus.iJanusWorkflow;
import insilico.janus.workflows.tools.POPClass;
import insilico.zebrafish_coral.ismZebrafishCoral;

/**
 *
 * @author Alberto
 */
public class JanusWorkflowReproTox implements iJanusWorkflow {

    private final ismDevtoxCaesar Caesar;
    private final ismDevToxPG PG;
    private final ismZebrafishCoral Zebra;
    
    private final InsilicoModelRunnerByMolecule Runner;
    
    private ExperimentalValues Experimental;
    
    
    public JanusWorkflowReproTox() throws InitFailureException {
        Experimental = null;
        
        Caesar = new ismDevtoxCaesar();
        PG = new ismDevToxPG();
        Zebra = new ismZebrafishCoral();
        
        
        // build the runner for multiple models
        Runner = new InsilicoModelRunnerByMolecule();
        Runner.AddModel(Caesar);
        Runner.AddModel(PG);        
        Runner.AddModel(Zebra);        
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
        InsilicoModelOutput OutCaesar = Runner.GetModelWrapper(ismDevtoxCaesar.class).getResult().get(0);
        InsilicoModelOutput OutPG = Runner.GetModelWrapper(ismDevToxPG.class).getResult().get(0);        
        InsilicoModelOutput OutZebra = Runner.GetModelWrapper(ismZebrafishCoral.class).getResult().get(0);        
        
        
        ////// main workflow
        int dev_final_value = JanusResult.MISSING_VALUE;
        double dev_final_reliability = JanusResult.MISSING_VALUE;

        int ExpPG = -1, ExpCAE = -1;
        int PredPG = -1, PredCAE = -1;
        short AdPG = 0, AdCAE = 0; // 3 high, 2 medium, 1 low, 0 n.a.
        
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_REPRO))) {

            // user added exp        
            dev_final_value = Experimental.getProperty(ExperimentalValues.KEY_REPRO).intValue();
            dev_final_reliability = 1;
            
        } else {
                
            if (OutPG.getStatus() == InsilicoModelOutput.OUTPUT_OK) {
                if (OutPG.HasExperimental()) {
                    int val = (int)OutPG.getExperimental();
                    switch (val) {
                        case 0: ExpPG = 0; break;
                        case 1: ExpPG = 1; break;
                        case 2: ExpPG = 1; break;
                        case 3: ExpPG = 1; break;
                        case 4: ExpPG = 1; break;
                        case 5: ExpPG = 1; break;
                        case 6: ExpPG = 0; break;
                        case 7: ExpPG = 0; break;
                        case 8: ExpPG = 1; break;
                        case 9: ExpPG = 0; break;
                        case 10: ExpPG = -1; break;
                    }
                }
                PredPG = (int)OutPG.getMainResultValue();
                AdPG = OutPG.getADI().GetAssessmentClass();
            }

            if (OutCaesar.getStatus() == InsilicoModelOutput.OUTPUT_OK) {
                if (OutCaesar.HasExperimental()) ExpCAE = (int)OutCaesar.getExperimental();
                PredCAE = (int)OutCaesar.getMainResultValue();
                AdCAE = OutCaesar.getADI().GetAssessmentClass();
            }

            boolean ZebraExp = false;
            double Zebra = OutZebra.getMainResultValue();
            if (OutZebra.HasExperimental()) {
                Zebra = OutZebra.getExperimental();
                ZebraExp = true;
            }


            if ( (ExpCAE!=-1) || (ExpPG!=-1) ) {

                // at least one exp
                
                if ( (ExpCAE != -1) || (ExpPG == -1) ) {
                    dev_final_value = ExpCAE;
                    dev_final_reliability = 1;
                } else if ( (ExpCAE == -1) || (ExpPG != -1) ) {
                    dev_final_value = ExpPG;
                    dev_final_reliability = 1;
                } else {
                    if (ExpCAE == ExpPG) {
                        dev_final_value = ExpPG;
                        dev_final_reliability = 1;
                    } else {
                        dev_final_value = 1;
                        dev_final_reliability = 0.9;                    
                    }
                }

            } else {

                if ( (PredCAE == 0) && (PredPG == 0) ) {

                    // both negative
                    if ( (AdPG >= 2) || (AdCAE >= 2)) {

                        if (Zebra <= 0.5) {
                            if (ZebraExp) {
                                dev_final_value = 1;
                                dev_final_reliability = 0.2;                                                
                            } else {
                                dev_final_value = 0;
                                dev_final_reliability = 0.6;                    
                            }
                        } else {
                            dev_final_value = 0;
                            dev_final_reliability = 0.8;                    
                        }

                    } else {

                        if (Zebra <= 0.5) {

                            if ( (ZebraExp) ) {
                                dev_final_value = 1;
                                dev_final_reliability = 0.2;                    
                            } else if (OutZebra.getADI().GetAssessmentClass() >= 2) {
                                dev_final_value = 0;
                                dev_final_reliability = 0.2;                    
                            } else {
                                dev_final_value = 0;
                                dev_final_reliability = 0.3;                                                    
                            }                        

                        } else {

                            if ( (ZebraExp) || (OutZebra.getADI().GetAssessmentClass() >= 2) ) {
                                dev_final_value = 0;
                                dev_final_reliability = 0.6;                    
                            } else {
                                dev_final_value = 0;
                                dev_final_reliability = 0.3;                    
                            }

                        }

                    }

                } else {

                    if ( (PredCAE == 1) || (PredPG == 1) ) {

                        // both positive
                        if ( (Zebra <= 0.5) && (ZebraExp)) {
                            dev_final_value = 1;
                            dev_final_reliability = 0.9;                                            
                        } else if ((Zebra > 0.5) && (AdCAE == 1) && (AdPG == 1) )  {
                            dev_final_value = 1;
                            dev_final_reliability = 0.3;                                                                        
                        }  else if ((Zebra <= 0.5) && (AdCAE == 1) && (AdPG == 1) )  {
                            dev_final_value = 1;
                            dev_final_reliability = 0.6;                                                                        
                        }  else if ((AdCAE == 3) || (AdPG == 3) )  {
                            dev_final_value = 1;
                            dev_final_reliability = 0.8;                                                                        
                        }  else if ((Zebra <= 0.5) && ((AdCAE >= 2) || (AdPG >= 2)) )  {
                            dev_final_value = 1;
                            dev_final_reliability = 0.8;                                                                        
                        } else {
                            dev_final_value = 1;
                            dev_final_reliability = 0.6;                                                                                                    
                        }

                    } else {

                        // discordant or one missing
                        if (PredPG == 1) {
                            dev_final_value = 1;
                            dev_final_reliability = 0.6;                                            
                        } else if (PredCAE == 1) {
                            if (AdCAE == 3) {
                                dev_final_value = 1;
                                dev_final_reliability = 0.3;                                            
                            } else {
                                dev_final_value = 1;
                                dev_final_reliability = 0.2;
                            }
                        }
                    }

                }
            }
        }
        
        
        ////// calculation of score
        double dev_score;
        if (dev_final_value == 1)
            dev_score = 1;
        else if (dev_final_value == 0)
            dev_score = 0.1;
        else
            dev_score = 0.5;
        
        
        ////// result object
        Results.Prediction = (double)dev_final_value;
        Results.PredictionClass = dev_final_value == 1 ? "TOXICANT" : (dev_final_value == JanusResult.MISSING_VALUE ? "n.a." : "NON Toxicant");
        Results.Reliability = dev_final_reliability;
        Results.Score = JanusEngine.TransformScore(dev_score, Results.Reliability);
        Results.AdditionalValues.put("Developmental toxicity classification model (Caesar) prediction", OutCaesar.getAssessment());
        Results.AdditionalValues.put("DART classification model (Procter&Gamble) prediction", OutPG.getAssessment());
        Results.AdditionalValues.put("Zebrafish embryo AC50 (Coral) prediction", OutZebra.getAssessment());
        
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_REPRO))) 
            Results.AdditionalValues.put("User added experimental Reproductive Toxicity", 
                    (Experimental.getProperty(ExperimentalValues.KEY_REPRO))==1 ? "Toxicant" : "NON toxicant");
        
        
        return Results;
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