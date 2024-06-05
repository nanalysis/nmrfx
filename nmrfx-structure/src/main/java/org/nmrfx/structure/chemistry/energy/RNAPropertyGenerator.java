package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;
import org.python.modules.math;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;

public class RNAPropertyGenerator {
    private static final Logger log = LoggerFactory.getLogger(PropertyGenerator.class);
    EnumMap<Residue.AngleAtoms, Double> anglesMap = new EnumMap<>(Residue.class);
    private Molecule molecule;
    public boolean getResidueProperties(Polymer polymer, Residue residue,
                                        int structureNum) {
        anglesMap.clear();
        int resNum = residue.getResNum();

        try {
            String polyName = polymer.getName();
            anglesMap.put(Residue.AngleAtoms.DELTAP, Residue.AngleAtoms.DELTAP.calcAngle(residue));
            anglesMap.put(Residue.AngleAtoms.EPSILON, Residue.AngleAtoms.EPSILON.calcAngle(residue));
            anglesMap.put(Residue.AngleAtoms.ZETA, Residue.AngleAtoms.ZETA.calcAngle(residue));
            anglesMap.put(Residue.AngleAtoms.ALPHA, Residue.AngleAtoms.ALPHA.calcAngle(residue));
            anglesMap.put(Residue.AngleAtoms.BETA, Residue.AngleAtoms.BETA.calcAngle(residue));
            anglesMap.put(Residue.AngleAtoms.GAMMA, Residue.AngleAtoms.GAMMA.calcAngle(residue));
            anglesMap.put(Residue.AngleAtoms.DELTA, Residue.AngleAtoms.DELTA.calcAngle(residue));
            anglesMap.put(Residue.AngleAtoms.NU2, Residue.AngleAtoms.NU2.calcAngle(residue));
        }  catch (Exception e) {
            log.warn(e.getMessage(), e);
            return false;
        }
        return true;
    }

    public static boolean checkAngles(Double... values) {
        for (Double value : values) {
            if ((value == null) || Double.isNaN(value) || Double.isInfinite(value)) {
                return false;
            }
        }
        return true;
    }

    public boolean genAttributes() {
        double[] attrValue = new double[100];
        double deltap = anglesMap.get(Residue.AngleAtoms.DELTAP);
        double epsilon = anglesMap.get(Residue.AngleAtoms.EPSILON);
        double zeta = anglesMap.get(Residue.AngleAtoms.ZETA);
        double alpha = anglesMap.get(Residue.AngleAtoms.ALPHA);
        double beta = anglesMap.get(Residue.AngleAtoms.BETA);
        double gamma = anglesMap.get(Residue.AngleAtoms.GAMMA);
        double delta = anglesMap.get(Residue.AngleAtoms.DELTA);
        double nu2 = anglesMap.get(Residue.AngleAtoms.NU2);

        attrValue[0] = checkAngles(deltap) ? math.cos(deltap) : 0.0;
        attrValue[1] = checkAngles(deltap) ? math.sin(deltap) : 0.0;
        attrValue[2] = checkAngles(deltap) ? math.cos(2*deltap) : 0.0;
        attrValue[3] = checkAngles(deltap) ? math.sin(2*deltap) : 0.0;
        attrValue[4] = checkAngles(deltap) ? math.cos(3*deltap) : 0.0;
        attrValue[5] = checkAngles(deltap) ? math.sin(3*deltap) : 0.0;

        attrValue[6] = checkAngles(epsilon) ? math.cos(epsilon) : 0.0;
        attrValue[7] = checkAngles(epsilon) ? math.sin(epsilon) : 0.0;
        attrValue[8] = checkAngles(epsilon) ? math.cos(2*epsilon) : 0.0;
        attrValue[9] = checkAngles(epsilon) ? math.sin(2*epsilon) : 0.0;
        attrValue[10] = checkAngles(epsilon) ? math.cos(3*epsilon) : 0.0;
        attrValue[11] = checkAngles(epsilon) ? math.sin(3*epsilon) : 0.0;

        attrValue[12] = checkAngles(zeta) ? math.cos(zeta) : 0.0;
        attrValue[13] = checkAngles(zeta) ? math.sin(zeta) : 0.0;
        attrValue[14] = checkAngles(zeta) ? math.cos(2*zeta) : 0.0;
        attrValue[15] = checkAngles(zeta) ? math.sin(2*zeta) : 0.0;
        attrValue[16] = checkAngles(zeta) ? math.cos(3*zeta) : 0.0;
        attrValue[17] = checkAngles(zeta) ? math.sin(3*zeta) : 0.0;

        attrValue[18] = checkAngles(alpha) ? math.cos(alpha) : 0.0;
        attrValue[19] = checkAngles(alpha) ? math.sin(alpha) : 0.0;
        attrValue[20] = checkAngles(alpha) ? math.cos(2*alpha) : 0.0;
        attrValue[21] = checkAngles(alpha) ? math.sin(2*alpha) : 0.0;
        attrValue[22] = checkAngles(alpha) ? math.cos(3*alpha) : 0.0;
        attrValue[23] = checkAngles(alpha) ? math.sin(3*alpha) : 0.0;

        attrValue[24] = checkAngles(beta) ? math.cos(beta) : 0.0;
        attrValue[25] = checkAngles(beta) ? math.sin(beta) : 0.0;
        attrValue[26] = checkAngles(beta) ? math.cos(2*beta) : 0.0;
        attrValue[27] = checkAngles(beta) ? math.sin(2*beta) : 0.0;
        attrValue[28] = checkAngles(beta) ? math.cos(3*beta) : 0.0;
        attrValue[29] = checkAngles(beta) ? math.sin(3*beta) : 0.0;

        attrValue[30] = checkAngles(gamma) ? math.cos(gamma) : 0.0;
        attrValue[31] = checkAngles(gamma) ? math.sin(gamma) : 0.0;
        attrValue[32] = checkAngles(gamma) ? math.cos(2*gamma) : 0.0;
        attrValue[33] = checkAngles(gamma) ? math.sin(2*gamma) : 0.0;
        attrValue[34] = checkAngles(gamma) ? math.cos(3*gamma) : 0.0;
        attrValue[35] = checkAngles(gamma) ? math.sin(3*gamma) : 0.0;

        attrValue[36] = checkAngles(delta) ? math.cos(delta) : 0.0;
        attrValue[37] = checkAngles(delta) ? math.sin(delta) : 0.0;
        attrValue[38] = checkAngles(delta) ? math.cos(2*delta) : 0.0;
        attrValue[39] = checkAngles(delta) ? math.sin(2*delta) : 0.0;
        attrValue[40] = checkAngles(delta) ? math.cos(3*delta) : 0.0;
        attrValue[41] = checkAngles(delta) ? math.sin(3*delta) : 0.0;

        attrValue[42] = checkAngles(nu2) ? math.cos(nu2) : 0.0;
        attrValue[43] = checkAngles(nu2) ? math.sin(nu2) : 0.0;
        attrValue[44] = checkAngles(nu2) ? math.cos(2*nu2) : 0.0;
        attrValue[45] = checkAngles(nu2) ? math.sin(2*nu2) : 0.0;
        attrValue[46] = checkAngles(nu2) ? math.cos(3*nu2) : 0.0;
        attrValue[47] = checkAngles(nu2) ? math.sin(3*nu2) : 0.0;

        attrValue[48] = checkAngles(deltap, epsilon) ? math.cos(deltap) * math.sin(epsilon) : 0.0;
        return true;
    }
}