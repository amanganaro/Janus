package insilico.janus.workflows;

import insilico.bcf_arnotgobas.ismBCFArnotGobas;
import insilico.bcf_caesar.ismBCFCaesar;
import insilico.bcf_knn.ismBCFKnn;
import insilico.bcf_meylan.ismBCFMeylan;
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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;

import insilico.logp_alogp.ismLogPALogP;
import insilico.logp_mlogp.ismLogPMLogP;
import insilico.meylanlogp.ismLogPMeylan;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Median;

/**
 *
 * @author Alberto
 */
public class JanusWorkflowBCF implements iJanusWorkflow {

    private final ismBCFArnotGobas Arnot;
    private final ismBCFCaesar Caesar;
    private final ismBCFKnn KNN;
    private final ismBCFMeylan Meylan;
    
    private final ismLogPALogP ALogP;
    private final ismLogPMLogP MLogP;
    private final ismLogPMeylan MeylanLogP;
    
    private final InsilicoModelRunnerByMolecule Runner;
    
    private ExperimentalValues Experimental;
    private POPClass POPClassification;
    
    
    public JanusWorkflowBCF() throws InitFailureException {
        Experimental = null;
        POPClassification = null;
        
        Arnot = new ismBCFArnotGobas();
        Caesar = new ismBCFCaesar();
        KNN = new ismBCFKnn();
        Meylan = new ismBCFMeylan();
        
        ALogP = new ismLogPALogP();
        MLogP = new ismLogPMLogP();
        MeylanLogP = new ismLogPMeylan();
        
        // build the runner for multiple models
        Runner = new InsilicoModelRunnerByMolecule();
        Runner.AddModel(Arnot);
        Runner.AddModel(Caesar);
        Runner.AddModel(KNN);
        Runner.AddModel(Meylan);        
        Runner.AddModel(ALogP);        
        Runner.AddModel(MLogP);        
        Runner.AddModel(MeylanLogP);        
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
        InsilicoModelOutput OutArnot = Runner.GetModelWrapper(ismBCFArnotGobas.class).getResult().get(0);
        InsilicoModelOutput OutCaesar = Runner.GetModelWrapper(ismBCFCaesar.class).getResult().get(0);
        InsilicoModelOutput OutKNN = Runner.GetModelWrapper(ismBCFKnn.class).getResult().get(0);
        InsilicoModelOutput OutMeylan = Runner.GetModelWrapper(ismBCFMeylan.class).getResult().get(0);        
        InsilicoModelOutput OutAlogP = Runner.GetModelWrapper(ismLogPALogP.class).getResult().get(0);
        InsilicoModelOutput OutMlogP = Runner.GetModelWrapper(ismLogPMLogP.class).getResult().get(0);
        InsilicoModelOutput OutMeylanlogP = Runner.GetModelWrapper(ismLogPMeylan.class).getResult().get(0);
        
        
        
        //// logP workflow
        
        ArrayList<InsilicoModelOutput> logpRes = new ArrayList<>();
        logpRes.add(OutAlogP);
        logpRes.add(OutMlogP);
        logpRes.add(OutMeylanlogP);
        LogpWorkflow wfLogP = new LogpWorkflow(logpRes, Experimental);
        
        
        ////// main workflow
        
        double BCF_final_value = JanusResult.MISSING_VALUE;
        double BCF_final_reliability = JanusResult.MISSING_VALUE;
        
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_BCF))) {

            // user added exp        
            BCF_final_value = Experimental.getProperty(ExperimentalValues.KEY_BCF);
            BCF_final_reliability = 1.0;
            
        } else if ( (POPClassification != null) && ( POPClassification.PCN || 
                POPClassification.PFAS || POPClassification.SCCP )  ) {
            
            if (POPClassification.PCN) {
                BCF_final_value = 4.4;
                BCF_final_reliability = 0.8;     
            } else if (POPClassification.PFAS) {
                BCF_final_value = 4.4;
                BCF_final_reliability = 0.8;     
            } else if (POPClassification.SCCP) {
                BCF_final_value = 3.9;
                BCF_final_reliability = 0.8;     
            }
            
        } else {
            
            // process model outputs
            ArrayList<InsilicoModelOutput> bcfResults = new ArrayList<>();
            bcfResults.add(OutArnot);
            bcfResults.add(OutCaesar);
            bcfResults.add(OutKNN);
            bcfResults.add(OutMeylan);
                    
            ArrayList<StandardizedModel> bcfIR = new ArrayList<>();
            int nBCFModels = 0;
            for (InsilicoModelOutput o : bcfResults) {
                nBCFModels++;
                StandardizedModel m = new StandardizedModel(o);
                bcfIR.add(m);
            }
            
            // check for exp from models
            ArrayList<StandardizedModel> ExpFromModels = new ArrayList<>();
            for (StandardizedModel o : bcfIR) {
                if (o.Reliability == 1) {
                    ExpFromModels.add(o);
                }
            }
            
            // calculate final BCF prediction and additional info for reliability
            double IR;
            double C;
            double DeltaScore = 0;
            boolean isExp = false;
            
            if (!ExpFromModels.isEmpty()) {
                
                // assessment only with exp values from models
                
                isExp = true;
                IR = 1; // exp
                BCF_final_value = CalculateBCF_Value(ExpFromModels);
                C = CalculateBCF_C(ExpFromModels, BCF_final_value, nBCFModels);
                
            } else {

                // assessment only with models predictions
                
                // calculate prediction for each rel class
                ArrayList<Double> reliability_classes = new ArrayList<>();
                reliability_classes.add(0.8);
                reliability_classes.add(0.6);
                reliability_classes.add(0.4);

                HashMap<Double, Double> bcf_by_reliability = new HashMap<>();
                HashMap<Double, Double> C_by_reliability = new HashMap<>();
                
                for (Double cur_reliability : reliability_classes) {
                    
                    ArrayList<StandardizedModel> cur_bcf_values = new ArrayList<>();
                    for (StandardizedModel o : bcfIR) 
                        if (o.Reliability == cur_reliability) 
                            cur_bcf_values.add(o);
                    
                    if (!cur_bcf_values.isEmpty()) {
                        double cur_bcf = CalculateBCF_Value(cur_bcf_values);
                        double cur_C = CalculateBCF_C(cur_bcf_values, cur_bcf, nBCFModels);
                        bcf_by_reliability.put(cur_reliability, cur_bcf);
                        C_by_reliability.put(cur_reliability, cur_C);
                    }
                }

                
                if (bcf_by_reliability.keySet().contains(0.8)) {
                    BCF_final_value = bcf_by_reliability.get(0.8);
                    IR = 0.8;
                    C = C_by_reliability.get(0.8);

                    switch ( CalculateBCF_DeltaScore(bcf_by_reliability, 0.8, 0.6) ) {
                        case 1: DeltaScore += 0.1; break;
                        case 2: DeltaScore += -0.05; break;
                        case 3: DeltaScore += -0.1; break;
                    }
                    switch ( CalculateBCF_DeltaScore(bcf_by_reliability, 0.8, 0.4) ) {
                        case 1: DeltaScore += 0.05; break;
                        case 2: DeltaScore += -0.025; break;
                        case 3: DeltaScore += -0.05; break;
                    }
                }

                else if (bcf_by_reliability.keySet().contains(0.6)) {
                    BCF_final_value = bcf_by_reliability.get(0.6);
                    IR = 0.6;
                    C = C_by_reliability.get(0.6);

                    switch ( CalculateBCF_DeltaScore(bcf_by_reliability, 0.6, 0.4) ) {
                        case 1: DeltaScore += 0.05; break;
                        case 2: DeltaScore += -0.025; break;
                        case 3: DeltaScore += -0.05; break;
                    }
                }

                else if (bcf_by_reliability.keySet().contains(0.4)) {
                    BCF_final_value = bcf_by_reliability.get(0.4);
                    IR = 0.4;
                    C = C_by_reliability.get(0.4);
                }

                else {
                    // missing values
                    BCF_final_value = JanusResult.MISSING_VALUE;
                    IR = 0.5;
                    C = 0;
                }                
                
            }
            
            // calculate logP factor
            double deltaLogP = 0;
            if (BCF_final_value <= 3.3) {
                if (wfLogP.prediction <= 3) {
                    if (wfLogP.reliability >= 0.75) 
                        deltaLogP = 0.2;
                    else if (wfLogP.reliability >= 0.5) 
                        deltaLogP = 0.1;
                }
            }            
            
            // calculate final prediction and reliability
            BCF_final_reliability = ( (IR + C) / 2.0 ) + deltaLogP + DeltaScore;
            if (isExp)
                BCF_final_reliability = BCF_final_reliability >= 1 ? 1 : BCF_final_reliability;
            else
                BCF_final_reliability = BCF_final_reliability >= 1 ? 0.995 : BCF_final_reliability;
            
        }
        

        ////// calculation of score
        double bcf_score;
        if (BCF_final_value < 3.0) {
            bcf_score = 1/(1+Math.exp(-1*(BCF_final_value-3)));
        } else {
            bcf_score = 1/(1+Math.exp(-2*(BCF_final_value-3))); 
        } 
        
        
        ////// result object
        DecimalFormatSymbols InternationalSymbols =
            new DecimalFormatSymbols();
        InternationalSymbols.setDecimalSeparator('.');         
        DecimalFormat Format_2D = new DecimalFormat("0.##", InternationalSymbols);
        
        Results.PredictionClass = BCF_final_value == JanusResult.MISSING_VALUE ? "n.a." : Format_2D.format(BCF_final_value);
        Results.Prediction = BCF_final_value;
        Results.Reliability = BCF_final_reliability>1 ? 1 : BCF_final_reliability;
        Results.Score = JanusEngine.TransformScore(bcf_score, Results.Reliability);
        Results.AdditionalValues.put("BCF Caesar Model prediction", OutCaesar.getAssessment());
        Results.AdditionalValues.put("BCF KNN Model prediction", OutKNN.getAssessment());
        Results.AdditionalValues.put("BCF Meylan Model prediction", OutMeylan.getAssessment());
        Results.AdditionalValues.put("BCF Arnot-Gobas Model prediction", OutArnot.getAssessment());
        Results.AdditionalValues.put("LogP (AlogP model) prediction", OutAlogP.getAssessment());
        Results.AdditionalValues.put("LogP (MlogP model) prediction", OutMlogP.getAssessment());
        Results.AdditionalValues.put("LogP (Meylan logP model) prediction", OutMeylanlogP.getAssessment());
        Results.AdditionalValues.put("Overall LogP assessment (used in the workflow)", Format_2D.format(wfLogP.prediction) + 
                " (reliability: " + Format_2D.format(wfLogP.reliability) + ")");
        
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_BCF))) 
            Results.AdditionalValues.put("User added experimental BCF value", 
                    Format_2D.format(Experimental.getProperty(ExperimentalValues.KEY_BCF)));
        if ((Experimental != null) && (Experimental.hasProperty(ExperimentalValues.KEY_LOGP))) 
            Results.AdditionalValues.put("User added experimental LogP value", 
                    Format_2D.format(Experimental.getProperty(ExperimentalValues.KEY_LOGP)));
        
        if ( (POPClassification != null) && ( POPClassification.PCN || 
                POPClassification.PFAS || POPClassification.SCCP )  ) {
            String ClassName = "";
            if (POPClassification.PCN)
                ClassName = "PCN";
            else if (POPClassification.PFAS)
                ClassName = "PFAS";
            else if (POPClassification.SCCP)
                ClassName = "SCCP";
            Results.AdditionalValues.put("Screening classes match", ClassName + 
                    " (value " + Format_2D.format(BCF_final_value) + ", reliability 0.8)");
        }
        
        return Results;
    }
    

    private double CalculateBCF_Value(ArrayList<StandardizedModel> Values) {
        double BCF;
        
        if (Values.isEmpty()) {
            BCF = JanusResult.MISSING_VALUE;
        }
        
        else if (Values.size() == 1) {
            BCF = Values.get(0).Prediction;
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
            
            if ( Math.abs(cur_max - cur_min) <= 0.6 ) {
                // arithmetic mean
                Mean mean = new Mean();
                BCF = mean.evaluate(buf);
            } else {
                // median
                Median median = new Median();
                BCF = median.evaluate(buf);
            } 
        }
        
        return BCF;
    }
           

    private double CalculateBCF_C(ArrayList<StandardizedModel> Values, 
            double FinalBCFValue, int nTotalModels) {
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
                if (Math.abs(v.Prediction - FinalBCFValue) <= 0.3)
                    S += 1;
                else if (Math.abs(v.Prediction - FinalBCFValue) <= 0.6)
                    S += -0.5;
                else
                    S += -1;                            
            }
            
            C = (S + nTotalModels) / (2.0 * nTotalModels);
        }
        
        return C;
    }


    private int CalculateBCF_DeltaScore(HashMap<Double, Double> bcf_values, 
            double FirstClass, double SecondClass) {

        // 0: missing value
        // 1: maxVal - minVal <= 0.6
        // 2: maxVal - minVal <= 1.2
        // 3: maxVal - minVal > 1.2

        if (!bcf_values.keySet().contains(FirstClass)) return 0;
        if (!bcf_values.keySet().contains(SecondClass)) return 0;

        double diff = Math.abs(bcf_values.get(FirstClass) - bcf_values.get(SecondClass));

        if (diff <= 0.6) return 1;
        else if (diff <= 1.2) return 2;
        else return 3;
    }


    @Override
    public void SetExperimentalValue(ExperimentalValues ExpVal) {
        this.Experimental = ExpVal;
    }

    @Override
    public void SetPOPClass(POPClass POP) {
        this.POPClassification = POP;
    }
    
    
    
    private class LogpWorkflow {
        
        public double prediction;
        public double reliability;
        
        public LogpWorkflow(ArrayList<InsilicoModelOutput> logpResults, ExperimentalValues ExpValues) {
            prediction = JanusResult.MISSING_VALUE;
            reliability = JanusResult.MISSING_VALUE;
            
            // check for user given exp
            if ((ExpValues != null) && (ExpValues.hasProperty(ExperimentalValues.KEY_LOGP))) {
                prediction = ExpValues.getProperty(ExperimentalValues.KEY_LOGP);
                reliability = 1.0;
                return;                
            }

            // process model outputs
            ArrayList<StandardizedModel> logpIR = new ArrayList<>();
            int nLogPModels = 0;
            for (InsilicoModelOutput o : logpResults) {
                StandardizedModel m = new StandardizedModel(o);
                logpIR.add(new StandardizedModel(o));
                if (m.Prediction != JanusResult.MISSING_VALUE)
                    nLogPModels++;
            }
            
            // check for exp from models
            for (StandardizedModel o : logpIR) {
                if (o.Reliability == 1) {
                    prediction = o.Prediction;                    
                    double rel = o.Reliability;
                    double C = rel - 0.3;
                    reliability = (rel + C) / 2.0;
                    return;
                }
            }
            
            // calculate prediction for each rel class
            ArrayList<Double> reliability_classes = new ArrayList<>();
            reliability_classes.add(0.8);
            reliability_classes.add(0.6);
            reliability_classes.add(0.4);
            
            HashMap<Double, Double> logp_by_reliability = new HashMap<>();
            HashMap<Double, Double> C_by_reliability = new HashMap<>();
            for (Double cur_reliability : reliability_classes) {
                ArrayList<Double> cur_logp_values = new ArrayList<>();
                double cur_min = 9999;
                double cur_max = -9999;
                for (StandardizedModel o : logpIR) {
                    if (o.Reliability == cur_reliability) {
                        cur_logp_values.add(o.Prediction);
                        if (o.Prediction < cur_min) cur_min = o.Prediction;
                        if (o.Prediction > cur_max) cur_max = o.Prediction;
                    }
                }
                if (!cur_logp_values.isEmpty()) {
                    
                    // calculate logp value
                    double cur_logp;

                    double[] buf = new double[cur_logp_values.size()];
                    for (int i=0; i<cur_logp_values.size(); i++)
                        buf[i] = cur_logp_values.get(i);

                    if ( Math.abs(cur_max - cur_min) <= 0.5 ) {
                        // arithmetic mean
                        Mean mean = new Mean();
                        cur_logp = mean.evaluate(buf);
                    } else {
                        // median
                        Median median = new Median();
                        cur_logp = median.evaluate(buf);
                    } 
                    
                    logp_by_reliability.put(cur_reliability, cur_logp);
                    
                    // calculate C factor for the reliability class
                    if (nLogPModels == 1) {
                        C_by_reliability.put(cur_reliability, (cur_reliability - 0.3) );
                    } else {
                        double sum_S = 0;
                        for (Double val : cur_logp_values) {
                            double delta = Math.abs(val - cur_logp);
                            if (delta <= 0.25) sum_S += 1; 
                            else if (delta <= 0.5) sum_S += -0.5; 
                            else sum_S += -1; 
                        }
                        double cur_C = (sum_S + nLogPModels) / (2.0 * nLogPModels);
                        C_by_reliability.put(cur_reliability, cur_C);
                    }
                }
            }
            
            // Get logp evaluation from highest reliability class
            double C_factor = 0;
            double Delta_LogP = 0;
            
            if (logp_by_reliability.keySet().contains(0.8)) {
                prediction = logp_by_reliability.get(0.8);
                reliability = 0.8;
                C_factor = C_by_reliability.get(0.8);
                
                switch ( CalculateLogpDeltaScore(logp_by_reliability, 0.8, 0.6) ) {
                    case 1: Delta_LogP += 0.1; break;
                    case 2: Delta_LogP += -0.05; break;
                    case 3: Delta_LogP += -0.1; break;
                }
                switch ( CalculateLogpDeltaScore(logp_by_reliability, 0.8, 0.4) ) {
                    case 1: Delta_LogP += 0.05; break;
                    case 2: Delta_LogP += -0.025; break;
                    case 3: Delta_LogP += -0.05; break;
                }
            }
            
            else if (logp_by_reliability.keySet().contains(0.6)) {
                prediction = logp_by_reliability.get(0.6);
                reliability = 0.6;
                C_factor = C_by_reliability.get(0.6);
                
                switch ( CalculateLogpDeltaScore(logp_by_reliability, 0.6, 0.4) ) {
                    case 1: Delta_LogP += 0.05; break;
                    case 2: Delta_LogP += -0.025; break;
                    case 3: Delta_LogP += -0.05; break;
                }
            }
            
            else if (logp_by_reliability.keySet().contains(0.4)) {
                prediction = logp_by_reliability.get(0.4);
                reliability = 0.4;
                C_factor = C_by_reliability.get(0.4);
            }
            
            else {
                // return with missing values
                return;
            }
            
            // correction for reliability
            reliability = ( (reliability + C_factor) / 2.0 ) + Delta_LogP;
        }
    
    
        private int CalculateLogpDeltaScore(HashMap<Double, Double> logp_values, 
                double FirstClass, double SecondClass) {

            // 0: missing value
            // 1: maxVal/minVal <= 0.5
            // 2: maxVal/minVal <= 1
            // 3: maxVal/minVal > 1

            if (!logp_values.keySet().contains(FirstClass)) return 0;
            if (!logp_values.keySet().contains(SecondClass)) return 0;

            double diff = Math.abs(logp_values.get(FirstClass) - logp_values.get(SecondClass));

            if (diff <= 0.5) return 1;
            else if (diff <= 1) return 2;
            else return 3;
        }
    }
    
}
