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
package org.nmrfx.processor.optimization;

import java.util.Random;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.MathIllegalStateException;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;

public class LorentzGaussND implements MultivariateFunction {

    final int nDim;
    int nParDim;
    int nFloating;
    int[] sigStarts = null;
    int nSignals;
    int nDelays = 1;
    int[][] positions;
    double[][] intensities;
    double[] delays;
    boolean fitC = false;
    double[] guesses;
    double[][] boundaries;
    double[] newStart;
    double[] unscaledPars;
    double[] scaledPars;
    int[] mapToAll;
    int[] mapFromAll;
    int[][] syncPars;
    double[][] uniformBoundaries = new double[2][];
    PointValuePair best = null;
    boolean calcGauss = false;
    boolean calcLorentz = true;
    double fracLorentz = 1.0;
    Random generator = null;

    public LorentzGaussND(final int[][] positions) {
        int nPoints = positions.length;
        nDim = positions[0].length;
        this.positions = new int[nPoints][];
        for (int i = 0; i < nPoints; i++) {
            this.positions[i] = positions[i].clone();
        }
    }

    public LorentzGaussND(final int[] sizes) {
        nDim = sizes.length;
        int i = 0;
        int nPoints = 1;
        for (int size : sizes) {
            nPoints *= size;
        }
        this.positions = new int[nPoints][sizes.length];
        MultidimensionalCounter counter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iterator = counter.iterator();
        i = 0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            int j = 0;
            for (int value : counts) {
                positions[i][j++] = value;
            }
            i++;
        }
    }

    public void setIntensities(final double[][] intensities) {
        this.intensities = intensities;
        nDelays = intensities.length;
    }

    public void setDelays(final double[] delays, boolean fitC) {
        this.fitC = fitC;
        this.delays = delays;
    }

    public void initRandom(long seed) {
        generator = new java.util.Random(seed);
    }

    public PointValuePair optimizeBOBYQA(final int nSteps, final int nInterpolationPoints) {
        //dumpArray(unscalePar(newStart));
        //dumpArray(unscalePar(uniformBoundaries[0]));
        //dumpArray(unscalePar(uniformBoundaries[1]));
        best = null;
        PointValuePair result = null;
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(nInterpolationPoints, 10.0, 1.0e-2);
        try {
            result = optimizer.optimize(
                    new MaxEval(nSteps),
                    new ObjectiveFunction(this), GoalType.MINIMIZE,
                    new SimpleBounds(uniformBoundaries[0], uniformBoundaries[1]),
                    new InitialGuess(newStart));
            result = new PointValuePair(unscalePar(result.getPoint()), result.getValue());
        } catch (TooManyEvaluationsException e) {
            result = best;
        } catch (MathIllegalStateException e) {
            System.out.println("illegals state " + optimizer.getEvaluations());
            result = best;
        }

        return result;
    }

    public void simulate(final double[] parameters, final double sdev) {
        if (generator == null) {
            initRandom(0);
        }
        intensities = new double[nDelays][];
        for (int iDelay = 0; iDelay < nDelays; iDelay++) {
            intensities[iDelay] = new double[positions.length];
            for (int i = 0; i < positions.length; i++) {
                intensities[0][i] = calculate(parameters, positions[i], iDelay) + generator.nextGaussian() * sdev;
            }
        }
    }

    public static void dumpArray(final double[] parameters) {
        for (double par : parameters) {
            System.out.print(par + " ");
        }
        System.out.println("");
    }

    public double value(final double[] parameters) {
        return valueWithUnScaled(unscalePar(parameters));
    }

    public double valueWithUnScaled(final double[] parameters) {
        double sum = 0.0;
        for (int iDelay = 0; iDelay < nDelays; iDelay++) {
            for (int i = 0; i < positions.length; i++) {
                double y = calculate(parameters, positions[i], iDelay);
                double delta = intensities[iDelay][i] - y;
                //sum += delta * delta;
                sum += FastMath.abs(delta);
            }
        }
        // double result = Math.sqrt(sum / positions.length);
        double result = sum / (positions.length * nDelays);
        //dumpArray(parameters);
        //System.out.println(result);
        if ((best == null) || (best.getValue() > result)) {
            best = new PointValuePair(parameters, result);
        }
        return result;
    }

    public double valueDump(final double[] point) {
        return 0.0;
    }

    public int maxPosDev(final double[] point) {
        return 0;
    }

    public double rms(final double[] point) {
        best = null;
        double value = valueWithUnScaled(point);
        return value;
    }

    public double getBestValue() {
        return best.getValue();
    }

    public double[] getBestPoint() {
        return best.getPoint();
    }

    public double[] getBestAmps() {
        return null;
    }

    public double calculate(double[] a, int[] x, int iDelay) {
        double y = a[0];
        for (int k = 0; k < nSignals; k++) {
            y += calculateOneSig(a, k, x, iDelay);
        }

        return y;
    }

    public double calculateOneSig(double[] a, int iSig, int[] x, int iDelay) {
        double y = 1.0;
        int iPar = sigStarts[iSig];
        double amplitude;
        double base = 0.0;
        if (intensities.length > 1) {
            if (delays != null) {
                amplitude = a[iPar++];
                amplitude *= FastMath.exp(-1.0 * delays[iDelay] / a[iPar++]);
                if (fitC) {
                    base = a[iPar++];
                }
            } else {
                amplitude = a[iPar + iDelay];
                iPar += nDelays;
            }
        } else {
            amplitude = a[iPar++];
        }
        for (int iDim = 0; iDim < nDim; iDim++) {
            double lw = a[iPar++];
            double freq = a[iPar++];
            y *= lShape(x[iDim], lw, freq);
        }
        y *= amplitude;
        y += base;
        return y;
    }

    public double lShape(double x, double b, double freq) {
        double yL = 0.0;
        double yG = 0.0;
        if (calcLorentz) {
            b *= 0.5;
            yL = fracLorentz * ((b * b) / ((b * b) + ((x - freq) * (x - freq))));
        }
        if (calcGauss) {
            double dX = (x - freq);
            yG = (1.0 - fracLorentz) * Math.exp(-dX * dX / b);
        }

        return yL + yG;
    }

    public double[] unscalePar(final double[] par) {
        for (int i = 0; i < nFloating; i++) {
            double f = (par[i] - 0.0) / (100.0 - 0.0);
            double low = boundaries[0][i];
            double up = boundaries[1][i];
            unscaledPars[mapToAll[i]] = f * (up - low) + low;
        }
        if (syncPars != null) {
            for (int i = 0; i < syncPars.length; i++) {
                int j = syncPars[i][1];
                int k = syncPars[i][0];
                unscaledPars[k] = unscaledPars[j];
            }
        }
        return unscaledPars;
    }

    public double[] scalePar(final double[] par) {
        for (int i = 0; i < nFloating; i++) {
            double delta = boundaries[1][i] - boundaries[0][i];
            double f = (par[i] - boundaries[0][i]) / delta;
            scaledPars[i] = 100.0 * f;
        }
        return scaledPars;
    }

    public void setOffsets(final double[] start, final double[] lower, final double[] upper, boolean[] floating, int[][] syncPars) {
        int nRelaxPar = 0;
        if (intensities.length > 1) {
            if (delays != null) {
                if (fitC) {
                    nRelaxPar = 2;
                } else {
                    nRelaxPar = 1;
                }
            } else {
                nRelaxPar = nDelays - 1;
            }
        }
        nSignals = (start.length - 1) / (nDim * 2 + 1 + nRelaxPar);
        if (nSignals * (nDim * 2 + 1 + nRelaxPar) != start.length - 1) {
            throw new IllegalArgumentException("Wrong number of starting parameters " + start.length + " nSig " + nSignals + " nCalc " + (nDim * 2 + 1 + nRelaxPar));
        }
        nParDim = start.length;
        nFloating = 0;
        for (boolean floats : floating) {
            if (floats) {
                nFloating++;
            }
        }
        sigStarts = new int[nSignals];
        int iStart = 1;
        for (int i = 0; i < nSignals; i++) {
            sigStarts[i] = iStart;
            iStart += nDim * 2 + 1 + nRelaxPar;
        }
        mapFromAll = new int[nParDim];
        mapToAll = new int[nFloating];
        int[] mapFromAll = new int[nParDim];
        newStart = new double[nFloating];
        unscaledPars = new double[nParDim];
        scaledPars = new double[nFloating];
        uniformBoundaries[0] = new double[nFloating];
        uniformBoundaries[1] = new double[nFloating];
        boundaries = new double[2][];
        boundaries[0] = new double[nFloating];
        boundaries[1] = new double[nFloating];
        if (syncPars != null) {
            this.syncPars = new int[syncPars.length][2];
        }
        int j = 0;
        for (int i = 0; i < nParDim; i++) {
            if (floating[i]) {
                double delta = upper[i] - lower[i];
                double f = (start[i] - lower[i]) / delta;
                newStart[j] = 100.0 * f;
                uniformBoundaries[0][j] = 0.0;
                uniformBoundaries[1][j] = 100.0;
                boundaries[0][j] = lower[i];
                boundaries[1][j] = upper[i];
                mapToAll[j] = i;
                mapFromAll[i] = j;
                j++;
            }
            unscaledPars[i] = start[i];
        }
        if (syncPars != null) {
            for (int i = 0; i < syncPars.length; i++) {
                this.syncPars[i][0] = syncPars[i][0];
                this.syncPars[i][1] = syncPars[i][1];
                System.out.println(i + " " + syncPars[i][0] + " " + syncPars[i][1]);
            }
        }

    }

    public static void main(String[] args) {
        double[] a = {2, 8, 3, 12, 4};
        double[] start = {4.2, 7.8, 2.5, 12.3, 3.8};
        double[] lower = {1, 5, 1, 8, 2};
        double[] upper = {6, 12, 6, 15, 8};
        boolean[] floating = {true, true, true, true};

        int[] sizes = {20, 20};
        LorentzGaussND peakFit = new LorentzGaussND(sizes);

        peakFit.setOffsets(start, lower, upper, floating, null);
        peakFit.simulate(a, 0.01);
        peakFit.value(peakFit.scalePar(a));
        int nSteps = 1000;
        int nParDim = start.length;
        int nInterpolationPoints = (nParDim + 1) * (nParDim + 2) / 2;
        System.out.println(start.length + " " + nInterpolationPoints);
        PointValuePair result = peakFit.optimizeBOBYQA(nSteps, nInterpolationPoints);
        double[] point = result.getPoint();
        System.out.println("done");
        dumpArray(peakFit.unscalePar(point));
        System.out.println(result.getValue());
    }
}
