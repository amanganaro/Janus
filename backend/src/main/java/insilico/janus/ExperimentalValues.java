package insilico.janus;

import insilico.core.exception.GenericFailureException;
import java.util.HashMap;

/**
 *
 * @author Alberto
 */
public class ExperimentalValues {

    public final static String KEY_BCF = "bcf";
    public final static String KEY_LOGP = "logp";
    public final static String KEY_P_SOIL = "p_soil";
    public final static String KEY_P_WAT = "p_water";
    public final static String KEY_P_SED = "p_sediment";
    public final static String KEY_TOX = "tox";
    public final static String KEY_CARC = "carc";
    public final static String KEY_MUTA = "muta";
    public final static String KEY_REPRO = "repro";
    public final static String KEY_ED = "ed";
    
    public final static String[][] EXP_KEYS = {
        {KEY_BCF,       "Experimental BCF value [log(kg/l)]"},
        {KEY_LOGP,      "Experimental Octanol-Water partition coefficient (LogP) value"},
        {KEY_P_SOIL,    "Experimental persistence in soil (half-life expressed in days)"},
        {KEY_P_WAT,     "Experimental persistence in water (half-life expressed in days)"},
        {KEY_P_SED,     "Experimental persistence in sediment (half-life expressed in days)"},
        {KEY_TOX,       "Experimental aquatic toxicity value (NOEC expressed in mg/l)"},
        {KEY_CARC,      "Experimental carcinogenicty value [class 1/0]"},
        {KEY_MUTA,      "Experimental mutagenicity (Ames test) value [class 1/0]"},
        {KEY_REPRO,     "Experimental reproductive toxicity value [class 1/0]"},
        {KEY_ED,        "Experimental endocrine disruptor activity value [class 1/0]"},
    };    
        
    private final HashMap<String, Double> ExperimentalValues;
    
    
    /**
     * 
     * @param input
     * @throws GenericFailureException
     */
    public ExperimentalValues(String input) throws GenericFailureException {
        ExperimentalValues = new HashMap<>();
        
        String[] buf = input.split("\t");
        for (String s : buf) {
            if (s.contains(":")) {
                String[] curBuf = s.split(":");
                if (curBuf.length == 2) {
                    for (String[] keys : EXP_KEYS) {
                        if (keys[0].compareToIgnoreCase(curBuf[0]) == 0) {
                            try {
                                Double val = Double.valueOf(curBuf[1]);
                                ExperimentalValues.put(keys[0], val);
                                break;
                            } catch (NumberFormatException ex) {
                                throw new GenericFailureException("unable to parse and convert value " + curBuf[1] + " for the key " + curBuf[0]);
                            }
                        }
                    }
                }
            }
        }
    }
    
    
    public Double getProperty(String Key) {
        if (hasProperty(Key))
            return ExperimentalValues.get(Key);
        else
            return null;
    }
    
    
    public boolean hasProperty(String Key) {
        return ExperimentalValues.containsKey(Key);
    }
    
}
