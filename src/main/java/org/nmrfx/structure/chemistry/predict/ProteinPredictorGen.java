package org.nmrfx.structure.chemistry.predict;

import java.util.Map;

public class ProteinPredictorGen {

    public static ProteinPredictorResult predict(Map<String, Double> valueMap, double[] coefs, double[] minMax, boolean explain) {
        double[] attrValue = new double[100];
        Double psiC = valueMap.get("psiC");
        Double h3 = valueMap.get("h3");
        Double psiS = valueMap.get("psiS");
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
        Double psiP = valueMap.get("psiP");
        Double chiC = valueMap.get("chiC");
        Double DIS = valueMap.get("DIS");
        Double BULK_S = valueMap.get("BULK_S");
        Double BULK_P = valueMap.get("BULK_P");
        Double phiC = valueMap.get("phiC");
        Double HPHB_P = valueMap.get("HPHB_P");
        Double HPHB_S = valueMap.get("HPHB_S");
        Double intercept = valueMap.get("intercept");
        Double phiS = valueMap.get("phiS");
        Double phiP = valueMap.get("phiP");
        Double PRO_P = valueMap.get("PRO_P");
        Double PRO_S = valueMap.get("PRO_S");
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[0] = Math.cos(chiC) * Math.sin(chi2C);
        } else {
            attrValue[0] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[1] = Math.sin(2 * chi2C);
        } else {
            attrValue[1] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP, psiC)) {
            attrValue[2] = Math.cos(psiC) * Math.cos(psiP);
        } else {
            attrValue[2] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[3] = Math.sin(3 * psiP);
        } else {
            attrValue[3] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[4] = Math.cos(2 * psiS);
        } else {
            attrValue[4] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[5] = Math.sin(phiC) * PRO_P;
        } else {
            attrValue[5] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[6] = Math.cos(2 * chiC);
        } else {
            attrValue[6] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[7] = Math.sin(phiP);
        } else {
            attrValue[7] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[8] = Math.sin(3 * psiC);
        } else {
            attrValue[8] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[9] = Math.cos(psiP);
        } else {
            attrValue[9] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[10] = Math.cos(2 * phiP);
        } else {
            attrValue[10] = 0.0;
        }
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
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[17] = Math.cos(phiP);
        } else {
            attrValue[17] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[18] = Math.sin(3 * psiS);
        } else {
            attrValue[18] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[19] = Math.cos(3 * psiS);
        } else {
            attrValue[19] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[20] = Math.cos(psiC) * Math.cos(chiC);
        } else {
            attrValue[20] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[21] = Math.cos(phiC) * BULK_P;
        } else {
            attrValue[21] = 0.0;
        }
        attrValue[22] = ring;
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[23] = Math.cos(2 * psiC);
        } else {
            attrValue[23] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[24] = Math.cos(phiC) * ARO_P;
        } else {
            attrValue[24] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[25] = Math.cos(phiC) * Math.cos(chi2C);
        } else {
            attrValue[25] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[26] = Math.cos(psiC) * Math.cos(phiC);
        } else {
            attrValue[26] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[27] = Math.sin(2 * psiC);
        } else {
            attrValue[27] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[28] = Math.sin(phiC) * ARO_P;
        } else {
            attrValue[28] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[29] = Math.cos(psiC) * ARO_S;
        } else {
            attrValue[29] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[30] = Math.cos(psiC) * CHRG_S;
        } else {
            attrValue[30] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[31] = Math.cos(psiC) * HPHB_S;
        } else {
            attrValue[31] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP, psiC)) {
            attrValue[32] = Math.sin(psiC) * Math.cos(psiP);
        } else {
            attrValue[32] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[33] = Math.cos(psiS);
        } else {
            attrValue[33] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[34] = Math.cos(chi2C);
        } else {
            attrValue[34] = 0.0;
        }
        attrValue[35] = hshift3;
        attrValue[36] = hshift2;
        attrValue[37] = hshift1;
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[38] = Math.sin(phiC) * Math.sin(chiC);
        } else {
            attrValue[38] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[39] = Math.sin(psiC) * PRO_S;
        } else {
            attrValue[39] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[40] = Math.sin(phiC) * Math.sin(chi2C);
        } else {
            attrValue[40] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[41] = Math.cos(psiC) * BULK_S;
        } else {
            attrValue[41] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[42] = Math.cos(chiC) * Math.cos(chi2C);
        } else {
            attrValue[42] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[43] = Math.cos(3 * psiC);
        } else {
            attrValue[43] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[44] = Math.sin(psiS);
        } else {
            attrValue[44] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[45] = Math.cos(2 * psiP);
        } else {
            attrValue[45] = 0.0;
        }
        attrValue[46] = 0.0;
        int interceptCoef = 46;
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[47] = Math.cos(2 * phiC);
        } else {
            attrValue[47] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[48] = Math.sin(psiC) * Math.sin(chiC);
        } else {
            attrValue[48] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[49] = Math.sin(psiC) * HPHB_S;
        } else {
            attrValue[49] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[50] = Math.cos(2 * phiS);
        } else {
            attrValue[50] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[51] = Math.sin(2 * psiS);
        } else {
            attrValue[51] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[52] = Math.sin(phiS);
        } else {
            attrValue[52] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[53] = Math.cos(phiC) * Math.sin(chi2C);
        } else {
            attrValue[53] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[54] = Math.sin(2 * phiP);
        } else {
            attrValue[54] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[55] = Math.sin(psiP);
        } else {
            attrValue[55] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[56] = Math.cos(phiS);
        } else {
            attrValue[56] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[57] = Math.cos(3 * phiS);
        } else {
            attrValue[57] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[58] = Math.cos(psiC) * Math.sin(chi2C);
        } else {
            attrValue[58] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[59] = Math.sin(phiC) * Math.cos(chi2C);
        } else {
            attrValue[59] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[60] = Math.sin(psiC) * CHRG_S;
        } else {
            attrValue[60] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[61] = Math.sin(2 * phiC);
        } else {
            attrValue[61] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[62] = Math.cos(psiC) * Math.sin(chiC);
        } else {
            attrValue[62] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[63] = Math.cos(phiC);
        } else {
            attrValue[63] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[64] = Math.sin(phiC);
        } else {
            attrValue[64] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[65] = Math.cos(3 * phiC);
        } else {
            attrValue[65] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[66] = Math.sin(chiC) * Math.sin(chi2C);
        } else {
            attrValue[66] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[67] = Math.cos(psiC) * PRO_S;
        } else {
            attrValue[67] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[68] = Math.sin(psiC) * Math.cos(chiC);
        } else {
            attrValue[68] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[69] = Math.sin(psiC);
        } else {
            attrValue[69] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[70] = Math.sin(chiC) * Math.cos(chi2C);
        } else {
            attrValue[70] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[71] = Math.cos(phiC) * HPHB_P;
        } else {
            attrValue[71] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[72] = Math.cos(phiC) * CHRG_P;
        } else {
            attrValue[72] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[73] = Math.cos(psiC);
        } else {
            attrValue[73] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[74] = Math.sin(2 * phiS);
        } else {
            attrValue[74] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[75] = Math.cos(chiC);
        } else {
            attrValue[75] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[76] = Math.cos(phiC) * PRO_P;
        } else {
            attrValue[76] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[77] = Math.sin(phiC) * CHRG_P;
        } else {
            attrValue[77] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[78] = Math.cos(psiC) * Math.sin(phiC);
        } else {
            attrValue[78] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[79] = Math.sin(3 * phiC);
        } else {
            attrValue[79] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[80] = Math.cos(3 * psiP);
        } else {
            attrValue[80] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP, psiC)) {
            attrValue[81] = Math.cos(psiC) * Math.sin(psiP);
        } else {
            attrValue[81] = 0.0;
        }
        attrValue[82] = DIS;
        if (ProteinPredictor.checkAngles(psiP, psiC)) {
            attrValue[83] = Math.sin(psiC) * Math.sin(psiP);
        } else {
            attrValue[83] = 0.0;
        }
        if (eshift != null) {
            attrValue[84] = eshift;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[85] = Math.sin(2 * psiP);
        } else {
            attrValue[85] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[86] = Math.sin(phiC) * Math.cos(chiC);
        } else {
            attrValue[86] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[87] = Math.sin(psiC) * Math.sin(phiC);
        } else {
            attrValue[87] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[88] = Math.sin(chiC);
        } else {
            attrValue[88] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[89] = Math.sin(phiC) * BULK_P;
        } else {
            attrValue[89] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[90] = Math.sin(psiC) * Math.cos(phiC);
        } else {
            attrValue[90] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[91] = Math.sin(psiC) * Math.sin(chi2C);
        } else {
            attrValue[91] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[92] = Math.cos(psiC) * Math.cos(chi2C);
        } else {
            attrValue[92] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[93] = Math.cos(2 * chi2C);
        } else {
            attrValue[93] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[94] = Math.sin(phiC) * HPHB_P;
        } else {
            attrValue[94] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[95] = Math.cos(3 * phiP);
        } else {
            attrValue[95] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[96] = Math.cos(phiC) * Math.sin(chiC);
        } else {
            attrValue[96] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[97] = Math.sin(2 * chiC);
        } else {
            attrValue[97] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[98] = Math.sin(3 * phiS);
        } else {
            attrValue[98] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[99] = Math.sin(psiC) * BULK_S;
        } else {
            attrValue[99] = 0.0;
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
