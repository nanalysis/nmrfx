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
import javafx.fxml.FXML;
import javafx.scene.Scene;
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
    List svdResults = new ArrayList<>();
    List<String> setNames = new ArrayList<>();
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
            Label pdbLabel = new Label("  PDB File: ");
            Label bmrbLabel = new Label("  BMRB File: ");
            toolBar.getItems().addAll(rdcButton, saveButton, bmrbLabel, bmrbFile, pdbLabel, pdbFile);

            rdcButton.setOnAction(e -> analyze());
            saveButton.setOnAction(e -> saveToFile());

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
                if (!svdResults.isEmpty()) {
                    double[] xValues = (double[]) svdResults.get(0);
                    double[] yValues = (double[]) svdResults.get(1);
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
            setChoice.getItems().add("");
            setChoice.setValue(setChoice.getItems().get(0));
        }
    }

    @FXML
    void analyze() {      
        String name = setChoice.getValue();
        rdcSet = RDCConstraintSet.getSet(name);
        if (rdcSet != null) {
            if (pdbFile.getText().equals("")) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error: No PDB file loaded (Load PDB XYZ...).");
                alert.showAndWait();
                return;
            }
                
            svdResults = OrderSVD.calcRDCs(rdcSet, true, false, null);

            double qRMS = (double) svdResults.get(2);
            double qRhomb = (double) svdResults.get(14);
            double rhombicity = Math.abs((double) svdResults.get(3));
            double magnitude = Math.abs((double) svdResults.get(4));
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
        if (!svdResults.isEmpty()) {
            double[] x = (double[]) svdResults.get(7);
            double qRMS = (double) svdResults.get(2);
            double rhombicity = Math.abs((double) svdResults.get(3));
            double mag = Math.abs((double) svdResults.get(4));
            double axial = (double) svdResults.get(8);
            double rhombic = (double) svdResults.get(9);
            double Sxx = (double) svdResults.get(10);
            double Syy = (double) svdResults.get(11);
            double Szz = (double) svdResults.get(12);
            double eta = (double) svdResults.get(13);
            double qRhomb = (double) svdResults.get(14);
            double[][] euler = (double[][]) svdResults.get(15);

            String[] atom1 = new String[rdcSet.getSize()];
            String[] atom2 = new String[rdcSet.getSize()];
            if (rdcSet != null) {
                for (int i=0; i<rdcSet.getSize(); i++) {
                    atom1[i] = rdcSet.get(i).getSpSets()[0].getFullName().split(":")[1];
                    atom2[i] = rdcSet.get(i).getSpSets()[1].getFullName().split(":")[1];
                }
            }
            double[] bmrbRDC = (double[]) svdResults.get(0);
            double[] svdRDC = (double[]) svdResults.get(1);
            double[] bmrbRDCNorm = (double[]) svdResults.get(5);
            double[] svdRDCNorm = (double[]) svdResults.get(6);
            double[] rdcDiff = new double[svdRDC.length];
            for (int i=0; i<rdcDiff.length; i++) {
                rdcDiff[i] = svdRDC[i] - bmrbRDC[i];
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save RDC Results");
            File directoryFile = chooser.showSaveDialog(null);
            if (directoryFile != null) {
                String[] headerFields = {"Atom_1", "Atom_2", "BMRB_RDC", "SVD_RDC", "RDC_Diff", "Normalized_BMRB_RDC", "Normalized_SVD_RDC"};
                StringBuilder headerBuilder = new StringBuilder();
    //            for (String field : headerFields) {
    //                if (headerBuilder.length() > 0) {
    //                    headerBuilder.append('\t');
    //                }
    //                headerBuilder.append(field);
    //            }

                String[][] atomFields = {atom1, atom2};
                double[][] valueFields = {bmrbRDC, svdRDC, rdcDiff, bmrbRDCNorm, svdRDCNorm};
                String[] formats = {"%.3f", "%.3f", "%.3f", "%.3E", "%.3E"};

                try (FileWriter writer = new FileWriter(directoryFile)) {
                    writer.write("Syy\tSzz\tSxy\tSxz\tSyz\n");
                    for(double value : x) {
                        writer.write(String.format("%.3E\t", value));
                    }
                    writer.write("\n\nSx'x'\tSy'y'\tSz'z'\n");
                    writer.write(String.format("%.3E\t%.3E\t%.3E\n\n", Sxx, Syy, Szz));
                    writer.write(String.format("Asymmetry parameter (eta) = %.3f \n", eta));
                    writer.write(String.format("Q (RMS) = %.3f \n", qRMS));
                    writer.write(String.format("Q (Rhombicity) = %.3f \n", qRhomb));
                    writer.write(String.format("Magnitude = %.3f \n\n", mag));
                    writer.write(String.format("Rhombic component = %.3E \n", rhombic));
                    writer.write(String.format("Axial component = %.3E \n", axial));
                    writer.write(String.format("Rhombicity = %.3f \n\n", rhombicity));
                    writer.write("Euler Angles for rotation about z, y', z''\n");
                    writer.write("Alpha\tBeta\tGamma\n");
                    writer.write(String.format("%.3f\t%.3f\t%.3f\n", euler[0][0], euler[0][1], euler[0][2]));
                    writer.write(String.format("%.3f\t%.3f\t%.3f\n", euler[0][0]+180., euler[0][1], euler[0][2]));
                    writer.write(String.format("%.3f\t%.3f\t%.3f\n", euler[1][0], euler[1][1], euler[1][2]+180.));
                    writer.write(String.format("%.3f\t%.3f\t%.3f\n\n", euler[1][0]+180., euler[1][1], euler[1][2]+180.));

                    for (String header : headerFields) {
                        headerBuilder.append(String.format("%s\t", header));
                    }
                    writer.write(headerBuilder.toString() + "\n");

                    for (int i=0; i<bmrbRDC.length; i++) {
                        StringBuilder valueBuilder = new StringBuilder();
                        for (String[] atom : atomFields) {
                            valueBuilder.append(String.format("%s\t", atom[i]));
                        }
                        for (double[] value : valueFields) {
                            valueBuilder.append(String.format(formats[Arrays.asList(valueFields).indexOf(value)]+"\t", value[i]));
                        }
                        writer.write(valueBuilder.toString() + "\n");
                    }

                } catch (IOException ex) {
                    ExceptionDialog dialog = new ExceptionDialog(ex);
                    dialog.showAndWait();
                }
            }
        }
    }

    protected void exportChart(SVGGraphicsContext svgGC) throws GraphicsIOException {
        svgGC.beginPath();
        activeChart.drawChart(svgGC);
    }

}
