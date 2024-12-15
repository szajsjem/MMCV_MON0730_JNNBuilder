package pl.szajsjem;


import pl.szajsjem.elements.ConnectionPoint;
import pl.szajsjem.elements.Node;
import pl.szajsjem.elements.SpecialNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;

public class NodeManager {
    private final List<Node> nodes = new ArrayList<>();
    private final ConnectionManager connectionManager;
    private final Set<Node> selectedNodes = new HashSet<>();
    private final Stack<UndoableAction> undoStack = new Stack<>();
    private final Stack<UndoableAction> redoStack = new Stack<>();
    private final JPanel canvas;
    private final NetworkEditorGUI parentgui;
    private Node draggedNode = null;
    private final Point dragOffset = new Point();
    private final List<Runnable> selectionListeners = new ArrayList<>();
    private Point lastMousePosition = new Point();
    private final List<Node> clipboardNodes = new ArrayList<>();
    private final Point clipboardOffset = new Point();
    private final Map<Node, Node> nodeMapping = new HashMap<>();
    private ConnectionPoint sourcePoint = null;

    public NodeManager(JPanel canvas, NetworkEditorGUI networkEditorGUI) {
        this.canvas = canvas;
        this.parentgui = networkEditorGUI;
        connectionManager = new ConnectionManager(new ArrayList<>(nodes));
    }

    public void createNode(String type) {
        Node node;
        if (Node.isSpecial(type)) {
            node = new SpecialNode(type);
        } else {
            node = new Node(type);
        }
        node.x = 200;
        node.y = 100;

        addUndoableAction(new CreateNodeAction(node));
        nodes.add(node);
        canvas.repaint();
    }

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
        sourcePoint = null;
        lastMousePosition = transformedPoint;

        Node clickedNode = getNodeAt(transformedPoint);

        // Check for connection point clicks first
        if (clickedNode != null) {
            ConnectionPoint clickedPoint = clickedNode.isOverDot(transformedPoint);
            if (clickedPoint != null) {
                sourcePoint = clickedPoint;
                notifySelectionListeners();
                return;
            }

            if (clickedNode.contains(transformedPoint)) {
                draggedNode = clickedNode;
                dragOffset.x = transformedPoint.x - clickedNode.x;
                dragOffset.y = transformedPoint.y - clickedNode.y;

                // Handle selection
                if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
                    if (selectedNodes.contains(clickedNode)) {
                        selectedNodes.remove(clickedNode);
                    } else {
                        selectedNodes.add(clickedNode);
                    }
                } else if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
                    if (selectedNodes.contains(clickedNode)) {
                        selectedNodes.remove(clickedNode);
                    } else {
                        selectedNodes.add(clickedNode);
                    }
                } else {
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

            updateConnectionPointHighlights(transformedPoint);
        }
    }

    public void handleMouseReleased(Point transformedPoint) {
        if (sourcePoint != null) {
            Node targetNode = getNodeAt(transformedPoint);
            if (targetNode != null) {
                ConnectionPoint targetPoint = targetNode.isOverDot(transformedPoint);
                if (targetPoint != null) {
                    tryCreateConnection(sourcePoint, targetPoint);
                }
            }
        }

        if (draggedNode != null) {
            handlePotentialConnections(transformedPoint);
            draggedNode = null;
        }

        sourcePoint = null;
    }

    public void handleMouseMoved(Point transformedPoint) {
        lastMousePosition = transformedPoint;
        updateConnectionPointHighlights(transformedPoint);
    }

    public void drawConnections(Graphics2D g2d) {
        // Draw temporary connection while dragging
        if (sourcePoint != null && lastMousePosition != null) {
            g2d.setColor(Color.GRAY);
            g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{9}, 0));

            Point startPoint = sourcePoint.getAbsolutePos();

            // Draw temp connection with bezier curve
            int controlDist = 50;
            Point2D.Float ctrl1, ctrl2;

            // Adjust control points based on whether source is input or output
            if (!sourcePoint.isInput()) {
                ctrl1 = new Point2D.Float(startPoint.x + controlDist, startPoint.y);
                ctrl2 = new Point2D.Float(lastMousePosition.x - controlDist, lastMousePosition.y);
            } else {
                ctrl1 = new Point2D.Float(startPoint.x - controlDist, startPoint.y);
                ctrl2 = new Point2D.Float(lastMousePosition.x + controlDist, lastMousePosition.y);
            }

            // Draw the bezier curve
            java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
            path.moveTo(startPoint.x, startPoint.y);
            path.curveTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, lastMousePosition.x, lastMousePosition.y);
            g2d.draw(path);
        }
    }

    private void updateConnectionPointHighlights(Point p) {
        // Reset all highlights
        for (Node node : nodes) {
            node.prev.highlighted = false;
            node.next.highlighted = false;
            if (node instanceof SpecialNode specialNode) {
                for (ConnectionPoint sp : specialNode.specialPoints) {
                    sp.highlighted = false;
                }
            }
        }

        // Update highlights based on current dragging
        if (sourcePoint != null) {
            Node targetNode = getNodeAt(p);
            if (targetNode != null) {
                ConnectionPoint targetPoint = targetNode.isOverDot(p);
                if (targetPoint != null && canConnect(sourcePoint, targetPoint)) {
                    targetPoint.highlighted = true;
                }
            }
        }
    }

    private boolean canConnect(ConnectionPoint source, ConnectionPoint target) {
        // Can't connect to self
        if (source.parent == target.parent) return false;

        // One must be input, one must be output
        return source.isInput() != target.isInput();
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
        // We only care about potential connections if we're dragging a node
        if (draggedNode == null) return;

        for (Node targetNode : nodes) {
            if (targetNode == draggedNode) continue;

            // Check both nodes' connection points
            ConnectionPoint draggedPoint = draggedNode.isOverDot(p);
            ConnectionPoint targetPoint = targetNode.isOverDot(p);

            if (draggedPoint != null && targetPoint != null) {
                // Both have connection points near each other
                if (canConnect(draggedPoint, targetPoint)) {
                    // Connect them respecting input/output direction
                    ConnectionPoint output = draggedPoint.isInput() ? targetPoint : draggedPoint;
                    ConnectionPoint input = draggedPoint.isInput() ? draggedPoint : targetPoint;
                    tryCreateConnection(output, input);
                }
            }
        }
    }

    private void tryCreateConnection(ConnectionPoint source, ConnectionPoint target) {
        if (!canConnect(source, target)) return;

        // Always connect from output to input
        ConnectionPoint output = source.isInput() ? target : source;
        ConnectionPoint input = source.isInput() ? source : target;

        if (!output.connected.contains(input)) {
            addUndoableAction(new CreateConnectionAction(output, input));
            output.connected.add(input);
        }
    }

    private void updateNodeHighlights(Point p) {
        // Reset all highlights
        for (Node node : nodes) {
            node.prev.highlighted = false;
            node.next.highlighted = false;
            if (node instanceof SpecialNode specialNode) {
                for (ConnectionPoint sp : specialNode.specialPoints) {
                    sp.highlighted = false;
                }
            }
        }

        // If we're dragging a node, check for potential connections
        if (draggedNode != null) {
            for (Node targetNode : nodes) {
                if (targetNode == draggedNode) continue;

                ConnectionPoint draggedPoint = draggedNode.isOverDot(p);
                ConnectionPoint targetPoint = targetNode.isOverDot(p);

                if (draggedPoint != null && targetPoint != null && canConnect(draggedPoint, targetPoint)) {
                    draggedPoint.highlighted = true;
                    targetPoint.highlighted = true;
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

            // First pass: create all nodes and their connection points
            for (Node node : selectedNodes) {
                Node nodeCopy;
                if (node instanceof SpecialNode) {
                    nodeCopy = new SpecialNode(node.getLabel());
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

                // Copy connections from output points
                copyConnectionPoint(originalNode.next, copiedNode.next);

                // Copy connections from input points
                copyConnectionPoint(originalNode.prev, copiedNode.prev);

                // Copy special point connections for SpecialNodes
                if (originalNode instanceof SpecialNode originalSpecial &&
                        copiedNode instanceof SpecialNode copiedSpecial) {
                    for (int i = 0; i < originalSpecial.specialPoints.size(); i++) {
                        ConnectionPoint originalPoint = originalSpecial.specialPoints.get(i);
                        ConnectionPoint copiedPoint = copiedSpecial.specialPoints.get(i);
                        copyConnectionPoint(originalPoint, copiedPoint);
                    }
                }
            }
            notifySelectionListeners();
        }
    }

    private void copyConnectionPoint(ConnectionPoint original, ConnectionPoint copy) {
        for (ConnectionPoint connected : original.connected) {
            Node connectedNode = connected.parent;
            if (selectedNodes.contains(connectedNode)) {
                Node copiedConnectedNode = nodeMapping.get(connectedNode);
                // Find the corresponding connection point in the copied node
                ConnectionPoint copiedConnectedPoint = findCorrespondingPoint(connected, copiedConnectedNode);
                if (copiedConnectedPoint != null) {
                    if (copy.isInput()) {
                        copiedConnectedPoint.connected.add(copy);
                    } else {
                        copy.connected.add(copiedConnectedPoint);
                    }
                }
            }
        }
    }

    private ConnectionPoint findCorrespondingPoint(ConnectionPoint original, Node copiedNode) {
        // Check if it's the main input or output point
        if (original == original.parent.prev) return copiedNode.prev;
        if (original == original.parent.next) return copiedNode.next;

        // Check special points if applicable
        if (original.parent instanceof SpecialNode originalSpecial && copiedNode instanceof SpecialNode copiedSpecial) {
            int index = originalSpecial.specialPoints.indexOf(original);
            if (index >= 0 && index < copiedSpecial.specialPoints.size()) {
                return copiedSpecial.specialPoints.get(index);
            }
        }
        return null;
    }

    public void paste() {
        if (!clipboardNodes.isEmpty()) {
            List<Node> pastedNodes = new ArrayList<>();
            nodeMapping.clear();
            selectedNodes.clear();

            // Calculate paste offset
            Point mouse = canvas.getMousePosition();
            Point2D pasteLocation = null;
            if (mouse != null) {
                pasteLocation = parentgui.transformPoint(mouse);
            }

            // First pass: create all nodes
            for (Node node : clipboardNodes) {
                Node newNode;
                if (node instanceof SpecialNode) {
                    newNode = new SpecialNode(node.getLabel());
                } else {
                    newNode = new Node(node.getLabel());
                }

                // Calculate new position
                if (pasteLocation != null) {
                    newNode.x = (int) (pasteLocation.getX() + (node.x - clipboardOffset.x));
                    newNode.y = (int) (pasteLocation.getY() + (node.y - clipboardOffset.y));
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
                nodeMapping.put(node, newNode);
            }

            // Second pass: recreate connections
            for (Node clipboardNode : clipboardNodes) {
                Node pastedNode = nodeMapping.get(clipboardNode);

                // Recreate connections for output point
                recreateConnections(clipboardNode.next, pastedNode.next);

                // Recreate connections for input point
                recreateConnections(clipboardNode.prev, pastedNode.prev);

                // Recreate special point connections
                if (clipboardNode instanceof SpecialNode clipboardSpecial &&
                        pastedNode instanceof SpecialNode pastedSpecial) {
                    for (int i = 0; i < clipboardSpecial.specialPoints.size(); i++) {
                        ConnectionPoint clipboardPoint = clipboardSpecial.specialPoints.get(i);
                        ConnectionPoint pastedPoint = pastedSpecial.specialPoints.get(i);
                        recreateConnections(clipboardPoint, pastedPoint);
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

    private void recreateConnections(ConnectionPoint clipboardPoint, ConnectionPoint pastedPoint) {
        for (ConnectionPoint connected : clipboardPoint.connected) {
            Node connectedNode = connected.parent;
            Node pastedConnectedNode = nodeMapping.get(connectedNode);
            if (pastedConnectedNode != null) {
                ConnectionPoint pastedConnectedPoint = findCorrespondingPoint(connected, pastedConnectedNode);
                if (pastedConnectedPoint != null) {
                    if (pastedPoint.isInput()) {
                        pastedConnectedPoint.connected.add(pastedPoint);
                    } else {
                        pastedPoint.connected.add(pastedConnectedPoint);
                    }
                }
            }
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
        private final Map<ConnectionPoint, List<ConnectionPoint>> storedConnections;

        public DeleteNodeAction(Node node) {
            this.node = node;
            this.storedConnections = new HashMap<>();

            // Store all connections for each connection point
            storeConnections(node.prev);
            storeConnections(node.next);
            if (node instanceof SpecialNode specialNode) {
                for (ConnectionPoint sp : specialNode.specialPoints) {
                    storeConnections(sp);
                }
            }
        }

        private void storeConnections(ConnectionPoint point) {
            List<ConnectionPoint> connections = new ArrayList<>(point.connected);
            if (!connections.isEmpty()) {
                storedConnections.put(point, connections);
            }
        }

        @Override
        public void undo() {
            nodes.add(node);

            // Restore all connections
            for (Map.Entry<ConnectionPoint, List<ConnectionPoint>> entry : storedConnections.entrySet()) {
                ConnectionPoint point = entry.getKey();
                List<ConnectionPoint> connections = entry.getValue();

                for (ConnectionPoint connectedPoint : connections) {
                    if (point.isInput()) {
                        connectedPoint.connected.add(point);
                    } else {
                        point.connected.add(connectedPoint);
                    }
                }
            }
        }

        @Override
        public void redo() {
            // Remove all connections before removing the node
            for (Map.Entry<ConnectionPoint, List<ConnectionPoint>> entry : storedConnections.entrySet()) {
                ConnectionPoint point = entry.getKey();
                List<ConnectionPoint> connections = entry.getValue();

                for (ConnectionPoint connectedPoint : connections) {
                    if (point.isInput()) {
                        connectedPoint.connected.remove(point);
                    } else {
                        point.connected.remove(connectedPoint);
                    }
                }
            }
            nodes.remove(node);
        }
    }

    private class CutAction implements UndoableAction {
        private final List<Node> cutNodes;
        private final Map<Node, Map<ConnectionPoint, List<ConnectionPoint>>> storedConnections;

        public CutAction(List<Node> nodes) {
            this.cutNodes = new ArrayList<>(nodes);
            this.storedConnections = new HashMap<>();

            // Store all connections for each node
            for (Node node : nodes) {
                Map<ConnectionPoint, List<ConnectionPoint>> nodeConnections = new HashMap<>();

                storeConnectionsForPoint(node.prev, nodeConnections);
                storeConnectionsForPoint(node.next, nodeConnections);

                if (node instanceof SpecialNode specialNode) {
                    for (ConnectionPoint sp : specialNode.specialPoints) {
                        storeConnectionsForPoint(sp, nodeConnections);
                    }
                }

                if (!nodeConnections.isEmpty()) {
                    storedConnections.put(node, nodeConnections);
                }
            }
        }

        private void storeConnectionsForPoint(ConnectionPoint point, Map<ConnectionPoint, List<ConnectionPoint>> nodeConnections) {
            List<ConnectionPoint> connections = new ArrayList<>(point.connected);
            if (!connections.isEmpty()) {
                nodeConnections.put(point, connections);
            }
        }

        @Override
        public void undo() {
            // Restore nodes
            nodes.addAll(cutNodes);

            // Restore all connections
            for (Node node : cutNodes) {
                Map<ConnectionPoint, List<ConnectionPoint>> nodeConnections = storedConnections.get(node);
                if (nodeConnections != null) {
                    for (Map.Entry<ConnectionPoint, List<ConnectionPoint>> entry : nodeConnections.entrySet()) {
                        ConnectionPoint point = entry.getKey();
                        List<ConnectionPoint> connections = entry.getValue();

                        for (ConnectionPoint connectedPoint : connections) {
                            if (point.isInput()) {
                                connectedPoint.connected.add(point);
                            } else {
                                point.connected.add(connectedPoint);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void redo() {
            // Remove all connections
            for (Node node : cutNodes) {
                Map<ConnectionPoint, List<ConnectionPoint>> nodeConnections = storedConnections.get(node);
                if (nodeConnections != null) {
                    for (Map.Entry<ConnectionPoint, List<ConnectionPoint>> entry : nodeConnections.entrySet()) {
                        ConnectionPoint point = entry.getKey();
                        List<ConnectionPoint> connections = entry.getValue();

                        for (ConnectionPoint connectedPoint : connections) {
                            if (point.isInput()) {
                                connectedPoint.connected.remove(point);
                            } else {
                                point.connected.remove(connectedPoint);
                            }
                        }
                    }
                }
            }

            // Remove nodes
            nodes.removeAll(cutNodes);
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
        private final ConnectionPoint output;
        private final ConnectionPoint input;

        public CreateConnectionAction(ConnectionPoint output, ConnectionPoint input) {
            this.output = output;
            this.input = input;
        }

        @Override
        public void undo() {
            output.connected.remove(input);
        }

        @Override
        public void redo() {
            output.connected.add(input);
        }
    }
}