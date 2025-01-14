package pl.szajsjem;

import pl.szajsjem.elements.ConnectionPoint;
import pl.szajsjem.elements.Node;
import pl.szajsjem.elements.SpecialNode;

import java.util.*;

public class NetworkLayout {
    private static final int LAYER_HORIZONTAL_SPACING = 200;
    private static final int NODE_VERTICAL_SPACING = 100;
    private static final int STARTING_X = 100;
    private static final int STARTING_Y = 100;

    public static void autoLayout(List<Node> nodes) {
        if (nodes.isEmpty()) return;

        // Step 1: Find input and output nodes
        List<Node> inputNodes = findInputNodes(nodes);
        List<Node> outputNodes = findOutputNodes(nodes);

        // Step 2: Assign layers to nodes
        Map<Node, Integer> layerAssignments = assignLayers(nodes, inputNodes);
        int maxLayer = layerAssignments.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        // Step 3: Group nodes by layer
        Map<Integer, List<Node>> layerGroups = new HashMap<>();
        for (Map.Entry<Node, Integer> entry : layerAssignments.entrySet()) {
            layerGroups.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }

        // Step 4: Order nodes within each layer to minimize crossings
        for (int layer = 0; layer <= maxLayer; layer++) {
            List<Node> layerNodes = layerGroups.get(layer);
            if (layerNodes != null) {
                orderNodesInLayer(layerNodes, layerAssignments);
            }
        }

        // Step 5: Position nodes
        for (int layer = 0; layer <= maxLayer; layer++) {
            List<Node> layerNodes = layerGroups.get(layer);
            if (layerNodes != null) {
                int x = STARTING_X + (layer * LAYER_HORIZONTAL_SPACING);
                int currentY = STARTING_Y;

                for (Node node : layerNodes) {
                    // Snap to grid (20 pixels)
                    node.x = Math.round(x / 20f) * 20;
                    node.y = Math.round(currentY / 20f) * 20;
                    currentY += NODE_VERTICAL_SPACING;
                }
            }
        }
    }

    private static List<Node> findInputNodes(List<Node> nodes) {
        return nodes.stream()
                .filter(node -> {
                    boolean hasInputs = !node.prev.connected.isEmpty();
                    if (node instanceof SpecialNode specialNode) {
                        hasInputs = hasInputs || specialNode.specialPoints.stream()
                                .filter(ConnectionPoint::isInput)
                                .anyMatch(p -> !p.connected.isEmpty());
                    }
                    return !hasInputs;
                })
                .toList();
    }

    private static List<Node> findOutputNodes(List<Node> nodes) {
        return nodes.stream()
                .filter(node -> {
                    boolean hasOutputs = !node.next.connected.isEmpty();
                    if (node instanceof SpecialNode specialNode) {
                        hasOutputs = hasOutputs || specialNode.specialPoints.stream()
                                .filter(p -> !p.isInput())
                                .anyMatch(p -> !p.connected.isEmpty());
                    }
                    return !hasOutputs;
                })
                .toList();
    }

    private static Map<Node, Integer> assignLayers(List<Node> nodes, List<Node> inputNodes) {
        Map<Node, Integer> layerAssignments = new HashMap<>();
        Queue<Node> queue = new LinkedList<>(inputNodes);
        Set<Node> visited = new HashSet<>();

        // Assign layer 0 to input nodes
        for (Node inputNode : inputNodes) {
            layerAssignments.put(inputNode, 0);
            visited.add(inputNode);
        }

        // Breadth-first traversal to assign layers
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            int currentLayer = layerAssignments.get(current);

            // Get all connected nodes through regular and special connections
            Set<Node> connectedNodes = getConnectedNodes(current);

            for (Node connected : connectedNodes) {
                if (!visited.contains(connected)) {
                    visited.add(connected);
                    queue.add(connected);
                }

                // Assign layer to connected node
                int existingLayer = layerAssignments.getOrDefault(connected, -1);
                int newLayer = currentLayer + 1;
                if (existingLayer < newLayer) {
                    layerAssignments.put(connected, newLayer);
                }
            }
        }

        return layerAssignments;
    }

    private static Set<Node> getConnectedNodes(Node node) {
        Set<Node> connected = new HashSet<>();

        // Add nodes connected through next point
        for (ConnectionPoint connPoint : node.next.connected) {
            connected.add(connPoint.parent);
        }

        // Add nodes connected through special points if it's a special node
        if (node instanceof SpecialNode specialNode) {
            for (ConnectionPoint sp : specialNode.specialPoints) {
                if (!sp.isInput()) {
                    connected.addAll(sp.connected.stream()
                            .map(cp -> cp.parent)
                            .toList());
                }
            }
        }

        return connected;
    }

    private static void orderNodesInLayer(List<Node> layerNodes, Map<Node, Integer> layerAssignments) {
        // Simple ordering based on average y-position of connected nodes in previous layer
        layerNodes.sort((n1, n2) -> {
            double y1 = getAverageConnectedY(n1, layerAssignments);
            double y2 = getAverageConnectedY(n2, layerAssignments);
            return Double.compare(y1, y2);
        });
    }

    private static double getAverageConnectedY(Node node, Map<Node, Integer> layerAssignments) {
        Set<Node> connectedNodes = new HashSet<>();

        // Get nodes connected through prev point
        connectedNodes.addAll(node.prev.connected.stream()
                .map(cp -> cp.parent)
                .toList());

        // Get nodes connected through special points if it's a special node
        if (node instanceof SpecialNode specialNode) {
            for (ConnectionPoint sp : specialNode.specialPoints) {
                if (sp.isInput()) {
                    connectedNodes.addAll(sp.connected.stream()
                            .map(cp -> cp.parent)
                            .toList());
                }
            }
        }

        // Calculate average Y position of connected nodes
        if (!connectedNodes.isEmpty()) {
            return connectedNodes.stream()
                    .mapToInt(n -> n.y)
                    .average()
                    .orElse(node.y);
        }

        return node.y;
    }
}
