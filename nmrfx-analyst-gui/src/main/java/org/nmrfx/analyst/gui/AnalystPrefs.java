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
 *
 * @author brucejohnson
 */
public class AnalystPrefs {

    static IntegerProperty libraryVectorSize = null;

    public static Integer getLibraryVectorSize() {
        libraryVectorSize = PreferencesController.getInteger(libraryVectorSize, "LIBRARY_VECTOR_SIZE", 14);
        return libraryVectorSize.getValue();
    }

    static DoubleProperty libraryVectorSF = null;

    public static Double getLibraryVectorSF() {
        libraryVectorSF = PreferencesController.getDouble(libraryVectorSF, "LIBRARY_VECTOR_SF", 600.0);
        return libraryVectorSF.getValue();
    }

    static DoubleProperty libraryVectorSW = null;

    public static Double getLibraryVectorSW() {
        libraryVectorSW = PreferencesController.getDouble(libraryVectorSW, "LIBRARY_VECTOR_SW", 10000.0);
        return libraryVectorSW.getValue();
    }

    static DoubleProperty libraryVectorLB = null;

    public static Double getLibraryVectorLB() {
        libraryVectorLB = PreferencesController.getDouble(libraryVectorLB, "LIBRARY_VECTOR_LB", 0.5);
        return libraryVectorLB.getValue();
    }

    static DoubleProperty libraryVectorREF = null;

    public static Double getLibraryVectorREF() {
        libraryVectorREF = PreferencesController.getDouble(libraryVectorREF, "LIBRARY_VECTOR_REF", 4.73);
        return libraryVectorREF.getValue();
    }

    static BooleanProperty useRemotePassword = null;

    public static Boolean getUseRemotePassword() {
        useRemotePassword = PreferencesController.getBoolean(useRemotePassword, "REMOTE_USE_PASSWORD", false);
        return useRemotePassword.getValue();
    }

    public static void setUseRemotePassword(boolean value) {
        useRemotePassword.setValue(value);
        PreferencesController.setBoolean("REMOTE_USE_PASSWORD", value);
    }

    static StringProperty remoteUserName = null;

    public static String getRemoteUserName() {
        remoteUserName = PreferencesController.getString(remoteUserName, "REMOTE_USER_NAME", "");
        return remoteUserName.getValue();
    }

    public static void setRemoteUserName(String name) {
        remoteUserName.setValue(name);
        PreferencesController.setString("REMOTE_USER_NAME", name);
    }

    static StringProperty remoteHostName = null;

    public static String getRemoteHostName() {
        remoteHostName = PreferencesController.getString(remoteHostName, "REMOTE_HOST", "");
        return remoteHostName.getValue();
    }

    public static void setRemoteHostName(String name) {
        remoteHostName.setValue(name);
        PreferencesController.setString("REMOTE_HOST", name);
    }

    static StringProperty remoteDirectory = null;

    public static String getRemoteDirectory() {
        remoteDirectory = PreferencesController.getString(remoteDirectory, "REMOTE_DIRECTORY", "");
        return remoteDirectory.getValue();
    }

    public static void setRemoteDirectory(String name) {
        remoteDirectory.setValue(name);
        PreferencesController.setString("REMOTE_DIRECTORY", name);
    }

    static StringProperty localDirectory = null;

    public static String getLocalDirectory() {
        Path defaultDir = Paths.get(System.getProperty("user.home"), "nmrdata");
        localDirectory = PreferencesController.getString(localDirectory, "LOCAL_DIRECTORY", defaultDir.toString());
        return localDirectory.getValue();
    }

    public static void setLocalDirectory(String name) {
        localDirectory.setValue(name);
        PreferencesController.setString("LOCAL_DIRECTORY", name);
    }

    static StringProperty localResidueDirectory = null;

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
        IntRangeOperationItem libraryVectorSizeItem = new IntRangeOperationItem(
                (a, b, c) -> {
                    libraryVectorSize.setValue((Integer) c);
                },
                getLibraryVectorSize(), 8, 17, "Spectrum Library", "VectorSize",
                "Log2 of size of simulated spectra, 8 -> 256, 9-> 512 etc.");
        DoubleRangeOperationItem libraryVectorLBItem = new DoubleRangeOperationItem(
                (a, b, c) -> {
                    libraryVectorLB.setValue((Double) c);
                },
                getLibraryVectorLB(), 0, 10.0, "Spectrum Library", "VectorLW",
                "Line broadening (Hz) for simulated spectra");
        DoubleRangeOperationItem libraryVectorSFItem = new DoubleRangeOperationItem(
                (a, b, c) -> {
                    libraryVectorSF.setValue((Double) c);
                },
                getLibraryVectorSF(), 40, 1200, "Spectrum Library", "VectorSF",
                "Spectrometer frequency (MHz) for simulated spectra");
        DoubleRangeOperationItem libraryVectorSWItem = new DoubleRangeOperationItem(
                (a, b, c) -> {
                    libraryVectorSW.setValue((Double) c);
                },
                getLibraryVectorSW(), 1000, 16000, "Spectrum Library", "VectorSW",
                "Sweep Width (Hz) for simulated spectra");
        DoubleRangeOperationItem libraryVectorREFItem = new DoubleRangeOperationItem(
                (a, b, c) -> {
                    libraryVectorREF.setValue((Double) c);
                },
                getLibraryVectorREF(), 0, 10.0, "Spectrum Library", "VectorREF",
                "Center reference (PPM) for simulated spectra");

        TextOperationItem remoteUserItem = new TextOperationItem((a, b, c) -> {
            setRemoteUserName((String) c);
        }, getRemoteUserName(), "Remote Data",
                "UserName",
                "User name on remote host");

        TextOperationItem remoteHostItem = new TextOperationItem((a, b, c) -> {
            setRemoteHostName((String) c);
        }, getRemoteHostName(), "Remote Data",
                "HostName",
                "Name of remote host (server)");

        TextOperationItem remoteDirectoryItem = new TextOperationItem((a, b, c) -> {
            setRemoteDirectory((String) c);
        }, getRemoteDirectory(), "Remote Data",
                "Directory",
                "Directory on remote host that stores data");

        BooleanOperationItem remoteUsePasswordItem = new BooleanOperationItem((a, b, c) -> {
            setUseRemotePassword((Boolean) c);
        }, getUseRemotePassword(), "Remote Data", "UsePassword", "Prompt for password when connecting");

        TextOperationItem localDirectoryItem = new TextOperationItem((a, b, c) -> {
            setLocalDirectory((String) c);
        }, getLocalDirectory(), "Local Data",
                "Directory",
                "Directory on local host that stores data");

        DirectoryOperationItem localResidueDirectoryItem = new DirectoryOperationItem((a, b, c) -> {
            setLocaResiduelDirectory((String) c);
        }, getLocalResidueDirectory(), "Structure",
                "Local Residue Directory",
                "Directory for custom residues");

        prefSheet.getItems().addAll(libraryVectorSizeItem, libraryVectorLBItem,
                libraryVectorSFItem, libraryVectorSWItem, libraryVectorREFItem,
                localDirectoryItem,
                remoteHostItem, remoteDirectoryItem, remoteUserItem, remoteUsePasswordItem,
                localResidueDirectoryItem);

    }

}
