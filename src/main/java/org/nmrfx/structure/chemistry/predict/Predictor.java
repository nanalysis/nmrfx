/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.HoseCodeGenerator;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.ProteinPredictor;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.energy.EnergyCoords;
import org.nmrfx.structure.chemistry.energy.EnergyLists;
import org.nmrfx.structure.chemistry.energy.RingCurrentShift;
import org.nmrfx.structure.chemistry.miner.NodeEvaluatorFactory;
import org.nmrfx.structure.chemistry.miner.NodeValidatorInterface;
import org.nmrfx.structure.chemistry.miner.PathIterator;
import org.python.util.PythonInterpreter;

/**
 *
 * @author Bruce Johnson
 */
public class Predictor {

    NodeValidatorInterface nodeValidator = null;

    static final Map<String, Double> RNA_REF_SHIFTS = new HashMap<>();
//    static final Map<String, Double> RNA_REF_DIST_SHIFTS = new HashMap<>();
    static final Map<String, Double> RNA_MAE_SHIFTS = new HashMap<>();

    static {
        RNA_MAE_SHIFTS.put("H2", 0.19);
        RNA_MAE_SHIFTS.put("H5''", 0.10);
        RNA_MAE_SHIFTS.put("H5", 0.20);
        RNA_MAE_SHIFTS.put("H6", 0.15);
        RNA_MAE_SHIFTS.put("H8", 0.19);
        RNA_MAE_SHIFTS.put("H4'", 0.08);
        RNA_MAE_SHIFTS.put("H5'", 0.16);
        RNA_MAE_SHIFTS.put("H2'", 0.21);
        RNA_MAE_SHIFTS.put("H3'", 0.13);
        RNA_MAE_SHIFTS.put("H1'", 0.13);
        RNA_MAE_SHIFTS.put("C2", 0.73);
        RNA_MAE_SHIFTS.put("C5", 0.84);
        RNA_MAE_SHIFTS.put("C6", 0.87);
        RNA_MAE_SHIFTS.put("C5'", 1.09);
        RNA_MAE_SHIFTS.put("C8", 1.07);
        RNA_MAE_SHIFTS.put("C3'", 1.41);
        RNA_MAE_SHIFTS.put("C4'", 0.93);
        RNA_MAE_SHIFTS.put("C1'", 0.88);
        RNA_MAE_SHIFTS.put("C2'", 0.58);
    }

    static {
        RNA_REF_SHIFTS.put("A.H1'", 5.383);
        RNA_REF_SHIFTS.put("A.H2", 8.024);
        RNA_REF_SHIFTS.put("A.H2'", 4.51);
        RNA_REF_SHIFTS.put("A.H3'", 4.569);
        RNA_REF_SHIFTS.put("A.H4'", 4.355);
        RNA_REF_SHIFTS.put("A.H5'", 4.3);
        RNA_REF_SHIFTS.put("A.H5''", 4.104);
        RNA_REF_SHIFTS.put("A.H8", 8.324);
        RNA_REF_SHIFTS.put("C.H1'", 5.481);
        RNA_REF_SHIFTS.put("C.H2'", 4.51);
        RNA_REF_SHIFTS.put("C.H3'", 4.569);
        RNA_REF_SHIFTS.put("C.H4'", 4.355);
        RNA_REF_SHIFTS.put("C.H5", 5.907);
        RNA_REF_SHIFTS.put("C.H5'", 4.3);
        RNA_REF_SHIFTS.put("C.H5''", 4.104);
        RNA_REF_SHIFTS.put("C.H6", 7.99);
        RNA_REF_SHIFTS.put("G.H1'", 5.345);
        RNA_REF_SHIFTS.put("G.H2'", 4.51);
        RNA_REF_SHIFTS.put("G.H3'", 4.569);
        RNA_REF_SHIFTS.put("G.H4'", 4.355);
        RNA_REF_SHIFTS.put("G.H5'", 4.3);
        RNA_REF_SHIFTS.put("G.H5''", 4.104);
        RNA_REF_SHIFTS.put("G.H8", 7.849);
        RNA_REF_SHIFTS.put("U.H1'", 5.554);
        RNA_REF_SHIFTS.put("U.H2'", 4.51);
        RNA_REF_SHIFTS.put("U.H3'", 4.569);
        RNA_REF_SHIFTS.put("U.H4'", 4.355);
        RNA_REF_SHIFTS.put("U.H5", 5.871);
        RNA_REF_SHIFTS.put("U.H5'", 4.3);
        RNA_REF_SHIFTS.put("U.H5''", 4.104);
        RNA_REF_SHIFTS.put("U.H6", 8.013);
        RNA_REF_SHIFTS.put("A.C1'", 90.584);
        RNA_REF_SHIFTS.put("A.C2", 154.711);
        RNA_REF_SHIFTS.put("A.C2'", 75.401);
        RNA_REF_SHIFTS.put("A.C3'", 72.982);
        RNA_REF_SHIFTS.put("A.C4'", 82.45);
        RNA_REF_SHIFTS.put("A.C5'", 65.269);
        RNA_REF_SHIFTS.put("A.C8", 140.623);
        RNA_REF_SHIFTS.put("C.C1'", 93.137);
        RNA_REF_SHIFTS.put("C.C2'", 75.401);
        RNA_REF_SHIFTS.put("C.C3'", 72.982);
        RNA_REF_SHIFTS.put("C.C4'", 82.45);
        RNA_REF_SHIFTS.put("C.C5", 98.444);
        RNA_REF_SHIFTS.put("C.C5'", 65.269);
        RNA_REF_SHIFTS.put("C.C6", 141.987);
        RNA_REF_SHIFTS.put("G.C1'", 91.236);
        RNA_REF_SHIFTS.put("G.C2'", 75.401);
        RNA_REF_SHIFTS.put("G.C3'", 72.982);
        RNA_REF_SHIFTS.put("G.C4'", 82.45);
        RNA_REF_SHIFTS.put("G.C5'", 65.269);
        RNA_REF_SHIFTS.put("G.C8", 137.576);
        RNA_REF_SHIFTS.put("U.C1'", 92.782);
        RNA_REF_SHIFTS.put("U.C2'", 75.401);
        RNA_REF_SHIFTS.put("U.C3'", 72.982);
        RNA_REF_SHIFTS.put("U.C4'", 82.45);
        RNA_REF_SHIFTS.put("U.C5", 104.425);
        RNA_REF_SHIFTS.put("U.C5'", 65.269);
        RNA_REF_SHIFTS.put("U.C6", 142.523);
    }

//    static {
//        RNA_REF_DIST_SHIFTS.put("A.H1'", 6.606);
//        RNA_REF_DIST_SHIFTS.put("A.H2", 7.977);
//        RNA_REF_DIST_SHIFTS.put("A.H2'", 4.449);
//        RNA_REF_DIST_SHIFTS.put("A.H3'", 4.341);
//        RNA_REF_DIST_SHIFTS.put("A.H4'", 4.357);
//        RNA_REF_DIST_SHIFTS.put("A.H5'", 4.275);
//        RNA_REF_DIST_SHIFTS.put("A.H5''", 4.274);
//        RNA_REF_DIST_SHIFTS.put("C.H1'", 5.7);
//        RNA_REF_DIST_SHIFTS.put("C.H2'", 4.449);
//        RNA_REF_DIST_SHIFTS.put("C.H3'", 4.341);
//        RNA_REF_DIST_SHIFTS.put("C.H4'", 4.357);
//        RNA_REF_DIST_SHIFTS.put("C.H5", 5.698);
//        RNA_REF_DIST_SHIFTS.put("C.H5'", 4.275);
//        RNA_REF_DIST_SHIFTS.put("C.H5''", 4.274);
//        RNA_REF_DIST_SHIFTS.put("C.H6", 7.978);
//        RNA_REF_DIST_SHIFTS.put("G.H1'", 6.234);
//        RNA_REF_DIST_SHIFTS.put("G.H2'", 4.449);
//        RNA_REF_DIST_SHIFTS.put("G.H3'", 4.341);
//        RNA_REF_DIST_SHIFTS.put("G.H4'", 4.357);
//        RNA_REF_DIST_SHIFTS.put("G.H5'", 4.275);
//        RNA_REF_DIST_SHIFTS.put("G.H5''", 4.274);
//        RNA_REF_DIST_SHIFTS.put("G.H8", 7.871);
//        RNA_REF_DIST_SHIFTS.put("U.H1'", 5.702);
//        RNA_REF_DIST_SHIFTS.put("U.H2'", 4.449);
//        RNA_REF_DIST_SHIFTS.put("U.H3'", 4.341);
//        RNA_REF_DIST_SHIFTS.put("U.H4'", 4.357);
//        RNA_REF_DIST_SHIFTS.put("U.H5", 5.642);
//        RNA_REF_DIST_SHIFTS.put("U.H5'", 4.275);
//        RNA_REF_DIST_SHIFTS.put("U.H5''", 4.274);
//        RNA_REF_DIST_SHIFTS.put("U.H6", 8.061);
//        RNA_REF_DIST_SHIFTS.put("A.C1'", 82.375);
//        RNA_REF_DIST_SHIFTS.put("A.C2", 154.377);
//        RNA_REF_DIST_SHIFTS.put("A.C2'", 77.631);
//        RNA_REF_DIST_SHIFTS.put("A.C3'", 67.556);
//        RNA_REF_DIST_SHIFTS.put("A.C4'", 80.026);
//        RNA_REF_DIST_SHIFTS.put("A.C5'", 65.858);
//        RNA_REF_DIST_SHIFTS.put("A.C8", 141.694);
//        RNA_REF_DIST_SHIFTS.put("C.C1'", 84.088);
//        RNA_REF_DIST_SHIFTS.put("C.C2'", 77.631);
//        RNA_REF_DIST_SHIFTS.put("C.C3'", 67.556);
//        RNA_REF_DIST_SHIFTS.put("C.C4'", 80.026);
//        RNA_REF_DIST_SHIFTS.put("C.C5", 99.777);
//        RNA_REF_DIST_SHIFTS.put("C.C5'", 65.858);
//        RNA_REF_DIST_SHIFTS.put("C.C6", 142.816);
//        RNA_REF_DIST_SHIFTS.put("G.C1'", 75.366);
//        RNA_REF_DIST_SHIFTS.put("G.C2'", 77.631);
//        RNA_REF_DIST_SHIFTS.put("G.C3'", 67.556);
//        RNA_REF_DIST_SHIFTS.put("G.C4'", 80.026);
//        RNA_REF_DIST_SHIFTS.put("G.C5'", 65.858);
//        RNA_REF_DIST_SHIFTS.put("G.C8", 138.7);
//        RNA_REF_DIST_SHIFTS.put("U.C1'", 86.524);
//        RNA_REF_DIST_SHIFTS.put("U.C2'", 77.631);
//        RNA_REF_DIST_SHIFTS.put("U.C3'", 67.556);
//        RNA_REF_DIST_SHIFTS.put("U.C4'", 80.026);
//        RNA_REF_DIST_SHIFTS.put("U.C5", 105.133);
//        RNA_REF_DIST_SHIFTS.put("U.C5'", 65.858);
//        RNA_REF_DIST_SHIFTS.put("U.C6", 143.318);
//
//    }
//    static double[] baseCAlphas = {109.797, -37.722, -42.722, 43.918, 34.351, 28.036,
//        8.282, -12.747, 22.815, -31.492, 14.434, -23.705, -99.898, 2.124,
//        18.657, -35.679, -23.634, -19.489, 46.318, -25.25, 60.338, -18.44,
//        -132.15, -36.312, 31.144, 50.921, 8.551, -34.733, 30.186, -22.981,
//        -92.437, -17.159, 46.791, -167.636, 98.993, -102.849, 48.193, 24.979,
//        -48.536, 130.381, -22.463, -83.248, -26.551, 33.386, -51.946, 157.547,
//        -78.555, -17.05};
//    static double[] riboseCAlphas = {183.252, -38.602, 39.064, 39.52, 9.936, 38.416,
//        -10.434, -17.213, -24.239, -45.227, -42.626, -16.284, -165.282, 39.621,
//        -59.543, 242.874, -129.387, 76.885, -42.6, -22.678, 8.644, -11.111,
//        -208.539, -82.817, 50.68, 60.957, 132.087, -174.547, 60.461, 19.286,
//        -174.358, 68.015, -115.779, 586.809, -309.398, 200.794, -152.764,
//        91.248, 2.49, -358.12, 108.341, -157.851, -28.714, 55.599, -148.782,
//        327.803, -162.567, -2.03};
//    static double[] baseHAlphas = {6.865, -3.892, -1.983, -0.507, 4.033, 1.264,
//        -0.721, -0.055, 0.83, 0.705, -0.346, -0.859, -17.689, 19.241, -4.373,
//        -34.864, 0.819, 0.957, 0.521, -1.355, 20.992, 2.978, -7.787, -1.922,
//        1.409, 10.776, -9.739, -0.055, 5.104, -2.825, -14.755, 12.592, -2.459,
//        -26.824, 2.379, 5.485, -8.897, 5.564, -2.356, 23.225, -5.205, -5.813,
//        17.198, -6.817, -20.967, 25.346, -11.519, -0.97};
//    static double[] riboseHAlphas = {2.629, -1.978, -2.491, -0.551, 2.6, 2.402, -0.884,
//        0.028, 0.39, 1.681, -0.218, -1.22, -2.413, 7.099, 5.023, -26.883,
//        11.952, -0.527, -7.7, 28.734, -50.508, 19.122, -3.53, -4.062, 0.709,
//        8.823, -36.481, 21.023, 6.634, 1.267, -2.01, 6.7, 12.972, -65.587,
//        9.095, 8.952, -9.218, 4.321, 0.207, 14.587, 10.079, -3.146, -3.358,
//        1.418, -3.314, -5.648, 6.943, -0.54};
//    static double[][] alphas = {baseCAlphas, riboseCAlphas, baseHAlphas, riboseHAlphas};
    static double[][] alphas = new double[4][];
    static Map<String, Double> baseShiftMap = new HashMap<>();
    static Map<String, Double> maeMap = new HashMap<>();
    static double rMax = 4.6;

    private boolean isRNA(Polymer polymer) {
        boolean rna = false;
        for (Residue residue : polymer.getResidues()) {
            String resName = residue.getName();
            if (resName.equals("A") || resName.equals("C") || resName.equals("G") || resName.equals("U")) {
                rna = true;
                break;
            }
        }
        return rna;
    }

    public static void loadRNADistData() {
        try {
            readFile("data/rna_pred_dist_H_4.6.txt");
            readFile("data/rna_pred_dist_C_4.6.txt");
        } catch (IOException ex) {
            Logger.getLogger(Predictor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static Double getMAE(Atom atom) {
        String aName = atom.getName();
        Double mae = maeMap.get(aName);
        return mae;
    }

    public static Double getDistBaseShift(Atom atom) {
        if (baseShiftMap.isEmpty()) {
            loadRNADistData();
        }
        String nucName = atom.getEntity().getName();
        String nucAtom = nucName + "." + atom.getName();
        Double basePPM;
        if (baseShiftMap.containsKey(nucAtom)) {
            basePPM = baseShiftMap.get(nucAtom);
        } else {
            basePPM = null;
        }
        return basePPM;
    }

    public static double getAlpha(int alphaClass, int index) {
        return alphas[alphaClass][index];

    }

    public void predictMolecule(Molecule mol, int iRef) throws InvalidMoleculeException {

        for (Polymer polymer : mol.getPolymers()) {
            if (!isRNA(polymer)) {
                predictPeptidePolymer(polymer, iRef);
            } else {
                predictRNAWithRingCurrent(polymer, 0, iRef);
            }
        }
        boolean hasPolymer = !mol.getPolymers().isEmpty();
        for (Entity entity : mol.getLigands()) {
            predictWithShells(entity, iRef);
            if (hasPolymer) {
                predictLigandWithRingCurrent(entity, iRef);
            }
        }
    }

    public void predictPeptidePolymer(Polymer polymer, int iRef) throws InvalidMoleculeException {
        String[] atomNames = {"N", "CA", "CB", "C", "H", "HA", "HA3"};
        ProteinPredictor predictor = new ProteinPredictor(polymer);
        polymer.getResidues().forEach((residue) -> {
            for (String atomName : atomNames) {
                if (residue.getName().equals("GLY") && atomName.equals("HA")) {
                    atomName = "HA2";
                }
                Atom atom = residue.getAtom(atomName);
                if (atom != null) {
                    Double value = predictor.predict(residue.getAtom(atomName), false);
                    if (value != null) {
                        atom.setRefPPM(iRef, value);
                    }
                }
            }
        });
    }

    public void predictRNAWithAttributes() {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            if (!molecule.getDotBracket().equals("")) {
                PythonInterpreter interp = new PythonInterpreter();
                interp.exec("import rnapred\nrnapred.predictFromSequence()");
            }
        }
    }

    public void predictRNAWithRingCurrent(Polymer polymer, int iStruct, int iRef) throws InvalidMoleculeException {
        RingCurrentShift ringShifts = new RingCurrentShift();
        ringShifts.makeRingList(polymer.molecule);

        double ringRatio = 0.56;
        List<Atom> atoms = polymer.getAtoms();
        for (Atom atom : atoms) {
            String name = atom.getShortName();
            String aName = atom.getName();
            String nucName = atom.getEntity().getName();
            String nucAtom = nucName + "." + aName;
            if (RNA_REF_SHIFTS.containsKey(nucAtom)) {
                double basePPM = RNA_REF_SHIFTS.get(nucName + "." + aName);
                double ringPPM = ringShifts.calcRingContributions(atom.getSpatialSet(), iStruct, ringRatio);
                double ppm = basePPM + ringPPM;
                atom.setRefPPM(iRef, ppm);
            }
        }
    }

    public void predictLigandWithRingCurrent(Entity ligand, int iRef) throws InvalidMoleculeException {
        RingCurrentShift ringShifts = new RingCurrentShift();
        ringShifts.makeRingList(ligand.molecule);

        double ringRatio = 0.56;
        List<Atom> atoms = ligand.getAtoms();
        for (Atom atom : atoms) {
            String name = atom.getShortName();
            String aName = atom.getName();
            double ringPPM = ringShifts.calcRingContributions(atom.getSpatialSet(), 0, ringRatio);
            PPMv ppmV = atom.getRefPPM(iRef);
            if (ppmV != null) {
                double basePPM = ppmV.getValue();
                double ppm = basePPM + ringPPM;
                atom.setRefPPM(iRef, ppm);
            }
        }
    }

    public void predictRNAWithDistances(Polymer polymer, int iStruct, int iRef) throws InvalidMoleculeException {
        if (false) {
            EnergyCoords eCoords = polymer.molecule.getEnergyCoords();
                eCoords.setCells(null, 10000,rMax, 0.0, true, 0.0, 0.0);
                eCoords.calcDistShifts(false, rMax, 1.0);

        } else {
            List<Atom> atoms = polymer.getAtoms();
            for (Atom atom : atoms) {
                String name = atom.getShortName();
                String aName = atom.getName();
//            if (aName.charAt(0) == 'H') {
//                continue;
//            }
                int alphaType = 0;
                if (aName.charAt(aName.length() - 1) == '\'') {
                    if (aName.charAt(0) == 'H') {
                        alphaType = 3;
                    } else {
                        alphaType = 1;

                    }
                } else {
                    if (aName.charAt(0) == 'H') {
                        alphaType = 2;
                    } else {
                        alphaType = 0;

                    }
                }
                String nucName = atom.getEntity().getName();
                String nucAtom = nucName + "." + aName;
                if (baseShiftMap.containsKey(nucAtom)) {
                    double basePPM = baseShiftMap.get(nucAtom);
                    double[] distances = polymer.molecule.calcDistanceInputMatrixRow(iStruct, rMax, atom);
                    double distPPM = 0.0;
                    for (int i = 0; i < alphas[alphaType].length; i++) {
                        distPPM += alphas[alphaType][i] * distances[i];
                    }
                    double ppm = basePPM + distPPM;
                    atom.setRefPPM(iRef, ppm);
                }
            }
        }
    }

    public void predictWithShells(Entity aC, int iRef) {
        HosePrediction hosePred = HosePrediction.getDefaultPredictor();
        PathIterator pI = new PathIterator(aC);
        if (nodeValidator == null) {
            nodeValidator = NodeEvaluatorFactory.getDefault();
        }
        pI.init(nodeValidator);
        pI.processPatterns();
        pI.setProperties("ar", "AROMATIC");
        pI.setHybridization();
        HoseCodeGenerator hoseGen = new HoseCodeGenerator();
        Map<Integer, String> result = hoseGen.genHOSECodes(aC, 5);
        for (Atom atom : aC.getAtoms()) {
            String predAtomType = "";
            Atom hoseAtom = null;
            double roundScale = 1.0;

            if (atom.getAtomicNumber() == 6) {
                hoseAtom = atom;
                predAtomType = "13C";
                roundScale = 10.0;
            } else if (atom.getAtomicNumber() == 1) {
                hoseAtom = atom.parent;
                predAtomType = "1H";
                roundScale = 100.0;
            }
            if ((hoseAtom != null) && (hoseAtom.getAtomicNumber() == 6)) {
                String hoseCode = (String) hoseAtom.getProperty("hose");
//                System.out.println(atom.getShortName() + " " + hoseAtom.getShortName() + " " + hoseCode);
                if (hoseCode != null) {
                    PredictResult predResult;
                    HosePrediction.HOSEPPM hosePPM = new HosePrediction.HOSEPPM(hoseCode);
                    predResult = hosePred.predict(hosePPM, predAtomType);
                    HOSEStat hoseStat = predResult.getStat(predAtomType);
                    double shift = hoseStat.dStat.getPercentile(50);
                    shift = Math.round(shift * roundScale) / roundScale;
                    System.out.println(atom.getShortName() + " " + predResult.getShell() + " " + shift);
                    atom.setRefPPM(iRef, shift);
                }
            }
        }
    }

    public static void readFile(String resourceName) throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream(resourceName);
        if (istream == null) {
            throw new IOException("Cannot find '" + resourceName + "' on classpath");
        } else {
            InputStreamReader reader = new InputStreamReader(istream);
            BufferedReader breader = new BufferedReader(reader);
            String aType = "";
            String state = "";
            int nCoef = 0;
            String[] coefAtoms = null;
            int typeIndex = 0;
            while (true) {
                String line = breader.readLine();
                if (line == null) {
                    break;
                } else {
                    if (!line.equals("")) {
                        String[] fields = line.split("\t");
                        if (fields.length > 0) {
                            if (fields[0].equals("rmax")) {
                                rMax = Double.parseDouble(fields[1]);
                                aType = fields[2];
                            } else if (fields[0].equals("coef")) {
                                state = "coef";
                                nCoef = Integer.parseInt(fields[2]);
                                if (fields[1].equals("base")) {
                                    typeIndex = 0;
                                } else {
                                    typeIndex = 1;
                                }
                                if (aType.equals("H")) {
                                    typeIndex += 2;
                                }
                                alphas[typeIndex] = new double[nCoef];
                                coefAtoms = new String[nCoef];
                            } else if (fields[0].equals("baseshifts")) {
                                state = "baseshifts";
                            } else if (fields[0].equals("mae")) {
                                state = "mae";
                            } else {
                                switch (state) {
                                    case "coef":
                                        int index = Integer.parseInt(fields[0]);
                                        coefAtoms[index] = fields[1];
                                        alphas[typeIndex][index] = Double.parseDouble(fields[2]);
                                        break;
                                    case "baseshifts":
                                        double shift = Double.parseDouble(fields[1]);
                                        baseShiftMap.put(fields[0], shift);
                                        break;
                                    case "mae":
                                        double value = Double.parseDouble(fields[1]);
                                        maeMap.put(fields[0], value);
                                        break;
                                }
                            }

                        }
                    }
                }
            }
            breader.close();
//            for (String key : statMap.keySet()) {
//                System.out.println(key);
//            }
        }
    }

}
