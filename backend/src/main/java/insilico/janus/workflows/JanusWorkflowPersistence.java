package insilico.janus.workflows;

import insilico.core.exception.GenericFailureException;
import insilico.core.exception.InitFailureException;
import insilico.core.model.InsilicoModelOutput;
import insilico.core.model.runner.InsilicoModelRunnerByMolecule;
import insilico.core.molecule.InsilicoMolecule;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusResult;
import insilico.janus.JanusWorkflowResult;
import insilico.janus.iJanusWorkflow;
import insilico.janus.StandardizedModel;
import insilico.janus.workflows.tools.POPClass;
import insilico.persistence_quantative_water_irfmn.ismPersistenceWaterQuantitativeIrfmn;
import insilico.persistence_sediment_irfmn.ismPersistenceSedimentIrfmn;
import insilico.persistence_sediment_quantitative_irfmn.ismPersistenceSedimentQuantitativeIrfmn;
import insilico.persistence_soil_irfmn.ismPersistenceSoilIrfmn;
import insilico.persistence_soil_quantitative_irfmn.ismPersistenceSoilQuantitativeIrfmn;
import insilico.persistence_water_irfmn.ismPersistenceWaterIrfmn;
import insilico.readybio_irfmn.ismReadyBioIRFMN;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 *
 * @author Alberto
 */
public class JanusWorkflowPersistence implements iJanusWorkflow {

    private final ismReadyBioIRFMN ReadyBio;
    private final ismPersistenceSoilQuantitativeIrfmn SoilQuant;
    private final ismPersistenceWaterQuantitativeIrfmn WaterQuant;
    private final ismPersistenceSedimentQuantitativeIrfmn SedQuant;
    private final ismPersistenceSoilIrfmn SoilClass;
    private final ismPersistenceWaterIrfmn WaterClass;
    private final ismPersistenceSedimentIrfmn SedClass;
    
    private final InsilicoModelRunnerByMolecule Runner;
    
    private ExperimentalValues Experimental;
    private POPClass POPClassification;
    
    
    public JanusWorkflowPersistence() throws InitFailureException {
        Experimental = null;    
        POPClassification = null;
        
        ReadyBio = new ismReadyBioIRFMN();
        SoilQuant = new ismPersistenceSoilQuantitativeIrfmn();
        WaterQuant = new ismPersistenceWaterQuantitativeIrfmn();
        SedQuant = new ismPersistenceSedimentQuantitativeIrfmn();
        SoilClass = new ismPersistenceSoilIrfmn();
        WaterClass = new ismPersistenceWaterIrfmn();
        SedClass = new ismPersistenceSedimentIrfmn();
        
        // build the runner for multiple models
        Runner = new InsilicoModelRunnerByMolecule();
        Runner.AddModel(ReadyBio);
        Runner.AddModel(SoilQuant);
        Runner.AddModel(WaterQuant);
        Runner.AddModel(SedQuant);        
        Runner.AddModel(SoilClass);        
        Runner.AddModel(WaterClass);        
        Runner.AddModel(SedClass);        
    }
    
    
    @Override
    public JanusWorkflowResult Calculate(InsilicoMolecule mol) throws GenericFailureException {
        
        JanusWorkflowResult Results = new JanusWorkflowResult();
        
        
        ////// check if user added exp value
        //
        
        
        ////// run needed models
        try {
            Runner.Run(mol);
        } catch (Throwable e) {
            if (e.getClass() == OutOfMemoryError.class) 
                throw new GenericFailureException("A memory error occurred. Please check the user's guide for troubleshooting about memory usage.");
            else 
                throw new GenericFailureException("An unexpected error has occurred: " + e.getLocalizedMessage());                    
        }                      
        InsilicoModelOutput OutReadyBio = Runner.GetModelWrapper(ismReadyBioIRFMN.class).getResult().get(0);
        InsilicoModelOutput OutSoilQuant = Runner.GetModelWrapper(ismPersistenceSoilQuantitativeIrfmn.class).getResult().get(0);
        InsilicoModelOutput OutWaterQuant = Runner.GetModelWrapper(ismPersistenceWaterQuantitativeIrfmn.class).getResult().get(0);
        InsilicoModelOutput OutSedQuant = Runner.GetModelWrapper(ismPersistenceSedimentQuantitativeIrfmn.class).getResult().get(0);
        InsilicoModelOutput OutSoilClass = Runner.GetModelWrapper(ismPersistenceSoilIrfmn.class).getResult().get(0);
        InsilicoModelOutput OutWaterClass = Runner.GetModelWrapper(ismPersistenceWaterIrfmn.class).getResult().get(0);
        InsilicoModelOutput OutSedClass = Runner.GetModelWrapper(ismPersistenceSedimentIrfmn.class).getResult().get(0);
        
        ////// main workflow
        Double UserExpSoil = null;
        Double UserExpWat = null;
        Double UserExpSed = null;
        if (Experimental != null) {
            if (Experimental.hasProperty(ExperimentalValues.KEY_P_SOIL))
                UserExpSoil = Experimental.getProperty(ExperimentalValues.KEY_P_SOIL);
            if (Experimental.hasProperty(ExperimentalValues.KEY_P_WAT))
                UserExpWat = Experimental.getProperty(ExperimentalValues.KEY_P_WAT);
            if (Experimental.hasProperty(ExperimentalValues.KEY_P_SED))
                UserExpSed = Experimental.getProperty(ExperimentalValues.KEY_P_SED);
        }
        
        SingleCompartmentWorkflow wfSoil = new SingleCompartmentWorkflow(OutSoilClass, OutSoilQuant, 180, 120, OutReadyBio, UserExpSoil);
        SingleCompartmentWorkflow wfWater = new SingleCompartmentWorkflow(OutWaterClass, OutWaterQuant, 60, 40, OutReadyBio, UserExpWat);
        SingleCompartmentWorkflow wfSediment = new SingleCompartmentWorkflow(OutSedClass, OutSedQuant, 180, 120, OutReadyBio, UserExpSed);
        
        double MaxHL = JanusResult.MISSING_VALUE;
        String MaxHLClass = "n.a.";
        double MaxRel = 0;
        double PC = 0; // number of compartments in the same class of the final prediction
        
        // If there is a SMARTS matching, uses its output as prediction
        if ( (POPClassification != null) && (POPClassification.PFAS)) {

            // PFAS POP Screening class
            MaxHL = 2.8;
            MaxHLClass = "vP";            
            MaxRel = 0.8;
            PC = 3; // fake total concordance
            
        } 
        
        // Otherwise process workflows output
        else {

            // Get max prediction and class
            if (wfSoil.normalizedPrediction > MaxHL) {
                MaxHL = wfSoil.normalizedPrediction;
                MaxHLClass = wfSoil.predictionClass;
            }
            if (wfWater.normalizedPrediction > MaxHL) {
                MaxHL = wfWater.normalizedPrediction;
                MaxHLClass = wfWater.predictionClass;
            }
            if (wfSediment.normalizedPrediction > MaxHL) {
                MaxHL = wfSediment.normalizedPrediction;
                MaxHLClass = wfSediment.predictionClass;
            }

            if (MaxHL == JanusResult.MISSING_VALUE) {

                MaxHLClass = "n.a.";

            } else {

                // Get max reliability and PC value
                if (wfSoil.predictionClass.equalsIgnoreCase(MaxHLClass)) {
                    MaxRel = wfSoil.reliability > MaxRel ? wfSoil.reliability : MaxRel;
                    PC++;
                }
                if (wfWater.predictionClass.equalsIgnoreCase(MaxHLClass)) {
                    MaxRel = wfWater.reliability > MaxRel ? wfWater.reliability : MaxRel;
                    PC++;
                }
                if (wfSediment.predictionClass.equalsIgnoreCase(MaxHLClass)) {
                    MaxRel = wfSediment.reliability > MaxRel ? wfSediment.reliability : MaxRel;
                    PC++;
                }
                
                // Adjust reliability
                MaxRel = (0.90 * MaxRel) + (0.10 * (PC / 3.0) );
            }

        }
        
        ////// calculation of score
        double p_score;
        
        if (MaxHL == JanusResult.MISSING_VALUE) {
            p_score = 0.5;
        } else {
            if (MaxHL < 0.3) {
                // linear
                p_score = (MaxHL / 0.3) * ( 1/(1+Math.exp(-3*(0.3-1))) );
            } else if (MaxHL < 1) {
                p_score = 1/(1+Math.exp(-3*(MaxHL-1)));
            } else if (MaxHL < 2.7) {
                p_score = 1/(1+Math.exp(-2*(MaxHL-1)));
            } else {
                // linear
                p_score = (1/(1+Math.exp(-2*(2.7-1)))) + ( (MaxHL-2.7)/(3.0-2.7) ) * (1 - (1/(1+Math.exp(-2*(2.7-1)))));
            }
        }
        
        
        ////// result object
        Results.Prediction = MaxHL;
        Results.PredictionClass = MaxHLClass;        
        Results.Reliability = MaxRel>1 ? 1 : MaxRel;
        Results.Score = JanusEngine.TransformScore(p_score, Results.Reliability);
        Results.AdditionalValues.put("Ready biodegradability model", OutReadyBio.getAssessment());
        Results.AdditionalValues.put("Quantitative persistence (water) model", OutWaterQuant.getAssessment());
        Results.AdditionalValues.put("Quantitative persistence (sediment) model", OutSedQuant.getAssessment());
        Results.AdditionalValues.put("Quantitative persistence (soil) model", OutSoilQuant.getAssessment());
        Results.AdditionalValues.put("Qualitative persistence (water) model", OutWaterClass.getAssessment());
        Results.AdditionalValues.put("Qualitative persistence (sediment) model", OutSedClass.getAssessment());
        Results.AdditionalValues.put("Qualitative persistence (soil) model", OutSoilClass.getAssessment());

        DecimalFormatSymbols InternationalSymbols =
            new DecimalFormatSymbols();
        InternationalSymbols.setDecimalSeparator('.');         
        DecimalFormat Format_2D = new DecimalFormat("0.##", InternationalSymbols);
        DecimalFormat Format_0D = new DecimalFormat("0", InternationalSymbols);

        Results.AdditionalValues.put("Overall persistence (water) assessment (used in the workflow)", wfWater.predictionClass + " - " +
                Format_0D.format(wfWater.prediction) + " days (reliability: " + Format_2D.format((wfWater.reliability)>1?1:(wfWater.reliability)) + ")" );
        Results.AdditionalValues.put("Overall persistence (sediment) assessment (used in the workflow)", wfSediment.predictionClass + " - " +
                Format_0D.format(wfSediment.prediction) + " days (reliability: " + Format_2D.format((wfSediment.reliability)>1?1:(wfSediment.reliability)) + ")" );
        Results.AdditionalValues.put("Overall persistence (soil) assessment (used in the workflow)", wfSoil.predictionClass + " - " +
                Format_0D.format(wfSoil.prediction) + " days (reliability: " + Format_2D.format((wfSoil.reliability)>1?1:(wfSoil.reliability)) + ")" );
        
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_P_SOIL))) 
            Results.AdditionalValues.put("User added experimental persistence in soil (half-life expressed in days)", 
                    Format_0D.format(Experimental.getProperty(ExperimentalValues.KEY_P_SOIL)));
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_P_SED))) 
            Results.AdditionalValues.put("User added experimental persistence in sediment (half-life expressed in days)", 
                    Format_0D.format(Experimental.getProperty(ExperimentalValues.KEY_P_SED)));
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_P_WAT))) 
            Results.AdditionalValues.put("User added experimental persistence in water (half-life expressed in days)", 
                    Format_0D.format(Experimental.getProperty(ExperimentalValues.KEY_P_WAT)));
        
        if ( (POPClassification != null) && (POPClassification.PFAS))            
            Results.AdditionalValues.put("Screening classes match", "PFAS (class vP, reliability 0.8)");
        
        return Results;
    }
    

    @Override
    public void SetExperimentalValue(ExperimentalValues ExpVal) {
        this.Experimental = ExpVal;
    }

    
    @Override
    public void SetPOPClass(POPClass POP) {
        this.POPClassification = POP;
    }
    
    
    private class SingleCompartmentWorkflow {
        
        public double prediction;
        public double normalizedPrediction;
        public String predictionClass;
        public double reliability;
        
        
        public SingleCompartmentWorkflow(InsilicoModelOutput ClassModel, 
                InsilicoModelOutput QuantModel, double ThresholdHigh, 
                double ThresholdLow, InsilicoModelOutput RBModel,
                Double ExpValue) {
            
            prediction = JanusResult.MISSING_VALUE;
            normalizedPrediction = JanusResult.MISSING_VALUE;
            predictionClass = "";
            reliability = JanusResult.MISSING_VALUE;

            if (ExpValue != null) {
                
                // Experimental value given by the user is available
                prediction = Math.round(Math.pow(10, ExpValue));
                reliability = 1;

                // Convert quantitative prediction into qualitative class 
                if (prediction < ThresholdLow) predictionClass = "nP";
                else if (prediction < ThresholdHigh) predictionClass = "P";
                else predictionClass = "vP";     
                
            } else {
                
                // Only VEGA models are used
                
                // If no quantitative value is available, NO OUTPUT
                // CONTROLLARE SE GIUSTO
                if (QuantModel.getStatus() != InsilicoModelOutput.OUTPUT_OK) {
                    return;
                }
                
                // Process quantitative value
                StandardizedModel quantitativeIR = new StandardizedModel(QuantModel);
                prediction = Math.round(Math.pow(10, quantitativeIR.Prediction));
                double ContinuosReliability = quantitativeIR.Reliability;
                
                // Concordance (fixed as we have only one quantitative value)
                double C = ContinuosReliability - 0.3;
                
                // Partial reliability
                double R1 = (ContinuosReliability + C) / 2.0;
                
                // Convert quantitative prediction into qualitative class 
                if (prediction < ThresholdLow) predictionClass = "nP";
                else if (prediction < ThresholdHigh) predictionClass = "P";
                else predictionClass = "vP";     
                
                // HL score
                int HL_class = 5; // worst case as default

                StandardizedModel qualitativeIR = new StandardizedModel(ClassModel);
                if (qualitativeIR.Prediction != JanusResult.MISSING_VALUE) {
                    if (predictionClass.equalsIgnoreCase("nP")) {
                        if (qualitativeIR.Prediction == 1) HL_class = 1;
                        else if (qualitativeIR.Prediction == 2) HL_class = 2;
                        else if (qualitativeIR.Prediction == 3) HL_class = 3;
                        else if (qualitativeIR.Prediction == 4) HL_class = 4;
                    }
                    if (predictionClass.equalsIgnoreCase("P")) {
                        if (qualitativeIR.Prediction == 1) HL_class = 3;
                        else if (qualitativeIR.Prediction == 2) HL_class = 2;
                        else if (qualitativeIR.Prediction == 3) HL_class = 2;
                        else if (qualitativeIR.Prediction == 4) HL_class = 3;
                    }
                    if (predictionClass.equalsIgnoreCase("vP")) {
                        if (qualitativeIR.Prediction == 1) HL_class = 4;
                        else if (qualitativeIR.Prediction == 2) HL_class = 3;
                        else if (qualitativeIR.Prediction == 3) HL_class = 2;
                        else if (qualitativeIR.Prediction == 4) HL_class = 1;
                    }                    
                }

                double HL;
                if (HL_class == 5) {
                    
                    // no correction
                    HL = 0;
                
                } else {
                
                    // experimental value for class model
                    if ( (quantitativeIR.Reliability == 1) && (qualitativeIR.Reliability == 1) ) {
                        switch (HL_class) {
                            case 1: HL = 0.2; break;
                            case 2: HL = 0.1; break;
                            case 3: HL = -0.1; break;
                            case 4: HL = -0.2; break;
                            default: HL = 0;
                        }
                    }
                    
                    // experimental value for quant model and good reliability
                    // for class model
                    else if ( (quantitativeIR.Reliability == 1) && (qualitativeIR.Reliability == 0.8) ) {
                        switch (HL_class) {
                            case 1: HL = 0.15; break;
                            case 2: HL = 0.05; break;
                            case 3: HL = -0.05; break;
                            case 4: HL = -0.15; break;
                            default: HL = 0;
                        }
                    }

                    // experimental value for class model and prediction for quant
                    else if ( (quantitativeIR.Reliability < 1) && (qualitativeIR.Reliability == 1) ) {
                        switch (HL_class) {
                            case 1: HL = 0.2; break;
                            case 2: HL = 0.1; break;
                            case 3: HL = -0.1; break;
                            case 4: HL = -0.2; break;
                            default: HL = 0;
                        }
                    }

                    else if ( (qualitativeIR.Reliability != 1) && (quantitativeIR.Reliability != 1) ) {
                        
                        // both prediction, class model reliability > quant model reliability
                        if (qualitativeIR.Reliability > quantitativeIR.Reliability) {
                            switch (HL_class) {
                                case 1: HL = 0.15; break;
                                case 2: HL = 0.05; break;
                                case 3: HL = -0.05; break;
                                case 4: HL = -0.15; break;
                                default: HL = 0;
                            }
                        }

                        // both prediction, quant model reliability >= class model reliability
                        else {
                            switch (HL_class) {
                                case 1: HL = 0.1; break;
                                case 2: HL = 0; break;
                                case 3: HL = 0; break;
                                case 4: HL = -0.1; break;
                                default: HL = 0;
                            }
                        }
                        
                    }
                    
                    // remaining cases
                    else {
                        HL = 0;
                    }
                    
                }
                
                // RB score
                StandardizedModel rbIR = new StandardizedModel(RBModel);
                double RB;
                
                // experimental RB value
                if ( (rbIR.Reliability == 1) && (rbIR.Prediction == 0) ) {
                    if (predictionClass.equalsIgnoreCase("nP")) 
                        RB = 0.2;
                    else
                        RB = -0.2;                    
                }
                
                // RB reliabile prediction 
                else if ( (rbIR.Reliability == 0.8) && (rbIR.Prediction == 0) ) {
                    if (predictionClass.equalsIgnoreCase("nP")) 
                        RB = 0.1;
                    else
                        RB = -0.1;                    
                }
                
                // possible RB reliabile prediction 
                else if ( (rbIR.Reliability == 0.8) && (rbIR.Prediction == 3) ) {
                    if (predictionClass.equalsIgnoreCase("nP")) 
                        RB = 0.05;
                    else
                        RB = -0.05;                    
                }

                // all other cases, no correction
                else
                    RB = 0;
                
                // Final reliability
                reliability = R1 + HL + RB;
                
                // Adjust value
                if (quantitativeIR.Reliability == 1)
                    reliability = reliability > 1 ? 1 : reliability;
                else
                    reliability = reliability > 0.98 ? 0.98 : reliability;
                
            }
            
            // normalization of prediction value
            // calculate normalized prediction
            double min=0, max=0;
            if (predictionClass.equalsIgnoreCase("nP")) {
                min = 0;
                max = ThresholdLow - 0.01;
            }
            if (predictionClass.equalsIgnoreCase("P")) {
                min = ThresholdLow;
                max = ThresholdHigh - 0.01;
            }
            if (predictionClass.equalsIgnoreCase("vP")) {
                min = ThresholdHigh;
                max = 2500;
            }
            double predWithCeil = prediction > 2500 ? 2500 : prediction;
            double norm = (predWithCeil - min) / (max - min);
            if (predictionClass.equalsIgnoreCase("nP")) 
                normalizedPrediction = norm;
            else if (predictionClass.equalsIgnoreCase("P")) 
                normalizedPrediction = norm + 1;
            else
                normalizedPrediction = norm + 2;            
         
        }
    }
    
}
