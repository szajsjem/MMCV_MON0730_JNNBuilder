package pl.szajsjem;

import pl.szajsjem.elements.Node;
import pl.szajsjem.elements.RNNNode;
import pl.szajsjem.jni.BeeDnnLib;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;

public class GraphingArea extends JFrame {
    private final JPanel palette;
    private final JPanel canvas;
    private final JTextField textField;
    private final ArrayList<Node> nodes = new ArrayList<>();
    private Node draggedNode = null;
    private final Point dragOffset = new Point();
    private Node sourceNode = null;  // For connection creation
    private boolean isDraggingFromOutput = false;

    public GraphingArea() {
        setTitle("Node Editor");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        // Create palette
        palette = new JPanel();
        palette.setPreferredSize(new Dimension(150, 0));
        palette.setBorder(BorderFactory.createTitledBorder("Palette"));

        String[] nodeTypes = BeeDnnLib.getLayerType();
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

                if (draggedNode != null) draggedNode.paint(g);
                // Draw nodes
                for (Node node : nodes) {
                    if (node != draggedNode)
                        node.paint(g);
                }
            }
        };
        canvas.setBackground(Color.LIGHT_GRAY);

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
                            if (!sourceNode.next.contains(targetNode)) {
                                sourceNode.next.add(targetNode);
                                targetNode.prev.add(sourceNode);
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
                                if (!targetNode.next.contains(draggedNode)) {
                                    targetNode.next.add(draggedNode);
                                    draggedNode.prev.add(targetNode);
                                }
                                break;
                            case -1: // draggedNode output -> targetNode input
                                if (!draggedNode.next.contains(targetNode)) {
                                    draggedNode.next.add(targetNode);
                                    targetNode.prev.add(draggedNode);
                                }
                                break;
                            case 2: // RNN feedback connection
                                if (targetNode instanceof RNNNode rnnNode) {
                                    if (!rnnNode.feedbackNodes.contains(draggedNode)) {
                                        rnnNode.feedbackNodes.add(draggedNode);
                                    }
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
        add(palette, BorderLayout.WEST);
        add(canvas, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Add popup menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem settingsItem = new JMenuItem("Settings");
        JMenuItem deleteItem = new JMenuItem("Delete Connection");
        popup.add(settingsItem);
        popup.add(deleteItem);

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
