/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.converter.DoubleStringConverter;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.util.converter.FloatStringConverter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.peaks.AtomResonanceFactory;
import org.nmrfx.processor.datasets.peaks.FreezeListener;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.project.StructureProject;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.MolFilter;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.ProteinPredictor;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.io.MoleculeIOException;
import org.nmrfx.structure.chemistry.io.PPMFiles;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.python.util.PythonInterpreter;

/**
 *
 * @author johnsonb
 */
public class AtomController implements Initializable, FreezeListener {

    static final DecimalFormat formatter = new DecimalFormat();
    static Map<String, String> filterMap = new HashMap<>();

    static {
        filterMap.put("Backbone", "*.H,N,HN,C,CA,CB");
        filterMap.put("Carbons", "*.C*");
        filterMap.put("Nitrogen", "*.N*");
        filterMap.put("H", "*.H*");
        filterMap.put("Phosphorous", "*.P*");
        filterMap.put("Amide", "*.H,HN,N");
        filterMap.put("RNA-H", "*.H8,H2,H6,H5,H1',H2',H3',H4',H5',H5''");
        filterMap.put("RNA-HC", "*.H8,C8,H2,C2,H6,C6,H5,C5,H1',C1',H2',C2',H3',C3',H4',C4',H5',C5'*,H5''");
        filterMap.put("HCN", "*.H*,C*,N*");
        filterMap.put("HC", "*.H*,C*");
    }
    private Stage stage;
    @FXML
    private ToolBar menuBar;
    @FXML
    private ToolBar atomNavigatorToolBar;
    @FXML
    private TableView<Atom> atomTableView;
    @FXML
    private TextField intensityField;
    @FXML
    private TextField volumeField;
    @FXML
    private TextField commentField;
    @FXML
    private TextField atomListNameField;
    @FXML
    private MenuButton molFilterMenuButton;
    @FXML
    private TextField molFilterTextField;
    @FXML
    private ToolBar atomReferenceToolBar;
    ObservableList<Atom> atoms = FXCollections.observableArrayList();

    Atom currentAtom;

    MolFilter molFilter = new MolFilter("*.C*,H*,N*");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initMenuBar();
        initTable();
        setFieldActions();
        filterMap.keySet().stream().sorted().forEach(filterName -> {
            MenuItem backBoneItem = new MenuItem(filterName);
            backBoneItem.setOnAction(e -> setFilterString(filterName));
            molFilterMenuButton.getItems().add(backBoneItem);
        });
        molFilterTextField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                updateView();
            }
        });
        PeakList.registerFreezeListener(this);
        updateView();
    }

    public Stage getStage() {
        return stage;
    }

    public static AtomController create() {
        FXMLLoader loader = new FXMLLoader(AtomController.class.getResource("/fxml/AtomScene.fxml"));
        AtomController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<AtomController>getController();
            controller.stage = stage;
            stage.setTitle("Atom Attributes");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    private void clearInsepctor() {
        atomTableView.getItems().clear();
        intensityField.setText("");
        volumeField.setText("");
        commentField.setText("");

    }

    void initMenuBar() {
        MenuButton fileMenu = new MenuButton("File");

        MenuItem readMolItem = new MenuItem("Read Mol...");
        readMolItem.setOnAction(e -> readMolecule());
        fileMenu.getItems().add(readMolItem);

        MenuItem writePPMItem = new MenuItem("Write PPM...");
        writePPMItem.setOnAction(e -> writePPM());
        fileMenu.getItems().add(writePPMItem);

        MenuItem readPPMItem = new MenuItem("Read PPM...");
        readPPMItem.setOnAction(e -> readPPM());
        fileMenu.getItems().add(readPPMItem);

        menuBar.getItems().add(fileMenu);

        MenuButton editMenu = new MenuButton("Edit");
        MenuItem clearPPMItem = new MenuItem("Clear Assigned Shifts");
        clearPPMItem.setOnAction(e -> {
            clearPPMs();
            atomTableView.refresh();
        }
        );
        MenuItem clearRefItem = new MenuItem("Clear Ref Shifts");
        clearRefItem.setOnAction(e -> {
            clearRefPPMs();
            atomTableView.refresh();
        }
        );
        MenuItem getPPMItem = new MenuItem("Get Frozen PPM");
        getPPMItem.setOnAction(e -> {
            ((AtomResonanceFactory) PeakDim.resFactory).assignFrozenAtoms("sim");
            atomTableView.refresh();
        }
        );
        editMenu.getItems().addAll(clearPPMItem, clearRefItem, getPPMItem);

        menuBar.getItems().add(editMenu);
        MenuButton predictMenu = new MenuButton("Predict");
        menuBar.getItems().add(predictMenu);
        MenuItem rnaAttributesItem = new MenuItem("RNA - Attributes");
        rnaAttributesItem.setOnAction(e -> predictRNAWithAttributes(e));
        MenuItem rna3DItem = new MenuItem("RNA - 3D");
        rna3DItem.setOnAction(e -> predictRNAWithStructure(e));
        MenuItem protein3DItem = new MenuItem("Protein - 3D");
        protein3DItem.setOnAction(e -> predictProteinWithStructure(e));
        MenuItem universalItem = new MenuItem("Universal");
        universalItem.setOnAction(e -> predictAll(e));
        predictMenu.getItems().addAll(rnaAttributesItem, rna3DItem, protein3DItem, universalItem);
    }

    @Override
    public void freezeHappened(Peak peak, boolean state) {
        if (Platform.isFxApplicationThread()) {
            atomTableView.refresh();
        } else {
            Platform.runLater(() -> {
                atomTableView.refresh();
            }
            );
        }
    }

    class FloatStringConverter2 extends FloatStringConverter {

        public Float fromString(String s) {
            Float v;
            try {
                v = Float.parseFloat(s);
            } catch (NumberFormatException nfE) {
                v = null;
            }
            return v;
        }

    }

    class TextFieldTableCellDouble extends TextFieldTableCell<Atom, Double> {

        public TextFieldTableCellDouble(StringConverter s) {
            super(s);
        }

        @Override
        public void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            if (item != null) {
                setText(String.valueOf(item));
            } else {
            }
        }
    };

    class ComboTableCell<SpectralDim, String> extends ComboBoxTableCell<SpectralDim, String> {

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
        }
    };

    void initTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
        FloatStringConverter fsConverter = new FloatStringConverter2();
        atomTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<Atom, String> atomNameCol = new TableColumn<>("Atom");
        atomNameCol.setCellValueFactory(new PropertyValueFactory("Name"));
        atomNameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        atomNameCol.setEditable(false);

        TableColumn<Atom, String> entityNameColumn = new TableColumn<>("Entity");
        entityNameColumn.setCellValueFactory(new PropertyValueFactory("PolymerName"));
        entityNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        entityNameColumn.setEditable(false);

        TableColumn<Atom, Integer> residueNumberColumn = new TableColumn<>("Seq");
        residueNumberColumn.setCellValueFactory(new PropertyValueFactory("ResidueNumber"));
        residueNumberColumn.setEditable(false);

        TableColumn<Atom, Integer> indexColumn = new TableColumn<>("Index");
        indexColumn.setCellValueFactory(new PropertyValueFactory("Index"));
        indexColumn.setEditable(false);

        TableColumn<Atom, String> residueNameColumn = new TableColumn<>("Res");
        residueNameColumn.setCellValueFactory(new PropertyValueFactory("ResidueName"));
        residueNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        residueNameColumn.setEditable(false);

        TableColumn<Atom, Double> ppmCol = new TableColumn<>("PPM");
        ppmCol.setCellValueFactory(new PropertyValueFactory("PPM"));
        ppmCol.setCellFactory(tc -> new TextFieldTableCellDouble(dsConverter));
        ppmCol.setOnEditCommit(
                (CellEditEvent<Atom, Double> t) -> {
                    Double value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setPPM(value);
                    }
                });

        ppmCol.setEditable(true);

        TableColumn<Atom, Double> refCol = new TableColumn<>("Ref PPM");
        refCol.setCellValueFactory(new PropertyValueFactory("RefPPM"));

        refCol.setEditable(false);

        atomTableView.getColumns().setAll(indexColumn, entityNameColumn, residueNameColumn, residueNumberColumn, atomNameCol, ppmCol, refCol);
    }

    private void setFilterString(String filterName) {
        String filterString = filterMap.get(filterName);
        if (filterString == null) {
            filterString = "";
        }
        molFilterTextField.setText(filterString);
        updateView();
    }

    public void updateView() {
        String filterString = molFilterTextField.getText().trim();
        molFilter = new MolFilter(filterString);
        Molecule molecule = Molecule.getActive();
        atoms.clear();
        if (molecule != null) {
            try {
                Molecule.selectAtomsForTable(molFilter, atoms);
            } catch (InvalidMoleculeException ex) {
            }
        }
        atomTableView.setItems(atoms);
    }

    private void setFieldActions() {
    }

    void writePPM() {
        try {
            FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                String listFileName = file.getPath();
                Molecule molecule = Molecule.getActive();
                if (molecule != null) {

                    try (FileWriter writer = new FileWriter(listFileName)) {
                        PPMFiles.writePPM(molecule, writer, 0, false);
                    }
                }
            }
        } catch (IOException ioE) {
            ExceptionDialog dialog = new ExceptionDialog(ioE);
            dialog.showAndWait();
        }
    }

    void readPPM() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            Path path = file.toPath();
            Molecule molecule = Molecule.getActive();
            if (molecule != null) {
                PPMFiles.readPPM(molecule, path, 0, false);
            }
            atomTableView.refresh();
        }
    }

    @FXML
    void predictRNAWithAttributes(ActionEvent e) {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
        } else {
            if (molecule.getDotBracket().equals("")) {
                TextInputDialog textDialog = new TextInputDialog("Enter dot-bracket sequence");
                Optional<String> result = textDialog.showAndWait();
                if (result.isPresent()) {
                    String dotBracket = result.get().trim();
                    if (dotBracket.equals("")) {
                        return;
                    }
                    molecule.setDotBracket(dotBracket);
                } else {
                    return;
                }
            }
            PythonInterpreter interp = MainApp.getInterpreter();
            interp.exec("import rnapred\nrnapred.predictFromSequence()");
            atomTableView.refresh();
        }
    }

    @FXML
    void predictRNAWithStructure(ActionEvent e) {
        predictShiftsWithStructure(true);
    }

    @FXML
    void predictProteinWithStructure(ActionEvent e) {
        predictShiftsWithStructure(false);
    }

    void predictShiftsWithStructure(boolean isRNA) {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
        } else if (molecule.structures.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule coordinates", ButtonType.CLOSE);
            alert.showAndWait();
        } else {
            try {
                if (isRNA) {
                    PythonInterpreter interp = MainApp.getInterpreter();
                    interp.exec("import refine\nrefiner=refine.refine()\nrefiner.predictShifts()");
                } else {
                    predictProtein(molecule, 0);
                }
            } catch (Exception ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
        atomTableView.refresh();
    }

    void predictProtein(Molecule molecule, int ppmSet) throws InvalidMoleculeException {
        List<Polymer> polymers = molecule.getPolymers();
        for (Polymer polymer : polymers) {
            ProteinPredictor predictor = new ProteinPredictor(polymer);
            for (Residue residue : polymer.getResidues()) {
                for (Atom atom : residue.getAtoms()) {
                    int aNum = atom.getAtomicNumber();
                    if ((aNum == 1) || (aNum == 6) || (aNum == 7)) {
                        Double value = predictor.predict(atom, false);
                        if (value != null) {
                            atom.setRefPPM(ppmSet, value);
                        }
                    }
                }

            }
        }
        atomTableView.refresh();

    }

    @FXML
    void predictAll(ActionEvent e) {
        Predictor predictor = new Predictor();
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            try {
                predictor.predictMolecule(mol, 0);
            } catch (InvalidMoleculeException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
        atomTableView.refresh();

    }

    void clearPPMs() {
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> atoms = mol.getAtoms();
            for (Atom atom : atoms) {
                atom.setPPMValidity(0, false);
            }
        }
        atomTableView.refresh();

    }

    void clearRefPPMs() {
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> atoms = mol.getAtoms();
            for (Atom atom : atoms) {
                PPMv ppmV = atom.getRefPPM(0);
                if (ppmV != null) {
                    ppmV.setValid(false, atom);
                }
            }
        }
        atomTableView.refresh();
    }

    void readMolecule() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            Path path = file.toPath();
            try {
                StructureProject.loadMolecule(path);
            } catch (MoleculeIOException ioE) {
                ExceptionDialog dialog = new ExceptionDialog(ioE);
                dialog.showAndWait();
            }
        }
        setFilterString("");
    }
}
