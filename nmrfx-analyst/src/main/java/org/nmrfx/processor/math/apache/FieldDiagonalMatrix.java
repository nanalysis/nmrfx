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

import org.apache.commons.math3.Field;
import org.apache.commons.math3.FieldElement;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.AbstractFieldMatrix;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.util.MathArrays;

import java.io.Serializable;

public class FieldDiagonalMatrix<T extends FieldElement<T>> extends AbstractFieldMatrix<T> implements
        Serializable {

    /**
     * Serializable version identifier.
     */
    private static final long serialVersionUID = 20121229L;
    /**
     * Entries of the diagonal.
     */
    private final T[] data;
    /**
     * Field to which the elements belong.
     */
    private final Field<T> field;

    /**
     * Get a reference to the underlying data array. This methods returns internal data, <strong>not</strong> a fresh
     * copy of it.
     *
     * @return the 2-dimensional array of entries.
     */
    public T[] getDataRef() {
        return data;
    }

    /**
     * Create a new {@code FieldDiagonalMatrix<T>} with the supplied row and column dimensions.
     *
     * @param field           Field to which the elements belong.
     * @param rowDimension    Number of rows in the new matrix.
     * @param columnDimension Number of columns in the new matrix.
     * @throws NotStrictlyPositiveException if row or column dimension is not positive.
     */
    public FieldDiagonalMatrix(final Field<T> field, final int rowDimension,
                               final int columnDimension)
            throws NotStrictlyPositiveException {
        super(field, rowDimension, columnDimension);
        this.field = field;
        data = MathArrays.buildArray(field, rowDimension);
    }

    /**
     * Creates a matrix with the supplied dimension.
     *
     * @param dimension Number of rows and columns in the new matrix.
     * @throws NotStrictlyPositiveException if the dimension is not positive.
     */
    public FieldDiagonalMatrix(final Field<T> field, final int dimension)
            throws NotStrictlyPositiveException {
        super(field, dimension, dimension);
        data = MathArrays.buildArray(field, dimension);
        this.field = field;
    }

    /**
     * Constructs a Zdiagmat and initializes it to a constant.
     *
     * @param order The order of the new Zdiagmat
     * @param val   The value to which the diagonal is to be initialized
     * @return A Zdiagmat whose diagonal is val.
     */
    public FieldDiagonalMatrix(final int dimension, T val) {
        field = val.getField();
        data = MathArrays.buildArray(field, dimension);
        for (int i = 0; i < dimension; i++) {
            data[i] = val;
        }
    }

    /**
     * Constructs a FieldDiagonalMatrix and initializes it to an array of T.
     *
     * @param val A Z1
     * @return A Zdiagmat whose diagonal elements are the elements of val.
     */
    public FieldDiagonalMatrix(T[] val) {
        field = val[0].getField();
        data = MathArrays.buildArray(field, val.length);
        for (int i = 0; i < val.length; i++) {
            data[i] = val[i];
        }
    }

    /**
     * Constructs a FieldDiagonalMatrix and initializes it to the k'th diagonal of a FieldMatrix<T>.
     *
     * @param A The matrix that we copy the diagonal from.
     * @param k The diagonal. For k=0 gives the princpal diagonal; k>0, the kth superdiagonal; k<0, the kth subdiagonal.
     */
    public FieldDiagonalMatrix(FieldMatrix<T> A, int k) throws DimensionMismatchException {
        field = A.getField();
        if (k >= 0) {
            if (k >= A.getColumnDimension()) {
                throw new DimensionMismatchException(A.getRowDimension(), A.getColumnDimension());
            }
            final int dimension = Math.min(A.getRowDimension(), A.getColumnDimension() - k);

            data = MathArrays.buildArray(field, dimension);
            for (int i = 0; i < dimension; i++) {
                data[i] = A.getEntry(i, i + k);
            }
        } else {
            k = -k;
            if (k >= A.getRowDimension()) {
                throw new DimensionMismatchException(A.getRowDimension(), A.getColumnDimension());
            }
            final int order = Math.min(A.getRowDimension() - k, A.getColumnDimension());

            data = MathArrays.buildArray(field, order);
            for (int i = 0; i < order; i++) {
                data[i] = A.getRow(i + k)[i];
            }
        }
    }

    /**
     * Constructs a FieldDiagonalMatrix and initializes it to the diagonal of a Field Matrix.
     *
     * @param A A FieldMatrix
     */
    public FieldDiagonalMatrix(FieldMatrix<T> A) {
        this(A, 0);
    }

    /**
     * Constructs a FieldDiagonalMatrix and initializes it from another FieldDiagonalMatrix<T>
     *
     * @param D A FieldDiagonalMatrix<T> to copy.
     */
    public FieldDiagonalMatrix(FieldDiagonalMatrix<T> D) {
        field = D.getField();
        data = MathArrays.buildArray(field, D.getRowDimension());

        for (int i = 0; i < D.getRowDimension(); i++) {
            data[i] = D.getEntry(i, i);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRowDimension() {
        return data.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getColumnDimension() {
        return data.length;
    }

    @Override
    public FieldDiagonalMatrix<T> createMatrix(int rowDimension, int columnDimension)
            throws NotStrictlyPositiveException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public FieldDiagonalMatrix<T> copy() {
        return new FieldDiagonalMatrix<>(this);
    }

    @Override
    public T getEntry(final int row, final int column) throws OutOfRangeException {
        MatrixUtils.checkMatrixIndex(this, row, column);
        return row == column ? data[row] : field.getZero();
    }

    /**
     * {@inheritDoc}
     *
     * @throws NumberIsTooLargeException if {@code row != column} and value is non-zero.
     */
    @Override
    public void setEntry(final int row, final int column, T value)
            throws OutOfRangeException, NumberIsTooLargeException {
        if (row == column) {
            MatrixUtils.checkRowIndex(this, row);
            data[row] = value;
        }
    }

    @Override
    public void addToEntry(int row, int column, T increment)
            throws OutOfRangeException {
        if (row == column) {
            MatrixUtils.checkRowIndex(this, row);
            data[row] = data[row].add(increment);
        }
    }

    @Override
    public void multiplyEntry(int row, int column, T factor)
            throws OutOfRangeException {
        if (row == column) {
            MatrixUtils.checkRowIndex(this, row);
            data[row] = data[row].multiply(factor);
        }
    }
}
