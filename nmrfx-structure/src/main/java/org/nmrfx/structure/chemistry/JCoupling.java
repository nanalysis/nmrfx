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
import org.nmrfx.chemistry.Point3;
import org.nmrfx.chemistry.SpatialSet;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author brucejohnson
 */
public class JCoupling {

    final ArrayList<SpatialSet> spatialSets;
    Double dihedral = null;
    final int shell;

    public JCoupling(final SpatialSet[] spatialSets, final int shell) {
        this.spatialSets = new ArrayList<SpatialSet>(Arrays.asList(spatialSets));
        this.shell = shell;
    }

    public JCoupling(final ArrayList<SpatialSet> spatialSets, final int shell) {
        this.spatialSets = spatialSets;
        this.shell = shell;
    }

    public static JCoupling couplingFromAtoms(final ArrayList<Atom> atoms, final int shell) {
        ArrayList<SpatialSet> localSets = new ArrayList<SpatialSet>();

        for (Atom atom : atoms) {
            localSets.add(atom.spatialSet);
        }
        return new JCoupling(localSets, shell);
    }

    public static JCoupling couplingFromAtoms(final Atom[] atoms, final int shell) {
        ArrayList<SpatialSet> localSets = new ArrayList<SpatialSet>();

        for (Atom atom : atoms) {
            localSets.add(atom.spatialSet);
        }
        return new JCoupling(localSets, shell);
    }

    public static JCoupling couplingFromAtoms(final Atom[] atoms, final int nAtoms, final int shell) {
        ArrayList<SpatialSet> localSets = new ArrayList<SpatialSet>();
        int i = 0;
        for (Atom atom : atoms) {
            if (i >= nAtoms) {
                break;
            }
            localSets.add(atom.spatialSet);
            i++;
        }
        return new JCoupling(localSets, shell);
    }

    public void updateDihedral(final int structureNum) {
        dihedral = null;
        if (spatialSets.size() == 4) {
            Point3[] pts = new Point3[4];
            int i = 0;
            for (SpatialSet spSet : spatialSets) {
                pts[i++] = spSet.getPoint(structureNum);
                if (pts[i] == null) {
                    break;
                }
            }
            dihedral = (Atom.calcDihedral(pts[0], pts[1], pts[2], pts[3]));

        }
    }

    public Double getDihedral() {
        if (dihedral == null) {
            updateDihedral(0);
        }
        return dihedral;
    }

    public Atom getAtom(int index) {
        return spatialSets.get(index).atom;
    }

    public int getNAtoms() {
        return spatialSets.size();
    }

    public int getShell() {
        return shell;
    }

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (SpatialSet sSet : spatialSets) {
            sBuilder.append(sSet.atom.getFullName());
            sBuilder.append(' ');
        }
        sBuilder.append(shell);
        return sBuilder.toString();
    }
}
