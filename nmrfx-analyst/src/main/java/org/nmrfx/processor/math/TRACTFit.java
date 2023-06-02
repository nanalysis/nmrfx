/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.math;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.nmrfx.processor.optimization.Fitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author brucejohnson
 */
public class TRACTFit {

    private static final Logger log = LoggerFactory.getLogger(TRACTFit.class);

    boolean reportFitness = true;
    int reportAt = 10;
    long startTime = 0;
    RelaxEquations[] relaxEquations;
    double rA;
    double rB;
    double[][] xValues;
    double[] yValues;
    double[] errValues;
    double[] bestPars;
    double[] parErrs;

    public TRACTFit(double sf, String elemI, String elemS) {
        relaxEquations = new RelaxEquations[1];
        relaxEquations[0] = new RelaxEquations(sf, elemI, elemS);
    }

    public TRACTFit(double[] sf, String elemI, String elemS) {
        relaxEquations = new RelaxEquations[sf.length];
        for (int i = 0; i < sf.length; i++) {
            relaxEquations[i] = new RelaxEquations(sf[i], elemI, elemS);
        }
    }

    public double[] getParErrors() {
        return parErrs.clone();
    }

    class MatchFunction implements UnivariateFunction {

        MatchFunction() {
        }

        @Override
        public double value(double tauC) {
            double nab2 = relaxEquations[0].TRACTdeltaAlphaBeta(tauC);
            double value = Math.abs((rB - rA) - nab2);
            return value;
        }
    }

    public double multiValue(double[] pars, double[][] values) {
        int n = values[0].length;
        double csa = pars[0];
        double theta = pars[1];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            int iRes = (int) Math.round(values[0][i]);
            int iSF = (int) Math.round(values[1][i]);
            double tauC = pars[iRes + 2];

            double nab2 = relaxEquations[iSF].TRACTdeltaAlphaBeta(tauC * 1.0e-9, csa * 1.0e-6, theta);
            double delta = (nab2 - values[2][i]) / values[3][i];
            sum += Math.abs(delta);
        }
        return sum / n;
    }

    public PointValuePair fitCSA(int nRes, int[] iresidues, int[] isfs, double[] nab2s, double[] errs) {

        Fitter fitter = Fitter.getArrayFitter(this::multiValue);
        double[][] xValues2 = new double[2][nab2s.length];
        for (int i = 0; i < nab2s.length; i++) {
            xValues2[0][i] = iresidues[i];
            xValues2[1][i] = isfs[i];
        }
        fitter.setXYE(xValues2, nab2s, errs);
        double[] start = new double[2 + nRes];
        double[] lower = new double[start.length];
        double[] upper = new double[start.length];
        start[0] = 170.0;
        lower[0] = 150.0;
        upper[0] = 180.0;
        start[1] = 4.0;
        lower[1] = 0.0;
        upper[1] = 12.0;
        for (int i = 2; i < start.length; i++) {
            start[i] = 5.0;
            lower[i] = 2.0;
            upper[i] = 20.0;
        }

        try {
            PointValuePair result = fitter.fit(start, lower, upper, 10.0);
            bestPars = result.getPoint();
            parErrs = fitter.bootstrap(result.getPoint(), 300);
            return result;
        } catch (Exception ex) {
            log.warn(ex.getMessage(), ex);
            return null;
        }

    }

    public double fit(double rA, double rB) {
        this.rA = rA;
        this.rB = rB;
        double tolAbs = 1E-12;
        double min = 1.0e-9;
        double max = 300.0e-9;
        MatchFunction f = new MatchFunction();
        UnivariateObjectiveFunction fOpt = new UnivariateObjectiveFunction(f);
        SearchInterval searchInterval = new SearchInterval(min, max);
        MaxEval maxEval = new MaxEval(100);
        double best;

        BrentOptimizer brentOptimizer = new BrentOptimizer(tolAbs * 10.0, tolAbs);
        UnivariatePointValuePair optValue = brentOptimizer.optimize(fOpt, GoalType.MINIMIZE, searchInterval, maxEval);

        best = optValue.getPoint();
        return best;
    }
}
