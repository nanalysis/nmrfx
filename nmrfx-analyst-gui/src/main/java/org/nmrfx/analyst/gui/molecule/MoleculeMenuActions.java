package org.nmrfx.analyst.gui.molecule;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.MenuActions;
import org.nmrfx.analyst.gui.molecule3D.MolSceneController;
import org.nmrfx.analyst.gui.peaks.NOETableController;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.chemistry.io.*;
import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.OpenChemLibConverter;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class MoleculeMenuActions extends MenuActions {
    private MolSceneController molController;
    private SeqDisplayController seqDisplayController = null;
    private AtomController atomController;
    private RDCGUI rdcGUI = null;
    private RNAPeakGeneratorSceneController rnaPeakGenController;
    private NOETableController noeTableController;

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
        MenuItem readPDBLigandItem = new MenuItem("Read PDB Ligand...");
        readPDBLigandItem.setOnAction(e -> readMolecule("pdbLigand"));
        molFileMenu.getItems().add(readPDBLigandItem);
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

        MenuItem renumberItem = new MenuItem("Renumber residues...");
        renumberItem.setOnAction(e -> renumberSequence());

        MenuItem atomsMenuItem = new MenuItem("Atom Table...");
        atomsMenuItem.setOnAction(this::showAtoms);
        MenuItem sequenceMenuItem = new MenuItem("Sequence Viewer...");
        sequenceMenuItem.setOnAction(this::showSequence);

        MenuItem molMenuItem = new MenuItem("Viewer...");
        molMenuItem.setOnAction(e -> showMols());


        Menu molConstraintsMenu = new Menu("Constraints");

        MenuItem noeTableMenuItem = new MenuItem("NOE Constraint Table...");
        noeTableMenuItem.setOnAction(e -> showNOETable());

        MenuItem rdcMenuItem = new MenuItem("RDC Analysis...");
        rdcMenuItem.setOnAction(e -> showRDCGUI());

        molConstraintsMenu.getItems().addAll(noeTableMenuItem, rdcMenuItem);

        MenuItem rnaPeakGenMenuItem = new MenuItem("RNA Label Scheme...");
        rnaPeakGenMenuItem.setOnAction(this::showRNAPeakGenerator);

        menu.getItems().addAll(seqGUIMenuItem, atomsMenuItem,
                sequenceMenuItem, molMenuItem, renumberItem, molConstraintsMenu, rnaPeakGenMenuItem);
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

    public static String getType(Path usePath) {
        File file = usePath.toFile();
        String type = null;
        String fileName = file.getName();
        int dotPos = fileName.lastIndexOf(".");
        if (dotPos != -1) {
            type = fileName.substring(dotPos + 1);
        }
        return type;

    }

    public static void readMoleculeInDirectory(Path dir) throws IOException {
        List<Path> moleculePaths = new ArrayList<>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dir, "*.{smiles,mol,pdb,mol2,sdf}")) {
            for (Path entry : paths) {
                moleculePaths.add(entry);
            }
        }
        for (Path usePath : moleculePaths) {
            if (usePath != null) {
                String type = getType(usePath);
                if (type == null) {
                    return;
                }
                readMolecule(usePath.toFile(), type);
            }
        }
    }

    public void readMolecule() {
        readMolecule("");
    }

    public void readMolecule(String type) {
        if (!type.equals("pdb xyz") && !checkForExisting()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (type.isEmpty()) {
            type = getType(file.toPath());
        }
        if (type != null) {
            Molecule molecule = readMolecule(file, type);
            if (molecule != null) {
                showMols();
                resetAtomController();
            }
        }
    }

    public static Molecule readMolecule(File file, String type) {
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
                        molecule = Molecule.getActive();
                        molecule = (Molecule) pdbReader.read(molecule, file.toString(), false);
                    }
                    case "pdb xyz" -> {
                        PDBFile pdb = new PDBFile();
                        molecule = Molecule.getActive();
                        pdb.readCoordinates(molecule, file.getPath(), -1, false, true);
                        molecule.updateAtomArray();
                    }
                    case "pdbLigand" -> {
                        PDBFile pdb = new PDBFile();
                        molecule = Molecule.getActive();
                        PDBFile.readResidue(file.toString(), null, molecule,null);
                        molecule.updateAtomArray();
                    }
                    case "sdf", "mol" -> {
                        molecule = (Molecule) MoleculeFactory.getActive();
                        if (molecule == null) {
                            molecule = (Molecule) SDFile.read(file.toString(), null);
                        } else {
                            SDFile.read(file.toString(), null, molecule, null, null);
                        }

                    }
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
                            ProjectBase.getActive().putMolecule(molecule);
                        }
                    }
                    default -> {
                    }
                }
                MoleculeFactory.setActive(molecule);
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
        }
        return molecule;
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
                    ProjectBase.getActive().putMolecule(molecule);
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

    private void renumberSequence() {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            GUIUtils.warn("Renumbering ", "No molecule present");
            return;
        }
        var result = renumberingDialog();
        if (result.isPresent()) {
            Renumbering renumbering = result.get();
            List<Polymer> polymers = renumbering.polymer == null ? molecule.getPolymers() : List.of(renumbering.polymer);
            for (Polymer polymer : polymers) {
                if (renumbering.updateResidues) {
                    polymer.addResidueOffset(renumbering.offset);
                    if (atomController != null) {
                        atomController.refreshAtomTable();
                    }
                }
                if (renumbering.updatePeaks()) {
                    ResonanceFactory resonanceFactory = ProjectBase.activeResonanceFactory();
                    resonanceFactory.renumber(renumbering.offset(), polymer.getName());
                }
            }
        }
    }

    public record Renumbering(Polymer polymer, int offset, boolean updateResidues, boolean updatePeaks) {

    }
    public static Optional<Renumbering> renumberingDialog() {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            GUIUtils.warn("Renumbering ", "No molecule present");
            return Optional.empty();
        }

        Dialog<Renumbering> dialog = new Dialog<>();
        dialog.setTitle("Renumbering");
        dialog.setHeaderText("Enter offset information:");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        dialog.getDialogPane().setContent(grid);
        int comboBoxWidth = 100;
        ComboBox<Polymer> polymerComboBox = new ComboBox<>();
        polymerComboBox.getItems().addAll(molecule.getPolymers());
        polymerComboBox.getItems().add(null);
        polymerComboBox.setValue(molecule.getPolymers().getFirst());
        polymerComboBox.setMinWidth(comboBoxWidth);
        polymerComboBox.setMaxWidth(comboBoxWidth);

        CheckBox residueCheckBox = new CheckBox("");
        CheckBox peakCheckBox = new CheckBox("");


        SimpleIntegerProperty offsetProp = new SimpleIntegerProperty(0);
        TextField offsetField = GUIUtils.getIntegerTextField(offsetProp);


        grid.add(new Label("Polymer"), 0, 0);
        grid.add(polymerComboBox, 2, 0);

        grid.add(new Label("Update Residues"), 0, 1);
        grid.add(residueCheckBox, 1, 1);

        grid.add(new Label("Update Peaks"), 0, 2);
        grid.add(peakCheckBox, 1, 2);

        grid.add(new Label("Offset number"), 0, 3);
        grid.add(offsetField, 2, 3);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                // The value set in the formatter may not have been set yet so commit the value before retrieving
                polymerComboBox.commitValue();
                offsetField.commitValue();
                return new Renumbering(polymerComboBox.getValue(), offsetProp.get(), residueCheckBox.isSelected(), peakCheckBox.isSelected());
            }
            return null;
        });

        return  dialog.showAndWait();
    }
}
