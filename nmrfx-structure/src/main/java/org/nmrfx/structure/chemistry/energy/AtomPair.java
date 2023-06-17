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

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomEnergyProp;
import org.nmrfx.chemistry.EnergyPair;
import org.nmrfx.chemistry.SpatialSet;

/**
 * This class represents an atom pair between two atoms. The energy of the atom pair is determined using AtomEnergyProp
 */
public class AtomPair {

    /**
     * First Atom
     */
    final SpatialSet spSet1;

    /**
     * Second Atom
     */
    final SpatialSet spSet2;

    /**
     * Branch unit1 for recurrent derivatives
     */
    final int unit1;

    /**
     * Branch unit2 for recurrent derivatives
     */
    final int unit2;

    /**
     * energy radius
     */
    final private double r0;

    /**
     * energy radius squared
     */
    final private double r02;

    /**
     * weight for contact energy
     */
    double weight = 1.0;

    /**
     * array for energy and derivative result with zero values
     */
    static final double[] zeroEnergy = {0.0, 0.0};

    /**
     * Energy Pair between both atoms
     */
    final EnergyPair ePair;

    /**
     * Simple Constructor
     *
     * @param atom1
     * @param atom2
     */
    public AtomPair(final Atom atom1, final Atom atom2, double hardSphere, boolean includeH, double shrinkValue, double shrinkHValue, double weight) {
        this.spSet1 = atom1.getSpatialSet();
        this.spSet2 = atom2.getSpatialSet();
        AtomEnergyProp prop1 = (AtomEnergyProp) atom1.getAtomEnergyProp();
        AtomEnergyProp prop2 = (AtomEnergyProp) atom2.getAtomEnergyProp();

        if ((prop2 != null) && (prop1 != null)) {
            // determines interaction between spSet1 and spSet2
            //true means that we are ignoring hydrogen atoms initially when minimizing energy, therefore we have to add .15 A to the rH value
            ePair = AtomEnergyProp.getInteraction(atom1, atom2, hardSphere, !includeH, shrinkValue, shrinkHValue);
            r0 = ePair.rh;
            r02 = r0 * r0;
        } else {
            r0 = 0.0;
            r02 = 0.0;
            ePair = null;
        }
        this.weight = weight;
        if (atom1.rotGroup != null) {
            unit1 = atom1.rotGroup.rotUnit;
        } else {
            unit1 = -1;
        }
        if (atom2.rotGroup != null) {
            unit2 = atom2.rotGroup.rotUnit;
        } else {
            unit2 = -1;
        }

    }

    public void getEnergy(boolean calcDeriv, double[] eDeriv) {
        double r2 = spSet1.getPoint().distanceSq(spSet2.getPoint());
        if (r2 > r02) {
            eDeriv[0] = 0.0;
            eDeriv[1] = 0.0;
        } else {
            double r = Math.sqrt(r2);
            double dif = r0 - r;
            eDeriv[0] = weight * dif * dif;
            if (calcDeriv) {
                //  what is needed is actually the derivitive/r, therefore
                // we divide by r
                eDeriv[1] = -2.0 * weight * dif / r;
            }
        }
    }

}
