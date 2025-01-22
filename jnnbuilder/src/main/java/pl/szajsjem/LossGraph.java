package pl.szajsjem;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class LossGraph extends JPanel {
    private final XYSeries trainSeries;
    private final XYSeries validationSeries;
    private final ChartPanel chartPanel;

    public LossGraph() {
        super(new BorderLayout());

        // Create dataset
        trainSeries = new XYSeries("Training Loss");
        validationSeries = new XYSeries("Validation Loss");
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(trainSeries);
        dataset.addSeries(validationSeries);

        // Create chart
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Training Progress",
                "Iteration",
                "Loss",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // Customize chart
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, Color.BLUE);
        renderer.setSeriesPaint(1, Color.RED);
        plot.setRenderer(renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.GRAY);
        plot.setDomainGridlinePaint(Color.GRAY);

        // Create chart panel
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));
        add(chartPanel, BorderLayout.CENTER);
    }

    public void updateData(List<Float> trainLosses, List<Float> validationLosses) {
        trainSeries.clear();
        validationSeries.clear();

        for (int i = 0; i < trainLosses.size(); i++) {
            trainSeries.add(i, trainLosses.get(i));
        }

        for (int i = 0; i < validationLosses.size(); i++) {
            validationSeries.add(i, validationLosses.get(i));
        }
    }

    public void reset() {
        trainSeries.clear();
        validationSeries.clear();
    }
}