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
package org.nmrfx.chemistry.constraints;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.RDC;
import org.nmrfx.chemistry.SpatialSet;

/**
 * @author brucejohnson
 */
public class RDCConstraint extends RDC implements Constraint {

    private final static DistanceStat defaultStat = new DistanceStat();

    /**
     * @return the defaultStat
     */
    public static DistanceStat getDefaultStat() {
        return defaultStat;
    }

    private final static double tolerance = 5.0;
    private int idNum = 0;
    private DistanceStat disStat = defaultStat;
    private int active = 1;

    public RDCConstraint(RDCConstraintSet set, final Atom atom1, final Atom atom2, final double value, final double err) {
        super(atom1, atom2);
        setExpRDC(value);
        setError(err);
        idNum = set.getSize();
        set.setDirty();
    }

    @Override
    public int getID() {
        return getIdNum();
    }

    @Override
    public DistanceStat getStat() {
        return getDisStat();
    }

    @Override
    public boolean isUserActive() {
        return getActive() > 0;
    }

    public void setActive(int state) {
        active = state;
    }

    public int getActive() {
        return active;
    }

    /**
     * @return the spSets
     */
    public SpatialSet[] getSpSets() {
        SpatialSet[] spSet = {getAtom1().spatialSet, getAtom2().spatialSet};
        return spSet;
    }

    public String getViolChars(DistanceStat dStat) {
        for (int i = 0; i < AngleConstraintSet.violCharArray.length; i++) {
            if (dStat.getViolStructures().get(i)) {
                AngleConstraintSet.violCharArray[i] = (char) ((i % 10) + '0');
            } else {
                AngleConstraintSet.violCharArray[i] = '_';
            }
        }
        return new String(AngleConstraintSet.violCharArray);
    }

    public double getViol(double rdc) {
        double viol = getRDC() - rdc;
        return viol;
    }

    @Override
    public String toSTARString() {
        StringBuilder result = new StringBuilder();
        char sep = ' ';
        result.append(RDCConstraintSet.id++);
        result.append(sep);
        getAtom1().getSpatialSet().addToSTARString(result);
        result.append(sep);
        result.append(".");
        result.append(sep);
        getAtom2().getSpatialSet().addToSTARString(result);
        result.append(sep);
        result.append(".");
        result.append(sep);
        result.append(".");
        result.append(sep);
        result.append(getValue());
        result.append(sep);
        result.append(getErr());
        result.append(sep);
        result.append(".");  // ssID
        result.append(sep);
        result.append("1");
        return result.toString();
    }

    /**
     * @return the idNum
     */
    public int getIdNum() {
        return idNum;
    }

    /**
     * @return the lower
     */
    @Override
    public double getValue() {
        return getExpRDC();
    }

    /**
     * @return the upper
     */
    public double getErr() {
        return getErr();
    }

    public double getPredicted() {
        return getRDC();
    }

    public void setPredicted(double value) {
        setRDC(value);
    }

    /**
     * @return the disStat
     */
    public DistanceStat getDisStat() {
        return disStat;
    }

    /**
     * @param disStat the disStat to set
     */
    public void setDisStat(DistanceStat disStat) {
        this.disStat = disStat;
    }

}
