package org.nmrfx.analyst.gui.molecule;

import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.nmrfx.chart.*;
import org.nmrfx.chemistry.Atom;

public class PPMPlotGUI {
    private Stage stage = null;
    private XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 800, 500);
    ObservableList<Atom> atoms = null;

    public void showPPMPlot(ObservableList<Atom> atoms) {
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Plot Atom Shifts");
            XYChartPane chartPane = new XYChartPane();
            chartPane.setBackground(Background.fill(Color.WHITE));
            activeChart = chartPane.getChart();
            borderPane.setCenter(chartPane);
            stage.setScene(stageScene);
        }
        this.atoms = atoms;
        stage.show();
        stage.toFront();
        updatePlot();
    }

    void updatePlot() {
        Axis xAxis = activeChart.getXAxis();
        Axis yAxis = activeChart.getYAxis();

        yAxis.setLabel("");
        xAxis.setLabel("");
        activeChart.getData().clear();

    }

    private DataSeries plotDeltas(int set1, int set2) {
        DataSeries dataseries = new DataSeries();
        for (Atom atom : atoms) {
            double x = atom.getDeltaPPM(set1, set2);
            double y = atom.getResidueNumber();
            XYValue xyValue = new XYValue(x, y);
            dataseries.add(xyValue);
        }
        return dataseries;
    }

    private DataSeries plotShifts(int set1, int set2) {
        DataSeries dataseries = new DataSeries();
        for (Atom atom : atoms) {
            double x1 = atom.getPPM();
            double x2 = atom.getPPM();
            double y = atom.getResidueNumber();
            XYValue xyValue1 = new XYValue(x1, y);
            XYValue xyValue2 = new XYValue(x2, y);
            dataseries.add(xyValue1);
            dataseries.add(xyValue2);
        }
        return dataseries;
    }
}
