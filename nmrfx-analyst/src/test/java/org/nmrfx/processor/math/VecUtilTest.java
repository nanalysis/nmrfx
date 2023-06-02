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

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.junit.Assert;
import org.junit.Test;

public class VecUtilTest {

    @Test
    public void testVector() {
        double[][] riVec = getRIVec();
        double[][] keep = copy(riVec);
        int n = riVec[0].length;

        FastFourierTransformer.transformInPlace(riVec, DftNormalization.STANDARD, TransformType.FORWARD);
        for (int i = 0; i < n; i++) {
            riVec[1][i] = 0.0;
        }
        VecUtil.hift(riVec, n, 0.5);
        double tol = 1.0e-10;
        for (int i = 0; i < n; i++) {
            Assert.assertEquals(keep[0][i], riVec[0][i], tol);
            Assert.assertEquals(keep[1][i], riVec[1][i], tol);
        }

    }

    double[][] copy(double[][] riVec) {
        int n = riVec[0].length;
        double[][] keep = new double[2][n];
        for (int i = 0; i < n; i++) {
            keep[0][i] = riVec[0][i];
            keep[1][i] = riVec[1][i];
        }
        return keep;
    }

    double[][] getRIVec() {
        int n = 4;
        double[][] riVec = new double[2][n];
        riVec[0][0] = 1.0;
        riVec[1][0] = 0.0;
        riVec[0][1] = riVec[0][0] * Math.cos(0.1) / 2;
        riVec[1][1] = riVec[0][0] * Math.sin(0.1) / 2;
        return riVec;
    }

    double[][] getRIVecFreq() {
        double[][] riVec = getRIVec();
        FastFourierTransformer.transformInPlace(riVec, DftNormalization.STANDARD, TransformType.FORWARD);
        return riVec;
    }

    double[] getRVecFreq() {
        double[][] riVec = getRIVecFreq();
        return riVec[0];
    }

    @Test
    public void testHFT1() {
        double[][] riVec = getRIVecFreq();
        int n = riVec[0].length;
        double[][] keep = copy(riVec);
        Complex[] result = VecUtil.hft(riVec[0], n);
        double tol = 1.0e-10;
        for (int i = 0; i < result.length; i++) {
            Assert.assertEquals(keep[0][i], result[i].getReal(), tol);
            Assert.assertEquals(keep[1][i], result[i].getImaginary(), tol);
        }
    }

    @Test
    public void testHFT2() {
        double[][] riVec = getRIVecFreq();
        int n = riVec[0].length;
        double[][] keep = new double[2][n];
        for (int i = 0; i < n; i++) {
            keep[0][i] = riVec[0][i];
            keep[1][i] = riVec[1][i];
        }
        for (int i = 0; i < n; i++) {
            riVec[1][i] = 0.0;
        }
        VecUtil.hft(riVec, n);
        double tol = 1.0e-10;
        for (int i = 0; i < n; i++) {
            Assert.assertEquals(keep[0][i], riVec[0][i], tol);
            Assert.assertEquals(keep[1][i], riVec[1][i], tol);
        }
    }

}
