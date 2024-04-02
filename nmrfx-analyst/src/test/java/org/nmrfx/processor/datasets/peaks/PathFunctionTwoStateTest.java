package org.nmrfx.processor.datasets.peaks;

import junit.framework.TestCase;
import org.apache.commons.math3.optim.PointValuePair;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.optimization.Fitter;

import java.util.Arrays;
import java.util.Random;

public class PathFunctionTwoStateTest extends TestCase {

    @Test
    public void testYCalc() {
        PathFunctionSingleState singleState = new PathFunctionSingleState(false, false, 1);
        PathFunctionTwoState twoState = new PathFunctionTwoState(1);
        double kd1 = 50.0;
        double kd2 = 1e9;
        double dB1 = 1.0;
        double dB2 = 0.0;
        double x = 50.0;
        double p = 1.0;
        double v1 = singleState.yCalc(0.0, kd1, dB1, x, p);
        double v2 = twoState.yCalc(kd1, kd2, dB1, dB2, x, p);
        double v3 = twoState.yCalc(kd2, kd1, dB2, dB1, x, p);
        double v4 = twoState.yCalc(kd1, kd1, dB1 / 2.0, dB1 / 2.0, x, p);
        double vExp = p / 2.0;
        Assert.assertEquals(vExp, v1, 0.01);
        Assert.assertEquals(v1, v2, 1.0e-3);
        Assert.assertEquals(v1, v3, 1.0e-3);
        Assert.assertEquals(v1, v4, 0.01);
    }

    private double[][] setupFit(double kD, double dY, double eVal) {
        double[] lig = {0.0, 5.0, 10.0, 20.0, 50.0, 90.0, 125.0, 200.0, 400.0, 600.0, 1000.0};
        double[] p = new double[lig.length];
        Arrays.fill(p, kD / 2.0);

        double[] y = new double[lig.length];
        double[] err = new double[lig.length];
        double[] indices = new double[lig.length];
        PathFunctionSingleState singleState = new PathFunctionSingleState(false, false, 1);
        Random random = new Random();
        for (int i = 0; i < lig.length; i++) {
            y[i] = singleState.yCalc(0.0, kD, dY, lig[i], p[i]) + random.nextGaussian() * eVal;
            err[i] = eVal;
        }
        double[][] result = {lig, p, indices, y, err};
        return result;
    }

    private double[][] setupFitTwo(double kD1, double kD2, double dY1, double dY2, double eVal) {
        //double[] lig = {0.0, 5.0, 10.0, 20.0, 50.0, 90.0, 125.0, 200.0, 400.0, 600.0, 1000.0, 1500.0, 2000.0};
        int nLig = 100;
        double[] lig = new double[nLig];
        for (int i = 0; i < nLig; i++) {
            lig[i] = i * 2000.0 / (nLig - 1);
        }
        double[] p = new double[lig.length];
        Arrays.fill(p, kD1 / 2.0);

        double[] y = new double[lig.length];
        double[] err = new double[lig.length];
        double[] indices = new double[lig.length];
        PathFunctionTwoState singleState = new PathFunctionTwoState(1);
        Random random = new Random();
        for (int i = 0; i < lig.length; i++) {
            y[i] = singleState.yCalc(kD1, kD2, dY1, dY2, lig[i], p[i]) + random.nextGaussian() * eVal;
            err[i] = eVal;
        }
        double[][] result = {lig, p, indices, y, err};
        return result;
    }

    @Test
    public void testGuess() {
        PathFunctionSingleState singleState = new PathFunctionSingleState(false, false, 1);
        double[][] values = setupFit(50.0, 10.0, 0.01);
        int[] indices = new int[values[0].length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = (int) Math.round(values[2][i]);
        }
        double[] guesses = singleState.getGuess(values[0], values[3], indices);
        Assert.assertEquals(50.0, guesses[0], 10.0);
        Assert.assertEquals(10.0, guesses[1], 1.0);
    }

    @Test
    public void testFitSingle() throws Exception {
        int nPaths = 1;
        PathFunctionSingleState fun = new PathFunctionSingleState(false, false, nPaths);
        double[] fitPars = {50.0, 10.0};
        double[][] values = setupFit(fitPars[0], fitPars[1], 0.2);
        int[] indices = new int[values[0].length];
        double[][] xValues = {values[0], values[1], values[2]};
        double[][] yValues = {values[3]};
        double[] errValues = values[4];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = (int) Math.round(values[2][i]);
        }

        Fitter fitter = Fitter.getArrayFitter(fun::apply);
        fitter.setXYE(xValues, yValues[0], errValues);

        double[] guess = fun.getGuess(xValues[0], yValues[0], indices);
        double[] lower = new double[guess.length];
        double[] upper = new double[guess.length];
        int iG = 0;
        lower[iG] = guess[iG] / 4.0;
        upper[iG] = guess[iG] * 3.0;
        for (int iPath = 0; iPath < nPaths; iPath++) {
            lower[iG + 1 + iPath] = guess[iG + 1 + iPath] / 2.0;
            upper[iG + 1 + iPath] = guess[iG + 1 + iPath] * 2.0;
        }

        PointValuePair result = fitter.fit(guess, lower, upper, 10.0, 5);
        double[] bestPars = result.getPoint();
        double[] parErrs = fitter.bootstrap(result.getPoint(), 300);
        for (int iPath = 0; iPath < nPaths; iPath++) {
            double[] pars = {bestPars[0], bestPars[iPath + 1]};
            double[] errs = {parErrs[0], parErrs[iPath + 1]};
            for (int i = 0; i < pars.length; i++) {
                Assert.assertEquals(fitPars[i], pars[i], 3.0 * errs[i]);
            }
        }
    }

    @Test
    public void testFitTwo() throws Exception {
        int nPaths = 1;
        PathFunctionTwoState fun = new PathFunctionTwoState(nPaths);
        double[] fitPars = {50.0, 500.0, 10.0, 3.0};
        double[][] values = setupFitTwo(fitPars[0], fitPars[1], fitPars[2], fitPars[3], 0.01);
        int[] indices = new int[values[0].length];
        double[][] xValues = {values[0], values[1], values[2]};
        double[][] yValues = {values[3]};
        double[] errValues = values[4];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = (int) Math.round(values[2][i]);
        }


        Fitter fitter = Fitter.getArrayFitter(fun::apply);
        fitter.setXYE(xValues, yValues[0], errValues);

        double[] guess = fun.getGuess(xValues[0], yValues[0], indices);
        double[] lower = new double[guess.length];
        double[] upper = new double[guess.length];
        lower[0] = guess[0] / 4.0;
        upper[0] = guess[0] * 50;
        lower[1] = guess[1] / 4.0;
        upper[1] = guess[1] * 5.0;
        for (int iPath = 0; iPath < nPaths; iPath++) {
            lower[2 + 2 * iPath] = 0.0;
            upper[2 + 2 * iPath] = guess[2 + 2 * iPath] * 4.0;
            lower[3 + 2 * iPath] = 0.0;
            upper[3 + 2 * iPath] = guess[3 + 2 * iPath] * 4.0;
        }
        Random random = new Random();

        PointValuePair bestResult = fitter.fit(guess, lower, upper, 10.0, 5);
        double[] bestPars = bestResult.getPoint();
        double[] parErrs = fitter.bootstrap(bestResult.getPoint(), 300);
        for (int iPath = 0; iPath < nPaths; iPath++) {
            double[] pars = {bestPars[0], bestPars[1], bestPars[iPath * 2 + 2], bestPars[iPath * 2 + 3]};
            double[] errs = {parErrs[0], parErrs[1], parErrs[iPath * 2 + 2], parErrs[iPath * 2 + 3]};
            for (int i = 0; i < pars.length; i++) {
                Assert.assertEquals(fitPars[i], pars[i], 3.0*errs[i]);
            }
        }
    }
}