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

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DimCounter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.processor.datasets.Nuclei;

/**
 *
 * @author brucejohnson
 */
public class PeakPicker {

    private final Dataset dataset;
    private final PeakPickParameters peakPickPar;
    private final int nDim;
    static final private String MSG_PEAK_LIST = "Peak List ";
    Peak lastPeakPicked = null;

    public PeakPicker(PeakPickParameters peakPickPar) {
        this.peakPickPar = peakPickPar;
        this.dataset = peakPickPar.theFile;
        nDim = dataset.getNDim();
        this.peakPickPar.fixLimits();
    }

    double readPoint(int[] pt, int[] dim) throws IOException {
        return dataset.readPoint(pt, dim);
    }

    String getLabel(int i) {
        return dataset.getLabel(i);
    }

    double getSf(int i) {
        return dataset.getSf(i);
    }

    double getSw(int i) {
        return dataset.getSw(i);
    }

    int getSize(int i) {
        return dataset.getSize(i);
    }

    public boolean checkForPeak(double centerValue, int[] pt,
            int[] dim, boolean findMax, boolean fixedPick, double regionSizeHz, int nPeakDim, int sign) {
        int[] checkPoint = new int[nDim];
        int[] deltaPoint = new int[nDim];
        int[] testPoint = new int[nDim];
        int[] regionSize = new int[nDim];
        boolean foundPeak = true;
        boolean ok;
        int i;
        boolean foundMax;
        double maxValue = Double.MIN_VALUE;
        if (fixedPick && (nDim == 1)) {
            return true;
        }
        if (fixedPick) {
            //myNDim = 1;
            // FIXME   should fixed pick search in "non-fixed" dimensions for max?
            //   code is mostly set up for this, but now I've got it set to just return true
            return true;
        }

        for (i = 0; i < nDim; i++) {
            if (regionSizeHz > 0.1) {
                regionSize[i] = (int) (regionSizeHz / dataset.getSw(i) * dataset.getSize(i));
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

            for (i = 0; i < nDim; i++) {
                deltaPoint[i] = -regionSize[i];
                pt[i] = testPoint[i];
            }

            while (true) {
                boolean isCenterPoint = true;
                for (i = 0; i < nDim; i++) {
                    if (deltaPoint[i] != 0) {
                        isCenterPoint = false;
                        checkPoint[i] = pt[i] + deltaPoint[i];
                        if (checkPoint[i] < 0) {
                            ok = false;
                        } else if (checkPoint[i] >= dataset.getSize(dim[i])) {
                            checkPoint[i] = checkPoint[i] - dataset.getSize(dim[i]);
                        }
                    } else {
                        checkPoint[i] = pt[i];
                    }
                }

                if (ok) {
                    double testValue = 0.0;
                    try {
                        testValue = sign * dataset.readPoint(checkPoint, dim);
                    } catch (IOException | IllegalArgumentException e) {
                        System.err.println(dim[0] + " " + dim[1] + " "
                                + dim[2]);
                        System.err.println(checkPoint[0] + " " + checkPoint[1]
                                + " " + checkPoint[2] + e.getMessage());
                        System.exit(1);
                    }

                    if (findMax) {
                        if (testValue > maxValue) {
                            foundMax = true;
                            maxValue = testValue;

                            for (i = 0; i < nDim; i++) {
                                testPoint[i] = checkPoint[i];
                            }
                        }
                    } else if (!isCenterPoint && (testValue > centerValue)) {
                        foundPeak = false;
                        break;
                    }
                }

                for (i = 0; i < nDim; i++) {
                    deltaPoint[i]++;

                    if (deltaPoint[i] > regionSize[i]) {
                        deltaPoint[i] = -regionSize[i];
                    } else {
                        break;
                    }
                }

                if (i == nDim) {
                    break;
                }
            }
        } while (foundMax && foundPeak);
        return (foundPeak || fixedPick);
    }

    public boolean measurePeak(double threshold, int[] pt, double[] cpt,
            int[] dim, int[] pldim, boolean fixedPick, Peak peak, int nPeakDim,
            double sDevN, int sign, boolean measurePeak) throws IOException {
        double testValue = 0.0;
        int[] checkPoint = new int[nDim];
        int[] maxWidth = new int[nDim];
        int[] minWidth = new int[nDim];
        double[] halfWidth = new double[2];
        double[] sideWidth = new double[2];
        int delta;
        boolean[] fold = new boolean[nDim];
        boolean[] f_ok = {false, false};
        double halfHeightValue;
        double[] f = {0.0, 0.0};
        double fPt;
        double centerValue;
        int i;
        int j;
        int k;
        int iDir;

        centerValue = sign * readPoint(pt, dim);
        if (!measurePeak) {
            for (i = 0; i < nDim; i++) {
                double bndHz = 15.0 * dataset.getSize(dim[i]) / dataset.getSw(dim[i]);
                peak.peakDims[i].setLineWidthValue((float) dataset.ptWidthToPPM(dim[i], bndHz / 2.0));
                peak.peakDims[i].setBoundsValue((float) dataset.ptWidthToPPM(dim[i], bndHz));
                fPt = (float) cpt[i];
                peak.peakDims[i].setChemShiftValueNoCheck((float) dataset.pointToPPM(dim[i], fPt));
            }
            peak.setIntensity((float) (sign * centerValue));
            return true;
        }

        for (i = 0; i < nDim; i++) {
            maxWidth[i] = (int) ((200.0 * dataset.getSize(dim[i])) / dataset.getSw(dim[i]));

            if (maxWidth[i] < 3) {
                maxWidth[i] = 3;
            }

            fold[i] = true;

            if (nDim > 1) {
                minWidth[i] = 1;
            } else {
                minWidth[i] = 2;
            }
        }

        double minRequired = (centerValue * 0.95) - sDevN;
        halfHeightValue = (centerValue / 2.0);

        for (i = 0; i < nPeakDim; i++) {
            peak.peakDims[pldim[i]].setLineWidthValue(0.0f);
            boolean widthOK[] = {true, true};
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

                for (k = 0; k < nDim; k++) {
                    checkPoint[k] = pt[k];
                }

                double minValue = centerValue;

                for (j = 1; j < maxWidth[i]; j++) {
                    checkPoint[i] += delta;

                    if (checkPoint[i] >= dataset.getSize(dim[i])) {
                        if (fold[i] && firstTime) {
                            checkPoint[i] = 0;
                            firstTime = false;
                        } else {
                            break;
                        }
                    }

                    if (checkPoint[i] < 0) {
                        if (fold[i] && firstTime) {
                            checkPoint[i] = dataset.getSize(dim[i]) - 1;
                            firstTime = false;
                        } else {
                            break;
                        }
                    }

                    try {
                        testValue = sign * readPoint(checkPoint, dim);
                    } catch (IOException e) {
                        System.err.println(i + " " + delta + " " + fold[i]
                                + " " + dim[i] + " " + checkPoint[i]);
                        System.err.println(checkPoint[0] + " " + checkPoint[1]
                                + " " + checkPoint[2] + e.getMessage());
                        System.err.println(pt[0] + " " + pt[1] + " " + pt[2]
                                + e.getMessage());
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
                            //return false;
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

            peak.peakDims[pldim[i]].setBoundsValue((float) dataset.ptWidthToPPM(dim[i], bounds));

            double width = halfWidth[0] + halfWidth[1];
            double width2 = 2.0 * halfWidth[useSide];
            if (width > 1.1 * width2) {
                width = 1.1 * width2;
            }

            peak.peakDims[pldim[i]].setLineWidthValue((float) dataset.ptWidthToPPM(dim[i], width));

            if (peak.peakDims[pldim[i]].getLineWidthValue() == 0.0) {
                peak.peakDims[pldim[i]].setLineWidthValue((float) (peak.peakDims[pldim[i]].getBoundsValue() * 0.7));
            }

            fPt = (float) pt[i];

            if (!fixedPick) {
                if (f_ok[0] && f_ok[1]) {
                    fPt += ((f[1] - f[0]) / (2.0 * ((2.0 * centerValue) - f[1] - f[0])));
                }
            } else {
                fPt = (float) cpt[i];
            }

            peak.peakDims[pldim[i]].setChemShiftValueNoCheck((float) dataset.pointToPPM(dim[i], fPt));
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
            switch (nuc) {
                case H1:
                    tol = 0.05;
                    break;
                case C13:
                    tol = 0.6;
                    break;
                case N15:
                    tol = 0.2;
                    break;
                default:
                    tol = minTol;
            }
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
        if (peakList != null) {
            for (int i = 0; i < nTries; i++) {
                try {
                    dataset.fromBuffer("prepick");
                    List<Peak> peaks = getPeaksInRegion();
                    peakList.peakFit(dataset, rows, peaks, true, -1, false);
                    dataset.addPeakList(peakList, -1.0);
                    // split
                    // combine
                    purgeOverlappingPeaks(peaks);
                    purgeSmallPeaks(peaks);
                    purgeNarrowPeaks(peaks);

                    dataset.fromBuffer("prepick");
                    peakList.peakFit(dataset, rows, getPeaksInRegion(), true, -1, false);
                    dataset.addPeakList(peakList, -1.0);
                    if (i != (nTries - 1)) {
                        peakPickPar.mode = "append";
                        peakList = peakPick();
                    }
                } catch (PeakFitException ex) {
                    Logger.getLogger(PeakPicker.class.getName()).log(Level.SEVERE, null, ex);
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
                System.out.println("purge " + peak.getName());
                peak.setStatus(-1);
            }
        }
        if (!peaks.isEmpty()) {
            peaks.get(0).peakList.compress();
        }
    }

    private void purgeOverlappingPeaks(List<Peak> peaks) throws IOException {
        for (Peak peakA : peaks) {
            for (Peak peakB : peaks) {
                if ((peakA.getStatus() >= 0) && (peakB.getStatus() >= 0)) {
                    if ((peakA != peakB) && (peakA.getIntensity() > peakB.getIntensity())) {
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

        }
        if (!peaks.isEmpty()) {
            peaks.get(0).peakList.compress();
        }
    }

    private void purgeNarrowPeaks(List<Peak> peaks) throws IOException {
        if (!peaks.isEmpty()) {
            PeakList peakList = peaks.get(0).peakList;

            for (int iDim = 0; iDim < peakList.getNDim(); iDim++) {
                DescriptiveStatistics stats = peakList.widthDStats(iDim);
                double mean = stats.getMean();
                double stdDev = stats.getStandardDeviation();
                double tol = mean - 3.0 * stdDev;
                System.out.printf("purge %7.3f %7.3f %7.3f\n", mean, stdDev, tol);
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
        int[] dim;
        int[][] pt;
        int[] pdim = new int[nDim];
        int[] checkPoint = new int[nDim];
        int[] lastPoint = new int[nDim];
        int nPeaks = 0;
        int nMatch;
        double checkValue;
        dim = peakPickPar.dim;
        pt = peakPickPar.pt;
        Double noiseLevel = dataset.getNoiseLevel();
        lastPeakPicked = null;

        if (nDim == peakPickPar.nPeakDim) {
            System.arraycopy(dim, 0, pdim, 0, pdim.length);
        } else {
            for (int i = 0; i < peakPickPar.nPeakDim; i++) {
                pdim[i] = i;
            }
        }
        PeakList peakList = PeakList.get(peakPickPar.listName);
        boolean listExists = (peakList != null);
        String mode = peakPickPar.mode;
        boolean alreadyPeaksInRegion = false;
        if (listExists) {
            alreadyPeaksInRegion = anyPeaksInRegion();
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
        } else if (mode.equalsIgnoreCase("appendregion") && !alreadyPeaksInRegion) {
            mode = "append";
        }

        if (mode.equalsIgnoreCase("new")) {
            if (listExists) {
                throw new IllegalArgumentException(MSG_PEAK_LIST + peakPickPar.listName + " already exists");
            }

            peakList = new PeakList(peakPickPar.listName, peakPickPar.nPeakDim);
            peakList.fileName = dataset.getFileName();

            for (int i = 0; i < peakPickPar.nPeakDim; i++) {
                SpectralDim sDim = peakList.getSpectralDim(pdim[i]);
                if (sDim == null) {
                    throw new IllegalArgumentException("Error picking list" + peakPickPar.listName + ", invalid dimension " + pdim[i]);
                }
                configureDim(sDim, dim[i]);
            }
        } else if (mode.equalsIgnoreCase("append")) {
            if (peakList == null) {
                throw new IllegalArgumentException(MSG_PEAK_LIST + peakPickPar.listName + "doesn't exist");
            }

            if (peakList.nDim != peakPickPar.nPeakDim) {
                throw new IllegalArgumentException("Number of Peak List dimensions doesn't match pick parameters");
            }

            nMatch = 0;

            for (int i = 0; i < peakList.nDim; i++) {
                for (int j = 0; j < peakPickPar.dim.length; j++) {
                    if (peakList.getSpectralDim(i).getDataDim() == peakPickPar.dim[j]) {
                        pdim[j] = i;
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
                SpectralDim sDim = peakList.getSpectralDim(pdim[i]);
                configureDim(sDim, dim[i]);
            }

        }
        boolean findMax = !peakPickPar.fixedPick && peakPickPar.region.equalsIgnoreCase("point");

        if (peakList == null) {
            throw new IllegalArgumentException("nv_dataset peakPick: invalid mode");
        }

        SummaryStatistics stats = new SummaryStatistics();
        int nStatPoints = 1024;

        int[] counterSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            counterSizes[i] = pt[i][1] - pt[i][0] + 1;
        }
        DimCounter counter = new DimCounter(counterSizes);
        DimCounter.Iterator cIter = counter.iterator();
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            for (int i = 0; i < nDim; i++) {
                points[i] += pt[i][0];
                checkPoint[i] = points[i];
            }
            checkValue = readPoint(points, dim);
            int sign = 1;
            stats.addValue(checkValue);
            if (stats.getN() == nStatPoints) {
                double stDev = stats.getStandardDeviation();
                if ((noiseLevel == null) || (stDev < noiseLevel)) {
                    noiseLevel = stDev;
                }
                stats.clear();
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
            if (checkForPeak(checkValue, checkPoint, dim, findMax, peakPickPar.fixedPick,
                    peakPickPar.regionWidth, peakPickPar.nPeakDim, sign)) {
                boolean aboveNoise = true;
                if (peakPickPar.noiseLimit > 0.001) {
                    double noiseRatio = dataset.checkNoiseLevel(checkValue, checkPoint, dim);
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
                        if (measurePeak(peakPickPar.level, checkPoint, peakPickPar.cpt, dim, pdim,
                                peakPickPar.fixedPick, peak,
                                peakPickPar.nPeakDim, peakPickPar.sDevN, sign, measurePeak)) {
                            nPeaks++;
                            peakList.addPeak(peak);
                            lastPeakPicked = peak;
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
            double[][] limits = new double[nDim][2];
            int[] dimMap = new int[nDim];
            for (int i = 0; i < nDim; i++) {
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
                    .filter(peak -> peak.inRegion(limits, null, dimMap)).findFirst();
            foundAny = firstPeak.isPresent();
        }
        return foundAny;
    }

    public List<Peak> getPeaksInRegion() {
        List<Peak> peaks = Collections.EMPTY_LIST;
        PeakList peakList = PeakList.get(peakPickPar.listName);
        if ((peakList != null) && (peakList.peaks() != null)) {
            double[][] limits = new double[nDim][2];
            for (int i = 0; i < nDim; i++) {
                int j = peakPickPar.dim[i];
                limits[i][1] = peakPickPar.theFile.pointToPPM(j, peakPickPar.pt[i][0]);
                limits[i][0] = peakPickPar.theFile.pointToPPM(j, peakPickPar.pt[i][1]);
            }
            peaks = peakList.peaks()
                    .stream()
                    .parallel()
                    .filter(p -> !p.isDeleted())
                    .filter(p -> p.inRegion(limits, null, peakPickPar.dim)).
                    collect(Collectors.toList());
        }
        return peaks;
    }

    public Peak getLastPick() {
        return lastPeakPicked;
    }

}
