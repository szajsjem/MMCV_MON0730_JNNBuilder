package pl.szajsjem.data;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSVLoaderDialog extends JDialog {
    private final JPanel columnsPanel;
    private final List<JComboBox<String>> columnMappers = new ArrayList<>();
    private final JButton loadButton;
    private final JLabel statusLabel;
    private final Map<Integer, CategoricalMapping> categoricalMappings = new HashMap<>();
    private String[][] previewData;
    private String[] headers;
    private File selectedFile;
    private LoadedData result = null;

    public CSVLoaderDialog(JFrame parent) {
        super(parent, "Load CSV Data", true);
        setLayout(new BorderLayout(10, 10));
        setSize(600, 400);
        setLocationRelativeTo(parent);

        // File selection panel
        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        JTextField filePathField = new JTextField();
        filePathField.setEditable(false);
        JButton browseButton = new JButton("Browse");
        filePanel.add(filePathField, BorderLayout.CENTER);
        filePanel.add(browseButton, BorderLayout.EAST);
        add(filePanel, BorderLayout.NORTH);

        // Columns mapping panel
        columnsPanel = new JPanel();
        columnsPanel.setLayout(new BoxLayout(columnsPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(columnsPanel);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with status and load button
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        statusLabel = new JLabel(" ");
        loadButton = new JButton("Load Data");
        loadButton.setEnabled(false);
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(loadButton, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Browse button action
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = getjFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
                }

                public String getDescription() {
                    return "CSV Files (*.csv)";
                }
            });

            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedFile = fileChooser.getSelectedFile();
                filePathField.setText(selectedFile.getPath());
                loadPreview();
            }
        });

        // Load button action
        loadButton.addActionListener(e -> {
            result = processData();
            if (result != null) {
                setVisible(false);
            }
        });
    }

    private static JFileChooser getjFileChooser() {
        JFileChooser fileChooser = new JFileChooser() {
            @Override
            protected void setup(FileSystemView view) {
                putClientProperty("FileChooser.useShellFolder", Boolean.FALSE);
                super.setup(view);
            }
        };

        // Use a simple file view that doesn't use system icons
        fileChooser.setFileView(new FileView() {
            @Override
            public String getName(File f) {
                return f.getName();
            }

            @Override
            public String getDescription(File f) {
                return f.getName();
            }

            @Override
            public String getTypeDescription(File f) {
                return f.isDirectory() ? "Folder" : "File";
            }

            @Override
            public Icon getIcon(File f) {
                // Return a simple default icon based on whether it's a directory or file
                return UIManager.getIcon(f.isDirectory() ? "FileView.directoryIcon" : "FileView.fileIcon");
            }
        });
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".bnn");
            }

            public String getDescription() {
                return "Neural Network Files (*.bnn)";
            }
        });
        return fileChooser;
    }

    public static LoadedData showDialog(JFrame parent) {
        CSVLoaderDialog dialog = new CSVLoaderDialog(parent);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void loadPreview() {
        try {
            // Read first few lines of the CSV
            BufferedReader reader = new BufferedReader(new FileReader(selectedFile));
            headers = reader.readLine().split(",");

            // Read preview data (first 5 rows)
            previewData = new String[5][];
            for (int i = 0; i < 5; i++) {
                String line = reader.readLine();
                if (line == null) break;
                previewData[i] = line.split(",");
            }
            reader.close();

            // Update UI
            updateColumnMappers();
            loadButton.setEnabled(true);
            statusLabel.setText("File loaded successfully. Please map columns.");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error reading CSV file: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            loadButton.setEnabled(false);
            statusLabel.setText("Error loading file.");
        }
    }

    private void updateColumnMappers() {
        columnsPanel.removeAll();
        columnMappers.clear();

        // Add header row
        JPanel headerPanel = new JPanel(new GridLayout(1, headers.length + 1));
        headerPanel.add(new JLabel("Column"));
        for (String header : headers) {
            headerPanel.add(new JLabel(header));
        }
        columnsPanel.add(headerPanel);

        // Add mapping row
        JPanel mappingPanel = new JPanel(new GridLayout(1, headers.length + 1));
        mappingPanel.add(new JLabel("Map to:"));
        for (int i = 0; i < headers.length; i++) {
            JComboBox<String> mapper = new JComboBox<>(new String[]{"Unused", "Input", "Output"});
            columnMappers.add(mapper);
            mappingPanel.add(mapper);
        }
        columnsPanel.add(mappingPanel);

        // Add preview rows
        if (previewData != null) {
            for (String[] row : previewData) {
                if (row == null) break;
                JPanel previewPanel = new JPanel(new GridLayout(1, headers.length + 1));
                previewPanel.add(new JLabel("Preview:"));
                for (String value : row) {
                    previewPanel.add(new JLabel(value));
                }
                columnsPanel.add(previewPanel);
            }
        }

        columnsPanel.revalidate();
        columnsPanel.repaint();
    }

    private int countRows() throws IOException {
        int count = 0;
        BufferedReader reader = new BufferedReader(new FileReader(selectedFile));
        reader.readLine(); // Skip header
        while (reader.readLine() != null) {
            count++;
        }
        reader.close();
        return count;
    }

    private LoadedData processData() {
        try {
            // Count inputs and outputs and identify categorical columns
            int inputCount = 0;
            int outputCount = 0;
            List<String> inputNames = new ArrayList<>();
            List<String> outputNames = new ArrayList<>();
            List<Boolean> isInputCategorical = new ArrayList<>();
            List<Boolean> isOutputCategorical = new ArrayList<>();

            // First pass: analyze columns and create mappings
            for (int i = 0; i < columnMappers.size(); i++) {
                String mapping = (String) columnMappers.get(i).getSelectedItem();
                if (mapping.equals("Input")) {
                    inputCount++;
                    inputNames.add(headers[i]);
                    boolean isCategorical = isCategoricalColumn(i);
                    isInputCategorical.add(isCategorical);
                    if (isCategorical) {
                        categoricalMappings.put(i, new CategoricalMapping(headers[i]));
                    }
                } else if (mapping.equals("Output")) {
                    outputCount++;
                    outputNames.add(headers[i]);
                    boolean isCategorical = isCategoricalColumn(i);
                    isOutputCategorical.add(isCategorical);
                    if (isCategorical) {
                        categoricalMappings.put(i, new CategoricalMapping(headers[i]));
                    }
                }
            }

            if (inputCount == 0 || outputCount == 0) {
                JOptionPane.showMessageDialog(this,
                        "Please select at least one input and one output column.",
                        "Invalid Mapping",
                        JOptionPane.WARNING_MESSAGE);
                return null;
            }

            // Count rows
            int rowCount = countRows();

            // Initialize arrays
            float[][] inputs = new float[rowCount][inputCount];
            float[][] outputs = new float[rowCount][outputCount];

            // Read and process data
            BufferedReader reader = new BufferedReader(new FileReader(selectedFile));
            reader.readLine(); // Skip header

            String line;
            int row = 0;
            while ((line = reader.readLine()) != null && row < rowCount) {
                String[] values = line.split(",");
                int inputIdx = 0;
                int outputIdx = 0;

                for (int col = 0; col < values.length; col++) {
                    String mapping = (String) columnMappers.get(col).getSelectedItem();
                    String value = values[col].trim();

                    if (mapping.equals("Input")) {
                        if (isInputCategorical.get(inputIdx)) {
                            CategoricalMapping catMapping = categoricalMappings.get(col);
                            inputs[row][inputIdx] = catMapping.getOrCreateIndex(value);
                        } else {
                            inputs[row][inputIdx] = Float.parseFloat(value);
                        }
                        inputIdx++;
                    } else if (mapping.equals("Output")) {
                        if (isOutputCategorical.get(outputIdx)) {
                            CategoricalMapping catMapping = categoricalMappings.get(col);
                            outputs[row][outputIdx] = catMapping.getOrCreateIndex(value);
                        } else {
                            outputs[row][outputIdx] = Float.parseFloat(value);
                        }
                        outputIdx++;
                    }
                }
                row++;
            }
            reader.close();

            return new LoadedData(inputs, outputs, inputNames.toArray(new String[0]),
                    outputNames.toArray(new String[0]), categoricalMappings);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error processing data: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private boolean isCategoricalColumn(int colIndex) {
        // Check first few rows to determine if column is categorical
        for (int i = 0; i < Math.min(5, previewData.length); i++) {
            if (previewData[i] != null) {
                try {
                    Float.parseFloat(previewData[i][colIndex].trim());
                } catch (NumberFormatException e) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class LoadedData {
        public final float[][] inputs;
        public final float[][] outputs;
        public final String[] inputColumnNames;
        public final String[] outputColumnNames;
        public final Map<Integer, CategoricalMapping> categoricalMappings;

        public LoadedData(float[][] inputs, float[][] outputs,
                          String[] inputColumnNames, String[] outputColumnNames,
                          Map<Integer, CategoricalMapping> categoricalMappings) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.inputColumnNames = inputColumnNames;
            this.outputColumnNames = outputColumnNames;
            this.categoricalMappings = new HashMap<>(categoricalMappings);
        }
    }
}