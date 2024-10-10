package org.nmrfx.analyst.gui.molecule;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.nmrfx.structure.chemistry.predict.Predictor.PredictionModes;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * FXML Controller class
 *
 * @author brucejohnson
 */
public class PredictorSceneController implements Initializable, StageBasedController {

    Stage stage = null;
    AtomController atomController;
    @FXML
    ChoiceBox<String> targetType;
    @FXML
    ChoiceBox<Integer> targetChoice;
    @FXML
    ChoiceBox<Predictor.PredictionModes> proteinChoice;
    @FXML
    ChoiceBox<Predictor.PredictionModes> rnaChoice;
    @FXML
    ChoiceBox<Predictor.PredictionModes> molChoice;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        targetType.getItems().addAll("Ref Set", "PPM Set");
        targetType.setValue("Ref Set");
        targetChoice.getItems().addAll(0, 1, 2, 3, 4);
        targetChoice.setValue(0);
        proteinChoice.getItems().addAll(PredictionModes.OFF, PredictionModes.THREED, PredictionModes.SHELL);
        proteinChoice.setValue(PredictionModes.THREED);
        rnaChoice.getItems().addAll(PredictionModes.OFF, PredictionModes.RNA_ATTRIBUTES, PredictionModes.THREED_DIST, PredictionModes.THREED_RC);
        rnaChoice.setValue(PredictionModes.RNA_ATTRIBUTES);
        molChoice.getItems().addAll(PredictionModes.OFF, PredictionModes.SHELL);
        molChoice.setValue(PredictionModes.SHELL);
    }

    public static PredictorSceneController create(AtomController atomController) {
        PredictorSceneController controller = Fxml.load(PredictorSceneController.class, "PredictorScene.fxml")
                .withNewStage("Predictor")
                .getController();
        controller.atomController = atomController;
        controller.stage.show();
        controller.stage.toFront();
        return controller;
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    @FXML
    public void predict() {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
            return;
        }

        try {
            int target = targetChoice.getValue();
            int ppmSet = 0;
            if (targetType.getValue().equals("Ref Set")) {
                ppmSet = -target - 1;
            } else {
                ppmSet = target;
            }
            Predictor.PredictionTypes predictionTypes = new Predictor.PredictionTypes(proteinChoice.getValue(), rnaChoice.getValue(), molChoice.getValue());
            predictMolecule(molecule, predictionTypes, ppmSet);
            atomController.refreshAtomTable();
        } catch (InvalidMoleculeException | IOException ex) {
            ExceptionDialog dialog = new ExceptionDialog(ex);
            dialog.showAndWait();
        }

    }

    boolean checkDotBracket(Molecule molecule) {
        if (molecule.getDotBracket().equals("")) {
            TextInputDialog textDialog = new TextInputDialog("Enter dot-bracket sequence");
            Optional<String> result = textDialog.showAndWait();
            if (result.isPresent()) {
                String dotBracket = result.get().trim();
                if (dotBracket.equals("")) {
                    return false;
                }
                molecule.setDotBracket(dotBracket);
            } else {
                return false;
            }
        }
        return true;
    }


    public void predictMolecule(Molecule mol, Predictor.PredictionTypes predictionTypes, int ppmSet) throws InvalidMoleculeException, IOException {
        if (mol == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
            return;
        }
        Predictor predictor = new Predictor();
        predictor.predictAll(mol, predictionTypes, ppmSet);
    }

}
