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
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.nmrfx.annotations.PluginAPI;

import java.util.ArrayList;

@PluginAPI("parametric")
public class FDSignalOpt implements MultivariateFunction {

    private final double[] values;
    private final double[] testVec;
    private final int vecSize;
    private final double[] parameters;
    private final int leftEdge;
    private final int rightEdge;
    private double[][] boundaries = null;
    private double[][] normBoundaries = null;
    private double[] normValues;
    private boolean absMode = false;
    ArrayList<Signal> signals;
    public static final RandomGenerator DEFAULT_RANDOMGENERATOR = new MersenneTwister(1);
    double bestValue = Double.MAX_VALUE;
    double[] bestPars;
    private int nEvaluations = 0;
    private double startValue = 0.0;
    private double finalValue = 0.0;
    private double finalDelta = 0.0;
    private double[] weights;
    private final int[] parMap;

    public FDSignalOpt(double[] testVec, int vecSize, ArrayList<? extends Signal> signals, boolean constrainWidth, int leftEdge, int rightEdge) {
        this.testVec = testVec;
        this.values = new double[vecSize];
        this.vecSize = vecSize;
        this.signals = new ArrayList<>();
        this.parMap = new int[3 * signals.size()];
        this.leftEdge = leftEdge;
        this.rightEdge = rightEdge;
        if (constrainWidth) {
            this.parameters = new double[1 + 2 * signals.size()];
            double sumWidth = 0.0;
            int iSig = 0;
            for (Signal signal : signals) {
                parMap[iSig * 3] = iSig * 2 + 1;
                parMap[iSig * 3 + 1] = iSig * 2 + 2;
                parMap[iSig * 3 + 2] = 0;
                parameters[iSig * 2 + 1] = signal.frequency;
                parameters[iSig * 2 + 2] = signal.amplitude;
                this.signals.add(new Signal(signal));
                sumWidth += signal.decay;
                iSig++;
            }
            parameters[0] = sumWidth / signals.size();
        } else {
            this.parameters = new double[3 * signals.size()];
            int i = 0;
            for (Signal signal : signals) {
                parMap[i] = i;
                parMap[i + 1] = i + 1;
                parMap[i + 2] = i + 2;
                parameters[i++] = signal.frequency;
                parameters[i++] = signal.amplitude;
                parameters[i++] = signal.decay;
                this.signals.add(new Signal(signal));
            }
        }

        bestPars = new double[parameters.length];
        calcWeights();
    }

    public void setAbsMode(final boolean state) {
        absMode = state;
    }

    public boolean getAbsMode() {
        return absMode;
    }

    private void setBoundaries(double sigma) {
        boundaries = new double[2][parameters.length];
        normBoundaries = new double[2][parameters.length];
        normValues = new double[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            normBoundaries[0][i] = 0.0;
            normBoundaries[1][i] = 100.0;
        }
        int nSignals = signals.size();
        for (int iSig = 0; iSig < nSignals; iSig++) {
            int iD = parMap[iSig * 3 + 2];
            int iA = parMap[iSig * 3 + 1];
            boundaries[0][iD] = parameters[iD] * 0.5;
            boundaries[1][iD] = parameters[iD] * 2.0;
            boundaries[0][iA] = parameters[iA] * 0.2;
            boundaries[1][iA] = parameters[iA] * 4.0;

            int iF = parMap[iSig * 3];
            if (iSig > 0) {
                int iFp = parMap[(iSig - 1) * 3];
                boundaries[0][iF] = (parameters[iF] + parameters[iFp]) / 2;
            }
            if (iSig < nSignals - 1) {
                int iFs = parMap[(iSig + 1) * 3];
                boundaries[1][iF] = (parameters[iF] + parameters[iFs]) / 2;
            }
            double testBou = parameters[iF] - parameters[iD] / 2;
            if ((iSig == 0) || (testBou > boundaries[0][iF])) {
                boundaries[0][iF] = testBou;
            }
            testBou = parameters[iF] + parameters[iD] / 2;
            if ((iSig == (nSignals - 1)) || (testBou < boundaries[1][iF])) {
                boundaries[1][iF] = testBou;
            }
        }

    }

    public void normalize(double[] inValues, double[] outValues) {
        for (int i = 0; i < inValues.length; i++) {
            outValues[i] = toNormalized(inValues[i], i);

        }
    }

    public void denormalize(double[] inValues, double[] outValues) {
        for (int i = 0; i < inValues.length; i++) {
            outValues[i] = fromNormalized(inValues[i], i);
        }
    }

    public double fromNormalized(double value, int i) {
        double f = (value - normBoundaries[0][i]) / (normBoundaries[1][i] - normBoundaries[0][i]);
        double normValue = f * (boundaries[1][i] - boundaries[0][i]) + boundaries[0][i];
        return normValue;
    }

    public double toNormalized(double value, int i) {
        double f = (value - boundaries[0][i]) / (boundaries[1][i] - boundaries[0][i]);
        double normValue = f * (normBoundaries[1][i] - normBoundaries[0][i]) + normBoundaries[0][i];
        return normValue;
    }

    private void fillVecBySignals() {
        Vec.fillVec(values, values.length, signals);
    }

    private void toSignal(double[] par) {
        int i = 0;
        for (Signal signal : signals) {
            signal.frequency = parameters[parMap[i++]];
            signal.amplitude = parameters[parMap[i++]];
            signal.decay = parameters[parMap[i++]];
        }
    }

    public double calcRMS() {
        double rss = calcRSS();
        return Math.sqrt(rss / vecSize);
    }

    public double calcRSS() {
        fillVecBySignals();
        double rss = 0.0;
        for (int i = 0; i < vecSize; i++) {
            double delta = values[i] - testVec[i];
            rss += weights[i] * delta * delta;
        }
        return rss;
    }

    public final void calcWeights() {
        weights = new double[testVec.length];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = 1.0;
        }
        int n = leftEdge;
        // Approximate formula for giving width at tenth max equal to n
        double c = 2 * n / 4.29193;
        for (int i = 0; i < n; i++) {
            double delta = n - i;
            weights[i] = Math.exp(-delta * delta / (2.0 * c * c));
        }
        n = rightEdge;
        for (int i = 0; i < n; i++) {
            double delta = n - i;
            weights[testVec.length - i - 1] = Math.exp(-delta * delta / (2.0 * c * c));
        }
    }

    public double calcSumAbs() {
        fillVecBySignals();
        double sumAbs = 0.0;
        for (int i = 0; i < vecSize; i++) {
            double deltaAbs = weights[i] * Math.abs(values[i] - testVec[i]);
            sumAbs += deltaAbs;
        }
        return sumAbs;
    }

    public double maxDelta(final double[] opars) {
        denormalize(opars, parameters);
        toSignal(parameters);
        fillVecBySignals();
        double maxDelta = 0.0;
        for (int i = 0; i < vecSize; i++) {
            double deltaAbs = weights[i] * Math.abs(values[i] - testVec[i]);
            if (deltaAbs > maxDelta) {
                maxDelta = deltaAbs;
            }
        }
        return maxDelta;
    }

    @Override
    public double value(final double[] opars) {
        denormalize(opars, parameters);
        toSignal(parameters);
        double value;
        if (absMode) {
            value = calcSumAbs();
        } else {
            value = calcRMS();
        }
        if (value < bestValue) {
            bestValue = value;
            System.arraycopy(opars, 0, bestPars, 0, opars.length);
        }
        return value;
    }

    public ArrayList<Signal> refineBOBYQA(final int nSteps, final double stopTrust) {
        setBoundaries(0.3);
        startValue = calcRMS();

        double initialTrust = 10.0;
        int n = normValues.length;
        int nInterp = n + 2;
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(nInterp, initialTrust, stopTrust);

        normalize(parameters, normValues);

        PointValuePair result;
        try {
            result = optimizer.optimize(
                    new MaxEval(nSteps),
                    new ObjectiveFunction(this), GoalType.MINIMIZE,
                    new SimpleBounds(normBoundaries[0], normBoundaries[1]),
                    new InitialGuess(normValues));
        } catch (TooManyEvaluationsException e) {
            result = new PointValuePair(bestPars, bestValue);
        } catch (MathIllegalStateException e) {
            result = new PointValuePair(bestPars, bestValue);
        }
        nEvaluations = optimizer.getEvaluations();
        finalValue = result.getValue();
        finalDelta = maxDelta(result.getPoint());
        denormalize(result.getPoint(), parameters);
        toSignal(parameters);
        return signals;
    }

    /**
     * @return the nEvaluations
     */
    public int getnEvaluations() {
        return nEvaluations;
    }

    /**
     * @return the startValue
     */
    public double getStartValue() {
        return startValue;
    }

    /**
     * @return the finalValue
     */
    public double getFinalValue() {
        return finalValue;
    }

    /**
     * @return the finalDelta
     */
    public double getFinalDelta() {
        return finalDelta;
    }

}
