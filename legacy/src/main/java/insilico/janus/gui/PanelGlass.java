package insilico.janus.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import javax.swing.JPanel;

/**
 * Panel for hiding other components in a frame (when disabled)
 *  
 * @author Lorenzo Bisi, Alberto Manganaro <a.manganaro@kode-solutions.net>
 */
public class PanelGlass extends JPanel{

    
    public PanelGlass() {
        setOpaque(false);  
        addMouseListener(new MouseAdapter() {});
        addMouseMotionListener(new MouseMotionAdapter() {});
        addKeyListener(new KeyAdapter() {});  
    }
    
    
    @Override
    public  void paintComponent(Graphics g)  {  
        //Fill a rectangle with the 15% grey color  
        g.setColor(new Color(0.05f, 0.05f, 0.05f, 0.1f));  
        g.fillRect(0, 0, this.getWidth(),getHeight());  
    } 
    
}
