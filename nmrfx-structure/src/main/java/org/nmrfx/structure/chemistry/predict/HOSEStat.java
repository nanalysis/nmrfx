/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Mean;

import java.util.ArrayList;

/**
 * @author brucejohnson
 */
public class HOSEStat {

    DescriptiveStatistics dStat = new DescriptiveStatistics();
    double[] weights;
    final double range;
    final int nValues;
    final double wmean;

    HOSEStat(ArrayList<Double> ppms, ArrayList<Double> distArray) {
        nValues = ppms.size();
        if (nValues > 0) {
            for (Double ppm : ppms) {
                dStat.addValue(ppm);
            }
            if (distArray.size() > 0) {
                weights = genWeights(distArray);
                wmean = getWeightedMean();
            } else {
                wmean = dStat.getMean();
            }
            range = dStat.getMax() - dStat.getMin();
        } else {
            range = -1.0;
            wmean = Double.NaN;
        }
    }

    HOSEStat(ArrayList<Double> ppms) {
        nValues = ppms.size();
        if (nValues > 0) {
            for (Double ppm : ppms) {
                dStat.addValue(ppm);
            }
            range = dStat.getMax() - dStat.getMin();
            wmean = dStat.getMean();
        } else {
            range = -1.0;
            wmean = Double.NaN;
        }
    }

    final double[] genWeights(ArrayList<Double> distArray) {
        this.weights = new double[distArray.size()];
        int i = 0;
        double sum = 0.0;
        for (Double dist : distArray) {
            sum += dist;
            weights[i++] = dist;
        }
        if (sum == 0.0) {
            sum = distArray.size();
            for (i = 0; i < weights.length; i++) {
                weights[i] = 1.0 / sum;
            }
        } else {
            for (i = 0; i < weights.length; i++) {
                weights[i] = weights[i] / sum;
            }
        }
        return weights;
    }

    public int getNValues() {
        return nValues;
    }

    public double getRange() {
        return range;
    }

    public DescriptiveStatistics getDStat() {
        return dStat;
    }

    public final double getWeightedMean() {
        if (weights == null) {
            return dStat.getMean();
        } else {
            Mean mean = new Mean();
            return mean.evaluate(dStat.getValues(), weights);
        }
    }
}
