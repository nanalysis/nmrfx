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
package org.nmrfx.processor.math.apache;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexField;
import org.apache.commons.math3.complex.ComplexUtils;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.ArrayFieldVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * ComplexSingularValueDecomposition implements the singular value decomposition of a Complex Matrix.
 */
public class ComplexSingularValueDecomposition {
    private static final Logger log = LoggerFactory.getLogger(ComplexSingularValueDecomposition.class);

    /**
     * Limits the number of iterations in the SVD algorithm
     */
    private static final int MAXITER = 30;
    /**
     * The matrix of left singular vectors
     */
    public Array2DRowFieldMatrix<Complex> U;
    /**
     * The matrix of right singular vectors
     */
    public Array2DRowFieldMatrix<Complex> V;
    /**
     * The diagonal matrix of singular values
     */
    public FieldDiagonalMatrix<Complex> S;

    /**
     * Computes the SVD of a Complex Matrix XX.
     *
     * @param matrix The matrix to perform the SVD on.
     * @throws Exception
     */
    public ComplexSingularValueDecomposition(
            Array2DRowFieldMatrix<Complex> matrix) throws Exception {
        int i, il, iu, iter, j, k, m, mc;

        double as, at, au, axkk, axkk1, dmax, dmin, ds, ea, es, shift, ss, t;

        Complex xkk, xkk1, xk1k1, tempComplex;

        Rot P = new Rot();

        /* Initialization */
        Complex scale;

        Array2DRowFieldMatrix<Complex> X = matrix;
        Complex[][] xdata = X.getDataRef();
        int xrow = X.getRowDimension();
        int xcol = X.getColumnDimension();

        ArrayFieldVector<Complex> h;
        Complex[] hdata;

        ArrayFieldVector<Complex> temp = new ArrayFieldVector<>(
                FastMath.max(xrow, xcol), Complex.ZERO);

        mc = FastMath.min(xrow, xcol);

        double d[] = new double[mc];
        double e[] = new double[mc];

        S = new FieldDiagonalMatrix<>(ComplexField.getInstance(), mc);
        int srow = S.getRowDimension();
        int scol = S.getColumnDimension();

        U = (Array2DRowFieldMatrix<Complex>) MatrixUtils
                .createFieldIdentityMatrix(ComplexField.getInstance(), xrow);
        Complex[][] udata = U.getDataRef();
        int urow = U.getRowDimension();
        int ucol = U.getColumnDimension();
        V = (Array2DRowFieldMatrix<Complex>) MatrixUtils
                .createFieldIdentityMatrix(ComplexField.getInstance(), mc == xrow ? xcol : xrow);
        Complex[][] vdata = V.getDataRef();
        int vrow = V.getRowDimension();
        int vcol = V.getColumnDimension();

        m = FastMath.min(xrow, xcol);

        /*
         * Reduction to Bidiagonal form.
         */
        for (k = 0; k < m; k++) {
            h = genc(xdata, k, xrow - 1, k);

            hdata = h.getDataRef();
            Complex[] tempdata = temp.getDataRef();

            premultiplyA(hdata, xdata, k, xrow - 1, k + 1, xcol - 1, tempdata);
            transformAwithU(udata, hdata, 0, urow - 1, k, ucol - 1, tempdata);

            if ((k + 1) != xcol) {
                h = genr(xdata, k, k + 1, xcol - 1);
                hdata = h.getDataRef();

                transformAwithU(xdata, hdata, k + 1, xrow - 1, k + 1, xcol - 1, tempdata);
                transformAwithU(vdata, hdata, 0, vrow - 1, k + 1, vcol - 1, tempdata);
            }
        } //matrices equal up to here

        /*
         * Scale the bidiagonal matrix so that its elements are real.
         */
        for (k = 0; k < m; k++) {
            xkk = xdata[k][k];

            axkk = xkk.abs();

            xdata[k][k] = new Complex(axkk);
            d[k] = axkk;
            scale = xkk.conjugate().divide(axkk);

            if (k < xcol - 1) {
                xkk1 = xdata[k][k + 1];
                xdata[k][k + 1] = xkk1.multiply(scale);
            }
            scale = scale.conjugate();
            for (i = 0; i < urow; i++) {
                udata[i][k] = udata[i][k].multiply(scale);
            }

            if (k < xcol - 1) {
                xkk1 = xdata[k][k + 1];
                axkk1 = xkk1.abs();
                xdata[k][k + 1] = new Complex(axkk1);
                e[k] = axkk1;
                scale = xkk1.conjugate().divide(axkk1);
                if (k < xrow) {
                    xk1k1 = xdata[k][k + 1];
                    xdata[k][k + 1] = xk1k1.multiply(scale);
                }
                for (i = 0; i < vrow; i++) {
                    vdata[i][k + 1] = vdata[i][k + 1].multiply(scale);
                }
            }
        }

        --m; // 0 based indexing from m
        /*
         * If X has more columns than rows, rotate out the extra superdiagonal
         * element.
         */
        if (xrow < xcol) {
            t = e[m];
            for (k = m; k >= 0; k--) {
                Rot.genr(d[k], t, P);
                d[k] = P.z.getReal();
                if (k != 0) {
                    t = P.s.getReal() * e[k - 1];
                    e[k - 1] = P.c * e[k - 1];
                }
                Rot.ap(vdata, P, 0, vrow - 1, k, xrow);
                Rot.ap(xdata, P, 0, xrow - 1, k, xrow);
            }
        }

        /*
         * Caculate the singular values of the bidiagonal matrix.
         */
        iu = m;
        iter = 0;
        while (true) {
            /*
             * These two loops determine the rows (il to iu) to iterate on.
             */
            while (iu > 0) {
                if (FastMath.abs(e[iu - 1]) > 1.0e-16 * (FastMath.abs(d[iu]) + FastMath
                        .abs(d[iu - 1]))) {
                    break;
                }
                e[iu - 1] = 0.;
                iter = 0;
                iu = iu - 1;
            }
            iter++;// = iter + 1;
            if (iter > MAXITER) {
                throw new Exception("Maximum number of iterations exceeded.");
            }
            if (iu == 0) {
                break;
            }

            il = iu - 1;
            while (il > 0) {
                if (FastMath.abs(e[il - 1]) <= 1.0e-16 * (FastMath.abs(d[il]) + FastMath
                        .abs(d[il - 1]))) {
                    break;
                }
                il = il - 1;
            }
            if (il != 0) {
                e[il - 1] = 0.;
            }
            /*
             * Compute the shift (formulas adapted from LAPACK).
             */
            dmax = FastMath.max(FastMath.abs(d[iu]), FastMath.abs(d[iu - 1]));
            dmin = FastMath.min(FastMath.abs(d[iu]), FastMath.abs(d[iu - 1]));
            ea = FastMath.abs(e[iu - 1]);
            if (dmin == 0.) {
                shift = 0.;
            } else if (ea < dmax) {
                as = 1. + dmin / dmax;
                at = (dmax - dmin) / dmax;
                au = ea / dmax;
                au = au * au;
                shift = dmin
                        * (2. / (FastMath.sqrt(as * as + au) + FastMath.sqrt(at * at
                        + au)));
            } else {
                au = dmax / ea;
                if (au == 0.) {
                    shift = (dmin * dmax) / ea;
                } else {
                    as = 1. + dmin / dmax;
                    at = (dmax - dmin) / dmax;
                    t = 1. / (FastMath.sqrt(1. + (as * au) * (as * au)) + FastMath
                            .sqrt(1. + (at * au) * (at * au)));
                    shift = (t * dmin) * au;
                }
            }
            /*
             * Perform the implicitly shifted QR step.
             */
            t = FastMath.max(FastMath.max(FastMath.abs(d[il]), FastMath.abs(e[il])), shift);
            ds = d[il] / t;
            es = e[il] / t;
            ss = shift / t;
            Rot.genr((ds - ss) * (ds + ss), ds * es, P);

            for (i = il; i < iu; i++) {
                t = P.c * d[i] - P.s.getReal() * e[i];
                e[i] = P.s.getReal() * d[i] + P.c * e[i];
                d[i] = t;
                t = -P.s.getReal() * d[i + 1];
                d[i + 1] = P.c * d[i + 1];
                Rot.ap(vdata, P, 0, vrow - 1, i, i + 1);
                Rot.genc(d[i], t, P);
                d[i] = P.z.getReal();
                t = P.c * e[i] + P.s.getReal() * d[i + 1];
                d[i + 1] = P.c * d[i + 1] - P.s.getReal() * e[i];
                e[i] = t;
                Rot.aph(udata, P, 0, urow - 1, i, 1 + i);

                if (i != iu - 1) {
                    t = P.s.getReal() * e[i + 1];
                    e[i + 1] = P.c * e[i + 1];
                    Rot.genr(e[i], t, P);
                    e[i] = P.z.getReal();
                }
            }
        }

        /*
         * Sort the singular values, setting negative values of d to positive.
         */
        for (k = m; k >= 0; k--) {
            if (d[k] < 0) {
                d[k] = -d[k];
                for (i = 0; i < xcol; i++) {
                    vdata[i][k] = vdata[i][k].negate();
                }
            }
            for (j = k; j < m; j++) {
                if (d[j] < d[j + 1]) {
                    t = d[j];
                    d[j] = d[j + 1];
                    d[j + 1] = t;
                    for (i = 0; i < xrow; i++) {
                        tempComplex = udata[i][j];
                        udata[i][j] = udata[i][j + 1];
                        udata[i][j + 1] = tempComplex;
                    }
                    for (i = 0; i < xcol; i++) {
                        tempComplex = vdata[i][j];
                        vdata[i][j] = vdata[i][j + 1];
                        vdata[i][j + 1] = tempComplex;
                    }
                }
            }
        }
        S = new FieldDiagonalMatrix<>(ComplexUtils.convertToComplex(d));
    }

    /**
     * Generates a Householder transformation from within the part of row r of a Complex array (altered) extending from
     * columns c1 to c2. The method overwrites the row with the result of applying the transformation.
     *
     * @param A  The matrix from which the transformation is to be generated (altered)
     * @param r  The index of the generating row
     * @param c1 The index of the column in which the generating row begins
     * @param c2 The index of the column in which the generating row ends
     * @return A Z1 of length r2-r1+1 containing the Householder vector
     */
    private ArrayFieldVector<Complex> genr(Complex[][] A, int r, int c1,
                                           int c2) {
        int j, cu;
        double norm, s;
        Complex scale;
        Complex t;

        cu = c2 - c1 + 1;
        ArrayFieldVector<Complex> u = new ArrayFieldVector<>(
                ComplexField.getInstance(), cu);
        Complex[] udata = u.getDataRef();

        for (j = c1; j <= c2; j++) {
            udata[j - c1] = A[r][j];
            A[r][j] = Complex.ZERO;
        }

        norm = calcFrobeniusNorm(udata);

        if (c1 == c2 || norm == 0) {
            A[r][c1] = udata[0].negate();
            udata[0] = new Complex(FastMath.sqrt(2));
            return u;
        }

        scale = new Complex(1 / norm, 0);

        if (udata[0].getReal() != 0 || udata[0].getImaginary() != 0) {
            t = udata[0];
            scale = scale.multiply(t.conjugate().divide(t.abs()));
        }
        A[r][c1] = Complex.ONE.divide(scale).negate();

        for (j = 0; j < cu; j++) {
            udata[j] = udata[j].multiply(scale);
        }

        udata[0] = new Complex(u.getEntry(0).getReal() + 1, 0);
        s = FastMath.sqrt(1 / udata[0].getReal());

        for (j = 0; j < cu; j++) {
            udata[j] = new Complex(udata[j].getReal() * s,
                    -udata[j].getImaginary() * s);
        }

        return u;
    }

    /**
     * Premultiplies the Householder transformation contained in a Vector into a Matrix A[r1:r2,c1:c2] and overwrites
     * Matrix A[r1:r2,c1:c2] with the results. If r1 &gt; r2 or c1 &gt; c2 the method does nothing.
     *
     * @param u  The Householder vector
     * @param A  The Matrix to which the transformation is to be applied (altered)
     * @param r1 The index of the first row to which the transformation is to be applied
     * @param r2 The index of the last row to which the transformation is to be applied
     * @param c1 The index of the first column to which the transformation is index of the to be applied
     * @param c2 The index of the last column to which the transformation is to be applied
     * @param v  A work array of length at least c2-c1+1
     * @throws Exception if arrays to small
     */
    public static void premultiplyA(Complex[] u, Complex[][] A, int r1, int r2, int c1,
                                    int c2, Complex[] v) throws Exception {
        int i, j;

        if (r2 < r1 || c2 < c1) {
            return;
        }

        if (r2 - r1 - 1 > u.length) {
            throw new Exception("Householder vector too short.");
        }

        if (c2 - c1 - 1 > v.length) {
            throw new Exception("Work vector too short.");
        }

        for (j = c1; j <= c2; j++) {
            v[j - c1] = Complex.ZERO;
        }
        double real, imag;
        for (i = r1; i <= r2; i++) {
            for (j = c1; j <= c2; j++) {

                real = v[j - c1].getReal() + u[i - r1].getReal()
                        * A[i][j].getReal() + u[i - r1].getImaginary()
                        * A[i][j].getImaginary();
                imag = v[j - c1].getImaginary() + u[i - r1].getReal()
                        * A[i][j].getImaginary() - u[i - r1].getImaginary()
                        * A[i][j].getReal();
                v[j - c1] = new Complex(real, imag);
            }
        }

        for (i = r1; i <= r2; i++) {
            for (j = c1; j <= c2; j++) {
                real = A[i][j].getReal() - u[i - r1].getReal()
                        * v[j - c1].getReal() + u[i - r1].getImaginary()
                        * v[j - c1].getImaginary();
                imag = A[i][j].getImaginary() - u[i - r1].getReal()
                        * v[j - c1].getImaginary() - u[i - r1].getImaginary()
                        * v[j - c1].getReal();
                A[i][j] = new Complex(real, imag);
            }
        }
    }

    public static void premultiplyA(Complex[] u, Complex[][] A, int r1, int r2, int c1,
                                    int c2) throws Exception {

        if (c1 > c2) {
            return;
        }

        premultiplyA(u, A, r1, r2, c1, c2, new ArrayFieldVector<>(c2 - c1 + 1,
                Complex.ZERO).getDataRef());
    }

    /**
     * Multiply Complex matrix A by Householder transformation vector
     *
     * @param A  The matrix to which the transformation is to be applied (altered)
     * @param u  The Householder vector
     * @param r1 The index of the first row to which the transformation is to be applied
     * @param r2 The index of the last row to which the transformation is to be applied
     * @param c1 The index of the first column to which the transformation is index of the to be applied
     * @param c2 The index of the last column to which the transformation is to be applied
     * @param v  A work array of length at least c2-c1+1
     * @throws Exception if u or v has too few columns
     */
    private void transformAwithU(Complex[][] A, Complex[] u, int r1, int r2, int c1,
                                 int c2, Complex[] v) throws Exception {

        int i, j;

        if (r2 < r1 || c2 < c1) {
            return;
        }

        if (c2 - c1 + 1 > u.length) {
            throw new Exception("Householder vector too short.");
        }

        if (r2 - r1 + 1 > v.length) {
            throw new Exception("Work vector too short.");
        }

        double real, imag;
        for (i = r1; i <= r2; i++) {
            v[i - r1] = Complex.ZERO;
            for (j = c1; j <= c2; j++) {
                real = v[i - r1].getReal() + A[i][j].getReal()
                        * u[j - c1].getReal() - A[i][j].getImaginary()
                        * u[j - c1].getImaginary();
                imag = v[i - r1].getImaginary() + A[i][j].getReal()
                        * u[j - c1].getImaginary() + A[i][j].getImaginary()
                        * u[j - c1].getReal();
                v[i - r1] = new Complex(real, imag);
            }
        }

        for (i = r1; i <= r2; i++) {
            for (j = c1; j <= c2; j++) {
                real = A[i][j].getReal() - v[i - r1].getReal()
                        * u[j - c1].getReal() - v[i - r1].getImaginary()
                        * u[j - c1].getImaginary();
                imag = A[i][j].getImaginary() + v[i - r1].getReal()
                        * u[j - c1].getImaginary() - v[i - r1].getImaginary()
                        * u[j - c1].getReal();
                A[i][j] = new Complex(real, imag);
            }
        }
    }

    /**
     * Multiply Complex matrix A by Householder transformation vector
     *
     * @param A  The matrix to which the transformation is to be applied (altered)
     * @param u  The Householder vector
     * @param r1 The index of the first row to which the transformation is to be applied
     * @param r2 The index of the last row to which the transformation is to be applied
     * @param c1 The index of the first column to which the transformation is index of the to be applied
     * @param c2 The index of the last column to which the transformation is to be applied
     * @throws Exception if u or v has too few columns
     */
    private void transformAwithU(Complex[][] A, Complex[] u, int r1, int r2, int c1,
                                 int c2) throws Exception {

        if (r2 < r1) {
            return;
        }

        transformAwithU(A, u, r1, r2, c1, c2, new ArrayFieldVector<>(r2 - r1 + 1,
                Complex.ZERO).getDataRef());
    }

    /**
     * Generates a Householder transformation from within the part of column c of a Zmat (altered) extending from rows
     * r1 to r2. The method overwrites the column with the result of applying the transformation.
     *
     * @param A  The matrix from which the transformation is to be generated (altered)
     * @param r1 The index of the row in which the generating column begins
     * @param r2 The index of the row in which the generating column ends
     * @param c  The index of the generating column
     * @return A Z1 of length r2-r1+1 containing the Householder vector
     */
    public static ArrayFieldVector<Complex> genc(Complex[][] A, int r1, int r2,
                                                 int c) {

        int i, ru;
        double norm;
        Complex scale;

        ru = r2 - r1 + 1;

        ArrayFieldVector<Complex> u = new ArrayFieldVector<>(
                ComplexField.getInstance(), r2 - r1 + 1);
        Complex[] udata = u.getDataRef();

        //changed from i <= to i < and now only one spot looks wrong.
        for (i = r1; i <= r2; i++) {
            udata[i - r1] = A[i][c];
            A[i][c] = Complex.ZERO;
        }

        norm = calcFrobeniusNorm(udata);

        if (r1 == r2 || norm == 0) {
            A[r1][c] = udata[0].negate();
            udata[0] = new Complex(FastMath.sqrt(2));
            return u;
        }

        scale = new Complex(1 / norm);

        if (udata[0].getReal() != 0 || udata[0].getImaginary() != 0) {
            Complex t = udata[0];

            Complex t1 = t.conjugate();
            t = t1.divide(t.abs());
            scale = scale.multiply(t);
        }

        A[r1][c] = Complex.ONE.divide(scale).negate();

        udata[0] = new Complex(udata[0].multiply(scale).getReal() + 1);

        double real, imag;
        double multiplicand = FastMath.sqrt(1.0 / udata[0].getReal());

        for (i = 1; i < ru; ++i) {
            real = (udata[i].getReal() * scale.getReal() - udata[i]
                    .getImaginary() * scale.getImaginary())
                    * multiplicand;
            imag = (udata[i].getImaginary() * scale.getReal() + udata[i]
                    .getReal() * scale.getImaginary())
                    * multiplicand;
            udata[i] = new Complex(real, imag);
        }
        udata[0] = new Complex(udata[0].getReal() * multiplicand,
                udata[0].getImaginary() * multiplicand);
        return u;
    }

    /**
     * Computes the Frobenius norm a Complex array
     *
     * @param u The Complex array
     * @return The Frobenius norm of u
     */
    private static double calcFrobeniusNorm(Complex[] u) {
        int i;
        double nrm, scale;

        int n = u.length;

        scale = 0.0;
        for (i = 0; i < n; i++) {
            scale = FastMath.max(scale, u[i].abs());
        }
        if (scale == 0) {
            return 0.0;
        }
        if (scale < 1) {
            scale = scale * 1.0e20;
        }
        scale = 1 / scale;
        nrm = 0;
// fixme scale not used.  Should be used to scale numbers before squaring and summing to minimize error
        for (i = 0; i < n; i++) {
            nrm += FastMath.pow(u[i].getReal(), 2)
                    + FastMath.pow(u[i].getImaginary(), 2);
        }
        return FastMath.sqrt(nrm);
    }

    // public Complex visit() {
    // return data[row][column];
    // }
    //
    // }

    /**
     * Rot generates and manipulates plane rotations. Given a 2-vector compontents are x and y, there is a unitary
     * matrix P such that
     *
     * <pre>
     *      P|x| =  |   c      s||x| = |z|
     *       |y|    |-conj(s)  c||y|   |0|
     * </pre>
     * <p>
     * The number c, which is always real, is the cosine of the rotation. The number s, which may be complex is the sine
     * of the rotation.
     * <p>
     * Comments: This suite will eventually contain methods for real rotations (two are already in place). The only
     * difference between real and complex rotations is that si and zi are zero for the former. The final routines will
     * do the efficient thing.
     *
     * @author G. W. Stewart
     * @version Pre-alpha
     */
    public static class Rot {

        /**
         * The cosine of the rotation
         */
        protected double c;
        /**
         * The sine of the rotation
         */
        public Complex s;
        /**
         * The transformed vector
         */
        public Complex z;

        /**
         * Given a real 2-vector, genc generates a real plane rotation P such that
         *
         * <pre>
         *      P|x| =  | c  s||x| = |z|
         *       |y|    |-s  c||y|   |0|
         * </pre>
         *
         * @param x The first component of the two vector
         * @param y The second component of the two vector
         * @param P The plane rotation
         */
        public static void genc(double x, double y, Rot P) {
            if (x == 0 & y == 0) {
                P.c = 1;
                P.s = Complex.ZERO;
                return;
            }

            double s = FastMath.abs(x) + FastMath.abs(y);

            P.z = new Complex(s
                    * FastMath.sqrt((x / s) * (x / s) + (y / s) * (y / s)));
            P.c = x / P.z.getReal();
            P.s = new Complex(y / P.z.getReal());
        }

        /**
         * Given a real 2-vector, genr generates a plane rotation such that
         *
         * <pre>
         *      |x y|P = |x y|| c  s||x| = |z 0|
         *                    |-s  c||y|
         * </pre>
         *
         * @param x The first component of the 2-vector
         * @param y The second component of the 2-vector
         * @param P The rotation
         */
        public static void genr(double x, double y, Rot P) {
            double s = FastMath.abs(x) + FastMath.abs(y);

            if (s == 0.) {
                P.c = 1.;
                P.s = Complex.ZERO;
                P.z = Complex.ZERO;
                return;
            }

            P.z = new Complex(s
                    * FastMath.sqrt((x / s) * (x / s) + (y / s) * (y / s)));
            P.c = x / P.z.getReal();
            P.s = new Complex(-y / P.z.getReal());
        }

        /**
         * Multiplies columns (ii1:ii2,jj1) and A(ii2:ii2,jj1) of a Zmat (altered) by a plane rotation.
         *
         * @param A  The Zmat (altered)
         * @param P  The rotation
         * @param i1 The first index of the column range
         * @param i2 The second index of the column range
         * @param j1 The index of the first column
         * @param j2 The index of the second column
         */
        public static void ap(Complex[][] A, Rot P, int i1, int i2, int j1,
                              int j2) {
            double t1r, t1i, t2r, t2i;

            for (int i = i1; i <= i2; i++) {
                t1r = A[i][j1].getReal();
                t1i = A[i][j1].getImaginary();
                t2r = A[i][j2].getReal();
                t2i = A[i][j2].getImaginary();

                A[i][j1] = new Complex(P.c * t1r - P.s.getReal() * t2r
                        - P.s.getImaginary() * t2i, P.c * t1i - P.s.getReal()
                        * t2i + P.s.getImaginary() * t2i);

                A[i][j2] = new Complex(P.c * t2r + P.s.getReal() * t1r
                        - P.s.getImaginary() * t1i, P.c * t2i + P.s.getReal()
                        * t1i + P.s.getImaginary() * t1r);
            }
        }

        /**
         * Multiplies columns (ii1:ii2,jj1) and A(ii2:ii2,jj1) of a Zmat (altered) by the conjugate transpose of plane
         * rotation.
         *
         * @param A  The Zmat (altered)
         * @param P  The rotation
         * @param i1 The first index of the column range
         * @param i2 The second index of the column range
         * @param j1 The index of the first column
         * @param j2 The index of the second column
         */
        public static void aph(Complex[][] A, Rot P, int i1, int i2, int j1,
                               int j2) {
            double j2r;
            double j2i;
            double j1r;
            double j1i;

            for (int i = i1; i <= i2; i++) {
                j1r = A[i][j1].getReal();
                j1i = A[i][j1].getImaginary();
                j2r = A[i][j2].getReal();
                j2i = A[i][j2].getImaginary();
                A[i][j1] = new Complex(P.c * j1r + P.s.getReal() * j2r + P.s.getImaginary() * j2i,
                        P.c * j1i + P.s.getReal() * j2i - P.s.getImaginary() * j2r);

                A[i][j2] = new Complex(P.c * j2r - P.s.getReal() * j1r + P.s.getImaginary() * j1i,
                        P.c * j2i - P.s.getReal() * j1i - P.s.getImaginary() * j1r);
            }
        }
    }

}
