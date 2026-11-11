package insilico.janus.workflows;

import insilico.core.exception.GenericFailureException;
import insilico.core.exception.InitFailureException;
import insilico.core.exception.InvalidMoleculeException;
import insilico.core.model.InsilicoModelOutput;
import insilico.core.model.runner.InsilicoModelRunnerByMolecule;
import insilico.core.molecule.InsilicoMolecule;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusWorkflowResult;
import insilico.janus.iJanusWorkflow;
import insilico.janus.workflows.tools.EndocrineBindingCombination;
import insilico.janus.workflows.tools.EndocrineECList;
import insilico.janus.workflows.tools.EndocrineECSmarts;
import insilico.janus.workflows.tools.POPClass;

import java.io.IOException;

import insilico.rba_cerapp.ismEstrogenBindingCerapp;
import insilico.rba_compara_irfmn.ismAndrogenBindingComparaIRFMN;
import insilico.rba_irfmn.ismRbaIRFMN;
import org.apache.commons.math3.stat.descriptive.moment.Mean;

/**
 *
 * @author Alberto
 */
public class JanusWorkflowEndocrine implements iJanusWorkflow {

    private final ismRbaIRFMN EstrogenIRFMN;
    private final ismEstrogenBindingCerapp EstrogenCerapp;
    private final ismAndrogenBindingComparaIRFMN AndrogenCompara;
    
    private final EndocrineECList ECList;
    private final EndocrineECSmarts ECSmarts;
    private final EndocrineBindingCombination BindingComb;
    
    private final InsilicoModelRunnerByMolecule Runner;
    
    private ExperimentalValues Experimental;
    
    
    public JanusWorkflowEndocrine() throws InitFailureException {
        Experimental = null;
        
        try {
            ECList = new EndocrineECList();
            ECSmarts = new EndocrineECSmarts();
        } catch (IOException ex) {
            throw new InitFailureException("Unable to init EC list - " + ex.getLocalizedMessage());
        }
        
        try {
            BindingComb = new EndocrineBindingCombination();
        } catch (Exception ex) {
            throw new InitFailureException("Unable to init binding combination table - " + ex.getLocalizedMessage());
        }
        
        EstrogenIRFMN = new ismRbaIRFMN();
        EstrogenCerapp = new ismEstrogenBindingCerapp();
        AndrogenCompara = new ismAndrogenBindingComparaIRFMN();        
        
        // build the runner for multiple models
        Runner = new InsilicoModelRunnerByMolecule();
        Runner.AddModel(EstrogenIRFMN);
        Runner.AddModel(EstrogenCerapp);        
        Runner.AddModel(AndrogenCompara);        
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
        InsilicoModelOutput OutEstrogenIRFMN = Runner.GetModelWrapper(ismRbaIRFMN.class).getResult().get(0);
        InsilicoModelOutput OutEstrogenCerapp = Runner.GetModelWrapper(ismEstrogenBindingCerapp.class).getResult().get(0);        
        InsilicoModelOutput OutAndrogenCompara = Runner.GetModelWrapper(ismAndrogenBindingComparaIRFMN.class).getResult().get(0);        
        
        // EC List matching
        boolean OutECListFound = false;
        String OutECListAndSmarts = "";
        
        try {
            InsilicoMolecule ECListMolecule = ECList.Match(mol);
            if (ECListMolecule != null) {
                OutECListFound = true;
                OutECListAndSmarts = "Exact match: " + ECListMolecule.GetId();
            } else {
                OutECListFound = false;
                OutECListAndSmarts = "No matches";                
            }
        } catch (InvalidMoleculeException ex) {
            OutECListFound = false;
            OutECListAndSmarts = "[Error during matching]";
        }
        
        // EC SMARTS matching
        boolean OutECSMARTSFound = false;
        
        if (!OutECListFound) {
            String res = ECSmarts.Match(mol);
            if (res != null) {
                OutECSMARTSFound = true;
                OutECListAndSmarts = "Match in the category: " + res;
            }
        }
        
        
        ////// main workflow
        double ed_activity = 0.5;
        double ed_reliability = 0;
        
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_ED))) {

            // user added exp        
            ed_activity = Experimental.getProperty(ExperimentalValues.KEY_ED);
            ed_reliability = 0;
            
        } else {

            short E_RME = -1, E_RBA = -1, A_RME = -1; // -1 non pred, 0 inactive, 1 active
            short E_RME_rel = 0, E_RBA_rel = 0, A_RME_rel = 0;
            // reliability: 4 exp, 3 high, 2 medium, 1 low, 0 n.a.

            // Check and convert the values for androgen and estrogen models
            if (OutAndrogenCompara.getStatus() == InsilicoModelOutput.OUTPUT_OK) {
                if (OutAndrogenCompara.HasExperimental()) {
                    A_RME = (short)OutAndrogenCompara.getExperimental();
                    A_RME_rel = 4;
                } else {
                    A_RME = (short)OutAndrogenCompara.getMainResultValue();
                    A_RME_rel = OutAndrogenCompara.getADI().GetAssessmentClass();
                }
            }

            if (OutEstrogenCerapp.getStatus() == InsilicoModelOutput.OUTPUT_OK) {
                if (OutEstrogenCerapp.HasExperimental()) {
                    E_RME = (short)OutEstrogenCerapp.getExperimental() == 2 ? (short)1 : (short)0;
                    E_RME_rel = 4;
                } else {
                    switch((short)OutEstrogenCerapp.getMainResultValue()) {
                        case -1: E_RME = 0; break; // not predicted -> inactive
                        case 0: E_RME = 0; break;
                        case 1: E_RME = 0; break;
                        case 2: E_RME = 1; break;
                        case 3: E_RME = 1; break;
                    }
                    E_RME_rel = OutEstrogenCerapp.getADI().GetAssessmentClass();
                }
            }

            if (OutEstrogenIRFMN.getStatus() == InsilicoModelOutput.OUTPUT_OK) {
                if (OutEstrogenIRFMN.HasExperimental()) {
                    E_RBA = (short)OutEstrogenIRFMN.getExperimental();
                    E_RBA_rel = 4;
                } else {
                    E_RBA = (short)OutEstrogenIRFMN.getMainResultValue();
                    E_RBA_rel = OutEstrogenIRFMN.getADI().GetAssessmentClass();
                }
            }


            
            if (OutECListFound) {

                // Found matching in the official EC list
                
                ed_activity = 1.0;
                ed_reliability = 1.0; // exp

            } else if (OutECSMARTSFound) {
                
                // Found matching in the SMARTS list

                ed_activity = 1.0;
                ed_reliability = 0.8; // high

            } else {

                // Matching with the combination table
                
                double RCcode = -1;
                
                for (double[] entry : BindingComb.Entries) {
                    
                    if ( (E_RBA == entry[EndocrineBindingCombination.E_RBA]) &&
                         ((E_RBA_rel == 4 ? 1 : 0) == entry[EndocrineBindingCombination.E_RBA_ISEXP]) &&
                         (E_RME == entry[EndocrineBindingCombination.E_RME]) &&
                         ((E_RME_rel == 4 ? 1 : 0) == entry[EndocrineBindingCombination.E_RME_ISEXP]) &&
                         (A_RME == entry[EndocrineBindingCombination.A_RME]) &&
                         ((A_RME_rel == 4 ? 1 : 0) == entry[EndocrineBindingCombination.A_RME_ISEXP]) ) {
                        
                        ed_activity = entry[EndocrineBindingCombination.RV];
                        
                        double E_RME_rel_conv = ConvertAD(E_RME_rel);
                        double E_RBA_rel_conv = ConvertAD(E_RBA_rel);
                        double A_RME_rel_conv = ConvertAD(A_RME_rel);
                        
                        double CF = 0; /////////////////////////////////

                        Mean mean = new Mean();
                        double buf[] = new double[2];
                    
                            if ( (E_RBA==0) && (E_RME==0) && (A_RME==0) ) {

                                ed_activity = 0.1; // inactive
                                buf[0] = Math.max(E_RBA_rel_conv, E_RME_rel_conv);
                                buf[1] = A_RME_rel_conv;
                                ed_reliability = mean.evaluate(buf);                                                    
                            }

                        RCcode = entry[EndocrineBindingCombination.RC_CODE];
                        if (RCcode == 1) 
                            ed_reliability = 1;
                        if (RCcode == 2)
                            ed_reliability = A_RME_rel_conv;
                        if (RCcode == 3)
                            ed_reliability = E_RBA_rel_conv;
                        if (RCcode == 4)
                            ed_reliability = E_RBA_rel_conv - CF;
                        if (RCcode == 5)
                            ed_reliability = E_RME_rel_conv - CF;
                        if (RCcode == 6)
                            ed_reliability = Math.max(E_RBA_rel_conv, E_RME_rel_conv);
                        if (RCcode == 7) {
                            buf[0] = E_RBA_rel_conv - CF;
                            buf[1] = A_RME_rel_conv;
                            ed_reliability = mean.evaluate(buf);
                        }
                        if (RCcode == 8) {
                            buf[0] = E_RBA_rel_conv;
                            buf[1] = A_RME_rel_conv;
                            ed_reliability = mean.evaluate(buf);
                        }
                        if (RCcode == 9) {
                            buf[0] = E_RME_rel_conv - CF;
                            buf[1] = A_RME_rel_conv;
                            ed_reliability = mean.evaluate(buf);
                        }
                        if (RCcode == 10) {
                            buf[0] = 1.0;
                            buf[1] = A_RME_rel_conv;
                            ed_reliability = mean.evaluate(buf);
                        }
                        if (RCcode == 11) {
                            buf[0] = Math.max(E_RBA_rel_conv, E_RME_rel_conv);
                            buf[1] = A_RME_rel_conv;
                            ed_reliability = mean.evaluate(buf);
                        }
                        if (RCcode == 12) {
                            ed_reliability = Math.max(Math.max(E_RBA_rel_conv, E_RME_rel_conv),A_RME_rel_conv);
                        }
                        
                    }
                    
                }
                
                
                
//                
//                // Check the values from receptor bindings models
//                
//                if ( (A_RME_rel == 4) || (E_RME_rel == 4) || (E_RBA_rel == 4)) {
//
//                    // At least one exp value from the models
//                    
//                    
//                    // RBA exp active
//                    if ((E_RBA==1) && (E_RBA_rel==4)) {
//                        
//                        // RME exp active
//                        if ((E_RME==1) && (E_RME_rel==4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = 1;                                
//                            }
//                        }
//                        
//                        // RME pred active
//                        else if ((E_RME==1) && (E_RME_rel!=4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = 1;                                
//                            }                            
//                        }
//                        
//                        // RME exp inactive
//                        else if ((E_RME==0) && (E_RME_rel==4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = 1;                                
//                            }                            
//                        }
//                        
//                        // RME pred inactive OR not predicted
//                        else if ( ((E_RME==0) && (E_RME_rel!=4)) || (E_RME==-1) ) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = 1;                                
//                            }                            
//                        }
//                        
//                    }
//                    
//                    
//                    // RBA pred active
//                    else if ((E_RBA==1) && (E_RBA_rel!=4)) {
//                        
//                        // RME exp active
//                        if ((E_RME==1) && (E_RME_rel==4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = 1;                                
//                            }                            
//                        }
//                        
//                        // RME pred active
//                        else if ((E_RME==1) && (E_RME_rel!=4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = ConvertAD((short)Math.max(E_RBA_rel, E_RME_rel));                                
//                            }                                                        
//                        }
//                        
//                        // RME exp inactive
//                        else if ((E_RME==0) && (E_RME_rel==4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.8; // active
//                                if (A_RME_rel==4)
//                                    ed_reliability = 1;
//                                else
//                                    ed_reliability = ConvertAD(A_RME_rel);
//                            } else {
//                                ed_activity = 0.1; // inactive
//                                if (A_RME_rel==4)
//                                    ed_reliability = 1;
//                                else
//                                    ed_reliability = (1.0 + ConvertAD(A_RME_rel)) / 2.0;
//                            }                                                        
//                        }
//                        
//                        // RME pred inactive OR not predicted
//                        else if ( ((E_RME==0) && (E_RME_rel!=4)) || (E_RME==-1) ) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = ConvertAD(E_RBA_rel);                                
//                            }
//                        }                        
//                    }
//                    
//                    
//                    // RBA exp inactive
//                    else if ((E_RBA==0) && (E_RBA_rel==4)) {
//
//                        // RME exp active
//                        if ((E_RME==1) && (E_RME_rel==4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = 1;
//                            }
//                        }
//                        
//                        // RME pred active
//                        // RME exp inactive
//                        // RME pred inactive OR not predicted
//                        else {
//                            if (A_RME==1) {
//                                ed_activity = 0.8; // active
//                                if (A_RME_rel==4)
//                                    ed_reliability = 1;
//                                else
//                                    ed_reliability = ConvertAD(A_RME_rel);
//                            } else {
//                                ed_activity = 0.1; // active
//                                if (A_RME_rel==4)
//                                    ed_reliability = 1;
//                                else
//                                    ed_reliability = (1.0 + ConvertAD(A_RME_rel)) / 2.0;
//                            }
//                        }
//                    }
//                    
//                    
//                    // RBA pred inactive
//                    else if ((E_RBA==0) && (E_RBA_rel!=4)) {
//                        
//                        // RME exp active
//                        if ((E_RME==1) && (E_RME_rel==4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = 1;
//                            }                            
//                        }
//                        
//                        // RME pred active
//                        else if ((E_RME==1) && (E_RME_rel!=4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.9; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.8; // active
//                                ed_reliability = ConvertAD(E_RME_rel);
//                            }                            
//                        }
//                        
//                        // RME exp inactive
//                        else if ((E_RME==0) && (E_RME_rel==4)) {
//                            if (A_RME==1) {
//                                ed_activity = 0.8; // active
//                                if (A_RME_rel==4)
//                                    ed_reliability = 1;
//                                else
//                                    ed_reliability = ConvertAD(A_RME_rel);
//                            } else {
//                                ed_activity = 0.1; // inactive
//                                if (A_RME_rel==4)
//                                    ed_reliability = 1;
//                                else
//                                    ed_reliability = (1.0 + ConvertAD(A_RME_rel)) / 2.0;
//                            }                            
//                        }
//                        
//                        // RME pred inactive OR not predicted
//                        else if ( ((E_RME==0) && (E_RME_rel!=4)) || (E_RME==-1) ) {
//                            if (A_RME==1) {
//                                ed_activity = 0.8; // active
//                                ed_reliability = 1;
//                            } else {
//                                ed_activity = 0.1; // inactive
//                                ed_reliability = (Math.max(ConvertAD(E_RBA_rel), ConvertAD(E_RME_rel)) + ConvertAD(A_RME_rel)) / 2.0;
//                            }                            
//                        }                        
//                    }
//                    
//                } else {
//                    
//                    // no exp, evaluate predictions
//                    
//                    double E_RME_rel_conv = ConvertAD(E_RME_rel);
//                    double E_RBA_rel_conv = ConvertAD(E_RBA_rel);
//                    double A_RME_rel_conv = ConvertAD(A_RME_rel);
//                    
//                    Mean mean = new Mean();
//                    double buf[] = new double[2];
//                    
//                    if ( (E_RBA==0) && (E_RME==0) && (A_RME==0) ) {
//                        
//                        ed_activity = 0.1; // inactive
//                        buf[0] = Math.max(E_RBA_rel_conv, E_RME_rel_conv);
//                        buf[1] = A_RME_rel_conv;
//                        ed_reliability = mean.evaluate(buf);                                                    
//                        
//                    } else if ( (E_RBA==1) && (E_RME==1) && (A_RME==1) ) {
//                        
//                        ed_activity = 0.9; // active
//                        buf[0] = Math.max(E_RBA_rel_conv, E_RME_rel_conv);
//                        buf[1] = A_RME_rel_conv;
//                        ed_reliability = mean.evaluate(buf);                                                    
//                        
//                    } else if ( (E_RBA==1) && (E_RME==1) && (A_RME==0) ) {
//                        
//                        ed_activity = 0.8; // active
//                        ed_reliability = Math.max(E_RBA_rel_conv, E_RME_rel_conv);                                                   
//                        
//                    } else if ( (E_RBA==0) && (E_RME==0) && (A_RME==1) ) {
//                        
//                        ed_activity = 0.8; // active
//                        ed_reliability = A_RME_rel_conv;                                                    
//                        
//                    } else if ( (E_RBA==0) && (E_RME==1) && (A_RME==1) ) {
//                        
//                        ed_activity = 0.9; // active
//                        buf[0] = E_RME_rel_conv - CalculateCF(E_RME_rel);
//                        buf[1] = A_RME_rel_conv;
//                        ed_reliability = mean.evaluate(buf);                                                    
//                        
//                    } else if ( (E_RBA==1) && (E_RME==0) && (A_RME==1) ) {
//                        
//                        ed_activity = 0.9; // active
//                        buf[0] = E_RBA_rel_conv - CalculateCF(E_RBA_rel);
//                        buf[1] = A_RME_rel_conv;
//                        ed_reliability = mean.evaluate(buf);                                                    
//                        
//                    } else if ( (E_RBA==1) && (E_RME==0) && (A_RME==0) ) {
//                        
//                        ed_activity = 0.8; // active
//                        ed_reliability = E_RBA_rel_conv;                                                  
//                        
//                    } else if ( (E_RBA==0) && (E_RME==1) && (A_RME==0) ) {
//                        
//                        ed_activity = 0.8; // active
//                        ed_reliability = E_RME_rel_conv;                                                    
//                        
//                    }
//                    
//                }
                

            }            
            
        }

        ////// result object   
        String final_class = "";
        if (ed_activity > 0.5)
            final_class = "ACTIVE";
        else if (ed_activity < 0.5)
            final_class = "Inactive";
        else
            final_class = "n.a.";
        
        Results.Prediction = ed_activity;
        Results.PredictionClass = final_class;
        Results.Reliability = ed_reliability;
        Results.Score = JanusEngine.TransformScore(ed_activity, Results.Reliability);;
        Results.AdditionalValues.put("Androgen Receptor-mediated effect (IRFMN/COMPARA) prediction", OutAndrogenCompara.getAssessment());
        Results.AdditionalValues.put("Estrogen Receptor Relative Binding Affinity model (IRFMN) prediction", OutEstrogenIRFMN.getAssessment());
        Results.AdditionalValues.put("Estrogen Receptor-mediated effect (IRFMN/CERAPP) prediction", OutEstrogenCerapp.getAssessment());
        Results.AdditionalValues.put("EC endocrine disruptor candidate list matching", OutECListAndSmarts);
        
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
        
    private double ConvertAD(short ADclass) {
        
        switch (ADclass) {
            case 4:
                return 1; 
            case 3:
                return 0.8; 
            case 2:
                return 0.6; 
            case 1:
                return 0.3; 
        }
        
        return 0;
    }
    
    private double CalculateCF(short ADclass) {
        switch (ADclass) {
            case 3:
                return 0.15; 
            case 2:
                return 0.1; 
            case 1:
                return 0.05; 
        }
        
        return 0;
    }
    
}