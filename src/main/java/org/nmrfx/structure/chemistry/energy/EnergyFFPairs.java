/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.fastlinear.FastVector3D;

/**
 *
 * @author brucejohnson
 */
public class EnergyFFPairs extends EnergyDistancePairs {

    double[] aValues;
    double[] bValues;
    double[] charge;

    public EnergyFFPairs(EnergyCoords eCoords) {
        super(eCoords);
    }

    @Override
    public void addPair(int i, int j, int iUnit, int jUnit, double r0, double a, double b, double charge) {
        if (i != j) {
            addPair(i, j, iUnit, jUnit);
            int iPair = nPairs - 1;
            this.rLow[iPair] = r0;
            rLow2[iPair] = r0 * r0;
            this.aValues[iPair] = a;
            this.bValues[iPair] = b;
            this.charge[iPair] = charge;
        }
    }

    void resize(int size) {
        if ((iAtoms == null) || (iAtoms.length < size)) {
            super.resize(size);
            int newSize = iAtoms.length;
            aValues = resize(aValues, newSize);
            bValues = resize(bValues, newSize);
            charge = resize(charge, newSize);
        }
    }

    @Override
    public double calcEnergy(boolean calcDeriv, double weight) {
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        double sum = 0.0;
        double cutoffScale = -1.0;
        for (int i = 0; i < nPairs; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            double a = aValues[i];
            double b = bValues[i];
            double c = charge[i]; // fixme

            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            double r2 = iV.disSq(jV);
            disSq[i] = r2;
            derivs[i] = 0.0;
            viol[i] = 0.0;
            if (!calcDeriv) {
                final double q = 1.0 + 0.25 * r2;
                final double s = 2.0 * q / (q * q + r2);
                final double s3 = s * s * s;
                final double s6 = s3 * s3;
                double e = weight * ((a * s3 - b) * s6 + c * s);
                if (cutoffScale >= 0.0) {
                    e *= cutoffScale;
                }
                viol[i] = e;
                sum += e;
            } else {
                final double u = 2.0 + 0.5 * r2;
                final double v = 1.0 + (0.0625 * r2 + 1.5) * r2;
                final double s = u / v;
                final double s2 = s * s;
                final double s3 = s2 * s;
                final double s5 = s2 * s3;
                final double s6 = s3 * s3;
                final double deds = (9.0 * a * s3 - 6.0 * b) * s5 + c;
                final double dsdp = (0.5 - (u / v) * (1.5 + 0.125 * r2)) / v;
                double e = weight * ((a * s3 - b) * s6 + c * s);
                /*
                 * what is needed is actually the derivitive/r, therefore the r that
                 * would be in following drops out
                 */
                double deriv = deds * dsdp * 2.0 * weight;
                if (cutoffScale >= 0.0) {
                    e *= cutoffScale;
                    deriv *= cutoffScale;
                }
                viol[i] = e;
                derivs[i] = deriv;
                sum += e;
            }

        }
        return sum;
    }

    double getEnergy(int i, double r2, double weight) {
        int iAtom = iAtoms[i];
        int jAtom = jAtoms[i];
        double a = aValues[i];
        double b = bValues[i];
        double c = charge[i]; // fixme
        final double q = 1.0 + 0.25 * r2;
        final double s = 2.0 * q / (q * q + r2);
        final double s3 = s * s * s;
        final double s6 = s3 * s3;
        double e = weight * ((a * s3 - b) * s6 + c * s);
        return e;

    }

    @Override
    public ViolationStats getError(int i, double limitVal, double weight) {
        String modeType = "FF";
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
            double energy = getEnergy(i, r2, weights[i] * weight);
            stat = new ViolationStats(2, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), r, rLow[i], 0.0, energy, eCoords);
        }

        return stat;
    }
}
