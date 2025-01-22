# Neural Network Editor

A Java-based graphical editor for designing, training, and managing neural networks. This tool provides an intuitive interface for creating and experimenting with various neural network architectures.

## Features

- Visual network design with drag-and-drop interface
- Support for various layer types and architectures
- Real-time network validation
- Data management and preprocessing tools
- Training configuration and monitoring
- Import/Export capabilities for network models
- Automatic network layout
- Undo/Redo functionality
- Copy/Paste support for network components

## Requirements

- Java 17 or higher
- Minimum 4GB RAM recommended
- Graphics card supporting Java2D

## Dependencies

- BeeDNN (Neural Network Library)
- JSON Library (org.json)
- org.jfree jfreechart library
- Swing/AWT for GUI components

## Installation

1. Clone the repository:
```bash
git clone https://github.com/szajsjem/MMCV_MON0730_JNNBuilder.git
```

2. Build using your preferred Java IDE or Maven:
```bash
mvn clean install
```

3. Run the application:
```bash
java -jar neural-network-editor.jar
```

## Usage

### Basic Network Creation

1. Launch the application
2. Use the layer palette on the left to add layers by clicking
3. Connect layers by dragging from output (right) to input (left) dots
4. Configure layer properties in the properties panel

### Working with Data

1. Go to Data → Load Data to import your CSV dataset
2. Use the Data Manager to preprocess and normalize your data
3. Configure input/output mappings for your network

### Training Configuration

1. Access Network → Training Settings to configure:
   - Optimizer settings
   - Learning rate
   - Batch size
   - Regularization
   - Loss function

### Saving and Loading

- Save your network design: File → Save (Ctrl+S)
- Load existing network: File → Open (Ctrl+O)
- Export trained model: File → Export → BeeDNN Model
- Export executable jar: File → Export → Trained Network JAR

## File Formats

- `.bnn` - Network design files
- `.beednn` - Exported trained models
- `.csv` - Data files for training/testing

## Keyboard Shortcuts

- Ctrl+N: New network
- Ctrl+S: Save
- Ctrl+O: Open
- Ctrl+Z: Undo
- Ctrl+Y: Redo
- Ctrl+C: Copy
- Ctrl+V: Paste
- Ctrl+X: Cut
- Delete: Remove selected
- Ctrl+L: Auto-layout
- Ctrl+0: Fit to window

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the Apache-2.0 license.

## Author

Szajsjem 2024-2025

## Acknowledgments

- BeeDNN library team for the neural network implementation
- Contributors to the project
- Java Swing/AWT for the GUI framework

## Support

For support, please open an issue on the project's GitHub page or contact the development team at szajsjem@gmail.pl

## Version History

- 1.0.0 (2025) future release
  - Initial release
  - Basic network editor functionality
  - Data management tools
  - Training interface