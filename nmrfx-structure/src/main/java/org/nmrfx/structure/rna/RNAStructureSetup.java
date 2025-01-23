package org.nmrfx.structure.rna;

import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.AngleConstraint;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.nmrfx.structure.chemistry.energy.ConstraintCreator.addDistanceConstraint;

public class RNAStructureSetup {

    private static Map<Integer, String> angleKeyMap = new HashMap<>();
    public static boolean dumpKeys = true;

    private static boolean usePlanarity = true;
    private static final Map<String, Map<String, Double>> angleDict = new HashMap<>();
    public static Map<String, List<AtomAtomDistance>> rnaPlanarity = new HashMap<>();
    public static Map<String, List<AtomAtomLowUp>> stackTo = new HashMap<>();
    public static Map<String, List<AtomAtomLowUp>> stackPairs = new HashMap<>();

    public record AtomAtomDistance(String aName1, String aName2, double distance) {
    }

    public record AtomAtomLowUp(String aName1, String aName2, double lower, double upper) {
    }

    static {
        var gcList = List.of(
                new AtomAtomDistance("C6p", "C4", 0.5),
                new AtomAtomDistance("C2", "C2p", 0.2)
        );
        var cgList = List.of(
                new AtomAtomDistance("C4", "C6p", 0.5),
                new AtomAtomDistance("C2p", "C2", 0.2)
        );

        RNAStructureSetup.rnaPlanarity.put("GC", gcList);
        RNAStructureSetup.rnaPlanarity.put("CG", cgList);
        RNAStructureSetup.rnaPlanarity.put("AU", gcList);
        RNAStructureSetup.rnaPlanarity.put("UA", cgList);
    }

    static {
        RNAStructureSetup.stackTo.put("C", List.of(new AtomAtomLowUp("H2'", "H6", 4.0, 2.7), new AtomAtomLowUp("H3'", "H6", 3.0, 3.3)));
        RNAStructureSetup.stackTo.put("U", List.of(new AtomAtomLowUp("H2'", "H6", 4.0, 2.7), new AtomAtomLowUp("H3'", "H6", 3.0, 3.3)));
        RNAStructureSetup.stackTo.put("G", List.of(new AtomAtomLowUp("H2'", "H8", 4.0, 2.7), new AtomAtomLowUp("H3'", "H8", 3.0, 3.3)));
        RNAStructureSetup.stackTo.put("A", List.of(new AtomAtomLowUp("H2'", "H8", 4.0, 2.7), new AtomAtomLowUp("H3'", "H8", 3.0, 3.3)));


        RNAStructureSetup.stackPairs.put("CU", List.of(new AtomAtomLowUp("H6", "H5", 1.8, 5.0), new AtomAtomLowUp("H6", "H6", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("CC", List.of(new AtomAtomLowUp("H6", "H5", 1.8, 5.0), new AtomAtomLowUp("H6", "H6", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("CG", List.of(new AtomAtomLowUp("H6", "H8", 1.8, 5.0), new AtomAtomLowUp("H5", "H8", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("CA", List.of(new AtomAtomLowUp("H6", "H8", 1.8, 5.0), new AtomAtomLowUp("H5", "H8", 1.8, 5.0)));

        RNAStructureSetup.stackPairs.put("UU", List.of(new AtomAtomLowUp("H6", "H5", 1.8, 5.0), new AtomAtomLowUp("H6", "H6", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("UC", List.of(new AtomAtomLowUp("H6", "H5", 1.8, 5.0), new AtomAtomLowUp("H6", "H6", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("UG", List.of(new AtomAtomLowUp("H6", "H8", 1.8, 5.0), new AtomAtomLowUp("H5", "H8", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("UA", List.of(new AtomAtomLowUp("H6", "H8", 1.8, 5.0), new AtomAtomLowUp("H5", "H8", 1.8, 5.0)));

        RNAStructureSetup.stackPairs.put("GU", List.of(new AtomAtomLowUp("H8", "H6", 1.8, 5.0), new AtomAtomLowUp("H8", "H5", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("GC", List.of(new AtomAtomLowUp("H8", "H6", 1.8, 5.0), new AtomAtomLowUp("H8", "H5", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("GG", List.of(new AtomAtomLowUp("H8", "H8", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("GA", List.of(new AtomAtomLowUp("H8", "H8", 1.8, 5.0)));

        RNAStructureSetup.stackPairs.put("AU", List.of(new AtomAtomLowUp("H8", "H6", 1.8, 5.0), new AtomAtomLowUp("H8", "H5", 1.8, 5.0), new AtomAtomLowUp("H2", "H1'", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("AC", List.of(new AtomAtomLowUp("H8", "H6", 1.8, 5.0), new AtomAtomLowUp("H8", "H5", 1.8, 5.0), new AtomAtomLowUp("H2", "H1'", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("AG", List.of(new AtomAtomLowUp("H8", "H8", 1.8, 5.0), new AtomAtomLowUp("H2", "H1'", 1.8, 5.0)));
        RNAStructureSetup.stackPairs.put("AA", List.of(new AtomAtomLowUp("H8", "H8", 1.8, 5.0), new AtomAtomLowUp("H2", "H1'", 1.8, 5.0)));
    }


    public static void setPlanarityUse(boolean state) {
        usePlanarity = state;
    }

    public static boolean getPlanarityUse() {
        return usePlanarity;
    }

    private static void loadAngles() throws IOException {
        InputStream iStream = PropertyGenerator.class.getResourceAsStream("/data/angles.txt");
        angleDict.clear();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            String[] header = null;
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String[] fields = line.split("\t");
                if (fields.length < 6) {
                    continue;
                }
                if (header == null) {
                    header = fields;
                } else {
                    String key = fields[0];
                    Map<String, Double> data = new HashMap<>();
                    for (int i = 1; i < fields.length; i++) {
                        String field = fields[i].trim();
                        String headerAtom = header[i];
                        if (!field.equals("-")) {
                            data.put(headerAtom, Double.parseDouble(field));
                        }
                    }
                    angleDict.put(key, data);
                }
            }
        }
    }

    public static Optional<Map<String, Double>> getAngles(String key) {
        String resP = ":P:";
        String resp = ":p:";
        Optional<Map<String, Double>> result;
        if (angleDict.isEmpty()) {
            try {
                loadAngles();
            } catch (IOException e) {
                result = Optional.empty();
                return result;
            }
        }
        Map<String, Double> angles = angleDict.get(key);
        if (angles == null) {
            String origAtom;
            String newAtom ;
            String origKey;
            String newKey;

            if (key.contains(resP)) {
                origAtom = "N1";
                newAtom = "N9";
                origKey = resP;
                newKey = resp;
            } else {
                origAtom = "N9";
                newAtom = "N1";
                origKey = resp;
                newKey = resP;
            }
            key = key.replace(origKey, newKey);
            angles = angleDict.get(key);
            if (angles != null) {
                Double angle = angles.get(origAtom);
                if (angle != null) {
                    angles.remove(origAtom);
                    angle= angle + 180.0;
                    if (angle > 180.0) {
                        angle -= 360.0;
                    }
                    angles.put(newAtom, angle);
                }
            }
        }
        return Optional.ofNullable(angles);
    }

    public static boolean hasAngles(String key) {
        if (angleDict.isEmpty()) {
            try {
                loadAngles();
            } catch (IOException e) {
                return false;
            }
        }
        return angleDict.containsKey(key);
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
                atoms[0] = ConstraintCreator.getConnectorAtom(molecule, connectorNames.get(0), true);
                atoms[1] = ConstraintCreator.getConnectorAtom(molecule, connectorNames.get(1), false);
                Entity poly0 = atoms[0].getTopEntity();
                Entity poly1 = atoms[1].getTopEntity();
                linkAtoms.add(atoms);
                if (formLinks) {
                    int nLinks = (Integer) connections.getOrDefault("n", 6);
                    double linkLen = (Double) connections.getOrDefault("length", 5.0);
                    double valAngle = (Double) connections.getOrDefault("valAngle", 110.0);
                    double dihAngle = (Double) connections.getOrDefault("dihAngle", 135.0);

                    if (poly0 == poly1) {
                        ConstraintCreator.breakBond(atoms[1].getParent(), atoms[1]);
                        addRNALinker(molecule, atoms[0], atoms[1], nLinks, linkLen, valAngle, dihAngle);
                    }
                }
            }
        }
        return linkAtoms;

    }

    public static void addRNALinker(Molecule molecule, Atom startAtom, Atom endAtom, int nLinks, double linkLen, double valAngle, double dihAngle) {
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
                String atom1Name = ConstraintCreator.getAtomName(residueI, atomNames[0]);
                String atom2Name = ConstraintCreator.getAtomName(residueJ, atomNames[1]);
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
            ConstraintCreator.addDistanceConstraint(atom1Names, atom2Names, lowAtomAtomDis, atomAtomDis, false);
        }
        if (bpType == 1) {
            Atom atomPI = residueI.getAtom("P");
            Atom atomPJ = residueJ.getAtom("P");
            if ((atomPI != null) && (atomPJ != null)) {
                String atomPIName = ConstraintCreator.getAtomName(residueI, "P");
                String atomPJName = ConstraintCreator.getAtomName(residueJ, "P");
                addDistanceConstraint(atomPIName, atomPJName, 14.0, 20.0, false);
            }
            if (addPlanarity) {
                String bpRes = resNameI + resNameJ;
                if (rnaPlanarity.containsKey(bpRes)) {
                    var planeValues = rnaPlanarity.get(bpRes);
                    for (var planeValue : planeValues) {
                        String atomIName = ConstraintCreator.getAtomName(residueI, planeValue.aName1());
                        String atomJName = ConstraintCreator.getAtomName(residueJ, planeValue.aName2());
                        addDistanceConstraint(atomIName, atomJName, 0.0, planeValue.distance(), false);
                    }
                }
            }
        }
    }

    public static void addStack(AtomAtomLowUp atomAtomLowUp, Polymer polyI, String resNumI) {
        double lowerIntra = atomAtomLowUp.lower() - 1.0;
        String atomNameI = polyI.getName() + ':' + resNumI + '.' + atomAtomLowUp.aName1();
        String atomNameJ = polyI.getName() + ':' + resNumI + '.' + atomAtomLowUp.aName2();
        addDistanceConstraint(atomNameI, atomNameJ, lowerIntra, atomAtomLowUp.lower(), false);
    }

    public static void addStack(AtomAtomLowUp atomAtomLowUp, Polymer polyI, Polymer polyJ, String resNumI, String resNumJ) {
        double lowerInter = atomAtomLowUp.upper() - 1.0;
        String atomNameI = polyI.getName() + ':' + resNumI + '.' + atomAtomLowUp.aName1();
        String atomNameJ = polyJ.getName() + ':' + resNumJ + '.' + atomAtomLowUp.aName2();
        addDistanceConstraint(atomNameI, atomNameJ, lowerInter, atomAtomLowUp.upper(), false);
    }

    public static void addStackPair(AtomAtomLowUp atomAtomLowUp, Polymer polyI, Polymer polyJ, String resNumI, String resNumJ) {
        String atomNameI = polyI.getName() + ':' + resNumI + '.' + atomAtomLowUp.aName1();
        String atomNameJ = polyJ.getName() + ':' + resNumJ + '.' + atomAtomLowUp.aName2();
        addDistanceConstraint(atomNameI, atomNameJ, atomAtomLowUp.lower(), atomAtomLowUp.upper(), false);
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

    public static void addHelix(List<Residue> helixResidues, Set<Residue> usedResidues) throws InvalidMoleculeException {
        int nRes = helixResidues.size() / 2;
        for (int i = 0; i < nRes; i++) {
            Residue resI = helixResidues.get(i * 2);
            Residue resJ = helixResidues.get(i * 2 + 1);
            String resINum = resI.getNumber();
            String resJNum = resJ.getNumber();
            Polymer polymerI = resI.getPolymer();
            Polymer polymerJ = resJ.getPolymer();
            if (!usedResidues.contains(resI)) {
                addSuiteBoundary(polymerI, resINum, "1a", 0.5);
                usedResidues.add(resI);
            }
            if (!usedResidues.contains(resJ)) {
                addSuiteBoundary(polymerJ, resJNum, "1a", 0.5);
                usedResidues.add(resJ);
            }
            addBasePair(resI, resJ, 1, false);

            if ((i + 1) < nRes) {
                Residue resINext = helixResidues.get((i + 1) * 2);
                Residue resJPrev = helixResidues.get((i + 1) * 2 + 1);
                //make sure we 're not in bulge before adding stack
                if ((resINext.getPrevious() == resI) && (resJPrev.getNext() == resJ)) {
                    addStackPair(resI, resINext);
                    addStackPair(resJPrev, resJ);
                    String resJName = resJ.getName();
                    if (Objects.equals(resJName, "A")) {
                        String atomNameI = ConstraintCreator.getAtomName(resINext, "H1'");
                        String atomNameJ = ConstraintCreator.getAtomName(resJ, "H2");
                        addDistanceConstraint(atomNameI, atomNameJ, 1.8, 5.0, false);
                    }
                }
            }
        }
    }

    public static void addHelixPP(List<Residue> helixResidues) {
        int nRes = helixResidues.size() / 2;
        for (int i = 0; i < nRes; i++) {
            Residue resI = helixResidues.get(i * 2);
            Residue resJ = helixResidues.get(i * 2 + 1);
            if ((i + 3) < nRes) {
                Residue resI3 = helixResidues.get((i + 3) * 2);
                if ((resI.getAtom("P") != null) && (resI3.getAtom("P") != null)) {
                    String atomNameI = ConstraintCreator.getAtomName(resI, "P");
                    String atomNameI3 = ConstraintCreator.getAtomName(resI3, "P");
                    addDistanceConstraint(atomNameI, atomNameI3, 16.5, 20.0, false);
                }
                Residue resJ3 = helixResidues.get((i + 3) * 2 + 1);
                if ((resJ3.getAtom("P") != null) && (resJ3.getAtom("P") != null)) {
                    String atomNameJ = ConstraintCreator.getAtomName(resJ, "P");
                    String atomNameJ3 = ConstraintCreator.getAtomName(resJ3, "P");
                    addDistanceConstraint(atomNameJ, atomNameJ3, 16.5, 20.0, false);
                }
                if ((i + 5) < nRes) {
                    Residue resJ5 = helixResidues.get((i + 5) * 2 + 1);
                    if ((resI.getAtom("P") != null) && (resJ5.getAtom("P") != null)) {
                        String atomNameI = ConstraintCreator.getAtomName(resI, "P");
                        String atomNameJ5 = ConstraintCreator.getAtomName(resJ5, "P");
                        addDistanceConstraint(atomNameI, atomNameJ5, 10, 12.0, false);
                    }
                }
            }
        }
    }

    private static void addSuiteBoundaries(List<Residue> loopResidues, RNALoops rnaLoops, Set<Residue> usedResidues) {
        String[] suites = rnaLoops.getSuites();
        for (int i = 0; i < suites.length; i++) {
            if (!suites[i].equals("..")) {
                Residue residue = loopResidues.get(i);
                try {
                    if (!usedResidues.contains(residue)) {
                        addSuiteBoundary(residue.getPolymer(), residue.getNumber(), suites[i], 0.5);
                        usedResidues.add(residue);
                    }
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

    static List<Residue> getLoopResidues(List<Residue> residues) {
        List<Residue> loopResidues = new ArrayList<>();
        loopResidues.add(residues.get(0).getPrevious());
        loopResidues.addAll(residues);
        loopResidues.add(residues.getLast().getNext());
        return loopResidues;
    }

    public static void addHelicesRestraints(SSGen ssGen) throws InvalidMoleculeException {
        Set<Residue> usedResidues = new HashSet<>();
        for (var ss : ssGen.structures()) {
            if (ss instanceof Loop loop) {
                List<Residue> residues = loop.getResidues();
                if (residues.size() == 4) {
                    List<Residue> loopResidues = getLoopResidues(residues);
                    String loopResidueNames = getResidueNames(loopResidues);
                    var rnaLoopsOptional = RNALoops.getRNALoop(loopResidueNames);
                    rnaLoopsOptional.ifPresent(rnaLoops -> {
                        addSuiteBoundaries(loopResidues, rnaLoops, usedResidues);
                        addBasePairs(loopResidues, rnaLoops);
                    });
                }
            }
        }
        for (var ss : ssGen.structures()) {
            if (ss.getName().equals("Helix")) {
                List<Residue> residues = ss.getResidues();
                addHelix(residues, usedResidues);
                addHelixPP(residues);
            }
        }
    }

    static void addSuiteBoundary(Polymer polymer, String residueNum, String suiteName, double mul) throws InvalidMoleculeException {
        List<AngleConstraint> angleConstraints = RNARotamer.getAngleBoundaries(polymer, residueNum, suiteName, mul);
        Molecule molecule = (Molecule) polymer.molecule;
        System.out.println("add suite " + residueNum + " " + suiteName);
        for (AngleConstraint angleConstraint : angleConstraints) {
            ConstraintCreator.addAngleConstraint(molecule, angleConstraint);
        }
    }

    static String getPurinePyrimidine(String nucType) {
        if (nucType.equals("A") || nucType.equals("G")) {
            nucType = "P";
        } else {
            nucType = "p";
        }
        return nucType;
    }


    static Residue getHelixResidue(List<Residue> residues, int index, int chain, boolean fromStart) {
        if (fromStart) {
            return residues.get(index * 2 + chain);
        } else {
            return residues.reversed().get(index * 2 + 1 - chain);
        }
    }

    static void setAngles(Residue residue, String key, boolean lockAngles) {
        AtomicBoolean found = new AtomicBoolean(false);
        getAngles(key).ifPresent(anglesToSet -> {
            found.set(true);
            RNARotamer.setDihedrals(residue, anglesToSet, 0.0, lockAngles);
        });
        key = found.get() ? "PRESENT:" + key : "ABSENT:" + key;
        angleKeyMap.put(residue.getResNum(), key);
    }


    static void setAnglesLoop(Loop loop, boolean lockLoop) {
        int iLoop = 0;
        for (Residue residue : loop.getResidues()) {
            String subType = getRNAResType(residue);
            String nucType = residue.getName();
            nucType = getPurinePyrimidine(nucType);
            String key = "Loop" + ':' + iLoop + ':' + nucType + ':' + subType;
            setAngles(residue, key, lockLoop);
            iLoop++;
        }

    }

    static void setAnglesInternalLoop(InternalLoop loop, boolean lockLoop) {
        int iLoop = 0;
        for (Residue residue : loop.getResidues()) {
            String subType = getRNAResType(residue);
            String nucType = residue.getName();
            nucType = getPurinePyrimidine(nucType);
            String key = "ILoop" + ':' + iLoop + ':' + nucType + ':' + subType;
            setAngles(residue, key, lockLoop);
            iLoop++;
        }

    }

    static void setAnglesJunction(Junction junction, boolean lockLoop) {
        int iLoop = 0;
        for (Residue residue : junction.getResidues()) {
            String subType = getRNAResType(residue);
            String nucType = residue.getName();
            nucType = getPurinePyrimidine(nucType);
            String key = "Junction" + ':' + iLoop + ':' + nucType + ':' + subType;
            setAngles(residue, key, lockLoop);
            iLoop++;
        }

    }

    static void setAnglesBulge(Bulge bulge, boolean lockBulge) {
        int iLoop = 0;
        for (Residue residue : bulge.getResidues()) {
            String subType = getRNAResType(residue);
            String nucType = residue.getName();
            nucType = getPurinePyrimidine(nucType);
            String key = "Bulge" + ':' + iLoop + ':' + nucType + ':' + subType;
            setAngles(residue, key, lockBulge);
            iLoop++;
        }
    }


    static void setAnglesHelix(RNAHelix rnaHelix, boolean lockFirst, boolean lockLast, boolean lockLoop, boolean doLock) {
        List<Residue> residues = rnaHelix.getResidues();
        for (Residue residue : residues) {
            String subType = getRNAResType(residue);

            boolean firstResI = residue == getHelixResidue(residues, 0, 0, true);
            boolean firstResJ = residue == getHelixResidue(residues, 0, 1, true);
            boolean lastResI = residue == getHelixResidue(residues, 0, 0, false);
            boolean lastResJ = residue == getHelixResidue(residues, 0, 1, false);
            boolean firstRes = firstResI || firstResJ;
            boolean lastRes = lastResI || lastResJ;
            String nucType = residue.getName();
            nucType = getPurinePyrimidine(nucType);
            String key = "Helix" + ":0:" + nucType + ":" + subType;
            if (getAngles(key).isPresent()) {
                boolean lock = doLock;
                if (firstRes && !lockFirst) {
                    lock = false;
                }
                if (lastRes && !lockLast) {
                    lock = false;
                }
                if ((lastRes && (subType.contains("hL") || subType.contains("hl")) && lockLoop)) {
                    lock = true;
                }
                setAngles(residue, key, lock);
            } else {
                //  subType = subType.charAt(subType.length() - 2) == 'X' ? "hL:GNRAXe" : "hl:GNRAxe";
                String key2 = "Helix" + ":0:" + nucType + ":" + subType;
                boolean lock = lastRes && lockLoop;
                setAngles(residue, key2, lock);
            }
        }
    }

    static String getResidueNames(List<Residue> residues) {
        StringBuilder stringBuilder = new StringBuilder();
        residues.forEach(residue -> stringBuilder.append(residue.getName()));
        return stringBuilder.toString();
    }

    static String getLoopType(SecondaryStructure ss) {
        if (ss instanceof Loop) {
            List<Residue> residues = ss.getResidues();
            if (residues.size() == 4) {
                List<Residue> loopResidues = getLoopResidues(residues);
                String loopSeq = getResidueNames(loopResidues);
                var rnaLoopsOptional = RNALoops.getRNALoop(loopSeq);

                return rnaLoopsOptional.map(rnaLoops -> ":" + rnaLoops.name).orElseGet(() -> String.valueOf(residues.size()));
            }
        }
        return "";
    }

    static String getJunctionType(Junction junction) {
        List<Integer> loopSizes = junction.getLoopSizes();
        StringBuilder result = new StringBuilder(String.valueOf(loopSizes.get(0)));

        for (int i = 1; i < loopSizes.size(); i++) {
            result.append("_").append(loopSizes.get(i));
        }
        return result.toString();
    }

    static String getHelixSubType(List<Residue> residues, Residue residue, SecondaryStructure ssNext, SecondaryStructure ssPrev, SecondaryStructure ssNext2) {
        Residue nextRes = residue.getNext();
        Residue prevRes = residue.getPrevious();
        Residue pairRes = residue.pairedTo;

        boolean lastRes2I = residue == getHelixResidue(residues, 1, 0, false);
        boolean lastRes2J = residue == getHelixResidue(residues, 1, 1, false);

        String subType = "h";
        String ssNextType;
        String ssPrevType;
        if (lastRes2I) {
            subType = "hE";
        } else if (lastRes2J) {
            subType = "he";
        } else {
            if (prevRes == null) {
                subType = "h5";
            } else if (ssNext instanceof Loop) {
                ssNextType = getLoopType(ssNext);
                subType = "hL" + ssNextType;
            } else if (ssNext2 instanceof Loop) {
                ssNextType = getLoopType(ssNext2);
                subType = "hL" + ssNextType;
            } else if (ssPrev instanceof Loop) {
                ssPrevType = getLoopType(ssPrev);
                subType = "hl" + ssPrevType;
            } else if (ssNext2 instanceof InternalLoop internalLoop) {
                ssNextType = getJunctionType(internalLoop);
                subType = "hI" + ssNextType;
            } else if (ssPrev instanceof InternalLoop internalLoop) {
                ssPrevType = getJunctionType(internalLoop);
                subType = "hi" + ssPrevType;
            } else if (ssNext instanceof Bulge) {
                subType = "hB0" + ":" + 'B' + nextRes.getSecondaryStructure().getResidues().size();
            } else if (ssPrev instanceof Bulge) {
                subType = "hB1" + ":" + 'B' + prevRes.getSecondaryStructure().getResidues().size();
            } else if (pairRes.getPrevious() != null) {
                if (pairRes.getPrevious().getSecondaryStructure() instanceof Bulge) {
                    subType = "hb1" + ":" + 'B' + pairRes.getPrevious().getSecondaryStructure().getResidues().size();
                    if (pairRes.getNext() != null) {
                        if (pairRes.getNext().getSecondaryStructure() instanceof Bulge) {
                            subType = "hb0" + ":" + 'B' + pairRes.getNext().getSecondaryStructure().getResidues().size();
                        }
                    }
                }
            }
        }
        return subType;
    }

    static int getHelixPos(RNAHelix rnaHelix, Residue residue, boolean fromStart) {
        int index = rnaHelix.getResidues().indexOf(residue) / 2;
        int length = rnaHelix.getResidues().size() / 2;
        if (!fromStart) {
            index = length - index - 1;
        }
        return index;
    }

    public static String getRNAResType(Residue residue) {
        SecondaryStructure ss = residue.getSecondaryStructure();
        String subType = "c";
        Residue nextRes = residue.getNext();
        Residue prevRes = residue.getPrevious();
        SecondaryStructure ssNext = nextRes != null ? nextRes.getSecondaryStructure() : null;
        SecondaryStructure ssPrev = null;
        if (prevRes != null) {
            ssPrev = prevRes.getSecondaryStructure();
            if (ssNext instanceof Loop) {
                String ssNextType = getLoopType(ssNext);
                subType = "hL:" + ssNextType;
            }
        }
        if (ss instanceof RNAHelix rnaHelix) {
            int index = getHelixPos(rnaHelix, residue, false);
            SecondaryStructure ssNext2 = null;
            if (index == 1) {
                ssNext2 = nextRes.getNext().getSecondaryStructure();
            }
            subType = getHelixSubType(ss.getResidues(), residue, ssNext, ssPrev, ssNext2);
        } else if (ss instanceof Loop) {
            subType = "T" + getLoopType(ss);
        } else if (ss instanceof Bulge) {
            subType = "B" + ss.getResidues().size();
            // check whether res num is greater than paired res num, to determine orientation of bulge
            Residue prevResidue = ss.getResidues().get(0).getPrevious();
            if (prevResidue.getResNum() > prevResidue.pairedTo.getResNum()) {
                subType = subType + "c";
            }
        } else if (ss instanceof InternalLoop internalLoop) {
            subType = "I" + getJunctionType(internalLoop);
            Residue prevResidue = ss.getResidues().get(0).getPrevious();
            if (prevResidue.getResNum() > prevResidue.pairedTo.getResNum()) {
                subType = subType + "c";
            }
        } else if (ss instanceof Junction junction) {
            subType = "J" + getJunctionType(junction);
        }
        Residue pairRes = residue.pairedTo;
        if (pairRes != null) {
            if (pairRes.getAtom("X1") != null) {
                if (pairRes == ss.getResidues().get(0)) {
                    subType = subType + "xb";
                } else {
                    subType = subType + "xe";
                }

            }
        }
        if (residue.getAtom("X1") != null) {
            if (residue == ss.getResidues().get(0)) {
                subType = subType + "Xb";
            } else {
                subType = subType + "Xe";
            }
        }

        return subType;
    }

    public static void setAnglesVienna(Map<String, Object> data, SSGen ssGen) {
        try {
            boolean doLock = (Boolean) data.getOrDefault("restrain", false);
            boolean lockFirst = (Boolean) data.getOrDefault("lockfirst", false);
            boolean lockLast = (Boolean) data.getOrDefault("locklast", false);
            boolean lockBulge = (Boolean) data.getOrDefault("lockbulge", doLock);
            boolean lockLoop = (Boolean) data.getOrDefault("lockloop", doLock);

            for (SecondaryStructure ss : ssGen.structures()) {
                if (ss instanceof RNAHelix rnaHelix) {
                    setAnglesHelix(rnaHelix, lockFirst, lockLast, lockLoop, doLock);
                } else if (ss instanceof Loop loop) {
                    setAnglesLoop(loop, lockLoop);
                } else if (ss instanceof Bulge bulge) {
                    setAnglesBulge(bulge, lockBulge);
                } else if (ss instanceof InternalLoop loop) {
                    setAnglesInternalLoop(loop, lockBulge);
                } else if (ss instanceof Junction junction) {
                    setAnglesJunction(junction, lockBulge);
                }
            }
            if (dumpKeys) {
                Molecule molecule = (Molecule) ssGen.structures().getFirst().firstResidue().molecule;
                String dotBracket = molecule.getDotBracket();

                angleKeyMap.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).forEach(entry -> {
                    int iRes = entry.getKey();
                    System.out.printf("%3d %c %s\n", iRes, dotBracket.charAt(iRes - 1), entry.getValue());
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<Integer, String> getAngleKeyMap() {
        return angleKeyMap;
    }

    record LinksLink(Atom atomI, Atom atomJ) {

    }

    record LinksBond(Atom atomI, Atom atomJ, String mode) {

    }

    public record StructureLinksBonds(List<StructureLink> links, List<StructureBond> bonds) {
    }

    public static StructureLinksBonds findSSLinks(SSGen ssGen) {
        List<StructureLink> links = new ArrayList<>();
        List<StructureBond> bonds = new ArrayList<>();
        for (SecondaryStructure secondaryStructure : ssGen.structures()) {
            if (secondaryStructure instanceof RNAHelix rnaHelix) {
                List<Residue> residues = rnaHelix.getResidues();
                Residue residueI = getHelixResidue(residues, 0, 0, true);
                Residue residueJ = getHelixResidue(residues, 0, 1, true);
                if (residueI.getPrevious() != null) {
                    Atom atomI = residueI.getAtom("H3'");
                    Atom atomJ = residueJ.getAtom("P");
                    StructureLink structureLink = new StructureLink(atomI, atomJ, true);
                    links.add(structureLink);
                    StructureBond structureBond = new StructureBond(atomJ.getParent(), atomJ, "float");
                    bonds.add(structureBond);
                }

                residueI = getHelixResidue(residues, 1, 0, false);
                residueJ = getHelixResidue(residues, 1, 1, false);
                Atom atomI = residueI.getAtom("H3'");
                Atom atomJ = residueJ.getAtom("P");
                StructureLink structureLink = new StructureLink(atomI, atomJ, true);
                links.add(structureLink);
                StructureBond structureBond = new StructureBond(atomJ.getParent(), atomJ, "float");
                bonds.add(structureBond);
            }
        }
        return new StructureLinksBonds(links, bonds);
    }
}
