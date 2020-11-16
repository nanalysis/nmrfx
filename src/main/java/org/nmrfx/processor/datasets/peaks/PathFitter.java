/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.commons.math3.optim.PointValuePair;
import org.nmrfx.peaks.PeakPaths;
import org.nmrfx.peaks.PeakPaths.PATHMODE;
import org.nmrfx.peaks.PeakPath;
import org.nmrfx.peaks.PeakDistance;
import org.nmrfx.processor.optimization.Fitter;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.regression.OLS;
import smile.regression.LinearModel;

/**
 *
 * @author brucejohnson
 */
public class PathFitter {

    PATHMODE pathMode;
    List<PeakPath> currentPaths = new ArrayList<>();
    boolean fit0 = false;
    boolean fitLog = false;
    double[] bestPars;
    double[] parErrs;
    double[][] xValues;
    double[][] yValues;
    double[] errValues;
    int nPaths;
    int nDims = 2;
    double pScale = 0.001;

    class PathFunction implements BiFunction<double[], double[][], Double> {

        double yCalc(double a, double b, double c, double x, double p) {
            double dP = c - a;
            double kD = fitLog ? Math.pow(10.0, b) : b;
            double n1 = p + x + kD;
            double s1 = Math.sqrt(n1 * n1 - 4.0 * x * p);
            double yCalc = a + dP * (n1 - s1) / (2.0 * p);
            return yCalc;

        }

        @Override
        public Double apply(double[] pars, double[][] values) {
            double a = fit0 ? pars[0] : 0.0;
            double b = fit0 ? pars[1] : pars[0];
            double sum = 0.0;
            int n = values[0].length;
            for (int i = 0; i < n; i++) {
                int iOff = (int) Math.round(values[2][i]);
                double c = fit0 ? pars[2 + iOff] : pars[1 + iOff];
                double x = values[0][i];
                double p = values[1][i];
                double y = values[3][i];
                double yCalc = yCalc(a, b, c, x, p);

                double delta = yCalc - y;
                sum += delta * delta;

            }
            double value = Math.sqrt(sum / n);
            return value;
        }

        public double[] getGuess(double[] x, double[] y, int[] indices) {
            int nPars = 1 + nPaths;

            double[] result = new double[nPars];
            for (int iPath = 0; iPath < nPaths; iPath++) {
                double yMax = Fitter.getMaxValue(y, indices, iPath);
                double yAtMinX = Fitter.getYAtMinX(x, y, indices, iPath);
                double xMid = Fitter.getMidY0(x, y, indices, iPath);
                result[1 + iPath] = yMax;
                result[0] += fitLog ? Math.log10(xMid) : xMid;
            }
            result[0] /= nPaths;
            return result;
        }

        public double[][] getSimValues(double[] pars, double first, double last, int n, double p) {
            double a = fit0 ? pars[0] : 0.0;
            double b = fit0 ? pars[1] : pars[0];
            double c = fit0 ? pars[2] : pars[1];

            double[][] result = new double[2][n];
            double delta = (last - first) / (n - 1);
            for (int i = 0; i < n; i++) {
                double x = first + delta * i;
                double y = yCalc(a, b, c, x, p);
                result[0][i] = x;
                result[1][i] = y;
            }
            return result;
        }

    }

    class PressureFunction implements BiFunction<double[], double[][], Double> {

        double yCalc(double b, double c, double x) {
            double yCalc = b * x + c * x * x;
            return yCalc;

        }

        @Override
        public Double apply(double[] pars, double[][] values) {
            double sum = 0.0;
            int n = values[0].length;
            for (int i = 0; i < n; i++) {
                int j = i % nDims;
                double b = pars[2 * j];
                double c = pars[2 * j + 1];
                double x = values[0][i];
                double y = values[1][i];
                double yCalc = yCalc(b, c, x);

                double delta = yCalc - y;
                sum += delta * delta;

            }
            double value = Math.sqrt(sum / n);
            return value;
        }

        public double[] getGuess(double[] x, double[] y) {
            double[] guess = new double[nDims * 2];
            return guess;
        }

        public double[][] getSimValues(double[] pars, double first, double last, int n, double p) {
            double b = pars[0];
            double c = pars[1];
            double[][] result = new double[2][n];
            double delta = (last - first) / (n - 1);
            for (int i = 0; i < n; i++) {
                double x = first + delta * i;
                double y = yCalc(b, c, x);
                result[0][i] = x;
                result[1][i] = y;
            }
            return result;
        }

    }

    void fitPressure() {
        int nSim = 100;
        int nPar = nDims * 3;
        int n = xValues[0].length;
        double[][] x = new double [n][3];
//        double[][] x = new double[n][2];
//        double[] y = new double[n];
        double[][] parValues = new double[nDims * 3][nSim];
        bestPars = new double[nDims * 3];
        parErrs = new double[nDims * 3];
        for (int iDim = 0; iDim < nDims; iDim++) {
            for (int i = 0; i < n; i++) {
                double p = xValues[0][i] * pScale;
                for (int iP = 0; iP < 2; iP++) {
                    x[i][iP] = Math.pow(p, iP + 1.0) / (iP + 1.0);
                }
//                y[i] = yValues[iDim][i];
                x[i][2] = yValues[iDim][i];
                  
            }
            System.out.println("ols");
            String[] colNames = {"a", "b", "y"};
            DataFrame dataframe = DataFrame.of(x, colNames);
            Formula f = Formula.lhs("y");
//            OLS ols = new OLS(x, y);
            LinearModel model = OLS.fit(f, dataframe);
//            System.out.println("ols " + ols.RSS());
//            double[][] ppars = ols.ttest();
            double [][] ppars = model.ttest();
            bestPars[iDim * 3 + 1] = ppars[0][0];
            bestPars[iDim * 3 + 2] = ppars[1][0];
            bestPars[iDim * 3] = ppars[2][0];
            parErrs[iDim * 3 + 1] = ppars[0][1];
            parErrs[iDim * 3 + 2] = ppars[1][1];
            parErrs[iDim * 3] = ppars[2][1];
            System.out.println("ols pars");

//            Bootstrap boot = new Bootstrap(y.length, nSim);
//            double[][] xs = new double[n][2];
//            double[] ys = new double[n];
//            for (int iSim = 0; iSim < nSim; iSim++) {
//                for (int j = 0; j < n; j++) {
//                    int index = boot.train[iSim][j];
//                    xs[j][0] = x[index][0];
//                    xs[j][1] = x[index][1];
//                    ys[j] = y[index];
//                    ols = new OLS(xs, ys);
//                    double[] sppars = ols.fittedValues();
//                    parValues[iDim * 3][iSim] = ols.intercept();
//                    parValues[iDim * 3 + 1][iSim] = ppars[0];
//                    parValues[iDim * 3 + 2][iSim] = ppars[1];
//
//                }
//            }
        }
//        parErrs = new double[nPar];
//        for (int i = 0; i < nPar; i++) {
//            DescriptiveStatistics dStat = new DescriptiveStatistics(parValues[i]);
//            parErrs[i] = dStat.getStandardDeviation();
//        }        for (int iPath = 0; iPath < nPaths; iPath++) {
        for (int iPath = 0; iPath < nPaths; iPath++) {
            PeakPath path = currentPaths.get(iPath);
            path.setFitPars(bestPars);
            path.setFitErrs(parErrs);
        }

    }

    public double[] getPars() {
        return bestPars;
    }

    public double[] getParErrs() {
        return parErrs;
    }

    public double[][] getSimValues(double[] pars, double first, double last, int n, double p) {
        PathFunction fun = new PathFunction();
        return fun.getSimValues(pars, first, last, n, p);
    }

    public double[][] getPressureSimValues(double[] pars, double first, double last, int n) {
        PathFunction fun = new PathFunction();
        double[][] xy = new double[nDims + 1][n];
        for (int iDim = 0; iDim < nDims; iDim++) {
            double delta = (last - first) / (n - 1);
            for (int j = 0; j < n; j++) {
                double p = first + delta * j;
                xy[0][j] = p;
                p *= pScale;
                double y = pars[iDim * 3];
                for (int iP = 0; iP < 2; iP++) {
                    double x = Math.pow(p, iP + 1.0) / (iP + 1.0);
                    y += x * pars[iDim * 3 + 1 + iP];
                }
                xy[1 + iDim][j] = y;
            }
        }

        return xy;
    }

    public double[][] getX() {
        return xValues;
    }

    public double[][] getY() {
        return yValues;
    }

    public void setup(PeakPaths peakPath, PeakPath path) {
        pathMode = peakPath.getPathMode();
        currentPaths.clear();
        currentPaths.add(path);
        double[][] iVars = peakPath.getXValues();
        List<PeakDistance> peakDists = path.getPeakDistances();
        int i = 0;
        double errValue = 0.1;
        int nX = pathMode == PATHMODE.PRESSURE ? 1 : 3;
        List<double[]> values = new ArrayList<>();
        for (PeakDistance peakDist : peakDists) {
            if (peakDist != null) {
                if (pathMode == PATHMODE.TITRATION) {
                    double[] row = {iVars[0][i], iVars[1][i], peakDist.getDistance(), errValue};
//                System.out.printf("%2d %.3f %.3f %.3f %.3f\n", i, row[0], row[1], row[2], row[3]);
                    values.add(row);
                } else {
                    double[] row = {iVars[0][i], peakDist.getDelta(0), peakDist.getDelta(1), errValue};
                    values.add(row);
                }
            }
            i++;
        }
        int n = values.size();
        if (pathMode == PATHMODE.TITRATION) {
            xValues = new double[nX][n];
            yValues = new double[1][n];
        } else {
            xValues = new double[nX][n];
            yValues = new double[2][n];
        }
        errValues = new double[n];
        i = 0;
        for (double[] v : values) {
            if (pathMode == PATHMODE.TITRATION) {
                xValues[0][i] = v[0];
                xValues[1][i] = v[1];
                xValues[2][i] = 0.0;
                yValues[0][i] = v[2];
            } else {
                xValues[0][i] = v[0];
                yValues[0][i] = v[1];
                yValues[1][i] = v[2];
            }
            errValues[i] = v[3];
            i++;
        }
        nPaths = 1;
    }

    public void setup(PeakPaths peakPath, List<PeakPath> paths) {
        pathMode = peakPath.getPathMode();
        currentPaths.clear();
        currentPaths.addAll(paths);
        double[][] iVars = peakPath.getXValues();
        List<double[]> values = new ArrayList<>();
        List<Integer> pathIndices = new ArrayList<>();
        int iPath = 0;
        int nX = pathMode == PATHMODE.PRESSURE ? 1 : 3;
        for (PeakPath path : paths) {
            List<PeakDistance> peakDists = path.getPeakDistances();
            int i = 0;
            double errValue = 0.1;

            for (PeakDistance peakDist : peakDists) {
                if (peakDist != null) {
                    if (pathMode == PATHMODE.TITRATION) {
                        double[] row = {iVars[0][i], iVars[1][i], peakDist.getDistance(), errValue};
//                System.out.printf("%2d %.3f %.3f %.3f %.3f\n", i, row[0], row[1], row[2], row[3]);
                        values.add(row);
                    } else {
                        double[] row = {iVars[0][i], peakDist.getDelta(0), peakDist.getDelta(1), errValue};
                        values.add(row);
                    }
                    pathIndices.add(iPath);
                }
                i++;
            }
            iPath++;
        }
        int n = values.size();
        if (pathMode == PATHMODE.TITRATION) {
            xValues = new double[nX][n];
            yValues = new double[1][n];
        } else {
            xValues = new double[nX][n];
            yValues = new double[2][n];
        }
        errValues = new double[n];

        int i = 0;
        for (double[] v : values) {
            if (pathMode == PATHMODE.TITRATION) {
                xValues[0][i] = v[0];
                xValues[1][i] = v[1];
                xValues[2][i] = pathIndices.get(i);
                yValues[0][i] = v[2];
            } else {
                xValues[0][i] = v[0];
                yValues[0][i] = v[1];
                yValues[1][i] = v[2];
            }
            errValues[i] = v[3];
            i++;
        }
        nPaths = paths.size();

    }

    public void fit() throws Exception {
        if (pathMode == PATHMODE.PRESSURE) {
            fitPressure();
        } else {
            fitTitration();
        }

    }

    void fitTitration() throws Exception {
        PathFunction fun = new PathFunction();
        Fitter fitter = Fitter.getArrayFitter(fun::apply);
        fitter.setXYE(xValues, yValues[0], errValues);
        int[] indices = new int[yValues[0].length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = (int) Math.round(xValues[2][i]);
        }

        double[] guess = fun.getGuess(xValues[0], yValues[0], indices);
        double[] lower = new double[guess.length];
        double[] upper = new double[guess.length];
        int iG = 0;
        if (fit0) {
            lower[0] = -guess[2] * 0.1;
            upper[0] = guess[0] + guess[2] * 0.1;
            iG = 1;
        }
        lower[iG] = guess[iG] / 4.0;
        upper[iG] = guess[iG] * 3.0;
        for (int iPath = 0; iPath < nPaths; iPath++) {
            lower[iG + 1 + iPath] = guess[iG + 1 + iPath] / 2.0;
            upper[iG + 1 + iPath] = guess[iG + 1 + iPath] * 2.0;
        }
//        for (int k = 0; k < guess.length; k++) {
//            System.out.printf("%.3f %.3f %.3f\n", lower[k], guess[k], upper[k]);
//        }

        PointValuePair result = fitter.fit(guess, lower, upper, 10.0);
        bestPars = result.getPoint();
        parErrs = fitter.bootstrap(result.getPoint(), 300);
        for (int iPath = 0; iPath < nPaths; iPath++) {
            PeakPath path = currentPaths.get(iPath);
            double[] pars = {bestPars[0], bestPars[iPath + 1]};
            double[] errs = {parErrs[0], parErrs[iPath + 1]};
            path.setFitPars(pars);
            path.setFitErrs(errs);
        }
    }
}
