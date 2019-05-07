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
import org.nmrfx.structure.fastlinear.FastVector3D;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.nmrfx.structure.chemistry.Point3;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.nmrfx.structure.chemistry.predict.RNAAttributes;

/**
 *
 * @author Bruce Johnson
 */
public class EnergyCoords {

    private static final int[][] offsets = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {-1, 1, 0}, {0, 0, 1},
    {1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {-1, 1, 1}, {-1, 0, 1},
    {-1, -1, 1}, {0, -1, 1}, {1, -1, 1}
    };

    FastVector3D[] vecCoords = null;
    EnergyPairs ePairs;
    EnergyConstraintPairs eConstraintPairs;
    int[] resNums = null;
    Atom[] atoms = null;
    int[] mAtoms = null;
    int[] shiftClass = null;
    double[] baseShifts = null;
    double[] shifts = null;
    double[] refShifts = null;
    boolean[] swapped = null;
    int[] hBondable = null;
    boolean[] hasBondConstraint = null;
    double[] contactRadii = null;
    int[] cellIndex = null;
    int nAtoms = 0;
    boolean[][] fixed;
    Map<Integer, Set<Integer>> kSwap = null;
    boolean setupShifts = false;

    private static double hbondDelta = 0.60;

    public EnergyCoords() {
        ePairs = new EnergyPairs(this);
        eConstraintPairs = new EnergyConstraintPairs(this);
    }

    public FastVector3D[] getVecCoords(int size) {
        if ((vecCoords == null) || (vecCoords.length != size)) {
            vecCoords = new FastVector3D[size];
            resNums = new int[size];
            atoms = new Atom[size];
            mAtoms = new int[size];
            swapped = new boolean[size];
            contactRadii = new double[size];
            hasBondConstraint = new boolean[size];
            hBondable = new int[size];
            cellIndex = new int[size];
            shiftClass = new int[size];
            baseShifts = new double[size];
            shifts = new double[size];
            refShifts = new double[size];
            for (int i = 0; i < size; i++) {
                vecCoords[i] = new FastVector3D();
                shiftClass[i] = -1;
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
        atomType.eAtom = i;
        if (setupShifts) {
            String aName = atomType.getName();
            int atomClass = RNAAttributes.getAtomSourceIndex(aName);
            if (atomClass >= 0) {
                int rnaClass = 0;
                if (aName.charAt(aName.length() - 1) == '\'') {
                    if (aName.charAt(0) == 'H') {
                        rnaClass = 3;
                    } else {
                        rnaClass = 2;

                    }
                } else {
                    if (aName.charAt(0) == 'H') {
                        rnaClass = 1;
                    } else {
                        rnaClass = 0;

                    }
                }
                atomClass += 256 * rnaClass;
            }
            shiftClass[i] = atomClass;
            Double baseValue = Predictor.getDistBaseShift(atomType);
            baseShifts[i] = baseValue == null ? 0.0 : baseValue;
            refShifts[i] = 0.0;
            PPMv ppmV = atomType.getPPM(0);
            if ((ppmV != null) && ppmV.isValid()) {
                shifts[i] = ppmV.getValue();
            }
        }
    }

    public void clear() {
        ePairs.pairs.clear();
        ePairs.nPairs = 0;
    }

    public void clearDist() {
        eConstraintPairs.nPairs = 0;
        eConstraintPairs.pairs.clear();
    }

    public int getNNOE() {
        return eConstraintPairs.nPairs;
    }

    public int getNContacts() {
        return ePairs.nPairs;
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double r0) {
        ePairs.addPair(i, j, iUnit, jUnit, r0);
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double rLow, double rUp, boolean isBond, int group, double weight) {
        eConstraintPairs.addPair(i, j, iUnit, jUnit, rLow, rUp, isBond, group, weight);
    }
    
    public void updateGroups() {
        eConstraintPairs.updateGroups();
    }

    public void doSwaps() {
        eConstraintPairs.doSwaps();
    }

    public double calcNOE(boolean calcDeriv, double weight) {
        return eConstraintPairs.calcEnergy(calcDeriv, weight);
    }

    public double calcRepel(boolean calcDeriv, double weight) {
        return ePairs.calcRepel(calcDeriv, weight);
    }

    public double calcDistShifts(boolean calcDeriv, double rMax, double weight) {
        return ePairs.calcDistShifts(calcDeriv, rMax, weight);
    }

    public ViolationStats getRepelError(int i, double limitVal, double weight) {
        return ePairs.getError(i, limitVal, weight);
    }

    public ViolationStats getNOEError(int i, double limitVal, double weight) {
        return eConstraintPairs.getError(i, limitVal, weight);
    }

    private char ijWild(String iAtomOld, String jAtomOld, String iAtomNew, String jAtomNew) {
        /* Returns i, j, or n depending if it was found to wild. 
            the char return is i or j if there was a suitable wild found and 
            n if no wild is found.*/
        String iAtomOldSub = iAtomOld.substring(0, iAtomOld.length() - 1);
        String iAtomNewSub = iAtomNew.substring(0, iAtomNew.length() - 1);

        String atomName = iAtomNewSub.substring(iAtomNewSub.indexOf(".") + 1);
        if (atomName.length() < 1) {
            return 'n';
        };

        if (iAtomOldSub.equals(iAtomNewSub) && jAtomNew.equals(jAtomOld)) {
            return 'i';
        }

        String jAtomOldSub = jAtomOld.substring(0, jAtomOld.length() - 1);
        String jAtomNewSub = jAtomNew.substring(0, jAtomNew.length() - 1);

        atomName = jAtomNewSub.substring(jAtomNewSub.indexOf(".") + 1);
        if (atomName.length() < 1) {
            return 'n';
        }
        if (jAtomOldSub.equals(jAtomNewSub) && iAtomNew.equals(iAtomOld)) {
            return 'j';
        }
        return 'n';
    }

//    public double dumpRestraints(boolean calcDeriv, double weight, int mode) {
//        double sum = 0.0;
//        int start = 0;
//        int end = disEnd;
//        if (mode != 0) {
//            start = repelStart;
//            end = repelEnd;
//        }
//        for (int i = start; i < end; i++) {
//            int iAtom = iAtoms[i];
//            int jAtom = jAtoms[i];
//            FastVector3D iV = vecCoords[iAtom];
//            FastVector3D jV = vecCoords[jAtom];
//            double r2 = iV.disSq(jV);
//            derivs[i] = 0.0;
//            viol[i] = 0.0;
//            final double dif;
//            final double r;
//            if (r2 <= rLow2[i]) {
//                r = FastMath.sqrt(r2);
//                dif = rLow[i] - r;
//            } else if (r2 >= rUp2[i]) {
//                r = FastMath.sqrt(r2);
//                dif = rUp[i] - r;
//            } else {
//                continue;
//            }
//            viol[i] = weights[i] * weight * dif * dif;
//            sum += viol[i];
//            if (calcDeriv) {
//                //  what is needed is actually the derivitive/r, therefore
//                // we divide by r
//                // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
//                derivs[i] = -2.0 * weights[i] * weight * dif / (r + RADJ);
//            }
//
//        }
//        return sum;
//    }
    public void addRepelDerivs(AtomBranch[] branches) {
        ePairs.addDerivs(branches);
    }

    public void addNOEDerivs(AtomBranch[] branches) {
        eConstraintPairs.addDerivs(branches);
    }

    public double calcDihedral(int a, int b, int c, int d) {
        FastVector3D av = vecCoords[a];
        FastVector3D bv = vecCoords[b];
        FastVector3D cv = vecCoords[c];
        FastVector3D dv = vecCoords[d];
        return calcDihedral(av, bv, cv, dv);
    }

    /**
     * Calculates the dihedral angle
     *
     * @param a first point
     * @param b second point
     * @param c third point
     * @param d fourth point
     * @return angle
     */
    public static double calcDihedral(final FastVector3D a, final FastVector3D b, final FastVector3D c, final FastVector3D d) {
        Point3 a3 = new Point3(a.getX(), a.getY(), a.getZ());
        Point3 b3 = new Point3(b.getX(), b.getY(), b.getZ());
        Point3 c3 = new Point3(c.getX(), c.getY(), c.getZ());
        Point3 d3 = new Point3(d.getX(), d.getY(), d.getZ());
        return AtomMath.calcDihedral(a3, b3, c3, d3);
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

    public void setCells(EnergyLists eList, int deltaEnd, double limit, double hardSphere, boolean includeH, double shrinkValue, double shrinkHValue) {
        double limit2 = limit * limit;
        double[][] bounds = getBoundaries();
        int[] nCells = new int[3];
        setRadii(hardSphere, includeH, shrinkValue, shrinkHValue);
//        System.out.println("set cells");
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
//            System.out.println(i + " index " + index + " " + atoms[i].getShortName() + " " + idx[0] + " " + idx[1] + " " + idx[2]);
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
//            System.out.println("index " + i + " " + index + " " + (cellStarts[index] + nAdded[index]));
            nAdded[index]++;
        }
        for (int ix = 0; ix < nCells[0]; ix++) {
            for (int iy = 0; iy < nCells[1]; iy++) {
                for (int iz = 0; iz < nCells[2]; iz++) {
                    int iCell = ix + iy * strides[1] + iz * strides[2];
                    int iStart = cellStarts[iCell];
                    int iEnd = iStart + cellCounts[iCell];
//                    System.out.println("iCell " + iCell + " " + iStart + " " + iEnd + " " + ix + " " + iy + " " + iz);
                    int jOffset = 0;
                    for (int iOff = 0; iOff < offsets.length; iOff++) {
                        int dX = offsets[iOff][0];
                        int dY = offsets[iOff][1];
                        int dZ = offsets[iOff][2];
                        int jx = ix + dX;
                        int jy = iy + dY;
                        int jz = iz + dZ;
//                        System.out.println(dX + " " + dY + " " + dZ + "iCell jCell");
                        if ((jx < 0) || (jx >= nCells[0])) {
                            continue;
                        }
                        if ((jy < 0) || (jy >= nCells[1])) {
                            continue;
                        }
                        if ((jz < 0) || (jz >= nCells[2])) {
                            continue;
                        }
                        int jCell = jx + jy * strides[1] + jz * strides[2];
//                        System.out.println(iCell + " cell " + jCell + " offset " + iOff + " " + jOffset++);
                        int jStart = cellStarts[jCell];
                        int jEnd = jStart + cellCounts[jCell];
//                        System.out.println("iCell " + iCell + " jCell " + jCell + " " + jStart + " " + jEnd);

                        for (int i = iStart; i < iEnd; i++) {
                            int ip = atomIndex[i];
                            if ((atoms[ip].getAtomicNumber() == 1) && !includeH) {
                                continue;
                            }
                            if (iCell == jCell) {
                                jStart = i + 1;
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
//                                    System.out.println("i " + i + " j " + j + " iCell " + iCell + " " + jCell + " " + iOff + " atom " + iAtom + " " + (jAtom - iAtom - 1) + " " + atom1.getShortName() + " " + atom2.getShortName() + " " + disSq);
                                    if (disSq < limit2) {
                                        int iRes = resNums[iAtom];
                                        int jRes = resNums[jAtom];
                                        int deltaRes = Math.abs(jRes - iRes);
                                        if (deltaRes >= deltaEnd) {
                                            continue;
                                        }
                                        boolean notFixed = true;
                                        double adjustClose = 0.0;
                                        // fixme could we have invalid jAtom-iAtom-1, if res test inappropriate
                                        if ((iRes == jRes) || (deltaRes == 1)) {
                                            notFixed = !getFixed(iAtom, jAtom);
                                            if (checkCloseAtoms(atom1, atom2)) {
                                                adjustClose = 0.2;
                                            }
                                        }
                                        boolean interactable1 = (contactRadii[iAtom] > 1.0e-6) && (contactRadii[jAtom] > 1.0e-6);
                                        // fixme  this is fast, but could miss interactions for atoms that are not bonded
                                        // as it doesn't test for an explicit bond between the pairs
                                        boolean notConstrained = !hasBondConstraint[iAtom] || !hasBondConstraint[jAtom];
//                                        System.out.println("        " + notFixed + " " + (fixed[iAtom][jAtom - iAtom - 1]) + " " + deltaRes + " "
//                                                + interactable1 + " " + notConstrained);
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
//                                System.out.println(iOff + " " + i + " " + j + " " + k + " " + ip + " " + jp + " " + disSq);
                            }
                        }
                    }
                }
            }
        }
//System.out.println("nrep " + (repelEnd-repelStart) + " " + includeH + " " + limit);
    }

    boolean getFixed(int i, int j) {
        return fixed[i][j];
    }

    void setFixed(int i, int j, boolean state) {
        fixed[i][j] = state;
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
        double[][][] disRange = new double[2][nAtoms][nAtoms];
        fixed = new boolean[nAtoms][nAtoms];
        for (int i = 0; i < nAtoms; i++) {
            int resNum = resNums[i];
            int lastAtom = i + 500;
            if (lastAtom >= nAtoms) {
                lastAtom = nAtoms - 1;
            }
            int nResAtoms = lastAtom - i;
//            int nResAtoms = resCounts[resNum];
//            nResAtoms -= (i - resStarts[resNum]) + 1;
//            if (resNum < (nResidues - 1)) {
//                nResAtoms += resCounts[resNum + 1];
//            }
            Arrays.fill(disRange[0][i], Double.MAX_VALUE);
            Arrays.fill(disRange[1][i], Double.NEGATIVE_INFINITY);

        }
        return disRange;
    }

    public void updateRanges(double[][][] disRanges) {
        for (int i = 0; i < nAtoms; i++) {
            FastVector3D v1 = vecCoords[i];
            for (int j = 0; j < nAtoms; j++) {
                FastVector3D v2 = vecCoords[j];
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
            for (int j = 0; j < nAtoms; j++) {
                double delta = Math.abs(disRanges[1][i][j] - disRanges[0][i][j]);
                //System.out.println(i + " " + j + " " + atoms[i].getShortName() + " " + atoms[i + j + 1].getShortName() + " " + delta);
                setFixed(i, j, delta < tol);
                if (getFixed(i, j)) {
                    nFixed++;
                }
//                if (fixed[i][j]) {
//                    System.out.print(" " + j);
//                }
            }
//            System.out.println("");
        }
        //dumpFixed();
    }

    public void dumpFixed() {
        System.out.println("dump fixed");
        for (int i = 0; i < nAtoms; i++) {
            System.out.println(atoms[i].getShortName());
        }
        for (int i = 0; i < fixed.length; i++) {
            for (int j = 0; j < fixed[i].length; j++) {
                if (fixed[i][j]) {
                    System.out.println(atoms[i].getShortName() + " " + atoms[i + j + 1].getShortName());
                }
            }
        }
    }

    public boolean fixedCurrent() {
        boolean status = (fixed != null) && (fixed.length == nAtoms);
        return status;
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
