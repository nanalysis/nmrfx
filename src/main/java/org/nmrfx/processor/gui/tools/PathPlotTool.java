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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
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
    TableView<PeakPath.Path> tableView;
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

            Button fitButton = new Button("Fit ");
            Button fitGroupButton = new Button("Fit Group");
            toolBar.getItems().addAll(fileMenu, fitButton, fitGroupButton);

            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();

            exportSVGButton.setOnAction(e -> activeChart.exportSVG());
            exportTableButton.setOnAction(e -> savePathTable());
            fitButton.setOnAction(e -> pathTool.fitPathsIndividual());
            fitGroupButton.setOnAction(e -> pathTool.fitPathsGrouped());

            borderPane.setTop(toolBar);
            tableView = new TableView<PeakPath.Path>();
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
        for (String colName : colNames) {
            TableColumn<PeakPath.Path, Number> col = new TableColumn<>(colName);
            if (colName.equals("Peak")) {
                col.setCellValueFactory(new PropertyValueFactory<>(colName));
            } else {
                col.setCellValueFactory(new Callback<CellDataFeatures<PeakPath.Path, Number>, ObservableValue<Number>>() {
                    public ObservableValue<Number> call(CellDataFeatures<PeakPath.Path, Number> p) {
                        // p.getValue() returns the Path instance for a particular TableView row
                        int iProp = colNames.indexOf(colName);
                        iProp--;  // account for Peak column
                        boolean isErr = iProp % 2 == 1;
                        iProp /= 2;  // account for Dev columns
                        double v = isErr ? p.getValue().getErr(iProp) : p.getValue().getPar(iProp);
                        ObservableValue<Number> ov = new SimpleDoubleProperty(v);
                        return ov;
                    }
                });
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
            }

            tableView.getColumns().add(col);

        }

        tableView.setOnKeyPressed(e
                -> {
            if ((e.getCode() == KeyCode.BACK_SPACE) || (e.getCode() == KeyCode.DELETE)) {
                List<PeakPath.Path> selPaths = getSelected();
                pathTool.removeActivePaths(selPaths);

            }
        }
        );
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
        int iSeries = 0;
        for (Integer index : selected) {
            PeakPath.Path path = (PeakPath.Path) tableView.getItems().get(index);
            Color color = XYCanvasChart.colors[iSeries % XYCanvasChart.colors.length];
            pathTool.showXYPath(path, color);
            iSeries++;
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
            boolean first = true;
            for (TableColumn column : tableView.getColumns()) {
                String header = column.getText();
                if (!first) {
                    writer.write('\t');
                } else {
                    first = false;
                }
                writer.write(header, 0, header.length());
            }
            for (PeakPath.Path item : tableView.getItems()) {
                StringBuilder sBuilder = new StringBuilder();
                sBuilder.append('\n');
                for (String colName : colNames) {
                    if (sBuilder.length() != 1) {
                        sBuilder.append('\t');
                    }
                    switch (colName) {
                        case "Peak":
                            sBuilder.append(item.getPeak());
                            break;
                        case "A":
                            sBuilder.append(String.format("%.4f", item.getA()));
                            break;
                        case "K":
                            sBuilder.append(String.format("%.4f", item.getK()));
                            break;
                        case "C":
                            sBuilder.append(String.format("%.4f", item.getC()));
                            break;
                        case "ADev":
                            sBuilder.append(String.format("%.4f", item.getADev()));
                            break;
                        case "KDev":
                            sBuilder.append(String.format("%.4f", item.getKDev()));
                            break;
                        case "CDev":
                            sBuilder.append(String.format("%.4f", item.getCDev()));
                            break;
                    }

                }
                String s = sBuilder.toString();
                writer.write(s, 0, s.length());
            }
        } catch (IOException x) {
        }
    }

}
