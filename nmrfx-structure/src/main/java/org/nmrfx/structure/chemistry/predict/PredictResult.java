/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

/**
 * @author brucejohnson
 */
public class PredictResult {
    HOSEStat cStat;
    HOSEStat hStat;
    final int shell;

    public PredictResult(HOSEStat cStat, HOSEStat hStat, final int shell) {
        this.cStat = cStat;
        this.hStat = hStat;
        this.shell = shell;
    }

    public HOSEStat getStat(String type) {
        if (type.equals("13C")) {
            return cStat;
        } else if (type.equals("15N")) {
            return cStat;
        } else if (type.equals("1H")) {
            return hStat;
        } else {
            throw new IllegalArgumentException("No stat of type " + type);
        }
    }

    public int getShell() {
        return shell;
    }

}
