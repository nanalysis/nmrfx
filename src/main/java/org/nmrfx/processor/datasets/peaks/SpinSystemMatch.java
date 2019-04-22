/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

/**
 *
 * @author brucejohnson
 */
class SpinSystemMatch {

    final double score;
    final SpinSystem spinSystemA;
    final SpinSystem spinSystemB;

    SpinSystemMatch(SpinSystem spinSystemA, SpinSystem spinSystemB, double score) {
        this.spinSystemA = spinSystemA;
        this.spinSystemB = spinSystemB;
        this.score = score;
    }

    public String toString() {
        String result = String.format("%s %s %.4f", spinSystemA.rootPeak.getName(), spinSystemB.rootPeak.getName(), score);
        return result;
    }

}
