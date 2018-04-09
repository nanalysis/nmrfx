package org.nmrfx.structure.chemistry.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Bond;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.Order;
import org.nmrfx.structure.chemistry.Point3;
import org.nmrfx.structure.chemistry.search.MNode;
import org.nmrfx.structure.chemistry.search.MTree;

/**
 *
 * @author Bruce Johnson
 */
public class AngleTreeGenerator {

    static final double TO_RAD = 180.0 / Math.PI;

    class BondSort implements Comparable<BondSort> {

        final Bond bond;
        final MNode mNode;

        BondSort(Bond bond, MNode mNode) {
            this.bond = bond;
            this.mNode = mNode;
        }

        @Override
        public int compareTo(BondSort o) {
            return MNode.compareByParValue(mNode, o.mNode);
        }

    }

    public void scan(Entity entity, Atom startAtom, Atom endAtom) {
        MTree mTree = new MTree();
        Map<Atom, Integer> hash = new HashMap<>();
        List<Atom> eAtomList = new ArrayList<>();
        int i = 0;
        int startIndex = -1;
        Map<Atom, List<Bond>> bondMap = new HashMap<>();
        List<Atom> atoms = entity.getAtoms();
        for (Atom atom : atoms) {
            if (atom == startAtom) {
                startIndex = i;
            }
            hash.put(atom, i);
            eAtomList.add(atom);
            MNode mNode = mTree.addNode();
            mNode.setAtom(atom);
            bondMap.put(atom, new ArrayList<>());
            i++;
        }
        if (startIndex == -1) {
            throw new IllegalArgumentException("Didnt' find start atom\"" + startAtom.getShortName() + "\"");
        }

        for (Atom atom : atoms) {
            for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
                Bond bond = atom.bonds.get(iBond);
                Integer iNodeBegin = hash.get(bond.begin);
                Integer iNodeEnd = hash.get(bond.end);

                if ((iNodeBegin != null) && (iNodeEnd != null)) {
                    mTree.addEdge(iNodeBegin, iNodeEnd);
                }
            }
        }
        int[] path = mTree.broad_path(startIndex);
        ArrayList<MNode> pathNodes = mTree.getPathNodes();
        for (MNode mNode : pathNodes) {
            mNode.setValue(mNode.getAtom().getAtomicNumber());
            if (mNode.getAtom() == endAtom) {
                mNode.setValue(10000);
            }
        }
        for (int iN = pathNodes.size() - 1; iN >= 0; iN--) {
            MNode mNode = pathNodes.get(iN);
            MNode parent = mNode.getParent();
            if (parent != null) {
                parent.setValue(parent.getValue() + mNode.getValue());
            }
        }
        for (MNode mNode : pathNodes) {
            mNode.sortNodesDescending();
        }

        path = mTree.broad_path(startIndex);
        pathNodes = mTree.getPathNodes();

        MNode lastNode = null;
        boolean firstAtom = true;
        double lastAngle = 0.0;
        double dih;

        int lastNodeIndex = 0;
        int nShells = pathNodes.get(pathNodes.size() - 1).getShell() + 1;
        for (Atom atom : atoms) {
            atom.parent = null;
        }
        int iAtom = 0;
        startAtom.setTreeIndex(iAtom++);
        startAtom.rotActive = false;
        startAtom.irpIndex = 0;
        for (int iShell = 0; iShell < nShells; iShell++) {
            List<MNode> shellNodes = getShellNodes(pathNodes, iShell, lastNodeIndex);
            lastNodeIndex += shellNodes.size();
            if (shellNodes.isEmpty()) {
                break;
            }
            for (MNode mNode3 : shellNodes) {
                if (mNode3.isRingClosure()) {
                    continue;
                }
                String name3 = mNode3.getAtom().getShortName();
                Atom atom3 = mNode3.getAtom();
                atom3.setTreeIndex(iAtom++);
                Point3 pt3 = atom3.getPoint();
                MNode mNode2 = mNode3.getParent();
                if ((lastNode == null) || (mNode2 != lastNode)) {
                    lastNode = mNode2;
                    firstAtom = true;
                    lastAngle = 0.0;
                }

                if (mNode2 != null) {
                    final Atom atom2 = mNode2.getAtom();
                    atom3.parent = atom2;
                    Point3 pt2 = atom2.getPoint();
                    atom3.bondLength = (float) AtomMath.calcDistance(pt2, pt3);
                    Optional<Bond> oBond = atom2.getBond(atom3);

                    oBond.ifPresent(b -> {
                        bondMap.get(atom2).add(b);
                        bondMap.get(atom3).add(b);
                    });
                    MNode mNode1 = mNode2.getParent();
                    if (mNode1 != null) {
                        Atom atom1 = mNode1.getAtom();
                        Point3 pt1 = atom1.getPoint();
                        atom3.valanceAngle = (float) AtomMath.calcAngle(pt1, pt2, pt3);
                        MNode mNode0 = mNode1.getParent();
                        if (mNode0 != null) {
                            Atom atom0 = mNode0.getAtom();
                            Point3 pt0 = atom0.getPoint();
                            dih = AtomMath.calcDihedral(pt0, pt1, pt2, pt3);
                            if (dih < 0.0) {
                                dih = dih + 2.0 * Math.PI;
                            }
                            double newDih;
                            if (!firstAtom) {
                                newDih = dih - lastAngle;
                            } else {
                                newDih = dih;
                            }
                            lastAngle = dih;
                            if (newDih > Math.PI) {
                                newDih = newDih - 2.0 * Math.PI;
                            }
                            if (newDih < -Math.PI) {
                                newDih = newDih + 2.0 * Math.PI;
                            }
                            atom3.dihedralAngle = (float) newDih;
//                        System.out.println(lastAngle * TO_RAD + " " + dih * TO_RAD + " " + newDih * TO_RAD + " " + firstAtom);
                        }

                    }

                    // fixme write faster code to get bond (like atom2.getbond(atom3) so you search bonds for atom not whole entity
                    boolean rotatable = true;
                    if (oBond.isPresent() && (oBond.get().getOrder() != Order.SINGLE)) {
                        rotatable = false;
                    } else if (atom3.bonds.size() < 2) {
                        rotatable = false;
                    } else if (atom3.getAtomicNumber() == 1) {
                        rotatable = false;
// fixme atom prop P hyb wrong so we have these special cases
                    } else if ((atom3.getAtomicNumber() == 8) && (atom2.getAtomicNumber() == 15)) {
                        rotatable = true;
                    } else if ((atom3.getAtomicNumber() == 15) && (atom2.getAtomicNumber() == 8)) {
                        rotatable = true;
                    } else if (atom3.getFlag(Atom.RESONANT) && atom2.getFlag(Atom.RESONANT)) {
                        rotatable = false;
                    } else if (atom3.getFlag(Atom.RING) && atom2.getFlag(Atom.RING)) {
                        rotatable = false;
                    } else if (atom3.getFlag(Atom.AROMATIC) && atom2.getFlag(Atom.AROMATIC)) {
                        rotatable = false;
                    }
                    atom3.irpIndex = rotatable ? 1 : 0;

                    String parentName = atom2 == null ? "" : atom2.getShortName();
                    System.out.printf("%10s %10s %3d %7.3f %7.3f %7.3f %b %3d %3d %b\n",
                            parentName, atom3.getShortName(), atom3.getTreeIndex(), atom3.bondLength,
                            atom3.valanceAngle * TO_RAD, atom3.dihedralAngle * TO_RAD, atom3.getRotActive(), atom3.rotUnit, atom3.irpIndex, rotatable);
                    firstAtom = false;
                }
            }
        }
        for (Atom atom : atoms) {
            atom.bonds.clear();
        }

        for (Atom atom : bondMap.keySet()) {
            for (Bond bond : bondMap.get(atom)) {
                atom.bonds.add(bond);
            }
        }
        List<Atom> atomPathList = new ArrayList<>();
        for (MNode mNode : pathNodes) {
            if (!mNode.isRingClosure()) {
                atomPathList.add(mNode.getAtom());
            }
        }
        for (Atom atom : atoms) {
            String par = "-";
            if (atom.parent != null) {
                par = atom.parent.getShortName();
            }
            String par2 = "-";
            if (atom.getParent() != null) {
                par2 = atom.getParent().getShortName();
            }
//            System.out.println(par + " --> " + par2 + " --> " + atom.getShortName() + " " + atom.getTreeIndex());
        }

        entity.molecule.setAtomPath(atomPathList);
        entity.molecule.resetGenCoords();
        entity.molecule.setupRotGroups();

        entity.molecule.genCoords();
    }

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
        Collections.sort(shellNodes, MNode::compareByParValue);
        return shellNodes;
    }

}
