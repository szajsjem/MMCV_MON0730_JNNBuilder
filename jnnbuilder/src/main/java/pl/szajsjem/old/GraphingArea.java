package pl.szajsjem.old;

import com.beednn.Layer;
import pl.szajsjem.ConnectionManager;
import pl.szajsjem.elements.Node;
import pl.szajsjem.elements.RNNNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

public class GraphingArea extends JFrame {
    private final JPanel palette;
    private final JPanel canvas;
    private final JTextField textField;
    private final ArrayList<Node> nodes = new ArrayList<>();
    private Node draggedNode = null;
    private final Point dragOffset = new Point();
    private Node sourceNode = null;  // For connection creation
    private boolean isDraggingFromOutput = false;
    private final ConnectionManager connectionManager;

    public GraphingArea() {
        setTitle("Node Editor");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        // Create palette
        palette = new JPanel();
        palette.setPreferredSize(new Dimension(150, 0));
        palette.setBorder(BorderFactory.createTitledBorder("Palette"));

        String[] nodeTypes = Layer.getAvailableLayers();
        for (String type : nodeTypes) {
            JButton btn = new JButton(type);
            btn.addActionListener(e -> createNode(type));
            palette.add(btn);
        }
        palette.setBackground(Color.LIGHT_GRAY);
        palette.setLayout(new BoxLayout(palette, BoxLayout.Y_AXIS));
        palette.setAlignmentX(LEFT_ALIGNMENT);
        palette.setAlignmentY(TOP_ALIGNMENT);
        palette.setOpaque(true);
        palette.setVisible(true);

        // Create canvas
        canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw connections
                g.setColor(Color.BLACK);
                for (Node node : nodes) {
                    for (Node next : node.next) {
                        g.drawLine(
                                node.x + node.width,
                                node.y + node.height/2,
                                next.x,
                                next.y + next.height/2
                        );
                    }
                }

                // Draw temporary connection line while dragging
                if (sourceNode != null && isDraggingFromOutput) {
                    Point mousePos = getMousePosition();
                    if (mousePos != null) {
                        g.drawLine(
                                sourceNode.x + sourceNode.width,
                                sourceNode.y + sourceNode.height / 2,
                                mousePos.x,
                                mousePos.y
                        );
                    }
                }

                if (draggedNode != null) draggedNode.paint(g, false);
                // Draw nodes
                for (Node node : nodes) {
                    if (node != draggedNode)
                        node.paint(g, false);
                }
            }
        };
        canvas.setBackground(Color.LIGHT_GRAY);

        connectionManager = new ConnectionManager(nodes);

        // Mouse listeners for drag, drop, and connection
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();
                sourceNode = null;
                isDraggingFromOutput = false;

                for (Node node : nodes) {
                    if (node.isOverOutputDot(p)) {
                        sourceNode = node;
                        isDraggingFromOutput = true;
                        return;
                    }

                    // Check if clicking on node body for dragging
                    if (e.getX() >= node.x && e.getX() <= node.x + node.width &&
                            e.getY() >= node.y && e.getY() <= node.y + node.height) {
                        draggedNode = node;
                        dragOffset.x = e.getX() - node.x;
                        dragOffset.y = e.getY() - node.y;
                        return;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Point p = e.getPoint();

                if (sourceNode != null && isDraggingFromOutput) {
                    for (Node targetNode : nodes) {
                        if (targetNode != sourceNode && targetNode.isOverInputDot(p)) {
                            boolean success = connectionManager.connectNodes(sourceNode, targetNode);
                            if (!success) {
                                JOptionPane.showMessageDialog(GraphingArea.this,
                                        "Cannot create connection - would create a cycle",
                                        "Invalid Connection",
                                        JOptionPane.WARNING_MESSAGE);
                            }
                        }
                    }
                }

                if (draggedNode != null) {
                    for (Node targetNode : nodes) {
                        if (targetNode == draggedNode) continue;

                        int connectionType = targetNode.isDotOverDot(draggedNode);
                        switch (connectionType) {
                            case 1: // targetNode output -> draggedNode input
                                boolean success = connectionManager.connectNodes(targetNode, draggedNode);
                                if (!success) {
                                    JOptionPane.showMessageDialog(GraphingArea.this,
                                            "Cannot create connection - would create a cycle",
                                            "Invalid Connection",
                                            JOptionPane.WARNING_MESSAGE);
                                }
                                break;
                            case -1: // draggedNode output -> targetNode input
                                success = connectionManager.connectNodes(draggedNode, targetNode);
                                if (!success) {
                                    JOptionPane.showMessageDialog(GraphingArea.this,
                                            "Cannot create connection - would create a cycle",
                                            "Invalid Connection",
                                            JOptionPane.WARNING_MESSAGE);
                                }
                                break;
                            case 2: // RNN feedback connection
                                if (targetNode instanceof RNNNode rnnNode) {
                                    connectionManager.connectRNNFeedback(rnnNode, draggedNode);
                                }
                                break;
                        }
                    }
                    adjustNodePosition(draggedNode);
                }

                draggedNode = null;
                sourceNode = null;
                isDraggingFromOutput = false;
                canvas.repaint();
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggedNode != null) {
                    draggedNode.x = e.getX() - dragOffset.x;
                    draggedNode.y = e.getY() - dragOffset.y;
                }
                updateHighlights(e.getPoint());
                canvas.repaint();
            }
        });

        // Create bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        textField = new JTextField();
        JButton applyButton = new JButton("Apply");
        bottomPanel.add(textField, BorderLayout.CENTER);
        bottomPanel.add(applyButton, BorderLayout.EAST);

        // Add components to frame
        add(new NetworkEditorMenuBar(this), BorderLayout.NORTH);
        add(palette, BorderLayout.WEST);
        add(canvas, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Add popup menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem settingsItem = new JMenuItem("Settings");
        JMenuItem deleteItem = new JMenuItem("Delete Connection");
        deleteItem.addActionListener(e -> {
            Point p = canvas.getMousePosition();
            if (p != null) {
                // Find nodes near the click
                Node clickedNode = null;
                for (Node node : nodes) {
                    if (node.contains(p)) {
                        clickedNode = node;
                        break;
                    }
                }


                if (clickedNode != null) {
                    // Show dialog to select which connection to delete
                    List<Node> connected = connectionManager.getConnectedNodes(clickedNode);
                    if (!connected.isEmpty()) {
                        Node[] options = connected.toArray(new Node[0]);
                        Node selected = (Node) JOptionPane.showInputDialog(
                                this,
                                "Select connection to delete:",
                                "Delete Connection",
                                JOptionPane.QUESTION_MESSAGE,
                                null,
                                options,
                                options[0]
                        );

                        if (selected != null) {
                            if (clickedNode instanceof RNNNode rnn &&
                                    rnn.feedbackNodes.contains(selected)) {
                                connectionManager.disconnectRNNFeedback(rnn, selected);
                            } else if (selected instanceof RNNNode rnn &&
                                    rnn.feedbackNodes.contains(clickedNode)) {
                                connectionManager.disconnectRNNFeedback(rnn, clickedNode);
                            } else if (clickedNode.next.contains(selected)) {
                                connectionManager.disconnectNodes(clickedNode, selected);
                            } else {
                                connectionManager.disconnectNodes(selected, clickedNode);
                            }
                            canvas.repaint();
                        }
                    }
                }
            }
        });
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    popup.show(canvas, e.getX(), e.getY());
                }
            }
        });
    }

    private void updateHighlights(Point currentPoint) {
        // Reset all highlights
        for (Node node : nodes) {
            node.inputHighlighted = false;
            node.outputHighlighted = false;
            if (node instanceof RNNNode) {
                ((RNNNode) node).feedbackHighlighted = false;
            }
        }

        if (draggedNode != null || sourceNode != null) {
            for (Node targetNode : nodes) {
                if (targetNode == draggedNode || targetNode == sourceNode) continue;

                if (isDraggingFromOutput) {
                    if (targetNode.isOverInputDot(currentPoint)) {
                        targetNode.inputHighlighted = true;
                    }
                } else {
                    if (targetNode.isOverOutputDot(currentPoint)) {
                        targetNode.outputHighlighted = true;
                    }
                    if (targetNode instanceof RNNNode &&
                            ((RNNNode) targetNode).isOverFeedbackDot(currentPoint)) {
                        ((RNNNode) targetNode).feedbackHighlighted = true;
                    }
                }
            }
        }
    }

    private void createNode(String type) {
        Node node;
        if (type.contains("RNN")) {
            node = new RNNNode(type);
        } else {
            node = new Node(type);
        }
        node.x = 200;
        node.y = 100;
        nodes.add(node);
        canvas.repaint();
    }

    private void adjustNodePosition(Node node) {
        boolean adjusted;
        do {
            adjusted = false;
            for (Node other : nodes) {
                if (other != node) {
                    if (Math.abs(node.x - other.x) < 120 &&
                            Math.abs(node.y - other.y) < 80) {
                        node.x += 20;
                        node.y += 20;
                        adjusted = true;
                    }
                }
            }
        } while (adjusted);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new GraphingArea().setVisible(true);
        });
    }
}
