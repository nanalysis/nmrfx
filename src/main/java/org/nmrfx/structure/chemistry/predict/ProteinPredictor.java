package org.nmrfx.structure.chemistry.predict;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.energy.PropertyGenerator;

public class ProteinPredictor {

    PropertyGenerator propertyGenerator;
    Map<String, Integer> aaMap = new HashMap<>();
    Map<String, double[]> minMaxMap = new HashMap<>();
    ArrayList<String> attrNames = new ArrayList<>();

    double[][] values = null;

    public void init(Molecule mol) throws InvalidMoleculeException, IOException {
        propertyGenerator = new PropertyGenerator();
        propertyGenerator.init(mol);
    }

    void loadAttrMap(String[] fields) {
        for (int i = 1; i < fields.length; i++) {
            aaMap.put(fields[i].trim(), i - 1);
        }
        System.out.println(aaMap.toString());

    }

    void loadCoefficients() throws IOException {
        InputStream iStream = this.getClass().getResourceAsStream("/data/predict/protein/coefs3d.txt");
        List<String[]> lines = new ArrayList<>();
        boolean firstLine = true;
        System.out.println("resource " + iStream);
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
        System.out.println(attrNames.toString());
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

    public void predict(Polymer polymer, List<String> atomNames) throws IOException {
        for (Residue residue : polymer.getResidues()) {
            predict(residue, atomNames);
        }

    }

    public void predict(Residue residue, List<String> atomNames) throws IOException {
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
                            double value = ProteinPredictorGen.predict(valueMap, coefs, minMax);
                            System.out.println(residue.getNumber() + " " + residue.getName() + " " + subName + " " + atom + " " + value);
                        }
                    }
                }
            }
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

    public static double predict(Map<String, Double> valueMap, double[] coefs, double[] minMax) {
        double[] attrValue = new double[109];
        Double GLN = valueMap.get("GLN");
        Double psiC = valueMap.get("psiC");
        Double h3 = valueMap.get("h3");
        Double psiS = valueMap.get("psiS");
        Double HPH_P = valueMap.get("HPH_P");
        Double ARO_P = valueMap.get("ARO_P");
        Double ARO_S = valueMap.get("ARO_S");
        Double hshift3 = valueMap.get("hshift3");
        Double hshift2 = valueMap.get("hshift2");
        Double hshift1 = valueMap.get("hshift1");
        Double HPH_S = valueMap.get("HPH_S");
        Double CHRG_S = valueMap.get("CHRG_S");
        Double CHRG_P = valueMap.get("CHRG_P");
        Double eshift = valueMap.get("eshift");
        Double chi2C = valueMap.get("chi2C");
        Double SER = valueMap.get("SER");
        Double LYS = valueMap.get("LYS");
        Double psiP = valueMap.get("psiP");
        Double chiC = valueMap.get("chiC");
        Double ringShift = valueMap.get("ringShift");
        Double BULK_S = valueMap.get("BULK_S");
        Double ASN = valueMap.get("ASN");
        Double BULK_P = valueMap.get("BULK_P");
        Double phiC = valueMap.get("phiC");
        Double VAL = valueMap.get("VAL");
        Double DIS = valueMap.get("DIS");
        Double intercept = valueMap.get("intercept");
        Double phiS = valueMap.get("phiS");
        Double phiP = valueMap.get("phiP");
        Double TRP = valueMap.get("TRP");
        Double omega = valueMap.get("omega");
        Double GLY = valueMap.get("GLY");
        Double PHE = valueMap.get("PHE");
        Double MET = valueMap.get("MET");
        Double LEU = valueMap.get("LEU");
        Double PRO_P = valueMap.get("PRO_P");
        Double PRO_S = valueMap.get("PRO_S");
        Double TYR = valueMap.get("TYR");
        if (checkAngles(chiC, chi2C)) {
            attrValue[0] = Math.cos(chiC) * Math.sin(chi2C);
        } else {
            attrValue[0] = 0.0;
        }
        if (checkAngles(psiS)) {
            attrValue[1] = Math.cos(2 * psiS);
        } else {
            attrValue[1] = 0.0;
        }
        if (checkAngles(psiP)) {
            attrValue[2] = Math.sin(3 * psiP);
        } else {
            attrValue[2] = 0.0;
        }
        if (checkAngles(chi2C)) {
            attrValue[3] = Math.sin(2 * chi2C);
        } else {
            attrValue[3] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[4] = Math.sin(phiC) * PRO_P;
        } else {
            attrValue[4] = 0.0;
        }
        if (checkAngles(chiC)) {
            attrValue[5] = Math.cos(2 * chiC);
        } else {
            attrValue[5] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[6] = Math.cos(psiC) * BULK_S;
        } else {
            attrValue[6] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[7] = Math.sin(3 * psiC);
        } else {
            attrValue[7] = 0.0;
        }
        if (checkAngles(psiP)) {
            attrValue[8] = Math.cos(psiP);
        } else {
            attrValue[8] = 0.0;
        }
        if (checkAngles(phiP)) {
            attrValue[9] = Math.cos(2 * phiP);
        } else {
            attrValue[9] = 0.0;
        }
        attrValue[10] = LYS;
        if (checkAngles(phiC, chiC)) {
            attrValue[11] = Math.cos(phiC) * Math.cos(chiC);
        } else {
            attrValue[11] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[12] = Math.sin(psiC) * ARO_S;
        } else {
            attrValue[12] = 0.0;
        }
        if (checkAngles(phiP)) {
            attrValue[13] = Math.sin(3 * phiP);
        } else {
            attrValue[13] = 0.0;
        }
        attrValue[14] = h3;
        if (checkAngles(psiC, chi2C)) {
            attrValue[15] = Math.sin(psiC) * Math.cos(chi2C);
        } else {
            attrValue[15] = 0.0;
        }
        if (checkAngles(chi2C)) {
            attrValue[16] = Math.sin(chi2C);
        } else {
            attrValue[16] = 0.0;
        }
        attrValue[17] = VAL;
        if (checkAngles(phiP)) {
            attrValue[18] = Math.cos(phiP);
        } else {
            attrValue[18] = 0.0;
        }
        if (checkAngles(psiS)) {
            attrValue[19] = Math.sin(3 * psiS);
        } else {
            attrValue[19] = 0.0;
        }
        if (checkAngles(psiS)) {
            attrValue[20] = Math.cos(3 * psiS);
        } else {
            attrValue[20] = 0.0;
        }
        if (checkAngles(psiC, chiC)) {
            attrValue[21] = Math.cos(psiC) * Math.cos(chiC);
        } else {
            attrValue[21] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[22] = Math.cos(phiC) * BULK_P;
        } else {
            attrValue[22] = 0.0;
        }
        if (checkAngles(phiS)) {
            attrValue[23] = Math.cos(phiS);
        } else {
            attrValue[23] = 0.0;
        }
        attrValue[24] = ringShift;
        attrValue[25] = PHE;
        if (checkAngles(psiC)) {
            attrValue[26] = Math.cos(2 * psiC);
        } else {
            attrValue[26] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[27] = Math.cos(phiC) * ARO_P;
        } else {
            attrValue[27] = 0.0;
        }
        if (checkAngles(phiC, chi2C)) {
            attrValue[28] = Math.cos(phiC) * Math.cos(chi2C);
        } else {
            attrValue[28] = 0.0;
        }
        attrValue[29] = TYR;
        if (checkAngles(psiC, phiC)) {
            attrValue[30] = Math.cos(psiC) * Math.cos(phiC);
        } else {
            attrValue[30] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[31] = Math.sin(2 * psiC);
        } else {
            attrValue[31] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[32] = Math.sin(phiC) * ARO_P;
        } else {
            attrValue[32] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[33] = Math.cos(psiC) * ARO_S;
        } else {
            attrValue[33] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[34] = Math.cos(psiC) * CHRG_S;
        } else {
            attrValue[34] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[35] = Math.cos(phiC) * CHRG_P;
        } else {
            attrValue[35] = 0.0;
        }
        if (checkAngles(phiC, chi2C)) {
            attrValue[36] = Math.sin(phiC) * Math.cos(chi2C);
        } else {
            attrValue[36] = 0.0;
        }
        if (checkAngles(chi2C)) {
            attrValue[37] = Math.cos(chi2C);
        } else {
            attrValue[37] = 0.0;
        }
        attrValue[38] = hshift3;
        attrValue[39] = hshift2;
        attrValue[40] = hshift1;
        if (checkAngles(phiC, chiC)) {
            attrValue[41] = Math.sin(phiC) * Math.sin(chiC);
        } else {
            attrValue[41] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[42] = Math.sin(psiC) * PRO_S;
        } else {
            attrValue[42] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[43] = Math.cos(phiC) * HPH_P;
        } else {
            attrValue[43] = 0.0;
        }
        if (checkAngles(phiC, chi2C)) {
            attrValue[44] = Math.sin(phiC) * Math.sin(chi2C);
        } else {
            attrValue[44] = 0.0;
        }
        if (checkAngles(phiP)) {
            attrValue[45] = Math.sin(phiP);
        } else {
            attrValue[45] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[46] = Math.sin(phiC) * HPH_P;
        } else {
            attrValue[46] = 0.0;
        }
        if (checkAngles(omega)) {
            attrValue[47] = Math.sin(omega);
        } else {
            attrValue[47] = 0.0;
        }
        if (checkAngles(chiC, chi2C)) {
            attrValue[48] = Math.cos(chiC) * Math.cos(chi2C);
        } else {
            attrValue[48] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[49] = Math.cos(3 * psiC);
        } else {
            attrValue[49] = 0.0;
        }
        if (checkAngles(psiS)) {
            attrValue[50] = Math.sin(psiS);
        } else {
            attrValue[50] = 0.0;
        }
        if (checkAngles(psiP)) {
            attrValue[51] = Math.cos(2 * psiP);
        } else {
            attrValue[51] = 0.0;
        }
        attrValue[52] = 0.0;
        int interceptCoef = 52;
        attrValue[53] = TRP;
        if (checkAngles(phiC)) {
            attrValue[54] = Math.cos(2 * phiC);
        } else {
            attrValue[54] = 0.0;
        }
        if (checkAngles(psiC, chiC)) {
            attrValue[55] = Math.sin(psiC) * Math.sin(chiC);
        } else {
            attrValue[55] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[56] = Math.sin(psiC) * HPH_S;
        } else {
            attrValue[56] = 0.0;
        }
        if (checkAngles(phiS)) {
            attrValue[57] = Math.cos(2 * phiS);
        } else {
            attrValue[57] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[58] = Math.cos(psiC) * HPH_S;
        } else {
            attrValue[58] = 0.0;
        }
        if (checkAngles(omega)) {
            attrValue[59] = Math.cos(omega);
        } else {
            attrValue[59] = 0.0;
        }
        if (checkAngles(psiS)) {
            attrValue[60] = Math.sin(2 * psiS);
        } else {
            attrValue[60] = 0.0;
        }
        if (checkAngles(phiS)) {
            attrValue[61] = Math.sin(phiS);
        } else {
            attrValue[61] = 0.0;
        }
        if (checkAngles(phiC, chi2C)) {
            attrValue[62] = Math.cos(phiC) * Math.sin(chi2C);
        } else {
            attrValue[62] = 0.0;
        }
        attrValue[63] = GLN;
        if (checkAngles(phiP)) {
            attrValue[64] = Math.sin(2 * phiP);
        } else {
            attrValue[64] = 0.0;
        }
        if (checkAngles(psiP)) {
            attrValue[65] = Math.sin(psiP);
        } else {
            attrValue[65] = 0.0;
        }
        attrValue[66] = GLY;
        if (checkAngles(phiS)) {
            attrValue[67] = Math.cos(3 * phiS);
        } else {
            attrValue[67] = 0.0;
        }
        if (checkAngles(psiC, chi2C)) {
            attrValue[68] = Math.cos(psiC) * Math.sin(chi2C);
        } else {
            attrValue[68] = 0.0;
        }
        if (checkAngles(psiS)) {
            attrValue[69] = Math.cos(psiS);
        } else {
            attrValue[69] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[70] = Math.sin(psiC) * CHRG_S;
        } else {
            attrValue[70] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[71] = Math.sin(2 * phiC);
        } else {
            attrValue[71] = 0.0;
        }
        if (checkAngles(psiC, chiC)) {
            attrValue[72] = Math.cos(psiC) * Math.sin(chiC);
        } else {
            attrValue[72] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[73] = Math.cos(phiC);
        } else {
            attrValue[73] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[74] = Math.sin(phiC);
        } else {
            attrValue[74] = 0.0;
        }
        attrValue[75] = SER;
        if (checkAngles(phiC)) {
            attrValue[76] = Math.cos(3 * phiC);
        } else {
            attrValue[76] = 0.0;
        }
        if (checkAngles(chiC, chi2C)) {
            attrValue[77] = Math.sin(chiC) * Math.sin(chi2C);
        } else {
            attrValue[77] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[78] = Math.cos(psiC) * PRO_S;
        } else {
            attrValue[78] = 0.0;
        }
        if (checkAngles(psiC, chiC)) {
            attrValue[79] = Math.sin(psiC) * Math.cos(chiC);
        } else {
            attrValue[79] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[80] = Math.sin(psiC);
        } else {
            attrValue[80] = 0.0;
        }
        if (checkAngles(psiP)) {
            attrValue[81] = Math.sin(2 * psiP);
        } else {
            attrValue[81] = 0.0;
        }
        attrValue[82] = ASN;
        if (checkAngles(psiC)) {
            attrValue[83] = Math.cos(psiC);
        } else {
            attrValue[83] = 0.0;
        }
        if (checkAngles(phiS)) {
            attrValue[84] = Math.sin(2 * phiS);
        } else {
            attrValue[84] = 0.0;
        }
        if (checkAngles(chiC, chi2C)) {
            attrValue[85] = Math.sin(chiC) * Math.cos(chi2C);
        } else {
            attrValue[85] = 0.0;
        }
        if (checkAngles(chiC)) {
            attrValue[86] = Math.cos(chiC);
        } else {
            attrValue[86] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[87] = Math.cos(phiC) * PRO_P;
        } else {
            attrValue[87] = 0.0;
        }
        attrValue[88] = MET;
        attrValue[89] = LEU;
        if (checkAngles(phiC)) {
            attrValue[90] = Math.sin(phiC) * CHRG_P;
        } else {
            attrValue[90] = 0.0;
        }
        if (checkAngles(psiC, phiC)) {
            attrValue[91] = Math.cos(psiC) * Math.sin(phiC);
        } else {
            attrValue[91] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[92] = Math.sin(3 * phiC);
        } else {
            attrValue[92] = 0.0;
        }
        if (checkAngles(psiP)) {
            attrValue[93] = Math.cos(3 * psiP);
        } else {
            attrValue[93] = 0.0;
        }
        attrValue[94] = DIS;
        if (eshift != null) {
            attrValue[95] = eshift;
        }
        if (checkAngles(phiC, chiC)) {
            attrValue[96] = Math.sin(phiC) * Math.cos(chiC);
        } else {
            attrValue[96] = 0.0;
        }
        if (checkAngles(psiC, phiC)) {
            attrValue[97] = Math.sin(psiC) * Math.sin(phiC);
        } else {
            attrValue[97] = 0.0;
        }
        if (checkAngles(chiC)) {
            attrValue[98] = Math.sin(chiC);
        } else {
            attrValue[98] = 0.0;
        }
        if (checkAngles(phiC)) {
            attrValue[99] = Math.sin(phiC) * BULK_P;
        } else {
            attrValue[99] = 0.0;
        }
        if (checkAngles(psiC, phiC)) {
            attrValue[100] = Math.sin(psiC) * Math.cos(phiC);
        } else {
            attrValue[100] = 0.0;
        }
        if (checkAngles(psiC, chi2C)) {
            attrValue[101] = Math.sin(psiC) * Math.sin(chi2C);
        } else {
            attrValue[101] = 0.0;
        }
        if (checkAngles(psiC, chi2C)) {
            attrValue[102] = Math.cos(psiC) * Math.cos(chi2C);
        } else {
            attrValue[102] = 0.0;
        }
        if (checkAngles(chi2C)) {
            attrValue[103] = Math.cos(2 * chi2C);
        } else {
            attrValue[103] = 0.0;
        }
        if (checkAngles(phiP)) {
            attrValue[104] = Math.cos(3 * phiP);
        } else {
            attrValue[104] = 0.0;
        }
        if (checkAngles(phiC, chiC)) {
            attrValue[105] = Math.cos(phiC) * Math.sin(chiC);
        } else {
            attrValue[105] = 0.0;
        }
        if (checkAngles(chiC)) {
            attrValue[106] = Math.sin(2 * chiC);
        } else {
            attrValue[106] = 0.0;
        }
        if (checkAngles(phiS)) {
            attrValue[107] = Math.sin(3 * phiS);
        } else {
            attrValue[107] = 0.0;
        }
        if (checkAngles(psiC)) {
            attrValue[108] = Math.sin(psiC) * BULK_S;
        } else {
            attrValue[108] = 0.0;
        }

        double contactSum = valueMap.get("contacts");
        double scale = calcDisorderScale(contactSum, minMax);
        double sum = coefs[interceptCoef];
        for (int i = 0; i < attrValue.length; i++) {
            sum += scale * attrValue[i] * coefs[i];
        }
        return sum;
    }

}
