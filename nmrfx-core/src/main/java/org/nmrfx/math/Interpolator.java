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
package org.nmrfx.math;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author brucejohnson
 */
public class Interpolator {

    private static final Logger log = LoggerFactory.getLogger(Interpolator.class);

    public static double[] getInterpolated(final double[] values, final int newSize) {
        SplineInterpolator sInterp = new SplineInterpolator();

        double[] interpIntensities = new double[newSize];
        int n = values.length;
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = i;
            y[i] = values[i];
        }

        PolynomialSplineFunction pSF = sInterp.interpolate(x, y);
        try {
            for (int i = 0; i < newSize; i++) {
                double xValue = 1.0 * i * (n - 1) / (newSize - 1);
                if (xValue < 0.0) {
                    xValue = 0.0;
                } else if (xValue > (n - 1)) {
                    xValue = n - 1;
                }
                interpIntensities[i] = pSF.value(xValue);
            }
        } catch (OutOfRangeException adE) {
            log.warn(adE.getMessage(), adE);
        }
        return interpIntensities;
    }

    public static double[] getInterpolated(final double[] values, final double start, final double end) {
        SplineInterpolator sInterp = new SplineInterpolator();

        int n = values.length;
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = i;
            y[i] = values[i];
        }
        int iStart = (int) Math.ceil(start);
        int iEnd = (int) Math.floor(end);
        int newSize = iEnd - iStart + 1;
        PolynomialSplineFunction pSF = sInterp.interpolate(x, y);

        double deltaOrig = n - 1;
        double newWidth = end - start;
        double incr = deltaOrig / newWidth;
        double[] interpIntensities = new double[newSize];

        try {
            double xValue = incr * (iStart - start);
            for (int i = 0; i < newSize; i++) {
                if (xValue < 0.0) {
                    xValue = 0.0;
                } else if (xValue > (n - 1)) {
                    xValue = n - 1;
                }
                interpIntensities[i] = pSF.value(xValue);
                xValue += incr;
            }
        } catch (OutOfRangeException adE) {
            log.warn(adE.getMessage(), adE);
        }
        return interpIntensities;
    }
}
