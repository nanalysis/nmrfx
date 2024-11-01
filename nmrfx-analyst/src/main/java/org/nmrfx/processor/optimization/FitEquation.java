package org.nmrfx.processor.optimization;

import org.apache.commons.math3.optim.PointValuePair;

/**
 * @author brucejohnson
 */
public abstract class FitEquation {
    double[][] xValues;
    double[][] yValues;
    double[][] errValues;
    double[] bestPars;
    double[] parErrs;

    int nY = 1;

    public abstract String[] parNames();

    public abstract int nY();

    public void nY(int nY) {
        this.nY = nY;
    }

    public abstract int nX();

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
        var optResult = fitter.fit(guesses.start(), guesses.lower(), guesses.upper(), 10.0);
        if (optResult.isPresent()) {
            PointValuePair result = optResult.get();
            bestPars = result.getPoint();
            var errResult = fitter.bootstrap(result.getPoint(), 200);
            if (errResult.isPresent()) {
                parErrs = errResult.get();
                return result;
            }
        }
        return null;
    }
}
