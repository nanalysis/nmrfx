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

import org.nmrfx.structure.chemistry.Molecule;

/**
 * @author brucejohnson
 */
public abstract class Refinement {

    final Dihedral dihedrals;
    long startTime;
    double bestEnergy = Double.MAX_VALUE;
    int nEvaluations = 0;
    int updateAt = 10;
    int reportAt = 100;
    Molecule molecule;

    public Refinement(Dihedral dihedrals) {
        this.dihedrals = dihedrals;
    }

    public void report(int iteration, int nEvaluations, long time, int nContacts, double energy) {
        System.out.printf("MIN %6d %6d %8d %5d %9.2f\n", iteration, nEvaluations, time, nContacts, energy);
    }

    public void report(String type, int iteration, int nContacts, double energy) {
        System.out.printf("%-6s %6d %8d %9.2f\n", type, iteration, nContacts, energy);
    }

    double energy() {
        return dihedrals.energy();
    }

    EnergyDeriv eDeriv() {
        return dihedrals.eDeriv();
    }

    void putDihedrals() {
        dihedrals.putDihedrals();
    }

    void getDihedrals() {
        dihedrals.getDihedrals();
    }

    public void prepareAngles(final boolean usePseudo) {
        dihedrals.prepareAngles(usePseudo);

    }

    public void setBoundaries(final double sigma, boolean useDegrees) {
        dihedrals.setBoundaries(sigma, useDegrees, 2.0 * Math.PI);
    }

    public void setBoundaries(final double sigma, boolean useDegrees, double maxRange) {
        dihedrals.setBoundaries(sigma, useDegrees, maxRange);
    }

}
