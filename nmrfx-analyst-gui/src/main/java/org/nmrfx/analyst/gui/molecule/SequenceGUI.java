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

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.chemistry.io.AtomParser;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.Sequence;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class SequenceGUI {
    private static Stage stage = null;

    AnalystApp analystApp;
    ChoiceBox<String> polymerType;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 900, 600);
    TextField molNameField;
    TextField polymerNameField;
    TextArea textArea = new TextArea();

    public SequenceGUI(AnalystApp analystApp) {
        this.analystApp = analystApp;
    }

    public static void showGUI(AnalystApp analystApp) {
        if (stage == null) {
            SequenceGUI seqGui = new SequenceGUI(analystApp);
            seqGui.create();
        }
        if (stage != null) {
            stage.show();
            stage.toFront();
        }
    }

    void create() {
        //Create new Stage for popup window
        stage = new Stage();
        stage.setTitle("Sequence GUI");
        textArea.setPrefHeight(200);
        ToolBar toolBar = new ToolBar();
        polymerType = new ChoiceBox<>();
        polymerType.getItems().addAll("Protein", "RNA", "DNA");
        Button openButton = new Button("Add Entity");
        openButton.setOnAction(e -> addEntity());
        Label typeLabel = new Label("Type: ");
        Label molNameLabel = new Label("MolName: ");
        molNameField = new TextField();
        Label polymerNameLabel = new Label("Chain: ");
        polymerNameField = new TextField("A");

        toolBar.getItems().addAll(typeLabel, polymerType, molNameLabel,
                molNameField, polymerNameLabel, polymerNameField, openButton);
        borderPane.setTop(toolBar);
        textArea.setPromptText("Enter a single character sequence (spaces and digits will be ignored");
        borderPane.setCenter(textArea);
        stage.setScene(stageScene);
    }

    void addEntity() {
        if (!MoleculeMenuActions.checkForExisting()) {
            return;
        }
        String type = polymerType.getValue();
        if ((type == null) || (type.equals(""))) {
            GUIUtils.warn("Polymer", "Please select a polymer type");
            return;
        }
        String singleChars = textArea.getText().trim();
        String molName = molNameField.getText().trim();
        if (molName.length() == 0) {
            GUIUtils.warn("Molecule Name", "Please enter a molecule name");
            return;
        }
        String polymerName = polymerNameField.getText().trim();
        if (polymerName.length() == 0) {
            GUIUtils.warn("Polymer Name", "Please enter a chain name");
            return;
        }
        Sequence sequence = new Sequence();
        List<String> seqList = new ArrayList<>();
        for (char ch : singleChars.toCharArray()) {
            if (Character.isLetter(ch)) {
                String resName = String.valueOf(ch);
                if (type.equals("Protein")) {
                    resName = AtomParser.convert1To3(resName);
                }
                seqList.add(resName);

            }
        }
        if (!seqList.isEmpty()) {
            try {
                Molecule mol = (Molecule) sequence.read(polymerName, seqList, "", molName);
                mol.setActive();
            } catch (MoleculeIOException ex) {
                GUIUtils.warn("Sequence Error", ex.getMessage());
            }
            textArea.setText("");
        }
    }
}
