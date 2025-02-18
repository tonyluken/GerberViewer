package gerberViewer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

import gerberFileReader.Attribute;
import gerberFileReader.AttributeDictionary;
import gerberFileReader.GerberFileReader;
import gerberFileReader.GraphicalObject;
import gerberFileReader.GraphicsStream;
import gerberFileReader.MetaData;
import gerberFileReader.Polarity;
import gerberFileReader.Units;

import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

@SuppressWarnings("serial")
public class GerberViewer extends JFrame {
    private static final double SCREEN_PPI = Toolkit.getDefaultToolkit().getScreenResolution();
    private static final double INCHES_PER_MAJOR_DIVISION = 0.75;
    private static final double PIXELS_PER_MAJOR_DIVISION = SCREEN_PPI * INCHES_PER_MAJOR_DIVISION;
    private static final double PIXEL_GAP = 20;
    private static final double ZOOM_PER_WHEEL_TICK = Math.pow(2, 1.0/4); //4 ticks to double zoom
    private static final int HORIZONTAL_SCALE_HEIGHT = 25;
    private static final int VERTICAL_SCALE_WIDTH = 45;
    private static final int SCALE_TICK_LENGTH = 5;

    private Map<String, GerberFileReader> parserMap;
    private JPanel contentPane;
    private DrawingPanel drawingPanel;
    private JProgressBar progressBar;
    private JMenuBar menuBar;
    private JMenuItem mntmUnits;
    private JRadioButtonMenuItem rdbtnmntmNative;
    private JRadioButtonMenuItem rdbtnmntmMetric;
    private JRadioButtonMenuItem rdbtnmntmImperial;
    private JCheckBoxMenuItem chckbxmntmShowReticle;
    private JButton btnAbort;
    private JMenu mnFile;
    private JMenu mnOptions;
    private JScrollPane scrollPaneDrawing;
    private JMenuItem mntmLoadDir;
    private JMenu mnView;
    private JRadioButtonMenuItem rdbtnmntmFront;
    private JRadioButtonMenuItem rdbtnmntmBack;
    private JSeparator separator;
    private JMenuItem mntmSaveImage;
    private JMenuItem mntmWritePnPData;
    private JMenuItem mntmWriteNetList;
    
    private Rectangle.Double defaultViewableBounds;
    private Rectangle2D graphicsBounds;
    private Rectangle.Double viewableBounds;
    private Rectangle.Double viewableClippingBounds;
    private Rectangle.Double scrollingBounds;
    private Rectangle.Double viewPortBounds;
    private double aspectRatio = 1;
    private double scaleFactor = 1;
    private double zoomFactor = 1;
    protected Point screenZoomPoint;
    public BufferedImage gerberImage;
    public List<Area> areas;
    public List<AttributeDictionary> areaAttributes;
    public List<MetaData> areaMetaData;
    public AffineTransform viewToObjectTransform;
    public AffineTransform screenToObjectTransform;
    private DrawingPanelColumnHeader drawingPanelColumnHeader;
    private DrawingPanelRowHeader drawingPanelRowHeader;
    private double unitsPerDivision;
    private double unitsPerTick;
    public AffineTransform objectToViewTransform;
    public AffineTransform objectToScreenTransform;
    private String displayUnit = "  ";
    private double displayMultiplier;
    private int displayDecimals;
    private DrawingPanelUnit drawingPanelUnit;
    public BufferedImage verticalScaleImage;
    public BufferedImage horizontalScaleImage;
    protected String displayUnits = "Native";
    protected boolean showReticle = true;
    protected Point screenDragStartPoint;
    protected boolean dragInProgress;
    protected Cursor savedCursor;
    protected String side = "Top";
    SwingWorker<Void, java.lang.Double> backgroundImageRenderer;
    private Area substrateArea;
    private Area solderMaskArea;
    protected Area silkArea;
    protected GraphicsStream solderMaskStream;
    private int splitPaneBottomHeight;
    private ColorScheme colorScheme;
    
    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    GerberViewer frame = new GerberViewer();
                    frame.setVisible(true);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Create the frame.
     */
    public GerberViewer() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(600, 400));
        setBounds(100, 100, 800, 600);
        setTitle("Gerber File Viewer");
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.setLayout(new BorderLayout(0, 0));
        setContentPane(contentPane);
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                splitPane.setDividerLocation(splitPane.getHeight() - splitPaneBottomHeight);
                computeResizedBounds();
                drawingPanel.revalidate();
                drawingPanel.repaint();
                drawingPanelRowHeader.repaint();
                drawingPanelColumnHeader.repaint();
            }});
        
        menuBar = new JMenuBar();
        getContentPane().add(menuBar, BorderLayout.NORTH);
        
        mnFile = new JMenu("File");
        menuBar.add(mnFile);
        
        JMenuItem mntmFileOpen = new JMenuItem(openFileAction);
        mnFile.add(mntmFileOpen);
        
        mntmLoadDir = new JMenuItem(loadDirAction);
        mnFile.add(mntmLoadDir);
        
        separator = new JSeparator();
        mnFile.add(separator);
        
        mntmSaveImage = new JMenuItem(saveImageAction);
        mntmSaveImage.setEnabled(false);
        mnFile.add(mntmSaveImage);
        
        separator = new JSeparator();
        mnFile.add(separator);
        
        mntmWritePnPData = new JMenuItem(writePnPDataAction);
        mntmWritePnPData.setEnabled(false);
        mnFile.add(mntmWritePnPData);
        
        mntmWriteNetList = new JMenuItem(writeNetListAction);
        mntmWriteNetList.setEnabled(false);
        mnFile.add(mntmWriteNetList);
        
        mnView = new JMenu("View");
        menuBar.add(mnView);
        
        ButtonGroup viewGroup = new ButtonGroup();
        rdbtnmntmFront = new JRadioButtonMenuItem("Front");
        rdbtnmntmFront.addActionListener(viewSelectedAction);
        viewGroup.add(rdbtnmntmFront);
        rdbtnmntmFront.setSelected(true);
        mnView.add(rdbtnmntmFront);
        
        rdbtnmntmBack = new JRadioButtonMenuItem("Back");
        rdbtnmntmBack.addActionListener(viewSelectedAction);
        viewGroup.add(rdbtnmntmBack);
        mnView.add(rdbtnmntmBack);
        
        mnOptions = new JMenu("Options");
        menuBar.add(mnOptions);
        
        mntmUnits = new JMenu("Units");
        mnOptions.add(mntmUnits);
        
        ButtonGroup unitsGroup = new ButtonGroup();
        rdbtnmntmNative = new JRadioButtonMenuItem("Native");
        rdbtnmntmNative.addActionListener(unitsSelectedAction);
        unitsGroup.add(rdbtnmntmNative);
        rdbtnmntmNative.setSelected(true);
        mntmUnits.add(rdbtnmntmNative);
        
        rdbtnmntmMetric = new JRadioButtonMenuItem("Metric");
        rdbtnmntmMetric.addActionListener(unitsSelectedAction);
        unitsGroup.add(rdbtnmntmMetric);
        mntmUnits.add(rdbtnmntmMetric);
        
        rdbtnmntmImperial = new JRadioButtonMenuItem("Imperial");
        rdbtnmntmImperial.addActionListener(unitsSelectedAction);
        unitsGroup.add(rdbtnmntmImperial);
        mntmUnits.add(rdbtnmntmImperial);
        
        chckbxmntmShowReticle = new JCheckBoxMenuItem("Show Reticle");
        chckbxmntmShowReticle.setSelected(true);
        chckbxmntmShowReticle.addActionListener(showReticleAction);
        
        mnColorSchemeMenu = new JMenu("Color Scheme");
        mnOptions.add(mnColorSchemeMenu);
        
        ButtonGroup colorGroup = new ButtonGroup();

        mntmGreen = new JRadioButtonMenuItem("Green");
        mntmGreen.addActionListener(colorSelectedAction);
        colorGroup.add(mntmGreen);
        mntmGreen.setSelected(true);
        mnColorSchemeMenu.add(mntmGreen);
        
        mntmPurple = new JRadioButtonMenuItem("Purple");
        mntmPurple.addActionListener(colorSelectedAction);
        colorGroup.add(mntmPurple);
        mnColorSchemeMenu.add(mntmPurple);
        
        mntmRed = new JRadioButtonMenuItem("Red");
        mntmRed.addActionListener(colorSelectedAction);
        colorGroup.add(mntmRed);
        mnColorSchemeMenu.add(mntmRed);
        
        mntmYellow = new JRadioButtonMenuItem("Yellow");
        mntmYellow.addActionListener(colorSelectedAction);
        colorGroup.add(mntmYellow);
        mnColorSchemeMenu.add(mntmYellow);
        
        mntmBlue = new JRadioButtonMenuItem("Blue");
        mntmBlue.addActionListener(colorSelectedAction);
        colorGroup.add(mntmBlue);
        mnColorSchemeMenu.add(mntmBlue);
        
        mntmWhite = new JRadioButtonMenuItem("White");
        mntmWhite.addActionListener(colorSelectedAction);
        colorGroup.add(mntmWhite);
        mnColorSchemeMenu.add(mntmWhite);
        
        mntmBlack = new JRadioButtonMenuItem("Black");
        mntmBlack.addActionListener(colorSelectedAction);
        colorGroup.add(mntmBlack);
        mnColorSchemeMenu.add(mntmBlack);
        
        separator = new JSeparator();
        mnColorSchemeMenu.add(separator);
        
        mntmCustom = new JRadioButtonMenuItem("Custom...");
        mntmCustom.addActionListener(colorSelectedAction);
        colorGroup.add(mntmCustom);
        mnColorSchemeMenu.add(mntmCustom);
        
        mnOptions.add(chckbxmntmShowReticle);
        
        drawingPanel = new DrawingPanel();
        
        drawingPanelColumnHeader = new DrawingPanelColumnHeader();
        drawingPanelColumnHeader.setBorder(BorderFactory.createEtchedBorder());
        
        drawingPanelRowHeader = new DrawingPanelRowHeader();
        drawingPanelRowHeader.setBorder(BorderFactory.createEtchedBorder());
        
        drawingPanelUnit = new DrawingPanelUnit();
        drawingPanelUnit.setBorder(BorderFactory.createEtchedBorder());
        
        drawingPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point point = e.getPoint();
                Point2D imagePoint = screenToObjectTransform.transform(point, null);
                boolean starting = true;
                for (int i=0; i<areas.size(); i++) {
                    if (areas.get(i).contains(imagePoint)) {
                        for (String attName : areaAttributes.get(i).keySet()) {
                            if (starting) {
                                textArea.append(String.format("Found attributes at [%.4f, %.4f]:\n", imagePoint.getX(), imagePoint.getY()));
                                starting = false;
                            }
                            textArea.append("    " + areaAttributes.get(i).get(attName) + "\n");
                        }
                    }
                }
                for (int i=0; i<areas.size(); i++) {
                    if (areas.get(i).contains(imagePoint)) {
                        MetaData metaData = areaMetaData.get(i);
                        if (!metaData.getRepeatId().isEmpty()) {
                            if (starting) {
                                textArea.append(String.format("Found meta data at [%.4f, %.4f]:\n", imagePoint.getX(), imagePoint.getY()));
                                starting = false;
                            }
                            textArea.append("    Metadata Repeat Id = " + metaData.getRepeatId() + "\n");
                        }
                        if (!metaData.getBlockId().isEmpty()) {
                            if (starting) {
                                textArea.append(String.format("Found meta data at [%.4f, %.4f]:\n", imagePoint.getX(), imagePoint.getY()));
                                starting = false;
                            }
                            textArea.append("    Metadata Block Id = " + metaData.getBlockId() + "\n");
                        }
                    }
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                screenDragStartPoint = e.getPoint();
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragInProgress) {
                    dragInProgress = false;
                    drawingPanel.setCursor(savedCursor);
                    computePannedBounds(screenDragStartPoint, e.getPoint());
                    drawingPanel.revalidate();
                    drawingPanel.repaint();
                }
            }
        });


        drawingPanel.addMouseWheelListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double newZoomFactor = 1e-6 * Math.round(Math.max(1, Math.pow(ZOOM_PER_WHEEL_TICK, 
                        -e.getPreciseWheelRotation())*zoomFactor) * 1e6);
                if (newZoomFactor != zoomFactor) {
                    computeZoomedBounds(newZoomFactor, e.getPoint());
                    drawingPanel.repaint();
                    drawingPanelRowHeader.repaint();
                    drawingPanelColumnHeader.repaint();
                    drawingPanelUnit.repaint();
                }
            }
        });
        
        drawingPanel.addMouseMotionListener(new MouseMotionAdapter() {

            @Override
            public void mouseDragged(MouseEvent e) {
                dragInProgress = true;
                if (savedCursor == null) {
                    savedCursor = drawingPanel.getCursor();
                };
                drawingPanel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
                computePannedBounds(screenDragStartPoint, e.getPoint());
                screenDragStartPoint = e.getPoint();
                drawingPanel.revalidate();
                drawingPanel.repaint();
                drawingPanelRowHeader.repaint();
                drawingPanelColumnHeader.repaint();
            }});
        
        drawingPanel.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                computeResizedBounds();
                drawingPanel.revalidate();
                drawingPanel.repaint();
                drawingPanelRowHeader.repaint();
                drawingPanelColumnHeader.repaint();
            }});
        
        JPanel panel = new JPanel();
        contentPane.add(panel, BorderLayout.SOUTH);
        panel.setLayout(new BorderLayout(0, 0));
        
        btnAbort = new JButton(abortAction);
        btnAbort.setHorizontalAlignment(SwingConstants.LEADING);
        btnAbort.setEnabled(false);
        panel.add(btnAbort, BorderLayout.WEST);
        
        progressBar = new JProgressBar();
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        panel.add(progressBar);
        
        splitPane = new JSplitPane();
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPane.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent changeEvent) {
                if (changeEvent.getPropertyName().equals(JSplitPane.DIVIDER_LOCATION_PROPERTY)) {
                    splitPaneBottomHeight = splitPane.getHeight() - splitPane.getDividerLocation();
                }
            }
        });
        contentPane.add(splitPane, BorderLayout.CENTER);
        
        scrollPaneText = new JScrollPane();
        splitPane.setRightComponent(scrollPaneText);
        
        textArea = new JTextArea();
        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        scrollPaneText.setViewportView(textArea);
        
        scrollPaneDrawing = new JScrollPane();
        splitPane.setLeftComponent(scrollPaneDrawing);
        scrollPaneDrawing.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrollPaneDrawing.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPaneDrawing.setViewportView(drawingPanel);
        scrollPaneDrawing.setColumnHeaderView(drawingPanelColumnHeader);
        scrollPaneDrawing.setRowHeaderView(drawingPanelRowHeader);
        scrollPaneDrawing.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, drawingPanelUnit);
        
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                Dimension newSize = getDefaultDisplayPanelSize();
                drawingPanel.setPreferredSize(newSize);
                drawingPanel.setSize(newSize);
                
                drawingPanelColumnHeader.setSize(newSize.width, HORIZONTAL_SCALE_HEIGHT);
                drawingPanelColumnHeader.setPreferredSize(new Dimension(newSize.width, HORIZONTAL_SCALE_HEIGHT));
                
                drawingPanelRowHeader.setSize(VERTICAL_SCALE_WIDTH, newSize.height);
                drawingPanelRowHeader.setPreferredSize(new Dimension(VERTICAL_SCALE_WIDTH, newSize.height));
                
                splitPane.setDividerLocation(0.8);
                colorScheme = new ColorScheme("Green");
            }
        });
    }

    private ActionListener viewSelectedAction = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getActionCommand().equals("Front")) {
                side = "Top";
            }
            else {
                side = "Bot";
            }
            solderMaskArea = null;
            computeTransforms();
            drawingPanel.repaint();
            drawingPanelRowHeader.repaint();
            drawingPanelColumnHeader.repaint();
            drawingPanelUnit.repaint();
        }
        
    };
    
    private ActionListener unitsSelectedAction = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            displayUnits = e.getActionCommand();
            renderGerberImage();
            drawingPanel.repaint();
            drawingPanelRowHeader.repaint();
            drawingPanelColumnHeader.repaint();
            drawingPanelUnit.repaint();
        }
        
    };
    
    private ActionListener colorSelectedAction = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getActionCommand().startsWith("Custom")) {
                CustomColorSchemeDialog dialog = new CustomColorSchemeDialog(GerberViewer.this, colorScheme);
                dialog.setVisible(true);
                ColorScheme newColorScheme = dialog.getColorScheme();
                if (newColorScheme != null) {
                    colorScheme = newColorScheme;
                }
                dialog.dispose();
            }
            else {
                colorScheme = new ColorScheme(e.getActionCommand());
            }
            renderGerberImage();
            drawingPanel.repaint();
        }
        
    };
    
    private ActionListener showReticleAction = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            showReticle = chckbxmntmShowReticle.isSelected();
            renderGerberImage();
            drawingPanel.repaint();
        }
        
    };
    
    private Dimension getDefaultDisplayPanelSize() {
        contentPane.revalidate();
        Dimension currentSize = scrollPaneDrawing.getViewport().getSize();
        Dimension x = drawingPanelRowHeader.getSize();
        Dimension y = drawingPanelColumnHeader.getSize();
        return new Dimension(currentSize.width + x.width - VERTICAL_SCALE_WIDTH, 
                currentSize.height + y.height - HORIZONTAL_SCALE_HEIGHT);
    }
    
    private void computeViewPortBoundsAndAspectRatio() {
        Dimension ddps = getDefaultDisplayPanelSize();
        scrollingBounds = new Rectangle.Double(0, 0, ddps.getWidth(), ddps.getHeight());
        viewPortBounds = new Rectangle.Double(PIXEL_GAP, PIXEL_GAP, 
                ddps.getWidth() - 2*PIXEL_GAP, ddps.getHeight() - 2*PIXEL_GAP);
        aspectRatio = ddps.getWidth() / ddps.getHeight();
    }
    
    private void computeDefaultViewableBounds() {
        if (parserMap.keySet().size() > 0) {
            boolean starting = true;
            for (String key : parserMap.keySet()) {
                GerberFileReader gerberFileReader = parserMap.get(key);
                if (gerberFileReader.getGraphicsStream() != null) {
                    if (gerberFileReader.getGraphicsStream().getBounds() != null) {
                        if (starting) {
                            graphicsBounds = gerberFileReader.getGraphicsStream().getBounds();
                            starting = false;
                        }
                        else {
                            graphicsBounds.add(gerberFileReader.getGraphicsStream().getBounds());
                        }
                    }
                }
            }
            defaultViewableBounds = new Rectangle.Double(graphicsBounds.getX(), graphicsBounds.getY(),
                    Math.max(graphicsBounds.getWidth(), aspectRatio*graphicsBounds.getHeight()),
                    Math.max(graphicsBounds.getHeight(), graphicsBounds.getWidth()/aspectRatio));
        }
        else {
            graphicsBounds = new Rectangle.Double(0, 0, aspectRatio, 1);
            defaultViewableBounds = new Rectangle.Double(0, 0, aspectRatio, 1);
        }
    }
    
    private void initializeBounds() {
        contentPane.revalidate();
        computeViewPortBoundsAndAspectRatio();
        computeDefaultViewableBounds();
        viewableBounds = defaultViewableBounds;
        computeTransforms();
    }
    
    private void computeTransforms() {
        if (viewableBounds == null) {
            return;
        }
        double sign = 1;
        double xOffset = 0;
        if (side.equals("Bot")) {
            sign = -1.0;
            xOffset = viewableBounds.getWidth();
        }
        scaleFactor = Math.min(viewPortBounds.width / viewableBounds.width,
                viewPortBounds.height / viewableBounds.height);

        objectToViewTransform = new AffineTransform();
        objectToViewTransform.scale(sign*scaleFactor, scaleFactor);
        objectToViewTransform.translate(-xOffset-viewableBounds.getX(), -viewableBounds.getY());

        objectToScreenTransform = new AffineTransform();
        objectToScreenTransform.translate(PIXEL_GAP, (int) scrollingBounds.height - PIXEL_GAP);
        objectToScreenTransform.scale(1, -1);
        objectToScreenTransform.concatenate(objectToViewTransform);

        viewToObjectTransform = new AffineTransform();
        viewToObjectTransform.translate(xOffset+viewableBounds.getX(), viewableBounds.getY());
        viewToObjectTransform.scale(sign*1.0/scaleFactor, 1.0/scaleFactor);

        screenToObjectTransform = new AffineTransform(viewToObjectTransform);
        screenToObjectTransform.scale(1, -1);
        screenToObjectTransform.translate(-PIXEL_GAP, -((int) scrollingBounds.height - PIXEL_GAP));
        
        viewableClippingBounds = new Rectangle.Double(viewableBounds.getX() - PIXEL_GAP/scaleFactor,
                viewableBounds.getY() - PIXEL_GAP/scaleFactor,
                viewableBounds.getWidth() + 2*PIXEL_GAP/scaleFactor,
                viewableBounds.getHeight() + 2*PIXEL_GAP/scaleFactor);
        
        renderGerberImage();
    }
    
    private void computeResizedBounds() {
        if (viewableBounds != null) {
            computeViewPortBoundsAndAspectRatio();
            computeDefaultViewableBounds();
            double newVBw = defaultViewableBounds.width / zoomFactor;
            double newVBh = defaultViewableBounds.height / zoomFactor;
            viewableBounds = new Rectangle.Double(viewableBounds.x, viewableBounds.y, newVBw, newVBh);
            computeTransforms();
        }
    }
    
    private void computePannedBounds(Point2D startPoint, Point2D endPoint) {
        Point2D objectStartPoint = screenToObjectTransform.transform(startPoint, null);
        Point2D objectEndPoint = screenToObjectTransform.transform(endPoint, null);
        
        viewableBounds = new Rectangle.Double(
                viewableBounds.x + objectStartPoint.getX() - objectEndPoint.getX(),
                viewableBounds.y + objectStartPoint.getY() - objectEndPoint.getY(), 
                viewableBounds.width, viewableBounds.height);
        computeTransforms();
    }
    
    private void computeZoomedBounds(double newZoomFactor, Point2D screenZoomPoint) {
        if (newZoomFactor > 1) {
            //Convert the mouse screen coordinates to object coordinates
            Point2D objectZoomPoint = screenToObjectTransform.transform(screenZoomPoint, null);
            
            //Now we compute the new viewableBounds so that it has the correct zoom and is located
            //so that the object point is at the same screen location as it was before the zoom
            double newVBw = defaultViewableBounds.width / newZoomFactor;
            double newVBh = defaultViewableBounds.height / newZoomFactor;
            double newVBx = objectZoomPoint.getX() - 
                    newVBw * (objectZoomPoint.getX() - viewableBounds.x) / viewableBounds.width;
            double newVBy = objectZoomPoint.getY() - 
                    newVBh * (objectZoomPoint.getY() - viewableBounds.y) / viewableBounds.height;
            viewableBounds = new Rectangle.Double(newVBx, newVBy, newVBw, newVBh);
        }
        else {
            viewableBounds = defaultViewableBounds;
        }
        zoomFactor = newZoomFactor;
        computeTransforms();
    }
    
    private class DrawingPanelUnit extends JPanel {
        @Override
        public void paintComponent(Graphics g) {
            Color foreGround = Color.BLACK;
            GerberFileReader gerberFileReader = null;
            if (parserMap != null) {
                if (parserMap.keySet().size() > 0) {
                    gerberFileReader = parserMap.get(parserMap.keySet().toArray()[0]);
                }
            }
            if (gerberFileReader != null && gerberFileReader.getGraphicsStream() != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                BufferedImage bufImgR = new BufferedImage(VERTICAL_SCALE_WIDTH, 
                        HORIZONTAL_SCALE_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR);
                Graphics2D offScr = bufImgR.createGraphics();
                offScr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                offScr.setColor(this.getBackground());
                offScr.fillRect(0, 0, VERTICAL_SCALE_WIDTH, HORIZONTAL_SCALE_HEIGHT);
                offScr.setColor(foreGround);
                Font font = new Font("SansSerif", Font.PLAIN, 14);
                offScr.setFont(font);
                FontRenderContext frc = offScr.getFontRenderContext();
                TextLayout textTl = new TextLayout(displayUnit, font, frc);
                Rectangle2D b = textTl.getBounds();
                offScr.drawString(displayUnit, (int) (VERTICAL_SCALE_WIDTH/2.0 - b.getWidth()/2.0),
                        (int) (HORIZONTAL_SCALE_HEIGHT/2.0 + b.getHeight()/2.0));
                
                offScr.dispose();
                g2.drawImage(bufImgR, 0, 0, this);
            }
            else {
                super.paintComponent(g);
            }
        }        
    }
    
    private class DrawingPanelColumnHeader extends JPanel {
        @Override
        public void paintComponent(Graphics g) {
            if (horizontalScaleImage != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(horizontalScaleImage, 0, 0, this);
            }
            else {
                super.paintComponent(g);
            }
        }
    }

    private class DrawingPanelRowHeader extends JPanel {
        
        @Override
        public void paintComponent(Graphics g) {
            if (verticalScaleImage != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(verticalScaleImage, 0, 0, this);
            }
            else {
                super.paintComponent(g);
            }
        }
    }
    
    private class DrawingPanel extends JPanel {
        
        @Override
        public void paintComponent(Graphics g) {
            if (gerberImage != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(gerberImage, 0, 0, this);
            }
            else {
                super.paintComponent(g);
            }
        }
        
    }

    private void renderGerberImage() {
        if (parserMap != null && parserMap.keySet().size() > 0) {
            if (backgroundImageRenderer != null && !backgroundImageRenderer.isDone()) {
                backgroundImageRenderer.cancel(true);
                while (!backgroundImageRenderer.isDone()) {
                    //wait for the previous renderer to cancel
                }
            }
            
            backgroundImageRenderer = new SwingWorker<Void, java.lang.Double>() {

                @Override
                protected Void doInBackground() throws Exception {
                    gerberImage = new BufferedImage((int) scrollingBounds.width, (int) scrollingBounds.height, BufferedImage.TYPE_4BYTE_ABGR);
                    Graphics2D offScr = gerberImage.createGraphics();
                    offScr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    Color background;
                    Color foreground;
                    Color transparentColor = new Color(0, 0, 0, 0);
                    
                    offScr.setColor(colorScheme.backgroundColor);
                    offScr.fillRect(0, 0, (int) scrollingBounds.width, (int) scrollingBounds.height);
                    
                    offScr.translate(PIXEL_GAP, (int) scrollingBounds.height - PIXEL_GAP);
                    offScr.scale(1, -1);
                    
                    areas = new ArrayList<>();
                    areaAttributes = new ArrayList<>();
                    areaMetaData = new ArrayList<>();
                    String[] plotOrder;
                    if (parserMap.keySet().size() > 1) {
                        plotOrder = new String[] {"Profile", "Copper", "Plated", "NonPlated", "Soldermask", "Legend"};
                    }
                    else {
                        plotOrder = new String[] {"Anything"};
                    }
                    double layerCount = plotOrder.length;
                    int layerNumber = 0;
                    GraphicsStream graphicStream = null;
                    functionLoop: for (String function : plotOrder) {
                        if (isCancelled()) {
                            break functionLoop;
                        }
                        final int lNum = layerNumber;
                        keyLoop: for (String key : parserMap.keySet()) {
                            GerberFileReader parser = parserMap.get(key);
                            AttributeDictionary fileAttributes = parser.getFileAttributes();
                            if (function.equals("Anything") || fileAttributes.get(".FileFunction").getValues().get(0).equals(function)) {
                                switch (function) {
                                    case "Profile":
                                        graphicStream = parser.getGraphicsStream();
                                        if (substrateArea == null) {
                                            substrateArea = constructBoardSubstrate(graphicStream, (p) -> updateProgressBar((lNum + p)/layerCount));
                                        }
                                        Shape substrate = objectToViewTransform.createTransformedShape(substrateArea);
                                        offScr.setColor(colorScheme.substrateColor);
                                        offScr.fill(substrate);
                                        break keyLoop;
                                    case "Copper":
                                        if (!fileAttributes.get(".FileFunction").getValues().get(2).equals(side)) {
                                            continue keyLoop;
                                        }
                                        foreground = colorScheme.copperColor;
                                        background = transparentColor;
                                        break;
                                    case "Soldermask":
                                        if (!fileAttributes.get(".FileFunction").getValues().get(1).equals(side)) {
                                            continue keyLoop;
                                        }
                                        graphicStream = parser.getGraphicsStream();
                                        solderMaskStream = graphicStream;
                                        if (solderMaskArea == null) {
                                            solderMaskArea = constructSolderMask(graphicStream, substrateArea, (p) -> updateProgressBar((lNum + p)/layerCount));
                                        }
                                        if (!solderMaskArea.equals(substrateArea)) {
                                            Shape mask = objectToViewTransform.createTransformedShape(solderMaskArea);
                                            offScr.setColor(colorScheme.maskColor);
                                            offScr.fill(mask);
                                        }
                                        break keyLoop;
                                    case "Legend":
                                        if (!fileAttributes.get(".FileFunction").getValues().get(1).equals(side)) {
                                            continue keyLoop;
                                        }
                                        graphicStream = parser.getGraphicsStream();
                                        offScr.setColor(colorScheme.legendColor);
                                        renderSilkScreen(offScr, graphicStream, solderMaskStream, 
                                                (p) -> updateProgressBar((lNum + p)/layerCount));
                                        break keyLoop;
                                    case "Plated":
                                    case "NonPlated":
                                        foreground = colorScheme.backgroundColor;
                                        background = transparentColor;
                                        break;
                                    case "Anything":
                                        foreground = colorScheme.copperColor;
                                        background = colorScheme.backgroundColor;
                                        break;
                                    default:
                                        continue keyLoop;
                                }
                                
                                graphicStream = parser.getGraphicsStream();
                                double total = graphicStream.getStream().size();
                                int count = 0;
                                for (GraphicalObject go : graphicStream.getStream()) {
                                    if (isCancelled()) {
                                        break functionLoop;
                                    }
                                    Rectangle2D bounds = go.getArea().getBounds2D();
                                    if (bounds.intersects(viewableClippingBounds) && (bounds.getWidth()*scaleFactor > 2 || bounds.getHeight()*scaleFactor > 2)) {
                                        if (!dragInProgress) {
                                            Shape shape = objectToViewTransform.createTransformedShape(go.getArea());
                                            Area area = go.getArea();
                                            if (go.getPolarity() == Polarity.DARK) {
                                                offScr.setColor(foreground);
                                            }
                                            else {
                                                offScr.setColor(background);
                                                for (Area prevArea : areas) {
                                                    prevArea.subtract(area);
                                                }
                                            }
                                            offScr.fill(shape);
                                            areas.add(area);
                                            areaAttributes.add(go.getAttributes());
                                            areaMetaData.add(go.getMetaData());
                                        }
                                        else {
                                            Shape shape = objectToViewTransform.createTransformedShape(bounds);
                                            offScr.setColor(foreground);
                                            offScr.draw(shape);
                                        }
                                    }
                                    count++;
                                    publish((layerNumber + count/total)/layerCount);
                                }
                                break keyLoop;
                            }
                        }
                        layerNumber++;
                    }
                    
                    overlayReticle(offScr);
                    
                    offScr.dispose();
                    return null;
                }
                
                @Override
                protected void process(List<java.lang.Double> chunksOfStatus) {
                   for (java.lang.Double d : chunksOfStatus) {
                        updateProgressBar(d);
                    }
                }
                
                @Override
                protected void done() {
                    try {
                        get();
                        drawingPanel.repaint();
                        drawingPanelUnit.repaint();
                        drawingPanelColumnHeader.repaint();
                        drawingPanelRowHeader.repaint();
                        mntmSaveImage.setEnabled(true);
                        if (parserMap.keySet().size() > 1) {
                            mntmWritePnPData.setEnabled(true);
                            mntmWriteNetList.setEnabled(true);
                        }

                        cleanUp(null);
                    }
                    catch (CancellationException e) {
                        //ok
                        cleanUp(null);
                    }
                    catch (InterruptedException | ExecutionException e) {
                        // TODO Auto-generated catch block
                        displayStackTrace(e);
                        cleanUp(e);
                    }
                }
            };
              
            backgroundImageRenderer.execute();
        }
        else {
            if (scrollingBounds == null) {
                computeViewPortBoundsAndAspectRatio();
            }
            gerberImage = new BufferedImage((int) scrollingBounds.width, (int) scrollingBounds.height, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D offScr = gerberImage.createGraphics();
            offScr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            offScr.setColor(drawingPanelUnit.getBackground());
            offScr.fillRect(0, 0, (int) scrollingBounds.width, (int) scrollingBounds.height);
            offScr.dispose();
            drawingPanel.repaint();
        }
    }
    
    private void renderSilkScreen(Graphics2D offScr, GraphicsStream graphicStream, 
            GraphicsStream solderMaskStream, Consumer<java.lang.Double> showProgress) {
        Long start = System.currentTimeMillis();
        textArea.append("Rendering Silkscreen...");
        double total = graphicStream.getStream().size() * solderMaskStream.getStream().size();
        int count = 0;
        outerLoop: for (GraphicalObject go : graphicStream.getStream()) {
            Area area = go.getArea();
            for (GraphicalObject go2 : solderMaskStream.getStream()) {
                if (backgroundImageRenderer.isCancelled()) {
                    break outerLoop;
                }
                if (area.getBounds2D().intersects(go2.getArea().getBounds2D())) {
                    area.subtract(go2.getArea());
                }
                count++;
                showProgress.accept(count/total);
            }
            Shape silk = objectToViewTransform.createTransformedShape(area);
            offScr.fill(silk);
        }
        textArea.append(" completed in " + (System.currentTimeMillis() - start) + " ms\n");
    }
    
    private Area constructSolderMask(GraphicsStream graphicStream, Area substrateArea, Consumer<java.lang.Double> showProgress) {
        textArea.append("Constructing Solder Mask...");
        Long start = System.currentTimeMillis();
        Area maskArea = new Area(substrateArea);
        double total = graphicStream.getStream().size();
        int count = 0;
        for (GraphicalObject go : graphicStream.getStream()) {
            if (backgroundImageRenderer.isCancelled()) {
                break;
            }
            if (go.getPolarity() == Polarity.DARK) {
                maskArea.subtract(go.getArea());
            }
            else {
                maskArea.add(go.getArea());
            }
            count++;
            showProgress.accept(count/total);
        }
        
        textArea.append(" completed in " + (System.currentTimeMillis() - start) + " ms\n");
        return maskArea;
    }
    
    private Area constructBoardSubstrate(GraphicsStream graphicStream, Consumer<java.lang.Double> showProgress) {
        textArea.append("Constructing board substrate...");
        Long start = System.currentTimeMillis();
        //First construct an Area object from all the graphic stream objects
        List<Path2D> profilePathList = new ArrayList<>();
        Area boardProfile = new Area();
        double total = graphicStream.getStream().size();
        int count = 0;
        for (GraphicalObject go : graphicStream.getStream()) {
            if (go.getMetaData().getStrokeInfo().getPath() != null) {
                profilePathList.add(go.getMetaData().getStrokeInfo().getPath());
            }
            if (go.getPolarity() == Polarity.DARK) {
                boardProfile.add(go.getArea());
            }
            else {
                boardProfile.subtract(go.getArea());
            }
            count++;
            showProgress.accept(count/total);
        }

        //Now we don't know if the profile is represented by a Region using G36/G37 commands or
        //by draws and arcs (strokes).  To tell the difference we need to examine all the closed
        //paths.  If Regions were used, the closed path with the largest geometric area will be the
        //outer perimeter of the board and will contain all other closed paths (the holes). And none
        //of the other closed paths will contain any other of the closed paths (the holes can't have 
        //their own holes).
        
        //First create all the closed paths and find the two with the largest areas
        List<Path2D> profilePaths = new ArrayList<>();
        Path2D profilePath = null;
        double[] coords = new double[6];
        PathIterator pathIter = boardProfile.getPathIterator(null);
        Path2D largestAreaPath = null;
        double largestArea = 0;
        Path2D secondLargestAreaPath = null;
        double secondLargestArea = 0;
        while (!pathIter.isDone()) {
            int segType = pathIter.currentSegment(coords);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    profilePath = new Path2D.Double();
                    profilePath.moveTo(coords[0], coords[1]);
                    break;
                case PathIterator.SEG_LINETO:
                    profilePath.lineTo(coords[0], coords[1]);
                    break;
                case PathIterator.SEG_QUADTO:
                    profilePath.quadTo(coords[0], coords[1], coords[2], coords[3]);
                    break;
                case PathIterator.SEG_CUBICTO:
                    profilePath.curveTo(coords[0], coords[1], coords[2], coords[3], 
                            coords[4], coords[5]);
                    break;
                case PathIterator.SEG_CLOSE:
                    profilePath.closePath();
                    profilePaths.add(profilePath);
                    double geometricArea = computeGeometricArea(profilePath);
                    if (geometricArea > largestArea) {
                        secondLargestAreaPath = largestAreaPath;
                        secondLargestArea = largestArea;
                        largestAreaPath = profilePath;
                        largestArea = geometricArea;
                    }
                    else if (geometricArea > secondLargestArea) {
                        secondLargestAreaPath = profilePath;
                        secondLargestArea = geometricArea;
                    }
                    break;
            }
            pathIter.next();
        }
        
        //First we assume the profile was defined using a region and look for paths wholly contained
        //within another. If we find one, it means the profile couldn't have been defined by
        //a region otherwise a hole has another hole within it and that can't happen.
        profilePaths.remove(largestAreaPath);
        boolean region = true;
        outerLoop: for (int i=0; i<profilePaths.size()-1; i++) {
            Path2D holePath1 = profilePaths.get(i);
            Area area1 = new Area(holePath1);
            for (int j=i+1; j<profilePaths.size(); j++) {
                Path2D holePath2 = profilePaths.get(j);
                Area area2 = new Area(holePath2);
                Area testArea = new Area(area1);
                testArea.add(area2);
                if (testArea.equals(area1) || testArea.equals(area2)) {
                    //one of the paths was wholly contained within the other so this must have
                    //been defined using draws and arcs (strokes) and not by a region
                    region = false;
                    break outerLoop;
                }
            }
        }
        
        //Now we check for the case where there are only two paths - this could mean the profile is
        //defined by a region with a single hole in it, or it could be defined by a single closed 
        //non-zero width stroke. We determine which case we have by looking at the average width of
        //the area between the two paths. We assume no practical PCB could have a hole in it so 
        //large that the average width of the remainder is less than 2 millimeters.
        double avgStrokeWidth = 0;
        if (region && profilePaths.size() == 1) {
            avgStrokeWidth = 2*(largestArea-secondLargestArea)/
                    (computeGeometricPerimeter(largestAreaPath) + computeGeometricPerimeter(secondLargestAreaPath));
            if (parserMap.get(parserMap.keySet().toArray()[0]).getUnits() == Units.INCHES) {
                avgStrokeWidth *= 25.4; //convert to millimeters
            }
            if (avgStrokeWidth < 2) {
                region = false;
            }
                
        }
        
        if (region) {
            return boardProfile;
        }
        
        //If we've gotten this far, the profile was defined by stroking with a non-zero width
        //aperture and the true profile is defined by the center-line of the stroke
        avgStrokeWidth = 2*(largestArea-secondLargestArea)/
                (computeGeometricPerimeter(largestAreaPath) + computeGeometricPerimeter(secondLargestAreaPath));
        List<Point2D> beginings = new ArrayList<>();
        List<Point2D> endings = new ArrayList<>();
        for (Path2D path : profilePathList) {
            PathIterator pathIterator = path.getPathIterator(null);
            Point2D begin = new Point2D.Double();
            Point2D end = new Point2D.Double();
            while (!pathIterator.isDone()) {
                int segType = pathIterator.currentSegment(coords);
                switch (segType) {
                    case PathIterator.SEG_MOVETO:
                        begin.setLocation(coords[0], coords[1]);
                        break;
                    case PathIterator.SEG_LINETO:
                        end.setLocation(coords[0], coords[1]);
                        break;
                    case PathIterator.SEG_QUADTO:
                        end.setLocation(coords[2], coords[3]);
                        break;
                    case PathIterator.SEG_CUBICTO:
                        end.setLocation(coords[4], coords[5]);
                        break;
                    case PathIterator.SEG_CLOSE:
                        end.setLocation(begin);
                        break;
                }
                pathIterator.next();
            }
            beginings.add(begin);
            endings.add(end);
        }
        
        Path2D orderedProfilePath = new Path2D.Double(profilePathList.get(0));
        Point2D firstBegining = beginings.get(0);
        Point2D lastEnd = endings.get(0);
        profilePathList.remove(0);
        beginings.remove(0);
        endings.remove(0);
        while (!profilePathList.isEmpty()) {
        
            boolean flip = false;
            int bestIdx = 0;
            double minDist = java.lang.Double.POSITIVE_INFINITY;
            for (int i=0; i<beginings.size(); i++) {
                Point2D pt = beginings.get(i);
                double dist = lastEnd.distance(pt);
                if (dist < minDist) {
                    minDist = dist;
                    bestIdx = i;
                }
            }
            for (int i=0; i<endings.size(); i++) {
                Point2D pt = endings.get(i);
                double dist = lastEnd.distance(pt);
                if (dist < minDist) {
                    minDist = dist;
                    bestIdx = i;
                    flip = true;
                }
            }
            
            Path2D next = profilePathList.get(bestIdx);
            
            if (flip) {
                next = reversePath(next);
                lastEnd = beginings.get(bestIdx);
            }
            else {
                lastEnd = endings.get(bestIdx);
            }
            profilePathList.remove(bestIdx);
            beginings.remove(bestIdx);
            endings.remove(bestIdx);
            
            orderedProfilePath.append(next, minDist < avgStrokeWidth/2);
            
            if (lastEnd.distance(firstBegining) < avgStrokeWidth/2) {
                orderedProfilePath.closePath();
                if (!profilePathList.isEmpty()) {
                    orderedProfilePath.append(profilePathList.get(0), false);
                    firstBegining = beginings.get(0);
                    lastEnd = endings.get(0);
                    profilePathList.remove(0);
                    beginings.remove(0);
                    endings.remove(0);
                }
            }
            
        }
        
        Area ret = new Area(orderedProfilePath);
        textArea.append(" completed in " + (System.currentTimeMillis() - start) + " ms\n");
        return ret;
    }
    
    private double computeGeometricArea(Path2D closedPath) {
        PathIterator pathIter = closedPath.getPathIterator(null, 0.001);
        double[] coords = new double[6];
        double firstX = 0;
        double firstY = 0;
        double prevX = 0;
        double prevY = 0;
        double area = 0;
        // Calculate the area using the shoelace formula
        while (!pathIter.isDone()) {
            int segType = pathIter.currentSegment(coords);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    firstX = coords[0];
                    firstY = coords[1];
                    prevX = coords[0];
                    prevY = coords[1];
                    break;
                case PathIterator.SEG_LINETO:
                    area += (prevX + coords[0]) * (prevY - coords[1]);
                    prevX = coords[0];
                    prevY = coords[1];
                    break;
                case PathIterator.SEG_CLOSE:
                    area += (prevX + firstX) * (prevY - firstY);
                    break;
            }
            pathIter.next();
        }
        return Math.abs(0.5*area);
    
    }
    
    private double computeGeometricPerimeter(Path2D closedPath) {
        PathIterator pathIter = closedPath.getPathIterator(null, 0.001);
        double[] coords = new double[6];
        double firstX = 0;
        double firstY = 0;
        double prevX = 0;
        double prevY = 0;
        double perim = 0;
        while (!pathIter.isDone()) {
            int segType = pathIter.currentSegment(coords);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    firstX = coords[0];
                    firstY = coords[1];
                    prevX = coords[0];
                    prevY = coords[1];
                    break;
                case PathIterator.SEG_LINETO:
                    perim += Math.hypot(coords[0] - prevX, coords[1] - prevY);
                    prevX = coords[0];
                    prevY = coords[1];
                    break;
                case PathIterator.SEG_CLOSE:
                    perim += Math.hypot(firstX - prevX, firstY - prevY);
                    break;
            }
            pathIter.next();
        }
        return perim;
    }
    
    private void overlayReticle(Graphics2D offScr) {
        double unitScaling = 1;
        if (displayUnits.equals("Metric") && parserMap.get(parserMap.keySet().toArray()[0]).getUnits()==Units.INCHES) {
            unitScaling = 25.4;
        }
        else if (displayUnits.equals("Imperial") && parserMap.get(parserMap.keySet().toArray()[0]).getUnits()==Units.MILLIMETERS) {
            unitScaling = 1.0/25.4;
        }
        
        Rectangle2D objectSpaceBounds = screenToObjectTransform.createTransformedShape(scrollingBounds).getBounds2D();
        double numberOfMajorDivisions = scrollingBounds.getWidth() / PIXELS_PER_MAJOR_DIVISION;
        
        unitsPerDivision = unitScaling * objectSpaceBounds.getWidth() / numberOfMajorDivisions;
        
        double powerOfTen = Math.floor(Math.log10(unitsPerDivision));
        double mult = unitsPerDivision / Math.pow(10, powerOfTen);
        if (mult >= 2.5) {
            mult = 5;
        }
        else if (mult >= 1.5) {
            mult = 2;
        }
        else {
            mult = 1;
        }
        unitsPerDivision = mult * Math.pow(10, powerOfTen);
        if (mult == 5) {
            unitsPerTick = unitsPerDivision / 5;
        }
        else {
            unitsPerTick = unitsPerDivision / 10;
        }
        
        if (displayUnits.equals("Metric") || (displayUnits.equals("Native") && parserMap.get(parserMap.keySet().toArray()[0]).getUnits()==Units.MILLIMETERS)) {
            if (unitsPerDivision > 10) {
                displayUnit = "cm";
                displayMultiplier = 0.1;
                displayDecimals = 0;
            }
            else if (unitsPerDivision > 1) {
                displayUnit = "mm";
                displayMultiplier = 1;
                displayDecimals = 0;
            }
            else if (unitsPerDivision > 0.1) {
                displayUnit = "mm";
                displayMultiplier = 1;
                displayDecimals = 1;
            }
            else {
                displayUnit = "um";
                displayMultiplier = 1000;
                displayDecimals = 0;
            }
        }
        else {
            if (unitsPerDivision >= 1) {
                displayUnit = "in";
                displayMultiplier = 1;
                displayDecimals = 0;
            }
            else if (unitsPerDivision >= 0.1) {
                displayUnit = "in";
                displayMultiplier = 1;
                displayDecimals = 1;
            }
            else {
                displayUnit = "mil";
                displayMultiplier = 1000;
                displayDecimals = 0;
            }
        }
        
        offScr.setColor(colorScheme.reticleColor);
        offScr.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        
        double tickLengthOver2 = 0.025*PIXELS_PER_MAJOR_DIVISION;
        double xMin = unitsPerDivision*Math.floor(unitScaling*objectSpaceBounds.getMinX()/unitsPerDivision);
        double xMax = unitsPerDivision*Math.ceil(unitScaling*objectSpaceBounds.getMaxX()/unitsPerDivision);
        double yMin = unitsPerDivision*Math.floor(unitScaling*objectSpaceBounds.getMinY()/unitsPerDivision);
        double yMax = unitsPerDivision*Math.ceil(unitScaling*objectSpaceBounds.getMaxY()/unitsPerDivision);
        
        AffineTransform at = new AffineTransform(objectToViewTransform);
        at.scale(1/unitScaling, 1/unitScaling);
        
        if (showReticle) {
            //Major vertical lines
            double x = xMin;
            while (x <= xMax) {
                Point2D start = new Point2D.Double(x, yMin);
                Point2D end = new Point2D.Double(x, yMax);
                
                at.transform(start, start);
                at.transform(end, end);
                
                offScr.drawLine((int) start.getX(), (int) start.getY(), (int) end.getX(), (int) end.getY());
                
                //with horizontal tick marks
                double y = yMin;
                while (y <= yMax) {
                    Point2D mid = new Point2D.Double(x, y);
                    
                    at.transform(mid, mid);
                    offScr.drawLine((int) (mid.getX()-tickLengthOver2), (int) mid.getY(), (int) (mid.getX()+tickLengthOver2), (int) mid.getY());
                    y += unitsPerTick;
                }
                x += unitsPerDivision;
            }
            
            //Major horizontal lines
            double y = yMin;
            while (y <= yMax) {
                Point2D start = new Point2D.Double(xMin, y);
                Point2D end = new Point2D.Double(xMax, y);
                
                at.transform(start, start);
                at.transform(end, end);
                
                offScr.drawLine((int) start.getX(), (int) start.getY(), (int) end.getX(), (int) end.getY());
                
                //with vertical tick marks
                x = xMin;
                while (x <= xMax) {
                    Point2D mid = new Point2D.Double(x, y);
                    
                    at.transform(mid, mid);
                    offScr.drawLine((int) mid.getX(), (int) (mid.getY()-tickLengthOver2), (int) mid.getX(), (int) (mid.getY()+tickLengthOver2));
                    x += unitsPerTick;
                }
                y += unitsPerDivision;
            }
        }
        renderVerticalScale(at, xMin, xMax, yMin, yMax);
        renderHorizontalScale(at, xMin, xMax, yMin, yMax);
    }
    
    private void renderVerticalScale(AffineTransform at, double xMin, double xMax,
            double yMin, double yMax) {
        Color foreGround = Color.BLACK;

        verticalScaleImage = new BufferedImage(VERTICAL_SCALE_WIDTH, (int) scrollingBounds.height, 
                BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D offScr = verticalScaleImage.createGraphics();
        offScr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        offScr.setColor(drawingPanelRowHeader.getBackground());
        offScr.fillRect(0, 0, VERTICAL_SCALE_WIDTH, (int) scrollingBounds.height);
        
        offScr.translate(0, (int) scrollingBounds.height - PIXEL_GAP);
        offScr.scale(1, -1);

        offScr.setColor(foreGround);
        offScr.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        offScr.setFont(new Font("SansSerif", Font.PLAIN, 10));
        
        //Major horizontal lines
        double y = yMin;
        Font f = new Font("SansSerif", Font.PLAIN, 10);
        FontRenderContext frc = offScr.getFontRenderContext();
        while (y <= yMax) {
            Point2D tick = new Point2D.Double(xMin, y);
            
            at.transform(tick, tick);
            
            offScr.drawLine(VERTICAL_SCALE_WIDTH-SCALE_TICK_LENGTH, (int) tick.getY(), VERTICAL_SCALE_WIDTH, (int) tick.getY());

            String text = String.format("%." + displayDecimals + "f", y*displayMultiplier);
            TextLayout textTl = new TextLayout(text, f, frc);
        
            AffineTransform transform = new AffineTransform();
            transform.translate(VERTICAL_SCALE_WIDTH-SCALE_TICK_LENGTH-textTl.getBounds().getWidth()-2, tick.getY()-textTl.getBounds().getHeight()/2);
            transform.scale(1, -1);
            Shape outline = textTl.getOutline(null);
            offScr.fill(transform.createTransformedShape(outline));
            
            y += unitsPerDivision;
        
        }
        
        offScr.dispose();
    }

    private void renderHorizontalScale(AffineTransform at, double xMin, double xMax,
            double yMin, double yMax) {
        Color foreGround = Color.BLACK;

        horizontalScaleImage = new BufferedImage((int) scrollingBounds.width, HORIZONTAL_SCALE_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D offScr = horizontalScaleImage.createGraphics();
        offScr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        offScr.setColor(drawingPanelColumnHeader.getBackground());
        offScr.fillRect(0, 0, (int) scrollingBounds.width, HORIZONTAL_SCALE_HEIGHT);
        
        offScr.translate(PIXEL_GAP, 0);
        offScr.scale(1, 1);
        
        offScr.setColor(foreGround);
        offScr.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        offScr.setFont(new Font("SansSerif", Font.PLAIN, 10));
        
        //Major vertical lines
        double x = xMin;
        FontRenderContext frc = offScr.getFontRenderContext();
        Font f = new Font("SansSerif", Font.PLAIN, 10);
        while (x <= xMax) {
            Point2D tick = new Point2D.Double(x, yMax);
            
            at.transform(tick, tick);
            
            offScr.drawLine((int) tick.getX(), HORIZONTAL_SCALE_HEIGHT, (int) tick.getX(), HORIZONTAL_SCALE_HEIGHT-SCALE_TICK_LENGTH);
            
            String text = String.format("%." + displayDecimals + "f", x*displayMultiplier);
            TextLayout textTl = new TextLayout(text, f, frc);
            AffineTransform transform = new AffineTransform();
            transform.translate(tick.getX()-textTl.getBounds().getWidth()/2, HORIZONTAL_SCALE_HEIGHT - 2 - textTl.getBounds().getHeight());
            Shape outline = transform.createTransformedShape(textTl.getOutline(null));
            offScr.fill(outline);
            
            x += unitsPerDivision;
        }
        
        offScr.dispose();
    }

    public final Action openFileAction = new AbstractAction() {
        {
            putValue(NAME, "View Single Gerber File..."); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            FileDialog fileDialog = new FileDialog(GerberViewer.this);
            fileDialog.setFilenameFilter(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".gbr"); //$NON-NLS-1$
                }
            });
            fileDialog.setTitle("Select a single Gerber file for viewing...");
            fileDialog.setFile("*.gbr");
            fileDialog.setVisible(true);
            if (fileDialog.getFile() == null) {
                return;
            }

            
            mnFile.setEnabled(false);
            mntmWritePnPData.setEnabled(false);
            mntmWriteNetList.setEnabled(false);
            mntmSaveImage.setEnabled(false);
            mnOptions.setEnabled(false);
            btnAbort.setEnabled(true);

            parserMap = null;
            renderGerberImage();
            textArea.setText(null);
            GerberFileReader gerberFileReader = null;
            
            try {
                File file = new File(new File(fileDialog.getDirectory()), fileDialog.getFile());

                setTitle("GerberViewer - " + file.toString());
                parserMap = new HashMap<>();
                gerberFileReader = new GerberFileReader(file);
//                gerberFileReader.parseFileHeaderOnly();
                AttributeDictionary fileAttributes = gerberFileReader.getFileAttributes();
                Attribute fileFunction = fileAttributes.get(".FileFunction");
                if (fileFunction != null) {
                    parserMap.put(fileFunction.getValues().get(0), gerberFileReader);
                    textArea.append("-----------------------\n");
                    textArea.append("File = " + gerberFileReader.getGerberFile().getName() + ", with attributes:\n");
                    for (String attKey : fileAttributes.keySet()) {
                        textArea.append(fileAttributes.get(attKey).toString() + "\n");
                    }
                }
                else {
                    parserMap.put("Test", gerberFileReader);
                }
                gerberFileReader.parseFileInBackground((p) -> updateProgressBar(p), 
                        () -> drawGerberImage(), (ex) -> cleanUp(ex));
            }
            catch (Exception e) {
                cleanUp(e);
            }
            
        }
    };

    public final Action loadDirAction = new AbstractAction() {
        {
            putValue(NAME, "View Realistic Image of a Board..."); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            FileDialog fileDialog = new FileDialog(GerberViewer.this);
            fileDialog.setTitle("Select any one of the board's Gerber files...");
            fileDialog.setFilenameFilter(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".gbr"); //$NON-NLS-1$
                }
            });
            fileDialog.setFile("*.gbr");
            fileDialog.setVisible(true);
            if (fileDialog.getFile() == null) {
                return;
            }            
            File dir = new File(fileDialog.getDirectory());
            File file = new File(new File(fileDialog.getDirectory()), fileDialog.getFile());
            
            
            mnFile.setEnabled(false);
            mntmWritePnPData.setEnabled(false);
            mntmWriteNetList.setEnabled(false);
            mntmSaveImage.setEnabled(false);
            mnOptions.setEnabled(false);
            btnAbort.setEnabled(true);

            textArea.append("Searching for Gerber files...");
            Long start = System.currentTimeMillis();
            try {
                String sameCoordinatesString = "";
                if (file != null) {
                    GerberFileReader parser = new GerberFileReader(file);
//                    parser.parseFileHeaderOnly();
                    AttributeDictionary fileAttributes = parser.getFileAttributes();
                    if (fileAttributes == null) {
                        throw new Exception("Selected file has no file attributes. In order to view an image of a board, its Gerber files must follow the GerberX2 specification.");
                    }
                    Attribute sameCoordinatesAttribute = fileAttributes.get(".SameCoordinates");
                    if (sameCoordinatesAttribute == null) {
                        throw new Exception("Selected file has no \".SameCoordinates\" file attribute. In order to view an image of a board, its Gerber files must follow the GerberX2 specification.");
                    }
                    sameCoordinatesString = sameCoordinatesAttribute.getValues().get(0);
                }
                File[] files = dir.listFiles();
                parserMap = new HashMap<>();
                for (File gerberFile : files) {
                    if (gerberFile.getName().toLowerCase().endsWith(".gbr")) {
                        GerberFileReader parser = new GerberFileReader(gerberFile);
//                        parser.parseFileHeaderOnly();
                        AttributeDictionary fileAttributes = parser.getFileAttributes();
                        if (fileAttributes == null) {
                            continue;
                        }
                        Attribute testSameCoordinatesAttribute = fileAttributes.get(".SameCoordinates");
                        if (testSameCoordinatesAttribute == null) {
                            continue;
                        }
                        String testSameCoordinatesString = "";
                        testSameCoordinatesString = testSameCoordinatesAttribute.getValues().get(0);
                        //We only want to keep files that have the .SameCoordinates attribute value
                        //as the file that the user selected
                        if (sameCoordinatesString.equals(testSameCoordinatesString)) {
                            //But in the event multiple files with the same .FileFunction attributes
                            //are found, we only want to keep the latest one
                            if (parserMap.containsKey(fileAttributes.get(".FileFunction").toString())) {
                                String prevDateTime = parserMap.get(fileAttributes.get(".FileFunction").toString()).
                                        getFileAttributes().get(".CreationDate").getValues().get(0);
                                String currDataTime = fileAttributes.get(".CreationDate").getValues().get(0);
                                if (currDataTime.compareTo(prevDateTime) <= 0) {
                                    break;
                                }
                            }
                            parserMap.put(fileAttributes.get(".FileFunction").toString(), parser);
                        }
                    }
                }
                textArea.append(" completed in " + (System.currentTimeMillis() - start) + " ms\n");

                //Start parsing each file on its own thread
                textArea.append("Parsing Gerber files...\n");
                start = System.currentTimeMillis();
                final long start2 = start;
                for (String key : parserMap.keySet()) {
                    GerberFileReader parser = parserMap.get(key);
                    textArea.append("File = " + parser.getGerberFile().getName() + ", with attributes:\n");
                    AttributeDictionary fileAttributes = parser.getFileAttributes();
                    for (String attKey : fileAttributes.keySet()) {
                        textArea.append("    " + fileAttributes.get(attKey).toString() + "\n");
                    }
                    parser.parseFileInBackground(null, null, null);
                }
                
                //Setup another background thread to wait for all the parsers to complete
                SwingWorker<Void, java.lang.Double> backgroundWorker = new SwingWorker<Void, java.lang.Double>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        int completed = 0;
                        double total = parserMap.keySet().size();
                        while (completed < total) {
                            completed = 0;
                            for (String key : parserMap.keySet()) {
                                if (parserMap.get(key).isDone()) {
                                    completed++;
                                }
                            }
                            publish(completed/total);
                        }
                        return null;
                    }
                    
                    @Override
                    protected void process(List<java.lang.Double> chunkOfWork) {
                        for (java.lang.Double progress : chunkOfWork) {
                            updateProgressBar(progress);
                        }
                    }
                    
                    @Override
                    protected void done() {
                        textArea.append("Parsing Gerber files completed in " + (System.currentTimeMillis() - start2) + " ms\n");

                        drawGerberImage();
                    }
                };
                
                backgroundWorker.execute();
            }
            catch (Exception e) {
                textArea.append("\nParsing Gerber files aborted in " + (System.currentTimeMillis() - start) + " ms\n");
                cleanUp(e);
            }
        }
    };

    public final Action saveImageAction = new AbstractAction() {
        {
            putValue(NAME, "Save Image..."); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String[] validTypes = new String[] {"png", "bmp"}; //"jpeg", "gif", "wbmp" not supported due to alpha channel
            FileDialog fileDialog = new FileDialog(GerberViewer.this, "Save Image As...", FileDialog.SAVE); //$NON-NLS-1$
            fileDialog.setFilenameFilter(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    for (String type : validTypes) {
                        if (name.toLowerCase().endsWith("." + type)) {
                            return true; //$NON-NLS-1$
                        }
                    }
                    return false; //$NON-NLS-1$
                }
            });
            String initFile = "";
            for (String type : validTypes) {
                initFile = initFile + "*." + type + "; ";
            }
            fileDialog.setFile(initFile);
            fileDialog.setVisible(true);
            String filename = fileDialog.getFile();
            if (filename == null) {
                return;
            }
            boolean invalidType = true;
            int typeIdx = 0;
            for (String type : validTypes) {
                if (filename.toLowerCase().endsWith("." + type)) {
                    invalidType = false;
                    break;
                }
                typeIdx++;
            }
            if (invalidType) {
                return;
            }
            File file = new File(new File(fileDialog.getDirectory()), filename);
            try {
                if (!ImageIO.write(gerberImage, validTypes[typeIdx], file)) {
                    textArea.append("ERROR - Appropriate writer not found for type " + validTypes[typeIdx] + "\n");
                }
            }
            catch (IOException e1) {
                // TODO Auto-generated catch block
                displayStackTrace(e1);
            }
        }
    };
    
    public final Action writePnPDataAction = new AbstractAction() {
        {
            putValue(NAME, "Write PnP Data..."); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            FileDialog fileDialog = new FileDialog(GerberViewer.this, "Write Pick-and-Place Data To...", FileDialog.SAVE); //$NON-NLS-1$
            fileDialog.setFilenameFilter(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".csv"); //$NON-NLS-1$
                }
            });
            fileDialog.setFile("*.csv");
            fileDialog.setVisible(true);
            
            String filename = fileDialog.getFile();
            if (filename == null) {
                return;
            }
            if (!filename.toLowerCase().endsWith(".csv")) {
                filename = filename + ".csv";
            }
            filename = fileDialog.getDirectory() + filename;
            Map<String, String> map = new HashMap<>();
            for (String key : parserMap.keySet()) {
                GerberFileReader parser = parserMap.get(key);
                AttributeDictionary fileAttributes = parser.getFileAttributes();
                if (!fileAttributes.get(".FileFunction").getValues().get(0).equals("Component")) {
                    continue;
                }
                String side = "\"" + fileAttributes.get(".FileFunction").getValues().get(2) + "\"";
                for (GraphicalObject go : parser.getGraphicsStream().getStream()) {
                    AttributeDictionary goAttributes = go.getAttributes();
                    if (!goAttributes.get(".AperFunction").getValues().get(0).equals("ComponentMain")) {
                        continue;
                    }
                    Rectangle2D bounds = go.getArea().getBounds2D();
                    map.put(goAttributes.get(".C").getValues().get(0), 
                            goAttributes.get(".C").getValues().get(0) + "," +
                            goAttributes.get(".CFtp").getValues().get(0) + "," +
                            goAttributes.get(".CVal").getValues().get(0) + "," + 
                            side + "," +
                            bounds.getCenterX() + "," +
                            bounds.getCenterY() + "," +
                            goAttributes.get(".CRot").getValues().get(0));
                }
            }
            List<String> keyList = new ArrayList<>();
            keyList.addAll(map.keySet());
            Collections.sort(keyList, new Comparator<String>() {

                @Override
                public int compare(String s1, String s2) {
                    if (s1.startsWith("\"")) {
                        s1 = s1.substring(1);
                    }
                    if (s1.endsWith("\"")) {
                        s1 = s1.substring(0, s1.length() - 1);
                    }
                    if (s2.startsWith("\"")) {
                        s2 = s2.substring(1);
                    }
                    if (s2.endsWith("\"")) {
                        s2 = s2.substring(0, s2.length() - 1);
                    }
                    int i = s1.length() - 1;
                    while (i>0 && "0123456789".contains(s1.substring(i, i+1))) {
                        i--;
                    }
                    String s1Prefix = s1.substring(0, i+1);
                    int s1Suffix = -1;
                    try {
                        s1Suffix = Integer.parseInt(s1.substring(i+1));
                    }
                    catch (NumberFormatException e) {
                        //ok
                    }
                    int j = s2.length() - 1;
                    while (j>0 && "0123456789".contains(s2.substring(j, j+1))) {
                        j--;
                    }
                    String s2Prefix = s2.substring(0, j+1);
                    int s2Suffix = -1;
                    try {
                        s2Suffix = Integer.parseInt(s2.substring(j+1));
                    }
                    catch (NumberFormatException e) {
                        //ok
                    }
                    if (s1Prefix.equals(s2Prefix)) {
                        if (s1Suffix < s2Suffix) {
                            return -1;
                        }
                        else if (s1Suffix > s2Suffix) {
                            return 1;
                        }
                        else {
                            return 0;
                        }
                    }
                    return s1Prefix.compareTo(s2Prefix);
                }
            });
            
            try (BufferedWriter br = new BufferedWriter(new FileWriter(filename))) {
                br.append("Ref Des," +
                        "Package," +
                        "Value," + 
                        "Side," +
                        "Xpos," +
                        "Ypos," +
                        "Rot");
                br.newLine();
                for (String key : keyList) {
                    br.append(map.get(key));
                    br.newLine();
                }
            }
            catch (IOException e1) {
                // TODO Auto-generated catch block
                displayStackTrace(e1);
            }
        }
    };
    
    public final Action writeNetListAction = new AbstractAction() {
        {
            putValue(NAME, "Write Net List..."); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            FileDialog fileDialog = new FileDialog(GerberViewer.this, "Write Net List To...", FileDialog.SAVE); //$NON-NLS-1$
            fileDialog.setFilenameFilter(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".csv"); //$NON-NLS-1$
                }
            });
            fileDialog.setFile("*.csv");
            fileDialog.setVisible(true);
            
            String filename = fileDialog.getFile();
            if (filename == null) {
                return;
            }
            if (!filename.toLowerCase().endsWith(".csv")) {
                filename = filename + ".csv";
            }
            filename = fileDialog.getDirectory() + filename;
            List<String> list = new ArrayList<>();
            for (String key : parserMap.keySet()) {
                GerberFileReader parser = parserMap.get(key);

                //It might be tempting here to skip any file that's not an outer copper layer but 
                //that would miss embedded components such as etched inductors and capacitors

                goLoop: for (GraphicalObject go : parser.getGraphicsStream().getStream()) {
                    AttributeDictionary goAttributes = go.getAttributes();
                    Attribute pinAttribute = goAttributes.get(".P");
                    if (pinAttribute == null) {
                        continue;
                    }
                    Attribute netAttribute = goAttributes.get(".N");
                    if (netAttribute == null || netAttribute.getValues().size() == 0 ||
                            netAttribute.getValues().get(0).equals("N/C")) {
                        continue;
                    }
                    String item = netAttribute.getValues().get(0) + "," +
                            pinAttribute.getValues().get(0) + "," +
                            pinAttribute.getValues().get(1);
                    for (String check : list) {
                        if (item.equals(check)) {
                            //Skip any duplicates
                            continue goLoop;
                        }
                    }
                    list.add(item);
                }
            }
            Collections.sort(list);
           
            try (BufferedWriter br = new BufferedWriter(new FileWriter(filename))) {
                br.append("Net," +
                        "Ref Des," +
                        "Pin");
                br.newLine();
                for (String item : list) {
                    br.append(item);
                    br.newLine();
                }
            }
            catch (IOException e1) {
                // TODO Auto-generated catch block
                displayStackTrace(e1);
            }
        }
    };
    
    public final Action abortAction = new AbstractAction() {
        {
            putValue(NAME, "Abort"); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            parserMap.get(parserMap.keySet().toArray()[0]).cancel();
            backgroundImageRenderer.cancel(true);
        }
    };
    private JSplitPane splitPane;
    private JScrollPane scrollPaneText;
    private JTextArea textArea;
    private JMenu mnColorSchemeMenu;
    private JMenuItem mntmGreen;
    private JMenuItem mntmPurple;
    private JMenuItem mntmRed;
    private JMenuItem mntmYellow;
    private JMenuItem mntmBlue;
    private JMenuItem mntmWhite;
    private JMenuItem mntmBlack;
    private JMenuItem mntmCustom;
    
    private void updateProgressBar(double progress) {
        progressBar.setValue((int) (100*progress));
    }
    
    private void drawGerberImage() {
        gerberImage = null;
        zoomFactor = 1.0;
        Dimension newSize = getDefaultDisplayPanelSize();
        drawingPanelColumnHeader.setPreferredSize(new Dimension(newSize.width, HORIZONTAL_SCALE_HEIGHT));
        drawingPanelRowHeader.setPreferredSize(new Dimension(VERTICAL_SCALE_WIDTH, newSize.height));
        
        drawingPanel.revalidate();
        drawingPanelRowHeader.revalidate();
        drawingPanelColumnHeader.revalidate();
        drawingPanelUnit.revalidate();
        
        substrateArea = null;
        solderMaskArea = null;
        
        initializeBounds();
    }
    
    private void cleanUp(Exception ex) {
        if (ex != null) {
            displayStackTrace(ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        mnFile.setEnabled(true);
        mnOptions.setEnabled(true);
        btnAbort.setEnabled(false);
        progressBar.setValue(0);
    }
    
    private void displayStackTrace(Exception ex) {
        textArea.append("\n");
        boolean starting = true;
        for (StackTraceElement ste : ex.getStackTrace()) {
            if (starting) {
                textArea.append(ste.toString() + "\n");
                starting = false;
            }
            else {
                textArea.append("    " + ste.toString() + "\n");
            }
        }
    }
    
    /**
     * Reverses the specified path so that its iterator traverses the path in the opposite direction
     * @param path - the path to reverse
     * @return the reversed path
     */
    public static Path2D reversePath(Path2D path) {
        List<Integer> segTypes = new ArrayList<>();
        List<Point2D> points = new ArrayList<>();
        PathIterator pathIter = path.getPathIterator(null);
        double[] coords = new double[6];
        while (!pathIter.isDone()) {
            int segType = pathIter.currentSegment(coords);
            segTypes.add(segType);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    points.add(new Point2D.Double(coords[0], coords[1]));
                    break;
                case PathIterator.SEG_LINETO:
                    points.add(new Point2D.Double(coords[0], coords[1]));
                    break;
                case PathIterator.SEG_QUADTO:
                    points.add(new Point2D.Double(coords[0], coords[1]));
                    points.add(new Point2D.Double(coords[2], coords[3]));
                    break;
                case PathIterator.SEG_CUBICTO:
                    points.add(new Point2D.Double(coords[0], coords[1]));
                    points.add(new Point2D.Double(coords[2], coords[3]));
                    points.add(new Point2D.Double(coords[4], coords[5]));
                    break;
                case PathIterator.SEG_CLOSE:
                    break;
            }
            pathIter.next();
        }
        Collections.reverse(segTypes);
        Collections.reverse(points);
        Path2D reversedPath = new Path2D.Double(path.getWindingRule());
        Point2D pt1 = null;
        Point2D pt2;
        Point2D pt3;
        Point2D currentPoint = null;
        Point2D startingPoint = null;
        boolean starting = true;
        int idx = 0;
        for (int segType : segTypes) {
            if (starting) {
                currentPoint = points.get(idx);
                idx++;
                reversedPath.moveTo(currentPoint.getX(), currentPoint.getY());
                startingPoint = currentPoint;
                starting = false;
            }
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    if (idx < points.size()) {
                        pt1 = points.get(idx);
                        idx++;
                        reversedPath.moveTo(pt1.getX(), pt1.getY());
                        startingPoint = pt1;
                    }
                    break;
                case PathIterator.SEG_LINETO:
                    if (idx < points.size()) {
                        pt1 = points.get(idx);
                        idx++;
                    }
                    else {
                        pt1 = startingPoint;
                    }
                    reversedPath.lineTo(pt1.getX(), pt1.getY());
                    break;
                case PathIterator.SEG_QUADTO:
                    pt1 = points.get(idx);
                    idx++;
                    if (idx < points.size()) {
                        pt2 = points.get(idx);
                        idx++;
                    }
                    else {
                        pt2 = startingPoint;
                    }
                    reversedPath.quadTo(pt1.getX(), pt1.getY(), pt2.getX(), pt2.getY());
                    break;
                case PathIterator.SEG_CUBICTO:
                    pt1 = points.get(idx);
                    idx++;
                    pt2 = points.get(idx);
                    idx++;
                    if (idx < points.size()) {
                        pt3 = points.get(idx);
                        idx++;
                    }
                    else {
                        pt3 = startingPoint;
                    }
                    reversedPath.curveTo(pt1.getX(), pt1.getY(), pt2.getX(), pt2.getY(), 
                            pt3.getX(), pt3.getY());
                    break;
                case PathIterator.SEG_CLOSE:
                    pt1 = points.get(idx);
                    idx++;
                    reversedPath.lineTo(pt1.getX(), pt1.getY());
                    startingPoint = currentPoint;
                    break;
            }
            currentPoint = pt1;
        }
        return reversedPath;
    }

}
