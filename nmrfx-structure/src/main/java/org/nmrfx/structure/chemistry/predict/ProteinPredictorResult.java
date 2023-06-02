/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

/**
 * @author brucejohnson
 */
public class ProteinPredictorResult {

    final double[] coefs;
    final double[] attrs;
    final double ppm;

    public ProteinPredictorResult(double[] coefs, double[] attrs, double ppm) {
        this.coefs = coefs.clone();
        this.attrs = attrs.clone();
        this.ppm = ppm;
    }

    public ProteinPredictorResult(double ppm) {
        this.coefs = null;
        this.attrs = null;
        this.ppm = ppm;
    }

}
