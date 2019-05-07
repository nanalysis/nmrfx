/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.util.FastMath;
import static org.nmrfx.structure.chemistry.energy.AtomMath.RADJ;
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

    List<RepelPair> pairs = new ArrayList<>();

    class RepelPair {

        int i;
        int j;
        int iUnit;
        int jUnit;
        double r0;

        RepelPair(int i, int j, int iUnit, int jUnit, double r0) {
            this.i = i;
            this.j = j;
            this.iUnit = iUnit;
            this.r0 = r0;
        }
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double r0) {
        RepelPair rPair = new RepelPair(i, j, iUnit, jUnit, r0);
        pairs.add(rPair);
    }

    public void initPairs(int n) {
        iAtoms = new int[n];
        jAtoms = new int[n];
        iUnits = new int[n];
        jUnits = new int[n];
        disSq = new double[n];
        rLow2 = new double[n];
        rLow = new double[n];
        viol = new double[n];
        weights = new double[n];
        derivs = new double[n];
    }

    public void updatePairs() {
        initPairs(pairs.size());
        int iPair = 0;
        for (RepelPair pair : pairs) {
            iAtoms[iPair] = pair.i;
            jAtoms[iPair] = pair.j;
            iUnits[iPair] = pair.iUnit;
            jUnits[iPair] = pair.jUnit;

            this.rLow[iPair] = pair.r0;
            rLow2[iPair] = pair.r0 * pair.r0;
            weights[iPair] = 1.0;
            iPair++;
        }
        nPairs = pairs.size();
//        repelEnd++;
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
        return sum;
    }

    public void addDerivs(AtomBranch[] branches, int mode) {
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

}
