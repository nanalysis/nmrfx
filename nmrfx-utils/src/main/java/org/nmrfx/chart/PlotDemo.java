/*
 * Copyright (C) 2016 Bruce Johnson
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
package org.nmrfx.chart;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A chart that displays rectangular bars with heights indicating data values
 * for categories. Used for displaying information when at least one axis has
 * discontinuous or discrete data.
 */
public class PlotDemo {
    private static final Logger log = LoggerFactory.getLogger(PlotDemo.class);
    static List<String> header;
    static List<String> classes;
    static Map<String, Integer> classMap = new HashMap<>();
    static double[][] iris = null;
    Stage stage;
    Canvas canvas;
    XYCanvasChart chart;
    GraphicsContextProxy gcP;
    Pane pane;

    public PlotDemo(Stage stage) {
        this.stage = stage;
    }

    public void showCanvasNow() {
        BorderPane borderPane = new BorderPane();
        pane = new Pane();
        borderPane.setCenter(pane);
        Button linePlotButton = new Button("Line Plot");
        linePlotButton.setOnAction(e -> addLinePlot());
        Button scatterPlotButton = new Button("Scatter Plot");
        scatterPlotButton.setOnAction(e -> addScatterPlot());
        Button addPlotGriddButton = new Button("Plot Grid");
        addPlotGriddButton.setOnAction(e -> addPlotGrid());
        Button barPlotButton = new Button("Bar Plot");
        barPlotButton.setOnAction(e -> addBarPlot());
        Button boxPlotButton = new Button("Box Plot");
        boxPlotButton.setOnAction(e -> addBoxPlot());
        VBox vBox = new VBox();
        vBox.getChildren().addAll(linePlotButton, scatterPlotButton,
                barPlotButton, boxPlotButton
        );
        borderPane.setLeft(vBox);
        canvas = new Canvas(500, 500);
        pane.getChildren().add(canvas);
        stage.setScene(new Scene(borderPane));
        stage.show();
        gcP = new GraphicsContextProxy(canvas.getGraphicsContext2D());
        borderPane.widthProperty().addListener(e -> refresh());
        borderPane.heightProperty().addListener(e -> refresh());
        refresh();
    }

    public void addLinePlot() {
    }

    public void addScatterPlot() {
        if (loadIRIS() == null) {
            return;
        }
        Map<String, DataSeries> allSeries = new HashMap<>();
        Color[] colors = XYCanvasChart.colors;

        for (int i = 0; i < iris.length; i++) {
            var row = iris[i];
            double x = row[0];
            double y = row[1];
            XYValue xy = new XYValue(x, y);
            String dataClass = classes.get(i);
            DataSeries series;
            if (!allSeries.containsKey(dataClass)) {
                series = new DataSeries();
                series.setName(dataClass);
                int iClass = allSeries.size();
                series.setFill(colors[iClass % colors.length]);
                series.setStroke(colors[iClass % colors.length]);
                allSeries.put(dataClass, series);

            } else {
                series = allSeries.get(dataClass);
            }
            series.add(xy);
        }
        chart = XYCanvasChart.buildChart(canvas);
        chart.getData().addAll(allSeries.values());
        chart.xAxis.setAutoRanging(true);
        chart.yAxis.setAutoRanging(true);
        chart.autoScale(true);
        chart.drawChart();
        refresh();
    }

    public void addBoxPlot() {
        if (loadIRIS() == null) {
            return;
        }
        Map<String, List<Double>> values = new HashMap<>();
        Color[] colors = XYCanvasChart.colors;

        for (int i = 0; i < iris.length; i++) {
            var row = iris[i];
            double x = row[0];
            double y = row[0];
            String dataClass = classes.get(i);
            List<Double> data;
            if (!values.containsKey(dataClass)) {
                data = new ArrayList<>();
                values.put(dataClass, data);
            } else {
                data = values.get(dataClass);
            }
            data.add(y);
        }
        List<DataSeries> allSeries = new ArrayList<>();
        int iValue = 0;
        for (var data : values.entrySet()) {
            DataSeries series = new DataSeries();
            series.setFill(colors[iValue % colors.length]);
            XYValue xy = new XYValue(iValue + 1.0, 0.0);
            double[] ddata = data.getValue().stream().mapToDouble(v -> v.doubleValue()).toArray();
            BoxPlotData fiveNum = new BoxPlotData(ddata);
            xy.setExtraValue(fiveNum);
            series.add(xy);
            allSeries.add(series);
            iValue++;
        }
        chart = XYCanvasBoxChart.buildChart(canvas);
        chart.getData().addAll(allSeries);
        chart.xAxis.setAutoRanging(true);
        chart.yAxis.setAutoRanging(true);
        chart.yAxis.setZeroIncluded(false);
        chart.autoScale(true);
        chart.drawChart();
        refresh();
    }

    public void addBarPlot() {
    }

    public void addPlotGrid() {
    }

    void refresh() {
        canvas.setWidth(pane.getWidth());
        canvas.setHeight(pane.getHeight());
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (chart != null) {
            chart.setWidth(width);
            chart.setHeight(height);
            chart.drawChart();
        }
    }

    public static double[][] loadFile(String fileName, String classHeader) throws IOException {
        Path path = Path.of(fileName);
        var lines = Files.readAllLines(path);
        String sepChar;
        int iLine = -1;
        double[][] result = new double[lines.size() - 1][];
        int classColumn = -1;
        classes = new ArrayList<>();
        for (var line : lines) {
            if (iLine >= 0) {
                String[] fields = line.split(",");
                result[iLine] = new double[fields.length];
                for (int iField = 0; iField < fields.length; iField++) {
                    String field = fields[iField];
                    if (iField == classColumn) {
                        if (!classMap.containsKey(field)) {
                            classMap.put(field, classMap.size());
                        }
                        classes.add(field);
                        int iClass = classMap.get(field);
                        result[iLine][iField] = iClass;
                    } else {
                        result[iLine][iField] = Double.parseDouble(field);
                    }
                }
            } else {
                if (line.contains("\t")) {
                    sepChar = "\t";
                } else {
                    sepChar = ",";
                }
                header = Arrays.stream(line.split(sepChar)).collect(Collectors.toList());
                classColumn = header.indexOf(classHeader);
            }
            iLine++;

        }
        return result;
    }

    public static double[][] loadIRIS() {
        if (iris == null) {
            try {
                iris = loadFile("src/test/data/iris.csv", "species");
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        return iris;
    }
}
