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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Bruce Johnson
 */
public class Measure {

    final List<Dataset> datasets;

    public enum MeasureTypes {
        VOLUME, MAX, MIN, EXTREME;
    }

    public enum OffsetTypes {
        NONE, REGION, WINDOW;
    }

    public Measure(Dataset dataset) {
        this.datasets = new ArrayList<>();
        datasets.add(dataset);
    }

    public Measure(List<Dataset> datasets) {
        this.datasets = new ArrayList<>();
        this.datasets.addAll(datasets);
    }

    public List<Double> measure(int iDim, double ppm1, double ppm2) throws IOException {
        return measure(iDim, ppm1, ppm2, ppm1, ppm2, 0, OffsetTypes.REGION, MeasureTypes.VOLUME);
    }

    public List<Double> measure(int iDim, double ppm1, double ppm2, double wppm1, double wppm2, int extra, OffsetTypes offsetType, MeasureTypes mode) throws IOException {
        Dataset dataset = datasets.get(0);
        int pt1 = dataset.ppmToPoint(iDim, ppm1);
        int pt2 = dataset.ppmToPoint(iDim, ppm2);
        int wpt1 = dataset.ppmToPoint(iDim, wppm1);
        int wpt2 = dataset.ppmToPoint(iDim, wppm2);
        return measure(iDim, pt1, pt2, wpt1, wpt2, extra, offsetType, mode);
    }

    public List<Double> measure(int iDim, int pt1, int pt2, int wpt1, int wpt2, int extra, OffsetTypes offsetType, MeasureTypes mode) throws IOException {
        Dataset dataset = datasets.get(0);
        int alongDim = iDim == 0 ? 1 : 0;
        int size = dataset.getSize(alongDim);
        int nDim = dataset.getNDim();
        int[] dim = new int[nDim];
        int[][] pt = new int[nDim][2];
        int[] edge1 = new int[nDim];
        int[] edge2 = new int[nDim];
        int[] cpt = new int[nDim];
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
        if (offsetType == OffsetTypes.WINDOW) {
            edge1[0] = wpt1 - extra;
            edge2[0] = wpt2 - extra;
        } else {
            edge1[0] = pt1 - extra;
            edge2[0] = pt2 - extra;

        }
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            pt[1][0] = i;
            pt[1][1] = i;
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
            if (offsetType != OffsetTypes.NONE) {
                iCorr = (v1 + v2) / 2.0;
                vCorr = Math.abs(pt[0][0] - pt[0][1]) * iCorr;
            }
            RegionData region = dataset.analyzeRegion(pt, cpt, width, dim);
            double value;
            switch (mode) {
                case VOLUME: {
                    value = region.getVolume_r();
                    value -= vCorr;
                    break;
                }
                case MAX: {
                    value = region.getMax();
                    value -= iCorr;
                    break;
                }
                case MIN: {
                    value = region.getMin();
                    value -= iCorr;
                    break;
                }
                case EXTREME: {
                    value = region.getExtreme();
                    value -= iCorr;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Invalid mode " + mode);
                }
            }
            values.add(value);
        }
        return values;
    }

}
