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

import org.apache.commons.math3.linear.RealVector;

/**
 * @author Bruce Johnson
 */
public class FastVector {

    final double[] data;

    public FastVector(int nDim) {
        data = new double[nDim];
    }

    public FastVector(double[] data) {
        this.data = data;
    }

    public double[] getValues() {
        return data;
    }

    public void copyFrom(FastVector source) {
        System.arraycopy(source.data, 0, data, 0, data.length);
    }

    public void set(double value) {
        for (int i = 0, len = data.length; i < len; i++) {
            data[i] = value;
        }
    }

    public double getEntry(int i) {
        return data[i];
    }

    public void setEntry(int i, double value) {
        data[i] = value;
    }

    public void copyFrom(FastVector vec, int start) {
        System.arraycopy(vec.data, 0, data, start, vec.data.length);
    }

    public void zero() {
        set(0.0);
    }

    public void subtract(FastVector v2, FastVector v3) {
        for (int i = 0, len = data.length; i < len; i++) {
            v3.data[i] = data[i] - v2.data[i];
        }
    }

    public void add(FastVector v2, FastVector v3) {
        for (int i = 0, len = data.length; i < len; i++) {
            v3.data[i] = data[i] + v2.data[i];
        }
    }

    public void divide(double value) {
        for (int i = 0, len = data.length; i < len; i++) {
            data[i] /= value;
        }
    }

    public void multiply(double value) {
        for (int i = 0, len = data.length; i < len; i++) {
            data[i] *= value;
        }
    }

    public void multiply(double value, FastVector out) {
        for (int i = 0, len = data.length; i < len; i++) {
            out.data[i] = data[i] * value;
        }
    }

    public double dotProduct(FastVector v2) {
        double sum = 0.0;
        for (int i = 0, len = data.length; i < len; i++) {
            sum += data[i] * v2.data[i];
        }
        return sum;
    }

    public double dotProduct(double[] data2) {
        double sum = 0.0;
        for (int i = 0, len = data.length; i < len; i++) {
            sum += data[i] * data2[i];
        }
        return sum;
    }

    public double getNorm() {
        double sumSq = 0.0;
        for (int i = 0, len = data.length; i < len; i++) {
            sumSq += data[i] * data[i];
        }
        return Math.sqrt(sumSq);

    }

    public void normalize() {
        double norm = getNorm();
        double invNorm = 1.0 / norm;
        for (int i = 0, len = data.length; i < len; i++) {
            data[i] *= invNorm;
        }
    }

    public void crossProduct(FastVector v2, FastVector v3) {
        double x = data[1] * v2.data[2] - data[2] * v2.data[1];
        double y = data[2] * v2.data[0] - data[0] * v2.data[2];
        double z = data[0] * v2.data[1] - data[1] * v2.data[0];
        v3.data[0] = x;
        v3.data[1] = y;
        v3.data[2] = z;
    }

    public void outerProduct(FastVector v, FastMatrix out) {
        final double[] vData = v.data;
        final double[] oData = out.data;
        final int nCols = out.nCols;
        final int m = data.length;
        final int n = vData.length;
        for (int i = 0; i < m; i++) {
            int rowOffset = i * nCols;
            for (int j = 0; j < n; j++) {
                oData[rowOffset + j] = data[i] * vData[j];
            }
        }

    }

    public void check(String name, RealVector v) {
        for (int i = 0, len = data.length; i < len; i++) {
            if (Math.abs(data[i] - v.getEntry(i)) > 1.0e-6) {
                System.out.println(name + " " + i + " " + data[i] + " " + v.getEntry(i));
            }
        }

    }

    public boolean compare(FastVector v2, double tol) {
        for (int i = 0, len = data.length; i < len; i++) {
            if (Math.abs(data[i] - v2.data[i]) > tol) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0, len = data.length; i < len; i++) {
            if (i > 0) {
                sBuilder.append(" ");
            }
            sBuilder.append(data[i]);
        }
        return sBuilder.toString();
    }
}
