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

import org.nmrfx.chemistry.SpatialSet;

/**
 *
 * @author brucejohnson
 */
public class AngleConstraint implements Constraint {

    private static DistanceStat defaultStat = new DistanceStat();

    /**
     * @return the defaultStat
     */
    public static DistanceStat getDefaultStat() {
        return defaultStat;
    }
    private static double tolerance = 5.0;
    private AngleConstraintSet angleSet;
    private int idNum = 0;
    private final SpatialSet[] spSets;
    private double lower;
    private double upper;
    private String name;
    private DistanceStat disStat = defaultStat;
    private int active = 1;

    public AngleConstraint(AngleConstraintSet angleSet, final String name, final SpatialSet[] spSets, final double lower, final double upper) {
        this.spSets = spSets;
        this.lower = lower;
        this.upper = upper;
        this.name = name;
        this.angleSet = angleSet;
        this.idNum = angleSet.getSize();
        angleSet.add(this);
        angleSet.setDirty();
    }

    public SpatialSet getSpatialSet(int i) {
        return getSpSets()[i];
    }

    public int getID() {
        return getIdNum();
    }

    public DistanceStat getStat() {
        return getDisStat();
    }

    public static double getTolerance() {
        return tolerance;
    }

    public static void setTolerance(double value) {
        tolerance = value;
    }

    public double getValue() {
        return getDisStat().getMean();
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

    public double getViol(double angle) {
        double viol = 0.0;
        if (getUpper() > getLower()) {
            if ((angle >= getLower()) && (angle <= getUpper())) {
                viol = 0.0;
            } else if (angle > getUpper()) {
                viol = angle - getUpper();
            } else {
                viol = getLower() - angle;
            }
        } else if ((angle >= getLower()) || (angle <= getUpper())) {
            viol = 0.0;
        } else {
            double uviol = 0.0;
            double lviol = 0.0;
            if (angle > getUpper()) {
                uviol = angle - getUpper();
            }
            if (angle < getLower()) {
                lviol = getLower() - angle;
            }
            viol = uviol > lviol ? lviol : uviol;
        }
        return viol;
    }

    public String toSTARString() {
        StringBuilder result = new StringBuilder();
        char sep = ' ';
//     _Torsion_angle_constraint.ID
        result.append(AngleConstraintSet.ID++);
        result.append(sep);
        //      _Torsion_angle_constraint.Torsion_angle_name
        result.append(getName());
        result.append(sep);
        getSpSets()[0].addToSTARString(result);
        result.append(sep);
        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);
        getSpSets()[1].addToSTARString(result);
        result.append(sep);
        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);
        getSpSets()[2].addToSTARString(result);
        result.append(sep);
        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);
        getSpSets()[3].addToSTARString(result);
        result.append(sep);
        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);

//      _Torsion_angle_constraint.Angle_lower_bound_val
        result.append(getLower());
        result.append(sep);
//      _Torsion_angle_constraint.Angle_upper_bound_val
        result.append(getUpper());
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
     * @return the spSets
     */
    public SpatialSet[] getSpSets() {
        return spSets;
    }

    /**
     * @return the lower
     */
    public double getLower() {
        return lower;
    }

    /**
     * @return the upper
     */
    public double getUpper() {
        return upper;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
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
