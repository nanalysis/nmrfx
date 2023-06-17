package org.nmrfx.structure.seqassign;

import java.util.List;

/**
 * @author brucejohnson
 */
public class PPMScore {

    boolean ok = true;
    String[] atomNames;
    double[] scores;
    double totalScore = 0.0;

    public PPMScore(String[] atomNames) {
        scores = new double[atomNames.length];
        this.atomNames = atomNames.clone();
    }

    public PPMScore(List<AtomShiftValue> atomShiftValues) {
        scores = new double[atomShiftValues.size()];
        atomNames = new String[atomShiftValues.size()];
        for (int i = 0; i < atomNames.length; i++) {
            atomNames[i] = atomShiftValues.get(i).getAName();
        }
    }

    public boolean ok() {
        return ok;
    }

    public void setOK(boolean value) {
        this.ok = value;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setScore(int i, double value) {
        scores[i] = value;
    }

    public String worst() {
        int iMax = 0;
        double max = 0.0;

        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > max) {
                max = scores[i];
                iMax = i;
            }
        }

        return atomNames[iMax];
    }

}
