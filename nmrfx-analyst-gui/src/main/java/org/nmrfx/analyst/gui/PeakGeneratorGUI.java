package org.nmrfx.analyst.gui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.analyst.peaks.PeakGenerator;

public class PeakGeneratorGUI {
    @FXML
    private BorderPane simBorderPane;
    @FXML
    private TextField simPeakListNameField;
    @FXML
    private ComboBox simDatasetNameField;

    @FXML
    private MenuButton simTypeMenu;

    @FXML
    private Label typeLabel;

    @FXML
    private Label subTypeLabel;

    @FXML
    private GridPane peakListParsPane;
    TextField[][] peakListParFields;

    @FXML
    private HBox optionBox;

    @FXML
    Button generateButton;


    private Slider distanceSlider = new Slider(2, 7.0, 5.0);
    private ChoiceBox transferLimitChoice = new ChoiceBox();
    private CheckBox useNCheckBox = new CheckBox("UseN");
    String type = "";
    String subType = "";
    double sfH = 700.0;

    PeakList makePeakListFromPars() {
        String listName = simPeakListNameField.getText();
        int nDim = peakListParFields.length;
        PeakList newPeakList = new PeakList(listName, nDim);
        try {
            for (int iDim = 0; iDim < nDim; iDim++) {
                newPeakList.getSpectralDim(iDim).setSw(Double.parseDouble(peakListParFields[iDim][0].getText()));
                newPeakList.getSpectralDim(iDim).setSf(Double.parseDouble(peakListParFields[iDim][1].getText()));
                newPeakList.getSpectralDim(iDim).setDimName(peakListParFields[iDim][2].getText());
            }
            newPeakList.setSampleConditionLabel("sim");
        } catch (NumberFormatException nfE) {
            System.out.println(nfE.getMessage());
            PeakList.remove(listName);
            newPeakList = null;
        }
        return newPeakList;
    }

    PeakList makePeakListFromDataset(Dataset dataset) {
        String listName = simPeakListNameField.getText();
        int nDim = dataset.getNDim();
        PeakList newPeakList = new PeakList(listName, nDim);
        for (int iDim = 0; iDim < nDim; iDim++) {
            newPeakList.getSpectralDim(iDim).setSw(dataset.getSw(iDim));
            newPeakList.getSpectralDim(iDim).setSf(dataset.getSf(iDim));
            newPeakList.getSpectralDim(iDim).setDimName(dataset.getLabel(iDim));
        }
        newPeakList.setSampleConditionLabel("sim");
        return newPeakList;
    }
    @FXML
    public void genPeaksAction(ActionEvent e) throws InvalidMoleculeException {
        String mode = subType;
        if ((mode != null) && !mode.equals("")) {
            PeakList newPeakList;
            Dataset dataset = null;
            String datasetName = "";
            if ((simDatasetNameField.getValue() == null) || simDatasetNameField.getValue().equals("Sim")) {
                newPeakList = makePeakListFromPars();
                if (newPeakList == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("Could not make peakList");
                    alert.showAndWait();
                    return;
                }
            } else {
                datasetName = simDatasetNameField.getValue().toString();
                dataset = Dataset.getDataset(datasetName);
                newPeakList = makePeakListFromDataset(dataset);
            }
            if (type.equals("Protein")) {
                makeProteinPeakList(dataset, newPeakList, subType);
            } else {
                switch (mode) {
                    case "NOESY":
                        double range = distanceSlider.getValue();
                        makeNOESY(newPeakList, range);
                        break;
                    case "TOCSY":
                        makeTocsy(newPeakList);
                        break;
                    case "HMBC":
                        int hmbcLimit = Integer.parseInt(transferLimitChoice.getValue().toString());
                        makeHMBC(newPeakList, hmbcLimit);
                        break;
                    case "HSQC-13C":
                        makeHSQC(newPeakList, 6);
                        break;
                    case "HSQC-15N":
                        makeHSQC(newPeakList, 7);
                        break;
                    case "RNA-NOESY-2nd-str":
                        break;
                    case "Proton-1D":
                        makeProton1D(newPeakList);
                    default:
                }
            }
        }
    }

    private void makeProteinPeakList(Dataset dataset, PeakList peakList, String expType) {
        PeakGenerator peakGenerator = new PeakGenerator();
        peakGenerator.generateProteinPeaks(dataset, peakList, expType);
    }

    private void makeTocsy(PeakList peakList) {
        PeakGenerator peakGenerator = new PeakGenerator();
        peakGenerator.generateTOCSY(peakList);
    }

    private void makeHSQC(PeakList peakList, int parentElement) {
        PeakGenerator peakGenerator = new PeakGenerator();
        peakGenerator.generateHSQC(peakList, 6);
    }

    private void makeHMBC(PeakList peakList, int limit) {
        PeakGenerator peakGenerator = new PeakGenerator();
        peakGenerator.generateHMBC(peakList, limit);
    }

    private void makeProton1D(PeakList peakList) {
        PeakGenerator peakGenerator = new PeakGenerator();
        peakGenerator.generate1DProton(peakList);
    }

    private void makeNOESY(PeakList peakList, double tol) throws InvalidMoleculeException {
        PeakGenerator peakGenerator = new PeakGenerator();
        peakGenerator.generateNOESY(peakList, tol);
    }
}
