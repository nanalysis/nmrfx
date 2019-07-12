/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Molecule;
import static org.nmrfx.structure.chemistry.energy.AtomMath.RADJ;
import org.nmrfx.structure.fastlinear.FastVector3D;

/**
 *
 * @author brucejohnson
 */
public class EnergyConstraintPairs extends EnergyDistancePairs {

    int[] iGroups;
    int[] groupSizes;
    double[] rUp2;
    double[] rUp;

    public EnergyConstraintPairs(EnergyCoords eCoords) {
        super(eCoords);

    }

    void resize(int size) {
        if ((iAtoms == null) || (iAtoms.length < size)) {
            super.resize(size);
            int newSize = iAtoms.length;
            iGroups = resize(iGroups, newSize);
            groupSizes = resize(groupSizes, newSize);
            rUp = resize(rUp, newSize);
            rUp2 = resize(rUp2, newSize);
        }

    }

    public void addPair(int i, int j, int iUnit, int jUnit, double rLow, double rUp, boolean isBond, int group, double weight) {
        if (i != j) {
            resize(nPairs + 1);
            int iPair = nPairs;
            iGroups[iPair] = group;

            iAtoms[iPair] = i;
            jAtoms[iPair] = j;
            iUnits[iPair] = iUnit;
            jUnits[iPair] = jUnit;

            this.rLow[iPair] = rLow;
            this.rLow2[iPair] = rLow * rLow;
            this.rUp[iPair] = rUp;
            this.rUp2[iPair] = rUp * rUp;
            weights[iPair] = weight;
            derivs[iPair] = 0.0;

            if (eCoords.fixed != null) {
                if (isBond) {
                    eCoords.setFixed(j, i, true);
                    eCoords.setFixed(i, j, true);
                }
            } else {
                System.out.println("null fixed");
            }
            nPairs = iPair + 1;
        }
    }

    public void updateSwappable() {
        Atom[] atoms = eCoords.atoms;
        int[] mAtoms = eCoords.mAtoms;
        int nPartner = 0;
        for (int i = 0; i < atoms.length; i++) {
            Optional<Atom> partner = atoms[i].getMethylenePartner();
            if (partner.isPresent()) {
                mAtoms[i] = partner.get().eAtom;
                nPartner++;
            } else {
                mAtoms[i] = -1;
            }
        }
        eCoords.kSwap = new HashMap<>();
        Map<Integer, Set<Integer>> kSwap = eCoords.kSwap;

        for (int k = 0; k < nPairs; k++) {
            int groupSize = groupSizes[k];
            for (int kk = 0; kk < groupSize; kk++) {
                int i = iAtoms[k + kk];
                int j = jAtoms[k + kk];
                int storeIndex;
                if (mAtoms[i] != -1) {
                    if (i < mAtoms[i]) {
                        storeIndex = i;
                    } else {
                        storeIndex = mAtoms[i];
                    }
                    Set<Integer> swaps = kSwap.get(storeIndex);
                    if (swaps == null) {
                        swaps = new HashSet<>();
                        kSwap.put(storeIndex, swaps);
                    }
                    swaps.add(k);
                }
                if (mAtoms[j] != -1) {
                    if (j < mAtoms[j]) {
                        storeIndex = j;
                    } else {
                        storeIndex = mAtoms[j];
                    }
                    Set<Integer> swaps = kSwap.get(storeIndex);
                    if (swaps == null) {
                        swaps = new HashSet<>();
                        kSwap.put(storeIndex, swaps);
                    }
                    swaps.add(k);
                }
            }
            if (groupSizes[k] > 1) {
                k += groupSizes[k] - 1;
            }
        }
    }

    public void updateGroups() {
        for (int i = 0; i < nPairs;) {
            groupSizes[i] = 1;
            int j = i + 1;
            while (iGroups[j] == iGroups[i] && j < nPairs) {
                groupSizes[i]++;
                j++;
            }
            i = j;
        }
        updateSwappable();
    }

    public void doSwaps() {
        for (Map.Entry<Integer, Set<Integer>> entry : eCoords.kSwap.entrySet()) {
            doSwap(entry.getKey(), entry.getValue());
        }
    }

    public void doSwap(int i, Set<Integer> swaps) {
        boolean[] swapped = eCoords.swapped;
        int[] mAtoms = eCoords.mAtoms;
        double preSwap = swapEnergy(swaps);
        swapIt(i);
        double postSwap = swapEnergy(swaps);
        if (postSwap < preSwap) {
            swapped[i] = !swapped[i];
            swapped[mAtoms[i]] = !swapped[mAtoms[i]];
        } else {
            // restore if swap not lower energy
            swapIt(i);
        }
//        double restoreSwap = swapEnergy(swaps);
//        System.out.printf("%3d %10s %8.3f %8.3f %8.3f\n", i, atoms[i].getFullName(), preSwap, postSwap, restoreSwap);
    }

    double swapEnergy(Set<Integer> swaps) {
        double sum = 0.0;
        for (Integer k : swaps) {
            sum += calcEnergy(false, 2.0, k);
        }
        return sum;
    }

    void swapIt(int origAtom) {
        int[] mAtoms = eCoords.mAtoms;
        int swapAtom = mAtoms[origAtom];
        if (swapAtom != -1) {
            Set<Integer> swaps = eCoords.kSwap.get(origAtom);
            if (swaps != null) {
                for (Integer k : swaps) {
                    int groupSize = groupSizes[k];
                    for (int kk = 0; kk < groupSize; kk++) {
                        int ik = kk + k;
                        if (iAtoms[ik] == origAtom) {
                            iAtoms[ik] = swapAtom;
                        } else if (iAtoms[ik] == swapAtom) {
                            iAtoms[ik] = origAtom;
                        }
                        if (jAtoms[ik] == origAtom) {
                            jAtoms[ik] = swapAtom;
                        } else if (jAtoms[ik] == swapAtom) {
                            jAtoms[ik] = origAtom;
                        }
                    }
                }
            }
        }
    }

    public void dumpSwaps() {
        for (Map.Entry<Integer, Set<Integer>> entry : eCoords.kSwap.entrySet()) {
            dumpSwap(entry.getKey(), entry.getValue());
        }
    }

    public void dumpSwap(int iAtom, Set<Integer> set) {
        Atom[] atoms = eCoords.atoms;
        System.out.print(atoms[iAtom].getFullName());
        for (Integer k : set) {
            int groupSize = groupSizes[k];
            for (int kk = 0; kk < groupSize; kk++) {
                int ik = kk + k;
                System.out.print(" " + k + " " + ik + " " + atoms[iAtoms[ik]].getFullName() + " " + atoms[jAtoms[ik]].getFullName());
            }
        }
        System.out.println("");
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

    public ViolationStats getError(int i, double limitVal, double weight) {
        Atom[] atoms = eCoords.atoms;
        String modeType = "Dis";

        int iAtom = iAtoms[i];
        int jAtom = jAtoms[i];
        double r2 = disSq[i];
        double r = FastMath.sqrt(r2);
        double dif = 0.0;
        if (r2 <= rLow2[i]) {
            r = FastMath.sqrt(r2);
            dif = rLow[i] - r;
        } else if (r2 >= rUp2[i]) {
            r = FastMath.sqrt(r2);
            dif = rUp[i] - r;
        }
        String result = "";
        ViolationStats stat = null;
        if (Math.abs(dif) > limitVal) {
            double energy = weights[i] * weight * dif * dif;
            stat = new ViolationStats(0, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), r, rLow[i], rUp[i], energy, eCoords);
        }

        return stat;
    }

    public void dumpRestraints() {
        DecimalFormat doubFormatter = new DecimalFormat("#.0");
        ArrayList<String[]> groupLineElements = new ArrayList<>();
        String prevGroup = "";
        String prevIAtom = "";
        String prevJAtom = "";
        Atom[] atoms = eCoords.atoms;
        try {
            FileWriter writerFile = new FileWriter("nmrfxsRestraints.txt");
            for (int i = 0; i < nPairs; i++) {
                String iIndex = Integer.toString(i);
                String iGroup = Integer.toString(iGroups[i]);
                boolean newGroup = !prevGroup.equals(iGroup);
                if (newGroup) {
                    for (String[] elems : groupLineElements) {
                        String line = String.format("%5s  %5s  %-13s  %-13s  %6s  %6s", elems[0], elems[1], elems[2], elems[3], elems[4], elems[5]);
                        writerFile.write(line + "\n");
                    }
                    groupLineElements.clear();
                }
                int iAtomIndex = iAtoms[i];
                int jAtomIndex = jAtoms[i];
                Molecule mol = Molecule.getActive();
                Atom iAtom = atoms[iAtomIndex];
                Atom jAtom = atoms[jAtomIndex];

                String iAtomName = iAtom.getFullName();
                String jAtomName = jAtom.getFullName();
                char wild = 'n';
                //if (!newGroup) {
                //    wild = ijWild(prevIAtom, prevJAtom, iAtomName, jAtomName);
                //}
                // If there is a new write out the values, if not, make empty
                String lower = newGroup ? doubFormatter.format(rLow[i]) : "";
                String upper = newGroup ? doubFormatter.format(rUp[i]) : "";
                String[] lineElements = {iIndex, iGroup, iAtomName, jAtomName, lower, upper};

                // If we find a wild, no need to add another line, can just use star
                // for wild in the line that wouldve preceded the new element
                if (wild == 'n') {
                    groupLineElements.add(lineElements);
                } else {
                    int editIndex = wild == 'i' ? 2 : 3;
                    lineElements = groupLineElements.get(groupLineElements.size() - 1);
                    String atomName = lineElements[editIndex];
                    atomName = atomName.substring(0, atomName.length() - 1) + "*";
                    lineElements[editIndex] = atomName;
                }

                prevGroup = iGroup;
                prevIAtom = iAtomName;
                prevJAtom = jAtomName;
            }
            writerFile.close();
        } catch (IOException e) {
            System.err.println("Error dumping NMRFxS restraints. Exception : " + e);
        }

    }

}
