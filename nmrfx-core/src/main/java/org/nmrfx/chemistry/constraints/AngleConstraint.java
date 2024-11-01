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

import org.nmrfx.chemistry.*;

import java.util.List;

/**
 * This class determines if the angle boundary is valid - angle boundary for
 * each bond. The angle must be between -180 degrees and 360 degrees
 */
public class AngleConstraint implements Constraint {
    private static final DistanceStat DEFAULT_STAT = new DistanceStat();

    protected int idNum = 0;

    /**
     * Upper Angle Bound
     */
    final double upper;
    /**
     * Lower Angle Bound
     */
    final double lower;

    private Atom[] atoms = null;
    /**
     * Scale
     */
    final double scale;
    /**
     * weight
     */
    final Double weight;
    /**
     * target
     */
    final Double target;
    /**
     * target error
     */
    final Double targetErr;
    /**
     * name
     */
    final String name;
    /**
     * Index to list of angles
     */
    private int active = 1;
    private int index = -1;
    private DistanceStat disStat = DEFAULT_STAT;
    final static double toRad = Math.PI / 180.0;

    public AngleConstraint(Atom[] atoms, double lower, double upper, final double scale,
                           Double weight, Double target, Double targetErr, String name) {
        if (atoms.length != 4) {
            throw new IllegalArgumentException("Must specify 4 atoms in AngleBoundary constructor");
        }
        int i = 0;
        for (Atom atom : atoms) {
            if (atom == null) {
                throw new IllegalArgumentException("null atom " + i + " in Angle constraint");
            }
            i++;
        }
        if ((atoms[2].parent != atoms[1]) && (atoms[1].parent != atoms[2])) {
            throw new IllegalArgumentException("Second atom must be parent of first atom, or vice versa " +
                    atoms[0] + " " + atoms[1] + " " + atoms[2] + " " + atoms[3] + " parent1 " +
                    atoms[1].parent + " parent2 " + atoms[2].parent);
        }
        this.lower = Util.reduceAngle(lower * toRad);
        this.upper = Util.reduceAngle(upper * toRad);
        this.scale = scale;
        this.weight = weight;
        this.target = target;
        this.targetErr = targetErr;
        this.name = name;
        this.atoms = new Atom[atoms.length];
        System.arraycopy(atoms, 0, this.atoms, 0, atoms.length);
    }

    public AngleConstraint(Atom[] atoms, double lower, double upper, final double scale) throws InvalidMoleculeException {
        this(atoms, lower, upper, scale, 1.0, (lower + upper) / 2.0, upper - lower, "");
    }

    public AngleConstraint(Atom[] atoms, double lower, double upper, String name) throws InvalidMoleculeException {
        this(atoms, lower, upper, 1.0, 1.0, (lower + upper) / 2.0, upper - lower, name);
    }

    public AngleConstraint(List<Atom> atoms, double lower, double upper, final double scale,
                           Double weight, Double target, Double targetErr, String name) throws InvalidMoleculeException {
        for (Atom atom : atoms) {
            if (atom == null) {
                throw new InvalidMoleculeException("null atom");
            }
        }
        if (atoms.size() != 4) {
            throw new IllegalArgumentException("Must specify 4 atoms in AngleBoundary constructor");
        }
        if ((atoms.get(2).parent != atoms.get(1)) && (atoms.get(1).parent != atoms.get(2))) {
            throw new IllegalArgumentException("Second atom must be parent of first atom, or vice versa " + atoms.get(1).getFullName() + " " + atoms.get(2).getFullName());
        }
        /*Changed from Original*/
        if (((lower < -180.0) && (upper < 0.0)) || (upper > 360.0) || (upper < lower)) {
            throw new IllegalArgumentException("Invalid angle bounds: " + lower + " " + upper);
        }
        if ((lower > 180) && (upper > 180)) {
            lower = lower - 360.0;
            upper = upper - 360.0;
        }

        this.lower = Util.reduceAngle(lower * toRad);
        this.upper = Util.reduceAngle(upper * toRad);
        this.scale = scale;
        this.weight = weight;
        this.target = target;
        this.targetErr = targetErr;
        this.name = name;
        this.atoms = new Atom[atoms.size()];
        atoms.toArray(this.atoms);
    }

    public AngleConstraint(List<Atom> atoms, double lower, double upper, final double scale) throws InvalidMoleculeException {
        this(atoms, lower, upper, scale, 1.0, (lower + upper) / 2.0, upper - lower, "");
    }

    @Override
    public String toString() {
        return String.format("%s %s %s %s %6.1f %6.1f", atoms[0].getFullName(),
                atoms[1].getFullName(), atoms[2].getFullName(),
                atoms[3].getFullName(), Math.toDegrees(lower), Math.toDegrees(upper));

    }

    public static boolean allowRotation(List<String> atomNames) {

        int arrayLength = atomNames.size();
        if (arrayLength != 4) {
            throw new IllegalArgumentException("Error adding dihedral boundary, must provide four atoms");
        }
        Atom[] atoms = new Atom[4];
        for (int i = 0; i < arrayLength; i++) {
            atoms[i] = MoleculeBase.getAtomByName(atomNames.get(i));
        }

        return !((atoms[2].parent != atoms[1]) && (atoms[1].parent != atoms[2]));
    }

    public void setIndex(final int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public Atom getAtom() {
        return atoms[atoms.length - 1];
    }

    public Atom[] getAtoms() {
        return atoms;
    }

    public Atom getRefAtom() {
        Atom refAtom = atoms[2].parent == atoms[1] ? atoms[2] : atoms[1];
        return refAtom;
    }

    public String getAtomNames() {
        return String.format("%10s %10s %10s %10s",
                atoms[0].getFullName(),
                atoms[1].getFullName(),
                atoms[2].getFullName(),
                atoms[3].getFullName());
    }

    /**
     * @param i int. The index of the spatial set in the list of atoms.
     * @return the spSet
     */
    public SpatialSet getSpSet(int i) {
        return atoms[i].spatialSet;
    }

    public double getWeight() {
        return weight;
    }

    public double getTargetValue() {
        return target;
    }

    public double getTargetError() {
        return targetErr;
    }

    public double getLower() {
        return lower;
    }

    public double getUpper() {
        return upper;
    }

    public double getScale() {
        return scale;
    }

    public String getName() {
        return name;
    }

    public void setActive(int state) {
        active = state;
    }

    public int getActive() {
        return active;
    }

    public double getViol(double angle) {
        double viol;
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

    @Override
    public int getID() {
        return idNum;
    }

    @Override
    public boolean isUserActive() {
        return getActive() > 0;
    }

    @Override
    public DistanceStat getStat() {
        return getDisStat();
    }

    @Override
    public double getValue() {
        return getDisStat().getMean();
    }

    @Override
    public String toSTARString() {
        StringBuilder result = new StringBuilder();
        char sep = ' ';
//     _Torsion_angle_constraint.ID
        result.append(AngleConstraintSet.id++);
        result.append(sep);
        //      _Torsion_angle_constraint.Torsion_angle_name
        result.append(getName());
        result.append(sep);
        getSpSet(0).addToSTARString(result);
        result.append(sep);
        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);
        getSpSet(1).addToSTARString(result);
        result.append(sep);
        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);
        getSpSet(2).addToSTARString(result);
        result.append(sep);
        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);
        getSpSet(3).addToSTARString(result);
        result.append(sep);
        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);

//      _Torsion_angle_constraint.Angle_lower_bound_val
        result.append(String.format("%.0f", Math.toDegrees(getLower())));
        result.append(sep);
//      _Torsion_angle_constraint.Angle_upper_bound_val
        result.append(String.format("%.0f", Math.toDegrees(getUpper())));
        result.append(sep);
        result.append(".");  // ssID
        result.append(sep);
        result.append("1");
        return result.toString();
    }
}
