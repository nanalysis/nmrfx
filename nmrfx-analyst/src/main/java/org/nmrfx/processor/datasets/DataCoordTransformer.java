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

public class DataCoordTransformer {

    final double[] size;
    final double[][] ppm;
    final double[] sw;

    public DataCoordTransformer(int[] dim, Dataset dataset) {
        super();
        int nDim = dataset.getNDim();
        this.size = new double[nDim];
        this.sw = new double[nDim];
        this.ppm = new double[nDim][2];
        for (int i = 0; i < nDim; i++) {
            this.size[i] = dataset.getSizeTotal(dim[i]);
            this.sw[i] = dataset.getSw(dim[i]);
            this.ppm[i][0] = dataset.pointToPPM(dim[i], 0);
            this.ppm[i][1] = dataset.pointToPPM(dim[i], size[i] - 1);
        }
    }

    double getFraction(int iDim, double value) {
        double fraction = (value - ppm[iDim][0]) / (ppm[iDim][1] - ppm[iDim][0]);
        return fraction;
    }

    public double transformToHz(int iDim, double value) {
        double fraction = getFraction(iDim, value);
        double hzValue = fraction * sw[iDim];
        return hzValue;
    }

    public double transformToTime(int iDim, double value) {
        double fraction = getFraction(iDim, value);
        double timeValue = fraction * (1.0 / sw[iDim]) * size[iDim];
        return timeValue;
    }

    public double transformToPt(int iDim, double value) {
        double fraction = getFraction(iDim, value);
        double ptValue = fraction * size[iDim];
        return ptValue;
    }
}
