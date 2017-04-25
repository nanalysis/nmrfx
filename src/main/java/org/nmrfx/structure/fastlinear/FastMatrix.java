/*
 * NMRFx Structure : A Program for Calculating Structures 
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

package org.nmrfx.structure.fastlinear;

import java.util.Arrays;
import org.apache.commons.math3.linear.RealMatrix;

/**
 *
 * @author Bruce Johnson
 */
public class FastMatrix {

    final int nRows;
    final int nCols;
    final int size;
    final double[] data;

    public FastMatrix(int nRows, int nCols) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.size = nRows * nCols;
        data = new double[size];
    }

    public void zero() {
        Arrays.fill(data, 0.0);
    }

    public void set(double value) {
        Arrays.fill(data, value);
    }

    public void setEntry(int row, int col, double value) {
        data[row * nCols + col] = value;
    }

    public void subtract(FastMatrix matrix, FastMatrix out) {
        double[] mdata = matrix.data;
        for (int i = 0; i < size; i++) {
            out.data[i] = data[i] - mdata[i];
        }
    }

    public void add(FastMatrix matrix, FastMatrix out) {
        double[] mdata = matrix.data;
        for (int i = 0; i < size; i++) {
            out.data[i] = data[i] + mdata[i];
        }
    }

    public void operate(final FastVector inVec, final FastVector outVec) {
        double[] in = inVec.data;
        for (int row = 0; row < nRows; ++row) {
            double sum = 0;
            for (int i = 0; i < nCols; ++i) {
                sum += data[row * nCols + i] * in[i];
            }
            outVec.data[row] = sum;
        }
    }

    public void transOperate(final FastVector inVec, final FastVector outVec) {
        double[] in = inVec.data;
        for (int row = 0; row < nRows; ++row) {
            double sum = 0;
            for (int i = 0; i < nCols; ++i) {
                sum += data[i * nCols + row] * in[i];
            }
            outVec.data[row] = sum;
        }
    }

    public double vMv(final FastVector inVec) {
        double[] in = inVec.data;
        double sumAll = 0.0;
        for (int row = 0; row < nRows; ++row) {
            double sum = 0.0;
            int rowIndex = row * nCols;
            for (int i = 0; i < nCols; ++i) {
                sum += data[rowIndex + i] * in[i];
            }
            sumAll += sum * in[row];
        }
        return sumAll;
    }

    public void multiply(final FastMatrix matrix, final FastMatrix out) {
        final int nRowsA = nRows;
        final int nColsB = matrix.nCols;
        final int outCols = out.nCols;

        for (int col = 0; col < nColsB; col++) {
            for (int row = 0; row < nRowsA; row++) {
                int rowIndex = row * nCols;
                double sum = 0;
                for (int i = 0; i < nCols; i++) {
                    sum += data[rowIndex + i] * matrix.data[i * nColsB + col];
                }
                out.data[row * outCols + col] = sum;
            }
        }
    }

    public void mMm(FastMatrix matrix, FastMatrix temp, FastMatrix out) {
//                RealMatrix tempMat2 = tempMat.multiply(iBranch.phiMat.transpose()).preMultiply(iBranch.phiMat);
        final int nRowsA = nRows;
        final int nColsB = matrix.nCols;

        for (int col = 0; col < nColsB; col++) {
            for (int row = 0; row < nRowsA; row++) {
                int rowIndex = row * nCols;
                double sum = 0;
                for (int i = 0; i < nCols; i++) {
                    sum += data[rowIndex + i] * matrix.data[matrix.nCols * col + i];
                }
                temp.data[row * temp.nCols + col] = sum;
            }
        }
        for (int col = 0; col < nColsB; col++) {
            for (int row = 0; row < nRowsA; row++) {
                int rowIndex = row * matrix.nCols;
                double sum = 0;
                for (int i = 0; i < nCols; i++) {
                    sum += matrix.data[rowIndex + i] * temp.data[i * temp.nCols + col];
                }
                out.data[row * out.nCols + col] = sum;
            }
        }
    }

    public void check(String name, RealMatrix v) {
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nCols; j++) {
                if (Math.abs(data[i * nCols + j] - v.getEntry(i, j)) > 1.0e-6) {
                    System.out.println(name + " " + i + " " + j + " " + data[i * nCols + j] + " " + v.getEntry(i, j));
                }
            }
        }
    }
}
