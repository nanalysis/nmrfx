/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import java.io.FileWriter;
import java.io.IOException;
import javafx.collections.ObservableList;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakPick;
import org.nmrfx.processor.datasets.peaks.PeakPicker;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

/**
 *
 * @author Bruce Johnson
 */
public class PeakPicking {

    public static void peakPickActive(FXMLController fxmlController) {
        PolyChart chart = fxmlController.getActiveChart();
        ObservableList<DatasetAttributes> dataList = chart.getDatasetAttributes();
        dataList.stream().forEach((DatasetAttributes dataAttr) -> {
            peakPickActive(chart, dataAttr, true, true, null);
        });
        chart.refresh();
    }

    public static PeakList peakPickActive(PolyChart chart, DatasetAttributes dataAttr, boolean useCrossHairs, boolean saveFile, String listName) {
        Dataset dataset = dataAttr.getDataset();
        int nDim = dataset.getNDim();
        String datasetName = dataset.getName();
        if (listName == null) {
            listName = PeakList.getNameForDataset(datasetName);
        }
        double level = dataAttr.getLevel();
        if (nDim == 1) {
            level = chart.crossHairPositions[0][PolyChart.HORIZONTAL];
        }
        PeakPick peakPickPar = (new PeakPick(dataset, listName)).level(level).mode("appendregion");
        peakPickPar.pos(dataAttr.getPosDrawOn()).neg(dataAttr.getNegDrawOn());
        peakPickPar.calcRange();
        for (int iDim = 0; iDim < nDim; iDim++) {
            int jDim = dataAttr.getDim(iDim);
            if (iDim < 2) {
                if (useCrossHairs) {
                    int orientation = iDim == 0 ? PolyChart.VERTICAL : PolyChart.HORIZONTAL;
                    peakPickPar.limit(jDim,
                            chart.crossHairPositions[0][orientation],
                            chart.crossHairPositions[1][orientation]);
                } else {
                    peakPickPar.limit(jDim, chart.axes[iDim].getLowerBound(), chart.axes[iDim].getUpperBound());
                }
            } else {
                peakPickPar.limit(jDim, (int) chart.axes[iDim].getLowerBound(), (int) chart.axes[iDim].getUpperBound());
            }
        }
        PeakPicker picker = new PeakPicker(peakPickPar);
        String canonFileName = dataset.getCanonicalFile();
        String listFileName = canonFileName.substring(0, canonFileName.lastIndexOf(".")) + ".xpk";
        PeakList peakList = null;
        try {
            peakList = picker.peakPick();
            chart.setupPeakListAttributes(peakList);
            if (saveFile) {
                try (final FileWriter writer = new FileWriter(listFileName)) {
                    peakList.writePeaksXPK(writer);
                }
            }
        } catch (IOException | InvalidPeakException ioE) {
            ExceptionDialog dialog = new ExceptionDialog(ioE);
            dialog.showAndWait();
        }
        chart.peakStatus.set(true);
        return peakList;
    }

    public static PeakList pickAtPosition(PolyChart chart, DatasetAttributes dataAttr, double x, double y, boolean fixed, boolean saveFile) {
        Dataset dataset = dataAttr.getDataset();
        int nDim = dataset.getNDim();
        String datasetName = dataset.getName();
        String listName = PeakList.getNameForDataset(datasetName);
        double level = dataAttr.getLevel();
        if (nDim == 1) {
            level = chart.crossHairPositions[0][PolyChart.HORIZONTAL];
        }
        PeakPick peakPickPar = (new PeakPick(dataset, listName)).level(level).mode("appendif");
        peakPickPar.pos(dataAttr.getPosDrawOn()).neg(dataAttr.getNegDrawOn());
        peakPickPar.region("point").fixed(fixed);
        peakPickPar.calcRange();
        for (int iDim = 0; iDim < nDim; iDim++) {
            int jDim = dataAttr.getDim(iDim);
            if (iDim < 2) {
                double pos = iDim == 0 ? x : y;
                peakPickPar.limit(jDim, pos, pos);
            } else {
                peakPickPar.limit(jDim, (int) chart.axes[iDim].getLowerBound(), (int) chart.axes[iDim].getUpperBound());
            }
        }
        PeakPicker picker = new PeakPicker(peakPickPar);
        String canonFileName = dataset.getCanonicalFile();
        String listFileName = canonFileName.substring(0, canonFileName.lastIndexOf(".")) + ".xpk";
        PeakList peakList = null;
        try {
            peakList = picker.peakPick();
            if (saveFile) {
                try (final FileWriter writer = new FileWriter(listFileName)) {
                    peakList.writePeaksXPK(writer);
                }
            }
        } catch (IOException | InvalidPeakException ioE) {
            ExceptionDialog dialog = new ExceptionDialog(ioE);
            dialog.showAndWait();
        }
        chart.peakStatus.set(true);
        return peakList;
    }

}
