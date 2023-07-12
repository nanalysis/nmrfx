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

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class PeakNeighbors {

    String[] dimNames;
    double[] cellSizes;
    int[] strides;
    PeakList[] peakLists = new PeakList[2];
    int[][] cellCounts = new int[2][];
    int[][] cellStarts = new int[2][];
    int[][] peakIndex = new int[2][];
    int[][] cellIndex = new int[2][];
    double[][] meanWidth = new double[2][];
    int[][] dims = new int[2][];

    int nCells;

    private static final int[][] OFFSETS2D_FULL = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    public PeakNeighbors(PeakList peakList, int nCells, String[] dimNames) {
        this.peakLists[0] = peakList;
        this.dimNames = dimNames.clone();
        this.nCells = nCells;
        cellSizes = new double[dimNames.length];
        setCells(0);
    }

    public PeakNeighbors(PeakList peakListA, PeakList peakListB, int nCells, String[] dimNames) {
        this.peakLists[0] = peakListA;
        this.peakLists[1] = peakListB;
        this.dimNames = dimNames.clone();
        this.nCells = nCells;
        cellSizes = new double[dimNames.length];
        setCells(0);
        setCells(1);
    }

    public PeakNeighbors(PeakList peakListA, PeakList peakListB, int nCells, List<String> dimNameList) {
        this.peakLists[0] = peakListA;
        this.peakLists[1] = peakListB;
        this.dimNames = dimNameList.toArray(new String[0]);
        this.nCells = nCells;
        cellSizes = new double[dimNameList.size()];
        setCells(0);
        setCells(1);
    }

    double[][] getBoundaries() {
        int nDim = dimNames.length;
        dims[0] = new int[nDim];
        meanWidth[0] = new double[nDim];

        double[][] limits = new double[nDim][2];
        for (int i = 0; i < nDim; i++) {
            dims[0][i] = peakLists[0].getListDim(dimNames[i]);
            if (dims[0][i] < 0) {
                throw new IllegalArgumentException("Invalid dimension " + dimNames[i]);
            }
            SpectralDim sDim = peakLists[0].getSpectralDim(dims[0][i]);
            double sf = sDim.getSf();
            DoubleSummaryStatistics shiftStatsA = peakLists[0].shiftStats(dims[0][i]);
            double aMin = shiftStatsA.getMin();
            double aMax = shiftStatsA.getMax();
            DoubleSummaryStatistics widthStats = peakLists[0].widthStats(dims[0][i]);
            meanWidth[0][i] = widthStats.getAverage() / sf;
            if (peakLists[1] == null) {
                limits[i][0] = aMin;
                limits[i][1] = aMax;
            } else {
                dims[1] = new int[nDim];
                meanWidth[1] = new double[nDim];
                dims[1][i] = peakLists[1].getListDim(dimNames[i]);
                if (dims[1][i] < 0) {
                    throw new IllegalArgumentException("Invalid dimension " + dimNames[i]);
                }
                SpectralDim sDimB = peakLists[1].getSpectralDim(dims[1][i]);
                double sfB = sDimB.getSf();
                DoubleSummaryStatistics shiftStatsB = peakLists[1].shiftStats(dims[1][i]);
                double bMin = shiftStatsB.getMin();
                double bMax = shiftStatsB.getMax();
                limits[i][0] = Math.min(aMin, bMin);
                limits[i][1] = Math.max(aMax, bMax);
                DoubleSummaryStatistics widthStatsB = peakLists[1].widthStats(dims[1][i]);
                meanWidth[1][i] = widthStatsB.getAverage() / sfB;
            }

        }
        for (int i = 0; i < nDim; i++) {
            limits[i][1] = limits[i][1] - limits[i][0];
            cellSizes[i] = limits[i][1] / nCells;
        }
        return limits;

    }

    final void setCells(int iP) {
        int nDim = dimNames.length;
        double[][] bounds = getBoundaries();
        strides = new int[nDim];
        int[] iDims = new int[nDim];

        int nStride = 1;
        int nCellsTotal = 1;
        for (int j = 0; j < nDim; j++) {
            iDims[j] = peakLists[iP].getListDim(dimNames[j]);
            if (iDims[j] < 0) {
                throw new IllegalArgumentException("Invalid dimension " + dimNames[j]);
            }
            int nCell = 1 + (int) Math.floor(bounds[j][1] / cellSizes[j]);
            strides[j] = nStride;
            nStride *= nCell;
            nCellsTotal *= nCell;
        }
        List<Peak> listPeaks = peakLists[iP].peaks();
        int nPeaks = listPeaks.size();
        cellCounts[iP] = new int[nCellsTotal];
        cellStarts[iP] = new int[nCellsTotal];
        peakIndex[iP] = new int[nPeaks];
        cellIndex[iP] = new int[nPeaks];
        int iPeak = 0;
        for (Peak peak : listPeaks) {
            int[] idx = new int[nDim];
            int index = 0;
            for (int j = 0; j < nDim; j++) {
                double ppm = peak.getPeakDim(iDims[j]).getChemShift();
                idx[j] = (int) Math.floor((ppm - bounds[j][0]) / cellSizes[j]);
                index += idx[j] * strides[j];
            }
            cellCounts[iP][index]++;
            cellIndex[iP][iPeak++] = index;
        }

        int start = 0;
        for (int i = 0; i < nCellsTotal; i++) {
            cellStarts[iP][i] = start;
            start += cellCounts[iP][i];
        }
        int[] nAdded = new int[nCellsTotal];
        for (int i = 0; i < nPeaks; i++) {
            int index = cellIndex[iP][i];
            peakIndex[iP][cellStarts[iP][index] + nAdded[index]] = i;
            nAdded[index]++;
        }
    }

    public double measureDistance() {
        return measureDistance(null);
    }

    public double measureDistance(double[] shiftOffset) {
        List<Peak> listPeaksA = peakLists[0].peaks();
        List<Peak> listPeaksB = peakLists[1].peaks();
        int nCellsTotal = cellCounts[1].length;
        int[] offsets1 = new int[OFFSETS2D_FULL.length];
        int nDim = dimNames.length;
        if (shiftOffset == null) {
            shiftOffset = new double[nDim];
        }
        for (int i = 0; i < offsets1.length; i++) {
            int delta = 0;
            for (int j = 0; j < nDim; j++) {
                delta += OFFSETS2D_FULL[i][j] * strides[j];
            }
            offsets1[i] = delta;
        }
        double sumMinDistance = 0.0;
        int nLonely = 0;
        // maxDistance is the maximum of the minimum distance for peaks with neighbors
        double maxDistance = Double.NEGATIVE_INFINITY;
        for (int iPeak = 0; iPeak < cellIndex[0].length; iPeak++) {
            Peak peak1 = listPeaksA.get(iPeak);
            int iCell = cellIndex[0][iPeak];
            double minDistance = Double.MAX_VALUE;
            int nNeighbors = 0;
            for (int offset : offsets1) {
                int jCell = iCell + offset;
                if ((jCell < 0) || (jCell >= nCellsTotal)) {
                    continue;
                }
                int jStart = cellStarts[1][jCell];
                int jEnd = jStart + cellCounts[1][jCell];
                for (int j = jStart; j < jEnd; j++) {
                    int jp = peakIndex[1][j];
                    if (iPeak == jp) {
                        continue;
                    }
                    Peak peak2 = listPeaksB.get(jp);
                    if ((peak1.getStatus() >= 0) && (peak2.getStatus() >= 0)) {
                        double sumSq = 0.0;
                        for (int k = 0; k < dims[0].length; k++) {
                            double dx = (peak1.getPeakDim(dims[0][k]).getChemShift()
                                    - peak2.getPeakDim(dims[1][k]).getChemShift() + shiftOffset[k]) / cellSizes[k];
                            sumSq += dx * dx;
                        }
                        double distance = Math.sqrt(sumSq);
                        minDistance = Math.min(distance, minDistance);
                        nNeighbors++;
                    }
                }
            }

            if (nNeighbors > 0) {
                sumMinDistance += minDistance;
                maxDistance = Math.max(minDistance, maxDistance);
            } else {
                nLonely++;
            }
        }
        // peaks with no neighbors get a distance corresponding to the maximum of all the minimum distances
        sumMinDistance += nLonely * maxDistance;
        return sumMinDistance;
    }

    public void optimizePeakLabelPositions() {
        List<Peak> listPeaks = peakLists[0].peaks();
        int nCellsTotal = cellCounts[0].length;
        int[] offsets1 = new int[OFFSETS2D_FULL.length];
        int nDim = dimNames.length;
        for (int i = 0; i < offsets1.length; i++) {
            int delta = 0;
            for (int j = 0; j < nDim; j++) {
                delta += OFFSETS2D_FULL[i][j] * strides[j];
            }
            offsets1[i] = delta;
        }
        List<Peak> neighbors = new ArrayList<>();
        for (int iPeak = 0; iPeak < cellIndex[0].length; iPeak++) {
            Peak peak1 = listPeaks.get(iPeak);
            int iCell = cellIndex[0][iPeak];
            neighbors.clear();
            for (int offset : offsets1) {
                int jCell = iCell + offset;
                if ((jCell < 0) || (jCell >= nCellsTotal)) {
                    continue;
                }
                int jStart = cellStarts[0][jCell];
                int jEnd = jStart + cellCounts[0][jCell];
                for (int j = jStart; j < jEnd; j++) {
                    int jp = peakIndex[0][j];
                    if (iPeak == jp) {
                        continue;
                    }
                    Peak peak2 = listPeaks.get(jp);
                    if ((peak1.getStatus() >= 0) && (peak2.getStatus() >= 0)) {
                        neighbors.add(peak2);
                    }
                }
            }
            Peak[] closestPeaks = new Peak[8];
            double[] disInArc = new double[8];
            double tolLimit = 1.5;
            if (!neighbors.isEmpty()) {
                double sumInvDistance = 0.0;
                for (int i = 0; i < closestPeaks.length; i++) {
                    closestPeaks[i] = null;
                    disInArc[i] = Double.MAX_VALUE;
                }
                // find closest peak within 8 arcs
                for (Peak peak2 : neighbors) {
                    double dx = (peak1.getPeakDim(dims[0][0]).getChemShift() - peak2.getPeakDim(dims[0][0]).getChemShift()) / cellSizes[0];
                    double dy = 0.0;
                    if (dims[0].length > 1) {
                        dy = (peak1.getPeakDim(dims[0][1]).getChemShift() - peak2.getPeakDim(dims[0][1]).getChemShift()) / cellSizes[1];
                    }
                    double angle = Math.atan2(dy, dx);
                    int arcIndex = (int) Math.round(angle / (Math.PI / 4.0)) + 4;
                    if (arcIndex >= 8) {
                        arcIndex = 0;
                    }
                    if (arcIndex < 0) {
                        arcIndex = 0;
                    }
                    double distance = Math.sqrt(dx * dx + dy * dy);

                    if ((closestPeaks[arcIndex] == null) || (disInArc[arcIndex] > distance)) {
                        closestPeaks[arcIndex] = peak2;
                        disInArc[arcIndex] = distance;
                    }
                }
                for (int i = 0; i < closestPeaks.length; i++) {
                    if (closestPeaks[i] != null) {
                        double distance = disInArc[i];
                        if (distance < tolLimit) {
                            sumInvDistance += 1.0 / distance;
                        }
                    }
                }
                double sumDx = 0.0;
                double sumDy = 0.0;
                for (Peak closestPeak : closestPeaks) {
                    if (closestPeak != null) {
                        Peak peak2 = closestPeak;
                        double dx = (peak1.getPeakDim(dims[0][0]).getChemShift() - peak2.getPeakDim(dims[0][0]).getChemShift()) / cellSizes[0];
                        double dy = 0.0;
                        if (dims[0].length > 1) {
                            dy = (peak1.getPeakDim(dims[0][1]).getChemShift() - peak2.getPeakDim(dims[0][1]).getChemShift()) / cellSizes[1];
                        }
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        if (distance < tolLimit) {
                            sumDx += dx * (1.0 / distance) / sumInvDistance;
                            sumDy += dy * (1.0 / distance) / sumInvDistance;
                        }
                    }
                }

                double dD = Math.sqrt(sumDx * sumDx + sumDy * sumDy);
                double scale = 0.7;
                if (dD > 0.01) {
                    double dx = scale * sumDx / dD;
                    double dy = scale * sumDy / dD;
                    peak1.setCorner(-dx, dy);
                } else {
                    peak1.setCorner("ne");
                }
            }
        }
    }

    public void optimizeMatch(final double[] iOffsets, double min, double max) {
        class MatchFunction implements MultivariateFunction {

            double[] bestMatch = null;
            double bestValue;

            MatchFunction() {
                bestValue = Double.MAX_VALUE;
            }

            @Override
            public double value(double[] x) {
                double[] minOffsets = new double[iOffsets.length];
                for (int i = 0; i < minOffsets.length; i++) {
                    minOffsets[i] = iOffsets[i] + x[i];
                }
                double score = measureDistance(minOffsets);
                if (score < bestValue) {
                    bestValue = score;
                    if (bestMatch == null) {
                        bestMatch = new double[x.length];
                    }
                    System.arraycopy(x, 0, bestMatch, 0, bestMatch.length);
                }
                return score;
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
        PointValuePair result;
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
