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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.HoseCodeGenerator;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.energy.EnergyCoords;
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

    ProteinPredictor proteinPredictor = null;

    /**
     * @return the rMax
     */
    public static double getRMax() {
        return rMax;
    }

    /**
     * @param arMax the rMax to set
     */
    public static void setRMax(double arMax) {
        rMax = arMax;
    }

    /**
     * @return the intraScale
     */
    public static double getIntraScale() {
        return intraScale;
    }

    /**
     * @param aIntraScale the intraScale to set
     */
    public static void setIntraScale(double aIntraScale) {
        intraScale = aIntraScale;
    }

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

    static double[][] alphas = new double[25][];
    static Map<String, Double> baseShiftMap = new HashMap<>();
    static Map<String, Double> maeMap = new HashMap<>();
    static Map<String, Integer> coefMap = new HashMap<>();
    private static double rMax = 4.6;
    private static double intraScale = 5.0;
    static Map<String, Set<String>> rnaFixedMap = new HashMap<>();

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

    public static int getAlphaIndex(String nucName, String aName) {
        Integer typeIndex = coefMap.get(aName);
        if (typeIndex == null) {
            typeIndex = coefMap.get(nucName + "." + aName);
        }
        return typeIndex != null ? typeIndex : -1;
    }

    public static double getAlpha(int alphaClass, int index) {
        return alphas[alphaClass][index];

    }

    public static double getAngleAlpha(int alphaClass, int index) {
        int nAlpha = alphas[alphaClass].length;
        return alphas[alphaClass][nAlpha - 4 + index];
    }

    public void predictProtein(Molecule mol, int iRef) throws InvalidMoleculeException, IOException {
        if (proteinPredictor == null) {
            proteinPredictor = new ProteinPredictor();
        }
        proteinPredictor.init(mol);
        proteinPredictor.predict(iRef);
    }

    public void predictMolecule(Molecule mol, int iRef) throws InvalidMoleculeException, IOException {

        boolean hasPeptide = false;
        for (Polymer polymer : mol.getPolymers()) {
            if (isRNA(polymer)) {
                predictRNAWithDistances(polymer, 0, iRef, false);
            } else if (polymer.isPeptide()) {
                hasPeptide = true;
            }
        }

        if (hasPeptide) {
            if (proteinPredictor == null) {
                proteinPredictor = new ProteinPredictor();
            }
            proteinPredictor.init(mol);
            proteinPredictor.predict(iRef);

        }
        boolean hasPolymer = !mol.getPolymers().isEmpty();
        for (Entity entity : mol.getLigands()) {
            predictWithShells(entity, iRef);
            if (hasPolymer) {
                predictLigandWithRingCurrent(entity, iRef);
            }
        }
    }

    public void predictRNAWithAttributes(int ppmSet) {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            if (!molecule.getDotBracket().equals("")) {
                PythonInterpreter interp = new PythonInterpreter();
                interp.exec("import rnapred\nrnapred.predictFromSequence(ppmSet=" + ppmSet + ")");
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
                if (iRef < 0) {
                    atom.setRefPPM(-iRef - 1, ppm);
                } else {
                    atom.setPPM(iRef, ppm);
                }
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
                if (iRef < 0) {
                    atom.setRefPPM(-iRef - 1, ppm);
                } else {
                    atom.setPPM(iRef, ppm);
                }
            }
        }
    }

    public static boolean isRNAPairFixed(Atom atom1, Atom atom2) {
        String aName1 = atom1.getName();
        if (!aName1.contains("'")) {
            aName1 = ((Residue) atom1.getEntity()).getName() + "." + aName1;
        }
        String aName2 = atom2.getName();
        if (!aName2.contains("'")) {
            aName2 = ((Residue) atom2.getEntity()).getName() + "." + aName2;
        }
        return isRNAPairFixed(aName1, aName2);
    }

    public static boolean isRNAPairFixed(String aName1, String aName2) {
        if (rnaFixedMap.isEmpty()) {
            try {
                readRNAFixed("data/rnafix.txt");
                System.out.println("loaded fixed");
            } catch (IOException ex) {
                System.out.println("failed load " + ex.getMessage());
                return false;
            }
        }
        String srcName;
        String targetName;
        if (aName1.compareTo(aName2) < 0) {
            srcName = aName1;
            targetName = aName2;
        } else {
            srcName = aName2;
            targetName = aName1;
        }

        if (rnaFixedMap.containsKey(srcName)) {
            return rnaFixedMap.get(srcName).contains(targetName);
        }
        return false;
    }

    public void predictRNAWithDistances(Polymer polymer, int iStruct, int iRef, boolean eMode) throws InvalidMoleculeException {
        if (eMode) {
            polymer.molecule.updateVecCoords();
            EnergyCoords eCoords = polymer.molecule.getEnergyCoords();
            // eCoords.setCells(eCoords.getShiftPairs(), 10000, rMax, 0.0, true, 0.0, 0.0);
            System.out.println("pred");
            eCoords.calcDistShifts(false, getRMax(), intraScale, 1.0);
        } else {
            System.out.println("rmax " + getRMax());
            List<Atom> atoms = polymer.getAtoms();
            for (Atom atom : atoms) {
                String aName = atom.getName();
                Double basePPM = getDistBaseShift(atom);
                double[] angleValues = new double[4];
                if (basePPM != null) {
                    String nucName = atom.getEntity().getName();
                    int alphaType = getAlphaIndex(nucName, aName);
                    if (alphaType >= 0) {
                        double[] distances = polymer.molecule.calcDistanceInputMatrixRow(iStruct, getRMax(), atom, getIntraScale());
                        double distPPM = 0.0;
                        double chi = ((Residue) atom.getEntity()).calcChi();
                        angleValues[0] = Math.cos(chi);
                        angleValues[1] = Math.sin(chi);
                        double nu2 = ((Residue) atom.getEntity()).calcNu2();
                        angleValues[2] = Math.cos(nu2);
                        angleValues[3] = Math.sin(nu2);
                        int angStart = alphas[alphaType].length - 4;
                        for (int i = 0; i < alphas[alphaType].length; i++) {
                            double alpha = alphas[alphaType][i];
                            double shiftContrib;
                            double dis = 0.0;
                            if (i < angStart) {
                                shiftContrib = alpha * distances[i];
                            } else {
                                shiftContrib = alpha * angleValues[i - angStart];
                            }
                            distPPM += shiftContrib;
                        }
                        double ppm = basePPM + distPPM;
                        if (iRef < 0) {
                            atom.setRefPPM(-iRef - 1, ppm);
                        } else {
                            atom.setPPM(iRef, ppm);
                        }
                    }
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
                    if (iRef < 0) {
                        atom.setRefPPM(-iRef - 1, shift);
                    } else {
                        atom.setPPM(iRef, shift);
                    }
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
                                setRMax(Double.parseDouble(fields[1]));
                                aType = fields[2];
                                setIntraScale(Double.parseDouble(fields[3]));
                            } else if (fields[0].equals("coef")) {
                                state = "coef";
                                nCoef = Integer.parseInt(fields[2]);
                                if (!coefMap.containsKey(fields[1])) {
                                    coefMap.put(fields[1], coefMap.size());
                                }
                                typeIndex = coefMap.get(fields[1]);
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

    static void addFixed(String aName1, String aName2) {
        String srcName;
        String targetName;
        if (aName1.compareTo(aName2) < 0) {
            srcName = aName1;
            targetName = aName2;
        } else {
            srcName = aName2;
            targetName = aName1;
        }
        Set<String> set = rnaFixedMap.get(srcName);
        if (set == null) {
            set = new HashSet<>();
            rnaFixedMap.put(srcName, set);
        }
        set.add(targetName);

    }

    public static void readRNAFixed(String resourceName) throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream(resourceName);
        if (istream == null) {
            throw new IOException("Cannot find '" + resourceName + "' on classpath");
        } else {
            InputStreamReader reader = new InputStreamReader(istream);
            BufferedReader breader = new BufferedReader(reader);
            String aType = "";
            while (true) {
                String line = breader.readLine();
                if (line == null) {
                    break;
                } else {
                    if (!line.equals("")) {
                        String[] fields = line.split("\t");
                        String aName1 = fields[0];
                        for (int i = 1; i < fields.length; i++) {
                            addFixed(aName1, fields[i]);
                        }
                    }
                }
            }
        }
    }

}
