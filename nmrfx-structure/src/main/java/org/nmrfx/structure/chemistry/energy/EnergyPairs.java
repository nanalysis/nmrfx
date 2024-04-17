/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.structure.fastlinear.FastVector3D;

/**
 * @author brucejohnson
 */
public class EnergyPairs {

    EnergyCoords eCoords;
    int[] iAtoms;
    int[] jAtoms;
    int[] iUnits;
    int[] jUnits;
    double[] viol;
    double[] weights;
    double[] derivs;
    int nPairs;

    public EnergyPairs(EnergyCoords eCoords) {
        this.eCoords = eCoords;

    }

    public void addPair(int i, int j, int iUnit, int jUnit) {
        resize(nPairs + 1);
        int iPair = nPairs;
        iAtoms[iPair] = i;
        jAtoms[iPair] = j;
        iUnits[iPair] = iUnit;
        jUnits[iPair] = jUnit;

        weights[iPair] = 1.0;
        derivs[iPair] = 0.0;
        nPairs = iPair + 1;
    }

    public void clear() {
        nPairs = 0;
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double r0) {
        addPair(i, j, iUnit, jUnit);
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double r0, double a, double b, double charge) {
        addPair(i, j, iUnit, jUnit);
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
            viol = resize(viol, newSize);
            weights = resize(weights, newSize);
            derivs = resize(derivs, newSize);
        }
    }

    public void addDerivs(AtomBranch[] branches) {
        FastVector3D[] vecCoords = eCoords.getVecCoords();

        FastVector3D v1 = new FastVector3D();
        FastVector3D v2 = new FastVector3D();
        int iMax = 0;
        double dMax = 0.0;
        for (int i = 0; i < nPairs; i++) {
            double deriv = derivs[i];
            if (Math.abs(deriv) > dMax) {
                dMax = Math.abs(deriv);
                iMax = i;
            }
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
        if (dMax > 100000.0) {
            System.out.printf("WARNING: Gradient %8.2g ", dMax);
            ViolationStats stats = getError(iMax, 0.1, 1.0, -1.0);
            if (stats != null) {
                System.out.print(stats.toString());
            }

        }
    }

    public ViolationStats getError(int i, double limitVal, double weight) {
        return null;
    }

    public ViolationStats getError(int i, double limitVal, double weight, double eWeight) {
        return null;
    }

}
