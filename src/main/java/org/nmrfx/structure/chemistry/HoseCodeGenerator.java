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

import org.nmrfx.structure.chemistry.search.MNode;
import org.nmrfx.structure.chemistry.search.MTree;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author brucejohnson
 */
public class HoseCodeGenerator {

    void genHOSECodes(Entity entity) {
        int targetElement = 6;
        MTree mTree = new MTree();
        HashMap<Atom, Integer> hash = new HashMap<>();
        ArrayList<Atom> eAtomList = new ArrayList<>();
        int i = 0;

        for (Atom atom : entity.atoms) {
            // entity check ensures that only atoms in same residue are used
            if (atom.entity == entity) {
                if (atom.isMethyl() && !atom.isFirstInMethyl()) {
                    continue;
                }
                hash.put(atom, i);
                eAtomList.add(atom);

                MNode mNode = mTree.addNode();
                mNode.setAtom(atom);
                i++;
            }

        }

        for (Atom atom : entity.atoms) {
            for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
                Bond bond = (Bond) atom.bonds.elementAt(iBond);
                Integer iNodeBegin = hash.get(bond.begin);
                Integer iNodeEnd = hash.get(bond.end);

                if ((iNodeBegin != null) && (iNodeEnd != null)) {
                    mTree.addEdge(iNodeBegin, iNodeEnd);
                }
            }

        }
        mTree.sortNodes();

        // get breadth first path from each atom
        for (int j = 0, n = eAtomList.size(); j < n; j++) {
            Atom atomStart = (Atom) eAtomList.get(j);
            if (atomStart.aNum != targetElement) {
                continue;
            }
            System.out.println("start " + j + " " + atomStart.getShortName());
            int[] path = mTree.broad_path(j);
            ArrayList<MNode> pathNodes = mTree.getPathNodes();
            int lastShell = 0;
            ArrayList<MNode> shellNodes = new ArrayList<>();
            ArrayList<MNode> sortedNodes = new ArrayList<>();

            MNode lastNode = pathNodes.get(pathNodes.size() - 1);
            for (MNode mNode : pathNodes) {
                int shell = mNode.getShell();
                if ((shell != lastShell) || (mNode == lastNode)) {
                    shellNodes.sort(mNode.reversed());
                    sortedNodes.addAll(shellNodes);
                    int indexInShell = 0;
                    for (MNode sNode : shellNodes) {
                        System.out.println(lastShell + " atom " + sNode.getAtom().getShortName() + " indexIn " + indexInShell + " value " + sNode.getValue());
                        sNode.setIndexInShell(indexInShell++);
                    }
                    shellNodes.clear();
                    lastShell = shell;
                }
                Atom pathAtom = mNode.getAtom();
                int value = 100 - pathAtom.getAtomicNumber();
                int parentShellIndex = 0;
                MNode parent = mNode.getParent();
                if (parent != null) {
                    parentShellIndex = parent.getIndexInShell();
                }
                value += (100 - parentShellIndex) * 10000;
                System.out.println(pathAtom.getShortName() + " parent SI " + parentShellIndex + " shell " + shell + " value " + value);
                mNode.setValue(value);
                shellNodes.add(mNode);
            }
            for (MNode mNode : sortedNodes) {
                Atom pathAtom = mNode.getAtom();
                MNode parent = mNode.getParent();
                String parentName = "";
                if (parent != null) {
                    Atom parentAtom = parent.getAtom();
                    parentName = parentAtom.getFullName();
                }
                if (pathAtom.aNum != 1) {
                    System.out.println("pshell " + mNode.getShell() + " " + pathAtom.getFullName() + " " + parentName);
                }
            }
        }
    }
}
