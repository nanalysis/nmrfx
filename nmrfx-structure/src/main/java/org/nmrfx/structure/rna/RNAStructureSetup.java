package org.nmrfx.structure.rna;

import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.AngleConstraint;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.ConstraintCreator;
import org.nmrfx.structure.chemistry.energy.PropertyGenerator;
import org.nmrfx.structure.chemistry.energy.RNARotamer;

import java.io.*;
import java.util.*;

import static org.nmrfx.structure.chemistry.energy.ConstraintCreator.addDistanceConstraint;

public class RNAStructureSetup {

    private static Map<Integer, String> angleKeyList = new HashMap<>();
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
                String[] fields = line.split(" +");
                if (fields.length < 6) {
                    continue;
                }
                if (header == null) {
                    header = fields;
                } else {
                    String ssType = fields[1];
                    String posType = fields[2];
                    String nucType = fields[3];
                    String subType = fields[4];
                    Map<String, Double> data = new HashMap<>();
                    for (int i = 5; i < fields.length; i++) {
                        String field = fields[i].trim();
                        String headerAtom = header[i];
                        if (!field.equals("-")) {
                            data.put(headerAtom, Double.parseDouble(field));
                        }
                    }
                    String key = ssType + ':' + posType + ':' + nucType + ':' + subType;
                    angleDict.put(key, data);
                }
            }
        }
    }

    public static Optional<Map<String, Double>> getAngles(String key) {
        Optional<Map<String, Double>> result;
        if (angleDict.isEmpty()) {
            try {
                loadAngles();
            } catch (IOException e) {
                result = Optional.empty();
                return result;
            }
        }
        result = Optional.ofNullable(angleDict.get(key));
        return result;
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
                atoms[1] = ConstraintCreator.getConnectorAtom(molecule, connectorNames.get(0), false);
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

    static List<Residue> getLoopResidues(List<Residue> residues) {
        List<Residue> loopResidues = new ArrayList<>();
        loopResidues.add(residues.get(0).getPrevious());
        loopResidues.addAll(residues);
        loopResidues.add(residues.getLast().getNext());
        return loopResidues;
    }

    public static void addHelicesRestraints(SSGen ssGen) throws InvalidMoleculeException {
        for (var ss : ssGen.structures()) {
            if (ss.getName().equals("Helix")) {
                List<Residue> residues = ss.getResidues();
                addHelix(residues);
                addHelixPP(residues);
            }
        }
        for (var ss : ssGen.structures()) {
            if (ss instanceof Loop loop) {
                List<Residue> residues = loop.getResidues();
                if (residues.size() == 4) {
                    List<Residue> loopResidues = getLoopResidues(residues);
                    String loopResidueNames = getResidueNames(loopResidues);
                    var rnaLoopsOptional = RNALoops.getRNALoop(loopResidueNames);
                    rnaLoopsOptional.ifPresent(rnaLoops -> {
                        addSuiteBoundaries(loopResidues, rnaLoops);
                        addBasePairs(loopResidues, rnaLoops);
                    });
                }
            }
        }
    }

    static void addSuiteBoundary(Polymer polymer, String residueNum, String suiteName, double mul) throws InvalidMoleculeException {
        List<AngleConstraint> angleConstraints = RNARotamer.getAngleBoundaries(polymer, residueNum, suiteName, mul);
        Molecule molecule = (Molecule) polymer.molecule;
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

    static void setAnglesLoop(Loop loop, boolean lockLoop) {
        int iLoop = 0;
        for (Residue residue : loop.getResidues()) {
            String subType = getRNAResType(residue);
            String nucType = residue.getName();
            nucType = getPurinePyrimidine(nucType);
            String key = "Loop" + ':' + iLoop + ':' + nucType + ':' + subType;
            getAngles(key).ifPresent(anglesToSet -> {
                angleKeyList.put(residue.getResNum(), key);
                RNARotamer.setDihedrals(residue, anglesToSet, 0.0, lockLoop);
            });
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
            getAngles(key).ifPresent(anglesToSet -> {
                angleKeyList.put(residue.getResNum(), key);
                RNARotamer.setDihedrals(residue, anglesToSet, 0.0, lockBulge);
            });
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
                var anglesToSet = getAngles(key).get();
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
                angleKeyList.put(residue.getResNum(), key);
                RNARotamer.setDihedrals(residue, anglesToSet, 0.0, lock);
            } else {
                subType = subType.charAt(subType.length() - 2) == 'X' ? "hL:GNRAXe" : "hl:GNRAxe";
                String key2 = "Helix" + ":0:" + nucType + ":" + subType;
                getAngles(key).ifPresent(anglesToSet -> {
                    boolean lock = lastRes && lockLoop;
                    angleKeyList.put(residue.getResNum(), key2);
                    RNARotamer.setDihedrals(residue, anglesToSet, 0.0, lock);
                });
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

    static String getHelixSubType(List<Residue> residues, Residue residue, SecondaryStructure ssNext, SecondaryStructure ssPrev) {
        Residue nextRes = residue.getNext();
        Residue prevRes = residue.getPrevious();
        Residue pairRes = residue.pairedTo;

        boolean lastResI = residue == getHelixResidue(residues, 0, 0, false);
        boolean lastResJ = residue == getHelixResidue(residues, 0, 1, false);

        String subType = "h";
        String ssNextType;
        String ssPrevType;
        if (lastResI) {
            subType = "hE";
        } else if (lastResJ) {
            subType = "he";
        } else {
            if (prevRes == null) {
                subType = "h5";
            } else if (ssNext instanceof Loop) {
                ssNextType = getLoopType(ssNext);
                subType = "hL" + ssNextType;
            } else if (ssPrev instanceof Loop) {
                ssPrevType = getLoopType(ssPrev);
                subType = "hl" + ssPrevType;
            } else if (ssNext instanceof Bulge) {
                subType = "hB0" + ":" + 'B' + nextRes.getSecondaryStructure().getResidues().size();
            } else if (ssPrev instanceof Bulge) {
                subType = "hB1" + ":" + 'B' + prevRes.getSecondaryStructure().getResidues().size();
                //     subType = "hl:GNRAxe";
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
        if (ss instanceof RNAHelix) {
            subType = getHelixSubType(ss.getResidues(), residue, ssNext, ssPrev);
        } else if (ss instanceof Loop) {
            subType = "T" + getLoopType(ss);
        } else if (ss instanceof Bulge) {
            subType = "B" + ss.getResidues().size();
            // check whether res num is greater than paired res num, to determine orientation of bulge
            Residue prevResidue = ss.getResidues().get(0).getPrevious();
            if (prevResidue.getResNum() > prevResidue.pairedTo.getResNum()) {
                subType = subType + "c";
            }
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
                }
            }
            if (dumpKeys) {
                Molecule molecule = (Molecule) ssGen.structures().getFirst().firstResidue().molecule;
                String dotBracket = molecule.getDotBracket();

                angleKeyList.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).forEach(entry -> {
                    int iRes = entry.getKey();
                    System.out.printf("%3d %c %s\n", iRes, dotBracket.charAt(iRes-1), entry.getValue());
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
