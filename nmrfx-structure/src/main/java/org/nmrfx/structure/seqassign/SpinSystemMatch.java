/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.seqassign;

/**
 * @author brucejohnson
 */
public class SpinSystemMatch implements Comparable<SpinSystemMatch> {

    double score;
    final int n;
    final boolean[] matched;

    final SpinSystem spinSystemA;
    final SpinSystem spinSystemB;

    public int getN() {
        return n;
    }

    public void norm(double normValue) {
        score /= normValue;
    }

    public double getScore() {
        return score;
    }

    public SpinSystem getSpinSystemA() {
        return spinSystemA;
    }

    public SpinSystem getSpinSystemB() {
        return spinSystemB;
    }

    public SpinSystemMatch(SpinSystem spinSystemA, SpinSystem spinSystemB, double score, int nMatch, boolean[] matched) {
        this.spinSystemA = spinSystemA;
        this.spinSystemB = spinSystemB;
        this.score = score;
        this.n = nMatch;
        this.matched = matched;
    }

    public String toString() {
        String result = String.format("%s %s %.4f", spinSystemA.rootPeak.getName(), spinSystemB.rootPeak.getName(), score);
        return result;
    }

    @Override
    public int compareTo(SpinSystemMatch o) {
        return Double.compare(score, o.score);
    }

}
