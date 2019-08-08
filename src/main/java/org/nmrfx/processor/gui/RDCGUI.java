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
package org.nmrfx.processor.gui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chart.Axis;
import org.nmrfx.chart.DataSeries;
import org.nmrfx.chart.XYCanvasChart;
import org.nmrfx.chart.XYChartPane;
import org.nmrfx.chart.XYValue;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.structure.chemistry.OrderSVD;
import org.nmrfx.structure.chemistry.constraints.RDCConstraintSet;

/**
 *
 * @author brucejohnson
 */
public class RDCGUI {

    AnalystApp analystApp;
    Stage stage = null;
    XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 900, 600);

    ChoiceBox<String> setChoice = new ChoiceBox<>();
    DataSeries series0 = new DataSeries();
    DataSeries series1 = new DataSeries();
    TextField qRMSField = new TextField("");
    TextField qRhombField = new TextField("");
    TextField rhombField = new TextField("");
    TextField magField = new TextField("");
    RDCConstraintSet rdcSet;
    OrderSVD svdResults = null;
    Label pdbFile = new Label("");
    Label bmrbFile = new Label("");

    public RDCGUI(AnalystApp analystApp) {
        this.analystApp = analystApp;
    }

    public void showRDCplot() {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("RDC Analysis");
            Label setLabel = new Label("  RDC Constraint Set:  ");
            Label qRMSlabel = new Label("  Q (RMS): ");
            Label qRhomblabel = new Label("  Q (Rhombicity): ");
            Label rhomblabel = new Label("  Rhombicity: ");
            Label maglabel = new Label("  Magnitude: ");
            //Populate ChoiceBoxes with fitting variable names
            setChoice.getItems().clear();
            try {
                setChoice.valueProperty().addListener((Observable x) -> {
                    updateRDCplot();
                });
            } catch (NullPointerException npEmc1) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error: Fit must first be performed.");
                alert.showAndWait();
                return;
            }

            ToolBar toolBar = new ToolBar();
            Button rdcButton = new Button("Perform RDC Analysis");
            Button saveButton = new Button("Save Results to File");
            Button exportButton = new Button("Export Plot");
            Label pdbLabel = new Label("  PDB File: ");
            Label bmrbLabel = new Label("  BMRB File: ");
            toolBar.getItems().addAll(rdcButton, saveButton, exportButton);//, bmrbLabel, bmrbFile, pdbLabel, pdbFile);

            rdcButton.setOnAction(e -> analyze());
            saveButton.setOnAction(e -> saveToFile());
            exportButton.setOnAction(e -> exportPlotSVGAction(e));

            HBox hBox = new HBox();
            HBox.setHgrow(hBox, Priority.ALWAYS);
            hBox.setMinWidth(600);
            qRMSField.setPrefWidth(60);
            qRhombField.setPrefWidth(60);
            rhombField.setPrefWidth(60);
            magField.setPrefWidth(60);
            hBox.getChildren().addAll(setLabel, setChoice, qRMSlabel, qRMSField, qRhomblabel, qRhombField, rhomblabel, rhombField, maglabel, magField);

            VBox vBox = new VBox();
            vBox.setMinWidth(600);
            vBox.getChildren().addAll(toolBar, hBox);
            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            borderPane.setTop(vBox);
            borderPane.setCenter(chartPane);
            stage.setScene(stageScene);
        }
        updateRDCPlotChoices();
        stage.show();
        stage.toFront();
        updateRDCplot();
    }

    void updateRDCplotWithLines() {
        updateRDCplot();
        if (!series0.getData().isEmpty()) {
            activeChart.getData().add(series0);
            activeChart.getData().add(series1);
        }
    }

    void updateRDCplot() {
        if (analystApp != null) {

            Axis xAxis = activeChart.getXAxis();
            Axis yAxis = activeChart.getYAxis();
            String xElem = setChoice.getValue();
            activeChart.setShowLegend(false);

            if ((xElem != null)) {
                xAxis.setLabel("BMRB RDC");
                yAxis.setLabel("SVD RDC");
                xAxis.setZeroIncluded(true);
                yAxis.setZeroIncluded(true);
                xAxis.setAutoRanging(true);
                yAxis.setAutoRanging(true);
                activeChart.getData().clear();
                //Prepare XYChart.Series objects by setting data
                series0.getData().clear();
                if (svdResults != null) {
                    double[] xValues = svdResults.getBUnNormVector().toArray();
                    double[] yValues = svdResults.getCalcBVector();
                    if ((xValues != null) && (yValues != null)) {
                        for (int j = 0; j < xValues.length; j++) {
                            series0.getData().add(new XYValue(xValues[j], yValues[j]));
                        }
                        series0.getData().sort(Comparator.comparing(XYValue::getXValue));
                        long lb = Math.round(series0.getData().get(0).getXValue());
                        long ub = Math.round(series0.getData().get(series0.getData().size()-1).getXValue());
                        series1.getData().add(new XYValue(lb, lb));
                        series1.getData().add(new XYValue(ub, ub));
                    }
                }
                System.out.println("plot");
                activeChart.autoScale(true);
            }
        }
    }

    void updateRDCPlotChoices() {
        System.out.println("up");
        setChoice.getItems().clear();
        if (analystApp != null) {
            if (!RDCConstraintSet.getNames().isEmpty()) {
                setChoice.getItems().addAll(RDCConstraintSet.getNames());
                setChoice.setValue(setChoice.getItems().get(0));
            }
        }
    }

    @FXML
    void analyze() {      
        String name = setChoice.getValue();
        rdcSet = RDCConstraintSet.getSet(name);
        if (rdcSet != null) {
//            if (pdbFile.getText().equals("")) {
//                Alert alert = new Alert(Alert.AlertType.ERROR);
//                alert.setContentText("Error: No PDB file loaded (Load PDB XYZ...).");
//                alert.showAndWait();
//                return;
//            }
                
            svdResults = OrderSVD.calcRDCs(rdcSet, true, false, null);
            svdResults.setRDCset(rdcSet);

            double qRMS = svdResults.getQ();
            double qRhomb = svdResults.getQrhomb();
            double rhombicity = Math.abs(svdResults.calcRhombicity());
            double magnitude = Math.abs(svdResults.calcMagnitude());
            qRMSField.setText(String.valueOf(Math.round(qRMS * 100.0) / 100.0));
            qRhombField.setText(String.valueOf(Math.round(qRhomb * 100.0) / 100.0));
            rhombField.setText(String.valueOf(Math.round(rhombicity * 100.0) / 100.0));
            magField.setText(String.valueOf(Math.round(magnitude * 100.0) / 100.0));

            series1.drawLine(true);
            series1.drawSymbol(false);
            series1.fillSymbol(false);
            updateRDCplotWithLines();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Error: No RDC Set.");
            alert.showAndWait();
            return;
        }

    }
    
    void saveToFile() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save RDC Results");
            File directoryFile = chooser.showSaveDialog(null);
            OrderSVD.writeToFile(svdResults, directoryFile);
        } catch (IOException ex) {
            ExceptionDialog dialog = new ExceptionDialog(ex);
            dialog.showAndWait();
        }
    }
        
    @FXML
    void exportPlotSVGAction(ActionEvent event) {
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
