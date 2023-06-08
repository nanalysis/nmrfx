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
package org.nmrfx.chemistry;


import java.util.ArrayList;
import java.util.List;

/**
 * @author bajlabuser
 */
public abstract class SecondaryStructure {
    public static int globalCounter = 0;
    public int globalIndex = 0;
    public int localIndex = 0;
    public int size;
    public List<Residue> secResidues = new ArrayList<>();

    public abstract String getName();

    @Override
    public String toString() {
        return getName() + getGlobalInd() + ":" + getLocalInd();
    }

    public List<Residue> getResidues() {
        return secResidues;
    }

    public Residue firstResidue() {
        return secResidues.get(0);
    }

    public Residue lastResidue() {
        return secResidues.get(secResidues.size() - 1);
    }

    public int getGlobalInd() {
        return globalIndex;
    }

    public int getLocalInd() {
        return localIndex;
    }

    public void getInvolvedRes() {
        secResidues.forEach((residue) -> {
            System.out.print(residue.getPolymer().getName() + ":" + residue.getName() + residue.getNumber());
        });
    }
}
