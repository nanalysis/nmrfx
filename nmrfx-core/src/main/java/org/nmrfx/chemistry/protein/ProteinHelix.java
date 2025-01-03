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
package org.nmrfx.chemistry.protein;

import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SecondaryStructure;

/**
 * @author bajlabuser, mbeckwith
 */
public class ProteinHelix extends SecondaryStructure {
    private static final String NAME = "Helix";
    private static int localCounter = 0;

    public ProteinHelix(Residue firstResidue, Residue lastResidue) {
        localIndex = localCounter++;
        globalIndex = globalCounter++;
        Residue residue = firstResidue;
        while (residue != null) {
            secResidues.add(residue);
            if (residue == lastResidue) {
                break;
            }
            residue = residue.next;
        }
    }

    @Override
    public void getInvolvedRes() {
        int i = 0;
        while (i < secResidues.size()) {
            Residue res1 = secResidues.get(i);
            Residue res2 = secResidues.get(i + 1);
            System.out.print(res1.getPolymer().getName() + ":" + res1.getName()
                    + res1.getResNum() + ":" + res2.getPolymer().getName()
                    + ":" + res2.getName() + res2.getResNum() + " ");
            i += 2;
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
