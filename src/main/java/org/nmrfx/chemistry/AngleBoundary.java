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
package org.nmrfx.chemistry;

import java.util.List;

/**
 * This class determines if the angle boundary is valid - angle boundary for
 * each bond. The angle must be between -180 degrees and 360 degrees
 */
public class AngleBoundary {

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
    private int index = -1;
    final static double toRad = Math.PI / 180.0;

    public AngleBoundary(Atom[] atoms, double lower, double upper, final double scale,
                         Double weight, Double target, Double targetErr, String name) throws InvalidMoleculeException {
        if (atoms.length != 4) {
            throw new IllegalArgumentException("Must specify 4 atoms in AngleBoundary constructor");
        }
        if ((atoms[2].parent != atoms[1]) && (atoms[1].parent != atoms[2])) {
            throw new IllegalArgumentException("Second atom must be parent of first atom, or vice versa");
        }
        /*Changed from Original*/
//        if (((lower < -180.0) && (upper < 0.0)) || (upper > 360.0) || (upper < lower)) {
//            throw new IllegalArgumentException("Invalid angle bounds: " + lower + " " + upper);
//        }
//        if ((lower > 180) && (upper > 180)) {
//            lower = lower - 360.0;
//            upper = upper - 360.0;
//        }

        /*
                if (lower < 0.0) {
            lower += 360.0;
        }
        if (upper < 0.0) {
            upper += 360.0;
        }
        if (lower > 360.0) {
            lower -= 360.0;
        }
        if (upper > 360.0) {
            upper -= 360.0;
        }

         */
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
    
    public AngleBoundary(Atom[] atoms, double lower, double upper, final double scale) throws InvalidMoleculeException {
        this(atoms, lower, upper, scale, 1.0, (lower + upper) / 2.0, upper - lower, "");
    }

    public AngleBoundary(List<Atom> atoms, double lower, double upper, final double scale,
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
            throw new IllegalArgumentException("Second atom must be parent of first atom, or vice versa");
        }
        /*Changed from Original*/
        if (((lower < -180.0) && (upper < 0.0)) || (upper > 360.0) || (upper < lower)) {
            throw new IllegalArgumentException("Invalid angle bounds: " + lower + " " + upper);
        }
        if ((lower > 180) && (upper > 180)) {
            lower = lower - 360.0;
            upper = upper - 360.0;
        }

        this.lower = lower * toRad;
        this.upper = upper * toRad;
        this.scale = scale;
        this.weight = weight;
        this.target = target;
        this.targetErr = targetErr;
        this.name = name;
        this.atoms = new Atom[atoms.size()];
        atoms.toArray(this.atoms);
    }
    
    public AngleBoundary(List<Atom> atoms, double lower, double upper, final double scale) throws InvalidMoleculeException {
        this(atoms, lower, upper, scale, 1.0, (lower + upper) / 2.0, upper - lower, "");
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
}
