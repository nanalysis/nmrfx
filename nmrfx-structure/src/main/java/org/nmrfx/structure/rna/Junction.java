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

import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SecondaryStructure;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bajlabuser
 */
public class Junction extends SecondaryStructure {

    public static int localCounter = 0;
    public static String name = "Junction";

    public Junction() {

    }
    public Junction(List<Residue> residues) {
        localIndex = localCounter++;
        globalIndex = globalCounter++;
        secResidues = residues;
    }
    public List<Integer> getLoopSizes() {
        List<Integer> sizes = new ArrayList<>();
        int last = 0;
        for (int i=1;i< secResidues.size();i++) {
            Residue res1 = secResidues.get(i-1);
            Residue res2 = secResidues.get(i);
            if (res2.getPrevious() != res1) {
                sizes.add(i - last);
                last = i;
            }
        }
        sizes.add(secResidues.size() - last);
        return sizes;
    }

    @Override
    public String getName() {
        return name;
    }
}
