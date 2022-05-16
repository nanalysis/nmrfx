package org.nmrfx.analyst.gui.tools;

import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.mousehandlers.MouseBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.nmrfx.utils.GUIUtils.affirm;

public class SimplePeakRegionTool implements ControllerTool {
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

    public Analyzer getAnalyzer() {
        if (analyzer == null) {
            chart = getChart();
            Dataset dataset = (Dataset) chart.getDataset();
            if ((dataset == null) || (dataset.getNDim() > 1)) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Chart must have a 1D dataset");
                alert.showAndWait();
                return null;
            }
            analyzer = new Analyzer(dataset);
            if (!chart.getPeakListAttributes().isEmpty()) {
                analyzer.setPeakList(chart.getPeakListAttributes().get(0).getPeakList());
            }
        }
        return analyzer;
    }

    public void clearAnalysis(boolean prompt) {
        if (!prompt || affirm("Clear Analysis")) {
            PeakList peakList = analyzer.getPeakList();
            if (peakList != null) {
                PeakList.remove(peakList.getName());
            }
            Analyzer analyzer = getAnalyzer();
            if (analyzer != null) {
                analyzer.clearRegions();
            }
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(false);
            chart.refresh();
        }
    }

    public boolean hasRegions() {
        Set<DatasetRegion> regions = chart.getDataset().getRegions();
        return (regions != null) && !regions.isEmpty();
    }

    public void findRegions() {
        if (hasRegions()) {
            clearAnalysis(true);
        }
        Analyzer analyzer = getAnalyzer();
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

                double threshold = regions.stream().mapToDouble(r -> r.getIntegral()).max().orElse(1.0) / 1000.0;
                regions.stream().mapToDouble(r -> r.getIntegral()).filter(r -> r > threshold).min().ifPresent(min -> {
                    dataset.setNorm(min * dataset.getScale() / 1.0);
                });
            }
            chart.refresh();
            chart.chartProps.setRegions(true);
            chart.chartProps.setIntegrals(true);
            chart.setActiveRegion(null);
            chart.refresh();
        }

    }
    public void peakPick() {
        Analyzer analyzer = getAnalyzer();
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
        }

    }

    public void analyzeMultiplets() {
        Analyzer analyzer = getAnalyzer();
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
        }
    }


}
