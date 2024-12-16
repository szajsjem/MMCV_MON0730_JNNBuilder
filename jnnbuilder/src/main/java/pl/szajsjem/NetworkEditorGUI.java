package pl.szajsjem;

import com.beednn.Layer;
import pl.szajsjem.data.CSVLoaderDialog;
import pl.szajsjem.data.DataManager;
import pl.szajsjem.elements.Node;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;

public class NetworkEditorGUI extends JFrame {
    private final JPanel canvas;
    private final NodeManager nodeManager;
    private final StatusBar statusBar;
    private final ToolPanel toolPanel;
    private Point dragStart;
    private float zoomLevel = 1.0f;
    private final Point2D.Float panOffset = new Point2D.Float(0, 0);
    private AffineTransform canvasTransform = new AffineTransform();
    private DataManager dataManager;
    private CSVLoaderDialog.LoadedData currentData;
    private JFrame dataFrame;

    public NetworkEditorGUI() {
        setTitle("Neural Network Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);

        // Main layout
        setLayout(new BorderLayout());

        // Create components
        canvas = new CanvasPanel();
        nodeManager = new NodeManager(canvas, this);
        toolPanel = new ToolPanel(this);
        statusBar = new StatusBar();

        // Setup main split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(toolPanel);
        splitPane.setRightComponent(new JScrollPane(canvas));
        splitPane.setDividerLocation(250);

        // Add components
        add(createMenuBar(), BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        setupCanvasListeners();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new NetworkEditorGUI().setVisible(true);
        });
    }

    private void setupCanvasListeners() {
        // Add key listener
        canvas.setFocusable(true);
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                nodeManager.handleKeyPressed(e);
                canvas.repaint();
            }
        });
        InputMap inputMap = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = canvas.getActionMap();

        // Undo
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.undo();
                canvas.repaint();
            }
        });

        // Redo
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.redo();
                canvas.repaint();
            }
        });

        // Cut
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "cut");
        actionMap.put("cut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.cut();
            }
        });

        // Copy
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        actionMap.put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.copy();
            }
        });

        // Paste
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        actionMap.put("paste", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.paste();
            }
        });

        // Mouse wheel for zooming
        canvas.addMouseWheelListener(e -> {
            Point2D p = transformPoint(e.getPoint());
            float oldZoom = zoomLevel;

            // Calculate zoom
            if (e.getWheelRotation() < 0) {
                zoomLevel *= 1.1f;
            } else {
                zoomLevel /= 1.1f;
            }
            zoomLevel = Math.max(0.1f, Math.min(5.0f, zoomLevel));

            // Adjust pan to keep mouse position fixed
            double scale = zoomLevel / oldZoom;
            panOffset.x = e.getX() - (float) ((e.getX() - panOffset.x) * scale);
            panOffset.y = e.getY() - (float) ((e.getY() - panOffset.y) * scale);

            canvas.repaint();
        });

        // Mouse listeners
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Request focus for key events
                canvas.requestFocusInWindow();

                if (e.getButton() == MouseEvent.BUTTON2) {
                    dragStart = e.getPoint();
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    // Right click
                    Point2D transformedPoint = transformPoint(e.getPoint());
                    showContextMenu(e.getPoint());
                } else {
                    Point2D transformedPoint = transformPoint(e.getPoint());
                    nodeManager.handleMousePressed(
                            new Point((int) transformedPoint.getX(), (int) transformedPoint.getY()),
                            e.getModifiersEx()
                    );
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2) {
                    dragStart = null;
                } else {
                    Point2D transformedPoint = transformPoint(e.getPoint());
                    nodeManager.handleMouseReleased(
                            new Point((int) transformedPoint.getX(), (int) transformedPoint.getY())
                    );
                }
                canvas.repaint();
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    // Panning
                    panOffset.x += e.getX() - dragStart.x;
                    panOffset.y += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                } else {
                    // Node dragging
                    Point2D transformedPoint = transformPoint(e.getPoint());
                    nodeManager.handleMouseDragged(
                            new Point((int) transformedPoint.getX(), (int) transformedPoint.getY())
                    );
                }
                canvas.repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Point2D transformedPoint = transformPoint(e.getPoint());
                nodeManager.handleMouseMoved(
                        new Point((int) transformedPoint.getX(), (int) transformedPoint.getY())
                );

                // Update status bar coordinates
                statusBar.setCoordinates(
                        (int) transformedPoint.getX(),
                        (int) transformedPoint.getY()
                );

                canvas.repaint();
            }
        });
    }

    Point2D transformPoint(Point screenPoint) {
        try {
            Point2D.Float point = new Point2D.Float(screenPoint.x, screenPoint.y);
            AffineTransform inverse = canvasTransform.createInverse();
            Point2D.Float transformed = new Point2D.Float();
            inverse.transform(point, transformed);
            return transformed;
        } catch (NoninvertibleTransformException e) {
            return screenPoint;
        }
    }

    private void zoomIn() {
        Point center = new Point(canvas.getWidth() / 2, canvas.getHeight() / 2);
        float oldZoom = zoomLevel;

        // Increase zoom by 20%
        zoomLevel *= 1.2f;
        zoomLevel = Math.min(5.0f, zoomLevel);

        // Adjust pan to keep center point fixed
        double scale = zoomLevel / oldZoom;
        panOffset.x = center.x - (float) ((center.x - panOffset.x) * scale);
        panOffset.y = center.y - (float) ((center.y - panOffset.y) * scale);

        canvas.repaint();
    }

    private void zoomOut() {
        Point center = new Point(canvas.getWidth() / 2, canvas.getHeight() / 2);
        float oldZoom = zoomLevel;

        // Decrease zoom by 20%
        zoomLevel /= 1.2f;
        zoomLevel = Math.max(0.1f, zoomLevel);

        // Adjust pan to keep center point fixed
        double scale = zoomLevel / oldZoom;
        panOffset.x = center.x - (float) ((center.x - panOffset.x) * scale);
        panOffset.y = center.y - (float) ((center.y - panOffset.y) * scale);

        canvas.repaint();
    }

    private void fitToWindow() {
        if (nodeManager.getAllNodes().isEmpty()) {
            // Reset to default view if no nodes
            zoomLevel = 1.0f;
            panOffset.x = 0;
            panOffset.y = 0;
            canvas.repaint();
            return;
        }

        // Find bounds of all nodes
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Node node : nodeManager.getAllNodes()) {
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
            maxX = Math.max(maxX, node.x + node.width);
            maxY = Math.max(maxY, node.y + node.height);
        }

        // Add padding
        int padding = 50;
        minX -= padding;
        minY -= padding;
        maxX += padding;
        maxY += padding;

        // Calculate required zoom level
        float scaleX = (float) canvas.getWidth() / (maxX - minX);
        float scaleY = (float) canvas.getHeight() / (maxY - minY);
        zoomLevel = Math.min(scaleX, scaleY);
        zoomLevel = Math.max(0.1f, Math.min(5.0f, zoomLevel));

        // Center the content
        panOffset.x = (canvas.getWidth() - (maxX + minX) * zoomLevel) / 2;
        panOffset.y = (canvas.getHeight() - (maxY + minY) * zoomLevel) / 2;

        canvas.repaint();
    }

    private void loadData(JMenuItem manageDataItem) {
        currentData = CSVLoaderDialog.showDialog(this);
        if (currentData != null) {
            // Create data manager if it doesn't exist
            if (dataManager == null) {
                dataManager = new DataManager();
            }

            // Update data in manager
            dataManager.setData(currentData, currentData.inputColumnNames, currentData.outputColumnNames);

            manageDataItem.setEnabled(true); // Enable "Manage Data" item

            // Update status
            statusBar.setStatus("Data loaded: " + currentData.inputs.length + " samples");
        }
    }

    private void showDataManager() {
        if (currentData == null || dataManager == null) {
            JOptionPane.showMessageDialog(this,
                    "No data loaded. Please load data first.",
                    "No Data",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Create frame if it doesn't exist or is disposed
        if (dataFrame == null || !dataFrame.isDisplayable()) {
            dataFrame = new JFrame("Data Manager");
            dataFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            dataFrame.add(dataManager);
            dataFrame.setSize(800, 600);

            // Position relative to main window
            Point loc = getLocation();
            loc.translate(50, 50);
            dataFrame.setLocation(loc);
        }

        dataFrame.setVisible(true);
        dataFrame.toFront();
    }

    // Add getter for current data
    public CSVLoaderDialog.LoadedData getCurrentData() {
        return currentData;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(new JMenuItem("New Network"));
        fileMenu.add(new JMenuItem("Open..."));
        fileMenu.add(new JMenuItem("Save"));
        fileMenu.add(new JMenuItem("Save As..."));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem("Export..."));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem("Exit"));

        // Edit menu
        JMenu editMenu = new JMenu("Edit");

        // Undo/Redo
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(e -> {
            nodeManager.undo();
            canvas.repaint();
        });

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(e -> {
            nodeManager.redo();
            canvas.repaint();
        });

        // Cut/Copy/Paste
        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        cutItem.addActionListener(e -> nodeManager.cut());

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyItem.addActionListener(e -> nodeManager.copy());

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        pasteItem.addActionListener(e -> nodeManager.paste());

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteItem.addActionListener(e -> {
            for (Node node : new ArrayList<>(nodeManager.getSelectedNodes())) {
                nodeManager.deleteNode(node);
            }
        });

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.add(deleteItem);

        // View menu
        JMenu viewMenu = new JMenu("View");
        JMenuItem zoomInItem = new JMenuItem("Zoom In");
        zoomInItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
        zoomInItem.addActionListener(e -> zoomIn());

        JMenuItem zoomOutItem = new JMenuItem("Zoom Out");
        zoomOutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        zoomOutItem.addActionListener(e -> zoomOut());

        JMenuItem fitToWindowItem = new JMenuItem("Fit to Window");
        fitToWindowItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        fitToWindowItem.addActionListener(e -> fitToWindow());

        viewMenu.add(zoomInItem);
        viewMenu.add(zoomOutItem);
        viewMenu.add(fitToWindowItem);
        viewMenu.addSeparator();
        viewMenu.add(new JCheckBoxMenuItem("Show Grid"));
        viewMenu.add(new JCheckBoxMenuItem("Show Layer Properties"));


        // Data menu
        JMenu dataMenu = new JMenu("Data");
        JMenuItem manageDataItem = new JMenuItem("Manage Data");
        JMenuItem loadDataItem = new JMenuItem("Load Data...");
        loadDataItem.addActionListener(e -> loadData(manageDataItem));

        manageDataItem.addActionListener(e -> showDataManager());
        manageDataItem.setEnabled(false); // Initially disabled until data is loaded

        dataMenu.add(loadDataItem);
        dataMenu.add(manageDataItem);

        // Network menu
        JMenu networkMenu = new JMenu("Network");
        networkMenu.add(new JMenuItem("Validate"));
        networkMenu.add(new JMenuItem("Auto-Layout"));
        networkMenu.add(new JMenuItem("Training Settings..."));
        networkMenu.addSeparator();
        networkMenu.add(new JMenuItem("Start Training"));

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(dataMenu);
        menuBar.add(networkMenu);

        return menuBar;
    }

    private void showContextMenu(Point p) {
        JPopupMenu popup = new JPopupMenu();

        Node node = nodeManager.getNodeAt(p);
        if (node != null) {
            popup.add(new JMenuItem("Edit Properties..."));
            popup.add(new JMenuItem("Delete"));
            popup.addSeparator();
            popup.add(new JMenuItem("Bring to Front"));
            popup.add(new JMenuItem("Send to Back"));
        } else {
            popup.add(new JMenuItem("Paste"));
            popup.add(new JMenuItem("Select All"));
        }

        popup.show(canvas, p.x, p.y);
    }

    private class CanvasPanel extends JPanel {
        private static final int GRID_SIZE = 20;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Store original transform
            AffineTransform originalTransform = g2d.getTransform();

            // Apply zoom and pan
            canvasTransform = new AffineTransform();
            canvasTransform.translate(panOffset.x, panOffset.y);
            canvasTransform.scale(zoomLevel, zoomLevel);
            g2d.transform(canvasTransform);

            // Enable antialiasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw grid
            drawGrid(g2d);

            // Draw connections and nodes
            nodeManager.drawConnections(g2d);
            nodeManager.drawNodes(g2d);

            // Restore original transform
            g2d.setTransform(originalTransform);
        }

        private void drawGrid(Graphics2D g2d) {
            g2d.setColor(new Color(240, 240, 240));

            int width = getWidth();
            int height = getHeight();

            // Adjust for zoom and pan
            int startX = (int) (-panOffset.x / zoomLevel);
            int startY = (int) (-panOffset.y / zoomLevel);
            int endX = (int) ((width - panOffset.x) / zoomLevel);
            int endY = (int) ((height - panOffset.y) / zoomLevel);

            // Draw vertical lines
            for (int x = startX - (startX % GRID_SIZE); x <= endX; x += GRID_SIZE) {
                g2d.drawLine(x, startY, x, endY);
            }

            // Draw horizontal lines
            for (int y = startY - (startY % GRID_SIZE); y <= endY; y += GRID_SIZE) {
                g2d.drawLine(startX, y, endX, y);
            }
        }
    }

    private class ToolPanel extends JPanel {
        public ToolPanel(NetworkEditorGUI parent) {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            // Layer types panel
            JPanel layerPanel = new JPanel();
            layerPanel.setLayout(new BoxLayout(layerPanel, BoxLayout.Y_AXIS));
            layerPanel.setBorder(BorderFactory.createTitledBorder("Available Layers"));

            // Add layer buttons
            for (String layerType : Layer.getAvailableLayers()) {
                JButton btn = new JButton(layerType);
                btn.setAlignmentX(Component.LEFT_ALIGNMENT);
                btn.addActionListener(e -> {
                    nodeManager.createNode(layerType);
                    canvas.repaint();  // Add this as a backup
                });
                layerPanel.add(btn);
                layerPanel.add(Box.createVerticalStrut(5));
            }

            JScrollPane scrollPane = new JScrollPane(layerPanel);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Increase from default 1
            scrollPane.getVerticalScrollBar().setBlockIncrement(64); // Increase from default 10

            PropertiesPanel propsPanel = new PropertiesPanel(nodeManager);
            add(scrollPane, BorderLayout.CENTER);
            add(propsPanel, BorderLayout.SOUTH);
        }
    }

    private class StatusBar extends JPanel {
        private final JLabel statusLabel;
        private final JLabel coordsLabel;

        public StatusBar() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEtchedBorder());

            statusLabel = new JLabel("Ready");
            coordsLabel = new JLabel("0, 0");

            add(statusLabel, BorderLayout.WEST);
            add(coordsLabel, BorderLayout.EAST);
        }

        public void setStatus(String status) {
            statusLabel.setText(status);
        }

        public void setCoordinates(int x, int y) {
            coordsLabel.setText(String.format("%d, %d", x, y));
        }
    }
}