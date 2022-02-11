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

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.SimplePointChecker;
import org.apache.commons.math3.optimization.univariate.BrentOptimizer;
import org.apache.commons.math3.optimization.univariate.UnivariatePointValuePair;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.RegionData;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.optimization.BipartiteMatcher;
import org.nmrfx.processor.optimization.LorentzGaussND;
import org.nmrfx.processor.optimization.LorentzGaussNDWithCatalog;
import org.nmrfx.processor.optimization.SineSignal;
import org.nmrfx.processor.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.CompleteLinkage;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static org.nmrfx.peaks.Peak.getMeasureFunction;

/**
 *
 * @author brucejohnson
 */
public class PeakListTools {
    private static final Logger log = LoggerFactory.getLogger(PeakListTools.class);

    public static ResonanceFactory resFactory() {
        Project project = (Project) Project.getActive();
        return project.resFactory;
    }

    public enum ARRAYED_FIT_MODE {
        SINGLE,
        PLANES,
        EXP;
    }
    /**
     *
     */
    public static final int FIT_ALL = 0;

    /**
     *
     */
    public static final int FIT_AMPLITUDES = 2;

    /**
     *
     */
    public static final int FIT_LW_AMPLITUDES = 1;

    /**
     *
     */
    public static final int FIT_MAX_DEV = 3;

    /**
     *
     */
    public static final int FIT_RMS = 4;

    /**
     *
     */
    public static Map<String, PeakList> peakListTable = new LinkedHashMap<>();

    /**
     *
     */
    public boolean inMem;

    public static void swap(double[] limits) {
        double hold;

        if (limits[1] < limits[0]) {
            hold = limits[0];
            limits[0] = limits[1];
            limits[1] = hold;
        }
    }

    public static void removeDiagonalPeaks(PeakList peakList) {
        removeDiagonalPeaks(peakList, -2.0);

    }

    public static void removeDiagonalPeaks(PeakList peakList, double tol) {
        int iDim = -1;
        int jDim = -1;

        for (int i = 0; i < peakList.getNDim(); i++) {
            for (int j = i + 1; j < peakList.getNDim(); j++) {
                SpectralDim isDim = peakList.getSpectralDim(i);
                SpectralDim jsDim = peakList.getSpectralDim(j);
                double isf = isDim.getSf();
                double jsf = jsDim.getSf();
                // get fractional diff between sfs
                double delta = Math.abs(isf - jsf) / Math.min(isf, jsf);
                // if sf diff < 1% assume these are the two dimensions for diagonal
                if (delta < 0.01) {
                    iDim = i;
                    jDim = j;
                    break;
                }
            }
            if (iDim != -1) {
                break;
            }
        }
        if ((iDim != -1)) {
            removeDiagonalPeaks(peakList, iDim, jDim, tol);
        }
    }

    public static void removeDiagonalPeaks(PeakList peakList, int iDim, int jDim, double tol) {
        if (tol < 0.0) {
            DoubleSummaryStatistics iStats = peakList.widthStatsPPM(iDim);
            DoubleSummaryStatistics jStats = peakList.widthStatsPPM(jDim);
            tol = Math.abs(tol) * Math.max(iStats.getAverage(), jStats.getAverage());
        }
        for (Peak peak : peakList.peaks()) {
            double v1 = peak.getPeakDim(iDim).getChemShiftValue();
            double v2 = peak.getPeakDim(jDim).getChemShiftValue();
            double delta = Math.abs(v1 - v2);
            if (delta < tol) {
                peak.setStatus(-1);
            }
        }
        peakList.compress();
        peakList.reNumber();
    }

    public static void addMirroredPeaks(PeakList peakList) {
        if (peakList.getNDim() == 2) {
            int n = peakList.size();
            for (int i = 0; i < n; i++) {
                Peak peak = peakList.getPeak(i);
                if (!peak.isDeleted()) {
                    Peak newPeak = peakList.getNewPeak();
                    peak.copyTo(newPeak);
                    newPeak.getPeakDim(1).setChemShiftValue(peak.getPeakDim(0).getChemShiftValue());
                    newPeak.getPeakDim(0).setChemShiftValue(peak.getPeakDim(1).getChemShiftValue());
                    newPeak.getPeakDim(1).setLabel(peak.getPeakDim(0).getLabel());
                    newPeak.getPeakDim(0).setLabel(peak.getPeakDim(1).getLabel());
                }
            }
        }
    }

    public static void autoCoupleHomoNuclear(PeakList peakList) {
        if (peakList.getNDim() == 2) {
            double min = 4.0 / peakList.getSpectralDim(0).getSf();
            double max = 18.0 / peakList.getSpectralDim(0).getSf();
            double[] minTol = {0.0, 0.0};
            double[] maxTol = {max, min};
            couple(peakList, minTol, maxTol, PhaseRelationship.INPHASE, 0);
            maxTol[0] = min;
            maxTol[1] = max;
            couple(peakList, minTol, maxTol, PhaseRelationship.INPHASE, 1);
        }
    }

    /**
     *
     * @param minTol
     * @param maxTol
     * @param phaseRel
     * @param dimVal
     * @throws IllegalArgumentException
     */
    public static void couple(PeakList peakList, double[] minTol, double[] maxTol,
            PhaseRelationship phaseRel, int dimVal) throws IllegalArgumentException {
        int nDim = peakList.getNDim();
        if (minTol.length != nDim) {
            throw new IllegalArgumentException("Number of minimum tolerances not equal to number of peak dimensions");
        }

        if (maxTol.length != nDim) {
            throw new IllegalArgumentException("Number of maximum tolerances not equal to number of peak dimensions");
        }

        class Match {

            int i = 0;
            int j = 0;
            double delta = 0.0;

            Match(int i, int j, double delta) {
                this.i = i;
                this.j = j;
                this.delta = delta;
            }

            double getDelta() {
                return delta;
            }
        }

        double biggestMax = 0.0;

        if (dimVal < 0) {
            for (int iDim = 0; iDim < nDim; iDim++) {
                if (maxTol[iDim] > biggestMax) {
                    biggestMax = maxTol[iDim];
                    dimVal = iDim;
                }
            }
        }

        final ArrayList matches = new ArrayList();
        for (int i = 0, n = peakList.size(); i < n; i++) {
            Peak iPeak = peakList.getPeak(i);

            if (iPeak.getStatus() < 0) {
                continue;
            }

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }

                Peak jPeak = peakList.getPeak(j);

                if (jPeak.getStatus() < 0) {
                    continue;
                }

                if (phaseRel != PhaseRelationship.ANYPHASE) {
                    PhaseRelationship phaseRelTest;

                    if (!phaseRel.isSigned()) {
                        phaseRelTest = PhaseRelationship.getType(iPeak.getIntensity(), jPeak.getIntensity());
                    } else {
                        phaseRelTest = PhaseRelationship.getType(iPeak.peakDims[dimVal].getChemShiftValue(),
                                iPeak.getIntensity(), jPeak.peakDims[dimVal].getChemShiftValue(), jPeak.getIntensity());
                    }

                    if (phaseRelTest != phaseRel) {
                        continue;
                    }
                }

                boolean ok = true;
                double deltaMatch = 0.0;

                for (int iDim = 0; iDim < nDim; iDim++) {
                    double delta = Math.abs(iPeak.peakDims[iDim].getChemShiftValue()
                            - jPeak.peakDims[iDim].getChemShiftValue());

                    if ((delta < minTol[iDim]) || (delta > maxTol[iDim])) {
                        ok = false;

                        break;
                    } else if (dimVal == iDim) {
                        deltaMatch = delta;
                    }
                }

                if (ok) {
                    Match match = new Match(i, j, deltaMatch);
                    matches.add(match);
                }
            }
        }

        matches.sort(comparing(Match::getDelta));

        boolean[] iUsed = new boolean[peakList.size()];

        for (int i = 0, n = matches.size(); i < n; i++) {
            Match match = (Match) matches.get(i);

            if (!iUsed[match.i] && !iUsed[match.j]) {
                iUsed[match.i] = true;
                iUsed[match.j] = true;

                Peak iPeak = peakList.getPeak(match.i);
                Peak jPeak = peakList.getPeak(match.j);
                float iIntensity = Math.abs(iPeak.getIntensity());
                float jIntensity = Math.abs(jPeak.getIntensity());
                for (int iDim = 0; iDim < nDim; iDim++) {
                    PeakDim iPDim = iPeak.peakDims[iDim];
                    PeakDim jPDim = jPeak.peakDims[iDim];

                    float iCenter = iPDim.getChemShiftValue();
                    float jCenter = jPDim.getChemShiftValue();
                    float newCenter = (iIntensity * iCenter + jIntensity * jCenter) / (iIntensity + jIntensity);
                    iPDim.setChemShiftValue(newCenter);

                    float iValue = iPDim.getLineWidthValue();
                    float jValue = jPDim.getLineWidthValue();
                    float newValue = (iIntensity * iValue + jIntensity * jValue) / (iIntensity + jIntensity);
                    iPDim.setLineWidthValue(newValue);

                    iValue = iPDim.getBoundsValue();
                    jValue = jPDim.getBoundsValue();

                    float[] edges = new float[4];
                    edges[0] = iCenter - (Math.abs(iValue / 2));
                    edges[1] = jCenter - (Math.abs(jValue / 2));
                    edges[2] = iCenter + (Math.abs(iValue / 2));
                    edges[3] = jCenter + (Math.abs(jValue / 2));

                    float maxDelta = 0.0f;

                    // FIXME need to calculate width
                    // FIXME should only do this if we don't store coupling and keep original bounds
                    for (int iEdge = 0; iEdge < 4; iEdge++) {
                        float delta = Math.abs(edges[iEdge] - newCenter);
                        if (delta > maxDelta) {
                            maxDelta = delta;
                            iPDim.setBoundsValue(delta * 2);
                        }
                    }
                }
                if (jIntensity > iIntensity) {
                    iPeak.setIntensity(jPeak.getIntensity());
                }

                jPeak.setStatus(-1);
            }
        }

        peakList.compress();
    }

    /**
     *
     * @param minTol
     * @param maxTol
     * @return
     */
    public static DistanceMatch[][] getNeighborDistances(PeakList peakList, double[] minTol,
            double[] maxTol) {
        final ArrayList matches = new ArrayList();
        int nDim = peakList.getNDim();

        double[] deltas = new double[nDim];
        DistanceMatch[][] dMatches;
        dMatches = new DistanceMatch[peakList.size()][];

        for (int i = 0, n = peakList.size(); i < n; i++) {
            dMatches[i] = null;

            Peak iPeak = peakList.getPeak(i);

            if (iPeak.getStatus() < 0) {
                continue;
            }

            matches.clear();

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }

                Peak jPeak = peakList.getPeak(j);

                if (jPeak.getStatus() < 0) {
                    continue;
                }

                boolean ok = true;
                double sum = 0.0;

                for (int iDim = 0; iDim < nDim; iDim++) {
                    deltas[iDim] = (iPeak.peakDims[iDim].getChemShiftValue()
                            - jPeak.peakDims[iDim].getChemShiftValue()) / maxTol[iDim];

                    double absDelta = Math.abs(deltas[iDim]);

                    if ((absDelta < (minTol[iDim] / maxTol[iDim]))
                            || (absDelta > 10.0)) {
                        ok = false;

                        break;
                    } else {
                        sum += (deltas[iDim] * deltas[iDim]);
                    }
                }

                if (ok) {
                    double distance = Math.sqrt(sum);
                    DistanceMatch match = new DistanceMatch(iPeak.getIdNum(), jPeak.getIdNum(), deltas, distance);
                    matches.add(match);
                }
            }

            if (matches.size() > 1) {
                matches.sort(comparing(DistanceMatch::getDelta));
                dMatches[i] = new DistanceMatch[matches.size()];

                for (int k = 0; k < matches.size(); k++) {
                    dMatches[i][k] = (DistanceMatch) matches.get(k);
                }
            }
        }

        return dMatches;
    }

    /**
     *
     * @param peakListA
     * @param peakListB
     * @param minTol
     * @param maxTol
     * @throws IllegalArgumentException
     */
    public static void mapLinkPeaks(PeakList peakListA,
            PeakList peakListB, double[] minTol, double[] maxTol)
            throws IllegalArgumentException {
        if (minTol.length != peakListA.getNDim()) {
            throw new IllegalArgumentException(
                    "Number of minimum tolerances not equal to number of peak dimensions");
        }

        if (maxTol.length != peakListB.getNDim()) {
            throw new IllegalArgumentException(
                    "Number of maximum tolerances not equal to number of peak dimensions");
        }

        DistanceMatch[][] aNeighbors = getNeighborDistances(peakListA, minTol,
                maxTol);
        DistanceMatch[][] bNeighbors = getNeighborDistances(peakListB, minTol,
                maxTol);

        class Match {

            int i = 0;
            int j = 0;
            double delta = 0.0;

            Match(int i, int j, double delta) {
                this.i = i;
                this.j = j;
                this.delta = delta;
            }
        }

        final ArrayList matches = new ArrayList();

        for (int i = 0; i < aNeighbors.length; i++) {
            if (aNeighbors[i] != null) {
                Peak peakA = peakListA.getPeak(i);

                for (int j = 0; j < bNeighbors.length; j++) {
                    Peak peakB = peakListB.getPeak(j);
                    double distance = peakA.distance(peakB, maxTol);

                    if (distance > 10.0) {
                        continue;
                    }

                    if (bNeighbors[j] != null) {
                        double score = aNeighbors[i][0].compare(aNeighbors, i,
                                bNeighbors, j);

                        if (score != Double.MAX_VALUE) {
                            Match match = new Match(i, j, score);
                            matches.add(match);
                        }
                    }
                }
            }
        }

        matches.sort(comparing(DistanceMatch::getDelta));

        int m = (aNeighbors.length > bNeighbors.length) ? aNeighbors.length
                : bNeighbors.length;
        boolean[] iUsed = new boolean[m];

        for (int i = 0, n = matches.size(); i < n; i++) {
            Match match = (Match) matches.get(i);

            if (!iUsed[match.i] && !iUsed[match.j]) {
                iUsed[match.i] = true;
                iUsed[match.j] = true;

                // fixme don't seem to be used
                //Peak iPeak = (Peak) peakListA.peaks.elementAt(aNeighbors[match.i][0].iPeak);
                // fixme   Peak jPeak = (Peak) peakListB.peaks.elementAt(bNeighbors[match.j][0].iPeak);
            }
        }
    }

    /**
     *
     */
    public static class MatchItem {

        final int itemIndex;
        final double[] values;

        MatchItem(final int itemIndex, final double[] values) {
            this.itemIndex = itemIndex;
            this.values = values;
        }
    }

    /**
     *
     * @param dims
     * @param tol
     * @param positions
     * @param names
     */
    public static void assignAtomLabels(PeakList peakList, int[] dims, double[] tol, double[][] positions, String[][] names) {
        List<MatchItem> peakItems = getMatchingItems(peakList, dims);
        List<MatchItem> atomItems = getMatchingItems(positions);
        if (tol == null) {
            tol = new double[dims.length];
            for (int iDim = 0; iDim < dims.length; iDim++) {
                tol[iDim] = peakList.widthStatsPPM(iDim).getAverage() * 2.0;
            }
        }
        double[] iOffsets = new double[dims.length];
        double[] jOffsets = new double[dims.length];
        MatchResult result = doBPMatch(peakList, peakItems, iOffsets, atomItems, jOffsets, tol);
        int[] matching = result.matching;
        for (int i = 0; i < peakItems.size(); i++) {
            MatchItem item = peakItems.get(i);
            if (item.itemIndex < peakList.size()) {
                if (matching[i] != -1) {
                    int j = matching[i];
                    if ((j < names.length) && (item.itemIndex < peakItems.size())) {
                        Peak peak = peakList.getPeak(item.itemIndex);
                        for (int iDim = 0; iDim < dims.length; iDim++) {
                            peak.peakDims[dims[iDim]].setLabel(names[j][iDim]);
                        }
                    }
                }

            }
        }
    }

    static List<MatchItem> getMatchingItems(PeakList peakList, int[] dims) {
        List<MatchItem> matchList = new ArrayList<>();
        List<Peak> searchPeaks = peakList.peaks();

        Set<Peak> usedPeaks = searchPeaks.stream().filter(p -> p.getStatus() < 0).collect(Collectors.toSet());

        int j = -1;
        for (Peak peak : searchPeaks) {
            j++;
            if (usedPeaks.contains(peak)) {
                continue;
            }
            double[] values = new double[dims.length];
            for (int iDim = 0; iDim < dims.length; iDim++) {
                List<PeakDim> linkedPeakDims = peakList.getLinkedPeakDims(peak, dims[iDim]);
                double ppmCenter = 0.0;
                for (PeakDim peakDim : linkedPeakDims) {
                    Peak peak2 = (Peak) peakDim.getPeak();
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

    static List<MatchItem> getMatchingItems(double[][] positions) {
        List<MatchItem> matchList = new ArrayList<>();
        for (int j = 0; j < positions.length; j++) {
            MatchItem matchItem = new MatchItem(j, positions[j]);
            matchList.add(matchItem);
        }
        return matchList;
    }

    static class MatchResult {

        final double score;
        final int nMatches;
        final int[] matching;

        MatchResult(final int[] matching, final int nMatches, final double score) {
            this.matching = matching;
            this.score = score;
            this.nMatches = nMatches;
        }
    }

    private static MatchResult doBPMatch(PeakList peakList, List<MatchItem> iMList, final double[] iOffsets, List<MatchItem> jMList, final double[] jOffsets, double[] tol) {
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
        for (int iPeak = 0; iPeak < iNPeaks; iPeak++) {
            double minDeltaSq = Double.MAX_VALUE;
            int minJ = -1;
            for (int jPeak = 0; jPeak < jNPeaks; jPeak++) {
                double weight = Double.NEGATIVE_INFINITY;
                MatchItem matchI = iMList.get(iPeak);
                MatchItem matchJ = jMList.get(jPeak);
                double deltaSqSum = getMatchingDistanceSq(matchI, iOffsets, matchJ, jOffsets, tol);
                if (deltaSqSum < minDeltaSq) {
                    minDeltaSq = deltaSqSum;
                    minJ = jPeak;
                }
                if (deltaSqSum < minDelta) {
                    weight = Math.exp(-deltaSqSum);
                }
                if (weight != Double.NEGATIVE_INFINITY) {
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

    class UnivariateRealPointValuePairChecker implements ConvergenceChecker {

        ConvergenceChecker<PointValuePair> cCheck = new SimplePointChecker<>();

        @Override
        public boolean converged(final int iteration, final Object previous, final Object current) {
            UnivariatePointValuePair pPair = (UnivariatePointValuePair) previous;
            UnivariatePointValuePair cPair = (UnivariatePointValuePair) current;

            double[] pPoint = new double[1];
            double[] cPoint = new double[1];
            pPoint[0] = pPair.getPoint();
            cPoint[0] = cPair.getPoint();
            PointValuePair rpPair = new PointValuePair(pPoint, pPair.getValue());
            PointValuePair rcPair = new PointValuePair(cPoint, cPair.getValue());
            boolean converged = cCheck.converged(iteration, rpPair, rcPair);
            return converged;
        }
    }

    private static void optimizeMatch(PeakList peakList, final ArrayList<MatchItem> iMList, final double[] iOffsets, final ArrayList<MatchItem> jMList, final double[] jOffsets, final double[] tol, int minDim, double min, double max) {
        class MatchFunction implements UnivariateFunction {

            int minDim = 0;

            MatchFunction(int minDim) {
                this.minDim = minDim;
            }

            @Override
            public double value(double x) {
                double[] minOffsets = new double[iOffsets.length];
                System.arraycopy(iOffsets, 0, minOffsets, 0, minOffsets.length);
                minOffsets[minDim] += x;
                MatchResult matchResult = doBPMatch(peakList, iMList, minOffsets, jMList, jOffsets, tol);
                return matchResult.score;
            }
        }
        MatchFunction f = new MatchFunction(minDim);
        double tolAbs = 1E-6;
        //UnivariateRealPointValuePairChecker cCheck = new UnivariateRealPointValuePairChecker();
        //BrentOptimizer brentOptimizer = new BrentOptimizer(tolAbs*10.0,tolAbs,cCheck);
        BrentOptimizer brentOptimizer = new BrentOptimizer(tolAbs * 10.0, tolAbs);
        try {
            UnivariatePointValuePair optValue = brentOptimizer.optimize(100, f, GoalType.MINIMIZE, min, max);

            iOffsets[minDim] += optValue.getPoint();
        } catch (Exception e) {
        }
    }
// fixme removed bpmatchpeaks

    /**
     *
     * @param iPeak
     * @param dimsI
     * @param iOffsets
     * @param jPeak
     * @param dimsJ
     * @param tol
     * @param jOffsets
     * @return
     */
    public static double getPeakDistanceSq(Peak iPeak, int[] dimsI, double[] iOffsets, Peak jPeak, int[] dimsJ, double[] tol, double[] jOffsets) {
        double deltaSqSum = 0.0;
        for (int k = 0; k < dimsI.length; k++) {
            double iCtr = iPeak.getPeakDim(dimsI[k]).getChemShift();
            double jCtr = jPeak.getPeakDim(dimsJ[k]).getChemShift();
            double delta = ((iCtr + iOffsets[k]) - (jCtr + jOffsets[k])) / tol[k];
            deltaSqSum += delta * delta;
        }
        return deltaSqSum;
    }

    /**
     *
     * @param iItem
     * @param iOffsets
     * @param jItem
     * @param jOffsets
     * @param tol
     * @return
     */
    public static double getMatchingDistanceSq(MatchItem iItem, double[] iOffsets, MatchItem jItem, double[] jOffsets, double[] tol) {
        double deltaSqSum = 0.0;
        for (int k = 0; k < iItem.values.length; k++) {
            double iCtr = iItem.values[k];
            double jCtr = jItem.values[k];
            double delta = ((iCtr + iOffsets[k]) - (jCtr + jOffsets[k])) / tol[k];
            deltaSqSum += delta * delta;
        }
        return deltaSqSum;
    }

    public static void clusterPeakColumns(PeakList peakList, int iDim) {
        double widthScale = 0.25;
        DescriptiveStatistics dStat = peakList.widthDStats(iDim);
        double widthPPM = dStat.getPercentile(50.0) / peakList.getSpectralDim(iDim).getSf();
        System.out.println("cluster " + widthPPM * widthScale);
        clusterPeakColumns(peakList, iDim, widthPPM * widthScale);
    }

    /**
     *
     * @param iDim
     * @param limit
     */
    public static void clusterPeakColumns(PeakList peakList, int iDim, double limit) {
        peakList.compress();
        peakList.reIndex();
        int n = peakList.size();
        double[][] proximity = new double[n][n];
        for (Peak peakA : peakList.peaks()) {
            // PeakList.unLinkPeak(peakA, iDim);
            double shiftA = peakA.getPeakDim(iDim).getChemShiftValue();
            for (Peak peakB : peakList.peaks()) {
                double shiftB = peakB.getPeakDim(iDim).getChemShiftValue();
                double dis = Math.abs(shiftA - shiftB);
                proximity[peakA.getIndex()][peakB.getIndex()] = dis;
            }
        }
        CompleteLinkage linkage = new CompleteLinkage(proximity);
        HierarchicalClustering clusterer = HierarchicalClustering.fit(linkage);
        int[] partition = clusterer.partition(limit);
        int nClusters = 0;
        for (int i = 0; i < n; i++) {
            if (partition[i] > nClusters) {
                nClusters = partition[i];
            }
        }
        nClusters++;
        Peak[] roots = new Peak[nClusters];
        for (int i = 0; i < n; i++) {
            int cluster = partition[i];
            if (roots[cluster] == null) {
                roots[cluster] = peakList.getPeak(i);
            } else {
                PeakList.linkPeaks(roots[cluster], iDim, peakList.getPeak(i), iDim);
            }
        }
    }

    // FIXME should check to see that nucleus is same
    // FIXME should check to see that nucleus is same
    /**
     *
     * @param signals
     * @param nExtra
     */
    public static void trimFreqs(ArrayList signals, int nExtra) {
        while (nExtra > 0) {
            int n = signals.size();
            double min = Double.MAX_VALUE;
            int iMin = 0;

            for (int i = 0; i < (n - 1); i++) {
                SineSignal signal0 = (SineSignal) signals.get(i);
                SineSignal signal1 = (SineSignal) signals.get(i + 1);
                double delta = signal0.diff(signal1);

                if (delta < min) {
                    min = delta;
                    iMin = i;
                }
            }

            SineSignal signal0 = (SineSignal) signals.get(iMin);
            SineSignal signal1 = (SineSignal) signals.get(iMin + 1);
            signals.remove(iMin + 1);
            signal0.merge(signal1);
            nExtra--;
            n--;
        }
    }

    /**
     *
     * @param freqs
     * @param amplitudes
     * @param nExtra
     */
    public static void trimFreqs(double[] freqs, double[] amplitudes, int nExtra) {
        double min = Double.MAX_VALUE;
        int iMin = 0;
        int jMin = 0;
        int n = freqs.length;

        while (nExtra > 0) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double delta = Math.abs(freqs[i] - freqs[j]);

                    if (delta < min) {
                        min = delta;
                        iMin = i;
                        jMin = j;
                    }
                }
            }

            freqs[iMin] = (freqs[iMin] + freqs[jMin]) / 2.0;
            amplitudes[iMin] = amplitudes[iMin] + amplitudes[jMin];

            for (int i = jMin; i < (n - 1); i++) {
                freqs[i] = freqs[i + 1];
                amplitudes[i] = amplitudes[i + 1];
            }

            nExtra--;
            n--;
        }
    }

    final static class CenterRef {

        final int index;
        final int dim;

        public CenterRef(final int index, final int dim) {
            this.index = index;
            this.dim = dim;
        }
    }

    final static class GuessValue {

        final double value;
        final double lower;
        final double upper;
        final boolean floating;

        public GuessValue(final double value, final double lower, final double upper, final boolean floating) {
            this.value = value;
            this.lower = lower;
            this.upper = upper;
            this.floating = floating;
        }
    }

    /**
     *
     * @param pt
     * @param cpt
     * @param width
     * @return
     */
    public static boolean inEllipse(final int pt[], final int cpt[], final double[] width) {
        double r2 = 0.0;
        boolean inEllipse = false;
        int cptDim = cpt.length;
        for (int ii = 0; ii < cptDim; ii++) {
            int delta = Math.abs(pt[ii] - cpt[ii]);
            r2 += (delta * delta) / (width[ii] * width[ii]);
        }
        if (r2 < 1.0) {
            inEllipse = true;
        }
        return inEllipse;
    }

    /**
     *
     * @param mode
     */
    public static void quantifyPeaks(PeakList peakList, String mode) {
        if ((peakList.peaks() == null) || peakList.peaks().isEmpty()) {
            return;
        }
        Dataset dataset = Dataset.getDataset(peakList.fileName);
        if (dataset == null) {
            throw new IllegalArgumentException("No dataset for peak list");
        }

        java.util.function.Function<RegionData, Double> f = getMeasureFunction(mode);
        if (f == null) {
            throw new IllegalArgumentException("Invalid measurment mode: " + mode);
        }
        int nDataDim = dataset.getNDim();
        int nDim = peakList.getNDim();
        if (nDim == nDataDim) {
            quantifyPeaks(peakList, dataset, f, mode);
        } else if (nDim == (nDataDim - 1)) {
            int scanDim = 2;
            int nPlanes = dataset.getSizeTotal(scanDim);
            quantifyPeaks(peakList, dataset, f, mode, nPlanes);
        } else if (nDim > nDataDim) {
            throw new IllegalArgumentException("Peak list has more dimensions than dataset");

        } else {
            throw new IllegalArgumentException("Dataset has more than one extra dimension (relative to peak list)");
        }
    }

    public static void quantifyPeaks(PeakList peakList, List<Dataset> datasets, String mode) {
        if ((peakList.peaks() == null) || peakList.peaks().isEmpty()) {
            return;
        }

        java.util.function.Function<RegionData, Double> f = getMeasureFunction(mode);
        if (f == null) {
            throw new IllegalArgumentException("Invalid measurment mode: " + mode);
        }
        int nDataDim = datasets.get(0).getNDim();
        int nDim = peakList.getNDim();
        if (nDim == nDataDim) {
            quantifyPeaks(peakList, datasets, f, mode, 1);  // fixme will this work
        } else if (nDim == (nDataDim - 1)) {
            int scanDim = 2;
            int nPlanes = datasets.get(0).getSizeTotal(scanDim);
            quantifyPeaks(peakList, datasets, f, mode, nPlanes);
        } else if (nDim > nDataDim) {
            throw new IllegalArgumentException("Peak list has more dimensions than dataset");

        } else {
            throw new IllegalArgumentException("Dataset has more than one extra dimension (relative to peak list)");
        }
    }

    /**
     *
     * @param peakList
     * @param dataset
     * @param f
     * @param mode
     */
    public static void quantifyPeaks(PeakList peakList, Dataset dataset, java.util.function.Function<RegionData, Double> f, String mode) {
        int[] pdim = peakList.getDimsForDataset(dataset, true);
        peakList.peaks().stream().forEach(peak -> {
            try {
                peak.quantifyPeak(dataset, pdim, f, mode);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        });
    }

    /**
     *
     * @param peakList
     * @param dataset
     * @param f
     * @param mode
     * @param nPlanes
     */
    public static void quantifyPeaks(PeakList peakList, Dataset dataset, java.util.function.Function<RegionData, Double> f, String mode, int nPlanes) {
        if (f == null) {
            throw new IllegalArgumentException("Unknown measurment type: " + mode);
        }

        peakList.peaks().stream().forEach(peak -> {
            double[][] values = new double[2][nPlanes];
            measurePlanes(nPlanes, peak, dataset, f, mode, values, 0);
            setValues(peak, values, mode);
        });
        setMeasureX(peakList, dataset, nPlanes);
    }

    /**
     *
     * @param peakList
     * @param datasets
     * @param f
     * @param mode
     * @param nPlanes
     */
    public static void quantifyPeaks(PeakList peakList, List<Dataset> datasets, java.util.function.Function<RegionData, Double> f, String mode, int nPlanes) {
        if (f == null) {
            throw new IllegalArgumentException("Unknown measurment type: " + mode);
        }

        peakList.peaks().stream().forEach(peak -> {
            double[][] values = new double[2][datasets.size() * nPlanes];
            int j = 0;
            for (Dataset dataset : datasets) {
                measurePlanes(nPlanes, peak, dataset, f, mode, values, j);
                j += nPlanes;
            }
            setValues(peak, values, mode);

        });
        setMeasureX(peakList, datasets, nPlanes);
    }

    private static void setValues(Peak peak, double[][] values, String mode) {
        if (mode.contains("vol")) {
            peak.setVolume1((float) values[0][0]);
            peak.setVolume1Err((float) values[1][0]);
        } else {
            peak.setIntensity((float) values[0][0]);
            peak.setIntensityErr((float) values[1][0]);
        }
        peak.setMeasures(values);

    }

    private static void measurePlanes(int nPlanes, Peak peak, Dataset dataset,
            java.util.function.Function<RegionData, Double> f,
            String mode, double[][] values, int iValue) {
        int[] planes = new int[1];
        int[] pdim = peak.getPeakList().getDimsForDataset(dataset, true);
        for (int i = 0; i < nPlanes; i++) {
            planes[0] = i;
            try {
                double[] value = peak.measurePeak(dataset, pdim, planes, f, mode);
                values[0][iValue] = value[0];
                values[1][iValue] = value[1];
                iValue++;
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    public static void setMeasureX(PeakList peakList, Dataset dataset, int nValues) {
        double[] pValues = null;
        for (int iDim = 0; iDim < dataset.getNDim(); iDim++) {
            pValues = dataset.getValues(iDim);
            if ((pValues != null) && (pValues.length == nValues)) {
                break;
            }
        }
        if (pValues == null) {
            pValues = new double[nValues];
            for (int i = 0; i < pValues.length; i++) {
                pValues[i] = i;
            }
        }
        Measures measure = new Measures(pValues);
        peakList.setMeasures(measure);
    }

    public static void setMeasureX(PeakList peakList, List<Dataset> datasets, int nValues) {
        double[] allValues = new double[nValues * datasets.size()];
        int j = 0;
        for (Dataset dataset : datasets) {
            double[] pValues = null;
            for (int iDim = 0; iDim < dataset.getNDim(); iDim++) {
                pValues = dataset.getValues(iDim);
                if ((pValues != null) && (pValues.length == nValues)) {
                    break;
                }
            }
            if (pValues == null) {
                pValues = new double[nValues];
                for (int i = 0; i < pValues.length; i++) {
                    pValues[i] = i;
                }
            }
            for (int i = 0; i < pValues.length; i++) {
                allValues[j++] = pValues[i];
            }
        }
        Measures measure = new Measures(allValues);
        peakList.setMeasures(measure);
    }

    /**
     *
     * @param dataset
     * @param speaks
     * @param planes
     */
    public static void tweakPeaks(PeakList peakList, Dataset dataset, Set<Peak> speaks, int[] planes) {
        int[] pdim = peakList.getDimsForDataset(dataset, true);
        speaks.stream().forEach(peak -> {
            try {
                peak.tweak(dataset, pdim, planes);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        });

    }

    /**
     *
     * @param dataset
     * @param planes
     */
    public static void tweakPeaks(PeakList peakList, Dataset dataset, int[] planes) {
        int[] pdim = peakList.getDimsForDataset(dataset, true);

        peakList.peaks().stream().forEach(peak -> {
            try {
                peak.tweak(dataset, pdim, planes);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        });

    }

    /**
     *
     * @param dataset
     * @param peakArray
     * @return
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static List<Object> peakFit(PeakList peakList, DatasetBase dataset, Peak... peakArray)
            throws IllegalArgumentException, IOException, PeakFitException {
        Dataset theFile = (Dataset) dataset;
        boolean doFit = true;
        int fitMode = FIT_ALL;
        boolean updatePeaks = true;
        double[] delays = null;
        double multiplier = 0.686;
        int[] rows = new int[theFile.getNDim()];
        List<Peak> peaks = Arrays.asList(peakArray);
        boolean[] fitPeaks = new boolean[peakArray.length];
        Arrays.fill(fitPeaks, true);
        return peakFit(peakList, theFile, peaks, fitPeaks, rows, doFit, fitMode, updatePeaks, delays, multiplier, false, -1, ARRAYED_FIT_MODE.SINGLE);
    }

    /**
     *
     * @param theFile
     * @param peaks
     * @return
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static List<Object> simPeakFit(PeakList peakList, Dataset theFile, int[] rows, double[] delays, List<Peak> peaks,
            boolean[] fitPeaks, boolean lsFit, int constrainDim, ARRAYED_FIT_MODE arrayedFitMode)
            throws IllegalArgumentException, IOException, PeakFitException {
        boolean doFit = true;
        int fitMode = FIT_ALL;
        boolean updatePeaks = true;
        double multiplier = 0.686;
        return peakFit(peakList, theFile, peaks, fitPeaks, rows, doFit, fitMode, updatePeaks, delays, multiplier, lsFit, constrainDim, arrayedFitMode);
    }

    /**
     *
     * @param theFile
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static void peakFit(PeakList peakList, Dataset theFile, int[] rows, double[] delays, boolean lsFit, int constrainDim, ARRAYED_FIT_MODE arrayedFitMode)
            throws IllegalArgumentException, IOException, PeakFitException {
        peakFit(peakList, theFile, rows, delays, peakList.peaks(), lsFit, constrainDim, arrayedFitMode);
    }

    /**
     *
     * @param theFile
     * @param peaks
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static void peakFit(PeakList peakList, Dataset theFile, int[] rows, double[] delays, Collection<Peak> peaks, boolean lsFit, int constrainDim, ARRAYED_FIT_MODE arrayedFitMode)
            throws IllegalArgumentException, IOException, PeakFitException {
        Set<List<Set<Peak>>> oPeaks = null;
        if (constrainDim < 0) {
            oPeaks = getPeakLayers(peaks);
        } else {
            oPeaks = getPeakColumns(peakList, peaks, constrainDim);
        }
        oPeaks.stream().forEach(oPeakSet -> {
            try {
                List<Peak> lPeaks = new ArrayList<>();
                int nFit = 0;
                for (int i = 0; i < 3; i++) {
//                    for (Peak peak : oPeakSet.get(i)) {
//                        System.out.print(peak.getName() + " ");
//                    }
//                    System.out.println("layer " + i);
                    lPeaks.addAll(oPeakSet.get(i));
                    if (i == 1) {
                        nFit = lPeaks.size();
                    }

                }
                boolean[] fitPeaks = new boolean[lPeaks.size()];
                Arrays.fill(fitPeaks, true);
                for (int i = nFit; i < fitPeaks.length; i++) {
                    fitPeaks[i] = false;
                }
//                System.out.println("fit lpe " + lPeaks.size());
                simPeakFit(peakList, theFile, rows, delays, lPeaks, fitPeaks, lsFit, constrainDim, arrayedFitMode);
            } catch (IllegalArgumentException | IOException | PeakFitException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        );
    }

    /**
     *
     * @param theFile
     * @param argv
     * @param start
     * @param rows
     * @param doFit
     * @param fitMode
     * @param updatePeaks
     * @param delays
     * @param multiplier
     * @return
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static List<Object> peakFit(PeakList peakList, Dataset theFile, String[] argv,
            int start, int[] rows, boolean doFit, int fitMode, final boolean updatePeaks, double[] delays, double multiplier)
            throws IllegalArgumentException, IOException, PeakFitException {

        List<Peak> peaks = new ArrayList<>();

        for (int iArg = start, iPeak = 0; iArg < argv.length; iArg++, iPeak++) {
            Peak peak = peakList.getAPeak(argv[iArg]);
            if (peak == null) {
                throw new IllegalArgumentException(
                        "Couln't find peak \"" + argv[iArg] + "\"");
            }
            peaks.add(peak);
        }
        boolean[] fitPeaks = new boolean[peaks.size()];
        Arrays.fill(fitPeaks, true);

        return peakFit(peakList, theFile, peakList.peaks(), fitPeaks, rows, doFit, fitMode, updatePeaks, delays, multiplier, false, -1, ARRAYED_FIT_MODE.SINGLE);
    }

    /**
     * Fit peaks by adjusting peak position (chemical shift), linewidth and
     * intensity to optimize agreement with data values. Multiple peaks are fit
     * simultaneously. These are normally a group of overlapping peaks.
     *
     * @param theFile The dataset to fit the peaks to
     * @param peaks A collection of peaks to fit simultaneously
     * @param fitPeaks A boolean array of to specify a subset of the peaks that
     * will actually be adjusted
     * @param rows An array of rows (planes etc) of the dataset to be used. This
     * is used when the number of peak dimensions is less than the number of
     * dataset dimensions.
     * @param doFit Currently unused
     * @param fitMode An int value that specifies whether to fit all parameters
     * or just amplitudes
     * @param updatePeaks If true update the peaks with the fitted parameters
     * otherwise return a list of the fit parameters
     * @param delays An array of doubles specifying relaxation delays. If not
     * null then fit peaks to lineshapes and an exponential delay model using
     * data values from different rows or planes of dataset
     * @param multiplier unused?? should multiply width of regions
     * @param lsFit If true and a lineshape catalog exists in dataset then use
     * the lineshape catalog to fit
     * @param constrainDim If this is greater than or equal to 0 then the
     * specified all positions and widths of the specified dimension will be
     * constrained to be the same value. Useful for fitting column or row of
     * peaks.
     * @return a List of alternating name/values with the parameters of the fit
     * if updatePeaks is false. Otherwise return empty list
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws PeakFitException
     */
    public static List<Object> peakFit(PeakList peakList, Dataset theFile, List<Peak> peaks,
            boolean[] fitPeaks,
            int[] rows, boolean doFit, int fitMode, final boolean updatePeaks,
            double[] delays, double multiplier, boolean lsFit, int constrainDim, ARRAYED_FIT_MODE arrayedFitMode)
            throws IllegalArgumentException, IOException, PeakFitException {
        List<Object> peaksResult = new ArrayList<>();
        if (peaks.isEmpty()) {
            return peaksResult;
        }
        boolean fitC = false;
        int nPeakDim = peakList.getNDim();
        int dataDim = theFile.getNDim();
        int rowDim = dataDim - 1;

        int[] pdim = new int[nPeakDim];
        int[][] p1 = new int[dataDim][2];
        int[][] p2 = new int[dataDim][2];
        int nPeaks = peaks.size();
        int[][] cpt = new int[nPeaks][dataDim];
        double[][] width = new double[nPeaks][dataDim];
        double maxDelay = 0.0;
        int nPlanes = 1;
        if ((delays != null) && (delays.length > 0)) {
            maxDelay = StatUtils.max(delays);
        }
        int[][] syncPars = null;
        if (constrainDim != -1) {
            syncPars = new int[peaks.size() * 2][2];
        }

        //int k=0;
        for (int i = 0; i < nPeakDim; i++) {
            pdim[i] = -1;
        }

        // a list of guesses for the fitter
        ArrayList<GuessValue> guessList = new ArrayList<>();
        ArrayList<CenterRef> centerList = new ArrayList<>();
        boolean firstPeak = true;
        int iPeak = -1;
        double globalMax = 0.0;
        for (Peak peak : peaks) {
            iPeak++;
            if (dataDim < nPeakDim) {
                throw new IllegalArgumentException(
                        "Number of peak list dimensions greater than number of dataset dimensions");
            }
            for (int j = 0; j < nPeakDim; j++) {
                boolean ok = false;
                for (int i = 0; i < dataDim; i++) {
                    if (peakList.getSpectralDim(j).getDimName().equals(
                            theFile.getLabel(i))) {
                        pdim[j] = i;
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    throw new IllegalArgumentException(
                            "Can't find match for peak dimension \""
                            + peak.peakList.getSpectralDim(j).getDimName() + "\"");
                }
            }
            for (int dDim = 0, iRow = 0; dDim < dataDim; dDim++) {
                boolean gotThisDim = false;
                for (int pDim = 0; pDim < pdim.length; pDim++) {
                    if (pdim[pDim] == dDim) {
                        gotThisDim = true;
                        break;
                    }
                }
                if (!gotThisDim) {
                    p2[dDim][0] = rows[iRow];
                    p2[dDim][1] = rows[iRow];
                    rowDim = dDim;
                    iRow++;
                }
            }

            peak.getPeakRegion(theFile, pdim, p1, cpt[iPeak], width[iPeak]);
//            System.out.println("fit " + peak);
//            for (int i = 0; i < p2.length; i++) {
//                System.out.println(i + " p1 " + p1[i][0] + " " + p1[i][1]);
//                System.out.println(i + " p2 " + p2[i][0] + " " + p2[i][1]);
//            }
//            for (int i = 0; i < width.length; i++) {
//                for (int j = 0; j < width[i].length; j++) {
//                    System.out.println(i + " " + j + " wid " + width[i][j] + " cpt " + cpt[i][j]);
//                }
//            }

            double intensity = (double) peak.getIntensity();
            GuessValue gValue;
            if (intensity > 0.0) {
                gValue = new GuessValue(intensity, intensity * 0.1, intensity * 3.5, true);
            } else {
                gValue = new GuessValue(intensity, intensity * 1.5, intensity * 0.5, true);
            }
            // add intensity for this peak to guesses
            guessList.add(gValue);
            if (FastMath.abs(intensity) > globalMax) {
                globalMax = FastMath.abs(intensity);
            }
            if ((dataDim - nPeakDim) == 1) {
                if (arrayedFitMode != ARRAYED_FIT_MODE.SINGLE) {
                    nPlanes = theFile.getSizeTotal(dataDim - 1);
                }
            }
            // if rate mode add guesses for relaxation time constant 1/rate and
            // intensity at infinite delay
            if ((delays != null) && (delays.length > 0)) {
                gValue = new GuessValue(maxDelay / 2.0, maxDelay * 5.0, maxDelay * 0.02, true);
                guessList.add(gValue);
                if (fitC) {
                    gValue = new GuessValue(0.0, -0.5 * FastMath.abs(intensity), 0.5 * FastMath.abs(intensity), true);
                    guessList.add(gValue);
                }
            } else if (nPlanes != 1) {
                for (int iPlane = 1; iPlane < nPlanes; iPlane++) {
                    gValue = new GuessValue(intensity, 0.0, intensity * 3.5, true);
                    guessList.add(gValue);
                }
            }
            // loop over dimensions and add guesses for width and position

            for (int pkDim = 0; pkDim < peak.peakList.nDim; pkDim++) {
                int dDim = pdim[pkDim];
                // adding one to account for global max inserted at end
                int parIndex = guessList.size() + 2;
                // if fit amplitudes constrain width fixme
                boolean fitThis = fitPeaks[iPeak];
                if (syncPars != null) {
                    if ((iPeak == 0) && (dDim == constrainDim)) {
                        syncPars[0][0] = parIndex;
                        syncPars[0][1] = parIndex;
                        syncPars[1][0] = parIndex - 1;
                        syncPars[1][1] = parIndex - 1;
                    } else if ((iPeak > 0) && (dDim == constrainDim)) {
                        fitThis = false;
                        syncPars[iPeak * 2][0] = parIndex;
                        syncPars[iPeak * 2][1] = syncPars[0][0];
                        syncPars[iPeak * 2 + 1][0] = parIndex - 1;
                        syncPars[iPeak * 2 + 1][1] = syncPars[1][0];
                    }
                }
                if (fitMode == FIT_AMPLITUDES) {
                    gValue = new GuessValue(width[iPeak][dDim], width[iPeak][dDim] * 0.05, width[iPeak][dDim] * 1.05, false);
                } else {
                    gValue = new GuessValue(width[iPeak][dDim], width[iPeak][dDim] * 0.2, width[iPeak][dDim] * 2.0, fitThis);
                }
                guessList.add(gValue);
                centerList.add(new CenterRef(parIndex, dDim));
                // if fit amplitudes constrain cpt to near current value  fixme
                // and set floating parameter of GuessValue to false
                if (fitMode == FIT_AMPLITUDES) {
                    gValue = new GuessValue(cpt[iPeak][dDim], cpt[iPeak][dDim] - width[iPeak][dDim] / 40, cpt[iPeak][dDim] + width[iPeak][dDim] / 40, false);
                } else {
                    gValue = new GuessValue(cpt[iPeak][dDim], cpt[iPeak][dDim] - width[iPeak][dDim] / 2, cpt[iPeak][dDim] + width[iPeak][dDim] / 2, fitThis);
                }
                guessList.add(gValue);
//System.out.println(iDim + " " + p1[iDim][0] + " " +  p1[iDim][1]);

                // update p2 based on region of peak so it encompasses all peaks
                if (firstPeak) {
                    p2[dDim][0] = p1[dDim][0];
                    p2[dDim][1] = p1[dDim][1];
                } else {
                    if (p1[dDim][0] < p2[dDim][0]) {
                        p2[dDim][0] = p1[dDim][0];
                    }

                    if (p1[dDim][1] > p2[dDim][1]) {
                        p2[dDim][1] = p1[dDim][1];
                    }
                }
            }
            firstPeak = false;
        }
        if ((delays != null) && (delays.length > 0)) {
            GuessValue gValue = new GuessValue(0.0, -0.5 * globalMax, 0.5 * globalMax, false);
            guessList.add(0, gValue);
        } else {
            GuessValue gValue = new GuessValue(0.0, -0.5 * globalMax, 0.5 * globalMax, false);
            guessList.add(0, gValue);
        }
        // get a list of positions that are near the centers of each of the peaks
        ArrayList<int[]> posArray = theFile.getFilteredPositions(p2, cpt, width, pdim, multiplier);
        if (posArray.isEmpty()) {
            System.out.println("no positions");
            for (Peak peak : peaks) {
                System.out.println(peak.getName());
            }
            for (int i = 0; i < p2.length; i++) {
                System.out.println(pdim[i] + " " + p2[i][0] + " " + p2[i][1]);
            }
            for (int i = 0; i < width.length; i++) {
                for (int j = 0; j < width[i].length; j++) {
                    System.out.println(i + " " + j + " " + width[i][j] + " " + cpt[i][j]);
                }
            }

            return peaksResult;

        }
        // adjust guesses for positions so they are relative to initial point
        // position in each dimension
        for (CenterRef centerRef : centerList) {
            GuessValue gValue = guessList.get(centerRef.index);
            int offset = p2[centerRef.dim][0];
            gValue = new GuessValue(gValue.value - offset, gValue.lower - offset, gValue.upper - offset, gValue.floating);
            guessList.set(centerRef.index, gValue);
        }
        int[][] positions = new int[posArray.size()][nPeakDim];
        int i = 0;
        for (int[] pValues : posArray) {
            for (int pkDim = 0; pkDim < nPeakDim; pkDim++) {
                int dDim = pdim[pkDim];
                positions[i][pkDim] = pValues[dDim] - p2[dDim][0];
            }
            i++;
        }
        LorentzGaussND peakFit;
        if (lsFit && (theFile.getLSCatalog() != null)) {
            peakFit = new LorentzGaussNDWithCatalog(positions, theFile.getLSCatalog());
        } else {
            peakFit = new LorentzGaussND(positions);
        }
        double[] guess = new double[guessList.size()];
        double[] lower = new double[guess.length];
        double[] upper = new double[guess.length];
        boolean[] floating = new boolean[guess.length];
        i = 0;
        for (GuessValue gValue : guessList) {
            guess[i] = gValue.value;
            lower[i] = gValue.lower;
            upper[i] = gValue.upper;
            floating[i] = gValue.floating;
            i++;
        }
        int nRates = nPlanes;
        if ((delays != null) && (delays.length > 0)) {
            nRates = delays.length;
        }

        double[][] intensities = new double[nRates][];
        if (nRates == 1) {
            intensities[0] = theFile.getIntensities(posArray);
        } else {
            for (int iRate = 0; iRate < nRates; iRate++) {
                ArrayList<int[]> pos2Array = new ArrayList<>();
                for (int[] pos : posArray) {
                    pos[rowDim] = iRate;
                    pos2Array.add(pos);
                }
                intensities[iRate] = theFile.getIntensities(pos2Array);
            }
        }
        peakFit.setDelays(delays, fitC);
        peakFit.setIntensities(intensities);
        peakFit.setOffsets(guess, lower, upper, floating, syncPars);
        int nFloating = 0;
        for (boolean floats : floating) {
            if (floats) {
                nFloating++;
            }
        }
        //int nInterpolationPoints = (nFloating + 1) * (nFloating + 2) / 2;
        int nInterpolationPoints = 2 * nFloating + 1;
        int nSteps = nInterpolationPoints * 5;
        //System.out.println(guess.length + " " + nInterpolationPoints);
        PointValuePair result;
        try {
            result = peakFit.optimizeBOBYQA(nSteps, nInterpolationPoints);
        } catch (TooManyEvaluationsException tmE) {
            throw new PeakFitException(tmE.getMessage());
        }
        double[] values = result.getPoint();
        for (CenterRef centerRef : centerList) {
            int offset = p2[centerRef.dim][0];
            values[centerRef.index] += offset;
        }
        if (updatePeaks) {
            int index = 1;
            for (Peak peak : peaks) {
                peak.setIntensity((float) values[index++]);
                if ((delays != null) && (delays.length > 0)) {
                    if (fitC) {
                        peak.setComment(String.format("T %.4f %.3f", values[index++], values[index++]));
                    } else {
                        peak.setComment(String.format("T %.4f", values[index++]));
                    }
                } else if (nPlanes > 1) {
                    double[][] measures = new double[2][nPlanes];
                    index--;
                    for (int iPlane = 0; iPlane < nPlanes; iPlane++) {
                        measures[0][iPlane] = values[index++];
                    }
                    peak.setMeasures(measures);
                }
                double lineWidthAll = 1.0;
                for (int pkDim = 0; pkDim < nPeakDim; pkDim++) {
                    int dDim = pdim[pkDim];
                    PeakDim peakDim = peak.getPeakDim(pkDim);
                    double lineWidth = theFile.ptWidthToPPM(dDim, values[index]);
                    //double lineWidthHz = theFile.ptWidthToHz(iDim, values[index++]);
                    double lineWidthHz = values[index++];
                    peakDim.setLineWidthValue((float) lineWidth);
                    //lineWidthAll *= lineWidthHz * (Math.PI / 2.0);
                    lineWidthAll *= lineWidthHz;
                    peakDim.setBoundsValue((float) (lineWidth * 1.5));
                    peakDim.setChemShiftValueNoCheck((float) theFile.pointToPPM(dDim, values[index++]));
                }
                peak.setVolume1((float) (peak.getIntensity() * lineWidthAll));
            }
            if (nPlanes > 1) {
                setMeasureX(peakList, theFile, nPlanes);
            }
        } else {
            int index = 1;
            for (Peak peak : peaks) {
                List<Object> peakData = new ArrayList<>();

                peakData.add("peak");
                peakData.add(peak.getName());
                peakData.add("int");
                peakData.add(values[index++]);
                if ((delays != null) && (delays.length > 0)) {
                    peakData.add("relax");
                    peakData.add(values[index++]);
                    if (fitC) {
                        peakData.add("relaxbase");
                        peakData.add(values[index++]);
                    }
                } else if (nPlanes > 1) {
                    index += nPlanes - 1;
                }
                double lineWidthAll = 1.0;
                for (int iDim = 0; iDim < peak.peakList.nDim; iDim++) {
                    //double lineWidthHz = theFile.ptWidthToHz(iDim, values[index++]);
                    double lineWidthHz = values[index++];
                    //lineWidthAll *= lineWidthHz * (Math.PI / 2.0);
                    lineWidthAll *= lineWidthHz;
                    String elem = (iDim + 1) + ".WH";
                    peakData.add(elem);
                    peakData.add(lineWidthHz);
                    elem = (iDim + 1) + ".P";
                    peakData.add(elem);
                    peakData.add(theFile.pointToPPM(iDim, values[index++]));
                }
                peakData.add("vol");
                peakData.add(peak.getIntensity() * lineWidthAll);
                peaksResult.add(peakData);
            }
        }
        return peaksResult;
    }

    /**
     *
     * @return
     */
    public static Set<List<Peak>> getOverlappingPeaks(PeakList peakList) {
        Set<List<Peak>> result = new HashSet<>();
        boolean[] used = new boolean[peakList.size()];
        for (int i = 0, n = peakList.size(); i < n; i++) {
            Peak peak = peakList.getPeak(i);
            if (used[i]) {
                continue;
            }
            Set<Peak> overlaps = peak.getAllOverlappingPeaks();
            result.add(new ArrayList<Peak>(overlaps));
            for (Peak checkPeak : overlaps) {
                used[checkPeak.getIndex()] = true;
            }
        }
        return result;
    }

    public static Set<List<Set<Peak>>> getPeakLayers(PeakList peakList) {
        return getPeakLayers(peakList.peaks());
    }

    /**
     *
     * @param fitPeaks
     * @return
     */
    public static Set<List<Set<Peak>>> getPeakLayers(Collection<Peak> fitPeaks) {
        Set<List<Set<Peak>>> result = new HashSet<>();
        Set<Peak> used = new HashSet<>();
        for (Peak peak : fitPeaks) {
            if (used.contains(peak)) {
                continue;
            }
            List<Set<Peak>> overlaps = peak.getOverlapLayers(1.5);
            result.add(overlaps);
            Set<Peak> firstLayer = overlaps.get(1);
            Set<Peak> secondLayer = overlaps.get(2);
            if (secondLayer.isEmpty()) {
                for (Peak checkPeak : firstLayer) {
                    used.add(checkPeak);
                }
            }
        }
        return result;
    }

    /**
     *
     * @param fitPeaks
     * @return
     */
    public static Set<List<Set<Peak>>> getPeakColumns(PeakList peakList, Collection<Peak> fitPeaks, int iDim) {
        Set<List<Set<Peak>>> result = new HashSet<>();
        Set<Peak> used = new HashSet<>();
        for (Peak peak : fitPeaks) {
            if (!used.contains(peak)) {
                List<PeakDim> peakDims = peakList.getLinkedPeakDims(peak, iDim);
                Set<Peak> firstLayer = new HashSet<>();
                for (PeakDim peakDim : peakDims) {
                    used.add((Peak) peakDim.getPeak());
                    firstLayer.add((Peak) peakDim.getPeak());
                }
                List<Set<Peak>> column = new ArrayList<>();
                column.add(firstLayer);
                column.add(Collections.EMPTY_SET);
                column.add(Collections.EMPTY_SET);
                result.add(column);
            }

        }
        return result;
    }

    /**
     *
     * @param fitPeaks
     * @return
     */
    public static Set<Set<Peak>> getOverlappingPeaks(Collection<Peak> fitPeaks) {
        Set<Set<Peak>> result = new HashSet<>();
        Set<Peak> used = new HashSet<>();
        for (Peak peak : fitPeaks) {
            if (used.contains(peak)) {
                continue;
            }
            Set<Peak> overlaps = peak.getAllOverlappingPeaks();
            result.add(overlaps);
            for (Peak checkPeak : overlaps) {
                used.add(checkPeak);
            }
        }
        return result;
    }

    /**
     *
     */
    static public class PhaseRelationship {

        private static final TreeMap TYPES_LIST = new TreeMap();

        /**
         *
         */
        public static final PhaseRelationship ANYPHASE = new PhaseRelationship(
                "anyphase");

        /**
         *
         */
        public static final PhaseRelationship INPHASE = new PhaseRelationship(
                "inphase");

        /**
         *
         */
        public static final PhaseRelationship INPHASE_POS = new PhaseRelationship(
                "inphase_pos");

        /**
         *
         */
        public static final PhaseRelationship INPHASE_NEG = new PhaseRelationship(
                "inphase_neg");

        /**
         *
         */
        public static final PhaseRelationship ANTIPHASE = new PhaseRelationship(
                "antiphase");

        /**
         *
         */
        public static final PhaseRelationship ANTIPHASE_LEFT = new PhaseRelationship(
                "antiphase_left");

        /**
         *
         */
        public static final PhaseRelationship ANTIPHASE_RIGHT = new PhaseRelationship(
                "antiphase_right");
        private final String name;

        private PhaseRelationship(String name) {
            this.name = name;
            TYPES_LIST.put(name, this);
        }

        @Override
        public String toString() {
            return name;
        }

        /**
         *
         * @return
         */
        public boolean isSigned() {
            return (toString().contains("_"));
        }

        /**
         *
         * @param name
         * @return
         */
        public static PhaseRelationship getFromString(String name) {
            return (PhaseRelationship) TYPES_LIST.get(name);
        }

        /**
         *
         * @param intensity1
         * @param intensity2
         * @return
         */
        public static PhaseRelationship getType(double intensity1,
                double intensity2) {
            if (intensity1 > 0) {
                if (intensity2 > 0) {
                    return INPHASE;
                } else {
                    return ANTIPHASE;
                }
            } else if (intensity2 > 0) {
                return ANTIPHASE;
            } else {
                return INPHASE;
            }
        }

        /**
         *
         * @param ctr1
         * @param intensity1
         * @param ctr2
         * @param intensity2
         * @return
         */
        public static PhaseRelationship getType(double ctr1, double intensity1,
                double ctr2, double intensity2) {
            double left;
            double right;

            if (ctr1 > ctr2) {
                left = intensity1;
                right = intensity2;
            } else {
                left = intensity2;
                right = intensity1;
            }

            if (left > 0) {
                if (right > 0) {
                    return INPHASE_POS;
                } else {
                    return ANTIPHASE_LEFT;
                }
            } else if (right > 0) {
                return ANTIPHASE_RIGHT;
            } else {
                return INPHASE_NEG;
            }
        }
    }

    /**
     *
     */
    public static class DistanceMatch {

        int iPeak = 0;
        int jPeak = 0;
        double delta = 0.0;
        double[] deltas;

        DistanceMatch(int iPeak, int jPeak, double[] deltas, double delta) {
            this.iPeak = iPeak;
            this.jPeak = jPeak;
            this.delta = delta;
            this.deltas = new double[deltas.length];

            System.arraycopy(deltas, 0, this.deltas, 0, deltas.length);
        }

        double getDelta() {
            return delta;
        }

        /**
         *
         * @param aNeighbors
         * @param iNeighbor
         * @param bNeighbors
         * @param jNeighbor
         * @return
         */
        public double compare(DistanceMatch[][] aNeighbors, int iNeighbor,
                DistanceMatch[][] bNeighbors, int jNeighbor) {
            double globalSum = 0.0;

            for (DistanceMatch aDis : aNeighbors[iNeighbor]) {
                double sumMin = Double.MAX_VALUE;

                for (DistanceMatch bDis : bNeighbors[jNeighbor]) {
                    double sum = 0.0;

                    for (int k = 0; k < deltas.length; k++) {
                        double dif = (aDis.deltas[k] - bDis.deltas[k]);
                        sum += (dif * dif);
                    }

                    if (sum < sumMin) {
                        sumMin = sum;
                    }
                }

                globalSum += Math.sqrt(sumMin);
            }

            return globalSum;
        }

        @Override
        public String toString() {
            StringBuilder sBuf = new StringBuilder();
            sBuf.append(iPeak);
            sBuf.append(" ");
            sBuf.append(jPeak);
            sBuf.append(" ");
            sBuf.append(delta);

            for (int j = 0; j < deltas.length; j++) {
                sBuf.append(" ");
                sBuf.append(deltas[j]);
            }

            return sBuf.toString();
        }
    }

}
