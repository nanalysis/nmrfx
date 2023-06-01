/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.compounds;

/**
 * @author brucejohnson
 */
public class CompoundMatch {

    CompoundData cData;
    double[] shifts;
    boolean[] active;
    double scale;

    CompoundMatch(CompoundData cData) {
        this.cData = cData;
        int nRegions = cData.getRegionCount();
        this.shifts = new double[nRegions];
        this.active = new boolean[nRegions];
        this.scale = 1.0;
    }

    public CompoundData getData() {
        return cData;
    }

    public void setShift(int iRegion, double shift) {
        shifts[iRegion] = shift;
    }

    public double getShift(int iRegion) {
        return shifts[iRegion];
    }

    public double[] getShifts() {
        return shifts;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public void setActive(int iRegion, boolean state) {
        active[iRegion] = state;
    }

    public boolean getActive(int iRegion) {
        return active[iRegion];
    }

    public int getNActive() {
        int nActive = 0;
        for (boolean actVal : active) {
            if (actVal) {
                nActive++;
            }
        }
        return nActive;
    }
}
