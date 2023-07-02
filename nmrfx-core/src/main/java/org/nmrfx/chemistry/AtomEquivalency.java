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

/*
 * AtomEquivalency.java
 *
 * Created on February 7, 2005, 8:36 PM
 */
package org.nmrfx.chemistry;

import java.util.ArrayList;

/**
 * @author brucejohnson
 */
public class AtomEquivalency {

    private ArrayList<Atom> atoms = null;
    private int shell = 0;
    private int index = 0;

    /**
     * Creates a new instance of AtomEquivalency
     */
    public AtomEquivalency() {
    }

    /**
     * @return the atoms
     */
    public ArrayList<Atom> getAtoms() {
        return atoms;
    }

    /**
     * @param atoms the atoms to set
     */
    public void setAtoms(ArrayList<Atom> atoms) {
        this.atoms = atoms;
    }

    /**
     * @return the shell
     */
    public int getShell() {
        return shell;
    }

    /**
     * @param shell the shell to set
     */
    public void setShell(int shell) {
        this.shell = shell;
    }

    /**
     * @return the index
     */
    public int getIndex() {
        return index;
    }

    /**
     * @param index the index to set
     */
    public void setIndex(int index) {
        this.index = index;
    }

    public boolean shareParent() {
        boolean shareParent = true;
        Atom parent = atoms.get(0).getParent();
        for (int i = 1; i < atoms.size(); i++) {
            if (atoms.get(i).getParent() != parent) {
                shareParent = false;
                break;
            }
        }
        return shareParent;
    }
}
