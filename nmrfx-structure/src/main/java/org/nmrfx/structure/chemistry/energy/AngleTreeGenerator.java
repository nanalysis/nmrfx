package org.nmrfx.structure.chemistry.energy;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.search.MNode;
import org.nmrfx.chemistry.search.MTree;
import org.nmrfx.structure.chemistry.CoordinateGenerator;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.ring.HanserRingFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Bruce Johnson
 */
@PluginAPI("residuegen")
public class AngleTreeGenerator {
    private static final Logger log = LoggerFactory.getLogger(AngleTreeGenerator.class);

    Map<Atom, Map<Atom, Double>> ringClosures;
    List<Bond> closureBonds = new ArrayList<>();
    List<Atom> atomPathList = new ArrayList<>();

    public static void genCoordinates(Entity entity, Atom startAtom) {
        AngleTreeGenerator aTreeGen = new AngleTreeGenerator();
        List<List<Atom>> atomTree = aTreeGen.genTree(entity, startAtom, null);
        List<Atom> atomList = aTreeGen.getPathList();
        int[][] genVecs = CoordinateGenerator.setupCoords(atomTree);
        CoordinateGenerator.prepareAtoms(atomList, false);
        for (Atom atom : atomList) {
            atom.setPointValidity(false);
        }
        CoordinateGenerator.genCoords(genVecs, atomList);
    }

    public static void fillCoordinates(Entity entity, Atom startAtom) {
        if (startAtom == null) {
            for (Atom atom : entity.atoms) {
                if (atom.getPointValidity()) {
                    startAtom = atom;
                    break;
                }
            }
        }
        AngleTreeGenerator aTreeGen = new AngleTreeGenerator();
        List<List<Atom>> atomTree = aTreeGen.genTree(entity, startAtom, null);
        List<Atom> atomList = aTreeGen.getPathList();
        int[][] genVecs = CoordinateGenerator.setupCoords(atomTree);
        CoordinateGenerator.prepareAtoms(atomList, true);
        CoordinateGenerator.genCoords(genVecs, atomList);
    }

    public static void genMeasuredTree(Entity entity, Atom startAtom) {
        if (startAtom == null) {
            startAtom = entity.atoms.get(0);
        }
        Molecule molecule = (Molecule) startAtom.entity.molecule;

        AngleTreeGenerator aTreeGen = new AngleTreeGenerator();
        List<List<Atom>> atomTree = aTreeGen.genTree(entity, startAtom, null);
        aTreeGen.measureAtomTree(entity, atomTree, true, false);
        molecule.setRingClosures(aTreeGen.getRingClosures());
    }

    static class BondSort implements Comparable<BondSort> {

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
        boolean ok = startAtom.bonds.size() == 1;
        if (ok) {
            Atom partner = startAtom.getConnected().get(0);
            ok = !partner.getFlag(Atom.RING);
        }
        return ok;
    }

    public Atom findStartAtom(ITree itree) {
        List<Atom> atoms = itree.getAtomArray();
        Atom startAtom = null;
        if (itree instanceof Entity && itree instanceof Polymer) {
            Polymer polymer = (Polymer) itree;
            startAtom = polymer.getFirstResidue().getFirstBackBoneAtom();
        } else {
            for (Atom atom : atoms) {
                if (checkStartAtom(atom)) {
                    startAtom = atom;
                    break;
                }
            }
        }
        return startAtom;
    }

    public List<List<Atom>> genTree(ITree itree, Atom startAtom, Atom endAtom)
            throws IllegalArgumentException {

        List<Atom> atoms = itree.getAtomArray();
        for (Atom atom : atoms) {
            atom.parent = null;
        }

        if (startAtom == null) {
            startAtom = findStartAtom(itree);
            if (startAtom == null) {
                throw new IllegalArgumentException("Didn't find start atom");
            }
        } else {
            if (!checkStartAtom(startAtom)) {
                log.debug("Start atom has more than 1 bond \"" + startAtom.getShortName() + "\"");
            }
        }
        Map<Atom, Integer> hash = new HashMap<>();
        MTree mTree = new MTree();
        int i = 0;
        int startIndex = -1;
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
            throw new IllegalArgumentException("Didn't find start atom\"" + startAtom.getShortName() + "\"");
        }

        Set<Bond> usedBonds = new HashSet<>();
        for (Atom atom : atoms) {
            for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
                Bond bond = atom.bonds.get(iBond);
                if (usedBonds.contains(bond)) {
                    continue;
                } else {
                    usedBonds.add(bond);
                }
                if (bond.getProperty(Bond.DEACTIVATE)) {
                    continue;
                }
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

        mTree.broad_path(startIndex);
        pathNodes = mTree.getPathNodes();

        MNode lastNode = null;
        boolean firstAtom = true;

        int lastNodeIndex = 0;
        int nShells = pathNodes.get(pathNodes.size() - 1).getShell() + 1;
        int iAtom = 0;
        atomPathList = new ArrayList<>();
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

                Atom atom2;
                Atom atom1 = null;
                Atom atom0 = null;
                if (mNode2 != null) {
                    atom2 = mNode2.getAtom();
                    if (firstAtom) {
                        atom2.daughterAtom = atom3;
                    }
                    atom3.parent = atom2;
                    if (atom2.getElementName() != null && atom3.getElementName() != null) {
                        Bond bond = new Bond(atom2, atom3);
                        atom2.addBond(bond);
                        atom3.addBond(bond);
                    }
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
        if (itree instanceof Molecule) {
            Molecule mol = (Molecule) itree;
            mol.setTreeList(atomPathList);
            mol.setAtomTree(atomTree);
        }
        ringClosures = new HashMap<>();
        if (log.isDebugEnabled()) {
            log.debug(dumpAtomTree(atomTree));
        }
        return atomTree;
    }

    public List<Bond> getClosureBonds() {
        return closureBonds;
    }

    public Map<Atom, Map<Atom, Double>> getRingClosures() {
        return ringClosures;
    }

    public void measureAtomTree(ITree itree, List<List<Atom>> atomTree, boolean changeBonds, boolean freezeRings) {
        // get Atom array --> getAtomList() difference?
        for (Atom atom : itree.getAtomArray()) {
            atom.parent = null;
        }
        Map<Atom, List<Bond>> bondMap = new HashMap<>();
        HanserRingFinder ringFinder = new HanserRingFinder();
        Molecule mol;
        if (itree instanceof Molecule) {
            mol = (Molecule) itree;
        } else {
            mol = (Molecule) MoleculeFactory.getActive();
        }
        ringFinder.findSmallestRings(mol);
        atomTree.forEach((branch) -> {
                    Atom a0 = branch.get(0);
                    Atom a1 = branch.get(1);
                    Atom a2 = branch.get(2);
                    Point3 p0 = a0 != null ? a0.getPoint() : null;
                    Point3 p1 = a1 != null ? a1.getPoint() : null;
                    Point3 p2 = a2 != null ? a2.getPoint() : null;
                    if ((p1 != null) && (p0 == null)) {
                        Vector3D ps0 =  p1.subtract(new Point3(1.0,1.0, 0.0));
                        p0 = new Point3(ps0);
                    }
                    double lastAngle = 0.0;
                    Optional<Bond> oBond = Optional.empty();
                    for (int j = 3; j < branch.size(); j++) {
                        Atom a3 = branch.get(j);
                        Point3 p3 = a3.getPoint();
                        if (a2 != null) {
                            oBond = a2.getBond(a3);
                            a3.parent = a2;
                            if (a3.getProperty("linker") == null && a3.getSymbol() != null) {
                                if (p2 != null) {
                                    float bondLength = (float) AtomMath.calcDistance(p2, p3);
                                    if (bondLength > 0.001) {
                                        a3.bondLength = bondLength;
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
                        // mode variable is used to explain the reason for setting rotatable state of a bond
                        // keep it here for use in debugging
                        int mode = 0;
                        if (oBond.isPresent() && (oBond.get().getOrder() != Order.SINGLE)) {
                            rotatable = false;
                            mode = 1;
                        } else if (a2 == null) {
                            rotatable = false;
                            mode = 2;
                        } else if (a3.bonds.size() < 2 && a3.getProperty("connector") == null) {
                            rotatable = false;
                            mode = 3;
                        } else if (a3.getAtomicNumber() == 1) {
                            rotatable = false;
                            mode = 4;
                            // fixme atom prop P hyb wrong so we have these special cases
                        } else if ((a3.getAtomicNumber() == 8) && (a2.getAtomicNumber() == 15)) {
                            rotatable = true;
                            mode = 5;
                        } else if ((a3.getAtomicNumber() == 15) && (a2.getAtomicNumber() == 8)) {
                            rotatable = true;
                            mode = 6;
                        } else if (a3.getFlag(Atom.AMIDE)) {
                            rotatable = false;
                            mode = 7;
                        } else if (a3.getFlag(Atom.AROMATIC) && a2.getFlag(Atom.AROMATIC)) { // wrong if connecting two rings
                            rotatable = false;
                            mode = 8;
                        } else if (a3.getFlag(Atom.RING) && a2.getFlag(Atom.RING)) {
                            if (freezeRings) {
                                rotatable = false;
                            } else {
                                rotatable = false;
                                mode = 9;
                                if ((a3.parent == a2) && (a3.daughterAtom != null)) {
                                    rotatable = true;
                                    mode = 10;
                                }
                            }
                        } else if (a2.getFlag(Atom.AROMATIC) && (a3.getAtomicNumber() == 7)) { // flatten nh2 on bases
                            rotatable = false;
                            mode = 11;
                        }
                        if (a3.getProperty("rings") != null && a2.getProperty("rings") != null) {
                            ArrayList<Ring> a3Rings = (ArrayList) a3.getProperty("rings");
                            ArrayList<Ring> a2Rings = (ArrayList) a2.getProperty("rings");
                            boolean isRot = true;
                            for (Ring ring : a3Rings) {
                                if (a2Rings.contains(ring)) {
                                    isRot = false;
                                    break;
                                }
                            }
                            if (isRot) {
                                rotatable = true;
                            }
                        }

                        int currIRP = a3.irpIndex;
                        if (currIRP == 0) {
                            currIRP = 1;
                        }
                        a3.irpIndex = rotatable ? currIRP : 0;
                    }
                }
        );
        if (changeBonds) {
            for (Atom atom
                    : itree.getAtomArray()) {
                atom.bonds.clear();
            }

            for (Atom atom
                    : bondMap.keySet()) {
                for (Bond bond : bondMap.get(atom)) {
                    atom.addBond(bond);
                }
            }

            ringClosures = new HashMap<>();
            for (Bond bond : closureBonds) {
                bond.begin.addBond(bond);
                bond.end.addBond(bond);
                addRingClosureSet(ringClosures, bond.begin, bond.end);
            }
            if (itree instanceof Molecule) {
                mol = (Molecule) itree;
                mol.updateAtomArray();
                mol.resetGenCoords();
                mol.setupRotGroups();
                mol.genCoords();
            }
        }
    }

    public static String dumpAtomTree(List<List<Atom>> atomTree) {
        StringBuilder sBuilder = new StringBuilder();
        for (List<Atom> branch : atomTree) {
            for (Atom atom : branch) {
                sBuilder.append(String.format("%8s", atom == null ? "____" : atom.getShortName()));
            }
            sBuilder.append(" " + branch.get(2).rotUnit + " " + branch.get(2).rotActive + "\n");
        }
        return sBuilder.toString();
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
        return shellNodes;
    }

    public static void addRingClosureSet(Map<Atom, Map<Atom, Double>> ringClosures, Atom begin, Atom end) {
        addRingClosure(ringClosures, begin, end);
        addRingClosurePairs(ringClosures, begin, end);
        addRingClosurePairs(ringClosures, end, begin);
    }

    public static void addRingClosure(Map<Atom, Map<Atom, Double>> ringClosures,
                                      Atom a1, Atom a2) {
        if (a1.getPoint() != null && a2.getPoint() != null) {
            double distance = Atom.calcDistance(a1.getPoint(), a2.getPoint());
            Atom atomKey = a1.getIndex() < a2.getIndex() ? a1 : a2;
            Atom nestedKey = a1 == atomKey ? a2 : a1;

            Map<Atom, Double> ringClosure;
            ringClosure = ringClosures.containsKey(atomKey) ? ringClosures.get(atomKey) : new HashMap<>();
            ringClosure.put(nestedKey, distance);
            ringClosures.put(atomKey, ringClosure);
        }
    }

    private static void addRingClosurePairs(Map<Atom, Map<Atom, Double>> ringClosures,
                                            Atom a, Atom a1) {
        List<Atom> atoms = a.getConnected();
        for (Atom a2 : atoms) {
            if ((a1 != a2)) {
                addRingClosure(ringClosures, a1, a2);
            }
        }
    }

    public static void addConstrainDistance(Map<Atom, Map<Atom, Double>> ringClosures, Atom begin, Atom end) {
        addRingClosure(ringClosures, begin, end);
    }


    public List<Atom> getPathList() {
        return atomPathList;
    }

    private boolean testTerminal(Atom a) {
        List<Atom> aList = a.getConnected();
        List<Atom> appeared = new ArrayList<>();
        for (Atom atom : aList) {
            if (!appeared.contains(atom)) {
                appeared.add(atom);
            }
        }
        return appeared.size() == 1;
    }
}
