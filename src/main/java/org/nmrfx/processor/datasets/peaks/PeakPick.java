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

import org.nmrfx.processor.datasets.Dataset;
import java.util.*;

public class PeakPick {

    public Dataset theFile;
    public String listName;
    public String mode = "new";
    public String region = "box";
    public boolean fixedPick = false;
    public int[][] pt;
    public int[][] ptMax;
    public double[] cpt;
    public int[] dim;
    public double level;
    public double regionWidth = 0;
    public int thickness = 0;
    boolean useAll = false;
    public double sDevN = 0.0;
    public int nPeakDim = 0;
    public int posNeg = 1;
    public double noiseLimit = 0.0;

    public PeakPick(Dataset dataset, String listName) {
        this.theFile = dataset;
        this.listName = listName;
    }

    public PeakPick mode(String mode) {
        this.mode = mode;
        return this;
    }

    public PeakPick region(String region) {
        this.region = region;
        return this;
    }

    public PeakPick fixed(boolean fixed) {
        this.fixedPick = fixed;
        return this;
    }

    public PeakPick level(double level) {
        this.level = level;
        return this;
    }

    public PeakPick noiseLimit(double noiseLimit) {
        this.noiseLimit = noiseLimit;
        return this;
    }

    public PeakPick level(int thickness) {
        this.thickness = thickness;
        return this;
    }

    public PeakPick pos(boolean value) {
        if (value) {
            posNeg = posNeg | 1;
        } else {
            posNeg = posNeg & 2;
        }
        return this;
    }

    public PeakPick neg(boolean value) {
        if (value) {
            posNeg = posNeg | 2;
        } else {
            posNeg = posNeg & 1;
        }
        return this;
    }

    public void calcRange() {
        int iDim = 0;
        int j = 0;
        String arg;
        boolean ptMode = true;
        int i = 0;
        int dataDim = theFile.getNDim();
        pt = new int[dataDim][2];
        cpt = new double[dataDim];

        ptMax = new int[dataDim][2];
        dim = new int[dataDim];

        for (i = 0; i < dataDim; i++) {
            pt[i][0] = 0;
            pt[i][1] = theFile.getSize(i) - 1;
            ptMax[i][0] = 0;
            ptMax[i][1] = theFile.getSize(i) - 1;
            dim[i] = i;
            cpt[i] = (pt[i][0] + pt[i][1]) / 2.0;
        }
    }

    public PeakPick limit(int iDim, double start, double last) {
        int iLast = theFile.ppmToPoint(iDim, start);
        int iStart = theFile.ppmToPoint(iDim, last);
        pt[iDim][0] = iStart;
        pt[iDim][1] = iLast;
        cpt[iDim] = (pt[iDim][0] + pt[iDim][1]) / 2.0;
        return this;
    }

    public PeakPick limit(int iDim, int iStart, int iLast) {
        pt[iDim][0] = iStart;
        pt[iDim][1] = iLast;
        cpt[iDim] = (pt[iDim][0] + pt[iDim][1]) / 2.0;
        return this;
    }

    void fixLimits() {
        int nDims = 1;
        int flatDim = 0;
        int bigDim = 0;
        int maxSize = 0;
        int dataDim = theFile.getNDim();

        if (theFile.getNFreqDims() != 0) {
            nPeakDim = theFile.getNFreqDims();
        } else {
            nPeakDim = dataDim;
        }
        if (!region.equalsIgnoreCase("point")) {
            for (int i = 0; i < dataDim; i++) {
                if ((pt[i][0] == pt[i][1]) && (!fixedPick || (i > 1))) {
                    if (useAll) {
                        pt[i][0] = ptMax[i][0];
                        pt[i][1] = ptMax[i][1];
                    } else {
                        pt[i][0] -= thickness;
                        pt[i][1] += thickness;
                    }
                }
            }
        }

        for (int i = 0; i < dataDim; i++) {
            if (pt[i][0] > pt[i][1]) {
                int hold = pt[i][0];
                pt[i][0] = pt[i][1];
                pt[i][1] = hold;
            }
        }

        if (nPeakDim > 1) {
            nDims = 0;
            DimSizes[] dimSizes = new DimSizes[dataDim];
            for (int i = 0; i < dataDim; i++) {
                int dimSize = Math.abs(pt[i][1] - pt[i][0]) + 1;

                if ((dimSize > 1) || (region.equalsIgnoreCase("point"))) {
                    nDims++;
                } else {
                    flatDim = i;
                }

                dimSizes[i] = new DimSizes(i, dimSize);
                if (dimSize > maxSize) {
                    maxSize = dimSize;
                    bigDim = i;
                }
            }
            Arrays.sort(dimSizes);
            if (((theFile.getNFreqDims() == 0) || (theFile.getNFreqDims() == dataDim)) && (nDims != 0)) {
                nPeakDim = nDims;
            }
            int[][] holdPt = new int[dataDim][2];
            for (int i = 0; i < dimSizes.length; i++) {
                holdPt[i][0] = pt[i][0];
                holdPt[i][1] = pt[i][1];
            }
            for (int i = 0; i < dimSizes.length; i++) {
                dim[i] = dimSizes[i].iDim;
                pt[i][0] = holdPt[dimSizes[i].iDim][0];
                pt[i][1] = holdPt[dimSizes[i].iDim][1];
            }
        }

        for (int i = 0; i < dataDim; i++) {
            if (fixedPick) {
                int hold = (pt[i][0] + pt[i][1]) / 2;
                pt[i][0] = hold;
                pt[i][1] = hold;
            } else {
                cpt[i] = (pt[i][0] + pt[i][1]) / 2.0;
            }

            System.err.println(i + " " + dim[i] + " " + pt[i][0] + " "
                    + pt[i][1] + " " + cpt[i]);
        }

        System.err.println("posneg " + posNeg + " fixed " + fixedPick + " nPeakDim " + nPeakDim);
    }

    private class DimSizes implements Comparable {

        final int iDim;
        final int dimSize;

        DimSizes(final int iDim, final int dimSize) {
            this.iDim = iDim;
            this.dimSize = dimSize;
        }

        public int compareTo(Object o2) {
            DimSizes d2 = (DimSizes) o2;
            int result = 0;
            if (dimSize < d2.dimSize) {
                result = 1;
            } else if (dimSize > d2.dimSize) {
                result = -1;
            } else {
                result = 0;
            }
            return result;
        }
    }
}
