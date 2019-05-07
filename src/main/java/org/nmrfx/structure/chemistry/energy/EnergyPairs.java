/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Atom;
import static org.nmrfx.structure.chemistry.energy.AtomMath.RADJ;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.nmrfx.structure.fastlinear.FastVector3D;

/**
 *
 * @author brucejohnson
 */
public class EnergyPairs {

    EnergyCoords eCoords;
    int[] iAtoms;
    int[] jAtoms;
    int[] iUnits;
    int[] jUnits;
    double[] disSq;
    double[] rLow2;
    double[] rLow;
    double[] viol;
    double[] weights;

    double[] derivs;
    int nPairs;

    public EnergyPairs(EnergyCoords eCoords) {
        this.eCoords = eCoords;

    }

    public void addPair(int i, int j, int iUnit, int jUnit, double r0) {
        resize(nPairs + 1);
        int iPair = nPairs;
        iAtoms[iPair] = i;
        jAtoms[iPair] = j;
        iUnits[iPair] = iUnit;
        jUnits[iPair] = jUnit;

        this.rLow[iPair] = r0;
        rLow2[iPair] = r0 * r0;
        weights[iPair] = 1.0;
        derivs[iPair] = 0.0;
        nPairs = iPair + 1;

    }

    int[] resize(int[] v, int size) {
        int[] newV = new int[size];
        if (v != null) {
            System.arraycopy(v, 0, newV, 0, v.length);
        }
        return newV;
    }

    double[] resize(double[] v, int size) {
        double[] newV = new double[size];
        if (v != null) {
            System.arraycopy(v, 0, newV, 0, v.length);
        }
        return newV;
    }

    boolean[] resize(boolean[] v, int size) {
        boolean[] newV = new boolean[size];
        if (v != null) {
            System.arraycopy(v, 0, newV, 0, v.length);
        }
        return newV;
    }

    void resize(int size) {
        if ((iAtoms == null) || (iAtoms.length < size)) {
            int newSize = size < 4096 ? 4096 : size * 3 / 2;
            iAtoms = resize(iAtoms, newSize);
            jAtoms = resize(jAtoms, newSize);
            iUnits = resize(iUnits, newSize);
            jUnits = resize(jUnits, newSize);
            disSq = resize(disSq, newSize);
            rLow = resize(rLow, newSize);
            rLow2 = resize(rLow2, newSize);
            viol = resize(viol, newSize);
            weights = resize(weights, newSize);
            derivs = resize(derivs, newSize);
        }
    }

    public double calcRepel(boolean calcDeriv, double weight) {
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        double sum = 0.0;
        for (int i = 0; i < nPairs; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            double r2 = iV.disSq(jV);
            disSq[i] = r2;
            derivs[i] = 0.0;
            viol[i] = 0.0;
            if (r2 <= rLow2[i]) {
                double r = FastMath.sqrt(r2);
                double dif = rLow[i] - r;
                viol[i] = weights[i] * weight * dif * dif;
                sum += viol[i];
                if (calcDeriv) {
                    //  what is needed is actually the derivative/r, therefore
                    // we divide by r
                    // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                    derivs[i] = -2.0 * weights[i] * weight * dif / (r + RADJ);
                }
            }
        }
//        System.out.println("repel " + nPairs + " " + sum);

        return sum;
    }

    public void addDerivs(AtomBranch[] branches) {
        FastVector3D[] vecCoords = eCoords.getVecCoords();

        FastVector3D v1 = new FastVector3D();
        FastVector3D v2 = new FastVector3D();
        for (int i = 0; i < nPairs; i++) {
            double deriv = derivs[i];
            if (deriv == 0.0) {
                continue;
            }
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];

            FastVector3D pv1 = vecCoords[iAtom];
            FastVector3D pv2 = vecCoords[jAtom];

            pv1.crossProduct(pv2, v1);
            v1.multiply(derivs[i]);

            pv1.subtract(pv2, v2);
            v2.multiply(derivs[i]);
            int iUnit = iUnits[i];
            int jUnit = jUnits[i];

            if (iUnit >= 0) {
                branches[iUnit].addToF(v1.getValues());
                branches[iUnit].addToG(v2.getValues());

            }
            if (jUnit >= 0) {
                branches[jUnit].subtractToF(v1.getValues());
                branches[jUnit].subtractToG(v2.getValues());
            }
        }
    }

    public ViolationStats getError(int i, double limitVal, double weight) {
        String modeType = "Rep";
        Atom[] atoms = eCoords.atoms;
        int iAtom = iAtoms[i];
        int jAtom = jAtoms[i];
        double r2 = disSq[i];
        double r = FastMath.sqrt(r2);
        double dif = 0.0;
        if (r2 <= rLow2[i]) {
            r = FastMath.sqrt(r2);
            dif = rLow[i] - r;
        }
        String result = "";
        ViolationStats stat = null;
        if (Math.abs(dif) > limitVal) {
            double energy = weights[i] * weight * dif * dif;
            stat = new ViolationStats(1, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), r, rLow[i], 0.0, energy, eCoords);
        }

        return stat;
    }

    public double calcDistShifts(boolean calcDeriv, double rLim, double weight) {
        double[] baseShifts = eCoords.baseShifts;
        double[] refShifts = eCoords.refShifts;
        int[] shiftClass = eCoords.shiftClass;
        double[] shifts = eCoords.shifts;
        Atom[] atoms = eCoords.atoms;
        FastVector3D[] vecCoords = eCoords.getVecCoords();

        double r2Lim = rLim * rLim;
        System.arraycopy(baseShifts, 0, refShifts, 0, baseShifts.length);

        for (int i = 0; i < nPairs; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            if ((baseShifts[iAtom] != 0.0) && shiftClass[jAtom] >= 0) {
                FastVector3D iV = vecCoords[iAtom];
                FastVector3D jV = vecCoords[jAtom];
                double r2 = iV.disSq(jV);
                if (r2 <= r2Lim) {
                    double r = FastMath.sqrt(r2);
                    int alphaClass = shiftClass[jAtom] >> 8;
                    int alphaIndex = shiftClass[jAtom] & 255;
                    double alpha = Predictor.getAlpha(alphaClass, alphaIndex);
                    double shiftContrib = alpha * r * r2;
                    refShifts[iAtom] += shiftContrib;
                    if (calcDeriv) {
                        //  what is needed is actually the derivative/r, therefore
                        // we divide by r
                        // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                        //derivs[i] = -2.0 * weights[i] * weight * dif / (r + RADJ);
                    }
                }
            }
        }
        double sum = 0.0;
        for (int i = 0; i < baseShifts.length; i++) {
            if (baseShifts[i] != 0.0) {
                double shiftDelta = shifts[i] = refShifts[i];
                sum += weight * shiftDelta * shiftDelta;
                System.out.println(i + " " + atoms[i].getShortName() + " " + refShifts[i]);
                atoms[i].setRefPPM(refShifts[i]);
            }
        }
        return sum;

    }
}
