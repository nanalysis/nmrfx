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
public class EnergyDistancePairs extends EnergyPairs {

    double[] disSq;
    double[] rDis2;
    double[] rDis;

    public EnergyDistancePairs(EnergyCoords eCoords) {
        super(eCoords);
    }

    @Override
    public void addPair(int i, int j, int iUnit, int jUnit, double r0) {
        if (i != j) {
            addPair(i, j, iUnit, jUnit);
            int iPair = nPairs - 1;
            this.rDis[iPair] = r0;
            rDis2[iPair] = r0 * r0;
        }
    }

    @Override
    void resize(int size) {
        if ((iAtoms == null) || (iAtoms.length < size)) {
            super.resize(size);
            int newSize = iAtoms.length;
            disSq = resize(disSq, newSize);
            rDis = resize(rDis, newSize);
            rDis2 = resize(rDis2, newSize);
        }
    }

    public double calcEnergy(boolean calcDeriv, double weight, double eWeight) {
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
            if (r2 <= rDis2[i]) {
                double r = Math.sqrt(r2);
                double dif = rDis[i] - r;
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

        return sum;
    }

    @Override
    public ViolationStats getError(int i, double limitVal, double weight, double eWeight) {
        return getError(i, limitVal, weight);
    }

    public ViolationStats getError(int i, double limitVal, double weight) {
        String modeType = "Rep";
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
        if (Math.abs(dif) > limitVal) {
            double energy = weights[i] * weight * dif * dif;
            stat = new ViolationStats(1, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), r, rDis[i], 0.0, energy, eCoords);
        }

        return stat;
    }
}
