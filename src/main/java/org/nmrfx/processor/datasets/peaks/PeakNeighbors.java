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

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 *
 * @author Bruce Johnson
 */
public class PeakNeighbors {

    PeakList aPeakList = null;
    String[] dimNames;
    double[] cellSizes;
    int[] cellCounts;
    int[] cellStarts;
    int[] atomIndex;
    int[] cellIndex;
    int[] strides;
    int[] iDims;

    int nCells;

//    private static final int[][] offsets = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {-1, 1, 0}, {0, 0, 1},
//    {1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {-1, 1, 1}, {-1, 0, 1},
//    {-1, -1, 1}, {0, -1, 1}, {1, -1, 1}
//    };
    private static final int[][] offsets2d = {{0, 0}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] offsets2dFull = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    public PeakNeighbors(PeakList peakList, int nCells, String[] dimNames) {
        this.aPeakList = peakList;
        this.dimNames = dimNames.clone();
        this.nCells = nCells;
        cellSizes = new double[dimNames.length];
        setCells();

    }

    double[][] getBoundaries() {
        int nDim = dimNames.length;
        iDims = new int[nDim];

        double[][] limits = new double[nDim][2];
        double[] meanWidth = new double[nDim];
        for (int i = 0; i < nDim; i++) {
            iDims[i] = aPeakList.getListDim(dimNames[i]);
            if (iDims[i] < 0) {
                throw new IllegalArgumentException("Invalid dimension " + dimNames[i]);
            }
            SpectralDim sDim = aPeakList.getSpectralDim(iDims[i]);
            double sf = sDim.getSf();
            DoubleSummaryStatistics shiftStats = aPeakList.shiftStats(iDims[i]);
            limits[i][0] = shiftStats.getMin();
            limits[i][1] = shiftStats.getMax();
            DoubleSummaryStatistics widthStats = aPeakList.widthStats(iDims[i]);
            meanWidth[i] = widthStats.getAverage() / sf;
        }
        for (int i = 0; i < nDim; i++) {
            limits[i][1] = limits[i][1] - limits[i][0];
            cellSizes[i] = limits[i][1] / nCells;
        }
        return limits;

    }

    final void setCells() {
        int nDim = dimNames.length;
        double[][] bounds = getBoundaries();
        int[] nCells = new int[nDim];
        strides = new int[nDim];
        int[] iDims = new int[nDim];

        int nStride = 1;
        int nCellsTotal = 1;
        for (int j = 0; j < nDim; j++) {
            iDims[j] = aPeakList.getListDim(dimNames[j]);
            if (iDims[j] < 0) {
                throw new IllegalArgumentException("Invalid dimension " + dimNames[j]);
            }
            nCells[j] = 1 + (int) Math.floor(bounds[j][1] / cellSizes[j]);
            strides[j] = nStride;
            nStride *= nCells[j];
            nCellsTotal *= nCells[j];
        }
        List<Peak> listPeaks = aPeakList.peaks();
        int nPeaks = listPeaks.size();
        cellCounts = new int[nCellsTotal];
        cellStarts = new int[nCellsTotal];
        atomIndex = new int[nPeaks];
        cellIndex = new int[nPeaks];
        int iPeak = 0;
        for (Peak peak : listPeaks) {
            int[] idx = new int[nDim];
            int index = 0;
            for (int j = 0; j < nDim; j++) {
                double ppm = peak.getPeakDim(iDims[j]).getChemShift();
                idx[j] = (int) Math.floor((ppm - bounds[j][0]) / cellSizes[j]);
                index += idx[j] * strides[j];
            }
            cellCounts[index]++;
            cellIndex[iPeak++] = index;
        }

        int start = 0;
        for (int i = 0; i < nCellsTotal; i++) {
            cellStarts[i] = start;
            start += cellCounts[i];
        }
        int[] nAdded = new int[nCellsTotal];
        for (int i = 0; i < nPeaks; i++) {
            int index = cellIndex[i];
            atomIndex[cellStarts[index] + nAdded[index]] = i;
            nAdded[index]++;
        }
    }

    public void findNeighbors() {
        List<Peak> listPeaks = aPeakList.peaks();
        int nCellsTotal = cellCounts.length;
        int[] offsets1 = new int[offsets2d.length];
        int nDim = dimNames.length;
        for (int i = 0; i < offsets1.length; i++) {
            int delta = 0;
            for (int j = 0; j < nDim; j++) {
                delta += offsets2d[i][j] * strides[j];
            }
            offsets1[i] = delta;
        }
        for (int iCell = 0; iCell < nCellsTotal; iCell++) {
            int iStart = cellStarts[iCell];
            int iEnd = iStart + cellCounts[iCell];
            for (int offset : offsets1) {
                int jCell = iCell + offset;
                if ((jCell < 0) || (jCell >= nCellsTotal)) {
                    continue;
                }
                int jStart = cellStarts[jCell];
                int jEnd = jStart + cellCounts[jCell];
                for (int i = iStart; i < iEnd; i++) {
                    int ip = atomIndex[i];
                    for (int j = jStart; j < jEnd; j++) {
                        int jp = atomIndex[j];
                        if ((iCell == jCell) && (ip >= jp)) {
                            continue;
                        }
                        Peak peak1 = listPeaks.get(ip);
                        Peak peak2 = listPeaks.get(jp);
                        if ((peak1.getStatus() >= 0) && (peak1.getStatus() >= 0)) {
                            double distance = peak1.distance(peak2, cellSizes);
                        }
                    }
                }
            }
        }
    }

    public void findNeighbors2() {
        List<Peak> listPeaks = aPeakList.peaks();
        int nCellsTotal = cellCounts.length;
        int[] offsets1 = new int[offsets2dFull.length];
        int nDim = dimNames.length;
        for (int i = 0; i < offsets1.length; i++) {
            int delta = 0;
            for (int j = 0; j < nDim; j++) {
                delta += offsets2dFull[i][j] * strides[j];
            }
            offsets1[i] = delta;
        }
        List<Peak> neighbors = new ArrayList<>();
        for (int iPeak = 0; iPeak < cellIndex.length; iPeak++) {
            Peak peak1 = listPeaks.get(iPeak);
            int iCell = cellIndex[iPeak];
            neighbors.clear();
            for (int offset : offsets1) {
                int jCell = iCell + offset;
                if ((jCell < 0) || (jCell >= nCellsTotal)) {
                    continue;
                }
                int jStart = cellStarts[jCell];
                int jEnd = jStart + cellCounts[jCell];
                for (int j = jStart; j < jEnd; j++) {
                    int jp = atomIndex[j];
                    if (iPeak == jp) {
                        continue;
                    }
                    Peak peak2 = listPeaks.get(jp);
                    if ((peak1.getStatus() >= 0) && (peak1.getStatus() >= 0)) {
                        neighbors.add(peak2);
                    }
                }
            }
            Peak[] closestInArc = new Peak[8];
            double[] disInArc = new double[8];
            double tolLimit = 1.5;
            if (!neighbors.isEmpty()) {
                double sumInvDistance = 0.0;
                for (int i = 0; i < closestInArc.length; i++) {
                    closestInArc[i] = null;
                    disInArc[i] = Double.MAX_VALUE;
                }
                // find closest peak within 8 arcs
                for (Peak peak2 : neighbors) {
                    double dx = (peak1.getPeakDim(iDims[0]).getChemShift() - peak2.getPeakDim(iDims[0]).getChemShift()) / cellSizes[0];
                    double dy = 0.0;
                    if (iDims.length > 1) {
                        dy = (peak1.getPeakDim(iDims[1]).getChemShift() - peak2.getPeakDim(iDims[1]).getChemShift()) / cellSizes[1];
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
                    ;

                    if ((closestInArc[arcIndex] == null) || (disInArc[arcIndex] > distance)) {
                        closestInArc[arcIndex] = peak2;
                        disInArc[arcIndex] = distance;
                    }
                }
                for (int i = 0; i < closestInArc.length; i++) {
                    if (closestInArc[i] != null) {
                        double distance = disInArc[i];
                        if (distance < tolLimit) {
                            sumInvDistance += 1.0 / distance;
                        }
                    }
                }
                double sumDx = 0.0;
                double sumDy = 0.0;
                for (int i = 0; i < closestInArc.length; i++) {
                    if (closestInArc[i] != null) {
                        Peak peak2 = closestInArc[i];
                        double dx = (peak1.getPeakDim(iDims[0]).getChemShift() - peak2.getPeakDim(iDims[0]).getChemShift()) / cellSizes[0];
                        double dy = 0.0;
                        if (iDims.length > 1) {
                            dy = (peak1.getPeakDim(iDims[1]).getChemShift() - peak2.getPeakDim(iDims[1]).getChemShift()) / cellSizes[1];
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

}
