package gerberViewer;


import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.JLabel;

@SuppressWarnings("serial")
public class CustomColorSchemeDialog extends JDialog {

    private final JPanel contentPanel = new JPanel();
    private ColorScheme colorScheme;
    private JColorChooser colorChooser;
    private String layer = "Background";
    private JPanel layerSelectionPane;
    private JPanel colorChooserPane;
    private JPanel buttonPane;
    private PreviewPanel previewPane;
    
    /**
     * Create the dialog.
     */
    public CustomColorSchemeDialog(Window owner, ColorScheme cs) {
        super(owner, "Custom Color Scheme", ModalityType.APPLICATION_MODAL);
        this.colorScheme = new ColorScheme(cs);
        setBounds(100, 100, 800, 600);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        {
            JPanel panel = new JPanel();
            contentPanel.add(panel);
            panel.setLayout(new BorderLayout(5, 5));
            {
                layerSelectionPane = new JPanel();
                panel.add(layerSelectionPane, BorderLayout.NORTH);
                layerSelectionPane.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
                ButtonGroup buttonGroup = new ButtonGroup();
                {
                    JLabel lblNewLabel = new JLabel("Layer to modify:     ");
                    layerSelectionPane.add(lblNewLabel);
                }
                {
                    JRadioButton rdbtnBackground = new JRadioButton("Background");
                    rdbtnBackground.setSelected(true);
                    rdbtnBackground.addActionListener(layerSelectionAction);
                    buttonGroup.add(rdbtnBackground);
                    layerSelectionPane.add(rdbtnBackground);
                }
                {
                    JRadioButton rdbtnSubstrate = new JRadioButton("Substrate");
                    rdbtnSubstrate.addActionListener(layerSelectionAction);
                    buttonGroup.add(rdbtnSubstrate);
                    layerSelectionPane.add(rdbtnSubstrate);
                }
                {
                    JRadioButton rdbtnCopper = new JRadioButton("Copper");
                    rdbtnCopper.addActionListener(layerSelectionAction);
                    buttonGroup.add(rdbtnCopper);
                    layerSelectionPane.add(rdbtnCopper);
                }
                {
                    JRadioButton rdbtnMask = new JRadioButton("Mask");
                    rdbtnMask.addActionListener(layerSelectionAction);
                    buttonGroup.add(rdbtnMask);
                    layerSelectionPane.add(rdbtnMask);
                }
                {
                    JRadioButton rdbtnSilk = new JRadioButton("Silk");
                    rdbtnSilk.addActionListener(layerSelectionAction);
                    buttonGroup.add(rdbtnSilk);
                    layerSelectionPane.add(rdbtnSilk);
                }
                {
                    JRadioButton rdbtnReticle = new JRadioButton("Reticle");
                    rdbtnReticle.addActionListener(layerSelectionAction);
                    buttonGroup.add(rdbtnReticle);
                    layerSelectionPane.add(rdbtnReticle);
                }
                
            }
            {
                colorChooserPane = new JPanel();
                colorChooser = new JColorChooser();
                colorChooser.setColor(colorScheme.backgroundColor);
                colorChooser.removeChooserPanel(colorChooser.getChooserPanels()[0]);
                previewPane = new PreviewPanel();
                previewPane.setPreferredSize(new Dimension(colorChooser.getPreferredSize().width, 
                        colorChooser.getPreferredSize().height/2));
                colorChooser.setPreviewPanel(previewPane);
                colorChooser.getSelectionModel().addChangeListener(new ChangeListener() {

                    @Override
                    public void stateChanged(ChangeEvent e) {
                        // TODO Auto-generated method stub
                        Color color = colorChooser.getColor();
                        switch (layer) {
                            case "Background":
                                colorScheme.backgroundColor = color;
                                break;
                            case "Substrate":
                                colorScheme.substrateColor = color;
                                break;
                            case "Copper":
                                colorScheme.copperColor = color;
                                break;
                            case "Mask":
                                colorScheme.maskColor = color;
                                break;
                            case "Silk":
                                colorScheme.legendColor = color;
                                break;
                            case "Reticle":
                                colorScheme.reticleColor = color;
                                break;
                        }
                        previewPane.repaint();
                    }});
                colorChooserPane.add(colorChooser);
                panel.add(colorChooserPane, BorderLayout.SOUTH);
            }
        }
        {
            buttonPane = new JPanel();
            buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
            getContentPane().add(buttonPane, BorderLayout.SOUTH);
            {
                JButton okButton = new JButton("OK");
                okButton.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        close();
                    }});
                buttonPane.add(okButton);
                getRootPane().setDefaultButton(okButton);
            }
            {
                JButton cancelButton = new JButton("Cancel");
                cancelButton.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        clearColorScheme();
                        close();
                    }});
                buttonPane.add(cancelButton);
            }
        }
        
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                int h = layerSelectionPane.getHeight() +
                        colorChooserPane.getHeight() +
                        previewPane.getHeight() +
                        buttonPane.getHeight();
                int w = Math.max(layerSelectionPane.getWidth(), colorChooserPane.getWidth());
                w = Math.max(w, buttonPane.getWidth());
                CustomColorSchemeDialog.this.setMinimumSize(new Dimension(3*w/2, 3*h/2));
//                CustomColorSchemeDialog.this.setMaximumSize(new Dimension(2*w, 2*h));
//                CustomColorSchemeDialog.this.setPreferredSize(new Dimension(2*w, 2*h));
//                CustomColorSchemeDialog.this.setSize(new Dimension(2*w, 2*h));
            }
        });

    }

    public void clearColorScheme() {
        colorScheme = null;
    }
    
    public ColorScheme getColorScheme() {
        return colorScheme;
    }
    
    public void close() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    public final Action layerSelectionAction = new AbstractAction() {

        @Override
        public void actionPerformed(ActionEvent e) {
            layer = e.getActionCommand();
            switch (layer) {
                case "Background":
                    colorChooser.setColor(colorScheme.backgroundColor);
                    break;
                case "Substrate":
                    colorChooser.setColor(colorScheme.substrateColor);
                    break;
                case "Copper":
                    colorChooser.setColor(colorScheme.copperColor);
                    break;
                case "Mask":
                    colorChooser.setColor(colorScheme.maskColor);
                    break;
                case "Silk":
                    colorChooser.setColor(colorScheme.legendColor);
                    break;
                case "Reticle":
                    colorChooser.setColor(colorScheme.reticleColor);
                    break;
            }
        }
        
    };
    
    private class PreviewPanel extends JPanel {
        @Override
        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = this.getWidth();
            int h = this.getHeight();
            
            BufferedImage bufImgR = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D offScr = (Graphics2D) bufImgR.getGraphics();
            offScr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            offScr.setColor(colorScheme.backgroundColor);
            offScr.fillRect(0, 0, w, h);
            
            offScr.setColor(colorScheme.substrateColor);
            offScr.fillRect((int) (w*0.05), (int) (h*0.05), (int) (w*0.90), (int) (h*0.90));
            
            offScr.setColor(colorScheme.copperColor);
            offScr.fillRect((int) (w*0.10), (int) (h*0.10), (int) (w*0.40), (int) (h*0.80));
            
            offScr.setColor(colorScheme.maskColor);
            offScr.fillRect((int) (w*0.15), (int) (h*0.15), (int) (w*0.70), (int) (h*0.70));
            
            offScr.setColor(colorScheme.legendColor);
            Font font = new Font("SansSerif", Font.PLAIN, 28);
            offScr.setFont(font);
            FontRenderContext frc = offScr.getFontRenderContext();
            TextLayout textTl = new TextLayout("SILKSCREEN", font, frc);
            Rectangle2D b = textTl.getBounds();
            offScr.drawString("SILKSCREEN", (int) (w/2.0 - b.getWidth()/2.0),
                (int) (h/2.0 + b.getHeight()/2.0));
            
            offScr.setColor(colorScheme.reticleColor);
            offScr.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
            for (int i=w/11; i<=10*w/11; i+=w/11) {
                offScr.drawLine(i, 0, i, h);
            }
            for (int i=h/6; i<=5*h/6; i+=h/3) {
                offScr.drawLine(0, i, w, i);
            }
            
            
            offScr.dispose();
            g2.drawImage(bufImgR, 0, 0, this);
        }        
    }
    
}
