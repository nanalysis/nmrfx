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
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.io.*;
import org.nmrfx.structure.chemistry.Molecule;

import java.io.File;

public class MoleculeMenuActions extends MenuActions {
    private MolSceneController molController;
    private SeqDisplayController seqDisplayController = null;
    private AtomController atomController;
    private RDCGUI rdcGUI = null;
    private RNAPeakGeneratorSceneController rnaPeakGenController;

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
        menu.getItems().add(molFileMenu);

    }

    @Override
    protected void advanced() {
        MenuItem seqGUIMenuItem = new MenuItem("Sequence Editor...");
        seqGUIMenuItem.setOnAction(e -> SequenceGUI.showGUI(app));

        MenuItem atomsMenuItem = new MenuItem("Atom Table...");
        atomsMenuItem.setOnAction(e -> showAtoms(e));
        MenuItem sequenceMenuItem = new MenuItem("Sequence Viewer...");
        sequenceMenuItem.setOnAction(e -> showSequence(e));

        MenuItem molMenuItem = new MenuItem("Viewer");
        molMenuItem.setOnAction(e -> showMols());

        MenuItem rdcMenuItem = new MenuItem("RDC Analysis...");
        rdcMenuItem.setOnAction(e -> showRDCGUI());

        MenuItem rnaPeakGenMenuItem = new MenuItem("Show RNA Label Scheme");
        rnaPeakGenMenuItem.setOnAction(e -> showRNAPeakGenerator(e));

        menu.getItems().addAll(seqGUIMenuItem, atomsMenuItem,
                sequenceMenuItem, molMenuItem, rdcMenuItem, rnaPeakGenMenuItem);
    }

    @FXML
    public void showMols() {
        if (molController == null) {
            molController = MolSceneController.create();
        }
        if (molController != null) {
            molController.getStage().show();
            molController.getStage().toFront();
        } else {
            System.out.println("Couldn't make molController");
        }
    }


    private void showSequence(ActionEvent event) {
        if (seqDisplayController == null) {
            seqDisplayController = SeqDisplayController.create();
        }
        if (seqDisplayController != null) {
            seqDisplayController.getStage().show();
            seqDisplayController.getStage().toFront();
        } else {
            System.out.println("Couldn't make seqDisplayController");
        }
    }

    private void showAtoms(ActionEvent event) {
        if (atomController == null) {
            atomController = AtomController.create();
        }
        if (atomController != null) {
            atomController.getStage().show();
            atomController.getStage().toFront();
        } else {
            System.out.println("Couldn't make atom controller");
        }
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
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        var currentMol = MoleculeFactory.getActive();
        if (file != null) {
            try {
                switch (type) {
                    case "pdb": {
                        PDBFile pdbReader = new PDBFile();
                        pdbReader.readSequence(file.toString(), false, 0);
                        System.out.println("read mol: " + file.toString());
                        break;
                    }
                    case "pdbx": {
                        PDBFile pdbReader = new PDBFile();
                        pdbReader.read(file.toString(), false);
                        System.out.println("read mol: " + file.toString());
                        break;
                    }
                    case "pdb xyz":
                        PDBFile pdb = new PDBFile();
                        pdb.readCoordinates(file.getPath(), 0, false, true);
                        Molecule mol = Molecule.getActive();
                        mol.updateAtomArray();
                        System.out.println("read mol: " + file.toString());
                        break;
                    case "sdf":
                    case "mol":
                        SDFile.read(file.toString(), null);
                        break;
                    case "mol2":
                        Mol2File.read(file.toString(), null);
                        break;
                    case "seq":
                        Sequence seq = new Sequence();
                        seq.read(file.toString());
                        break;
                    case "mmcif": {
                        MMcifReader.read(file);
                        System.out.println("read mol: " + file.toString());
                        break;
                    }
                    default:
                        break;
                }
                showMols();
            } catch (Exception ex) {
                var mol = MoleculeFactory.getActive();
                if (mol != null) {
                    if (mol != currentMol) {
                        MoleculeFactory.removeMolecule(mol.getName());
                        MoleculeFactory.setActive(currentMol);
                    }
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
        if (rnaPeakGenController != null) {
            rnaPeakGenController.getStage().show();
            rnaPeakGenController.getStage().toFront();
        } else {
            System.out.println("Couldn't make rnaPeakGenController ");
        }
    }

}
