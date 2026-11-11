package insilico.janus.cli;

import insilico.core.exception.GenericFailureException;
import insilico.core.exception.InitFailureException;
import insilico.core.molecule.InsilicoMolecule;
import insilico.core.molecule.conversion.SmilesMolecule;
import insilico.core.tools.utils.GeneralUtilities;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusResult;
import insilico.janus.JanusWorkflowResult;
import insilico.janus.gui.iJanusModelRunnerMessenger;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;

/**
 *
 * @author Alberto
 */
public class JanusCLI {

    private final File Input;
    private final File OutputDir;
    private final boolean Metabolism;
    private final JanusEngine engine;

    private final PrintWriter MainOut;
    private final PrintWriter OutP;
    private final PrintWriter OutB;
    private final PrintWriter OutT;
    private final PrintWriter OutC;
    private final PrintWriter OutM;
    private final PrintWriter OutR;
    private final PrintWriter OutED;

    private final DecimalFormat Format_2Dgt;
    private final DecimalFormat Format_3Dgt;
    
    
    public JanusCLI(String InputFile, String OutputDirectory, boolean UseMetabolism) 
            throws InitFailureException {
        
        System.out.println("* Initializing Janus...");
        
        DecimalFormatSymbols InternationalSymbols = new DecimalFormatSymbols();
        InternationalSymbols.setDecimalSeparator('.');         
        Format_2Dgt = new DecimalFormat("0.##", InternationalSymbols);        
        Format_3Dgt = new DecimalFormat("0.###", InternationalSymbols);        
        
        Input = new File(InputFile);
        Metabolism = UseMetabolism;
        
        engine = new JanusEngine(true);
                
        if (!Input.exists())
            throw new InitFailureException("Input file " + InputFile + " does not exists.");
        
        Calendar runDate = Calendar.getInstance();
        String dirName = "/janus_run_" + 
                runDate.get(Calendar.YEAR) + "_" + 
                Format_2Dgt.format((runDate.get(Calendar.MONTH)+1)) + "_" + 
                Format_2Dgt.format(runDate.get(Calendar.DAY_OF_MONTH)) + "__" + 
                Format_2Dgt.format(runDate.get(Calendar.HOUR_OF_DAY)) + "_" + 
                Format_2Dgt.format(runDate.get(Calendar.MINUTE)) + "_" + 
                Format_2Dgt.format(runDate.get(Calendar.SECOND));
        OutputDir = new File((new File(OutputDirectory)).getAbsolutePath() + dirName);
        if (!OutputDir.mkdir())
            throw new InitFailureException("Unable to create directory: " + OutputDir);        
        
        try {
            MainOut = new PrintWriter(OutputDir.getAbsolutePath() + "/janus_txt_main_results.txt");
            OutP = new PrintWriter(OutputDir.getAbsolutePath() + "/janus_txt_results_details_P.txt");
            OutB = new PrintWriter(OutputDir.getAbsolutePath() + "/janus_txt_results_details_B.txt");
            OutT = new PrintWriter(OutputDir.getAbsolutePath() + "/janus_txt_results_details_T.txt");
            OutC = new PrintWriter(OutputDir.getAbsolutePath() + "/janus_txt_results_details_C.txt");
            OutM = new PrintWriter(OutputDir.getAbsolutePath() + "/janus_txt_results_details_M.txt");
            OutR = new PrintWriter(OutputDir.getAbsolutePath() + "/janus_txt_results_details_R.txt");
            OutED = new PrintWriter(OutputDir.getAbsolutePath() + "/janus_txt_results_details_ED.txt");
        } catch (FileNotFoundException e) {
            throw new InitFailureException("Unable to write the output file in " + OutputDir);
        }      
        
    }
    
    
    public void Process() throws GenericFailureException {
        
        long startTime = System.currentTimeMillis(); 
                
        System.out.println("* Starting dataset processing...");
        
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(Input));
        } catch (FileNotFoundException e) {
            throw new GenericFailureException("Input file " + Input.getAbsolutePath() + " does not exists.");
        }
        
        // Messenger for progress 
        iJanusModelRunnerMessenger Messenger = new iJanusModelRunnerMessenger() {

            @Override
            public void SendMessage(String msg) {
                System.out.println(msg);
            }

            @Override
            public void UpdateProgress() {
                //
            } 

            @Override
            public void SetDatasetSize(int size) {
                //
            }

            @Override
            public void SendLogMessage(String msg) {
                System.out.println(msg);
            }
        };        
        
        try {
            
            // Output headers
            String headers = "";
            headers += "No.\tId\tSMILES\tP assessment [class]\tP assessment [index]\tP reliability\tP score";
            headers += "\tB assessment [log units]\tB reliability\tB score";
            headers += "\tT assessment [mg/l]\tT reliability\tT score";
            headers += "\tC assessment\tC reliability\tC score";
            headers += "\tM assessment\tM reliability\tM score";
            headers += "\tR assessment\tR reliability\tR score";
            headers += "\tED assessment [class]\tED assessment [index]\tED reliability\tED score";
            headers += "\tSCORE(vPvB)\tSCORE(SVHC)\tSCORE(PBT)\tRemarks";        
            MainOut.println(headers);
            
            String CurLine;
            int idx = 0; 
            int orig_idx = 0;
            int mol_idx = 0;
            
            // init engine
            engine.SetMessenger(Messenger);
            engine.setMetabolismCalculation(Metabolism);
            
            
            // cycle on each molecule from the input file            
            boolean FirstRes = true;
            while ( ((CurLine = br.readLine())!=null) ) {

                if ( (CurLine.equalsIgnoreCase("")) || (CurLine.equalsIgnoreCase("\n")))
                    continue;
                String s = GeneralUtilities.TrimString(CurLine);
                String[] ss = s.split("\t");
                
                // Read molecule and exp
                orig_idx++;
                System.out.println();
                System.out.println("* Processing molecule " + orig_idx);
                InsilicoMolecule CurMol = SmilesMolecule.Convert(ss[0]);
                
                if (!CurMol.IsValid()) {
                    System.out.println("*** Invalid molecule: " + CurMol.GetErrors().GetMessages());
                    continue;
                }
                
                mol_idx++;
                CurMol.SetId("Mol " + mol_idx);
                
                ExperimentalValues CurExp = new ExperimentalValues(CurLine);
                
                // Process molecule
                ArrayList<InsilicoMolecule> inMol = new ArrayList<>();
                inMol.add(CurMol);
                ArrayList<ExperimentalValues> inExp = new ArrayList<>();
                inExp.add(CurExp);
                ArrayList<JanusResult> Results = engine.Calculate(inMol, inExp);
                
                for (JanusResult res : Results) {
                    idx++;
                    res.Num = idx;
                }
                
                // Save output (can be multiple molecules as metabolism
                // could have been applied to input molecule
                PrintMainResults(Results);
                PrintProperty(Results, OutP, "P", FirstRes);
                PrintProperty(Results, OutB, "B", FirstRes);
                PrintProperty(Results, OutT, "T", FirstRes);
                PrintProperty(Results, OutC, "C", FirstRes);
                PrintProperty(Results, OutM, "M", FirstRes);
                PrintProperty(Results, OutR, "R", FirstRes);
                PrintProperty(Results, OutED, "ED", FirstRes);
                if (FirstRes) FirstRes = false;
                
                // flush on some steps (every 5 mols)
                if (idx % 5 == 0) {
                    MainOut.flush();
                    OutP.flush(); OutB.flush(); OutT.flush(); 
                    OutC.flush(); OutM.flush(); OutR.flush(); 
                    OutED.flush();
                }
                
            }
            
            // Finalize output
            MainOut.close();
            OutP.close();
            OutB.close();
            OutT.close();
            OutC.close();
            OutM.close();
            OutR.close();
            OutED.close();
            
            long elapsedTimeMillis = System.currentTimeMillis() - startTime;
            float elapsedTimeSec = ((int)elapsedTimeMillis/1000F) % 60;
            float elapsedTimeMin = elapsedTimeMillis/(60*1000F);
            String ElapsedTime = "Total elapsed time: " + (int) elapsedTimeMin +
                " min, " + (int) elapsedTimeSec + " sec.";
            
            System.out.println();
            System.out.println("* Done.");
            System.out.println("* " + ElapsedTime);
            
        } catch (IOException | InitFailureException e) {
            throw new GenericFailureException(e);
        }       
    }
    
    
    private void PrintMainResults(ArrayList<JanusResult> Results) {
        
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
            MainOut.println(msg);
        }
        
    }
    
    private void PrintProperty(ArrayList<JanusResult> Results, PrintWriter Out, 
            String Property, boolean PrintHeaders) {
        
        JanusWorkflowResult r = GetWorkflowResult(Results.get(0), Property);
        
        if (PrintHeaders) {
            String headers = "";
            headers += "No.\tId\tSMILES";
            headers += "\tAssessment\tAssessment (numerical value)\tReliability\tScore";
            for (String k : r.AdditionalValues.keySet())
                headers += "\t" + k;
            headers += "\tRemarks";        
            Out.println(headers);
        }
        
        for (JanusResult res : Results) {
            String msg = "";
            msg += res.Num + "\t" + res.Id + "\t" + res.SMILES;
            
            r = GetWorkflowResult(res, Property);
            msg += "\t" + r.PredictionClass + "\t" + Format_3D(r.Prediction) + "\t" + Format_2D(r.Reliability) + "\t" + Format_3D(r.Score);
            for (String k : r.AdditionalValues.keySet())
                msg += "\t" + r.AdditionalValues.get(k);
            msg += "\t" + res.Notes;
            Out.println(msg);
        }        
                
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
    
    private String Format_3D(double val) {
        if (val == JanusResult.MISSING_VALUE) return "n.a.";
        return Format_3Dgt.format(val);
    }
    
    private String Format_2D(double val) {
        if (val == JanusResult.MISSING_VALUE) return "n.a.";
        return Format_2Dgt.format(val);
    }
    
    
}
