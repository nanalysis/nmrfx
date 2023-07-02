/*
 * NMRFx Structure : A Program for Calculating Structures
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * PPMScore.java
 *
 * Created on January 13, 2006, 12:11 PM
 */
package org.nmrfx.structure.chemistry;

import org.nmrfx.structure.bionmr.AtomShiftData;

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

    public PPMScore(AtomShiftData[] atomShiftData) {
        scores = new double[atomShiftData.length];
        atomNames = new String[atomShiftData.length];
        for (int i = 0; i < atomNames.length; i++) {
            atomNames[i] = atomShiftData[i].getAName();
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
