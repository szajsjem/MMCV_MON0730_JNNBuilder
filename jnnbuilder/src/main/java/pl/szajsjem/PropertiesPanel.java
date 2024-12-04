package pl.szajsjem;

import com.beednn.Layer;
import pl.szajsjem.elements.Node;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PropertiesPanel extends JPanel {
    private final NodeManager nodeManager;
    private final List<JTextField> stringFields = new ArrayList<>();
    private final List<JTextField> floatFields = new ArrayList<>();
    private final JPanel contentPanel;
    private final Timer updateTimer;

    public PropertiesPanel(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Properties"));

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        add(new JScrollPane(contentPanel), BorderLayout.CENTER);

        // Create timer for delayed updates to avoid too frequent Layer creation
        updateTimer = new Timer(500, e -> applyChanges());
        updateTimer.setRepeats(false);

        // Listen for selection changes
        nodeManager.addSelectionListener(this::updateProperties);
    }

    private void updateProperties() {
        Set<Node> selectedNodes = nodeManager.getSelectedNodes();
        contentPanel.removeAll();
        stringFields.clear();
        floatFields.clear();

        if (selectedNodes.isEmpty()) {
            contentPanel.add(new JLabel("No layer selected"));
            revalidate();
            repaint();
            return;
        }

        // Get the type of the first selected node
        String type = null;
        boolean sameType = true;
        for (Node node : selectedNodes) {
            if (type == null) {
                type = node.getType();
            } else if (!type.equals(node.getType())) {
                sameType = false;
                break;
            }
        }

        if (!sameType) {
            contentPanel.add(new JLabel("Multiple layer types selected"));
            revalidate();
            repaint();
            return;
        }

        // Parse the usage string
        String usage = Layer.getLayerUsage(type);
        String[] lines = usage.split("\n");
        if (lines.length < 3) {
            contentPanel.add(new JLabel("Invalid layer usage format"));
            revalidate();
            repaint();
            return;
        }

        // Add summary
        contentPanel.add(new JLabel(lines[0]));
        contentPanel.add(Box.createVerticalStrut(10));

        // Process string parameters
        String[] stringDescs = lines[1].split(";");
        if (stringDescs.length > 0) {
            contentPanel.add(new JLabel("String Parameters:"));
            for (int i = 0; i < stringDescs.length; i++) {
                JPanel paramPanel = new JPanel(new BorderLayout());
                String desc = stringDescs[i].trim();
                paramPanel.add(new JLabel(desc), BorderLayout.NORTH);

                if (desc.contains("Activation")) {
                    // Create combo box for activation functions
                    JComboBox<String> combo = new JComboBox<>(Layer.getAvailableActivations());
                    String value = getCommonStringParam(selectedNodes, i);
                    if (!value.equals("...")) {
                        combo.setSelectedItem(value);
                    }
                    int finalI = i;
                    combo.addActionListener(e -> {
                        stringFields.get(finalI).setText((String) combo.getSelectedItem());
                        updateTimer.restart();
                    });

                    // Add hidden text field for value storage
                    JTextField hiddenField = new JTextField();
                    hiddenField.setVisible(false);
                    stringFields.add(hiddenField);

                    paramPanel.add(combo, BorderLayout.CENTER);
                } else if (desc.contains("Reduction")) {
                    // Create combo box for reduction operations
                    JComboBox<String> combo = new JComboBox<>(Layer.getAvailableReductions());
                    String value = getCommonStringParam(selectedNodes, i);
                    if (!value.equals("...")) {
                        combo.setSelectedItem(value);
                    }
                    int finalI1 = i;
                    combo.addActionListener(e -> {
                        stringFields.get(finalI1).setText((String) combo.getSelectedItem());
                        updateTimer.restart();
                    });

                    // Add hidden text field for value storage
                    JTextField hiddenField = new JTextField();
                    hiddenField.setVisible(false);
                    stringFields.add(hiddenField);

                    paramPanel.add(combo, BorderLayout.CENTER);
                } else {
                    // Regular string parameter
                    JTextField field = new JTextField(20);
                    stringFields.add(field);

                    // Set initial value
                    String value = getCommonStringParam(selectedNodes, i);
                    field.setText(value);

                    // Add change listener
                    field.getDocument().addDocumentListener(new DelayedUpdateListener());

                    paramPanel.add(field, BorderLayout.CENTER);
                }

                contentPanel.add(paramPanel);
                contentPanel.add(Box.createVerticalStrut(5));
            }
        }

        String[] floatDescs = lines[2].split(";");
        if (floatDescs.length > 0) {
            contentPanel.add(new JLabel("Numeric Parameters:"));
            for (int i = 0; i < floatDescs.length; i++) {
                JPanel paramPanel = new JPanel(new BorderLayout());
                paramPanel.add(new JLabel(floatDescs[i].trim()), BorderLayout.NORTH);

                JTextField field = new JTextField(20);
                floatFields.add(field);

                // Set initial value
                String value = getCommonFloatParam(selectedNodes, i);
                field.setText(value);

                // Add change listener
                field.getDocument().addDocumentListener(new DelayedUpdateListener());

                paramPanel.add(field, BorderLayout.CENTER);
                contentPanel.add(paramPanel);
                contentPanel.add(Box.createVerticalStrut(5));
            }
        }

        revalidate();
        repaint();
    }

    private void replaceFirstStringFieldWithComboBox(String[] options) {
        if (!stringFields.isEmpty() && contentPanel.getComponentCount() > 2) {
            JPanel paramPanel = (JPanel) contentPanel.getComponent(2);
            paramPanel.remove(1);

            JComboBox<String> combo = new JComboBox<>(options);
            combo.setSelectedItem(stringFields.get(0).getText());
            combo.addActionListener(e -> {
                stringFields.get(0).setText((String) combo.getSelectedItem());
                updateTimer.restart();
            });

            paramPanel.add(combo, BorderLayout.CENTER);
        }
    }

    private String getCommonStringParam(Set<Node> nodes, int index) {
        String commonValue = null;
        boolean first = true;

        for (Node node : nodes) {
            String[] params = node.getStringParams();
            if (params.length > index) {
                if (first) {
                    commonValue = params[index];
                    first = false;
                } else if (!params[index].equals(commonValue)) {
                    return "...";
                }
            }
        }

        return commonValue != null ? commonValue : "";
    }

    private String getCommonFloatParam(Set<Node> nodes, int index) {
        Float commonValue = null;
        boolean first = true;

        for (Node node : nodes) {
            float[] params = node.getFloatParams();
            if (params.length > index) {
                if (first) {
                    commonValue = params[index];
                    first = false;
                } else if (params[index] != commonValue) {
                    return "...";
                }
            }
        }

        return commonValue != null ? String.valueOf(commonValue) : "";
    }

    private void applyChanges() {
        Set<Node> selectedNodes = nodeManager.getSelectedNodes();
        if (selectedNodes.isEmpty()) return;

        // Collect new values
        String[] stringParams = stringFields.stream()
                .map(JTextField::getText)
                .toArray(String[]::new);
        AtomicInteger ctr = new AtomicInteger();
        float[] floatParams = floatFields.stream()
                .map(field -> {
                    try {
                        return Float.parseFloat(field.getText());
                    } catch (NumberFormatException e) {
                        return 0f;
                    }
                })
                .mapToDouble(f -> f).collect(() -> new float[floatFields.size()],
                        (array, value) -> array[ctr.getAndIncrement()] = (float) value,
                        (array1, array2) -> {
                        });

        // Apply to all selected nodes
        for (Node node : selectedNodes) {
            node.setStringParams(stringParams.clone());
            node.setFloatParams(floatParams.clone());

            // Recreate the layer in BeeDNN
            try {
                Layer layer = new Layer(node.getType(), floatParams, String.join(";", stringParams));
                // TODO: Update the node's native layer pointer if needed
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Error creating layer: " + e.getMessage(),
                        "Layer Creation Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class DelayedUpdateListener implements javax.swing.event.DocumentListener {
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            updateTimer.restart();
        }

        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            updateTimer.restart();
        }

        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            updateTimer.restart();
        }
    }
}
