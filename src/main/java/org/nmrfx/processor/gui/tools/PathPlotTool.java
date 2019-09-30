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
package org.nmrfx.processor.gui.tools;

import java.util.ArrayList;
import java.util.List;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.nmrfx.chart.Axis;
import org.nmrfx.chart.DataSeries;
import org.nmrfx.chart.XYCanvasChart;
import org.nmrfx.chart.XYChartPane;
import org.nmrfx.processor.datasets.peaks.PeakPath;

/**
 *
 * @author brucejohnson
 */
public class PathPlotTool {

    PathTool pathTool;
    Stage stage = null;
    XYCanvasChart activeChart = null;
    BorderPane borderPane = new BorderPane();
    TableView tableView;
    Scene stageScene = new Scene(borderPane, 500, 500);

    public PathPlotTool(PathTool pathTool) {
        this.pathTool = pathTool;
    }

    public void show(String xAxisName, String yAxisName) {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Plot Tool");
            ToolBar toolBar = new ToolBar();
            Button exportButton = new Button("Export");
            Button fitButton = new Button("Fit ");
            Button fitGroupButton = new Button("Fit Group");
            toolBar.getItems().addAll(exportButton, fitButton, fitGroupButton);

            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            exportButton.setOnAction(e -> activeChart.exportSVG());
            fitButton.setOnAction(e -> pathTool.fitPathsIndividual());
            fitGroupButton.setOnAction(e -> pathTool.fitPathsGrouped());
            borderPane.setTop(toolBar);
            tableView = new TableView();
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

    public void updateTable(ObservableList<PeakPath.Path> paths) {
        tableView.getItems().setAll(paths);
        System.out.println("items " + tableView.getItems().size());
    }

    void initTable() {
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ListChangeListener selectionListener = (ListChangeListener) (ListChangeListener.Change c) -> {
            selectionChanged();
        };
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
        TableColumn<PeakPath.Path, Integer> peakCol = new TableColumn<>("Peak");
        peakCol.setCellValueFactory(new PropertyValueFactory<>("Peak"));
        tableView.getColumns().add(peakCol);
        String[] colNames = {"A", "K", "C"};
        for (String colName : colNames) {
            TableColumn<PeakPath.Path, Number> col = new TableColumn<>(colName);
            col.setCellValueFactory(new PropertyValueFactory<>(colName));
            col.setCellFactory(c
                    -> new TableCell<PeakPath.Path, Number>() {
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

    List<PeakPath.Path> getSelected() {
        List<PeakPath.Path> paths = new ArrayList<>();
        List<Integer> selected = tableView.getSelectionModel().getSelectedIndices();
        for (Integer index : selected) {
            PeakPath.Path path = (PeakPath.Path) tableView.getItems().get(index);
            paths.add(path);
        }
        return paths;
    }

    final protected void selectionChanged() {
        pathTool.clearXYPath();
        List<Integer> selected = tableView.getSelectionModel().getSelectedIndices();
        for (Integer index : selected) {
            PeakPath.Path path = (PeakPath.Path) tableView.getItems().get(index);
            pathTool.showXYPath(path);
        }
    }
}
