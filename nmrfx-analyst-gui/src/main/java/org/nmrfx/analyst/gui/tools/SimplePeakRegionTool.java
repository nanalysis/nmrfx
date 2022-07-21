package org.nmrfx.analyst.gui.tools;

import javafx.scene.control.Alert;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.AnalyzerTool;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.nmrfx.utils.GUIUtils.affirm;

public class SimplePeakRegionTool implements ControllerTool, AnalyzerTool {
    private static final Logger log = LoggerFactory.getLogger(SimplePeakRegionTool.class);
    Analyzer analyzer;
    FXMLController controller;
    PolyChart chart;

    public SimplePeakRegionTool(FXMLController controller, PolyChart chart) {
        this.controller = controller;
        this.chart = chart;
    }

    @Override
    public void close() {

    }

    PolyChart getChart() {
        chart = controller.getActiveChart();
        return chart;
    }

    /**
     * Updates the tool's chart and analyzer.
     */
    public void updateTool() {
        getAnalyzer();
    }

    /**
     * Gets the currently active chart and sets the analyzer based on the chart's
     * dataset.
     */
    public Analyzer getAnalyzer() {
        chart = getChart();
        Dataset dataset = (Dataset) chart.getDataset();
        if (analyzer == null) {
            if ((dataset == null) || (dataset.getNDim() > 1)) {
                analyzer = null;
                return analyzer;
            }
            analyzer = new Analyzer(dataset);
        }  else {
            if (dataset == null || dataset.getNDim() > 1){
                analyzer = null;
                return analyzer;
            }
            if (analyzer.getDataset() != dataset && dataset.getNDim() <= 1) {
                analyzer = new Analyzer(dataset);
            }
        }
        if (!chart.getPeakListAttributes().isEmpty()) {
            analyzer.setPeakList(chart.getPeakListAttributes().get(0).getPeakList());
        }
        return analyzer;
    }

    public void clearAnalysis(boolean prompt) {
        if (!prompt || affirm("Clear Analysis")) {
            if (analyzer != null) {
                PeakList peakList = analyzer.getPeakList();
                if (peakList != null) {
                    PeakList.remove(peakList.getName());
                }
                analyzer.clearRegions();
            }
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(false);
            chart.refresh();
        }
    }

    private void invalidDatasetAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public boolean hasRegions() {
        Dataset dataset = (Dataset) chart.getDataset();
        if (dataset != null) {
            Set<DatasetRegion> regions = dataset.getRegions();
            return (regions != null) && !regions.isEmpty();
        }
        return false;
    }

    public void findRegions() {
        if (hasRegions()) {
            clearAnalysis(true);
        }
        if (analyzer != null) {
            analyzer.calculateThreshold();
            analyzer.getThreshold();
            analyzer.autoSetRegions();
            try {
                analyzer.integrate();
            } catch (IOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
                return;
            }
            Set<DatasetRegion> regions = chart.getDataset().getRegions();
            Dataset dataset = (Dataset) chart.getDataset();
            if (!regions.isEmpty()) {
                // normalize to the smallest integral, but don't count very small integrals (less than 0.001 of max
                // to avoid artifacts

                double threshold = regions.stream().mapToDouble(DatasetRegion::getIntegral).max().orElse(1.0) / 1000.0;
                regions.stream().mapToDouble(DatasetRegion::getIntegral).filter(r -> r > threshold).min().ifPresent(
                        min -> dataset.setNorm(min * dataset.getScale()));
            }
            chart.refresh();
            chart.chartProps.setRegions(true);
            chart.chartProps.setIntegrals(true);
            chart.setActiveRegion(null);
            chart.refresh();
        } else {
            invalidDatasetAlert("Chart must have a 1D dataset.");
        }

    }
    public void peakPick() {
        if (analyzer != null) {
            Set<DatasetRegion> regions = chart.getDataset().getRegions();
            if ((regions == null) || regions.isEmpty()) {
                analyzer.calculateThreshold();
                double threshold = analyzer.getThreshold();
                PeakPicking.peakPickActive(controller, threshold);
                analyzer.setPeakList(chart.getPeakListAttributes().get(0).getPeakList());
            } else {
                analyzer.peakPickRegions();
            }
            PeakList peakList = analyzer.getPeakList();
            List<String> peakListNames = new ArrayList<>();
            peakListNames.add(peakList.getName());
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(true);
            chart.updatePeakLists(peakListNames);
            var dStat = peakList.widthDStats(0);
            double minWidth = dStat.getPercentile(10);
            double maxWidth = dStat.getPercentile(90);
            peakList.setProperty("minWidth", String.valueOf(minWidth));
            peakList.setProperty("maxWidth", String.valueOf(maxWidth));
            chart.refresh();
        } else {
            invalidDatasetAlert("Chart must have a valid dataset.");
        }

    }

    public void analyzeMultiplets() {
        if (analyzer != null) {
            try {
                analyzer.analyze();
                PeakList peakList = analyzer.getPeakList();
                List<String> peakListNames = new ArrayList<>();
                peakListNames.add(peakList.getName());
                chart.chartProps.setRegions(false);
                chart.chartProps.setIntegrals(true);
                chart.updatePeakLists(peakListNames);
                chart.refresh();
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        } else {
            invalidDatasetAlert("Chart must have a 1D dataset.");
        }
    }


}
