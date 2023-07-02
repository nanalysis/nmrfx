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
package org.nmrfx.chemistry.search;

import org.nmrfx.chemistry.Atom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

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
    boolean ringClosure = false;

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        String atomName = atom != null ? atom.getShortName() : "noatom";
        int pid = parent != null ? parent.id : -1;
        sBuilder.append("id ").append(id).append(" pid ").append(pid).append(" sh ").append(shell).append(" pp ").append(pathPos).append(" atm ").append(atomName).append(" rng ").append(ringClosure).append(" v ").append(value);
        return sBuilder.toString();
    }

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

    public void sortNodesDescending() {
        Collections.sort(nodes, reversed());
    }

    @Override
    public int compare(Object o1, Object o2) {
        MNode mNode1 = (MNode) o1;
        return mNode1.compareTo(o2);
    }

    public boolean isRingClosure() {
        return ringClosure;
    }

    public int getParValue() {
        int parValue = 0;
        if (parent != null) {
            parValue = parent.value;
        }
        parValue = parValue + value;
        return parValue;
    }

    public int getParValue2() {
        int parValue = 0;
        if (parent != null) {
            parValue = parent.value;
        }
        return parValue;
    }

    public static int compareByParValue(MNode a, MNode b) {
        int value = Integer.compare(b.getParValue2(), a.getParValue2());
        if (value == 0) {
            value = Integer.compare(b.getValue(), a.getValue());
        }
        if (value == 0) {
            value = a.getAtom().getName().compareTo(b.getAtom().getName());
        }
        return value;
    }
}
