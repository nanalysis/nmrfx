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
import org.nmrfx.structure.chemistry.Molecule;
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
    Map<Atom, Map> ringClosures;

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

    public boolean checkStartAtom(Atom startAtom) {
        return startAtom.bonds.size() == 1;
    }

    public void scan(Entity entity, Atom startAtom, Atom endAtom) {
        MTree mTree = new MTree();
        Map<Atom, Integer> hash = new HashMap<>();
        List<Atom> eAtomList = new ArrayList<>();
        int i = 0;
        int startIndex = -1;
        Map<Atom, List<Bond>> bondMap = new HashMap<>();
        List<Atom> atoms = entity.getAtoms();

        if (startAtom == null) {
            for (Atom atom : atoms) {
                if (checkStartAtom(atom)) {
                    startAtom = atom;
                    break;
                }
            }
        } else {
            if (!checkStartAtom(startAtom)) {
                throw new IllegalArgumentException("Start atom has more than 1 bond \"" + startAtom.getShortName() + "\"");
            }
        }
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
        if (endAtom == null) {
            pathNodes.get(pathNodes.size() - 1).setValue(10000);
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

                final Atom atom2;
                Point3 pt2 = null;
                Optional<Bond> oBond = Optional.empty();
                MNode mNode1 = null;
                if (mNode2 != null) {
                    atom2 = mNode2.getAtom();
                    atom3.parent = atom2;
                    pt2 = atom2.getPoint();
                    oBond = atom2.getBond(atom3);
                    mNode1 = mNode2.getParent();
                } else {
                    atom2 = null;
                    pt2 = new Point3(0.0, 0.0, 0.0);
                }
                atom3.bondLength = (float) AtomMath.calcDistance(pt2, pt3);

                oBond.ifPresent(b -> {
                    bondMap.get(atom2).add(b);
                    bondMap.get(atom3).add(b);
                });
                MNode mNode0 = null;
                Atom atom1 = null;
                Point3 pt1 = null;
                if (mNode1 != null) {
                    atom1 = mNode1.getAtom();
                    pt1 = atom1.getPoint();
                    mNode0 = mNode1.getParent();
                } else {
                    if (mNode2 == null) {
                        pt1 = new Point3(-1.0, 0.0, 0.0);
                    } else {
                        pt1 = new Point3(0.0, 0.0, 0.0);

                    }
                }
                atom3.valanceAngle = (float) AtomMath.calcAngle(pt1, pt2, pt3);
                Atom atom0 = null;
                Point3 pt0 = null;
                if (mNode0 != null) {
                    atom0 = mNode0.getAtom();
                    pt0 = atom0.getPoint();
                } else {
                    if (mNode1 != null) {
                        pt0 = new Point3(0.0, 0.0, 0.0);
                    } else {
                        if (mNode2 == null) {
                            pt0 = new Point3(-1.0, -1.0, 0.0);
                        } else {
                            pt0 = new Point3(-1.0, 0.0, 0.0);
                        }
                    }
                }
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

                // fixme write faster code to get bond (like atom2.getbond(atom3) so you search bonds for atom not whole entity
                boolean rotatable = true;
                if (oBond.isPresent() && (oBond.get().getOrder() != Order.SINGLE)) {
                    rotatable = false;
                } else if (atom2 == null) {
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
//                    System.out.printf("%10s %10s %3d %7.3f %7.3f %7.3f %b %3d %3d %b\n",
//                            parentName, atom3.getShortName(), atom3.getTreeIndex(), atom3.bondLength,
//                            atom3.valanceAngle * TO_RAD, atom3.dihedralAngle * TO_RAD, atom3.getRotActive(), atom3.rotUnit, atom3.irpIndex, rotatable);
                firstAtom = false;
            }
        }
        for (Atom atom : atoms) {
            atom.bonds.clear();
        }

        for (Atom atom : atoms) {
            switch (atom.getAtomicNumber()) {
                case 1:
                    atom.setType("H");
                    break;
                case 6:
                    atom.setType("C3");
                    break;
                case 7:
                    atom.setType("N");
                    break;
                case 16:
                    atom.setType("S");
                    break;
                case 15:
                    atom.setType("P");
                    break;
                case 8:
                    atom.setType("O");
                    break;
                default:
                    atom.setType("C");
            }
        }

        for (Atom atom : bondMap.keySet()) {
            for (Bond bond : bondMap.get(atom)) {
                atom.bonds.add(bond);
            }
        }
        List<Atom> atomPathList = new ArrayList<>();
        // Adds all the ring closures for bonds broken in rings
        ringClosures = new HashMap<>();
        for (MNode mNode : pathNodes) {
            if (!mNode.isRingClosure()) {
                atomPathList.add(mNode.getAtom());
            } else {
                Atom atom1 = mNode.getAtom();
                Atom atom2 = mNode.getParent().getAtom();
                if ((ringClosures.containsKey(atom1) && ringClosures.get(atom1).containsKey(atom2)) || (ringClosures.containsKey(atom2) && ringClosures.get(atom2).containsKey(atom1))) {
                } else {
                    addRingClosure(atom1, atom2);
                    addRingClosurePairs(atom1, atom2);
                    addRingClosurePairs(atom2, atom1);
                }
            }
        }

        entity.molecule.setRingClosures(ringClosures);
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
        entity.molecule.sortByIndex();
        Molecule.makeAtomList();
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

    private void addRingClosure(Atom a1, Atom a2) {
        double distance = Atom.calcDistance(a1.getPoint(), a2.getPoint());
        Atom atomKey;
        Map<Atom, Double> ringClosure;
        if (ringClosures.containsKey(a1)) {
            ringClosure = ringClosures.get(a1);
            atomKey = a1;
            if (!ringClosure.containsKey(a2)) {
                ringClosure.put(a2, distance);
            }
        } else if (ringClosures.containsKey(a2)) {
            ringClosure = ringClosures.get(a2);
            atomKey = a2;
            if (!ringClosure.containsKey(a1)) {
                ringClosure.put(a1, distance);
            }
        } else {
            ringClosure = new HashMap<>();
            ringClosure.put(a2, distance);
            atomKey = a1;
        }
        
        ringClosures.put(atomKey, ringClosure);
    }

    private void addRingClosurePairs(Atom a, Atom a1) {
        List<Atom> atoms = a.getConnected();
        for (int i = 0; i < atoms.size(); i++) {
            Atom a2 = atoms.get(i);
            addRingClosure(a1, a2);
        }
    }
}
