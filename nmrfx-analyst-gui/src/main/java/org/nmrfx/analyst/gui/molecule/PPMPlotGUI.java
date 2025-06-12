package org.nmrfx.analyst.gui.molecule;

import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.nmrfx.chart.*;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.utils.GUIUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PPMPlotGUI {
    private Stage stage = null;
    private XYChartPane chartPane;
    private XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 800, 500);
    AtomController atomController = null;

    public void create(AtomController atomController) {
        stage = new Stage();
        stage.setTitle("Atom Table Plot");
        stage.setScene(stageScene);
        chartPane = new XYChartPane();
        chartPane.setBackground(Background.fill(Color.WHITE));
        borderPane.setCenter(chartPane);
        this.atomController = atomController;
    }

    public void showPPMPlot() {
        if (stage != null) {
            activeChart.getYAxis().setZeroIncluded(true);
            activeChart.getXAxis().setZeroIncluded(true);
            stage.show();
            stage.toFront();
        }
    }

    private List<AtomController.PPMSet> getSelectedPPMSets() {
        return atomController.PPMSets.stream().
                filter(set -> set.isSelected.getValue())
                .toList();
    }

    public void plotDeltas() {
        activeChart = chartPane.getBarChart();
        activeChart.getData().clear();
        chartPane.updateChart();
        activeChart.getXAxis().setLabel("Residue Number");
        activeChart.getYAxis().setLabel("Delta (PPM)");
        List<AtomController.PPMSet> ppmSets = getSelectedPPMSets();
        if (ppmSets.isEmpty()) {
            GUIUtils.warn("select columns to plot", "select columns to plot");
            return;
        }

        List<DataSeries> dataseries = getDeltas(ppmSets);
        activeChart.setData(dataseries);
        activeChart.getYAxis().setAutoRanging(true);
        showPPMPlot();
    }

    private List<DataSeries> getDeltas(List<AtomController.PPMSet> ppmSets) {
        List<DataSeries> data = new ArrayList<>();
        DataSeries dataseries = new DataSeries();

        AtomController.PPMSet set1 = ppmSets.getFirst();
        AtomController.PPMSet set2 = ppmSets.getLast();
        HashMap<Double, Double> deltas = new HashMap<>();

        atomController.atoms.stream()
                .filter(atom -> atom.getPPMByMode(set1.iSet, set1.refSet) != null &&
                        atom.getPPMByMode(set2.iSet, set2.refSet) != null)
                .forEach( atom -> {
                            double y = atom.getDeltaPPM2(set1.iSet, set2.iSet, set1.refSet, set2.refSet);
                            double x = atom.getResidueNumber();
                            if (atom.getAtomicNumber() == 15) { y = y/5.0;}
                            deltas.put(x, deltas.getOrDefault(x, 0.0) + Math.pow(y,2.0));
                        });
        deltas.forEach((key, value) -> dataseries.add(new XYValue(key, Math.sqrt(value))));

        dataseries.setFill(Color.DARKORANGE);
        data.add(dataseries);
        return data;
    }

    public void plotShifts() {
        activeChart = chartPane.getXYChart();
        chartPane.updateChart();
        activeChart.getData().clear();
        List<AtomController.PPMSet> ppmSets = getSelectedPPMSets();
        if (ppmSets.isEmpty()) {
            GUIUtils.warn("select columns to plot", "select columns to plot");
            return;
        }
        activeChart.getXAxis().setLabel(ppmSets.getFirst().ref + " " + ppmSets.getFirst().iSet);
        activeChart.getYAxis().setLabel(ppmSets.getLast().ref + " " + ppmSets.getLast().iSet);
        DataSeries dataseries = plotShifts(ppmSets);
        activeChart.getData().add(dataseries);
        showPPMPlot();
    }

    private DataSeries plotShifts(List<AtomController.PPMSet> ppmSets) {
        AtomController.PPMSet set1 = ppmSets.getFirst();
        AtomController.PPMSet set2 = ppmSets.getLast();
        DataSeries dataseries = new DataSeries();
        atomController.atoms.stream()
                .filter(atom -> atom.getPPMByMode(set1.iSet, set1.refSet) != null &&
                                atom.getPPMByMode(set2.iSet, set2.refSet) != null)
                .forEach(atom -> {
                    PPMv x = atom.getPPMByMode(set1.iSet, set1.refSet);
                    PPMv y = atom.getPPMByMode(set2.iSet, set2.refSet);
                    XYValue xyValue1 = new XYValue(x.getValue(), y.getValue());
                    dataseries.add(xyValue1);
                });
        dataseries.drawSymbol(true);
        dataseries.setFill(Color.DARKORANGE);
        return dataseries;
    }
}
