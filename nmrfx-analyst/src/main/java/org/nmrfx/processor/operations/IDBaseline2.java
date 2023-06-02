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
package org.nmrfx.processor.operations;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.nmrfx.processor.math.Vec;

/**
 * @author johnsonb
 */
public class IDBaseline2 extends Operation {

    public enum ThreshMode {
        SDEV,
        AUTO,
        FRACTION,
        ABS;
    }

    private final int minSize;
    private final int[] limits;
    private final double ratio;
    private final ThreshMode mode;
    private boolean[] signalPoints;

    public IDBaseline2 eval(Vec vector) throws OperationException {
        analyzeWithSDev(vector);
        return this;
    }

    public IDBaseline2(int minSize, int[] limits, double ratio, ThreshMode mode) {
        this.minSize = minSize;
        this.limits = limits;
        this.ratio = ratio;
        this.mode = mode;
    }

    public boolean[] getResult() //what if this is called n times?
    {
        return signalPoints;
    }

    private double getFractionThreshold(Vec vector, double fraction) throws OperationException {
        if (vector.isComplex()) {
            throw new OperationException("idBaseline2: vector complex");
        }
        signalPoints = new boolean[vector.getSize()];
        double[] rvec = vector.getRvec();
        if (fraction > 95.0) {
            fraction = 95.0;
        } else if (fraction < 5.0) {
            fraction = 5.0;
        }
        Percentile percentile = new Percentile(fraction);
        double value = percentile.evaluate(rvec);
        return value;

    }

    private void analyzeWithSDev(Vec vector) throws OperationException {
        if (vector.isComplex()) {
            throw new OperationException("idBaseline2: vector complex");
        }
// find good threshold
// start with threshold above everything, then recalculate by separating points into baseline and signal
// when number of baseline points doesn't change, threshold is set
        signalPoints = new boolean[vector.getSize()];
        int first = 0;
        int last = 0;
        double[] rvec = vector.getRvec();
        double threshold;
        int winSize = vector.getSize() / 256;
        if (winSize < 4) {
            winSize = 4;
        }
        double[] sdevMean = Util.getMeanAndStdDev(vector, winSize, 2);
        double sdevThreshold = sdevMean[0] + ratio * sdevMean[1];
        double autoThreshold = getThreshold(vector, ratio);
        double fractionThreshold = getFractionThreshold(vector, ratio);

        switch (mode) {
            case SDEV:
                threshold = sdevThreshold;
                break;
            case AUTO:
                threshold = autoThreshold;
                break;
            case FRACTION:
                threshold = fractionThreshold;
                break;
            case ABS:
                threshold = ratio;
                break;
            default:
                threshold = sdevThreshold;
        }

        for (int i = minSize; i < (vector.getSize() - minSize - 1); i++) {
            boolean isSignal = false;
            for (int j = -minSize; j <= minSize; j++) {
                if (rvec[i + j] > threshold) {
                    isSignal = true;
                    break;
                }
            }
            // used to set limits value which is used as a range for autophase op so that phasing is not done
            // on very weak signals at edge of spectrum
            if (isSignal && (rvec[i] > (20.0 * threshold))) {
                if ((i > (vector.getSize() / 16)) && (first == 0)) {
                    first = i;
                }
                if (i < (vector.getSize() - vector.getSize() / 16)) {
                    last = i;
                }
            }
            signalPoints[i] = isSignal;
        }
        for (int j = 0; j < minSize; j++) {
            signalPoints[j] = true;
            signalPoints[vector.getSize() - j - 1] = true;
        }
        first = minSize + 1;
        last = vector.getSize() - minSize;
        limits[0] = first;
        limits[1] = last;
    }

    static double getThreshold(Vec vector, double ratio) {
        double threshold = Double.MAX_VALUE;
        int lastBaseline = -1;

        double[] rvec = vector.getRvec();
        while (true) {
            double sum = 0.0;
            int nBaseline = 0;
            for (int i = 0; i < vector.getSize(); i++) {
                double value = rvec[i];
                if (value < threshold) {
                    nBaseline++;
                    sum += value;
                }
            }
            double mean = sum / nBaseline;

            double sumSq = 0.0;
            for (int i = 0; i < vector.getSize(); i++) {
                double value = rvec[i];
                if (value < threshold) {
                    double delta = rvec[i] - mean;
                    sumSq += delta * delta;
                }
            }
            double sdev = Math.sqrt(sumSq / nBaseline);
            if (nBaseline == lastBaseline) {
                break;
            }
            lastBaseline = nBaseline;
            threshold = mean + ratio * sdev;
        }
        return threshold;

    }
}
