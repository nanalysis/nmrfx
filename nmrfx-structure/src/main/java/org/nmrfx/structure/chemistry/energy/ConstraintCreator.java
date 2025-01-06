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

    record AtomAtomDistance(String aName1, String aName2, double distance) {
    }

    record AtomAtomLowUp(String aName1, String aName2, double lower, double upper) {
    }

    static Map<String, List<AtomAtomDistance>> rnaPlanarity = new HashMap<>();

    static {
        var gcList = List.of(
                new AtomAtomDistance("C6p", "C4", 0.5),
                new AtomAtomDistance("C2", "C2p", 0.2)
        );
        var cgList = List.of(
                new AtomAtomDistance("C4", "C6p", 0.5),
                new AtomAtomDistance("C2p", "C2", 0.2)
        );

        rnaPlanarity.put("GC", gcList);
        rnaPlanarity.put("CG", cgList);
        rnaPlanarity.put("AU", gcList);
        rnaPlanarity.put("UA", cgList);
    }

    static Map<String, List<AtomAtomLowUp>> stackTo = new HashMap<>();
    static Map<String, List<AtomAtomLowUp>> stackPairs = new HashMap<>();

    static {
        stackTo.put("C", List.of(new AtomAtomLowUp("H2'", "H6", 4.0, 2.7), new AtomAtomLowUp("H3'", "H6", 3.0, 3.3)));
        stackTo.put("U", List.of(new AtomAtomLowUp("H2'", "H6", 4.0, 2.7), new AtomAtomLowUp("H3'", "H6", 3.0, 3.3)));
        stackTo.put("G", List.of(new AtomAtomLowUp("H2'", "H8", 4.0, 2.7), new AtomAtomLowUp("H3'", "H8", 3.0, 3.3)));
        stackTo.put("A", List.of(new AtomAtomLowUp("H2'", "H8", 4.0, 2.7), new AtomAtomLowUp("H3'", "H8", 3.0, 3.3)));


        stackPairs.put("CU", List.of(new AtomAtomLowUp("H6", "H5", 1.8, 5.0), new AtomAtomLowUp("H6", "H6", 1.8, 5.0)));
        stackPairs.put("CC", List.of(new AtomAtomLowUp("H6", "H5", 1.8, 5.0), new AtomAtomLowUp("H6", "H6", 1.8, 5.0)));
        stackPairs.put("CG", List.of(new AtomAtomLowUp("H6", "H8", 1.8, 5.0), new AtomAtomLowUp("H5", "H8", 1.8, 5.0)));
        stackPairs.put("CA", List.of(new AtomAtomLowUp("H6", "H8", 1.8, 5.0), new AtomAtomLowUp("H5", "H8", 1.8, 5.0)));

        stackPairs.put("UU", List.of(new AtomAtomLowUp("H6", "H5", 1.8, 5.0), new AtomAtomLowUp("H6", "H6", 1.8, 5.0)));
        stackPairs.put("UC", List.of(new AtomAtomLowUp("H6", "H5", 1.8, 5.0), new AtomAtomLowUp("H6", "H6", 1.8, 5.0)));
        stackPairs.put("UG", List.of(new AtomAtomLowUp("H6", "H8", 1.8, 5.0), new AtomAtomLowUp("H5", "H8", 1.8, 5.0)));
        stackPairs.put("UA", List.of(new AtomAtomLowUp("H6", "H8", 1.8, 5.0), new AtomAtomLowUp("H5", "H8", 1.8, 5.0)));

        stackPairs.put("GU", List.of(new AtomAtomLowUp("H8", "H6", 1.8, 5.0), new AtomAtomLowUp("H8", "H5", 1.8, 5.0)));
        stackPairs.put("GC", List.of(new AtomAtomLowUp("H8", "H6", 1.8, 5.0), new AtomAtomLowUp("H8", "H5", 1.8, 5.0)));
        stackPairs.put("GG", List.of(new AtomAtomLowUp("H8", "H8", 1.8, 5.0)));
        stackPairs.put("GA", List.of(new AtomAtomLowUp("H8", "H8", 1.8, 5.0)));

        stackPairs.put("AU", List.of(new AtomAtomLowUp("H8", "H6", 1.8, 5.0), new AtomAtomLowUp("H8", "H5", 1.8, 5.0), new AtomAtomLowUp("H2", "H1'", 1.8, 5.0)));
        stackPairs.put("AC", List.of(new AtomAtomLowUp("H8", "H6", 1.8, 5.0), new AtomAtomLowUp("H8", "H5", 1.8, 5.0), new AtomAtomLowUp("H2", "H1'", 1.8, 5.0)));
        stackPairs.put("AG", List.of(new AtomAtomLowUp("H8", "H8", 1.8, 5.0), new AtomAtomLowUp("H2", "H1'", 1.8, 5.0)));
        stackPairs.put("AA", List.of(new AtomAtomLowUp("H8", "H8", 1.8, 5.0), new AtomAtomLowUp("H2", "H1'", 1.8, 5.0)));
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
                addRNALinker(molecule, startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle);
            } else {
                molecule.createLinker(startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle);
            }
        }
    }

    static Atom getConnectorAtom(Molecule molecule, String connectorName, boolean first) {
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

    public static List<Atom[]> readRNALinkerDict(Molecule molecule, List<Map<String, Object>> rnaLinkerDict, boolean formLinks) throws IllegalArgumentException {
        List<Atom[]> linkAtoms = new ArrayList<>();
        for (var connections : rnaLinkerDict) {
            if (connections.containsKey("connect")) {
                List<String> connectorNames = (List<String>) connections.get("connect");
                Atom[] atoms = new Atom[2];
                if (connectorNames.size() != 2) {
                    throw new IllegalArgumentException("Should be two elemens in connection");
                }
                atoms[0] = getConnectorAtom(molecule, connectorNames.get(0), true);
                atoms[1] = getConnectorAtom(molecule, connectorNames.get(0), false);
                Entity poly0 = atoms[0].getTopEntity();
                Entity poly1 = atoms[1].getTopEntity();
                linkAtoms.add(atoms);
                if (formLinks) {
                    int nLinks = (Integer) connections.getOrDefault("n", 6);
                    double linkLen = (Double) connections.getOrDefault("length", 5.0);
                    double valAngle = (Double) connections.getOrDefault("valAngle", 110.0);
                    double dihAngle = (Double) connections.getOrDefault("dihAngle", 135.0);

                    if (poly0 == poly1) {
                        breakBond(atoms[1].getParent(), atoms[1]);
                        addRNALinker(molecule, atoms[0], atoms[1], nLinks, linkLen, valAngle, dihAngle);
                    }
                }
            }
        }
        return linkAtoms;

    }

    static void addRNALinker(Molecule molecule, Atom startAtom, Atom endAtom, int nLinks, double linkLen, double valAngle, double dihAngle) {
        if (endAtom.getName().equals("P")) {
            addRNALinkerTurn(molecule, startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle);
        } else {
            addRNALinkerHelix(molecule, startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle);
        }
    }

    static void addRNALinkerHelix(Molecule molecule, Atom startAtom, Atom endAtom, int nLinks, double linkLen, double valAngle, double dihAngle) {
        String[] linkNames = new String[nLinks + 3];
        double[] linkLens = new double[nLinks + 3];
        double[] valAngles = new double[nLinks + 3];
        Arrays.fill(linkLens, linkLen);
        Arrays.fill(valAngles, valAngle);

        int n = linkLens.length;
        linkLens[n - 3] = 1.41;
        linkLens[n - 2] = 1.60;
        linkLens[n - 1] = 1.59;
        valAngles[n - 3] = 112.21;
        valAngles[n - 2] = 120.58;
        valAngles[n - 1] = 104.38;
        for (int i = 0; i < nLinks; i++) {
            linkNames[i] = "X" + (i + 1);
        }
        linkNames[n - 3] = "XC3'";
        linkNames[n - 2] = "XO3'";
        linkNames[n - 1] = "XP";
        molecule.createLinker(startAtom, endAtom, linkLens, valAngles, linkNames, dihAngle);
    }

    static void addRNALinkerTurn(Molecule molecule, Atom startAtom, Atom endAtom, int nLinks, double linkLen, double valAngle, double dihAngle) {
        String[] linkNames = new String[nLinks + 3];
        double[] linkLens = new double[nLinks + 3];
        double[] valAngles = new double[nLinks + 3];
        Arrays.fill(linkLens, linkLen);
        Arrays.fill(valAngles, valAngle);

        int n = linkLens.length;
        linkLens[n - 2] = 1.52;
        linkLens[n - 1] = 1.41;
        valAngles[n - 2] = 116.47;
        valAngles[n - 1] = 112.21;
        for (int i = 0; i < nLinks; i++) {
            linkNames[i] = "X" + (i + 1);
        }
        linkNames[n - 3] = "XC4'";
        linkNames[n - 2] = "XC3'";
        linkNames[n - 1] = "XO3'";
        Atom oParent1 = endAtom.getParent();
        Atom oParent2 = oParent1.getParent();
        Atom oParent3 = oParent2.getParent();
        List<Atom> newAtoms = molecule.createLinker(startAtom, endAtom, linkLens, valAngles, linkNames, dihAngle);

        String atomName1 = newAtoms.reversed().get(0).getFullName();
        String atomName2 = oParent1.getFullName();
        addDistanceConstraint(atomName1, atomName2, 0.0, 0.01, true);
        atomName1 = newAtoms.reversed().get(1).getFullName();
        atomName2 = oParent2.getFullName();
        addDistanceConstraint(atomName1, atomName2, 0.0, 0.01, true);
        atomName1 = newAtoms.reversed().get(2).getFullName();
        atomName2 = oParent3.getFullName();
        addDistanceConstraint(atomName1, atomName2, 0.0, 0.01, true);
    }

    static void addDistanceConstraint(List<String> atomNames1, List<String> atomNames2, double lower, double upper, boolean bond) {
        MolecularConstraints.addDistanceConstraint(atomNames1, atomNames2, lower, upper, bond);
    }

    static void addDistanceConstraint(String atomName1, String atomName2, double lower, double upper, boolean bond) {
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

    static void addAngleConstraint(Molecule molecule, AngleConstraint angleConstraint) throws InvalidMoleculeException {
        getAngleConstraintSet(molecule).add(angleConstraint);
    }

    static void addSuiteBoundary(Polymer polymer, String residueNum, String suiteName, double mul) throws InvalidMoleculeException {
        List<AngleConstraint> angleConstraints = RNARotamer.getAngleBoundaries(polymer, residueNum, suiteName, mul);
        Molecule molecule = (Molecule) polymer.molecule;
        for (AngleConstraint angleConstraint : angleConstraints) {
            addAngleConstraint(molecule, angleConstraint);
        }
    }

    static String getAtomName(Residue residue, String aName) {
        return residue.getPolymer().getName() + ':' + residue.getNumber() + '.' + aName;

    }

    static void addBasePair(Residue residueI, Residue residueJ, int bpType, boolean addPlanarity) {
        String resNameI = residueI.getName();
        String resNameJ = residueJ.getName();
        var basePairs = AllBasePairs.getBasePair(bpType, resNameI, resNameJ);
        if (basePairs == null) {
            return;
        }
        for (var bp : basePairs.getBPConstraints()) {
            double lowAtomAtomDis = bp.getLower();
            double atomAtomDis = bp.getUpper();
            double lowAtomParentDis = bp.getLowerHeavy();
            double atomParentDis = bp.getUpperHeavy();
            List<String> atom1Names = new ArrayList<>();
            List<String> atom2Names = new ArrayList<>();
            var allAtomNames = bp.getAtomNames();
            for (var atomNames : allAtomNames) {
                String atom1Name = getAtomName(residueI, atomNames[0]);
                String atom2Name = getAtomName(residueJ, atomNames[1]);
                atom1Names.add(atom1Name);
                atom2Names.add(atom2Name);
            }
            String atomI = allAtomNames[0][0];
            String atomJ = allAtomNames[0][1];
            if (atomI.startsWith("H")) {
                Atom parentAtom = residueI.getAtom(atomI).parent;
                String parentAtomName = parentAtom.getFullName();
                addDistanceConstraint(parentAtomName, atom2Names.get(0), lowAtomParentDis, atomParentDis, false);
            } else if (atomJ.startsWith("H")) {
                Atom parentAtom = residueJ.getAtom(atomJ).parent;
                String parentAtomName = parentAtom.getFullName();
                addDistanceConstraint(parentAtomName, atom1Names.get(0), lowAtomParentDis, atomParentDis, false);
            }
            addDistanceConstraint(atom1Names, atom2Names, lowAtomAtomDis, atomAtomDis, false);
        }
        if (bpType == 1) {
            Atom atomPI = residueI.getAtom("P");
            Atom atomPJ = residueJ.getAtom("P");
            if ((atomPI != null) && (atomPJ != null)) {
                String atomPIName = getAtomName(residueI, "P");
                String atomPJName = getAtomName(residueJ, "P");
                addDistanceConstraint(atomPIName, atomPJName, 14.0, 20.0, false);
            }
            if (addPlanarity) {
                String bpRes = resNameI + resNameJ;
                if (rnaPlanarity.containsKey(bpRes)) {
                    var planeValues = rnaPlanarity.get(bpRes);
                    for (var planeValue : planeValues) {
                        String atomIName = getAtomName(residueI, planeValue.aName1);
                        String atomJName = getAtomName(residueJ, planeValue.aName2);
                        addDistanceConstraint(atomIName, atomJName, 0.0, planeValue.distance, false);
                    }
                }
            }
        }
    }

    public static void addStack(AtomAtomLowUp atomAtomLowUp, Polymer polyI, String resNumI) {
        double lowerIntra = atomAtomLowUp.lower - 1.0;
        String atomNameI = polyI.getName() + ':' + resNumI + '.' + atomAtomLowUp.aName1;
        String atomNameJ = polyI.getName() + ':' + resNumI + '.' + atomAtomLowUp.aName2;
        addDistanceConstraint(atomNameI, atomNameJ, lowerIntra, atomAtomLowUp.lower, false);
    }

    public static void addStack(AtomAtomLowUp atomAtomLowUp, Polymer polyI, Polymer polyJ, String resNumI, String resNumJ) {
        double lowerInter = atomAtomLowUp.upper - 1.0;
        String atomNameI = polyI.getName() + ':' + resNumI + '.' + atomAtomLowUp.aName1;
        String atomNameJ = polyJ.getName() + ':' + resNumJ + '.' + atomAtomLowUp.aName2;
        addDistanceConstraint(atomNameI, atomNameJ, lowerInter, atomAtomLowUp.upper, false);
    }

    public static void addStackPair(AtomAtomLowUp atomAtomLowUp, Polymer polyI, Polymer polyJ, String resNumI, String resNumJ) {
        String atomNameI = polyI.getName() + ':' + resNumI + '.' + atomAtomLowUp.aName1;
        String atomNameJ = polyJ.getName() + ':' + resNumJ + '.' + atomAtomLowUp.aName2;
        addDistanceConstraint(atomNameI, atomNameJ, atomAtomLowUp.lower, atomAtomLowUp.upper, false);
    }

    public static void addStackPair(Residue resI, Residue resJ) {
        String resNameI = resI.getName();
        String resNameJ = resJ.getName();
        String resNumI = resI.getNumber();
        String resNumJ = resJ.getNumber();
        Polymer polyI = resI.getPolymer();
        Polymer polyJ = resJ.getPolymer();
        if (polyI != polyJ) {
            return;
        }
        for (AtomAtomLowUp atomAtomLowUp : stackTo.get(resNameI)) {
            addStack(atomAtomLowUp, polyI, resNumI);
        }
        for (AtomAtomLowUp atomAtomLowUp : stackTo.get(resNameJ)) {
            addStack(atomAtomLowUp, polyI, polyJ, resNumI, resNumJ);
        }
        for (AtomAtomLowUp atomAtomLowUp : stackPairs.get(resNameI + resNameJ)) {
            addStackPair(atomAtomLowUp, polyI, polyJ, resNumI, resNumJ);
        }
    }


    public static void addHelix(List<Residue> helixResidues) throws InvalidMoleculeException {
        int nRes = helixResidues.size() / 2;
        for (int i = 0; i < nRes; i++) {
            Residue resI = helixResidues.get(i * 2);
            Residue resJ = helixResidues.get(i * 2 + 1);
            String resINum = resI.getNumber();
            String resJNum = resJ.getNumber();
            Polymer polymerI = resI.getPolymer();
            Polymer polymerJ = resJ.getPolymer();
            addSuiteBoundary(polymerI, resINum, "1a", 0.5);
            addSuiteBoundary(polymerJ, resJNum, "1a", 0.5);
            addBasePair(resI, resJ, 1, false);

            if ((i + 1) < nRes) {
                Residue resINext = helixResidues.get((i + 1) * 2);
                Residue resJPrev = helixResidues.get((i + 1) * 2 + 1);
                //make sure we 're not in bulge before adding stack
                if ((resINext.getPrevious() == resI) && (resJPrev.getNext() == resJ)) {
                    addStackPair(resI, resINext);
                    addStackPair(resJPrev, resJ);
                    String resJName = resJ.getName();
                    if (resJName == "A") {
                        String atomNameI = getAtomName(resINext, "H1'");
                        String atomNameJ = getAtomName(resJ, "H2");
                        addDistanceConstraint(atomNameI, atomNameJ, 1.8, 5.0, false);
                    }
                }
            }
        }
    }

    public static void addHelixPP(List<Residue> helixResidues) throws InvalidMoleculeException {
        int nRes = helixResidues.size() / 2;
        for (int i = 0; i < nRes; i++) {
            Residue resI = helixResidues.get(i * 2);
            Residue resJ = helixResidues.get(i * 2 + 1);
            if ((i + 3) < nRes) {
                Residue resI3 = helixResidues.get((i + 3) * 2);
                if ((resI.getAtom("P") != null) && (resI3.getAtom("P") != null)) {
                    String atomNameI = getAtomName(resI, "P");
                    String atomNameI3 = getAtomName(resI3, "P");
                    addDistanceConstraint(atomNameI, atomNameI3, 16.5, 20.0, false);
                }
                Residue resJ3 = helixResidues.get((i + 3) * 2 + 1);
                if ((resJ3.getAtom("P") != null) && (resJ3.getAtom("P") != null)) {
                    String atomNameJ = getAtomName(resJ, "P");
                    String atomNameJ3 = getAtomName(resJ3, "P");
                    addDistanceConstraint(atomNameJ, atomNameJ3, 16.5, 20.0, false);
                }
                if ((i + 5) < nRes) {
                    Residue resJ5 = helixResidues.get((i + 5) * 2 + 1);
                    if ((resI.getAtom("P") != null) && (resJ5.getAtom("P") != null)) {
                        String atomNameI = getAtomName(resI, "P");
                        String atomNameJ5 = getAtomName(resJ5, "P");
                        addDistanceConstraint(atomNameI, atomNameJ5, 10, 12.0, false);
                    }
                }
            }
        }
    }

    private static void addSuiteBoundaries(List<Residue> loopResidues, RNALoops rnaLoops) {
        String[] suites = rnaLoops.getSuites();
        for (int i = 0; i < suites.length; i++) {
            if (!suites[i].equals("..")) {
                Residue residue = loopResidues.get(i);
                try {
                    addSuiteBoundary(residue.getPolymer(), residue.getNumber(), suites[i], 0.5);
                } catch (InvalidMoleculeException e) {
                }
            }
        }
    }

    private static void addBasePairs(List<Residue> loopResidues, RNALoops rnaLoops) {
        for (int[] bp : rnaLoops.getBasePairs()) {
            Residue residue0 = loopResidues.get(bp[0] - 1);
            Residue residue1 = loopResidues.get(bp[1] - 1);
            addBasePair(residue0, residue1, 1, false);
        }
    }

    public static void addHelicesRestraints(Molecule molecule, SSGen ssGen) throws InvalidMoleculeException {
        for (var ss : ssGen.structures()) {
            if (ss.getName().equals("Helix")) {
                List<Residue> residues = ss.getResidues();
                addHelix(residues);
                addHelixPP(residues);
            }
        }
        for (var ss : ssGen.structures()) {
            if (ss instanceof Loop loop) {
                List<Residue> loopResidues = loop.getResidues();
                if (loopResidues.size() == 4) {
                    loopResidues.add(0, loopResidues.get(0).getPrevious());
                    loopResidues.add(loopResidues.reversed().get(0).getNext());
                    StringBuilder stringBuilder = new StringBuilder();
                    for (Residue residue : loopResidues) {
                        stringBuilder.append(residue.getName());
                    }
                    var rnaLoopsOptional = RNALoops.getRNALoop(stringBuilder.toString());
                    rnaLoopsOptional.ifPresent(rnaLoops -> {
                        addSuiteBoundaries(loopResidues, rnaLoops);
                        addBasePairs(loopResidues, rnaLoops);
                    });
                }
            }
        }
    }
}
