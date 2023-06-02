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

import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.search.MNode;
import org.nmrfx.chemistry.search.MTree;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author brucejohnson
 */
public class HoseCodeGenerator {

    private List<MNode> getShellNodes(List<MNode> nodes, int iShell, int start) {
        List<MNode> shellNodes = new ArrayList<>();
        for (int i = start; i < nodes.size(); i++) {
            MNode node = nodes.get(i);
            if (node.getShell() > iShell) {
                break;
            } else if (node.getShell() == iShell) {
                shellNodes.add(node);
            }
        }
        return shellNodes;
    }

    class NodeComparator implements Comparator<MNode> {

        @Override
        public int compare(MNode o1, MNode o2) {
            return o2.compareTo(o1);
        }
    }

    private void initNodeValue(Entity entity, MNode mNode) {
        MNode parent = mNode.getParent();
        Atom nodeAtom = mNode.getAtom();
        int value = 0;
        if (parent != null) {
            Atom parentAtom = parent.getAtom();
            IBond iBond = entity.getBond(parentAtom, nodeAtom);

            if (iBond != null) {
                Order order = iBond.getOrder();
                value += order.getOrderNum() * 5000;
            }
            int symNumber = 0;
            String sym = mNode.isRingClosure() ? "" : nodeAtom.getElementName();
            switch (sym) {
                case "C":
                    symNumber = 10;
                    break;
                case "O":
                    symNumber = 9;
                    break;
                case "N":
                    symNumber = 8;
                    break;
                case "S":
                    symNumber = 7;
                    break;
                case "P":
                    symNumber = 6;
                    break;
                case "Si":
                    symNumber = 5;
                    break;
                case "B":
                    symNumber = 4;
                    break;
                case "F":
                    symNumber = 3;
                    break;
                case "Cl":
                    symNumber = 2;
                    break;
                case "Br":
                    symNumber = 1;
                    break;
                default:
                    symNumber = 0;
            }
            value += (symNumber + 1) * 100;
        }
        mNode.setValue(value);
    }

    public Map<Integer, String> genHOSECodes(Entity entity, int nShells, int targetElement) {
        Set<Integer> targetSet = new HashSet<>();
        targetSet.add(targetElement);
        MTree mTree = new MTree();
        HashMap<Atom, Integer> hash = new HashMap<>();
        List<Atom> eAtomList = new ArrayList<>();
        int i = 0;

        for (Atom atom : entity.atoms) {
            // entity check ensures that only atoms in same residue are used
            // if entity is not polymer
            if (atom.getAtomicNumber() == 0) {
                continue;
            }
            if ((entity instanceof Polymer) || (atom.entity == entity)) {
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
            if (atom.getAtomicNumber() == 0) {
                continue;
            }

            for (Bond bond : atom.bonds) {
                if ((bond.begin.getAtomicNumber() == 1) || (bond.end.getAtomicNumber() == 1)) {
                    continue;
                }
                Integer iNodeBegin = hash.get(bond.begin);
                Integer iNodeEnd = hash.get(bond.end);

                if ((iNodeBegin != null) && (iNodeEnd != null)) {
                    if (bond.begin == atom) {
                        mTree.addEdge(iNodeBegin, iNodeEnd, false);
                    } else {
                        mTree.addEdge(iNodeEnd, iNodeBegin, false);
                    }
                }
            }

        }
        mTree.sortNodes();

        // get breadth first path from each atom
        Map<Integer, String> result = new HashMap<>();
        for (int j = 0, n = eAtomList.size(); j < n; j++) {
            Atom atomStart = (Atom) eAtomList.get(j);
            if (!targetSet.contains(atomStart.getAtomicNumber())) {
                continue;
            }
            mTree.broad_path(j);
            List<MNode> pathNodes = mTree.getPathNodes();
            for (MNode mNode : pathNodes) {
                initNodeValue(entity, mNode);
            }
            for (int iN = pathNodes.size() - 1; iN >= 0; iN--) {
                MNode mNode = pathNodes.get(iN);
                MNode parent = mNode.getParent();
                if (parent != null) {
                    parent.setValue(parent.getValue() + mNode.getValue());
                }
            }
            MNode lastNode = pathNodes.get(pathNodes.size() - 1);
            ArrayDeque<List<MNode>> sortedNodes = new ArrayDeque<>();
            int lastNodeIndex = 0;
            List<MNode> lastShellNodes = new ArrayList<>();
            for (int iShell = 0; iShell < nShells; iShell++) {
                List<MNode> shellNodes = getShellNodes(pathNodes, iShell, lastNodeIndex);
                lastNodeIndex += shellNodes.size();
                if (shellNodes.isEmpty()) {
                    break;
                }
                List<MNode> thisShell = new ArrayList<>();
                if (lastShellNodes.isEmpty()) {
                    thisShell.addAll(shellNodes);
                } else {
                    for (MNode nNode : lastShellNodes) {
                        int id = nNode.getID();
                        List<MNode> thisBranch = shellNodes.stream().filter(nn -> nn.getParent().getID() == id).sorted(new NodeComparator()).collect(Collectors.toList());
                        thisShell.addAll(thisBranch);
                    }
                }
                lastShellNodes.clear();
                lastShellNodes.addAll(thisShell);
                sortedNodes.add(thisShell);
            }

            String hoseCode = dumpCodes(entity, sortedNodes);
            atomStart.setProperty("hose", hoseCode);
            result.put(atomStart.getID(), hoseCode);
        }
        return result;
    }

    String formatCenterNode(MNode node) {
        StringBuilder result = new StringBuilder();
        Atom atom = node.getAtom();
        result.append(atom.getElementName());
        Integer hyb = (Integer) atom.getProperty("hyb");
        if (hyb == null) {
            hyb = 3;
        }
        if (atom.getFlag(Atom.AROMATIC)) {
            if (hyb == 3) {
                hyb = 6;
            } else {
                hyb = hyb + 4;
            }
        }
        result.append(hyb);
        return result.toString();
    }

    String getHoseCodeElement(String elementName) {
        switch (elementName) {
            case "Si":
                return "Q";
            case "Cl":
                return "X";
            case "Br":
                return "Y";
            default:
                break;
        }
        return elementName;
    }

    private String dumpCodes(Entity entity, ArrayDeque<List<MNode>> sortedNodes) {
        StringBuilder result = new StringBuilder();
        List<Atom> lastShell = new ArrayList<>();
        List<MNode> firstNodes = sortedNodes.pollFirst();
        if (firstNodes.isEmpty()) {
            return "";
        }
        MNode centerNode = firstNodes.get(0);
        result.append(formatCenterNode(centerNode));
        for (List<MNode> sNodes : sortedNodes) {
            result.append("/");
            List<Atom> thisShell = new ArrayList<>();
            int lastIndex = 0;

            for (MNode mNode : sNodes) {
                Atom pathAtom = mNode.getAtom();
                MNode parent = mNode.getParent();
                String parentName = "";
                Order order = null;
                int index = -1;
                Atom parentAtom = parent.getAtom();
                parentName = parentAtom.getFullName();
                IBond iBond = entity.getBond(parentAtom, pathAtom);
                if (iBond != null) {
                    order = iBond.getOrder();
                }
                index = lastShell.indexOf(parentAtom);

                if (pathAtom.aNum != 1) {
                    if (index != -1) {
                        while (lastIndex < index) {
                            result.append(",");
                            lastIndex++;
                        }
                    }
                    thisShell.add(pathAtom);
                    if (pathAtom.getFlag(Atom.AROMATIC) && parentAtom.getFlag(Atom.AROMATIC)) {
                        result.append("*");
                    } else if ((order != null) && (order == Order.TRIPLE)) {
                        result.append("%");
                    } else if ((order != null) && (order == Order.DOUBLE)) {
                        result.append("=");
                    }

                    if (mNode.isRingClosure()) {
                        result.append("&");
                    } else {
                        result.append(getHoseCodeElement(pathAtom.getElementName()));
                    }
                    lastIndex = index;
                }

            }
            while ((lastIndex + 1) < lastShell.size()) {
                result.append(",");
                lastIndex++;
            }

            lastShell.clear();
            lastShell.addAll(thisShell);
        }
        return result.toString();
    }

    public void scoreNode(MNode mNode) {
        String elemName = mNode.getAtom().getElementName();
    }
}
