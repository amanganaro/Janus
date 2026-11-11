package insilico.janus.workflows;

import static insilico.core.ad.item.ADIndex.INDEX_HIGH;
import static insilico.core.ad.item.ADIndex.INDEX_LOW;
import static insilico.core.ad.item.ADIndex.INDEX_MEDIUM;

import insilico.carcinogenicity_antares.ismCarcinogenicityAntares;
import insilico.carcinogenicity_bb.ismCarcinogenicityBB;
import insilico.carcinogenicity_caesar.ismCarcinogenicityCaesar;
import insilico.carcinogenicity_isscancgx.ismCarcinogenicityIsscanCgx;
import insilico.carcinogenicity_sfi_classification.ismCarcinogenicitySFIClassification;
import insilico.carcinogenicity_sfiregression.ismCarcinogenicitySFIRegression;
import insilico.carcinogenicity_sfoclassification.ismCarcinogenicitySFOClassification;
import insilico.carcinogenicity_sforegression.ismCarcinogenicitySFORegression;
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
import insilico.janus.workflows.tools.POPClass;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Alberto
 */
public class JanusWorkflowCarcinogenicity implements iJanusWorkflow {

    private final ismCarcinogenicityAntares Antares;
    private final ismCarcinogenicityBB BB;
    private final ismCarcinogenicityCaesar Caesar;
    private final ismCarcinogenicityIsscanCgx Isscan;
    private final ismCarcinogenicitySFIClassification SFIclass;
    private final ismCarcinogenicitySFIRegression SFIreg;
    private final ismCarcinogenicitySFOClassification SFOclass;
    private final ismCarcinogenicitySFORegression SFOreg;
    
    private final InsilicoModelRunnerByMolecule Runner;
    
    private ExperimentalValues Experimental;
    
    
    public JanusWorkflowCarcinogenicity() throws InitFailureException {
        Experimental = null;
        
        Antares = new ismCarcinogenicityAntares();
        BB = new ismCarcinogenicityBB();
        Caesar = new ismCarcinogenicityCaesar();
        Isscan = new ismCarcinogenicityIsscanCgx();
        SFIclass = new ismCarcinogenicitySFIClassification();
        SFIreg = new ismCarcinogenicitySFIRegression();
        SFOclass = new ismCarcinogenicitySFOClassification();
        SFOreg = new ismCarcinogenicitySFORegression();
        
        
        // build the runner for multiple models
        Runner = new InsilicoModelRunnerByMolecule();
        Runner.AddModel(Antares);
        Runner.AddModel(BB);
        Runner.AddModel(Caesar);
        Runner.AddModel(Isscan);        
        Runner.AddModel(SFIclass);        
        Runner.AddModel(SFIreg);        
        Runner.AddModel(SFOclass);        
        Runner.AddModel(SFOreg);        
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
        InsilicoModelOutput OutAntares = Runner.GetModelWrapper(ismCarcinogenicityAntares.class).getResult().get(0);
        InsilicoModelOutput OutBB = Runner.GetModelWrapper(ismCarcinogenicityBB.class).getResult().get(0);
        InsilicoModelOutput OutCaesar = Runner.GetModelWrapper(ismCarcinogenicityCaesar.class).getResult().get(0);
        InsilicoModelOutput OutIsscan = Runner.GetModelWrapper(ismCarcinogenicityIsscanCgx.class).getResult().get(0);        
        InsilicoModelOutput OutSFIclass = Runner.GetModelWrapper(ismCarcinogenicitySFIClassification.class).getResult().get(0);
        InsilicoModelOutput OutSFIreg = Runner.GetModelWrapper(ismCarcinogenicitySFIRegression.class).getResult().get(0);
        InsilicoModelOutput OutSFOclass = Runner.GetModelWrapper(ismCarcinogenicitySFOClassification.class).getResult().get(0);
        InsilicoModelOutput OutSFOreg = Runner.GetModelWrapper(ismCarcinogenicitySFORegression.class).getResult().get(0);
        
        
        ////// main workflow
        double carc_final_class;
        double carc_final_value;
        double carc_final_reliability;

        
        // convert models
        HashMap<Integer, Integer> conversion;
                
        conversion = new HashMap();
        conversion.put(1, 1);
        conversion.put(0, 0);
        conversion.put(-1, 0); // possible non c
        ModelConvertedClassOutput oAntares = new ModelConvertedClassOutput(OutAntares, conversion);

        conversion = new HashMap();
        conversion.put(1, 1);
        conversion.put(0, 0);
        ModelConvertedClassOutput oBB = new ModelConvertedClassOutput(OutBB, conversion);

        conversion = new HashMap();
        conversion.put(1, 1);
        conversion.put(0, 0);
        ModelConvertedClassOutput oCaesar = new ModelConvertedClassOutput(OutCaesar, conversion);

        conversion = new HashMap();
        conversion.put(1, 1);
        conversion.put(0, 0);
        conversion.put(-1, 0); // possible non c
        ModelConvertedClassOutput oIsscan = new ModelConvertedClassOutput(OutIsscan, conversion);

        conversion = new HashMap();
        conversion.put(1, 1);
        conversion.put(0, 0);
        ModelConvertedClassOutput oSFIclass = new ModelConvertedClassOutput(OutSFIclass, conversion);
        
        conversion = new HashMap();
        conversion.put(1, 1);
        conversion.put(0, 0);
        ModelConvertedClassOutput oSFOclass = new ModelConvertedClassOutput(OutSFOclass, conversion);
        
        ModelConvertedQuantOutput oSFO = new ModelConvertedQuantOutput(OutSFOreg);
        ModelConvertedQuantOutput oSFI = new ModelConvertedQuantOutput(OutSFIreg);
        
            
        // check if all exp
        ArrayList<ModelConvertedClassOutput> classModels = new ArrayList<>();
        classModels.add(oAntares); classModels.add(oBB); classModels.add(oCaesar); 
        classModels.add(oIsscan); classModels.add(oSFIclass); classModels.add(oSFOclass); 

        int nExpPos=0, nExpNeg=0, nPredNeg=0, nPredPos=0;
        for (ModelConvertedClassOutput o : classModels) {
            if (o.experimental == 1) nExpPos++;
            if (o.experimental == 0) nExpNeg++;
            if (o.prediction == 1) nPredPos++;
            if (o.prediction == 0) nPredNeg++;
        }

        if (nExpPos + nExpNeg > 0) {

            // exp values in classification

            if ( (nExpNeg > 0) && (nExpPos == 0) ) {

                // all negative, concordant

                if ( (oSFI.hasExperimental()) || 
                     ( (oSFIclass.prediction == 1) && (oSFIclass.ADIClass == INDEX_HIGH) ) ) {

                    carc_final_class = 1;
                    carc_final_value = oSFI.hasExperimental() ? oSFI.experimental : oSFI.prediction;
                    carc_final_reliability = oSFI.hasExperimental() ? 5 : oSFI.ADIClass;

                } else {

                    carc_final_class = 0;
                    carc_final_value = JanusResult.MISSING_VALUE;
                    if ( (oSFIclass.prediction == 1) && ( (oSFIclass.ADIClass == INDEX_MEDIUM) || (oSFIclass.ADIClass == INDEX_LOW) ) )
                        carc_final_reliability = 4;
                    else
                        carc_final_reliability = 5;
                }

            } else {

                // at least one positive

                carc_final_class = 1;

                if ( (oSFI.hasExperimental()) && (oSFO.hasExperimental()) ) {
                    carc_final_value = oSFI.experimental > oSFO.experimental ? oSFI.experimental : oSFO.experimental;
                    carc_final_reliability = 5;                    
                }

                else if ( oSFI.hasExperimental() ) {
                    carc_final_value = oSFI.experimental;
                    carc_final_reliability = 5;                    
                }

                else if ( oSFO.hasExperimental() ) {
                    carc_final_value = oSFO.experimental;
                    carc_final_reliability = 5;                    
                }

                else {
                    carc_final_value = oSFI.prediction > oSFO.prediction ? oSFI.prediction : oSFO.prediction;
                    carc_final_reliability = 4;                    
                }

            }

        } else {

            // Prediction based

            double vA = (oCaesar.GetConvertedValue() + oAntares.GetConvertedValue()) / 2.0;
            double vB = (oBB.GetConvertedValue() + oIsscan.GetConvertedValue()) / 2.0;
            double vC = oSFOclass.GetConvertedValue();

            double wMean = 0.25 * vA + 0.35 * vB + 0.4 * vC;

            if (oSFIclass.GetConvertedValue() >= 0.7 ) {

                carc_final_class = 1;

                if (wMean >= 0.5) {

                    int MaxAdiSF = Math.max(oSFO.ADIClass, oSFI.ADIClass);
                    int MinAdiSF = Math.min(oSFO.ADIClass, oSFI.ADIClass);

                    if ( (MaxAdiSF >= 2) && (MinAdiSF == 1) ) {
                        if (oSFO.ADIClass > oSFI.ADIClass) {
                            carc_final_value = oSFO.prediction;
                            carc_final_reliability = oSFO.ADIClass;
                        } else {
                            carc_final_value = oSFI.prediction;
                            carc_final_reliability = oSFI.ADIClass;
                        }                        
                    } else {
                        if (oSFO.prediction > oSFI.prediction) {
                            carc_final_value = oSFO.prediction;
                            carc_final_reliability = oSFO.ADIClass;
                        } else {
                            carc_final_value = oSFI.prediction;
                            carc_final_reliability = oSFI.ADIClass;
                        }                        
                    }                    

                } else {

                    carc_final_value = oSFI.prediction;
                    carc_final_reliability = oSFI.ADIClass;

                }

            } else {

                if (wMean >= 0.5) {

                    carc_final_class = 1;
                    carc_final_value = oSFO.prediction;
                    carc_final_reliability = oSFO.ADIClass;

                } else {

                    carc_final_class = 0;
                    carc_final_value = JanusResult.MISSING_VALUE;

                    if (nPredNeg >= 4)
                        carc_final_reliability = 3;
                    else if (nPredNeg >= 2)
                        carc_final_reliability = 2;
                    else 
                        carc_final_reliability = 1;

                }

            }            
        }        
        
        // convert final reliability
        if (carc_final_reliability == 5) carc_final_reliability = 1;
        else if (carc_final_reliability == 4) carc_final_reliability = 0.9;
        else if (carc_final_reliability == 3) carc_final_reliability = 0.8;
        else if (carc_final_reliability == 2) carc_final_reliability = 0.6;
        else if (carc_final_reliability == 1) carc_final_reliability = 0.3;
        else carc_final_reliability = JanusResult.MISSING_VALUE;
            

        ////// calculation of score
        double carc_score;
        if (carc_final_class == 1) {
            // normalize SF value into range [0.5, 1]
            double NormSlopeFactor = 1.0 / (1.0 + Math.pow(0.5, carc_final_value));
            carc_score = 0.5 + 0.5 * NormSlopeFactor;
        } else if (carc_final_class == 0) {
            carc_score = 0.1;
        } else {
            carc_score = 0.5;
        }
        
        
        ////// result object
        DecimalFormatSymbols InternationalSymbols =
            new DecimalFormatSymbols();
        InternationalSymbols.setDecimalSeparator('.');         
        DecimalFormat Format_2D = new DecimalFormat("0.##", InternationalSymbols);

        Results.Prediction = carc_final_value;
        Results.PredictionClass = carc_final_class == 0 ? "NON Carcinogenic" : (carc_final_class == 1 ? "CARCINOGENIC (SF: " + Format_2D.format(carc_final_value) + ")" : "n.a.");
        Results.Reliability = carc_final_reliability;
        Results.Score = JanusEngine.TransformScore(carc_score, Results.Reliability);;
        Results.AdditionalValues.put("Carcinogenicity classification model (Antares) prediction", OutAntares.getAssessment());
        Results.AdditionalValues.put("Carcinogenicity classification model (Benigni-Bossa rulebase) prediction", OutBB.getAssessment());
        Results.AdditionalValues.put("Carcinogenicity classification model (Caesar) prediction", OutCaesar.getAssessment());
        Results.AdditionalValues.put("Carcinogenicity classification model (ISSCAN-CGX) prediction", OutIsscan.getAssessment());
        Results.AdditionalValues.put("Carcinogenicity classification model (Oral - IRFMN) prediction", OutSFIclass.getAssessment());
        Results.AdditionalValues.put("Carcinogenicity classification model (Inhalation - IRFMN) prediction", OutSFOclass.getAssessment());
        Results.AdditionalValues.put("Carcinogenicity slope factor model (Oral - IRFMN) prediction", OutSFOreg.getAssessment());
        Results.AdditionalValues.put("Carcinogenicity slope factor model (Inhalation - IRFMN) prediction", OutSFIreg.getAssessment());
        
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
    
    
    private class ModelConvertedClassOutput {
        public int prediction; // 0/1/mv
        public int ADIClass; // 3 high, 2 medium, 1 low, 0 n.a.
        public int experimental; // 0/1/mv
        
        public ModelConvertedClassOutput(InsilicoModelOutput model, HashMap<Integer, Integer> outputConversion) {
            prediction = JanusResult.MISSING_VALUE; 
            ADIClass = 0; 
            experimental = JanusResult.MISSING_VALUE;
            
            if (model.getStatus() == InsilicoModelOutput.OUTPUT_OK) {
                if (model.HasExperimental()) 
                    this.experimental = (int)model.getExperimental();
                
                int val = (int)model.getMainResultValue();
                if (outputConversion.containsKey(val))
                    this.prediction = outputConversion.get(val);
                
                this.ADIClass = model.getADI().GetAssessmentClass();
            }
        }
        
        public double GetConvertedValue() {
            if (prediction == 1) {
                switch (ADIClass) {
                    case 3: return 0.9; 
                    case 2: return 0.8; 
                    case 1: return 0.7; 
                    default: return 0.5;
                }
            }
            if (prediction == 0) {
                switch (ADIClass) {
                    case 3: return 0.1; 
                    case 2: return 0.2; 
                    case 1: return 0.3; 
                    default: return 0.5;
                }
            }
            return 0.5;
        }
        
        public boolean hasExperimental() {
            return (experimental != JanusResult.MISSING_VALUE);
        }
        
    }  
    
    private class ModelConvertedQuantOutput {
        public double prediction; // val/mv
        public int ADIClass; // 3 high, 2 medium, 1 low, 0 n.a.
        public double experimental; // val/mv
        
        public ModelConvertedQuantOutput(InsilicoModelOutput model) {
            prediction = JanusResult.MISSING_VALUE; 
            ADIClass = 0; 
            experimental = JanusResult.MISSING_VALUE;
            
            if (model.getStatus() == InsilicoModelOutput.OUTPUT_OK) {
                if (model.HasExperimental()) 
                    this.experimental = model.getExperimental();
                this.prediction = model.getMainResultValue();
                this.ADIClass = model.getADI().GetAssessmentClass();
            }
        }
        
        public boolean hasExperimental() {
            return (experimental != JanusResult.MISSING_VALUE);
        }
        
    } 
    
}
