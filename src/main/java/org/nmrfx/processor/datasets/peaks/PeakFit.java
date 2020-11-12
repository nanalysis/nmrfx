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
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.peaks.CouplingItem;
import org.nmrfx.peaks.CouplingPattern;
import org.nmrfx.processor.optimization.NNLSMat;
import org.nmrfx.processor.optimization.SineSignal;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.direct.BOBYQAOptimizer;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.apache.commons.math3.util.FastMath;

public class PeakFit implements MultivariateFunction {

    static RandomGenerator random = new SynchronizedRandomGenerator(new Well19937c());

    double[][] freqs = null;
    double[][] amplitudes = null;
    double[] sigAmps = null;
    double[] lw = null;
    RealMatrix A = null;
    RealVector V = null;
    int[] sigStarts = null;
    int[] nSigAmps = null;
    int nSignals = 0;
    int nFit = 0;
    CouplingItem[][] cplItems;
    double[] xv = null;
    double[] yv = null;
    Random generator = null;
    PointValuePair best = null;
    int nSigAmpsTotal = 0;
    double[][] boundaries = new double[2][];
    double[] newStart;
    double[] unscaledPars;
    double[] scaledPars;
    double[][] uniformBoundaries = new double[2][];
    boolean reportFitness = false;
    int reportAt = 10;
    boolean fitAmps = false;

    public class Checker extends SimpleValueChecker {

        public Checker(double relativeThreshold, double absoluteThreshold, int maxIter) {
            super(relativeThreshold, absoluteThreshold, maxIter);
        }

        @Override
        public boolean converged(final int iteration, final org.apache.commons.math3.optim.PointValuePair previous, final org.apache.commons.math3.optim.PointValuePair current) {
            boolean converged = super.converged(iteration, previous, current);
            if (reportFitness) {
                if (converged) {
                    System.out.println(previous.getValue() + " " + current.getValue());
                }
            }
            return converged;
        }
    }

    public PeakFit(boolean fitAmps) {
        this.fitAmps = fitAmps;
    }

    public void initTest(final int n) {
        xv = new double[n];
        for (int i = 0; i < n; i++) {
            xv[i] = i;
        }
        yv = new double[n];
    }

    public void initRandom(long seed) {
        generator = new java.util.Random(seed);
    }

    public double optimizeCMAES(int nSteps) throws Exception {
        double stopFitness = 0.0;
        double lambdaMul = 3.0;
        double tol = 1.0e-5;
        int diagOnly = 0;
        random.setSeed(1);
        double inputSigma = 10.0;
        int lambda = (int) (lambdaMul * FastMath.round(4 + 3 * FastMath.log(newStart.length)));

        CMAESOptimizer cmaesOptimizer = new CMAESOptimizer(nSteps, stopFitness, true, diagOnly, 0,
                random, true,
                new Checker(tol, tol, nSteps));
        org.apache.commons.math3.optim.PointValuePair result = null;
        double[] sigma = new double[newStart.length];
        Arrays.fill(sigma, inputSigma);

        try {
            result = cmaesOptimizer.optimize(
                    new CMAESOptimizer.PopulationSize(lambda),
                    new CMAESOptimizer.Sigma(sigma),
                    new MaxEval(2000000),
                    new ObjectiveFunction(this), org.apache.commons.math3.optim.nonlinear.scalar.GoalType.MINIMIZE,
                    new SimpleBounds(uniformBoundaries[0], uniformBoundaries[1]),
                    new InitialGuess(newStart));
        } catch (DimensionMismatchException | NotPositiveException | NotStrictlyPositiveException | TooManyEvaluationsException e) {
            throw new Exception("failure to fit data " + e.getMessage());
        }
        return result.getValue();
    }

    public double optimizeBOBYQA(final int nSteps, final int nInterpolationPoints) {
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(nInterpolationPoints);
        PointValuePair result = optimizer.optimize(nSteps, this, GoalType.MINIMIZE, newStart, uniformBoundaries[0], uniformBoundaries[1]);
        double[] point = result.getPoint();
        return result.getValue();
    }

    void dumpMatrix(RealMatrix matrix) {
        for (int j = 0; j < matrix.getColumnDimension(); j++) {
            for (int i = 0; i < matrix.getRowDimension(); i++) {
                System.out.print(matrix.getEntry(i, 0) + " ");
            }
            System.out.println();
        }
    }

    public void setXY(final double[] xv, final double[] yv) {
        this.xv = xv.clone();
        this.yv = yv.clone();
    }

    public void simulate(final double[] parameters, final RealVector ampVector, final double sdev) {
        RealVector yCalc;
        if (fitAmps) {
            yCalc = calcVec(parameters);
        } else {
            fillMatrix(parameters);
            yCalc = A.operate(ampVector);
        }
        if (generator == null) {
            initRandom(0);
        }
        for (int i = 0; i < xv.length; i++) {
            yv[i] = yCalc.getEntry(i) + generator.nextGaussian() * sdev;
        }
    }

    @Override
    public double value(final double[] parameters) {
        return valueWithUnScaled(unscalePar(parameters));
    }

    public double valueWithUnScaled(final double[] parameters) {
        RealVector yCalc;
        RealVector ampVector = null;

        if (fitAmps) {
            yCalc = calcVec(parameters);
        } else {
            fillMatrix(parameters);
            ampVector = fitSignalAmplitudesNN(A.copy());
            yCalc = A.operate(ampVector);
        }

        double sum = 0.0;
        for (int i = 0; i < xv.length; i++) {
            double delta = yv[i] - yCalc.getEntry(i);
            sum += delta * delta;
        }
        double result = Math.sqrt(sum / yv.length);
//        for (int i = 0; i < parameters.length; i++) {
//            System.out.print(unscaledPars[i] + " ");
//        }
//        System.out.println(result);
        if ((best == null) || (best.getValue() > result)) {
            best = new PointValuePair(unscaledPars, result);
            if (ampVector != null) {
                sigAmps = ampVector.toArray();
            } else {
                sigAmps = null;
            }
        }
        return result;
    }

    public double valueDump(final double[] parameters) {
        double sum = 0.0;
        RealVector yCalc;
        RealVector ampVector = null;

        if (fitAmps) {
            yCalc = calcVec(parameters);
        } else {
            fillMatrix(parameters);
            ampVector = fitSignalAmplitudesNN(A.copy());
            yCalc = A.operate(ampVector);
        }
        for (int i = 0; i < xv.length; i++) {
            double delta = yv[i] - yCalc.getEntry(i);
            System.out.println(i + " " + xv[i] + " " + yv[i] + " " + yCalc.getEntry(i) + " " + delta);
            sum += delta * delta;
        }
        double result = Math.sqrt(sum / yv.length);
        for (int i = 0; i < parameters.length; i++) {
            System.out.print(parameters[i] + " ");
        }
        System.out.println(result);
        return result;
    }

    public int maxPosDev(final double[] parameters, int halfSize) {
        RealVector yCalc;
        RealVector ampVector = null;

        if (fitAmps) {
            yCalc = calcVec(parameters);
        } else {
            fillMatrix(parameters);
            ampVector = fitSignalAmplitudesNN(A.copy());
            yCalc = A.operate(ampVector);
        }
        double maxPosDev = Double.NEGATIVE_INFINITY;
        int devLoc = 0;

        for (int i = halfSize; i < (xv.length - halfSize); i++) {
            double sumDelta = 0.0;
            for (int j = -halfSize; j <= halfSize; j++) {
                int k = i + j;
                if (yv[k] > yCalc.getEntry(k)) {
                    sumDelta += yv[k] - yCalc.getEntry(k);
                }
            }
            if (sumDelta > maxPosDev) {
                maxPosDev = sumDelta;
                devLoc = (int) xv[i];
            }
        }
        return devLoc;
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
        return sigAmps;
    }

    public void dumpSignals() {
        for (int i = 0; i < cplItems.length; i++) {
            CouplingPattern.jSplittings(cplItems[i], freqs[i], amplitudes[i]);
            for (CouplingItem cplItem : cplItems[i]) {
                System.out.print("coup " + cplItem.getCoupling());
            }

            System.out.println("");
        }

        for (double[] amplitude : amplitudes) {
            for (int j = 0; j < amplitude.length; j++) {
                System.out.print("amp " + amplitude[j]);
            }
            System.out.println("");
        }

        for (double[] freq : freqs) {
            for (int j = 0; j < freq.length; j++) {
                System.out.print("freq " + freq[j]);
            }
            System.out.println("");
        }

        for (int i = 0; i < sigStarts.length; i++) {
            System.out.println("sigStarts " + sigStarts[i]);
        }
        System.out.println(nFit);
    }

    public void setSignals(CouplingItem[][] cplItems) {
        nSignals = cplItems.length;
        lw = new double[nSignals];
        amplitudes = new double[nSignals][];
        freqs = new double[nSignals][];
        this.cplItems = cplItems;
        sigStarts = new int[nSignals];
        nSigAmps = new int[nSignals];

        int start = 0;
        nSigAmpsTotal = 0;
        nFit = 0;
        for (int i = 0; i < nSignals; i++) {
            Arrays.sort(cplItems[i]);
            sigStarts[i] = start;
            start++; // allow for linewidth par
            nFit++;
            int nFreqs = 1;

            if ((cplItems[i].length == 1) && (cplItems[i][0].getNSplits() < 0)) { // generic multiplet
                nFreqs = -cplItems[i][0].getNSplits();
                start += nFreqs;
                nFit += nFreqs;
                if (fitAmps) {
                    start += nFreqs;
                    nFit += nFreqs;
                }
                nSigAmpsTotal += nFreqs;
                nSigAmps[i] = nFreqs;
            } else {
                for (CouplingItem cplItem : cplItems[i]) {
                    nFreqs = nFreqs * cplItem.getNSplits();
                }
                nSigAmpsTotal++;
                nSigAmps[i] = 1;
                start += (cplItems[i].length * 2 + 1);
                nFit += (cplItems[i].length * 2 + 1);
                if (fitAmps) {
                    start++;
                    nFit++;
                }
            }
            amplitudes[i] = new double[nFreqs];
            freqs[i] = new double[nFreqs];
        }
        sigAmps = new double[nSigAmpsTotal];
    }

    public CouplingItem[] getCouplings(int iSig) {
        return cplItems[iSig];
    }

    public List<List<SineSignal>> getSignals() {
        List<List<SineSignal>> signalGroups = new ArrayList<>(nSignals);
        double[] aCalc = best.getPoint();
        int iSigAmp = 0;
        for (int iSig = 0; iSig < nSignals; iSig++) {
            List<SineSignal> signals = new ArrayList<>();
            signalGroups.add(signals);

            int start = sigStarts[iSig];
            lw[iSig] = Math.abs(aCalc[start++]);

            if ((cplItems[iSig].length == 1) && (cplItems[iSig][0].getNSplits() < 0)) { // generic multiplet

                int nFreqs = -cplItems[iSig][0].getNSplits();

                for (int iLine = 0; iLine < nFreqs; iLine++) {
                    double amp = 0.0;
                    if (fitAmps) {
                        amp = aCalc[start++];
                    } else {
                        amp = sigAmps[iSigAmp++];
                    }
                    double freq = aCalc[start++];

                    SineSignal signal = new SineSignal(freq, lw[iSig], amp);
                    signals.add(signal);
                }
            } else {
                double amp = 0.0;
                if (fitAmps) {
                    amp = aCalc[start++];
                } else {
                    amp = sigAmps[iSigAmp++];
                }
                double freq = aCalc[start++];

                for (int i = 0; i < cplItems[iSig].length; i++) {
                    cplItems[iSig][i] = new CouplingItem(aCalc[start++], aCalc[start++], freq, cplItems[iSig][i].getNSplits());
                }

                SineSignal signal = new SineSignal(freq,
                        lw[iSig], amp);
                signals.add(signal);
            }
            Collections.sort(signals);
        }

        return signalGroups;
    }

    public double calculate(double[] a, double x) {
        double y = 0;
        for (int k = 0; k < nSignals; k++) {
            y += calculateOneSig(a, k, x);
        }

        return y;
    }

    public double calculateOneSig(double[] a, int iSig, double x) {
        int start = sigStarts[iSig];
        double sigLw = a[start++];
        freqs[iSig][0] = a[start++];
        for (int i = 0; i < cplItems[iSig].length; i++) {
            cplItems[iSig][i] = new CouplingItem(a[start++], cplItems[iSig][i].getNSplits());
        }

        for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
//System.out.println(iLine + " "  + iSig + " " + iLine + " " + a.length + " " + amplitudes[iSig].length + " " + amplitudes.length);
            amplitudes[iSig][iLine] = a[start + iLine];
            //amplitudes[iSig][iLine] = 1.0;
        }

        CouplingPattern.jSplittings(cplItems[iSig], freqs[iSig], amplitudes[iSig]);

        double y = 0.0;

        for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
            double yTemp = lShape(x, sigLw, freqs[iSig][iLine]);

            if (amplitudes[iSig][iLine] < 0) {
                yTemp = -yTemp;
            }

            y += (yTemp * amplitudes[iSig][iLine]);
        }

        return y;
    }

    public RealVector calcVec(double[] a) {
        if (V == null) {
            V = new ArrayRealVector(xv.length);
        }
        V.set(0.0);
        for (int iSig = 0; iSig < nSignals; iSig++) {
            int start = sigStarts[iSig];
            double sigLw = a[start++];
            if ((cplItems[iSig].length == 1) && (cplItems[iSig][0].getNSplits() < 0)) { // generic multiplet
                for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
                    amplitudes[iSig][iLine] = a[start++];
                    freqs[iSig][iLine] = a[start++];
                }
                for (int i = 0; i < xv.length; i++) {
                    double y = 0.0;
                    for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
                        y += amplitudes[iSig][iLine] * lShape(xv[i], sigLw, freqs[iSig][iLine]);
                    }
                    V.addToEntry(i, y);
                }
            } else {
                double thisAmp = a[start++];
                freqs[iSig][0] = a[start++];
                amplitudes[iSig][0] = 1.0;

                for (int i = 0; i < cplItems[iSig].length; i++) {
                    cplItems[iSig][i] = new CouplingItem(a[start++], a[start++], freqs[iSig][0], cplItems[iSig][i].getNSplits());
                }

                CouplingPattern.jSplittings(cplItems[iSig], freqs[iSig], amplitudes[iSig]);
                for (int i = 0; i < amplitudes[iSig].length; i++) {
                    amplitudes[iSig][i] *= thisAmp;
                }
                for (int i = 0; i < xv.length; i++) {
                    double y = 0.0;
                    for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
                        y += amplitudes[iSig][iLine] * lShape(xv[i], sigLw, freqs[iSig][iLine]);
                    }
                    V.addToEntry(i, y);

                }
            }
        }

        return V;
    }

    public RealMatrix fillMatrix(double[] a) {
        if (A == null) {
            A = new Array2DRowRealMatrix(xv.length, nSigAmpsTotal);
        }
        int iCol = 0;
        for (int iSig = 0; iSig < nSignals; iSig++) {
            int start = sigStarts[iSig];
            double sigLw = a[start++];
            if ((cplItems[iSig].length == 1) && (cplItems[iSig][0].getNSplits() < 0)) { // generic multiplet
                for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
                    freqs[iSig][iLine] = a[start++];
                }
                for (int i = 0; i < xv.length; i++) {
                    for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
                        double y = lShape(xv[i], sigLw, freqs[iSig][iLine]);
//                    System.out.println(iSig + " " + i+ " " + iLine + " " + yTemp + " " + lw + " " + freqs[iSig][iLine]);
                        if (amplitudes[iSig][iLine] < 0) {
                            y = -y;
                        }

                        A.setEntry(i, iCol + iLine, y);
                    }

                }
                iCol += freqs[iSig].length;
            } else {
                freqs[iSig][0] = a[start++];
                for (int i = 0; i < cplItems[iSig].length; i++) {
                    cplItems[iSig][i] = new CouplingItem(a[start++], a[start++], freqs[iSig][0], cplItems[iSig][i].getNSplits());
                }

                for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
//System.out.println(iLine + " "  + iSig + " " + iLine + " " + a.length + " " + amplitudes[iSig].length + " " + amplitudes.length);
                    //amplitudes[iSig][iLine] = a[start + iLine];
                    amplitudes[iSig][iLine] = 1.0;
                }

                CouplingPattern.jSplittings(cplItems[iSig], freqs[iSig], amplitudes[iSig]);
                for (int i = 0; i < xv.length; i++) {
                    double y = 0.0;
                    for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {

                        double yTemp = lShape(xv[i], sigLw, freqs[iSig][iLine]);
//                    System.out.println(iSig + " " + i+ " " + iLine + " " + yTemp + " " + lw + " " + freqs[iSig][iLine]);
                        if (amplitudes[iSig][iLine] < 0) {
                            yTemp = -yTemp;
                        }
                        y += (yTemp * amplitudes[iSig][iLine]);
                    }
                    A.setEntry(i, iCol, y);

                }
                iCol++;
            }
        }

        return A;
    }

    public double lShape(double x, double b, double freq, double fR, double fI) {
        double denom = ((b * b) + ((x - freq) * (x - freq)));
        double yR = (b * b) / denom;
        double yI = -b * (x - freq) / denom;
        double y = fR * yR + fI * yI;
        return y;
    }

    public double lShape(double x, double b, double freq) {
        double y;
        boolean LORENTZIAN = true;
        if (LORENTZIAN) {
            b *= 0.5;
            y = (b * b) / ((b * b) + ((x - freq) * (x - freq)));
        } else {
            double dX = (x - freq);
            y = Math.exp(-dX * dX / b);
        }

        return y;
    }

    public double lShapeImag(double x, double b, double freq) {
        double y;
        b *= 0.5;
        y = -b * (x - freq) / ((b * b) + ((x - freq) * (x - freq)));

        return y;
    }

    public double value(double[] pars, double[][] values) {
        int nSig = pars.length / 3;
        int n = values[0].length;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double x = values[0][i];
            double y = values[1][i];
            double yCalc = 0.0;
            for (int j = 0; j < nSig; j++) {
                double amp = pars[j * 3];
                double f = pars[j * 3 + 1];
                double lw = pars[j * 3 + 2];
                yCalc += amp * lShape(x, lw, f);
            }
            double delta = yCalc - y;
            sum += delta * delta;
        }
        return Math.sqrt(sum / n);

    }

    public double[] sim(double[] pars, double[][] values) {
        int nSig = pars.length / 3;
        int n = values[0].length;
        double sum = 0.0;
        double[] ySim = new double[n];

        for (int i = 0; i < n; i++) {
            double x = values[0][i];
            double yCalc = 0.0;
            for (int j = 0; j < nSig; j++) {
                double amp = pars[j * 3];
                double f = pars[j * 3 + 1];
                double lw = pars[j * 3 + 2];
                yCalc += amp * lShape(x, lw, f);
            }
            ySim[i] = yCalc;
        }
        return ySim;

    }

    public RealVector fitSignalAmplitudesNN(RealMatrix AR) {
        int nRows = xv.length;

        //dumpMatrix(A);
        int nCols = A.getColumnDimension();
        double[] b = new double[nRows];

        System.arraycopy(yv, 0, b, 0, nRows);

        RealMatrix BR = new Array2DRowRealMatrix(b);
        NNLSMat nnlsMat = new NNLSMat(AR, BR);
        double[] XR = nnlsMat.getX();
        RealVector result = new ArrayRealVector(nCols);

        for (int i = 0; i < nCols; i++) {
            double amp = XR[i];
            int iSig = i;
            result.setEntry(iSig, amp);
        }

        return result;
    }

    public double[] unscalePar(final double[] par) {
        int nDim = par.length;
        for (int i = 0; i < nDim; i++) {
            double f = (par[i] - 0.0) / (100.0 - 0.0);
            double low = boundaries[0][i];
            double up = boundaries[1][i];
            unscaledPars[i] = f * (up - low) + low;
        }
        return unscaledPars;
    }

    public double[] scalePar(final double[] par) {
        int nDim = par.length;
        for (int i = 0; i < nDim; i++) {
            double delta = boundaries[1][i] - boundaries[0][i];
            double f = (par[i] - boundaries[0][i]) / delta;
            scaledPars[i] = 100.0 * f;
        }
        return scaledPars;
    }

    public void setOffsets(final double[] start, final double[] lower, final double[] upper) {
        int nDim = start.length;
        newStart = new double[nDim];
        unscaledPars = new double[nDim];
        scaledPars = new double[nDim];
        uniformBoundaries[0] = new double[nDim];
        uniformBoundaries[1] = new double[nDim];
        for (int i = 0; i < nDim; i++) {
            double delta = upper[i] - lower[i];
            double f = (start[i] - lower[i]) / delta;
            newStart[i] = 100.0 * f;
            uniformBoundaries[0][i] = 0.0;
            uniformBoundaries[1][i] = 100.0;
        }
        boundaries[0] = lower;
        boundaries[1] = upper;
    }

    public static void main(String[] args) {
        PeakFit peakFit = new PeakFit(true);
        int n = 100;
//        CouplingItem[][] cplItems = new CouplingItem[2][2];
//        cplItems[0][0] = new CouplingItem(0, 2);
//        cplItems[0][1] = new CouplingItem(0, 2);
//        cplItems[1][0] = new CouplingItem(0, 3);
//        cplItems[1][1] = new CouplingItem(0, 2);
//        peakFit.initTest(n);
//        peakFit.setSignals(cplItems);
//        peakFit.dumpSignals();
//        double[] a = {2, 15, 10, 5, 2, 59, 10, 2};
//        double[] amps = {1, 2};

        CouplingItem[][] cplItems = new CouplingItem[2][1];
        cplItems[0][0] = new CouplingItem(0, 2);
        cplItems[1][0] = new CouplingItem(0, 2);
        peakFit.initTest(n);
        peakFit.setSignals(cplItems);
        peakFit.dumpSignals();
        //            lw  f   j  d         lw  f  j d
        double[] a = {2, 15, 10, 30, 2, 59, 10, 5000};
        double[] start = {2.2, 12, 9, 70, 2.2, 55, 9, 4000};
        double[] lower = {1, 10, 8, 10, 1, 50, 8, 3000};
        double[] upper = {3, 19, 15, 120, 3, 70, 13, 7000};

        double[] amps = {1};

        RealVector ampVector = new ArrayRealVector(amps);
        peakFit.simulate(a, ampVector, 0.001);
        peakFit.setOffsets(start, lower, upper);
        peakFit.value(peakFit.scalePar(a));
        int nSteps = 1000;
        int nDim = start.length;
        int nInterpolationPoints = (nDim + 1) * (nDim + 2) / 2;
        System.out.println(start.length + " " + nInterpolationPoints);
        peakFit.optimizeBOBYQA(nSteps, nInterpolationPoints);
    }
}
