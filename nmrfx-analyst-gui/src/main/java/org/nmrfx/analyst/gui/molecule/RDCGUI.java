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
package org.nmrfx.analyst.gui.molecule;

import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.math3.linear.RealMatrix;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.chart.*;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.RDC;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.constraints.RDCConstraintSet;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.rdc.AlignmentCalc;
import org.nmrfx.structure.rdc.AlignmentMatrix;
import org.nmrfx.structure.rdc.RDCFitQuality;
import org.nmrfx.structure.rdc.SVDFit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author brucejohnson
 */
public class RDCGUI {

    AnalystApp analystApp;
    Stage stage = null;
    XYCanvasChart activeChart;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 900, 600);

    public ChoiceBox<String> setChoice = new ChoiceBox<>();
    DataSeries series0 = new DataSeries();
    DataSeries series1 = new DataSeries();
    TextField qRMSField = new TextField("");
    TextField qRhombField = new TextField("");
    TextField rhombField = new TextField("");
    TextField magField = new TextField("");
    Slider fracSlider = new Slider(0.1, 15, 5);
    ChoiceBox<String> modeBox = new ChoiceBox<>();
    ChoiceBox<String> stericBox = new ChoiceBox<>();

    RDCConstraintSet localRDCSet;
    public Label bmrbFile = new Label("");
    SVDFit svdFit;
    AlignmentMatrix aMat;

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
                setChoice.valueProperty().addListener((Observable x) -> updateRDCplot());
            } catch (NullPointerException npEmc1) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error: Fit must first be performed.");
                alert.showAndWait();
                return;
            }

            ToolBar toolBar = new ToolBar();
            Button openButton = new Button("Read");
            Button rdcButton = new Button("Perform RDC Analysis");
            Button saveButton = new Button("Save Results to File");
            Button exportButton = new Button("Export Plot");
            toolBar.getItems().addAll(openButton, rdcButton, saveButton, exportButton);

            openButton.setOnAction(e -> loadRDCTextFile());
            rdcButton.setOnAction(e -> analyze());
            saveButton.setOnAction(e -> saveToFile());
            exportButton.setOnAction(this::exportPlotSVGAction);

            HBox hBox = new HBox();
            HBox.setHgrow(hBox, Priority.ALWAYS);
            hBox.setMinWidth(600);
            stericBox.getItems().addAll("Wall", "Cylinder");
            stericBox.setValue("Wall");
            modeBox.getItems().addAll("SVD", "Steric");
            modeBox.setValue("SVD");
            Label fracLabel = new Label("  W/V %: ");
            Label modeLabel = new Label(" Calc Mode: ");
            Label stericLabel = new Label(" Steric Mode: ");
            Label fracValue = new Label("");
            fracSlider.setPrefWidth(200.0);
            hBox.getChildren().addAll(modeLabel, modeBox, stericLabel, stericBox, fracLabel, fracSlider, fracValue);
            fracSlider.valueProperty().addListener(c -> {
                double val = fracSlider.getValue();
                fracValue.setText(String.format("%.3f", val / 100.0));
            });
            fracValue.setText(String.format("%.3f", fracSlider.getValue() / 100.0));

            HBox hBox2 = new HBox();
            HBox.setHgrow(hBox2, Priority.ALWAYS);
            hBox2.setMinWidth(600);
            qRMSField.setPrefWidth(60);
            qRhombField.setPrefWidth(60);
            rhombField.setPrefWidth(60);
            magField.setPrefWidth(60);
            hBox2.getChildren().addAll(setLabel, setChoice, qRMSlabel, qRMSField, qRhomblabel, qRhombField, rhomblabel, rhombField, maglabel, magField);

            VBox vBox = new VBox();
            vBox.setMinWidth(600);
            vBox.getChildren().addAll(toolBar, hBox, hBox2);
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
        if (!series0.isEmpty()) {
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
                xAxis.setLabel("Experimental RDCs");
                yAxis.setLabel("Calculated RDCs");
                xAxis.setAutoRanging(true);
                yAxis.setAutoRanging(true);
                activeChart.getData().clear();
                //Prepare XYChart.Series objects by setting data
                series0.clear();
                if (aMat != null) {
                    List<RDC> rdcValues = new ArrayList<>(localRDCSet.get());
                    for (RDC rdcValue : rdcValues) {
                        series0.add(new XYValue(rdcValue.getExpRDC(), rdcValue.getRDC()));
                    }
                    series0.getData().sort(Comparator.comparing(XYValue::getXValue));
                    long lb = Math.round(series0.getData().get(0).getXValue());
                    long ub = Math.round(series0.getData().reversed().get(0).getXValue());
                    series1.add(new XYValue(lb, lb));
                    series1.add(new XYValue(ub, ub));
                }
                activeChart.autoScale(true);
            }
        }
    }

    void updateRDCPlotChoices() {
        setChoice.getItems().clear();
        MoleculeBase molecule = MoleculeFactory.getActive();
        if (molecule != null) {
            MolecularConstraints molConstr = molecule.getMolecularConstraints();
            if ((molConstr != null) && !molConstr.getRDCSetNames().isEmpty()) {
                setChoice.getItems().addAll(molConstr.getRDCSetNames());
                setChoice.setValue(setChoice.getItems().get(0));
            }
        }
    }

    RDCConstraintSet rdcSet(String name) {
        RDCConstraintSet rdcSet = null;
        MoleculeBase molecule = MoleculeFactory.getActive();
        if (molecule != null) {
            MolecularConstraints molConstr = molecule.getMolecularConstraints();
            if (molConstr != null) {
                rdcSet = molConstr.getRDCSet(name);
            }
        }
        if (rdcSet == null) {
            rdcSet = this.localRDCSet;
        }
        return rdcSet;
    }

    @FXML
    void analyze() {
        String name = setChoice.getValue();
        localRDCSet = rdcSet(name);
        if (localRDCSet != null) {
            List<RDC> rdcValues = new ArrayList<>(localRDCSet.get());

            RealMatrix directionMatrix = AlignmentMatrix.setupDirectionMatrix(rdcValues);
            if (modeBox.getValue().equals("SVD")) {
                svdFit = new SVDFit(directionMatrix, rdcValues);
                aMat = svdFit.fit();
                aMat.calcAlignment();
            } else {
                aMat = calcStericAlignment(directionMatrix, rdcValues);
            }
            Molecule mol = (Molecule) MoleculeFactory.getActive();
            mol.setRDCResults(aMat);

            RDCFitQuality fitQuality = new RDCFitQuality();
            fitQuality.evaluate(aMat, rdcValues);

            double qRMS = fitQuality.getQRMS();
            double qRhomb = fitQuality.getQRhomb();
            double rhombicity = Math.abs(aMat.calcRhombicity());
            double magnitude = Math.abs(aMat.calcMagnitude());
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
        }

    }

    AlignmentMatrix calcStericAlignment(RealMatrix rMat, List<RDC> rdcValues) {
        MoleculeBase mol = MoleculeFactory.getActive();
        AlignmentCalc aCalc = new AlignmentCalc(mol, true, 2.0);
        aCalc.center();
        aCalc.genAngles(122, 18, 1.0);
        aCalc.findMinimums();
        double slabWidth = 0.2;
        double f = fracSlider.getValue() / 100.0;
        double d = 40.0;
        String mode = stericBox.getValue().equals("Wall") ? "bicelle" : "pf1";
        if (mode.equals("pf1")) {
            d = 67.0;
        }
        aCalc.calcCylExclusions(slabWidth, f, d, mode);
        aCalc.calcTensor(0.8);
        AlignmentMatrix aMatrix = aCalc.getAlignment();
        aMatrix.calcAlignment();
        aMatrix.calcRDC(rMat, rdcValues);
        return aMatrix;
    }

    void saveToFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save RDC Results");
        File directoryFile = chooser.showSaveDialog(null);
        if (directoryFile != null) {
            try {
                try (FileWriter fileWriter = new FileWriter(directoryFile)) {
                    fileWriter.write(aMat.toString());
                }
            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    @FXML
    void exportPlotSVGAction(ActionEvent event) {
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

    void loadRDCTextFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load RDC Text File");
        File file = chooser.showOpenDialog(null);
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        MolecularConstraints molConstraints = null;
        if (molecule != null) {
            molConstraints = molecule.getMolecularConstraints();
        }
        if (file != null) {
            String setName = file.getName();
            setName = setName.substring(0, setName.indexOf("."));
            localRDCSet = RDCConstraintSet.newSet(molConstraints, setName);
            try {
                localRDCSet.readInputFile(file);
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error: file read error: " + ex.getMessage());
                alert.showAndWait();
                return;
            }
            updateRDCPlotChoices();
            setChoice.setValue(setName);
        }

    }

}
