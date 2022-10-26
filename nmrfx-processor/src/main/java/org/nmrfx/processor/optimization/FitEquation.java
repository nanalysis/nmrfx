package org.nmrfx.processor.optimization;

import org.apache.commons.math3.optim.PointValuePair;

import java.util.Arrays;

/**
 * @author brucejohnson
 */
public abstract class FitEquation {
    double[][] xValues;
    double[][] yValues;
    double[][] errValues;
    double[] bestPars;
    double[] parErrs;

    record Guesses(double[] start, double[] lower, double[] upper) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Guesses guesses = (Guesses) o;
            return Arrays.equals(start, guesses.start) && Arrays.equals(lower, guesses.lower) && Arrays.equals(upper, guesses.upper);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(start);
            result = 31 * result + Arrays.hashCode(lower);
            result = 31 * result + Arrays.hashCode(upper);
            return result;
        }

        @Override
        public String toString() {
            return "Guesses{" +
                    "start=" + Arrays.toString(start) +
                    "\nlower=" + Arrays.toString(lower) +
                    "\nupper=" + Arrays.toString(upper) +
                    '}';
        }
    }

    public abstract  int nY();

    public abstract  int nX();

    public abstract Guesses guess();

    public abstract double[] calcValue(double[] xA, double[] pars);

    public void setXYE(double[][] xValues, double[][] yValues, double[][] errValues) {
        this.xValues = xValues;
        this.yValues = yValues;
        this.errValues = errValues;
    }

    public double[][] getSimValues(double[] first, double[] last, int n) {
        int m = xValues.length + yValues.length;
        double[][] result = new double[m][n];
        double[] xA = new double[xValues.length];
        for (int i = 0; i < n; i++) {
            for (int iX = 0; iX < xA.length; iX++) {
                double x = first[iX] + (last[iX] - first[iX]) / (n - 1) * i;
                xA[iX] = x;
            }
            double[] rpY = calcValue(xA, bestPars);
            for (int iX = 0; iX < xA.length; iX++) {
                result[iX][i] = xA[iX];
            }
            for (int iY = 0; iY < rpY.length; iY++) {
                result[xA.length + iY][i] = rpY[iY];
            }
        }
        return result;
    }

    public double value(double[] pars, double[][] values) {
        int n = values[0].length;
        double sum = 0.0;
        double[] xA = new double[xValues.length];
        for (int i = 0; i < n; i++) {
            for (int iX = 0; iX < xA.length; iX++) {
                xA[iX] = values[iX][i];
            }
            double[] rpY = calcValue(xA, pars);
            for (int j = 0; j < rpY.length; j++) {
                double delta = rpY[j] - values[1 + j][i];
                sum += delta * delta;
            }
        }
        return Math.sqrt(sum / n);
    }


    public double[] getPars() {
        return bestPars;
    }

    public double[] getParErrs() {
        return parErrs;
    }

    public PointValuePair fit() {
        Fitter2 fitter = Fitter2.getArrayFitter(this::value);
        fitter.setXYE(xValues, yValues, errValues);
        var guesses = guess();
        System.out.println(guesses);
        try {
            PointValuePair result = fitter.fit(guesses.start, guesses.lower, guesses.upper, 10.0);
            bestPars = result.getPoint();
            parErrs = fitter.bootstrap(result.getPoint(), 100);
            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

    }
}
