package insilico.janus;

import insilico.core.ad.item.ADIndex;
import insilico.core.model.InsilicoModelOutput;

/**
 * Class for the standardization of VEGA models output,  with the calculation 
 * of the initial reliability IR (used by different workflows).
 * 
 * @author Alberto
 */
public class StandardizedModel {
    
    private static final double REL_EXPERIMENTAL = 1;
    private static final double REL_GOOD = 0.8;
    private static final double REL_MODERATE = 0.6;
    private static final double REL_LOW = 0.4;
    
    public final double Prediction;
    public final double Reliability;
    
    
    /**
     * Constructor. To be used when specific fields should be used to retrieve
     * the values (for prediction and for experimental values). Otherwise,
     * the standard constructor uses the main values.
     * 
     * @param model
     * @param ModelPredictionOutputIndex
     * @param ModelExpOutputIndex 
     */
    public StandardizedModel(InsilicoModelOutput model,
            int ModelPredictionOutputIndex, int ModelExpOutputIndex ) {
        
        // exp value
        if (model.HasExperimental()) {
            Prediction = Double.valueOf(model.getResults()[ModelExpOutputIndex]);
            Reliability = REL_EXPERIMENTAL;
            return;
        }
        
        // no prediction and no exp (error)
        if (model.getStatus() != InsilicoModelOutput.OUTPUT_OK) {
            Prediction = JanusResult.MISSING_VALUE;
            Reliability = JanusResult.MISSING_VALUE;
            return;
        }
        
        // normal prediction
        Prediction = Double.valueOf(model.getResults()[ModelPredictionOutputIndex]);
        switch (model.getADI().GetAssessmentClass()) {
            case ADIndex.INDEX_HIGH:
                Reliability = REL_GOOD; break;
            case ADIndex.INDEX_MEDIUM:
                Reliability = REL_MODERATE; break;
            case ADIndex.INDEX_LOW:
                Reliability = REL_LOW; break;
            default:
                Reliability = JanusResult.MISSING_VALUE;
        }

    } 


    public StandardizedModel(InsilicoModelOutput model) {
        
        // exp value
        if (model.HasExperimental()) {
            Prediction = model.getExperimental();
            Reliability = REL_EXPERIMENTAL;
            return;
        }
        
        // no prediction and no exp (error)
        if (model.getStatus() != InsilicoModelOutput.OUTPUT_OK) {
            Prediction = JanusResult.MISSING_VALUE;
            Reliability = JanusResult.MISSING_VALUE;
            return;
        }
        
        // normal prediction
        Prediction = model.getMainResultValue();
        switch (model.getADI().GetAssessmentClass()) {
            case ADIndex.INDEX_HIGH:
                Reliability = REL_GOOD; break;
            case ADIndex.INDEX_MEDIUM:
                Reliability = REL_MODERATE; break;
            case ADIndex.INDEX_LOW:
                Reliability = REL_LOW; break;
            default:
                Reliability = JanusResult.MISSING_VALUE;
        }

    }
    
   
   
    
}
