package pl.szajsjem;

import pl.szajsjem.elements.Node;
import pl.szajsjem.elements.RNNNode;

import java.util.*;

public class ConnectionManager {
    private final ArrayList<Node> nodes;

    public ConnectionManager(ArrayList<Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * Attempts to connect two nodes and checks for cycles
     *
     * @return true if connection was successful, false if it would create a cycle
     */
    public boolean connectNodes(Node source, Node target) {
        // Don't connect if it would create a cycle
        if (wouldCreateCycle(source, target)) {
            return false;
        }

        if (!source.next.contains(target)) {
            source.next.add(target);
            target.prev.add(source);
        }
        return true;
    }

    /**
     * Connects RNN feedback
     */
    public void connectRNNFeedback(RNNNode rnnNode, Node feedbackNode) {
        if (!rnnNode.feedbackNodes.contains(feedbackNode)) {
            rnnNode.feedbackNodes.add(feedbackNode);
        }
    }

    /**
     * Disconnects two nodes
     */
    public void disconnectNodes(Node source, Node target) {
        source.next.remove(target);
        target.prev.remove(source);
    }

    /**
     * Removes RNN feedback connection
     */
    public void disconnectRNNFeedback(RNNNode rnnNode, Node feedbackNode) {
        rnnNode.feedbackNodes.remove(feedbackNode);
    }

    /**
     * Checks if adding a connection would create a cycle
     */
    private boolean wouldCreateCycle(Node source, Node target) {
        // If target is already connected to source (directly or indirectly),
        // adding this connection would create a cycle
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(target);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current == source) {
                return true;
            }

            if (visited.add(current)) {
                queue.addAll(current.next);
            }
        }

        return false;
    }

    /**
     * Gets all nodes that connect to the specified node
     */
    public List<Node> getConnectedNodes(Node node) {
        Set<Node> connectedNodes = new HashSet<>();
        connectedNodes.addAll(node.prev);
        connectedNodes.addAll(node.next);

        if (node instanceof RNNNode rnn) {
            connectedNodes.addAll(rnn.feedbackNodes);
        }

        // Check if this node is a feedback node for any RNN nodes
        for (Node n : nodes) {
            if (n instanceof RNNNode rnn && rnn.feedbackNodes.contains(node)) {
                connectedNodes.add(rnn);
            }
        }

        return new ArrayList<>(connectedNodes);
    }

    /**
     * Removes all connections to/from a node
     */
    public void disconnectAll(Node node) {
        // Remove normal connections
        for (Node prev : new ArrayList<>(node.prev)) {
            disconnectNodes(prev, node);
        }
        for (Node next : new ArrayList<>(node.next)) {
            disconnectNodes(node, next);
        }

        // Remove RNN feedback connections
        if (node instanceof RNNNode rnn) {
            for (Node feedback : new ArrayList<>(rnn.feedbackNodes)) {
                disconnectRNNFeedback(rnn, feedback);
            }
        }

        // Remove this node from any RNN feedback connections
        for (Node n : nodes) {
            if (n instanceof RNNNode rnn) {
                disconnectRNNFeedback(rnn, node);
            }
        }
    }

    /**
     * Validates the entire network structure
     *
     * @return List of error messages, empty if valid
     */
    public List<String> validateNetwork() {
        List<String> errors = new ArrayList<>();

        // Check for disconnected nodes
        for (Node node : nodes) {
            if (node.prev.isEmpty() && node.next.isEmpty()) {
                errors.add("Node '" + node.getLabel() + "' is disconnected");
            }
        }

        // Find input and output nodes
        List<Node> inputNodes = nodes.stream()
                .filter(n -> n.prev.isEmpty())
                .toList();
        List<Node> outputNodes = nodes.stream()
                .filter(n -> n.next.isEmpty())
                .toList();

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

    private boolean canReachOutput(Node start) {
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.next.isEmpty()) {
                return true; // Found an output node
            }

            if (visited.add(current)) {
                queue.addAll(current.next);
                if (current instanceof RNNNode rnn) {
                    queue.addAll(rnn.feedbackNodes);
                }
            }
        }

        return false;
    }
}