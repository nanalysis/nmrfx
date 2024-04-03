/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.structure.fastlinear.FastVector3D;

/**
 * @author brucejohnson
 */
public class EnergyFFPairs extends EnergyDistancePairs {

    public final static double TWOPI3_2 = 2.0 * Math.pow(Math.PI, 3.0 / 2.0);

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
            this.rDis[iPair] = r0;
            rDis2[iPair] = r0 * r0;
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
    public double calcEnergy(boolean calcDeriv, double weight, double eWeight) {
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        double sum = 0.0;
        double cutoffScale = -1.0;
        double rMin = eCoords.forceWeight.getNBMin();
        double a1 = rMin * 2.0;
        double b1 = 1.0 / a1 * 0.25;
        double c1 = 2.01;
        // 0.25  0.83 0.18
        // 0.5   1.057 0.1661
        // 0.75  1.4923 0.1513
        // 1.00  1.880  0.1413
        if (rMin < 0.35) {
            a1 = 0.83;
            b1 = 0.18;
        } else if (rMin < 0.65) {
            a1 = 1.057;
            b1 = 0.1661;
        } else if (rMin < 0.85) {
            a1 = 1.4923;
            b1 = 0.1513;
        } else if (rMin < 1.1) {
            a1 = 1.880;
            b1 = 0.1413;
        } else {
            a1 = 2.5;
            b1 = 0.068;
            c1 = 2.03;
        }
        double a12 = a1 * a1;
        double b12 = b1 * b1;

        for (int i = 0; i < nPairs; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            double a = aValues[i];
            double b = bValues[i];
            double c = charge[i]; // fixme
            if (eWeight < 0.0) {
                c = 0.0;
            }
//derivative of ( (2.0 + 0.5 *  x^2)/(1.0 + (0.0625 *  x^2 + 1.5) * x^2))
// derivative of ( 2.0*(a + b *  x^2)/((a+b*x^2)^2 + x^2))
// derivative of ( 2.0*(1.0 + 0.25 *  x^2)/((1.0+0.25*x^2)^2 + x^2)) 
//derivative of ( 2.0*(2.0 + 0.15 *  x^2)/((2.0+0.15*x^2)^2 + x^2)) 
// derivative of ( 2.0*q/(q^2+x^2))
// derivative of (a*s3-b)*(s6)
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            double r2 = iV.disSq(jV);
            disSq[i] = r2;
            derivs[i] = 0.0;
            viol[i] = 0.0;
            final double q = a1 + b1 * r2;
            final double u = c1 * q;
            final double v = q * q + r2;
            final double s = u / v;
            final double s3 = s * s * s;
            final double s6 = s3 * s3;
            double e = weight * ((a * s3 - b) * s6 + c * s);
            viol[i] = e;
            sum += e;
            if (calcDeriv) {
                double s2 = s * s;
                double s5 = s2 * s3;
                double deds = (9.0 * a * s3 - 6.0 * b) * s5 + c;
                double r4 = r2 * r2;
                double dem2 = (a12 + 2 * a1 * b1 * r2 + b12 * r4 + r2);
                double dsdp = -(c1 * (a12 * b1 + 2 * a1 * b12 * r2 + a1 + b12 * b1 * r4)) / (dem2 * dem2);

                /*
                 * what is needed is actually the derivitive/r, therefore the r that
                 * would be in following drops out
                 * double dqdx = 0.5 * r;
                 */
                double deriv = deds * dsdp * 2.0 * weight;
                derivs[i] = deriv;
            }

        }
        return sum;
    }

    double poly(double r, double last) {
        double a = 2;
        double b = -3;
        double c = 0;
        double d = 1;

        double x = r / last;

        double p = a * x;
        p = (p + b) * x;
        p = (p + c) * x;
        p = p + d;

        return p;
    }

    public double calcLKEnergy(boolean calcDeriv, double weight) {
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        double sum = 0.0;
        double cutoffScale = -1.0;

        for (int i = 0; i < nPairs; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            double lambda = 1;  // fixme
            double dGFree = 1.0;
            double V = 1.0;
            double sigmaI = eCoords.contactRadii[iAtom] / EnergyCoords.RSCALE;
            double sigmaIJ = (eCoords.contactRadii[iAtom] + eCoords.contactRadii[jAtom]) / EnergyCoords.RSCALE;

            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            double r2 = iV.disSq(jV);
            disSq[i] = r2;
            derivs[i] = 0.0;
            viol[i] = 0.0;
            double r = Math.sqrt(r2);
            double x = (r - sigmaIJ) / lambda;
            double alphaTerm = -V * dGFree / (TWOPI3_2 * lambda * sigmaI * sigmaI);
            double expTerm = Math.exp(-x * x);
            double eVal = alphaTerm * expTerm;
            double e = eVal;
            viol[i] = e;
            sum += e;
            if (calcDeriv) {
                /*
                 * what is needed is actually the derivitive/r, therefore the r that
                 * would be in following drops out
                 */
                double deriv = -2.0 * alphaTerm * (r - sigmaIJ) / (lambda * lambda) * expTerm;
                derivs[i] = deriv;
                sum += e;
            }

        }
        return sum;
    }

    double getEnergy(int i, double r2, double weight, double eWeight) {
        int iAtom = iAtoms[i];
        int jAtom = jAtoms[i];
        double a = aValues[i];
        double b = bValues[i];
        double c = charge[i]; // fixme
        double rMin = eCoords.forceWeight.getNBMin();
        double a1 = rMin * 2.0;
        double b1 = 1.0 / a1 * 0.25;
        final double q = a1 + b1 * r2;
        final double u = 2.0 * q;
        final double v = q * q + r2;
        final double s = 2.0 * q / (q * q + r2);
        final double s3 = s * s * s;
        final double s6 = s3 * s3;
        double eV = weight * ((a * s3 - b) * s6);
        double eE = eWeight < 0.0 ? 0.0 : weight * (c * s);
        return eV + eE;

    }

    @Override
    public ViolationStats getError(int i, double limitVal, double weight) {
        return getError(i, limitVal, weight, -1.0);
    }

    @Override
    public ViolationStats getError(int i, double limitVal, double weight, double eWeight) {
        String modeType = "FF";
        Atom[] atoms = eCoords.atoms;
        int iAtom = iAtoms[i];
        int jAtom = jAtoms[i];
        double r2 = disSq[i];
        double r = Math.sqrt(r2);
        double dif = 0.0;
        if (r2 <= rDis2[i]) {
            r = Math.sqrt(r2);
            dif = rDis[i] - r;
        }
        String result = "";
        ViolationStats stat = null;
        double energy = getEnergy(i, r2, weights[i] * weight, weights[i] * eWeight);
        if (Math.abs(dif) > limitVal) {
            stat = new ViolationStats(2, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), r, rDis[i], 0.0, energy, eCoords);
        }

        return stat;
    }
}
