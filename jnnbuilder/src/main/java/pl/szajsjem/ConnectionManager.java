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
     * Validates connections involving special points
     */
    private boolean validateSpecialPointConnection(ConnectionPoint source, ConnectionPoint target) {
        boolean sourceIsSpecial = isSpecialPoint(source);
        boolean targetIsSpecial = isSpecialPoint(target);

        // If points are in the same node
        if (source.parent == target.parent) {
            // Never allow connections between points in the same node if either is special
            if (sourceIsSpecial || targetIsSpecial) {
                return false;
            }
        }

        // For connections between different nodes:
        // Allow connections between special points and normal points
        if ((sourceIsSpecial && !targetIsSpecial) || (!sourceIsSpecial && targetIsSpecial)) {
            return true;
        }

        // If neither point is special, allow the connection
        return !sourceIsSpecial && !targetIsSpecial;

        // If both points are special (even in different nodes), don't allow direct connection
    }

    /**
     * Checks if there's any path between special and normal points
     * through any sequence of connections
     */
    private boolean hasPathBetweenSpecialAndNormal(ConnectionPoint source, ConnectionPoint target) {
        boolean sourceIsSpecial = isSpecialPoint(source);
        boolean targetIsSpecial = isSpecialPoint(target);

        // If source and target are of different types (one special, one normal),
        // or trying to connect within same node, reject immediately
        if ((sourceIsSpecial != targetIsSpecial) || source.parent == target.parent) {
            return true;
        }

        // Find all reachable points from source
        Set<Node> visited = new HashSet<>();
        Set<ConnectionPoint> reachablePoints = new HashSet<>();
        Queue<ConnectionPoint> queue = new LinkedList<>();
        queue.add(source);

        while (!queue.isEmpty()) {
            ConnectionPoint current = queue.poll();
            if (reachablePoints.add(current)) {
                // Add connected points
                for (ConnectionPoint connected : current.connected) {
                    queue.add(connected);
                }

                // If current point is an output, add all points it can reach
                if (!current.isInput() && visited.add(current.parent)) {
                    // For normal points
                    if (!isSpecialPoint(current)) {
                        if (current == current.parent.next) {
                            for (ConnectionPoint conn : current.connected) {
                                if (conn.parent.prev != null) {
                                    queue.add(conn.parent.prev);
                                }
                            }
                        }
                    }
                    // For special points
                    else if (current.parent instanceof SpecialNode specialNode) {
                        for (ConnectionPoint sp : specialNode.specialPoints) {
                            if (!sp.isInput()) {
                                queue.addAll(sp.connected);
                            }
                        }
                    }
                }
            }
        }

        // Check if any reachable point is of different type (special vs normal)
        for (ConnectionPoint point : reachablePoints) {
            if (isSpecialPoint(point) != sourceIsSpecial) {
                return true;
            }
        }

        // Also check if the target can reach any points of different type
        visited.clear();
        reachablePoints.clear();
        queue.add(target);

        while (!queue.isEmpty()) {
            ConnectionPoint current = queue.poll();
            if (reachablePoints.add(current)) {
                for (ConnectionPoint connected : current.connected) {
                    queue.add(connected);
                }

                if (!current.isInput() && visited.add(current.parent)) {
                    if (!isSpecialPoint(current)) {
                        if (current == current.parent.next) {
                            for (ConnectionPoint conn : current.connected) {
                                if (conn.parent.prev != null) {
                                    queue.add(conn.parent.prev);
                                }
                            }
                        }
                    } else if (current.parent instanceof SpecialNode specialNode) {
                        for (ConnectionPoint sp : specialNode.specialPoints) {
                            if (!sp.isInput()) {
                                queue.addAll(sp.connected);
                            }
                        }
                    }
                }
            }
        }

        for (ConnectionPoint point : reachablePoints) {
            if (isSpecialPoint(point) != targetIsSpecial) {
                return true;
            }
        }

        return false;
    }

    boolean isInSpecialSubgraph(Node node) {
        return findSubgraphParent(node.prev) != null;
    }

    private List<Node> findRealInputNodes() {
        return nodes.stream()
                .filter(n -> n.prev.connected.isEmpty() && !isInSpecialSubgraph(n))
                .toList();
    }

    private List<Node> findRealOutputNodes() {
        return nodes.stream()
                .filter(n -> n.next.connected.isEmpty() && !isInSpecialSubgraph(n))
                .toList();
    }

    public List<String> validateNetwork() {
        List<String> errors = new ArrayList<>();

        // Find actual input and output nodes
        List<Node> inputNodes = findRealInputNodes();
        List<Node> outputNodes = findRealOutputNodes();

        // Validate input/output requirements
        if (inputNodes.isEmpty()) {
            errors.add("Network must have at least one input node outside of special subgraphs");
        }
        if (outputNodes.isEmpty()) {
            errors.add("Network must have at least one output node outside of special subgraphs");
        }

        // Check for disconnected nodes
        for (Node node : nodes) {
            boolean hasConnections = !node.prev.connected.isEmpty() ||
                    !node.next.connected.isEmpty();

            if (node instanceof SpecialNode specialNode) {
                for (ConnectionPoint sp : specialNode.specialPoints) {
                    if (!sp.connected.isEmpty()) {
                        hasConnections = true;
                        break;
                    }
                }
            }

            if (!hasConnections && !inputNodes.contains(node) && !outputNodes.contains(node)) {
                errors.add("Node '" + node.getLabel() + "' is disconnected");
            }
        }

        // Validate paths from inputs to outputs
        for (Node input : inputNodes) {
            if (!canReachAnyOutput(input, outputNodes)) {
                errors.add("Input node '" + input.getLabel() +
                        "' has no path to any output");
            }
        }

        // Check for unresolved parallel paths
        errors.addAll(validateParallelPaths());

        // Check special subgraph connectivity
        errors.addAll(validateSpecialSubgraphs());

        return errors;
    }

    private boolean canReachAnyOutput(Node start, List<Node> outputNodes) {
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();

            if (outputNodes.contains(current)) {
                return true;
            }

            if (visited.add(current)) {
                // Add nodes connected through next point
                for (ConnectionPoint connected : current.next.connected) {
                    queue.add(connected.parent);
                }
            }
        }

        return false;
    }

    private List<String> validateSpecialSubgraphs() {
        List<String> errors = new ArrayList<>();
        Map<SpecialNode, Set<Node>> subgraphs = findAllSubgraphs();

        for (Map.Entry<SpecialNode, Set<Node>> entry : subgraphs.entrySet()) {
            SpecialNode specialNode = entry.getKey();
            Set<Node> subgraphNodes = entry.getValue();

            // Check each node in the subgraph
            for (Node node : subgraphNodes) {
                if (node == specialNode) continue; // Skip the special node itself

                // Check inputs
                if (node.prev.connected.isEmpty()) {
                    // Node has no inputs - check if it's connected to special node's output
                    boolean hasSpecialInput = specialNode.specialPoints.stream()
                            .filter(sp -> !sp.isInput())
                            .anyMatch(sp -> sp.connected.stream()
                                    .anyMatch(cp -> cp.parent == node));

                    if (!hasSpecialInput) {
                        errors.add(String.format(
                                "Node '%s' in subgraph of special node '%s' has no inputs",
                                node.getLabel(), specialNode.getLabel()
                        ));
                    }
                }

                // Check outputs
                if (node.next.connected.isEmpty()) {
                    // Node has no outputs - check if it's connected to special node's input
                    boolean hasSpecialOutput = specialNode.specialPoints.stream()
                            .filter(ConnectionPoint::isInput)
                            .anyMatch(sp -> node.next.connected.contains(sp));

                    if (!hasSpecialOutput) {
                        errors.add(String.format(
                                "Node '%s' in subgraph of special node '%s' has no outputs",
                                node.getLabel(), specialNode.getLabel()
                        ));
                    }
                }

                // Ensure node is not being used as network input/output
                if (findRealInputNodes().contains(node)) {
                    errors.add(String.format(
                            "Node '%s' in subgraph of special node '%s' cannot be used as network input",
                            node.getLabel(), specialNode.getLabel()
                    ));
                }
                if (findRealOutputNodes().contains(node)) {
                    errors.add(String.format(
                            "Node '%s' in subgraph of special node '%s' cannot be used as network output",
                            node.getLabel(), specialNode.getLabel()
                    ));
                }
            }
        }

        return errors;
    }

    private Map<SpecialNode, Set<Node>> findAllSubgraphs() {
        Map<SpecialNode, Set<Node>> subgraphs = new HashMap<>();

        // Find all special nodes
        for (Node node : nodes) {
            if (node instanceof SpecialNode specialNode) {
                Set<Node> subgraphNodes = findSubgraphNodes(specialNode);
                if (!subgraphNodes.isEmpty()) {
                    subgraphs.put(specialNode, subgraphNodes);
                }
            }
        }

        return subgraphs;
    }

    private Set<Node> findSubgraphNodes(SpecialNode specialNode) {
        Set<Node> subgraphNodes = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();

        // Start with nodes directly connected to special points
        for (ConnectionPoint sp : specialNode.specialPoints) {
            sp.connected.stream()
                    .map(cp -> cp.parent)
                    .forEach(queue::add);
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (subgraphNodes.add(current)) {
                // Add nodes connected through regular connections
                current.next.connected.stream()
                        .map(cp -> cp.parent)
                        .filter(n -> !(n instanceof SpecialNode) || n == specialNode)
                        .forEach(queue::add);

                current.prev.connected.stream()
                        .map(cp -> cp.parent)
                        .filter(n -> !(n instanceof SpecialNode) || n == specialNode)
                        .forEach(queue::add);
            }
        }

        return subgraphNodes;
    }

    private List<String> validateParallelPaths() {
        List<String> errors = new ArrayList<>();

        // Find all cases where a node has multiple inputs or outputs
        for (Node node : nodes) {
            if (node.prev.connected.size() > 1) {
                // Check if this node is explicitly marked as parallel
                if (!node.getType().toLowerCase().contains("parallel")) {
                    errors.add("Node '" + node.getLabel() +
                            "' receives multiple inputs but is not a parallel layer. " +
                            "Consider using a parallel layer with appropriate reduction.");
                }
            }
        }

        return errors;
    }


    /**
     * Finds the special node parent (if any) of the subgraph containing this connection point's node
     * and all nodes reachable through normal connections
     */
    private Node findSubgraphParent(ConnectionPoint start) {
        // If starting point is special, its parent is the subgraph parent
        if (isSpecialPoint(start)) {
            return start.parent;
        }

        Set<Node> visitedNodes = new HashSet<>();
        Queue<Node> nodeQueue = new LinkedList<>();
        nodeQueue.add(start.parent);

        while (!nodeQueue.isEmpty()) {
            Node currentNode = nodeQueue.poll();
            if (!visitedNodes.add(currentNode)) {
                continue;
            }

            // Check prev connections
            for (ConnectionPoint connected : currentNode.prev.connected) {
                Node connectedNode = connected.parent;
                if (!visitedNodes.contains(connectedNode)) {
                    if (isSpecialPoint(connected)) {
                        return connected.parent;
                    }
                    nodeQueue.add(connectedNode);
                }
            }

            // Check next connections
            for (ConnectionPoint connected : currentNode.next.connected) {
                Node connectedNode = connected.parent;
                if (!visitedNodes.contains(connectedNode)) {
                    if (isSpecialPoint(connected)) {
                        return connected.parent;
                    }
                    nodeQueue.add(connectedNode);
                }
            }

            // Add nodes that could potentially be connected through normal points
            // This is the key change - consider nodes that could form a valid normal connection
            for (Node otherNode : nodes) {
                if (!visitedNodes.contains(otherNode) && otherNode != currentNode) {
                    // If these nodes could be connected through normal points
                    if ((currentNode.next.connected.isEmpty() && otherNode.prev.connected.isEmpty()) ||
                            (currentNode.prev.connected.isEmpty() && otherNode.next.connected.isEmpty())) {
                        nodeQueue.add(otherNode);
                    }
                }
            }
        }

        return null; // No special node parent found
    }

    private boolean nodeInSameSubgraph(Node node, Node otherNode) {
        // Start with checking if nodes are part of regular flow
        if (isInRegularFlow(node) && isInRegularFlow(otherNode)) {
            return true;
        }

        // Check if they're in the same special node's subgraph
        return findCommonSpecialParent(node, otherNode) != null;
    }

    private boolean isInRegularFlow(Node node) {
        // Check if node has any regular (non-special) connections
        for (Node otherNode : nodes) {
            if (otherNode.next.connected.contains(node.prev) ||
                    node.next.connected.contains(otherNode.prev)) {
                return true;
            }
        }
        return false;
    }

    private SpecialNode findCommonSpecialParent(Node node1, Node node2) {
        Set<SpecialNode> node1Parents = findAllSpecialParents(node1);
        Set<SpecialNode> node2Parents = findAllSpecialParents(node2);

        // Find intersection of parent sets
        for (SpecialNode parent : node1Parents) {
            if (node2Parents.contains(parent)) {
                return parent;
            }
        }
        return null;
    }

    private Set<SpecialNode> findAllSpecialParents(Node node) {
        Set<SpecialNode> parents = new HashSet<>();
        findAllSpecialParentsRecursive(node, parents);
        return parents;
    }

    private void findAllSpecialParentsRecursive(Node node, Set<SpecialNode> parents) {
        // Check all possible connections to special nodes
        for (Node otherNode : nodes) {
            if (otherNode instanceof SpecialNode specialNode) {
                // Check if this node is connected to any special points
                boolean isConnected = false;
                for (ConnectionPoint sp : specialNode.specialPoints) {
                    if (!sp.isInput() && sp.connected.contains(node.prev)) {
                        isConnected = true;
                        break;
                    }
                    if (sp.isInput() && node.next.connected.contains(sp)) {
                        isConnected = true;
                        break;
                    }
                }

                if (isConnected) {
                    parents.add(specialNode);
                    // Recursively check if this special node is part of another subgraph
                    findAllSpecialParentsRecursive(specialNode, parents);
                }
            }
        }
    }

    /**
     * Validates whether two points can be connected based on subgraph rules
     */
    private boolean canConnectSubgraphs(ConnectionPoint source, ConnectionPoint target) {
        // Don't allow connections within same node
        if (source.parent == target.parent) {
            return false;
        }

        // Find subgraph parents for both points
        Node sourceParent = findSubgraphParent(source);
        Node targetParent = findSubgraphParent(target);

        // If both have same parent or both have no parent, allow connection
        if (sourceParent == targetParent) {
            return true;
        }

        // If they have different parents and neither is null, prevent connection
        if (sourceParent != null && targetParent != null) {
            return false;
        }

        // If one is null, check that the top-level graph doesn't contain the other's parent
        if (sourceParent == null && targetParent != null) {
            return !nodeInSameSubgraph(source.parent, target.parent);
        }

        if (targetParent == null && sourceParent != null) {
            return !nodeInSameSubgraph(target.parent, source.parent);
        }

        return true;
    }

    /**
     * Attempts to connect two connection points
     */
    public boolean connectPoints(ConnectionPoint source, ConnectionPoint target) {
        // Don't connect a point to itself
        if (source == target) {
            return false;
        }

        // One must be input, one must be output
        if (source.isInput() == target.isInput()) {
            return false;
        }

        // Check subgraph rules
        if (!canConnectSubgraphs(source, target)) {
            return false;
        }

        // Check for cycles
        if (wouldCreateCycle(source, target)) {
            return false;
        }

        // Make the connection (store in both points)
        ConnectionPoint output = source.isInput() ? target : source;
        ConnectionPoint input = source.isInput() ? source : target;

        if (!output.connected.contains(input)) {
            output.connected.add(input);
            input.connected.add(output);
        }
        return true;
    }

    private boolean isSpecialPoint(ConnectionPoint point) {
        if (point.parent instanceof SpecialNode specialNode) {
            return specialNode.specialPoints.contains(point);
        }
        return false;
    }

    private boolean wouldCreateCycle(ConnectionPoint source, ConnectionPoint target) {
        // Always check from output to input
        ConnectionPoint output = source.isInput() ? target : source;
        ConnectionPoint input = source.isInput() ? source : target;

        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(input.parent);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current == output.parent && !isSpecialPoint(output)) {
                return true;
            }

            if (visited.add(current)) {
                // Only follow regular next connections
                for (ConnectionPoint connected : current.next.connected) {
                    queue.add(connected.parent);
                }
            }
        }

        return false;
    }


    /**
     * Helper method for cycle detection that checks if we can reach a target node from a start node
     */
    private boolean canReachNode(Node start, Node target, Set<Node> visited) {
        if (start == target) {
            return true;
        }

        if (!visited.add(start)) {
            return false;
        }

        // Check normal connections through next point
        for (ConnectionPoint connected : start.next.connected) {
            if (canReachNode(connected.parent, target, visited)) {
                return true;
            }
        }

        // Check special point connections
        if (start instanceof SpecialNode specialNode) {
            for (ConnectionPoint sp : specialNode.specialPoints) {
                if (!sp.isInput()) {
                    for (ConnectionPoint connected : sp.connected) {
                        if (canReachNode(connected.parent, target, visited)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }


    public void disconnectPoints(ConnectionPoint source, ConnectionPoint target) {
        // Remove connection from both points
        source.connected.remove(target);
        target.connected.remove(source);
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
}