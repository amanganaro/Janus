package insilico.janus.workflows;

import insilico.algae_ec50.ismAlgaeEC50;
import insilico.algae_noec.ismAlgaeNOEC;
import insilico.core.exception.GenericFailureException;
import insilico.core.exception.InitFailureException;
import insilico.core.model.InsilicoModelOutput;
import insilico.core.model.runner.InsilicoModelRunnerByMolecule;
import insilico.core.molecule.InsilicoMolecule;
import insilico.daphnia_demetra.ismDaphniaDemetra;
import insilico.daphnia_ec50.ismDaphniaEC50;
import insilico.daphnia_epa.ismDaphniaEPA;
import insilico.daphnia_noec.ismDaphniaNOEC;
import insilico.fathead_epa.ismFatheadEPA;
import insilico.fathead_knn.ismFatheadKnn;
import insilico.fish_knn.ismFishKnn;
import insilico.fish_lc50.ismFishLC50;
import insilico.fish_nic.ismFishNic;
import insilico.fish_noec.ismFishNOEC;
import insilico.guppy_knn.ismGuppyKnn;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusResult;
import insilico.janus.JanusWorkflowResult;
import insilico.janus.iJanusWorkflow;
import insilico.janus.StandardizedModel;
import insilico.janus.workflows.tools.POPClass;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;

import insilico.watersolubility.ismWaterSolubilityIRFMN;
import org.apache.commons.math3.stat.descriptive.moment.GeometricMean;
import org.apache.commons.math3.stat.descriptive.rank.Median;

/**
 *
 * @author Alberto
 */
public class JanusWorkflowToxicity implements iJanusWorkflow {

    private final static short TC_MISSING = -1;
    private final static short TC_NON_TOXIC = 0;
    private final static short TC_TOXIC = 1;
    
    private final ismFishKnn Fish_Acute_KNN;
    private final ismFishNic Fish_Acute_NIC;
    private final ismFishLC50 Fish_Acute_IRFMN;
    private final ismFishNOEC Fish_Chronic_IRMFN;
    
    private final ismFatheadEPA Fathead_Acute_EPA;
    private final ismFatheadKnn Fathead_Acute_KNN;
    private final ismGuppyKnn Guppy_Acute_KNN;
    
    private final ismDaphniaDemetra Daphnia_Acute_Demetra;
    private final ismDaphniaEPA Daphnia_Acute_EPA;
    private final ismDaphniaEC50 Daphnia_Acute_IRFMN;
    private final ismDaphniaNOEC Daphnia_Chronic_IRMFN;
    
    private final ismAlgaeEC50 Algae_Acute_IRFMN;
    private final ismAlgaeNOEC Algae_Chronic_IRFMN;
    
    private final ismWaterSolubilityIRFMN WS;
    
    private final InsilicoModelRunnerByMolecule Runner;
    
    private ExperimentalValues Experimental;
    
    private final DecimalFormat Format_1D;
    private final DecimalFormat Format_2D;
    private final DecimalFormat Format_4D;
    
    
    public JanusWorkflowToxicity() throws InitFailureException {
        Experimental = null;
        
        Fish_Acute_KNN = new ismFishKnn();
        Fish_Acute_NIC = new ismFishNic();
        Fish_Acute_IRFMN = new ismFishLC50();
        Fish_Chronic_IRMFN = new ismFishNOEC();
        Fathead_Acute_EPA = new ismFatheadEPA();
        Fathead_Acute_KNN = new ismFatheadKnn();
        Guppy_Acute_KNN = new ismGuppyKnn();
        Daphnia_Acute_Demetra = new ismDaphniaDemetra();
        Daphnia_Acute_EPA = new ismDaphniaEPA();
        Daphnia_Acute_IRFMN = new ismDaphniaEC50();
        Daphnia_Chronic_IRMFN = new ismDaphniaNOEC();
        Algae_Acute_IRFMN = new ismAlgaeEC50();
        Algae_Chronic_IRFMN = new ismAlgaeNOEC();
        WS = new ismWaterSolubilityIRFMN();
        
        // build the runner for multiple models
        Runner = new InsilicoModelRunnerByMolecule();
        Runner.AddModel(Fish_Acute_KNN);
        Runner.AddModel(Fish_Acute_NIC);
        Runner.AddModel(Fish_Acute_IRFMN);
        Runner.AddModel(Fish_Chronic_IRMFN);
        Runner.AddModel(Fathead_Acute_EPA);
        Runner.AddModel(Fathead_Acute_KNN);
        Runner.AddModel(Guppy_Acute_KNN);
        Runner.AddModel(Daphnia_Acute_Demetra);
        Runner.AddModel(Daphnia_Acute_EPA);
        Runner.AddModel(Daphnia_Acute_IRFMN);
        Runner.AddModel(Daphnia_Chronic_IRMFN);
        Runner.AddModel(Algae_Acute_IRFMN);
        Runner.AddModel(Algae_Chronic_IRFMN);
        Runner.AddModel(WS);
        
        DecimalFormatSymbols InternationalSymbols =
            new DecimalFormatSymbols();
        InternationalSymbols.setDecimalSeparator('.');         
        Format_1D = new DecimalFormat("0.#", InternationalSymbols);
        Format_2D = new DecimalFormat("0.##", InternationalSymbols);
        Format_4D = new DecimalFormat("0.####", InternationalSymbols);
        
    }
    
    
    @Override
    public JanusWorkflowResult Calculate(InsilicoMolecule mol) throws GenericFailureException {
        
        JanusWorkflowResult Results = new JanusWorkflowResult();
        double Tox_final_prediction = JanusResult.MISSING_VALUE;
        double Tox_final_reliability = JanusResult.MISSING_VALUE;
        
        
        ////// run needed models
        try {
            Runner.Run(mol);
        } catch (Throwable e) {
            if (e.getClass() == OutOfMemoryError.class) 
                throw new GenericFailureException("A memory error occurred. Please check the user's guide for troubleshooting about memory usage.");
            else 
                throw new GenericFailureException("An unexpected error has occurred: " + e.getLocalizedMessage());                    
        }                      
        InsilicoModelOutput OutFish_Acute_KNN = Runner.GetModelWrapper(ismFishKnn.class).getResult().get(0);
        InsilicoModelOutput OutFish_Acute_NIC = Runner.GetModelWrapper(ismFishNic.class).getResult().get(0);
        InsilicoModelOutput OutFish_Acute_IRFMN = Runner.GetModelWrapper(ismFishLC50.class).getResult().get(0);
        InsilicoModelOutput OutFish_Chronic_IRMFN = Runner.GetModelWrapper(ismFishNOEC.class).getResult().get(0);
        InsilicoModelOutput OutFathead_Acute_EPA = Runner.GetModelWrapper(ismFatheadEPA.class).getResult().get(0);
        InsilicoModelOutput OutFathead_Acute_KNN = Runner.GetModelWrapper(ismFatheadKnn.class).getResult().get(0);
        InsilicoModelOutput OutGuppy_Acute_KNN = Runner.GetModelWrapper(ismGuppyKnn.class).getResult().get(0);
        InsilicoModelOutput OutDaphnia_Acute_Demetra = Runner.GetModelWrapper(ismDaphniaDemetra.class).getResult().get(0);
        InsilicoModelOutput OutDaphnia_Acute_EPA = Runner.GetModelWrapper(ismDaphniaEPA.class).getResult().get(0);
        InsilicoModelOutput OutDaphnia_Acute_IRFMN = Runner.GetModelWrapper(ismDaphniaEC50.class).getResult().get(0);
        InsilicoModelOutput OutDaphnia_Chronic_IRFMN = Runner.GetModelWrapper(ismDaphniaNOEC.class).getResult().get(0);
        InsilicoModelOutput OutAlgae_Acute_IRFMN = Runner.GetModelWrapper(ismAlgaeEC50.class).getResult().get(0);
        InsilicoModelOutput OutAlgae_Chronic_IRFMN = Runner.GetModelWrapper(ismAlgaeNOEC.class).getResult().get(0);
        InsilicoModelOutput OutWS = Runner.GetModelWrapper(ismWaterSolubilityIRFMN.class).getResult().get(0);
        
        //// class matching
        // to do
        
        ////// main workflow

        // WS model
        StandardizedModel WS = new StandardizedModel(OutWS, 1, 3);
        
        // fish acute
        ArrayList<StandardizedModel> curIR = new  ArrayList<>();
        curIR.add(new StandardizedModel(OutFish_Acute_KNN, 1, 3));
        curIR.add(new StandardizedModel(OutFish_Acute_NIC, 1, 3));
        curIR.add(new StandardizedModel(OutFish_Acute_IRFMN, 1, 3));
        curIR.add(new StandardizedModel(OutFathead_Acute_EPA, 1, 3));
        curIR.add(new StandardizedModel(OutFathead_Acute_KNN, 1, 4));
        curIR.add(new StandardizedModel(OutGuppy_Acute_KNN, 1, 4));
        TrophicWorkflow FishAcute = new TrophicWorkflow(curIR, WS);

        // fish chronic
        curIR = new  ArrayList<>();
        curIR.add(new StandardizedModel(OutFish_Chronic_IRMFN, 1, 3));
        TrophicWorkflow FishChronic = new TrophicWorkflow(curIR, WS);

        // daphnia acute
        curIR = new  ArrayList<>();
        curIR.add(new StandardizedModel(OutDaphnia_Acute_Demetra, 1, 3));
        curIR.add(new StandardizedModel(OutDaphnia_Acute_EPA, 1, 3));
        curIR.add(new StandardizedModel(OutDaphnia_Acute_IRFMN, 1, 3));
        TrophicWorkflow DaphniaAcute = new TrophicWorkflow(curIR, WS);

        // daphnia chronic
        curIR = new  ArrayList<>();
        curIR.add(new StandardizedModel(OutDaphnia_Chronic_IRFMN, 1, 3));
        TrophicWorkflow DaphniaChronic = new TrophicWorkflow(curIR, WS);

        // algae acute
        curIR = new  ArrayList<>();
        curIR.add(new StandardizedModel(OutAlgae_Acute_IRFMN, 1, 3));
        TrophicWorkflow AlgaeAcute = new TrophicWorkflow(curIR, WS);

        // algae chronic
        curIR = new  ArrayList<>();
        curIR.add(new StandardizedModel(OutAlgae_Chronic_IRFMN, 1, 3));
        TrophicWorkflow AlgaeChronic = new TrophicWorkflow(curIR, WS);
        
        // assessment for the 3 throphic levels
        TrophicAssessment AssFish = new TrophicAssessment(FishAcute, FishChronic);
        TrophicAssessment AssDaphnia = new TrophicAssessment(DaphniaAcute, DaphniaChronic);
        TrophicAssessment AssAlgae = new TrophicAssessment(AlgaeAcute, AlgaeChronic);

        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_TOX))) {

            // user added exp        
            Tox_final_prediction = Experimental.getProperty(ExperimentalValues.KEY_TOX);
            Tox_final_reliability = 1.0;
            
        } else {
            
            // find worst case (lower prediction / NOEC)
            ArrayList<TrophicAssessment> finalAss = new ArrayList<>();
            finalAss.add(AssFish);
            finalAss.add(AssDaphnia);
            finalAss.add(AssAlgae);

            TrophicAssessment worstCase = null;
            for (TrophicAssessment ass : finalAss) {
                if (ass.Prediction != JanusResult.MISSING_VALUE) {
                    if (worstCase == null)
                        worstCase = ass;
                    else if (ass.Prediction < worstCase.Prediction) {
                        worstCase = ass;
                    }
                }
            }

            if (worstCase != null) {
                
                Tox_final_prediction = worstCase.Prediction;
                
                // check concordance of toxic classes (threshold)
                short curClass = Tox_final_prediction < 0.01 ? TC_TOXIC : TC_NON_TOXIC;
                double concordance = 0;
                for (TrophicAssessment ass : finalAss)
                    concordance += (curClass == ass.ToxicityClass) ? 1 : 0;
                concordance /= 3.0;
                
                Tox_final_reliability = (0.9 * worstCase.Reliability) + (0.1 * concordance);
            
            } else {
                
                Tox_final_prediction = JanusResult.MISSING_VALUE;
                Tox_final_reliability = JanusResult.MISSING_VALUE;
                
            }
        }
        
        
        ////// calculation of score
        double t_score;
        if (Tox_final_prediction != JanusResult.MISSING_VALUE)
            t_score= 1 - 1/(1+Math.exp(-1*( Math.log10(Tox_final_prediction) + 1)));
        else
            t_score = 0.5;
        
        
        ////// result object
        
        Results.Prediction = Tox_final_prediction;
        Results.Reliability = Tox_final_reliability;
        Results.PredictionClass = (Tox_final_prediction == JanusResult.MISSING_VALUE) ? "n.a." : ((Tox_final_prediction < 0.01) ? "TOXIC" : "NON Toxic");
        Results.Score = JanusEngine.TransformScore(t_score, Results.Reliability);
        Results.AdditionalValues.put("Fish Acute (LC50) Toxicity model (KNN)", OutFish_Acute_KNN.getAssessment());
        Results.AdditionalValues.put("Fish Acute (LC50) Toxicity model (NIC)", OutFish_Acute_NIC.getAssessment());
        Results.AdditionalValues.put("Fish Acute (LC50) Toxicity model (IRFMN)", OutFish_Acute_IRFMN.getAssessment());
        Results.AdditionalValues.put("Fathead Minnow Acute (LC50) Toxicity model (EPA)", OutFathead_Acute_EPA.getAssessment());
        Results.AdditionalValues.put("Fathead Minnow Acute (LC50) Toxicity model (KNN)", OutFathead_Acute_KNN.getAssessment());
        Results.AdditionalValues.put("Guppy Acute (LC50) Toxicity model (KNN)", OutGuppy_Acute_KNN.getAssessment());
        Results.AdditionalValues.put("Fish Chronic (NOEC) Toxicity model (IRFMN)", OutFish_Chronic_IRMFN.getAssessment());
        Results.AdditionalValues.put("Daphnia Magna Acute (LC50) Toxicity model (DEMETRA)", OutDaphnia_Acute_Demetra.getAssessment());
        Results.AdditionalValues.put("Daphnia Magna Acute (LC50) Toxicity model (EPA)", OutDaphnia_Acute_EPA.getAssessment());
        Results.AdditionalValues.put("Daphnia Magna Acute (LC50) Toxicity model (IRFMN)", OutDaphnia_Acute_IRFMN.getAssessment());
        Results.AdditionalValues.put("Daphnia Magna Chronic (NOEC) Toxicity model (IRFMN)", OutDaphnia_Chronic_IRFMN.getAssessment());
        Results.AdditionalValues.put("Algae Acute (EC50) Toxicity model (IRFMN)", OutAlgae_Acute_IRFMN.getAssessment());
        Results.AdditionalValues.put("Algae Chronic (NOEC) Toxicity model (IRFMN)", OutAlgae_Chronic_IRFMN.getAssessment());
        Results.AdditionalValues.put("Water Solubility model (IRFMN)", OutWS.getAssessment());

        String val = FormatToxValue(FishAcute.prediction);
        Results.AdditionalValues.put("Overall Fish Acute (LC50) Toxicity assessment (used in the workflow)", val + 
                " mg/L (reliability: " + Format_2D.format(FishAcute.reliability) + ")");
        
        val = FormatToxValue(FishChronic.prediction);
        Results.AdditionalValues.put("Overall Fish Chronic (NOEC) Toxicity assessment (used in the workflow)", val + 
                " mg/L (reliability: " + Format_2D.format(FishChronic.reliability) + ")");
        
        val = FormatToxValue(AssFish.Prediction);
        String tClass = AssFish.ToxicityClass == TC_TOXIC ? "Toxic" : "NON Toxic";
        Results.AdditionalValues.put("Overall Fish assessment (used in the workflow)", tClass + ", " + val + 
                " mg/L (reliability: " + Format_2D.format(AssFish.Reliability) + ")");
        
        
        val = FormatToxValue(DaphniaAcute.prediction); 
        Results.AdditionalValues.put("Overall Daphnia Magna Acute (LC50) Toxicity assessment (used in the workflow)", val + 
                " mg/L (reliability: " + Format_2D.format(DaphniaAcute.reliability) + ")");
        
        val = FormatToxValue(DaphniaChronic.prediction); 
        Results.AdditionalValues.put("Overall Daphnia Magna Chronic (NOEC) Toxicity assessment (used in the workflow)", val + 
                " mg/L (reliability: " + Format_2D.format(DaphniaChronic.reliability) + ")");
        
        val = FormatToxValue(AssDaphnia.Prediction);
        tClass = AssDaphnia.ToxicityClass == TC_TOXIC ? "Toxic" : "NON Toxic";
        Results.AdditionalValues.put("Overall Daphnia Magna assessment (used in the workflow)", tClass + ", " + val + 
                " mg/L (reliability: " + Format_2D.format(AssDaphnia.Reliability) + ")");

        
        val = FormatToxValue(AlgaeAcute.prediction);
        Results.AdditionalValues.put("Overall Algae Acute (EC50) Toxicity assessment (used in the workflow)", val + 
                " mg/L (reliability: " + Format_2D.format(AlgaeAcute.reliability) + ")");
        
        val = FormatToxValue(AlgaeChronic.prediction);
        Results.AdditionalValues.put("Overall Algae Chronic (NOEC) Toxicity assessment (used in the workflow)", val + 
                " mg/L (reliability: " + Format_2D.format(AlgaeChronic.reliability) + ")");
        
        val = FormatToxValue(AssAlgae.Prediction);
        tClass = AssAlgae.ToxicityClass == TC_TOXIC ? "Toxic" : "NON Toxic";
        Results.AdditionalValues.put("Overall Algae assessment (used in the workflow)", tClass + ", " + val + 
                " mg/L (reliability: " + Format_2D.format(AssAlgae.Reliability) + ")");

        
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_TOX)))  {
            val = FormatToxValue(Experimental.getProperty(ExperimentalValues.KEY_TOX));
            Results.AdditionalValues.put("User added experimental aquatic toxicity value value", (val + " mg/l"));
        }
        
        
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
    
    
    private String FormatToxValue(double Value) {
        if (Value == JanusResult.MISSING_VALUE)
            return "n.a.";
        if (Value < 1)
            return Format_4D.format(Value);
        if (Value < 10)
            return Format_2D.format(Value);
        return Format_1D.format(Value);         
    }
    
    
    
    
    private class TrophicAssessment {
        
        short ToxicityClass;
        double Reliability;
        double Prediction;
        
        public TrophicAssessment(TrophicWorkflow Acute, TrophicWorkflow Chronic) {

            if ( !HasPrediction(Chronic) ) {
                // no chronic, can not proceed
                Prediction = JanusResult.MISSING_VALUE;
                Reliability = 0;
                ToxicityClass = TC_MISSING;
                return;
            }

            // NOEC (chronic) available, check for acute to calculate/adjust
            // R2 for NOEC
            double R2_NOEC;

            if ( !HasPrediction(Acute) ) {
                // no acute, R2 NOEC equal to original NOEC reliability
                R2_NOEC = Chronic.reliability;
            } else {
                // acute and chronic available, adjust NOEC reliability if
                // chronic is less than acute
                if (Chronic.prediction <= Acute.prediction) {
                    R2_NOEC = Chronic.reliability;
                } else {
                    if (Chronic.fromExperimental) {
                        if (Acute.fromExperimental)
                            R2_NOEC = Chronic.reliability / 1.5;
                        else
                            R2_NOEC = Chronic.reliability;
                    } else {
                        if (Acute.fromExperimental)
                            R2_NOEC = Chronic.reliability / 2.0;
                        else
                            R2_NOEC = Chronic.reliability / 1.75;
                    }
                }
            }  
            
            // Calculate Rc
            double Rc;
            
            if (Chronic.fromExperimental) {
                if (R2_NOEC >= 1)
                    Rc = 1;
                else
                    Rc = R2_NOEC;
            } else {
                Rc = R2_NOEC;
            }
            
            // Prediction and reliability is always the value from NOEC 
            // and adjusted R2 NOEC
            Prediction  = Chronic.prediction;
            Reliability = R2_NOEC;
            
            // Toxicity class depending on thresholds and reliabilities
            if ( (HasPrediction(Acute)) && (Acute.reliability > R2_NOEC) ) {
                
                if (Acute.prediction < 0.01) 
                    ToxicityClass = TC_TOXIC;
                else {
                    if (Chronic.prediction < 0.01)
                        ToxicityClass = TC_TOXIC;                
                    else
                        ToxicityClass = TC_NON_TOXIC;                
                }
                
            } else {

                if (Chronic.prediction < 0.01)
                    ToxicityClass = TC_TOXIC;                
                else
                    ToxicityClass = TC_NON_TOXIC;                
                
            }
            
        }
        
        private boolean HasPrediction(TrophicWorkflow wf) {
            if (wf != null) 
                if (wf.prediction != JanusResult.MISSING_VALUE)
                        return true;
            return false;
        }
    }
    
    
    
    
    private class TrophicWorkflow {
    
        public double prediction;
        public double reliability;
        public boolean fromExperimental;
        

        public TrophicWorkflow(ArrayList<StandardizedModel> Models, StandardizedModel ModelWS) {
            
            prediction = JanusResult.MISSING_VALUE;
            reliability = JanusResult.MISSING_VALUE;

            // check for exp from models
            ArrayList<StandardizedModel> ExpFromModels = new ArrayList<>();
            int nTotalModels = 0;
            for (StandardizedModel o : Models) {
                nTotalModels++;
                if (o.Reliability == 1) {
                    ExpFromModels.add(o);
                }
            }
            
            // calculate final prediction and additional info for reliability
            double IR;
            double C;
            double DeltaScore = 0;
            fromExperimental = false;
            
            if (!ExpFromModels.isEmpty()) {
                
                // assessment only with exp values from models
                
                IR = 1; // exp
                prediction = CalculateTox_Value(ExpFromModels);
                C = CalculateTox_C(ExpFromModels, prediction, nTotalModels);
                fromExperimental = true;
                
            } else {

                // assessment only with models predictions
                
                // calculate prediction for each rel class
                ArrayList<Double> reliability_classes = new ArrayList<>();
                reliability_classes.add(0.8);
                reliability_classes.add(0.6);
                reliability_classes.add(0.4);

                HashMap<Double, Double> tox_by_reliability = new HashMap<>();
                HashMap<Double, Double> C_by_reliability = new HashMap<>();
                
                for (Double cur_reliability : reliability_classes) {
                    
                    ArrayList<StandardizedModel> cur_tox_values = new ArrayList<>();
                    for (StandardizedModel o : Models) 
                        if (o.Reliability == cur_reliability) 
                            cur_tox_values.add(o);
                    
                    if (!cur_tox_values.isEmpty()) {
                        double cur_tox = CalculateTox_Value(cur_tox_values);
                        double cur_C = CalculateTox_C(cur_tox_values, cur_tox, nTotalModels);
                        tox_by_reliability.put(cur_reliability, cur_tox);
                        C_by_reliability.put(cur_reliability, cur_C);
                    }
                }

                
                if (tox_by_reliability.keySet().contains(0.8)) {
                    prediction = tox_by_reliability.get(0.8);
                    IR = 0.8;
                    C = C_by_reliability.get(0.8);

                    switch ( CalculateTox_DeltaScore(tox_by_reliability, 0.8, 0.6) ) {
                        case 1: DeltaScore += 0.1; break;
                        case 2: DeltaScore += -0.05; break;
                        case 3: DeltaScore += -0.1; break;
                    }
                    switch ( CalculateTox_DeltaScore(tox_by_reliability, 0.8, 0.4) ) {
                        case 1: DeltaScore += 0.05; break;
                        case 2: DeltaScore += -0.025; break;
                        case 3: DeltaScore += -0.05; break;
                    }
                }

                else if (tox_by_reliability.keySet().contains(0.6)) {
                    prediction = tox_by_reliability.get(0.6);
                    IR = 0.6;
                    C = C_by_reliability.get(0.6);

                    switch ( CalculateTox_DeltaScore(tox_by_reliability, 0.6, 0.4) ) {
                        case 1: DeltaScore += 0.05; break;
                        case 2: DeltaScore += -0.025; break;
                        case 3: DeltaScore += -0.05; break;
                    }
                }

                else if (tox_by_reliability.keySet().contains(0.4)) {
                    prediction = tox_by_reliability.get(0.4);
                    IR = 0.4;
                    C = C_by_reliability.get(0.4);
                }

                else {
                    // missing values
                    prediction = JanusResult.MISSING_VALUE;
                    IR = 0.5;
                    C = 0;
                }                
                
            }
            
            // calculate WS factor
            double WS = 0;            
            if (ModelWS.Reliability == 1) {
                if (prediction <= ModelWS.Prediction)
                    WS = 0.2;
                else
                    WS = -0.2;
            } else if (ModelWS.Reliability == 0.8) {
                if (prediction <= ModelWS.Prediction)
                    WS = 0.1;
                else
                    WS = -0.1;
            } 
             
            // calculate final prediction and reliability
            reliability = ( (IR + C) / 2.0 ) + WS + DeltaScore;
            
            // if reliability is greater than 1, it is scaled depending 
            // on no. of models, to avoid
            if (reliability >= 1) {
                if (fromExperimental) {
                    // for exp values, always scaled to 1
                    reliability = 1;
                } else {
                    int nModels = Models.size();
                    switch (nModels) {
                        case 1:
                            reliability = 0.45 * reliability + 0.495;
                            break;
                        case 3:
                            reliability = 0.012 * reliability + 0.983;
                            break;
                        case 6:
                            reliability = 0.9985;
                            break;
                        default:
                            reliability = 1; // just to be sure - it should never go here
                            break;
                    }
                    
                }
            }
            
        }
        
        
        private double CalculateTox_Value(ArrayList<StandardizedModel> Values) {
            double T;

            if (Values.isEmpty()) {
                T = JanusResult.MISSING_VALUE;
            }

            else if (Values.size() == 1) {
                T = Values.get(0).Prediction;
            }

            else {
                double cur_min = 9999, cur_max = -9999;
                double[] buf = new double[Values.size()];
                for (StandardizedModel v : Values) {
                    if (v.Prediction<cur_min) cur_min = v.Prediction;
                    if (v.Prediction>cur_max) cur_max = v.Prediction;
                }

                for (int i=0; i<Values.size(); i++)
                    buf[i] = Values.get(i).Prediction;

                if ( (cur_max / cur_min) <= 3.0 ) {
                    // geometric mean
                    GeometricMean gmean = new GeometricMean();
                    T = gmean.evaluate(buf);
                } else {
                    // median
                    Median median = new Median();
                    T = median.evaluate(buf);
                } 
            }

            return T;
        }


        private double CalculateTox_C(ArrayList<StandardizedModel> Values, 
                double FinalToxValue, int nTotalModels) {
            double C;

            if (Values.isEmpty()) {
                C = 0;
            }

            else if (nTotalModels == 1) {
                C = Values.get(0).Reliability - 0.3;
            }

            else {

                double S = 0;

                for (StandardizedModel v : Values) {
                    double ratio = v.Prediction > FinalToxValue ?
                            v.Prediction / FinalToxValue : 
                            FinalToxValue / v.Prediction;    
                    if ( ratio <= 1.5 )
                        S += 1;
                    else if ( ratio <= 3.0 )
                        S += -0.5;
                    else
                        S += -1;                            
                }

                C = (S + nTotalModels) / (2.0 * nTotalModels);
            }

            return C;
        }
        
        
        private int CalculateTox_DeltaScore(HashMap<Double, Double> tox_values, 
                double FirstClass, double SecondClass) {

            // 0: missing value
            // 1: maxVal/minVal <= 3
            // 2: maxVal/minVal <= 5
            // 3: maxVal/minVal > 5

            if (!tox_values.keySet().contains(FirstClass)) return 0;
            if (!tox_values.keySet().contains(SecondClass)) return 0;

            double ratio = tox_values.get(FirstClass) > tox_values.get(SecondClass) ?
                    tox_values.get(FirstClass) / tox_values.get(SecondClass) : 
                    tox_values.get(SecondClass) / tox_values.get(FirstClass);    

            if (ratio <= 3.0) return 1;
            else if (ratio <= 5.0) return 2;
            else return 3;
        }                        
    }    
    
}
