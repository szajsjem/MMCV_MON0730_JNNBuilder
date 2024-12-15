package pl.szajsjem;

import pl.szajsjem.elements.ConnectionPoint;
import pl.szajsjem.elements.Node;
import pl.szajsjem.elements.SpecialNode;

import java.util.*;

public class ConnectionManager {
    private final ArrayList<Node> nodes;

    public ConnectionManager(ArrayList<Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * Attempts to connect two connection points
     * @return true if connection was successful, false if it would create an invalid connection
     */
    public boolean connectPoints(ConnectionPoint source, ConnectionPoint target) {
        // Don't connect points from the same node
        if (source.parent == target.parent) {
            return false;
        }

        // One must be input, one must be output
        if (source.isInput() == target.isInput()) {
            return false;
        }

        // Handle special points
        boolean sourceIsSpecial = isSpecialPoint(source);
        boolean targetIsSpecial = isSpecialPoint(target);

        // If either point is special, verify the connection path
        if (sourceIsSpecial || targetIsSpecial) {
            if (!validateSpecialPointConnection(source, target)) {
                return false;
            }
        }

        // Check for cycles
        if (wouldCreateCycle(source, target)) {
            return false;
        }

        // Make the connection (always connect from output to input)
        ConnectionPoint output = source.isInput() ? target : source;
        ConnectionPoint input = source.isInput() ? source : target;

        if (!output.connected.contains(input)) {
            output.connected.add(input);
        }
        return true;
    }

    /**
     * Disconnects two connection points
     */
    public void disconnectPoints(ConnectionPoint source, ConnectionPoint target) {
        source.connected.remove(target);
        target.connected.remove(source);
    }

    /**
     * Checks if the connection point is a special point
     */
    private boolean isSpecialPoint(ConnectionPoint point) {
        if (point.parent instanceof SpecialNode specialNode) {
            return specialNode.specialPoints.contains(point);
        }
        return false;
    }

    /**
     * Validates connections involving special points
     */
    private boolean validateSpecialPointConnection(ConnectionPoint source, ConnectionPoint target) {
        // Get the actual path between source and target nodes
        List<ConnectionPoint> path = findPath(source, target);
        if (path == null) {
            return false;
        }

        // For special points, ensure the path only contains prev/next points except at endpoints
        for (int i = 1; i < path.size() - 1; i++) {
            if (isSpecialPoint(path.get(i))) {
                return false;
            }
        }

        // Special points can only connect within their own node
        if (isSpecialPoint(source) && isSpecialPoint(target)) {
            return source.parent == target.parent;
        }

        return true;
    }

    /**
     * Finds a path between two connection points if one exists
     */
    private List<ConnectionPoint> findPath(ConnectionPoint start, ConnectionPoint end) {
        Queue<List<ConnectionPoint>> queue = new LinkedList<>();
        Set<ConnectionPoint> visited = new HashSet<>();
        queue.add(Collections.singletonList(start));

        while (!queue.isEmpty()) {
            List<ConnectionPoint> path = queue.poll();
            ConnectionPoint current = path.get(path.size() - 1);

            if (current == end) {
                return path;
            }

            if (visited.add(current)) {
                // Get next possible points
                List<ConnectionPoint> nextPoints = new ArrayList<>();

                // If current is an output, add its connected inputs
                if (!current.isInput()) {
                    nextPoints.addAll(current.connected);
                }

                // Add the node's next/prev points depending on current point type
                if (current == current.parent.prev) {
                    nextPoints.add(current.parent.next);
                } else if (current == current.parent.next) {
                    // For next points, add prev points of connected nodes
                    for (ConnectionPoint connected : current.connected) {
                        if (!visited.contains(connected.parent.prev)) {
                            nextPoints.add(connected.parent.prev);
                        }
                    }
                }

                // Process next points
                for (ConnectionPoint next : nextPoints) {
                    if (!visited.contains(next)) {
                        List<ConnectionPoint> newPath = new ArrayList<>(path);
                        newPath.add(next);
                        queue.add(newPath);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks if adding a connection would create a cycle
     */
    private boolean wouldCreateCycle(ConnectionPoint source, ConnectionPoint target) {
        // Always check from output to input
        ConnectionPoint output = source.isInput() ? target : source;
        ConnectionPoint input = source.isInput() ? source : target;

        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(input.parent);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current == output.parent) {
                return true;
            }

            if (visited.add(current)) {
                // Add nodes connected to the current node's output points
                if (!current.next.connected.isEmpty()) {
                    for (ConnectionPoint connected : current.next.connected) {
                        queue.add(connected.parent);
                    }
                }

                // Add nodes connected via special points
                if (current instanceof SpecialNode specialNode) {
                    for (ConnectionPoint sp : specialNode.specialPoints) {
                        if (!sp.isInput() && !sp.connected.isEmpty()) {
                            for (ConnectionPoint connected : sp.connected) {
                                queue.add(connected.parent);
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Gets all connection points that connect to the specified node
     */
    public List<ConnectionPoint> getConnectedPoints(Node node) {
        Set<ConnectionPoint> connectedPoints = new HashSet<>();

        // Add points connected to prev/next
        for (ConnectionPoint connected : node.prev.connected) {
            connectedPoints.add(connected);
        }
        for (ConnectionPoint connected : node.next.connected) {
            connectedPoints.add(connected);
        }

        // Add special point connections
        if (node instanceof SpecialNode specialNode) {
            for (ConnectionPoint sp : specialNode.specialPoints) {
                connectedPoints.addAll(sp.connected);
            }
        }

        return new ArrayList<>(connectedPoints);
    }

    /**
     * Removes all connections to/from a node
     */
    public void disconnectAll(Node node) {
        // Clear prev/next connections
        clearConnections(node.prev);
        clearConnections(node.next);

        // Clear special point connections
        if (node instanceof SpecialNode specialNode) {
            for (ConnectionPoint sp : specialNode.specialPoints) {
                clearConnections(sp);
            }
        }
    }

    private void clearConnections(ConnectionPoint point) {
        // Clear bidirectional connections
        for (ConnectionPoint connected : new ArrayList<>(point.connected)) {
            disconnectPoints(point, connected);
        }
    }

    /**
     * Validates the entire network structure
     */
    public List<String> validateNetwork() {
        List<String> errors = new ArrayList<>();

        // Find nodes with no connections
        for (Node node : nodes) {
            boolean hasConnections = !node.prev.connected.isEmpty() ||
                    !node.next.connected.isEmpty();

            // Check special point connections for special nodes
            if (node instanceof SpecialNode specialNode) {
                for (ConnectionPoint sp : specialNode.specialPoints) {
                    if (!sp.connected.isEmpty()) {
                        hasConnections = true;
                        break;
                    }
                }
            }

            if (!hasConnections) {
                errors.add("Node '" + node.getLabel() + "' is disconnected");
            }
        }

        // Validate input/output structure
        List<Node> inputNodes = findInputNodes();
        List<Node> outputNodes = findOutputNodes();

        if (inputNodes.isEmpty()) {
            errors.add("Network has no input nodes");
        }
        if (outputNodes.isEmpty()) {
            errors.add("Network has no output nodes");
        }

        // Validate paths from inputs to outputs
        for (Node input : inputNodes) {
            if (!canReachOutput(input)) {
                errors.add("Input node '" + input.getLabel() +
                        "' has no path to any output");
            }
        }

        return errors;
    }

    private List<Node> findInputNodes() {
        return nodes.stream()
                .filter(n -> n.prev.connected.isEmpty() &&
                        (!(n instanceof SpecialNode) ||
                                ((SpecialNode) n).specialPoints.stream()
                                        .filter(ConnectionPoint::isInput)
                                        .allMatch(sp -> sp.connected.isEmpty())))
                .toList();
    }

    private List<Node> findOutputNodes() {
        return nodes.stream()
                .filter(n -> n.next.connected.isEmpty() &&
                        (!(n instanceof SpecialNode) ||
                                ((SpecialNode) n).specialPoints.stream()
                                        .filter(p -> !p.isInput())
                                        .allMatch(sp -> sp.connected.isEmpty())))
                .toList();
    }

    private boolean canReachOutput(Node start) {
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();

            // Check if this is an output node
            if (current.next.connected.isEmpty() &&
                    (!(current instanceof SpecialNode) ||
                            ((SpecialNode) current).specialPoints.stream()
                                    .filter(p -> !p.isInput())
                                    .allMatch(sp -> sp.connected.isEmpty()))) {
                return true;
            }

            if (visited.add(current)) {
                // Add nodes connected to next point
                for (ConnectionPoint connected : current.next.connected) {
                    queue.add(connected.parent);
                }

                // Add nodes connected via special points
                if (current instanceof SpecialNode specialNode) {
                    for (ConnectionPoint sp : specialNode.specialPoints) {
                        if (!sp.isInput()) {
                            for (ConnectionPoint connected : sp.connected) {
                                queue.add(connected.parent);
                            }
                        }
                    }
                }
            }
        }

        return false;
    }
}