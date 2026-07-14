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
package org.nmrfx.processor.math;

import org.apache.commons.math3.util.FastMath;

import java.util.Comparator;

/**
 * @author Bruce Johnson
 */
public class MatrixPeak implements Comparator<MatrixPeak> {

    double[][] intensities;
    int[][] offsets;
    int[][] pts;
    double[] centers;
    double[] widths;
    double height;
    int[] widthLim;

    MatrixPeak(double[][] intensities, int[][] offsets, int[][] pts, double scale, int[] widthLim, int[] halfWidths) {
        int nDim = intensities.length;
        this.intensities = new double[nDim][3];
        this.offsets = new int[nDim][3];
        this.centers = new double[nDim];
        this.widths = new double[nDim];
        this.pts = new int[nDim][3];
        this.widthLim = widthLim;
        for (int i = 0; i < nDim; i++) {
            System.arraycopy(intensities[i], 0, this.intensities[i], 0, 3);
            System.arraycopy(offsets[i], 0, this.offsets[i], 0, 3);
            System.arraycopy(pts[i], 0, this.pts[i], 0, 3);
            widths[i] = 2.0 * halfWidths[i];
        }
        estimatePeakValues(scale);
    }

    @Override
    public int compare(MatrixPeak o1, MatrixPeak o2) {
        if (o1 == o2) {
            return 0;
        } else {
            return Double.compare(o1.height, o2.height);
        }

    }

    final void estimatePeakValues(double scale) {
        int nDim = intensities.length;
        double sumHeights = 0.0;
        for (int i = 0; i < nDim; i++) {
            double v0 = intensities[i][0];
            double v2 = intensities[i][2];
            double v1 = intensities[i][1];
            // parabolic fit to find interpolated position
            double xOff1 = ((v0 - v2) / (2.0 * ((2.0 * v1) - v0 - v2)));
            // y = ax^2 + bx +c   
            // v1 = c    x is 0
            double c = v1;
            // v0 = a-b+c   x is -1
            // v2 = a+b+c x is 1
            // v2+v0 = 2a+2c
            // v2+v0 = 2a+2v1
            // 2a = v2+v0-2v1
            double a = (v2 + v0 - 2.0 * v1) / 2.0;
            // v2-v0 = 2b
            double b = (v2 - v0) / 2.0;
            // vertex = (-b/2a,-D/4a) D = b2-4ac
            double xOff = -b / (2.0 * a);
            double yOff = -(b * b - 4.0 * a * c) / (4.0 * a);
            double f = 0.1;
            double polyHalfWidth = FastMath.sqrt(FastMath.abs(f * c / a));
            widths[i] = polyHalfWidth / 3.74 * 20.0 * scale;  // approximate conversion from parabola half width at 90% height to
                                                          // Generalized lorenztian full width at half height
            if (widths[i] < 0.5) {
                widths[i] = 0.5;
                xOff = 0.0;
            } else if (widths[i] > widthLim[i]) {
                widths[i] = widthLim[i];
            }
            centers[i] = pts[i][1] + xOff;

            if (i > 0) {
                sumHeights += yOff;
            }

        }
        // take average from each dimension
        height = sumHeights / (nDim - 1);
    }

    boolean overlap(MatrixPeak peak) {
        // fixme, what about edges
        boolean overlaps = true;
        // fixme start at 1 because first dim is direct dim.  Need to reconsider how we do this
        for (int i = 1; i < centers.length; i++) {
            if (FastMath.abs(centers[i] - peak.centers[i]) > 3) {
                overlaps = false;
                break;
            }
        }
        return overlaps;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0; i < intensities.length; i++) {
            sBuilder.append(String.format(" dim %d: %5d %5d %7.2f %7.2f %7.2f %7.2f %7.2f", i,
                    offsets[i][1], pts[i][1], centers[i], widths[i],
                    intensities[i][0], intensities[i][1], intensities[i][2]));
        }
        sBuilder.append(" ").append(String.format("%7.2f", height));
        return sBuilder.toString();
    }

}
