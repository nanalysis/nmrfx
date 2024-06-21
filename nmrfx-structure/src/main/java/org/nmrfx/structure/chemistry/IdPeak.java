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
package org.nmrfx.structure.chemistry;

import org.nmrfx.chemistry.*;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.structure.noe.NOEAssign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class IdPeak {
    private MoleculeBase molecule;
    List<SpatialSet> atomList = new ArrayList<>();
    List<SpatialSet>[] protonList = new ArrayList[2];
    double keepThresh = 10000.0;
    double disThresh = 10.0;
    boolean useRef = false;
    int ppmSet = 0;

    public void clearAtomList() {
        atomList.clear();
    }

    public void setPPMSet(final int ppmSet) {
        this.ppmSet = ppmSet;
    }

    public void setKeepThresh(double value) {
        keepThresh = value;
    }

    public void setMin(double value) {
        disThresh = value;
    }

    public void setKeepThresh(boolean value) {
        useRef = value;
    }

    public MatchCriteria[] setup(PeakList peakList, Molecule molecule) {
        this.molecule = molecule;
        return NOEAssign.getMatchCriteria(peakList, false);
    }

    public List<SpatialSet>[] scan(final MatchCriteria[] matchCriteria) {
        int nDim = matchCriteria.length;
        ArrayList<SpatialSet>[] matchList = new ArrayList[nDim];
        for (int j = 0; j < nDim; j++) {
            boolean atomPatMatch = false;
            matchList[j] = new ArrayList<>();
            for (SpatialSet spatialSet : atomList) {
                if (spatialSet == null) {
                    continue;
                }

                for (int k = 0; k < matchCriteria[j].getAtomPatCount(); k++) {
                    if (Util.stringMatch(spatialSet.atom.name.toLowerCase(),
                            matchCriteria[j].getAtomPat(k))) {
                        atomPatMatch = true;
                        PPMv ppmv = spatialSet.getPPM(ppmSet);
                        double tol = matchCriteria[j].getTol();
                        if ((ppmv == null) && useRef) {
                            ppmv = spatialSet.getRefPPM();
                            if (ppmv != null) {
                                tol = ppmv.getError() * 3;
                            }
                        }

                        if (ppmv == null) {
                            continue;
                        }

                        if (Math.abs(ppmv.getValue() - matchCriteria[j].getPpm()) < tol) {
                            matchList[j].add(spatialSet);
                            break;
                        }
                    }
                }
            }
            if (!atomPatMatch) {
                throw new IllegalArgumentException("No atoms match pattern for dim \"" + j + "\"");
            }
        }
        return matchList;
    }

    public static double getPPMDelta(double testPPM, MatchCriteria mC) {
// fixme need to to alias (if iFoldCount < 0)
        int iFoldCount = mC.getFoldCount();
        if (iFoldCount < 0) {
            iFoldCount = -iFoldCount;
        }
        double deltaMin = Double.MAX_VALUE;
        for (int iFold = -iFoldCount; iFold <= iFoldCount; iFold++) {
            double ppm = mC.getPpm() + iFold * mC.getFolding();
            double delta = testPPM - ppm;
            if (Math.abs(delta) < Math.abs(deltaMin)) {
                deltaMin = delta;
            }
        }
        return deltaMin;
    }

    boolean checkPPM(SpatialSet sSet, MatchCriteria mC, int iFold) {
        boolean value = false;
        PPMv ppmv = sSet.getPPM(ppmSet);
        double tol = mC.getTol();
        if ((ppmv == null) && useRef) {
            ppmv = sSet.getRefPPM();
            if (ppmv != null) {
                tol = ppmv.getError();
            }
        }
        if (ppmv != null) {
// fixme need to to alias (if iFoldCount < 0)
            int iFoldCount = mC.getFoldCount();
            double ppm = mC.getPpm() + iFold * mC.getFolding();
            if (Math.abs(ppmv.getValue() - ppm) < tol) {
                value = true;
            }
        }
        return value;
    }

    public List<SpatialSet>[] scan3(MatchCriteria[] matchCriteria, boolean useFolding) {
        List<SpatialSet>[] matchLists = new ArrayList[2];
        for (int iDim = 0; iDim < 2; iDim++) {
            matchLists[iDim] = new ArrayList<>();
            int iFoldCount = 0;
            if (useFolding) {
                iFoldCount = matchCriteria[iDim].getFoldCount();
                if (iFoldCount < 0) {
                    iFoldCount = -iFoldCount;
                }
            }
            for (int iFold = -iFoldCount; iFold <= iFoldCount; iFold++) {
                int jFoldCount = 0;
                if (useFolding && (matchCriteria[iDim + 2] != null)) {
                    jFoldCount = matchCriteria[iDim + 2].getFoldCount();
                    if (jFoldCount < 0) {
                        jFoldCount = -jFoldCount;
                    }
                }
                for (int jFold = -jFoldCount; jFold <= jFoldCount; jFold++) {
                    Double sPPM = matchCriteria[iDim].getPpm() + iFold * matchCriteria[iDim].getFolding();
                    SpatialSetPPMComparator ssPC = new SpatialSetPPMComparator();
                    int index1 = Collections.binarySearch(protonList[iDim], sPPM, ssPC);
                    if (index1 < 0) {
                        index1 = -(index1 + 1);
                    }
                    if (index1 >= protonList[iDim].size()) {
                        index1 = protonList[iDim].size() - 1;
                    }
                    if (!protonList[iDim].isEmpty()) {
                        for (int j = index1 - 1; j >= 0; j--) {
                            SpatialSet sSet = protonList[iDim].get(j);
                            if (checkPPM(sSet, matchCriteria[iDim], iFold)) {
                                boolean atomMatch = false;
                                if (matchCriteria[iDim + 2] == null) {
                                    atomMatch = true;
                                } else {
                                    String[] heavyPatterns = matchCriteria[iDim + 2].getAtomPats();
                                    Atom parent = sSet.atom.getParent();
                                    if (parent == null) {
                                        System.out.println("null parent " + sSet.atom.getShortName());
                                    } else {
                                        SpatialSet sSet2 = parent.spatialSet;
                                        if (checkPPM(sSet2, matchCriteria[iDim + 2], jFold)) {
                                            atomMatch = matchName(parent.getName().toLowerCase(), heavyPatterns);
                                        }
                                    }
                                }
                                if (atomMatch) {
                                    matchLists[iDim].add(sSet);
                                }

                            } else {
                                break;
                            }
                        }
                        for (int j = (index1); j < protonList[iDim].size(); j++) {
                            SpatialSet sSet = protonList[iDim].get(j);
                            if (checkPPM(sSet, matchCriteria[iDim], iFold)) {
                                boolean atomMatch = false;
                                if (matchCriteria[iDim + 2] == null) {
                                    atomMatch = true;
                                } else {
                                    String[] heavyPatterns = matchCriteria[iDim + 2].getAtomPats();
                                    Atom parent = sSet.atom.getParent();
                                    if (parent == null) {
                                        System.out.println("null parent " + sSet.atom.getShortName());
                                    } else {
                                        SpatialSet sSet2 = parent.spatialSet;
                                        if (checkPPM(sSet2, matchCriteria[iDim + 2], jFold)) {
                                            atomMatch = matchName(parent.getName().toLowerCase(), heavyPatterns);
                                        }
                                    }
                                }
                                if (atomMatch) {
                                    matchLists[iDim].add(sSet);
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return matchLists;

    }

    /**
     * @return the molecule
     */
    public MoleculeBase getMolecule() {
        return molecule;
    }

    /**
     * @param molecule the molecule to set
     */
    public void setMolecule(MoleculeBase molecule) {
        this.molecule = molecule;
    }

    class SpatialSetPPMComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            Double ppm1 = null;
            Double ppm2 = null;
            int result;
            if (o1 instanceof SpatialSet a1) {
                PPMv ppmv1 = a1.getPPM(ppmSet);
                if ((ppmv1 == null) && useRef) {
                    ppmv1 = a1.getRefPPM();
                }
                if (ppmv1 != null) {
                    ppm1 = ppmv1.getValue();
                }
            } else if (o1 instanceof Double ppm) {
                ppm1 = ppm;
            }
            if (o2 instanceof SpatialSet a2) {
                PPMv ppmv2 = a2.getPPM(ppmSet);
                if ((ppmv2 == null) && useRef) {
                    ppmv2 = a2.getRefPPM();
                }
                if (ppmv2 != null) {
                    ppm2 = ppmv2.getValue();
                }
            } else if (o2 instanceof Double ppm) {
                ppm2 = ppm;
            }
            if (ppm1 != null) {
                if (ppm2 == null) {
                    result = 1;
                } else {
                    result = Double.compare(ppm1, ppm2);
                }
            } else if (ppm2 == null) {
                result = 0;
            } else {
                result = -1;
            }
            return result;
        }
    }

    public void getAtomsWithPPMs() {
        for (CoordSet coordSet : molecule.coordSets.values()) {
            for (Entity entity : coordSet.getEntities().values()) {
                for (Atom atom : entity.atoms) {
                    if (atom.isMethyl() && !atom.isFirstInMethyl()) {
                        continue;
                    }
                    SpatialSet spatialSet = atom.spatialSet;
                    if (spatialSet != null) {
                        PPMv ppmv = spatialSet.getPPM(ppmSet);
                        if ((ppmv == null) && useRef) {
                            ppmv = spatialSet.getRefPPM();
                        }

                        if (ppmv != null) {
                            if (ppmv.getValue() > Atom.NULL_PPM) {
                                atomList.add(spatialSet);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean matchName(String aName, String[] patterns) {
        boolean atomMatch = false;
        aName = aName.toLowerCase();
        for (String pattern : patterns) {
            if ((pattern.isEmpty()) || (pattern.charAt(0) == '*')) {
                atomMatch = true;

                break;
            }
            if (Util.stringMatch(aName, pattern)) {
                atomMatch = true;

                break;
            }
        }
        return atomMatch;
    }

    public void getProtons(int iDim, String[] protonPats) {
        protonList[iDim] = new ArrayList<>();

        for (CoordSet coordSet : molecule.coordSets.values()) {
            for (Entity entity : coordSet.getEntities().values()) {
                for (Atom atom : entity.atoms) {
                    String aName = atom.getName();
                    if (atom.isMethyl() && !atom.isFirstInMethyl()) {
                        continue;
                    }
                    char char0 = aName.charAt(0);
                    if ((char0 == 'H') || (char0 == 'h')) {
                        boolean atomMatch = matchName(aName.toLowerCase(), protonPats);
                        if (atomMatch) {
                            SpatialSet spatialSet = atom.spatialSet;
                            if (spatialSet != null) {
                                PPMv ppmv = spatialSet.getPPM(ppmSet);
                                if ((ppmv == null) && useRef) {
                                    ppmv = spatialSet.getRefPPM();
                                }

                                if (ppmv != null) {
                                    if (ppmv.getValue() > Atom.NULL_PPM) {
                                        protonList[iDim].add(spatialSet);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SpatialSetPPMComparator ssPC = new SpatialSetPPMComparator();
        protonList[iDim].sort(ssPC);

    }

    static boolean checkPattern(SpatialSet[] spatialSets, MatchCriteria[] matchCriteria) {
        int ires;
        int jres;
        int ares;
        int nDim = matchCriteria.length;
        String[] atest = new String[nDim];
        int[] rtest = new int[nDim];
        int[] parent = new int[nDim];

        ires = -1;
        jres = -1;
// change to also support magnetization linkage and parent should work either direction
        for (int i = 0; i < nDim; i++) {
            MatchCriteria mC = matchCriteria[i];
            if (!mC.getRelation().isEmpty()) {
                if ((mC.getRelation().charAt(0) != 'D')
                        && (mC.getRelation().charAt(0) != 'd')) {
                    throw new IllegalArgumentException(
                            "Invalid relation " + mC.getRelation());
                }

                parent[i] = Integer.parseInt(mC.getRelation().substring(1));
                parent[i]--;

                /*printf("Parent %d %d %d\n",parent[i],spatialSets[atmNums[i]].parent,atmNums[parent[i]]);*/
                if (spatialSets[i].atom.parent != spatialSets[parent[i]].atom) {
                    return (false);
                }
            }
        }

        for (int i = 0; i < nDim; i++) {
            atest[i] = spatialSets[i].atom.name.toLowerCase().trim();

            Compound residue = (Compound) spatialSets[i].atom.entity;
            ares = Integer.parseInt(residue.number);
            rtest[i] = ares;
        }

        boolean atomsMatch = true;

        for (int i = 0; i < nDim; i++) {
            boolean atomMatch = false;
            MatchCriteria mC = matchCriteria[i];

            for (int k = 0; k < mC.getAtomPatCount(); k++) {
                if ((mC.getAtomPat(k).isEmpty())
                        || (mC.getAtomPat(k).charAt(0) == '*')) {
                    atomMatch = true;

                    break;
                }

                if (Util.stringMatch(atest[i], mC.getAtomPat(k))) {
                    atomMatch = true;

                    break;
                }
            }

            if (!atomMatch) {
                atomsMatch = false;

                break;
            }
        }
        if (!atomsMatch) {
            return false;
        }
        boolean ok = true;
        boolean[] processedRes = new boolean[nDim];
        boolean noResidue = true;
        for (MatchCriteria mC : matchCriteria) {
            for (int k = 0; k < mC.getResPatCount(); k++) {
                if (!mC.getResPat(k).isEmpty()) {
                    noResidue = false;
                }
            }
        }
        if (noResidue) {
            return true;
        }

        // check for explicit residue number, if present and correct then no need for
        // further processing of this dimension
        // if any pattern present other than explicit residue nmber then further
        // analysis necessary
        //  if only an explicit residue numbere is present for a dimension, then if it is
        // not correct pattern is not ok, no need for further analysis
        int nProcessed = 0;

        for (int i = 0; i < nDim; i++) {
            ok = false;
            MatchCriteria mC = matchCriteria[i];

            for (int k = 0; k < mC.getResPatCount(); k++) {
                boolean isInt = true;
                ares = 0;
                int rPatLen = mC.getResPat(k).length();
                for (int iCh = 0; iCh < rPatLen; iCh++) {
                    char rCh = mC.getResPat(k).charAt(iCh);
                    if (!Character.isDigit(rCh)) {
                        isInt = false;
                        break;
                    }
                }
                if (isInt) {
                    try {
                        ares = Integer.parseInt(mC.getResPat(k));
                        isInt = true;
                    } catch (NumberFormatException nE) {
                        isInt = false;
                    }
                }

                if (isInt) {
                    if (ares == rtest[i]) {
                        processedRes[i] = true;
                        nProcessed++;
                        ok = true;

                        break;
                    }
                } else {
                    ok = true;

                    break;
                }
            }

            if (!ok) {
                break;
            }
        }

        //  look for i.atom and j.atom patterns with out delta (i-1, j-1 etc.)
        // only check if there is only one residue pattern
        // should find at least one dimension with a single residue pattern
        if (ok && (nProcessed < nDim)) {
            for (int i = 0; i < nDim; i++) {
                if (processedRes[i]) {
                    continue;
                }
                MatchCriteria mC = matchCriteria[i];

                int nPats = mC.getResPatCount();

                if (nPats == 1) {
                    if (mC.getResPat(0).equals("i")) {
                        if ((ires != -1) && (ires != rtest[i])) {
                            ok = false;
                        } else {
                            nProcessed++;
                            processedRes[i] = true;
                            ires = rtest[i];
                        }
                    } else if (mC.getResPat(0).equals("j")) {
                        if ((jres != -1) && (jres != rtest[i])) {
                            ok = false;
                        } else {
                            nProcessed++;
                            processedRes[i] = true;
                            jres = rtest[i];
                        }
                    }
                }
            }

            // should have found at least one dimension with an i.atom or j.atom pattern
            // fixme really should check to see if there is at least one of these patterns
            // for each of i or j that are used
            if ((ires == -1) && (jres == -1)) {
                throw new IllegalArgumentException("bad format, no i.atom or j.atom pattern");
            }

            if (ok && (nProcessed < nDim)) {
                for (int i = 0; i < nDim; i++) {
                    if (processedRes[i]) {
                        continue;
                    }
                    MatchCriteria mC = matchCriteria[i];

                    int nPats = mC.getResPatCount();
                    ok = false;

                    for (int k = 0; k < nPats; k++) {
                        int delta = 0;
                        int res = 0;

                        if (mC.getResPat(k).length() == 0) {
                            res = ires;
                        } else if (mC.getResPat(k).charAt(0) == 'i') {
                            res = ires;
                        } else if (mC.getResPat(k).charAt(0) == 'i') {
                            res = jres;
                        } else {
                            throw new IllegalArgumentException("Bad format, should start with i or j");
                        }

                        if (res == -1) {
                            throw new IllegalArgumentException("bad format, no i.atom or j.atom pattern");
                        }

                        if (Util.stringMatch(mC.getResPat(k), "[ij][-+][0-9]")) {
                            try {
                                int mul = 1;
                                if (mC.getResPat(k).charAt(1) == '-') {
                                    mul = -1;
                                }
                                delta = mul * Integer.parseInt(mC.getResPat(k).substring(2));
                            } catch (NumberFormatException nE1) {
                                throw new IllegalArgumentException("Bad format " + mC.getResPat(k));
                            }
                        }

                        int test = res + delta;

                        if (test == rtest[i]) {
                            ok = true;

                            break;
                        }
                    }

                    if (!ok) {
                        break;
                    }
                }
            }
        }

        return ok;
    }

    IdResult measureDistances(SpatialSet[] spatialSets, MatchCriteria[] matchCriteria) {
        int nDim = spatialSets.length;
        SpatialSet proton1 = null;
        SpatialSet proton2 = null;
        PPMv ppmv;
        double[] dp = new double[nDim];
        double dismin = 1.0e6;
        double dismax = -1.0e6;
        double dissum = 0.0;
        int ndis = 0;
        int nthresh = 0;
        double percent;
        double dis;
        String[] aname = new String[nDim];
        Point3 point1;
        Point3 point2;
        int j;
        IdResult idResult = new IdResult(nDim);
        for (j = 0; j < nDim; j++) {
            aname[j] = spatialSets[j].atom.name;
            idResult.setSpatialSet(j, spatialSets[j]);
            boolean isProton = aname[j].toUpperCase().charAt(0) == 'H';
            if (isProton) {
                if (proton1 == null) {
                    proton1 = spatialSets[j];
                } else {
                    proton2 = spatialSets[j];
                }
            }

            ppmv = spatialSets[j].getPPM(ppmSet);
            if ((ppmv == null) && useRef) {
                ppmv = spatialSets[j].getRefPPM();
            }

            if (ppmv != null) {
                double delta = getPPMDelta(ppmv.getValue(), matchCriteria[j]);
                dp[j] = 100.0 * delta / matchCriteria[j].getTol();
                idResult.dp[j] = dp[j];
            } else {
                idResult.dp[j] = 1.0e30;
                System.out.println("no ppm");
            }
        }

        int[] structureList = molecule.getActiveStructures();

        if ((proton1 != null) && (proton2 != null) && (structureList.length != 0)) {
            for (int iStructure : structureList) {
                point1 = proton1.getPoint(iStructure);
                point2 = proton2.getPoint(iStructure);

                if ((point1 == null) || (point2 == null)) {
                    continue;
                }

                dis = Atom.calcDistance(point1, point2);
                if (dis < dismin) {
                    dismin = dis;
                }
                if (dis > dismax) {
                    dismax = dis;
                }
                dissum += dis;

                if (dis < disThresh) {
                    nthresh++;
                }

                ndis++;
            }

            if (ndis > 0) {
                dis = dissum / ndis;
                percent = 100.0 * (((double) nthresh) / ((double) ndis));
                idResult.hasDistances = true;
            } else {
                percent = 100.0;
                dis = 0.0;
                dismin = 0.0;
                dismax = 0.0;
            }

            if (dismin > keepThresh) {
                return (null);
            }
            idResult.setDistances(dis, dismin, dismax);
            idResult.inRange = percent;
        }

        return idResult;
    }

    public List<IdResult> getIdResults(List<SpatialSet>[] matchList, MatchCriteria[] matchCriteria) {
        int nDim = matchList.length;
        int[] idx = new int[nDim];
        List<IdResult> result = new ArrayList<>();
        for (int i = 0; i < nDim; i++) {
            if (matchList[i].isEmpty()) {
                return result;
            }

            idx[i] = 0;
        }

        SpatialSet[] spatialSets = new SpatialSet[nDim];

        while (true) {
            for (int i = 0; i < nDim; i++) {
                spatialSets[i] = matchList[i].get(idx[i]);
            }

            if (checkPattern(spatialSets, matchCriteria)) {
                IdResult idResult = measureDistances(spatialSets, matchCriteria);
                if (idResult != null) {
                    result.add(idResult);
                }
            }
            int i;
            for (i = 0; i < nDim; i++) {
                idx[i]++;

                if (idx[i] >= matchList[i].size()) {
                    idx[i] = 0;
                } else {
                    break;
                }
            }

            if (i == nDim) {
                break;
            }
        }

        return result;
    }

    public List<IdResult> getResults2(List<SpatialSet>[] matchList, MatchCriteria[] matchCriteria) {
        int nDim = matchList.length;
        int[] idx = new int[nDim];
        List<IdResult> result = new ArrayList<>();
        for (int i = 0; i < nDim; i++) {
            if ((matchList[i] == null) || (matchList[i].isEmpty())) {
                return result;
            }
            idx[i] = 0;
        }

        SpatialSet[] spatialSets = new SpatialSet[nDim];

        while (true) {
            for (int i = 0; i < nDim; i++) {
                spatialSets[i] = matchList[i].get(idx[i]);
            }

            IdResult idResult = measureDistances(spatialSets, matchCriteria);
            if (idResult != null) {
                result.add(idResult);
            }

            int i;
            for (i = 0; i < nDim; i++) {
                idx[i]++;

                if (idx[i] >= matchList[i].size()) {
                    idx[i] = 0;
                } else {
                    break;
                }
            }

            if (i == nDim) {
                break;
            }
        }

        return result;
    }
}
