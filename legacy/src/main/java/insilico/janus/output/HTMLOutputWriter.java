package insilico.janus.output;

import insilico.core.molecule.InsilicoMolecule;
import insilico.core.molecule.conversion.SmilesMolecule;
import static insilico.core.molecule.tools.Depiction.DepictMolecule;
import static insilico.core.molecule.tools.Depiction.SaveImageAsPNG;
import insilico.janus.JanusResult;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author Alberto
 */
public class HTMLOutputWriter {

    private final static String HTML_RES = "/resources/html_files.zip";
    
    
    public HTMLOutputWriter(File DestDirectory, ArrayList<JanusResult> Results,
            Calendar runDate) throws Exception {

        // copy all the base HTML files in the dest directory
        URL src = getClass().getResource(HTML_RES);
        unzip(src.openStream(), DestDirectory);
        
        // Generate js for local data
        String IncDir = DestDirectory.getAbsolutePath() + "/assets/js";
        JSONOutputWriter jsonOut = new JSONOutputWriter(Results);
        PrintWriter dataOut = new PrintWriter(IncDir + "/dataSource.js");

        DecimalFormat Format_2D = new DecimalFormat("00");
        String docDate = Format_2D.format(runDate.get(Calendar.DAY_OF_MONTH)) + "/" + 
                Format_2D.format((runDate.get(Calendar.MONTH)+1)) + "/" + 
                runDate.get(Calendar.YEAR);

        dataOut.println("getResults = function() {");
        dataOut.println("return '" + jsonOut.GenerateJSON() + "';");
        dataOut.println("}");
        dataOut.println("getDate = function() {");
        dataOut.println("return '" + docDate + "';");
        dataOut.println("}");
        dataOut.close();
        
        // generate images
        String ImgDir = DestDirectory.getAbsolutePath() + "/assets/img";
        File ImgDirFile = new File(ImgDir);
        if (!ImgDirFile.mkdir())
            throw new IOException("Unable to create directory: " + ImgDir);        
        for (JanusResult r : Results) {            
            try {
                InsilicoMolecule m = SmilesMolecule.Convert(r.SMILES);
                String id = ImgDir + "/" + r.Num + ".png";
                BufferedImage img = DepictMolecule(m, 400, 400);
                SaveImageAsPNG(img, id);
            } catch (Exception e) {
                // in caso di errore?
            }
        }
    }
    
 
    /**
     * @param source zip stream
     * @param target target directory
     * @throws IOException extraction failed
     */
    public static void unzip(InputStream source, File target) throws IOException {
        final ZipInputStream zipStream = new ZipInputStream(source);
        ZipEntry nextEntry;
        while ((nextEntry = zipStream.getNextEntry()) != null) {
            final String name = nextEntry.getName();
            // only extract files
            if (!name.endsWith("/")) {
                final File nextFile = new File(target, name);

                // create directories
                final File parent = nextFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }

                // write file
                try (OutputStream targetStream = new FileOutputStream(nextFile)) {
                    copy(zipStream, targetStream);
                }
            }
        }
    }

    
    private static void copy(final InputStream source, final OutputStream target) throws IOException {
        final int bufferSize = 4 * 1024;
        final byte[] buffer = new byte[bufferSize];

        int nextCount;
        while ((nextCount = source.read(buffer)) >= 0) {
            target.write(buffer, 0, nextCount);
        }
    }    
    
}
