package org.nmrfx.processor.optimization;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.FastMath;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

// Fitter2 allows multiple yValues per index (compared to Fitter, which allows only 1).
// we may refactor so Fitter2 extends, or replaces Fitter

public class Fitter2 {
    private static final Logger log = LoggerFactory.getLogger(Fitter2.class);
    private static final RandomGenerator random = new SynchronizedRandomGenerator(new Well19937c());

    boolean reportFitness = false;
    int reportAt = 10;
    double[][] parValues;
    double[][] xValues;
    double[][] yValues;
    double[][] errValues;

    double[] lowerBounds;
    double[] upperBounds;
    double[] start;
    double inputSigma;
    BiFunction<double[], double[], double[]> function;
    BiFunction<double[], double[][], Double> valuesFunction = null;
    ExpressionEvaluator expressionEvaluator = null;

    private Fitter2() {

    }

    public static Fitter getFitter(BiFunction<double[], double[], Double> function) {
        Fitter fitter = new Fitter();
        fitter.function = function;
        return fitter;
    }

    public static Fitter2 getArrayFitter(BiFunction<double[], double[][], Double> function) {
        Fitter2 fitter = new Fitter2();
        fitter.valuesFunction = function;
        return fitter;
    }

    // for runtime compilation of expressions with janino.  Not yet implemented here
    public static Fitter2 getExpressionFitter(String expression, String[] parNames, String[] varNames) throws CompileException {
        Fitter2 fitter = new Fitter2();

        ExpressionEvaluator ee = new ExpressionEvaluator();
        Class[] allClasses = new Class[parNames.length + varNames.length];
        String[] allNames = new String[parNames.length + varNames.length];
        System.arraycopy(parNames, 0, allNames, 0, parNames.length);
        System.arraycopy(varNames, 0, allNames, parNames.length, varNames.length);

        Arrays.fill(allClasses, double.class);
        ee.setParameters(allNames, allClasses);
        ee.setExpressionType(double.class);
        ee.cook(expression);
        fitter.expressionEvaluator = ee;
        return fitter;
    }

    public Optional<PointValuePair> fit(double[] start, double[] lowerBounds, double[] upperBounds, double inputSigma) {
        this.start = start;
        this.lowerBounds = lowerBounds.clone();
        this.upperBounds = upperBounds.clone();
        this.inputSigma = inputSigma;
        Optimizer opt = new Optimizer();
        opt.setXYE(xValues, yValues, errValues);

        return opt.refineCMAES(start, inputSigma);
    }

    public double rms(double[] pars) {
        Optimizer opt = new Optimizer();
        opt.setXYE(xValues, yValues, errValues);
        return opt.valueWithDenormalized(pars);
    }

    public void setXYE(double[][] xValues, double[][] yValues, double[][] errValues) {
        this.xValues = xValues;
        this.yValues = yValues;
        this.errValues = errValues;
    }

    public double[][] getX() {
        return xValues;
    }

    public double[][] getY() {
        return yValues;
    }

    class Optimizer implements MultivariateFunction {
        double[][] xValues;
        double[][] yValues;
        double[][] errValues;
        double[][] values;
        long startTime;
        long endTime;
        long fitTime;
        double tol = 1.0e-5;
        boolean absMode = false;
        boolean weightFit = false;
        RandomGenerator random = new SynchronizedRandomGenerator(new Well19937c());

        public class Checker extends SimpleValueChecker {

            public Checker(double relativeThreshold, double absoluteThreshold, int maxIter) {
                super(relativeThreshold, absoluteThreshold, maxIter);
            }

            @Override
            public boolean converged(final int iteration, final PointValuePair previous, final PointValuePair current) {
                boolean converged = super.converged(iteration, previous, current);
                if (reportFitness && (converged || (iteration == 1) || ((iteration % reportAt) == 0))) {
                    long time = System.currentTimeMillis();
                    long deltaTime = time - startTime;
                    log.info("Delta {} Iteration {} Value {}", deltaTime, iteration, current.getValue());
                }
                return converged;
            }
        }

        @Override
        public double value(double[] normPar) {
            double[] par = deNormalize(normPar);
            return valueWithDenormalized(par);
        }

        public double valueWithDenormalized(double[] par) {
            if (valuesFunction != null) {
                return valuesFunction.apply(par, values);
            }
            double sumAbs = 0.0;
            double sumSq = 0.0;
            double[] ax = new double[xValues.length];
            for (int i = 0; i < yValues.length; i++) {
                final double[] value;
                for (int j = 0; j < xValues.length; j++) {
                    ax[j] = xValues[j][i];
                }
                value = function.apply(par, ax);

                for (int j = 0; j < yValues.length; j++) {
                    double delta = (value[j] - yValues[j][i]);
                    if (weightFit) {
                        delta /= errValues[j][i];
                    }
                    sumAbs += FastMath.abs(delta);
                    sumSq += delta * delta;
                }
            }
            if (absMode) {
                return sumAbs / (yValues.length - par.length);
            } else {
                return sumSq / (yValues.length - par.length);
            }

        }

        void fixGuesses(double[] guesses) {
            for (int i = 0; i < guesses.length; i++) {
                if (guesses[i] > 98.0) {
                    guesses[i] = 98.0;
                } else if (guesses[i] < 2) {
                    guesses[i] = 2.0;
                }
            }
        }

        double[] normalize(double[] pars) {
            double[] normPars = new double[pars.length];
            for (int i = 0; i < pars.length; i++) {
                normPars[i] = 100.0 * (pars[i] - lowerBounds[i]) / (upperBounds[i] - lowerBounds[i]);
            }
            return normPars;
        }

        double[] deNormalize(double[] normPars) {
            double[] pars = new double[normPars.length];
            for (int i = 0; i < pars.length; i++) {
                pars[i] = normPars[i] / 100.0 * (upperBounds[i] - lowerBounds[i]) + lowerBounds[i];
            }
            return pars;
        }

        void setXYE(double[][] xValues, double[][] yValues, double[][] errValues) {
            this.xValues = xValues;
            this.yValues = yValues;
            this.errValues = errValues;
            // setup values array in case we've passed in a function that uses it

            int nA = xValues.length + yValues.length + errValues.length;
            this.values = new double[nA][];
            int k = 0;
            for (double[] xValue : xValues) {
                this.values[k] = new double[xValue.length];
                System.arraycopy(xValue, 0, this.values[k++], 0, xValue.length);
            }
            for (double[] yValue : yValues) {
                this.values[k] = new double[yValue.length];
                System.arraycopy(yValue, 0, this.values[k++], 0, yValue.length);
            }
            for (double[] errValue : errValues) {
                this.values[k] = new double[errValue.length];
                System.arraycopy(errValue, 0, this.values[k++], 0, errValue.length);
            }
        }

        public Optional<PointValuePair> refineCMAES(double[] guess, double inputSigma) {
            startTime = System.currentTimeMillis();
            random.setSeed(1);
            double lambdaMul = 3.0;
            int lambda = (int) (lambdaMul * FastMath.round(4 + 3 * FastMath.log(guess.length)));
            int nSteps = 2000;
            double stopFitness = 0.0;
            int diagOnly = 0;
            double[] normLower = new double[guess.length];
            double[] normUpper = new double[guess.length];
            double[] sigma = new double[guess.length];
            Arrays.fill(normLower, 0.0);
            Arrays.fill(normUpper, 100.0);
            Arrays.fill(sigma, inputSigma);
            double[] normGuess = normalize(guess);
            fixGuesses(normGuess);

            CMAESOptimizer cmaesOptimizer = new CMAESOptimizer(nSteps, stopFitness, true, diagOnly, 0,
                    random, true,
                    new Checker(tol, tol, nSteps));
            PointValuePair result;

            try {
                result = cmaesOptimizer.optimize(
                        new CMAESOptimizer.PopulationSize(lambda),
                        new CMAESOptimizer.Sigma(sigma),
                        new MaxEval(2000000),
                        new ObjectiveFunction(this), GoalType.MINIMIZE,
                        new SimpleBounds(normLower, normUpper),
                        new InitialGuess(normGuess));
            } catch (DimensionMismatchException | NotPositiveException | NotStrictlyPositiveException |
                     MaxCountExceededException e) {
                log.error("Failure in refineCMAES", e);
                return Optional.empty();
            }
            endTime = System.currentTimeMillis();
            fitTime = endTime - startTime;
            return Optional.of(new PointValuePair(deNormalize(result.getPoint()), result.getValue()));
        }
    }

    public Optional<double[]> bootstrap(double[] guess, int nSim) {
        reportFitness = false;
        int nPar = start.length;
        parValues = new double[nPar + 1][nSim];
        AtomicInteger nCount = new AtomicInteger();
        IntStream.range(0, nSim).parallel().forEach(iSim -> {
            double[][] newX = new double[xValues.length][yValues[0].length];
            double[][] newY = new double[yValues.length][yValues[0].length];
            double[][] newErr = new double[yValues.length][yValues[0].length];
            Optimizer optimizer = new Optimizer();
            for (int iValue = 0; iValue < yValues[0].length; iValue++) {
                int rI = random.nextInt(yValues[0].length);
                for (int xIndex = 0; xIndex < newX.length; xIndex++) {
                    newX[xIndex][iValue] = xValues[xIndex][rI];
                }
                for (int iY = 0; iY < yValues.length; iY++) {
                    newY[iY][iValue] = yValues[iY][rI];
                    newErr[iY][iValue] = errValues[iY][rI];
                }
            }

            optimizer.setXYE(newX, newY, newErr);


            var optResult = optimizer.refineCMAES(guess, inputSigma);
            if (optResult.isPresent()) {
                PointValuePair result = optResult.get();
                double[] rPoint = result.getPoint();
                for (int j = 0; j < nPar; j++) {
                    parValues[j][iSim] = rPoint[j];
                }
                parValues[nPar][iSim] = result.getValue();
                nCount.incrementAndGet();
            } else {
                return;
            }
        });
        if (nCount.get() == nSim) {
            double[] parSDev = new double[nPar];
            for (int i = 0; i < nPar; i++) {
                DescriptiveStatistics dStat = new DescriptiveStatistics(parValues[i]);
                parSDev[i] = dStat.getStandardDeviation();
            }
            return Optional.of(parSDev);
        }
        return Optional.empty();
    }
}
