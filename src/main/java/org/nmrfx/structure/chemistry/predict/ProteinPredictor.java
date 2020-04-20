package org.nmrfx.structure.chemistry.predict;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.energy.PropertyGenerator;

public class ProteinPredictor {

    static final Set<String> atomTypes = new HashSet<>();

    static {
        Collections.addAll(atomTypes, "MHB", "MHG", "MHD", "MHE", "MCB",
                "MCG", "MCD", "MCE", "C", "CA", "CB", "N", "H", "HA", "HB", "HG", "HD",
                "HE", "HZ", "CG", "CD", "CE", "CZ");
    }
    PropertyGenerator propertyGenerator;
    Map<String, Integer> aaMap = new HashMap<>();
    Map<String, Double> rmsMap = new HashMap<>();
    Map<String, double[]> minMaxMap = new HashMap<>();
    ArrayList<String> attrNames = new ArrayList<>();
    Molecule molecule = null;

    double[][] values = null;

    String reportAtom = null;

    public void init(Molecule mol) throws InvalidMoleculeException, IOException {
        propertyGenerator = new PropertyGenerator();
        propertyGenerator.init(mol);
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
        initRMS();
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

    public void predict(int iRef) throws InvalidMoleculeException, IOException {
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                predict(polymer, iRef);
            }
        }
    }

    public void predict(Polymer polymer, int iRef) throws IOException {
        for (Residue residue : polymer.getResidues()) {
            predict(residue, iRef);
        }

    }

    Optional<String> getAtomNameType(Atom atom) {
        Optional<String> atomType = Optional.empty();
        String aName = atom.getName();
        String useName = null;
        if (atom.isMethyl()) {
            if (atom.getAtomicNumber() == 1) {
                if (atom.isFirstInMethyl()) {
                    useName = "MH" + aName.charAt(1);
                }
            }
        } else if (atom.isMethylCarbon()) {
            useName = "MC" + aName.charAt(1);
        } else {
            int aLen = aName.length();
            if (aLen > 2) {
                aName = aName.substring(0, 2);
            }
            useName = aName;
        }
        if ((useName != null) && atomTypes.contains(useName)) {
            atomType = Optional.of(useName);
        }
        return atomType;
    }

    public void predict(Residue residue, int iRef) throws IOException {
        if (values == null) {
            loadCoefficients();
        }
        Map<String, Double> valueMap = propertyGenerator.getValues();
        Polymer polymer = residue.getPolymer();
        if (propertyGenerator.getResidueProperties(polymer, residue)) {
            for (Atom atom : residue.getAtoms()) {
                Optional<String> atomTypeOpt = getAtomNameType(atom);
                atomTypeOpt.ifPresent(atomType -> {
                    propertyGenerator.getAtomProperties(atom);
                    String type = atomType + '_' + residue.getName();
                    Integer jType = aaMap.get(type);
                    if (jType != null) {
                        double[] coefs = values[jType];
                        double[] minMax = minMaxMap.get(atomType);
                        ProteinPredictorResult predResult
                                = ProteinPredictorGen.predict(valueMap,
                                        coefs, minMax, reportAtom != null);
                        double value = predResult.ppm;
                        value = Math.round(value * 100) / 100.0;
                        double rms = getRMS(atomType);
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
                        //System.out.println(residue.getNumber() + " " + residue.getName() + " " + subName + " " + atom + " " + value);
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
        InputStream iStream = this.getClass().getResourceAsStream("/data/predict/protein/contact_minmax.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String[] fields = line.split("\t");
                double[] minmax = new double[2];
                String name = fields[0];
                minmax[0] = Double.parseDouble(fields[1]);
                minmax[1] = Double.parseDouble(fields[2]);
                minMaxMap.put(name, minmax);
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

    private void initRMS() throws IOException {
        InputStream iStream = this.getClass().getResourceAsStream("/data/predict/protein/rms.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if ((line.length() > 2) && !line.startsWith("#")) {
                    String[] fields = line.split("\t");
                    if (fields.length > 1) {
                        String name = fields[0];
                        Double rms = Double.parseDouble(fields[1]);
                        rmsMap.put(name, rms);
                    }
                }
            }
        }
    }

}
