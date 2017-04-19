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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.math;

import org.nmrfx.processor.math.apache.ComplexHouseholderQRDecomposition;
import org.nmrfx.processor.math.apache.ComplexSingularValueDecomposition;
import org.nmrfx.processor.math.apache.FieldDiagonalMatrix;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexField;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.FieldMatrixChangingVisitor;

/**
 * Provides methods for doing linear prediction.
 *
 * @author Bruce Johnson
 */
public class LinearPrediction {

    Vec vector;
    int size;
    Complex[] cvec;

    public LinearPrediction(Vec vector) {
        this.vector = vector;
        this.size = vector.getSize();
        vector.makeApache();
        this.cvec = vector.cvec;
    }

    void resize(int newSize) {
        vector.resize(newSize);
        size = vector.getSize();
        this.cvec = vector.cvec;
    }

    void resize(int newSize, boolean complex) {
        vector.resize(newSize, complex);
        size = vector.getSize();
        this.cvec = vector.cvec;
    }

    /**
     * Perform linear prediction using singular value decomposition.
     *
     * @param fitStart First point used for fitting linear prediction coefficients
     * @param fitEnd Last point used for fitting linear prediction coefficients
     * @param ncoef Number of coefficients to calculate
     * @param threshold Include matrix values whose singular values are greater than this threshold
     * @param startPred First point to predict
     * @param endPred Last Point to predict
     * @param nPred Number of points to predict (used if endPred less than or equal to 0)
     * @param calculateBackward Perform fitting in backwards direction
     * @param calculateForward Perform fitting in forward direction
     * @param insertion If true, insert predicted points at beginning of vector
     * @param mirror If true, perform mirror image prediction
     * @throws VecException if invalid arguments
     */
    public void svdPredLP(int fitStart, int fitEnd, int ncoef, double threshold, int startPred, int endPred, int nPred,
            boolean calculateBackward, boolean calculateForward, boolean insertion, int mirror) throws VecException {
        if (insertion) {
            if (mirror != 0) {
                throw new VecException("svdPredLP: can't do mirror image prediction in replace (LPR) mode");
            }
            if (startPred <= 0) {
                startPred = 0;
            }
            if (endPred <= 0) {
                if (nPred > 0) {
                    endPred = nPred - 1;
                } else {
                    endPred = 0;
                }
            }
            if (fitStart <= 0) {
                fitStart = endPred + 1;
            }
            if (calculateBackward && (fitStart < 1)) {
                fitStart = 1;
            }
            if (fitEnd < 16) {
                fitEnd = 16;
            }
            if (fitEnd >= size) {
                fitEnd = size - 1;
            }
            if (ncoef <= 0) {
                ncoef = 1 * (fitEnd - fitStart + 1) / 4;
                if (ncoef > 16) {
                    ncoef = 16;
                }
            }
            if (ncoef <= 2) {
                throw new VecException("svdPredLP: ncoef <= 2");
            }

            if (fitEnd <= fitStart) {
                throw new VecException("svdPredLP: fitEnd <= fitStart");
            }
        } else {
            if ((mirror < 0) || (mirror > 2)) {
                throw new VecException("svdPredLP: invalid mirror mode.  Must be 0,1, or 2");
            }
            int startSize = size;
            int addPoints = 0;
            if (mirror != 0) {
                addPoints = startSize + (mirror - 2);
            }
            if (startPred <= 0) {
                startPred = addPoints + size;
            }
            if (endPred <= 0) {
                if (nPred > 0) {
                    endPred = addPoints + size + nPred - 1;
                } else {
                    endPred = addPoints + 2 * size - 1;
                }
            }
            if (fitStart < 0) {
                fitStart = 0;
            }
            if (calculateBackward && (fitStart < 1)) {
                fitStart = 1;
            }
            if (fitEnd <= 0) {
                fitEnd = addPoints + size - 1;
            }
            if (ncoef <= 0) {
                ncoef = 1 * (fitEnd - fitStart + 1) / 8;
                if (ncoef > 16) {
                    ncoef = 16;
                }
            }

            if (fitEnd <= fitStart) {
                throw new VecException("svdPredLP: fitEnd <= fitStart");
            }
            if (ncoef <= 2) {
                throw new VecException("svdPredLP: ncoef <= 2");
            }

        }

        int n = ncoef;
        int m = fitEnd - n - fitStart + 1;
        if (m < n) {
            throw new VecException("svdPredLP: m < n");
        }

        //System.out.printf("ncoef %2d mirror %d m %3d n %3d fitStart %4d fitEnd %4d startPred %4d endPred %4d size %d\n",ncoef, mirror,  m, n, fitStart, fitEnd, startPred, endPred, size);
        //printLocation();
        int startSize = size;
        if (mirror != 0) {
            int addPoints = startSize + (mirror - 2);
            resize(size + addPoints, true);
            for (int i = 0; i < startSize; i++) {
                cvec[size - i - 1] = cvec[size - i - startSize];
            }
            for (int i = 0; i < addPoints; i++) {
                cvec[i] = cvec[size - i - 1].conjugate();
            }
        }

        Polynomial polyF = null;
        Polynomial polyB = null;
        if (calculateBackward) {
            Complex[] coefB = getCoefsByTLS(cvec, fitStart, m, n, threshold, true);
            for (Complex cmplx : coefB) {
                if (cmplx.isNaN()) {
                    System.out.println("NANb");
                    for (int ii = 0; ii < size; ii++) {
                        Complex cValue = cvec[ii];
                        System.out.println(ii + " " + cValue.getReal() + " " + cValue.getImaginary());
                    }
                    break;
                }
            }
            Complex[] ocoefB = new Complex[coefB.length + 1];
            // negate,copy and reverse terms
            for (int i = 0; i < coefB.length; i++) {
                ocoefB[i] = new Complex(-coefB[coefB.length - 1 - i].getReal(), -coefB[coefB.length - 1 - i].getImaginary());
            }
            // add highest order term
            ocoefB[ocoefB.length - 1] = Complex.ONE;

            polyB = new Polynomial(ocoefB.length);
            polyB.svejgardRoot(ocoefB.length - 1, ocoefB);
        }

        if (calculateForward) {
            Complex[] coefF = getCoefsByTLS(cvec, fitStart, m, n, threshold, false);
            for (Complex cmplx : coefF) {
                if (cmplx.isNaN()) {
                    System.out.println("NANf");
                    for (int ii = 0; ii < size; ii++) {
                        Complex cValue = cvec[ii];
                        System.out.println(ii + " " + cValue.getReal() + " " + cValue.getImaginary());
                    }
                    break;
                }
            }

            Complex[] ocoefF = new Complex[coefF.length + 1];
            // negate and copy terms
            for (int i = 0; i < coefF.length; i++) {
                ocoefF[i] = new Complex(-coefF[i].getReal(), -coefF[i].getImaginary());
            }
            // add highest order term
            ocoefF[ocoefF.length - 1] = Complex.ONE;

            polyF = new Polynomial(ocoefF.length);
            polyF.svejgardRoot(ocoefF.length - 1, ocoefF);
        }

        if (!insertion) {
            if (polyF != null) {
                VecUtil.reflectRoots(polyF.root, false);
            }
            if (polyB != null) {
                VecUtil.conjugate(polyB.root);
                VecUtil.reflectRoots(polyB.root, false);
            }
        } else {
            if (polyF != null) {
                VecUtil.conjugate(polyF.root);
                VecUtil.reflectRoots(polyF.root, true);
            }
            if (polyB != null) {
                VecUtil.reflectRoots(polyB.root, true);
            }
        }

        Complex[] coefFinal = null;
        Complex[] coefF2 = null;
        Complex[] coefB2 = null;

        if (polyF != null) {
            coefF2 = polyF.makeCoeffs();
            VecUtil.negate(coefF2);
            coefFinal = coefF2;
        }

        if (polyB != null) {
            coefB2 = polyB.makeCoeffs();
            VecUtil.negate(coefB2);
            coefFinal = coefB2;
        }

        if ((coefF2 != null) && (coefB2 != null)) {
            VecUtil.addVector(coefF2, coefF2.length, coefB2, coefF2);
            for (int i = 0; i < coefF2.length; i++) {
                coefFinal[i] = new Complex(coefF2[i].getReal() / 2, coefF2[i].getImaginary() / 2);
            }
        }
        if (insertion) {
            VecUtil.reverse(coefFinal);
            insertWithPrediction(coefFinal, endPred, startPred, ncoef);
        } else {
            extendWithPrediction(coefFinal, endPred, startPred, ncoef);
        }
        if (mirror != 0) {
            int addPoints = startSize + (mirror - 2);
            int newSize = size - addPoints;
            for (int i = 0; i < newSize; i++) {
                cvec[i] = cvec[i + addPoints];
            }
            resize(newSize, true);
        }
    }

    private void insertWithPrediction(Complex[] coef, int endPred, int startPred, int ncoef) {
        int nPredict = endPred - startPred + 1;
        if (startPred < 0) {
            System.arraycopy(cvec, 0, cvec, -startPred, cvec.length + startPred);
            endPred += -startPred;
        }

        for (int i = 0; i < nPredict; i++) {
            Complex sum = Complex.ZERO;
            for (int j = 0; j < ncoef; j++) {
                sum = sum.add(coef[j].multiply(cvec[endPred + 1 - i + j]));
            }
            //System.out.println(i + " " + (endPred - i)  + " " + cvec[endPred - i].getReal()+" "+sum.getReal());
            cvec[endPred - i] = sum;
        }
    }

    private void extendWithPrediction(Complex[] coef, int endPred, int startPred, int ncoef) {
        int nPredict = endPred - startPred + 1;
        int newSize = endPred + 1;
        if (newSize > size) {
            resize(newSize);
        }
        for (int i = 0; i < nPredict; i++) {
            Complex sum = Complex.ZERO;
            for (int j = 0; j < ncoef; j++) {
                sum = sum.add(coef[j].multiply(cvec[startPred - ncoef + i + j]));
            }
            //System.out.println(i + " " + (startPred + i) + " " + cvec[startPred + i].getReal() + " " + sum.getReal());
            cvec[startPred + i] = sum;
        }
    }

    /**
     * Calculate pseudo-inverse of a matrix
     *
     *
     * @param A matrix to invert
     * @param nSingular number of singular values to use in calculating pseudoInverse
     * @return the pseudo-inverse
     */
    //functions involving Zsvd to work on later
    public static FieldMatrix<Complex> PInv(Array2DRowFieldMatrix<Complex> A, int nSingular) {
        //Zsvd zsvd = null;
        //Zmat Ainv = null;
        ComplexSingularValueDecomposition csvd;
        FieldMatrix<Complex> Ainv;

        try {
            //zsvd = new Zsvd(A);
            csvd = new ComplexSingularValueDecomposition(A);
        } catch (Exception jpE) {
            System.out.println(jpE.getMessage());
            jpE.printStackTrace();
            return null;
        }

        //Zdiagmat s = zsvd.S;
        //Zmat Sinv = new Zmat(A.nr, A.nc);
        FieldDiagonalMatrix<Complex> s = csvd.S;
        FieldMatrix<Complex> Sinv = new Array2DRowFieldMatrix<>(
                ComplexField.getInstance(), A.getRowDimension(),
                A.getColumnDimension());

        for (int i = 0; i < nSingular; i++) {
            Complex z = s.getEntry(i, i);
            //z = z.Div(new Z(Z.ONE), z);
            Sinv.setEntry(i, i, z.reciprocal());
        }

        FieldMatrix<Complex> U = csvd.U;
        FieldMatrix<Complex> V = csvd.V;
        Sinv.transpose();
        ComplexMatrixConjugateTranspose cmcj = new ComplexMatrixConjugateTranspose();
        Sinv.walkInOptimizedOrder(cmcj);

        //Zmat Vs = Times.o(V, H.o(Sinv));
        FieldMatrix<Complex> Vs = V.multiply(Sinv);
        U.walkInOptimizedOrder(new ComplexMatrixConjugateTranspose());
        Ainv = Vs.multiply(U);

        return Ainv;
    }

    /**
     * Calculate linear prediction coefficients using total least squares
     *
     * @param cvec Array of complex values to analyze
     * @param start Starting point in cvec
     * @param m Number of points to use
     * @param n Number of coefficients
     * @param threshold unused FIXME
     * @param backward if true perform backwards linear prediction
     * @return a Complex array of coefficients
     * @throws VecException if total least squares fails
     */
    public static Complex[] getCoefsByTLS(Complex[] cvec, int start, int m, int n, double threshold, boolean backward) throws VecException {
        Complex[][] Ary = new Complex[m][n + 1];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < (n); j++) {
                Ary[i][j] = new Complex(cvec[j + i + start].getReal(), cvec[j + i + start].getImaginary());
            }
        }
        for (int i = 0; i < m; i++) {
            if (backward) {
                Ary[i][n] = new Complex(cvec[i + start - 1].getReal(), cvec[i + start - 1].getImaginary());
            } else {
                Ary[i][n] = new Complex(cvec[i + start + n].getReal(), cvec[i + start + n].getImaginary());
            }
        }
        Array2DRowFieldMatrix<Complex> A = new Array2DRowFieldMatrix<>(Ary);
        try {
            Complex[] coefB = tlsMat(A);
            return coefB;
        } catch (Exception e) {
            throw new VecException(e.getMessage());
        }
    }

    /**
     *
     */
    public static class Complexqrdthin {

        /**
         *
         */
        public FieldMatrix<Complex> Q;

        /**
         *
         */
        public Array2DRowFieldMatrix<Complex> R;

        /**
         *
         */
        public ComplexHouseholderQRDecomposition hqr;

        /**
         * Perform QR Decomposition of matrix A
         *
         * @param A the matrix
         * @throws Exception if an error occurs while calculating
         */
        public Complexqrdthin(FieldMatrix<Complex> A) throws Exception {
            hqr = new ComplexHouseholderQRDecomposition(A); //householder QR decomposition
            R = hqr.R; //upper triangular
        }

        /**
         * Computes the product QB
         *
         * @param B A complex matrix
         * @return QB
         * @throws Exception if error occurs while calculating
         */
        public FieldMatrix<Complex> qb(FieldMatrix<Complex> B) throws Exception {
            FieldMatrix<Complex> qb = hqr.qb(B);
            return qb; //premultiply B with 
        }
    }

    /**
     * Generate linear prediction coefficients by total least squares of matrix.
     *
     * @param A the matrix
     * @return the coefficients
     * @throws Exception if error in svd etc.
     */
    public static Complex[] tlsMat(Array2DRowFieldMatrix<Complex> A) throws Exception {
        //Zsvd zsvd = null;
        ComplexSingularValueDecomposition csvd = null;
        Complexqrdthin chqrd = new Complexqrdthin(A);
        try {
            csvd = new ComplexSingularValueDecomposition(chqrd.R);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new Exception("CSVD Failed");
        }

        FieldMatrix<Complex> V = csvd.V;
//        for (int i = 0; i < V.getRowDimension(); ++i) {
//            for (int j = 0; j < V.getColumnDimension(); ++j)
//                System.out.println(i + " " + j + " " + V.getEntry(i, j));
//            System.out.println();
//        }
        int m1 = csvd.S.getRowDimension();
        double sValMin = csvd.S.getEntry(m1 - 1, m1 - 1).getReal();
        if (m1 == 1) {
            sValMin = 0.0;
        }
        int p = m1 - 1;
        double ratio = 1.5;
        for (int k = 0; k < m1; k++) {
            double sVal = csvd.S.getEntry(k, k).getReal();
            if (sVal < ratio * sValMin) {  // fixme need better criteria (sVal < (sValMin + error) ??)
                p = k;
                break;
            }
        }
        if (p < 2) {
            p = 2;
        }
        double sum = 0.0;
        for (int j = p; j < m1; j++) {
            double absVal = V.getEntry(m1 - 1, j).abs();
            sum += absVal * absVal;
        }
        double norm = -1.0 / sum;
//        System.out.println("norm: " + norm);
        Complex[] ocoef = new Complex[m1 - 1];
        for (int k = 0; k < (m1 - 1); k++) {
            Complex zSum = Complex.ZERO;
            for (int i = p; i < m1; i++) {
                Complex zval = V.getEntry(m1 - 1, i);
                //System.out.println("zval: " + zval);
                zval = zval.conjugate();
                zval = zval.multiply(V.getEntry(k, i));
                zSum = zSum.add(zval);
            }
            zSum = zSum.multiply(norm);

            ocoef[k] = zSum;
        }
//        System.out.println("ocoef:");
//        for (int i = 0; i < ocoef.length; ++i)
//            System.out.println(ocoef[i]);
        return ocoef;
    }

    /**
     * Calculate the pseudo-inverse of a Complex matrix
     *
     * @param A the matrix
     * @return the pseudo-inverse
     * @throws Exception if an error occurs
     */
    public static FieldMatrix<Complex> getPseudoInverse(FieldMatrix<Complex> A) throws Exception {
        // FIXME should use correct value, rather than an arbitrary one for threshold
        double threshold = 1.0e-16;
        FieldMatrix<Complex> Ainv;

        Complexqrdthin chqrd = new Complexqrdthin(A);
        ComplexSingularValueDecomposition csvd = new ComplexSingularValueDecomposition(chqrd.R);
        FieldMatrix<Complex> U1 = csvd.U;
        FieldMatrix<Complex> U = chqrd.qb(U1);
        /*
         zsvd = new Zsvd(A);
         U = zsvd.U;
         */

        FieldDiagonalMatrix<Complex> s = csvd.S;
//        Zmat Sinv = new Zmat(A.nr, A.nc);

        try {
            for (int i = 0; i < s.getRowDimension(); i++) {
                Complex c = s.getEntry(i, i);
                if (c.getReal() > threshold) {
                    c = c.reciprocal();
                    s.setEntry(i, i, c);
                } else {
                    s.setEntry(i, i, Complex.ZERO);
                }

            }

            FieldMatrix<Complex> V = csvd.V;
            FieldMatrix<Complex> Vs = V.multiply(s);
            FieldMatrix<Complex> Ur = U.getSubMatrix(0, U.getRowDimension() - 1, 0, s.getRowDimension() - 1);
            Ur.walkInOptimizedOrder(new ComplexMatrixConjugateTranspose()); //H.o(Ur);

            //Ainv = Times.o(Vs, H.o(Ur));
            Ainv = Vs.multiply(Ur);

        } catch (OutOfRangeException | NumberIsTooLargeException | DimensionMismatchException | NumberIsTooSmallException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return null;
        }

        return Ainv;
    }

    /**
     * Generate a decaying sinusoidal signal from the specified parameters
     *
     * @param v Put signal in this Complex array
     * @param startSignals ??
     * @param fd Frequency and decay values as Complex numbers
     * @param amps Amplitudes
     * @param phases Phases
     * @param start start adding signals to v at this point
     * @param end end adding signals to v at this point
     */
    public static void genSignal(Complex[] v, int startSignals, Complex[] fd, double[] amps, double[] phases, int start, int end) {
        if (start < 0) {
            System.arraycopy(v, 0, v, -start, v.length + start);
            startSignals = startSignals - start;
            end = end - start;
            start = 0;
        }
        for (int i = start; i <= end; i++) {
            v[i] = new Complex(0, 0);
        }

        Complex[] temp = new Complex[amps.length];
        Complex[] fdAdj = new Complex[amps.length];
        for (int i = 0; i < amps.length; i++) {
            temp[i] = new Complex(amps[i], 0.0);
            fdAdj[i] = new Complex(fd[i].getReal(), fd[i].getImaginary());
        }
        for (int j = 0; j < amps.length; j++) {
            Complex w = new Complex(fdAdj[j].getReal(), fdAdj[j].getImaginary());
            for (int i = start; i < startSignals; i++) {
                temp[j] = temp[j].divide(w);
            }
            v[start] = v[start].add(temp[j]);
        }
        for (int i = (start + 1); i <= end; i++) {
            for (int j = 0; j < amps.length; j++) {
                Complex w = new Complex(fdAdj[j].getReal(), fdAdj[j].getImaginary());
                temp[j] = temp[j].multiply(w);
                v[i] = v[i].add(temp[j]);
            }
        }
    }

    private static class ComplexMatrixConjugateTranspose implements FieldMatrixChangingVisitor<Complex> {

        private int rows, columns, startRow, endRow, startColumn, endColumn;

        ComplexMatrixConjugateTranspose() {
        }

        @Override
        public void start(int rows, int columns, int startRow, int endRow, int startColumn, int endColumn) {
            //
        }

        @Override
        public Complex visit(int i, int i1, Complex t) {
            return t.conjugate();
        }

        @Override
        public Complex end() {
            return Complex.ZERO;
        }
    }

}
