/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

/**
 * @author brucejohnson
 */
public class ViolationStats {

    int mode;
    String aName1;
    String aName2;
    double dis;
    double rUp;
    double rLow;
    double energy;
    double constraintDis = 0.0;
    double dif;
    private final EnergyCoords outer;

    public ViolationStats(int mode, String aName1, String aName2, double dis, double rLow, double rUp, double energy, final EnergyCoords outer) {
        this.outer = outer;
        this.mode = mode;
        this.aName1 = aName1;
        this.aName2 = aName2;
        this.dis = dis;
        this.rLow = rLow;
        this.rUp = rUp;
        this.energy = energy;
        dif = 0.0;
        if (mode == 1) {
            constraintDis = rLow;
            if (dis < rLow) {
                dif = dis - rLow;
            }
        } else if (mode == 2) {
            constraintDis = rLow;
            if (dis < rLow) {
                dif = dis - rLow;
            }
        } else {
            if (dis < rLow) {
                constraintDis = rLow;
                dif = dis - rLow;
            } else if (dis > rUp) {
                constraintDis = rUp;
                dif = dis - rUp;
            }
        }
    }

    double getViol() {
        return dif;
    }

    public String toString() {
        String modeType = "Dis";
        if (mode == 1) {
            modeType = "Rep";
        } else if (mode == 2) {
            modeType = "cFF";
        } else if (mode == 3) {
            modeType = "STK";
        }
        String result = String.format("%s: %10s %10s %5.2f %5.2f %5.2f %7.3f\n", modeType, aName1, aName2, constraintDis, dis, dif, energy);
        return result;
    }

}
