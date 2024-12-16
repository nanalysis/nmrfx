/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.analyst.gui.tools;

import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.nmrfx.chart.*;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.structure.tools.LACSCalculator;
import org.nmrfx.utilities.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * @author brucejohnson
 */
public class LACSPlotGui {

    private static final Logger log = LoggerFactory.getLogger(LACSPlotGui.class);
    Stage stage = null;
    XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 800, 800);

    TextField resultsField;

    public void showMCplot() {
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("LACS Analysis");
            ToolBar toolBar = new ToolBar();
            MenuButton fileMenu = new MenuButton("File");

            MenuItem exportSVGMenuItem = new MenuItem("Export SVG...");
            fileMenu.getItems().add(exportSVGMenuItem);
            exportSVGMenuItem.setOnAction(this::exportAsSVG);

            MenuItem exportDataMenuItem = new MenuItem("Export Data...");
            fileMenu.getItems().add(exportDataMenuItem);
            exportDataMenuItem.setOnAction(e -> exportData());

            Button fitButton = new Button("Fit");
            toolBar.getItems().addAll(fileMenu, fitButton);

            fitButton.setOnAction(e -> updateMCplot());

            VBox vBox = new VBox();
            vBox.setMinWidth(750);
            vBox.getChildren().addAll(toolBar);
            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            borderPane.setTop(vBox);
            borderPane.setCenter(chartPane);
            resultsField = new TextField();
            borderPane.setBottom(resultsField);
            stage.setScene(stageScene);
        }
        stage.show();
        stage.toFront();
        updateMCplot();
    }

    List<DataSeries> getLines(LACSCalculator.LACSResult lacsResult) {
        DataSeries series = new DataSeries();
        double x0 = 0.0;
        double y0 = lacsResult.allMedian();
        double xP = lacsResult.xMax();
        double yP = lacsResult.xMax() * lacsResult.pSlope() + lacsResult.allMedian();
        double xN = lacsResult.xMin();
        double yN = lacsResult.xMin() * lacsResult.nSlope() + lacsResult.allMedian();
        XYValue xyValue00 = new XYValue(x0, 0.0);
        XYValue xyValue0 = new XYValue(x0, y0);
        XYValue xyValueN = new XYValue(xN, yN);
        XYValue xyValueP = new XYValue(xP, yP);
        series.clear();
        series.add(xyValueN);
        series.add(xyValue0);
        series.add(xyValueP);
        series.drawSymbol(false);
        series.drawLine(true);
        series.setStroke(Color.BLUE);

        DataSeries offsetSeries = new DataSeries();
        offsetSeries.add(xyValue00);
        offsetSeries.add(xyValue0);
        offsetSeries.drawSymbol(false);
        offsetSeries.drawLine(true);
        offsetSeries.setStroke(Color.BLUE);

        return List.of(series, offsetSeries);
    }

    void updateMCplot() {
        Axis xAxis = activeChart.getXAxis();
        Axis yAxis = activeChart.getYAxis();
        activeChart.setShowLegend(false);
        LACSCalculator lacsCalculator = new LACSCalculator();
        var result = lacsCalculator.calculateLACS("CA", 0);
        result.ifPresent(lacsResult -> {
            xAxis.setLabel("Δδ Cα-ΔδCβ");
            yAxis.setLabel("ΔδCα");
            xAxis.setZeroIncluded(true);
            yAxis.setZeroIncluded(true);
            xAxis.setAutoRanging(true);
            yAxis.setAutoRanging(true);
            DataSeries series = new DataSeries();
            activeChart.getData().clear();
            //Prepare XYChart.Series objects by setting data
            series.clear();
            int n = lacsResult.shiftsX().size();
            for (int i = 0; i < n; i++) {
                double x = lacsResult.shiftsX().get(i);
                double y = lacsResult.shiftsY().get(i);
                series.add(new XYValue(x, y));
            }
            activeChart.getData().clear();
            activeChart.getData().add(series);
            activeChart.getData().addAll(getLines(lacsResult));
            activeChart.autoScale(true);
            resultsField.setText(String.format("Offset: %.2f", lacsResult.allMedian()));
        });

    }


    void exportAsSVG(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to SVG");
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            Canvas canvas = activeChart.getCanvas();
            svgGC.create(canvas.getWidth(), canvas.getHeight(), selectedFile.toString());
            exportChart(svgGC);
            svgGC.saveFile();
        }
    }

    protected void exportChart(SVGGraphicsContext svgGC) {
        svgGC.beginPath();
        activeChart.drawChart(svgGC);
    }

    void writeData(FileWriter writer) throws IOException {
        List<DataSeries> data = activeChart.getData();
        if (!data.isEmpty()) {
            DataSeries series = data.get(0);
            List<XYValue> values = series.getData();
            int n = values.size();
            writer.write("#Data " + n + " \n");
            for (int i = 0; i < n; i++) {
                XYValue v0 = values.get(i);
                String outStr = String.format("%.2f %.2f%n", v0.getXValue(), v0.getYValue());
                writer.write(outStr);
            }
        }
    }


    void exportData() {
        Util.exportData(f -> {
            try {
                writeData(f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
