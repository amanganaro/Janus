package insilico.janus.eawag.bbd;

import insilico.core.exception.InitFailureException;
import insilico.janus.eawag.EawagBlock;

import java.io.IOException;
import java.net.URL;

/**
 *
 * @author Alberto
 */
public class EawagBBD extends EawagBlock {
    
    public EawagBBD() throws IOException, InitFailureException {
        super();
        
        URL u = getClass().getResource("/resources/bbd.txt");
        this.Init(u.openStream());
    }
    
}
