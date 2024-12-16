package pl.szajsjem.data;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DataManager extends JPanel {
    private final JTable table;
    private final DataTableModel tableModel;
    private final JLabel statusLabel;
    private String[] inputColumnNames;
    private String[] outputColumnNames;
    private float[][] inputs;
    private float[][] outputs;
    private Map<Integer, CategoricalMapping> categoricalMappings;

    public DataManager() {
        setLayout(new BorderLayout(5, 5));

        // Create table model and table
        tableModel = new DataTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setRowSelectionAllowed(true);

        // Add custom renderer for float values
        DefaultTableCellRenderer floatRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Float) {
                    setText(String.format("%.4f", value));
                }
                return this;
            }
        };
        floatRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        table.setDefaultRenderer(Float.class, floatRenderer);

        // Create toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton deleteButton = new JButton("Delete Selected Rows");
        deleteButton.addActionListener(e -> deleteSelectedRows());
        toolBar.add(deleteButton);

        JButton normalizeButton = new JButton("Normalize Data");
        normalizeButton.addActionListener(e -> normalizeData());
        toolBar.add(normalizeButton);

        // Status label
        statusLabel = new JLabel(" ");
        toolBar.addSeparator();
        toolBar.add(statusLabel);

        // Add components to panel
        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void setData(CSVLoaderDialog.LoadedData loadedData, String[] inputNames, String[] outputNames) {
        this.inputs = loadedData.inputs;
        this.outputs = loadedData.outputs;
        this.inputColumnNames = inputNames;
        this.outputColumnNames = outputNames;
        this.categoricalMappings = loadedData.categoricalMappings;

        // Add categorical data info button to toolbar
        JButton catInfoButton = new JButton("Show Categories");
        catInfoButton.addActionListener(e -> showCategoricalInfo());

        tableModel.fireTableStructureChanged();
        updateStatus();
    }

    private void showCategoricalInfo() {
        if (categoricalMappings.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No categorical columns in the data.",
                    "Categorical Data",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder info = new StringBuilder("Categorical Columns:\n\n");
        for (Map.Entry<Integer, CategoricalMapping> entry : categoricalMappings.entrySet()) {
            CategoricalMapping mapping = entry.getValue();
            info.append(mapping.getColumnName()).append(":\n");
            List<String> values = mapping.getAllValues();
            for (int i = 0; i < values.size(); i++) {
                info.append(String.format("  %d: %s\n", i, values.get(i)));
            }
            info.append("\n");
        }

        JTextArea textArea = new JTextArea(info.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        JOptionPane.showMessageDialog(this,
                scrollPane,
                "Categorical Data Information",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteSelectedRows() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) return;

        // Sort in descending order to avoid index shifting
        Arrays.sort(selectedRows);
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            int row = selectedRows[i];
            removeRow(row);
        }

        tableModel.fireTableDataChanged();
        updateStatus();
    }

    private void removeRow(int row) {
        // Create new arrays without the selected row
        float[][] newInputs = new float[inputs.length - 1][];
        float[][] newOutputs = new float[outputs.length - 1][];

        // Copy rows before the deleted row
        System.arraycopy(inputs, 0, newInputs, 0, row);
        System.arraycopy(outputs, 0, newOutputs, 0, row);

        // Copy rows after the deleted row
        System.arraycopy(inputs, row + 1, newInputs, row, inputs.length - row - 1);
        System.arraycopy(outputs, row + 1, newOutputs, row, outputs.length - row - 1);

        inputs = newInputs;
        outputs = newOutputs;
    }

    private void normalizeData() {
        // Normalize inputs
        for (int col = 0; col < inputs[0].length; col++) {
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;

            // Find min and max
            for (float[] input : inputs) {
                min = Math.min(min, input[col]);
                max = Math.max(max, input[col]);
            }

            // Normalize column
            float range = max - min;
            if (range != 0) {
                for (float[] input : inputs) {
                    input[col] = (input[col] - min) / range;
                }
            }
        }

        // Normalize outputs
        for (int col = 0; col < outputs[0].length; col++) {
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;

            // Find min and max
            for (float[] output : outputs) {
                min = Math.min(min, output[col]);
                max = Math.max(max, output[col]);
            }

            // Normalize column
            float range = max - min;
            if (range != 0) {
                for (float[] output : outputs) {
                    output[col] = (output[col] - min) / range;
                }
            }
        }

        tableModel.fireTableDataChanged();
        JOptionPane.showMessageDialog(this,
                "Data normalized to range [0, 1]",
                "Normalization Complete",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateStatus() {
        statusLabel.setText(String.format("Total rows: %d", inputs.length));
    }

    public float[][] getInputs() {
        return inputs;
    }

    public float[][] getOutputs() {
        return outputs;
    }

    private class DataTableModel extends AbstractTableModel {
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex < inputs[0].length) {
                float value = inputs[rowIndex][columnIndex];
                CategoricalMapping mapping = categoricalMappings.get(columnIndex);
                if (mapping != null) {
                    return String.format("%s (%d)", mapping.getValue((int) value), (int) value);
                }
                return value;
            } else {
                int outputCol = columnIndex - inputs[0].length;
                float value = outputs[rowIndex][outputCol];
                CategoricalMapping mapping = categoricalMappings.get(columnIndex);
                if (mapping != null) {
                    return String.format("%s (%d)", mapping.getValue((int) value), (int) value);
                }
                return value;
            }
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            try {
                if (columnIndex < inputs[0].length) {
                    CategoricalMapping mapping = categoricalMappings.get(columnIndex);
                    if (mapping != null) {
                        String strValue = value.toString();
                        if (strValue.contains("(")) {
                            strValue = strValue.substring(0, strValue.lastIndexOf("(")).trim();
                        }
                        inputs[rowIndex][columnIndex] = mapping.getOrCreateIndex(strValue);
                    } else {
                        inputs[rowIndex][columnIndex] = Float.parseFloat(value.toString());
                    }
                } else {
                    int outputCol = columnIndex - inputs[0].length;
                    CategoricalMapping mapping = categoricalMappings.get(columnIndex);
                    if (mapping != null) {
                        String strValue = value.toString();
                        if (strValue.contains("(")) {
                            strValue = strValue.substring(0, strValue.lastIndexOf("(")).trim();
                        }
                        outputs[rowIndex][outputCol] = mapping.getOrCreateIndex(strValue);
                    } else {
                        outputs[rowIndex][outputCol] = Float.parseFloat(value.toString());
                    }
                }
                fireTableCellUpdated(rowIndex, columnIndex);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(DataManager.this,
                        "Please enter a valid value",
                        "Invalid Input",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        @Override
        public String getColumnName(int column) {
            String baseName;
            if (column < inputColumnNames.length) {
                baseName = inputColumnNames[column] + " (Input)";
            } else {
                baseName = outputColumnNames[column - inputColumnNames.length] + " (Output)";
            }

            CategoricalMapping mapping = categoricalMappings.get(column);
            if (mapping != null) {
                return baseName + " [Categorical: " + mapping.getCategories() + " categories]";
            }
            return baseName;
        }

        @Override
        public int getRowCount() {
            return inputs != null ? inputs.length : 0;
        }

        @Override
        public int getColumnCount() {
            return (inputs != null ? inputs[0].length : 0) +
                    (outputs != null ? outputs[0].length : 0);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return Float.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }
    }
}