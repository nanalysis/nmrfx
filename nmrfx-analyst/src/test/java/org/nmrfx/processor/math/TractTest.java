/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.math;

import org.apache.commons.math3.optim.PointValuePair;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author brucejohnson
 */
public class TractTest {

    double[] yValues = {234.56, 237.99, 230.94, 233.5, 221.72, 233.64, 213.29, 226.8, 207.5, 226.4, 200.4,
            221.94, 190.17, 219.91, 167.9, 209.40, 153.70, 200.53, 144.8, 193.65, 136.7, 187.3,
            130.46, 184.46, 120.20, 176.8, 108.37, 167.87, 100.11, 159.96, 94.71, 153.22, 86.11,
            150.1, 82.33, 146.03, 62.38, 125.4, 52.75, 114.9, 42.64, 99.77, 36.15, 89.94, 30.384,
            80.11, 25.14, 70.79, 18.721, 62.051, 13.799, 54.39, 13.472, 46.94, 11.261, 40.95,
            9.546, 39.31, 7.331, 32.55, 6.815, 29.257, 5.458, 29.163};

    double[] x = {0.001, 0.002, 0.003, 0.004, 0.005, 0.006, 0.008, 0.010, 0.012, 0.014, 0.016, 0.018,
            0.020, 0.022, 0.024, 0.026, 0.028, 0.030, 0.035, 0.040, 0.045, 0.050, 0.055,
            0.060, 0.065, 0.070, 0.075, 0.080, 0.085, 0.090, 0.095, 0.100};

    @Test
    public void testTract() throws IllegalArgumentException {
        int n = yValues.length;
        double[][] xValues = new double[2][n];
        double[] errValues = new double[n];
        for (int i = 0; i < n; i++) {
            xValues[0][i] = 2.0 * x[i / 2];
            xValues[1][i] = i % 2;
            errValues[i] = 1.0;
        }
        TRACTSimFit tractSimFit = new TRACTSimFit(600.0e6, "H", "N");
        tractSimFit.setXYE(xValues, yValues, errValues);
        PointValuePair result = tractSimFit.fit();
        double[] point = result.getPoint();
        double tauC = point[3];
        Assert.assertEquals(tauC, 3.3, 0.1);


    }
}
