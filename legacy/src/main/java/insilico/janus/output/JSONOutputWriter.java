
package insilico.janus.output;

import insilico.janus.JanusResult;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

/**
 *
 * @author Alberto
 */
public class JSONOutputWriter {

    private final ArrayList<JanusResult> res;
    private final DecimalFormat Format_0D;
    private final DecimalFormat Format_2D;
    private final DecimalFormat Format_3D;
    private final DecimalFormat Format_4D;
    private final DecimalFormat Format_6D;
    
    public JSONOutputWriter(ArrayList<JanusResult> Results) {
        this.res = Results;

        DecimalFormatSymbols InternationalSymbols =
            new DecimalFormatSymbols();
        InternationalSymbols.setDecimalSeparator('.');         
        Format_0D = new DecimalFormat("0", InternationalSymbols);
        Format_2D = new DecimalFormat("0.##", InternationalSymbols);
        Format_3D = new DecimalFormat("0.###", InternationalSymbols);
        Format_4D = new DecimalFormat("0.####", InternationalSymbols);
        Format_6D = new DecimalFormat("0.######", InternationalSymbols);
        
    }
    
    public String GenerateJSON() throws IOException {

        String s = "[";
        String line = "";
        for (JanusResult r : res) {
            if (!line.isEmpty()) {
                line += ",";
                s += line;
            }
            line = "{";
            line += "\"num\":" + r.Num + ",";
            line += "\"id\":\"" + r.Id + "\",";
            line += "\"smiles\":\"" + r.SMILES + "\",";
            line += "\"ismetabolite\":\"" + r.isMetabolite + "\",";
            line += "\"parent\":\"" + r.ParentNum + "\",";
            line += "\"metabolites\":\"";
            String meta = "";
            for (Integer i : r.Metabolites) {
                if (!meta.isEmpty()) meta += ",";
                meta += i;
            }
            line += meta + "\",";
            line += "\"notes\":\"" + r.Notes + "\",";
            line += "\"valid\":\"" + r.Valid + "\",";

            line += "\"s_vpvb\":\"" + Format_3D.format(r.S_vPvB) + "\",";
            line += "\"s_pbt\":\"" + Format_3D.format(r.S_PBT) + "\",";
            line += "\"s_svhc\":\"" + Format_3D.format(r.S_SVHC) + "\",";
            
            String val;
            if (r.P.Prediction<10) val = Format_2D.format(r.P.Prediction);
            else val = Format_0D.format(r.P.Prediction);
            line += "\"p_ass\":" + val + ",";
            line += "\"p_cla\":\"" + r.P.PredictionClass + "\",";
            line += "\"p_rel\":" + Format_2D.format(r.P.Reliability) + ",";
            line += "\"p_sco\":" + Format_3D.format(r.P.Score) ;
            if (!r.P.AdditionalValues.isEmpty()) {
                line += "," + "\"p_add\":[";
                boolean isFirst = true;
                for (String k : r.P.AdditionalValues.keySet()) {
                    if (isFirst) isFirst = false; else line += ",";                    
                    line += "{\"name\":\"" + k + "\",";
                    line += "\"value\":\"" + r.P.AdditionalValues.get(k) + "\"}";
                }
                line += "]";
            }

            line += "," + "\"b_ass\":" + Format_2D.format(r.B.Prediction) + ",";
            line += "\"b_rel\":" + Format_2D.format(r.B.Reliability) + ",";
            line += "\"b_sco\":" + Format_3D.format(r.B.Score);
            if (!r.B.AdditionalValues.isEmpty()) {
                line += "," + "\"b_add\":[";
                boolean isFirst = true;
                for (String k : r.B.AdditionalValues.keySet()) {
                    if (isFirst) isFirst = false; else line += ",";                    
                    line += "{\"name\":\"" + k + "\",";
                    line += "\"value\":\"" + r.B.AdditionalValues.get(k) + "\"}";
                }
                line += "]";
            }

            line += ",";
            val = "";
            if (r.T.Prediction<0.0001) val = Format_6D.format(r.T.Prediction);
            else if (r.T.Prediction<0.01) val = Format_4D.format(r.T.Prediction);
            else if (r.T.Prediction<10) val = Format_2D.format(r.T.Prediction);
            else val = Format_0D.format(r.T.Prediction);
            line += "\"t_ass\":" + val + ",";
            line += "\"t_rel\":" + Format_2D.format(r.T.Reliability) + ",";
            line += "\"t_sco\":" + Format_3D.format(r.T.Score);
            if (!r.T.AdditionalValues.isEmpty()) {
                line += "," + "\"t_add\":[";
                boolean isFirst = true;
                for (String k : r.T.AdditionalValues.keySet()) {
                    if (isFirst) isFirst = false; else line += ",";                    
                    line += "{\"name\":\"" + k + "\",";
                    line += "\"value\":\"" + r.T.AdditionalValues.get(k) + "\"}";
                }
                line += "]";
            }

            line += "," + "\"c_ass\":" + ((r.C.Score > 0.5) ? Format_2D.format(r.C.Prediction) : r.C.Prediction) + ",";
            line += "\"c_cla\":\"" + r.C.PredictionClass + "\",";
            line += "\"c_rel\":" + Format_2D.format(r.C.Reliability) + ",";
            line += "\"c_sco\":" + Format_3D.format(r.C.Score);
            if (!r.C.AdditionalValues.isEmpty()) {
                line += "," + "\"c_add\":[";
                boolean isFirst = true;
                for (String k : r.C.AdditionalValues.keySet()) {
                    if (isFirst) isFirst = false; else line += ",";                    
                    line += "{\"name\":\"" + k + "\",";
                    line += "\"value\":\"" + r.C.AdditionalValues.get(k) + "\"}";
                }
                line += "]";
            }
            
            line += "," + "\"m_ass\":" + r.M.Prediction + ",";
            line += "\"m_cla\":\"" + r.M.PredictionClass + "\",";
            line += "\"m_rel\":" + Format_2D.format(r.M.Reliability) + ",";
            line += "\"m_sco\":" + Format_3D.format(r.M.Score);
            if (!r.M.AdditionalValues.isEmpty()) {
                line += "," + "\"m_add\":[";
                boolean isFirst = true;
                for (String k : r.M.AdditionalValues.keySet()) {
                    if (isFirst) isFirst = false; else line += ",";                    
                    line += "{\"name\":\"" + k + "\",";
                    line += "\"value\":\"" + r.M.AdditionalValues.get(k) + "\"}";
                }
                line += "]";
            }
            
            line += "," + "\"r_ass\":" + r.R.Prediction + ",";
            line += "\"r_cla\":\"" + r.R.PredictionClass + "\",";
            line += "\"r_rel\":" + Format_2D.format(r.R.Reliability) + ",";
            line += "\"r_sco\":" + Format_3D.format(r.R.Score);
            if (!r.R.AdditionalValues.isEmpty()) {
                line += "," + "\"r_add\":[";
                boolean isFirst = true;
                for (String k : r.R.AdditionalValues.keySet()) {
                    if (isFirst) isFirst = false; else line += ",";                    
                    line += "{\"name\":\"" + k + "\",";
                    line += "\"value\":\"" + r.R.AdditionalValues.get(k) + "\"}";
                }
                line += "]";
            }
            
            line += "," + "\"ed_ass\":" + r.ED.Prediction + ",";
            line += "\"ed_cla\":\"" + r.ED.PredictionClass + "\",";
            line += "\"ed_rel\":" + Format_2D.format(r.ED.Reliability) + ",";
            line += "\"ed_sco\":" + Format_3D.format(r.ED.Score);
            if (!r.ED.AdditionalValues.isEmpty()) {
                line += "," + "\"ed_add\":[";
                boolean isFirst = true;
                for (String k : r.ED.AdditionalValues.keySet()) {
                    if (isFirst) isFirst = false; else line += ",";                    
                    line += "{\"name\":\"" + k + "\",";
                    line += "\"value\":\"" + r.ED.AdditionalValues.get(k) + "\"}";
                }
                line += "]";
            }
            
            
            line += "}";
        }
        s += line + "]";
        
        return(s);
    }
    
}
