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

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.MathIllegalStateException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.processor.datasets.peaks.LineShapes;
import org.nmrfx.processor.datasets.peaks.SyncPar;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
    double[][] boundaries;
    double[] newStart;
    double[] unscaledPars;
    double[] scaledPars;
    int[] mapToAll;
    int[] mapFromAll;
    List<SyncPar> syncPars = new ArrayList<>();
    double[][] uniformBoundaries = new double[2][];
    PointValuePair best = null;
    boolean fitZZ = false;
    boolean fitKAB;
    boolean fitR1AB;
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
        int nPoints = 1;
        for (int size : sizes) {
            nPoints *= size;
        }
        this.positions = new int[nPoints][sizes.length];
        MultidimensionalCounter counter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iterator = counter.iterator();
        int i = 0;
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

    public void fitZZ(boolean state) {
        fitZZ = state;
    }

    public boolean fitZZ() {
        return fitZZ;
    }
    public void fitKAB(boolean state) {
        fitKAB = state;
    }

    public boolean fitKAB() {
        return fitKAB;
    }
    public void fitR1AB(boolean state) {
        fitR1AB = state;
    }

    public boolean fitR1AB() {
        return fitR1AB;
    }

    public void initRandom(long seed) {
        generator = new java.util.Random(seed);
    }

    public PointValuePair optimizeBOBYQA(final int nSteps, final int nInterpolationPoints) {
        best = null;
        PointValuePair result;
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
        System.out.println();
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
                sum += FastMath.abs(delta);
            }
        }

        double result = sum / (positions.length * nDelays);
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
        return valueWithUnScaled(point);
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
        double y = fitZZ ? 0.0 : a[0];
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
        int last = a.length - 1;
        int nZZ = 0;
        if (intensities.length > 1) {
            if (delays != null) {
                int nR = 1;
                if (fitZZ) {
                    amplitude = a[0];
                    nZZ = 3;
                    if (fitKAB) {
                        nZZ++;
                    }
                    if (fitR1AB) {
                        nZZ++;
                        nR = 2;
                    }
                    iPar++;
                } else {
                    amplitude = a[iPar++];
                }
                if (fitZZ) {
                    double r1A = a[last - nZZ + 1];
                    double r1B = r1A;
                    if (fitR1AB) {
                        r1B = a[last - nZZ + 2];
                    }
                    double popA = a[last - 1];
                    double kExAB = a[last - nR + 1];
                    double kExBA = kExAB;
                    if (fitKAB) {
                        kExBA = a[last - nR + 2];
                    }
                    if (!fitR1AB && !fitKAB) {
                        amplitude *= zzAmplitude(r1A, popA, kExAB, delays[iDelay], iSig);
                    } else {
                        amplitude *= zzAmplitude2(r1A, r1B, popA, kExAB, kExBA, delays[iDelay], iSig);
                    }
                } else {
                    amplitude *= Math.exp(-a[iPar++] * delays[iDelay]);
                    if (fitC) {
                        base = a[iPar++];
                    }
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
            double shapeFactor = a[a.length - nZZ - nDim + iDim];
            double f = lShape(x[iDim], lw, freq, shapeFactor);
            y *= f;
        }
        y *= amplitude;
        y += base;
        return y;
    }

    public double lShape(double x, double b, double freq, double shapeFactor) {
        return LineShapes.G_LORENTZIAN.calculate(x, 1.0, freq, b, shapeFactor);
    }

    public static double zzAmplitude(double r1, double popA, double kEx, double delay, int iSig) {
        double popB = 1.0 - popA;
        double relax = Math.exp(-r1 * delay);
        double exchange = Math.exp(-kEx * delay);
        double amplitude;
        if (iSig == 0) {
            amplitude = popA * (popA + popB * exchange) * relax;
        } else if (iSig == 1) {
            amplitude = popB * (popB + popA * exchange) * relax;
        } else {
            amplitude = popA * popB * (1.0 - exchange) * relax;
        }
        return amplitude;
    }
    public static double zzAmplitude2(double r1A, double r1B, double popA, double kAB, double kBA, double delay, int iSig) {
        double popB = 1.0 - popA;

        double a11 = r1A + kAB;
        double a12 = -kBA;
        double a21 = -kAB;
        double a22 = r1B+kBA;
        double dA = a11-a22;
        double lambda1 = 0.5 * ((a11 + a22) + Math.sqrt(dA*dA+4.0*kAB*kBA));
        double lambda2 = 0.5 * ((a11 + a22) -  Math.sqrt(dA*dA+4.0*kAB*kBA));

        double amplitude;
        if (iSig == 0) {
            amplitude = popA*(-(lambda2-a11)*Math.exp(-lambda1*delay) + (lambda1-a11)*Math.exp(-lambda2*delay))/(lambda1-lambda2);
        } else if (iSig == 1) {
            amplitude = popB*(-(lambda2-a22)*Math.exp(-lambda1*delay) + (lambda1-a22)*Math.exp(-lambda2*delay))/(lambda1-lambda2);
        } else if (iSig == 2) {
            amplitude = popA*(a21*Math.exp(-lambda1*delay) - a21*Math.exp(-lambda2*delay))/(lambda1-lambda2);
        } else {
            amplitude = popB*(a12*Math.exp(-lambda1*delay) - a12*Math.exp(-lambda2*delay))/(lambda1-lambda2);
        }
        return amplitude;
    }

    public double[] unscalePar(final double[] par) {
        for (int i = 0; i < nFloating; i++) {
            double f = (par[i] - 0.0) / (100.0 - 0.0);
            double low = boundaries[0][i];
            double up = boundaries[1][i];
            unscaledPars[mapToAll[i]] = f * (up - low) + low;
        }
        for (SyncPar syncPar : syncPars) {
                int to = syncPar.to();
                int from = syncPar.from();
                unscaledPars[to] = unscaledPars[from];
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

    public void setOffsets(final double[] start, final double[] lower, final double[] upper, boolean[] floating, List<SyncPar> syncPars) {
        int nRelaxPar = 0;
        int nZZ = 0;
        if (intensities.length > 1) {
            if (delays != null) {
                if (fitZZ) {
                 nZZ = 3;
                 if (fitKAB) {
                     nZZ++;
                 }
                 if (fitR1AB) {
                     nZZ++;
                 }
                 nRelaxPar = 0;
                } else {
                    if (fitC) {
                        nRelaxPar = 2;
                    } else {
                        nRelaxPar = 1;
                    }
                }
            } else {
                nRelaxPar = nDelays - 1;
            }
        }
        nSignals = (start.length - 1 - nDim - nZZ) / (nDim * 2 + 1 + nRelaxPar);
        if (nSignals * (nDim * 2 + 1 + nRelaxPar) != start.length - 1 - nDim - nZZ) {
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
        this.syncPars.clear();
        this.syncPars.addAll(syncPars);
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
