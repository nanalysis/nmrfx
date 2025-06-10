package org.nmrfx.analyst.gui.molecule;

import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.nmrfx.chart.*;
import org.nmrfx.utils.GUIUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class PPMPlotGUI {
    private Stage stage = null;
    private XYChartPane chartPane;
    private XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 800, 500);
    AtomController atomController = null;
    Color[] COLORS = new Color[]{Color.DARKORANGE, Color.BLUE};

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

    public void plotDeltas() {
        activeChart = chartPane.getBarChart();
        activeChart.getData().clear();
        chartPane.updateChart();
        activeChart.getXAxis().setLabel("Residue Number");
        activeChart.getYAxis().setLabel("Delta (PPM)");
        activeChart.getYAxis().setAutoRanging(true);

        List<DataSeries> dataseries = getDeltas();
        activeChart.setData(dataseries);
        showPPMPlot();
    }

    private List<DataSeries> getDeltas() {
        List<DataSeries> data = new ArrayList<>();
        DataSeries dataseries = new DataSeries();
        List<AtomController.PPMSet> ppmSets = atomController.PPMSets.stream().
                filter(set -> set.isSelected.getValue())
                .toList();
        if (ppmSets.isEmpty()) {
            GUIUtils.warn("select columns to plot", "select columns to plot");
            return null;
        }
        AtomController.PPMSet set1 = ppmSets.getFirst();
        AtomController.PPMSet set2 = ppmSets.getLast();

        atomController.atoms.stream()
                .filter(atom -> atom.getAtomicNumber() == 1)
                .filter(atom -> set1.refSet ? atom.getRefPPM(set1.iSet) != null :
                        atom.getPPM(set1.iSet) != null &&
                                set2.refSet ? atom.getRefPPM(set2.iSet) != null :
                        atom.getPPM(set2.iSet) != null)
                .forEach(atom -> {
                    double y = atom.getDeltaPPM2(set1.iSet, set2.iSet, set1.refSet, set2.refSet);
                    double x = atom.getResidueNumber();
                    XYValue xyValue = new XYValue(x, Math.abs(y));
                    dataseries.add(xyValue);
                });
        dataseries.setFill(COLORS[0]);
        data.add(dataseries);
        return data;
    }

    public void plotShifts() {
        activeChart = chartPane.getXYChart();
        chartPane.updateChart();
        activeChart.getData().clear();
        activeChart.getXAxis().setLabel("Residue Number");
        activeChart.getYAxis().setLabel("PPM");
        Iterator<Color> color = Arrays.stream(COLORS).iterator();
        atomController.PPMSets.stream().
                filter(set -> set.isSelected.getValue())
                .forEach(set -> {
                    DataSeries dataseries = plotShifts(set.iSet, color.next());
                    activeChart.getData().add(dataseries);}
                );
        showPPMPlot();
    }

    private DataSeries plotShifts(int set1, Color color) {
        DataSeries dataseries = new DataSeries();
        atomController.atoms.stream()
                .filter(atom -> atom.getAtomicNumber() == 1 && atom.getPPM(set1) != null)
                .forEach(atom -> {
                    double x = atom.getResidueNumber();
                    double y = atom.getPPM(set1).getValue();
                    XYValue xyValue1 = new XYValue(x, y);
                    dataseries.add(xyValue1);
                });
        dataseries.drawSymbol(true);
        dataseries.setFill(color);
        return dataseries;
    }
}
