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
import org.nmrfx.analyst.gui.tools.fittools.DiffusionFitGUI;
import org.nmrfx.analyst.gui.tools.fittools.FitGUI;
import org.nmrfx.chart.*;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.processor.optimization.FitEquation;
import org.nmrfx.processor.optimization.FitExp;
import org.nmrfx.processor.optimization.FitReactionAB;
import org.nmrfx.processor.optimization.Gaussian;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.TableItem;

import java.io.File;
import java.util.*;

/**
 * @author brucejohnson
 */
public class TablePlotGUI {

    Stage stage = null;
    protected TableView<TableItem> tableView;
    protected XYCanvasChart activeChart;
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
    VBox extraBox = new VBox();

    FitGUI fitGUI = null;

    CheckBox fitSimCheckBox = new CheckBox("SimFit");
    VBox fitVBox;
    boolean showGroup = false;

    public enum ExtraMode {
        DIFFUSION
    }

    ExtraMode extraMode;
    boolean fitMode;
    List<String> chartTypes = Arrays.asList("ScatterPlot", "BoxPlot");
    protected List<String> skipColumns = Arrays.asList("path", "sequence", "ndim");

    /**
     * Creates a TablePlotGUI instance
     *
     * @param tableView the TableView object that will be used to get data to
     *                  plot
     */
    public TablePlotGUI(TableView<? extends TableItem> tableView, ExtraMode extraMode, boolean fitMode) {
        this.tableView = (TableView<TableItem>) tableView;
        this.extraMode = extraMode;
        this.fitMode = fitMode;
    }

    public record ParItem(String columnName, int group, String parName, double value, double error) {
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
            chartTypeChoice.getItems().addAll(chartTypes);
            chartTypeChoice.setValue(chartTypes.getFirst());
            xArrayChoice.getItems().clear();
            yArrayChoice.getItems().clear();
            xArrayChoice.setMinWidth(100);
            yArrayChoice.setMinWidth(150);
            try {
                chartTypeChoice.valueProperty().addListener(x -> {
                    updateChartType();
                    updateAxisChoices();
                });

                xArrayChoice.valueProperty().addListener(x -> updatePlot());
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
            toolBar.getItems().addAll(fileMenu, typelabel, chartTypeChoice,
                    xlabel, xArrayChoice, ylabel, yArrayChoice);
            if (fitMode) {
                CheckBox showFitCheckBox = new CheckBox("Fitting");
                showFitCheckBox.setSelected(false);
                showFitCheckBox.setOnAction(e -> toggleFitPane(showFitCheckBox));

                ToolBar toolBar2 = new ToolBar();
                Button button = new Button("Fit");
                button.setOnAction(e -> analyze());
                equationChoice.getItems().addAll("ExpAB", "ExpABC", "GaussianAB", "GaussianABC", "A<->B");
                equationChoice.setValue("ExpABC");
                toolBar2.getItems().addAll(equationChoice, button, fitSimCheckBox);
                ToolBarUtils.addFiller(toolBar, 10, 200);
                toolBar.getItems().add(showFitCheckBox);

                fitVBox = new VBox();
                fitVBox.setMinWidth(200);
                fitVBox.getChildren().addAll(toolBar2, extraBox, parTable);
                setupTable();
                if (extraMode == ExtraMode.DIFFUSION) {
                    fitGUI = new DiffusionFitGUI();
                    fitGUI.setupGridPane(extraBox);
                    equationChoice.setValue("GaussianAB");
                    showFitCheckBox.setSelected(true);
                    toggleFitPane(showFitCheckBox);
                }
            }
            chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            borderPane.setTop(toolBar);
            borderPane.setCenter(chartPane);
            borderPane.setRight(null);
            stage.setScene(stageScene);
        }
        updateAxisChoices();
        if (extraMode == ExtraMode.DIFFUSION) {
            fitGUI.setXYChoices(tableView, xArrayChoice, yArrayChoice);
        }

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

    protected Map<Integer, List<TableItem>> getGroups() {
        var groups = new HashMap<Integer, List<TableItem>>();
        List<TableItem> items = tableView.getItems();
        for (TableItem item : items) {
            if (item.getActive()) {
                if (!groups.containsKey(item.getGroup())) {
                    groups.put(item.getGroup(), new ArrayList<>());
                }
                var itemList = groups.get(item.getGroup());
                itemList.add(item);
            }
        }
        return groups;
    }

    private void updateChartType() {
        switch (chartTypeChoice.getValue()) {
            case "ScatterPlot" -> activeChart = chartPane.getXYChart();
            case "BoxPlot" -> activeChart = chartPane.getBoxChart();
            case "BarChart" -> activeChart = chartPane.getBarChart();
        }
        chartPane.updateChart();
    }

    int nActive(List<TableItem> items) {
       return (int) items.stream().filter(TableItem::getActive).count();
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
            case "BarChart" -> updateBarChart();
            default -> {}
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
                        int nItems = nActive(items);
                        double[] ddata = new double[nItems];
                        int i = 0;
                        for (TableItem item : items) {
                            if (item.getActive()) {
                                double y = item.getDouble(nameMap.get(yElem));
                                ddata[i++] = y;
                            }
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

    protected void setYAxisLabel(String label) {
        activeChart.getYAxis().setLabel(label);
    }

    protected void setXAxisLabel(String label) {
        activeChart.getXAxis().setLabel(label);
    }

    protected DataSeries getScatterPlotData(List<TableItem> items, String yElem) {
        DataSeries series = new DataSeries();
        series.clear();
        String xElem = getXElem();
        for (TableItem item : items) {
            if (item.getActive()) {
                double x = item.getDouble(nameMap.get(xElem));
                double y = item.getDouble(nameMap.get(yElem));
                series.add(new XYValue(x, y));
            }
        }
        return series;
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
                    String yElem = yElems.getFirst();
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
                            DataSeries series = getScatterPlotData(items, yElem);
                            series.setFill(ScanTable.getGroupColor(groupNum));
                            series.setStroke(ScanTable.getGroupColor(groupNum));
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
                            DataSeries series = getScatterPlotData(tableView.getItems(), yElem);
                            series.setName(yElem);
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
            if (skipColumns.stream().anyMatch(text::equals)) {
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
            items.remove(null);
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
                    xArrayChoice.setValue(xArrayChoice.getItems().getFirst());
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
        if (nY == 0) {
            if (fitSimCheckBox.isSelected()) {
                nY = yElems.size();
            } else {
                nY = 1;
            }
        }
        fitEquation.nY(nY);

        if ((nY == 2) && (nY != yElems.size())) {
            GUIUtils.warn("Fit Y values", "Need " + nY + " columns");
            return;
        }
        fitLineSeries.clear();
        ObservableList<ParItem> allResults = FXCollections.observableArrayList();
        if ((xElem != null) && !yElems.isEmpty()) {
            int iGroup = 0;
            if (nY > 1) {
                allResults.addAll(fit(xElem, yElems, fitEquation, nY, iGroup));
            } else {
                for (var yElem : yElems) {
                    List<String> subYElem = new ArrayList<>(Collections.singleton(yElem));
                    allResults.addAll(fit(xElem, subYElem, fitEquation, nY, iGroup++));
                }
            }
            if (fitGUI != null) {
                List<TablePlotGUI.ParItem> newItems = fitGUI.addDerivedPars(allResults);
                allResults.addAll(newItems);
            }
            updatePlotWithFitLines();
            parTable.getItems().setAll(allResults);
        }
    }

    private List<ParItem> fit(String xElem, List<String> yElems, FitEquation fitEquation, int nY, int iGroup) {
        List<TableItem> items = tableView.getItems();
        int nItems = nActive(items);
        double[][] xValues = new double[1][nItems];
        double[][] yValues = new double[nY][nItems];
        double[][] errValues = new double[nY][nItems];
        int i = 0;
        double maxX = 0.0;
        for (TableItem item : items) {
            if (item.getActive()) {
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
        }
        fitEquation.setXYE(xValues, yValues, errValues);
        PointValuePair result = fitEquation.fit();
        if (result == null) {
            GUIUtils.warn("Fitting", "Error fitting data");
            return Collections.emptyList();
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
            ParItem parItem = new ParItem(yElems.getFirst(), 0, parNames[j], value, errValue);
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

    protected void setChartTypeChoice(List<String> chartTypes) {
        this.chartTypes = chartTypes;
    }

    protected Map<String, String> getNameMap() {
        return nameMap;
    }

    protected String getXElem() {
        return xArrayChoice.getValue();
    }

    protected List<String> getYElem() {
        return yArrayChoice.getCheckModel().getCheckedItems();
    }

    protected DataSeries getBarChartData(List<TableItem> items, String yElem) {
        return null;
    }

    private void updateBarChart() {
        if (tableView != null) {
            Axis xAxis = activeChart.getXAxis();
            Axis yAxis = activeChart.getYAxis();
            String xElem = xArrayChoice.getValue();
            List<String> yElems = yArrayChoice.getCheckModel().getCheckedItems();
            activeChart.setShowLegend(false);

            if ((xElem != null) && !yElems.isEmpty()) {
                if (yElems.size() == 1) {
                    String yElem = yElems.getFirst();
                    if (yElem != null) {
                        xAxis.setZeroIncluded(true);
                        yAxis.setZeroIncluded(true);
                        xAxis.setAutoRanging(true);
                        yAxis.setAutoRanging(true);
                        activeChart.getData().clear();
                        //Prepare XYChart.Series objects by setting data
                        var groups = getGroups();
                        for (var groupEntry : groups.entrySet()) {
                            DataSeries series = getBarChartData(groupEntry.getValue(), yElem);
                            activeChart.getData().add(series);
                            activeChart.autoScale(true);
                        }
                    }
                } else {
                    xAxis.setZeroIncluded(true);
                    yAxis.setZeroIncluded(true);
                    xAxis.setAutoRanging(true);
                    yAxis.setAutoRanging(true);
                    activeChart.getData().clear();
                    int groupNum = 0;

                    for (var yElem : yElems) {
                        if (yElem != null) {
                            DataSeries series = getBarChartData(tableView.getItems(), yElem);
                            series.setName(yElem);
                            series.setFill(ScanTable.getGroupColor(groupNum));
                            series.setStroke(ScanTable.getGroupColor(groupNum));
                            groupNum++;
                            activeChart.getData().add(series);
                            activeChart.autoScale(true);
                        }
                    }
                }
            }
        }
    }
}