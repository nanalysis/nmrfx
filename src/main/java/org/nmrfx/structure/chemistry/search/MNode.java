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

package org.nmrfx.structure.chemistry.search;

import org.nmrfx.structure.chemistry.Atom;
import java.util.*;

public class MNode implements Comparable, Comparator {

    ArrayList nodes = null;
    int id = -1;
    int shell = -1;
    int value = 0;
    MNode parent = null;
    int pathPos = -1;
    Atom atom = null;
    int indexInShell = -1;
    int maxShell = 0;
    MNode lastRotatable = null;

    public MNode() {
        nodes = new ArrayList();
        this.id = -1;
    }

    public MNode(int id) {
        nodes = new ArrayList();
        this.id = id;
    }

    public void addNode(MNode iNode) {
        nodes.add(iNode);
    }

    public void setValue(int i) {
        value = i;
    }

    public int getID() {
        return (id);
    }

    public Atom getLastRotatableAtom() {
        Atom lAtom = null;
        if (lastRotatable != null) {
            lAtom = lastRotatable.atom;
        }
        return lAtom;
    }
    
    public int getShell() {
        return (shell);
    }
    
    public int getMaxShell() {
        return maxShell;
    }

    public MNode getParent() {
        return parent;
    }

    public int getValue() {
        return (value);
    }

    public int getIndexInShell() {
        return indexInShell;
    }

    public void setIndexInShell(int index) {
        indexInShell = index;
    }

    public void setAtom(Atom atom) {
        this.atom = atom;
    }

    public Atom getAtom() {
        return atom;
    }

    public int compareTo(Object y) {
        int result = 1;

        if (y instanceof MNode) {
            MNode yNode = (MNode) y;

            if (value == yNode.value) {
                result = 0;
            } else if (value < yNode.value) {
                result = -1;
            }
        }

        return result;
    }

    public void sortNodes() {
        Collections.sort(nodes);
    }

    @Override
    public int compare(Object o1, Object o2) {
        MNode mNode1 = (MNode) o1;
        return mNode1.compareTo(o2);
    }
}
