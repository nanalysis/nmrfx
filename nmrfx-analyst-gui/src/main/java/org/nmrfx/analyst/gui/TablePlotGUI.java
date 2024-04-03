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
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.math3.optim.PointValuePair;
import org.controlsfx.control.CheckComboBox;
import org.nmrfx.analyst.gui.tools.ScanTable;
import org.nmrfx.chart.*;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.processor.optimization.FitEquation;
import org.nmrfx.processor.optimization.FitExp;
import org.nmrfx.processor.optimization.FitReactionAB;
import org.nmrfx.processor.optimization.Gaussian;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.util.*;

/**
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
    List<DataSeries> fitLineSeries = new ArrayList<>();
    ChoiceBox<String> equationChoice = new ChoiceBox<>();
    TableView<ParItem> parTable = new TableView<>();
    VBox fitVBox;
    boolean showGroup = false;

    /**
     * Creates a TablePlotGUI instance
     *
     * @param tableView the TableView object that will be used to get data to
     *                  plot
     */
    public TablePlotGUI(TableView<FileTableItem> tableView) {
        this.tableView = tableView;
    }

    record ParItem(String columnName, int group, String parName, double value, double error) {
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
            stage.setWidth(750);
            Label typelabel = new Label("  Type:  ");
            Label xlabel = new Label("  X Var:  ");
            Label ylabel = new Label("  Y Var:  ");
            chartTypeChoice.setMinWidth(100);
            chartTypeChoice.getItems().addAll("ScatterPlot", "BoxPlot");
            chartTypeChoice.setValue("ScatterPlot");
            xArrayChoice.getItems().clear();
            yArrayChoice.getItems().clear();
            xArrayChoice.setMinWidth(100);
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
            CheckBox showFitCheckBox = new CheckBox("Fitting");
            showFitCheckBox.setSelected(false);
            showFitCheckBox.setOnAction(e -> toggleFitPane(showFitCheckBox));

            ToolBar toolBar2 = new ToolBar();

            Button button = new Button("Fit");
            button.setOnAction(e -> analyze());
            equationChoice.getItems().addAll("ExpAB", "ExpABC", "GaussianAB", "GaussianABC", "A<->B");
            equationChoice.setValue("ExpABC");
            toolBar2.getItems().addAll(equationChoice, button);
            toolBar.getItems().addAll(fileMenu, typelabel, chartTypeChoice,
                    xlabel, xArrayChoice, ylabel, yArrayChoice);
            ToolBarUtils.addFiller(toolBar, 10, 200);
            toolBar.getItems().add(showFitCheckBox);

            fitVBox = new VBox();
            fitVBox.setMinWidth(200);
            fitVBox.getChildren().addAll(toolBar2, parTable);

            chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            borderPane.setTop(toolBar);
            borderPane.setCenter(chartPane);
            borderPane.setRight(null);
            stage.setScene(stageScene);
            setupTable();
        }
        updateAxisChoices();
        stage.show();
        stage.toFront();
        updatePlot();
    }

    private void toggleFitPane(CheckBox showFitBox) {
        if (showFitBox.isSelected()) {
            borderPane.setRight(fitVBox);
        } else {
            borderPane.setRight(null);
        }

    }

    void setupTable() {
        TableColumn<ParItem, String> columnNameColumn = new TableColumn<>("Column");
        columnNameColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().columnName()));
        TableColumn<ParItem, Integer> groupColumn = null;
        if (showGroup) {
            groupColumn = new TableColumn<>("Group");
            groupColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().group()));
        }

        TableColumn<ParItem, String> parNameColumn = new TableColumn<>("Parameter");
        parNameColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().parName()));
        TableColumn<ParItem, Double> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().value()));
        TableColumn<ParItem, Double> errorColumn = new TableColumn<>("Error");
        errorColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().error()));
        parTable.getColumns().add(columnNameColumn);
        if (showGroup) {
            parTable.getColumns().add(groupColumn);
        }
        parTable.getColumns().addAll(parNameColumn, valueColumn, errorColumn);
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
        chartPane.updateChart();
    }


    void updatePlotWithFitLines() {
        updateScatterPlot();
        for (var series : fitLineSeries) {
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
                Optional<String> yElemOpt = yElems.stream().filter(e -> nameMap.containsKey(e)).findFirst();
                yElemOpt.ifPresent(yElem -> {
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
                });
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
                    if (yElem != null) {
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
                                series.setStroke(ScanTable.getGroupColor(groupNum));
                            }
                            activeChart.getData().add(series);
                            activeChart.autoScale(true);
                        }
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
                        if (yElem != null) {
                            DataSeries series = new DataSeries();
                            series.setName(yElem);
                            series.clear();
                            for (FileTableItem item : tableView.getItems()) {
                                double x = item.getDouble(nameMap.get(xElem));
                                double y = item.getDouble(nameMap.get(yElem));
                                series.add(new XYValue(x, y));
                            }
                            series.setFill(ScanTable.getGroupColor(groupNum));
                            series.setStroke(ScanTable.getGroupColor(groupNum));
                            groupNum++;
                            activeChart.getData().add(series);
                            activeChart.autoScale(true);
                        }
                    }
                    activeChart.setShowLegend(true);
                }
            }
            activeChart.drawChart();
        }
    }

    List<String> usableColumns() {
        List<String> columnNames = new ArrayList<>();
        nameMap.clear();
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
            nameMap.put(name, text);
            columnNames.add(name);
        }
        return columnNames;
    }

    private void updateAxisChoices() {
        List<String> currentChecks = new ArrayList<>(yArrayChoice.getCheckModel().getCheckedItems());
        if (tableView != null) {
            var items = yArrayChoice.getItems();
            List<String> columnNames = usableColumns();
            var iterator = items.listIterator();
            while (iterator.hasNext()) {
                String name = iterator.next();
                if (!columnNames.contains(name)) {
                    yArrayChoice.getCheckModel().clearCheck(name);
                    iterator.remove();
                    currentChecks.remove(name);
                }
            }
            if (items.contains(null)) {
                items.remove(null);
            }
            for (String name : columnNames) {
                if (!items.contains(name)) {
                    items.add(name);
                }
            }
            String currentX = xArrayChoice.getValue();
            xArrayChoice.getItems().clear();
            xArrayChoice.getItems().addAll(columnNames);
            if ((currentX != null) && columnNames.contains(currentX)) {
                xArrayChoice.setValue(currentX);
            } else {
                if (!xArrayChoice.getItems().isEmpty()) {
                    xArrayChoice.setValue(xArrayChoice.getItems().get(0));
                }
            }
            for (var item : currentChecks) {
                yArrayChoice.getCheckModel().check(item);
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
            svgGC.create(canvas.getWidth(), canvas.getHeight(), selectedFile.toString());
            exportChart(svgGC);
            svgGC.saveFile();
        }
    }

    private void exportChart(SVGGraphicsContext svgGC) {
        svgGC.beginPath();
        activeChart.drawChart(svgGC);
    }

    void analyze() {
        String xElem = xArrayChoice.getValue();
        List<String> yElems = yArrayChoice.getCheckModel().getCheckedItems();
        FitEquation fitEquation = switch (equationChoice.getValue()) {
            case "ExpAB" -> new FitExp(false);
            case "ExpABC" -> new FitExp(true);
            case "GaussianAB" -> new Gaussian(false);
            case "GaussianABC" -> new Gaussian(true);
            case "A<->B" -> new FitReactionAB();
            default -> null;
        };
        if (fitEquation == null) {
            return;
        }
        int nY = fitEquation.nY();
        if ((nY == 2) && (nY != yElems.size())) {
            GUIUtils.warn("Fit Y values", "Need " + nY + " columns");
            return;
        }
        fitLineSeries.clear();
        ObservableList<ParItem> allResults = FXCollections.observableArrayList();
        if ((xElem != null) && !yElems.isEmpty()) {
            int iGroup = 0;
            if (nY == 2) {
                allResults.addAll(fit(xElem, yElems, fitEquation, nY, iGroup));
            } else {
                for (var yElem : yElems) {
                    List<String> subYElem = new ArrayList<>(Collections.singleton(yElem));
                    allResults.addAll(fit(xElem, subYElem, fitEquation, nY, iGroup++));
                }
            }
            updatePlotWithFitLines();
            parTable.getItems().setAll(allResults);
        }
    }

    private List<ParItem> fit(String xElem, List<String> yElems, FitEquation fitEquation, int nY, int iGroup) {
        List<FileTableItem> items = tableView.getItems();
        double[][] xValues = new double[1][items.size()];
        double[][] yValues = new double[nY][items.size()];
        double[][] errValues = new double[2][items.size()];
        int i = 0;
        double maxX = 0.0;
        for (FileTableItem item : items) {
            double x = item.getDouble(nameMap.get(xElem));
            xValues[0][i] = x;
            maxX = Math.max(xValues[0][i], maxX);
            int j = 0;
            for (var yElem : yElems) {
                yValues[j][i] = item.getDouble(nameMap.get(yElem));
                errValues[j][i] = 1.0;
                j++;
            }
            i++;
        }
        fitEquation.setXYE(xValues, yValues, errValues);
        PointValuePair result = fitEquation.fit();
        if (result == null) {
            GUIUtils.warn("Fitting", "Error fitting data");
            return Collections.EMPTY_LIST;
        }
        double[] errs = fitEquation.getParErrs();
        String[] parNames = fitEquation.parNames();
        double[] values = result.getPoint();
        List<ParItem> results = new ArrayList<>();
        for (int j = 0; j < values.length; j++) {
            double errValue = errs[j];
            int nSig = (int) Math.floor(Math.log10(errValue)) - 1;
            nSig = -nSig;
            if (nSig < 0) {
                nSig = 0;
            }
            double scale = Math.pow(10, nSig);
            errValue = Math.round(errValue * scale) / scale;
            double value = Math.round(values[j] * scale) / scale;
            ParItem parItem = new ParItem(yElems.get(0), 0, parNames[j], value, errValue);
            results.add(parItem);
        }
        double[] first = {0.0};
        double[] last = {maxX};
        double[][] curve0 = fitEquation.getSimValues(first, last, 200);
        for (int iSeries = 0; iSeries < yValues.length; iSeries++) {
            DataSeries series = new DataSeries();
            fitLineSeries.add(series);
            for (int j = 0; j < curve0[0].length; j++) {
                series.add(new XYValue(curve0[0][j], curve0[xValues.length + iSeries][j]));
            }
            series.setStroke(ScanTable.getGroupColor(iGroup));
            series.setFill(ScanTable.getGroupColor(iGroup));
            series.drawLine(true);
            series.drawSymbol(false);
            series.fillSymbol(false);
            series.setName(yElems.get(iSeries) + ":Fit");
        }
        return results;
    }
}