/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import javafx.collections.ObservableList;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.io.PeakWriter;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;
import org.nmrfx.processor.datasets.peaks.PeakPicker;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author Bruce Johnson
 */
public class PeakPicking {
    private static final Logger log = LoggerFactory.getLogger(PeakPicking.class);

    static Consumer singlePickAction = null;

    public static void registerSinglePickAction(Consumer func) {
        singlePickAction = func;
    }

    public static void peakPickActive(FXMLController fxmlController, double level) {
        peakPickActive(fxmlController, false, level);
    }

    public static void peakPickActive(FXMLController fxmlController, boolean refineLS, Double level) {
        PolyChart chart = fxmlController.getActiveChart();
        ObservableList<DatasetAttributes> dataList = chart.getDatasetAttributes();
        dataList.stream().filter(dataAttr -> !dataAttr.isProjection())
                .forEach((DatasetAttributes dataAttr) -> peakPickActive(chart, dataAttr, chart.getCrossHairs().hasCrosshairRegion(), refineLS, level, false, null));
        chart.refresh();
    }

    private static String getListName(PolyChart chart, DatasetAttributes dataAttr) {
        String listName = null;
        List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
        for (PeakListAttributes peakAttr : peakAttrs) {
            if (peakAttr.getDatasetAttributes() == dataAttr) {
                listName = peakAttr.getPeakListName();
                break;
            }
        }
        if (listName == null) {
            listName = PeakList.getNameForDataset(dataAttr.getFileName());
        }
        return listName;
    }

    public static PeakList peakPickActive(PolyChart chart, DatasetAttributes dataAttr, boolean useCrossHairs, boolean refineLS,
                                          Double level, boolean saveFile, String listName) {
         return peakPickActive(chart, dataAttr,  null, useCrossHairs,  refineLS, level, saveFile, listName);
    }

    public static PeakList peakPickActive(PolyChart chart, DatasetAttributes dataAttr, double[][] region,
                                          Double level) {
        chart.getPeakListAttributes().clear();
        return peakPickActive(chart, dataAttr,  region, false,  false, level, false, null);
    }

    public static PeakList peakPickActive(PolyChart chart, DatasetAttributes dataAttr, double[][] region, boolean useCrossHairs, boolean refineLS,
        Double level, boolean saveFile, String listName) {
        DatasetBase datasetBase = dataAttr.getDataset();
        Dataset dataset = (Dataset) datasetBase;
        int nDim = dataset.getNDim();
        if (listName == null) {
            listName = getListName(chart, dataAttr);
        }
        PeakList testList = PeakList.get(listName);
        if (testList != null) {
            if (chart.is1D() && (testList.getNDim() != 1)) {
                GUIUtils.warn("Peak Picking", "Peak list exists and is not 1D");
                return null;
            } else if (!chart.is1D() && (testList.getNDim() == 1)) {
                GUIUtils.warn("Peak Picking", "Peak list exists and is 1D");
                return null;
            }
        }

        if (level == null) {
            level = dataAttr.getLvl();
            if (nDim == 1) {
                if (chart.getCrossHairs().getCrossHairState(0, PolyChart.HORIZONTAL)) {
                    level = chart.getCrossHairs().getPosition(0, PolyChart.HORIZONTAL);
                } else {
                    level /= 10.0;
                }
            }
        }
        PeakPickParameters peakPickPar = (new PeakPickParameters(dataset, listName)).level(level).mode("appendregion");
        peakPickPar.pos(dataAttr.getPos()).neg(dataAttr.getNeg());
        peakPickPar.calcRange();
        for (int iDim = 0; iDim < nDim; iDim++) {
            int jDim = dataAttr.getDim(iDim);
            if (iDim < 2) {
                if (region != null) {
                    if (region.length > iDim) {
                        peakPickPar.limit(jDim, region[iDim][0], region[iDim][1]);
                    } else {
                        List<Integer> drawList = chart.getDrawList();
                        int row = 0;
                        if (!drawList.isEmpty()) {
                            row = drawList.get(0);
                        }
                        peakPickPar.limit(jDim, row, row);
                    }
                } else if (useCrossHairs) {
                    int orientation = iDim == 0 ? PolyChart.VERTICAL : PolyChart.HORIZONTAL;
                    peakPickPar.limit(jDim,
                            chart.getCrossHairs().getPosition(0, orientation),
                            chart.getCrossHairs().getPosition(1, orientation));
                } else {
                    peakPickPar.limit(jDim, chart.axes[iDim].getLowerBound(), chart.axes[iDim].getUpperBound());
                }
            } else {
                int p1 = chart.axModes[iDim].getIndex(dataAttr, iDim, chart.axes[iDim].getLowerBound());
                int p2 = chart.axModes[iDim].getIndex(dataAttr, iDim, chart.axes[iDim].getUpperBound());
                if (dataAttr.drawList.isEmpty()) {
                    peakPickPar.limit(jDim, p1, p2);
                } else {
                    int firstPlane = dataAttr.drawList.get(0);
                    peakPickPar.limit(jDim, firstPlane, firstPlane);
                }
            }
        }
        PeakPicker picker = new PeakPicker(peakPickPar);
        PeakList peakList = null;
        try {
            if (refineLS) {
                peakList = picker.refinePickWithLSCat();
            } else {
                peakList = picker.peakPick();
            }
            if (peakList != null) {
                chart.setupPeakListAttributes(peakList);
                if (saveFile) {
                    String canonFileName = dataset.getCanonicalFile();
                    int lastDot = canonFileName.lastIndexOf(".");
                    String listFileName = lastDot < 0 ? canonFileName + ".xpk2"
                            : canonFileName.substring(0, lastDot) + ".xpk2";
                    try (final FileWriter writer = new FileWriter(listFileName)) {
                        PeakWriter peakWriter = new PeakWriter();
                        peakWriter.writePeaksXPK2(writer, peakList);
                    }
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
        DatasetBase datasetBase = dataAttr.getDataset();
        Dataset dataset = (Dataset) datasetBase;
        int nDim = dataset.getNDim();
        String listName = getListName(chart, dataAttr);
        PeakList testList = PeakList.get(listName);
        if (testList != null) {
            if (chart.is1D() && (testList.getNDim() != 1)) {
                GUIUtils.warn("Peak Picking", "Peak list exists and is not 1D");
                return null;
            } else if (!chart.is1D() && (testList.getNDim() == 1)) {
                GUIUtils.warn("Peak Picking", "Peak list exists and is 1D");
                return null;
            }
        }

        double level = dataAttr.getLvl();
        if (nDim == 1) {
            Double threshold = dataset.getNoiseLevel();
            if (threshold == null) {
                threshold = 0.0;
            }
            level = Math.max(3.0 * threshold, y);
        }
        PeakPickParameters peakPickPar = (new PeakPickParameters(dataset, listName)).level(level).mode("appendif");
        peakPickPar.pos(dataAttr.getPos()).neg(dataAttr.getNeg());
        peakPickPar.region("point").fixed(fixed);
        peakPickPar.calcRange();
        for (int iDim = 0; iDim < nDim; iDim++) {
            int jDim = dataAttr.getDim(iDim);
            if (iDim < 2) {
                double pos = iDim == 0 ? x : y;
                peakPickPar.limit(jDim, pos, pos);
            } else {
                if (chart.axModes[iDim] == DatasetAttributes.AXMODE.PTS) {
                    peakPickPar.limit(jDim, (int) chart.axes[iDim].getLowerBound(), (int) chart.axes[iDim].getUpperBound());
                } else {
                    peakPickPar.limit(jDim, chart.axes[iDim].getLowerBound(), chart.axes[iDim].getUpperBound());
                }
            }
        }
        PeakPicker picker = new PeakPicker(peakPickPar);
        String canonFileName = dataset.getCanonicalFile();
        int lastDot = canonFileName.lastIndexOf(".");
        String listFileName = lastDot < 0 ? canonFileName + ".xpk2"
                : canonFileName.substring(0, lastDot) + ".xpk2";
        PeakList peakList = null;
        Peak peak = null;
        try {
            peakList = picker.peakPick();
            if (peakList != null) {
                chart.setupPeakListAttributes(peakList);
                if (saveFile) {
                    try (final FileWriter writer = new FileWriter(listFileName)) {
                        PeakWriter peakWriter = new PeakWriter();
                        peakWriter.writePeaksXPK2(writer, peakList);
                    }
                }
                peak = picker.getLastPick();
            }
        } catch (IOException | InvalidPeakException ioE) {
            ExceptionDialog dialog = new ExceptionDialog(ioE);
            dialog.showAndWait();
        }
        chart.peakStatus.set(true);
        if ((peak != null)) {
            FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
            if (controller.isPeakAttrControllerShowing()) {
                controller.getPeakAttrController().gotoPeak(peak);
            }
        }
        if ((peak != null) && (singlePickAction != null)) {
            singlePickAction.accept(peak);
        }
        return peakList;
    }

}
