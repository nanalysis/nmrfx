package org.nmrfx.processor.optimization;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
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

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

public class Fitter {

    private static final Logger log = LoggerFactory.getLogger(Fitter.class);
    private static final RandomGenerator random = new SynchronizedRandomGenerator(new Well19937c());

    boolean reportFitness = false;
    int reportAt = 10;
    double[][] parValues;
    double[][] xValues;
    double[] yValues;
    double[] errValues;

    double[] lowerBounds;
    double[] upperBounds;
    double[] start;
    double inputSigma;
    BiFunction<double[], double[], Double> function;
    BiFunction<double[], double[][], Double> valuesFunction = null;
    ExpressionEvaluator ee = null;

    public Fitter() {

    }


    public static Fitter getFitter(BiFunction<double[], double[], Double> function) {
        Fitter fitter = new Fitter();
        fitter.function = function;
        return fitter;
    }

    public static Fitter getArrayFitter(BiFunction<double[], double[][], Double> function) {
        Fitter fitter = new Fitter();
        fitter.valuesFunction = function;
        return fitter;
    }

    public static Fitter getExpressionFitter(String expression, String[] parNames, String[] varNames) throws CompileException {
        Fitter fitter = new Fitter();

        ExpressionEvaluator ee = new ExpressionEvaluator();
        Class[] allClasses = new Class[parNames.length + varNames.length];
        String[] allNames = new String[parNames.length + varNames.length];
        System.arraycopy(parNames, 0, allNames, 0, parNames.length);
        System.arraycopy(varNames, 0, allNames, parNames.length, varNames.length);

        Arrays.fill(allClasses, double.class);
        ee.setParameters(allNames, allClasses);
        ee.setExpressionType(double.class);
        ee.cook(expression);
        fitter.ee = ee;
        return fitter;
    }

    public double evalExpression(double[] pars, double[] vars) {
        Double[] exprPars = new Double[pars.length + vars.length];
        for (int i = 0; i < pars.length; i++) {
            exprPars[i] = pars[i];
        }
        for (int i = 0; i < vars.length; i++) {
            exprPars[i + pars.length] = vars[i];
        }
        double value = 0.0;
        try {
            value = (Double) ee.evaluate(exprPars);
        } catch (InvocationTargetException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return value;
    }

    public double[] evalExpression(double[] pars, double[][] vars) {
        Double[] exprPars = new Double[pars.length + vars.length];
        int n = vars[0].length;
        double[] values = new double[n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < pars.length; i++) {
                exprPars[i] = pars[i];
            }
            for (int i = 0; i < vars.length; i++) {
                exprPars[i + pars.length] = vars[i][j];
            }
            double value = 0.0;
            try {
                value = (Double) ee.evaluate(exprPars);
            } catch (InvocationTargetException ex) {
                log.warn(ex.getMessage(), ex);
            }
            values[j] = value;
        }
        return values;
    }

    public PointValuePair fit(double[] start, double[] lowerBounds, double[] upperBounds, double inputSigma, int nTries) throws Exception {
        this.start = start;
        this.lowerBounds = lowerBounds.clone();
        this.upperBounds = upperBounds.clone();
        this.inputSigma = inputSigma;
        Optimizer opt = new Optimizer();
        opt.setXYE(xValues, yValues, errValues);
        PointValuePair bestResult = null;
        for (int iTry=0;iTry< nTries;iTry++) {
            PointValuePair result = opt.refineCMAES(this.start, inputSigma);
            if ((bestResult == null) || (result.getValue() < bestResult.getValue())) {
                bestResult = result;
            }
            for (int iPar=0;iPar< this.start.length;iPar++) {
                double delta = (this.upperBounds[iPar] - this.lowerBounds[iPar]);
                this.start[iPar] = this.lowerBounds[iPar] + delta * 0.8 * random.nextDouble() + delta*0.1;
            }
        }


        return bestResult;
    }

    public double rms(double[] pars) throws Exception {
        Optimizer opt = new Optimizer();
        opt.setXYE(xValues, yValues, errValues);
        return opt.valueWithDenormalized(pars);
    }

    public int maxDevLoc(double[] yCalc, int halfSize) {
        double maxPosDev = Double.NEGATIVE_INFINITY;
        int devLoc = 0;

        for (int i = halfSize; i < (yValues.length - halfSize); i++) {
            double sumDelta = 0.0;
            for (int j = -halfSize; j <= halfSize; j++) {
                int k = i + j;
                if (yValues[k] > yCalc[k]) {
                    sumDelta += yValues[k] - yCalc[k];
                }
            }
            if (sumDelta > maxPosDev) {
                maxPosDev = sumDelta;
                devLoc = (int) xValues[0][i];
            }
        }
        return devLoc;
    }

    public void setXYE(double[][] xValues, double[] yValues, double[] errValues) {
        this.xValues = xValues;
        this.yValues = yValues;
        this.errValues = errValues;
    }

    public double[][] getX() {
        return xValues;
    }

    public double[] getY() {
        return yValues;
    }

    class Optimizer implements MultivariateFunction {

        RandomGenerator random = new SynchronizedRandomGenerator(new Well19937c());

        public class Checker extends SimpleValueChecker {

            public Checker(double relativeThreshold, double absoluteThreshold, int maxIter) {
                super(relativeThreshold, absoluteThreshold, maxIter);
            }

            @Override
            public boolean converged(final int iteration, final PointValuePair previous, final PointValuePair current) {
                boolean converged = super.converged(iteration, previous, current);
                if (reportFitness) {
                    if (converged) {
                        System.out.println(previous.getValue() + " " + current.getValue());
                    }
                    if (converged || (iteration == 1) || ((iteration % reportAt) == 0)) {
                        long time = System.currentTimeMillis();
                        long deltaTime = time - startTime;
                        System.out.println(deltaTime + " " + iteration + " " + current.getValue());
                    }
                }
                return converged;
            }
        }

        double[][] xValues;
        double[] yValues;
        double[] errValues;
        double[][] values;
        long startTime;
        long endTime;
        long fitTime;
        double tol = 1.0e-5;
        boolean absMode = false;
        boolean weightFit = false;

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
                final double value;
                for (int j = 0; j < xValues.length; j++) {
                    ax[j] = xValues[j][i];
                }
                if (ee != null) {
                    value = evalExpression(par, ax);
                } else {
                    value = function.apply(par, ax);

                }
                double delta = (value - yValues[i]);
                if (weightFit) {
                    delta /= errValues[i];
                }
                sumAbs += FastMath.abs(delta);
                sumSq += delta * delta;
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

        void setXYE(double[][] xValues, double[] yValues, double[] errValues) {
            this.xValues = xValues;
            this.yValues = yValues;
            this.errValues = errValues;
            // setup values array in case we've passed in a functin that uses it

            int nA = xValues.length + 2;
            this.values = new double[nA][];
            for (int i = 0; i < xValues.length; i++) {
                this.values[i] = xValues[i];
            }
            this.values[nA - 2] = yValues;
            this.values[nA - 1] = errValues;
        }

        public PointValuePair refineCMAES(double[] guess, double inputSigma) throws Exception {
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
            PointValuePair result = null;

            try {
                result = cmaesOptimizer.optimize(
                        new CMAESOptimizer.PopulationSize(lambda),
                        new CMAESOptimizer.Sigma(sigma),
                        new MaxEval(2000000),
                        new ObjectiveFunction(this), GoalType.MINIMIZE,
                        new SimpleBounds(normLower, normUpper),
                        new InitialGuess(normGuess));
            } catch (DimensionMismatchException | NotPositiveException | NotStrictlyPositiveException |
                     TooManyEvaluationsException e) {
                throw new Exception("failure to fit data " + e.getMessage());
            }
            endTime = System.currentTimeMillis();
            fitTime = endTime - startTime;
            PointValuePair deNormResult = new PointValuePair(deNormalize(result.getPoint()), result.getValue());

            return deNormResult;
        }
    }

    public double[] bootstrap(double[] guess, int nSim) {
        reportFitness = false;
        int nPar = start.length;
        parValues = new double[nPar + 1][nSim];

        IntStream.range(0, nSim).parallel().forEach(iSim -> {
            double[][] newX = new double[xValues.length][yValues.length];
            double[] newY = new double[yValues.length];
            double[] newErr = new double[yValues.length];
            Optimizer optimizer = new Optimizer();
            for (int iValue = 0; iValue < yValues.length; iValue++) {
                int rI = random.nextInt(yValues.length);
                for (int xIndex = 0; xIndex < newX.length; xIndex++) {
                    newX[xIndex][iValue] = xValues[xIndex][rI];
                }
                newY[iValue] = yValues[rI];
                newErr[iValue] = errValues[rI];
            }

            // fixme  idNum should be set in above loop
            optimizer.setXYE(newX, newY, newErr);

            PointValuePair result;
            try {
                result = optimizer.refineCMAES(guess, inputSigma);
            } catch (Exception ex) {
                return;
            }
            double[] rPoint = result.getPoint();
            for (int j = 0; j < nPar; j++) {
                parValues[j][iSim] = rPoint[j];
            }
            parValues[nPar][iSim] = result.getValue();
        });

        double[] parSDev = new double[nPar];
        for (int i = 0; i < nPar; i++) {
            DescriptiveStatistics dStat = new DescriptiveStatistics(parValues[i]);
            parSDev[i] = dStat.getStandardDeviation();
        }
        return parSDev;
    }
}
