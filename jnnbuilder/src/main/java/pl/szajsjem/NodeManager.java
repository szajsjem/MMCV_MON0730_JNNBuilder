package pl.szajsjem;

import pl.szajsjem.elements.Node;
import pl.szajsjem.elements.RNNNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;

public class NodeManager {
    private final List<Node> nodes = new ArrayList<>();
    private final ConnectionManager connectionManager;
    private final Set<Node> selectedNodes = new HashSet<>();
    // For undo/redo support
    private final Stack<UndoableAction> undoStack = new Stack<>();
    private final Stack<UndoableAction> redoStack = new Stack<>();
    private final JPanel canvas;
    private final NetworkEditorGUI parentgui;
    private Node draggedNode = null;
    private final Point dragOffset = new Point();
    private Node sourceNode = null;
    private boolean isDraggingFromOutput = false;
    private Point lastMousePosition = new Point();
    private final List<Node> clipboardNodes = new ArrayList<>();
    private final Point clipboardOffset = new Point();
    private final Map<Node, Node> nodeMapping = new HashMap<>();

    public NodeManager(JPanel canvas, NetworkEditorGUI networkEditorGUI) {
        this.canvas = canvas;
        this.parentgui = networkEditorGUI;
        connectionManager = new ConnectionManager(new ArrayList<>(nodes));
    }

    public void createNode(String type) {
        Node node;
        if (type.contains("RNN")) {
            node = new RNNNode(type);
        } else {
            node = new Node(type);
        }
        node.x = 200;
        node.y = 100;

        addUndoableAction(new CreateNodeAction(node));
        nodes.add(node);
        canvas.repaint();  // Add this line
    }

    private final List<Runnable> selectionListeners = new ArrayList<>();

    public void drawNodes(Graphics2D g2d) {

        // Draw dragged node on bottom
        if (draggedNode != null) {
            draggedNode.paint(g2d, selectedNodes.contains(draggedNode));
        }

        // Draw non-selected nodes
        for (Node node : nodes) {
            if (node != draggedNode) {
                node.paint(g2d, false);
            }
        }

        // Draw selected nodes
        for (Node node : selectedNodes) {
            if (draggedNode != node) {
                node.paint(g2d, true);
            }
        }
    }

    public void handleMousePressed(Point transformedPoint, int modifiers) {
        sourceNode = null;
        isDraggingFromOutput = false;
        lastMousePosition = transformedPoint;

        Node clickedNode = getNodeAt(transformedPoint);

        // Check for connection point clicks first
        if (clickedNode != null) {
            if (clickedNode.isOverOutputDot(transformedPoint)) {
                sourceNode = clickedNode;
                isDraggingFromOutput = true;
                notifySelectionListeners();
                return;
            }

            if (clickedNode.contains(transformedPoint)) {
                draggedNode = clickedNode;
                dragOffset.x = transformedPoint.x - clickedNode.x;
                dragOffset.y = transformedPoint.y - clickedNode.y;

                // Handle selection
                if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                    // Control-click: toggle selection without clearing others
                    if (selectedNodes.contains(clickedNode)) {
                        selectedNodes.remove(clickedNode);
                    } else {
                        selectedNodes.add(clickedNode);
                    }
                } else if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
                    // Shift-click: toggle selection
                    if (selectedNodes.contains(clickedNode)) {
                        selectedNodes.remove(clickedNode);
                    } else {
                        selectedNodes.add(clickedNode);
                    }
                } else {
                    // Regular click: select only this node if not already selected
                    if (!selectedNodes.contains(clickedNode)) {
                        selectedNodes.clear();
                        selectedNodes.add(clickedNode);
                    }
                }
                notifySelectionListeners();
                return;
            }
        }

        // Clicked empty space
        if ((modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) == 0) {
            selectedNodes.clear();
        }
        notifySelectionListeners();
    }

    // For moving multiple selected nodes
    public void handleMouseDragged(Point transformedPoint) {
        lastMousePosition = transformedPoint;

        if (draggedNode != null) {
            // Calculate the movement delta
            int dx = transformedPoint.x - dragOffset.x - draggedNode.x;
            int dy = transformedPoint.y - dragOffset.y - draggedNode.y;

            // Move all selected nodes
            for (Node node : selectedNodes) {
                node.x += dx;
                node.y += dy;

                // Snap to grid
                node.x = Math.round(node.x / 20f) * 20;
                node.y = Math.round(node.y / 20f) * 20;
            }

            // Update dragged node position
            draggedNode.x = transformedPoint.x - dragOffset.x;
            draggedNode.y = transformedPoint.y - dragOffset.y;
            draggedNode.x = Math.round(draggedNode.x / 20f) * 20;
            draggedNode.y = Math.round(draggedNode.y / 20f) * 20;

            updateNodeHighlights(transformedPoint);
        }
    }

    public void handleMouseReleased(Point transformedPoint) {
        if (sourceNode != null && isDraggingFromOutput) {
            for (Node targetNode : nodes) {
                if (targetNode != sourceNode && targetNode.isOverInputDot(transformedPoint)) {
                    tryCreateConnection(sourceNode, targetNode);
                }
            }
        }

        if (draggedNode != null) {
            handlePotentialConnections(transformedPoint);
            draggedNode = null;
        }

        sourceNode = null;
        isDraggingFromOutput = false;
    }

    public void handleMouseMoved(Point transformedPoint) {
        lastMousePosition = transformedPoint;
        updateNodeHighlights(transformedPoint);
    }

    public void drawConnections(Graphics2D g2d) {
        // Set up nice looking lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(2.0f));

        // Draw regular connections
        for (Node node : nodes) {
            for (Node next : node.next) {
                drawConnection(g2d, node, next, false);
            }

            // Draw RNN feedback connections
            if (node instanceof RNNNode rnnNode) {
                for (Node feedback : rnnNode.feedbackNodes) {
                    drawConnection(g2d, rnnNode, feedback, true);
                }
            }
        }

        // Draw temporary connection while dragging
        if (sourceNode != null && isDraggingFromOutput) {
            g2d.setColor(Color.GRAY);
            g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{9}, 0));
            drawBezierConnection(g2d,
                    new Point(sourceNode.x + sourceNode.width, sourceNode.y + sourceNode.height / 2),
                    lastMousePosition);
        }
    }

    private void drawConnection(Graphics2D g2d, Node from, Node to, boolean isFeedback) {
        Point start = new Point(from.x + from.width, from.y + from.height / 2);
        Point end = new Point(to.x, to.y + to.height / 2);

        // Different styles for different connection types
        if (isFeedback) {
            g2d.setColor(new Color(0, 100, 0));
            g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{9}, 0));
        } else {
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2.0f));
        }

        drawBezierConnection(g2d, start, end);
    }

    private void drawBezierConnection(Graphics2D g2d, Point start, Point end) {
        int controlDist = 50;
        Point2D.Float ctrl1 = new Point2D.Float(start.x + controlDist, start.y);
        Point2D.Float ctrl2 = new Point2D.Float(end.x - controlDist, end.y);

        Path2D.Float path = new Path2D.Float();
        path.moveTo(start.x, start.y);
        path.curveTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, end.x, end.y);
        g2d.draw(path);
    }

    public Node getNodeAt(Point p) {
        // Check in reverse order to get top-most node
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node.contains(p)) {
                return node;
            }
        }
        return null;
    }

    public void handleDragging(Point p) {
        if (draggedNode != null) {
            draggedNode.x = p.x - dragOffset.x;
            draggedNode.y = p.y - dragOffset.y;

            // Snap to grid
            draggedNode.x = Math.round(draggedNode.x / 20f) * 20;
            draggedNode.y = Math.round(draggedNode.y / 20f) * 20;

            updateNodeHighlights(p);
        }
    }

    public void stopDragging(Point p) {
        if (draggedNode != null) {
            // Record position for undo
            addUndoableAction(new MoveNodeAction(draggedNode,
                    new Point(draggedNode.x - (p.x - dragOffset.x),
                            draggedNode.y - (p.y - dragOffset.y)),
                    new Point(draggedNode.x, draggedNode.y)));

            // Check for connections
            handlePotentialConnections(p);

            draggedNode = null;
        }
    }

    private void handlePotentialConnections(Point p) {
        for (Node targetNode : nodes) {
            if (targetNode == draggedNode) continue;

            int connectionType = targetNode.isDotOverDot(draggedNode);
            switch (connectionType) {
                case 1: // targetNode output -> draggedNode input
                    tryCreateConnection(targetNode, draggedNode);
                    break;
                case -1: // draggedNode output -> targetNode input
                    tryCreateConnection(draggedNode, targetNode);
                    break;
                case 2: // RNN feedback connection
                    if (targetNode instanceof RNNNode rnnNode) {
                        connectionManager.connectRNNFeedback(rnnNode, draggedNode);
                    }
                    break;
            }
        }
    }

    private void tryCreateConnection(Node from, Node to) {
        boolean success = connectionManager.connectNodes(from, to);
        if (success) {
            addUndoableAction(new CreateConnectionAction(from, to));
        }
    }

    private void updateNodeHighlights(Point p) {
        // Reset all highlights
        for (Node node : nodes) {
            node.inputHighlighted = false;
            node.outputHighlighted = false;
            if (node instanceof RNNNode) {
                ((RNNNode) node).feedbackHighlighted = false;
            }
        }

        // Update highlights based on current dragging
        if (draggedNode != null || sourceNode != null) {
            for (Node targetNode : nodes) {
                if (targetNode == draggedNode || targetNode == sourceNode) continue;

                if (isDraggingFromOutput) {
                    if (targetNode.isOverInputDot(p)) {
                        targetNode.inputHighlighted = true;
                    }
                } else {
                    if (targetNode.isOverOutputDot(p)) {
                        targetNode.outputHighlighted = true;
                    }
                    if (targetNode instanceof RNNNode &&
                            ((RNNNode) targetNode).isOverFeedbackDot(p)) {
                        ((RNNNode) targetNode).feedbackHighlighted = true;
                    }
                }
            }
        }
    }

    public void handleKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            // Delete selected nodes
            for (Node node : new ArrayList<>(selectedNodes)) {
                deleteNode(node);
            }
            selectedNodes.clear();
        } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
            // Select all
            selectedNodes.clear();
            selectedNodes.addAll(nodes);
            notifySelectionListeners();
        }
    }

    public void deleteNode(Node node) {
        if (node != null) {
            addUndoableAction(new DeleteNodeAction(node));
            connectionManager.disconnectAll(node);
            nodes.remove(node);
            selectedNodes.remove(node);
            canvas.repaint();  // Add this line
            notifySelectionListeners();
        }
    }

    public void cut() {
        if (!selectedNodes.isEmpty()) {
            copy();
            // Add cut action to undo stack before deleting nodes
            addUndoableAction(new CutAction(new ArrayList<>(selectedNodes)));

            // Delete selected nodes
            for (Node node : new ArrayList<>(selectedNodes)) {
                connectionManager.disconnectAll(node);
                nodes.remove(node);
            }
            selectedNodes.clear();
            canvas.repaint();
            notifySelectionListeners();
        }
    }

    public void copy() {
        if (!selectedNodes.isEmpty()) {
            clipboardNodes.clear();
            nodeMapping.clear();  // Clear previous mapping

            // Store offset from first selected node for pasting
            Node firstNode = selectedNodes.iterator().next();
            clipboardOffset.x = firstNode.x;
            clipboardOffset.y = firstNode.y;

            // First pass: create all nodes
            for (Node node : selectedNodes) {
                Node nodeCopy;
                if (node instanceof RNNNode) {
                    nodeCopy = new RNNNode(node.getLabel());
                } else {
                    nodeCopy = new Node(node.getLabel());
                }
                nodeCopy.x = node.x;
                nodeCopy.y = node.y;
                clipboardNodes.add(nodeCopy);
                nodeMapping.put(node, nodeCopy);  // Store mapping
            }

            // Second pass: copy connections between selected nodes
            for (Node originalNode : selectedNodes) {
                Node copiedNode = nodeMapping.get(originalNode);

                // Copy normal connections
                for (Node nextNode : originalNode.next) {
                    if (selectedNodes.contains(nextNode)) {
                        Node copiedNextNode = nodeMapping.get(nextNode);
                        copiedNode.next.add(copiedNextNode);
                        copiedNextNode.prev.add(copiedNode);
                    }
                }

                // Copy RNN feedback connections
                if (originalNode instanceof RNNNode originalRNN && copiedNode instanceof RNNNode copiedRNN) {
                    for (Node feedbackNode : originalRNN.feedbackNodes) {
                        if (selectedNodes.contains(feedbackNode)) {
                            copiedRNN.feedbackNodes.add(nodeMapping.get(feedbackNode));
                        }
                    }
                }
            }
            notifySelectionListeners();
        }
    }

    public void paste() {
        if (!clipboardNodes.isEmpty()) {
            List<Node> pastedNodes = new ArrayList<>();
            nodeMapping.clear();
            selectedNodes.clear();

            // Calculate paste offset
            Point mouse = canvas.getMousePosition();
            if (mouse == null) {
                clipboardOffset.x += 20;
                clipboardOffset.y += 20;
            }

            // First pass: create all nodes
            for (Node node : clipboardNodes) {
                Node newNode;
                if (node instanceof RNNNode) {
                    newNode = new RNNNode(node.getLabel());
                } else {
                    newNode = new Node(node.getLabel());
                }

                if (mouse != null) {
                    Point2D transformed = parentgui.transformPoint(mouse);
                    newNode.x = (int) (transformed.getX() + (node.x - clipboardOffset.x));
                    newNode.y = (int) (transformed.getY() + (node.y - clipboardOffset.y));
                } else {
                    newNode.x = node.x + 20;
                    newNode.y = node.y + 20;
                }

                // Snap to grid
                newNode.x = Math.round(newNode.x / 20f) * 20;
                newNode.y = Math.round(newNode.y / 20f) * 20;

                nodes.add(newNode);
                selectedNodes.add(newNode);
                pastedNodes.add(newNode);
                nodeMapping.put(node, newNode);  // Store mapping
            }

            // Second pass: recreate connections
            for (Node clipboardNode : clipboardNodes) {
                Node pastedNode = nodeMapping.get(clipboardNode);

                // Recreate normal connections
                for (Node nextNode : clipboardNode.next) {
                    Node pastedNextNode = nodeMapping.get(nextNode);
                    if (pastedNextNode != null) {
                        connectionManager.connectNodes(pastedNode, pastedNextNode);
                    }
                }

                // Recreate RNN feedback connections
                if (clipboardNode instanceof RNNNode clipboardRNN && pastedNode instanceof RNNNode pastedRNN) {
                    for (Node feedbackNode : clipboardRNN.feedbackNodes) {
                        Node pastedFeedbackNode = nodeMapping.get(feedbackNode);
                        if (pastedFeedbackNode != null) {
                            connectionManager.connectRNNFeedback(pastedRNN, pastedFeedbackNode);
                        }
                    }
                }
            }

            // Add paste action to undo stack
            addUndoableAction(new PasteAction(pastedNodes));

            // Update clipboard nodes with new positions
            clipboardNodes.clear();
            copy();

            canvas.repaint();
            notifySelectionListeners();
        }
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            UndoableAction action = undoStack.pop();
            action.undo();
            redoStack.push(action);
            canvas.repaint();  // Add this line
            notifySelectionListeners();
        }
    }

    private void addUndoableAction(UndoableAction action) {
        undoStack.push(action);
        redoStack.clear();
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            UndoableAction action = redoStack.pop();
            action.redo();
            undoStack.push(action);
            canvas.repaint();  // Add this line
            notifySelectionListeners();
        }
    }

    public void addSelectionListener(Runnable listener) {
        selectionListeners.add(listener);
    }

    // Modify the parts where selection changes to call notifySelectionListeners()
    private void notifySelectionListeners() {
        for (Runnable listener : selectionListeners) {
            listener.run();
        }
    }

    public Set<Node> getSelectedNodes() {
        return selectedNodes;
    }

    public List<Node> getAllNodes() {
        return nodes;
    }

    // Undoable Action interfaces and implementations
    private interface UndoableAction {
        void undo();

        void redo();
    }

    private class CreateNodeAction implements UndoableAction {
        private final Node node;

        public CreateNodeAction(Node node) {
            this.node = node;
        }

        @Override
        public void undo() {
            nodes.remove(node);
        }

        @Override
        public void redo() {
            nodes.add(node);
        }
    }

    private class DeleteNodeAction implements UndoableAction {
        private final Node node;
        private final List<Node> prevNodes;
        private final List<Node> nextNodes;
        private final List<Node> feedbackNodes;

        public DeleteNodeAction(Node node) {
            this.node = node;
            this.prevNodes = new ArrayList<>(node.prev);
            this.nextNodes = new ArrayList<>(node.next);
            this.feedbackNodes = node instanceof RNNNode ?
                    new ArrayList<>(((RNNNode) node).feedbackNodes) :
                    new ArrayList<>();
        }

        @Override
        public void undo() {
            nodes.add(node);
            for (Node prev : prevNodes) {
                connectionManager.connectNodes(prev, node);
            }
            for (Node next : nextNodes) {
                connectionManager.connectNodes(node, next);
            }
            if (node instanceof RNNNode rnn) {
                for (Node feedback : feedbackNodes) {
                    connectionManager.connectRNNFeedback(rnn, feedback);
                }
            }
        }

        @Override
        public void redo() {
            connectionManager.disconnectAll(node);
            nodes.remove(node);
        }
    }

    private class CutAction implements UndoableAction {
        private final List<Node> cutNodes;
        private final List<Node> prevNodes;
        private final List<Node> nextNodes;
        private final List<Node> feedbackNodes;

        public CutAction(List<Node> nodes) {
            this.cutNodes = new ArrayList<>(nodes);
            this.prevNodes = new ArrayList<>();
            this.nextNodes = new ArrayList<>();
            this.feedbackNodes = new ArrayList<>();

            // Store all connections
            for (Node node : nodes) {
                prevNodes.addAll(node.prev);
                nextNodes.addAll(node.next);
                if (node instanceof RNNNode rnn) {
                    feedbackNodes.addAll(rnn.feedbackNodes);
                }
            }
        }

        @Override
        public void undo() {
            // Restore nodes
            nodes.addAll(cutNodes);

            // Restore connections
            for (Node node : cutNodes) {
                // Restore regular connections
                for (Node prev : prevNodes) {
                    if (prev.next.contains(node)) {
                        connectionManager.connectNodes(prev, node);
                    }
                }
                for (Node next : nextNodes) {
                    if (node.next.contains(next)) {
                        connectionManager.connectNodes(node, next);
                    }
                }

                // Restore RNN feedback connections
                if (node instanceof RNNNode rnn) {
                    for (Node feedback : feedbackNodes) {
                        if (rnn.feedbackNodes.contains(feedback)) {
                            connectionManager.connectRNNFeedback(rnn, feedback);
                        }
                    }
                }
            }
        }

        @Override
        public void redo() {
            // Remove all connections and nodes again
            for (Node node : cutNodes) {
                connectionManager.disconnectAll(node);
                nodes.remove(node);
            }
        }
    }

    private class PasteAction implements UndoableAction {
        private final List<Node> pastedNodes;

        public PasteAction(List<Node> nodes) {
            this.pastedNodes = new ArrayList<>(nodes);
        }

        @Override
        public void undo() {
            // Remove pasted nodes
            for (Node node : pastedNodes) {
                connectionManager.disconnectAll(node);
                nodes.remove(node);
                selectedNodes.remove(node);
            }
        }

        @Override
        public void redo() {
            // Restore pasted nodes
            nodes.addAll(pastedNodes);
            selectedNodes.addAll(pastedNodes);
        }
    }

    private class MoveNodeAction implements UndoableAction {
        private final Node node;
        private final Point oldPos;
        private final Point newPos;

        public MoveNodeAction(Node node, Point oldPos, Point newPos) {
            this.node = node;
            this.oldPos = oldPos;
            this.newPos = newPos;
        }

        @Override
        public void undo() {
            node.x = oldPos.x;
            node.y = oldPos.y;
        }

        @Override
        public void redo() {
            node.x = newPos.x;
            node.y = newPos.y;
        }
    }

    private class CreateConnectionAction implements UndoableAction {
        private final Node from;
        private final Node to;

        public CreateConnectionAction(Node from, Node to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public void undo() {
            connectionManager.disconnectNodes(from, to);
        }

        @Override
        public void redo() {
            connectionManager.connectNodes(from, to);
        }
    }
}