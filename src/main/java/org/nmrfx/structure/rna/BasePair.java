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
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.HydrogenBond;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author bajlabuser
 */
public class BasePair {
    public Residue res1;
    public Residue res2;
    
    public BasePair(Residue res1, Residue res2){
        this.res1 = res1;
        this.res2 = res2;
    }

    public static int getBasePairType(Residue residue1, Residue residue2) {
        int bpCount;
        boolean valid = false;
        List<AllBasePairs> basePairs = new ArrayList<>();
        if (!residue1.name.matches("[GCAU]") || !residue2.name.matches("[GCAU]")) {
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
            for (String atomPair : bp.atomPairs) {
                String[] atomPairs = atomPair.split(":");
                String[] atoms0 = atomPairs[0].split("/");
                String[] atoms1 = atomPairs[1].split("/");
                for (String atom1Str : atoms0) {
                    for (String atom2Str : atoms1) {
                        Atom atom1 = residue1.getAtom(atom1Str);
                        Atom atom2 = residue2.getAtom(atom2Str);
                        if (atom1 != null && atom2 != null) {
                            if (atom1Str.contains("H")) {
                                valid = HydrogenBond.validateRNA(atom1.getSpatialSet(), atom2.getSpatialSet(), 0);
                            } else if (atom2Str.contains("H")) {
                                valid = HydrogenBond.validateRNA(atom2.getSpatialSet(), atom1.getSpatialSet(), 0);
                            }
                            if (valid) {
                                bpCount++;
                            }
                        }
                    }
                }
            }
            if (bpCount == bp.atomPairs.length) {
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
