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
package org.nmrfx.analyst.gui;

import javafx.beans.Observable;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.math3.optim.PointValuePair;
import org.controlsfx.control.CheckComboBox;
import org.nmrfx.analyst.gui.tools.ScanTable;
import org.nmrfx.chart.*;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.math.ReactionFit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author brucejohnson
 */
public class TablePlotGUI {

    Stage stage = null;
    TableView<FileTableItem> tableView;
    XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 500, 500);
    XYChartPane chartPane;
    ChoiceBox<String> chartTypeChoice = new ChoiceBox<>();
    ChoiceBox<String> xArrayChoice = new ChoiceBox<>();
    CheckComboBox<String> yArrayChoice = new CheckComboBox<>();
    Map<String, String> nameMap = new HashMap<>();
    List<DataSeries>  fitLineSeries = new ArrayList<>();

    /**
     * Creates a TablePlotGUI instance
     *
     * @param tableView the TableView object that will be used to get data to
     * plot
     */
    public TablePlotGUI(TableView<FileTableItem> tableView) {
        this.tableView = tableView;
    }

    /**
     * Creates and populates a new stage for the TablePlotGUI, if not already
     * created. The stage is displayed and raised to front.
     */
    public void showPlotStage() {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Table Plotter");
            Label typelabel = new Label("  Type:  ");
            Label xlabel = new Label("  X Var:  ");
            Label ylabel = new Label("  Y Var:  ");
            chartTypeChoice.setMinWidth(150);
            chartTypeChoice.getItems().addAll("ScatterPlot", "BoxPlot");
            chartTypeChoice.setValue("ScatterPlot");
            xArrayChoice.getItems().clear();
            yArrayChoice.getItems().clear();
            xArrayChoice.setMinWidth(150);
            yArrayChoice.setMinWidth(150);
            try {
                chartTypeChoice.valueProperty().addListener((Observable x) -> {
                    updateChartType();
                    updateAxisChoices();
                });

                xArrayChoice.valueProperty().addListener((Observable x) -> updatePlot());
                yArrayChoice.getCheckModel().getCheckedItems().addListener((Observable y) -> updatePlot());
            } catch (NullPointerException npEmc1) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error: Fit must first be performed.");
                alert.showAndWait();
                return;
            }
            tableView.getColumns().
                    addListener((ListChangeListener) (c -> updateAxisChoices()));

            ToolBar toolBar = new ToolBar();
            MenuButton fileMenu = new MenuButton("File");

            MenuItem exportSVGMenuItem = new MenuItem("Export SVG...");
            fileMenu.getItems().add(exportSVGMenuItem);
            exportSVGMenuItem.setOnAction(e -> exportSVGAction());

            Button button = new Button("Fit");
            button.setOnAction(e -> analyze());
            toolBar.getItems().addAll(fileMenu, button);
            HBox hBox = new HBox();
            HBox.setHgrow(hBox, Priority.ALWAYS);
            hBox.setMinWidth(600);
            hBox.getChildren().addAll(typelabel, chartTypeChoice,
                    xlabel, xArrayChoice, ylabel, yArrayChoice);
            hBox.setAlignment(Pos.CENTER);

            VBox vBox = new VBox();
            vBox.setMinWidth(600);
            vBox.getChildren().addAll(toolBar, hBox);
            chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            borderPane.setTop(vBox);
            borderPane.setCenter(chartPane);
            stage.setScene(stageScene);
        }
        updateAxisChoices();
        stage.show();
        stage.toFront();
        updatePlot();
    }

    private Map<Integer, List<FileTableItem>> getGroups() {
        var groups = new HashMap<Integer, List<FileTableItem>>();
        List<FileTableItem> items = tableView.getItems();
        for (FileTableItem item : items) {
            if (!groups.containsKey(item.getGroup())) {
                groups.put(item.getGroup(), new ArrayList<>());
            }
            var itemList = groups.get(item.getGroup());
            itemList.add(item);
        }
        return groups;
    }

    private void updateChartType() {
        if (chartTypeChoice.getValue().equals("ScatterPlot")) {
            activeChart = chartPane.getXYChart();
        } else {
            activeChart = chartPane.getBoxChart();
        }

    }


    void updatePlotWithFitLines() {
        updateScatterPlot();
        for (var series:fitLineSeries) {
            activeChart.getData().add(series);
        }
    }

    private void updatePlot() {
        switch (chartTypeChoice.getValue()) {
            case "ScatterPlot" -> updateScatterPlot();
            case "BoxPlot" -> updateBoxPlot();
            default -> {
            }
        }

    }

    private void updateBoxPlot() {
        if (tableView != null) {
            Axis xAxis = activeChart.getXAxis();
            Axis yAxis = activeChart.getYAxis();
            String xElem = xArrayChoice.getValue();
            List<String> yElems = yArrayChoice.getCheckModel().getCheckedItems();
            activeChart.setShowLegend(false);

            if ((xElem != null) && !yElems.isEmpty()) {
                String yElem = yElems.get(0);
                xAxis.setLabel(xElem);
                yAxis.setLabel(yElem);
                xAxis.setZeroIncluded(true);
                yAxis.setZeroIncluded(false);
                xAxis.setAutoRanging(true);
                yAxis.setAutoRanging(true);
                activeChart.getData().clear();
                //Prepare XYChart.Series objects by setting data
                var groups = getGroups();
                int iValue = 0;
                for (var groupEntry : groups.entrySet()) {
                    int groupNum = groupEntry.getKey();
                    var items = groupEntry.getValue();
                    DataSeries series = new DataSeries();
                    series.setFill(ScanTable.getGroupColor(groupNum));

                    series.clear();
                    double[] ddata = new double[items.size()];
                    int i = 0;
                    for (FileTableItem item : items) {
                        double y = item.getDouble(nameMap.get(yElem));
                        ddata[i++] = y;
                    }
                    BoxPlotData fiveNum = new BoxPlotData(ddata);
                    XYValue xy = new XYValue(iValue + 1.0, 0.0);
                    xy.setExtraValue(fiveNum);
                    series.add(xy);
                    iValue++;

                    activeChart.getData().add(series);
                    activeChart.autoScale(true);
                }
            }
        }
    }

    private void updateScatterPlot() {
        if (tableView != null) {
            Axis xAxis = activeChart.getXAxis();
            Axis yAxis = activeChart.getYAxis();
            String xElem = xArrayChoice.getValue();
            List<String> yElems = yArrayChoice.getCheckModel().getCheckedItems();
            activeChart.setShowLegend(false);

            if ((xElem != null) && !yElems.isEmpty()) {
                if (yElems.size() == 1) {
                    xAxis.setLabel(xElem);
                    String yElem = yElems.get(0);
                    yAxis.setLabel(yElem);
                    xAxis.setZeroIncluded(true);
                    yAxis.setZeroIncluded(true);
                    xAxis.setAutoRanging(true);
                    yAxis.setAutoRanging(true);
                    activeChart.getData().clear();
                    //Prepare XYChart.Series objects by setting data
                    var groups = getGroups();
                    for (var groupEntry : groups.entrySet()) {
                        int groupNum = groupEntry.getKey();
                        var items = groupEntry.getValue();
                        DataSeries series = new DataSeries();
                        series.clear();
                        for (FileTableItem item : items) {
                            double x = item.getDouble(nameMap.get(xElem));
                            double y = item.getDouble(nameMap.get(yElem));
                            series.add(new XYValue(x, y));
                            series.setFill(ScanTable.getGroupColor(groupNum));
                        }
                        activeChart.getData().add(series);
                        activeChart.autoScale(true);
                    }
                } else {
                    xAxis.setLabel(xElem);
                    yAxis.setLabel("Intensity");
                    xAxis.setZeroIncluded(true);
                    yAxis.setZeroIncluded(true);
                    xAxis.setAutoRanging(true);
                    yAxis.setAutoRanging(true);
                    activeChart.getData().clear();
                    //Prepare XYChart.Series objects by setting data
                    int groupNum = 0;

                    for (var yElem : yElems) {
                        DataSeries series = new DataSeries();
                        series.setName(yElem);
                        series.clear();
                        for (FileTableItem item : tableView.getItems()) {
                            double x = item.getDouble(nameMap.get(xElem));
                            double y = item.getDouble(nameMap.get(yElem));
                            series.add(new XYValue(x, y));
                        }
                        series.setFill(ScanTable.getGroupColor(groupNum));
                        groupNum++;
                        activeChart.getData().add(series);
                        activeChart.autoScale(true);
                    }
                    activeChart.setShowLegend(true);
                }
            }
        }
    }

    private void updateAxisChoices() {
        xArrayChoice.getItems().clear();
        yArrayChoice.getItems().clear();
        if (tableView != null) {
            var columns = tableView.getColumns();
            for (var column : columns) {
                String text = column.getText();
                if (text.equals("path") || text.equals("sequence") || text.equals("ndim")) {
                    continue;
                }
                String name = text;
                if (text.contains(":")) {
                    name = text.substring(0, text.indexOf(":"));
                }
                xArrayChoice.getItems().add(name);
                yArrayChoice.getItems().add(name);
                nameMap.put(name, text);
            }
            if (!xArrayChoice.getItems().isEmpty()) {
                xArrayChoice.setValue(xArrayChoice.getItems().get(0));
            }
        }
    }

    private void exportSVGAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to SVG");
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            Canvas canvas = activeChart.getCanvas();
            svgGC.create(true, canvas.getWidth(), canvas.getHeight(), selectedFile.toString());
            exportChart(svgGC);
            svgGC.saveFile();
        }
    }

    private void exportChart(SVGGraphicsContext svgGC) {
        svgGC.beginPath();
        activeChart.drawChart(svgGC);
    }

    void analyze() {
        ReactionFit reactionFit = new ReactionFit();
        String xElem = xArrayChoice.getValue();
        List<String> yElems = yArrayChoice.getCheckModel().getCheckedItems();

        if ((xElem != null) && !yElems.isEmpty()) {
            List<FileTableItem> items = tableView.getItems();
            double[][] xValues = new double[1][items.size()];
            double[][] yValues = new double[yElems.size()][items.size()];
            double[][] errValues = new double[2][items.size()];
            int i = 0;
            double maxX = 0.0;
            for (FileTableItem item : items) {
                double x = item.getDouble(nameMap.get(xElem));
                xValues[0][i] = x;
                maxX = Math.max(xValues[0][i], maxX);
                int j=0;
                for (var yElem : yElems) {
                    yValues[j][i] = item.getDouble(nameMap.get(yElem));
                    errValues[j][i] = 1.0;
                    j++;
                }
                i++;
            }
            reactionFit.setXYE(xValues, yValues, errValues);
            PointValuePair result = reactionFit.fit();
            double[] errs = reactionFit.getParErrs();
            double[] values = result.getPoint();
            for (int j=0;j<values.length;j++) {
                System.out.println(values[j] + " " + errs[j]);
            }
//            StringBuilder sBuilder = new StringBuilder();
//            double r1 = reactionFit.getR1(values);
//            double e1 = r1 / values[1] * errs[1];
//            sBuilder.append(String.format("%3s %.1f +/- %.1f    ", "Ra", r1, e1));
//            sBuilder.append(String.format("%3s %.1f +/- %.1f    ", "Rb", values[1], errs[1]));
//            sBuilder.append(String.format("%3s %.1f +/- %.1f ns", "tau", values[3], errs[3]));
//            resultsField.setText(sBuilder.toString());
//
            double[] first = {0.0};
            double[] last = {maxX};
            double[][] curve0 = reactionFit.getSimValues(first, last, 200);
            fitLineSeries.clear();
            for (int iSeries = 0; iSeries <yValues.length;iSeries++) {
                DataSeries series = new DataSeries();
                fitLineSeries.add(series);
                for (int j = 0; j < curve0[0].length; j++) {
                    series.add(new XYValue(curve0[0][j], curve0[xValues.length+iSeries][j]));
                }
                series.setFill(ScanTable.getGroupColor(iSeries));
                series.drawLine(true);
                series.drawSymbol(false);
                series.fillSymbol(false);
                series.setName(yElems.get(iSeries)+":Fit");
            }
            updatePlotWithFitLines();
        }

    }
}
