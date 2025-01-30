package pl.szajsjem;

import com.beednn.Layer;
import com.beednn.Net;
import pl.szajsjem.elements.Node;

import java.util.*;

public class NetworkStructureSerializer {
    private final List<Node> nodes;
    private final Map<Node, CompositeLayer> processedNodes = new HashMap<>();

    public NetworkStructureSerializer(List<Node> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    public Net buildNetwork() {
        // First validate the network structure
        ConnectionManager connectionManager = new ConnectionManager(new ArrayList<>(nodes));
        List<String> errors = connectionManager.validateNetwork();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid network structure: " + String.join(", ", errors));
        }

        // Create new Net instance
        Net network = new Net();

        // Get the list of layers in correct order using serializeNetwork
        List<CompositeLayer> layers = serializeNetwork(connectionManager);

        // Helper function to recursively add layers and get their layer references
        addLayersToNet(network, layers);

        return network;
    }

    // Returns layer pointer for referencing in parent layers
    private String addLayersToNet(Net network, List<CompositeLayer> layers) {
        StringBuilder layerPtrs = new StringBuilder();

        for (CompositeLayer compositeLayer : layers) {
            // If this is a parallel layer, handle specially
            if (compositeLayer.type.equals("parallel")) {
                // Process all child paths and collect their layer pointers
                List<String> childPtrs = new ArrayList<>();
                for (CompositeLayer child : compositeLayer.children) {
                    String ptr = addLayersToNet(network, Collections.singletonList(child));
                    if (ptr != null && !ptr.isEmpty()) {
                        childPtrs.add(ptr);
                    }
                }

                // Create parallel layer with reduction type and collected layer pointers
                String reductionType = compositeLayer.reduction != null ? compositeLayer.reduction : "sum";
                Layer parallelLayer = new Layer("LayerParallel", new float[0],
                        reductionType + "," + String.join(",", childPtrs));
                network.addLayer(parallelLayer);

                if (layerPtrs.length() > 0) layerPtrs.append(",");
                layerPtrs.append(parallelLayer.getNativePtr());
            } else {
                // Normal layer - create and add it
                Layer layer = createLayer(compositeLayer);
                if (layer != null) {
                    network.addLayer(layer);
                    if (layerPtrs.length() > 0) layerPtrs.append(",");
                    layerPtrs.append(layer.getNativePtr());
                }

                // Process children recursively
                if (!compositeLayer.children.isEmpty()) {
                    String childPtrs = addLayersToNet(network, compositeLayer.children);
                    if (childPtrs != null && !childPtrs.isEmpty()) {
                        if (layerPtrs.length() > 0) layerPtrs.append(",");
                        layerPtrs.append(childPtrs);
                    }
                }
            }
        }

        return layerPtrs.toString();
    }

    private Layer createLayer(CompositeLayer compositeLayer) {
        if (compositeLayer.sourceNode == null) {
            return null;  // Skip empty composite layers
        }

        // Get parameters from source node
        String type = compositeLayer.sourceNode.getType();
        float[] floatParams = compositeLayer.sourceNode.getFloatParams();
        String[] stringParams = compositeLayer.sourceNode.getStringParams();

        // Create layer using parameters
        try {
            return new Layer(type, floatParams, String.join(";", stringParams));
        } catch (Exception e) {
            throw new IllegalStateException("Error creating layer of type " + type + ": " + e.getMessage());
        }
    }

    public List<CompositeLayer> serializeNetwork(ConnectionManager cm) {
        // Find input nodes (nodes with no inputs except in special subgraphs)
        List<Node> inputNodes = nodes.stream()
                .filter(n -> n.prev.connected.isEmpty() && !cm.isInSpecialSubgraph(n))
                .toList();

        if (inputNodes.isEmpty()) {
            throw new IllegalStateException("Network must have at least one input node");
        }

        List<CompositeLayer> topLevelLayers = new ArrayList<>();

        // If there's only one input node, process it normally
        if (inputNodes.size() == 1) {
            topLevelLayers.add(processNode(inputNodes.get(0)));
        } else {
            // For multiple input nodes, find where they converge
            Map<Node, List<Node>> convergencePoints = findInputConvergencePoints(inputNodes);

            if (convergencePoints.isEmpty()) {
                // No convergence - use default parallel structure
                CompositeLayer parallel = new CompositeLayer("parallel", null, "concat");
                for (Node inputNode : inputNodes) {
                    parallel.children.add(processNode(inputNode));
                }
                topLevelLayers.add(parallel);
            } else {
                // Process paths to each convergence point
                for (Map.Entry<Node, List<Node>> entry : convergencePoints.entrySet()) {
                    Node convergenceNode = entry.getKey();
                    List<Node> pathNodes = entry.getValue();

                    CompositeLayer parallel;
                    if (isLayerParallel(convergenceNode)) {
                        // Use layer's native parallel processing
                        parallel = new CompositeLayer("parallel", convergenceNode,
                                getLayerReductionType(convergenceNode));
                    } else {
                        // Default to concat for non-parallel layers
                        parallel = new CompositeLayer("parallel", null, "concat");
                    }

                    for (Node pathNode : pathNodes) {
                        parallel.children.add(processPath(pathNode, convergenceNode));
                    }
                    topLevelLayers.add(parallel);
                }
            }
        }

        return topLevelLayers;
    }

    private CompositeLayer processNode(Node node) {
        // Check if we've already processed this node
        if (processedNodes.containsKey(node)) {
            return processedNodes.get(node);
        }

        // Create layer for current node
        CompositeLayer layer = new CompositeLayer(node.getType(), node);
        processedNodes.put(node, layer);

        // Process outputs
        List<Node> nextNodes = node.next.connected.stream()
                .map(cp -> cp.parent)
                .toList();

        if (nextNodes.size() == 1) {
            // Single output - series connection
            layer.children.add(processNode(nextNodes.get(0)));
        } else if (nextNodes.size() > 1) {
            // Multiple outputs - check for paths that converge
            Map<Node, List<Node>> convergencePoints = findConvergencePoints(node, nextNodes);

            if (!convergencePoints.isEmpty()) {
                // Create parallel paths up to convergence points
                for (Map.Entry<Node, List<Node>> entry : convergencePoints.entrySet()) {
                    Node convergencePoint = entry.getKey();
                    List<Node> pathNodes = entry.getValue();

                    if (pathNodes.size() == 1) {
                        // Single path to convergence point
                        layer.children.add(processPath(pathNodes.get(0), convergencePoint));
                    } else {
                        // Multiple paths to convergence point - create parallel structure
                        CompositeLayer parallel = new CompositeLayer("parallel", null, "sum");
                        for (Node pathNode : pathNodes) {
                            parallel.children.add(processPath(pathNode, convergencePoint));
                        }
                        layer.children.add(parallel);
                    }
                }
            }
        }

        return layer;
    }

    private CompositeLayer processPath(Node start, Node end) {
        // If start and end are the same, just return the node
        if (start == end) {
            return processNode(start);
        }

        // Create series of layers from start to end
        CompositeLayer current = processNode(start);
        Node currentNode = start;

        while (currentNode != end) {
            List<Node> nextNodes = currentNode.next.connected.stream()
                    .map(cp -> cp.parent)
                    .filter(n -> canReachNode(n, end, new HashSet<>()))
                    .toList();

            if (nextNodes.isEmpty()) {
                break;
            }

            currentNode = nextNodes.get(0);
            current.children.add(processNode(currentNode));
        }

        return current;
    }

    private Map<Node, List<Node>> findConvergencePoints(Node start, List<Node> nextNodes) {
        Map<Node, List<Node>> convergencePoints = new HashMap<>();

        // Find all nodes reachable from each next node
        for (Node nextNode : nextNodes) {
            Set<Node> reachableNodes = findReachableNodes(nextNode);

            // Check which nodes are reachable from other paths
            for (Node other : nextNodes) {
                if (other != nextNode) {
                    Set<Node> otherReachable = findReachableNodes(other);

                    // Find common reachable nodes (convergence points)
                    Set<Node> common = new HashSet<>(reachableNodes);
                    common.retainAll(otherReachable);

                    for (Node convergence : common) {
                        convergencePoints.computeIfAbsent(convergence, k -> new ArrayList<>())
                                .add(nextNode);
                    }
                }
            }
        }

        return convergencePoints;
    }

    private Set<Node> findReachableNodes(Node start) {
        Set<Node> reachable = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (reachable.add(current)) {
                current.next.connected.stream()
                        .map(cp -> cp.parent)
                        .forEach(queue::add);
            }
        }

        return reachable;
    }

    private boolean canReachNode(Node start, Node target, Set<Node> visited) {
        if (start == target) {
            return true;
        }

        if (!visited.add(start)) {
            return false;
        }

        return start.next.connected.stream()
                .map(cp -> cp.parent)
                .anyMatch(n -> canReachNode(n, target, visited));
    }

    private Map<Node, List<Node>> findInputConvergencePoints(List<Node> inputNodes) {
        Map<Node, List<Node>> convergencePoints = new HashMap<>();

        // Find all nodes reachable from each input node
        Map<Node, Set<Node>> reachableNodesMap = new HashMap<>();
        for (Node inputNode : inputNodes) {
            reachableNodesMap.put(inputNode, findReachableNodes(inputNode));
        }

        // Find nodes that are reachable from multiple inputs
        Set<Node> allReachableNodes = new HashSet<>();
        reachableNodesMap.values().forEach(allReachableNodes::addAll);

        for (Node reachable : allReachableNodes) {
            List<Node> converging = new ArrayList<>();
            for (Node inputNode : inputNodes) {
                if (reachableNodesMap.get(inputNode).contains(reachable)) {
                    converging.add(inputNode);
                }
            }

            if (converging.size() > 1) {
                // Check if this is the first convergence point in each path
                boolean isFirstConvergence = true;
                for (Node input : converging) {
                    if (hasEarlierConvergence(input, reachable, reachableNodesMap)) {
                        isFirstConvergence = false;
                        break;
                    }
                }

                if (isFirstConvergence) {
                    convergencePoints.put(reachable, converging);
                }
            }
        }

        return convergencePoints;
    }

    private boolean hasEarlierConvergence(Node start, Node target,
                                          Map<Node, Set<Node>> reachableNodesMap) {
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current == target) {
                return false;
            }

            if (visited.add(current)) {
                // Check if current node is a convergence point
                if (current != start && reachableNodesMap.entrySet().stream()
                        .filter(e -> e.getKey() != start)
                        .anyMatch(e -> e.getValue().contains(current))) {
                    return true;
                }

                // Add next nodes
                current.next.connected.stream()
                        .map(cp -> cp.parent)
                        .forEach(queue::add);
            }
        }

        return false;
    }

    private boolean isLayerParallel(Node node) {
        String type = node.getType().toLowerCase();
        return type.contains("parallel") ||
                type.contains("concat") ||
                type.contains("sum") ||
                type.contains("average");
    }

    private String getLayerReductionType(Node node) {
        String type = node.getType().toLowerCase();
        if (type.contains("sum")) return "sum";
        if (type.contains("average")) return "average";
        if (type.contains("concat")) return "concat";
        // Default reduction for parallel layers
        return "sum";
    }

    public static class CompositeLayer {
        public final String type;
        public final List<CompositeLayer> children = new ArrayList<>();
        public final Node sourceNode;
        public final String reduction;

        public CompositeLayer(String type, Node sourceNode) {
            this(type, sourceNode, null);
        }

        public CompositeLayer(String type, Node sourceNode, String reduction) {
            this.type = type;
            this.sourceNode = sourceNode;
            this.reduction = reduction;
        }
    }
}