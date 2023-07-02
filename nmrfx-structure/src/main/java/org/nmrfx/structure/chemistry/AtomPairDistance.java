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

import org.nmrfx.chemistry.Atom;

public class AtomPairDistance {

    final Atom atom1;
    final Atom atom2;
    final double distance;

    public AtomPairDistance(Atom atom1, Atom atom2, double distance) {
        this.atom1 = atom1;
        this.atom2 = atom2;
        this.distance = distance;
    }

    public double getDistance() {
        return distance;
    }

    public Atom getAtom1() {
        return atom1;
    }

    public Atom getAtom2() {
        return atom2;
    }

    @Override
    public String toString() {
        return atom1.getFullName() + " " + atom2.getFullName() + " " + distance;
    }
}
