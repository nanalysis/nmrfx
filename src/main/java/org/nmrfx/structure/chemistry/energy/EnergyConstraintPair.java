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
public class EnergyConstraintPair extends EnergyPairs {

    int[] iGroups;
    int[] groupSizes;
    double[] rUp2;
    double[] rUp;
    List<ConstraintPair> pairs = new ArrayList<>();

    class ConstraintPair {

        int i;
        int j;
        int iUnit;
        int jUnit;
        double rLow;
        double rUp;
        boolean isBond;
        int group;
        double weight;

        ConstraintPair(int i, int j, int iUnit, int jUnit, double rLow, double rUp, boolean isBond, int group, double weight) {
            this.i = i;
            this.j = j;
            this.iUnit = iUnit;
            this.rLow = rUp;
            this.rUp = rUp;
            this.isBond = isBond;
            this.group = group;
            this.weight = weight;
        }
    }

    public void initPairs(int n) {
        super.initPairs(n);
        rUp2 = new double[n];
        rUp = new double[n];
        iGroups = new int[n];
        groupSizes = new int[n];
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double rLow, double rUp, boolean isBond, int group, double weight) {
        ConstraintPair pair = new ConstraintPair(i, j, iUnit, jUnit, rLow, rUp, isBond, group, weight);
        pairs.add(pair);
    }

    public void updatePairs() {
        initPairs(pairs.size());
        int iPair = 0;
        for (ConstraintPair pair : pairs) {
            iGroups[iPair] = pair.group;

            iAtoms[iPair] = pair.i;
            jAtoms[iPair] = pair.j;
            iUnits[iPair] = pair.iUnit;
            jUnits[iPair] = pair.jUnit;

            this.rLow[iPair] = pair.rLow;
            this.rLow2[iPair] = pair.rLow * pair.rLow;
            this.rUp[iPair] = pair.rUp;
            this.rUp2[iPair] = pair.rUp * pair.rUp;
            weights[iPair] = pair.weight;
            eCoords.hasBondConstraint[pair.i] = pair.isBond;
            eCoords.hasBondConstraint[pair.j] = pair.isBond;
            if (eCoords.fixed != null) {
                if (pair.isBond) {
                    int i = pair.i;
                    int j = pair.j;
                    eCoords.setFixed(i, j, true);
                }
            }

            iPair++;
        }
        nPairs = pairs.size();

//        repelEnd++;
    }

    public double calcEnergy(boolean calcDeriv, double weight) {
        double sum = 0.0;
        for (int i = 0; i < nPairs; i++) {
            sum += calcEnergy(calcDeriv, weight, i);
            if (groupSizes[i] > 1) {
                i += groupSizes[i] - 1;
            }
        }
        return sum;
    }

    public double calcEnergy(boolean calcDeriv, double weight, int i) {
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        double sum = 0.0;
        int groupSize = groupSizes[i];
        int nMono = 1;
        double r2;
        double r2Min = Double.MAX_VALUE;
        if (groupSize > 1) {
            double sum2 = 0.0;
            for (int j = 0; j < groupSize; j++) {
                int iAtom = iAtoms[i + j];
                int jAtom = jAtoms[i + j];
                FastVector3D iV = vecCoords[iAtom];
                FastVector3D jV = vecCoords[jAtom];
                double r2Temp = iV.disSq(jV);
                double r = FastMath.sqrt(r2Temp);
                sum2 += FastMath.pow(r, -6);
                derivs[i + j] = 0.0;
                viol[i + j] = 0.0;
                if (r2Temp < r2Min) {
                    r2Min = r2Temp;
                }
            }
            sum2 /= nMono;
            double r = FastMath.pow(sum2, -1.0 / 6);
            r2 = r * r;
            for (int j = 0; j < groupSize; j++) {
                disSq[i + j] = r2;
            }
        } else {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            r2 = iV.disSq(jV);
            disSq[i] = r2;
            derivs[i] = 0.0;
            viol[i] = 0.0;
            r2Min = r2;
        }
        final double dif;
        final double r;
        if (r2Min <= rLow2[i]) {
            r = FastMath.sqrt(r2Min);
            dif = rLow[i] - r;
        } else if (r2 >= rUp2[i]) {
            r = FastMath.sqrt(r2);
            dif = rUp[i] - r;
        } else {
            return 0.0;
        }
        viol[i] = weights[i] * weight * dif * dif;
        sum += viol[i];
        if (calcDeriv) {
            //  what is needed is actually the derivative/r, therefore
            // we divide by r
            // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
            derivs[i] = -2.0 * weights[i] * weight * dif / (r + RADJ);
        }
        if (groupSize > 1) {
            for (int j = 1; j < groupSize; j++) {
                viol[i + j] = viol[i];
                sum += viol[i + j];
                if (calcDeriv) {
                    //  what is needed is actually the derivative/r, therefore
                    // we divide by r
                    // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                    derivs[i + j] = derivs[i];
                }
            }
        }

        return sum;
    }

}
