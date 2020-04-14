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
    final String filterString1;
    final String filterString2;
    final String resName1;
    final String resName2;
    final double weight;
    final double targetValue;
    final double targetErr;
    
    public DistancePair(final Atom[] atoms1, final Atom[] atoms2, final double rLow, final double rUp, final boolean isBond, 
            final String filterString1, final String filterString2, final String resName1, final String resName2, 
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
        this.filterString1 = filterString1;
        this.filterString2 = filterString2;
        this.resName1 = resName1;
        this.resName2 = resName2;
        this.weight = weight;
        this.targetValue = targetValue;
        this.targetErr = targetErr;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (AtomDistancePair aPair : atomPairs) {
            sBuilder.append("pair: ");
            sBuilder.append(aPair.toString());
            sBuilder.append(" ");
        }
        sBuilder.append(filterString1);
        sBuilder.append(" ");
        sBuilder.append(filterString2);
        sBuilder.append(" ");
        sBuilder.append(resName1);
        sBuilder.append(" ");
        sBuilder.append(resName2);
        sBuilder.append(" ");
        sBuilder.append(weight);
        sBuilder.append(" ");
        sBuilder.append(targetValue);
        sBuilder.append(" ");
        sBuilder.append(targetErr);
        sBuilder.append(" ");
        sBuilder.append(rLow);
        sBuilder.append(" ");
        sBuilder.append(rUp);
        return sBuilder.toString();
    }
    
    public AtomDistancePair[] getAtomPairs() {
        return atomPairs;
    }
    
    public String getAtomFilter1() {
        return filterString1;
    }
    
    public String getAtomFilter2() {
        return filterString2;
    }
    
    public String getResName1() {
        return resName1;
    }
    
    public String getResName2() {
        return resName2;
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
    
    public double getLower() {
        return rLow;
    }
    
    public double getUpper() {
        return rUp;
    }
}
