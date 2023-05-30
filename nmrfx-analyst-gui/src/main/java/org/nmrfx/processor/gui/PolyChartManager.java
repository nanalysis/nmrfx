package org.nmrfx.processor.gui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import org.codehaus.commons.nullanalysis.Nullable;
import org.nmrfx.datasets.DatasetBase;

import java.util.Optional;

public class PolyChartManager {

    // singleton - maybe remove once the ownership is clearer

    private static final PolyChartManager instance = new PolyChartManager();

    public static PolyChartManager getInstance() {
        return instance;
    }

    private final ObservableList<PolyChart> allCharts = FXCollections.observableArrayList();
    private final SimpleObjectProperty<PolyChart> activeChart = new SimpleObjectProperty<>(null);
    private final SimpleBooleanProperty multipleCharts = new SimpleBooleanProperty(false);
    private final SimpleObjectProperty<DatasetBase> currentDataset = new SimpleObjectProperty<>(null);
    private final PolyChartSynchronizer synchronizer = new PolyChartSynchronizer();
    private int lastId = 0;

    public PolyChartManager() {
        allCharts.addListener((ListChangeListener<PolyChart>) e -> multipleCharts.set(allCharts.size() > 1));
    }

    public PolyChart create(FXMLController controller, Pane plotContent, Canvas canvas, Canvas peakCanvas, Canvas annoCanvas) {
        PolyChart chart = new PolyChart(controller, generateNextName(), plotContent, canvas, peakCanvas, annoCanvas);
        registerNewChart(chart);
        return chart;
    }

    private synchronized String generateNextName() {
        lastId++;
        return String.valueOf(lastId);
    }

    public void registerNewChart(PolyChart chart) {
        allCharts.add(chart);
        activeChart.set(chart);
    }

    public void unregisterChart(PolyChart chart) {
        allCharts.remove(chart);
        chart.getController().removeChart(chart);
        if (chart == activeChart.get()) {
            if (allCharts.isEmpty()) {
                activeChart.set(null);
            } else {
                activeChart.set(allCharts.get(0));
            }
        }
    }

    public void closeAll() {
        for (PolyChart chart : allCharts) {
            chart.clearDataAndPeaks();
            chart.clearAnnotations();
        }
    }

    public Optional<PolyChart> findChartByName(String name) {
        for (PolyChart chart : allCharts) {
            if (chart.getName().equals(name)) {
                return Optional.of(chart);
            }
        }
        return Optional.empty();
    }

    public ObservableList<PolyChart> getAllCharts() {
        return allCharts;
    }

    public void setActiveChart(PolyChart chart) {
        activeChart.set(chart);
        currentDataset.set(chart.getDataset());
        chart.getController().setActiveChart(chart);
    }

    public PolyChart getActiveChart() {
        return activeChart.get();
    }

    @Nullable
    public PolyChart getFirstChart() {
        return allCharts.isEmpty() ? null : allCharts.get(0);
    }

    public SimpleObjectProperty<PolyChart> activeChartProperty() {
        return activeChart;
    }

    public SimpleBooleanProperty multipleChartsProperty() {
        return multipleCharts;
    }

    public SimpleObjectProperty<DatasetBase> currentDatasetProperty() {
        return currentDataset;
    }

    public PolyChartSynchronizer getSynchronizer() {
        return synchronizer;
    }
}
