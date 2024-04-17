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
package org.nmrfx.structure.rna;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Point3;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.HydrogenBond;
import org.nmrfx.structure.rna.AllBasePairs.BPConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bajlabuser
 */
public class BasePair {

    public Residue res1;
    public Residue res2;
    int iRes1;
    int iRes2;
    int type;

    public BasePair(Residue res1, int iRes1, Residue res2, int iRes2, int type) {
        this.res1 = res1;
        this.iRes1 = iRes1;
        this.res2 = res2;
        this.iRes2 = iRes2;
        this.type = type;
    }

    public Residue getResA() {
        return res1;
    }

    public Residue getResB() {
        return res2;
    }

    public int getIResA() {
        return iRes1;
    }

    public int getIResB() {
        return iRes2;
    }

    public int getType() {
        return type;
    }

    public static int getBasePairType(Residue residue1, Residue residue2) {
        int bpCount;
        boolean valid = false;
        List<AllBasePairs> basePairs = new ArrayList<>();
        Atom atom1test = residue1.getAtom("C1'");
        Atom atom2test = residue2.getAtom("C1'");
        if ((atom1test != null) && (atom2test != null)) {
            Point3 pt1 = atom1test.getPoint();
            Point3 pt2 = atom2test.getPoint();
            if ((pt1 != null) && (pt2 != null)) {
                double dis = Atom.calcDistance(pt1, pt2);
                if (dis > 25.0) {
                    return 0;
                }
            } else {
                return 0;
            }
        } else {
            return 0;
        }

        if (!"GCAU".contains(residue1.name) || !"GCAU".contains(residue2.name)) {
            basePairs = AllBasePairs.getBasePairs();
        } else {
            for (int type = 0; type <= 12; type++) {
                AllBasePairs bp = AllBasePairs.getBasePair(type, residue1.name, residue2.name);
                if (bp != null) {
                    basePairs.add(bp);
                }
            }
        }
        for (AllBasePairs bp : basePairs) {
            bpCount = 0;
            for (BPConstraint bpConstraint : bp.bpConstraints) {
                String[][] atomNames = bpConstraint.atomNames;
                int nAtomPairs = atomNames.length;
                for (int i = 0; i < nAtomPairs; i++) {
                    String atom1Str = atomNames[i][0];
                    String atom2Str = atomNames[i][1];
                    Atom atom1 = residue1.getAtom(atom1Str);
                    Atom atom2 = residue2.getAtom(atom2Str);
                    if (atom1 != null && atom2 != null) {
                        if (atom1.getAtomicNumber() == 1) {
                            valid = HydrogenBond.validateRNA(atom1.getSpatialSet(), atom2.getSpatialSet(), 0);
                        } else if (atom2.getAtomicNumber() == 1) {
                            valid = HydrogenBond.validateRNA(atom2.getSpatialSet(), atom1.getSpatialSet(), 0);
                        }
                        if (valid) {
                            bpCount++;
                            break;
                        }
                    }
                }
            }
            if (bpCount == bp.bpConstraints.length) {
                return bp.type;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return res1.iRes + ":" + res2.iRes;
    }

    public static boolean isCanonical(Residue res1, Residue res2) {
        boolean canon = false;
        if (getBasePairType(res1, res2) == 1) {
            canon = true;
        }
        return canon;
    }
}
