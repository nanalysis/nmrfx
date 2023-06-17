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
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.ArrayFieldVector;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.util.FastMath;

import static org.nmrfx.processor.math.apache.ComplexSingularValueDecomposition.premultiplyA;

/**
 * Computes a Householder QR decomposition.Specifically, given a matrix A there are is a unitary matrix U such that
 * <pre>
 *    QA = R
 * </pre> where R is zero below its diagonal. In constructing this decomposition, Zhqrd represents Q as a product of
 * Householder transformations with each transformation represented by a Z1. R is represented by a Zutmat. Methods are
 * provided to apply the transformations to other matrices.
 * <br>
 * Comments: The routines to postmultiply by Q are soft coded and should ultimately be replaced.
 *
 * @author G. W. Stewart
 * @version Pre-alpha
 */
public class ComplexHouseholderQRDecomposition {

    /**
     * The number of rows in A
     */
    public int nrow;
    /**
     * The number of columns in A
     */
    public int ncol;
    /**
     * The number of Householder transformations
     */
    public int ntran;
    /**
     * An array containing the generating vectors for the Householder transformations.
     */
    public ArrayFieldVector<Complex>[] U;
    /**
     * The R factor. If nrow&gt;ncol then R is square of order ncol. Otherwise R has the same dimenstions as A.
     */
    public Array2DRowFieldMatrix<Complex> R;

    /**
     * Computes a Householder QR decomposition of a Zmat
     *
     * @param A A Zmat
     * @throws Exception Passed from below.
     */
    public ComplexHouseholderQRDecomposition(FieldMatrix<Complex> A) throws Exception {
        /* Initialize. */

        nrow = A.getRowDimension();
        ncol = A.getColumnDimension();
        ntran = FastMath.min(nrow, ncol);

        U = new ArrayFieldVector[ntran];
        /* Perform the reduction in R */

        R = new Array2DRowFieldMatrix<>(ComplexField.getInstance(), A.getData());
        for (int k = 0; k < ntran; k++) {

            U[k] = ComplexSingularValueDecomposition.genc(R.getDataRef(), k, A.getRowDimension() - 1, k);
            premultiplyA(U[k].getDataRef(), R.getDataRef(), k, A.getRowDimension() - 1, k + 1, A.getColumnDimension() - 1);
        }
        if (nrow > ncol) {// Chop off zeros at the bottom.
            int nCols = R.getColumnDimension();
            Complex[][] data = new Complex[nCols][nCols];
            Complex[][] dataRef = R.getDataRef();
            for (int i = 0; i < nCols; i++) {
                System.arraycopy(dataRef[i], 0, data[i], 0, nCols);
            }
            R = new Array2DRowFieldMatrix<>(ComplexField.getInstance(), data);
        }
    }

    /**
     * Computes the product QB. Throws Exception for inconsistent dimenstions.
     *
     * @param B A Zmat
     * @return QB
     * @throws Exception Thrown for inconsistent dimensions.
     */
    public FieldMatrix<Complex> qb(FieldMatrix<Complex> B) throws Exception {
        if (B.getColumnDimension() != ncol) {
            throw new Exception("Inconsistent dimensions");
        }

        Array2DRowFieldMatrix<Complex> C = new Array2DRowFieldMatrix(ComplexField.getInstance(), B.getData());

        for (int k = ntran - 1; k >= 0; k--) {
            premultiplyA(U[k].getDataRef(), C.getDataRef(), k, C.getRowDimension(), 0, C.getColumnDimension());
        }

        return C;

    }

    /**
     * Computes the product Q<sup>H</sup>B. Throws Exception for inconsistent dimenstions.
     *
     * @param B A Complex matrix
     * @return QH
     */
    public FieldMatrix<Complex> qhb(FieldMatrix<Complex> B) {

        if (B.getColumnDimension() != ncol) {
            return null; //inconsistent dimensions
        }

        FieldMatrix<Complex> C = new Array2DRowFieldMatrix<>(
                ComplexField.getInstance(), B.getData());

        for (int k = 0; k < ntran; k++) {
            C.preMultiply(U[k]);
        }

        return C;

    }
}
