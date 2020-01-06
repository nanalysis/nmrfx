/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import org.controlsfx.control.PropertySheet;
import static org.nmrfx.processor.gui.MainApp.preferencesController;
import org.nmrfx.utils.properties.DoubleRangeOperationItem;
import org.nmrfx.utils.properties.IntRangeOperationItem;

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

    public static void addPrefs() {
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
                getLibraryVectorLB(), 0, 10.0, "Spectrum Library", "VectorSW",
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

        prefSheet.getItems().addAll(libraryVectorSizeItem, libraryVectorLBItem,
                libraryVectorSFItem, libraryVectorSWItem, libraryVectorREFItem);

    }

}
