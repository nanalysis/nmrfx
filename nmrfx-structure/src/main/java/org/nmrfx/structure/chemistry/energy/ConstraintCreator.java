package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.AngleConstraint;
import org.nmrfx.chemistry.constraints.AngleConstraintSet;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.miner.NodeValidator;
import org.nmrfx.structure.chemistry.miner.PathIterator;
import org.nmrfx.structure.rna.*;

import java.util.*;

public class ConstraintCreator {

    public static void addRingClosures() {
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        var ringClosures = molecule.getRingClosures();
        double delta = 0.01;
        for (var entry : ringClosures.entrySet()) {
            Atom atom1 = entry.getKey();
            for (var atomDis : entry.getValue().entrySet()) {
                Atom atom2 = atomDis.getKey();
                double distance = atomDis.getValue();
                MolecularConstraints.addDistanceConstraint(atom1.getFullName(), atom2.getFullName(),
                        distance - delta, distance + delta, true);
            }
        }
    }

    record LinkProps(int n, double length, double valAngle, double dihAngle) {
    }

    static boolean isCyclicLinker(Map<String, Object> linkerDict) {
        boolean isCyclic = false;
        if (linkerDict.containsKey("bond")) {
            var bondMap = (Map<String, Object>) linkerDict.get("bond");
            if (!bondMap.containsKey("cyclic")) {
                isCyclic = true;
            }
        }
        return isCyclic;
    }

    static void processCyclic(Molecule molecule, Map<String, Object> linkerDict) {
        Map<String, Object> bondDict = (Map<String, Object>) linkerDict.get("bond");
        Boolean cyclicValue = (Boolean) bondDict.get("cyclic");
        List<Polymer> polymers = molecule.getPolymers();
        if ((polymers.size() != 1) && !bondDict.containsKey("pName")) {
            throw new IllegalArgumentException("Multiple polymers in structure but no specification for which to be made cyclic\"");
        }
        Polymer polymer = null;
        if (bondDict.containsKey("pName")) {
            String pName = (String) bondDict.get("pName");
            var polyOpt = polymers.stream().filter(p -> p.getName().equals(pName)).findFirst();
            if (polyOpt.isEmpty()) {
                throw new IllegalArgumentException(pName + " is not a polymer within the molecule");
            } else {
                polymer = polyOpt.get();
            }
        } else {
            polymer = polymers.get(0);
        }
        polymer.setCyclic(true);
    }

    public static void addCyclicConstraints(Molecule molecule) {
        molecule.getPolymers().stream().filter(p -> p.isCyclic()).forEach(polymer -> {
            addCyclicBond(polymer);
        });
    }

    static void addCyclicBond(Polymer polymer) {
        var distanceConstraints = polymer.getCyclicConstraints();
        for (var distanceConstraint : distanceConstraints) {
            String[] fields = distanceConstraint.split(" ");
            String atomName1 = fields[0];
            String atomName2 = fields[1];
            double distance = Double.parseDouble(fields[2]);
            addDistanceConstraint(atomName1, atomName2, distance - .0001, distance + .0001, true);
        }
    }

    public static void readLinkerDict(Molecule molecule, Map<String, Object> linkerDict) throws IllegalArgumentException {
        if (linkerDict == null) {
            return;
        }
        LinkProps linkProps;
        final Entity startEntity;
        final Entity endEntity;
        final Atom startAtom;
        final Atom endAtom;
        int nLinks;
        double linkLen;
        double valAngle;
        double dihAngle;

        if (isCyclicLinker(linkerDict)) {
            processCyclic(molecule, linkerDict);
            return;
        }

        if (linkerDict.containsKey("atoms")) {
            List<String> atomNames = (List<String>) linkerDict.get("atoms");
            Atom atom1 = MoleculeBase.getAtomByName(atomNames.get(0));
            Atom atom2 = MoleculeBase.getAtomByName(atomNames.get(1));
            Entity entity1 = atom1.getTopEntity();
            Entity entity2 = atom2.getTopEntity();
            var startOpt = entity1.startAtom();
            if (startOpt.isPresent()) {
                Atom startAtomTest = startOpt.get();
                if (startAtomTest == atom1) {
                    startEntity = entity2;
                    startAtom = atom2;
                    endEntity = entity1;
                    endAtom = atom1;
                } else {
                    startEntity = entity1;
                    startAtom = atom1;
                    endEntity = entity2;
                    endAtom = atom2;
                }
            } else {
                throw new IllegalArgumentException("No start atom on entity");
            }
            nLinks = (Integer) linkerDict.getOrDefault("n", 6);
            linkLen = (Double) linkerDict.getOrDefault("length", 5.0);
            valAngle = (Double) linkerDict.getOrDefault("valAngle", 110.0);
            dihAngle = (Double) linkerDict.getOrDefault("dihAngle", 135.0);
            linkProps = new LinkProps(nLinks, linkLen, valAngle, dihAngle);
        } else {
            throw new IllegalArgumentException("No atoms or no cyclic bonds");
        }
        if (linkerDict.containsKey("bond")) {
            Map<String, Object> bondDict = (Map<String, Object>) linkerDict.get("bond");
            boolean sameEnt = startEntity == endEntity;
            float bondLength = (Float) bondDict.getOrDefault("length", 1.08f);
            String bondOrder = (String) bondDict.getOrDefault("order", "SINGLE");
            if (!sameEnt) {
                molecule.createLinker(startAtom, endAtom, bondOrder, bondLength);
            } else {
                var lAtoms = getLinkerAtoms(bondDict);
                if (lAtoms.isPresent()) {
                    Atom[] atoms = lAtoms.get();
                    double lower = bondLength - 0.0001;
                    double upper = bondLength + 0.0001;
                    MolecularConstraints.addDistanceConstraint(atoms[0].getFullName(), atoms[1].getFullName(), lower, upper, true);
                }
            }
        } else {
            if (linkerDict.containsKey("rna")) {
                RNAStructureSetup.addRNALinker(molecule, startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle);
            } else {
                molecule.createLinker(startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle);
            }
        }
    }

    public static Atom getConnectorAtom(Molecule molecule, String connectorName, boolean first) {
        Atom atom;
        if (!connectorName.contains(":")) {
            Polymer polymer = molecule.getPolymer(connectorName);
            List<Residue> residues = polymer.getResidues();
            if (first) {
                Residue residue = residues.reversed().get(0);
                atom = residue.getAtom("H3'");
            } else {
                Residue residue = residues.get(0);
                atom = residue.getAtom("O5'");
            }
        } else {
            if (first) {
                String aName = connectorName + ".H3'";
                atom = molecule.getAtomByName(aName);
            } else {
                String aName = connectorName + ".P";
                atom = molecule.getAtomByName(aName);
                if (atom == null) {
                    aName = connectorName + ".O5'";
                    atom = molecule.getAtomByName(aName);

                }
            }
        }
        return atom;
    }

    public static void breakBond(Atom atom1, Atom atom2) {
        atom1.removeBondTo(atom2);
        atom2.removeBondTo(atom1);
        atom1.daughterAtom = null;
        atom1.rotActive = false;
        atom1.rotUnit = -1;
        atom1.rotGroup = null;
    }

    public static void addDistanceConstraint(List<String> atomNames1, List<String> atomNames2, double lower, double upper, boolean bond) {
        MolecularConstraints.addDistanceConstraint(atomNames1, atomNames2, lower, upper, bond);
    }

    public static void addDistanceConstraint(String atomName1, String atomName2, double lower, double upper, boolean bond) {
        MolecularConstraints.addDistanceConstraint(atomName1, atomName2, lower, upper, bond);
    }

    static Optional<Atom[]> getLinkerAtoms(Map<String, Object> linkerMap) {
        Optional<Atom[]> result = Optional.empty();
        if (linkerMap.containsKey("atoms")) {
            List<String> atomNames = (List<String>) linkerMap.get("atoms");
            Atom[] atoms = new Atom[2];
            atoms[0] = MoleculeBase.getAtomByName(atomNames.get(0));
            atoms[1] = MoleculeBase.getAtomByName(atomNames.get(1));
            if ((atoms[0] != null) && (atoms[1] != null)) {
                result = Optional.of(atoms);

            }
        }
        return result;
    }

    public static Map<String, String> setEntityEntryDict(List<Map<String, Object>> linkerList, Map<String, String> treeDict) {
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        Entity startEntity = null;
        Atom entryAtom = null;
        if (treeDict != null) {
            String entryAtomName = treeDict.getOrDefault("start", null);
            entryAtom = MoleculeBase.getAtomByName(entryAtomName);
            startEntity = entryAtom.getTopEntity();
        } else {
            startEntity = molecule.entities.firstEntry().getValue();
            entryAtom = AngleTreeGenerator.findStartAtom(startEntity);
            treeDict = new HashMap<>();
            treeDict.put("start", entryAtom.getFullName());
        }
        startEntity.startAtom(entryAtom);
        List<Entity> visitedEntities = new ArrayList<>();
        visitedEntities.add(startEntity);

        if (linkerList != null) {
            List<Map<String, Object>> linkerList2 = new ArrayList<>(linkerList);

            while (!linkerList2.isEmpty()) {
                Map<String, Object> linkerDict = linkerList2.get(0);
                var atomOpt = getLinkerAtoms(linkerDict);
                Atom[] atoms;
                if (atomOpt.isPresent()) {
                    atoms = atomOpt.get();
                    Entity entity0 = atoms[0].getTopEntity();
                    Entity entity1 = atoms[1].getTopEntity();
                    if (visitedEntities.contains(entity0) && visitedEntities.contains(entity1)) {
                        linkerList2.remove(0);
                        continue;
                    } else if (visitedEntities.contains(entity0)) {
                        entryAtom = atoms[1];
                        linkerList2.remove(0);
                    } else if (visitedEntities.contains(entity1)) {
                        entryAtom = atoms[0];
                        linkerList2.remove(0);
                    } else {
                        linkerList2.remove(0);
                        linkerList2.add(linkerDict);
                        continue;
                    }
                    Entity entity = entryAtom.getTopEntity();
                    entity.startAtom(entryAtom);
                    visitedEntities.add(entity);
                }
            }
        }

        return treeDict;
    }

    public static void measureTree(Molecule molecule) {
        var aTree = new AngleTreeGenerator();
        for (Entity entity : molecule.getEntities()) {
            if (entity instanceof Polymer polymer) {
                Atom prfStartAtom = aTree.findStartAtom(polymer);
                var treeStartOpt = polymer.startAtom();
                if (treeStartOpt.isPresent()) {
                    Atom treeStartAtom = treeStartOpt.get();
                    if (prfStartAtom == treeStartAtom) {
                        if (molecule.getEntities().size() > 1) {
                            continue;
                        }
                    } else {
                        AngleTreeGenerator.genCoordinates(polymer, prfStartAtom);
                    }
                }
            }
            setupAtomProperties(entity);
            entity.startAtom().ifPresent(atom -> {
                AngleTreeGenerator.genMeasuredTree(entity, atom);
            });
        }
    }

    public static void setupAtomProperties(Entity entity) {
        var pI = new PathIterator(entity);
        var nodeValidator = new NodeValidator();
        pI.init(nodeValidator);
        pI.processPatterns();
        pI.setProperties("ar", "AROMATIC");
        pI.setProperties("res", "RESONANT");
        pI.setProperties("namide", "AMIDE");
        pI.setProperties("r", "RING");
        pI.setHybridization();
    }

    static AngleConstraintSet getAngleConstraintSet(Molecule molecule) {
        var molConstraints = molecule.getMolecularConstraints();
        var optSet = molConstraints.activeAngleSet();
        AngleConstraintSet angleCon = null;
        if (optSet.isPresent()) {
            angleCon = optSet.get();
        } else {
            angleCon = molConstraints.newAngleSet("default");
        }
        return angleCon;
    }

    static void addAngleConstraint(Molecule molecule, Atom[] atoms, double lower, double upper, double scale) throws InvalidMoleculeException {
        getAngleConstraintSet(molecule).addAngleConstraint(atoms, lower, upper, scale);
    }

    public static void addAngleConstraint(Molecule molecule, AngleConstraint angleConstraint) throws InvalidMoleculeException {
        getAngleConstraintSet(molecule).add(angleConstraint);
    }

    public static String getAtomName(Residue residue, String aName) {
        return residue.getPolymer().getName() + ':' + residue.getNumber() + '.' + aName;

    }


}
