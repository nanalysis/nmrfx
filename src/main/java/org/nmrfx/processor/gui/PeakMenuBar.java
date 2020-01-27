package org.nmrfx.processor.gui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.io.PeakReader;
import org.nmrfx.processor.datasets.peaks.io.PeakWriter;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.utils.GUIUtils;

/**
 *
 * @author brucejohnson
 */
public class PeakMenuBar {

    final PeakMenuTarget menuTarget;

    public PeakMenuBar(PeakMenuTarget menuTarget) {
        this.menuTarget = menuTarget;
    }

    void initMenuBar(ToolBar menuBar) {
        MenuButton fileMenu = new MenuButton("File");

        MenuItem saveXPK2 = new MenuItem("Save XPK2...");
        saveXPK2.setOnAction(e -> saveList());

        MenuItem saveXPK = new MenuItem("Save XPK...");
        saveXPK.setOnAction(e -> savePeaks("xpk", "xpk"));

        MenuItem saveSparky = new MenuItem("Save Sparky...");
        saveSparky.setOnAction(e -> savePeaks("sparky", "txt"));

        fileMenu.getItems().addAll(saveXPK2, saveSparky);

        MenuItem readListItem = new MenuItem("Open...");
        readListItem.setOnAction(e -> readList());
        fileMenu.getItems().add(readListItem);

        menuBar.getItems().add(fileMenu);

        MenuButton editMenu = new MenuButton("Edit");

        MenuItem compressMenuItem = new MenuItem("Compress");
        compressMenuItem.setOnAction(e -> compressPeakList());
        editMenu.getItems().add(compressMenuItem);

        MenuItem degapMenuItem = new MenuItem("Degap");
        degapMenuItem.setOnAction(e -> renumberPeakList());
        editMenu.getItems().add(degapMenuItem);

        MenuItem compressAndDegapMenuItem = new MenuItem("Compress and Degap");
        compressAndDegapMenuItem.setOnAction(e -> compressAndDegapPeakList());
        editMenu.getItems().add(compressAndDegapMenuItem);

        MenuItem deleteMenuItem = new MenuItem("Delete List");
        deleteMenuItem.setOnAction(e -> deletePeakList());
        editMenu.getItems().add(deleteMenuItem);

        MenuItem unlinkPeakMenuItem = new MenuItem("Unlink List");
        unlinkPeakMenuItem.setOnAction(e -> unLinkPeakList());
        editMenu.getItems().add(unlinkPeakMenuItem);

        MenuItem duplicateMenuItem = new MenuItem("Duplicate");
        duplicateMenuItem.setOnAction(e -> duplicatePeakList());
        editMenu.getItems().add(duplicateMenuItem);

        MenuItem clusterMenuItem = new MenuItem("Cluster");
        clusterMenuItem.setOnAction(e -> clusterPeakList());
        editMenu.getItems().add(clusterMenuItem);

        menuBar.getItems().add(editMenu);

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

        menuBar.getItems().add(measureMenu);
    }

    void refreshPeakView() {
        menuTarget.refreshPeakView();

    }

    boolean checkDataset() {
        boolean ok = false;
        String datasetName = getPeakList().getDatasetName();
        if ((datasetName == null) || datasetName.equals("")) {
            PolyChart chart = PolyChart.getActiveChart();
            Dataset dataset = chart.getDataset();
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
            getPeakList().unLinkPeaks();
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

    void duplicatePeakList() {
        if (getPeakList() != null) {
            TextInputDialog dialog = new TextInputDialog();
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                PeakList newPeakList = getPeakList().copy(result.get(), false, false, true);
                if (newPeakList != null) {
                    setPeakList(newPeakList);
                }
            }
        }
    }

    void measureIntensities() {
        if (!checkDataset()) {
            return;
        }
        getPeakList().quantifyPeaks("center");
        refreshPeakView();
    }

    void measureVolumes() {
        if (!checkDataset()) {
            return;
        }
        getPeakList().quantifyPeaks("volume");
        refreshPeakView();
    }

    void measureEVolumes() {
        if (!checkDataset()) {
            return;
        }
        getPeakList().quantifyPeaks("evolume");
        refreshPeakView();
    }

    void compressPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Permanently remove deleted peaks");
            alert.showAndWait().ifPresent(response -> {
                getPeakList().compress();
                refreshPeakView();
            });
        }
    }

    void renumberPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Renumber peak list (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                getPeakList().reNumber();
                refreshPeakView();
            });
        }
    }

    void compressAndDegapPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove deleted peaks and renumber (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                getPeakList().compress();
                getPeakList().reNumber();
                refreshPeakView();
            });
        }
    }

    void deletePeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Peak List");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    PeakList.remove(getPeakList().getName());
                    PeakList list = null;
                    setPeakList(list);
                }
            });
        }
    }

    void clearAssignments() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Clear Assignments");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    getPeakList().clearAtomLabels();
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
                        writer.close();
                    }
                    if (getPeakList().hasMeasures()) {
                        if (listFileName.endsWith(".xpk2")) {
                            String measureFileName = listFileName.substring(0, listFileName.length() - 4) + "mpk2";
                            try (FileWriter writer = new FileWriter(measureFileName)) {
                                PeakWriter peakWriter = new PeakWriter();
                                peakWriter.writePeakMeasures(writer, getPeakList());
                                writer.close();
                            }
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
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
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
                            case "xpk":
                                peakWriter.writePeaksXPK(writer, getPeakList());
                                break;
                            case "sparky":
                                peakWriter.writePeaksToSparky(writer, getPeakList());
                                break;
                            default:
                                peakWriter.writePeaksXPK2(writer, getPeakList());
                        }
                        writer.close();
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
