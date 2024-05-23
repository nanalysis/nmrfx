package org.nmrfx.analyst.gui.molecule;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.MenuActions;
import org.nmrfx.analyst.gui.molecule3D.MolSceneController;
import org.nmrfx.analyst.gui.peaks.DistanceConstraintTableController;
import org.nmrfx.analyst.gui.peaks.NOETableController;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.constraints.DistanceConstraintSet;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.chemistry.io.*;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.OpenChemLibConverter;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class MoleculeMenuActions extends MenuActions {
    private MolSceneController molController;
    private SeqDisplayController seqDisplayController = null;
    private AtomController atomController;
    private RDCGUI rdcGUI = null;
    private RNAPeakGeneratorSceneController rnaPeakGenController;
    private NOETableController noeTableController;
    private DistanceConstraintTableController distanceConstraintTableController;

    public MoleculeMenuActions(AnalystApp app, Menu menu) {
        super(app, menu);
    }

    @Override
    public void basic() {
        Menu molFileMenu = new Menu("File");

        MenuItem readSeqItem = new MenuItem("Read Sequence...");
        readSeqItem.setOnAction(e -> readMolecule("seq"));
        molFileMenu.getItems().add(readSeqItem);
        MenuItem readPDBItem = new MenuItem("Read PDB...");
        readPDBItem.setOnAction(e -> readMolecule("pdb"));
        molFileMenu.getItems().add(readPDBItem);
        MenuItem readCoordinatesItem = new MenuItem("Read Coordinates ...");
        readCoordinatesItem.setOnAction(e -> readMolecule("pdb xyz"));
        molFileMenu.getItems().add(readCoordinatesItem);
        MenuItem readPDBxyzItem = new MenuItem("Read PDB XYZ...");
        readPDBxyzItem.setOnAction(e -> readMolecule("pdbx"));
        molFileMenu.getItems().add(readPDBxyzItem);
        MenuItem readMMCIFItem = new MenuItem("Read mmCIF...");
        readMMCIFItem.setOnAction(e -> readMolecule("mmcif"));
        molFileMenu.getItems().add(readMMCIFItem);
        MenuItem readMolItem = new MenuItem("Read Mol...");
        readMolItem.setOnAction(e -> readMolecule("mol"));
        molFileMenu.getItems().add(readMolItem);
        MenuItem readMol2Item = new MenuItem("Read Mol2...");
        readMol2Item.setOnAction(e -> readMolecule("mol2"));
        molFileMenu.getItems().add(readMol2Item);
        MenuItem readSMILESItem = new MenuItem("Read SMILES...");
        readSMILESItem.setOnAction(e -> readMolecule("smiles"));
        molFileMenu.getItems().add(readSMILESItem);

        MenuItem clearAllItem = new MenuItem("Clear Molecules...");
        clearAllItem.setOnAction(e -> clearExisting());

        MenuItem smileItem = new MenuItem("Input SMILE...");
        smileItem.setOnAction(e -> getSMILEMolecule());

        menu.getItems().addAll(molFileMenu, smileItem, clearAllItem);

    }

    @Override
    protected void advanced() {
        MenuItem seqGUIMenuItem = new MenuItem("Sequence Editor...");
        seqGUIMenuItem.setOnAction(e -> SequenceGUI.showGUI(app));

        MenuItem atomsMenuItem = new MenuItem("Atom Table...");
        atomsMenuItem.setOnAction(this::showAtoms);
        MenuItem sequenceMenuItem = new MenuItem("Sequence Viewer...");
        sequenceMenuItem.setOnAction(this::showSequence);

        MenuItem molMenuItem = new MenuItem("Viewer...");
        molMenuItem.setOnAction(e -> showMols());


        Menu molConstraintsMenu = new Menu("Constraints");

        MenuItem noeTableMenuItem = new MenuItem("NOE Constraint Table...");
        noeTableMenuItem.setOnAction(e -> showNOETable());

        MenuItem distanceConstraintTableMenuItem = new MenuItem("Distance Constraint Table...");
        distanceConstraintTableMenuItem.setOnAction(e -> showDistanceConstraintTable());

        MenuItem rdcMenuItem = new MenuItem("RDC Analysis...");
        rdcMenuItem.setOnAction(e -> showRDCGUI());

        molConstraintsMenu.getItems().addAll(noeTableMenuItem, distanceConstraintTableMenuItem, rdcMenuItem);

        MenuItem rnaPeakGenMenuItem = new MenuItem("RNA Label Scheme...");
        rnaPeakGenMenuItem.setOnAction(this::showRNAPeakGenerator);

        menu.getItems().addAll(seqGUIMenuItem, atomsMenuItem,
                sequenceMenuItem, molMenuItem, molConstraintsMenu, rnaPeakGenMenuItem);
    }

    void clearExisting() {
        if (GUIUtils.affirm("Clear all molecules?")) {
            MoleculeFactory.clearAllMolecules();
            if (atomController != null) {
                atomController.refreshAtomTable();
            }
            if (molController != null) {
                molController.removeAll();
                molController.clearSS();
            }
        }
    }

    @FXML
    public void showMols() {
        if (molController == null) {
            molController = MolSceneController.create();
        }
        molController.getStage().show();
        molController.getStage().toFront();
    }


    private void showSequence(ActionEvent event) {
        if (seqDisplayController == null) {
            seqDisplayController = SeqDisplayController.create();
        }
        seqDisplayController.getStage().show();
        seqDisplayController.getStage().toFront();
    }

    private void showAtoms(ActionEvent event) {
        if (atomController == null) {
            atomController = AtomController.create();
        }
        atomController.getStage().show();
        atomController.getStage().toFront();
    }

    void showRDCGUI() {
        if (rdcGUI == null) {
            rdcGUI = new RDCGUI(app);
        }
        rdcGUI.showRDCplot();
    }

    public void updateRDC(File starFile) {
        if (rdcGUI != null) {
            rdcGUI.bmrbFile.setText(starFile.getName());
            rdcGUI.setChoice.getItems().clear();
            MolecularConstraints molConstr = MoleculeFactory.getActive().getMolecularConstraints();
            if (!molConstr.getRDCSetNames().isEmpty()) {
                rdcGUI.setChoice.getItems().addAll(molConstr.getRDCSetNames());
                rdcGUI.setChoice.setValue(rdcGUI.setChoice.getItems().get(0));
            }
        }

    }

    public void resetAtomController() {
        if (atomController != null) {
            atomController.setFilterString("");
        }
    }

    public void readMolecule(String type) {
        if (!checkForExisting()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        Molecule molecule = null;
        if (file != null) {
            try {
                switch (type) {
                    case "pdb" -> {
                        PDBFile pdbReader = new PDBFile();
                        molecule = (Molecule) pdbReader.readSequence(file.toString(), 0);
                    }
                    case "pdbx" -> {
                        PDBFile pdbReader = new PDBFile();
                        molecule = (Molecule) pdbReader.read(file.toString(), false);
                    }
                    case "pdb xyz" -> {
                        PDBFile pdb = new PDBFile();
                        molecule = Molecule.getActive();
                        pdb.readCoordinates(molecule, file.getPath(), 0, false, true);
                        molecule.updateAtomArray();
                    }
                    case "sdf", "mol" -> molecule = (Molecule) SDFile.read(file.toString(), null);
                    case "mol2" -> molecule = (Molecule) Mol2File.read(file.toString(), null);
                    case "seq" -> {
                        Sequence seq = new Sequence();
                        molecule = (Molecule) seq.read(file.toString());
                    }
                    case "mmcif" -> molecule = (Molecule) MMcifReader.read(file);
                    case "smiles" -> {
                        List<Molecule> molecules = OpenChemLibConverter.readSMILES(file);
                        if (!molecules.isEmpty()) {
                            molecule = molecules.get(0);
                        }
                    }
                    default -> {
                    }
                }
                MoleculeFactory.setActive(molecule);
                showMols();
            } catch (Exception ex) {
                var mol = MoleculeFactory.getActive();
                if ((mol != null) && (mol != molecule)) {
                    MoleculeFactory.removeMolecule(mol.getName());
                    MoleculeFactory.setActive(molecule);
                }
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.setTitle("Error reading molecule file");
                dialog.showAndWait();
            }
            resetAtomController();
        }
    }

    @FXML
    private void showRNAPeakGenerator(ActionEvent event) {
        if (rnaPeakGenController == null) {
            rnaPeakGenController = RNAPeakGeneratorSceneController.create();
        }
        rnaPeakGenController.getStage().show();
        rnaPeakGenController.getStage().toFront();
    }

    public static boolean checkForExisting() {
        if (MoleculeFactory.getActive() != null) {
            var result = GUIUtils.deleteAppendCancel("Molecule exists");
            if (result == GUIUtils.AlertRespones.CANCEL) {
                return false;
            }
            if (result == GUIUtils.AlertRespones.DELETE) {
                MoleculeFactory.clearAllMolecules();
            }
        }
        return true;
    }

    void getSMILEMolecule() {
        String smileString = GUIUtils.input("SMILE String");
        if (!smileString.isBlank()) {
            String molName = GUIUtils.input("Molecule Name");
            if (!molName.isBlank()) {
                try {
                    Molecule molecule = OpenChemLibConverter.parseSmiles("mol", smileString);
                    molecule.setActive();
                } catch (IllegalArgumentException iaE) {
                    GUIUtils.warn("SMILES Parser", iaE.getMessage());
                    return;
                }
                resetAtomController();
            }
        }
    }

    private void showNOETable() {
        if (noeTableController == null) {
            noeTableController = NOETableController.create();
            if (noeTableController == null) {
                return;
            }
            Collection<NoeSet> noeSets = MoleculeFactory.getActive().getMolecularConstraints().noeSets();

            noeSets.stream().findFirst().ifPresent(noeTableController::setNoeSet);
        }
        noeTableController.getStage().show();
        noeTableController.getStage().toFront();
        noeTableController.updateNoeSetMenu();
    }
    private void showDistanceConstraintTable() {
        if (distanceConstraintTableController == null) {
            distanceConstraintTableController = DistanceConstraintTableController.create();
            if (distanceConstraintTableController == null) {
                return;
            }
            Collection<DistanceConstraintSet> noeSets = MoleculeFactory.getActive().getMolecularConstraints().distanceSets();

            noeSets.stream().findFirst().ifPresent(distanceConstraintTableController::setDistanceConstraintSet);
        }
        distanceConstraintTableController.getStage().show();
        distanceConstraintTableController.getStage().toFront();
        distanceConstraintTableController.updateDistanceSetMenu();
    }

}
