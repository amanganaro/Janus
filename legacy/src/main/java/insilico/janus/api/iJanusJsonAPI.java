package insilico.janus.api;

/**
 *
 * @author Alberto
 */
public interface iJanusJsonAPI {
    
    /**
     * Return the results of the Janus CARCINOGENICITY workflow as a JSON.
     * 
     * Format of the resulting JSON object:
     * 
     * {
     *   "SMILES": input molecule SMILES,
     *   "Prediction": numerical prediction,
     *   "PredictionLabel": label/classification for the prediction,
     *   "Reliability": reliability index (between 1 and 0) of the prediction
     * }
     * 
     * 
     * @param SMILES SMILES string for the input molecule
     * @return JSON string
     */
    abstract public String GetWorkflowCarcinogenicity(String SMILES);

    /**
     * Return the results of the Janus MUTAGENICITY workflow as a JSON.
     * 
     * Format of the resulting JSON object:
     * 
     * {
     *   "SMILES": input molecule SMILES,
     *   "Prediction": numerical prediction,
     *   "PredictionLabel": label/classification for the prediction,
     *   "Reliability": reliability index (between 1 and 0) of the prediction
     * }
     * 
     * 
     * @param SMILES SMILES string for the input molecule
     * @return JSON string
     */
    abstract public String GetWorkflowMutagenicity(String SMILES);

    /**
     * Return the results of the Janus REPROTOXICITY workflow as a JSON.
     * 
     * Format of the resulting JSON object:
     * 
     * {
     *   "SMILES": input molecule SMILES,
     *   "Prediction": numerical prediction,
     *   "PredictionLabel": label/classification for the prediction,
     *   "Reliability": reliability index (between 1 and 0) of the prediction
     * }
     * 
     * 
     * @param SMILES SMILES string for the input molecule
     * @return JSON string
     */
    abstract public String GetWorkflowReprotox(String SMILES);
    
}
