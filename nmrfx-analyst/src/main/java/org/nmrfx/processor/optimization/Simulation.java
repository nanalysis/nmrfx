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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization;

import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.general.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.processor.optimization.equations.OptFunction;

import java.util.Random;

/**
 * @author graham
 */
public class Simulation {

    Random rNum = new Random();

    private class SortableParam implements Comparable {

        private final double[] values;
        private final int sIndex;

        SortableParam(PointVectorValuePair v, int sIndex) {
            values = v.getPoint();
            this.sIndex = sIndex;
        }

        public double[] getValues() {
            return values;
        }

        public double getValueToSort() {
            return values[sIndex];
        }

        public int compareTo(Object o) {
            SortableParam sp = (SortableParam) o;
            double cVal1, cVal2;

            cVal1 = this.getValueToSort();
            cVal2 = sp.getValueToSort();

            if (cVal1 < cVal2) {
                return -1;
            } else if (cVal1 > cVal2) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    private int iterations;
    private double[] wt;
    private PointVectorValuePair[] dSet;
    private double sdev;
    private OptFunction func;
    private PointVectorValuePair bestFitTarget;

    public Simulation(OptFunction func,
                      double[] wt,
                      double sdev,
                      int iterations) {
        this.func = func;
        this.wt = wt;
        this.sdev = sdev;
        this.iterations = iterations;

        dSet = new PointVectorValuePair[iterations];

        int maxTries = 10;
        int iTries = 0;
        boolean ok = false;
        do {
            LevenbergMarquardtOptimizer estimator = new LevenbergMarquardtOptimizer();
            iTries++;
            try {
                bestFitTarget = estimator.optimize(800, func, func.target(), wt, func.startpoint());
                ok = true;
            } catch (TooManyEvaluationsException tmE) {
                System.out.println("Too Many Evaluations" + tmE.getMessage());
                throw tmE;
            } catch (MathIllegalArgumentException fEE) {
                System.out.println("Illegal arguments exception " + fEE.getMessage());
                throw fEE;
            }

        } while (!ok);
    }

    public void simulate() {
        LevenbergMarquardtOptimizer estimator;
        int maxTries = 10;
        for (int i = 0; i < iterations; i++) {
            boolean ok = false;
            int iTries = 0;
            do {
                estimator = new LevenbergMarquardtOptimizer();
                iTries++;
                try {
                    dSet[i] = estimator.optimize(400, func, randomizeValues(), wt, bestFitTarget.getPoint());
                    ok = true;
                } catch (TooManyEvaluationsException tmE) {
                    if (iTries >= maxTries) {
                        throw tmE;
                    }
                } catch (MathIllegalArgumentException fEE) {
                    System.out.println("Illegal arguments exception " + fEE.getMessage());
                    return;
                }

            } while (!ok);
        }
    }

    public ConfidenceInterval getConfidenceInterval(double interval, VecID param) {
        int paramIndex = func.getUnboundParamIndex(param);
        SortableParam[] sp = new SortableParam[iterations];
        DescriptiveStatistics dStat = new DescriptiveStatistics();
        for (int i = 0; i < iterations; i++) {
            sp[i] = new SortableParam(dSet[i], paramIndex);
            dStat.addValue(dSet[i].getPoint()[paramIndex]);
        }
        double bottomPercentile = (100.0 - 100.0 * interval) / 2.0;
        double topPercentile = 100.0 - bottomPercentile;
        double dBottom = dStat.getPercentile(bottomPercentile);
        double dTop = dStat.getPercentile(topPercentile);

        double[] cfi = new double[4];

        cfi[0] = bestFitTarget.getPoint()[paramIndex];
        cfi[1] = dBottom;
        cfi[2] = dTop;
        cfi[3] = dStat.getStandardDeviation();

        return new ConfidenceInterval(param,
                interval,
                cfi);
    }

    private double[] randomizeValues() {
        double targetVals[] = func.value(bestFitTarget.getPoint());
        double[] rVals = new double[targetVals.length];

        for (int i = 0; i < rVals.length; i++) {
            rVals[i] = targetVals[i] + rNum.nextGaussian() * sdev;
        }

        return rVals;
    }
}
