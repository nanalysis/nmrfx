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
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

import java.util.ArrayList;


public class SignalOpt implements MultivariateFunction {
    private final Complex[] values;
    private final Complex[] testVec;
    private final int vecSize;
    private final int nSignals;
    private final double[] parameters;
    private double[] inputSigma;
    private double[][] boundaries = null;
    private double[][] normBoundaries = null;
    private double[] normValues;

    private final Complex[] fd;
    private final Complex[] pa;
    private final int start = 0;
    public static final RandomGenerator DEFAULT_RANDOMGENERATOR = new MersenneTwister(1);

    public SignalOpt(Complex[] testVec, int vecSize, Complex[] fd, Complex[] pa) {
        this.testVec = testVec;
        this.values = new Complex[vecSize];
        this.vecSize = vecSize;
        this.nSignals = fd.length;
        this.parameters = new double[4 * nSignals];
        this.fd = fd;
        this.pa = pa;
        int i = 0;
        for (int iSig = 0; iSig < nSignals; iSig++) {
            parameters[i++] = fd[iSig].getReal();
            parameters[i++] = fd[iSig].getImaginary();
            parameters[i++] = pa[iSig].getReal();
            parameters[i++] = pa[iSig].getImaginary();
        }
    }

    public SignalOpt(Complex[] testVec, int vecSize, ArrayList<Signal> signals) {
        this.testVec = testVec;
        this.values = new Complex[vecSize];
        this.vecSize = vecSize;
        this.nSignals = signals.size();
        this.parameters = new double[4 * signals.size()];
        this.fd = null;
        this.pa = null;
        int i = 0;
        for (Signal signal : signals) {
            parameters[i++] = signal.frequency;
            parameters[i++] = signal.decay;
            parameters[i++] = signal.amplitude;
            parameters[i++] = signal.phase;
        }
    }

    private void setBoundaries(double sigma) {
        boundaries = new double[2][parameters.length];
        normBoundaries = new double[2][parameters.length];
        normValues = new double[parameters.length];
        inputSigma = new double[parameters.length];
// dfap
        for (int i = 0; i < parameters.length; i++) {
            normBoundaries[0][i] = 0.0;
            normBoundaries[1][i] = 100.0;
            switch (i % 4) {
                case 0:
                    boundaries[0][i] = parameters[i] - 0.1;
                    boundaries[1][i] = parameters[i] + 0.1;
                    if (boundaries[0][i] <= 0.0) {
                        boundaries[0][i] = 1.0e-8;
                    }
                    break;
                case 1:
                    boundaries[0][i] = parameters[i] - 0.1;
                    boundaries[1][i] = parameters[i] + 0.1;
                    break;
                case 2:
                    boundaries[0][i] = parameters[i] - 0.1;
                    boundaries[1][i] = parameters[i] + 0.1;
                    if (boundaries[0][i] <= 0.0) {
                        boundaries[0][i] = 1.0e-8;
                    }
                    break;
                case 3:
                    boundaries[0][i] = parameters[i] - 0.1;
                    boundaries[1][i] = parameters[i] + 0.1;
                    break;
                default:
                    break;
            }
            inputSigma[i] = sigma;

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
        if (boundaries[1][i] > FastMath.PI) {
            if (value < 0.0) {
                value += 2.0 * FastMath.PI;
            }
        }
        double f = (value - boundaries[0][i]) / (boundaries[1][i] - boundaries[0][i]);
        double normValue = f * (normBoundaries[1][i] - normBoundaries[0][i]) + normBoundaries[0][i];
        return normValue;
    }

    private void fillVecBySignals() {
        for (int i = 0; i < vecSize; i++) {
            values[i] = Complex.ZERO;
        }
        for (int j = 0; j < nSignals; j++) {
            Complex z2 = pa[j];
            Complex z = fd[j];
            for (int i = 0; i < start; i++) {
                z2 = z2.multiply(z);
            }
            for (int i = 0; i < vecSize; i++) {
                values[i] = values[i].add(z2);
                z2 = z2.multiply(z);
            }
        }
    }

    private void fillVecBySignal(int j) {
        for (int i = 0; i < vecSize; i++) {
            values[i] = Complex.ZERO;
        }
        Complex z2 = pa[j];
        Complex z = fd[j];
        for (int i = 0; i < start; i++) {
            z2 = z2.multiply(z);
        }
        for (int i = 0; i < vecSize; i++) {
            values[i] = values[i].add(z2);
            z2 = z2.multiply(z);
        }
    }

    public double dotProduct(int j) {
        fillVecBySignal(j);
        double sum = 0.0;
        for (int i = 0; i < vecSize; i++) {
            Complex a = values[i];
            Complex b = testVec[i].conjugate();
            sum += a.multiply(b).abs();
        }
        return sum;
    }

    public void toPar(Complex[] fd1, Complex[] pa1) {
        int nSigs = fd1.length;
        for (int i = 0; i < nSigs; i++) {
            parameters[i * 4 + 0] = fd1[i].abs();  // decay
            parameters[i * 4 + 1] = Math.atan2(fd1[i].getImaginary(), fd1[i].getReal());  // freq

            parameters[i * 4 + 2] = pa1[i].abs();  // amplitude
            parameters[i * 4 + 3] = Math.atan2(pa1[i].getImaginary(), pa1[i].getReal());  // phase
            Complex testfd = ComplexUtils.polar2Complex(parameters[i * 4], parameters[i * 4 + 1]);
            Complex testpa = ComplexUtils.polar2Complex(parameters[i * 4 + 2], parameters[i * 4 + 3]);
        }
    }

    public void toComplex(double[] par) {
        int nSigs = par.length / 4;
        for (int i = 0; i < nSigs; i++) {
            double d = par[i * 4];
            double f = par[i * 4 + 1];
            double a = par[i * 4 + 2];
            double p = par[i * 4 + 3];
            fd[i] = ComplexUtils.polar2Complex(d, f);
            pa[i] = ComplexUtils.polar2Complex(a, p);
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
            double delta = values[i].subtract(testVec[i]).abs();
            rss += delta * delta;
        }
        return rss;
    }

    public double calcSumAbs() {
        fillVecBySignals();
        double sumAbs = 0.0;
        for (int i = 0; i < vecSize; i++) {
            double deltaAbs = values[i].subtract(testVec[i]).abs();
            sumAbs += deltaAbs;
        }
        return sumAbs;
    }

    @Override
    public double value(final double[] opars) {
        denormalize(opars, parameters);
        toComplex(parameters);

        double rms = calcRMS();
        System.out.println(rms);
        return rms;
    }

    public PointValuePair refineCMAES(final int nSteps, final double stopFitness, final double sigma, final double lambdaMul, final int diagOnly, final boolean useDegrees) {
        //initial guess for energy. This value will continually be updated
        //calls getDihdrals reduces all angles
        toPar(fd, pa);
        //sets the angle boundaries
        setBoundaries(0.1);
        double value = calcRMS();
        System.out.println("start value " + value);

        DEFAULT_RANDOMGENERATOR.setSeed(1);

        //suggested default value for population size represented by variable 'labda'
        //anglesValue.length represents the number of parameters
        int lambda = (int) (lambdaMul * FastMath.round(4 + 3 * FastMath.log(values.length)));

        CMAESOptimizer optimizer = new CMAESOptimizer(nSteps, stopFitness, true, diagOnly, 0,
                DEFAULT_RANDOMGENERATOR, true,
                new SimpleValueChecker(100 * Precision.EPSILON, 100 * Precision.SAFE_MIN));

        normalize(parameters, normValues);

        PointValuePair result = optimizer.optimize(
                new CMAESOptimizer.PopulationSize(lambda),
                new CMAESOptimizer.Sigma(inputSigma), new MaxEval(2000000),
                new ObjectiveFunction(this), GoalType.MINIMIZE,
                new SimpleBounds(normBoundaries[0], normBoundaries[1]),
                new InitialGuess(normValues));

        System.out.println(optimizer.getIterations() + " " + result.getValue());
        return result;
    }
}
