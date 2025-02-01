/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.nmrfx.peaks.PeakDistance;
import org.nmrfx.peaks.PeakPath;
import org.nmrfx.peaks.PeakPaths;
import org.nmrfx.peaks.PeakPaths.PATHMODE;
import org.nmrfx.processor.optimization.Fitter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class PathFitter {

    PATHMODE pathMode;

    int nState = 2;
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

    void fitPressure() {
        int n = xValues[0].length;
        double[][] x = new double[n][2];
        bestPars = new double[nDims * 3];
        parErrs = new double[nDims * 3];
        for (int iDim = 0; iDim < nDims; iDim++) {
            for (int i = 0; i < n; i++) {
                double p = xValues[0][i] * pScale;
                for (int iP = 0; iP < 2; iP++) {
                    x[i][iP] = Math.pow(p, iP + 1.0) / (iP + 1.0);
                }
            }
            OLSMultipleLinearRegression olsMultipleLinearRegression = new OLSMultipleLinearRegression();
            olsMultipleLinearRegression.newSampleData(yValues[iDim], x);
            double[] pars = olsMultipleLinearRegression.estimateRegressionParameters();
            double[] errs = olsMultipleLinearRegression.estimateRegressionParametersStandardErrors();
            bestPars[iDim * 3] = pars[0];
            bestPars[iDim * 3 + 1] = pars[1];
            bestPars[iDim * 3 + 2] = pars[2];
            parErrs[iDim * 3] = errs[0];
            parErrs[iDim * 3 + 1] = errs[1];
            parErrs[iDim * 3 + 2] = errs[2];
        }

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
        PathFunction fun = pars.length / 2 == 1 ? new PathFunctionSingleState(fit0, fitLog, nPaths) : new PathFunctionTwoState(nPaths);
        return fun.getSimValues(pars, first, last, n, p);
    }

    public double[][] getPressureSimValues(double[] pars, double first, double last, int n) {
        PathFunctionSingleState fun = new PathFunctionSingleState(fit0, fitLog, nPaths);
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
        nDims = peakPath.getNDim();
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
                    values.add(row);
                } else {
                    double[] row = new double[2 + nDims];
                    row[0] = iVars[0][i];
                    for (int j=0;j<nDims;j++) {
                        row[j + 1] = peakDist.getDelta(j);
                    }
                    row[row.length - 1] =errValue;
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
            yValues = new double[nDims][n];
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
                for (int j=0;j<nDims;j++) {
                    yValues[j][i] = v[1 + j];
                }
            }
            errValues[i] = v[3];
            i++;
        }
        nPaths = 1;
    }

    public void nStates(int value) {
        nState = value;
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

    void setBounds(double[] guess, double[] lower, double[] upper, int nState) {
        if (nState == 1) {
            setBoundsSingleState(guess, lower, upper);
        } else {
            setBoundsMultipleState(guess, lower, upper, nState);
        }

    }

    void setBoundsSingleState(double[] guess, double[] lower, double[] upper) {
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
    }

    void setBoundsMultipleState(double[] guess, double[] lower, double[] upper, int nState) {
        for (int iState = 0; iState < nState; iState++) {
            lower[iState] = guess[iState] / 4.0;
            upper[iState] = guess[iState] * 3.0;
        }
        for (int iPath = 0; iPath < nPaths; iPath++) {
            for (int iState = 0; iState < nState; iState++) {
                lower[2 + iState + nState * iPath] = 0.0;
                upper[2 + iState + nState * iPath] = guess[2 + iState + nState * iPath] * 4.0;
            }
        }
    }

    void fitTitration() throws Exception {
        PathFunction fun = nState == 1 ? new PathFunctionSingleState(fit0, fitLog, nPaths) : new PathFunctionTwoState(nPaths);
        Fitter fitter = Fitter.getArrayFitter(fun::apply);
        fitter.setXYE(xValues, yValues[0], errValues);
        int[] indices = new int[yValues[0].length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = (int) Math.round(xValues[2][i]);
        }

        double[] guess = fun.getGuess(xValues[0], yValues[0], indices);
        double[] lower = new double[guess.length];
        double[] upper = new double[guess.length];
        setBounds(guess, lower, upper, nState);

        PointValuePair result = fitter.fit(guess, lower, upper, 10.0, 5);
        bestPars = result.getPoint();
        parErrs = fitter.bootstrap(result.getPoint(), 300);
        for (int iPath = 0; iPath < nPaths; iPath++) {
            PeakPath path = currentPaths.get(iPath);
            double[] pars = new double[nState * 2];
            double[] errs = new double[nState * 2];
            for (int i = 0; i < nState; i++) {
                pars[i] = bestPars[i];
                pars[i + nState] = bestPars[iPath * nState + nState + i];
                errs[i] = parErrs[i];
                errs[i + nState] = parErrs[iPath * nState + nState + i];
            }
            path.setFitPars(pars);
            path.setFitErrs(errs);
        }
    }
}
