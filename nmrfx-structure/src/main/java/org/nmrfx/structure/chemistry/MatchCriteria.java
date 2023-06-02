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
package org.nmrfx.structure.chemistry;

import org.nmrfx.peaks.Peak;


public class MatchCriteria {

    private final int dim;
    private final String relation;
    private final String[] resPats;
    private final String[] atomPats;
    private double ppm;
    private final double tol;
    private final double folding;
    private final int foldCount;

    public MatchCriteria(int dim, final double ppm, final double tol, final String[] atomPats, final String[] resPats, final String relation, final double folding, final int foldCount) {
        super();
        this.dim = dim;
        this.ppm = ppm;
        this.tol = tol;
        this.atomPats = atomPats;
        this.resPats = resPats;
        this.relation = relation;
        this.folding = folding;
        this.foldCount = foldCount;
    }

    public void setPPM(double value) {
        ppm = value;
    }

    public void setPPM(Peak peak) {
        ppm = peak.getPeakDim(dim).getChemShiftValue();
    }

    /**
     * @return the dim
     */
    public int getDim() {
        return dim;
    }

    /**
     * @return the relation
     */
    public String getRelation() {
        return relation;
    }

    public int getResPatCount() {
        return resPats.length;
    }

    /**
     * @return the resPats
     */
    public String getResPat(final int i) {
        return resPats[i];
    }

    public int getAtomPatCount() {
        return atomPats.length;
    }

    /**
     * @return the atomPats
     */
    public String getAtomPat(final int i) {
        return atomPats[i];
    }

    public String[] getAtomPats() {
        return atomPats.clone();
    }

    /**
     * @return the ppm
     */
    public double getPpm() {
        return ppm;
    }

    /**
     * @param ppm the ppm to set
     */
    public void setPpm(double ppm) {
        this.ppm = ppm;
    }

    /**
     * @return the tol
     */
    public double getTol() {
        return tol;
    }

    /**
     * @return the folding
     */
    public double getFolding() {
        return folding;
    }

    /**
     * @return the foldCount
     */
    public int getFoldCount() {
        return foldCount;
    }
}
