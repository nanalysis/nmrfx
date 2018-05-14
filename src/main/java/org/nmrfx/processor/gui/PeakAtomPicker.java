package org.nmrfx.processor.gui;

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
import org.nmrfx.structure.chemistry.Molecule;

/**
 *
 * @author Bruce Johnson
 */
public class PeakAtomPicker {

    Stage stage;
    BorderPane borderPane;
    ChoiceBox[] atomChoices;
    ChoiceBox[] entityChoices;
    TextField[] atomFields;
    double xOffset = 50;
    double yOffset = 50;

    public void create() {
        stage = new Stage(StageStyle.DECORATED);
        borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("RNA Peak Picker");
        stage.setAlwaysOnTop(true);

        Button pickButton = new Button("Pick");
        int nDim = 2;
        HBox hBox = new HBox();
        borderPane.setCenter(hBox);
        double width1 = 200;
        double width2 = 125;
        atomChoices = new ChoiceBox[nDim];
        entityChoices = new ChoiceBox[nDim];
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
        }
        borderPane.setBottom(pickButton);
        stage.setAlwaysOnTop(true);
        stage.show();
    }
    
    public void show(double x, double y) {
        stage.show();
        stage.toFront();
        
        stage.setX(x+xOffset);
        stage.setY(y+yOffset);
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            for (ChoiceBox choiceBox: entityChoices) {
                choiceBox.getItems().setAll(mol.entities.keySet());
            }
        }
    }
    
    void doPick() {
        stage.close();
    }

}
