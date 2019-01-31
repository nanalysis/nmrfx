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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.optimization.Lmder_f77;
import org.nmrfx.processor.optimization.SineSignal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.exception.TooManyEvaluationsException;

/**
 *
 * @author brucejohnson
 */
public class PeakFitter {

    int[][] splitCount;
    final Dataset theFile;
    boolean rootedPeaks;
    int fitMode;
    Peak[] peaks;
    final int[][] p2;
    boolean fixWeakDoublet = true;
    final int[] pdim;

    public PeakFitter(final Dataset theFile, boolean rootedPeaks, int fitMode) {
        this.theFile = theFile;
        this.rootedPeaks = rootedPeaks;
        this.fitMode = fitMode;
        int dataDim = theFile.getNDim();
        p2 = new int[dataDim][2];
        pdim = new int[dataDim];
    }

    public void setup(String[] argv) {
        int nPeaks = argv.length;
        peaks = new Peak[nPeaks];
        for (int iArg = 0, iPeak = 0; iArg < argv.length;
                iArg++, iPeak++) {
            peaks[iPeak] = PeakList.getAPeak(argv[iArg]);

            if (peaks[iPeak] == null) {
                throw new IllegalArgumentException("Couln't find peak \"" + argv[iArg] + "\"");
            }
        }
    }

    public void setup(List<Peak> peaksList) {
        peaks = new Peak[peaksList.size()];
        for (int i = 0; i < peaks.length; i++) {
            peaks[i] = peaksList.get(i);
        }
    }

    public double doJFit(int i0, int i1, int[] rows, boolean doFit)
            throws IllegalArgumentException {
        int dataDim = theFile.getNDim();
        int[][] p1 = new int[dataDim][2];
        int[] cpt = new int[dataDim];
        double[] width = new double[dataDim];

        ArrayList guessList = new ArrayList();

        //int k=0;
        if (i0 > i1) {
            int hold = i0;
            i0 = i1;
            i1 = hold;
        }
        int nPeaks = peaks.length;

        splitCount = new int[nPeaks][];
        for (int i = 0; i < dataDim; i++) {
            pdim[i] = -1;
        }

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            if (dataDim < peaks[iPeak].peakList.nDim) {
                throw new IllegalArgumentException("Number of peak list dimensions greater than number of dataset dimensions");
            }

            for (int j = 0; j < peaks[iPeak].peakList.nDim; j++) {
                boolean ok = false;

                for (int i = 0; i < dataDim; i++) {
                    if (peaks[iPeak].peakList.getSpectralDim(j).getDimName().equals(
                            theFile.getLabel(i))) {
                        pdim[j] = i;
                        ok = true;

                        break;
                    }
                }

                if (!ok) {
                    throw new IllegalArgumentException("Can't find match for peak dimension \""
                            + peaks[iPeak].peakList.getSpectralDim(j).getDimName() + "\"");
                }
            }
            int nextDim = peaks[iPeak].peakList.nDim;
            for (int i = 0; i < dataDim; i++) {
                boolean gotThisDim = false;
                for (int j = 0; j < pdim.length; j++) {
                    if (pdim[j] == i) {
                        gotThisDim = true;
                        break;
                    }
                }
                if (!gotThisDim) {
                    pdim[nextDim] = i;
                    p2[nextDim][0] = rows[i];
                    p2[nextDim][1] = rows[i];
                }
            }

            rootedPeaks = true;

            List<Peak> linkedPeaks2 = PeakList.getLinks(peaks[iPeak], true);

            if (rootedPeaks && (linkedPeaks2.size() < 1)) {
                continue;
            }

            peaks[iPeak].getPeakRegion(theFile, pdim, p1, cpt, width);
            double c = theFile.ppmToDPoint(0, peaks[iPeak].peakDims[0].getMultiplet().getCenter());

            //   double c = theFile.ppmToDPoint(0, peaks[iPeak].peakDim[0].getChemShiftValue());
            double c1 = theFile.ppmToDPoint(0, peaks[iPeak].peakDims[0].getMultiplet().getCenter() + peaks[iPeak].peakDims[0].getLineWidthValue());
            //System.out.println("lw "+c+" "+c1+" "+(c1-c));
            guessList.add(Math.abs(c1 - c));
            guessList.add(c);

            int pEdge0 = (int) (c - width[0] - 1);
            int pEdge1 = (int) (c + width[0] + 1);
            splitCount[iPeak] = new int[0];

            if (rootedPeaks) {
                Coupling coupling = peaks[iPeak].peakDims[0].getMultiplet().getCoupling();
                if (coupling instanceof CouplingPattern) {
                    CouplingPattern cPat = (CouplingPattern) coupling;
                    int nCouplings = cPat.getNCouplingValues();
                    splitCount[iPeak] = cPat.getNValues();

                    for (int iCoup = 0; iCoup < nCouplings; iCoup++) {
                        double cVal = cPat.getValueAt(iCoup);
                        int nVal = cPat.getNValue(iCoup);
                        splitCount[iPeak][iCoup] = nVal;
                        pEdge0 -= (int) ((nVal * theFile.hzWidthToPoints(0, cVal)) / 2);
                        pEdge1 += (int) ((nVal * theFile.hzWidthToPoints(0, cVal)) / 2);
                        guessList.add(theFile.hzWidthToPoints(0, cVal));
                        guessList.add(theFile.hzWidthToPoints(0, 0.0));
                    }
                } else if (coupling instanceof ComplexCoupling) {
                    ComplexCoupling cCoup = (ComplexCoupling) coupling;
                    Multiplet multiplet = peaks[iPeak].peakDims[0].getMultiplet();
                    int nFreqs = cCoup.getFrequencyCount();
                    splitCount[iPeak] = new int[1];
                    splitCount[iPeak][0] = -nFreqs;
                    guessList.remove(guessList.size() - 1);

                    FreqIntensities freqInt = cCoup.getFreqIntensitiesFromSplittings();
                    for (int iFreq = 0; iFreq < freqInt.freqs.length; iFreq++) {
                        double dw = theFile.hzWidthToPoints(0, freqInt.freqs[iFreq]);
                        int cw0 = (int) ((c + dw) - Math.abs(width[0]) - 1);
                        int cw1 = (int) (c + dw + Math.abs(width[0]) + 1);

                        if (cw0 < pEdge0) {
                            pEdge0 = cw0;
                        }

                        if (cw1 > pEdge1) {
                            pEdge1 = cw1;
                        }

                        guessList.add(c + dw);
                    }
                }
            } else {
                List<Peak> linkedPeaks = PeakList.getLinks(peaks[iPeak], true);

                if (linkedPeaks.size() > 1) {
                    splitCount[iPeak] = new int[1];
                    splitCount[iPeak][0] = -linkedPeaks.size();

                    for (int iLink = 1; iLink < linkedPeaks.size(); iLink++) {
                        Peak lPeak = (Peak) linkedPeaks.get(iLink);
                        c = theFile.ppmToDPoint(0, lPeak.peakDims[0].getChemShiftValue());

                        int cw0 = (int) (c - Math.abs(width[0]) - 1);
                        int cw1 = (int) (c + Math.abs(width[0]) + 1);

                        if (cw0 < pEdge0) {
                            pEdge0 = cw0;
                        }

                        if (cw1 > pEdge1) {
                            pEdge1 = cw1;
                        }

                        guessList.add(c);
                    }
                }
            }

            if (pEdge0 < i0) {
                pEdge0 = i0;
            }

            if (pEdge1 > i1) {
                pEdge1 = i1;
            }

            if (iPeak == 0) {
                p2[0][0] = pEdge0;
                p2[0][1] = pEdge1;
            } else {
                if (pEdge0 < p2[0][0]) {
                    p2[0][0] = pEdge0;
                }

                if (pEdge1 > p2[0][1]) {
                    p2[0][1] = pEdge1;
                }
            }
        }
        if (fitMode == PeakList.FIT_MAX_DEV) {
            if (p2[0][0] > i0) {
                p2[0][0] = i0;
            }
            if (p2[0][1] < i1) {
                p2[0][1] = i1;
            }

            if (p2[0][0] < 0) {
                p2[0][0] = 0;
            }
        }

        if (p2[0][1] >= theFile.getSize(pdim[0])) {
            p2[0][1] = theFile.getSize(pdim[0]) - 1;
        }

        int iGuess = 0;
        double[] guesses = new double[guessList.size()];
        double[] lower = new double[guesses.length];
        double[] upper = new double[guesses.length];
        //            lw  f   j  d         lw  f  j d
        //double[] a =     {2,   15, 10,30, 2, 59, 10, 5000};

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            double lineWidth = ((Double) guessList.get(iGuess));
            guesses[iGuess] = lineWidth;
            lower[iGuess] = guesses[iGuess] * 0.5;
            upper[iGuess] = guesses[iGuess] * 2.0;
            iGuess++;

            if ((splitCount[iPeak].length == 1) && (splitCount[iPeak][0] < 0)) { // generic multiplet

                int nFreq = -splitCount[iPeak][0];
                for (int iFreq = 0; iFreq < nFreq; iFreq++) {
                    guesses[iGuess] = ((Double) guessList.get(iGuess))
                            - p2[0][0];
                    lower[iGuess] = guesses[iGuess] - lineWidth;
                    upper[iGuess] = guesses[iGuess] + lineWidth;
                    iGuess++;
                }
            } else {
                guesses[iGuess] = ((Double) guessList.get(iGuess))
                        - p2[0][0];
                lower[iGuess] = guesses[iGuess] - lineWidth;
                upper[iGuess] = guesses[iGuess] + lineWidth;
                iGuess++;

                int nCouplings = splitCount[iPeak].length;
                int[] couplingIndices = new int[nCouplings];
                for (int iCoupling = 0; iCoupling < nCouplings; iCoupling++) {
                    couplingIndices[iCoupling] = iGuess;
                    guesses[iGuess] = ((Double) guessList.get(iGuess));
                    iGuess++;
                    guesses[iGuess] = ((Double) guessList.get(iGuess));
                    lower[iGuess] = -0.8;
                    upper[iGuess] = 0.8;
                    if ((lower[iGuess] > guesses[iGuess]) || (upper[iGuess] < guesses[iGuess])) {
                        guesses[iGuess] = (lower[iGuess] + upper[iGuess]) / 2.0;
                    }
                    iGuess++;
                }
                // setup bounds on couplings so that they can't change to more than half the distance
                //    to nearest one
                for (int couplingIndex : couplingIndices) {
                    double value = guesses[couplingIndex];
                    lower[couplingIndex] = value * 0.3;
                    upper[couplingIndex] = value * 2.0;
                    for (int couplingIndex2 : couplingIndices) {
                        if (couplingIndex == couplingIndex2) {
                            continue;
                        }
                        double value2 = guesses[couplingIndex2];
                        if (value2 < value) {
                            if (((value2 + value) / 2) > lower[couplingIndex]) {
                                lower[couplingIndex] = (value + value2) / 2;
                            }
                        }
                        if (value2 > value) {
                            if (((value2 + value) / 2) < upper[couplingIndex]) {
                                upper[couplingIndex] = (value + value2) / 2;
                            }
                        }
                    }
                }
            }
            List<Peak> linkedPeaks = PeakList.getLinks(peaks[iPeak], true);
            for (Peak lPeak : linkedPeaks) {
                if (lPeak.getFlag(5)) {
                    fixWeakDoublet = false;
                }
            }
        }
        int i = 0;

        double result = fitNow(guesses, lower, upper);
        return result;
    }

    double fitNow(final double[] guesses, final double[] lower, final double[] upper) throws IllegalArgumentException {
        PeakFit peakFit = new PeakFit();

        int size = p2[0][1] - p2[0][0] + 1;
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid point range in jfit");
        }

        Vec fitVec = new Vec(size);
        try {
            theFile.readVectorFromDatasetFile(p2, pdim, fitVec);
        } catch (IOException ioE) {
            throw new IllegalArgumentException(ioE.getMessage());
        }

        CouplingItem[][] cplItems = new CouplingItem[splitCount.length][];
        for (int iSplit = 0; iSplit < splitCount.length; iSplit++) {
            cplItems[iSplit] = new CouplingItem[splitCount[iSplit].length];
            for (int jSplit = 0; jSplit < splitCount[iSplit].length; jSplit++) {
                cplItems[iSplit][jSplit] = new CouplingItem(0.0, splitCount[iSplit][jSplit]);
            }
        }
        peakFit.setSignals(cplItems);

        int extra = 10;
        int nFitPoints = (size + (2 * extra));
        double[] xv = new double[nFitPoints];
        double[] yv = new double[nFitPoints];

        for (int j = 0; j < (size + (2 * extra)); j++) {
            xv[j] = j - extra;
        }

        for (int j = 0; j < (size + (2 * extra)); j++) {
            yv[j] = 0.0;
        }

        for (int j = 0; j < size; j++) {
            yv[j + extra] = fitVec.getReal(j);
            //  System.out.println(j + " " + xv[j + extra] + " " + yv[j + extra]);
        }
        peakFit.setXY(xv, yv);
        peakFit.setOffsets(guesses, lower, upper);
        double rms;
        double result;
        switch (fitMode) {
            case PeakList.FIT_RMS:
                rms = peakFit.rms(guesses);
                result = rms;
                return result;
            case PeakList.FIT_AMPLITUDES:
                rms = peakFit.rms(guesses);
                break;
            case PeakList.FIT_MAX_DEV:
                int maxDev = peakFit.maxPosDev(guesses, 3);
                double maxDevFreq = theFile.pointToPPM(0, maxDev + p2[0][0]);
                result = maxDevFreq;
                return result;
            default:
                int nDim = guesses.length;
                int nInterpolationPoints = 2 * nDim + 1;
                int nSteps = nInterpolationPoints * 10;
                if (nSteps > 400) {
                    nSteps = 400;
                }   //System.out.println(guesses.length + " " + nInterpolationPoints + " " + nSteps);
                long startTime = System.currentTimeMillis();
                try {
                    peakFit.optimizeBOBYQA(nSteps, nInterpolationPoints);
                } catch (TooManyEvaluationsException tmE) {
                }
                long duration = System.currentTimeMillis() - startTime;
                rms = peakFit.getBestValue();
                //System.out.println(peakFit.getBestValue());
//            for (double pValue : point) {
//               System.out.print(pValue + " ");
//          }
//            System.out.println(duration);
                break;
        }

//        if (fitMode == PeakListCmd.FIT_LW_AMPLITUDES) {
////            fcn.initLWAmpFit(guesses);
////            minimizer.initpt0(guesses, fcn.map);
////        }
        result = rms;

        ArrayList signalGroups = peakFit.getSignals();
        int nPeaks = peaks.length;
        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {

            List<Peak> linkedPeaks = PeakList.getLinks(peaks[iPeak], true);

            if (rootedPeaks && (linkedPeaks.size() < 1)) {
                continue;
            }

            ArrayList signals = (ArrayList) signalGroups.get(iPeak);
            double w = ((SineSignal) signals.get(0)).getWidth();
            double lineWidth = theFile.ptWidthToPPM(0, w);
            //System.out.println(w + " " + lineWidth);
            //System.out.println(theFile.getName());
            //System.out.println(iPeak+" "+w+" "+lineWidth+" "+signals.size());
            peaks[iPeak].peakDims[0].setLineWidthValue((float) lineWidth);
            peaks[iPeak].peakDims[0].setBoundsValue((float) lineWidth * 3);

            int nFreqs = signals.size();

            if (rootedPeaks) {
                int nExtra = nFreqs - (linkedPeaks.size());

                if (nExtra < 0) {
                    throw new IllegalArgumentException("negative nExtra in jfit nFreqs: " + nFreqs + "nPeaks: " + linkedPeaks.size());
                }

                if (nExtra > 0) {
                    PeakList.trimFreqs(signals, nExtra);
                }

                nFreqs -= nExtra;
            }

            double[] amplitudes = new double[nFreqs];
            double[] freqs = new double[nFreqs];
            boolean ok = true;

            for (int i = 0; i < signals.size(); i++) {
                SineSignal signal = (SineSignal) signals.get(i);
                amplitudes[i] = signal.getAmplitude();
                freqs[i] = signal.getFreq();

                if ((freqs[i] < 0.0) || (freqs[i] > size)) {
                    ok = false;
                    System.out.println("invalid frequency " + freqs[i]);

                    break;
                }
            }

            if (!ok) {
                continue;
            }

            if (rootedPeaks) {
                Multiplet multiplet = peaks[iPeak].peakDims[0].getMultiplet();
                if ((splitCount[iPeak].length == 1)
                        && (splitCount[iPeak][0] < 0)) { // generic multiplet

                    for (int iFreq = 0; iFreq < freqs.length; iFreq++) {
                        double delta = freqs[iFreq] - peakFit.getCFreq(iPeak);
                        freqs[iFreq] = theFile.ptWidthToHz(0, delta);
                    }

                    double centerPPM = theFile.pointToPPM(0, peakFit.getCFreq(iPeak) + p2[0][0]);
                    multiplet.set(centerPPM, freqs, amplitudes);

                } else {
                    CouplingItem[] cplItems2 = peakFit.getCouplings(iPeak);
                    double[] couplings = new double[cplItems2.length];
                    double[] sin2Thetas = new double[cplItems2.length];
                    double[] amps = peakFit.getBestAmps();
                    for (int iCoup = 0; iCoup < couplings.length; iCoup++) {
                        couplings[iCoup] = theFile.ptWidthToHz(0, cplItems2[iCoup].getCoupling());
                        sin2Thetas[iCoup] = cplItems2[iCoup].getSin2Theta();
                        //System.out.println(cplItems2[iCoup].getCoupling() + " " + couplings[iCoup] + " " + amps[iCoup] + " " + sin2Thetas[iCoup]);
                    }

                    double centerPPM = theFile.pointToPPM(0, peakFit.getCFreq(iPeak) + p2[0][0]);
                    multiplet.set(centerPPM, couplings, amps[0], sin2Thetas);
                }
                peaks[iPeak].peakDims[0].getMultiplet().setMultipletComponentValues();
                //System.out.println(peakFit.getCFreq(iPeak) + " " + p2[0][0]);
                for (int jPeak = 0; jPeak < linkedPeaks.size(); jPeak++) {
                    Peak lPeak = (Peak) linkedPeaks.get(jPeak);
                    lPeak.peakDims[0].setLineWidthValue((float) lineWidth);
                    lPeak.peakDims[0].setBoundsValue((float) lineWidth * 3);
                    lPeak.setFlag(4, true);
                }

                //peaks[iPeak].peakDim[0].setMultipletComponentValues();
            } else {
                for (int iFreq = 0; iFreq < nFreqs; iFreq++) {
                    Peak lPeak = (Peak) linkedPeaks.get(iFreq);
                    SineSignal signal = (SineSignal) signals.get(iFreq);
                    lPeak.peakDims[0].setChemShiftValueNoCheck((float) theFile.pointToPPM(
                            0, signal.getFreq() + p2[0][0]));
                    lPeak.peakDims[0].setLineWidthValue((float) lineWidth);
                    lPeak.peakDims[0].setBoundsValue((float) lineWidth * 3);
                    lPeak.setIntensity((float) signal.getAmplitude());
                    lPeak.setVolume1((float) (lPeak.getIntensity() * lineWidth * (Math.PI / 2.0) / 1.05));
                    lPeak.setFlag(4, true);
                }
            }
        }
        return result;
    }

    public double doFit(int i0, int i1, int[] rows, boolean doFit, boolean linearFit)
            throws IllegalArgumentException, IOException, PeakFitException {
        int dataDim = theFile.getNDim();
        int[][] p1 = new int[dataDim][2];
        int[][] pt = new int[dataDim][2];
        int[] cpt = new int[dataDim];
        double[] width = new double[dataDim];
        double result;

        //double guesses[] = new double[3*nPeaks];
        int nPeaks = peaks.length;
        double[] guesses = new double[(2 * nPeaks) + 1];

        if (i0 > i1) {
            int hold = i0;
            i0 = i1;
            i1 = hold;
        }

        //int k=0;
        int k = 1;
        int[] fitDim = new int[dataDim];
        double lwSum = 0.0;

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            if (dataDim < peaks[iPeak].peakList.nDim) {
                throw new IllegalArgumentException(
                        "Number of peak list dimensions greater than number of dataset dimensions");
            }

            for (int j = 0; j < peaks[iPeak].peakList.nDim; j++) {
                boolean ok = false;

                for (int i = 0; i < dataDim; i++) {
                    if (peaks[iPeak].peakList.getSpectralDim(j).getDimName().equals(
                            theFile.getLabel(i))) {
                        fitDim[j] = i;
                        ok = true;

                        break;
                    }
                }

                if (!ok) {
                    throw new IllegalArgumentException(
                            "Can't find match for peak dimension \""
                            + peaks[iPeak].peakList.getSpectralDim(j).getDimName() + "\"");
                }
            }
            int nextDim = peaks[iPeak].peakList.nDim;
            for (int i = 0; i < dataDim; i++) {
                boolean gotThisDim = false;
                for (int j = 0; j < fitDim.length; j++) {
                    if (fitDim[j] == i) {
                        gotThisDim = true;
                        break;
                    }
                }
                if (!gotThisDim) {
                    fitDim[nextDim] = i;
                    pt[nextDim][0] = rows[i];
                    pt[nextDim][1] = rows[i];
                }
            }

            peaks[iPeak].getPeakRegion(theFile, fitDim, p1, cpt, width);
            int cw0 = (int) (cpt[0] - Math.abs(width[0]) - 1);
            int cw1 = (int) (cpt[0] + Math.abs(width[0]) + 1);

            if (cw0 < 0) {
                cw0 = 0;
            }

            if (cw1 >= theFile.getSize(fitDim[0])) {
                cw1 = theFile.getSize(fitDim[0]) - 1;
            }

            if (iPeak == 0) {
                pt[0][0] = cw0;
                pt[0][1] = cw1;
            } else {
                if (cw0 < pt[0][0]) {
                    pt[0][0] = cw0;
                }

                if (cw1 > pt[0][1]) {
                    pt[0][1] = cw1;
                }
            }

            double c = theFile.ppmToDPoint(0, peaks[iPeak].peakDims[0].getChemShiftValue());
            double c1 = theFile.ppmToDPoint(0,
                    peaks[iPeak].peakDims[0].getChemShiftValue()
                    + peaks[iPeak].peakDims[0].getLineWidthValue());
            guesses[k++] = peaks[iPeak].getIntensity();
            guesses[k++] = c;
            lwSum += Math.abs(c1 - c);
        }
//System.out.println(p2[0][0]+" "+i0+" "+p2[0][1]+" "+i1);
        if (pt[0][0] < i0) {
            pt[0][0] = i0;
        }

        if (pt[0][1] > i1) {
            pt[0][1] = i1;
        }

        guesses[0] = lwSum / nPeaks;

        //k = 1;
        k = 2;

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            guesses[k] = guesses[k] - pt[0][0];

            //k += 3;
            k += 2;
        }

        int size = pt[0][1] - pt[0][0] + 1;
        Vec fitVec = new Vec(size);
        theFile.readVectorFromDatasetFile(pt, fitDim, fitVec);

        //LmdifTest_f77  lmdifTest = new LmdifTest_f77();
        Lmder_f77 lmdifTest = new Lmder_f77();

        //int extra = size/2;
        int extra = 0;
        lmdifTest.setLengths(size + (2 * extra));
        double[] values = lmdifTest.getArray(0);

        for (int j = 0; j < (size + (2 * extra)); j++) {
            values[j] = j - extra;
        }

        values = lmdifTest.getArray(1);

        for (int j = 0; j < (size + (2 * extra)); j++) {
            values[j] = 0.0;
        }

        for (int j = 0; j < size; j++) {
            values[j + extra] = fitVec.getReal(j);
        }

        //lmdifTest.setFunc(8);
        lmdifTest.setFunc(11);
        lmdifTest.setN(guesses.length);

        //lmdifTest.initpt();
        lmdifTest.initpt0offset(guesses);

        if (!doFit) {
            double rms = lmdifTest.rms();
            result = rms;
        } else {
            int[] map = new int[nPeaks + 1];
            map[0] = 0;

            for (int j = 0; j < nPeaks; j++) {
                map[j + 1] = (2 * j) + 2;
            }

            lmdifTest.setMap(map);

            if (linearFit) {
                lmdifTest.doLinearNN();
            } else {
                lmdifTest.doMin();
            }

            double rms = lmdifTest.rms();
            result = rms;

            double[] pars = lmdifTest.getPars();

            //k=1;
            k = 2;
//            System.out.println("lin fit peaks " + nPeaks);
            for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
                k++;

                double newC = pars[k++];

                if ((newC < 0.0) || (newC > size)) {
                    throw new PeakFitException("fit failed for peak "
                            + peaks[iPeak].getName() + " " + iPeak + " "
                            + newC);
                }
            }

            k = 2;

            for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
                peaks[iPeak].setIntensity((float) pars[k++]);

                double newC = pars[k++];
                // double w = pars[k++];
                double w = Math.abs(pars[1]);

                if ((newC < 0.0) || (newC > size)) {
                    continue;
                }

                double c = newC + pt[0][0];
                double c1 = w + c;
                peaks[iPeak].peakDims[0].setChemShiftValueNoCheck((float) theFile.pointToPPM(
                        0, c));
//                System.out.printf("%d %10.6f\n", iPeak, peaks[iPeak].peakDim[0].getChemShift());
                peaks[iPeak].peakDims[0].setLineWidthValue((float) Math.abs(theFile.pointToPPM(
                        0, c1) - peaks[iPeak].peakDims[0].getChemShiftValue()));
                float lineWidth = peaks[iPeak].peakDims[0].getLineWidthValue();
                peaks[iPeak].peakDims[0].setBoundsValue((float) lineWidth * 3);
                peaks[iPeak].setVolume1((float) (peaks[iPeak].getIntensity() * peaks[iPeak].peakDims[0].getLineWidthValue() * Math.PI / 2 / 1.05));

                //peaks[iPeak].flag[4] = true;
            }
        }
        return result;
    }
}
