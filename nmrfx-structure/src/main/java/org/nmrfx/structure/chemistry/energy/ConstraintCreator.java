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
   static final String PNAME = "pName";
   static final String ATOMS = "atoms";

   static final String START = "start";
   static final String FLOAT_BOND = "float";

   private ConstraintCreator() {

   }
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
        List<Polymer> polymers = molecule.getPolymers();
        if ((polymers.size() != 1) && !bondDict.containsKey(PNAME)) {
            throw new IllegalArgumentException("Multiple polymers in structure but no specification for which to be made cyclic\"");
        }
        Polymer polymer;
        if (bondDict.containsKey(PNAME)) {
            String pName = (String) bondDict.get(PNAME);
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
        molecule.getPolymers().stream().filter(Polymer::isCyclic).forEach(ConstraintCreator::addCyclicBond);
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

    public static void processBonds(List<StructureBond> structureBonds, String phase) throws IllegalArgumentException {
        for (var structureBond : structureBonds) {
            String bondMode = structureBond.mode;
            Atom atom1 = structureBond.atom1;
            Atom atom2 = structureBond.atom2;
            if (phase.equals("add") && bondMode.equals("add")) {
                double lower = structureBond.lower;
                double upper = structureBond.upper;
                addDistanceConstraint(atom1.getFullName(), atom2.getFullName(), lower, upper, false);
            } else if (phase.equals(FLOAT_BOND) && bondMode.equals(FLOAT_BOND)) {
                floatBond(atom1, atom2);
            } else if (phase.equals(FLOAT_BOND) && bondMode.equals("constrain")) {
                constrainDistance(atom1, atom2);
            } else if (phase.equals("break") && (bondMode.equals("break") || bondMode.equals(FLOAT_BOND))) {
                breakBond(atom1, atom2);
            }
        }
    }

    public static List<StructureBond> parseBonds(List<Map<String, Object>> bondDicts) throws IllegalArgumentException {
        List<StructureBond> structureBonds = new ArrayList<>();
        for (var bondDict : bondDicts) {
            String bondMode = (String) bondDict.getOrDefault("mode", "add");
            Object atomObject = bondDict.get(ATOMS);
            String atomName1;
            String atomName2;
            if (atomObject == null) {
                throw new IllegalArgumentException("No \"atoms\" entry in bond dictionary");
            } else if (atomObject instanceof List atomList) {
                if (atomList.size() != 2) {
                    throw new IllegalArgumentException("Need two atoms in bond dictionary atom entry");
                } else {
                    atomName1 = atomList.get(0).toString();
                    atomName2 = atomList.get(1).toString();
                }
            } else {
                throw new IllegalArgumentException("\"atoms\" entry not a list");
            }
            Atom atom1 = MoleculeBase.getAtomByName(atomName1);
            Atom atom2 = MoleculeBase.getAtomByName(atomName2);
            StructureBond structureBond = new StructureBond(atom1, atom2, bondMode);
            structureBonds.add(structureBond);
        }
        return structureBonds;
    }
    public static Polymer getPolymer(Molecule molecule, Map<String, Object> linkerDict) {
        List<Polymer> polymers = molecule.getPolymers();
        if ((polymers.size() != 1) && !linkerDict.containsKey(PNAME)) {
            throw new IllegalArgumentException("Multiple polymers in structure but no specification for which to be made cyclic\"");
        }
        Polymer polymer;
        if (linkerDict.containsKey(PNAME)) {
            String pName = (String) linkerDict.get(PNAME);
            var polyOpt = polymers.stream().filter(p -> p.getName().equals(pName)).findFirst();
            if (polyOpt.isEmpty()) {
                throw new IllegalArgumentException(pName + " is not a polymer within the molecule");
            } else {
                polymer = polyOpt.get();
            }
        } else {
            polymer = polymers.get(0);
        }
        return polymer;
    }

    public static List<StructureLink> parseLinkerDict(Molecule molecule, List<Map<String, Object>> linkerList) {
        List<StructureLink> result = new ArrayList<>();
        for (Map<String, Object> linkerDict : linkerList){
            Polymer polymer = getPolymer(molecule, linkerDict);
            if (linkerDict.containsKey(ATOMS)) {
                final Atom startAtom;
                final Atom endAtom;
                int nLinks;
                double linkLen;
                double valAngle;
                double dihAngle;
                List<String> atomNames = (List<String>) linkerDict.get(ATOMS);
                Atom atom1 = MoleculeBase.getAtomByName(atomNames.get(0));
                Atom atom2 = MoleculeBase.getAtomByName(atomNames.get(1));
                Entity entity1 = atom1.getTopEntity();
                var startOpt = entity1.startAtom();
                if (startOpt.isPresent()) {
                    Atom startAtomTest = startOpt.get();
                    if (startAtomTest == atom1) {
                        startAtom = atom2;
                        endAtom = atom1;
                    } else {
                        startAtom = atom1;
                        endAtom = atom2;
                    }
                } else {
                    throw new IllegalArgumentException("No start atom on entity");
                }
                nLinks = (Integer) linkerDict.getOrDefault("n", 6);
                linkLen = (Double) linkerDict.getOrDefault("length", 5.0);
                valAngle = (Double) linkerDict.getOrDefault("valAngle", 110.0);
                dihAngle = (Double) linkerDict.getOrDefault("dihAngle", 135.0);
                String bondOrder = (String) linkerDict.getOrDefault("order", "SINGLE");
                StructureLink structureLink = new StructureLink(startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle);
                structureLink.rna(linkerDict.containsKey("rna"));
                structureLink.bond(linkerDict.containsKey("bond"));
                structureLink.cyclic(linkerDict.containsKey("cyclic"));
                structureLink.order(bondOrder);
                structureLink.polymer(polymer);
                result.add(structureLink);
            } else {
                throw new IllegalArgumentException("No atoms or no cyclic bonds");
            }
        }
        return result;
    }

    public static void processLinks(Molecule molecule, List<StructureLink> structureLinks) {
        for (StructureLink sL : structureLinks) {
            if (sL.isCyclic && (sL.polymer != null)) {
                sL.polymer.setCyclic(true);
            } else if (sL.isBond) {
                if (sL.atom1.getTopEntity() != sL.atom2.getTopEntity()) {
                    molecule.createLinker(sL.atom1, sL.atom2, sL.bondOrder, (float) sL.length);
                } else {
                    double lower = sL.length - 0.0001;
                    double upper = sL.length + 0.0001;
                    MolecularConstraints.addDistanceConstraint(sL.atom1.getFullName(), sL.atom2.getFullName(),
                            lower, upper, true);

                }
            } else {
                if (sL.isRNALink) {
                    RNAStructureSetup.addRNALinker(molecule, sL.atom1, sL.atom2,
                            sL.nLinks, sL.length, sL.valAngle, sL.dihAngle);
                } else {
                    molecule.createLinker(sL.atom1, sL.atom2,
                            sL.nLinks, sL.length, sL.valAngle, sL.dihAngle);
                }

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
                atom = MoleculeBase.getAtomByName(aName);
            } else {
                String aName = connectorName + ".P";
                atom = MoleculeBase.getAtomByName(aName);
                if (atom == null) {
                    aName = connectorName + ".O5'";
                    atom = MoleculeBase.getAtomByName(aName);
                }
            }
        }
        return atom;
    }

    public static void floatBond(Atom atom1, Atom atom2) {
        Molecule molecule = (Molecule) atom1.getEntity().molecule;
        var ringClosures = molecule.getRingClosures();
        AngleTreeGenerator.addRingClosureSet(ringClosures, atom1, atom2);
    }

    public static void constrainDistance(Atom atom1, Atom atom2) {
        Molecule molecule = (Molecule) atom1.getEntity().molecule;
        var ringClosures = molecule.getRingClosures();
        AngleTreeGenerator.addConstrainDistance(ringClosures, atom1, atom2);
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
        if (linkerMap.containsKey(ATOMS)) {
            List<String> atomNames = (List<String>) linkerMap.get(ATOMS);
            Atom[] atoms = new Atom[2];
            atoms[0] = MoleculeBase.getAtomByName(atomNames.get(0));
            atoms[1] = MoleculeBase.getAtomByName(atomNames.get(1));
            if ((atoms[0] != null) && (atoms[1] != null)) {
                result = Optional.of(atoms);

            }
        }
        return result;
    }

    public static Map<String, String> setEntityEntryDict(List<StructureLink> linkerList, Map<String, String> treeDict) {
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        Entity startEntity;
        Atom entryAtom;
        if (treeDict != null) {
            String entryAtomName = treeDict.getOrDefault(START, null);
            entryAtom = MoleculeBase.getAtomByName(entryAtomName);
            startEntity = entryAtom.getTopEntity();
        } else {
            startEntity = molecule.entities.firstEntry().getValue();
            entryAtom = AngleTreeGenerator.findStartAtom(startEntity);
            treeDict = new HashMap<>();
            treeDict.put(START, entryAtom.getFullName());
        }
        startEntity.startAtom(entryAtom);
        List<Entity> visitedEntities = new ArrayList<>();
        visitedEntities.add(startEntity);

        if (linkerList != null) {
            List<StructureLink> linkerList2 = new ArrayList<>(linkerList);

            while (!linkerList2.isEmpty()) {
                StructureLink structureLink = linkerList2.get(0);
                if (structureLink.atom1 != null) {
                    Atom[] atoms = {structureLink.atom1, structureLink.atom2};
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
                        linkerList2.add(structureLink);
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
        for (Entity entity : molecule.getEntities()) {
            if (entity instanceof Polymer polymer) {
                Atom prfStartAtom = AngleTreeGenerator.findStartAtom(polymer);
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
            entity.startAtom().ifPresent(atom -> AngleTreeGenerator.genMeasuredTree(entity, atom));
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
        return optSet.orElseGet(() -> molConstraints.newAngleSet("default"));
    }

    static void addAngleConstraint(Molecule molecule, Atom[] atoms, double lower, double upper, double scale) throws InvalidMoleculeException {
        getAngleConstraintSet(molecule).addAngleConstraint(atoms, lower, upper, scale);
    }

    public static void addAngleConstraint(Molecule molecule, AngleConstraint angleConstraint) {
        getAngleConstraintSet(molecule).add(angleConstraint);
    }

    public static String getAtomName(Residue residue, String aName) {
        return residue.getPolymer().getName() + ':' + residue.getNumber() + '.' + aName;

    }

    public static List<StructureLink> validateLinkers(Molecule molecule, List<StructureLink> linkerList, Map<String, Object> treeDict, List<Map<String, Object>> rnaLinkerDicts) {
        List<Entity> unusedEntities = molecule.getEntities();
        String entryAtomName = treeDict != null ? (String) treeDict.get(START) : null;
        Atom entryAtom = entryAtomName != null ? MoleculeBase.getAtomByName(entryAtomName) : null;
        Entity firstEntity = entryAtom != null ? entryAtom.getTopEntity() : unusedEntities.getFirst();
        unusedEntities.remove(firstEntity);
        if (linkerList == null) {
            linkerList = new ArrayList<>();
        }
        if (rnaLinkerDicts != null) {
            linkerList.clear();
            List<Atom[]> linkerAtoms = RNAStructureSetup.readRNALinkerDict(molecule, rnaLinkerDicts, false);
            for (Atom[] linkPair : linkerAtoms) {
                StructureLink structureLink = new StructureLink(linkPair[0], linkPair[1]);
                linkerList.add(structureLink);
                for (Atom atom : linkPair) {
                    Entity entity = atom.getTopEntity();
                    unusedEntities.remove(entity);
                }
            }
        } else {
            for (var structureLink : linkerList) {
                Atom atom1 = structureLink.atom1;
                Atom atom2 = structureLink.atom2;
                var atoms = List.of(atom1, atom2);
                for (Atom atom : atoms) {
                    Entity entity = atom.getTopEntity();
                    unusedEntities.remove(entity);
                }
            }
        }
        for (Entity entity : unusedEntities) {
            Atom startAtom = firstEntity.getLastAtom();
            Atom endAtom = AngleTreeGenerator.findStartAtom(entity);
            StructureLink structureLink = new StructureLink(startAtom, endAtom);
            linkerList.add(structureLink);
        }
        return linkerList;
    }
}
