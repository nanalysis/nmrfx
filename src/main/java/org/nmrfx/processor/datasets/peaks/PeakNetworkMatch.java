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
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.optimization.BipartiteMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;

/**
 *
 * @author brucejohnson
 */
public class PeakNetworkMatch {

    final PeakList iList;
    final PeakList jList;
    double[] optOffset = null;

    public PeakNetworkMatch(final PeakList iList, final PeakList jList) {
        this.iList = iList;
        this.jList = jList;
    }

    public PeakNetworkMatch(final String iListName, final String jListName) {
        this.iList = PeakList.get(iListName);
        this.jList = PeakList.get(jListName);
    }

    public double[] getOptOffset() {
        return optOffset;
    }

    public void bpMatchPeaks() {
        double[] initialOffset = new double[2];
        bpMatchPeaks("H1", "13C", 0.1, 3.0, initialOffset, false, null);
    }
// fixme  need to pass in array of dimNames to allow for varying dimensions

    public void bpMatchPeaks(final String dimName0, final String dimName1, final double tol0, final double tol1, double[] initialOffset, boolean optimizeMatch, final double[][] boundary) {
        String[][] dimNames = new String[2][2];
        dimNames[0][0] = dimName0;
        dimNames[0][1] = dimName1;
        dimNames[1][0] = dimName0;
        dimNames[1][1] = dimName1;
        double[][] tols = new double[2][2];
        tols[0][0] = tol0;
        tols[0][1] = tol1;
        tols[1][0] = tol0;
        tols[1][1] = tol1;
        double[][] offsets = new double[2][2];
        System.arraycopy(initialOffset, 0, offsets[0], 0, offsets[0].length);
        bpMatchPeaks(dimNames, tols, offsets, optimizeMatch, 1.0, boundary);
        optOffset = new double[offsets[0].length];
        System.arraycopy(offsets[0], 0, optOffset, 0, optOffset.length);
    }

    void bpMatchPeaks(String[][] dimNames, double[][] tols, double[][] offsets, boolean optimizeMatch, double tolMul, final double[][] boundary) {
        // should check for "deleted" peaks
        String[] dimNamesI, dimNamesJ;

        int nDim = iList.nDim;
        dimNamesI = dimNames[0];
        dimNamesJ = dimNames[1];

        boolean matched = true;
        int[] dimsI;
        int[] dimsJ;
        double[] tol = new double[nDim];
        int nDimJ = jList.nDim;
        if ((dimNamesI == null) || (dimNamesI.length == 0)) {
            dimsI = new int[nDim];
            dimsJ = new int[nDimJ];
            for (int k = 0; k < dimsI.length; k++) {
                dimsI[k] = dimsJ[k] = k;
            }
        } else {
            if ((dimNamesJ == null) || (dimNamesJ.length == 0)) {
                dimNamesJ = dimNamesI;
            } else if (dimNamesJ.length != dimNamesI.length) {
                throw new IllegalArgumentException("Number of dims specified for first list not same as for second list");
            }
            dimsI = new int[dimNamesI.length];
            dimsJ = new int[dimNamesI.length];
            for (int k = 0; k < dimsI.length; k++) {
                dimsI[k] = -1;
                dimsJ[k] = -1;
                tol[k] = tols[0][k] > tols[1][k] ? tols[0][k] : tols[1][k];
                for (int i = 0; i < nDim; i++) {
                    if (dimNamesI[k].equals(iList.getSpectralDim(i).getDimName())) {
                        dimsI[k] = i;
                    }
                }
                for (int i = 0; i < nDimJ; i++) {
                    if (dimNamesJ[k].equals(jList.getSpectralDim(i).getDimName())) {
                        dimsJ[k] = i;
                    }
                }
                if ((dimsI[k] == -1) || (dimsJ[k] == -1)) {
                    matched = false;
                    break;
                }
            }

            if (!matched) {
                throw new IllegalArgumentException("Peak Label doesn't match template label");
            }
        }
        ArrayList<MatchItem> iMList = getMatchingItems(iList, dimsI, boundary);
        ArrayList<MatchItem> jMList = getMatchingItems(jList, dimsJ, boundary);
        if (optimizeMatch) {
            optimizeMatch(iMList, offsets[0], jMList, offsets[1], tol, 0, 0.0, 1.0);
        }

        MatchResult matchResult = doBPMatch(iMList, offsets[0], jMList, offsets[1], tol, true);
        System.out.println(matchResult.score);
//        int[] matching = matchResult.matching;
//        TclObject resultList = TclList.newInstance();
//        for (int i = 0; i < iMList.size(); i++) {
//            MatchItem iItem = iMList.get(i);
//            if ((matching[i] >= 0) && (matching[i] < jMList.size())) {
//                MatchItem jItem = jMList.get(matching[i]);
//                Peak iPeak = (Peak) iList.getPeak(iItem.itemIndex);
//                Peak jPeak = (Peak) jList.getPeak(jItem.itemIndex);
//                double deltaSqSum = getMatchingDistanceSq(iItem, offsets[0], jItem, offsets[1], tol);
//                double delta = Math.sqrt(deltaSqSum);
//                TclList.append(interp, resultList, TclString.newInstance(iPeak.getIdNum() + " " + jPeak.getIdNum() + " " + delta));
//            }
//
//        }
//        TclObject result = TclList.newInstance();
//        TclList.append(interp, result, TclString.newInstance("score"));
//        TclList.append(interp, result, TclDouble.newInstance(matchResult.score));
//        TclList.append(interp, result, TclString.newInstance("nMatches"));
//        TclList.append(interp, result, TclInteger.newInstance(matchResult.nMatches));
//        TclList.append(interp, result, TclString.newInstance("offsets"));
//        TclObject offsetList = TclList.newInstance();
//        for (int k = 0; k < dimsI.length; k++) {
//            TclList.append(interp, offsetList, TclDouble.newInstance(offsets[0][k]));
//        }
//
//        TclList.append(interp, result, offsetList);
//        TclList.append(interp, result, TclString.newInstance("matches"));
//        TclList.append(interp, result, resultList);
    }

    double getPeakDistanceSq(Peak iPeak, int[] dimsI, double[] iOffsets, Peak jPeak, int[] dimsJ, double[] tol, double[] jOffsets) {
        double deltaSqSum = 0.0;
        for (int k = 0; k < dimsI.length; k++) {
            double iCtr = iPeak.getPeakDim(dimsI[k]).getChemShift();
            double jCtr = jPeak.getPeakDim(dimsJ[k]).getChemShift();
            double delta = ((iCtr + iOffsets[k]) - (jCtr + jOffsets[k])) / tol[k];
            deltaSqSum += delta * delta;
        }
        return deltaSqSum;
    }

    public double getLinkedSum(final int iIndex, final int jIndex) {
        double deltaSqSum = 0.0;
        Peak iPeak = iList.getPeak(iIndex);
        Peak jPeak = jList.getPeak(jIndex);
        int iDim = 0;
        int iDim2 = 1;
        int jDim = 0;
        int jDim2 = 1;
        PeakDim iPeakDim = iPeak.getPeakDim(iDim);
        PeakDim jPeakDim = jPeak.getPeakDim(jDim);
        List<PeakDim> iPeakDims = PeakList.getLinkedPeakDims(iPeak, iDim);
        List<PeakDim> jPeakDims = PeakList.getLinkedPeakDims(jPeak, jDim);

        ArrayList<MatchItem> iPPMs = new ArrayList<MatchItem>();
        int i = 0;
        for (PeakDim peakDim : iPeakDims) {
            if ((peakDim != iPeakDim) && (peakDim.getSpectralDim() == iDim)) {
                Peak peak = peakDim.getPeak();
                PeakDim peakDim2 = peak.getPeakDim(iDim2);
                MatchItem matchItem = new MatchItem(i, peakDim2.getChemShift());
//System.out.println(i + " " + peak.getIdNum() + " " + peakDim2.getChemShift());
                iPPMs.add(matchItem);
            }
            i++;
        }
        i = 0;
        ArrayList<MatchItem> jPPMs = new ArrayList<MatchItem>();
        for (PeakDim peakDim : jPeakDims) {
            if ((peakDim != jPeakDim) && (peakDim.getSpectralDim() == jDim)) {
                Peak peak = peakDim.getPeak();
                PeakDim peakDim2 = peak.getPeakDim(jDim2);
                MatchItem matchItem = new MatchItem(i, peakDim2.getChemShift());
//System.out.println(i + " " + peak.getIdNum() + " " + peakDim2.getChemShift());
                jPPMs.add(matchItem);
            }
            i++;
        }
//System.out.println(iIndex + " nI " + iPeakDims.size() + " " + iPPMs.size() + " " + jIndex + " nJ " + jPeakDims.size() + " " + jPPMs.size());
        double[] iOffsets = {0.0};
        double[] jOffsets = {0.0};
        double[] tol = {0.1};
        MatchResult matchResult = doBPMatch(iPPMs, iOffsets, jPPMs, jOffsets, tol, false);
//System.out.println(matchResult.score + " " + matchResult.nMatches);
        return matchResult.score;
    }

    double getMatchingDistanceSq(MatchItem iItem, double[] iOffsets, MatchItem jItem, double[] jOffsets, double[] tol) {
        double deltaSqSum = 0.0;
        for (int k = 0; k < iItem.values.length; k++) {
            double iCtr = iItem.values[k];
            double jCtr = jItem.values[k];
            double delta = ((iCtr + iOffsets[k]) - (jCtr + jOffsets[k])) / tol[k];
            deltaSqSum += delta * delta;
        }
        return deltaSqSum;
    }

    static class MatchItem {

        final int itemIndex;
        final double[] values;

        MatchItem(final int itemIndex, final double[] values) {
            this.itemIndex = itemIndex;
            this.values = values;
        }

        MatchItem(final int itemIndex, final double value) {
            this.itemIndex = itemIndex;
            double[] valueArray = {value};
            this.values = valueArray;
        }
    }

    ArrayList<MatchItem> getMatchingItems(PeakList peakList, int[] dims, final double[][] boundary) {
        ArrayList<MatchItem> matchList = new ArrayList<MatchItem>();
        int nPeaks = peakList.size();
        HashSet usedPeaks = new HashSet();
        for (int j = 0; j < nPeaks; j++) {
            Peak peak = (Peak) peakList.getPeak(j);
            if (peak.getStatus() < 0) {
                usedPeaks.add(peak);
            } else if (boundary != null) {
                for (int iDim = 0; iDim < dims.length; iDim++) {
                    double ppm = peak.getPeakDim(iDim).getChemShiftValue();
                    if ((ppm < boundary[iDim][0]) || (ppm > boundary[iDim][1])) {
                        usedPeaks.add(peak);
                        break;
                    }
                }
            }
        }
        for (int j = 0; j < nPeaks; j++) {
            Peak peak = (Peak) peakList.getPeak(j);
            if (usedPeaks.contains(peak)) {
                continue;
            }
            double[] values = new double[dims.length];
            for (int iDim = 0; iDim < dims.length; iDim++) {
                List<PeakDim> linkedPeakDims = PeakList.getLinkedPeakDims(peak, dims[iDim]);
                double ppmCenter = 0.0;
                for (PeakDim peakDim : linkedPeakDims) {
                    Peak peak2 = peakDim.getPeak();
                    usedPeaks.add(peak2);
                    ppmCenter += peakDim.getChemShiftValue();
                }
                values[iDim] = ppmCenter / linkedPeakDims.size();
            }
            MatchItem matchItem = new MatchItem(j, values);
            matchList.add(matchItem);
        }
        return matchList;
    }

    ArrayList<MatchItem> getMatchingItems(double[][] positions) {
        ArrayList<MatchItem> matchList = new ArrayList<MatchItem>();
        for (int j = 0; j < positions.length; j++) {
            MatchItem matchItem = new MatchItem(j, positions[j]);
            matchList.add(matchItem);
        }
        return matchList;
    }

    class MatchResult {

        final double score;
        final int nMatches;
        final int[] matching;

        MatchResult(final int[] matching, final int nMatches, final double score) {
            this.matching = matching;
            this.score = score;
            this.nMatches = nMatches;
        }
    }

    private MatchResult doBPMatch(ArrayList<MatchItem> iMList, final double[] iOffsets, ArrayList<MatchItem> jMList, final double[] jOffsets, double[] tol, final boolean doLinkMatch) {
        int iNPeaks = iMList.size();
        int jNPeaks = jMList.size();
        int nPeaks = iNPeaks + jNPeaks;
        BipartiteMatcher bpMatch = new BipartiteMatcher();
        bpMatch.reset(nPeaks, true);
        // fixme should we add reciprocol match
        for (int iPeak = 0; iPeak < iNPeaks; iPeak++) {
            bpMatch.setWeight(iPeak, jNPeaks + iPeak, -1.0);
        }
        for (int jPeak = 0; jPeak < jNPeaks; jPeak++) {
            bpMatch.setWeight(iNPeaks + jPeak, jPeak, -1.0);
        }
        double minDelta = 10.0;
        int nMatches = 0;
// fixme
//    check for deleted peaks
//    check for peaks outside of a specified region
        for (int iPeak = 0; iPeak < iNPeaks; iPeak++) {
            double minDeltaSq = Double.MAX_VALUE;
            for (int jPeak = 0; jPeak < jNPeaks; jPeak++) {
                double weight = Double.NEGATIVE_INFINITY;
                MatchItem matchI = iMList.get(iPeak);
                MatchItem matchJ = jMList.get(jPeak);
                double deltaSqSum = getMatchingDistanceSq(matchI, iOffsets, matchJ, jOffsets, tol);
                if (deltaSqSum < minDeltaSq) {
                    minDeltaSq = deltaSqSum;
                }
                if (deltaSqSum < minDelta) {
                    weight = Math.exp(-deltaSqSum);
                }
                if (weight != Double.NEGATIVE_INFINITY) {
                    if (doLinkMatch) {
                        //System.out.println("weigh");
                        double linkedSum = getLinkedSum(matchI.itemIndex, matchJ.itemIndex);
                        weight += linkedSum / 10.0;
                        //System.out.println(iPeak + " " + jPeak + " " + deltaSqSum + " " + linkedSum);
                    }
                    bpMatch.setWeight(iPeak, jPeak, weight);
                    nMatches++;
                }
            }
        }
        int[] matching = bpMatch.getMatching();
        double score = 0.0;
        nMatches = 0;
        for (int i = 0; i < iNPeaks; i++) {
            MatchItem matchI = iMList.get(i);
            if ((matching[i] >= 0) && (matching[i] < jMList.size())) {
                MatchItem matchJ = jMList.get(matching[i]);
                double deltaSqSum = getMatchingDistanceSq(matchI, iOffsets, matchJ, jOffsets, tol);
//System.out.println(i + " " + Math.sqrt(deltaSqSum));
                if (deltaSqSum < minDelta) {
                    score += 1.0 - Math.exp(-deltaSqSum);
                } else {
                    score += 1.0;
                }
                nMatches++;
            } else {
                score += 1.0;
            }

        }
        MatchResult matchResult = new MatchResult(matching, nMatches, score);
        return matchResult;
    }

    private void optimizeMatch(final ArrayList<MatchItem> iMList, final double[] iOffsets, final ArrayList<MatchItem> jMList, final double[] jOffsets, final double[] tol, int minDim, double min, double max) {
        class MatchFunction implements MultivariateFunction {

            double[] bestMatch = null;
            double bestValue;

            MatchFunction() {
                bestValue = Double.MAX_VALUE;
            }

            public double value(double[] x) {
                double[] minOffsets = new double[iOffsets.length];
                for (int i = 0; i < minOffsets.length; i++) {
                    minOffsets[i] = iOffsets[i] + x[i];
                }
                MatchResult matchResult = doBPMatch(iMList, minOffsets, jMList, jOffsets, tol, true);
//                System.out.println("score " + matchResult.score);
                if (matchResult.score < bestValue) {
                    bestValue = matchResult.score;
                    if (bestMatch == null) {
                        bestMatch = new double[x.length];
                    }
                    System.arraycopy(x, 0, bestMatch, 0, bestMatch.length);
                }
                return matchResult.score;
            }
        }

        MatchFunction f = new MatchFunction();

        int n = iOffsets.length;
        double initialTrust = 0.5;
        double stopTrust = 1.0e-4;
        int nInterp = n + 2;
        int nSteps = 100;
        double[][] boundaries = new double[2][n];
        for (int i = 0; i < n; i++) {
            boundaries[0][i] = -max;
            boundaries[1][i] = max;
        }
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(nInterp, initialTrust, stopTrust);
        double[] initialGuess = new double[iOffsets.length];
        PointValuePair result = null;
        try {
            result = optimizer.optimize(
                    new MaxEval(nSteps),
                    new ObjectiveFunction(f), GoalType.MINIMIZE,
                    new SimpleBounds(boundaries[0], boundaries[1]),
                    new InitialGuess(initialGuess));
        } catch (TooManyEvaluationsException e) {
            result = new PointValuePair(f.bestMatch, f.bestValue);
        }
        int nEvaluations = optimizer.getEvaluations();
        double finalValue = result.getValue();
        double[] finalPoint = result.getPoint();
        for (int i = 0; i < finalPoint.length; i++) {
            iOffsets[i] += finalPoint[i];
        }

        System.out.println("n " + nEvaluations + " " + finalValue);
        for (double v : iOffsets) {
            System.out.println(v);
        }

    }

}
