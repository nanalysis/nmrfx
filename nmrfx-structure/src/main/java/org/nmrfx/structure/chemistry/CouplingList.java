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

import org.nmrfx.chemistry.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class CouplingList {

    ArrayList<JCoupling> jCouplings = new ArrayList<>();
    ArrayList<JCoupling> tocsyLinks = new ArrayList<>();
    ArrayList<JCoupling> hmbcLinks = new ArrayList<>();

    public void generateCouplings(Entity entity, int nShells, int minShells,
                                  int tocsyShells, int hmbcShells) {
        nShells = Math.max(nShells, Math.max(tocsyShells, hmbcShells));
        jCouplings.clear();
        tocsyLinks.clear();
        hmbcLinks.clear();
        Molecule.getCouplings(entity, jCouplings, tocsyLinks, hmbcLinks,
                nShells, minShells, tocsyShells, hmbcShells);
    }

    public List<JCoupling> getJCouplings() {
        return jCouplings;
    }

    public List<JCoupling> getTocsyLinks() {
        return tocsyLinks;
    }

    public List<JCoupling> getHMBCLinks() {
        return hmbcLinks;
    }
}
