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

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BisectionSolver;
import org.apache.commons.math3.analysis.solvers.UnivariateSolverUtils;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.math.VecException;
import org.nmrfx.processor.operations.Asmooth;
import org.nmrfx.processor.optimization.NNLSMat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.nmrfx.processor.math.Vec.apache_ift;
import static org.nmrfx.processor.math.Vec.pascalrow;

/**
 * A compilation of static methods which do not explicitly use Vec objects but
 * are related to processing Vec objects and generally operate on arrays of
 * double or Complex instead of Vec objects directly.
 *
 * @author johnsonb
 */
@PluginAPI("parametric")
public class VecUtil {

    private static final Logger log = LoggerFactory.getLogger(VecUtil.class);

    /**
     * Do a non-negative least squares fit of AX=B to find X given A and B.
     * Results are returned in an AmplitudeFitResult object which provides
     * statistical information on quality of fit
     *
     * @param AR the A matrix
     * @param BR the B matrix
     * @return the result as an AmplitudeFitResult object
     */
    public static AmplitudeFitResult nnlsFit(final RealMatrix AR, final RealMatrix BR) {
        int nRows = AR.getRowDimension();
        int nCols = AR.getColumnDimension();
        int[] pivot = new int[nCols];

        for (int j = 0; j < nCols; j++) {
            pivot[j] = j;
        }

        RealMatrix aMat = new Array2DRowRealMatrix(AR.getData());
        RealMatrix bMat = new Array2DRowRealMatrix(BR.getData());
        NNLSMat nnlsMat = new NNLSMat(aMat, bMat);

        double[] Xp = nnlsMat.getX();
        double[] XR = new double[nCols];
        for (int i = 0; i < nCols; i++) {
            double amp = Xp[i];
            int iSig = i;
            XR[iSig] = amp;
        }
        ArrayRealVector rV = new ArrayRealVector(XR);
        ArrayRealVector Ax = (ArrayRealVector) AR.operate(rV);
        ArrayRealVector b = (ArrayRealVector) BR.getColumnVector(0);
        ArrayRealVector AxMinusB = Ax.subtract(b);
        ArrayRealVector delAbs = (ArrayRealVector) AxMinusB.mapToSelf(new org.apache.commons.math3.analysis.function.Abs());
        int maxIndex = delAbs.getMaxIndex();
        double maxValue = delAbs.getMaxValue();
        double norm = Ax.subtract(b).getNorm();
        double rss = norm * norm;
        int k = nCols * 3;
        double aic = nRows * Math.log(rss / nRows) + 2 * k + (2 * k * (k + 1) / (nRows - k - 1));
        AmplitudeFitResult afR = new AmplitudeFitResult(norm, rss, aic, XR, nCols, maxIndex, maxValue);
        return afR;
    }

    public static double[] fitLinear(double[] x, double[] y, double[] sigmaY) {

        double s = 0.0;
        double sumX = 0.0;
        double sumY = 0.0;
        double sumXX = 0.0;
        double sumXY = 0.0;

        for (int i = 0; i < x.length; i++) {
            double sigma2 = sigmaY == null ? 1.0 : sigmaY[i] * sigmaY[i];
            s += 1.0 / sigma2;
            sumX += x[i] / sigma2;
            sumY += y[i] / sigma2;
            sumXX += (x[i] * x[i]) / sigma2;
            sumXY += (x[i] * y[i]) / sigma2;
        }

        double delta = s * sumXX - sumX * sumX;

        double intercept = (sumXX * sumY - sumX * sumXY) / delta;
        double slope = (s * sumXY - sumX * sumY) / delta;

        double interceptErr = sumXX / delta;
        double slopeErr = s / delta;

        double[] result = {intercept, slope, interceptErr, slopeErr};
        return result;
    }

    static class AbsDevBFunc implements UnivariateFunction {

        double[] x = null;
        double[] y = null;
        int n = 0;
        double a = 0.0;
        double sumAbsDev = 0.0;

        AbsDevBFunc(double[] x, double[] y, int numPoints) {
            this.n = numPoints;
            this.x = x;
            this.y = y;
        }

        public double value(double b) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double delta = y[i] - (x[i] * b);
                sumAbsDev += Math.abs(delta);
                sum += x[i] * Math.signum(delta);
            }
            return sum;

        }

        public double getA() {
            return a;
        }

        public double getMeanDev() {
            return sumAbsDev / n;
        }
    }

    public static double[] fitAbsDev(double[] x, double[] y, double[] sigmaY) {
        double[] parameters = fitLinear(x, y, sigmaY);

        double b = parameters[1];
        double b1Dev = parameters[3];
        double b1 = b - 2.0 * b1Dev - Math.abs(b) * 0.1;
        double b2 = b + 2.0 * b1Dev + Math.abs(b) * 0.1;

        AbsDevBFunc abF = new AbsDevBFunc(x, y, x.length);
        double f1 = abF.value(b1);
        double f2 = abF.value(b2);
        double a = 0.0;
        try {
            if (f1 * f2 > 0) {
                double[] brackets = UnivariateSolverUtils.bracket(abF, b, -1.0e6, 1.0e6, 1000);
                b1 = brackets[0];
                b2 = brackets[1];
            }
            BisectionSolver bisect = new BisectionSolver();
            b = bisect.solve(100, abF, b1, b2);
            a = abF.getA();
        } catch (MathIllegalArgumentException fE) {
            log.warn("function evaluation failure {}", fE.getMessage(), fE);
        }
        parameters[0] = a;
        parameters[1] = b;
        return parameters;
    }

    /**
     * Analyze a vector of complex values to determine the frequencies and decay
     * rates
     *
     * @param x1        the complex values
     * @param winSize   The size of window that the values came from
     * @param nCoef     The number of coefficients to find
     * @param threshold the threshold for singular values
     * @return the frequency and decay rates as a vector of complex values
     * @throws VecException if an error occurs
     */
    public static Complex[] tlsFreq(final Complex[] x1, final int winSize,
                                    final int nCoef, final double threshold) throws VecException {

        int tlsStart = 1;
        if (tlsStart < 1) {
            tlsStart = 1;
        }
        int m = winSize - nCoef - tlsStart + 1;
        Complex[] coefB = LinearPrediction.getCoefsByTLS(x1, tlsStart, m, nCoef, threshold, true);
        Complex[] ocoefB = new Complex[coefB.length + 1];
        // negate,copy and reverse terms
        for (int iCoef = 0; iCoef < coefB.length; iCoef++) {
            ocoefB[iCoef] = new Complex(-coefB[coefB.length - 1 - iCoef].getReal(), -coefB[coefB.length - 1 - iCoef].getImaginary());
        }
        // add highest order term
        ocoefB[ocoefB.length - 1] = Complex.ONE;

        Polynomial polyB = new Polynomial(ocoefB.length);
        polyB.svejgardRoot(ocoefB.length - 1, ocoefB);
        conjugate(polyB.root);
        reflectRoots(polyB.root, false);
        Complex[] fd = new Complex[nCoef];
        for (int iCoef = 0; iCoef < coefB.length; iCoef++) {
            fd[iCoef] = new Complex(polyB.root[iCoef].getReal(), polyB.root[iCoef].getImaginary());
        }
        return fd;
    }

    /**
     * Use an abbreviated Hilbert transform to convert a spectrum with real
     * values to a time domain signal with complex values
     *
     * @param x The real spectrum
     * @param n The number of valid points in spectrum
     * @return the Complex time domain signal
     */
    public static Complex[] hift(final double[] x, final int n, double fpMul) {
        int factor = 0;
        int newSize = (int) Math.round(Math.pow(2, Math.ceil((Math.log(n) / Math.log(2)) + factor)));
        Complex[] xc = new Complex[newSize];
        for (int i = 0; i < n; i++) {
            xc[i] = new Complex(x[i] * 2.0, 0.0);
        }
        for (int i = n; i < newSize; i++) {
            xc[i] = new Complex(0.0, 0.0);
        }

        xc = apache_ift(xc);
        xc[0] = new Complex(xc[0].getReal() * fpMul, xc[0].getImaginary());
        int outSize = newSize / 2;
        for (int i = outSize; i < newSize; i++) {
            xc[i] = new Complex(0.0, 0.0);
        }
        Complex[] xcOut = new Complex[outSize];
        System.arraycopy(xc, 0, xcOut, 0, outSize);
        return xcOut;
    }

    /**
     * Use an abbreviated Hilbert transform to convert a spectrum with real
     * values to a time domain signal with complex values
     *
     * @param x The real spectrum as the first row of a 2D array. The time
     *          domain signal will replace the original values with real values in the
     *          first row and imaginary values in second.
     * @param n The number of valid points in spectrum
     */
    public static void hift(final double[][] x, final int n, double fpMul) {
        int factor = 0;
        // fixme we don't resize here, assumes x already correct size. Either remove some of 
        //       the following code, or actually do a resize
        int newSize = (int) Math.round(Math.pow(2, Math.ceil((Math.log(n) / Math.log(2)) + factor)));
        for (int i = 0; i < n; i++) {
            x[0][i] = x[0][i] * 2.0;
            x[1][i] = 0.0;
        }
        for (int i = n; i < newSize; i++) {
            x[0][i] = 0.0;
            x[1][i] = 0.0;
        }
        FastFourierTransformer.transformInPlace(x, DftNormalization.STANDARD, TransformType.INVERSE);
        x[0][0] = x[0][0] * fpMul;
        x[1][0] = x[1][0] * fpMul;
        int outSize = newSize / 2;
        for (int i = outSize; i < newSize; i++) {
            x[0][i] = 0.0;
            x[1][i] = 0.0;
        }
    }

    /**
     * Perform a Hilbert transform of data.
     *
     * @param x Spectrum as real array
     * @param n Number of valid points
     * @return Complex array
     */
    public static Complex[] hft(final double[] x, final int n) {
        int origSize = n;
        int factor = 0;
        int newSize = (int) Math.round(Math.pow(2, Math.ceil((Math.log(n) / Math.log(2)) + factor)));
        Complex[] xc = new Complex[newSize];
        for (int i = 0; i < n; i++) {
            xc[i] = new Complex(x[i] * 2.0, 0.0);
        }
        for (int i = n; i < newSize; i++) {
            xc[i] = new Complex(0.0, 0.0);
        }

        xc = apache_ift(xc);
        xc[0] = new Complex(xc[0].getReal() / 2, xc[0].getImaginary());
        int outSize = newSize / 2;
        for (int i = outSize; i < newSize; i++) {
            xc[i] = new Complex(0.0, 0.0);
        }
        xc = Vec.apache_fft(xc);
        Complex[] xcOut = new Complex[origSize];
        System.arraycopy(xc, 0, xcOut, 0, origSize);
        return xcOut;
    }

    /**
     * Perform a Hilbert transform of data.
     *
     * @param x The real spectrum as the first row of a 2D array. The time
     *          domain signal will replace the original values with real values in the
     *          first row and imaginary values in second.
     * @param n The number of valid points in spectrum
     */
    public static void hft(final double[][] x, final int n) {
        int origSize = n;
        int factor = 0;
        int newSize = (int) Math.round(Math.pow(2, Math.ceil((Math.log(n) / Math.log(2)) + factor)));
        Complex[] xc = new Complex[newSize];
        for (int i = 0; i < n; i++) {
            xc[i] = new Complex(x[0][i] * 2.0, 0.0);
        }
        for (int i = n; i < newSize; i++) {
            xc[i] = new Complex(0.0, 0.0);
        }

        xc = apache_ift(xc);
        xc[0] = new Complex(xc[0].getReal() / 2, xc[0].getImaginary());
        int outSize = newSize / 2;
        for (int i = outSize; i < newSize; i++) {
            xc[i] = new Complex(0.0, 0.0);
        }
        xc = Vec.apache_fft(xc);
        for (int i = 0; i < origSize; i++) {
            x[0][i] = xc[i].getReal();
            x[1][i] = xc[i].getImaginary();
        }
    }

    /**
     * Add two complex arrays and put result in third complex array.
     *
     * @param a    First array
     * @param size Valid size
     * @param b    Second array
     * @param c    Array for result of @code(a + b)
     */
    public static void addVector(Complex[] a, int size, Complex[] b,
                                 Complex[] c) {
        for (int i = 0; i < size; ++i) {
            c[i] = a[i].add(b[i]);
        }
    }

    static void psmooth(double[] y, int m, double lambda) /* Smoothing and interpolation differences of order <=5.
     Input:  weights (w), data (y): vector from 0 to m-1.
     Input:  smoothing parameter (lambda), length (m).
     Input:  order of differences (n).
     Output: smoothed vector (z): vector from 1 to m. */ {
        double[] w = new double[m + 1];
        double[] z = new double[m + 1];
        for (int i = 1; i <= m; i++) {
            w[i] = 1.0;
        }
        int n = 1;
        double[] a = new double[n + 1];
        pascalrow(a, 1);
        (new Asmooth(w, y, z, a, lambda, m, 1)).eval(new Vec(1));
//        asmooth(w, y, z, a, lambda, m, 1);
        for (int i = 1; i <= m; i++) {
            y[i] = z[i];
        }
    }

    /**
     * Calculate standard deviation of real array of values
     *
     * @param rvec     real array of values
     * @param m        Valid size
     * @param winSize  Size of window
     * @param nWindows Number of windows to use
     * @return standard deviation
     */
    public static double sdev(double[] rvec, int m, int winSize, int nWindows) {
        int nRegions = (m) / winSize;

        double[] regionVec = new double[nRegions];
        double[] sumSqVec = new double[nRegions];

        /* Calculate means of each window */
        double max = Double.NEGATIVE_INFINITY;

        int k = 0;
        for (int j = 0; j < nRegions; j++) {
            double reSum = 0.0;

            for (int i = 0; i < winSize; i++) {
                if (rvec[k] > max) {
                    max = rvec[k];
                }

                reSum += rvec[k];
                k++;
            }

            regionVec[j] = reSum / winSize;
        }

        /* Form centered vector and calculate st. dev. for window */
        k = 0;

        for (int j = 0; j < nRegions; j++) {
            double sumsq = 0.0;

            for (int i = 0; i < winSize; i++) {
                double dev = rvec[k] - regionVec[j];
                sumsq += (dev * dev);
                k++;
            }

            sumSqVec[j] = sumsq;
        }

        /* Estimate standard deviation from sorted vector */
        Arrays.sort(sumSqVec);
        double threshold = max * 1.0e-8;
        double sd = 0.0;
        int n = 0;

        for (int i = 0; i < nRegions; i++) {
            if (sumSqVec[i] > threshold) {
                sd += sumSqVec[i];
                n++;
            }

            if (n >= nWindows) {
                break;
            }
        }

        double sdev = Math.sqrt(sd / (nWindows * winSize));

        return sdev;
    }

    /**
     * Calculate complex conjugate of an array of complex values
     *
     * @param roots array of complex values
     */
    public static void conjugate(Complex[] roots) {
        for (int i = 0; i < roots.length; i++) {
            roots[i] = roots[i].conjugate();
        }
    }

    /**
     * Negate all the values of the provided Complex array. The sign of both
     * real and imaginary components are changed.
     *
     * @param roots the Complex array
     */
    public static void negate(Complex[] roots) {
        for (int i = 0; i < roots.length; i++) {
            roots[i] = roots[i].negate();
        }
    }

    /**
     * Reflect roots inside or outside the unit circle
     *
     * @param roots     an array of Complex roots
     * @param toOutside true if roots should be reflected outside circle
     */
    public static void reflectRoots(Complex[] roots, boolean toOutside) {
        for (int i = 0; i < roots.length; i++) {
            // reflect roots outside unit circle
            double dReal = roots[i].getReal();
            double dImaginary = roots[i].getImaginary();
            double dSqr = dReal * dReal + dImaginary * dImaginary;
            if (((toOutside) && (dSqr < 1.0)) || (!toOutside && (dSqr > 1.0))) {
                roots[i] = new Complex(dReal / dSqr, dImaginary / dSqr);
            }
        }
    }

    /**
     * Reverse an array of Complex values
     *
     * @param c the Complex array
     */
    public static void reverse(Complex[] c) {
        int n = c.length;
        for (int i = 0; i < c.length / 2; i++) {
            Complex hold = c[i];
            c[i] = c[n - i - 1];
            c[n - i - 1] = hold;
        }
    }

}
