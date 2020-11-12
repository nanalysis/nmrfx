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
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.DefaultRealMatrixChangingVisitor;
import org.apache.commons.math3.linear.DefaultRealMatrixPreservingVisitor;
import org.apache.commons.math3.linear.RealMatrixChangingVisitor;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author michael
 */
public class MatrixUtil {

    static class AsinChanger extends DefaultRealMatrixChangingVisitor {

        @Override
        public double visit(int row, int column, double value) {
            return Math.asin(value);
        }
    }

    static class FunctionChanger extends DefaultRealMatrixChangingVisitor {

        final UnivariateFunction function;

        FunctionChanger(UnivariateFunction function) {
            this.function = function;
        }

        @Override
        public double visit(int row, int column, double value) {
            return function.value(value);
        }
    }

    /*set mean [eval $stat mean $dl]
     set min [eval $stat min $dl]
     set max [eval $stat max $dl]
     set median [eval $stat median $dl]
     set sV [eval $stat sampleVariance $dl $mean]
     set sSD [eval $stat sampleStandardDeviation [$dl size] $sV]
     */
    static class ScaleFunction implements UnivariateFunction {

        final double scale;

        ScaleFunction(DescriptiveStatistics stats, String mode) {

            switch (mode) {
                case "none":
                    scale = 1.0;
                    break;
                case "stddev":
                    scale = stats.getStandardDeviation();
                    break;
                case "pareto":
                    scale = Math.sqrt(stats.getStandardDeviation());
                    break;
                case "vast":
                    double stDev = stats.getStandardDeviation();
// fixme can't do following with 0.0 mean
                    scale = (stDev * stDev) / stats.getMean();
                    break;
                case "range":
                    scale = stats.getMax() - stats.getMin();
                    break;
                case "level":
                    scale = stats.getMean();
                    break;
                case "levelmed":
                    scale = stats.getPercentile(50.0);
                    break;
                default:
// fixme throw exception
                    scale = 1.0;
                    break;
            }
        }

        @Override
        public double value(double value) {
            return value / scale;
        }

        public double getScale() {
            return scale;
        }
    }

    static class PositiveFunction implements UnivariateFunction {

        @Override
        public double value(double value) {
            return Math.max(value, 0.0);
        }
    }

    static class SqrtFunction implements UnivariateFunction {

        @Override
        public double value(double value) {
            if (value > 0.0) {
                return Math.sqrt(value);
            } else {
                return 0.0;
            }
        }
    }

    static class GLogFunction implements UnivariateFunction {
// z=ln(y+ y2+λ)

        final double y0;
        final double lambda;

        GLogFunction(double y0, double lambda) {
            this.y0 = y0;
            this.lambda = lambda;
        }

        @Override
        public double value(double value) {
            double cvalue = value - y0;
            return Math.log(cvalue + Math.sqrt(cvalue * cvalue + lambda));
        }
    }

    static class OffsetFunction implements UnivariateFunction {

        final double offset;

        OffsetFunction(DescriptiveStatistics stats, String mode) {

            switch (mode) {
                case "none":
                    offset = 0.0;
                    break;
                case "mean":
                    offset = stats.getMean();
                    break;
                case "median":
                    offset = stats.getPercentile(50.0);
                    break;
                case "min":
                    offset = stats.getMin();
                    break;
                default:
// fixme throw exception
                    offset = 0.0;
                    break;
            }
        }

        @Override
        public double value(double value) {
            return value - offset;
        }
    }

    public static void asin(RealMatrix matrix) {
        DefaultRealMatrixChangingVisitor visitor = new AsinChanger();
        matrix.walkInOptimizedOrder(visitor);
    }

    public static void apply(RealMatrix matrix, RealMatrixChangingVisitor visitor) {
        matrix.walkInOptimizedOrder(visitor);
    }

    public static void apply(RealMatrix matrix, UnivariateFunction visitorFunction) {
        DefaultRealMatrixChangingVisitor visitor = new FunctionChanger(visitorFunction);
        matrix.walkInOptimizedOrder(visitor);
    }

    static double transformVector(RealVector vector, String centerMode, String scaleMode) {
        DescriptiveStatistics stats = new DescriptiveStatistics(vector.toArray());
        double scale = 1.0;
        switch (centerMode) {
            case "positive":
                {
                    UnivariateFunction offsetFunction = new PositiveFunction();
                    vector.mapToSelf(offsetFunction);
                    break;
                }
            case "sqrt":
                {
                    UnivariateFunction scaleFunction = new SqrtFunction();
                    vector.mapToSelf(scaleFunction);
                    UnivariateFunction offsetFunction = new OffsetFunction(stats, centerMode);
                    vector.mapToSelf(offsetFunction);
                    break;
                }
            default:
                {
                    UnivariateFunction offsetFunction = new OffsetFunction(stats, centerMode);
                    vector.mapToSelf(offsetFunction);
                    ScaleFunction scaleFunction = new ScaleFunction(stats, scaleMode);
                    scale = scaleFunction.getScale();
                    vector.mapToSelf(scaleFunction);
                    break;
                }
        }
        return scale;

    }

    public static double[] transformColumns(RealMatrix matrix, String centerMode, String scaleMode) {
        int nCols = matrix.getColumnDimension();
        int firstCol = 0;
        int lastCol = nCols - 1;
        double[] scales = new double[nCols];
        for (int iCol = firstCol; iCol <= lastCol; iCol++) {
            RealVector vector = matrix.getColumnVector(iCol);
            scales[iCol] = transformVector(vector, centerMode, scaleMode);
            matrix.setColumnVector(iCol, vector);
        }
        return scales;
    }

    public static double[] transformRows(RealMatrix matrix, String centerMode, String scaleMode) {
        int nRows = matrix.getRowDimension();
        int firstRow = 0;
        int lastRow = nRows - 1;
        double[] scales = new double[nRows];
        for (int iRow = firstRow; iRow <= lastRow; iRow++) {
            RealVector vector = matrix.getRowVector(iRow);
            scales[iRow] = transformVector(vector, centerMode, scaleMode);
            matrix.setRowVector(iRow, vector);
        }
        return scales;
    }

    public static double[] glogColumns(RealMatrix matrix, double y0, double lambda) {
        int nCols = matrix.getColumnDimension();
        int firstCol = 0;
        int lastCol = nCols - 1;
        double[] scales = new double[nCols];
        UnivariateFunction glog = new GLogFunction(y0, lambda);
        for (int iCol = firstCol; iCol <= lastCol; iCol++) {
            RealVector vector = matrix.getColumnVector(iCol);
            vector.mapToSelf(glog);
            matrix.setColumnVector(iCol, vector);
            scales[iCol] = 1.0;
        }
        return scales;
    }

    public static double[] glogRows(RealMatrix matrix, double y0, double lambda) {
        int nRows = matrix.getRowDimension();
        int firstRow = 0;
        int lastRow = nRows - 1;
        double[] scales = new double[nRows];
        UnivariateFunction glog = new GLogFunction(y0, lambda);
        for (int iRow = firstRow; iRow <= lastRow; iRow++) {
            RealVector vector = matrix.getRowVector(iRow);
            vector.mapToSelf(glog);
            matrix.setRowVector(iRow, vector);
            scales[iRow] = 1.0;
        }
        return scales;
    }

    public static RealMatrix reduce(RealMatrix A, int newSize) {
        int nRows = A.getRowDimension();
        int nColumns = A.getColumnDimension();
        SingularValueDecomposition svd = new SingularValueDecomposition(A);
        int uR = svd.getU().getRowDimension();
        int uC = svd.getU().getColumnDimension();
        int vR = svd.getVT().getRowDimension();
        int vC = svd.getVT().getColumnDimension();
        RealMatrix U = svd.getU().getSubMatrix(0, nRows - 1, 0, newSize - 1);
        RealMatrix Vt = svd.getVT().getSubMatrix(0, newSize - 1, 0, nColumns - 1);
        final double[] svs = svd.getSingularValues();
        U.walkInOptimizedOrder(new DefaultRealMatrixChangingVisitor() {
            @Override
            public double visit(int row, int column, double value) {
                return value * svs[column];
            }
        }
        );

        RealMatrix newA = U.multiply(Vt);
        double norm = newA.subtract(A).getFrobeniusNorm();
        return newA;
    }

    public static RealMatrix getCovariance(RealMatrix A, final double minSingularValue) {
        // get the number of singular values to consider
        SingularValueDecomposition svd = new SingularValueDecomposition(A);
        final double[] singularValues = svd.getSingularValues();
        final int p = singularValues.length;
        int dimension = 0;
        while (dimension < p
                && singularValues[dimension] >= minSingularValue) {
            ++dimension;
        }

        if (dimension == 0) {
            throw new NumberIsTooLargeException(LocalizedFormats.TOO_LARGE_CUTOFF_SINGULAR_VALUE,
                    minSingularValue, singularValues[0], true);
        }

        final double[][] data = new double[dimension][p];
        svd.getVT().walkInOptimizedOrder(new DefaultRealMatrixPreservingVisitor() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void visit(final int row, final int column,
                    final double value) {
                data[row][column] = value * singularValues[row];
            }
        }, 0, dimension - 1, 0, p - 1);

        RealMatrix jv = new Array2DRowRealMatrix(data, false);
        return jv.transpose().multiply(jv);
    }

    public static RealMatrix getCovarianceHorizontal(RealMatrix A, final double minSingularValue) {
        // get the number of singular values to consider
        SingularValueDecomposition svd = new SingularValueDecomposition(A);
        final double[] singularValues = svd.getSingularValues();
        final int p = singularValues.length;
        int dimension = 0;
        while (dimension < p
                && singularValues[dimension] >= minSingularValue) {
            ++dimension;
        }

        if (dimension == 0) {
            throw new NumberIsTooLargeException(LocalizedFormats.TOO_LARGE_CUTOFF_SINGULAR_VALUE,
                    minSingularValue, singularValues[0], true);
        }

        final double[][] data = new double[dimension][p];
        svd.getU().walkInOptimizedOrder(new DefaultRealMatrixPreservingVisitor() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void visit(final int row, final int column,
                    final double value) {
                data[row][column] = value * singularValues[row];
            }
        }, 0, dimension - 1, 0, p - 1);

        RealMatrix ju = new Array2DRowRealMatrix(data, false);
        return ju.multiply(ju.transpose());
    }

}
