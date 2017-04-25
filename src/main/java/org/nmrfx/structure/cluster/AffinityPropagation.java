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

package org.nmrfx.structure.cluster;

import cern.colt.function.IntIntDoubleFunction;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.jet.math.Functions;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author brucejohnson
 */
public class AffinityPropagation {

    SparseDoubleMatrix2D S = null;
    SparseDoubleMatrix2D R = null;
    SparseDoubleMatrix2D A = null;
    Set rowSet = new TreeSet();
    double lambda = 0.5;
    Functions F = Functions.functions;

    static class SparseMax2 implements IntIntDoubleFunction {

        double[] maxValues1 = null;
        double[] maxValues2 = null;

        SparseMax2(int size) {
            maxValues1 = new double[size];
            maxValues2 = new double[size];

            for (int i = 0; i < size; i++) {
                maxValues1[i] = Double.NEGATIVE_INFINITY;
                maxValues2[i] = Double.NEGATIVE_INFINITY;
            }
        }

        public double apply(int row, int column, double value) {
            if (value > maxValues1[row]) {
                maxValues2[row] = maxValues1[row];
                maxValues1[row] = value;
            }
            return value;
        }
    }

    class SparseSumColumn implements IntIntDoubleFunction {

        double[] sum = null;
        double[] sumd = null;

        SparseSumColumn(int size) {
            sum = new double[size];
            sumd = new double[size];
        }

        public double apply(int row, int column, double value) {
            if (row != column) {
                if (value > 0.0) {
                    sum[column] += value;
                }
            } else {
                sumd[column] += value;
            }
            return value;
        }
    }

    class SubMax implements IntIntDoubleFunction {

        double[] maxValues1 = null;
        double[] maxValues2 = null;

        SubMax(double[] maxValues1, double[] maxValues2) {
            this.maxValues1 = maxValues1;
            this.maxValues2 = maxValues2;
        }

        public double apply(int row, int column, double value) {
            if (value == maxValues1[row]) {
                value -= maxValues2[row];
            } else {
                value -= maxValues1[row];
            }
            return value;
        }
    }

    class SubSum implements IntIntDoubleFunction {

        double[] sum = null;
        double[] sumd = null;

        SubSum(double[] sum, double[] sumd) {
            this.sum = sum;
            this.sumd = sumd;
        }

        public double apply(int row, int column, double value) {
            if (row != column) {
                if (value > 0.0) {
                    value = sum[column] - value;
                } else {
                    value = sum[column];
                }
            } else {
                value = sumd[column] - value;
            }
            return value;
        }
    }

    public AffinityPropagation(int n) {
        S = new SparseDoubleMatrix2D(n, n);
        A = new SparseDoubleMatrix2D(n, n);
        R = new SparseDoubleMatrix2D(n, n);
    }

    public void setSimilarities(int i, int j, double s) {
        S.set(i, j, s);
        R.set(i, j, 0.0);
        A.set(i, j, 0.0);
        rowSet.add(Integer.valueOf(i));
    }

    public void initSelf() {
    }

    public void updateResponsibilities() {
        SparseDoubleMatrix2D holdR = (SparseDoubleMatrix2D) R.copy();
        SparseDoubleMatrix2D AS = (SparseDoubleMatrix2D) A.copy();
        AS.assign(S, F.plus);
        System.out.println("assigned");
        SparseMax2 sMax = new SparseMax2(S.rows());
        AS.forEachNonZero(sMax);
        System.out.println("foreached");
        SubMax subMax = new SubMax(sMax.maxValues1, sMax.maxValues2);
        R.forEachNonZero(subMax);
        System.out.println("submaxed");

        // Dampen Responsibilities
        R.assign(F.mult(0.0 - lambda));
        holdR.assign(F.mult(lambda));
        R.assign(holdR, F.plus);
    }

    public void updateAvailabilities() {
        SparseDoubleMatrix2D holdA = (SparseDoubleMatrix2D) A.copy();
        SparseDoubleMatrix2D Rp = (SparseDoubleMatrix2D) R.copy();

        SparseSumColumn sumColumn = new SparseSumColumn(Rp.rows());
        SparseDoubleMatrix2D Rpsum = (SparseDoubleMatrix2D) Rp.copy();
        Rpsum.forEachNonZero(sumColumn);

        SubSum subSum = new SubSum(sumColumn.sum, sumColumn.sumd);

        Rpsum.forEachNonZero(subSum);

        // Dampen availabilities
        A.assign(F.mult(1.0 - lambda));
        holdA.assign(F.mult(lambda));
        A.assign(holdA, F.plus);
    }
}
