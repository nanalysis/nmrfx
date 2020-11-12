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
package org.nmrfx.structure.chemistry.constraints;

import org.nmrfx.chemistry.SpatialSet;

/**
 *
 * @author brucejohnson
 */
public class RDC implements Constraint {

    private static DistanceStat defaultStat = new DistanceStat();

    /**
     * @return the defaultStat
     */
    public static DistanceStat getDefaultStat() {
        return defaultStat;
    }
    private static double tolerance = 5.0;
    private int idNum = 0;
    private final SpatialSet sp1;
    private final SpatialSet sp2;
    private final double value;
    private final double err;
    Double length = null;
    private DistanceStat disStat = defaultStat;
    private int active = 1;

    public RDC(RDCConstraintSet set, final SpatialSet sp1, final SpatialSet sp2, final double value, final double err) {
        this.sp1 = sp1;
        this.sp2 = sp2;
        this.value = value;
        this.err = err;
        idNum = set.getSize();
        set.setDirty();
    }

    public int getID() {
        return getIdNum();
    }

    public DistanceStat getStat() {
        return getDisStat();
    }

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
        SpatialSet[] spSet = {sp1, sp2};
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
        double viol = value - rdc;
        return viol;
    }

    public String toSTARString() {
        StringBuilder result = new StringBuilder();
        char sep = ' ';
        result.append(RDCConstraintSet.ID++);
        result.append(sep);
        sp1.addToSTARString(result);
        result.append(sep);
        result.append(".");
        result.append(sep);
        sp2.addToSTARString(result);
        result.append(sep);
        result.append(".");
        result.append(sep);
        result.append(".");
        result.append(sep);
        result.append(getValue());
        result.append(sep);
        result.append(getErr());
        result.append(sep);

        String ssID = null;
        if (ssID == null) {
            result.append(".");
        } else {
            result.append(ssID);
        }
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
    public double getValue() {
        return value;
    }

    /**
     * @return the upper
     */
    public double getErr() {
        return err;
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
