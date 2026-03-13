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
package org.nmrfx.processor.datasets;

import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.RegionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class Measure {

    String name = "";
    final int iDim;
    public final double ppm1;
    public final double ppm2;

    final double wppm1;
    final double wppm2;
    final int extra;
    final OffsetTypes offsetType;
    final MeasureTypes measureType;

    public enum MeasureTypes {
        V("V", "Volume"), M("M", "Maximum"), m("m", "Minimum"),
        E("E", "Extreme"), R("R", "RMS");

        final String name;
        final String symbol;

        MeasureTypes(String symbol, String name) {
            this.symbol = symbol;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public String getSymbol() {
            return symbol;
        }

    }

    public enum OffsetTypes {
        N("None"), R("Region"), W("Window");

        final String name;

        OffsetTypes(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public String getSymbol() {
            return name.substring(0, 1);
        }

    }

    public Measure(String name, int iDim, double ppm1, double ppm2) {
        this.name = name;
        this.iDim = iDim;
        this.ppm1 = ppm1;
        this.ppm2 = ppm2;
        this.wppm1 = ppm1;
        this.wppm2 = ppm2;
        this.extra = 0;
        this.offsetType = OffsetTypes.N;
        this.measureType = MeasureTypes.V;
    }

    public Measure(String name, int iDim, double ppm1, double ppm2, OffsetTypes oType, MeasureTypes mType) {
        this.name = name;
        this.iDim = iDim;
        this.ppm1 = ppm1;
        this.ppm2 = ppm2;
        this.wppm1 = ppm1;
        this.wppm2 = ppm2;
        this.extra = 0;
        this.offsetType = oType;
        this.measureType = mType;
    }

    public Measure(String name, int iDim, double ppm1, double ppm2, double wppm1, double wppm2, int extra, OffsetTypes oType, MeasureTypes mType) {
        this.name = name;
        this.iDim = iDim;
        this.ppm1 = ppm1;
        this.ppm2 = ppm2;
        this.wppm1 = wppm1;
        this.wppm2 = wppm2;
        this.extra = extra;
        this.offsetType = oType;
        this.measureType = mType;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Double> measure(DatasetBase dataset) throws IOException {
        int alongDim = iDim == 0 ? 1 : 0;
        int nDim = dataset.getNDim();
        int size = nDim == 1 ? 1 : dataset.getSizeTotal(alongDim);
        int[] dim = new int[nDim];
        int[][] pt = new int[nDim][2];
        int[] edge1 = new int[nDim];
        int[] edge2 = new int[nDim];
        int[] cpt = new int[nDim];
        int pt1 = dataset.ppmToPoint(iDim, ppm1);
        int pt2 = dataset.ppmToPoint(iDim, ppm2);
        int wpt1 = dataset.ppmToPoint(iDim, wppm1);
        int wpt2 = dataset.ppmToPoint(iDim, wppm2);

        double[] width = new double[nDim];
        if (pt1 > pt2) {
            int hold = pt1;
            pt1 = pt2;
            pt2 = hold;
        }
        if (wpt1 > wpt2) {
            int hold = wpt1;
            wpt1 = wpt2;
            wpt2 = hold;
        }
        pt[0][0] = pt1;
        pt[0][1] = pt2;
        // fixme  need to switch dim for iDim != 0
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (offsetType == OffsetTypes.W) {
                edge1[0] = wpt1 - extra;
                edge2[0] = wpt2 - extra;
            } else {
                edge1[0] = pt1 - extra;
                edge2[0] = pt2 - extra;

            }
            if (nDim > 1) {
                pt[1][0] = i;
                pt[1][1] = i;
            }
            for (int j = 0; j < nDim; j++) {
                cpt[j] = (pt[j][0] + pt[j][1]) / 2;
                width[j] = (double) Math.abs(pt[j][0] - pt[j][1]);
                if (j > 0) {
                    edge1[j] = i;
                }
                dim[j] = j;
            }
            double v1 = 0.0;
            double v2 = 0.0;
            for (int iE = -extra; iE <= extra; iE++) {
                v1 += dataset.readPoint(edge1, dim);
                v2 += dataset.readPoint(edge2, dim);
                edge1[0]++;
                edge2[0]++;
            }
            v1 /= 1 + 2 * extra;
            v2 /= 1 + 2 * extra;
            double dv = v2 - v1;
            v1 = dv * (1.0 * pt1 - edge1[0]) / (edge2[0] - edge1[0]) + v1;
            v2 = dv * (1.0 * pt2 - edge1[0]) / (edge2[0] - edge1[0]) + v1;
            double iCorr = 0.0;
            double vCorr = 0.0;
            if (offsetType != OffsetTypes.N) {
                iCorr = (v1 + v2) / 2.0;
                vCorr = Math.abs(pt[0][0] - pt[0][1]) * iCorr;
            }
            RegionData region = dataset.analyzeRegion(pt, cpt, width, dim);
            double value;
            switch (measureType) {
                case V: {
                    value = region.getVolume_r();
                    value -= vCorr;
                    break;
                }
                case M: {
                    value = region.getMax();
                    value -= iCorr;
                    break;
                }
                case m: {
                    value = region.getMin();
                    value -= iCorr;
                    break;
                }
                case E: {
                    value = region.getExtreme();
                    value -= iCorr;
                    break;
                }
                case R: {
                    value = region.getRMS();
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Invalid mode " + measureType.toString());
                }
            }
            values.add(value);
        }
        return values;
    }

    public List<double[]> measureBins(DatasetBase dataset, int nBins) throws IOException {
        int alongDim = iDim == 0 ? 1 : 0;
        int nDim = dataset.getNDim();
        int rows = nDim == 1 ? 1 : dataset.getSizeTotal(alongDim);
        int rowSize = dataset.getSizeTotal(iDim);
        int[] pt = new int[nDim];
        int pt1 = dataset.ppmToPoint(iDim, ppm1);
        int pt2 = dataset.ppmToPoint(iDim, ppm2);

        if (pt1 > pt2) {
            int hold = pt1;
            pt1 = pt2;
            pt2 = hold;
        }
        int regionSize = pt2 - pt1 + 1;
        int binSize = regionSize / nBins;
        if (binSize * nBins < regionSize) {
            binSize++;
        }
        // fixme  need to switch dim for iDim != 0
        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            if (nDim > 1) {
                pt[1] = i;
            }
            double[] binnedValues = new double[nBins];
            for (int iBin = 0; iBin < nBins; iBin++) {
                double sum = 0.0;
                for (int j = 0; j < binSize; j++) {
                    int index = pt1 + iBin * binSize + j;
                    if (index >= rowSize) {
                        break;
                    }
                    pt[0] = index;
                    sum += dataset.readPoint(pt);
                }
                binnedValues[iBin] = sum;
            }
            result.add(binnedValues);
        }
        return result;
    }

    public String getColumnDescriptor() {
        String measureName = measureType.getSymbol();
        String columnDescriptor;
        if (null == offsetType) {
            columnDescriptor = String.format("_%s_%.4f_%.4f%s", measureName, ppm1, ppm2, "_no_");
        } else {
            switch (offsetType) {
                case W:
                    columnDescriptor = String.format("%.4f_%.4f_%.4f_%.4f_%sW", ppm1, ppm2, wppm1, wppm2, measureName);
                    break;
                case R:
                    columnDescriptor = String.format("%.4f_%.4f_%sR", ppm1, ppm2, measureName, "R");
                    break;
                default:
                    columnDescriptor = String.format("%.4f_%.4f_%sN", ppm1, ppm2, measureName);
                    break;
            }
        }
        return columnDescriptor;
    }

    public String getFileString() {
        return name + " " + getColumnDescriptor().replace('_', ' ').trim();
    }

}
