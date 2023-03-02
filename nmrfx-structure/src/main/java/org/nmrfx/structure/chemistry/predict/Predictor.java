/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.HoseCodeGenerator;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.EnergyCoords;
import org.nmrfx.structure.chemistry.energy.RingCurrentShift;
import org.nmrfx.structure.chemistry.miner.NodeEvaluatorFactory;
import org.nmrfx.structure.chemistry.miner.NodeValidatorInterface;
import org.nmrfx.structure.chemistry.miner.PathIterator;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 *
 * @author Bruce Johnson
 */
public class Predictor {
    private static final Logger log = LoggerFactory.getLogger(Predictor.class);

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

    void clearRefPPMs(int iRef) {
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> molAtoms = mol.getAtoms();
            for (Atom atom : molAtoms) {
                PPMv ppmV;
                if (iRef < 0) {
                    ppmV = atom.getRefPPM(-iRef - 1);
                } else {
                    ppmV = atom.getPPM(iRef);
                }
                if (ppmV != null) {
                    ppmV.setValid(false, atom);
                }
            }
        }
    }

    // used from Python
    public void setToBMRBValues(int iRef) {
        clearRefPPMs(iRef);
        BMRBStats.loadAllIfEmpty();
        Molecule mol = Molecule.getActive();
        if (mol != null) {
            List<Atom> molAtoms = mol.getAtoms();
            for (Atom atom : molAtoms) {
                String aName = atom.getName();
                String resName = atom.getEntity().getName();
                Optional<PPMv> ppmVOpt = BMRBStats.getValue(resName, aName);
                if (ppmVOpt.isPresent()) {
                    PPMv ppmV = ppmVOpt.get();
                    if (iRef < 0) {
                        atom.setRefPPM(-iRef - 1, ppmV.getValue());
                        atom.setRefError(-iRef - 1, ppmV.getError());
                    } else {
                        atom.setPPM(iRef, ppmV.getValue());
                        atom.setPPMError(iRef, ppmV.getError());
                    }
                }
            }
        }
    }

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
            readFile("data/rna_pred_dist_H_6.0.txt");
            readFile("data/rna_pred_dist_C_6.0.txt");
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    public static Double getMAE(Atom atom) {
        String aName = atom.getName();
        return maeMap.get(aName);
    }

    public static Double getDistBaseShift(Atom atom) {
        checkRNADistData();
        String nucName = atom.getEntity().getName();
        String nucAtom = nucName + "." + atom.getName();
        return baseShiftMap.getOrDefault(nucAtom, null);
    }

    public static void checkRNADistData() {
        if (coefMap.isEmpty()) {
            loadRNADistData();
        }

    }

    public static int getAlphaIndex(String nucName, String aName) {
        checkRNADistData();
        Integer typeIndex = coefMap.get(aName);
        if (typeIndex == null) {
            typeIndex = coefMap.get(nucName + "." + aName);
        }
        return typeIndex != null ? typeIndex : -1;
    }

    public static double getAlpha(int alphaClass, int index) {
        return alphas[alphaClass][index];
    }

    public static double getAlphaIntercept(int alphaClass) {
        return alphas[alphaClass][alphas[alphaClass].length - 1];
    }

    public static double getAngleAlpha(int alphaClass, int index) {
        int nAlpha = alphas[alphaClass].length - 1;
        return alphas[alphaClass][nAlpha - 4 + index];
    }

    public void predictProtein(Molecule mol, int iStructure, int iRef) throws InvalidMoleculeException, IOException {
        if (proteinPredictor == null) {
            proteinPredictor = new ProteinPredictor();
        }
        proteinPredictor.init(mol, iStructure);
        proteinPredictor.predict(iRef, iStructure);
    }

    public void predictMolecule(Molecule mol, int iStructure, int iRef, boolean rcMode) throws InvalidMoleculeException, IOException {

        boolean hasPeptide = false;
        for (Polymer polymer : mol.getPolymers()) {
            if (isRNA(polymer)) {
                if (rcMode) {
                    predictRNAWithRingCurrent(polymer, iStructure, iRef);
                } else {
                    predictRNAWithDistances(polymer, iStructure, iRef, false);
                }
            } else if (polymer.isPeptide()) {
                hasPeptide = true;
            }
        }

        if (hasPeptide) {
            if (proteinPredictor == null) {
                proteinPredictor = new ProteinPredictor();
            }
            proteinPredictor.init(mol, iStructure);
            proteinPredictor.predict(iRef, iStructure);

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
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        if ((molecule != null) && !molecule.getDotBracket().equals("")) {
            try (PythonInterpreter interp = new PythonInterpreter()) {
                interp.exec("import rnapred\nrnapred.predictFromSequence(ppmSet=" + ppmSet + ")");
            }
        }
    }

    public void predictRNAWithRingCurrent(Polymer polymer, int iStruct, int iRef) {
        RingCurrentShift ringShifts = new RingCurrentShift();
        ringShifts.makeRingList(polymer.molecule);

        double ringRatio = 0.56;
        List<Atom> atoms = polymer.getAtoms();
        for (Atom atom : atoms) {
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

    public void predictLigandWithRingCurrent(Entity ligand, int iRef) {
        RingCurrentShift ringShifts = new RingCurrentShift();
        ringShifts.makeRingList(ligand.molecule);

        double ringRatio = 0.56;
        List<Atom> atoms = ligand.getAtoms();
        for (Atom atom : atoms) {
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
            aName1 = atom1.getEntity().getName() + "." + aName1;
        }
        String aName2 = atom2.getName();
        if (!aName2.contains("'")) {
            aName2 = atom2.getEntity().getName() + "." + aName2;
        }
        return isRNAPairFixed(aName1, aName2);
    }

    public static boolean isRNAPairFixed(String aName1, String aName2) {
        if (rnaFixedMap.isEmpty()) {
            try {
                readRNAFixed("data/rnafix.txt");
            } catch (IOException ex) {
                log.error("failed load ", ex);
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

    public void predictRNAWithDistances(Polymer polymer, int iStruct, int iRef, boolean eMode) {
        Molecule molecule = (Molecule) polymer.molecule;
        if (eMode) {
            molecule.updateVecCoords();
            EnergyCoords eCoords = molecule.getEnergyCoords();
            eCoords.calcDistShifts(false, getRMax(), intraScale, 1.0);
        } else {
            List<Atom> atoms = polymer.getAtoms();
            double[] angleValues = new double[4];
            for (Atom atom : atoms) {
                String aName = atom.getName();
                String nucName = atom.getEntity().getName();
                int alphaType = getAlphaIndex(nucName, aName);
                if (alphaType >= 0) {
                    int nAlpha = alphas[alphaType].length - 1;
                    double[] distances = molecule.calcDistanceInputMatrixRow(iStruct, getRMax(), atom, getIntraScale());
                    double distPPM = 0.0;
                    double chi = ((Residue) atom.getEntity()).calcChi(iStruct);
                    angleValues[0] = Math.cos(chi);
                    angleValues[1] = Math.sin(chi);
                    double nu2 = ((Residue) atom.getEntity()).calcNu2(iStruct);
                    angleValues[2] = Math.cos(nu2);
                    angleValues[3] = Math.sin(nu2);
                    int angStart = nAlpha - 4;
                    for (int i = 0; i < nAlpha; i++) {
                        double alpha = alphas[alphaType][i];
                        double shiftContrib;
                        if (i < angStart) {
                            shiftContrib = alpha * distances[i];
                        } else {
                            shiftContrib = alpha * angleValues[i - angStart];
                        }
                        distPPM += shiftContrib;
                    }
                    double ppm = alphas[alphaType][nAlpha] + distPPM;
                    Double mae = getMAE(atom);
                    if (iRef < 0) {
                        atom.setRefPPM(-iRef - 1, ppm);
                        if (mae != null) {
                            atom.setRefError(-iRef - 1, mae);
                        }
                    } else {
                        atom.setPPM(iRef, ppm);
                        if (mae != null) {
                            atom.setPPMError(-iRef - 1, mae);
                        }
                    }
                }
            }
        }
    }

    public void predictWithShells(Entity aC, int iRef) {
        PathIterator pI = new PathIterator(aC);
        if (nodeValidator == null) {
            nodeValidator = NodeEvaluatorFactory.getDefault();
        }
        pI.init(nodeValidator);
        pI.processPatterns();
        pI.setProperties("ar", "AROMATIC");
        pI.setHybridization();
        predictWithShells(aC, iRef, 6);
        predictWithShells(aC, iRef, 7);
    }

    public void predictWithShells(Entity aC, int iRef, int aNum) {
        HosePrediction hosePred = aNum == 6 ? HosePrediction.getDefaultPredictor() : HosePrediction.getDefaultPredictorN();
        HoseCodeGenerator hoseGen = new HoseCodeGenerator();
        hoseGen.genHOSECodes(aC, 5, aNum);
        for (Atom atom : aC.getAtoms()) {
            String predAtomType = "";
            Atom hoseAtom = null;
            double roundScale = 1.0;
            if (atom.getAtomicNumber() == 1) {
                if (atom.getParent().getAtomicNumber() != aNum) {
                    continue;
                }
            } else if (atom.getAtomicNumber() != aNum) {
                continue;
            }

            if (atom.getAtomicNumber() == aNum) {
                hoseAtom = atom;
                predAtomType = aNum == 6 ? "13C" : "15N";
                roundScale = 10.0;
            } else if (atom.getAtomicNumber() == 1) {
                hoseAtom = atom.parent;
                predAtomType = "1H";
                roundScale = 100.0;
            }
            if ((hoseAtom != null) && (hoseAtom.getAtomicNumber() == aNum)) {
                String hoseCode = (String) hoseAtom.getProperty("hose");
                if (hoseCode != null) {
                    PredictResult predResult;
                    HosePrediction.HOSEPPM hosePPM = new HosePrediction.HOSEPPM(hoseCode);
                    predResult = hosePred.predict(hosePPM, predAtomType);
                    HOSEStat hoseStat = predResult.getStat(predAtomType);
                    if (hoseStat != null) {
                        double shift = hoseStat.dStat.getPercentile(50);
                        shift = Math.round(shift * roundScale) / roundScale;
                        if (iRef < 0) {
                            atom.setRefPPM(-iRef - 1, shift);
                        } else {
                            atom.setPPM(iRef, shift);
                        }
                    } else {
                        log.warn("no hose prediction for " + hoseAtom.getFullName() + " " + hoseCode);
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
            String state = "";
            int nCoef;
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
                                // For this line fields[2] is unused but its value is the atom type, e.g. H, C
                                // it was previously used to help set the typeIndex
                                setIntraScale(Double.parseDouble(fields[3]));
                            } else if (fields[0].equals("coef")) {
                                state = "coef";
                                nCoef = Integer.parseInt(fields[2]) + 1;
                                if (!coefMap.containsKey(fields[1])) {
                                    coefMap.put(fields[1], coefMap.size());
                                }
                                typeIndex = coefMap.get(fields[1]);
                                alphas[typeIndex] = new double[nCoef];
                                coefAtoms = new String[nCoef];
                                alphas[typeIndex][nCoef - 1] = Double.parseDouble(fields[3]);
                                coefAtoms[nCoef - 1] = "intercept";
                            } else if (fields[0].equals("baseshifts")) {
                                state = "baseshifts";
                            } else if (fields[0].equals("mae")) {
                                state = "mae";
                            } else {
                                switch (state) {
                                    case "coef" -> {
                                        int index = Integer.parseInt(fields[0]);
                                        coefAtoms[index] = fields[1];
                                        alphas[typeIndex][index] = Double.parseDouble(fields[2]);
                                    }
                                    case "baseshifts" -> {
                                        double shift = Double.parseDouble(fields[1]);
                                        baseShiftMap.put(fields[0], shift);
                                    }
                                    case "mae" -> {
                                        double value = Double.parseDouble(fields[1]);
                                        maeMap.put(fields[0], value);
                                    }
                                }
                            }

                        }
                    }
                }
            }
            breader.close();
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
        Set<String> set = rnaFixedMap.computeIfAbsent(srcName, k -> new HashSet<>());
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
