package org.nmrfx.analyst.gui.peaks;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.peaks.AtomBrowser.AtomDelta;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
public class PeakAtomPicker {

    Stage stage;
    BorderPane borderPane;
    ChoiceBox<String>[] atomChoices;
    ChoiceBox<String>[] entityChoices;
    Map<String, AtomDelta>[] atomDeltaMaps;
    Label[] ppmLabels;
    TextField[] atomFields;
    double xOffset = 50;
    Peak selPeak = null;
    int[] peakDims;
    boolean removePeakOnClose = false;

    public void create() {
        stage = new Stage(StageStyle.DECORATED);
        borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("Peak Assigner");
        stage.setAlwaysOnTop(true);

        Button pickButton = new Button("Assign");
        pickButton.setOnAction(e -> doAssign());
        int nDim = 2;
        HBox hBox = new HBox();
        borderPane.setCenter(hBox);
        double width1 = 200;
        double width2 = 125;
        atomChoices = new ChoiceBox[nDim];
        entityChoices = new ChoiceBox[nDim];
        ppmLabels = new Label[nDim];
        atomDeltaMaps = new HashMap[nDim];
        atomFields = new TextField[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            GridPane gridPane = new GridPane();
            hBox.getChildren().add(gridPane);
            Label ppmLabel = new Label(" ppm");
            ppmLabel.setPrefWidth(width1);
            ChoiceBox<String> atomChoice = new ChoiceBox<>();
            atomChoice.setPrefWidth(width1);
            ChoiceBox<String> entityChoice = new ChoiceBox<>();
            entityChoice.setPrefWidth(width2);
            TextField atomField = new TextField();
            atomField.setPrefWidth(width1 - width2);
            gridPane.add(ppmLabel, 0, 0, 2, 1);
            gridPane.add(atomChoice, 0, 1, 2, 1);
            gridPane.add(entityChoice, 0, 2);
            gridPane.add(atomField, 1, 2);
            atomChoices[iDim] = atomChoice;
            entityChoices[iDim] = entityChoice;
            atomFields[iDim] = atomField;
            ppmLabels[iDim] = ppmLabel;
            atomDeltaMaps[iDim] = new HashMap<>();
        }
        borderPane.setBottom(pickButton);
        stage.setAlwaysOnTop(true);
        stage.setOnCloseRequest(e -> cancel());
    }

    public void show(double x, double y, Peak peak) {
        removePeakOnClose = peak != null;
        double tol = 0.04;
        stage.show();
        stage.toFront();
        double screenWidth = Screen.getPrimary().getBounds().getWidth();
        if (x > (screenWidth / 2)) {
            x = x - stage.getWidth() - xOffset;
        } else {
            x = x + 100;
        }

        y = y - stage.getHeight() / 2.0;

        stage.setX(x);
        stage.setY(y);
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            for (ChoiceBox choiceBox : entityChoices) {
                choiceBox.getItems().setAll(mol.entities.keySet());
            }
        }
        FXMLController fxmlController = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        PolyChart chart = fxmlController.getActiveChart();
        List<Peak> selected = chart.getSelectedPeaks();
        selPeak = null;
        if (peak != null) {
            selPeak = peak;
        } else {
            if (selected.size() == 1) {
                selPeak = selected.get(0);
            }
            // fixme if more than one peak selected figure out if they're in row or column and set label
            // for a single (appropriate) dimension"
        }
        if (selPeak != null) {
            stage.setTitle("Peak Assigner: " + selPeak.getName());
            PeakListAttributes usePeakAttr = null;
            List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
            for (PeakListAttributes peakAttr : peakAttrs) {
                if (selPeak.getPeakList() == peakAttr.getPeakList()) {
                    usePeakAttr = peakAttr;
                    break;
                }
            }
            if (usePeakAttr != null) {
                peakDims = usePeakAttr.getPeakDim();
                DatasetBase dataset = chart.getDataset();
                int i = 0;
                for (int peakDim : peakDims) {
                    SpectralDim sDim = selPeak.getPeakList().getSpectralDim(peakDim);
                    double shift = selPeak.getPeakDim(peakDim).getChemShiftValue();
                    List<AtomDelta> atoms1 = AtomBrowser.getMatchingAtomNames(dataset, sDim, shift, tol);
                    atomChoices[i].getItems().clear();
                    atomChoices[i].getItems().add("Other");
                    atomDeltaMaps[i].clear();
                    for (AtomDelta atomDelta : atoms1) {
                        atomChoices[i].getItems().add(atomDelta.toString());
                        atomDeltaMaps[i].put(atomDelta.getName(), atomDelta);
                    }
                    if (!atoms1.isEmpty()) {
                        atomChoices[i].setValue(atoms1.get(0).toString());
                    }
                    ppmLabels[i].setText(String.format("%8.3f ppm +/ %.3f", shift, tol));
                    i++;
                }
            }
        }
    }

    void cancel() {
        if (removePeakOnClose) {
            if (selPeak != null) {
                PeakList peakList = selPeak.getPeakList();
                peakList.removePeak(selPeak);
                AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getActiveChart().drawPeakLists(true);
            }
        }
        stage.close();
    }

    void doAssign() {
        int i = 0;
        for (ChoiceBox<String> cBox : atomChoices) {
            String value = cBox.getValue();
            PeakDim peakDim0 = selPeak.getPeakDim(peakDims[i]);

            if (value == null) {
            } else if (value.equals("Other")) {
                String entityName = entityChoices[i].getValue();
                String aName = atomFields[i].getText();
                String atomSpecifier;
                if ((aName != null) && !aName.equals("")) {
                    if ((entityName != null) && !entityName.equals("")) {
                        atomSpecifier = entityName + ":" + aName;
                    } else {
                        atomSpecifier = aName;
                    }
                    peakDim0.setLabel(atomSpecifier);
                }
            } else if (value.length() > 0) {
                String[] fields = value.split(" ");
                if (fields.length > 0) {
                    String atomSpecifier = fields[0];
                    AtomDelta atomDelta = atomDeltaMaps[i].get(atomSpecifier);
                    peakDim0.setLabel(atomSpecifier);
                    if (atomDelta.getPeakDim() != null) {
                        PeakDim peakDim1 = atomDelta.getPeakDim();
                        if (peakDim1.getLabel().equals("")) {
                            PeakList.linkPeakDims(peakDim0, peakDim1);
                            // force a reset of shifts so new peak gets shifted to the groups shift
                            peakDim0.setChemShift(peakDim0.getChemShift());
                            peakDim0.setFrozen(peakDim0.isFrozen());
                            peakDim0.setLabel(atomSpecifier);
                        } else {
                            PeakList.linkPeakDims(peakDim1, peakDim0);
                            peakDim0.setChemShift(peakDim1.getChemShift());
                            peakDim0.setFrozen(peakDim1.isFrozen());
                        }
                    }

                }
            }
            i++;
        }
        stage.close();
    }
}
