package insilico.janus;

import java.util.LinkedHashMap;

/**
 *
 * @author Alberto
 */
public class JanusWorkflowResult {

    public String PredictionClass;
    public Double Prediction;
    public Double Reliability;
    public Double Score;
    public LinkedHashMap<String, String> AdditionalValues;
    
    public JanusWorkflowResult() {
        PredictionClass = "-";
        Prediction = (double)JanusResult.MISSING_VALUE;
        Reliability = (double)JanusResult.MISSING_VALUE;
        Score = (double)JanusResult.MISSING_VALUE;
        AdditionalValues = new LinkedHashMap<>();
    }
    
}
