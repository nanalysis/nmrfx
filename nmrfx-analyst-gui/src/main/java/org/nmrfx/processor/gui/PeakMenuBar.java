package org.nmrfx.processor.gui;

import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.peaks.io.PeakReader;
import org.nmrfx.peaks.io.PeakWriter;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakFolder;
import org.nmrfx.processor.datasets.peaks.PeakListTools;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.processor.gui.undo.ChartUndo;
import org.nmrfx.processor.gui.undo.GroupUndo;
import org.nmrfx.processor.gui.undo.PeakListUndo;
import org.nmrfx.processor.gui.undo.UndoManager;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;

/**
 * @author brucejohnson
 */
public class PeakMenuBar {
    private static final Map<String, Consumer<PeakList>> extras = new LinkedHashMap<>();
    private final HashMap<PeakList, UndoManager> undoManagers = new HashMap<>();
    private final List<ChartUndo> undos = new ArrayList<>();
    private final List<ChartUndo> redos = new ArrayList<>();

    private final PeakMenuTarget menuTarget;
    private MenuButton peakListMenu = null;

    public PeakMenuBar(PeakMenuTarget menuTarget) {
        this.menuTarget = menuTarget;
    }

    public void initMenuBar(ToolBar menuBar, boolean initPeakListMenu) {
        if (initPeakListMenu) {
            peakListMenu = new MenuButton("List");
            menuBar.getItems().add(peakListMenu);
        }
        MenuButton fileMenu = new MenuButton("File");

        MenuItem saveXPK2 = new MenuItem("Save XPK2...");
        saveXPK2.setOnAction(e -> saveList());

        MenuItem saveXPK = new MenuItem("Save XPK...");
        saveXPK.setOnAction(e -> savePeaks("xpk", "xpk"));

        MenuItem saveSparky = new MenuItem("Save Sparky...");
        saveSparky.setOnAction(e -> savePeaks("sparky", "txt"));

        MenuItem saveNMRPipe = new MenuItem("Save nmrPipe...");
        saveNMRPipe.setOnAction(e -> savePeaks("nmrpipe", "txt"));

        fileMenu.getItems().addAll(saveXPK2, saveXPK, saveSparky, saveNMRPipe);

        MenuItem readListItem = new MenuItem("Open...");
        readListItem.setOnAction(e -> readList());
        fileMenu.getItems().add(readListItem);

        menuBar.getItems().add(fileMenu);

        MenuButton editMenu = new MenuButton("Edit");

        Menu undoMenu = new Menu("Undo");

        Menu redoMenu = new Menu("Redo");

        editMenu.setOnShowing(e -> {
            cleanUndManagers();
            undoMenu.getItems().clear();
            var optManager = getUndoManager();
            optManager.ifPresent(undoManager -> {
                String lastAction = undoManager.getUndoLabelName();
                MenuItem undoMenuItem = new MenuItem("Undo " + lastAction);
                undoMenuItem.setOnAction(event -> undo());
                undoMenu.getItems().add(undoMenuItem);

                redoMenu.getItems().clear();
                lastAction = undoManager.getRedoLabelName();
                MenuItem redoMenuItem = new MenuItem("Redo " + lastAction);
                redoMenuItem.setOnAction(event -> redo());
                redoMenu.getItems().add(redoMenuItem);
            });
        });


        editMenu.getItems().add(undoMenu);
        editMenu.getItems().add(redoMenu);


        MenuItem copyMenu = new MenuItem("Copy");
        copyMenu.setOnAction(e -> menuTarget.copyPeakTableView());
        editMenu.getItems().add(copyMenu);

        MenuItem compressMenuItem = new MenuItem("Compress");
        compressMenuItem.setOnAction(e -> compressPeakList());
        editMenu.getItems().add(compressMenuItem);

        MenuItem degapMenuItem = new MenuItem("Degap");
        degapMenuItem.setOnAction(e -> renumberPeakList());
        editMenu.getItems().add(degapMenuItem);

        MenuItem compressAndDegapMenuItem = new MenuItem("Compress and Degap");
        compressAndDegapMenuItem.setOnAction(e -> compressAndDegapPeakList());
        editMenu.getItems().add(compressAndDegapMenuItem);

        MenuItem deletePeakMenuItem = new MenuItem("Delete Peaks");
        deletePeakMenuItem.setOnAction(e -> deletePeaks());
        editMenu.getItems().add(deletePeakMenuItem);

        MenuItem restorePeakMenuItem = new MenuItem("Restore Peaks");
        restorePeakMenuItem.setOnAction(e -> restorePeaks());
        editMenu.getItems().add(restorePeakMenuItem);

        MenuItem deleteMenuItem = new MenuItem("Delete List");
        deleteMenuItem.setOnAction(e -> deletePeakList());
        editMenu.getItems().add(deleteMenuItem);

        MenuItem unlinkPeakMenuItem = new MenuItem("Unlink List");
        unlinkPeakMenuItem.setOnAction(e -> unLinkPeakList());
        editMenu.getItems().add(unlinkPeakMenuItem);

        MenuItem duplicateMenuItem = new MenuItem("Duplicate");
        duplicateMenuItem.setOnAction(e -> duplicatePeakList());
        editMenu.getItems().add(duplicateMenuItem);

        MenuItem coupleMenuItems = new MenuItem("AutoCouple (2D 1H)");
        coupleMenuItems.setOnAction(e -> autoCouplePeakList());
        editMenu.getItems().add(coupleMenuItems);

        MenuItem removeDiagonalMenuItem = new MenuItem("Remove Diagonal (2D 1H)");
        removeDiagonalMenuItem.setOnAction(e -> removeDiagonal());
        editMenu.getItems().add(removeDiagonalMenuItem);

        MenuItem mirrorMenuItem = new MenuItem("Mirror 2D List");
        mirrorMenuItem.setOnAction(e -> mirror2DList());
        editMenu.getItems().add(mirrorMenuItem);


        MenuItem unifyMenuItem = new MenuItem("Unify Widths");
        unifyMenuItem.setOnAction(e -> unifyPeakWidths());
        editMenu.getItems().add(unifyMenuItem);

        MenuItem foldMenuItem = new MenuItem("Fold Peaks");
        foldMenuItem.setOnAction(e -> foldPeaks());
        editMenu.getItems().add(foldMenuItem);

        menuBar.getItems().add(editMenu);

        MenuButton clusterMenu = new MenuButton("Cluster");
        MenuItem clusterMenuItem = new MenuItem("Cluster...");
        clusterMenuItem.setOnAction(e -> clusterPeakList());
        clusterMenu.getItems().add(clusterMenuItem);

        MenuItem clusterRowsMenuItem = new MenuItem("Cluster Rows");
        clusterRowsMenuItem.setOnAction(e -> clusterPeakListDim(0));
        clusterMenu.getItems().add(clusterRowsMenuItem);

        MenuItem clusterColumnsMenuItem = new MenuItem("Cluster Columns");
        clusterColumnsMenuItem.setOnAction(e -> clusterPeakListDim(1));
        clusterMenu.getItems().add(clusterColumnsMenuItem);

        menuBar.getItems().add(clusterMenu);

        MenuButton assignMenu = new MenuButton("Assign");
        MenuItem clearAssignMenuItem = new MenuItem("Clear All");
        clearAssignMenuItem.setOnAction(e -> clearAssignments());
        assignMenu.getItems().add(clearAssignMenuItem);

        menuBar.getItems().add(assignMenu);

        MenuButton measureMenu = new MenuButton("Measure");
        MenuItem measureIntensityItem = new MenuItem("Intensities");
        measureIntensityItem.setOnAction(e -> measureIntensities());
        measureMenu.getItems().add(measureIntensityItem);

        MenuItem measureVolumeItem = new MenuItem("Volumes");
        measureVolumeItem.setOnAction(e -> measureVolumes());
        measureMenu.getItems().add(measureVolumeItem);

        MenuItem measureEVolumeItem = new MenuItem("EVolumes");
        measureEVolumeItem.setOnAction(e -> measureEVolumes());
        measureMenu.getItems().add(measureEVolumeItem);

        MenuItem measureEVolumeMultiItem = new MenuItem("EVolumes - Multi");
        measureEVolumeMultiItem.setOnAction(e -> measureEVolumesMulti());
        measureMenu.getItems().add(measureEVolumeMultiItem);

        menuBar.getItems().add(measureMenu);

        for (Entry<String, Consumer<PeakList>> entry : extras.entrySet()) {
            MenuItem menuItem = new MenuItem(entry.getKey());
            Consumer<PeakList> consumer = entry.getValue();
            menuItem.setOnAction(e -> consumer.accept(getPeakList()));
            editMenu.getItems().add(menuItem);
        }
    }

    void cleanUndManagers() {
        undoManagers.keySet().removeIf(peakList -> PeakList.get(peakList.getName()) == null);
    }

    Optional<UndoManager> getUndoManager() {
        PeakList peakList = getPeakList();
        if (peakList == null) {
            return Optional.empty();
        } else {
            return Optional.of(undoManagers.computeIfAbsent(peakList, peakList1 -> new UndoManager()));
        }
    }

    void doUndoRedo(Consumer<PeakList> consumer, String label) {
        resetUndoGroup();
        PeakList peakList = getPeakList();
        addPeakListUndo(peakList);
        consumer.accept(peakList);
        addPeakListRedo(peakList);
        addUndoGroup(label);
    }

    void undo() {
        getUndoManager().ifPresent(undoManger -> {
            undoManger.undo();
            refreshPeakView();
            refreshChangedListView();
        });
    }

    void redo() {
        getUndoManager().ifPresent(undoManger -> {
            undoManger.redo();
            refreshPeakView();
            refreshChangedListView();
        });
    }

    void addPeakListUndo(PeakList peakList) {
        if (undos.isEmpty()) {
            redos.clear();
        }
        PeakListUndo undo = new PeakListUndo(peakList);
        undos.add(undo);
    }

    void addPeakListRedo(PeakList peakList) {
        PeakListUndo undo = new PeakListUndo(peakList);
        redos.add(undo);
    }

    void addUndoGroup(String label) {
        getUndoManager().ifPresent(undoManger -> {
            if (!undos.isEmpty() && (undos.size() == redos.size())) {
                GroupUndo groupUndo = new GroupUndo(undos);
                GroupUndo groupRedo = new GroupUndo(redos);
                undos.clear();
                redos.clear();
                undoManger.add(label, groupUndo, groupRedo);
            }
        });
    }

    void resetUndoGroup() {
        undos.clear();
        redos.clear();
    }

    public MenuButton getPeakListMenu() {
        return peakListMenu;
    }

    void refreshPeakView() {
        menuTarget.refreshPeakView();
    }

    void refreshChangedListView() {
        menuTarget.refreshChangedListView();
    }

    public static void addExtra(String name, Consumer<PeakList> function) {
        extras.put(name, function);
    }

    boolean checkDataset() {
        boolean ok = false;
        String datasetName = getPeakList().getDatasetName();
        if ((datasetName == null) || datasetName.isEmpty()) {
            PolyChart chart = PolyChartManager.getInstance().getActiveChart();
            DatasetBase dataset = chart.getDataset();
            if (dataset != null) {
                for (PeakListAttributes peakAttr : chart.getPeakListAttributes()) {
                    if (peakAttr.getPeakList() == getPeakList()) {
                        getPeakList().setDatasetName(dataset.getName());
                        ok = true;
                        break;
                    }
                }
            }
        } else {
            ok = true;
        }
        if (!ok) {
            GUIUtils.warn("PeakList", "No dataset assigned to this peak list");
        }
        return ok;
    }

    void unLinkPeakList() {
        if (getPeakList() != null) {
            doUndoRedo(peakList -> getPeakList().unLinkPeaks(), "unlink list");
        }
    }

    void mirror2DList() {
        if (getPeakList() != null) {
            doUndoRedo(PeakListTools::addMirroredPeaks, "mirror 2D");
        }
    }

    void autoCouplePeakList() {
        if (getPeakList() != null) {
            doUndoRedo(PeakListTools::autoCoupleHomoNuclear, "autoCouple");
        }
    }

    void removeDiagonal() {
        if (getPeakList() != null) {
            doUndoRedo(PeakListTools::removeDiagonalPeaks, "remove diagonal");
        }
    }

    void deletePeaks() {
        if (getPeakList() != null) {
            doUndoRedo(peakList -> menuTarget.deletePeaks(), "delete peaks");
        }
    }

    void restorePeaks() {
        if (getPeakList() != null) {
            doUndoRedo(peakList -> menuTarget.restorePeaks(), "restore peaks");
        }
    }

    void clusterPeakListDim(int dim) {
        if ((getPeakList() != null) && (getPeakList().getNDim() > dim)) {
            doUndoRedo(peakList -> PeakListTools.clusterPeakColumns(peakList, dim), "cluster rows/cols");
        }
    }

    void clusterPeakList() {
        if (getPeakList() != null) {
            if (getPeakList().hasSearchDims()) {
                getPeakList().clusterPeaks();
            } else {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setHeaderText("Enter dimensions and tolerances (like: HN 0.1 N 0.5)");
                Optional<String> result = dialog.showAndWait();
                if (result.isPresent()) {
                    try {
                        getPeakList().setSearchDims(result.get());
                        getPeakList().clusterPeaks();
                    } catch (IllegalArgumentException iE) {
                        ExceptionDialog edialog = new ExceptionDialog(iE);
                        edialog.showAndWait();
                    }
                }
            }
        }
    }

    void foldPeaks() {
        PeakList peakList = getPeakList();
        if (peakList != null) {
            PeakFolder peakFolder = new PeakFolder();
            List<String> dimLabels = peakFolder.dimLabels;
            List<SpectralDim> dimsToFold = peakList.getFoldedDims();
            if (dimsToFold.isEmpty()) {
                GUIUtils.warn("Assign dimension to fold", "Indicate which dimension to fold in Peak Tool's Reference tab");
            }
            // if the peaklist is only 2 dimensions, ensure that they are H and C
            // otherwise user is required to set the bonded dimension
            for (SpectralDim dim : dimsToFold) {
                String bondedDim = dim.getRelationDim();
                if (bondedDim.isBlank()) {
                    if (peakList.getNDim() == 2 &&
                            new HashSet<>(peakList.getSpectralDims().stream()
                                    .map(SpectralDim::getNucleus)
                                    .map(s -> s.substring(s.length() - 1))
                                    .toList()).containsAll(dimLabels)) {
                        String currentDim = dim.getDimName();
                        bondedDim = peakList.getSpectralDims().stream()
                                .filter((spectralDim -> !spectralDim.getDimName().equals(currentDim))).toList().getFirst().getDimName();
                        dim.setRelation(bondedDim);
                    } else {
                        GUIUtils.warn("Set bonded dimension", "Assign bonded dimension in Peak Tool's reference tab");
                    }
                }
            }
            String[] dims = new String[dimsToFold.size()];
            boolean[] alias = new boolean[dimsToFold.size()];
            for (int i = 0; i < dimsToFold.size(); i++) {
                dims[i] = dimsToFold.get(i).getDimName();
                alias[i] = dimsToFold.get(i).getFoldMode() == 'a';
            }

            if (Molecule.getActive() == null) {
                GUIUtils.warn("No Active Molecule", "Load a molecule");
            }
            peakFolder.unfoldPeakList(peakList, dims, alias);

        }
    }

    void duplicatePeakList() {
        if (getPeakList() != null) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setContentText("New Peak List Name");
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                PeakList newPeakList = getPeakList().copy(result.get(), false, false, true);
                if (newPeakList != null) {
                    setPeakList(newPeakList);
                }
            }
        }
    }

    void unifyPeakWidths() {
        menuTarget.getPeak().ifPresent(peak -> doUndoRedo(peakList -> PeakListTools.unifyWidths(peak), "unify peak widths"));
    }

    void measureIntensities() {
        if (!checkDataset()) {
            return;
        }
        PeakListTools.quantifyPeaks(getPeakList(), "center");
        refreshPeakView();
    }

    void measureVolumes() {
        if (!checkDataset()) {
            return;
        }
        PeakListTools.quantifyPeaks(getPeakList(), "volume");
        refreshPeakView();
    }

    void measureEVolumes() {
        if (!checkDataset()) {
            return;
        }
        PeakListTools.quantifyPeaks(getPeakList(), "evolume");
        refreshPeakView();
    }

    void measureEVolumesMulti() {
        PolyChart chart = PolyChartManager.getInstance().getActiveChart();
        var datasets = new ArrayList<Dataset>();
        for (var dataAttr : chart.getDatasetAttributes()) {
            datasets.add((Dataset) dataAttr.getDataset());
        }
        PeakListTools.quantifyPeaks(getPeakList(), datasets, "evolume");
        refreshPeakView();
    }

    void compressPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove deleted peaks");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    doUndoRedo(peakList -> {
                        peakList.compress();
                        refreshChangedListView();
                    }, "compress");
                }
            });
        }
    }

    void renumberPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Renumber peak list");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    doUndoRedo(peakList -> {
                        peakList.reNumber();
                        refreshChangedListView();
                    }, "degap");
                }
            });
        }
    }

    void compressAndDegapPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove deleted peaks and renumber");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    doUndoRedo(peakList -> {
                        getPeakList().compress();
                        getPeakList().reNumber();
                        refreshChangedListView();
                    }, "compress and degap");
                }
            });
        }
    }

    void deletePeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Peak List");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    PeakList.remove(getPeakList().getName());
                    setPeakList(null);
                }
            });
        }
    }

    void clearAssignments() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Clear Assignments");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    doUndoRedo(peakList -> getPeakList().clearAtomLabels(), "clear all");
                }
            });
        }
    }

    void saveList() {
        if (getPeakList() != null) {
            try {
                FileChooser fileChooser = new FileChooser();
                File file = fileChooser.showSaveDialog(null);
                if (file != null) {
                    String listFileName = file.getPath();

                    try (FileWriter writer = new FileWriter(listFileName)) {
                        PeakWriter peakWriter = new PeakWriter();
                        peakWriter.writePeaksXPK2(writer, getPeakList());
                    }
                    if (getPeakList().hasMeasures() && listFileName.endsWith(".xpk2")) {
                        String measureFileName = listFileName.substring(0, listFileName.length() - 4) + "mpk2";
                        try (FileWriter writer = new FileWriter(measureFileName)) {
                            PeakWriter peakWriter = new PeakWriter();
                            peakWriter.writePeakMeasures(writer, getPeakList());
                        }
                    }
                }

            } catch (IOException | InvalidPeakException ioE) {
                ExceptionDialog dialog = new ExceptionDialog(ioE);
                dialog.showAndWait();
            }
        }
    }

    void readList() {
        FileChooser fileChooser = new FileChooser();
        List<File> files = fileChooser.showOpenMultipleDialog(null);
        for (File file : files) {
            String listFileName = file.getPath();
            try {
                PeakReader peakReader = new PeakReader();

                PeakList newPeakList = peakReader.readPeakList(listFileName);
                if (newPeakList != null) {
                    setPeakList(newPeakList);
                    int lastDot = listFileName.lastIndexOf('.');
                    if (lastDot != -1) {
                        String mpk2File = listFileName.substring(0, lastDot) + ".mpk2";
                        Path mpk2Path = Paths.get(mpk2File);
                        if (Files.exists(mpk2Path)) {
                            peakReader.readMPK2(getPeakList(), mpk2Path.toString());
                        }
                    }
                }

            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
                return;
            }
        }
    }

    void savePeaks(String mode, String extension) {
        if (getPeakList() != null) {
            try {
                FileChooser fileChooser = new FileChooser();

                File file = fileChooser.showSaveDialog(null);

                if (file != null) {
                    String listFileName = file.getPath();
                    try (FileWriter writer = new FileWriter(listFileName)) {
                        PeakWriter peakWriter = new PeakWriter();
                        switch (mode) {
                            case "xpk" -> peakWriter.writePeaksXPK(writer, getPeakList());
                            case "sparky" -> peakWriter.writePeaksToSparky(writer, getPeakList());
                            case "nmrpipe" -> peakWriter.writePeakstoNMRPipe(writer, getPeakList());
                            default -> peakWriter.writePeaksXPK2(writer, getPeakList());
                        }
                    }
                }

            } catch (IOException | InvalidPeakException ioE) {
                ExceptionDialog dialog = new ExceptionDialog(ioE);
                dialog.showAndWait();
            }
        }
    }

    /**
     * @return the peakList
     */
    private PeakList getPeakList() {
        return menuTarget.getPeakList();
    }

    /**
     * @param peakList the peakList to set
     */
    private void setPeakList(PeakList peakList) {
        menuTarget.setPeakList(peakList);
    }

}
