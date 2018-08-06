package org.nmrfx.structure.chemistry.energy;

import java.util.ArrayList;
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
    Map<Atom, Map<Atom, Double>> ringClosures;
    List<Bond> closureBonds = new ArrayList<>();

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

    public List<List<Atom>> genTree(Molecule mol, Atom startAtom, Atom endAtom) {
        System.out.println("gentree");
        MTree mTree = new MTree();
        Map<Atom, Integer> hash = new HashMap<>();
        int i = 0;
        int startIndex = -1;
        List<Atom> atoms = mol.getAtoms();

        if (startAtom == null) {
            for (Atom atom : atoms) {
                if (checkStartAtom(atom)) {
                    startAtom = atom;
                    break;
                }
            }
        } else {
            if (!checkStartAtom(startAtom)) {
                //throw new IllegalArgumentException("Start atom has more than 1 bond \"" + startAtom.getShortName() + "\"");
            }
        }
        for (Atom atom : atoms) {
            if (atom == startAtom) {
                startIndex = i;
            }
            hash.put(atom, i);
            MNode mNode = mTree.addNode();
            mNode.setAtom(atom);
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

                if (!bond.isRingClosure() && (iNodeBegin != null) && (iNodeEnd != null)) {
                    mTree.addEdge(iNodeBegin, iNodeEnd);
                } else if (bond.isRingClosure()) {
                    closureBonds.add(bond);
                }
            }
        }
        mTree.broad_path(startIndex);
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
        // fixme should we sort
//        for (MNode mNode : pathNodes) {
//            mNode.sortNodesDescending();
//        }

        mTree.broad_path(startIndex);
        pathNodes = mTree.getPathNodes();

        MNode lastNode = null;
        boolean firstAtom = true;

        int lastNodeIndex = 0;
        int nShells = pathNodes.get(pathNodes.size() - 1).getShell() + 1;
        int iAtom = 0;
        List<Atom> atomPathList = new ArrayList<>();
        List<List<Atom>> atomTree = new ArrayList<>();
        List<Atom> branchList = null;
        for (int iShell = 0; iShell < nShells; iShell++) {
            List<MNode> shellNodes = getShellNodes(pathNodes, iShell, lastNodeIndex);
            lastNodeIndex += shellNodes.size();
            if (shellNodes.isEmpty()) {
                break;
            }
            for (MNode mNode3 : shellNodes) {
                if (mNode3.isRingClosure()) {
                    Atom atom3 = mNode3.getAtom();
                    atom3.rotActive = false;
                    MNode mNode2 = mNode3.getParent();
                    if (mNode2 != null) {
                        Atom atom2 = mNode2.getAtom();
                        Optional<Bond> oBond = atom2.getBond(atom3);
                        if (oBond.isPresent()) {
                            oBond.get().setRingClosure(true);
                            closureBonds.add(oBond.get());
                        }
                    }
                    continue;
                }
                Atom atom3 = mNode3.getAtom();
                atom3.setTreeIndex(iAtom++);
                atomPathList.add(atom3);
                if (iShell == 0) {
                    break;
                }
                MNode mNode2 = mNode3.getParent();
                if ((lastNode == null) || (mNode2 != lastNode)) {
                    lastNode = mNode2;
                    firstAtom = true;
                    branchList = new ArrayList<>();
                    atomTree.add(branchList);
                }

                Optional<Bond> oBond = Optional.empty();
                Atom atom2 = null;
                Atom atom1 = null;
                Atom atom0 = null;
                if (mNode2 != null) {
                    atom2 = mNode2.getAtom();
                    if (firstAtom) {
                        atom2.daughterAtom = atom3;
                    }
                    atom3.parent = atom2;
                    Point3 pt2 = atom2.getPoint();
                    oBond = atom2.getBond(atom3);
                    MNode mNode1 = mNode2.getParent();
                    if (mNode1 != null) {
                        atom1 = mNode1.getAtom();
                        MNode mNode0 = mNode1.getParent();
                        if (mNode0 != null) {
                            atom0 = mNode0.getAtom();
                        }
                    }
                } else {
                    atom2 = null;
                }
                if (branchList.isEmpty()) {
                    branchList.add(atom0);
                    branchList.add(atom1);
                    branchList.add(atom2);
                }
                branchList.add(atom3);

                firstAtom = false;
            }
        }
        mol.setTreeList(atomPathList);
        ringClosures = new HashMap<>();
//        for (MNode mNode : pathNodes) {
//            if (mNode.isRingClosure()) {
//                Atom atom1 = mNode.getAtom();
//                Atom atom2 = mNode.getParent().getAtom();
//                if ((ringClosures.containsKey(atom1) && ringClosures.get(atom1).containsKey(atom2)) || (ringClosures.containsKey(atom2) && ringClosures.get(atom2).containsKey(atom1))) {
//                } else {
//                    addRingClosure(atom1, atom2);
//                    addRingClosurePairs(atom1, atom2);
//                    addRingClosurePairs(atom2, atom1);
//                }
//            }
//        }

        dumpAtomTree(atomTree);
        return atomTree;
    }

    public Map<Atom, Map<Atom, Double>> getRingClosures() {
        return ringClosures;
    }

    public void measureAtomTree(Molecule molecule, List<List<Atom>> atomTree) {
        for (Atom atom : molecule.getAtomArray()) {
            atom.parent = null;
        }
        Map<Atom, List<Bond>> bondMap = new HashMap<>();
        atomTree.forEach((branch) -> {
            Atom a0 = branch.get(0);
            Atom a1 = branch.get(1);
            Atom a2 = branch.get(2);
            Point3 p0 = a0 != null ? a0.getPoint() : null;
            Point3 p1 = a1 != null ? a1.getPoint() : null;
            Point3 p2 = a2 != null ? a2.getPoint() : null;
            double lastAngle = 0.0;
            Optional<Bond> oBond = Optional.empty();
            for (int j = 3; j < branch.size(); j++) {
                Atom a3 = branch.get(j);
                Point3 p3 = a3.getPoint();
                if (p2 != null) {
                    oBond = a2.getBond(a3);
                    a3.parent = a2;
                    a3.bondLength = (float) AtomMath.calcDistance(p2, p3);
                    if (p1 != null) {
                        a3.valanceAngle = (float) AtomMath.calcAngle(p1, p2, p3);
                        if (p0 != null) {
                            double dih = AtomMath.calcDihedral(p0, p1, p2, p3);
                            if (dih < 0.0) {
                                dih = dih + 2.0 * Math.PI;
                            }
                            double newDih;
                            if (j > 3) {
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
                            a3.dihedralAngle = (float) newDih;
                        }
                    }
                }
                oBond.ifPresent(b -> {
                    if (!bondMap.containsKey(a2)) {
                        bondMap.put(a2, new ArrayList<Bond>());
                    }
                    if (!bondMap.containsKey(a3)) {
                        bondMap.put(a3, new ArrayList<Bond>());
                    }
                    bondMap.get(a2).add(b);
                    bondMap.get(a3).add(b);
                });

                // fixme write faster code to get bond (like atom2.getbond(atom3) so you search bonds for atom not whole entity
                boolean rotatable = true;
                if (oBond.isPresent() && (oBond.get().getOrder() != Order.SINGLE)) {
                    rotatable = false;
                } else if (a2 == null) {
                    rotatable = false;
                } else if (a3.bonds.size() < 2) {
                    rotatable = false;
                } else if (a3.getAtomicNumber() == 1) {
                    rotatable = false;
// fixme atom prop P hyb wrong so we have these special cases
                } else if ((a3.getAtomicNumber() == 8) && (a2.getAtomicNumber() == 15)) {
                    rotatable = true;
                } else if ((a3.getAtomicNumber() == 15) && (a2.getAtomicNumber() == 8)) {
                    rotatable = true;
                } else if (a3.getFlag(Atom.RESONANT) && a2.getFlag(Atom.RESONANT)) {
                    rotatable = false;
                } else if (a3.getFlag(Atom.RING) && a2.getFlag(Atom.RING)) {
//                    rotatable = false;
                } else if (a3.getFlag(Atom.AROMATIC) && a2.getFlag(Atom.AROMATIC)) {
                    rotatable = false;
                }
                a3.irpIndex = rotatable ? 1 : 0;
//                System.out.println(a3.getShortName() + " " + a3.irpIndex + " " + rotatable);
            }
        });
        for (Atom atom : molecule.getAtomArray()) {
            atom.bonds.clear();
        }

        for (Atom atom : bondMap.keySet()) {
            for (Bond bond : bondMap.get(atom)) {
                atom.addBond(bond);
            }
        }
        ringClosures = new HashMap<>();
        System.out.println("add clo " + closureBonds.size());
        for (Bond bond : closureBonds) {
            bond.begin.addBond(bond);
            bond.end.addBond(bond);
            System.out.println("close bond " + bond.toString());
            addRingClosure(bond.begin, bond.end);
            addRingClosurePairs(bond.begin, bond.end);
            addRingClosurePairs(bond.end, bond.begin);
        }
        Molecule.makeAtomList();
        molecule.resetGenCoords();
        molecule.setupRotGroups();
        molecule.genCoords();
    }
//   List<Atom> atomPathList = new ArrayList<>();
//        // Adds all the ring closures for bonds broken in rings
//        ringClosures = new HashMap<>();
//        for (MNode mNode : pathNodes) {
//            if (!mNode.isRingClosure()) {
//                atomPathList.add(mNode.getAtom());
//            } else {
//                Atom atom1 = mNode.getAtom();
//                Atom atom2 = mNode.getParent().getAtom();
//                if ((ringClosures.containsKey(atom1) && ringClosures.get(atom1).containsKey(atom2)) || (ringClosures.containsKey(atom2) && ringClosures.get(atom2).containsKey(atom1))) {
//                } else {
//                    addRingClosure(atom1, atom2);
//                    addRingClosurePairs(atom1, atom2);
//                    addRingClosurePairs(atom2, atom1);
//                }
//            }
//        }

    public static void dumpAtomTree(List<List<Atom>> atomTree) {
        for (List<Atom> branch : atomTree) {
            for (Atom atom : branch) {
                System.out.printf("%8s", atom == null ? "____" : atom.getShortName());
            }
            System.out.println("");
        }

    }

    private List<MNode> getShellNodes(List<MNode> nodes, int iShell, int start) {
        List<MNode> shellNodes = new ArrayList<>();
        for (int i = start; i < nodes.size(); i++) {
            MNode node = nodes.get(i);
            if (node.getShell() > iShell) {
                break;
            } else {
                shellNodes.add(node);
            }
        }
// Sorting the shellNodes results in errors calculating dihedral angles
//        Collections.sort(shellNodes, MNode::compareByParValue);
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
            if ((a1 != a2) && (a2.getAtomicNumber() != 1)) {
                addRingClosure(a1, a2);
            }
        }
    }
}
