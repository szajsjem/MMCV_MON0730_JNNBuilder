package pl.szajsjem;

import com.beednn.Layer;
import com.beednn.NetTrain;
import org.json.JSONObject;
import pl.szajsjem.elements.Node;

import java.io.IOException;
import java.util.*;

public class NetworkLoader {
    public static ValidationResult loadAndValidate(String filePath) throws IOException {
        // First load the network data
        NetworkSerializer.NetworkData data = NetworkSerializer.loadFromFile(filePath);
        ValidationResult result = new ValidationResult(data);

        // Get available components from JNI
        Set<String> availableLayerTypes = new HashSet<>(Arrays.asList(Layer.getAvailableLayers()));
        Set<String> availableActivations = new HashSet<>(Arrays.asList(Layer.getAvailableActivations()));
        Set<String> availableInitializers = new HashSet<>(Arrays.asList(Layer.getAvailableInitializers()));
        Set<String> availableReductions = new HashSet<>(Arrays.asList(Layer.getAvailableReductions()));
        Set<String> availableLosses = new HashSet<>(Arrays.asList(NetTrain.getAvailableLosses()));
        Set<String> availableOptimizers = new HashSet<>(Arrays.asList(NetTrain.getAvailableOptimizers()));
        Set<String> availableRegularizers = new HashSet<>(Arrays.asList(NetTrain.getAvailableRegularizers()));

        // Validate nodes
        for (Node node : data.nodes) {
            validateNode(node, availableLayerTypes, availableActivations,
                    availableInitializers, availableReductions, result);
        }

        // Validate training settings
        validateTrainingSettings(data.netTrain, availableLosses,
                availableOptimizers, availableRegularizers, result);

        // Validate network structure
        validateNetworkStructure(data.nodes, result);

        return result;
    }

    private static void validateNode(Node node, Set<String> availableLayerTypes,
                                     Set<String> availableActivations,
                                     Set<String> availableInitializers,
                                     Set<String> availableReductions,
                                     ValidationResult result) {
        // Check if layer type exists
        if (!availableLayerTypes.contains(node.getType())) {
            result.errors.add("Layer type '" + node.getType() + "' is not available in the current version");
            return;
        }

        // Get layer usage info to check parameters
        String usage = Layer.getLayerUsage(node.getType());
        String[] usageLines = usage.split("\n");

        // Validate string parameters
        String[] stringParams = node.getStringParams();
        if (usageLines.length > 1) {
            String[] stringDescs = usageLines[1].split(";");
            for (int i = 0; i < Math.min(stringParams.length, stringDescs.length); i++) {
                String desc = stringDescs[i].toLowerCase();
                String param = stringParams[i];

                if (desc.contains("activation") && !availableActivations.contains(param)) {
                    result.errors.add("Activation function '" + param + "' is not available for layer '" + node.getType() + "'");
                } else if (desc.contains("initializer") && !availableInitializers.contains(param)) {
                    result.errors.add("Initializer '" + param + "' is not available for layer '" + node.getType() + "'");
                } else if (desc.contains("reduction") && !availableReductions.contains(param)) {
                    result.errors.add("Reduction method '" + param + "' is not available for layer '" + node.getType() + "'");
                }
            }
        }

        // Validate numeric parameters if specified in usage
        float[] floatParams = node.getFloatParams();
        if (usageLines.length > 2) {
            String[] floatDescs = usageLines[2].split(";");
            if (floatParams.length != floatDescs.length) {
                result.warnings.add("Layer '" + node.getType() + "' has " + floatParams.length +
                        " numeric parameters but expects " + floatDescs.length);
            }
        }
    }

    private static void validateTrainingSettings(NetTrain netTrain,
                                                 Set<String> availableLosses,
                                                 Set<String> availableOptimizers,
                                                 Set<String> availableRegularizers,
                                                 ValidationResult result) {
        try {
            // Extract settings from the NetTrain save string
            String settings = netTrain.save();
            JSONObject settingsJson = new JSONObject(settings);

            // Check loss function
            if (settingsJson.has("loss")) {
                String loss = settingsJson.getString("loss");
                if (!availableLosses.contains(loss)) {
                    result.errors.add("Loss function '" + loss + "' is not available in the current version");
                }
            }

            // Check optimizer
            if (settingsJson.has("optimizer")) {
                String optimizer = settingsJson.getString("optimizer");
                if (!availableOptimizers.contains(optimizer)) {
                    result.errors.add("Optimizer '" + optimizer + "' is not available in the current version");
                }
            }

            // Check regularizer
            if (settingsJson.has("regularizer")) {
                String regularizer = settingsJson.getString("regularizer");
                if (!availableRegularizers.contains(regularizer)) {
                    result.errors.add("Regularizer '" + regularizer + "' is not available in the current version");
                }
            }
        } catch (Exception e) {
            result.warnings.add("Could not fully validate training settings: " + e.getMessage());
        }
    }

    private static void validateNetworkStructure(List<Node> nodes, ValidationResult result) {

        // Structural checks could be added here

    }

    public static class ValidationResult {
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final NetworkSerializer.NetworkData networkData;

        public ValidationResult(NetworkSerializer.NetworkData networkData) {
            this.networkData = networkData;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}