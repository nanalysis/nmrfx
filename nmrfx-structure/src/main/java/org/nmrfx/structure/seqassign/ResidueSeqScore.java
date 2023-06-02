/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.seqassign;

import org.nmrfx.chemistry.Residue;

import java.util.Collection;

/**
 * @author brucejohnson
 */
public class ResidueSeqScore implements Comparable<ResidueSeqScore> {

    final Residue firstResidue;
    final int nResidues;
    double score;

    public Residue getFirstResidue() {
        return firstResidue;
    }

    public int getNResidues() {
        return nResidues;
    }

    public double getScore() {
        return score;
    }

    public ResidueSeqScore(Residue firstResidue, int nResidues, double score) {
        this.firstResidue = firstResidue;
        this.nResidues = nResidues;
        this.score = score;
    }

    public void norm(double norm) {
        score /= norm;
    }

    public static void norm(Collection<ResidueSeqScore> seqScores) {
        double sum = 0.0;
        for (ResidueSeqScore seqScore : seqScores) {
            sum += seqScore.score;

        }
        for (ResidueSeqScore seqScore : seqScores) {
            seqScore.norm(sum);

        }
    }

    @Override
    public int compareTo(ResidueSeqScore o) {
        return Double.compare(o.score, score);
    }

}
