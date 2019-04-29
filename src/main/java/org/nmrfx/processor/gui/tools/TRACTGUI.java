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

import java.io.File;
import java.util.List;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.math3.optim.PointValuePair;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chart.Axis;
import org.nmrfx.chart.DataSeries;
import org.nmrfx.chart.XYCanvasChart;
import org.nmrfx.chart.XYChartPane;
import org.nmrfx.chart.XYValue;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.ScannerController;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.gui.controls.ScanTable;
import org.nmrfx.processor.math.TRACTSimFit;

/**
 *
 * @author brucejohnson
 */
public class TRACTGUI {

    ScannerController scanController;
    Stage stage = null;
    XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 500, 500);

    ChoiceBox<String> xArrayChoice = new ChoiceBox<>();
    ChoiceBox<String> yArrayChoice = new ChoiceBox<>();
    DataSeries series0 = new DataSeries();
    DataSeries series1 = new DataSeries();

    public TRACTGUI(ScannerController scanController) {
        this.scanController = scanController;
    }

    public void showMCplot() {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            Label xlabel = new Label("  X Array:  ");
            Label ylabel = new Label("  Y Array:  ");
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
            HBox hBox = new HBox();
            HBox.setHgrow(hBox, Priority.ALWAYS);
            Button exportButton = new Button("Export");
            exportButton.setOnAction(e -> exportBarPlotSVGAction(e));
            Button fitButton = new Button("Fit");
            fitButton.setOnAction(e -> analyze());

            hBox.getChildren().addAll(exportButton, fitButton, xlabel, xArrayChoice, ylabel, yArrayChoice);            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            borderPane.setTop(hBox);
            borderPane.setCenter(chartPane);
            stage.setScene(stageScene);
        }
        updateMCPlotChoices();
        stage.show();
        updateMCplot();
    }

    void updateMCplot() {
        if (scanController != null) {
            ScanTable scanTable = scanController.getScanTable();

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
                series.getData().clear();
                List<FileTableItem> items = scanTable.getItems();
                for (FileTableItem item : items) {
                    double x = 2.0e-3 * item.getDate().doubleValue();
                    double y = item.getDoubleExtra(yElem);
                    series.getData().add(new XYValue(x, y));
                }
                System.out.println("plot");
                activeChart.getData().add(series);
                if (!series0.getData().isEmpty()) {
                    activeChart.getData().add(series0);
                    activeChart.getData().add(series1);
                }

                activeChart.autoScale(true);
            }
        }
    }

    void updateMCPlotChoices() {
        xArrayChoice.getItems().clear();
        yArrayChoice.getItems().clear();
        if (scanController != null) {
            ScanTable scanTable = scanController.getScanTable();
            List<String> headers = scanTable.getHeaders();
            for (String header : headers) {
                if (scanTable.isData(header)) {
                    yArrayChoice.getItems().add(header);
                }
            }
            xArrayChoice.getItems().add("etime");
            xArrayChoice.setValue(xArrayChoice.getItems().get(0));
            yArrayChoice.setValue(yArrayChoice.getItems().get(0));
        }
    }

    @FXML
    void analyze() {
        TRACTSimFit tractFit = new TRACTSimFit();
        String xElem = xArrayChoice.getValue();
        String yElem = yArrayChoice.getValue();

        if ((xElem != null) && (yElem != null)) {
            ScanTable scanTable = scanController.getScanTable();
            List<FileTableItem> items = scanTable.getItems();
            double[][] xValues = new double[2][items.size()];
            double[] yValues = new double[items.size()];
            double[] errValues = new double[items.size()];
            int i = 0;
            double maxX = 0.0;
            for (FileTableItem item : items) {
                xValues[0][i] = 2.0e-3 * item.getDate().doubleValue();
                maxX = Math.max(xValues[0][i], maxX);
                xValues[1][i] = i % 2;
                yValues[i] = item.getDoubleExtra(yElem);
                errValues[i] = 1.0;
                i++;
            }
            tractFit.setXYE(xValues, yValues, errValues);
            PolyChart chart = PolyChart.getActiveChart();
            double sf = 1.0e6 * chart.getDataset().getSf(0);
            PointValuePair result = tractFit.fit(sf); // fixme
            double[] errs = tractFit.getParErrs();
            double[] values = result.getPoint();
            for (int j = 0; j < values.length; j++) {
                System.out.println(values[j] + " +/- " + errs[j]);
            }

            double[][] curve0 = tractFit.getSimValues(0.0, maxX, 200, false);
            double[][] curve1 = tractFit.getSimValues(0.0, maxX, 200, true);
            series0.getData().clear();
            series1.getData().clear();
            for (int j = 0; j < curve0[0].length; j++) {
                series0.getData().add(new XYValue(curve0[0][j], curve0[1][j]));
                series1.getData().add(new XYValue(curve1[0][j], curve1[1][j]));
            }
            series0.drawLine(true);
            series1.drawLine(true);
            series0.drawSymbol(false);
            series1.drawSymbol(false);
            series0.fillSymbol(false);
            series1.fillSymbol(false);
            updateMCplot();

        }

    }

    @FXML
    void exportBarPlotSVGAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to SVG");
        //fileChooser.setInitialDirectory(pyController.getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            try {
                Canvas canvas = activeChart.getCanvas();
                svgGC.create(true, canvas.getWidth(), canvas.getHeight(), selectedFile.toString());
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

}
