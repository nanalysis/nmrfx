package org.nmrfx.structure.chemistry.predict;

import java.util.Map;

public class ProteinPredictorGen {

    public static ProteinPredictorResult predict(Map<String, Double> valueMap, double[] coefs, double[] minMax, boolean explain) {
        double[] attrValue = new double[107];
        Double GLN = valueMap.get("GLN");
        Double psiC = valueMap.get("psiC");
        Double h3 = valueMap.get("h3");
        Double psiS = valueMap.get("psiS");
        Double GLY = valueMap.get("GLY");
        Double ARO_P = valueMap.get("ARO_P");
        Double ARO_S = valueMap.get("ARO_S");
        Double hshift3 = valueMap.get("hshift3");
        Double hshift2 = valueMap.get("hshift2");
        Double ring = valueMap.get("ring");
        Double CHRG_S = valueMap.get("CHRG_S");
        Double hshift1 = valueMap.get("hshift1");
        Double CHRG_P = valueMap.get("CHRG_P");
        Double eshift = valueMap.get("eshift");
        Double chi2C = valueMap.get("chi2C");
        Double SER = valueMap.get("SER");
        Double LYS = valueMap.get("LYS");
        Double psiP = valueMap.get("psiP");
        Double chiC = valueMap.get("chiC");
        Double DIS = valueMap.get("DIS");
        Double BULK_S = valueMap.get("BULK_S");
        Double ASN = valueMap.get("ASN");
        Double BULK_P = valueMap.get("BULK_P");
        Double phiC = valueMap.get("phiC");
        Double VAL = valueMap.get("VAL");
        Double HPHB_P = valueMap.get("HPHB_P");
        Double HPHB_S = valueMap.get("HPHB_S");
        Double intercept = valueMap.get("intercept");
        Double phiS = valueMap.get("phiS");
        Double phiP = valueMap.get("phiP");
        Double TRP = valueMap.get("TRP");
        Double PHE = valueMap.get("PHE");
        Double MET = valueMap.get("MET");
        Double LEU = valueMap.get("LEU");
        Double PRO_P = valueMap.get("PRO_P");
        Double PRO_S = valueMap.get("PRO_S");
        Double TYR = valueMap.get("TYR");
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[0] = Math.cos(chiC) * Math.sin(chi2C);
        } else {
            attrValue[0] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[1] = Math.cos(2 * psiS);
        } else {
            attrValue[1] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[2] = Math.sin(3 * psiP);
        } else {
            attrValue[2] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[3] = Math.sin(2 * chi2C);
        } else {
            attrValue[3] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[4] = Math.sin(phiC) * PRO_P;
        } else {
            attrValue[4] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[5] = Math.cos(2 * chiC);
        } else {
            attrValue[5] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[6] = Math.cos(psiC) * BULK_S;
        } else {
            attrValue[6] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[7] = Math.sin(3 * psiC);
        } else {
            attrValue[7] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[8] = Math.cos(psiP);
        } else {
            attrValue[8] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[9] = Math.cos(2 * phiP);
        } else {
            attrValue[9] = 0.0;
        }
        attrValue[10] = LYS;
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[11] = Math.cos(phiC) * Math.cos(chiC);
        } else {
            attrValue[11] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[12] = Math.sin(psiC) * ARO_S;
        } else {
            attrValue[12] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[13] = Math.sin(3 * phiP);
        } else {
            attrValue[13] = 0.0;
        }
        attrValue[14] = h3;
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[15] = Math.sin(psiC) * Math.cos(chi2C);
        } else {
            attrValue[15] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[16] = Math.sin(chi2C);
        } else {
            attrValue[16] = 0.0;
        }
        attrValue[17] = VAL;
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[18] = Math.cos(phiP);
        } else {
            attrValue[18] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[19] = Math.sin(3 * psiS);
        } else {
            attrValue[19] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[20] = Math.cos(3 * psiS);
        } else {
            attrValue[20] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[21] = Math.cos(psiC) * Math.cos(chiC);
        } else {
            attrValue[21] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[22] = Math.cos(phiC) * BULK_P;
        } else {
            attrValue[22] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[23] = Math.cos(phiS);
        } else {
            attrValue[23] = 0.0;
        }
        attrValue[24] = ring;
        attrValue[25] = PHE;
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[26] = Math.cos(2 * psiC);
        } else {
            attrValue[26] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[27] = Math.cos(phiC) * ARO_P;
        } else {
            attrValue[27] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[28] = Math.cos(phiC) * Math.cos(chi2C);
        } else {
            attrValue[28] = 0.0;
        }
        attrValue[29] = TYR;
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[30] = Math.cos(psiC) * Math.cos(phiC);
        } else {
            attrValue[30] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[31] = Math.sin(2 * psiC);
        } else {
            attrValue[31] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[32] = Math.sin(phiC) * ARO_P;
        } else {
            attrValue[32] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[33] = Math.cos(psiC) * ARO_S;
        } else {
            attrValue[33] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[34] = Math.cos(psiC) * CHRG_S;
        } else {
            attrValue[34] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[35] = Math.cos(psiC) * HPHB_S;
        } else {
            attrValue[35] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[36] = Math.cos(phiC) * CHRG_P;
        } else {
            attrValue[36] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[37] = Math.sin(phiC) * Math.cos(chi2C);
        } else {
            attrValue[37] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[38] = Math.cos(chi2C);
        } else {
            attrValue[38] = 0.0;
        }
        attrValue[39] = hshift3;
        attrValue[40] = hshift2;
        attrValue[41] = hshift1;
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[42] = Math.sin(phiC) * Math.sin(chiC);
        } else {
            attrValue[42] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[43] = Math.sin(psiC) * PRO_S;
        } else {
            attrValue[43] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[44] = Math.sin(phiC) * Math.sin(chi2C);
        } else {
            attrValue[44] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[45] = Math.sin(phiP);
        } else {
            attrValue[45] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[46] = Math.cos(chiC) * Math.cos(chi2C);
        } else {
            attrValue[46] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[47] = Math.cos(3 * psiC);
        } else {
            attrValue[47] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[48] = Math.sin(psiS);
        } else {
            attrValue[48] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[49] = Math.cos(2 * psiP);
        } else {
            attrValue[49] = 0.0;
        }
        attrValue[50] = 0.0;
        int interceptCoef = 50;
        attrValue[51] = TRP;
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[52] = Math.cos(2 * phiC);
        } else {
            attrValue[52] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[53] = Math.sin(psiC) * Math.sin(chiC);
        } else {
            attrValue[53] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[54] = Math.sin(psiC) * HPHB_S;
        } else {
            attrValue[54] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[55] = Math.cos(2 * phiS);
        } else {
            attrValue[55] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[56] = Math.sin(2 * psiS);
        } else {
            attrValue[56] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[57] = Math.sin(phiS);
        } else {
            attrValue[57] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[58] = Math.cos(phiC) * Math.sin(chi2C);
        } else {
            attrValue[58] = 0.0;
        }
        attrValue[59] = GLN;
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[60] = Math.sin(2 * phiP);
        } else {
            attrValue[60] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[61] = Math.sin(psiP);
        } else {
            attrValue[61] = 0.0;
        }
        attrValue[62] = GLY;
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[63] = Math.cos(3 * phiS);
        } else {
            attrValue[63] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[64] = Math.cos(psiC) * Math.sin(chi2C);
        } else {
            attrValue[64] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[65] = Math.cos(psiS);
        } else {
            attrValue[65] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[66] = Math.sin(psiC) * CHRG_S;
        } else {
            attrValue[66] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[67] = Math.sin(2 * phiC);
        } else {
            attrValue[67] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[68] = Math.cos(psiC) * Math.sin(chiC);
        } else {
            attrValue[68] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[69] = Math.cos(phiC);
        } else {
            attrValue[69] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[70] = Math.sin(phiC);
        } else {
            attrValue[70] = 0.0;
        }
        attrValue[71] = SER;
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[72] = Math.cos(3 * phiC);
        } else {
            attrValue[72] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[73] = Math.sin(chiC) * Math.sin(chi2C);
        } else {
            attrValue[73] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[74] = Math.cos(psiC) * PRO_S;
        } else {
            attrValue[74] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[75] = Math.sin(psiC) * Math.cos(chiC);
        } else {
            attrValue[75] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[76] = Math.sin(psiC);
        } else {
            attrValue[76] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[77] = Math.sin(2 * psiP);
        } else {
            attrValue[77] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[78] = Math.cos(phiC) * HPHB_P;
        } else {
            attrValue[78] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[79] = Math.cos(psiC);
        } else {
            attrValue[79] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[80] = Math.sin(2 * phiS);
        } else {
            attrValue[80] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[81] = Math.sin(chiC) * Math.cos(chi2C);
        } else {
            attrValue[81] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[82] = Math.cos(chiC);
        } else {
            attrValue[82] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[83] = Math.cos(phiC) * PRO_P;
        } else {
            attrValue[83] = 0.0;
        }
        attrValue[84] = ASN;
        attrValue[85] = MET;
        attrValue[86] = LEU;
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[87] = Math.sin(phiC) * CHRG_P;
        } else {
            attrValue[87] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[88] = Math.cos(psiC) * Math.sin(phiC);
        } else {
            attrValue[88] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[89] = Math.sin(3 * phiC);
        } else {
            attrValue[89] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[90] = Math.cos(3 * psiP);
        } else {
            attrValue[90] = 0.0;
        }
        attrValue[91] = DIS;
        if (eshift != null) {
            attrValue[92] = eshift;
        }
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[93] = Math.sin(phiC) * Math.cos(chiC);
        } else {
            attrValue[93] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[94] = Math.sin(psiC) * Math.sin(phiC);
        } else {
            attrValue[94] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[95] = Math.sin(chiC);
        } else {
            attrValue[95] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[96] = Math.sin(phiC) * BULK_P;
        } else {
            attrValue[96] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[97] = Math.sin(psiC) * Math.cos(phiC);
        } else {
            attrValue[97] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[98] = Math.sin(psiC) * Math.sin(chi2C);
        } else {
            attrValue[98] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[99] = Math.cos(psiC) * Math.cos(chi2C);
        } else {
            attrValue[99] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[100] = Math.cos(2 * chi2C);
        } else {
            attrValue[100] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[101] = Math.sin(phiC) * HPHB_P;
        } else {
            attrValue[101] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[102] = Math.cos(3 * phiP);
        } else {
            attrValue[102] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[103] = Math.cos(phiC) * Math.sin(chiC);
        } else {
            attrValue[103] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[104] = Math.sin(2 * chiC);
        } else {
            attrValue[104] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[105] = Math.sin(3 * phiS);
        } else {
            attrValue[105] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[106] = Math.sin(psiC) * BULK_S;
        } else {
            attrValue[106] = 0.0;
        }

        double contactSum = valueMap.get("contacts");
        double scale = ProteinPredictor.calcDisorderScale(contactSum, minMax);
        double sum = coefs[interceptCoef];
        for (int i = 0; i < attrValue.length; i++) {
            sum += scale * attrValue[i] * coefs[i];
        }

        ProteinPredictorResult result;
        if (explain) {
            result = new ProteinPredictorResult(coefs, attrValue, sum);
        } else {
            result = new ProteinPredictorResult(sum);
        }
        return result;
    }

}
