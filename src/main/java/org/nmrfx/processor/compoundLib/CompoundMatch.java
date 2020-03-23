/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.compoundLib;

/**
 *
 * @author brucejohnson
 */
public class CompoundMatch {

    CompoundData cData;
    double[] shifts;
    double scale;

    CompoundMatch(CompoundData cData) {
        this.cData = cData;
        int nRegions = cData.getRegionCount();
        this.shifts = new double[nRegions];
        this.scale = 1.0;
    }

}
