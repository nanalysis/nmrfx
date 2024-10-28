/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import org.controlsfx.control.PropertySheet;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.utils.properties.*;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author brucejohnson
 */
public class AnalystPrefs {
    private static final String REMOTE_HOST_STR = "REMOTE_HOST";
    private static final String REMOTE_USER_STR = "REMOTE_USER_NAME";
    private static IntegerProperty libraryVectorSize = null;
    private static DoubleProperty libraryVectorSF = null;
    private static DoubleProperty libraryVectorSW = null;
    private static DoubleProperty libraryVectorLB = null;
    private static DoubleProperty libraryVectorREF = null;

    private static StringProperty segmentLibraryFile = null;
    private static BooleanProperty useRemotePassword = null;
    private static StringProperty remoteUserName = null;
    private static StringProperty remoteHostName = null;
    private static StringProperty remoteDirectory = null;
    private static StringProperty localDirectory = null;
    private static StringProperty localResidueDirectory = null;
    private static StringProperty gissmoFileProp = null;

    private AnalystPrefs() {
        throw new IllegalAccessError("Utility class shouldn't be instantiated!");
    }


    public static String getSegmentLibraryFile() {
        segmentLibraryFile = PreferencesController.getString(segmentLibraryFile, "SEGMENT_LBRARY_FILE", "/Users/brucejohnson/metabo/save/all.json");
        return segmentLibraryFile.getValue();
    }

    public static Integer getLibraryVectorSize() {
        libraryVectorSize = PreferencesController.getInteger(libraryVectorSize, "LIBRARY_VECTOR_SIZE", 14);
        return libraryVectorSize.getValue();
    }

    public static Double getLibraryVectorSF() {
        libraryVectorSF = PreferencesController.getDouble(libraryVectorSF, "LIBRARY_VECTOR_SF", 600.0);
        return libraryVectorSF.getValue();
    }

    public static Double getLibraryVectorSW() {
        libraryVectorSW = PreferencesController.getDouble(libraryVectorSW, "LIBRARY_VECTOR_SW", 10000.0);
        return libraryVectorSW.getValue();
    }

    public static Double getLibraryVectorLB() {
        libraryVectorLB = PreferencesController.getDouble(libraryVectorLB, "LIBRARY_VECTOR_LB", 0.5);
        return libraryVectorLB.getValue();
    }

    public static Double getLibraryVectorREF() {
        libraryVectorREF = PreferencesController.getDouble(libraryVectorREF, "LIBRARY_VECTOR_REF", 4.73);
        return libraryVectorREF.getValue();
    }

    public static Boolean getUseRemotePassword() {
        useRemotePassword = PreferencesController.getBoolean(useRemotePassword, "REMOTE_USE_PASSWORD", false);
        return useRemotePassword.getValue();
    }

    public static void setUseRemotePassword(boolean value) {
        useRemotePassword.setValue(value);
        PreferencesController.setBoolean("REMOTE_USE_PASSWORD", value);
    }

    public static String getRemoteUserName() {
        remoteUserName = PreferencesController.getString(remoteUserName, REMOTE_USER_STR, "");
        return remoteUserName.getValue();
    }

    public static StringProperty getRemoteUserNameProperty() {
        remoteUserName = PreferencesController.getString(remoteUserName, REMOTE_USER_STR, "");
        return remoteUserName;
    }

    public static void setRemoteUserName(String name) {
        remoteUserName.setValue(name);
        PreferencesController.setString(REMOTE_USER_STR, name);
    }

    public static String getRemoteHostName() {
        remoteHostName = PreferencesController.getString(remoteHostName, REMOTE_HOST_STR, "");
        return remoteHostName.getValue();
    }

    public static StringProperty getRemoteHostNameProperty() {
        remoteHostName = PreferencesController.getString(remoteHostName, REMOTE_HOST_STR, "");
        return remoteHostName;
    }

    public static void setRemoteHostName(String name) {
        remoteHostName.setValue(name);
        PreferencesController.setString(REMOTE_HOST_STR, name);
    }

    public static String getRemoteDirectory() {
        remoteDirectory = PreferencesController.getString(remoteDirectory, "REMOTE_DIRECTORY", "");
        return remoteDirectory.getValue();
    }

    public static void setRemoteDirectory(String name) {
        remoteDirectory.setValue(name);
        PreferencesController.setString("REMOTE_DIRECTORY", name);
    }

    public static String getLocalDirectory() {
        Path defaultDir = Paths.get(System.getProperty("user.home"), "nmrdata");
        localDirectory = PreferencesController.getString(localDirectory, "LOCAL_DIRECTORY", defaultDir.toString());
        return localDirectory.getValue();
    }

    public static void setLocalDirectory(String name) {
        localDirectory.setValue(name);
        PreferencesController.setString("LOCAL_DIRECTORY", name);
    }

    public static String getLocalResidueDirectory() {
        Path defaultDir = Paths.get(System.getProperty("user.home"), "nmrfx_residues");
        localResidueDirectory = PreferencesController.getString(localResidueDirectory, "LOCAL_RESIDUE_DIRECTORY", defaultDir.toString());
        return localResidueDirectory.getValue();
    }

    public static void setLocaResiduelDirectory(String name) {
        localResidueDirectory.setValue(name);
        PDBFile.setLocalResLibDir(name);
        PreferencesController.setString("LOCAL_RESIDUE_DIRECTORY", name);
    }

    public static void addPrefs(PreferencesController preferencesController) {
        PropertySheet prefSheet = preferencesController.getPrefSheet();
        IntRangeOperationItem libraryVectorSizeItem = new IntRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    libraryVectorSize.setValue((Integer) c);
                },
                getLibraryVectorSize(), 8, 17, "Spectrum Library", "VectorSize",
                "Log2 of size of simulated spectra, 8 -> 256, 9-> 512 etc.");
        DoubleRangeOperationItem libraryVectorLBItem = new DoubleRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    libraryVectorLB.setValue((Double) c);
                },
                getLibraryVectorLB(), 0, 10.0, "Spectrum Library", "VectorLW",
                "Line broadening (Hz) for simulated spectra");
        DoubleRangeOperationItem libraryVectorSFItem = new DoubleRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    libraryVectorSF.setValue((Double) c);
                },
                getLibraryVectorSF(), 40, 1200, "Spectrum Library", "VectorSF",
                "Spectrometer frequency (MHz) for simulated spectra");
        DoubleRangeOperationItem libraryVectorSWItem = new DoubleRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    libraryVectorSW.setValue((Double) c);
                },
                getLibraryVectorSW(), 1000, 16000, "Spectrum Library", "VectorSW",
                "Sweep Width (Hz) for simulated spectra");
        DoubleRangeOperationItem libraryVectorREFItem = new DoubleRangeOperationItem(prefSheet,
                (a, b, c) -> {
                    libraryVectorREF.setValue((Double) c);
                },
                getLibraryVectorREF(), 0, 10.0, "Spectrum Library", "VectorREF",
                "Center reference (PPM) for simulated spectra");

        FileOperationItem segmentLibraryFileItem = new FileOperationItem(prefSheet, (a, b, c) -> segmentLibraryFile.setValue(c.toString()), getSegmentLibraryFile(), "Spectrum Library", "Segment Library", "File containing segment library");

        TextOperationItem remoteUserItem = new TextOperationItem(prefSheet, (a, b, c) -> {
            setRemoteUserName((String) c);
        }, getRemoteUserName(), "Remote Data",
                "UserName",
                "User name on remote host");

        TextOperationItem remoteHostItem = new TextOperationItem(prefSheet, (a, b, c) -> {
            setRemoteHostName((String) c);
        }, getRemoteHostName(), "Remote Data",
                "HostName",
                "Name of remote host (server)");

        TextOperationItem remoteDirectoryItem = new TextOperationItem(prefSheet, (a, b, c) -> {
            setRemoteDirectory((String) c);
        }, getRemoteDirectory(), "Remote Data",
                "Directory",
                "Directory on remote host that stores data");

        BooleanOperationItem remoteUsePasswordItem = new BooleanOperationItem(prefSheet, (a, b, c) -> {
            setUseRemotePassword((Boolean) c);
        }, getUseRemotePassword(), "Remote Data", "UsePassword", "Prompt for password when connecting");

        TextOperationItem localDirectoryItem = new TextOperationItem(prefSheet, (a, b, c) -> {
            setLocalDirectory((String) c);
        }, getLocalDirectory(), "Local Data",
                "Directory",
                "Directory on local host that stores data");

        DirectoryOperationItem localResidueDirectoryItem = new DirectoryOperationItem(prefSheet, (a, b, c) -> {
            setLocaResiduelDirectory((String) c);
        }, getLocalResidueDirectory(), "Structure",
                "Local Residue Directory",
                "Directory for custom residues");

        FileOperationItem gissmoFileItem = new FileOperationItem(prefSheet,
                (a, b, c) -> {
                    PreferencesController.setString("GISSMO-FILE", (String) c);
                    gissmoFileProp.setValue((String) c);
                }
                , getGissmoFile(), "Spectrum Library", "User File", "File for GISSMO data");


        prefSheet.getItems().addAll(gissmoFileItem, libraryVectorSizeItem, libraryVectorLBItem,
                libraryVectorSFItem, libraryVectorSWItem, libraryVectorREFItem, segmentLibraryFileItem,
                localDirectoryItem,
                remoteHostItem, remoteDirectoryItem, remoteUserItem, remoteUsePasswordItem,
                localResidueDirectoryItem);

    }
    public static String getGissmoFile() {
        gissmoFileProp = PreferencesController.getString(gissmoFileProp, "GISSMO-FILE", "");
        return gissmoFileProp.getValue();
    }

    public static void setGissmoFile(String fileName) {
        gissmoFileProp.setValue(fileName);
        PreferencesController.setString("GISSMO-FILE", fileName);
    }


}
