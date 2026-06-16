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

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.checkerframework.checker.units.qual.C;
import org.nmrfx.chart.Axis;
import org.nmrfx.chart.DataSeries;
import org.nmrfx.chart.XYCanvasChart;
import org.nmrfx.chart.XYChartPane;
import org.nmrfx.peaks.PeakPath;
import org.nmrfx.peaks.PeakPaths;
import org.nmrfx.utils.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class PathPlotTool {

    private static final Logger log = LoggerFactory.getLogger(PathPlotTool.class);
    PathTool pathTool;
    Stage stage = null;
    XYCanvasChart activeChart = null;
    BorderPane borderPane = new BorderPane();
    ChoiceBox<PeakPaths.BINDINGMODE> bindingmodeChoiceBox;
    TableView<PeakPath> tableView;
    Scene stageScene = new Scene(borderPane, 500, 500);
    List<String> colNames;

    public PathPlotTool(PathTool pathTool, List<String> colNames) {
        this.pathTool = pathTool;
        this.colNames = colNames;
    }

    public void show(String xAxisName, String yAxisName) {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Plot Tool");
            MenuButton fileMenu = new MenuButton("File");
            ToolBar toolBar = new ToolBar();
            MenuItem exportSVGButton = new MenuItem("Export SVG Plot");
            MenuItem exportTableButton = new MenuItem("Export Table");
            fileMenu.getItems().addAll(exportSVGButton, exportTableButton);

            bindingmodeChoiceBox = new ChoiceBox<>();
            bindingmodeChoiceBox.getItems().addAll(PeakPaths.BINDINGMODE.values());
            bindingmodeChoiceBox.setValue(PeakPaths.BINDINGMODE.SINGLE_SITE);
            Button fitButton = new Button("Fit ");
            Button fitGroupButton = new Button("Fit Group");
            toolBar.getItems().addAll(fileMenu, bindingmodeChoiceBox, fitButton, fitGroupButton);

            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();

            exportSVGButton.setOnAction(e -> activeChart.exportSVG());
            exportTableButton.setOnAction(e -> savePathTable());
            fitButton.setOnAction(e -> fitPathsIndividual());
            fitGroupButton.setOnAction(e -> fitPathsGrouped());

            borderPane.setTop(toolBar);
            tableView = new TableView<PeakPath>();
            SplitPane sPane = new SplitPane(chartPane, tableView);
            sPane.setOrientation(Orientation.VERTICAL);
            borderPane.setCenter(sPane);
            stage.setScene(stageScene);
            updateChart(xAxisName, yAxisName);
            initTable();
        }
        stage.show();
        stage.toFront();
    }

    private void fitPathsIndividual() {
        pathTool.fitPathsIndividual(bindingmodeChoiceBox.getValue());
    }

    private void fitPathsGrouped() {
        pathTool.fitPathsGrouped(bindingmodeChoiceBox.getValue());
    }

    public XYCanvasChart getChart() {
        return activeChart;
    }

    public void clear() {
        if (activeChart != null) {
            activeChart.getData().clear();
        }
    }

    void updateChart(String xAxisName, String yAxisName) {
        Axis xAxis = activeChart.getXAxis();
        Axis yAxis = activeChart.getYAxis();
        activeChart.setShowLegend(false);

        xAxis.setLabel(xAxisName);
        yAxis.setLabel(yAxisName);
        xAxis.setZeroIncluded(true);
        yAxis.setZeroIncluded(true);
        xAxis.setAutoRanging(true);
        yAxis.setAutoRanging(true);
        DataSeries series = new DataSeries();
        activeChart.getData().clear();
        //Prepare XYChart.Series objects by setting data
        activeChart.getData().add(series);
        activeChart.autoScale(true);
    }

    public void updateTable(ObservableList<PeakPath> paths) {
        tableView.getItems().setAll(paths);
    }

    void initTable() {
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ListChangeListener selectionListener = (ListChangeListener) (ListChangeListener.Change c) -> {
            selectionChanged();
        };
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
        for (String colName : colNames) {
            if (colName.equals("Peak")) {
                TableColumn<PeakPath, Number> col = new TableColumn<>(colName);
                col.setCellValueFactory(new PropertyValueFactory<>(colName));
                tableView.getColumns().add(col);
            } else if (colName.equals("Atom")) {
                TableColumn<PeakPath, String> atomCol = new TableColumn<>(colName);
                atomCol.setCellValueFactory(new PropertyValueFactory<>(colName));
                tableView.getColumns().add(atomCol);
            } else {
                TableColumn<PeakPath, Number> col = new TableColumn<>(colName);
                col.setCellValueFactory(new Callback<CellDataFeatures<PeakPath, Number>, ObservableValue<Number>>() {
                    public ObservableValue<Number> call(CellDataFeatures<PeakPath, Number> p) {
                        // p.getValue() returns the Path instance for a particular TableView row
                        int iProp = colNames.indexOf(colName);
                        iProp -= 2;  // account for Peak and Atom column
                        boolean isErr = iProp % 2 == 1;
                        iProp /= 2;  // account for Dev columns
                        if (p.getValue().hasPars()) {
                            int nPars = p.getValue().getFitPars().length;
                            int jProp = iProp / 2;
                            int iState = iProp % 2;
                            int nStates = nPars / 2;
                            if (iState >= nStates) {
                                return null;
                            }
                            if (nStates < 2) {
                                iProp /= 2;
                            }
                            double v = isErr ? p.getValue().getErr(iProp) : p.getValue().getPar(iProp);
                            ObservableValue<Number> ov = new SimpleDoubleProperty(v);
                            return ov;
                        } else {
                            return null;
                        }
                    }
                });
                col.setCellFactory(c
                        -> new TableCell<PeakPath, Number>() {
                    @Override
                    public void updateItem(Number value, boolean empty) {
                        super.updateItem(value, empty);
                        if (empty || (value == null)) {
                            setText(null);
                        } else {
                            setText(String.format("%.4f", value.doubleValue()));
                        }
                    }
                });
                tableView.getColumns().add(col);
            }
        }

        tableView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                gotoSelection();
            }
        });
        tableView.setOnKeyPressed(e
                        -> {
                    if ((e.getCode() == KeyCode.BACK_SPACE) || (e.getCode() == KeyCode.DELETE)) {
                        List<PeakPath> selPaths = getSelected();
                        pathTool.removeActivePaths(selPaths);

                    }
                }
        );
    }

    public void clearSelection() {
        tableView.getSelectionModel().clearSelection();
    }

    public void selectRow(PeakPath path) {
        tableView.getSelectionModel().clearSelection();
        tableView.getSelectionModel().select(path);
    }

    List<PeakPath> getSelected() {
        List<PeakPath> paths = new ArrayList<>();
        List<Integer> selected = tableView.getSelectionModel().getSelectedIndices();
        for (Integer index : selected) {
            PeakPath path = tableView.getItems().get(index);
            paths.add(path);
        }
        return paths;
    }

    final protected void selectionChanged() {
        pathTool.clearXYPath();
        List<Integer> selected = tableView.getSelectionModel().getSelectedIndices();
        int iSeries = 0;
        for (Integer index : selected) {
            PeakPath path = tableView.getItems().get(index);
            Color color = XYCanvasChart.colors[iSeries % XYCanvasChart.colors.length];
            pathTool.showXYPath(path, iSeries, selected.size());
            iSeries++;
        }
    }

    final protected void gotoSelection() {
        PeakPath peakPath = tableView.getSelectionModel().getSelectedItem();
        if (peakPath != null) {
            int peakID = peakPath.getPeak();
            pathTool.peakNavigator.gotoPeakId(peakID);
        }
    }

    public void savePathTable() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Table File");
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            savePathTable(file);
        }
    }

    private void savePathTable(File file) {
        Charset charset = Charset.forName("US-ASCII");
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), charset)) {
            String text = TableUtils.getTableAsString(tableView, true);
            writer.write(text);
        } catch (IOException x) {
            log.warn(x.getMessage(), x);
        }
    }

}
