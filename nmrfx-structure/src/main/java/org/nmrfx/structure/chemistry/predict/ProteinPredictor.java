package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.PDBAtomParser;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.ProteinPropertyGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ProteinPredictor {

    public final static Map<String, Double> RANDOM_SCALES = new HashMap<>();
    // values from Journal of Biomolecular NMR (2018) 70:141–165 Potenci

    static {
        RANDOM_SCALES.put("N", -0.472);
        RANDOM_SCALES.put("C", 0.185);
        RANDOM_SCALES.put("CB", -0.154);
        RANDOM_SCALES.put("CA", 0.198);
        RANDOM_SCALES.put("H", -0.067);
        RANDOM_SCALES.put("HA", -0.026);
        RANDOM_SCALES.put("HB", 0.022);

    }

    static final Set<String> atomTypes = new HashSet<>();

    ProteinPropertyGenerator propertyGenerator;
    Map<String, Integer> aaMap = new HashMap<>();
    Map<String, Double> rmsMap = new HashMap<>();
    Map<String, double[]> minMaxMap = new HashMap<>();
    ArrayList<String> attrNames = new ArrayList<>();
    Map<String, Double> shiftMap = new HashMap<>();
    Map<String, Double> tempMap = new HashMap<>();
    Map<String, double[]> neighborMap = new HashMap<>();
    Map<String, Map<String, CorrComb>> corrCombMap = new HashMap<>();
    Molecule molecule = null;

    double[][] values = null;

    String reportAtom = null;

    public void init(Molecule mol, int iStructure) throws InvalidMoleculeException, IOException {
        propertyGenerator = new ProteinPropertyGenerator();
        propertyGenerator.init(mol, iStructure);
        this.molecule = mol;
    }

    public void setReportAtom(String name) {
        reportAtom = name;
    }

    void loadAttrMap(String[] fields) {
        for (int i = 1; i < fields.length; i++) {
            aaMap.put(fields[i].trim(), i - 1);
        }

    }

    void loadCoefficients() throws IOException {
        InputStream iStream = this.getClass().getResourceAsStream("/data/predict/protein/coefs3d.txt");
        List<String[]> lines = new ArrayList<>();
        boolean firstLine = true;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String[] fields = line.split("\t");
                if (firstLine) {
                    loadAttrMap(fields);
                    firstLine = false;
                } else {
                    lines.add(fields);
                }
            }
        }
        int nCoef = lines.size();
        int nTypes = lines.get(0).length - 1;
        values = new double[nTypes][nCoef];
        for (int j = 0; j < nCoef; j++) {
            attrNames.add(lines.get(j)[0].trim());
            for (int i = 0; i < nTypes; i++) {
                values[i][j] = Double.parseDouble(lines.get(j)[i + 1]);
            }
        }
        initMinMax();
    }

    class CorrComb {

        int relPos;
        String centerAA;
        String neighborType;
        double value1;
        double value2;

        public CorrComb(int relPos, String centerAA, String neighborType, double value1, double value2) {
            this.relPos = relPos;
            this.centerAA = centerAA;
            this.neighborType = neighborType;
            this.value1 = value1;
            this.value2 = value2;
        }

    }

    void loadPotenci() throws IOException {
        InputStream iStream = this.getClass().getResourceAsStream("/data/predict/protein/potenci.txt");
        List<String[]> lines = new ArrayList<>();
        boolean firstLine = true;
        String mode = "";
        List<String> atomNames = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.strip();
                if (line.isBlank()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    mode = line.substring(1);
                    continue;
                }
                String[] fields = line.split("\t");
                switch (mode) {
                    case "SHIFT": {
                        if (fields[0].equals("aa")) {
                            atomNames.clear();
                            for (int i = 1; i < fields.length; i++) {
                                atomNames.add(fields[i]);
                            }
                        } else {
                            String aaName = fields[0];
                            for (int i = 1; i < fields.length; i++) {
                                if (!fields[i].equals("None")) {
                                    Double value = Double.parseDouble(fields[i]);
                                    shiftMap.put(aaName + "." + atomNames.get(i - 1), value);
                                }
                            }
                        }
                        break;
                    }
                    case "NEIGHBOR": {
                        String aName = fields[0];
                        String aaName = fields[1];
                        double[] values = new double[4];
                        for (int i = 0; i < values.length; i++) {
                            values[i] = Double.parseDouble(fields[2 + i]);
                        }
                        neighborMap.put(aaName + "." + aName, values);
                        break;
                    }
                    //C       -1      G       r       xrGxx   0.2742  1.4856

                    case "TERMCORRS": {
                        String aName = fields[0];
                        String termName = fields[1];
                        double[] values = new double[4];
                        if (termName.equals("n")) {
                            values[3] = Double.parseDouble(fields[2]);
                        } else {
                            values[0] = Double.parseDouble(fields[2]);
                        }
                        neighborMap.put(termName + "." + aName, values);
                        break;
                    }
                    case "GROUPCORR": {
                        String aName = fields[0];
                        String segment = fields[4];
                        int relPos = Integer.parseInt(fields[1]);
                        String centerAA = fields[2];
                        String neighborType = fields[3];
                        Double value1 = Double.parseDouble(fields[5]);
                        Double value2 = Double.parseDouble(fields[6]);

                        CorrComb corrComb = new CorrComb(relPos, centerAA, neighborType, value1, value2);
                        if (!corrCombMap.containsKey(aName)) {
                            corrCombMap.put(aName, new HashMap<String, CorrComb>());
                        }
                        Map<String, CorrComb> segMap = corrCombMap.get(aName);
                        segMap.put(segment, corrComb);
                        break;
                    }
                    case "TEMPCORRS": {
                        if (fields[0].equals("aa")) {
                            atomNames.clear();
                            for (int i = 1; i < fields.length; i++) {
                                atomNames.add(fields[i]);
                            }
                        } else {
                            String aaName = fields[0];
                            double[] values = new double[atomNames.size()];
                            for (int i = 1; i < fields.length; i++) {
                                double value = Double.parseDouble(fields[i]);
                                tempMap.put(aaName + "." + atomNames.get(i - 1), value);
                            }
                            break;
                        }
                    }

                    default: {
                    }
                }
            }
        }
    }

    public static double calcDisorderScale(double contactSum, double[] minMax) {
        double sValue = -2.0 * (contactSum - minMax[0]) / (minMax[1] - minMax[0]);
        double eValue = Math.exp(sValue);
        return (1.0 - eValue) / (1.0 + eValue);
    }

    public static boolean checkAngles(Double... values) {
        for (Double value : values) {
            if ((value == null) || Double.isNaN(value) || Double.isInfinite(value)) {
                return false;
            }
        }
        return true;
    }

    public void predict(int iRef, int structureNum) throws InvalidMoleculeException, IOException {
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                predict(polymer, iRef, structureNum);
            }
        }
    }

    public void predict(Polymer polymer, int iRef, int structureNum) throws IOException {
        for (Residue residue : polymer.getResidues()) {
            predict(residue, iRef, structureNum);
        }
    }

    Optional<String> getAtomNameType(Atom atom) {
        Optional<String> atomType = Optional.empty();
        String aName = atom.getName();
        int aLen = aName.length();
        String useName = null;
        if (atom.isMethyl()) {
            if (atom.getAtomicNumber() == 1) {
                if (atom.isFirstInMethyl()) {
                    if (aLen == 4) {
                        useName = "MH" + aName.substring(1, 3);
                    } else {
                        useName = "MH" + aName.charAt(1);
                    }
                }
            }
        } else if (atom.isMethylCarbon()) {
            if (aLen == 3) {
                useName = "MC" + aName.substring(1, 3);
            } else {
                useName = "MC" + aName.charAt(1);
            }
        } else if (atom.isAAAromatic()) {
            if (atom.getAtomicNumber() == 1) {
                useName = "AH" + aName.charAt(1);
            } else {
                useName = "AC" + aName.charAt(1);
            }
        } else {
            useName = aName;
        }
        if (useName != null) {
            useName += "_" + atom.getEntity().getName();
        }
        if ((useName != null) && atomTypes.contains(useName)) {
            atomType = Optional.of(useName);
        }
        return atomType;
    }

    public void predict(Residue residue, int iRef, int structureNum) throws IOException {
        if (values == null) {
            loadCoefficients();
        }
        Map<String, Double> valueMap = propertyGenerator.getValues();
        Polymer polymer = residue.getPolymer();
        if (propertyGenerator.getResidueProperties(polymer, residue, structureNum)) {
            for (Atom atom : residue.getAtoms()) {
                Optional<String> atomTypeOpt = getAtomNameType(atom);
                atomTypeOpt.ifPresent(atomType -> {
                    propertyGenerator.getAtomProperties(atom, structureNum);
                    String type = atomType;
                    Integer jType = aaMap.get(type);
                    if (jType != null) {
                        double[] coefs = values[jType];
                        double[] minMax = minMaxMap.get(type);
                        ProteinPredictorResult predResult
                                = ProteinPredictorGen.predict(valueMap,
                                coefs, minMax, reportAtom != null);
                        double value = predResult.ppm;
                        value = Math.round(value * 100) / 100.0;
                        double rms = getRMS(type);
                        if (iRef < 0) {
                            atom.setRefPPM(-iRef - 1, value);
                            atom.setRefError(-iRef - 1, rms);
                        } else {
                            atom.setPPM(iRef, value);
                            atom.setPPMError(iRef, rms);
                        }

                        if ((reportAtom != null) && atom.getFullName().equals(reportAtom)) {
                            dumpResult(predResult);
                        }
                    }
                });
            }

        }
    }

    void dumpResult(ProteinPredictorResult predResult) {
        for (int i = 0; i < predResult.attrs.length; i++) {
            System.out.println(attrNames.get(i) + " " + predResult.coefs[i] + " " + predResult.attrs[i]);
        }
    }

    private void initMinMax() throws IOException {
        InputStream iStream = this.getClass().getResourceAsStream("/data/predict/protein/fitOutput.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.contains("Avg")) {
                    continue;
                }
                String[] fields = line.split("\t");
                double[] minmax = new double[2];
                String name = fields[0].trim();
                double rms = Double.parseDouble(fields[2]);
                minmax[0] = Double.parseDouble(fields[4]);
                minmax[1] = Double.parseDouble(fields[5]);
                minMaxMap.put(name, minmax);
                rmsMap.put(name, rms);
                atomTypes.add(name);
            }
        }
    }

    double getRMS(String atomType) {
        double rms = 1.0;
        if (rmsMap.containsKey(atomType)) {
            rms = rmsMap.get(atomType);
        } else {
            if (atomType.startsWith(("H"))) {
                rms = 0.5;
            } else if (atomType.startsWith("C")) {
                rms = 1.0;
            } else if (atomType.startsWith("N")) {
                rms = 3.0;
            } else if (atomType.startsWith("MH")) {
                rms = 0.5;
            } else if (atomType.startsWith("MC")) {
                rms = 1.0;
            }
        }
        return rms;
    }

    public static double getRandomCoilError(Atom atom) {
        String aName = atom.getName();
        String scaleName = aName;
        if (aName.length() > 2) {
            scaleName = aName.substring(0, 2);
        }
        double scale;
        if (RANDOM_SCALES.containsKey(scaleName)) {
            scale = RANDOM_SCALES.get(scaleName);
        } else if (RANDOM_SCALES.containsKey(scaleName.substring(0, 1))) {
            scale = RANDOM_SCALES.get(scaleName.substring(0, 1));
        } else {
            scale = 1.0;
        }
        return scale;

    }

    String convert3To1(String name) {
        if (name.equals("MSE")) {
            return "M";
        } else {
            return PDBAtomParser.convert3To1(name);
        }
    }

    public void predictRandom(Molecule molecule, int iRef) throws IOException {
        String[] aNames = {"C", "CA", "CB", "HA", "H", "N", "HB"};
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                for (Residue residue : polymer.getResidues()) {
                    for (String aName : aNames) {
                        var aNames2 = new ArrayList<String>();
                        if (residue.getName().equals("GLY") && aName.equals("HA")) {
                            aNames2.add("HA2");
                            aNames2.add("HA3");
                        } else if (aName.equals("HB")) {
                            aNames2.add("HB");
                            aNames2.add("HB2");
                            aNames2.add("HB3");
                        } else {
                            aNames2.add(aName);
                        }
                        for (var aName2 : aNames2) {
                            Atom atom = residue.getAtom(aName2);
                            if (atom != null) {
                                Double ppm = predictRandom(residue, aName, 298.0);
                                if (ppm != null) {
                                    double errValue = getRandomCoilError(atom);
                                    if (iRef < 0) {
                                        atom.setRefPPM(-iRef - 1, ppm);
                                        atom.setRefError(-iRef - 1, errValue);
                                    } else {
                                        atom.setPPM(iRef, ppm);
                                        atom.setPPMError(iRef, errValue);
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    public Double predictRandom(Residue residue, String aName, double tempK) throws IOException {
        Double result = null;
        Residue prevRes = residue.getPrevious();
        Residue nextRes = residue.getNext();
        if ((prevRes != null) && (nextRes != null)) {
            String[] aaChars = new String[5];
            aaChars[1] = convert3To1(prevRes.getName());
            aaChars[2] = convert3To1(residue.getName());
            aaChars[3] = convert3To1(nextRes.getName());
            Residue prev2Res = prevRes.getPrevious();
            Residue next2Res = nextRes.getNext();
            if (prev2Res == null) {
                aaChars[0] = "n";
            } else {
                aaChars[0] = convert3To1(prev2Res.getName());
            }
            if (next2Res == null) {
                aaChars[4] = "c";
            } else {
                aaChars[4] = convert3To1(next2Res.getName());
            }
            for (String aaChar : aaChars) {
                if (aaChar == null) {
                    System.out.println("No sgnl res " + residue.getName() + " " + aName);
                    return null;
                }
            }
            result = predictRandom(aaChars, aName, tempK);
        }
        return result;
    }

    public List<Map<String, Double>> predictRandomSequence(String sequence, double tempK) throws IOException {
        String[] aNames = {"C", "CA", "CB", "HA", "H", "N", "HB"};
        int nResidues = sequence.length();
        String[] seq = sequence.split("");
        List<Map<String, Double>> result = new ArrayList<>();
        for (int i = 0; i < nResidues - 2; i++) {
            String[] seqChars = new String[5];
            seqChars[1] = seq[i];
            seqChars[2] = seq[i + 1];
            seqChars[3] = seq[i + 2];
            if (i == 0) {
                seqChars[0] = "n";
            } else {
                seqChars[0] = seq[i];
            }
            if (i == nResidues - 3) {
                seqChars[4] = "c";
            } else {
                seqChars[4] = seq[i + 3];
            }
            Map<String, Double> aaMap = new HashMap<>();
            for (String aName : aNames) {
                Double value = predictRandom(seqChars, aName, tempK);
                aaMap.put(aName, value);
            }
            result.add(aaMap);
        }
        return result;
    }

    public Double predictRandom(String[] aaChars, String aName, double tempK) throws IOException {
        String[] groups = {"G", "P", "FYW", "LIVMCA", "KR", "DE"};
        String[] labels = {"G", "P", "r", "a", "+", "-", "p"};
        Double result = null;
        if (shiftMap.isEmpty()) {
            loadPotenci();
        }
        int[] neighorPositions = {2, 1, -1, -2};
        String aaKey = aaChars[2] + "." + aName;
        if (shiftMap.containsKey(aaKey)) {
            result = shiftMap.get(aaKey);
            for (int i = 0; i < neighorPositions.length; i++) {
                String neighborName = aaChars[2 + neighorPositions[i]];
                String key = neighborName + "." + aName;
                double[] neighborCorrs = neighborMap.get(key);
                if (neighborCorrs == null) {
                    System.out.println("no neighbor " + key);
                } else {
                    result += neighborCorrs[i];
                }
            }
            List<String> groupStr = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String aaName = aaChars[i];
                boolean found = false;
                int j = 0;
                for (String group : groups) {
                    if (group.contains(aaName)) {
                        groupStr.add(labels[j]);
                        found = true;
                        break;
                    }
                    j++;
                }
                if (!found) {
                    groupStr.add("p");
                }
            }
            Map<String, CorrComb> segMap = corrCombMap.get(aName);
            String centerType = groupStr.get(2);
            for (String segment : segMap.keySet()) {
                CorrComb corrComb = segMap.get(segment);
                if (corrComb.centerAA.equals(centerType) && groupStr.get(2 + corrComb.relPos).equals(corrComb.neighborType)) {
                    if ((!groupStr.get(2).equals("p") || !corrComb.neighborType.equals("p")) || "ST".contains(aaChars[2])) {
                        result += corrComb.value1;
                    }
                }
            }
            if (tempMap.containsKey(aaKey)) {
                double tempFactor = tempMap.get(aaKey);
                result += (tempFactor / 1000.0) * (tempK - 298.0);
            }
        }

        return result;
    }

}
