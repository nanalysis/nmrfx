/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.dataops;

import org.nmrfx.processor.datasets.Dataset;

/**
 * @author brucejohnson
 */
public class SimDataVecPars {

    private final String label;
    private final int n;
    private double sf;
    private final double sw;
    private final double ref;
    private final double vref;

    public SimDataVecPars(double sf, double sw, int n, double ref, String label) {
        this.sf = sf;
        this.sw = sw;
        this.n = n;
        this.ref = ref;
        this.label = label;
        this.vref = sw / sf / 2 + ref;
    }

    public SimDataVecPars(Dataset currData) {
        this.label = currData.getLabel(0);
        this.sf = currData.getSf(0);
        this.sw = currData.getSw(0);
        this.n = currData.getSizeReal(0);
        this.ref = currData.pointToPPM(0, n / 2);
        this.vref = sw / sf / 2 + ref;
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return the n
     */
    public int getN() {
        return n;
    }

    /**
     * @return the sf
     */
    public double getSf() {
        return sf;
    }

    public void setSF(double value) {
        sf = value;
    }

    /**
     * @return the sw
     */
    public double getSw() {
        return sw;
    }

    /**
     * @return the ref
     */
    public double getRef() {
        return ref;
    }

    /**
     * @return the vref
     */
    public double getVref() {
        return vref;
    }

}
