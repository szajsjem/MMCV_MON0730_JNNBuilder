package pl.szajsjem.old;


import com.beednn.NetTrain;

import javax.swing.*;
import java.awt.*;


public class NetworkEditorMenuBar extends JMenuBar {
    private final GraphingArea parent;

    public NetworkEditorMenuBar(GraphingArea parent) {
        this.parent = parent;

        // File Menu
        JMenu fileMenu = new JMenu("File");
        add(fileMenu);

        JMenuItem newProject = new JMenuItem("New Project");
        JMenuItem openProject = new JMenuItem("Open Project");
        JMenuItem saveProject = new JMenuItem("Save Project");
        JMenuItem exportNetwork = new JMenuItem("Export Trained Network");

        fileMenu.add(newProject);
        fileMenu.add(openProject);
        fileMenu.add(saveProject);
        fileMenu.addSeparator();
        fileMenu.add(exportNetwork);

        // Data Menu
        JMenu dataMenu = new JMenu("Data");
        add(dataMenu);

        JMenuItem loadTraining = new JMenuItem("Load Training Data");
        JMenuItem loadValidation = new JMenuItem("Load Validation Data");

        dataMenu.add(loadTraining);
        dataMenu.add(loadValidation);

        // Training Menu
        JMenu trainingMenu = new JMenu("Training");
        add(trainingMenu);

        JMenuItem configureTraining = new JMenuItem("Configure Training");
        JMenuItem startTraining = new JMenuItem("Start Training");
        JMenuItem viewProgress = new JMenuItem("View Training Progress");

        trainingMenu.add(configureTraining);
        trainingMenu.add(startTraining);
        trainingMenu.add(viewProgress);

        // Add action listeners
        configureTraining.addActionListener(e -> showTrainingConfigDialog());
        loadTraining.addActionListener(e -> showDataLoadDialog(true));
        loadValidation.addActionListener(e -> showDataLoadDialog(false));
        startTraining.addActionListener(e -> startNetworkTraining());
    }

    private void showTrainingConfigDialog() {
        JDialog dialog = new JDialog(parent, "Training Configuration", true);
        dialog.setLayout(new BorderLayout());

        JPanel configPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Add components for epochs, batch size, etc.
        JComboBox<String> optimizerCombo = new JComboBox<>(NetTrain.getAvailableOptimizers());
        JComboBox<String> lossCombo = new JComboBox<>(NetTrain.getAvailableLosses());
        JComboBox<String> regularizerCombo = new JComboBox<>(NetTrain.getAvailableRegularizers());
        JSpinner epochSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 1));

        // Add components to panel...

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void showDataLoadDialog(boolean isTraining) {
        // Create data loading dialog with CSV preview and column selection
    }

    private void startNetworkTraining() {
        // Validate network structure
        // Build network in C++ lib
        // Start training in separate thread
        // Show progress dialog
    }
}
