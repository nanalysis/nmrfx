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

package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.structure.chemistry.Atom;
import java.util.HashMap;
import org.apache.commons.math3.util.FastMath;

public class AtomEnergyProp {

    final String name;
    //leonard-jones a parameter
    private final double a;
    //leonard-jones b parameter
    private final double b;
    //ideal distance
    private final double r;
    //hard sphere repulsive distance
    private final double rh;
    //energy at r
    private final double e;
    //electric charge
    private final double c;
    //atomic mass
    private final double mass;
    // hbond donor (1), acceptor (-1)
    private final int hbondMode;
    //scaling factor
    private static double rscale = 0.68;
    private static final HashMap<String, AtomEnergyProp> propMap = new HashMap<String, AtomEnergyProp>();
    private static double hbondDelta = 0.30;

    static {
        // AtomType, 
        propMap.put("C1", new AtomEnergyProp("C1", 75142.00, 1900.00, 3.90, 1.6, -0.180, 0.00, 13.02, 0));
        propMap.put("C2", new AtomEnergyProp("C2", 75142.00, 1900.00, 3.90, 1.6, -0.180, 0.00, 14.03, 0));
        propMap.put("C3", new AtomEnergyProp("C3", 75142.00, 1900.00, 3.90, 1.6, -0.180, 0.00, 15.03, 0));
        propMap.put("C'", new AtomEnergyProp("C", 12470.00, 355.00, 3.75, 1.5, -0.042, 0.46, 12.01, 0));
        propMap.put("C-", new AtomEnergyProp("C-", 12470.00, 355.00, 3.75, 1.5, -0.042, 0.49, 12.01, 0));
        propMap.put("A0", new AtomEnergyProp("A0", 17080.00, 540.00, 3.62, 1.6, -0.080, 0.00, 12.01, 0));
        propMap.put("A1", new AtomEnergyProp("A1", 33396.00, 845.00, 3.90, 1.6, -0.080, 0.00, 13.02, 0));
        propMap.put("CA", new AtomEnergyProp("CA", 17080.00, 540.00, 3.62, 1.6, -0.080, 0.39, 13.02, 0));
        propMap.put("CB", new AtomEnergyProp("CB", 33396.00, 845.00, 3.90, 1.6, -0.080, 0.39, 13.02, 0));
        propMap.put("N'", new AtomEnergyProp("N", 15037.00, 628.00, 3.30, 1.45, -0.162, -0.26, 14.01, -1));
        propMap.put("N+", new AtomEnergyProp("N+", 15037.00, 628.00, 3.30, 1.5, -0.162, -0.52, 14.01, -1));
        propMap.put("N*", new AtomEnergyProp("N*", 15037.00, 628.00, 3.30, 1.5, -0.162, 0.00, 14.01, 0));
        propMap.put("S", new AtomEnergyProp("S", 104857.60, 2457.60, 4.00, 1.8, -0.200, 0.00, 32.06, 0));
        propMap.put("P", new AtomEnergyProp("P", 104857.60, 2457.60, 4.00, 1.8, -0.200, 0.00, 32.06, 0));
        propMap.put("H", new AtomEnergyProp("H", 0.00, 0.00, 0.00, 1.0, 0.000, 0.00, 0.00, 0));
        propMap.put("M", new AtomEnergyProp("M", 0.00, 0.00, 0.00, 1.0, 0.000, 0.00, 0.00, 0));
        propMap.put("H'", new AtomEnergyProp("H'", 9.99, 1.26, 2.28, 0.95, -0.003, 0.26, 1.01, 1));
        propMap.put("H+", new AtomEnergyProp("H+", 9.99, 1.26, 2.28, 1.0, -0.003, 0.33, 1.01, 1));
        propMap.put("HO", new AtomEnergyProp("HO", 9.99, 1.26, 2.28, 1.0, -0.003, 0.30, 1.01, 1));
        propMap.put("O", new AtomEnergyProp("O", 45770.00, 1410.00, 3.65, 1.3, -0.200, -0.30, 16.00, -1));
        propMap.put("O'", new AtomEnergyProp("O'", 45770.00, 1410.00, 3.65, 1.3, -0.200, -0.46, 16.00, -1));
        propMap.put("O-", new AtomEnergyProp("O-", 45800.00, 1410.00, 3.65, 1.3, -0.200, -0.48, 16.00, -1));
        propMap.put("FE", new AtomEnergyProp("FE", 32.82, 1.94, 2.94, -0.001, 1.8, 2.00, 55.85, 0));
        propMap.put("ZN", new AtomEnergyProp("ZN", 32.82, 1.94, 2.94, -0.001, 1.8, 2.00, 65.37, 0));
        propMap.put("Pcg", new AtomEnergyProp("Pcg", 0.0, 0.0, 0.0, 1.55, 0.0, 0.0, 0.0, 0));
        propMap.put("Scg", new AtomEnergyProp("Scg", 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0));
        propMap.put("Acg", new AtomEnergyProp("Acg", 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0));
        propMap.put("Ccg", new AtomEnergyProp("Ccg", 0.0, 0.0, 0.0, 1.7, 0.0, 0.0, 0.0, 0));
    }

    public AtomEnergyProp(final String name, final double a, final double b, final double r, final double rh, final double e, final double c, final double mass, final int hbondMode) {
        this.name = name;
        this.a = FastMath.sqrt(a);
        this.b = FastMath.sqrt(b);
        this.r = r;
        this.rh = rh;
        this.e = e;
        this.c = c;
        this.mass = mass;
        this.hbondMode = hbondMode;
    }

    public void clear() {
        propMap.clear();
    }

    public static void add(final String atomType, final AtomEnergyProp prop) {
        propMap.put(atomType, prop);
    }

    public static AtomEnergyProp get(final String atomType) {
        return propMap.get(atomType);
    }

    public String getName() {
        return name;
    }

    /**
     * @return the a
     */
    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }

    public double getC() {
        return c;
    }

    public double getR() {
        return r;
    }

    public double getRh() {
        return rh;
    }

    public double getE() {
        return e;
    }

    public double getMass() {
        return mass;
    }
    
    public int getHBondMode() {
        return hbondMode;
    }

    /**
     * computes interaction between two molecules based on table values
     * <p>
     * This method calculates the interation between 2 molecules. It retrieves the ideal energy values from the table
     * for both atoms It then calculates the radius or distance between both atoms. The rh value is calculating by
     * simply adding both the radius. Hydrogen may be removed and substited by a certain number of Angstrom's indicated
     * by AtomEnergyProp
     *
     * @param AtomEnergy iProp Properties of atom 1
     * @param AtomEnergy jProp properties of atom 2
     * @param boolean hardSphere determines if you want to calculate rh w/out hydrogen
     * @param double hardSphere determines the value you want to add in substitution for hydrogen
     */
    public static EnergyPair getInteraction(final Atom atom1, final Atom atom2, double hardSphere,
            boolean usehardSphere, double shrinkValue, double shrinkHValue) {

        AtomEnergyProp iProp = (AtomEnergyProp) atom1.atomEnergyProp;
        AtomEnergyProp jProp = (AtomEnergyProp) atom2.atomEnergyProp;

        double a1 = iProp.getA() * jProp.getA();
        double b1 = iProp.getB() * jProp.getB();
        double c1 = 0.0;

        double r = FastMath.sqrt(iProp.getR() * jProp.getR());
        double ea = -FastMath.sqrt(iProp.getE() * jProp.getE());
        double r2 = (rscale * r) * (rscale * r);
        double rh1 = iProp.getRh();
        double rh2 = jProp.getRh();
        if ((atom1.getAtomicNumber() != 1) || (atom2.getAtomicNumber() != 1) || jProp.getName().equals("HO")) {
            Atom testAtom1;
            Atom testAtom2;
            if (atom1.rotUnit < atom2.rotUnit) {
                testAtom1 = atom1;
                testAtom2 = atom2;
            } else {
                testAtom2 = atom1;
                testAtom1 = atom2;
            }
            Atom rotGroup1 = testAtom1.rotGroup;
            Atom rotGroup2 = testAtom2.rotGroup;
            Atom parent2 = null;
            if (rotGroup2 != null) {
                parent2 = rotGroup2.parent;
            }
            // atoms in close groups are allowed to be a little closer to add a little more flexibility since we don't allow bond angles and lengths to change
            if (parent2 != null && (parent2.parent == testAtom1)) {
                rh1 -= 0.1;
                rh2 -= 0.1;
            } else if (rotGroup1 != null) {
                Atom parent1 = rotGroup1.parent;
                if (parent1 != null) {
                    if (parent1.parent == testAtom2) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (parent1 == testAtom2.parent) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (rotGroup1 == parent2) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (parent1 == testAtom2.parent) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (parent2 == testAtom1.parent) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (parent1 == parent2) {
                        //  rh1 -= 0.1;
                        //  rh2 -= 0.1;
                    }
                }
            }
        }

        int hbond = iProp.hbondMode * jProp.hbondMode;
        if (hbond < 0) {
            rh1 -= hbondDelta;
            rh2 -= hbondDelta;
        }
        if (atom1.getAtomicNumber() != 1) {
            rh1 -= shrinkValue;
        } else {
            rh1 -= shrinkHValue;
        }
        if (atom2.getAtomicNumber() != 1) {
            rh2 -= shrinkValue;
        } else {
            rh2 -= shrinkHValue;
        }

        if (usehardSphere) {
            if (atom1.hydrogens > 0) {
                rh1 += hardSphere;
            }
            if (atom2.hydrogens > 0) {
                rh2 += hardSphere;
            }
        }
        double rh = rh1 + rh2;

        EnergyPair ePair = new EnergyPair(a1, b1, c1, r, r2, rh, ea);
        return ePair;
    }

    public static boolean interact(final Atom atom1, final Atom atom2) {
        final boolean value;
        AtomEnergyProp prop1 = (AtomEnergyProp) atom1.atomEnergyProp;
        AtomEnergyProp prop2 = (AtomEnergyProp) atom2.atomEnergyProp;
// fixme  only appropriate for rh mode of repel function
        if ((prop2 != null) && (prop1 != null) && (prop1.rh > 1.0e-6) && (prop2.rh > 1.0e-6)) {
            value = true;
        } else {
            value = false;
        }
        return value;
    }
}
