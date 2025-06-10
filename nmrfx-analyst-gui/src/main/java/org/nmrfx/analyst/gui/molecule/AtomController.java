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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import org.controlsfx.control.tableview2.TableView2;
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
    private TableView2<Atom> atomTableView;
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
    List<PPMSet> PPMSets = new ArrayList<>();
    ObservableList<Atom> atoms = FXCollections.observableArrayList();

    MolFilter molFilter = new MolFilter("*.C*,H*,N*");

    LACSPlotGui lacsPlotGui = null;
    PPMPlotGUI ppmPlotGUI = null;

    static class PPMSet {
        enum mode {
            REF,
            ASSIGNED;
        }
        mode ref;
        int iSet;
        boolean refSet;
        ObservableValue<Boolean> isSelected = new SimpleBooleanProperty();
        PPMSet(int iSet, boolean refMode) {
            this.ref = refMode ? mode.REF : mode.ASSIGNED;
            this.iSet = iSet;
            this.refSet = refMode;
        }
        void setSelected(BooleanProperty isSelected) {
            this.isSelected = isSelected;
        }
    }

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

        MenuItem writeRefPPMItem = new MenuItem("Write Ref PPM...");
        writeRefPPMItem.setOnAction(e -> writeRefPPM());
        fileMenu.getItems().add(writeRefPPMItem);

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

        MenuButton addPPMColButton = new MenuButton("Add");
        String[] iSets = new String[]{"0", "1", "2", "3", "4", "5"};
        Menu ppmMenuItem = new Menu("PPM");
        for (String i : iSets) {
            MenuItem set = new MenuItem(i);
            ppmMenuItem.getItems().add(set);
            set.setOnAction(e -> makePPMCol(Integer.parseInt(set.getText()), false));
        }

        Menu refMenuItem = new Menu("Ref");
        for (String i : iSets) {
            MenuItem set = new MenuItem(i);
            refMenuItem.getItems().add(set);
            set.setOnAction(e -> makePPMCol(Integer.parseInt(set.getText()), true));
        }
        addPPMColButton.getItems().addAll(ppmMenuItem, refMenuItem);
        menuBar.getItems().addAll(addPPMColButton);

        for (String i : iSets) {
            int iSet = Integer.parseInt(i);
            boolean hasPPM = !atoms.stream().filter(atom -> atom.getPPM(iSet) != null).toList().isEmpty();
            if (hasPPM) { makePPMCol(iSet, false);}
        }

        MenuButton ppmPlotButton = new MenuButton();
        ppmPlotButton.setText("Plot");
        MenuItem deltasMenuItem = new MenuItem("Deltas");
        deltasMenuItem.setOnAction(e -> plotDeltas());
        MenuItem shiftsMenuItem = new MenuItem("Shifts");
        shiftsMenuItem.setOnAction(e -> plotShifts());
        ppmPlotButton.getItems().addAll(deltasMenuItem, shiftsMenuItem);
        menuBar.getItems().add(ppmPlotButton);
    }

    private void makePPMCol(int iSet, boolean ref) {
        PPMSet set = new PPMSet(iSet, ref);
        makePPMCol(set);
        PPMSets.add(set);
    }

    private void makePPMCol(PPMSet set) {
        TableColumn<Atom, Number> ppmCol = new TableColumn<>(set.ref.name() + " " + set.iSet);
        int iSet = set.iSet;
        boolean ref = set.refSet ;

        ppmCol.setCellValueFactory((CellDataFeatures<Atom, Number> p) -> {
            Atom atom = p.getValue();
            PPMv ppmVal = ref ? atom.getRefPPM(iSet) : atom.getPPM(iSet);
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
                        t.getRowValue().setPPM(iSet, value.doubleValue());
                    }
                });
        ppmCol.setCellFactory(tc -> new TextFieldTableCellNumber(new DoubleStringConverter4()));
        ppmCol.setEditable(true);
        CheckBox columnCheckBox = new CheckBox();
        ppmCol.setGraphic(columnCheckBox);
        columnCheckBox.selectedProperty().addListener(e -> set.setSelected(columnCheckBox.selectedProperty()));
        atomTableView.getColumns().add(ppmCol);
    }

    private TableColumn<Atom, Number> makeSDevCol(PPMSet ppmSet) {
        int iSet = ppmSet.iSet;
        TableColumn<Atom, Number> sdevCol = new TableColumn<>("SDev");
        sdevCol.setCellValueFactory((CellDataFeatures<Atom, Number> p) -> {
            Atom atom = p.getValue();
            PPMv ppmVal = atom.getRefPPM(iSet);
            ObservableValue<Number> ov;
            if ((ppmVal != null) && ppmVal.isValid()) {
                ov = new SimpleDoubleProperty(ppmVal.getError());
            } else {
                ov = null;
            }
            return ov;
        });
        sdevCol.setCellFactory(tc -> new TextFieldTableCellNumber(new DoubleStringConverter4()));
        sdevCol.setEditable(false);
        atomTableView.getColumns().add(sdevCol);
        return sdevCol;
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

    void initTable() {
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

        atomTableView.getColumns().setAll(indexColumn, entityNameColumn,
                residueNameColumn, residueNumberColumn, atomNameCol);
    }

    private void addDeltaCol() {
        DoubleStringConverter dsConverter4 = new DoubleStringConverter4();
        TableColumn<Atom, Number> deltaCol = new TableColumn<>("Delta");
        deltaCol.setCellValueFactory((CellDataFeatures<Atom, Number> p) -> {
            int ppmSet = PPMSets.getFirst().iSet;
            int refSet = PPMSets.get(1).iSet;
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
        writePPM(0, false);
    }

    void writeRefPPM() {
        writePPM(0, true);
    }
    void writePPM(int ppmSet, boolean refMode) {
        try {
            FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                String listFileName = file.getPath();
                Molecule molecule = Molecule.getActive();
                if (molecule != null) {

                    try (FileWriter writer = new FileWriter(listFileName)) {
                        PPMFiles.writePPM(molecule, writer, ppmSet, refMode);
                    }
                }
            }
        } catch (IOException ioE) {
            ExceptionDialog dialog = new ExceptionDialog(ioE);
            dialog.showAndWait();
        }
    }

    List<PPMSet> getPPMSets(boolean refMode) {
        return PPMSets.stream().filter(ppmSet -> ppmSet.refSet).toList();
    }

    void readPPM(boolean refMode) {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            Path path = file.toPath();
            Molecule molecule = Molecule.getActive();
            if (molecule != null) {
                int iSet = getPPMSets(refMode).size() + 1;
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
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> molAtoms = mol.getAtoms();
            for (Atom atom : molAtoms) {
                for (PPMSet ppmSet : getPPMSets(false)) {
                    atom.setPPMValidity(ppmSet.iSet, false);
                }
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
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> molAtoms = mol.getAtoms();
            for (Atom atom : molAtoms) {
                for (PPMSet refSet : getPPMSets(true)) {
                    PPMv ppmV = atom.getRefPPM(refSet.iSet);
                    if (ppmV != null) {
                        ppmV.setValid(false, atom);
                    }
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

    private void plotDeltas() {
        if (ppmPlotGUI == null) {
            ppmPlotGUI = new PPMPlotGUI();
            ppmPlotGUI.create(this);
        }
        ppmPlotGUI.plotDeltas();
    }

    private void plotShifts() {
        if (ppmPlotGUI == null) {
            ppmPlotGUI = new PPMPlotGUI();
            ppmPlotGUI.create(this);
        }
        ppmPlotGUI.plotShifts();
    }

}
