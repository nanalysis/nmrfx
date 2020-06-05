/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Atom;
import static org.nmrfx.structure.chemistry.energy.AtomMath.RADJ;
import org.nmrfx.structure.fastlinear.FastVector3D;

/**
 *
 * @author brucejohnson
 */
public class EnergyBaseStacking extends EnergyDistancePairs {

    public EnergyBaseStacking(EnergyCoords eCoords) {
        super(eCoords);
    }

    @Override
    public double calcEnergy(boolean calcDeriv, double weight) {
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
            if ((r2 > 16.0) && (r2 < 36.0)) {
                double r = FastMath.sqrt(r2);
                double du = (r - 4.0) / 2.0;
                double g = -0.2 * (2.0 * (Math.pow(du, 3.0) - 3.0 * Math.pow(du, 2.0)) + 1.0);

                viol[i] = weights[i] * weight * g;
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
                    derivs[i] = weights[i] * weight * diff / (r + RADJ);

                }
            }
        }
//        System.out.println("repel " + nPairs + " " + sum);

        return sum;
    }

    public static double getEnergy(int i, double r2, double weight) {
        double energy = 0.0;
        if ((r2 > 16.0) && (r2 < 36.0)) {
            double r = FastMath.sqrt(r2);
            double du = (r - 4.0) / 2.0;
            double g = -0.2 * (2.0 * (Math.pow(du, 3.0) - 3.0 * Math.pow(du, 2.0)) + 1.0);

            energy = weight * g;
        }
        return energy;

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
        if (r2 <= rDis2[i]) {
            r = FastMath.sqrt(r2);
            dif = rDis[i] - r;
        }
        String result = "";
        ViolationStats stat = null;
        double energy = getEnergy(i, r2, weights[i] * weight);
        if (Math.abs(dif) > limitVal) {
            stat = new ViolationStats(2, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), r, rDis[i], 0.0, energy, eCoords);
        }

        return stat;
    }
}
