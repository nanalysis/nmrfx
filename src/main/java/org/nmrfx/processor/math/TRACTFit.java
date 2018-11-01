/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.math;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;

/**
 *
 * @author brucejohnson
 */
public class TRACTFit {

    boolean reportFitness = true;
    int reportAt = 10;
    long startTime = 0;
    RelaxEquations relaxEqn = new RelaxEquations();
    double B0;
    double rA;
    double rB;

    class MatchFunction implements UnivariateFunction {

        MatchFunction() {
        }

        public double value(double tauC) {
            double nab2 = relaxEqn.TRACTdeltaAlphaBeta(B0, tauC);
            double value = Math.abs((rB - rA) - nab2);
            return value;
        }
    }

    public double fit(double sf, double rA, double rB) {
        this.B0 = sf * 2.0 * Math.PI / RelaxEquations.GAMMA_H;
        this.rA = rA;
        this.rB = rB;
        double tolAbs = 1E-12;
        double min = 5.0e-9;
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
