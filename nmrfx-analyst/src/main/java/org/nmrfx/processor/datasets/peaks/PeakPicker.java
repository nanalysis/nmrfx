/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.math.VecBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DimCounter;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

import static org.nmrfx.datasets.Nuclei.*;

/**
 * @author brucejohnson
 */
@PythonAPI("dscript")
public class PeakPicker {
    private static final Logger log = LoggerFactory.getLogger(PeakPicker.class);

    private final Dataset dataset;
    private final PeakPickParameters peakPickPar;
    private final int nDataDim;
    private static final String MSG_PEAK_LIST = "Peak List ";
    Peak lastPeakPicked = null;
    int nPeaks;

    public PeakPicker(PeakPickParameters peakPickPar) {
        this.peakPickPar = peakPickPar;
        this.dataset = peakPickPar.theFile;
        nDataDim = dataset.getNDim();
        this.peakPickPar.fixLimits();
    }

    double readPoint(int[] pt, int[] dim) throws IOException {
        return dataset.readPoint(pt, dim);
    }

    double getSf(int i) {
        return dataset.getSf(i);
    }

    double getSw(int i) {
        return dataset.getSw(i);
    }

    int getSize(int i) {
        return dataset.getSizeReal(i);
    }

    public boolean checkForPeak(double centerValue, int[] pt,
                                int[] dimOrder, boolean findMax, boolean fixedPick, double regionSizeHz, int nPeakDim, int sign) {
        int[] checkPoint = new int[nDataDim];
        int[] deltaPoint = new int[nDataDim];
        int[] testPoint = new int[nDataDim];
        int[] regionSize = new int[nDataDim];
        boolean foundPeak = true;
        boolean ok;
        int i;
        boolean foundMax;
        double maxValue = Double.MIN_VALUE;
        if (fixedPick && (nDataDim == 1)) {
            return true;
        }
        if (fixedPick) {
            //myNDim = 1;
            // FIXME   should fixed pick search in "non-fixed" dimensions for max?
            //   code is mostly set up for this, but now I've got it set to just return true
            return true;
        }

        for (i = 0; i < nDataDim; i++) {
            if (regionSizeHz > 0.1) {
                regionSize[i] = (int) (regionSizeHz / dataset.getSw(i) * dataset.getSizeReal(i));
            } else {
                regionSize[i] = 2;
            }

            if (regionSize[i] < 1) {
                regionSize[i] = 1;
            }

            if (i >= nPeakDim) {
                regionSize[i] = 0;
            }

            testPoint[i] = pt[i];
        }
        do {
            ok = true;
            foundMax = false;

            for (i = 0; i < nDataDim; i++) {
                deltaPoint[i] = -regionSize[i];
                pt[i] = testPoint[i];
            }

            do {
                boolean isCenterPoint = true;
                for (i = 0; i < nDataDim; i++) {
                    if (deltaPoint[i] != 0) {
                        isCenterPoint = false;
                        checkPoint[i] = pt[i] + deltaPoint[i];
                        if (checkPoint[i] < 0) {
                            ok = false;
                        } else if (checkPoint[i] >= dataset.getSizeReal(dimOrder[i])) {
                            checkPoint[i] = checkPoint[i] - dataset.getSizeReal(dimOrder[i]);
                        }
                    } else {
                        checkPoint[i] = pt[i];
                    }
                }

                if (ok) {
                    double testValue = 0.0;
                    try {
                        testValue = sign * dataset.readPoint(checkPoint, dimOrder);
                    } catch (IOException | IllegalArgumentException e) {
                        log.error("{} {} {}", dimOrder[0], dimOrder[1], dimOrder[2]);
                        log.error("{} {} {} {}", checkPoint[0], checkPoint[1], checkPoint[2], e.getMessage(), e);
                        System.exit(1);
                    }

                    if (findMax) {
                        if (testValue > maxValue) {
                            foundMax = true;
                            maxValue = testValue;
                            System.arraycopy(checkPoint, 0, testPoint, 0, checkPoint.length);
                        }
                    } else if (!isCenterPoint && (testValue > centerValue)) {
                        foundPeak = false;
                        break;
                    }
                }

                for (i = 0; i < nDataDim; i++) {
                    deltaPoint[i]++;

                    if (deltaPoint[i] > regionSize[i]) {
                        deltaPoint[i] = -regionSize[i];
                    } else {
                        break;
                    }
                }

                if (i == nDataDim) {
                    break;
                }
            } while (true);
        } while (foundMax && foundPeak);
        return (foundPeak || fixedPick);
    }

    public boolean measurePeak(double threshold, int[] pt, double[] cpt,
                               int[] dimOrder, int[] dataToPk, boolean fixedPick, Peak peak, int nPeakDim,
                               double sDevN, int sign, boolean measurePeak) throws IOException {
        double testValue = 0.0;
        int[] checkPoint = new int[nDataDim];
        int[] maxWidth = new int[nDataDim];
        int[] minWidth = new int[nDataDim];
        double[] halfWidth = new double[2];
        double[] sideWidth = new double[2];
        int delta;
        boolean[] fold = new boolean[nDataDim];
        boolean[] f_ok = {false, false};
        double halfHeightValue;
        double[] f = {0.0, 0.0};
        double fPt;
        double centerValue;
        int i;
        int j;
        int iDir;

        centerValue = sign * readPoint(pt, dimOrder);
        if (!measurePeak) {
            for (i = 0; i < nDataDim; i++) {
                double bndHz = 15.0 * dataset.getSizeReal(dimOrder[i]) / dataset.getSw(dimOrder[i]);
                peak.peakDims[i].setLineWidthValue((float) dataset.ptWidthToPPM(dimOrder[i], bndHz / 2.0));
                peak.peakDims[i].setBoundsValue((float) dataset.ptWidthToPPM(dimOrder[i], bndHz));
                fPt = (float) cpt[i];
                peak.peakDims[i].setChemShiftValueNoCheck((float) dataset.pointToPPM(dimOrder[i], fPt));
            }
            peak.setIntensity((float) (sign * centerValue));
            return true;
        }

        for (i = 0; i < nDataDim; i++) {
            maxWidth[i] = (int) ((200.0 * dataset.getSizeReal(dimOrder[i])) / dataset.getSw(dimOrder[i]));

            if (maxWidth[i] < 3) {
                maxWidth[i] = 3;
            }

            fold[i] = true;

            if (nDataDim > 1) {
                minWidth[i] = 1;
            } else {
                minWidth[i] = 2;
            }
        }

        double minRequired = (centerValue * 0.95) - sDevN;
        halfHeightValue = (centerValue / 2.0);

        for (i = 0; i < nPeakDim; i++) {
            int dataDim = dimOrder[i];
            int peakDim = dataToPk[dataDim];
            peak.peakDims[peakDim].setLineWidthValue(0.0f);
            boolean[] widthOK = {true, true};
            for (iDir = 0; iDir < 2; iDir++) {
                sideWidth[iDir] = 0.0;
                halfWidth[iDir] = 0.0;

                if (iDir == 0) {
                    delta = -1;
                } else {
                    delta = 1;
                }

                boolean firstTime = true;
                boolean foundHalf = false;
                double previousValue = centerValue;

                System.arraycopy(pt, 0, checkPoint, 0, nDataDim);

                double minValue = centerValue;

                for (j = 1; j < maxWidth[i]; j++) {
                    checkPoint[i] += delta;

                    if (checkPoint[i] >= dataset.getSizeReal(dataDim)) {
                        if (fold[i] && firstTime) {
                            checkPoint[i] = 0;
                            firstTime = false;
                        } else {
                            break;
                        }
                    }

                    if (checkPoint[i] < 0) {
                        if (fold[i] && firstTime) {
                            checkPoint[i] = dataset.getSizeReal(dataDim) - 1;
                            firstTime = false;
                        } else {
                            break;
                        }
                    }

                    try {
                        testValue = sign * readPoint(checkPoint, dimOrder);
                    } catch (IOException e) {
                        log.warn("{} {} {} {} {}", i, delta, fold[i], dimOrder[i], checkPoint[i]);
                        log.warn("{} {} {} {}", checkPoint[0], checkPoint[1], checkPoint[2], e.getMessage(), e);
                        log.warn("{} {} {}", pt[0], pt[1], pt[2]);
                    }

                    if (testValue < minValue) {
                        minValue = testValue;
                    }

                    if (j == 1) {
                        f_ok[iDir] = true;
                        f[iDir] = testValue;
                    }

                    if (!foundHalf && (testValue < halfHeightValue)) {
                        halfWidth[iDir] = (j - 1) + ((previousValue - halfHeightValue) / (previousValue - testValue));
                        foundHalf = true;
                    }

                    if (testValue < threshold) {
                        if (j < minWidth[i]) {
                            widthOK[iDir] = false;
                        }

                        if (sideWidth[iDir] == 0.0) {
                            sideWidth[iDir] = (j - 1) + ((previousValue - threshold) / (previousValue - testValue));
                        }
                        if (foundHalf) {
                            break;
                        }
                    }

                    if ((testValue > centerValue)
                            || (testValue > (minValue + (0.05 * centerValue)
                            + sDevN))) {
                        sideWidth[iDir] = (j - 0.5);

                        if (!fixedPick && (minValue > minRequired)) {
                            return false;
                        }

                        break;
                    }

                    if (!fixedPick && (testValue > centerValue)) {
                        return false;
                    }

                    previousValue = testValue;
                }

                if (!fixedPick && (minValue > minRequired)) {
                    return false;
                }

                if (sideWidth[iDir] == 0.0) {
                    if (foundHalf) {
                        sideWidth[iDir] = halfWidth[iDir] / 0.7;
                    } else {
                        sideWidth[iDir] = maxWidth[i];
                    }
                }
            }
            if (!widthOK[0] && !widthOK[1]) {
                return false;
            }

            // when doing a fixedPick (where peak center is not located) use the largest side
            //  otherwise use the smaller side to keep from getting excessively wide peaks from
            //  a wide edge
            int useSide;

            if (sideWidth[0] > sideWidth[1]) {
                if (fixedPick) {
                    useSide = 0;
                } else {
                    useSide = 1;
                }
            } else if (fixedPick) {
                useSide = 1;
            } else {
                useSide = 0;
            }

            double bounds = sideWidth[0] + sideWidth[1];
            double bounds2 = 2.0 * sideWidth[useSide];
            if (bounds > 1.1 * bounds2) {
                bounds = 1.1 * bounds2;
            }

            peak.peakDims[peakDim].setBoundsValue((float) dataset.ptWidthToPPM(dataDim, bounds));

            double width = halfWidth[0] + halfWidth[1];
            double width2 = 2.0 * halfWidth[useSide];
            if (width > 1.1 * width2) {
                width = 1.1 * width2;
            }

            peak.peakDims[peakDim].setLineWidthValue((float) dataset.ptWidthToPPM(dataDim, width));

            if (peak.peakDims[peakDim].getLineWidthValue() < 1.0e-6) {
                peak.peakDims[peakDim].setLineWidthValue((float) (peak.peakDims[peakDim].getBoundsValue() * 0.7));
            }

            fPt = pt[i];

            if (!fixedPick) {
                if (f_ok[0] && f_ok[1]) {
                    fPt += ((f[1] - f[0]) / (2.0 * ((2.0 * centerValue) - f[1] - f[0])));
                }
            } else {
                fPt = (float) cpt[i];
            }

            peak.peakDims[peakDim].setChemShiftValueNoCheck((float) dataset.pointToPPM(dataDim, fPt));
        }

        peak.setIntensity((float) (sign * centerValue));

        return (true);
    }

    public void configureDim(SpectralDim sDim, int dDim) {
        sDim.setDimName(dataset.getLabel(dDim));
        sDim.setSf(getSf(dDim));
        sDim.setSw(getSw(dDim));
        sDim.setSize(getSize(dDim));
        double minTol = Math.round(100 * 2.0 * getSw(dDim) / getSf(dDim) / getSize(dDim)) / 100.0;
        double tol = minTol;
        Nuclei nuc = dataset.getNucleus(dDim);
        if (null != nuc) {
            tol = switch (nuc.getNameNumber()) {
                case "H1" -> 0.05;
                case "C13" -> 0.6;
                case "N15" -> 0.2;
                default -> minTol;
            };
        }
        tol = Math.min(tol, minTol);

        sDim.setIdTol(tol);
        sDim.setDataDim(dDim);
        sDim.setNucleus(dataset.getNucleus(dDim).getNumberName());

    }

    public PeakList refinePickWithLSCat() throws IOException, IllegalArgumentException {
        if (dataset.getLSCatalog() == null) {
            return null;
        }
        dataset.toBuffer("prepick");
        int[] rows = new int[dataset.getNDim()];  // only works if datset dims = peak list dims
        int nTries = 2;
        PeakList peakList = PeakList.get(peakPickPar.listName);
        PeakFitParameters fitPars = new PeakFitParameters();
        fitPars.lsFit(true);
        if (peakList != null) {
            for (int i = 0; i < nTries; i++) {
                try {
                    dataset.fromBuffer("prepick");
                    List<Peak> peaks = getPeaksInRegion();
                    PeakListTools.groupPeakListAndFit(peakList, dataset, rows, null, fitPars);
                    dataset.addPeakList(peakList, -1.0);
                    // split
                    // combine
                    purgeOverlappingPeaks(peaks);
                    purgeSmallPeaks(peaks);
                    purgeNarrowPeaks(peaks);

                    dataset.fromBuffer("prepick");
                    PeakListTools.groupPeakListAndFit(peakList, dataset, rows, null, fitPars);
                    dataset.addPeakList(peakList, -1.0);
                    if (i != (nTries - 1)) {
                        peakPickPar.mode = "append";
                        peakList = peakPick();
                    }
                } catch (PeakFitException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        }
        dataset.fromBuffer("prepick");
        dataset.removeBuffer("prepick");
        return peakList;
    }

    private void purgeSmallPeaks(List<Peak> peaks) throws IOException {
        for (Peak peak : peaks) {
            boolean aboveThreshold = dataset.getLSCatalog().
                    addToDatasetInterpolated(dataset, peak, 1.0, peakPickPar.level);
            if (!aboveThreshold) {
                log.info("purge {}", peak.getName());
                peak.setStatus(-1);
            }
        }
        if (!peaks.isEmpty()) {
            peaks.get(0).getPeakList().compress();
        }
    }

    private void purgeOverlappingPeaks(List<Peak> peaks) {
        for (Peak peakA : peaks) {
            for (Peak peakB : peaks) {
                if ((peakA.getStatus() >= 0) && (peakB.getStatus() >= 0) && (peakA != peakB) && (peakA.getIntensity() > peakB.getIntensity())) {
                    boolean overlaps = true;
                    for (int iDim = 0; iDim < peakA.peakList.getNDim(); iDim++) {
                        if (!peakA.overlapsLineWidth(peakB, iDim, 0.75)) {
                            overlaps = false;
                            break;
                        }
                    }
                    if (overlaps) {
                        peakB.setStatus(-1);
                    }
                }
            }
        }
        if (!peaks.isEmpty()) {
            peaks.get(0).getPeakList().compress();
        }
    }

    private void purgeNarrowPeaks(List<Peak> peaks) {
        if (!peaks.isEmpty()) {
            PeakList peakList = peaks.get(0).getPeakList();

            for (int iDim = 0; iDim < peakList.getNDim(); iDim++) {
                DescriptiveStatistics stats = peakList.widthDStats(iDim);
                double mean = stats.getMean();
                double stdDev = stats.getStandardDeviation();
                double tol = mean - 3.0 * stdDev;
                if (log.isInfoEnabled()) {
                    DecimalFormat decimalFormatter = new DecimalFormat("#######.###");
                    log.info("purge {} {} {}", decimalFormatter.format(mean), decimalFormatter.format(stdDev), decimalFormatter.format(tol));
                }
                for (Peak peak : peaks) {
                    if (peak.getPeakDim(iDim).getLineWidthHz() < tol) {
                        peak.setStatus(-1);
                    }
                }
            }
            if (!peaks.isEmpty()) {
                peakList.compress();
            }
        }
    }

    public PeakList peakPick()
            throws IOException, IllegalArgumentException {
        int[][] pt;
        int[] pkToData = new int[peakPickPar.nPeakDim];
        int[] dataToPk = new int[nDataDim];
        int[] dimOrder = peakPickPar.dim;
        int[] lastPoint = new int[nDataDim];
        nPeaks = 0;
        int nMatch;
        double checkValue;
        pt = peakPickPar.pt;
        Double noiseLevel = dataset.getNoiseLevel();
        lastPeakPicked = null;

        for (int i = 0; i < peakPickPar.nPeakDim; i++) {
            pkToData[i] = dimOrder[i];
        }
        Arrays.fill(dataToPk, -1);
        for (int i=0;i < pkToData.length;i++) {
            dataToPk[pkToData[i]] =i;
        }
        PeakList peakList = PeakList.get(peakPickPar.listName);
        boolean listExists = (peakList != null);
        String mode = peakPickPar.mode;
        boolean alreadyPeaksInRegion = false;
        if (listExists) {
            alreadyPeaksInRegion = anyPeaksInRegion();
            if (alreadyPeaksInRegion && mode.startsWith("append")) {
                removeExistingPeaks();
                peakList.compress();
                peakList.reNumber();
                alreadyPeaksInRegion = false;
            }
        }
        if (mode.equalsIgnoreCase("replaceif") && listExists) {
            mode = "replace";
        } else if (mode.equalsIgnoreCase("replaceif") && !listExists) {
            mode = "new";
        } else if (mode.equalsIgnoreCase("appendif") && !listExists) {
            mode = "new";
        } else if (mode.equalsIgnoreCase("appendif") && listExists) {
            mode = "append";
        } else if (mode.equalsIgnoreCase("appendregion") && !listExists) {
            mode = "new";
        } else if (mode.equalsIgnoreCase("appendregion") && alreadyPeaksInRegion) {
            mode = "replace";
        } else if (mode.equalsIgnoreCase("appendregion")) {
            mode = "append";
        }
        if (mode.equalsIgnoreCase("new")) {
            if (listExists) {
                throw new IllegalArgumentException(MSG_PEAK_LIST + peakPickPar.listName + " already exists");
            }

            peakList = new PeakList(peakPickPar.listName, peakPickPar.nPeakDim);
            peakList.fileName = dataset.getFileName();

            for (int i = 0; i < peakPickPar.nPeakDim; i++) {
                SpectralDim sDim = peakList.getSpectralDim(i);
                if (sDim == null) {
                    throw new IllegalArgumentException("Error picking list" + peakPickPar.listName + ", invalid dimension " + pkToData[i]);
                }
                configureDim(sDim, pkToData[i]);
            }
        } else if (mode.equalsIgnoreCase("append")) {
            if (peakList == null) {
                throw new IllegalArgumentException(MSG_PEAK_LIST + peakPickPar.listName + "doesn't exist");
            }

            if (peakList.nDim != peakPickPar.nPeakDim) {
                throw new IllegalArgumentException("Number of Peak List dimensions doesn't match pick parameters");
            }

            nMatch = 0;

            Arrays.fill(dataToPk, -1);
            for (int i = 0; i < peakList.nDim; i++) {
                for (int j = 0; j < dimOrder.length; j++) {
                    if (peakList.getSpectralDim(i).getIndex() == dimOrder[j]) {
                        pkToData[i] = dimOrder[j];
                        dataToPk[dimOrder[j]] = i;
                        nMatch++;
                        break;
                    }
                }
            }

            if (nMatch != peakList.nDim) {
                throw new IllegalArgumentException("Dimensions not equal to those of peak list!");
            }
        } else if (mode.equalsIgnoreCase("replace")) {
            if (!listExists) {
                throw new IllegalArgumentException(MSG_PEAK_LIST + peakPickPar.listName + " doesn't exist");
            }

            PeakList.remove(peakPickPar.listName);
            peakList = new PeakList(peakPickPar.listName, peakPickPar.nPeakDim);
            peakList.fileName = dataset.getFileName();
            for (int i = 0; i < peakPickPar.nPeakDim; i++) {
                SpectralDim sDim = peakList.getSpectralDim(i);
                configureDim(sDim, pkToData[i]);
            }
        }
        boolean findMax = !peakPickPar.fixedPick && peakPickPar.region.equalsIgnoreCase("point");

        if (peakList == null) {
            throw new IllegalArgumentException("nv_dataset peakPick: invalid mode");
        }

        SummaryStatistics stats = new SummaryStatistics();
        int nStatPoints = 1024;

        int[] counterSizes = new int[nDataDim];
        for (int i = 0; i < nDataDim; i++) {
            counterSizes[i] = pt[i][1] - pt[i][0] + 1;
        }
        int[] checkPoint = new int[nDataDim];
        Iterator<int[]> cIter;
        boolean filterMode = peakPickPar.filter && peakPickPar.filterList != null;
        int[] filtPkToData = null;
        if (filterMode) {
            filtPkToData = peakPickPar.filterList.getDimsForDataset(dataset, true);
            cIter =  (new PeakDimCounter(dataset, peakPickPar.filterList.peaks(), dimOrder, filtPkToData, pt, peakPickPar.filterWidth)).iterator();
        } else {
            cIter = (new DimCounter(counterSizes)).iterator();
        }
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            for (int i = 0; i < nDataDim; i++) {
                if (!filterMode) {
                    points[i] += pt[i][0];
                }
                checkPoint[i] = points[i];
            }
            checkValue = readPoint(points, dimOrder);
            int sign = 1;
            if (nDataDim > 1) {
                stats.addValue(checkValue);
                if (stats.getN() == nStatPoints) {
                    double stDev = stats.getStandardDeviation();
                    if ((noiseLevel == null) || (stDev < noiseLevel)) {
                        noiseLevel = stDev;
                    }
                    stats.clear();
                }
            }
            boolean measurePeak = true;
            if (!peakPickPar.fixedPick) {
                if ((checkValue >= 0.0) && (checkValue < peakPickPar.level)) {
                    continue;
                }
                if ((checkValue < 0.0) && (checkValue > -peakPickPar.level)) {
                    continue;
                }

                if ((checkValue < 0.0) && ((peakPickPar.posNeg & 2) == 0)) {
                    continue;
                }

                if ((checkValue > 0.0) && ((peakPickPar.posNeg & 1) == 0)) {
                    continue;
                }
            } else {
                if ((checkValue >= 0.0) && (checkValue < peakPickPar.level)) {
                    measurePeak = false;
                }
                if ((checkValue < 0.0) && (checkValue > -peakPickPar.level)) {
                    measurePeak = false;
                }
            }

            if (checkValue < 0.0) {
                sign = -1;
                checkValue *= -1;
            }
            if (checkForPeak(checkValue, checkPoint, dimOrder, findMax, peakPickPar.fixedPick,
                    peakPickPar.regionWidth, peakPickPar.nPeakDim, sign)) {
                boolean aboveNoise = true;
                if (peakPickPar.useNoise && (peakPickPar.noiseLimit > 0.001)) {
                    double noiseRatio = dataset.checkNoiseLevel(checkValue, checkPoint, dimOrder);
                    if (noiseRatio < peakPickPar.noiseLimit) {
                        aboveNoise = false;
                    }
                }
                if (aboveNoise) {
                    boolean samePeak = false;
                    if (findMax || peakPickPar.fixedPick) {
                        samePeak = true;
                        for (int ii = 0; ii < checkPoint.length; ii++) {
                            if (lastPoint[ii] != checkPoint[ii]) {
                                samePeak = false;
                                break;
                            }
                        }
                    }
                    if (!samePeak) {
                        Peak peak = new Peak(peakList, peakPickPar.nPeakDim);
                        if (measurePeak(peakPickPar.level, checkPoint, peakPickPar.cpt, dimOrder, dataToPk,
                                peakPickPar.fixedPick, peak,
                                peakPickPar.nPeakDim, peakPickPar.sDevN, sign, measurePeak)) {
                            Peak pickedPeak = peakList.addPeak(peak);
                            if (pickedPeak != null) {
                                nPeaks++;
                                lastPeakPicked = pickedPeak;
                            } else {
                                peakList.idLast--;
                            }
                        } else {
                            peakList.idLast--;
                        }
                        if (findMax || peakPickPar.fixedPick) {
                            System.arraycopy(checkPoint, 0, lastPoint, 0, checkPoint.length);
                        }

                    }
                }
            }
        }

        if ((noiseLevel != null) && (noiseLevel > 0.0)) {
            peakList.setFOM(noiseLevel);
        }
        dataset.setNoiseLevel(noiseLevel);
        peakList.reIndex();
        return peakList;
    }

    public boolean anyPeaksInRegion() {
        boolean foundAny = false;
        PeakList peakList = PeakList.get(peakPickPar.listName);
        if ((peakList != null) && (peakList.peaks() != null)) {
            double[][] limits = new double[nDataDim][2];
            int[] dimMap = new int[nDataDim];
            for (int i = 0; i < nDataDim; i++) {
                dimMap[i] = -1;
                int j = peakPickPar.dim[i];
                int[] pDims = peakList.getDimsForDataset(dataset);
                for (int k = 0; k < pDims.length; k++) {
                    if (pDims[k] == j) {
                        dimMap[i] = k;
                        break;
                    }
                }
                limits[i][1] = peakPickPar.theFile.pointToPPM(j, peakPickPar.pt[i][0]);
                limits[i][0] = peakPickPar.theFile.pointToPPM(j, peakPickPar.pt[i][1]);
            }
            Optional<Peak> firstPeak = peakList.peaks()
                    .stream()
                    .parallel()
                    .filter(peak -> peak.inRegion(limits, null, null, dimMap)).findFirst();
            foundAny = firstPeak.isPresent();
        }
        return foundAny;
    }

    void removeExistingPeaks() {
        getPeaksInRegion().forEach(Peak::delete);
    }

    public List<Peak> getPeaksInRegion() {
        List<Peak> peaks = Collections.emptyList();
        PeakList peakList = PeakList.get(peakPickPar.listName);
        if ((peakList != null) && (peakList.peaks() != null)) {
            double[][] limits = new double[nDataDim][2];
            for (int i = 0; i < nDataDim; i++) {
                int j = peakPickPar.dim[i];
                limits[i][1] = peakPickPar.theFile.pointToPPM(j, peakPickPar.pt[i][0]);
                limits[i][0] = peakPickPar.theFile.pointToPPM(j, peakPickPar.pt[i][1]);
            }
            peaks = peakList.peaks()
                    .stream()
                    .parallel()
                    .filter(p -> !p.isDeleted())
                    .filter(p -> p.inRegion(limits, null, null, peakPickPar.dim)).toList();
        }
        return peaks;
    }

    public Peak getLastPick() {
        return lastPeakPicked;
    }

    public static double calculateThreshold(Dataset dataset, boolean scaleToLargest) {
        int nWin = 32;
        double maxRatio = 50.0;
        double sdRatio = 5.0;
        return calculateThreshold(dataset, scaleToLargest, nWin, maxRatio, sdRatio);
    }

    public static double calculateThreshold(Dataset dataset, boolean scaleToLargest, int nWin, double maxRatio, double sdRatio) {
        Vec vec;
        double threshold;
        try {
            vec = dataset.readVector(0, 0);
        } catch (IOException ex) {
            log.error("Failed to get dataset vector", ex);
            return 0.0;
        }
        int size = vec.getSize();
        int sdevWin = Math.max(16, size / 64);
        double sDev = vec.sdev(sdevWin);
        threshold = 0.0;
        if (scaleToLargest) {
            int nIncr = size / nWin;
            List<Double> maxs = new ArrayList<>();
            for (int i = 0; i < size; i += nIncr) {
                int j = i + nIncr - 1;
                VecBase.IndexValue maxIndexVal = vec.maxIndex(i, j);
                double max = maxIndexVal.getValue();
                // Also get the smallest indexes, to account for negative peaks.
                VecBase.IndexValue minIndexVal = vec.minIndex(i, j);
                double min = minIndexVal.getValue();
                maxs.add(Math.max(Math.abs(max), Math.abs(min)));
            }
            Collections.sort(maxs);
            int nMax = maxs.size();
            double max = maxs.get(nMax - 3);

            threshold = max / maxRatio;
        }
        if (threshold < sdRatio * sDev) {
            if (dataset.getNucleus(0) == H1) {
                threshold = sdRatio * sDev;
            } else {
                threshold = sdRatio / 3.0 * sDev;
            }
        }
        return threshold;
    }
}
