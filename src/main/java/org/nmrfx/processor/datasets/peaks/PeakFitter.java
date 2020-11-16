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

import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.optimization.Lmder_f77;
import org.nmrfx.processor.optimization.SineSignal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Comparator.comparing;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.PointValuePair;
import org.nmrfx.processor.optimization.Fitter;

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
    boolean fitAmps = true;
    final int[] pdim;
    double BIC;

    public PeakFitter(final Dataset theFile, boolean rootedPeaks, int fitMode) {
        this.theFile = theFile;
        this.rootedPeaks = rootedPeaks;
        this.fitMode = fitMode;
        int dataDim = theFile.getNDim();
        p2 = new int[dataDim][2];
        pdim = new int[dataDim];
    }

    public double getBIC() {
        return BIC;
    }

    public void setup(String[] argv) {
        int nPeaks = argv.length;
        peaks = new Peak[nPeaks];
        for (int iArg = 0, iPeak = 0; iArg < argv.length;
                iArg++, iPeak++) {
            peaks[iPeak] = PeakListBase.getAPeak(argv[iArg]);

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

    void getDims(Peak peak, int[] rows) {
        int dataDim = theFile.getNDim();
        PeakDim peakDim = peak.getPeakDim(0);
        //System.out.println("jfit " + peaks[iPeak].getName());
        if (dataDim < peak.peakList.nDim) {
            throw new IllegalArgumentException("Number of peak list dimensions greater than number of dataset dimensions");
        }

        for (int j = 0; j < peak.peakList.nDim; j++) {
            boolean ok = false;

            for (int i = 0; i < dataDim; i++) {
                if (peak.peakList.getSpectralDim(j).getDimName().equals(
                        theFile.getLabel(i))) {
                    pdim[j] = i;
                    ok = true;

                    break;
                }
            }

            if (!ok) {
                throw new IllegalArgumentException("Can't find match for peak dimension \""
                        + peak.peakList.getSpectralDim(j).getDimName() + "\"");
            }
        }
        int nextDim = peak.peakList.nDim;
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

    }

    void updateEdge(int iPeak, int pEdge0, int pEdge1, int i0, int i1) {
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

    List<Double> getGuesses(int i0, int i1) {
        int dataDim = theFile.getNDim();
        int[][] p1 = new int[dataDim][2];
        int[] cpt = new int[dataDim];
        double[] width = new double[dataDim];
        int nPeaks = peaks.length;

        splitCount = new int[nPeaks][];
        List<Double> guessList = new ArrayList<>();
        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            PeakDim peakDim = peaks[iPeak].getPeakDim(0);
            Multiplet multiplet = peakDim.getMultiplet();
            multiplet.getMultipletRegion(theFile, pdim, p1, cpt, width);
            double c = theFile.ppmToDPoint(0, multiplet.getCenter());

            double c1 = theFile.ppmToDPoint(0, multiplet.getCenter() + peakDim.getLineWidthValue());
            //System.out.println("lw "+c+" "+c1+" "+(c1-c));

            int pEdge0 = (int) (c - width[0] - 1);
            int pEdge1 = (int) (c + width[0] + 1);
            splitCount[iPeak] = new int[0];

            Coupling coupling = peaks[iPeak].peakDims[0].getMultiplet().getCoupling();
            if (coupling instanceof CouplingPattern) {  // multiplet with couplings
                guessList.add(Math.abs(c1 - c));  // linewidth in points
                if (fitAmps) {
                    guessList.add(multiplet.getIntensity());
                } // peak amplitude
                guessList.add(c);   // peak center in points
                CouplingPattern cPat = (CouplingPattern) coupling;
                int nCouplings = cPat.getNCouplingValues();
                splitCount[iPeak] = cPat.getNValues();

                for (int iCoup = 0; iCoup < nCouplings; iCoup++) {
                    double cVal = cPat.getValueAt(iCoup);
                    double slope = cPat.getSin2Theta(iCoup);
                    int nVal = cPat.getNValue(iCoup);
                    splitCount[iPeak][iCoup] = nVal;
                    pEdge0 -= (int) ((nVal * theFile.hzWidthToPoints(0, cVal)) / 2);
                    pEdge1 += (int) ((nVal * theFile.hzWidthToPoints(0, cVal)) / 2);
                    guessList.add(theFile.hzWidthToPoints(0, cVal));  // coupling in hz
                    guessList.add(slope);   // slope 
                }
            } else if (coupling instanceof ComplexCoupling) {  // generic multiplet (list of freqs)
                guessList.add(Math.abs(c1 - c));  // linewidth in points
                ComplexCoupling cCoup = (ComplexCoupling) coupling;
                int nFreqs = cCoup.getFrequencyCount();
                splitCount[iPeak] = new int[1];
                splitCount[iPeak][0] = -nFreqs;

                List<RelMultipletComponent> multipletComps = cCoup.getRelComponentList();

                double maxInt = Double.NEGATIVE_INFINITY;
                for (MultipletComponent comp : multipletComps) {
                    maxInt = Math.max(maxInt, comp.getIntensity());
                }
                for (MultipletComponent comp : multipletComps) {
                    double dw = theFile.hzWidthToPoints(0, comp.getOffset());
                    int cw0 = (int) ((c + dw) - Math.abs(width[0]) - 1);
                    int cw1 = (int) (c + dw + Math.abs(width[0]) + 1);

                    if (cw0 < pEdge0) {
                        pEdge0 = cw0;
                    }

                    if (cw1 > pEdge1) {
                        pEdge1 = cw1;
                    }
                    if (fitAmps) {
                        double intensity = comp.getIntensity();
                        if (intensity < maxInt / 10.0) {
                            intensity = maxInt / 10.0;  // fixme bad for doing rms
                        }
                        guessList.add(intensity);
                    }     // amplitude
                    guessList.add(c + dw);  // multiplet center plus individual peak offset
                }
            } else {  // singlet
                guessList.add(Math.abs(c1 - c));  // linewidth in points
                if (fitAmps) {
                    guessList.add(multiplet.getIntensity());
                }
                guessList.add(c);   // peak center in points
            }
            updateEdge(iPeak, pEdge0, pEdge1, i0, i1);
        }
        return guessList;
    }

    public double doJFit(int i0, int i1, int[] rows, boolean doFit)
            throws IllegalArgumentException {
        int nPeaks = peaks.length;
        int dataDim = theFile.getNDim();
        rootedPeaks = true;

        //int k=0;
        if (i0 > i1) {
            int hold = i0;
            i0 = i1;
            i1 = hold;
        }
        for (int i = 0; i < dataDim; i++) {
            pdim[i] = -1;
        }
        //System.out.println(" jfit " + peaks.length);
        getDims(peaks[0], rows);
        List<Double> guessList = getGuesses(i0, i1);
//        if (fitMode == PeakList.FIT_MAX_DEV) {
        if (p2[0][0] > i0) {
            p2[0][0] = i0;
        }
        if (p2[0][1] < i1) {
            p2[0][1] = i1;
        }

        if (p2[0][0] < 0) {
            p2[0][0] = 0;
        }
//        }

        if (p2[0][1] >= theFile.getSize(pdim[0])) {
            p2[0][1] = theFile.getSize(pdim[0]) - 1;
        }
//        for (double guess : guessList) {
//            System.out.println(guess);
//        }
        int size = p2[0][1] - p2[0][0] + 1;
        int iGuess = 0;
        double[] guesses = new double[guessList.size()];
        double[] lower = new double[guesses.length];
        double[] upper = new double[guesses.length];
        //            lw  f   j  d         lw  f  j d
        //double[] a =     {2,   15, 10,30, 2, 59, 10, 5000};

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            double lineWidth = Math.abs(((Double) guessList.get(iGuess)));
            guesses[iGuess] = lineWidth;  // guess for linewidth
            lower[iGuess] = guesses[iGuess] * 0.5;  // constraints on linewidth
            upper[iGuess] = guesses[iGuess] * 2.0;
            if (lower[iGuess] < 0.5) {
                lower[iGuess] = 0.5;
                if (guesses[iGuess] < lower[iGuess]) {
                    guesses[iGuess] = lower[iGuess] + 1;
                    if (guesses[iGuess] >= upper[iGuess]) {
                        upper[iGuess] = guesses[iGuess] + 1.0;
                    }
                }
            }
            iGuess++;

            if ((splitCount[iPeak].length == 1) && (splitCount[iPeak][0] < 0)) { // generic multiplet
                int nFreq = -splitCount[iPeak][0];
                int jGuess = iGuess;
                for (int iFreq = 0; iFreq < nFreq; iFreq++) {
                    if (fitAmps) {
                        guesses[iGuess] = guessList.get(iGuess);
                        if (guesses[iGuess] < 0.0) {
                            upper[iGuess] = 0.0;
                            lower[iGuess] = 5.0 * guesses[iGuess];
                        } else {
                            lower[iGuess] = 0.0;
                            upper[iGuess] = 5.0 * guesses[iGuess];
                        }
                        iGuess++;
                    }
                    guesses[iGuess] = (guessList.get(iGuess)) - p2[0][0];
                    iGuess++;
                }
                // set lower and upper bounds so component can't cross over
                // previous or following component
                int guessOffset = fitAmps ? 2 : 1;
                for (int iFreq = 0; iFreq < nFreq; iFreq++) {
                    if (fitAmps) {
                        jGuess++;
                    }
                    if (iFreq == 0) {
                        lower[jGuess] = guesses[jGuess] - lineWidth / 2.0;
                    } else {
                        lower[jGuess] = (guesses[jGuess] + guesses[jGuess - guessOffset]) / 2;
                    }
                    if (lower[jGuess] < 1) {
                        lower[jGuess] = 1;

                    }
                    if (iFreq == (nFreq - 1)) {
                        upper[jGuess] = guesses[jGuess] + lineWidth / 2.0;
                    } else {
                        upper[jGuess] = (guesses[jGuess] + guesses[jGuess + guessOffset]) / 2;
                    }
                    if (upper[jGuess] > size - 2) {
                        upper[jGuess] = size - 2;
                    }
                    if ((guesses[jGuess] <= lower[jGuess]) || (guesses[jGuess] >= upper[jGuess])) {
                        guesses[jGuess] = (lower[jGuess] + upper[jGuess]) / 2;
                    }
                    jGuess++;
                }
            } else {
                if (fitAmps) {
                    guesses[iGuess] = guessList.get(iGuess);
                    if (guesses[iGuess] < 0.0) {
                        upper[iGuess] = 0.0;
                        lower[iGuess] = 5.0 * guesses[iGuess];
                    } else {
                        lower[iGuess] = 0.0;
                        upper[iGuess] = 5.0 * guesses[iGuess];
                    }
                    iGuess++;
                }
                guesses[iGuess] = ((Double) guessList.get(iGuess))
                        - p2[0][0];
                lower[iGuess] = guesses[iGuess] - lineWidth;
                upper[iGuess] = guesses[iGuess] + lineWidth;
                iGuess++;

                int nCouplings = splitCount[iPeak].length;
                //System.out.println("pattern " + nCouplings);
                int[] couplingIndices = new int[nCouplings];
                for (int iCoupling = 0; iCoupling < nCouplings; iCoupling++) {
                    couplingIndices[iCoupling] = iGuess;
                    guesses[iGuess] = ((Double) guessList.get(iGuess));  // coupling
                    iGuess++;
                    guesses[iGuess] = ((Double) guessList.get(iGuess)); // slope
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
            List<Peak> linkedPeaks = PeakListBase.getLinks(peaks[iPeak], true);
            for (Peak lPeak : linkedPeaks) {
                if (lPeak.getFlag(5)) {
                    fixWeakDoublet = false;
                }
            }
        }

//        System.out.println(peaks[0].getName());
//        for (int i = 0; i < guesses.length; i++) {
//            System.out.printf("%10.4f %10.4f %10.4f\n", guesses[i], lower[i], upper[i]);
//        }
//
        double result = fitNow(guesses, lower, upper);
        return result;
    }

    void updateBIC(double rms, int size, int nDim) {
        double meanSq = rms * rms;
        BIC = size * Math.log(meanSq) + nDim * Math.log(size);
    }

    double fitNow(final double[] guesses, final double[] lower, final double[] upper) throws IllegalArgumentException {
        PeakFit peakFit = new PeakFit(fitAmps);

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
        int nDim = guesses.length;
        BIC = 0.0;
        switch (fitMode) {
            case PeakList.FIT_RMS:
                rms = peakFit.rms(guesses);
                updateBIC(rms, size, nDim);
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
                int nInterpolationPoints = 2 * nDim + 1;
                int nSteps = nInterpolationPoints * 10;
                if (nSteps > 400) {
                    nSteps = 400;
                }   //System.out.println(guesses.length + " " + nInterpolationPoints + " " + nSteps);
                long startTime = System.currentTimeMillis();
                try {
                    peakFit.optimizeCMAES(nSteps);
                } catch (TooManyEvaluationsException tmE) {
                } catch (Exception ex) {
                    Logger.getLogger(PeakFitter.class.getName()).log(Level.SEVERE, null, ex);
                }
                long duration = System.currentTimeMillis() - startTime;
                rms = peakFit.getBestValue();
                updateBIC(rms, size, nDim);

//                System.out.print(peakFit.getBestValue() + " " + BIC + " ");
//                double[] point = peakFit.getBestPoint();
//                for (double pValue : point) {
//                    System.out.printf("%9.3f ", pValue);
//                }
//                System.out.println(duration);
                break;
        }

        result = rms;

        List<List<SineSignal>> signalGroups = peakFit.getSignals();
        int nPeaks = peaks.length;
        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            List<SineSignal> signals = signalGroups.get(iPeak);

            Multiplet multiplet = peaks[iPeak].peakDims[0].getMultiplet();
            if ((splitCount[iPeak].length == 1)
                    && (splitCount[iPeak][0] < 0)) { // generic multiplet
                int nFreqs = signals.size();
                double[] volumes = new double[nFreqs];
                double[] deltaPPMs = new double[nFreqs];
                double[] lineWidthPPMs = new double[nFreqs];
                double[] amplitudes = new double[nFreqs];
                double sumFreq = 0.0;
                for (SineSignal signal : signals) {
                    sumFreq += signal.getFreq();
                }
                double centerFreq = sumFreq / nFreqs;
                int iFreq = 0;
                for (SineSignal signal : signals) {
                    double sigWidth = signal.getWidth();
                    double sigWidthPPM = theFile.ptWidthToPPM(0, sigWidth);
                    double amplitude = signal.getAmplitude();
                    double delta = signal.getFreq() - centerFreq;
                    deltaPPMs[iFreq] = -theFile.ptWidthToPPM(0, delta);
                    volumes[iFreq] = amplitude * sigWidthPPM * (Math.PI / 2.0) / 1.05;
                    amplitudes[iFreq] = amplitude;
                    lineWidthPPMs[iFreq] = sigWidthPPM;
                    iFreq++;
                }

                double centerPPM = theFile.pointToPPM(0, centerFreq + p2[0][0]);
                multiplet.set(centerPPM, deltaPPMs, amplitudes, volumes, lineWidthPPMs[0]);
                peaks[iPeak].peakDims[0].setLineWidthValue((float) lineWidthPPMs[0]);
                peaks[iPeak].peakDims[0].setBoundsValue((float) lineWidthPPMs[0] * 3);

            } else {
                SineSignal signal = signals.get(0);
                double sigWidth = signal.getWidth();
                double sigWidthPPM = theFile.ptWidthToPPM(0, sigWidth);
                double centerPPM = theFile.pointToPPM(0, signal.getFreq() + p2[0][0]);

                CouplingItem[] cplItems2 = peakFit.getCouplings(iPeak);
                double[] couplings = new double[cplItems2.length];
                double[] sin2Thetas = new double[cplItems2.length];
                int nComp = 1;
                for (int iCoup = 0; iCoup < couplings.length; iCoup++) {
                    couplings[iCoup] = theFile.ptWidthToHz(0, cplItems2[iCoup].getCoupling());
                    sin2Thetas[iCoup] = cplItems2[iCoup].getSin2Theta();
//                        System.out.println(cplItems2[iCoup].getCoupling() + " " + couplings[iCoup] + " " + sin2Thetas[iCoup]);
                    nComp *= cplItems2[iCoup].getNSplits();
                }
                double amp = signal.getAmplitude();
                double volume = nComp * amp * sigWidthPPM * (Math.PI / 2.0) / 1.05;
                multiplet.getOrigin().setVolume1((float) volume);
                multiplet.set(centerPPM, couplings, amp, sin2Thetas);
                peaks[iPeak].peakDims[0].setLineWidthValue((float) sigWidthPPM);
                peaks[iPeak].peakDims[0].setBoundsValue((float) sigWidthPPM * 3);
            }
            peaks[iPeak].setFlag(4, true);

        }
        return result;
    }

    public double simpleFit(int i0, int i1, int[] rows, boolean doFit)
            throws IllegalArgumentException, Exception {
        int nPeaks = peaks.length;
        int dataDim = theFile.getNDim();
        Arrays.sort(peaks, comparing((p) -> -p.getPeakDim(0).getChemShiftValue()));

        //int k=0;
        if (i0 > i1) {
            int hold = i0;
            i0 = i1;
            i1 = hold;
        }
        for (int i = 0; i < dataDim; i++) {
            pdim[i] = -1;
        }
        //System.out.println(" jfit " + peaks.length);
        getDims(peaks[0], rows);
//        if (fitMode == PeakList.FIT_MAX_DEV) {
        p2[0][0] = i0;
        p2[0][1] = i1;
        if (p2[0][0] > i0) {
            p2[0][0] = i0;
        }
        if (p2[0][1] < i1) {
            p2[0][1] = i1;
        }

        if (p2[0][0] < 0) {
            p2[0][0] = 0;
        }
//        }

        if (p2[0][1] >= theFile.getSize(pdim[0])) {
            p2[0][1] = theFile.getSize(pdim[0]) - 1;
        }
//        for (double guess : guessList) {
//            System.out.println(guess);
//        }
        int extra = 5;
        int size = p2[0][1] - p2[0][0] + 1;
        int nFitPoints = (size + (2 * extra));
        double[][] xv = new double[1][nFitPoints];
        double[] yv = new double[nFitPoints];
        double[] ev = new double[nFitPoints];
        double[] guesses = new double[nPeaks * 3];
        double[] lower = new double[guesses.length];
        double[] upper = new double[guesses.length];
        Vec fitVec = new Vec(size);
        try {
            theFile.readVectorFromDatasetFile(p2, pdim, fitVec);
        } catch (IOException ioE) {
            throw new IllegalArgumentException(ioE.getMessage());
        }
        for (int j = 0; j < (size + (2 * extra)); j++) {
            xv[0][j] = j - extra;
        }

        for (int j = 0; j < (size + (2 * extra)); j++) {
            yv[j] = 0.0;
            ev[j] = 1.0;
        }

        for (int j = 0; j < size; j++) {
            yv[j + extra] = fitVec.getReal(j);
            //  System.out.println(j + " " + xv[j + extra] + " " + yv[j + extra]);
        }
        //            lw  f   j  d         lw  f  j d
        //double[] a =     {2,   15, 10,30, 2, 59, 10, 5000};

        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            PeakDim peakDim = peaks[iPeak].getPeakDim(0);
            double intensity = peaks[iPeak].getIntensity();
            double centerPPM = peakDim.getChemShiftValue();
            double lineWidthPPM = peakDim.getLineWidthValue();
            double c = theFile.ppmToDPoint(0, centerPPM);
            double c1 = theFile.ppmToDPoint(0, centerPPM + lineWidthPPM);
            double lineWithPts = Math.abs(c1 - c);

            guesses[iPeak * 3] = intensity;
            guesses[iPeak * 3 + 1] = c - p2[0][0];
            guesses[iPeak * 3 + 2] = lineWithPts;

            int cPos = (int) Math.round(c - p2[0][0]);
            if (intensity > 0.0) {
                lower[iPeak * 3] = 0.0;
                upper[iPeak * 3] = yv[cPos + extra] * 1.2;
            } else {
                lower[iPeak * 3] = yv[cPos + extra] * 1.2;
                upper[iPeak * 3] = 0.0;
            }
            lower[iPeak * 3 + 2] = 2;
            upper[iPeak * 3 + 2] = lineWithPts * 3;
        }
        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            double lineWidthPts = guesses[iPeak * 3 + 2];
            if (iPeak == 0) {
                lower[iPeak * 3 + 1] = guesses[iPeak * 3 + 1] - lineWidthPts;
            } else {
                lower[iPeak * 3 + 1] = (guesses[iPeak * 3 + 1] + guesses[(iPeak - 1) * 3 + 1]) / 2;
            }
            if (iPeak == nPeaks - 1) {
                upper[iPeak * 3 + 1] = guesses[iPeak * 3 + 1] + lineWidthPts;
            } else {
                upper[iPeak * 3 + 1] = (guesses[iPeak * 3 + 1] + guesses[(iPeak + 1) * 3 + 1]) / 2;
            }
        }
//        System.out.println(peaks[0].getName());
//        for (int i = 0; i < guesses.length; i++) {
//            System.out.printf("%10.4f %10.4f %10.4f\n", guesses[i], lower[i], upper[i]);
//        }

        PeakFit peakFit = new PeakFit(true);
        Fitter fitter = Fitter.getArrayFitter(peakFit::value);
        fitter.setXYE(xv, yv, ev);
        int nPars = guesses.length;
        double[] bestPars;
        double rms;
        if (fitMode == PeakList.FIT_RMS) {
            rms = fitter.rms(guesses);
            updateBIC(rms, size, nPars);
            return rms;
        } else if (fitMode == PeakList.FIT_MAX_DEV) {
            double[] yCalc = peakFit.sim(guesses, xv);
            int maxPos = fitter.maxDevLoc(yCalc, 3);
            double centerPt = maxPos + p2[0][0];
            double centerPPM = theFile.pointToPPM(0, centerPt);
            return centerPPM;
        }

        try {
            PointValuePair result = fitter.fit(guesses, lower, upper, 10.0);
            bestPars = result.getPoint();
//            for (int i = 0; i < bestPars.length; i++) {
//                System.out.println(i + " " + bestPars[i]);
//            }
            rms = result.getValue();
            updateBIC(rms, size, nPars);
//            System.out.println("rms " + rms + " " + BIC);
        } catch (Exception ex) {

            System.out.println(ex.getMessage());
            ex.printStackTrace();
            return 0.0;
        }
        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            Peak peak = peaks[iPeak];
            PeakDim peakDim = peak.getPeakDim(0);

            double intensity = bestPars[iPeak * 3];
            double centerPt = bestPars[iPeak * 3 + 1] + p2[0][0];
            double lineWithPts = bestPars[iPeak * 3 + 2];
            double lineWidthPPM = theFile.ptWidthToPPM(0, lineWithPts);
            double centerPPM = theFile.pointToPPM(0, centerPt);
            double volume = intensity * lineWidthPPM * Math.PI / 2 / 1.05;

            peak.setIntensity((float) intensity);
            peak.setVolume1((float) volume);
            peakDim.setLineWidthValue((float) lineWidthPPM);
            peakDim.setChemShiftValue((float) centerPPM);

        }
        return rms;
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

        if (i0 > i1) {
            int hold = i0;
            i0 = i1;
            i1 = hold;
        }
        //int k=0;
        int[] fitDim = new int[dataDim];
        List<AbsMultipletComponent> absComps = new ArrayList<>();
        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            Peak peak = peaks[iPeak];
            for (int j = 0; j < peaks[iPeak].peakList.nDim; j++) {
                if (dataDim < peaks[iPeak].peakList.nDim) {
                    throw new IllegalArgumentException(
                            "Number of peak list dimensions greater than number of dataset dimensions");
                }
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
            absComps.addAll(peak.getPeakDim(0).getMultiplet().getAbsComponentList());
        }
        double[] guesses = new double[(2 * absComps.size()) + 1];
        int nextDim = peaks[0].peakList.nDim;
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
        double sf = peaks[0].getPeakDim(0).getSpectralDimObj().getSf();
        boolean firstComp = true;
        int k = 1;
        int nComps = absComps.size();
        double lwSum = 0.0;
//        System.out.println(" do fit " + nPeaks + " " + nComps + " " + doFit + " " + linearFit + " " + peaks[0].getName() + " " + absComps.size());
        for (AbsMultipletComponent mulComp : absComps) {
            mulComp.getRegion(theFile, fitDim, p1, cpt, width);
            int cw0 = (int) (cpt[0] - Math.abs(width[0]) - 1);
            int cw1 = (int) (cpt[0] + Math.abs(width[0]) + 1);

            if (cw0 < 0) {
                cw0 = 0;
            }

            if (cw1 >= theFile.getSize(fitDim[0])) {
                cw1 = theFile.getSize(fitDim[0]) - 1;
            }

            if (firstComp) {
                pt[0][0] = cw0;
                pt[0][1] = cw1;
                firstComp = false;
            } else {
                if (cw0 < pt[0][0]) {
                    pt[0][0] = cw0;
                }

                if (cw1 > pt[0][1]) {
                    pt[0][1] = cw1;
                }
            }

            double c = theFile.ppmToDPoint(0, mulComp.getOffset());
            double c1 = theFile.ppmToDPoint(0, mulComp.getOffset()
                    + mulComp.getLineWidth());
            guesses[k++] = mulComp.getIntensity();
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

        guesses[0] = lwSum / absComps.size();

        //k = 1;
        k = 2;
        for (int iPeak = 0; iPeak < nComps; iPeak++) {
            guesses[k] = guesses[k] - pt[0][0];

            //k += 3;
            k += 2;
        }
        //for (int i = 0; i < guesses.length; i++) {
        //System.out.printf("%10.4f ", guesses[i]);
        //}
        //System.out.println("");

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
            int[] map = new int[nComps + 1];
            map[0] = 0;

            for (int j = 0; j < nComps; j++) {
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
            //for (int i = 1; i < pars.length; i++) {
            //System.out.printf("%10.4f ", pars[i]);

            //}
            //System.out.println(" rms " + rms);
            //k=1;
            k = 2;
//            System.out.println("lin fit peaks " + nPeaks);
            for (int iPeak = 0; iPeak < nComps; iPeak++) {
                k++;

                double newC = pars[k++];

                if ((newC < 0.0) || (newC > size)) {
                    throw new PeakFitException("fit failed for comp "
                            + absComps.get(iPeak).getMultiplet().getPeakDim().getPeak().getName() + " " + iPeak + " "
                            + newC);
                }
            }

            k = 2;
            double w = Math.abs(pars[1]);

            for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
                List<AbsMultipletComponent> comps = peaks[iPeak].getPeakDim(0).getMultiplet().getAbsComponentList();
                int nComp = comps.size();
                for (AbsMultipletComponent comp : comps) {
                    double intensity = pars[k++];
                    double newC = pars[k++];
                    double c = newC + pt[0][0];
                    double c1 = w + c;
                    double newPPM = theFile.pointToPPM(0, c);
                    double lineWidth = Math.abs(theFile.pointToPPM(0, c) - theFile.pointToPPM(0, c1));
                    double volume = intensity * lineWidth * Math.PI / 2 / 1.05;
                    //System.out.println(intensity + " " + volume);
                    comp.setOffset(newPPM);
                    comp.setLineWidth(lineWidth);
                    comp.setIntensity(intensity);
                    comp.setVolume(volume);
                }
                peaks[iPeak].getPeakDim(0).getMultiplet().updateCoupling(comps);
            }
        }
        return result;

    }
}
