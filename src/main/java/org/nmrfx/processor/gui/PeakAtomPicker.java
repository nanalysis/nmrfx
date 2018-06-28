package org.nmrfx.processor.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.AtomBrowser.AtomDelta;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.structure.chemistry.Molecule;

/**
 *
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
    double yOffset = 50;
    Peak selPeak = null;
    int[] peakDims;

    public void create() {
        stage = new Stage(StageStyle.DECORATED);
        borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("RNA Peak Picker");
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
            Label ppmLabel = new Label("7.5 ppm");
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
        show(300, 300);
    }

    public void show(double x, double y) {
        stage.show();
        stage.toFront();

        stage.setX(x + xOffset);
        stage.setY(y + yOffset);
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            for (ChoiceBox choiceBox : entityChoices) {
                choiceBox.getItems().setAll(mol.entities.keySet());
            }
        }
        FXMLController fxmlController = FXMLController.getActiveController();
        PolyChart chart = fxmlController.getActiveChart();
        List<Peak> selected = chart.getSelectedPeaks();
        selPeak = null;
        if (selected.size() == 1) {
            selPeak = selected.get(0);
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
                Dataset dataset = chart.getDataset();
                int i = 0;
                for (int peakDim : peakDims) {
                    double shift = selPeak.getPeakDim(peakDim).getChemShiftValue();
                    List<AtomDelta> atoms1 = AtomBrowser.getMatchingAtomNames(dataset, shift, 0.04);
                    System.out.println(atoms1.toString());
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
                    ppmLabels[i].setText(String.format("%8.3f ppm", shift));
                    i++;
                }
            }
        }
    }

    void doAssign() {
        int i = 0;
        for (ChoiceBox<String> cBox : atomChoices) {
            String value = cBox.getValue();
            System.out.println("val " + value + " " + i);
            if (value.equals("Other")) {

            } else if (value.length() > 0) {
                String[] fields = value.split(" ");
                if (fields.length > 2) {
                    String atomName = fields[fields.length - 1];
                    System.out.println(atomName);
                    AtomDelta atomDelta = atomDeltaMaps[i].get(atomName);
                    PeakDim peakDim0 = selPeak.getPeakDim(peakDims[i]);
                    peakDim0.setLabel(atomName);
                    if (atomDelta.getPeakDim() != null) {
                        PeakDim peakDim1 = atomDelta.getPeakDim();
                        if (peakDim1.getLabel().equals("")) {
                            PeakList.linkPeakDims(peakDim0, peakDim1);
                            // force a reset of shifts so new peak gets shifted to the groups shift
                            peakDim0.setChemShift(peakDim0.getChemShift());
                            peakDim0.setFrozen(peakDim0.isFrozen());
                            peakDim0.setLabel(atomName);
                        } else {
                            PeakList.linkPeakDims(peakDim1, peakDim0);
                            peakDim1.setChemShift(peakDim1.getChemShift());
                            peakDim1.setFrozen(peakDim1.isFrozen());
                        }
                    }

                }
            }
            i++;
        }
        stage.close();
    }
}
