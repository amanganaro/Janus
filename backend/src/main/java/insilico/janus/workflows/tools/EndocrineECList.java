package insilico.janus.workflows.tools;

import insilico.core.exception.InvalidMoleculeException;
import insilico.core.molecule.InsilicoMolecule;
import insilico.core.molecule.conversion.SmilesMolecule;
import insilico.core.similarity.Similarity;
import insilico.core.similarity.SimilarityDescriptors;
import insilico.core.similarity.SimilarityDescriptorsBuilder;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

/**
 *
 * @author Alberto
 */
public class EndocrineECList {
    
    private class SimMol {
        InsilicoMolecule molecule;
        SimilarityDescriptors simDescriptors;
    }
    
    private final static String LIST_PATH = "/resources/ed_ec.txt";
    
    private final SimilarityDescriptorsBuilder SimDescBuilder;
    private final Similarity SimEngine;
    private final ArrayList<SimMol> EC;
    
    
    public EndocrineECList() throws IOException  {
        
        SimDescBuilder = new SimilarityDescriptorsBuilder();
        SimEngine = new Similarity();
        
        // load list of compounds
        URL src = getClass().getResource(LIST_PATH);
        DataInputStream in = new DataInputStream(src.openStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        EC = new ArrayList<>();
        String CurLine;
        while ((CurLine = br.readLine()) != null) {
            String[] buf = CurLine.split("\\t");
            InsilicoMolecule m = SmilesMolecule.Convert(buf[0]);
            m.SetId(buf[1]);
            SimMol sm = new SimMol();
            sm.molecule = m;
            sm.simDescriptors = SimDescBuilder.Calculate(m);
            EC.add(sm);
        }
       
    }
    
    
    public InsilicoMolecule Match(InsilicoMolecule target) throws InvalidMoleculeException {
        
        SimilarityDescriptors targetDesc = SimDescBuilder.Calculate(target);
        
        for (SimMol sm : EC) {
            double s = SimEngine.CalculateExactMatches(targetDesc, sm.simDescriptors, 
                    target.GetStructure(), sm.molecule.GetStructure());
            if (s == 1)
                return sm.molecule;
        }
        
        return null;
    }
    
}
