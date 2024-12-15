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
package org.nmrfx.analyst.gui.molecule;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.FloatStringConverter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.tools.LACSPlotGui;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.chemistry.io.PPMFiles;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.FreezeListener;
import org.nmrfx.processor.gui.utils.AtomUpdater;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.ParseException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.predict.BMRBStats;
import org.nmrfx.structure.chemistry.predict.ProteinPredictor;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

/**
 * @author johnsonb
 */
public class AtomController implements Initializable, StageBasedController, FreezeListener, MoleculeListener, PropertyChangeListener {
    private static final Logger log = LoggerFactory.getLogger(AtomController.class);

    static final Map<String, String> filterMap = new HashMap<>();
    PredictorSceneController predictorController = null;

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
    ChoiceBox<Integer> ppmSetChoice;
    ChoiceBox<Integer> refSetChoice;
    ObservableList<Atom> atoms = FXCollections.observableArrayList();

    MolFilter molFilter = new MolFilter("*.C*,H*,N*");

    LACSPlotGui lacsPlotGui = null;

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
        ProjectBase.addPropertyChangeListener(this::propertyChange);
        updateView();
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public static AtomController create() {
        AtomController controller = Fxml.load(AtomController.class, "AtomScene.fxml")
                .withNewStage("Atom Attributes")
                .getController();
        controller.stage.show();

        AnalystApp.addMoleculeListener(controller::moleculeMapChanged);
        return controller;
    }

    private void moleculeMapChanged(MapChangeListener.Change<? extends String, ? extends MoleculeBase> change) {
        if (MoleculeFactory.getMolecules().isEmpty()) {
            setFilterString("");
            refreshAtomTable();
        }
    }

    public void refreshAtomTable() {
        atomTableView.refresh();
    }

    void initMenuBar() {
        MenuButton fileMenu = new MenuButton("File");

        MenuItem readCoordinatesItem = new MenuItem("Read Multiple Coordinates...");
        readCoordinatesItem.setOnAction(e -> readCoordinates());
        fileMenu.getItems().add(readCoordinatesItem);

        MenuItem writePPMItem = new MenuItem("Write PPM...");
        writePPMItem.setOnAction(e -> writePPM());
        fileMenu.getItems().add(writePPMItem);

        MenuItem readPPMItem = new MenuItem("Read PPM...");
        readPPMItem.setOnAction(e -> readPPM(false));
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
                    ProjectBase.activeResonanceFactory().assignFrozenAtoms("sim");
                    atomTableView.refresh();
                }
        );
        MenuItem getAllPPMItem = new MenuItem("Get PPM");
        getAllPPMItem.setOnAction(e -> {
                    ProjectBase.activeResonanceFactory().assignFromPeaks(null);
                    atomTableView.refresh();
                }
        );
        editMenu.getItems().addAll(clearPPMItem, clearRefItem, getPPMItem, getAllPPMItem);

        menuBar.getItems().add(editMenu);

        MenuButton refMenu = new MenuButton("Reference");
        menuBar.getItems().add(refMenu);

        MenuItem bmrbRefItem = new MenuItem("BMRB Mean");
        bmrbRefItem.setOnAction(e -> loadBMRBStats());
        refMenu.getItems().addAll(bmrbRefItem);

        MenuItem peptideRandomItem = new MenuItem("Peptide Random");
        peptideRandomItem.setOnAction(e -> getRandomPPM());
        refMenu.getItems().addAll(peptideRandomItem);

        MenuItem readRefPPMItem = new MenuItem("Read PPM...");
        readRefPPMItem.setOnAction(e -> readPPM(true));
        refMenu.getItems().add(readRefPPMItem);

        MenuItem lacsPlotItem = new MenuItem("LACS Plot...");
        lacsPlotItem.setOnAction(e -> showLACSPlot());
        refMenu.getItems().add(lacsPlotItem);

        MenuButton predictMenu = new MenuButton("Predict");
        menuBar.getItems().add(predictMenu);
        MenuItem preditorMenuItem = new MenuItem("Predictor");
        preditorMenuItem.setOnAction(e -> showPredictor());
        predictMenu.getItems().addAll(preditorMenuItem);

        ppmSetChoice = new ChoiceBox<>();
        refSetChoice = new ChoiceBox<>();
        for (int iSet = 0; iSet < 5; iSet++) {
            ppmSetChoice.getItems().add(iSet);
            refSetChoice.getItems().add(iSet);
        }
        ppmSetChoice.setValue(0);
        refSetChoice.setValue(0);
        menuBar.getItems().addAll(new Label("PPM Set:"), ppmSetChoice,
                new Label("Ref Set:"), refSetChoice);
        ppmSetChoice.setOnAction(e -> atomTableView.refresh());
        refSetChoice.setOnAction(e -> atomTableView.refresh());
    }

    private void showLACSPlot() {
        if (lacsPlotGui == null) {
            lacsPlotGui = new LACSPlotGui();
        }
        lacsPlotGui.showMCplot();
    }
    @Override
    public void freezeHappened(Peak peak, boolean state) {
        Fx.runOnFxThread(atomTableView::refresh);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (Objects.equals(evt.getPropertyName(), "molecule")) {
            updateView();
            Molecule activeMol = Molecule.getActive();
            if (activeMol != null) {
                AtomUpdater atomUpdater = new AtomUpdater(activeMol);
                activeMol.registerUpdater(atomUpdater);
                activeMol.registerAtomChangeListener(this);
            }
        }
    }

    class DoubleStringConverter4 extends DoubleStringConverter {

        @Override
        public String toString(Double v) {
            if (v == null) {
                return "";
            } else {
                return String.format("%.4f", v);
            }

        }

    }

    class FloatStringConverter2 extends FloatStringConverter {

        @Override
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
            }
        }
    }


    class TextFieldTableCellNumber extends TextFieldTableCell<Atom, Number> {

        public TextFieldTableCellNumber(StringConverter s) {
            super(s);
        }

        @Override
        public void updateItem(Number item, boolean empty) {
            super.updateItem(item, empty);
            if (item != null) {
                setText(String.valueOf(item));
            }
        }
    }

    int getPPMSet() {
        return ppmSetChoice.getValue();
    }

    int getRefSet() {
        return refSetChoice.getValue();
    }

    void initTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
        DoubleStringConverter dsConverter4 = new DoubleStringConverter4();
        FloatStringConverter fsConverter = new FloatStringConverter2();
        atomTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        atomTableView.setEditable(true);

        TableColumn<Atom, String> atomNameCol = new TableColumn<>("Atom");
        atomNameCol.setCellValueFactory(new PropertyValueFactory<>("Name"));
        atomNameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        atomNameCol.setEditable(false);

        TableColumn<Atom, String> entityNameColumn = new TableColumn<>("Entity");
        entityNameColumn.setCellValueFactory(new PropertyValueFactory<>("PolymerName"));
        entityNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        entityNameColumn.setEditable(false);

        TableColumn<Atom, Integer> residueNumberColumn = new TableColumn<>("Seq");
        residueNumberColumn.setCellValueFactory(new PropertyValueFactory<>("ResidueNumber"));
        residueNumberColumn.setEditable(false);

        TableColumn<Atom, Integer> indexColumn = new TableColumn<>("Index");
        indexColumn.setCellValueFactory(new PropertyValueFactory<>("Index"));
        indexColumn.setEditable(false);

        TableColumn<Atom, String> residueNameColumn = new TableColumn<>("Res");
        residueNameColumn.setCellValueFactory(new PropertyValueFactory<>("ResidueName"));
        residueNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        residueNameColumn.setEditable(false);

        TableColumn<Atom, Number> ppmCol = new TableColumn<>("PPM");

        ppmCol.setCellValueFactory((CellDataFeatures<Atom, Number> p) -> {
            Atom atom = p.getValue();
            int ppmSet = getPPMSet();
            PPMv ppmVal = atom.getPPM(ppmSet);
            ObservableValue<Number> ov;
            if ((ppmVal != null) && ppmVal.isValid()) {
                ov = new SimpleDoubleProperty(ppmVal.getValue());
            } else {
                ov = null;
            }
            return ov;
        });

        ppmCol.setOnEditCommit(
                (CellEditEvent<Atom, Number> t) -> {
                    Number value = t.getNewValue();
                    if (value != null) {
                        int ppmSet = getPPMSet();
                        t.getRowValue().setPPM(ppmSet, value.doubleValue());
                    }
                });

        ppmCol.setCellFactory(tc -> new TextFieldTableCellNumber(dsConverter4));
        ppmCol.setEditable(true);

        TableColumn<Atom, Number> refCol = new TableColumn<>("Ref");
        refCol.setCellValueFactory((CellDataFeatures<Atom, Number> p) -> {
            Atom atom = p.getValue();
            int refSet = getRefSet();
            PPMv ppmVal = atom.getRefPPM(refSet);
            ObservableValue<Number> ov;
            if ((ppmVal != null) && ppmVal.isValid()) {
                ov = new SimpleDoubleProperty(ppmVal.getValue());
            } else {
                ov = null;
            }
            return ov;
        });

        refCol.setCellFactory(tc -> new TextFieldTableCellNumber(dsConverter4));
        refCol.setEditable(false);

        TableColumn<Atom, Number> sdevCol = new TableColumn<>("SDev");
        sdevCol.setCellValueFactory((CellDataFeatures<Atom, Number> p) -> {
            Atom atom = p.getValue();
            int ppmSet = getRefSet();
            PPMv ppmVal = atom.getRefPPM(ppmSet);
            ObservableValue<Number> ov;
            if ((ppmVal != null) && ppmVal.isValid()) {
                ov = new SimpleDoubleProperty(ppmVal.getError());
            } else {
                ov = null;
            }
            return ov;
        });

        sdevCol.setCellFactory(tc -> new TextFieldTableCellNumber(dsConverter4));
        sdevCol.setEditable(false);

        TableColumn<Atom, Number> deltaCol = new TableColumn<>("Delta");
        deltaCol.setCellValueFactory((CellDataFeatures<Atom, Number> p) -> {
            int ppmSet = getPPMSet();
            int refSet = getRefSet();
            Atom atom = p.getValue();
            Double delta = atom.getDeltaPPM(ppmSet, refSet);
            ObservableValue<Number> ov;
            if (delta != null) {
                ov = new SimpleDoubleProperty(delta);
            } else {
                ov = null;
            }
            return ov;
        });
        deltaCol.setCellFactory(tc -> new TextFieldTableCellNumber(dsConverter4));

        deltaCol.setEditable(false);

        atomTableView.getColumns().setAll(indexColumn, entityNameColumn,
                residueNameColumn, residueNumberColumn, atomNameCol, ppmCol,
                refCol, sdevCol, deltaCol);
    }

    public void setFilterString(String filterName) {
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
                log.warn(ex.getMessage(), ex);
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

    void readPPM(boolean refMode) {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            Path path = file.toPath();
            Molecule molecule = Molecule.getActive();
            if (molecule != null) {
                int iSet = refMode
                        ? refSetChoice.getValue() : ppmSetChoice.getValue();
                if (file.getName().endsWith(".str")) {
                    if (refMode) {
                        iSet = -1 - iSet;
                    }
                    try {
                        NMRStarReader.readChemicalShifts(file, iSet);
                    } catch (ParseException ex) {
                        GUIUtils.warn("Error reading .str file", ex.getMessage());
                        return;
                    }
                } else {
                    PPMFiles.readPPM(molecule, path, iSet, refMode);
                }
            }
            atomTableView.refresh();
        }
    }

    void clearPPMs() {
        int ppmSet = ppmSetChoice.getValue();
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> molAtoms = mol.getAtoms();
            for (Atom atom : molAtoms) {
                atom.setPPMValidity(ppmSet, false);
            }
        }
        atomTableView.refresh();

    }

    void getRandomPPM() {
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            ProteinPredictor predictor = new ProteinPredictor();
            try {
                predictor.predictRandom(mol, -1);
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
            }

        }
    }

    void loadBMRBStats() {
        clearRefPPMs();
        BMRBStats.loadAllIfEmpty();
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> molAtoms = mol.getAtoms();
            for (Atom atom : molAtoms) {
                String aName = atom.getName();
                String resName = atom.getEntity().getName();
                Optional<PPMv> ppmVOpt = BMRBStats.getValue(resName, aName);
                if (ppmVOpt.isPresent()) {
                    PPMv ppmV = ppmVOpt.get();
                    atom.setRefPPM(ppmV.getValue());
                    atom.setRefError(ppmV.getError());
                }
            }
        }
        atomTableView.refresh();
    }

    void clearRefPPMs() {
        int refSet = refSetChoice.getValue();
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> molAtoms = mol.getAtoms();
            for (Atom atom : molAtoms) {
                PPMv ppmV = atom.getRefPPM(refSet);
                if (ppmV != null) {
                    ppmV.setValid(false, atom);
                }
            }
        }
        atomTableView.refresh();
    }

    void readCoordinates() {
        FileChooser fileChooser = new FileChooser();
        List<File> files = fileChooser.showOpenMultipleDialog(null);
        if ((files != null) && !files.isEmpty()) {
            PDBFile pdbFile = new PDBFile();
            try {
                pdbFile.readMultipleCoordinateFiles(files, true);
            } catch (MoleculeIOException | IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
            setFilterString("");
        }
    }

    private void showPredictor() {
        if (predictorController == null) {
            predictorController = PredictorSceneController.create(this);
        }
        if (predictorController != null) {
            predictorController.getStage().show();
            predictorController.getStage().toFront();
        } else {
            System.out.println("Couldn't make predictor controller");
        }
    }
    @Override
    public void moleculeChanged(MoleculeEvent e){
        refreshAtomTable();
    }

}
