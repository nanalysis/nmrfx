package org.nmrfx.processor.gui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.codehaus.commons.nullanalysis.Nullable;

import java.util.Optional;

public class PolyChartManager {

    // singleton - maybe remove once the ownership is clearer

    private static final PolyChartManager instance = new PolyChartManager();

    public static PolyChartManager getInstance() {
        return instance;
    }

    private final ObservableList<PolyChart> CHARTS = FXCollections.observableArrayList();
    private final SimpleObjectProperty<PolyChart> activeChart = new SimpleObjectProperty<>(null);
    private final SimpleBooleanProperty multipleCharts = new SimpleBooleanProperty(false);

    public PolyChartManager() {
        CHARTS.addListener((ListChangeListener) (e -> multipleCharts.set(CHARTS.size() > 1)));
    }

    public void registerNewChart(PolyChart chart) {
        CHARTS.add(chart);
        activeChart.set(chart);
    }

    public void unregisterChart(PolyChart chart) {
        CHARTS.remove(chart);
        chart.getController().removeChart(chart);
        if (chart == activeChart.get()) {
            if (CHARTS.isEmpty()) {
                activeChart.set(null);
            } else {
                activeChart.set(CHARTS.get(0));
            }
        }
    }

    public void closeAll() {
        for (PolyChart chart : CHARTS) {
            chart.clearDataAndPeaks();
            chart.clearAnnotations();
        }
    }

    public Optional<PolyChart> findChartByName(String name) {
        for (PolyChart chart : CHARTS) {
            if (chart.getName().equals(name)) {
                return Optional.of(chart);
            }
        }
        return Optional.empty();
    }


    // getters

    public ObservableList<PolyChart> getCharts() {
        return CHARTS;
    }

    public void setActiveChart(PolyChart chart) {
        activeChart.set(chart);
    }

    public PolyChart getActiveChart() {
        return activeChart.get();
    }

    @Nullable
    public PolyChart getFirstChart() {
        return CHARTS.isEmpty() ? null : CHARTS.get(0);
    }

    public SimpleObjectProperty<PolyChart> activeChartProperty() {
        return activeChart;
    }

    public boolean isMultipleCharts() {
        return multipleCharts.get();
    }

    public SimpleBooleanProperty multipleChartsProperty() {
        return multipleCharts;
    }
}
