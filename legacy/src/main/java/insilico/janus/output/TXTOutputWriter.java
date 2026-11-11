package insilico.janus.output;

import insilico.janus.JanusResult;
import insilico.janus.JanusWorkflowResult;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

/**
 *
 * @author Alberto
 */
public class TXTOutputWriter {

    private final DecimalFormat Format_2Dgt;
    private final DecimalFormat Format_3Dgt;
    
    
    public TXTOutputWriter() {
        DecimalFormatSymbols InternationalSymbols = new DecimalFormatSymbols();
        InternationalSymbols.setDecimalSeparator('.');         
        Format_2Dgt = new DecimalFormat("0.##", InternationalSymbols);        
        Format_3Dgt = new DecimalFormat("0.###", InternationalSymbols);        
    }
    
    
    public void WriteMainResults(ArrayList<JanusResult> Results, String OutputPath)
            throws FileNotFoundException {
        
        PrintWriter out = new PrintWriter(OutputPath);
        
        String headers = "";
        headers += "No.\tId\tSMILES\tP assessment [class]\tP assessment [index]\tP reliability\tP score";
        headers += "\tB assessment [log units]\tB reliability\tB score";
        headers += "\tT assessment [mg/l]\tT reliability\tT score";
        headers += "\tC assessment\tC reliability\tC score";
        headers += "\tM assessment\tM reliability\tM score";
        headers += "\tR assessment\tR reliability\tR score";
        headers += "\tED assessment [class]\tED assessment [index]\tED reliability\tED score";
        headers += "\tSCORE(vPvB)\tSCORE(SVHC)\tSCORE(PBT)\tRemarks";        
        out.println(headers);
     
        for (JanusResult res : Results) {
            String msg = "";
            msg += res.Num + "\t" + res.Id + "\t" + res.SMILES;
            msg += "\t" + res.P.PredictionClass + "\t" + Format_3D(res.P.Prediction) + "\t" + Format_2D(res.P.Reliability) + "\t" + Format_3D(res.P.Score);
            msg += "\t" + Format_3D(res.B.Prediction) + "\t" + Format_2D(res.B.Reliability) + "\t" + Format_3D(res.B.Score);
            msg += "\t" + res.T.Prediction + "\t" + Format_2D(res.T.Reliability) + "\t" + Format_3D(res.T.Score);
            msg += "\t" + res.C.PredictionClass + "\t" + Format_2D(res.C.Reliability) + "\t" + Format_3D(res.C.Score);
            msg += "\t" + res.M.PredictionClass + "\t" + Format_2D(res.M.Reliability) + "\t" + Format_3D(res.M.Score);
            msg += "\t" + res.R.PredictionClass + "\t" + Format_2D(res.R.Reliability) + "\t" + Format_3D(res.R.Score);
            msg += "\t" + res.ED.PredictionClass + "\t" + res.ED.Prediction + "\t" + Format_2D(res.ED.Reliability) + "\t" + Format_3D(res.ED.Score);
            msg += "\t" + Format_3D(res.S_vPvB) + "\t" + Format_3D(res.S_SVHC) + "\t" + Format_3D(res.S_PBT) + "\t" + res.Notes;
            out.println(msg);
        }

        out.close();
    }
        
    
    public void WriteProperty(ArrayList<JanusResult> Results, String Property,
            String OutputPath) throws FileNotFoundException {
        
        PrintWriter out = new PrintWriter(OutputPath);
        
        JanusWorkflowResult r = GetWorkflowResult(Results.get(0), Property);
        
        String headers = "";
        headers += "No.\tId\tSMILES";
        headers += "\tAssessment\tAssessment (numerical value)\tReliability\tScore";
        for (String k : r.AdditionalValues.keySet())
            headers += "\t" + k;
        headers += "\tRemarks";        
        out.println(headers);
     
        for (JanusResult res : Results) {
            String msg = "";
            msg += res.Num + "\t" + res.Id + "\t" + res.SMILES;
            
            r = GetWorkflowResult(res, Property);
            msg += "\t" + r.PredictionClass + "\t" + Format_3D(r.Prediction) + "\t" + Format_2D(r.Reliability) + "\t" + Format_3D(r.Score);
            for (String k : r.AdditionalValues.keySet())
                msg += "\t" + r.AdditionalValues.get(k);
            msg += "\t" + res.Notes;
            out.println(msg);
        }

        out.close();
    }    
    
    
    private String Format_3D(double val) {
        if (val == JanusResult.MISSING_VALUE) return "n.a.";
        return Format_3Dgt.format(val);
    }
    
    private String Format_2D(double val) {
        if (val == JanusResult.MISSING_VALUE) return "n.a.";
        return Format_2Dgt.format(val);
    }
    
    private JanusWorkflowResult GetWorkflowResult(JanusResult Res, String Property) {
        if (Property.equalsIgnoreCase("P")) return Res.P;
        if (Property.equalsIgnoreCase("B")) return Res.B;
        if (Property.equalsIgnoreCase("T")) return Res.T;
        if (Property.equalsIgnoreCase("C")) return Res.C;
        if (Property.equalsIgnoreCase("M")) return Res.M;
        if (Property.equalsIgnoreCase("R")) return Res.R;
        if (Property.equalsIgnoreCase("ED")) return Res.ED;
        return null;
    }
    
}
