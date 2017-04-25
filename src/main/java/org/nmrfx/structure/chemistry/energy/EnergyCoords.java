/*
 * NMRFx Structure : A Program for Calculating Structures 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.structure.chemistry.Atom;
import static org.nmrfx.structure.chemistry.energy.AtomMath.RADJ;
import org.nmrfx.structure.fastlinear.FastVector3D;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.math3.util.FastMath;

/**
 *
 * @author Bruce Johnson
 */
public class EnergyCoords {

    private static final int[][] offsets = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {-1, 1, 0}, {0, 0, 1},
    {1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {-1, 1, 1}, {-1, 0, 1},
    {-1, -1, 1}, {0, -1, 1}, {1, -1, 1}
    };

    final static private int DEFAULTSIZE = 160000;
    FastVector3D[] vecCoords = null;
    int[] resNums = null;
    Atom[] atoms = null;
    int[] hBondable = null;
    boolean[] hasBondConstraint = null;
    double[] contactRadii = null;
    int[] cellIndex = null;
    int[] iAtoms = new int[DEFAULTSIZE];
    int[] jAtoms = new int[DEFAULTSIZE];
    int[] iUnits = new int[DEFAULTSIZE];
    int[] jUnits = new int[DEFAULTSIZE];
    double[] rLow2 = new double[DEFAULTSIZE];
    double[] rLow = new double[DEFAULTSIZE];
    double[] rUp2 = new double[DEFAULTSIZE];
    double[] rUp = new double[DEFAULTSIZE];
    double[] viol = new double[DEFAULTSIZE];
    double[] derivs = new double[DEFAULTSIZE];
    int repelStart = 5000;
    int repelEnd = repelStart;
    int disEnd = 0;
    int nAtoms = 0;
    boolean[][] fixed;
    private static double hbondDelta = 0.60;

    public FastVector3D[] getVecCoords(int size) {
        if ((vecCoords == null) || (vecCoords.length != size)) {
            vecCoords = new FastVector3D[size];
            resNums = new int[size];
            atoms = new Atom[size];
            contactRadii = new double[size];
            hasBondConstraint = new boolean[size];
            hBondable = new int[size];
            cellIndex = new int[size];
            for (int i = 0; i < size; i++) {
                vecCoords[i] = new FastVector3D();
            }
        }
        nAtoms = size;

        return vecCoords;
    }

    public FastVector3D[] getVecCoords() {
        return vecCoords;
    }

    public void setCoords(int i, double x, double y, double z, int resNum, Atom atomType) {
        vecCoords[i].set(x, y, z);
        resNums[i] = resNum;
        atoms[i] = atomType;
    }

    public void clear() {
        repelEnd = repelStart;
    }

    public void clearDist() {
        disEnd = 0;
    }

    public int getNNOE() {
        return disEnd;
    }

    public int getNContacts() {
        return repelEnd - repelStart;
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double r0) {
        iAtoms[repelEnd] = i;
        jAtoms[repelEnd] = j;
        iUnits[repelEnd] = iUnit;
        jUnits[repelEnd] = jUnit;

        this.rLow[repelEnd] = r0;
        rLow2[repelEnd] = r0 * r0;
        rUp2[repelEnd] = Double.MAX_VALUE;
        repelEnd++;
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double rLow, double rUp, boolean isBond, int group) {
        iAtoms[disEnd] = i;
        jAtoms[disEnd] = j;
        iUnits[disEnd] = iUnit;
        jUnits[disEnd] = jUnit;

        this.rLow[disEnd] = rLow;
        rLow2[disEnd] = rLow * rLow;
        this.rUp[disEnd] = rUp;
        rUp2[disEnd] = rUp * rUp;

        hasBondConstraint[i] = isBond;
        hasBondConstraint[j] = isBond;

        disEnd++;
    }

    public double calcRepelOff(boolean calcDeriv, double weight) {

        double sum = 0.0;
        for (int i = 0; i < repelEnd; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            double r2 = iV.disSq(jV);
            derivs[i] = 0.0;
            viol[i] = 0.0;

            if (r2 <= rLow2[i]) {
                double r = FastMath.sqrt(r2);
                double dif = rLow[i] - r;
                viol[i] = weight * dif * dif;
                sum += viol[i];
                if (calcDeriv) {
                    //  what is needed is actually the derivitive/r, therefore
                    // we divide by r
                    derivs[i] = -2.0 * weight * dif / r;
                }
            }
        }
        return sum;
    }

    public double calcRepel(boolean calcDeriv, double weight) {
        return calcEnergy(calcDeriv, weight, 1);
    }

    public double calcNOE(boolean calcDeriv, double weight) {
        return calcEnergy(calcDeriv, weight, 0);
    }

    public String getRepelError(int i, double limitVal, double weight) {
        return getError(i, limitVal, weight, 1);
    }

    public String getNOEError(int i, double limitVal, double weight) {
        return getError(i, limitVal, weight, 0);
    }

    public String getError(int i, double limitVal, double weight, int mode) {
        String modeType = "Dis";
        if (mode == 1) {
            i += repelStart;
            modeType = "Rep";
        }
        int iAtom = iAtoms[i];
        int jAtom = jAtoms[i];
        FastVector3D iV = vecCoords[iAtom];
        FastVector3D jV = vecCoords[jAtom];
        double r2 = iV.disSq(jV);
        double r = FastMath.sqrt(r2);
        double dif = 0.0;
        double constraintDis = 0.0;
        if (r2 <= rLow2[i]) {
            r = FastMath.sqrt(r2);
            dif = rLow[i] - r;
            constraintDis = rLow[i];
        } else if (r2 >= rUp2[i]) {
            r = FastMath.sqrt(r2);
            dif = rUp[i] - r;
            constraintDis = rUp[i];
        }
        String result = "";
        if (Math.abs(dif) > limitVal) {
            double energy = weight * dif * dif;
            result = String.format("%s: %10s %10s %5.2f %5.2f %5.2f %7.3f\n", modeType, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), constraintDis, r, dif, energy);
        }

        return result;

    }

    public double calcEnergy(boolean calcDeriv, double weight, int mode) {
        double sum = 0.0;
        int start = 0;
        int end = disEnd;
        if (mode != 0) {
            start = repelStart;
            end = repelEnd;
        }
        for (int i = start; i < end; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            double r2 = iV.disSq(jV);
            derivs[i] = 0.0;
            viol[i] = 0.0;
            final double dif;
            final double r;
            if (r2 <= rLow2[i]) {
                r = FastMath.sqrt(r2);
                dif = rLow[i] - r;
            } else if (r2 >= rUp2[i]) {
                r = FastMath.sqrt(r2);
                dif = rUp[i] - r;
            } else {
                continue;
            }
            viol[i] = weight * dif * dif;
            sum += viol[i];
            if (calcDeriv) {
                //  what is needed is actually the derivitive/r, therefore
                // we divide by r
                // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                derivs[i] = -2.0 * weight * dif / (r + RADJ);
            }

        }
        return sum;
    }

    public void addRepelDerivs(AtomBranch[] branches) {
        addDerivs(branches, 1);
    }

    public void addNOEDerivs(AtomBranch[] branches) {
        addDerivs(branches, 0);
    }

    public void addDerivs(AtomBranch[] branches, int mode) {
        int start = 0;
        int end = disEnd;
        if (mode != 0) {
            start = repelStart;
            end = repelEnd;
        }
        FastVector3D v1 = new FastVector3D();
        FastVector3D v2 = new FastVector3D();
        for (int i = start; i < end; i++) {
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

    double[][] getBoundaries() {
        double[][] bounds = new double[3][2];
        for (int i = 0; i < 3; i++) {
            bounds[i][0] = Double.MAX_VALUE;
            bounds[i][1] = Double.NEGATIVE_INFINITY;
        }
        for (int i = 0; i < nAtoms; i++) {
            double[] data = vecCoords[i].getValues();
            for (int j = 0; j < 3; j++) {
                bounds[j][0] = Math.min(data[j], bounds[j][0]);
                bounds[j][1] = Math.max(data[j], bounds[j][1]);
            }
        }
        // change bounds to be minimum and size
        for (int j = 0; j < 3; j++) {
            bounds[j][1] = bounds[j][1] - bounds[j][0];
        }
        return bounds;
    }

    public void setRadii(double hardSphere, boolean includeH, double shrinkValue, double shrinkHValue) {
        for (int i = 0; i < nAtoms; i++) {
            Atom atom1 = atoms[i];
            AtomEnergyProp iProp = (AtomEnergyProp) atom1.atomEnergyProp;
            if (iProp == null) {
                contactRadii[i] = 0.0;
            } else if (atom1.getType().endsWith("g")) { // coarseGrain
                contactRadii[i] = 0.0;
            } else {
                hBondable[i] = iProp.getHBondMode();
                double rh1 = iProp.getRh();

                if (atom1.getAtomicNumber() != 1) {
                    rh1 -= shrinkValue;
                } else {
                    rh1 -= shrinkHValue;
                }

                if (!includeH) {
                    if (atom1.hydrogens > 0) {
                        rh1 += hardSphere;
                    }
                }
                contactRadii[i] = rh1;
            }
        }
    }

    public void setCellsOld(int deltaEnd, double limit, double hardSphere, boolean includeH, double shrinkValue, double shrinkHValue) {
        double limit2 = limit * limit;
        double[][] bounds = getBoundaries();
        int[] nCells = new int[3];
        setRadii(hardSphere, includeH, shrinkValue, shrinkHValue);

        clear();

        for (int j = 0; j < 3; j++) {
            nCells[j] = 1 + (int) Math.floor(bounds[j][1] / limit);
        }
        ArrayList<Integer>[][][] cells = new ArrayList[nCells[0]][nCells[1]][nCells[2]];

        for (int i = 0; i < nAtoms; i++) {
            double[] data = vecCoords[i].getValues();
            int[] indices = new int[3];
            for (int j = 0; j < 3; j++) {
                indices[j] = (int) Math.floor((data[j] - bounds[j][0]) / limit);
            }
            if (cells[indices[0]][indices[1]][indices[2]] == null) {
                cells[indices[0]][indices[1]][indices[2]] = new ArrayList<>();
            }
            cells[indices[0]][indices[1]][indices[2]].add(i);
        }
        for (int[] offset : offsets) {
            for (int i = 0; i < nCells[0]; i++) {
                int dI = i + offset[0];
                if ((dI < 0) || (dI >= nCells[0])) {
                    continue;
                }
                for (int j = 0; j < nCells[1]; j++) {
                    int dJ = j + offset[1];
                    if ((dJ < 0) || (dJ >= nCells[1])) {
                        continue;
                    }
                    for (int k = 0; k < nCells[2]; k++) {
                        int dK = k + offset[2];
                        if ((dK < 0) || (dK >= nCells[2])) {
                            continue;
                        }
                        ArrayList<Integer> pts0 = cells[i][j][k];
                        ArrayList<Integer> pts1 = cells[dI][dJ][dK];
                        if ((pts0 == null) || (pts1 == null)) {
                            continue;
                        }
                        for (int ip : pts0) {
                            if ((atoms[ip].getAtomicNumber() == 1) && !includeH) {
                                continue;
                            }
                            for (int jp : pts1) {
                                if ((atoms[jp].getAtomicNumber() == 1) && !includeH) {
                                    continue;
                                }
                                if (ip != jp) {
                                    int iAtom;
                                    int jAtom;
                                    if (ip < jp) {
                                        iAtom = ip;
                                        jAtom = jp;
                                    } else {
                                        iAtom = jp;
                                        jAtom = ip;
                                    }
                                    Atom atom1 = atoms[iAtom];
                                    Atom atom2 = atoms[jAtom];
                                    double disSq = vecCoords[iAtom].disSq(vecCoords[jAtom]);
                                    if (disSq < limit2) {
                                        int iRes = resNums[iAtom];
                                        int jRes = resNums[jAtom];
                                        int deltaRes = jRes - iRes;
                                        if (deltaRes >= deltaEnd) {
                                            continue;
                                        }
                                        boolean notFixed = true;
                                        double adjustClose = 0.0;
                                        if ((iRes == jRes) || (deltaRes == 1)) {
                                            if (fixed[iAtom][jAtom - iAtom - 1]) {
                                                notFixed = false;
                                            }
                                            if (checkCloseAtoms(atom1, atom2)) {
                                                adjustClose = 0.2;
                                            }
                                        }
//                                        if (ok && (contactRadii[iAtom] > 1.0e-6) && (contactRadii[jAtom] > 1.0e-6)) {
//                                            double rH = contactRadii[iAtom] + contactRadii[jAtom];
                                        boolean interactable1 = (contactRadii[iAtom] > 1.0e-6) && (contactRadii[jAtom] > 1.0e-6);
                                        //boolean interactable2 = AtomEnergyProp.interact(atom1, atom2);

                                        if (notFixed && interactable1) {
                                            int iUnit;
                                            int jUnit;
                                            if (atom1.rotGroup != null) {
                                                iUnit = atom1.rotGroup.rotUnit;
                                            } else {
                                                iUnit = -1;
                                            }
                                            if (atom2.rotGroup != null) {
                                                jUnit = atom2.rotGroup.rotUnit;
                                            } else {
                                                jUnit = -1;
                                            }

                                            //double rH = ePair.getRh();
                                            double rH = contactRadii[iAtom] + contactRadii[jAtom];
                                            if (hBondable[iAtom] * hBondable[jAtom] < 0) {
                                                rH -= hbondDelta;
                                            }
                                            rH -= adjustClose;

                                            addPair(iAtom, jAtom, iUnit, jUnit, rH);

                                        }
                                    }
                                }
//                                System.out.println(iOff + " " + i + " " + j + " " + k + " " + ip + " " + jp + " " + dis);
                            }
                        }
                    }
                }
            }
        }
        //System.out.println("nrep " + (repelEnd-repelStart) + " " + includeH + " " + limit);
    }

    public void setCells(EnergyLists eList, int deltaEnd, double limit, double hardSphere, boolean includeH, double shrinkValue, double shrinkHValue) {
        double limit2 = limit * limit;
        double[][] bounds = getBoundaries();
        int[] nCells = new int[3];
        setRadii(hardSphere, includeH, shrinkValue, shrinkHValue);

        clear();

        for (int j = 0; j < 3; j++) {
            nCells[j] = 1 + (int) Math.floor(bounds[j][1] / limit);
        }
        int[] strides = {1, nCells[0], nCells[0] * nCells[1]};
        int nCellsTotal = nCells[0] * nCells[1] * nCells[2];
        int[] cellCounts = new int[nCellsTotal];
        int[] cellStarts = new int[nCellsTotal];
        for (int i = 0; i < nAtoms; i++) {
            double[] data = vecCoords[i].getValues();
            int[] idx = new int[3];
            for (int j = 0; j < 3; j++) {
                idx[j] = (int) Math.floor((data[j] - bounds[j][0]) / limit);
            }
            int index = idx[0] + idx[1] * strides[1] + idx[2] * strides[2];
            cellCounts[index]++;
            cellIndex[i] = index;
        }
        int[] offsets1 = new int[offsets.length];
        int start = 0;
        for (int i = 0; i < nCellsTotal; i++) {
            cellStarts[i] = start;
            start += cellCounts[i];
        }
        for (int i = 0; i < offsets1.length; i++) {
            int delta = offsets[i][0] + offsets[i][1] * strides[1] + offsets[i][2] * strides[2];
            offsets1[i] = delta;
        }
        int[] atomIndex = new int[nAtoms];
        int[] nAdded = new int[nCellsTotal];
        for (int i = 0; i < nAtoms; i++) {
            int index = cellIndex[i];
            atomIndex[cellStarts[index] + nAdded[index]] = i;
            nAdded[index]++;
        }
        for (int iCell = 0; iCell < nCellsTotal; iCell++) {
            int iStart = cellStarts[iCell];
            int iEnd = iStart + cellCounts[iCell];
            for (int offset : offsets1) {
                int jCell = iCell + offset;
                if ((jCell < 0) || (jCell >= nCellsTotal)) {
                    continue;
                }
                int jStart = cellStarts[jCell];
                int jEnd = jStart + cellCounts[jCell];

                for (int i = iStart; i < iEnd; i++) {
                    int ip = atomIndex[i];
                    if ((atoms[ip].getAtomicNumber() == 1) && !includeH) {
                        continue;
                    }
                    for (int j = jStart; j < jEnd; j++) {
                        int jp = atomIndex[j];
                        if ((atoms[jp].getAtomicNumber() == 1) && !includeH) {
                            continue;
                        }
                        if (ip != jp) {
                            int iAtom;
                            int jAtom;
                            if (ip < jp) {
                                iAtom = ip;
                                jAtom = jp;
                            } else {
                                iAtom = jp;
                                jAtom = ip;
                            }
                            Atom atom1 = atoms[iAtom];
                            Atom atom2 = atoms[jAtom];
                            double disSq = vecCoords[iAtom].disSq(vecCoords[jAtom]);
                            if (disSq < limit2) {
                                int iRes = resNums[iAtom];
                                int jRes = resNums[jAtom];
                                int deltaRes = jRes - iRes;
                                if (deltaRes >= deltaEnd) {
                                    continue;
                                }
                                boolean notFixed = true;
                                double adjustClose = 0.0;
                                if ((iRes == jRes) || (deltaRes == 1)) {
                                    if (fixed[iAtom][jAtom - iAtom - 1]) {
                                        notFixed = false;
                                    }
                                    if (checkCloseAtoms(atom1, atom2)) {
                                        adjustClose = 0.2;
                                    }
                                }
                                boolean interactable1 = (contactRadii[iAtom] > 1.0e-6) && (contactRadii[jAtom] > 1.0e-6);
                                // fixme  this is fast, but could miss interactions for atoms that are not bonded
                                // as it doesn't test for an explicit bond between the pairs
                                boolean notConstrained = !hasBondConstraint[iAtom] || !hasBondConstraint[jAtom];

                                if (notFixed && interactable1 && notConstrained) {
                                    int iUnit;
                                    int jUnit;
                                    if (atom1.rotGroup != null) {
                                        iUnit = atom1.rotGroup.rotUnit;
                                    } else {
                                        iUnit = -1;
                                    }
                                    if (atom2.rotGroup != null) {
                                        jUnit = atom2.rotGroup.rotUnit;
                                    } else {
                                        jUnit = -1;
                                    }

                                    //double rH = ePair.getRh();
                                    double rH = contactRadii[iAtom] + contactRadii[jAtom];
                                    if (hBondable[iAtom] * hBondable[jAtom] < 0) {
                                        rH -= hbondDelta;
                                    }
                                    rH -= adjustClose;

                                    addPair(iAtom, jAtom, iUnit, jUnit, rH);

                                }
                            }
                        }
//                                System.out.println(iOff + " " + i + " " + j + " " + k + " " + ip + " " + jp + " " + dis);
                    }
                }
            }
        }
//System.out.println("nrep " + (repelEnd-repelStart) + " " + includeH + " " + limit);
    }

    public double[][][] getFixedRange() {
        int lastRes = Integer.MIN_VALUE;
        int nResidues = 0;
        for (int i = 0; i < nAtoms; i++) {
            if (resNums[i] != lastRes) {
                lastRes = resNums[i];
                nResidues++;
            }
        }
        int[] resCounts = new int[nResidues];
        int[] resStarts = new int[nResidues];
        lastRes = Integer.MIN_VALUE;
        int j = 0;
        for (int i = 0; i < nAtoms; i++) {
            resCounts[resNums[i]]++;
            if (resNums[i] != lastRes) {
                lastRes = resNums[i];
                resStarts[j++] = i;
            }
        }
        double[][][] disRange = new double[2][nAtoms][];
        fixed = new boolean[nAtoms][];
        for (int i = 0; i < nAtoms; i++) {
            int resNum = resNums[i];
            int nResAtoms = resCounts[resNum];
            nResAtoms -= (i - resStarts[resNum]) + 1;
            if (resNum < (nResidues - 1)) {
                nResAtoms += resCounts[resNum + 1];
            }
            disRange[0][i] = new double[nResAtoms];
            fixed[i] = new boolean[nResAtoms];
            Arrays.fill(disRange[0][i], Double.MAX_VALUE);
            disRange[1][i] = new double[nResAtoms];
            Arrays.fill(disRange[1][i], Double.NEGATIVE_INFINITY);

        }
        return disRange;
    }

    public void updateRanges(double[][][] disRanges) {
        for (int i = 0; i < nAtoms; i++) {
            FastVector3D v1 = vecCoords[i];
            for (int j = 0, len = disRanges[0][i].length; j < len; j++) {
                FastVector3D v2 = vecCoords[i + j + 1];
                double dis = v1.dis(v2);
                disRanges[0][i][j] = Math.min(dis, disRanges[0][i][j]);
                disRanges[1][i][j] = Math.max(dis, disRanges[1][i][j]);
            }
        }
    }

    public void updateFixed(double[][][] disRanges) {
        double tol = 0.2;
        int nFixed = 0;
        for (int i = 0; i < nAtoms; i++) {
//            System.out.print(i);
            for (int j = 0, len = disRanges[0][i].length; j < len; j++) {
                double delta = disRanges[1][i][j] - disRanges[0][i][j];
                fixed[i][j] = delta < tol;
                if (fixed[i][j]) {
                    nFixed++;
                }
                Atom atom1 = atoms[i];
                Atom atom2 = atoms[i + j + 1];
//                if (fixed[i][j]) {
//                    System.out.print(" " + j);
//                }
            }
//            System.out.println("");
        }
        System.out.println("nfix " + nFixed);
    }

    public boolean fixedCurrent() {
        return (fixed != null) && (fixed.length == nAtoms);
    }

    public boolean checkCloseAtoms(Atom atom1, Atom atom2) {
        boolean close = false;
        if ((atom1.getAtomicNumber() != 1) || (atom2.getAtomicNumber() != 1)) {
            Atom testAtom1;
            Atom testAtom2;
            if (atom1.rotUnit < atom2.rotUnit) {
                testAtom1 = atom1;
                testAtom2 = atom2;
            } else {
                testAtom2 = atom1;
                testAtom1 = atom2;
            }
            Atom rotGroup1 = testAtom1.rotGroup;
            Atom rotGroup2 = testAtom2.rotGroup;
            Atom parent2 = null;
            if (rotGroup2 != null) {
                parent2 = rotGroup2.parent;
            }
            // atoms in close groups are allowed to be a little closer to add a little more flexibility since we don't allow bond angles and lengths to change
            if (parent2 != null && (parent2.parent == testAtom1)) {
                close = true;
            } else if (rotGroup1 != null) {
                Atom parent1 = rotGroup1.parent;
                if (parent1 != null) {
                    if (parent1.parent == testAtom2) {
                        close = true;
                    } else if (parent1 == testAtom2.parent) {
                        close = true;
                    } else if (rotGroup1 == parent2) {
                        close = true;
                    } else if (parent1 == testAtom2.parent) {
                        close = true;
                    } else if (parent2 == testAtom1.parent) {
                        close = true;
                    } else if (parent1 == parent2) {
                        //  rh1 -= 0.1;
                        //  rh2 -= 0.1;
                    }
                }
            }
        }
        return close;

    }

}
