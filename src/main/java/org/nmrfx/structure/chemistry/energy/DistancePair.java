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

public class DistancePair {

    final AtomDistancePair[] atomPairs;
    final double rLow;
    final double rUp;
    final boolean isBond;
    final double weight;
    final double targetValue;
    final double targetErr;

    public DistancePair(final Atom[] atoms1, final Atom[] atoms2, final double rLow, final double rUp, final boolean isBond,
            final double weight, final double targetValue, final double targetErr) {
        if (atoms1.length != atoms2.length) {
            throw new IllegalArgumentException("atom arrays are not of equal length");
        }
        atomPairs = new AtomDistancePair[atoms1.length];
        for (int i = 0; i < atoms1.length; i++) {
            AtomDistancePair atomPair = new AtomDistancePair(atoms1[i], atoms2[i]);
            atomPairs[i] = atomPair;
        }

        this.rLow = rLow;
        this.rUp = rUp;
        this.isBond = isBond;
        this.weight = weight;
        this.targetValue = targetValue;
        this.targetErr = targetErr;
    }

    public DistancePair(final Atom[] atoms1, final Atom[] atoms2, final double rLow, final double rUp, final boolean isBond) {

        this(atoms1, atoms2, rLow, rUp, isBond, 1.0, (rLow + rUp) / 2.0, rUp - rLow);

    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (AtomDistancePair aPair : atomPairs) {
            sBuilder.append("pair: ");
            sBuilder.append(aPair.toString());
            sBuilder.append(" ");
        }
        sBuilder.append(rLow);
        sBuilder.append(" ");
        sBuilder.append(rUp);
        sBuilder.append(" ");
        sBuilder.append(weight);
        sBuilder.append(" ");
        sBuilder.append(targetValue);
        sBuilder.append(" ");
        sBuilder.append(targetErr);
        return sBuilder.toString();
    }

    public AtomDistancePair[] getAtomPairs() {
        return atomPairs;
    }

    public double getLower() {
        return rLow;
    }

    public double getUpper() {
        return rUp;
    }

    public boolean getIsBond() {
        return isBond;
    }


    public double getWeight() {
        return weight;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public double getTargetError() {
        return targetErr;
    }

}
