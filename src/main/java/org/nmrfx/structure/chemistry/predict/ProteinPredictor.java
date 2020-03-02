package org.nmrfx.structure.chemistry.predict;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.energy.PropertyGenerator;
import org.nmrfx.structure.chemistry.predict.ProteinPredictorResult;

public class ProteinPredictor {

    PropertyGenerator propertyGenerator;
    Map<String, Integer> aaMap = new HashMap<>();
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
            for (int i = 1; i < nTypes; i++) {
                values[i - 1][j] = Double.parseDouble(lines.get(j)[i]);
            }
        }
        initMinMax();
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
        List<String> atomNames = Arrays.asList("N", "CA", "CB", "CG", "C", "H", "HA", "HB", "HG");
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isPeptide()) {
                predict(polymer, atomNames, iRef);
            }
        }
    }

    public void predict(Polymer polymer, List<String> atomNames, int iRef) throws IOException {
        for (Residue residue : polymer.getResidues()) {
            predict(residue, atomNames, iRef);
        }

    }

    public void predict(Residue residue, List<String> atomNames, int iRef) throws IOException {
        if (values == null) {
            loadCoefficients();
        }
        Map<String, Double> valueMap = propertyGenerator.getValues();
        String[] subTypes = {"", "2", "3"};
        Polymer polymer = residue.getPolymer();
        if (propertyGenerator.getResidueProperties(polymer, residue)) {

            for (String atomName : atomNames) {
                for (String subType : subTypes) {
                    String subName = atomName + subType;
                    Atom atom = residue.getAtom(subName);
                    if (atom != null) {
                        propertyGenerator.getAtomProperties(atom);
                        String type = atomName + '_' + residue.getName();
                        Integer jType = aaMap.get(type);
                        if (jType != null) {
                            double[] coefs = values[jType];
                            double[] minMax = minMaxMap.get(atomName);
                            ProteinPredictorResult predResult =
                                    ProteinPredictorGen.predict(valueMap,
                                            coefs, minMax, reportAtom != null);
                            double value = predResult.ppm;
                            atom.setRefPPM(iRef, value);
                            if ((reportAtom != null) && atom.getFullName().equals(reportAtom)) {
                                dumpResult(predResult);
                            }
                            //System.out.println(residue.getNumber() + " " + residue.getName() + " " + subName + " " + atom + " " + value);
                        }
                    }
                }
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
}
