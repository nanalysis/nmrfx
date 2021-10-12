/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chart;

/**
 *
 * @author brucejohnson
 */
public class AxisTransform {

    double min;
    double max;
    double pixMin;
    double pixMax;

    public double tr(double value) {
        double f = (value - min) / (max - min);
        return f * (pixMax - pixMin) + pixMin;
    }

}
