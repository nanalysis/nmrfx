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

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SpatialSet;
import org.nmrfx.chemistry.SpatialSetGroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DistanceConstraint implements Constraint {
    private static final DistanceStat DEFAULT_STAT = new DistanceStat();

    private final AtomDistancePair[] atomPairs;
    private boolean isBond;
    protected double lower;
    protected double upper;
    protected double weight;
    protected double target;
    protected double targetErr;
    public DistanceStat disStat = DEFAULT_STAT;
    DistanceStat disStatAvg = DEFAULT_STAT;

    public DistanceConstraint(final Atom[] atoms1, final Atom[] atoms2, final double rLow, final double rUp, final boolean isBond,
                              final double weight, final double targetValue, final double targetErr) {
        if (atoms1.length != atoms2.length) {
            throw new IllegalArgumentException("atom arrays are not of equal length");
        }
        atomPairs = new AtomDistancePair[atoms1.length];
        for (int i = 0; i < atoms1.length; i++) {
            AtomDistancePair atomPair = new AtomDistancePair(atoms1[i], atoms2[i]);
            atomPairs[i] = atomPair;
        }

        this.lower = rLow;
        this.upper = rUp;
        this.isBond = isBond;
        this.weight = weight;
        this.target = targetValue;
        this.targetErr = targetErr;
    }

    public DistanceConstraint(final Atom[] atoms1, final Atom[] atoms2, final double rLow, final double rUp, final boolean isBond) {

        this(atoms1, atoms2, rLow, rUp, isBond, 1.0, (rLow + rUp) / 2.0, rUp - rLow);

    }

    public DistanceConstraint(SpatialSet sp1, SpatialSet sp2) {
        atomPairs = new AtomDistancePair[1];
        int i = 0;
        Atom atom1 = sp1.getAtom();
        Atom atom2 = sp2.getAtom();
        AtomDistancePair atomPair = new AtomDistancePair(atom1, atom2);
        atomPairs[i++] = atomPair;
        this.lower = 1.8;
        this.upper = 5.0;
        this.isBond = false;
        this.weight = 1.0;
        this.target = (lower + upper) / 2.0;
        this.targetErr = (upper - lower) / 2.0;

    }

    public DistanceConstraint(SpatialSetGroup spg1, SpatialSetGroup spg2) {
        Set<SpatialSet> spSets1 = spg1.getSpSets();
        Set<SpatialSet> spSets2 = spg2.getSpSets();
        atomPairs = new AtomDistancePair[spSets1.size() * spSets2.size()];
        int i = 0;
        for (SpatialSet sp1 : spSets1) {
            for (SpatialSet sp2 : spSets2) {
                Atom atom1 = sp1.getAtom();
                Atom atom2 = sp2.getAtom();
                AtomDistancePair atomPair = new AtomDistancePair(atom1, atom2);
                atomPairs[i++] = atomPair;
            }
        }
        this.lower = 1.8;
        this.upper = 5.0;
        this.isBond = false;
        this.weight = 1.0;
        this.target = (lower + upper) / 2.0;
        this.targetErr = (upper - lower) / 2.0;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (AtomDistancePair aPair : atomPairs) {
            sBuilder.append("pair: ");
            sBuilder.append(aPair.toString());
            sBuilder.append(" ");
        }
        sBuilder.append(lower);
        sBuilder.append(" ");
        sBuilder.append(upper);
        sBuilder.append(" ");
        sBuilder.append(weight);
        sBuilder.append(" ");
        sBuilder.append(target);
        sBuilder.append(" ");
        sBuilder.append(targetErr);
        return sBuilder.toString();
    }

    public AtomDistancePair[] getAtomPairs() {
        return atomPairs;
    }

    public double getLower() {
        return lower;
    }

    public double getUpper() {
        return upper;
    }

    public boolean getIsBond() {
        return isBond;
    }

    public double getWeight() {
        return weight;
    }

    public double getTarget() {
        return target;
    }

    public double getTargetError() {
        return targetErr;
    }

    public Map<String, Set<Atom>> getUniqueAtoms(AtomDistancePair[] pairs, int atomNum) {
        Map<String, Set<Atom>> atomsMap = new HashMap<>();
        Set<Atom> atoms = new HashSet<>();
        for (AtomDistancePair pair : pairs) {
            Atom a = null;
            if (atomNum == 1) {
                a = pair.getAtoms1()[0];
            } else if (atomNum == 2) {
                a = pair.getAtoms2()[0];
            }
            if (a != null) {
                int polymerID = ((Residue) a.entity).polymer.entityID;
                int seqCode = ((Residue) a.entity).getIDNum();
                String key = polymerID + ":" + seqCode;
                if (!atomsMap.containsKey(key) && !atoms.isEmpty()) {
                    atoms.clear();
                }
                atoms.add(a);
                atomsMap.put(key, atoms);
            }
        }
        return atomsMap;
    }

    public boolean isBond() {
        return isBond;
    }
    public void isBond(boolean value) {
        isBond = value;
    }

    public double getTargetErr() {
        return targetErr;
    }

    @Override
    public int getID() {
        return 0;
    }

    @Override
    public boolean isUserActive() {
        return false;
    }

    @Override
    public DistanceStat getStat() {
        return disStat;
    }

    @Override
    public double getValue() {
        return 0;
    }

    @Override
    public String toSTARString() {
        return null;
    }
}
