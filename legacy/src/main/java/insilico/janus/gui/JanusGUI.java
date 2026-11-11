package insilico.janus.gui;

import insilico.core.exception.GenericFailureException;
import insilico.core.molecule.InsilicoMolecule;
import insilico.core.molecule.conversion.file.MoleculeFileSmiles;
import insilico.core.tools.utils.GeneralUtilities;
import insilico.janus.ExperimentalValues;
import insilico.janus.JanusEngine;
import insilico.janus.JanusResult;
import insilico.janus.launcher.Launcher;
import insilico.janus.output.HTMLOutputWriter;
import insilico.janus.output.TXTOutputWriter;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.DefaultCaret;

/**
 *
 * @author Alberto
 */
public class JanusGUI extends javax.swing.JFrame {

    // Molecules list
    private ArrayList<InsilicoMolecule> DataSet;
    private ArrayList<ExperimentalValues> DataSetExperimentalValues;
    
    private ArrayList<JanusResult> Results;

    // Dir for output results
    private File OutputDir;
    private File OutputFolder;
        
    // Time for calculation
    private long startTime;
    private String ElapsedTime;
    private Calendar runDate;
    
    private final PanelGlass GlassPanel;
    private final FrameAbout About;    
    private final DialogAdvancedOptions AdvOptions;

    
    
    /**
     * Creates new form JanusGUI
     */
    public JanusGUI() {
        initComponents();

        this.setTitle("JANUS - version " + Launcher.VERSION);
        
        // Set glass panel
        GlassPanel = new PanelGlass();
        this.setGlassPane(GlassPanel);
        
        // Center the form
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        int w = this.getSize().width;
        int h = this.getSize().height;
        int x = (dim.width-w)/2;
        int y = (dim.height-h)/2;
        this.setLocation(x, y);        
        
        // Create about frame
        About = new FrameAbout();
        About.setVisible(false);
        
        // Create advanced options dialog
        AdvOptions = new DialogAdvancedOptions(this, true);

        jLabelInfo.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent evt) {
                jLabelInfo.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                About.setLocationRelativeTo(null);
                About.setVisible(true);
            }
        });
        
        jLabelAdv.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent evt) {
                jLabelAdv.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                AdvOptions.setLocationRelativeTo(null);
                AdvOptions.setVisible(true);
            }
        });
                
        // autoscroll for the log textarea
        DefaultCaret caret = (DefaultCaret)jTextAreaLog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);        
        
        runDate = Calendar.getInstance();
    }

    public void setAdvancedOptions(boolean status) {
        this.jLabelAdv.setVisible(status);
    }    
    
    private void setEnableGUI(boolean value){
        if(!value){
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            GlassPanel.setVisible(true); 
        }
        else{
            GlassPanel.setVisible(false);  
            this.setCursor(Cursor.getDefaultCursor());
        }
    }

    
    private void SelectOutputDir() {

        try{            
            JFileChooser fc = new JFileChooser();        
            fc.setMultiSelectionEnabled(false);
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int returnVal = fc.showSaveDialog(null);
            OutputDir = fc.getSelectedFile();
            this.jLabelOutput.setText(OutputDir.getAbsolutePath());
        }catch(NullPointerException e) {
            //Cancel    
        }
        
    }
    
    private void LoadFile() {
      
        // Filters for file dialog

        class SMILESFilter extends FileFilter {

            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }

                String ext = null;
                String s = f.getName();
                int i = s.lastIndexOf('.');
                if (i > 0 &&  i < s.length() - 1) 
                    ext = s.substring(i+1).toLowerCase();
                
                if (ext != null) {
                    if (ext.equals("smi") || ext.equals("txt")) 
                        return true;
                    else 
                        return false;
                }

                return false;
            }

            @Override
            public String getDescription() {
                return "SMILES files (.smi, .txt)";
            }
        }        
        
        // Shows file dialogs and let user choose file
        
        JFileChooser fc = new JFileChooser();
        fc.setAcceptAllFileFilterUsed(false);
        fc.addChoosableFileFilter(new SMILESFilter());
        fc.setDialogTitle("Select molecule file");
        int res = fc.showOpenDialog(this);
        if ((res == JFileChooser.CANCEL_OPTION) || (res == JFileChooser.ERROR_OPTION))
            return;
        final File selFile = fc.getSelectedFile();
        
        
        // Loads file
      
        class SWLoadMolFile extends SwingWorker<Object, Object> {
            
            private boolean Error = false;
            public JanusGUI parent;
                
            
            @Override
            protected Object doInBackground() throws Exception {
                 
                Error = false;
                
                try {
                    
                    int fSMILES = -1, fCAS = -1, fId = -1;

                    // Check if files contains more than one column
                    int nFields = -1;

                    BufferedReader br = new BufferedReader(new FileReader(selFile));
                    String CurLine;
                    int idx = 1;
                    while ( ((CurLine = br.readLine())!=null) ) {
                        idx++;
                        if ( (CurLine.equalsIgnoreCase("")) || (CurLine.equalsIgnoreCase("\n")))
                            continue;
                        String s = GeneralUtilities.TrimString(CurLine);
                        String[] ss = s.split("\t");

                        nFields = Math.max(ss.length, nFields);
                        
//                        if (nFields == -1)
//                            nFields = ss.length;
//                        else {
//                            if (nFields != ss.length) {
//                                throw new MoleculeConversionException("File contains unsequal number of fields in some rows");                                        
//                            }
//                        }
                    }
                    br.close();

                    if (nFields > 1) {
                        DialogSMILES dlgSMILES = new DialogSMILES(parent, true, selFile, nFields);
                        dlgSMILES.setLocationRelativeTo(parent);
                        dlgSMILES.setVisible(true);
                        if (!dlgSMILES.Error) {
                            fSMILES = dlgSMILES.fieldSMILES;
                            fCAS = dlgSMILES.fieldCAS;
                            fId = dlgSMILES.fieldId;
                        }
                    }

                    MoleculeFileSmiles SMIReader = new MoleculeFileSmiles();
                    SMIReader.setCASField(fCAS);
                    SMIReader.setIdField(fId);
                    SMIReader.setSmilesField(fSMILES);
                    SMIReader.OpenFile(selFile.getAbsolutePath());
                    DataSet = SMIReader.ReadAll();
                    SMIReader.CloseFile();
                    
                    // read possible experimental values in a separate structure
                    DataSetExperimentalValues = new ArrayList<>();
                    br = new BufferedReader(new FileReader(selFile));
                    while ((CurLine = br.readLine())!=null) {
                        if ( (CurLine.equalsIgnoreCase("")) || (CurLine.equalsIgnoreCase("\n")))
                            continue;
                        ExperimentalValues curExp = new  ExperimentalValues(CurLine);
                        DataSetExperimentalValues.add(curExp);
                    }
                    br.close();
                    
                    if (DataSet.size() != DataSetExperimentalValues.size())
                        throw new Exception("different number of records between the dataset and the experimental values");
                    
                    parent.jLabelLoadedFile.setText(selFile.getName() + " (" + DataSet.size() + " molecules)");
                    
                } catch (Throwable e) {
                    JOptionPane.showMessageDialog(null, 
                            "Error while importing File.\nReported error: " + 
                            e.getMessage());
                    Error = true;
                }

                return null;
                
            }
            
 
            @Override
            protected void done() { 
                if (isCancelled()) {
                    JOptionPane.showMessageDialog(null,
                            "File import stopped.");
                } else if (!Error) {
                    JOptionPane.showMessageDialog(null, 
                            "File successfully loaded.\n" + 
                            DataSet.size() + " molecules imported");
                }
                setEnableGUI(true);
            }

        }
            
        setEnableGUI(false);
        SWLoadMolFile worker = new SWLoadMolFile();
        worker.parent = this;
        worker.execute();
    }    
    
    
    private void RunCalculation() {
        
        // Initial check
        if (DataSet == null) {
            JOptionPane.showMessageDialog(this, "No input file selected.");
            return;
        }
        if (DataSet.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No molecules provided as input");
            return;
        }
        if ( (!jCheckBoxOutTxt.isSelected()) && (!jCheckBoxOutHTML.isSelected()) ) {
            JOptionPane.showMessageDialog(this, "No output format selected");
            return;                        
        }
        if (OutputDir == null) {
            JOptionPane.showMessageDialog(this, "No output directory selected");
            return;            
        }
        if (!OutputDir.exists()) {
            JOptionPane.showMessageDialog(this, "Output directory does not exist");
            return;            
        }
                
        
        // Builds worker thread
        class SWModelRunner extends SwingWorker<Object, Object> {
            
            private boolean Error;
            
            @Override
            protected Object doInBackground() throws Exception {
               
                //// Models calculation
                startTime = System.currentTimeMillis(); 
                runDate = Calendar.getInstance();

                // Messenger for progress bar
                iJanusModelRunnerMessenger Messenger = new iJanusModelRunnerMessenger() {
                    private String curMsg = "";
                    private int DatasetSize = 0;
                    
                    @Override
                    public void SendMessage(String msg) {
                        curMsg = msg;
                        ProgressBar.setValue(ProgressBar.getMinimum());
                    }

                    @Override
                    public void UpdateProgress() {
                        ProgressBar.setValue(ProgressBar.getValue() + 1);
                        ProgressBar.setString(curMsg + " - " + ProgressBar.getValue() 
                                + "/" + this.DatasetSize );
                    } 

                    @Override
                    public void SetDatasetSize(int size) {
                        this.DatasetSize = size;
                        ProgressBar.setMinimum(0);
                        ProgressBar.setMaximum(this.DatasetSize);
                    }

                    @Override
                    public void SendLogMessage(String msg) {
                        jTextAreaLog.append(msg + "\n");
                    }
                };

                ProgressBar.setIndeterminate(false);
                ProgressBar.setStringPainted(true);
                ProgressBar.setValue(0);
                jTextAreaLog.setText("");
                
                Error = false;
                
                try {
                    
                    JanusEngine engine = new JanusEngine(false);
                    engine.SetMessenger(Messenger);
                    engine.setMetabolismCalculation(jCheckBoxMetabolism.isSelected());
                    engine.Workflows = AdvOptions.Workflows;
                    Results = engine.Calculate(DataSet, DataSetExperimentalValues);
                    
                } catch (Throwable e) {
                    Error = true;
                    jTextAreaLog.append("An error has occurred - " + e.getMessage() + "\n");
                    JOptionPane.showMessageDialog(null, "An error has occurred - " + e.getMessage());                            
                }

                return null;
            }

  
            @Override 
            protected void done() { 
                if (isCancelled()) {
                    Thread.interrupted();
                }  else {
                    if (!Error)
                        SaveResults();
                }
                setEnableGUI(true);
            }
    
        }
                  
        setEnableGUI(false);
        final SWModelRunner SW = new SWModelRunner();
        SW.execute();         
    }
    
    
    private void SaveResults() {
        
        // SwingWorker for creating reports
        class SRreports extends SwingWorker<Object, Object> {

            @Override
            protected Object doInBackground() throws Exception {
              
                ProgressBar.setIndeterminate(true);
                ProgressBar.setString("Generating output");

                try {
                
                    // create dest directory with name set as run date+time      
                    DecimalFormat Format_2D = new DecimalFormat("00");
                    String dirName = "/janus_run_" + 
                            runDate.get(Calendar.YEAR) + "_" + 
                            Format_2D.format((runDate.get(Calendar.MONTH)+1)) + "_" + 
                            Format_2D.format(runDate.get(Calendar.DAY_OF_MONTH)) + "__" + 
                            Format_2D.format(runDate.get(Calendar.HOUR_OF_DAY)) + "_" + 
                            Format_2D.format(runDate.get(Calendar.MINUTE)) + "_" + 
                            Format_2D.format(runDate.get(Calendar.SECOND));
                    OutputFolder = new File(OutputDir.getAbsolutePath() + dirName);
                    if (!OutputFolder.mkdir())
                        throw new IOException("Unable to create directory: " + OutputFolder);        
                    
                    // create TXT output
                    if (jCheckBoxOutTxt.isSelected()) {
                        TXTOutputWriter oTxt = new TXTOutputWriter();
                        oTxt.WriteMainResults(Results, OutputDir.getAbsolutePath() + dirName + "/janus_txt_main_results.txt");
                        oTxt.WriteProperty(Results, "P", OutputDir.getAbsolutePath() + dirName + "/janus_txt_results_details_P.txt");
                        oTxt.WriteProperty(Results, "B", OutputDir.getAbsolutePath() + dirName + "/janus_txt_results_details_B.txt");
                        oTxt.WriteProperty(Results, "T", OutputDir.getAbsolutePath() + dirName + "/janus_txt_results_details_T.txt");
                        oTxt.WriteProperty(Results, "C", OutputDir.getAbsolutePath() + dirName + "/janus_txt_results_details_C.txt");
                        oTxt.WriteProperty(Results, "M", OutputDir.getAbsolutePath() + dirName + "/janus_txt_results_details_M.txt");
                        oTxt.WriteProperty(Results, "R", OutputDir.getAbsolutePath() + dirName + "/janus_txt_results_details_R.txt");
                        oTxt.WriteProperty(Results, "ED", OutputDir.getAbsolutePath() + dirName + "/janus_txt_results_details_ED.txt");
                    }   
                    
                    // create HTML output
                    if (jCheckBoxOutHTML.isSelected()) {
                        HTMLOutputWriter outHtml = new HTMLOutputWriter(OutputFolder, Results, runDate);
                    }
                                        
                } catch (Throwable e) {
                    JOptionPane.showMessageDialog(null, 
                            "Error while creating output.\nReported error: " + 
                            e.getMessage());
                    jTextAreaLog.append("Error while creating output.\nReported error: " + 
                            e.getMessage() + "\n");
                }
                
                ProgressBar.setIndeterminate(false);
                ProgressBar.setStringPainted(false);
                
                return null;
            }
            

            @Override
            protected void done() { 
                if (isCancelled()) {
                    Thread.interrupted();
                } else {
                    long elapsedTimeMillis = System.currentTimeMillis() - startTime;
                    float elapsedTimeSec = ((int)elapsedTimeMillis/1000F) % 60;
                    float elapsedTimeMin = elapsedTimeMillis/(60*1000F);
                    ElapsedTime = "\nTotal time elapsed: " + (int) elapsedTimeMin +
                        " min, " + (int) elapsedTimeSec + " sec";
                    jTextAreaLog.append("Calculation done." + ElapsedTime);
                }
                setEnableGUI(true);
                try {
                    if (jCheckBoxOpenHTML.isSelected()) OpenHTML(OutputFolder.getAbsolutePath() + File.separator + "index.html");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, e.getMessage());
                }
            }
            
        }
        
    
        final SRreports SR = new SRreports();
        setEnableGUI(false);
        SR.execute();
        
        // Force garbage collector
        System.gc();        
    }
    
    
    public void OpenHTML(String destFile) throws GenericFailureException {
        
        try {

            boolean DesktopSupported = true;
            if (!Desktop.isDesktopSupported()) {
                DesktopSupported = false;
            } else {
                Desktop desktop = Desktop.getDesktop();
                if (!desktop.isSupported(Desktop.Action.OPEN)) {
                    DesktopSupported = false;
                }
            }
            
            if (!DesktopSupported) {
                JOptionPane.showMessageDialog(this, "Unable to open the web browser on your system - please open output results manually.");
                return;            
            }
            
            Desktop desktop = Desktop.getDesktop();
            desktop.open(new File(destFile));
            
        } catch (Exception ex) {
            throw new GenericFailureException("Unable to open file.\n(Error: " + ex.getMessage() + ")");            
        }            
    }
    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanelMain = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jButtonLoad = new javax.swing.JButton();
        jLabelLoadedFile = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jButtonDir = new javax.swing.JButton();
        jLabelOutput = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jCheckBoxOpenHTML = new javax.swing.JCheckBox();
        jCheckBoxMetabolism = new javax.swing.JCheckBox();
        jCheckBoxOutHTML = new javax.swing.JCheckBox();
        jCheckBoxOutTxt = new javax.swing.JCheckBox();
        jButton3 = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jLabelInfo = new javax.swing.JLabel();
        jLabelAdv = new javax.swing.JLabel();
        ProgressBar = new javax.swing.JProgressBar();
        jPanel1 = new javax.swing.JPanel();
        jScrollPaneLog = new javax.swing.JScrollPane();
        jTextAreaLog = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanelMain.setBackground(new java.awt.Color(255, 255, 255));
        jPanelMain.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(102, 51, 0)));

        jLabel1.setText("Loaded dataset:");

        jButtonLoad.setText("Load SMILES file");
        jButtonLoad.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonLoadActionPerformed(evt);
            }
        });

        jLabelLoadedFile.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        jLabelLoadedFile.setText("-");

        jLabel2.setText("Directory for output:");

        jButtonDir.setText("Select directory");
        jButtonDir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonDirActionPerformed(evt);
            }
        });

        jLabelOutput.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        jLabelOutput.setText("-");

        jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(102, 51, 0)));

        jLabel3.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        jLabel3.setText("Options");

        jCheckBoxOpenHTML.setSelected(true);
        jCheckBoxOpenHTML.setText("Open HTML results after calculation");

        jCheckBoxMetabolism.setText("Calculate metabolism for input molecules");

        jCheckBoxOutHTML.setSelected(true);
        jCheckBoxOutHTML.setText("Create the HTML output");
        jCheckBoxOutHTML.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxOutHTMLActionPerformed(evt);
            }
        });

        jCheckBoxOutTxt.setSelected(true);
        jCheckBoxOutTxt.setText("Create the plain text output");
        jCheckBoxOutTxt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxOutTxtActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jCheckBoxMetabolism)
                            .addComponent(jLabel3)
                            .addComponent(jCheckBoxOpenHTML))
                        .addContainerGap(187, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jCheckBoxOutTxt)
                            .addComponent(jCheckBoxOutHTML))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jCheckBoxMetabolism)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jCheckBoxOutTxt)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jCheckBoxOutHTML)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jCheckBoxOpenHTML)
                .addContainerGap(18, Short.MAX_VALUE))
        );

        jButton3.setText(" RUN ");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        jPanel3.setBackground(new java.awt.Color(255, 255, 255));

        jLabelInfo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/ico_question.jpg"))); // NOI18N
        jLabelInfo.setToolTipText("Info about Janus");

        jLabelAdv.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/ico_plus.jpg"))); // NOI18N
        jLabelAdv.setToolTipText("Advanced Options");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(jLabelInfo)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabelAdv)
                .addGap(0, 32, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelInfo, javax.swing.GroupLayout.Alignment.TRAILING)
            .addComponent(jLabelAdv, javax.swing.GroupLayout.Alignment.TRAILING)
        );

        javax.swing.GroupLayout jPanelMainLayout = new javax.swing.GroupLayout(jPanelMain);
        jPanelMain.setLayout(jPanelMainLayout);
        jPanelMainLayout.setHorizontalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMainLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanelMainLayout.createSequentialGroup()
                        .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelMainLayout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabelLoadedFile))
                            .addComponent(jButtonLoad)
                            .addGroup(jPanelMainLayout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabelOutput))
                            .addComponent(jButtonDir))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanelMainLayout.createSequentialGroup()
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanelMainLayout.setVerticalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMainLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabelLoadedFile))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonLoad)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jLabelOutput))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonDir)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton3)
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));
        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        jTextAreaLog.setColumns(20);
        jTextAreaLog.setRows(5);
        jScrollPaneLog.setViewportView(jTextAreaLog);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPaneLog, javax.swing.GroupLayout.DEFAULT_SIZE, 464, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPaneLog, javax.swing.GroupLayout.DEFAULT_SIZE, 113, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelMain, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ProgressBar, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelMain, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    
    private void jButtonLoadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonLoadActionPerformed
        LoadFile();
    }//GEN-LAST:event_jButtonLoadActionPerformed

    
    private void jButtonDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonDirActionPerformed
        SelectOutputDir();
    }//GEN-LAST:event_jButtonDirActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        RunCalculation();
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jCheckBoxOutTxtActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxOutTxtActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jCheckBoxOutTxtActionPerformed

    private void jCheckBoxOutHTMLActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxOutHTMLActionPerformed
        if (jCheckBoxOutHTML.isSelected()) {
            jCheckBoxOpenHTML.setEnabled(true);
        } else {
            jCheckBoxOpenHTML.setSelected(false);
            jCheckBoxOpenHTML.setEnabled(false);
        }
    }//GEN-LAST:event_jCheckBoxOutHTMLActionPerformed

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar ProgressBar;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButtonDir;
    private javax.swing.JButton jButtonLoad;
    private javax.swing.JCheckBox jCheckBoxMetabolism;
    private javax.swing.JCheckBox jCheckBoxOpenHTML;
    private javax.swing.JCheckBox jCheckBoxOutHTML;
    private javax.swing.JCheckBox jCheckBoxOutTxt;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabelAdv;
    private javax.swing.JLabel jLabelInfo;
    private javax.swing.JLabel jLabelLoadedFile;
    private javax.swing.JLabel jLabelOutput;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanelMain;
    private javax.swing.JScrollPane jScrollPaneLog;
    private javax.swing.JTextArea jTextAreaLog;
    // End of variables declaration//GEN-END:variables
}
