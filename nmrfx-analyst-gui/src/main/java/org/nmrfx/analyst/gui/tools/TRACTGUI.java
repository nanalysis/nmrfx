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

import javafx.beans.Observable;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.math3.optim.PointValuePair;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chart.*;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.math.TRACTSimFit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.nmrfx.utilities.Util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author brucejohnson
 */
public class TRACTGUI {

    private static final Logger log = LoggerFactory.getLogger(TRACTGUI.class);
    ScannerTool scannerTool;
    Stage stage = null;
    XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 500, 500);

    ChoiceBox<String> xArrayChoice = new ChoiceBox<>();
    ChoiceBox<String> yArrayChoice = new ChoiceBox<>();
    DataSeries series0 = new DataSeries();
    DataSeries series1 = new DataSeries();
    TextField resultsField;
    TextField scaleField;
    PolyChart chart;

    public TRACTGUI(ScannerTool scannerTool) {
        this.scannerTool = scannerTool;
        chart = PolyChartManager.getInstance().getActiveChart();
    }

    public void showMCplot() {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("TRACT Analysis");
            Label xlabel = new Label("  X Array:  ");
            Label ylabel = new Label("  Y Array:  ");
            Label scalelabel = new Label("  X Scale:  ");
            //Populate ChoiceBoxes with fitting variable names
            xArrayChoice.getItems().clear();
            yArrayChoice.getItems().clear();
            try {
                xArrayChoice.valueProperty().addListener((Observable x) -> {
                    updateMCplot();
                });
                yArrayChoice.valueProperty().addListener((Observable y) -> {
                    updateMCplot();
                });
            } catch (NullPointerException npEmc1) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error: Fit must first be performed.");
                alert.showAndWait();
                return;
            }
            ScanTable scanTable = scannerTool.getScanTable();
            scanTable.getTableView().getColumns().
                    addListener((ListChangeListener) (c -> {
                        updateMCPlotChoices();
                    }));

            ToolBar toolBar = new ToolBar();
            MenuButton fileMenu = new MenuButton("File");

            MenuItem exportSVGMenuItem = new MenuItem("Export SVG...");
            fileMenu.getItems().add(exportSVGMenuItem);
            exportSVGMenuItem.setOnAction(e -> exportBarPlotSVGAction(e));

            MenuItem exportDataMenuItem = new MenuItem("Export Data...");
            fileMenu.getItems().add(exportDataMenuItem);
            exportDataMenuItem.setOnAction(e -> exportData());

            Button fitButton = new Button("Fit");
            toolBar.getItems().addAll(fileMenu, fitButton);

            fitButton.setOnAction(e -> analyze());
            scaleField = new TextField("2.0e-3");
            scaleField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    updateMCplot();
                }
            });

            HBox hBox = new HBox();
            HBox.setHgrow(hBox, Priority.ALWAYS);
            hBox.setMinWidth(600);
            hBox.getChildren().addAll(xlabel,
                    xArrayChoice, ylabel, yArrayChoice, scalelabel, scaleField);

            VBox vBox = new VBox();
            vBox.setMinWidth(600);
            vBox.getChildren().addAll(toolBar, hBox);
            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            borderPane.setTop(vBox);
            borderPane.setCenter(chartPane);
            resultsField = new TextField();
            borderPane.setBottom(resultsField);
            stage.setScene(stageScene);
        }
        updateMCPlotChoices();
        stage.show();
        stage.toFront();
        updateMCplot();
    }

    double getXValue(FileTableItem item, String xElem) {
        double xValue;
        if (xElem.equals("etime")) {
            xValue = item.getDate().doubleValue();
        } else {
            xValue = item.getDoubleExtra(xElem);
        }
        double scale;
        try {
            scale = Double.parseDouble(scaleField.getText().trim());
        } catch (NumberFormatException nfE) {
            scale = 1.0;
        }
        xValue *= scale;
        return xValue;

    }

    void updateMCplotWithLines() {
        updateMCplot();
        if (!series0.isEmpty()) {
            activeChart.getData().add(series0);
            activeChart.getData().add(series1);
        }
    }

    void updateMCplot() {
        if (scannerTool != null) {
            ScanTable scanTable = scannerTool.getScanTable();

            Axis xAxis = activeChart.getXAxis();
            Axis yAxis = activeChart.getYAxis();
            String xElem = xArrayChoice.getValue();
            String yElem = yArrayChoice.getValue();
            activeChart.setShowLegend(false);

            if ((xElem != null) && (yElem != null)) {
                xAxis.setLabel("Delay");
                yAxis.setLabel("Intensity");
                xAxis.setZeroIncluded(true);
                yAxis.setZeroIncluded(true);
                xAxis.setAutoRanging(true);
                yAxis.setAutoRanging(true);
                DataSeries series = new DataSeries();
                activeChart.getData().clear();
                //Prepare XYChart.Series objects by setting data
                series.clear();
                List<FileTableItem> items = scanTable.getItems();
                for (FileTableItem item : items) {
                    double x = getXValue(item, xElem);
                    double y = item.getDoubleExtra(yElem);
                    series.add(new XYValue(x, y));
                }
                System.out.println("plot");
                activeChart.getData().add(series);

                activeChart.autoScale(true);
            }
        }
    }

    void updateMCPlotChoices() {
        System.out.println("up");
        xArrayChoice.getItems().clear();
        yArrayChoice.getItems().clear();
        if (scannerTool != null) {
            ScanTable scanTable = scannerTool.getScanTable();
            List<String> headers = scanTable.getHeaders();
            for (String header : headers) {
                if (scanTable.isData(header)) {
                    yArrayChoice.getItems().add(header);
                }
                if (header.equalsIgnoreCase("etime")
                        || header.equalsIgnoreCase("value")
                        || header.equalsIgnoreCase("delay")
                        || header.equalsIgnoreCase("vd")
                ) {
                    xArrayChoice.getItems().add(header);
                }

            }
            xArrayChoice.setValue(xArrayChoice.getItems().get(0));
            if (!yArrayChoice.getItems().isEmpty()) {
                yArrayChoice.setValue(yArrayChoice.getItems().get(0));
            }
        }

    }

    @FXML
    void analyze() {
        double sf = 1.0e6 * chart.getDataset().getSf(0);
        TRACTSimFit tractFit = new TRACTSimFit(sf, "H", "N");
        String xElem = xArrayChoice.getValue();
        String yElem = yArrayChoice.getValue();

        if ((xElem != null) && (yElem != null)) {
            ScanTable scanTable = scannerTool.getScanTable();
            List<FileTableItem> items = scanTable.getItems();
            double[][] xValues = new double[2][items.size()];
            double[] yValues = new double[items.size()];
            double[] errValues = new double[items.size()];
            int i = 0;
            double maxX = 0.0;
            DataSeries series = activeChart.getData().get(0);
            for (XYValue value : series.getData()) {
                double xValue = value.getXValue();
                xValues[0][i] = xValue;
                maxX = Math.max(xValues[0][i], maxX);
                xValues[1][i] = i % 2;
                yValues[i] = value.getYValue();
                errValues[i] = 1.0;
                i++;
            }

            tractFit.setXYE(xValues, yValues, errValues);
            PointValuePair result = tractFit.fit(); // fixme
            double[] errs = tractFit.getParErrs();
            double[] values = result.getPoint();
            StringBuilder sBuilder = new StringBuilder();
            double r1 = tractFit.getR1(values);
            double e1 = r1 / values[1] * errs[1];
            sBuilder.append(String.format("%3s %.1f +/- %.1f    ", "Ra", r1, e1));
            sBuilder.append(String.format("%3s %.1f +/- %.1f    ", "Rb", values[1], errs[1]));
            sBuilder.append(String.format("%3s %.1f +/- %.1f ns", "tau", values[3], errs[3]));
            resultsField.setText(sBuilder.toString());

            double[][] curve0 = tractFit.getSimValues(0.0, maxX, 200, false);
            double[][] curve1 = tractFit.getSimValues(0.0, maxX, 200, true);
            series0.clear();
            series1.clear();
            for (int j = 0; j < curve0[0].length; j++) {
                series0.add(new XYValue(curve0[0][j], curve0[1][j]));
                series1.add(new XYValue(curve1[0][j], curve1[1][j]));
            }
            series0.drawLine(true);
            series1.drawLine(true);
            series0.drawSymbol(false);
            series1.drawSymbol(false);
            series0.fillSymbol(false);
            series1.fillSymbol(false);
            updateMCplotWithLines();

        }

    }

    @FXML
    void exportBarPlotSVGAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to SVG");
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            try {
                Canvas canvas = activeChart.getCanvas();
                svgGC.create(canvas.getWidth(), canvas.getHeight(), selectedFile.toString());
                exportChart(svgGC);
                svgGC.saveFile();
            } catch (GraphicsIOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
    }

    protected void exportChart(SVGGraphicsContext svgGC) throws GraphicsIOException {
        svgGC.beginPath();
        activeChart.drawChart(svgGC);
    }

    void writeData(FileWriter writer) throws IOException {
        List<DataSeries> data = activeChart.getData();
        if (!data.isEmpty()) {
            DataSeries series = data.get(0);
            List<XYValue> values = series.getData();
            int n = values.size() / 2;
            writer.write("#Data " + n + " \n");

            for (int i = 0; i < n; i++) {
                XYValue v0 = values.get(i * 2);
                XYValue v1 = values.get(i * 2 + 1);
                String outStr = String.format("%.5f %.5f %.5f\n", v0.getXValue(), v0.getYValue(), v1.getYValue());
                writer.write(outStr);
            }
        }
        if (data.size() == 3) {
            DataSeries series0 = data.get(1);
            DataSeries series1 = data.get(2);
            List<XYValue> values0 = series0.getData();
            List<XYValue> values1 = series1.getData();
            int n = values0.size();
            writer.write("#Simulated " + n + " \n");
            for (int i = 0; i < n; i++) {
                XYValue v0 = values0.get(i);
                XYValue v1 = values1.get(i);
                String outStr = String.format("%.5f %.5f %.5f\n", v0.getXValue(), v0.getYValue(), v1.getYValue());
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
