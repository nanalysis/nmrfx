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
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.datasets;

/**
 * Instances of this class can calculate and store statistical information about
 * a region of the dataset.
 *
 * @author brucejohnson
 */
public class RegionData {

    static final private String[] FIELDS = {"Min", "Max", "Extreme", "Center", "RVolume", "EVolume", "Mean", "RMS", "N", "MaxPt"};

    protected int npoints = 0;
    protected int nEllipse = 0;
    protected double value = 0.0;
    protected double center = 0.0;
    protected double jitter = 0.0;
    protected double volume_e = 0.0;
    protected double volume_r = 0.0;
    protected double i_max = Double.NEGATIVE_INFINITY;
    protected double i_min = Double.MAX_VALUE;
    protected double i_extreme = 0.0;
    protected double s = 0.0;
    protected double volume_t = 0.0;
    protected double svar = 0.0;
    protected double rms = 0.0;
    protected double mean = 0.0;
    int[] maxPoint;
    double[] dmaxPoint;
    private final DatasetBase dataset;

    public RegionData(DatasetBase dataset) {
        this.dataset = dataset;
        maxPoint = new int[dataset.getNDim()];
        dmaxPoint = new double[dataset.getNDim()];
    }

    public void calcPass1(int[] iPointAbs, int[] cpt, double[] width, final int[] dim, double threshold, double[] iTol) {
        if (((center > 0.0) && (value > threshold)) || ((center < 0.0) && (value < threshold))) {
            volume_t += value;
        }
        s = value - mean;
        svar += (s * s);
        boolean inTol = true;
        for (int ii = 0; ii < dataset.getNDim(); ii++) {
            int delta = Math.abs(iPointAbs[ii] - cpt[ii]);
            if (delta > dataset.getSizeReal(dim[ii]) / 2) {
                delta = dataset.getSizeReal(dim[ii]) - delta;
            }
            if (delta > iTol[ii]) {
                inTol = false;
                break;
            }
        }
        if (inTol && (((center > 0.0) && (value > center)) || ((center < 0.0) && (value < center)))) {
            if (Math.abs(value) > Math.abs(jitter)) {
                jitter = value;
            }
        }
    }

    /*
     * Sum points in optimal eliptical area. 0.47 = (1.37/2.0)^2 1.37 is
     * approximately the optimal value for the peak integration area see.
     * Rischel JMR A 116:255-258 (1995) 2.0 is to get the radius from
     * "diameter" Note: the Rischel paper uses a rectangular not eliptical
     * area.
     *
     */
    public void calcPass0(final int[] iPointAbs, final int[] cpt, final double[] width, final int[] dim) {
        npoints++;
        volume_r += value;
        double r2 = 0.0;
        boolean iscenter = true;
        int nDim = dataset.getNDim();
        for (int ii = 0; ii < nDim; ii++) {
            if (iPointAbs[ii] != cpt[ii]) {
                iscenter = false;
            }
            // if width is near 0.0 (use tolerance to avoid exact test for 0.0)
            //   then we're probably integrating an n-1 dim peak in an n dim dataset
            // so don't check r2 distance from center
            if (width[ii] > 1.0e-6) {
                int delta = Math.abs(iPointAbs[ii] - cpt[ii]);
                if (delta > dataset.getSizeReal(dim[ii]) / 2) {
                    delta = dataset.getSizeReal(dim[ii]) - delta;
                }
                r2 += (delta * delta) / (0.47 * width[ii] * width[ii]);
            }
        }
        if (iscenter) {
            center = value;
            jitter = center;
        }
        if (r2 < 1.0) {
            volume_e += value;
            nEllipse++;
        }
        if (Math.abs(value) > Math.abs(i_extreme)) {
            i_extreme = value;
            System.arraycopy(iPointAbs, 0, maxPoint, 0, nDim);
        }
        if (value > i_max) {
            i_max = value;
        }
        if (value < i_min) {
            i_min = value;
        }
    }

    /**
     * Return the number of data points in the region
     *
     * @return the number of points
     */
    public int getNpoints(String mode) {
        int n = 1;
        if (mode.startsWith("evol")) {
            n = nEllipse;
        } else if (mode.startsWith("vol")) {
            n = npoints;
        } else {
            n = 1;
        }
        return n;
    }

    /**
     * Return the number of data points in the region
     *
     * @return the number of points
     */
    public int getNpoints() {
        return npoints;
    }

    /**
     * Return the number of data points in the elliptical region
     *
     * @return the number of points
     */
    public int getNEllipticalPoints() {
        return nEllipse;
    }

    /**
     * Return the last value set
     *
     * @return the value
     */
    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    /**
     * Return the value at center
     *
     * @return the center value
     */
    public double getCenter() {
        return center;
    }

    /**
     * Return the maximum value near the center
     *
     * @return maximum value near center
     */
    public double getJitter() {
        return jitter;
    }

    /**
     * Return the sum of values in an elliptical region around center
     *
     * @return the elliptical volume
     */
    public double getVolume_e() {
        return volume_e;
    }

    /**
     * Return the sum of values in a rectangular region around center
     *
     * @return the rectangular volume
     */
    public double getVolume_r() {
        return volume_r;
    }

    /**
     * Return the maximum value in region
     *
     * @return maximum value
     */
    public double getMax() {
        return i_max;
    }

    /**
     * Return the minimum value in region
     *
     * @return minimum value
     */
    public double getMin() {
        return i_min;
    }

    /**
     * Return the value with the largest absolute value in region
     *
     * @return extreme value
     */
    public double getExtreme() {
        return i_extreme;
    }

    protected double getS() {
        return s;
    }

    /**
     * @return the volume_t
     */
    public double getVolume_t() {
        return volume_t;
    }

    /**
     * Return the sum of squared deviations from mean in region
     *
     * @return sum of squares
     */
    public double getSumSq() {
        return svar;
    }

    public void setSVar(double svar) {
        this.svar = svar;
    }

    /**
     * Return the rms of deviations from mean in region
     *
     * @return the rms value
     */
    public double getRMS() {
        return rms;
    }

    public void setRMS(double rms) {
        this.rms = rms;
    }

    /**
     * Return the mean of the values in the rectangular region
     *
     * @return the mean
     */
    public double getMean() {
        return mean;
    }

    public void setMean(double mean) {
        this.mean = mean;
    }

    /**
     * Return the position in points of the maximum intensity in region
     *
     * @return array of indices of the maximum
     */
    public int[] getMaxPoint() {
        return maxPoint.clone();
    }

    /**
     * Return the position in points (in double precision) of the maximum
     * intensity in region
     *
     * @return array of double valued indices of the maximum
     */
    public double[] getMaxDPoint() {
        return dmaxPoint.clone();
    }

    public void setMaxDPoint(double[] maxD) {
        this.dmaxPoint = maxD.clone();
    }

    public static String[] getFields() {
        return FIELDS;
    }

    public double getValue(String name) {
        double result = 0.0;
        switch (name) {
            case "Min":
                result = getMin();
                break;
            case "Max":
                result = getMax();
                break;
            case "Extreme":
                result = getExtreme();
                break;
            case "Center":
                result = getCenter();
                break;
            case "RVolume":
                result = getVolume_r();
                break;
            case "EVolume":
                result = getVolume_e();
                break;
            case "Mean":
                result = getMean();
                break;
            case "RMS":
                result = getRMS();
                break;
        }
        return result;
    }
}
