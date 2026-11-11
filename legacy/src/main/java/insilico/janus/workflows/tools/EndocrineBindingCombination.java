package insilico.janus.workflows.tools;

import insilico.core.exception.InitFailureException;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

/**
 *
 * @author Alberto
 */
public class EndocrineBindingCombination {
    
    private final static String COMBINATIONS_PATH = "/resources/binding_combinations.txt";
    
    public final static int E_RBA = 1;
    public final static int E_RBA_ISEXP = 2;
    public final static int E_RME = 3;
    public final static int E_RME_ISEXP = 4;
    public final static int A_RME = 5;
    public final static int A_RME_ISEXP = 6;
    public final static int RV = 7;
    public final static int RC_CODE = 8;

    public ArrayList<double[]> Entries;
    
//    RC_CODE	RC
//    1     1
//    2     IR_A-RME
//    3     IR_E-RBA
//    4     IR_E-RBA-CF
//    5     IR_E-RME-CF
//    6     max(IR_E-RBA; IR_E-RME)
//    7     mean(IR_E-RBA-CF; IR_A-RME)
//    8     mean(IR_E-RBA; IR_A-RME)
//    9     mean(IR_E-RME-CF; IR_A-RME)
//    10    mean[1; IR_A-RME]
//    11    mean[max(IR_E-RBA; IR_E-RME); IR_A-RME]
    
    
    public EndocrineBindingCombination() throws Exception {
        // Load array of data from local resource
        URL src = getClass().getResource(COMBINATIONS_PATH);
        DataInputStream in = new DataInputStream(src.openStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        Entries = new ArrayList<>();
        String CurLine;
        br.readLine(); // skip first line - headers
        while ((CurLine = br.readLine()) != null) {
            String[] buf = CurLine.split("\\t");
            if (buf.length < 9)
                throw new Exception("Wrong number of columns");
            double[] values = new double[buf.length];
            for (int i=0; i<buf.length; i++)
                values[i] = Double.valueOf(buf[i]);  
            Entries.add(values);
        }
        
    }
    
}
