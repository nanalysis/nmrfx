/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chart;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Arrays;

/**
 * @author brucejohnson
 */
public class BoxPlotData {

    final double min;
    final double q1;
    final double median;
    final double q3;
    final double max;
    final double[] outliers;

    public BoxPlotData(double[] data) {
        DescriptiveStatistics dStat = new DescriptiveStatistics(data);
        min = dStat.getMin();
        q1 = dStat.getPercentile(25.0);
        median = dStat.getPercentile(50.0);
        q3 = dStat.getPercentile(75.0);
        max = dStat.getMax();
        double minW = getMinWhisker();
        double maxW = getMaxWhisker();
        outliers = Arrays.stream(data).filter(v -> (v < minW) || (v > maxW)).toArray();
    }

    @Override
    public String toString() {
        return "FiveNumberSummary{" + "min=" + min + ", q1=" + q1 + ", median=" + median + ", q3=" + q3 + ", max=" + max + '}';
    }

    public double getIQR() {
        return q3 - q1;
    }

    public final double getMinWhisker() {
        return getMinWhisker(1.5);
    }

    public final double getMinWhisker(double extent) {
        double min1 = q1 - getIQR() * extent;
        return Math.max(min, min1);
    }

    public final double getMaxWhisker() {
        return getMaxWhisker(1.5);
    }

    public final double getMaxWhisker(double extent) {
        double max1 = q3 + getIQR() * extent;
        return Math.min(max, max1);
    }

}
