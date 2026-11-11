package insilico.janus.launcher;

import insilico.janus.api.JanusJsonAPI;
import insilico.janus.cli.JanusCLI;
import insilico.janus.gui.JanusGUI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Alberto
 */
public class Launcher {
    
    public final static String VERSION = "1.0.4";
    public final static String BUILD_DATE = "08/10/2020";
    protected final static Logger LOGGER = Logger.getLogger(Launcher.class.getName());
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {

//        JanusJsonAPI API = new JanusJsonAPI();
//        String r = API.GetWorkflowMutagenicity("c1ccccc1");
//        System.out.println(r);
//        if (1==1) return;
        
        
        LOGGER.setLevel(Level.OFF);
//        InsilicoLogger.InitLoggerOff();

        // check what should be run
        boolean StartGUI;
        boolean AdvancedOptions;
        if (args.length == 0) {
            StartGUI = true;
            AdvancedOptions = false;
        } else {
            StartGUI = false;
            AdvancedOptions = false;
            for (String s : args) {
                if (s.compareToIgnoreCase("-adv") == 0) {
                    StartGUI = true;
                    AdvancedOptions = true;
                    break;
                }
            }
        }
        
        if (StartGUI) {
        
            // run the GUI version
            JanusGUI gui = new JanusGUI();
            gui.setAdvancedOptions(AdvancedOptions);
            gui.setVisible(true);
        
        } else {
            
            // command line version for batch processing
            System.out.println("Janus - ver. " + VERSION + " - build date " + BUILD_DATE);
            System.out.println();
            
            String in = null;
            String out = null;
            String metabolites = "no";
            
            for (String s : args) {
                String[] ss = s.split("=");
                if ( (ss[0].compareToIgnoreCase("-h")==0) || (ss[0].compareToIgnoreCase("-help")==0) ) {
                    System.out.println("Usage: Janus -source=[SOURCE FILE] -dest=[DESTINATION DIRECTORY] -metabolism=[yes/no]");
                    System.out.println();
                    return;
                }
                if (ss.length != 2) continue;
                if (ss[0].compareToIgnoreCase("-source") == 0)
                    in = ss[1];
                if (ss[0].compareToIgnoreCase("-dest") == 0)
                    out = ss[1];
                if (ss[0].compareToIgnoreCase("-metabolism") == 0)
                    metabolites = ss[1];
            }

            if (in == null) {
                System.out.println("*** No input file provided");
                return;
            }
            if (out == null) {
                System.out.println("*** No destination file provided");
                return;
            }
            
            try {
                JanusCLI CLI = new JanusCLI(in, out, metabolites.compareToIgnoreCase("yes")==0);
                CLI.Process();
            } catch (Throwable e) {
                System.out.println("*** Fatal error");
                System.out.println("*** " + e.getMessage());
            }
        }
    }
    
}
