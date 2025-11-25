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
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.math3.linear.RealMatrix;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.chart.*;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.constraints.RDCConstraint;
import org.nmrfx.chemistry.constraints.RDCConstraintSet;
import org.nmrfx.chemistry.relax.ValueWithError;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.PDFGraphicsContext;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.rdc.AlignmentCalc;
import org.nmrfx.structure.rdc.AlignmentMatrix;
import org.nmrfx.structure.rdc.RDCFitQuality;
import org.nmrfx.structure.rdc.SVDFit;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    public ChoiceBox<Integer> structureChoice = new ChoiceBox<>();
    DataSeries series1 = new DataSeries();
    TextField qRMSField = new TextField("");
    TextField qRhombField = new TextField("");
    TextField rhombField = new TextField("");
    TextField magField = new TextField("");
    Slider fracSlider = new Slider(0.1, 15, 5);
    ChoiceBox<String> modeBox = new ChoiceBox<>();
    ChoiceBox<String> stericBox = new ChoiceBox<>();
    TextField statusField = new TextField();
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
            Label setLabel = new Label("  RDC Set:  ");
            Label qRMSlabel = new Label("  Q (RMS): ");
            Label qRhomblabel = new Label("  Q (Rhombicity): ");
            Label rhomblabel = new Label("  Rhombicity: ");
            Label maglabel = new Label("  Magnitude: ");
            setChoice.setPrefWidth(150);
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
            structureChoice.getItems().add(0);
            structureChoice.setValue(0);
            structureChoice.setOnMousePressed(e -> updateStructureChoices());

            ToolBar toolBar = new ToolBar();

            MenuButton fileMenuButton = new MenuButton(("File"));

            MenuItem openButton = new MenuItem("Read...");
            openButton.setOnAction(e -> loadRDCTextFile());

            MenuItem saveButton = new MenuItem("Save Results to File...");
            saveButton.setOnAction(e -> saveToFile());

            MenuItem exportSVGButton = new MenuItem("Export SVG Plot...");
            exportSVGButton.setOnAction(this::exportPlotSVGAction);

            MenuItem exportPDFButton = new MenuItem("Export PDF Plot...");
            exportPDFButton.setOnAction(this::exportPlotPDFAction);

            fileMenuButton.getItems().addAll(openButton, saveButton, exportSVGButton);

            Button rdcButton = new Button("Perform RDC Analysis");

            Button peakListButton = new Button("Use PeakList...");
            peakListButton.setOnAction(this::extractFromArtsy);
            toolBar.getItems().addAll(fileMenuButton, setLabel, setChoice, peakListButton);

            rdcButton.setOnAction(e -> analyze());

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
            hBox.setSpacing(10);
            fracSlider.valueProperty().addListener(c -> {
                double val = fracSlider.getValue();
                fracValue.setText(String.format("%.3f", val / 100.0));
            });
            fracValue.setText(String.format("%.3f", fracSlider.getValue() / 100.0));

            HBox hBox2 = new HBox();
            HBox.setHgrow(hBox2, Priority.ALWAYS);
            hBox2.setMinWidth(600);
            hBox2.setSpacing(10);
            qRMSField.setPrefWidth(60);
            qRhombField.setPrefWidth(60);
            rhombField.setPrefWidth(60);
            magField.setPrefWidth(60);
            hBox2.getChildren().addAll(rdcButton, new Label("Structure:"), structureChoice);

            VBox vBox = new VBox();
            vBox.setMinWidth(600);
            vBox.getChildren().addAll(toolBar, hBox, hBox2);
            //Create the Scatter chart
            XYChartPane chartPane = new XYChartPane();
            activeChart = chartPane.getChart();
            chartPane.setBackground(Background.fill(Color.WHITE));
            activeChart.canvas.setOnMousePressed(e -> pickChart(e));
            borderPane.setTop(vBox);
            borderPane.setCenter(chartPane);
            statusField.setPrefWidth(250);
            VBox bottomBox = new VBox();
            HBox statusBox = new HBox();
            HBox rdcResultBox = new HBox();
            rdcResultBox.setSpacing(10);
            statusBox.getChildren().addAll(statusField);
            rdcResultBox.getChildren().addAll(qRMSlabel, qRMSField, qRhomblabel, qRhombField, rhomblabel, rhombField, maglabel, magField);
            bottomBox.getChildren().addAll(statusBox, rdcResultBox);

            borderPane.setBottom(bottomBox);
            stage.setScene(stageScene);
        }
        updateRDCPlotChoices();
        stage.show();
        stage.toFront();
        updateRDCplot();
    }

    void pickChart(MouseEvent mouseEvent) {
        var hitOpt = activeChart.pickChart(mouseEvent.getX(), mouseEvent.getY(), 10.0);
        statusField.setText("");
        hitOpt.ifPresent(hit -> {
            Object extraValue = hit.getValue().getExtraValue();
            if (extraValue instanceof RDC rdc) {
                Atom atom1 = rdc.getAtom1();
                Atom atom2 = rdc.getAtom2();
                String status = String.format("%8s %8s Calc: %5.1f Exp: %5.1f", atom1.getShortName(), atom2.getShortName(), rdc.getRDC(), rdc.getExpRDC());
                statusField.setText(status);
            }
        });
    }

    void updateStructureChoices() {
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        molecule.getStructures();
        structureChoice.getItems().clear();
        for (int iStructure : molecule.getStructures()) {
            structureChoice.getItems().add(iStructure);
        }
    }

    void updateRDCplotWithLines() {
        Map<String, DataSeries> seriesMap = updateRDCplot();
        if (!seriesMap.isEmpty()) {
            AtomicInteger atomicIndex = new AtomicInteger(0);
            seriesMap.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(e -> {
                e.getValue().setFill(XYCanvasChart.colors[atomicIndex.getAndIncrement()]);
                activeChart.getData().add(e.getValue());
            });

            activeChart.getData().add(series1);
        }
    }

    Map<String, DataSeries> updateRDCplot() {
        Map<String, DataSeries> seriesMap = new HashMap<>();
        if (analystApp != null) {

            Axis xAxis = activeChart.getXAxis();
            Axis yAxis = activeChart.getYAxis();
            String xElem = setChoice.getValue();
            activeChart.setShowLegend(true);

            if ((xElem != null)) {
                xAxis.setLabel("Experimental RDCs");
                yAxis.setLabel("Calculated RDCs");
                xAxis.setAutoRanging(false);
                yAxis.setAutoRanging(false);
                activeChart.getData().clear();
                //Prepare XYChart.Series objects by setting data
                if (aMat != null) {
                    double min = Double.MAX_VALUE;
                    double max = Double.NEGATIVE_INFINITY;
                    List<RDC> rdcValues = new ArrayList<>(localRDCSet.get());
                    for (RDC rdcValue : rdcValues) {
                        double scale = rdcValue.getMaxRDC() * 1.0e-4;
                        double x =  rdcValue.getExpRDC() / scale;
                        double y = rdcValue.getRDC() / scale;
                        double err = rdcValue.getError() / scale;
                        XYEValue xyeValue = new XYEValue(x, y, err);
                        xyeValue.setExtraValue(rdcValue);
                        String aName1 = rdcValue.getAtom1().getName();
                        String aName2 = rdcValue.getAtom2().getName();
                        String aName = aName1 + ":" + aName2;
                        DataSeries series = seriesMap.computeIfAbsent(aName, k -> {
                            DataSeries newSeries = new DataSeries();
                            newSeries.setRadius(6);
                            newSeries.setName(aName);
                            return newSeries;
                        });
                        series.add(xyeValue);
                        min = Math.min(x, min);
                        min = Math.min(y, min);
                        max = Math.max(x, max);
                        max = Math.max(y, max);
                    }
                    xAxis.setLowerBound(min - 1.0);
                    yAxis.setLowerBound(min - 1.0);
                    xAxis.setUpperBound(max + 1.0);
                    yAxis.setUpperBound(max + 1.0);
                    series1.add(new XYValue(min, min));
                    series1.add(new XYValue(max, max));
                }
            }
        }
        AtomicInteger atomicIndex = new AtomicInteger(0);
        seriesMap.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(e -> {
            e.getValue().setFill(XYCanvasChart.colors[atomicIndex.getAndIncrement()]);
        });
        return seriesMap;
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
        int iStructure = structureChoice.getValue();
        localRDCSet = rdcSet(name);
        if (localRDCSet != null) {
            List<RDC> rdcValues = new ArrayList<>(localRDCSet.get());

            RealMatrix directionMatrix = AlignmentMatrix.setupDirectionMatrix(rdcValues, iStructure);
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
    void exportPlotPDFAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            PDFGraphicsContext pdfGC = new PDFGraphicsContext();
            try {
                Canvas canvas = activeChart.getCanvas();
                pdfGC.create(true, canvas.getWidth(), canvas.getHeight(), null, null, selectedFile.toString());
                exportChart(pdfGC);
                pdfGC.saveFile();
            } catch (GraphicsIOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
    }

    protected void exportChart(GraphicsContextInterface svgGC) throws GraphicsIOException {
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

    private void extractFromArtsy(ActionEvent actionEvent) {
        var peakListopt = rdcPeakDialog();
        peakListopt.ifPresent(peakListSelector ->
                extractFromArtsyList(peakListSelector.peakListActive, peakListSelector.peakListRef, peakListSelector.tau));
    }

    ValueWithError variableFlipCalc(double i45, double i90, double tau, double noise) {
        double ratio = i45 / i90;
        double jValue = Math.atan(Math.sqrt(4.0 * ratio * ratio - 2.0)) / (Math.PI * tau);
        double err = (2.0 * noise * Math.sqrt(ratio * ratio + 1.0)) /
                (i90 * Math.PI * tau * Math.sqrt(4.0 * ratio - 2.0) * (4.0 * ratio - 1.0));
        return new ValueWithError(jValue, err);
    }

    ValueWithError artsyCalc(double intR, double intA, double tau, double noise) {
        double ratio = intA / intR;
        double jValue = Math.abs(-1.0 / tau + (2.0 / (Math.PI * tau)) * Math.asin(ratio / 2.0));
        double err = Math.abs(1.0 / (Math.PI * tau * (intR / noise)));
        return new ValueWithError(jValue, err);
    }

    ValueWithError peakValue(double[][] v, double tau) {
        double intR = v[0][0];
        double intA = v[0][1];
        double noise = v[1][0];
        return artsyCalc(intR, intA, tau, noise);
    }

    void extractFromArtsyList(PeakList peakListOrdered, PeakList peakListIsotropic, double tau) {
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        MolecularConstraints molConstraints = null;
        if (molecule != null) {
            molConstraints = molecule.getMolecularConstraints();
        }
        int iStructure = structureChoice.getValue();
        String setName = peakListOrdered.getName();
        localRDCSet = RDCConstraintSet.newSet(molConstraints, setName);


        peakListOrdered.peaks().stream().filter(p -> !p.getPeakDim(0).getLabel().isEmpty()).forEach(ordPeak -> {
            String[] orderedLabel = {ordPeak.getPeakDim(0).getLabel()};
            List<Peak> isoPeaks = peakListIsotropic.matchPeaks(orderedLabel, false, true);

            if (isoPeaks.size() == 1) {
                Peak isoPeak = isoPeaks.getFirst();
                Optional<double[][]> orderedOpt = ordPeak.getMeasures();
                Optional<double[][]> isoOpt = isoPeak.getMeasures();
                if (orderedOpt.isPresent() && isoOpt.isPresent()) {
                    ValueWithError valueOrdered = peakValue(orderedOpt.get(), tau);
                    ValueWithError valueIso = peakValue(isoOpt.get(), tau);
                    Atom atom1 = MoleculeBase.getAtomByName(ordPeak.getPeakDim(0).getLabel());
                    Atom atom2 = MoleculeBase.getAtomByName(ordPeak.getPeakDim(1).getLabel());
                    if ((atom1 != null) && (atom2 != null)) {
                        double rdcValue = valueOrdered.value() - valueIso.value();
                        double err = valueIso.error();
                        RDCConstraint rdc = new RDCConstraint(localRDCSet, atom1, atom2, iStructure, rdcValue, err);
                        localRDCSet.add(rdc);
                    }
                }
            }
            ;
        });
        updateRDCPlotChoices();
        setChoice.setValue(setName);
    }

    public record PeakListSelector(PeakList peakListActive, PeakList peakListRef, Double tau) {

    }

    enum RDCCalcMode {
        VFHMQC,
        ARTSY
    }

    public static Optional<PeakListSelector> rdcPeakDialog() {

        Dialog<PeakListSelector> dialog = new Dialog<>();
        dialog.setTitle("RDC PeakList Extractor");
        dialog.setHeaderText("Select peak list:");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        dialog.getDialogPane().setContent(grid);
        int comboBoxWidth = 250;

        ChoiceBox<RDCCalcMode> rdcCalcModeChoiceBox = new ChoiceBox<>();
        rdcCalcModeChoiceBox.getItems().addAll(RDCCalcMode.values());
        rdcCalcModeChoiceBox.setValue(RDCCalcMode.ARTSY);

        ChoiceBox<PeakList> peakListAlignedChoiceBox = new ChoiceBox<>();
        peakListAlignedChoiceBox.getItems().addAll(PeakList.peakLists());
        peakListAlignedChoiceBox.setMinWidth(comboBoxWidth);
        peakListAlignedChoiceBox.setMaxWidth(comboBoxWidth);
        ChoiceBox<PeakList> peakListIsoChoiceBox = new ChoiceBox<>();
        peakListIsoChoiceBox.getItems().addAll(PeakList.peakLists());
        peakListIsoChoiceBox.setMinWidth(comboBoxWidth);
        peakListIsoChoiceBox.setMaxWidth(comboBoxWidth);

        SimpleDoubleProperty tauProp = new SimpleDoubleProperty(0.0);
        TextField tauPropField = GUIUtils.getDoubleTextField(tauProp, 1);

        grid.add(new Label("RDC Calc Mode"), 0, 0);
        grid.add(rdcCalcModeChoiceBox, 1, 0);

        grid.add(new Label("PeakList-Aligned"), 0, 1);
        grid.add(peakListAlignedChoiceBox, 1, 1);

        grid.add(new Label("PeakList-Isotropic"), 0, 2);
        grid.add(peakListIsoChoiceBox, 1, 2);

        grid.add(new Label("Tau (ms)"), 0, 3);
        grid.add(tauPropField, 1, 3);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new PeakListSelector(peakListAlignedChoiceBox.getValue(), peakListIsoChoiceBox.getValue(), tauProp.getValue() / 1000.0);
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
