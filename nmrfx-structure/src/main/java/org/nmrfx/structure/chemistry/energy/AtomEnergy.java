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

/**
 * This class represents the energy of an atom
 */
public class AtomEnergy {

    /**
     * energy
     */
    private final double energy;
    /**
     * deriv - derivative of Energy with respect to some parameter
     */
    private final double deriv;
    /**
     * Instance of Atom Energy with 0 Energy
     */
    public static final AtomEnergy ZERO = new AtomEnergy(0, 0);

    /**
     * Simple Constructor
     *
     * @param energy
     * @param deriv
     */
    public AtomEnergy(final double energy, final double deriv) {
        this.energy = energy;
        this.deriv = deriv;
    }

    /**
     * Simple Constructor
     *
     * @param energy
     */
    public AtomEnergy(final double energy) {
        this.energy = energy;
        this.deriv = 0.0;
    }

    /**
     * @return energy of atom
     */
    public double getEnergy() {
        return energy;
    }

    /**
     * @return deriv of atom
     */
    public double getDeriv() {
        return deriv;
    }
}
