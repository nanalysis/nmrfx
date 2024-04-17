/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.structure.fastlinear.FastVector3D;

import static org.nmrfx.structure.chemistry.energy.AtomMath.RADJ;

/**
 * @author brucejohnson
 */
public class EnergyBaseStacking extends EnergyDistancePairs {

    int[] baseAtoms1a;
    int[] baseAtoms1b;
    int[] baseAtoms2a;
    int[] baseAtoms2b;

    public EnergyBaseStacking(EnergyCoords eCoords) {
        super(eCoords);
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double r0,
                        int baseAtom1a, int baseAtom1b,
                        int baseAtom2a, int baseAtom2b) {
        if (i != j) {
            addPair(i, j, iUnit, jUnit, r0);
            int iPair = nPairs - 1;
            baseAtoms1a[iPair] = baseAtom1a;
            baseAtoms2a[iPair] = baseAtom2a;
            baseAtoms1b[iPair] = baseAtom1b;
            baseAtoms2b[iPair] = baseAtom2b;
        }
    }

    void resize(int size) {
        if ((iAtoms == null) || (iAtoms.length < size)) {
            super.resize(size);
            int newSize = iAtoms.length;
            baseAtoms1a = resize(baseAtoms1a, newSize);
            baseAtoms1b = resize(baseAtoms1b, newSize);
            baseAtoms2a = resize(baseAtoms2a, newSize);
            baseAtoms2b = resize(baseAtoms2b, newSize);
        }
    }

    @Override
    public double calcEnergy(boolean calcDeriv, double weight, double eWeight) {
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        double sum = 0.0;
        for (int i = 0; i < nPairs; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            FastVector3D pV1a = vecCoords[baseAtoms1a[i]];
            FastVector3D pV1b = vecCoords[baseAtoms1b[i]];
            FastVector3D pV2a = vecCoords[baseAtoms2a[i]];
            FastVector3D pV2b = vecCoords[baseAtoms2b[i]];

            FastVector3D vDiffa = new FastVector3D();
            FastVector3D vDiffb = new FastVector3D();
            FastVector3D pV1aZ = new FastVector3D();
            FastVector3D pV1bZ = new FastVector3D();
            FastVector3D pV2aZ = new FastVector3D();
            FastVector3D pV2bZ = new FastVector3D();
            pV1a.subtract(iV, pV1aZ);
            pV1b.subtract(iV, pV1bZ);
            pV2a.subtract(jV, pV2aZ);
            pV2b.subtract(jV, pV2bZ);

            jV.subtract(iV, vDiffa);
            iV.subtract(jV, vDiffb);
            FastVector3D normToPlanea = new FastVector3D();
            FastVector3D normToPlaneb = new FastVector3D();
            pV1aZ.crossProduct(pV1bZ, normToPlanea);
            pV2aZ.crossProduct(pV2bZ, normToPlaneb);

            double angleA = FastVector3D.angle(vDiffa, normToPlanea);
            double angleB = FastVector3D.angle(vDiffb, normToPlaneb);
            double cosA = Math.cos(angleA);
            double cosB = Math.cos(angleB);
            double planarityScale = 0.5 * (cosA * cosA + cosB * cosB);
            Atom[] atoms = eCoords.atoms;
            double r2 = iV.disSq(jV);
            disSq[i] = r2;
            derivs[i] = 0.0;
            viol[i] = 0.0;
            if ((r2 > 16.0) && (r2 < 36.0)) {
                double r = Math.sqrt(r2);
                double du = (r - 4.0) / 2.0;
                double g = -0.2 * (2.0 * Math.pow(du, 3.0) - 3.0 * Math.pow(du, 2.0) + 1.0);

                viol[i] = weights[i] * weight * planarityScale * g;
                sum += viol[i];
                if (calcDeriv) {
                    //  what is needed is actually the derivative/r, therefore
                    // we divide by r
                    // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                    /*  MATLAB code for deriv
                    f= -0.2*(2.0*((r-4.0)/2.0)^3 - 3.0*((r-4.0)/2.0)^2.0 + 1.0)
                    df=diff(f,r)
                    df =
                       (3*r)/10 - (3*(r/2 - 2)^2)/5 - 6/5
                     */
                    double diff = (3.0 * r) / 10.0 - (3.0 * Math.pow(du, 2)) / 5.0 - 1.2;
                    derivs[i] = weights[i] * weight * planarityScale * diff / (r + RADJ);

                }
            }
        }

        return sum;
    }

    public static double getEnergy(int i, double r2, double weight) {
        double energy = 0.0;
        if ((r2 > 16.0) && (r2 < 36.0)) {
            double r = Math.sqrt(r2);
            double du = (r - 4.0) / 2.0;
            double g = -0.2 * (2.0 * Math.pow(du, 3.0) - 3.0 * Math.pow(du, 2.0) + 1.0);
            energy = weight * g;
        }
        return energy;

    }

    @Override
    public ViolationStats getError(int i, double limitVal, double weight) {
        String modeType = "STK";
        Atom[] atoms = eCoords.atoms;
        int iAtom = iAtoms[i];
        int jAtom = jAtoms[i];
        double r2 = disSq[i];
        double r = Math.sqrt(r2);
        double dif = 0.0;
        if (r2 <= 36.0) {
            r = Math.sqrt(r2);
            dif = 6.0 - r;
        }
        String result = "";
        ViolationStats stat = null;
        double energy = getEnergy(i, r2, weights[i] * weight);
        if (Math.abs(dif) > 0.1) {
            stat = new ViolationStats(3, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), r, 0.0, 6.0, energy, eCoords);
        }

        return stat;
    }
}
