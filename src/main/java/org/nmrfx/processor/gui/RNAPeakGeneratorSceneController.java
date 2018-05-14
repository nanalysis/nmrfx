/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.RNALabels;

/**
 * FXML Controller class
 *
 * @author Bruce Johnson
 */
public class RNAPeakGeneratorSceneController implements Initializable {

    @FXML
    GridPane adenosinePane;
    @FXML
    GridPane guanosinePane;
    @FXML
    GridPane uridinePane;
    @FXML
    GridPane cytidinePane;
    @FXML
    CheckBox d2oCheckBox;
    @FXML
    CheckBox carbonCheckBox;
    @FXML
    CheckBox nitrogenCheckBox;
    @FXML
    ListView<String> selGroupListView;
    @FXML
    Button clearSelGroupButton;
    @FXML
    Button clearAllSelGroupsButton;
    @FXML
    Button addSelGroupButton;
    @FXML
    Button loadSelGroupButton;
    @FXML
    Button applySelGroupButton;
    @FXML
    Button replaceSelGroupButton;
    @FXML
    Button showSelGroupButton;
    @FXML
    ChoiceBox<String> entityChoiceBox;
    @FXML
    TextField firstResidueField;
    @FXML
    TextField lastResidueField;
    @FXML
    private ComboBox<String> genDatasetNameField;

    ObservableList<String> selGroupList;

    Stage stage;
    String[][] baseAtoms = {
        {"H2", "C2", "H8", "C8"}, // Adenine
        {"H61", "H62", "N6"},
        {"H8", "C8"},// Guanine
        {"H21", "H22", "N2"},
        {"H5", "C5", "H6", "C6"},// Uridine
        {"H3", "N3"},
        {"H5", "C5", "H6", "C6"},//Cytosine
        {"H41", "H42", "N4"}
    };
    String[] riboseAtoms = {"H1'", "H2'", "H3'", "C1'", "C2'", "C3'", "H4'", "H5'", "H5''", "C4'", "C5'"};
    String[] baseChars = {"A", "G", "U", "C"};

    CheckBox[][][] checkBoxes = new CheckBox[4][3][];

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        GridPane[] gridPanes = {adenosinePane, guanosinePane, uridinePane, cytidinePane};

        double colWidth = 70;
        for (int iBase = 0; iBase < 4; iBase++) {
            gridPanes[iBase].getColumnConstraints().add(new ColumnConstraints(colWidth));
            gridPanes[iBase].getColumnConstraints().add(new ColumnConstraints(colWidth));
            gridPanes[iBase].getColumnConstraints().add(new ColumnConstraints(colWidth));
            gridPanes[iBase].getColumnConstraints().add(new ColumnConstraints(colWidth));
            int row = 0;
            Label label = new Label("Base");
            Button allButton = new Button("All");
            final int baseIndex = iBase;
            allButton.setOnAction(e -> {
                setAll(true, baseIndex, true);
            });
            Button noneButton = new Button("None");
            noneButton.setOnAction(e -> {
                setAll(true, baseIndex, false);
            });
            gridPanes[iBase].addRow(row, label, allButton, noneButton);
            row++;
            for (int iType = 0; iType < 2; iType++) {
                String[] atomNames = baseAtoms[iBase * 2 + iType];
                int iAtom = 0;
                checkBoxes[iBase][iType] = new CheckBox[atomNames.length];
                for (String aName : atomNames) {
                    CheckBox checkBox = new CheckBox(aName);
                    checkBoxes[iBase][iType][iAtom] = checkBox;
                    gridPanes[iBase].add(checkBox, iAtom, row);
                    if (aName.charAt(0) == 'C') {
                        checkBox.disableProperty().bind(carbonCheckBox.selectedProperty().not());
                    }
                    if (aName.charAt(0) == 'N') {
                        checkBox.disableProperty().bind(nitrogenCheckBox.selectedProperty().not());
                    }
                    if ((iType == 1) && aName.charAt(0) == 'H') {
                        checkBox.disableProperty().bind(d2oCheckBox.selectedProperty());
                    }

                    iAtom++;
                }
                row++;
            }
            Label riboLabel = new Label("Ribose");
            Button riboAllButton = new Button("All");
            riboAllButton.setOnAction(e -> {
                setAll(false, baseIndex, true);
            });
            Button riboNoneButton = new Button("None");
            riboNoneButton.setOnAction(e -> {
                setAll(false, baseIndex, false);
            });
            gridPanes[iBase].addRow(row, riboLabel, riboAllButton, riboNoneButton);
            row++;
            int col = 0;
            int iAtom = 0;
            checkBoxes[iBase][2] = new CheckBox[riboseAtoms.length];
            for (String aName : riboseAtoms) {
                CheckBox checkBox = new CheckBox(aName);
                checkBoxes[iBase][2][iAtom++] = checkBox;
                gridPanes[iBase].add(checkBox, col, row);
                if (aName.charAt(0) == 'C') {
                    checkBox.disableProperty().bind(carbonCheckBox.selectedProperty().not());
                }

                col++;
                if (col > 2) {
                    col = 0;
                    row++;
                }
            }
        }
        d2oCheckBox.setSelected(true);
        setD2O();
        d2oCheckBox.setOnAction(e -> setD2O());

        selGroupList = selGroupListView.getItems();
        clearSelGroupButton.disableProperty().bind(selGroupListView.getSelectionModel().selectedItemProperty().isNull());
        replaceSelGroupButton.disableProperty().bind(selGroupListView.getSelectionModel().selectedItemProperty().isNull());
        showSelGroupButton.disableProperty().bind(selGroupListView.getSelectionModel().selectedItemProperty().isNull());
        applySelGroupButton.disableProperty().bind(genDatasetNameField.valueProperty().isNull());
        loadSelGroupButton.disableProperty().bind(genDatasetNameField.valueProperty().isNull());
        genDatasetNameField.setOnShowing(e -> updateGenDatasetNames());
        entityChoiceBox.setOnShowing(e -> updateMolecule());
        updateGenDatasetNames();
        updateMolecule();
        entityChoiceBox.setValue("*");
    }

    public void updateGenDatasetNames() {
        genDatasetNameField.getItems().clear();
        Dataset.datasets().stream().sorted().forEach(d -> {
            genDatasetNameField.getItems().add(d.getName());
        });
    }

    void updateMolecule() {
        ObservableList<String> entityNames = FXCollections.observableArrayList();
        entityNames.add("*");
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            entityNames.addAll(mol.entities.keySet());
        }
        entityChoiceBox.getItems().setAll(entityNames);

    }

    void setD2O() {
        boolean state = d2oCheckBox.isSelected();
        for (int iBase = 0; iBase < 4; iBase++) {
            for (CheckBox checkBox : checkBoxes[iBase][1]) {
                if (checkBox.getText().startsWith("H")) {
                    checkBox.setSelected(!state);
                }
            }
        }
    }

    @FXML
    void setAllBasesOn() {
        setAll(0, true);
    }

    @FXML
    void setAllBasesOff() {
        setAll(0, false);
    }

    @FXML
    void setAllRibosesOn() {
        setAll(2, true);
    }

    @FXML
    void setAllRibosesOff() {
        setAll(2, false);
    }

    void setAll(int iType, boolean state) {
        for (int iBase = 0; iBase < 4; iBase++) {
            for (CheckBox checkBox : checkBoxes[iBase][iType]) {
                if (!checkBox.isDisabled()) {
                    checkBox.setSelected(state);
                }
            }
        }
    }

    void setAll(boolean isBase, int iBase, boolean state) {
        int index = 0;
        if (!isBase) {
            index = 2;
        }
        for (CheckBox checkBox : checkBoxes[iBase][index]) {
            if (!checkBox.isDisabled()) {
                checkBox.setSelected(state);
            }
        }
        if (isBase) {
            for (CheckBox checkBox : checkBoxes[iBase][1]) {
                if (!checkBox.isDisabled()) {
                    checkBox.setSelected(state);
                }
            }
        }
    }

    @FXML
    void clearSelGroup() {
        int index = selGroupListView.getSelectionModel().getSelectedIndex();
        if (index != -1) {
            selGroupList.remove(index);
        }
    }

    @FXML
    void clearAllSelGroups() {
        selGroupList.clear();
    }

    @FXML
    void addSelGroup() {
        String selGroup = genSelGroup();
        selGroupList.add(selGroup);
    }

    @FXML
    void replaceSelGroup() {
        String selGroup = genSelGroup();
        int index = selGroupListView.getSelectionModel().getSelectedIndex();
        if (index != -1) {
            selGroupList.set(index, selGroup);
        }

    }

    @FXML
    void applySelGroup() {
        Dataset dataset = Dataset.getDataset(genDatasetNameField.getValue());
        if (dataset != null) {
            StringBuilder sBuilder = new StringBuilder();
            for (String selGroup : selGroupList) {
                if (sBuilder.length() != 0) {
                    sBuilder.append(';');
                }
                sBuilder.append(selGroup.trim());
            }
            dataset.addProperty("selGroups", sBuilder.toString());
        }
    }

    @FXML
    void loadSelGroup() {
        Dataset dataset = Dataset.getDataset(genDatasetNameField.getValue());
        selGroupList.clear();
        if (dataset != null) {
            String selGroupPar = dataset.getProperty("selGroups");
            String[] selGroups = selGroupPar.split(";");
            for (String selGroup : selGroups) {
                selGroupList.add(selGroup);
            }
        }
    }

    @FXML
    void showSelGroup() {
        updateButtons();
    }

    @FXML
    public String genSelGroup() {
        String[] typeChars = {"n", "e", "r"};
        String[] atomChars = {"C", "N", "C"};
        String[] prefix = new String[4];
        String[] suffix = new String[4];
        for (int iBase = 0; iBase < 4; iBase++) {
            StringBuilder sBuilder = new StringBuilder();
            for (int iType = 0; iType < 3; iType++) {
                boolean allH = true;
                boolean allX = true;
                String[] atomNames;
                if (iType < 2) {
                    atomNames = baseAtoms[iBase * 2 + iType];
                } else {
                    atomNames = riboseAtoms;
                }
                int col = 0;
                int iAtom = 0;
                List<String> hNames = new ArrayList<>();
                List<String> xNames = new ArrayList<>();
                for (String aName : atomNames) {
                    CheckBox checkBox = checkBoxes[iBase][iType][iAtom++];
                    boolean status = !checkBox.isDisabled() && checkBox.isSelected();
                    if (aName.charAt(0) == 'H') {
                        if (status) {
                            hNames.add(aName);
                        } else {
                            allH = false;
                        }
                    } else if (aName.charAt(0) == 'C') {
                        if (status) {
                            xNames.add(aName);
                        } else {
                            allX = false;
                        }
                    } else if (aName.charAt(0) == 'N') {
                        if (status) {
                            xNames.add(aName);
                        } else {
                            allX = false;
                        }
                    }
                }
//                System.out.println(baseChars[iBase] + " " + iType + " " + allH + " " + allC + " " + allN);
//                System.out.println(hNames);
//                System.out.println(xNames);
                String sym = typeChars[iType];
                String xatomChar = atomChars[iType];
                if (allH) {
                    sBuilder.append(',').append("H").append(sym);
                } else {
                    for (String name : hNames) {
                        sBuilder.append(',').append(name);
                    }
                }
                if (allX) {
                    sBuilder.append(',').append(xatomChar).append(sym);
                } else {
                    for (String name : xNames) {
                        sBuilder.append(',').append(name);
                    }
                }
            }
            StringBuilder sAtoms = new StringBuilder();

            if (sBuilder.length() > 0) {
                sAtoms.append(entityChoiceBox.getValue()).append(":");
                sAtoms.append(baseChars[iBase]);
                String range = getResidueRange();
                sAtoms.append(range).append('.');
                prefix[iBase] = sAtoms.toString();
                suffix[iBase] = sBuilder.substring(1);
            } else {
                prefix[iBase] = "";
                suffix[iBase] = "";
            }
        }
        boolean allMatch = true;
        boolean empty = true;
        for (int iBase = 0; iBase < 4; iBase++) {
            if (!suffix[iBase].equals(suffix[0])) {
                allMatch = false;
            }
            if (!suffix[iBase].equals("")) {
                empty = false;
            }
        }
        StringBuilder result = new StringBuilder();
        if (!empty) {
            String entityStr = entityChoiceBox.getValue() + ":";
            if (!allMatch) {
                boolean[] done = new boolean[4];
                for (int iBase = 0; iBase < 4; iBase++) {
                    if (!done[iBase]) {
                        done[iBase] = true;
                        StringBuilder baseValue = new StringBuilder();
                        baseValue.append(baseChars[iBase]);
                        for (int jBase = iBase + 1; jBase < 4; jBase++) {
                            if (suffix[iBase].equals(suffix[jBase])) {
                                baseValue.append(',').append(baseChars[jBase]);
                                done[jBase] = true;
                            }
                        }
                        String range = getResidueRange();
                        baseValue.append(range).append('.');
                        if (suffix[iBase].length() > 0) {
                            if (iBase != 0) {
                                result.append(' ');
                            }
                            result.append(entityStr).append(baseValue.toString()).append(suffix[iBase]);
                        }
                    }
                }
            } else {
                result.append(entityStr);
                String range = getResidueRange();
                result.append(range).append('.');
                result.append(suffix[0]);
            }
        }
        System.out.println("selAtoms: " + result.toString());
        return result.toString().trim();
    }

    public String getResidueRange() {
        String range = "*";
        String firstResidue = firstResidueField.getText().trim();
        String lastResidue = lastResidueField.getText().trim();
        if (!firstResidue.equals("") || !lastResidue.equals("")) {
            if (!firstResidue.equals("") && !lastResidue.equals("")) {
                if (firstResidue.equals(lastResidue)) {
                    range = firstResidue;
                } else {
                    range = firstResidue + "-" + lastResidue;
                }
            } else if (firstResidue.equals("") && !lastResidue.equals("")) {
                range = "-" + lastResidue;
            } else if (!firstResidue.equals("") && lastResidue.equals("")) {
                range = firstResidue + "-";
            }
        }
        return range;

    }

    void clearAllButtons() {
        for (int iBase = 0; iBase < 4; iBase++) {
            for (int iType = 0; iType < 3; iType++) {
                for (CheckBox checkBox : checkBoxes[iBase][iType]) {
                    checkBox.setSelected(false);
                }
            }
        }

    }

    void updateButtons() {
        clearAllButtons();
        firstResidueField.setText("");
        lastResidueField.setText("");
        entityChoiceBox.setValue("*");
        int index = selGroupListView.getSelectionModel().getSelectedIndex();
        if (index != -1) {
            String selGroupEntry = selGroupList.get(index);
            if (selGroupEntry.length() > 0) {
                String[] selGroupStrs = selGroupEntry.split(" ");
                for (String selGroupStr : selGroupStrs) {
                    RNALabels.SelGroup selGroup = RNALabels.parseSelGroup(selGroupStr);
                    if (selGroup.firstRes != null) {
                        firstResidueField.setText(String.valueOf(selGroup.firstRes));
                    }
                    if (selGroup.lastRes != null) {
                        lastResidueField.setText(String.valueOf(selGroup.lastRes));
                    }
                    entityChoiceBox.setValue(selGroup.entityStr);
                    for (int iBase = 0; iBase < 4; iBase++) {
                        if (RNALabels.checkResType(baseChars[iBase], selGroup.resTypes)) {
                            for (int iType = 0; iType < 3; iType++) {
                                for (CheckBox checkBox : checkBoxes[iBase][iType]) {
                                    boolean exchangeable = iType == 1;
                                    boolean ribose = iType == 2;
                                    boolean state = RNALabels.checkAtom(checkBox.getText(), checkBox.getText().substring(0, 1), selGroup.gAtomNames, ribose, exchangeable);
                                    if (state) {
                                        checkBox.setSelected(true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public Stage getStage() {
        return stage;
    }

    public static RNAPeakGeneratorSceneController create() {
        FXMLLoader loader = new FXMLLoader(MinerController.class.getResource("/fxml/RNAPeakGeneratorScene.fxml"));
        RNAPeakGeneratorSceneController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");
            scene.getStylesheets().add("/styles/rnapeakgeneratorscene.css");

            controller = loader.<RNAPeakGeneratorSceneController>getController();
            controller.stage = stage;
            stage.setTitle("RNA Peak Generator");
            stage.setAlwaysOnTop(true);
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

}
