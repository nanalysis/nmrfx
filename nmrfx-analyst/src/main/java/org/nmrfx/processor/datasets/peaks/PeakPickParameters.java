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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.nmrfx.processor.datasets.peaks.PeakPickParameters.PickMode.NEW;

@PythonAPI("dscript")
public class PeakPickParameters {

    private static final Logger log = LoggerFactory.getLogger(PeakPickParameters.class);
    public Dataset theFile = null;
    public String listName = null;
    public PeakList filterList = null;
    public boolean filter = false;
    public double filterWidth;
    public PickMode mode = NEW;
    public String region = "box";
    public boolean useCrossHairs;
    public boolean refineLS = false;
    public boolean saveFile = false;
    public boolean fixedPick = false;
    public int[][] pt = null;
    public int[][] ptMax;
    public double[] cpt;
    public int[] dim;
    public Double level = null;
    public double regionWidth = 0;
    public int thickness = 0;
    public boolean useAll = false;
    public double sDevN = 0.0;
    public int nPeakDim = 0;
    public int posNeg = 1;
    public double noiseLimit = 0.0;

    public boolean useNoise = false;

    public enum PickMode {
        NEW,
        APPEND,
        APPENDIF,
        APPENDREGION,
        REPLACE,
        REPLACEIF;

        public boolean isAppend() {
            return this == APPEND || this == APPENDIF || this == APPENDREGION;
        }
    }
    public PeakPickParameters(Dataset dataset, String listName) {
        this.theFile = dataset;
        this.listName = listName;
    }
    public PeakPickParameters() {
    }

    public PeakPickParameters mode(PickMode mode) {
        this.mode = mode;
        return this;
    }

    public PeakPickParameters region(String region) {
        this.region = region;
        return this;
    }

    public PeakPickParameters fixed(boolean fixed) {
        this.fixedPick = fixed;
        return this;
    }

    public PeakPickParameters level(double level) {
        this.level = level;
        return this;
    }

    public PeakPickParameters noiseLimit(double noiseLimit) {
        this.noiseLimit = noiseLimit;
        return this;
    }

    public PeakPickParameters level(int thickness) {
        this.thickness = thickness;
        return this;
    }

    public PeakPickParameters pos(boolean value) {
        if (value) {
            posNeg = posNeg | 1;
        } else {
            posNeg = posNeg & 2;
        }
        return this;
    }

    public PeakPickParameters neg(boolean value) {
        if (value) {
            posNeg = posNeg | 2;
        } else {
            posNeg = posNeg & 1;
        }
        return this;
    }

    public void calcRange() {
        int dataDim = theFile.getNDim();
        pt = new int[dataDim][2];
        cpt = new double[dataDim];

        ptMax = new int[dataDim][2];
        dim = new int[dataDim];

        for (int i = 0; i < dataDim; i++) {
            pt[i][0] = 0;
            pt[i][1] = theFile.getSizeReal(i) - 1;
            ptMax[i][0] = 0;
            ptMax[i][1] = theFile.getSizeReal(i) - 1;
            dim[i] = i;
            cpt[i] = (pt[i][0] + pt[i][1]) / 2.0;
        }
    }

    public PeakPickParameters limit(int iDim, double start, double last) {
        if (pt == null) {
            calcRange();
        }
        int iLast = theFile.ppmToPoint(iDim, start);
        int iStart = theFile.ppmToPoint(iDim, last);
        pt[iDim][0] = iStart;
        pt[iDim][1] = iLast;
        cpt[iDim] = (pt[iDim][0] + pt[iDim][1]) / 2.0;
        return this;
    }

    public PeakPickParameters limit(int iDim, int iStart, int iLast) {
        if (pt == null) {
            calcRange();
        }
        pt[iDim][0] = iStart;
        pt[iDim][1] = iLast;
        cpt[iDim] = (pt[iDim][0] + pt[iDim][1]) / 2.0;
        return this;
    }

    void fixLimits() {
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
            int nDims = 0;
            DimSizes[] dimSizes = new DimSizes[dataDim];
            for (int i = 0; i < dataDim; i++) {
                if (!theFile.getFreqDomain(i)) {
                    pt[i][0] = pt[i][1] = 0;
                }
                int dimSize = Math.abs(pt[i][1] - pt[i][0]) + 1;

                if ((dimSize > 1) || (region.equalsIgnoreCase("point"))) {
                    nDims++;
                }

                dimSizes[i] = new DimSizes(i, dimSize);
                if (dimSize > maxSize) {
                    maxSize = dimSize;
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

        }
    }

    private static class DimSizes implements Comparable {

        final int iDim;
        final int dimSize;

        DimSizes(final int iDim, final int dimSize) {
            this.iDim = iDim;
            this.dimSize = dimSize;
        }

        @Override
        public int compareTo(Object o2) {
            DimSizes d2 = (DimSizes) o2;
            return Integer.compare(d2.dimSize, dimSize);
        }
    }
}
