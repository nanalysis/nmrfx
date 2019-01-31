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
package org.nmrfx.structure.chemistry.constraints;

import cern.jet.stat.Descriptive;
import org.nmrfx.structure.chemistry.*;
import org.nmrfx.processor.datasets.peaks.AtomResonance;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.Resonance;
import org.nmrfx.processor.datasets.peaks.SpectralDim;
import org.nmrfx.structure.utilities.Util;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

@SuppressWarnings({"UnusedDeclaration"})
enum Flags {

    REDUNDANT("redundant", 'r') {
    },
    FIXED("fixed", 'f') {
    },
    MAXAMBIG("maxamb", 'a') {
    },
    MINCONTRIB("mincontrib", 'c') {
    },
    DIAGONAL("diagonal", 'd') {
    },
    MINPPM("minppm", 'p') {
    },
    MAXVIOL("maxviol", 'v') {
    },
    LABEL("label", 'l') {
    },
    USER("user", 'u') {
    };
    private String description;
    private char charDesc;

    Flags(String description, char charDesc) {
        this.description = description;
        this.charDesc = charDesc;
    }

    void set(Noe noe) {
        noe.inactivate(this);
    }

    public String getDescription() {
        return description;
    }

    public char getCharDesc() {
        return charDesc;
    }
}

enum DisTypes {

    MINIMUM("minimum") {
        public double getDistance(Noe noe) {
            return noe.disStatAvg.getMin();
        }
    },
    MAXIMUM("maximum") {
        public double getDistance(Noe noe) {
            return noe.disStatAvg.getMax();
        }
    },
    MEAN("mean") {
        public double getDistance(Noe noe) {
            return noe.disStatAvg.getMean();
        }
    };
    private String description;

    public abstract double getDistance(Noe noe);

    DisTypes(String description) {
        this.description = description;
    }
}

public class Noe implements Constraint, Serializable {

    private static NoeSet activeSet = NoeSet.addSet("default");
    private static double SYM_BONUS = 10.0;
    private static double CMAX = 5.5;
    private static double CMAX_BONUS = 10.0;
    private static double MAX_BONUS = 20.0;
    private static boolean useDistances = false;
    private static int nStructures = 0;
    private static double tolerance = 0.2;
    private static char[] violCharArray = new char[0];
    private static DistanceStat defaultStat = new DistanceStat();
    private static boolean sumAverage = true;
    private int idNum = 0;
    public SpatialSetGroup spg1;
    public SpatialSetGroup spg2;
    public Peak peak = null;
    public double intensity = 0.0;
    public double volume = 0.0;
    public double scale = 1.0;
    public double atomScale = 1.0;
    public DistanceStat disStat = defaultStat;
    public DistanceStat disStatAvg = defaultStat;
    public double lower = 0.0;
    public double target = 0.0;
    public double upper = 0.0;
    public int dcClass = 0;
    public double ppmError = 0.0;
    private short active = 1;
    public boolean symmetrical = false;
    public double contribution = 1.0;
    public double disContrib = 1.0;
    public int nPossible = 0;
    public double netWorkValue = 1;
    private static boolean dirty = true;
    private static boolean calibratable = true;
    private boolean swapped = false;
    private boolean filterSwapped = false;
    public Map resMap = null;
    EnumSet<Flags> activeFlags = null;
    private static DisTypes distanceType = DisTypes.MINIMUM;
    private GenTypes genType = GenTypes.MANUAL;
    public static int ppmSet = 0;

    public Noe(Peak p, SpatialSet sp1, SpatialSet sp2, double newScale) {
        SpatialSetGroup spg1t = new SpatialSetGroup(sp1);
        SpatialSetGroup spg2t = new SpatialSetGroup(sp2);
        this.spg1 = spg1t;
        this.spg2 = spg2t;
        if (spg1t.compare(spg2t) >= 0) {
            swapped = true;
        }

        peak = p;
        scale = newScale;
        idNum = activeSet.getSize();
        activeSet.add(this);
        dirty = true;
        activeFlags = EnumSet.noneOf(Flags.class);

    }

    public Noe(Peak p, SpatialSetGroup spg1, SpatialSetGroup spg2, double newScale) {
        this.spg1 = spg1;
        this.spg2 = spg2;
        if (spg1.compare(spg2) > 0) {
            swapped = true;
        }
        peak = p;
        scale = newScale;
        idNum = activeSet.getSize();
        activeSet.add(this);
        dirty = true;
        activeFlags = EnumSet.noneOf(Flags.class);

    }

    public static ArrayList getPeakList(Peak peak) {
        ArrayList peakList = (ArrayList) activeSet.getConstraintsForPeak(peak);
        return peakList;
    }

    public int getID() {
        return idNum;
    }

    public static double getTolerance() {
        return tolerance;
    }

    public static void setTolerance(double value) {
        tolerance = value;
    }

    public void updatePPMError(MatchCriteria[] matchCriteria) {

        PeakDim peakDim = peak.getPeakDim(matchCriteria[0].getDim());
        double ppm = peakDim.getChemShift();
        matchCriteria[0].setPPM(ppm);

        peakDim = peak.getPeakDim(matchCriteria[1].getDim());
        ppm = peakDim.getChemShift();
        matchCriteria[1].setPPM(ppm);
        SpatialSet[] spSets = new SpatialSet[4];
        spSets[0] = spg1.getFirstSet();
        spSets[1] = spg2.getFirstSet();

        if (matchCriteria[2] != null) {
            peakDim = peak.getPeakDim(matchCriteria[2].getDim());
            ppm = peakDim.getChemShift();
            matchCriteria[2].setPPM(ppm);
            spSets[2] = spSets[0].atom.getParent().spatialSet;
        }

        if (matchCriteria[3] != null) {
            peakDim = peak.getPeakDim(matchCriteria[3].getDim());
            ppm = peakDim.getChemShift();
            matchCriteria[3].setPPM(ppm);
            spSets[3] = spSets[1].atom.getParent().spatialSet;
        }
        double[] dp = new double[4];
        double sum = 0.0;
        for (int j = 0; j < 4; j++) {
            if (spSets[j] != null) {
                PPMv ppmv = spSets[j].getPPM(ppmSet);

                if (ppmv != null) {
                    double delta = IdPeak.getPPMDelta(ppmv.getValue(), matchCriteria[j]);
                    dp[j] = delta / matchCriteria[j].getTol();
                } else {
                    dp[j] = 1.0e30;
                    System.out.println("no ppm for " + spSets[j].getFullName());
                }
                sum += dp[j] * dp[j];

            }
        }
        ppmError = Math.exp(-1.0 * sum / 2.0);

    }

    public static void updatePPMErrors() {
        MatchCriteria[] matchCriteria = null;
        PeakList lastList = null;
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            Peak peak = entry.getKey();
            PeakList peakList = peak.getPeakList();
            if ((matchCriteria == null) || (lastList != peakList)) {
                try {
                    matchCriteria = getMatchCriteria(peakList);
                } catch (NumberFormatException nfE) {
                    matchCriteria = null;
                }
                lastList = peakList;
            }
            if (matchCriteria != null) {
                ArrayList<Noe> noeList = entry.getValue();
                for (Noe noe : noeList) {
                    noe.updatePPMError(matchCriteria);
                }
            }
        }
    }

    public static void updateGenTypes() {
        Map<String, NoeMatch> map = new HashMap<String, NoeMatch>();
        MatchCriteria[] matchCriteria = null;
        PeakList lastList = null;
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            Peak peak = entry.getKey();
            PeakList peakList = peak.getPeakList();
            if ((matchCriteria == null) || (lastList != peakList)) {
                try {
                    matchCriteria = getMatchCriteria(peakList);
                } catch (NumberFormatException nfE) {
                    matchCriteria = null;
                }
                lastList = peakList;
            }
            if (matchCriteria != null) {
                ArrayList<Noe> noeList = entry.getValue();
                for (Noe noe : noeList) {
//                    noe.updateGenType(map, matchCriteria);
                }
            }
        }
    }

//    public void updateGenType(Map<String, NoeMatch> map, MatchCriteria[] matchCriteria) {
//        if ((peak != null) && (peak.getStatus() >= 0)) {
//            map.clear();
//            PeakDim peakDim = peak.getPeakDim(matchCriteria[0].getDim());
//            double ppm = peakDim.getChemShift();
//            matchCriteria[0].setPPM(ppm);
//            ArrayList res1s = peakDim.getResonances();
//
//            peakDim = peak.getPeakDim(matchCriteria[1].getDim());
//            ppm = peakDim.getChemShift();
//            matchCriteria[1].setPPM(ppm);
//            ArrayList res2s = peakDim.getResonances();
//
//            int nRes1 = res1s.size();
//            int nRes2 = res2s.size();
//            if ((nRes1 > 0) && (nRes2 > 0)) {
//                if ((nRes1 != 1) && (nRes2 != 1) && (nRes1 != nRes2)) {
//                    throw new IllegalArgumentException("Peak \"" + peak.getName() + "\" has unbalanced assignments");
//                }
//                int maxN = nRes1 > nRes2 ? nRes1 : nRes2;
//
//                for (int iRes = 0; iRes < maxN; iRes++) {
//                    AtomResonance r1 = null;
//                    if (iRes < nRes1) {
//                        r1 = (AtomResonance) res1s.get(iRes);
//                    } else {
//                        r1 = (AtomResonance) res1s.get(0);
//                    }
//                    AtomResonance r2 = null;
//                    if (iRes < nRes2) {
//                        r2 = (AtomResonance) res2s.get(iRes);
//                    } else {
//                        r2 = (AtomResonance) res2s.get(0);
//                    }
//                    Atom r1Atom = r1.getAtom();
//                    SpatialSet sp1 = null;
//                    SpatialSet sp2 = null;
//                    if ((r1Atom != null)) {
//                        sp1 = r1Atom.spatialSet;
//                    }
//                    Atom r2Atom = r2.getAtom();
//                    if ((r2Atom != null)) {
//                        sp2 = r2Atom.spatialSet;
//                    }
//                    if ((sp1 != null) && (sp2 != null) && (sp1 != sp2)) {
//                        String name = sp1.getFullName() + "_" + sp2.getFullName();
//                        NoeMatch match = new NoeMatch(sp1, sp2, Constraint.GenTypes.MANUAL, 0.0);
//                        map.put(name, match);
//                    }
//                }
//            }
//
//            if (matchCriteria[2] != null) {
//                peakDim = peak.getPeakDim(matchCriteria[2].getDim());
//                ppm = peakDim.getChemShift();
//                matchCriteria[2].setPPM(ppm);
//            }
//
//            if (matchCriteria[3] != null) {
//                peakDim = peak.getPeakDim(matchCriteria[3].getDim());
//                ppm = peakDim.getChemShift();
//                matchCriteria[3].setPPM(ppm);
//            }
//            Atom[][] atoms = getAtoms(peak);
//            int pDim1 = matchCriteria[0].getDim();
//            int pDim2 = matchCriteria[1].getDim();
//            if ((atoms[pDim1] != null) && (atoms[pDim2] != null)) {
//                int nProtons1 = atoms[pDim1].length;
//                int nProtons2 = atoms[pDim2].length;
//                if ((nProtons1 > 0) && (nProtons2 > 0)) {
//                    if ((nProtons1 == nProtons2) || (nProtons1 == 1) || (nProtons2 == 1)) {
//                        int maxN = nProtons1 > nProtons2 ? nProtons1 : nProtons2;
//                        for (int iProton = 0; iProton < maxN; iProton++) {
//                            SpatialSet sp1 = null;
//                            SpatialSet sp2 = null;
//                            int iProton1 = iProton;
//                            int iProton2 = iProton;
//                            if (iProton >= nProtons1) {
//                                iProton1 = 0;
//                            }
//                            if (iProton >= nProtons2) {
//                                iProton2 = 0;
//                            }
//                            if (atoms[pDim1][iProton1] != null) {
//                                sp1 = atoms[pDim1][iProton1].spatialSet;
//                            }
//                            if ((atoms[pDim2][iProton2] != null)) {
//                                sp2 = atoms[pDim2][iProton2].spatialSet;
//                            }
//                            if ((sp1 != null) && (sp2 != null) && (sp1 != sp2)) {
//                                String name = sp1.getFullName() + "_" + sp2.getFullName();
//                                NoeMatch match = new NoeMatch(sp1, sp2, Constraint.GenTypes.MANUAL, 0.0);
//                                map.put(name, match);
//                            }
//                        }
//
//                    }
//                }
//            }
//
//            int nMan = map.size();
//            Constraint.GenTypes type = Constraint.GenTypes.MANUAL;
//
//            String name = spg1.getFullName() + "_" + spg2.getFullName();
//            if (!map.containsKey(name)) {
//                type = Constraint.GenTypes.AUTOMATIC;
//                if (nMan > 0) {
//                    type = Constraint.GenTypes.AUTOPLUS;
//                }
//            }
//            setGenType(type);
//        }
//    }
//    public void updatePPMError() {
//   //
//
//    }
    public SpatialSetGroup getSPG(int setNum, boolean getSwapped, boolean filterMode) {
        if (setNum == 0) {
            if ((filterMode && filterSwapped) || (!filterMode && swapped && getSwapped)) {
                return spg2;
            } else {
                return spg1;
            }
        } else if ((filterMode && filterSwapped) || (!filterMode && swapped && getSwapped)) {
            return spg1;
        } else {
            return spg2;
        }
    }

    public static int getNStructures() {
        return nStructures;
    }

    public String getViolChars(DistanceStat dStat) {
        for (int i = 0; i < violCharArray.length; i++) {
            if (dStat.getViolStructures() == null) {
                violCharArray[i] = 'x';
            } else if (dStat.getViolStructures().get(i)) {
                violCharArray[i] = (char) ((i % 10) + '0');
            } else {
                violCharArray[i] = '_';
            }
        }
        return new String(violCharArray);
    }

    public DistanceStat getStat() {
        return disStat;
    }

    public static void setActive(NoeSet noeSet) {
        activeSet = noeSet;
    }

    public static NoeSet getActiveSet() {
        return activeSet;
    }

    public static int getSize() {
        return activeSet.getSize();
    }

    public static void resetConstraints() {
        activeSet.clear();
    }

    public static boolean isCalibratable() {
        return calibratable;
    }

    public static void setCalibratable(final boolean state) {
        calibratable = state;
    }

    public static void setSumAverage(boolean state) {
        sumAverage = state;
    }

    public static boolean getSumAverage() {
        return sumAverage;
    }

    public static void setDirty() {
        dirty = true;
    }

    public static boolean isDirty() {
        return dirty;
    }

    public static synchronized ArrayList<Noe> getConstraints(boolean requireActive) {
        return getConstraints("", requireActive);
    }

    public static synchronized ArrayList<Noe> getConstraints(String filter, boolean requireActive) {
        ArrayList listCopy = new ArrayList();
        if (dirty) {
            updateContributions(useDistances, requireActive);
        }
        if (filter.trim().length() == 0) {
            for (Noe noe : activeSet.get()) {
                if (requireActive && !noe.isActive()) {
                    continue;
                }
                listCopy.add(noe);
            }
        } else {
            String filterVals[] = filter.split("\\s");
            for (Noe noe : activeSet.get()) {
                noe.filterSwapped = false;
                if (requireActive && !noe.isActive()) {
                    continue;
                }
                String name1 = noe.spg1.getFullName();
                String name2 = noe.spg2.getFullName();
                if (name1.indexOf(":") == -1) {
                    name1 = "*.*:" + name1;
                }
                if (name2.indexOf(":") == -1) {
                    name2 = "*.*:" + name2;
                }
                boolean addNoe = false;
                if (filterVals.length == 1) {
                    if (Util.stringMatch(name1, filter)) {
                        addNoe = true;
                    } else if (Util.stringMatch(name2, filter)) {
                        noe.filterSwapped = true;
                        addNoe = true;
                    }
                } else if (filterVals.length > 1) {
                    for (int iDir = 0; iDir < 2; iDir++) {
                        int jDir = iDir == 0 ? 1 : 0;
                        addNoe = true;
                        if (!Util.stringMatch(name1, filterVals[iDir])) {
                            addNoe = false;
                        } else if (!Util.stringMatch(name2, filterVals[jDir])) {
                            addNoe = false;
                        } else if (jDir == 0) {
                            noe.filterSwapped = true;
                        }
                        if (addNoe) {
                            break;
                        }
                    }

                }
                if (addNoe) {
                    listCopy.add(noe);
                }
            }
        }
        return listCopy;
    }

    public String getPeakListName() {
        String listName = "";
        if (peak != null) {
            listName = peak.peakList.getName();
        }
        return listName;
    }

    public int getPeakNum() {
        int peakNum = 0;
        if (peak != null) {
            peakNum = peak.getIdNum();
        }
        return peakNum;
    }

    public static void updateContributions(boolean useDistances, boolean requireActive) {
        Noe.useDistances = useDistances;
        updateDistances(requireActive);
        findSymmetrical();
        findNetworks(false);
        calculateContributions(useDistances);
        findNetworks(true);
        calculateContributions(useDistances);
        findNetworks(true);
        calculateContributions(useDistances);
        calibrateExp(null);
        findRedundant();
        dirty = false;

    }

    public static Noe get(int i) {
        if ((i < 0) || (i >= activeSet.getSize())) {
            return null;
        }

        return activeSet.get(i);
    }

    public String getEntity(SpatialSetGroup spg) {
        String value = "";
        if (spg != null) {
            Entity entity = spg.getAnAtom().getEntity();
            if (entity instanceof Residue) {
                value = ((Residue) entity).polymer.getName();
            } else {
                value = ((Compound) entity).getName();
            }
        }
        return value;
    }

    public static void inactivateDiagonal() {
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            boolean hasDiagonal = false;
            ArrayList<Noe> noeList = entry.getValue();
            for (Noe noe : noeList) {
                if (noe.spg1.getFirstSet() == noe.spg2.getFirstSet()) {  // don't include diagonal peaks
                    hasDiagonal = true;
                    break;
                }
            }
            if (hasDiagonal) {
                for (Noe noe : noeList) {
                    noe.inactivate(Flags.DIAGONAL);
                }
            } else {
                for (Noe noe : noeList) {
                    noe.activate(Flags.DIAGONAL);
                }

            }

        }
    }

    public static void convertToMethyls() {
        for (Noe noe : activeSet.get()) {
            noe.spg1.convertToMethyl();
            noe.spg2.convertToMethyl();
        }
    }

    public static void updateNOEListDistances(ArrayList<Noe> noeList) {
        double sum = 0.0;
        Molecule mol = Molecule.getActive();
        int[] structures = mol.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        ArrayList<Double> dList = new ArrayList<>();
        for (Noe noe : noeList) {
            dList.clear();
            double bound = noe.upper;
            int nInBounds = 0;
            nStructures = 1;
            BitSet violStructures = noe.disStat.getViolStructures();
            if (structures.length > 0) {
                nStructures = structures.length;
                if (violStructures == null) {
                    violStructures = new BitSet(nStructures);
                }
                violStructures.clear();
                for (int iStruct : structures) {
                    double distance = Atom.calcWeightedDistance(noe.spg1, noe.spg2, iStruct, 6, false, sumAverage);
                    if (distance < bound) {
                        nInBounds++;
                    } else {
                        violStructures.set(iStruct);
                    }
                    dList.add(distance);
                }
            }
            double fracInBound = (double) nInBounds / nStructures;
            SummaryStatistics stat = new SummaryStatistics();
            dList.stream().forEach(stat::addValue);
            double minDis = stat.getMin();
            double maxDis = stat.getMax();
            double meanDis = stat.getMean();

            double stdDevDis = 0.0;
            if (dList.size() > 1) {
                stdDevDis = stat.getStandardDeviation();
            }
            DistanceStat dStat = new DistanceStat(minDis, maxDis, meanDis, stdDevDis, fracInBound, violStructures);
            noe.disStat = dStat;
        }
    }

    public static void updateDistancesIndividual() {
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            return;
        }
        int[] structures = mol.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        int lastStruct = 0;
        for (int iStruct : structures) {
            lastStruct = iStruct > lastStruct ? iStruct : lastStruct;
        }
        violCharArray = new char[lastStruct + 1];
        if (activeSet.getPeakMapEntries().size() == 0) {
            ArrayList<Noe> noeList = activeSet.get();
            updateNOEListDistances(noeList);
        } else {
            for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
                ArrayList<Noe> noeList = entry.getValue();
                updateNOEListDistances(noeList);
            }
        }
    }

    public static double avgDistance(ArrayList<Double> dArray, double expValue, int nMonomers, boolean sumAverage) {
        double sum = 0.0;
        int n = 0;
        for (Double dis : dArray) {
            sum += Math.pow(dis, expValue);
            n++;

        }
        if (!sumAverage) {
            nMonomers = n;
        }
        double distance = Math.pow((sum / nMonomers), 1.0 / expValue);
        return distance;

    }

    public static void updateNOEListDistancesAvg(ArrayList<Noe> noeList, boolean requireActive) {
        Molecule mol = Molecule.getActive();
        int[] structures = mol.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        nStructures = structures.length;
        SummaryStatistics sumStat = new SummaryStatistics();
        ArrayList<Double> dArray = new ArrayList<Double>();
        int nInBounds = 0;
        BitSet violStructures = new BitSet(nStructures);
        for (int iStruct : structures) {
            dArray.clear();
            double bound = 0.0;
            double max = 10.0;
            for (Noe noe : noeList) {
                bound = noe.upper;
                if (!requireActive || noe.isActive()) {
                    Atom.getDistances(noe.spg1, noe.spg2, iStruct, dArray);
                } else {
                    double distance = noe.disStat.getMax();
                    if (distance > max) {
                        max = distance;
                    }
                }
            }
            if (dArray.size() == 0) {
                dArray.add(max);
            }
            double distance = avgDistance(dArray, -6, 1, sumAverage);

            if (distance < bound) {
                nInBounds++;
            } else {
                violStructures.set(iStruct);
            }
            sumStat.addValue(distance);
        }
        for (Noe noe : noeList) {
            double stdDevDis = 0.0;
            if (sumStat.getN() > 1) {
                stdDevDis = Math.sqrt(sumStat.getVariance());
            }
            DistanceStat dStat = new DistanceStat(sumStat.getMin(), sumStat.getMax(), sumStat.getMean(), stdDevDis, (double) nInBounds / nStructures, violStructures);
            noe.disStatAvg = dStat;
        }
    }

    public static void updateDistances(boolean requireActive) {
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            return;
        }
        updateDistancesIndividual();
        int[] structures = mol.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        int lastStruct = 0;
        for (int iStruct : structures) {
            lastStruct = iStruct > lastStruct ? iStruct : lastStruct;
        }
        violCharArray = new char[lastStruct + 1];
        if (activeSet.getPeakMapEntries().size() == 0) {
            ArrayList<Noe> noeList = activeSet.get();
            for (Noe noe : noeList) {
                noe.disStatAvg = noe.disStat;
            }
        } else {
            for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
                ArrayList<Noe> noeList = entry.getValue();
                updateNOEListDistancesAvg(noeList, requireActive);
            }
        }
    }

    public static void updateDistanceContribs() {
        double expNum = 6.0;
        double disMinLim = 2.2;
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            ArrayList<Noe> noeList = entry.getValue();
            double sum = 0.0;
            for (Noe noe : noeList) {
                double disMin = noe.disStat.getMin();
                if (disMin < disMinLim) {
                    disMin = disMinLim;
                }
                sum += Math.pow(disMin, -expNum);
            }
            for (Noe noe : noeList) {
                double disMin = noe.disStat.getMin();
                if (disMin < disMinLim) {
                    disMin = disMinLim;
                }
                noe.disContrib = Math.pow(disMin, -expNum) / sum;
            }
        }
    }

    static Atom[][] getProtons(Atom[][] atoms) {
        Atom[][] protons = new Atom[2][0];
        if (atoms[0] != null) {
            protons[0] = new Atom[atoms[0].length];
            protons[1] = new Atom[atoms[0].length];
            int k = 0;
            for (int j = 0; j < atoms[0].length; j++) {
                int nProton = 0;
                for (int i = 0; i < atoms.length; i++) {
                    if ((atoms[i] != null) && (j < atoms[i].length)) {
                        if (atoms[i][j].aNum == 1) {
                            if (nProton == 0) {
                                protons[0][k] = atoms[i][j];
                                nProton++;
                            } else if (nProton == 1) {
                                protons[1][k] = atoms[i][j];
                                k++;
                                nProton++;
                            }
                        }
                    }
                }
            }
        }
        return protons;
    }

    public static void limitToAssigned() {
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            double sum = 0.0;
            ArrayList<Noe> noeList = entry.getValue();
            Peak peak = entry.getKey();
            Atom[][] atoms = getAtoms(peak);
            Atom[][] protons = getProtons(atoms);
            boolean isAssigned = false;
            for (int i = 0; i < protons[0].length; i++) {
                if ((protons[0][i] != null) && (protons[1][i] != null)) {
                    System.out.println(protons[0][i].spatialSet.getFullName());
                    System.out.println(protons[1][i].spatialSet.getFullName());
                    isAssigned = true;
                    break;
                }
            }
            System.out.println(protons[0].length + " " + isAssigned);
            for (Noe noe : noeList) {
                String spg1Name = noe.spg1.getFullName();
                String spg2Name = noe.spg2.getFullName();
                boolean consistent = false;
                if (isAssigned) {
                    for (int i = 0; i < protons[0].length; i++) {
                        if ((protons[0][i] != null) && (protons[1][i] != null)) {
                            if (protons[0][i].spatialSet.getFullName().equals(spg1Name) && protons[1][i].spatialSet.getFullName().equals(spg2Name)) {
                                consistent = true;
                                break;
                            } else if (protons[1][i].spatialSet.getFullName().equals(spg1Name) && protons[0][i].spatialSet.getFullName().equals(spg2Name)) {
                                consistent = true;
                                break;
                            }
                        }
                    }
                }
                System.out.println(spg1Name + " " + spg2Name + " " + consistent);
                if (isAssigned && !consistent) {
                    noe.inactivate(Flags.LABEL);
                } else {
                    noe.activate(Flags.LABEL);
                }
            }
        }
    }

    public static void clearLimitToAssigned() {
        for (Noe noe : activeSet.get()) {
            noe.activate(Flags.LABEL);
        }
    }

    public static void limitToMaxAmbig(int maxVal) {
        for (Noe noe : activeSet.get()) {
            if (noe.nPossible > maxVal) {
                noe.inactivate(Flags.MAXAMBIG);
            } else {
                noe.activate(Flags.MAXAMBIG);
            }
        }
    }

    public static void limitToMinContrib(double minContrib) {
        for (Noe noe : activeSet.get()) {
            if (noe.contribution < minContrib) {
                noe.inactivate(Flags.MINCONTRIB);
            } else {
                noe.activate(Flags.MINCONTRIB);
            }
        }
    }

    public static void limitToMinPPMError(double minPPM) {
        for (Noe noe : activeSet.get()) {
            if (noe.ppmError < minPPM) {
                noe.inactivate(Flags.MINPPM);
            } else {
                noe.activate(Flags.MINPPM);
            }
        }
    }

    public static void limitToMaxViol(double maxViol) {
        for (Noe noe : activeSet.get()) {
            double disMin = noe.disStat.getMin();
            if ((disMin - noe.upper) > maxViol) {
                noe.inactivate(Flags.MAXVIOL);
            } else {
                noe.activate(Flags.MAXVIOL);
            }
        }
    }

    public static HashMap<PeakList, ArrayList<Double>> calcMedian(String mMode, PeakList whichList) {
        Map<PeakList, ArrayList<Double>> valuesMap = new HashMap<PeakList, ArrayList<Double>>();
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            PeakList peakList = entry.getKey().peakList;
            if ((whichList != null) && (whichList != peakList)) {
                continue;
            }
            ArrayList<Noe> noeList = entry.getValue();
            double maxContrib = 0.0;
            double scaledIntensity = 1.0;
            boolean foundActive = false;
            for (Noe noe : noeList) {
                if (!noe.isActive()) {
                    continue;
                }
                double scale = 1.0;
                double intensity = 0.0;
                for (int i = 0; i < 2; i++) {
                    SpatialSetGroup sp = (i == 0) ? noe.spg1 : noe.spg2;
                    Atom atom = sp.getAnAtom();
                    Atom parent = atom.parent;
                    String pName = "";
                    if (parent != null) {
                        pName = parent.getName();
                    }
                    if (atom.isMethyl()) {
                        scale *= 3.0;
                    } else if (pName.equals("N") || pName.equals("CA") || pName.equals("CB")) {
                        scale *= 1.0;
                    } else {
                        scale *= 1.5;
                    }
                }
                scale = Math.sqrt(scale);
                noe.atomScale = scale;
                if (noe.contribution > maxContrib) {
                    if (mMode.startsWith("int")) {
                        intensity = Math.abs(noe.intensity);
                    } else {
                        intensity = Math.abs(noe.volume);
                    }
                    maxContrib = noe.contribution;
                    scaledIntensity = intensity / noe.scale / scale;
                }
                foundActive = true;
            }
            ArrayList<Double> dList = valuesMap.get(peakList);
            if (dList == null) {
                dList = new ArrayList<>();
                valuesMap.put(peakList, dList);
            }
            if (foundActive) {
                dList.add(scaledIntensity);
            }
        }
        HashMap<PeakList, ArrayList<Double>> medianMap = new HashMap<PeakList, ArrayList<Double>>();
        for (Entry<PeakList, ArrayList<Double>> entry : valuesMap.entrySet()) {
            PeakList peakList = entry.getKey();
            ArrayList<Double> dList = entry.getValue();
            ArrayList<Double> valueList = new ArrayList<Double>();
            DescriptiveStatistics stat = new DescriptiveStatistics();
            dList.forEach(stat::addValue);
            double median = stat.getPercentile(50.0);

            int m = dList.size();
            valueList.add(dList.get(0));
            valueList.add(dList.get(m / 4));
            valueList.add(median);
            valueList.add(dList.get(3 * m / 4));
            valueList.add(dList.get(m - 1));
            medianMap.put(peakList, valueList);
        }
        return medianMap;
    }

    public static void clearScaleMap() {
        activeSet.clearScaleMap();
    }

    public static void setScale(PeakList peakList, NoeCalibration noeCal) {
        activeSet.setScale(peakList, noeCal);
    }

    public static NoeCalibration defaultCal(PeakList peakList) {
        String mMode = "intensity";
        HashMap<PeakList, ArrayList<Double>> medianMap = calcMedian(mMode, peakList);
        ArrayList<Double> valueList = medianMap.get(peakList);
        double referenceValue = valueList.get(2);
        double referenceDist = 2.7;
        double expValue = 6.0;
        double lower = 1.8;
        double minBound = 2.0;
        double maxBound = 6.0;
        double fError = 0.125;
        NoeCalibration noeCal = new NoeCalibrationExp(mMode, lower, referenceValue, referenceDist, expValue, minBound, maxBound, fError, true);
        setScale(peakList, noeCal);
        return noeCal;
    }

    public static void calibrateExp(PeakList whichList) {
        if (activeSet.getSize() == 0) {
            return;
        }
        if (!calibratable) {
            return;
        }
        double maxBound = 5.5;
        double minBound = 2.0;
        double floor = 1.0e-16;
        double fError = 0.125;
        double lower = 1.8;
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            PeakList peakList = entry.getKey().peakList;
            if ((whichList != null) && (whichList != peakList)) {
                continue;
            }
            ArrayList<Noe> noeList = entry.getValue();
            NoeCalibration noeCal = activeSet.getCalibration(peakList);
            if (noeCal == null) {
                noeCal = defaultCal(peakList);
            }
            for (Noe noe : noeList) {
                // fixme  what about negative NOE peaks?
                if (!noe.isActive()) {

                    continue;
                }
                noeCal.calibrate(noe);
            }
        }
    }

    public static void updateNPossible(PeakList whichList) {
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            PeakList peakList = entry.getKey().peakList;
            if ((whichList != null) && (whichList != peakList)) {
                continue;
            }
            ArrayList<Noe> noeList = entry.getValue();
            NoeCalibration noeCal = activeSet.getCalibration(peakList);
            if (noeCal == null) {
                noeCal = defaultCal(peakList);
            }
            for (Noe noe : noeList) {
                noe.nPossible = noeList.size();
            }
        }
    }

    public static void findSymmetrical() {
        int nNoe = activeSet.getSize();
        Map<String, Noe> symMap = new HashMap<String, Noe>();
        for (int i = 0; i < nNoe; i++) {
            Noe iNoe = activeSet.get(i);
            iNoe.symmetrical = false;
            Peak iPeak = iNoe.peak;
            if (iPeak != null) {
                String listName = iPeak.peakList.getName();
                String spg1Name = iNoe.spg1.getFullName();
                String spg2Name = iNoe.spg2.getFullName();
                symMap.put(listName + "." + spg1Name + "." + spg2Name, iNoe);
            }
        }
        for (int i = 0; i < nNoe; i++) {
            Noe iNoe = activeSet.get(i);
            Peak iPeak = iNoe.peak;
            if ((iPeak != null) && (!iNoe.symmetrical)) {
                String listName = iPeak.peakList.getName();
                String spg1Name = iNoe.spg1.getFullName();
                String spg2Name = iNoe.spg2.getFullName();
                Noe jNoe = symMap.get(listName + "." + spg2Name + "." + spg1Name);
                if (jNoe != null) {
                    iNoe.symmetrical = true;
                    jNoe.symmetrical = true;
                }
            }
        }
    }

    public static void calculateContributions(boolean useDistances) {
        for (Entry<Peak, ArrayList<Noe>> entry : activeSet.getPeakMapEntries()) {
            double sum = 0.0;
            ArrayList<Noe> noeList = entry.getValue();
            for (Noe noe : noeList) {
                if (!(noe.activeFlags.contains(Flags.DIAGONAL))) {
                    double value = 1.0;
                    if (noe.symmetrical) {
                        value *= SYM_BONUS;
                    }
                    value *= noe.netWorkValue;
                    if (value > MAX_BONUS) {
                        value = MAX_BONUS;
                    }
                    value *= noe.ppmError;
                    if (useDistances) {
                        value *= noe.disContrib;
                    }
                    noe.contribution = value;

                    sum += value;
                }
            }

            for (Noe noe : noeList) {
                if (sum == 0.0) {
                    noe.contribution = 0.0;
                } else {
                    noe.contribution /= sum;
                }
            }
        }
    }

    public static void findNetworks(boolean useContrib) {
        int nNoe = activeSet.getSize();
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            System.out.println("null mol");
            return;
        }
        Map<String, Map<String, Noe>> resMap1 = new TreeMap<String, Map<String, Noe>>();
//
//        for (CoordSet cSet : mol.coordSets.values()) {
//            for (Entity entity : cSet.entities.values()) {
//                if (entity instanceof Polymer) {
//                    Vector residues = ((Polymer) entity).getResidues();
//                    for (int iRes = 0; iRes < residues.size(); iRes++) {
//                        Residue residue = (Residue) residues.elementAt(iRes);
//                        String cName = cSet.getName() + "." + entity.getName() + ":" + residue.getNumber();
//                    }
//                }
//            }
//        }
//        ArrayList<String> aList = mol.getCompounds();
//        for (String cmpdName: aList) {
//            
//        }
        StringBuilder cName1 = new StringBuilder();
        StringBuilder cName2 = new StringBuilder();
        long start = System.currentTimeMillis();
        for (int i = 0; i < nNoe; i++) {
            cName1.setLength(0);
            cName2.setLength(0);
            Noe iNoe = activeSet.get(i);
            iNoe.spg1.getName();
            if (iNoe.spg1.getFirstSet() == iNoe.spg2.getFirstSet()) {  // don't include diagonal peaks
                continue;
            }
            Entity e1 = iNoe.spg1.getAnAtom().getEntity();
            Entity e2 = iNoe.spg2.getAnAtom().getEntity();
            Residue r1 = null;
            Residue r2 = null;

            if (e1 instanceof Residue) {
                r1 = (Residue) e1;
            } else {
                System.out.println(e1.getName() + " not polymer");
            }
            if (e2 instanceof Residue) {
                r2 = (Residue) e2;
            } else {
                System.out.println(e2.getName() + " not polymer");
            }

            if ((r1 != null) && (r2 != null)) {
                String eName1, eName2;
                if (e1 instanceof Residue) {
                    eName1 = ((Residue) e1).polymer.name;
                } else {
                    eName1 = e1.getName();
                }
                if (e2 instanceof Residue) {
                    eName2 = ((Residue) e2).polymer.name;
                } else {
                    eName2 = e2.getName();
                }
                cName1.append(iNoe.spg1.getName());
                cName1.append('.');
                cName1.append(eName1);
                cName1.append(':');
                cName1.append(r1.getNumber());
                cName2.append(iNoe.spg2.getName());
                cName2.append('.');
                cName2.append(eName2);
                cName2.append(':');
                cName2.append(r2.getNumber());

                String cName;
                String aName;
                String a1 = iNoe.spg1.getAnAtom().getName();
                String a2 = iNoe.spg2.getAnAtom().getName();
                if (cName1.toString().compareTo(cName2.toString()) < 0) {
                    cName1.append('_');
                    cName1.append(cName2);
                    cName = cName1.toString();
                    aName = a1 + ">" + a2;
                } else {
                    cName2.append('_');
                    cName2.append(cName1);
                    cName = cName2.toString();
                    aName = a2 + "<" + a1;
                }
                Map<String, Noe> resMap2 = resMap1.get(cName);
                if (resMap2 == null) {
                    resMap2 = new HashMap<String, Noe>();
                    resMap1.put(cName, resMap2);
                }
                Noe testNoe = resMap2.get(aName);
                if ((testNoe == null) || (testNoe.contribution < iNoe.contribution)) {
                    resMap2.put(aName, iNoe);
                }
                iNoe.resMap = resMap2;
            }
        }
        long mid = System.currentTimeMillis();

        Map<Residue, Integer> countMap = new HashMap<Residue, Integer>();
        for (Noe iNoe : activeSet.get()) {
            Entity e1 = iNoe.spg1.getAnAtom().getEntity();
            Entity e2 = iNoe.spg2.getAnAtom().getEntity();
            Residue r1 = null;
            Residue r2 = null;
            if (e1 instanceof Residue) {
                r1 = (Residue) e1;
            }
            if (e2 instanceof Residue) {
                r2 = (Residue) e2;
            }
            if ((r1 != null) && (r2 != null)) {
                if (r1 == r2) {
                    iNoe.netWorkValue = 1.0;
                } else {
                    Integer count1 = countMap.get(r1);
                    if (count1 == null) {
                        int nAtoms1 = r1.getAtoms("H*").size();
                        count1 = Integer.valueOf(nAtoms1);
                        countMap.put(r1, count1);
                    }
                    Integer count2 = countMap.get(r2);
                    if (count2 == null) {
                        int nAtoms2 = r2.getAtoms("H*").size();
                        count2 = Integer.valueOf(nAtoms2);
                        countMap.put(r2, count2);
                    }
                    Map<String, Noe> resMap2 = iNoe.resMap;
                    double scale = Math.sqrt(count1 * count2);
                    double sum = 0.01;
                    if (resMap2 != null) {
                        for (Noe jNoe : resMap2.values()) {
                            if (useContrib) {
                                sum += jNoe.contribution;
                            } else {
                                sum += 1.0;
                            }
                        }
                    }
                    iNoe.netWorkValue = sum / scale;
                }
            }
        }
        long done = System.currentTimeMillis();
        System.out.println((mid - start) + " " + (done - mid));
    }

    public static void findRedundant() {
        int nNoe = activeSet.getSize();
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            System.out.println("null mol");
            return;
        }
        Map<String, ArrayList<Noe>> dupMap = new TreeMap<String, ArrayList<Noe>>();
        StringBuilder cName1 = new StringBuilder();
        StringBuilder cName2 = new StringBuilder();

        for (int i = 0; i < nNoe; i++) {
            cName1.setLength(0);
            cName2.setLength(0);
            Noe iNoe = activeSet.get(i);
            iNoe.spg1.getName();
            if (iNoe.spg1.getFirstSet() == iNoe.spg2.getFirstSet()) {  // don't include diagonal peaks
                continue;
            }
            Entity e1 = iNoe.spg1.getAnAtom().getEntity();
            Entity e2 = iNoe.spg2.getAnAtom().getEntity();
            Residue r1 = null;
            Residue r2 = null;

            if (e1 instanceof Residue) {
                r1 = (Residue) e1;
            } else {
                System.out.println(e1.getName() + " not polymer");
            }
            if (e2 instanceof Residue) {
                r2 = (Residue) e2;
            } else {
                System.out.println(e2.getName() + " not polymer");
            }

            if ((r1 != null) && (r2 != null)) {
                String eName1, eName2;
                if (e1 instanceof Residue) {
                    eName1 = ((Residue) e1).polymer.name;
                } else {
                    eName1 = e1.getName();
                }
                if (e2 instanceof Residue) {
                    eName2 = ((Residue) e2).polymer.name;
                } else {
                    eName2 = e2.getName();
                }
                String a1 = iNoe.spg1.getAnAtom().getName();
                String a2 = iNoe.spg2.getAnAtom().getName();

                cName1.append(iNoe.spg1.getName());
                cName1.append('.');
                cName1.append(eName1);
                cName1.append(':');
                cName1.append(r1.getNumber());
                cName1.append('.');
                cName1.append(a1);

                cName2.append(iNoe.spg2.getName());
                cName2.append('.');
                cName2.append(eName2);
                cName2.append(':');
                cName2.append(r2.getNumber());
                cName2.append('.');
                cName2.append(a2);

                String cName;
                if (cName1.toString().compareTo(cName2.toString()) < 0) {
                    cName1.append('_');
                    cName1.append(cName2);
                    cName = cName1.toString();
                } else {
                    cName2.append('_');
                    cName2.append(cName1);
                    cName = cName2.toString();
                }

                ArrayList<Noe> noeList = dupMap.get(cName);
                if (noeList == null) {
                    noeList = new ArrayList<Noe>();
                    dupMap.put(cName, noeList);
                }
                noeList.add(iNoe);

            }
        }
        for (Entry<String, ArrayList<Noe>> eSet : dupMap.entrySet()) {
            String cName = eSet.getKey();
            List<Noe> noeList = eSet.getValue();
            Noe weakestNoe = null;
            double maxBound = 0.0;
            for (Noe noe : noeList) {
                if (noe.upper >= maxBound) {
                    maxBound = noe.upper;
                    weakestNoe = noe;
                }
            }
            for (Noe noe : noeList) {
                noe.activate(Flags.REDUNDANT);
            }
            if (weakestNoe != null) {
                for (Noe noe : noeList) {
                    if (noe.peak == null) {
                        continue;
                    }
                    PeakList peakList = noe.peak.getPeakList();
                    NoeCalibration noeCal = activeSet.getCalibration(peakList);
                    if (noeCal == null) {
                        noeCal = defaultCal(peakList);
                    }
                    if (noeCal.removeRedundant()) {
                        if (noe != weakestNoe) {
                            noe.inactivate(Flags.REDUNDANT);
                        }
                    }
                }
            }

        }
    }

    public boolean isActive() {
        boolean activeFlag = false;
        if (activeFlags.size() == 0) {
            activeFlag = true;
        } else if (activeFlags.size() == 1) {
            if (getActivityFlags().equals("f")) {
                activeFlag = true;
            }
        }
        return activeFlag;
    }

    public boolean isUserActive() {
        return (active > 0);
    }

    public int getActive() {
        return active;
    }

    public void setActive(int newState) {
        this.active = (short) newState;
    }

    public String getActivityFlags() {
        StringBuffer result = new StringBuffer();
        for (Flags f : activeFlags) {
            result.append(f.getCharDesc());
        }
        return result.toString();
    }

    public void inactivate(Flags enumVal) {
        activeFlags.add(enumVal);
    }

    public void activate(Flags enumVal) {
        activeFlags.remove(enumVal);
    }

    /**
     * @return the distance
     */
    public double getDistance() {

        return distanceType.getDistance(this);
    }

    public double getValue() {
        return getDistance();
    }

    public String toSTARString() {
        if (peak != NoeSet.lastPeakWritten) {
            NoeSet.ID++;
            NoeSet.lastPeakWritten = peak;
            NoeSet.memberID = 1;
        } else {
            NoeSet.memberID++;
        }
        String logic = ".";
        if (nPossible > 1) {
            logic = "OR";
        }

        StringBuilder result = new StringBuilder();
        char sep = ' ';
        char stringQuote = '"';

        //        Gen_dist_constraint.ID
        result.append(NoeSet.ID);
        result.append(sep);
        //_Gen_dist_constraint.Member_ID
        result.append(NoeSet.memberID);
        result.append(sep);
        //_Gen_dist_constraint.Member_logic_code
        result.append(logic);
        result.append(sep);
        spg1.addToSTARString(result);
        result.append(sep);
        //_Gen_dist_constraint.Resonance_ID_1
        result.append('.');
        result.append(sep);
        spg2.addToSTARString(result);
        result.append(sep);
        //_Gen_dist_constraint.Resonance_ID_2
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Intensity_val
        result.append(intensity);
        result.append(sep);
        //_Gen_dist_constraint.Intensity_lower_val_err
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Intensity_upper_val_err
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Distance_val
        if (target < lower) {
            target = (lower + upper) / 2.0;
        }
        result.append(target);
        result.append(sep);
        //_Gen_dist_constraint.Distance_lower_bound_val
        result.append(lower);
        result.append(sep);
        //_Gen_dist_constraint.Distance_upper_bound_val
        result.append(upper);
        result.append(sep);
        //_Gen_dist_constraint.Contribution_fractional_val
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Spectral_peak_ID
        if (peak == null) {
            result.append('.');
            result.append(sep);
            result.append('.');
        } else {
            result.append(peak.getIdNum());
            result.append(sep);
            //_Gen_dist_constraint.Spectral_peak_list_ID
            result.append(peak.getPeakList().getId());
        }
        result.append(sep);

        result.append("."); // fixme do we need to save ssid here

        result.append(sep);
        result.append("1");
        return result.toString();
    }

    /**
     * @return the genType
     */
    public GenTypes getGenType() {
        return genType;
    }

    /**
     * @param genType the genType to set
     */
    public void setGenType(GenTypes genType) {
        this.genType = genType;
    }

    public static Atom[][] getAtoms(Peak peak) {
        int nElems = -1;
        Atom[][] atoms = new Atom[peak.peakList.nDim][];

        for (int i = 0; i < peak.peakList.nDim; i++) {
            atoms[i] = null;
            String label = peak.peakDims[i].getLabel();
            String[] elems = label.split(" ");

            if (elems.length == 0) {
                continue;
            }

            nElems = elems.length;
            atoms[i] = new Atom[nElems];

            for (int j = 0; j < elems.length; j++) {
                atoms[i][j] = Molecule.getAtomByName(elems[j]);
            }
        }

        return atoms;
    }

    static MatchCriteria[] getMatchCriteria(PeakList peakList) throws NumberFormatException {
        int nDim = peakList.nDim;
        MatchCriteria[] matchCriteria = new MatchCriteria[4];
        String[][] atomPats = new String[nDim][];
        String[][] resPats = new String[nDim][];
        String[] relation = new String[nDim];
        double[] tol = new double[nDim];
        double[] folding = new double[nDim];
        int[] foldCount = new int[nDim];
        int protonDim1 = -1;
        int protonDim2 = -1;
        for (int iDim = 0; iDim < peakList.nDim; iDim++) {
            SpectralDim spectralDim = peakList.getSpectralDim(iDim);
            if (spectralDim.getSf() > 1.0e-6) {
                folding[iDim] = spectralDim.getSw() / spectralDim.getSf();
            }
            foldCount[iDim] = spectralDim.getFoldCount();
            if (spectralDim.getFoldMode() == 'n') {
                foldCount[iDim] = 0;
            } else if (spectralDim.getFoldMode() == 'f') {
                foldCount[iDim] = -foldCount[iDim];
            }

            tol[iDim] = spectralDim.getIdTol();
            relation[iDim] = spectralDim.getRelation();
            if (relation[iDim].equals(".")) {
                relation[iDim] = "";
            }
            String pattern = spectralDim.getPattern();
            int dot = pattern.indexOf('.');
            if (dot < 0) {
                resPats[iDim] = new String[1];
                atomPats[iDim] = new String[1];
                resPats[iDim][0] = "";
                atomPats[iDim][0] = "*";
            } else {
                resPats[iDim] = pattern.substring(0, dot).split(",");
                atomPats[iDim] = pattern.substring(dot + 1).toLowerCase().split(",");
            }
            for (int j = 0; j < atomPats[iDim].length; j++) {
                if (Util.stringMatch(atomPats[iDim][j], "h*")) {
                    if (protonDim1 == -1) {
                        protonDim1 = iDim;
                    } else if (protonDim2 == -1) {
                        protonDim2 = iDim;
                    } else {
                        throw new IllegalArgumentException("Too many proton dimensions, check peakList patterns");
                    }
                }
            }
        }
        if ((protonDim1 == -1) || (protonDim2 == -1)) {
            throw new IllegalArgumentException("Too few proton dimensions, check peakList patterns");
        }
        matchCriteria[0] = new MatchCriteria(protonDim1, 3.0, tol[protonDim1], atomPats[protonDim1], resPats[protonDim1], relation[protonDim1], folding[protonDim1], foldCount[protonDim1]);
        matchCriteria[1] = new MatchCriteria(protonDim2, 3.0, tol[protonDim2], atomPats[protonDim2], resPats[protonDim2], relation[protonDim2], folding[protonDim2], foldCount[protonDim2]);
        int[] pDim = {-1, -1};
        for (int i = 0; i < 2; i++) {
            MatchCriteria mC = matchCriteria[i];
            if (mC.getRelation().length() > 0) {
                if ((mC.getRelation().charAt(0) != 'D') && (mC.getRelation().charAt(0) != 'd')) {
                    throw new IllegalArgumentException("Invalid relation " + mC.getRelation());
                }
                pDim[i] = Integer.parseInt(mC.getRelation().substring(1));
                pDim[i]--;
                matchCriteria[i + 2] = new MatchCriteria(pDim[i], 3.0, tol[pDim[i]], atomPats[pDim[i]], resPats[pDim[i]], relation[pDim[i]], folding[pDim[i]], foldCount[pDim[i]]);
            }
        }
        return matchCriteria;
    }

    // mode == 0  only extract contraints for peaks with one assignment
    // mode == 1  extract constraints for peaks with one or more (ambiguous) assignments
    public static void extractNoePeaks(PeakList peakList,
            int mode, boolean onlyFrozen) {
        Peak peak;
        double scale = 1.0;
        int[] atomIndex = new int[2];
        int nPeaks;
        boolean includeDiag = true;
        nPeaks = peakList.size();

        for (int i = 0; i < nPeaks; i++) {
            peak = (Peak) peakList.getPeak(i);

            if ((peak != null) && (peak.getStatus() >= 0)) {
                Atom[][] atoms = getAtoms(peak);
                boolean ok = true;
                int nAtoms = 0;

                for (int iDim = 0; iDim < atoms.length; iDim++) {
                    if (atoms[iDim] == null) {
                        ok = false;

                        break;
                    }

                    if (iDim == 0) {
                        nAtoms = atoms[iDim].length;
                    } else if (atoms[iDim].length != nAtoms) {
                        ok = false;

                        break;
                    }
                }

                if (!ok) {
                    continue;
                }

                int nAssign = 0;

                ArrayList noeList = Noe.getPeakList(peak);
                for (int iPass = 0; iPass < 2; iPass++) {
                    for (int iPos = 0; iPos < atoms[0].length; iPos++) {
                        int nProtons = 0;

                        for (int iDim = 0; iDim < peakList.nDim; iDim++) {
                            if (atoms[iDim][iPos] == null) {
                                continue;
                            }

                            if (atoms[iDim][iPos].aNum == 1) {
                                if (nProtons > 1) {
                                    throw new IllegalArgumentException("too many protons for peak " + peak.getIdNum());
                                }
                                atomIndex[nProtons] = iDim;
                                nProtons++;
                            }
                        }

                        if (iPass == 0) {
                            if (nProtons == 2) {
                                if (onlyFrozen) {
                                    if (!peak.getFlag(atomIndex[0] + 8) || !peak.getFlag(atomIndex[1] + 8)) {
                                        break;
                                    }
                                }
                                nAssign++;
                            }
                        } else if (includeDiag || !atoms[atomIndex[0]][iPos].getShortName().equals(atoms[atomIndex[1]][iPos].getShortName())) {
                            if (nAssign == 1) {
                                if (nProtons == 2) {
                                    Noe noe = new Noe(peak,
                                            atoms[atomIndex[0]][iPos].spatialSet,
                                            atoms[atomIndex[1]][iPos].spatialSet, scale);
                                    noe.intensity = peak.getIntensity();
                                    noe.volume = peak.getVolume1();
                                    noe.nPossible = nAssign;
                                    noeList.add(noe);
                                }
                            } else if (mode == 1) {
                                if (nProtons < 3) {
                                    Noe noe = new Noe(peak,
                                            atoms[atomIndex[0]][iPos].spatialSet,
                                            atoms[atomIndex[1]][iPos].spatialSet, scale);
                                    noe.intensity = peak.getIntensity();
                                    noe.volume = peak.getVolume1();
                                    noe.nPossible = nAssign;
                                    noeList.add(noe);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // mode == 0  only extract contraints for peaks with one assignment
    // mode == 1  extract constraints for peaks with one or more (ambiguous) assignments

    public static void extractNoePeaksSlow(PeakList peakList,
            int mode) throws InvalidMoleculeException {
        Peak peak;
        double scale = 1.0;
        int nPeaks;
        nPeaks = peakList.size();
        MatchCriteria[] matchCriteria = new MatchCriteria[peakList.nDim];
        IdPeak idPeak = new IdPeak();
        idPeak.setMolecule(Molecule.getActive());

        if (idPeak.getMolecule() == null) {
            throw new InvalidMoleculeException("Can't find default molecule");
        }

        idPeak.clearAtomList();
        idPeak.getAtomsWithPPMs();
        int nDim = peakList.nDim;
        String[][] atomPats = new String[nDim][];
        String[][] resPats = new String[nDim][];
        String[] relation = new String[nDim];
        double[] tol = new double[nDim];
        double[] folding = new double[nDim];
        int protonDim1 = -1;
        int protonDim2 = -1;
        for (int iDim = 0; iDim < peakList.nDim; iDim++) {
            SpectralDim spectralDim = peakList.getSpectralDim(iDim);
            if (spectralDim.getSf() > 1.0e-6) {
                folding[iDim] = spectralDim.getSw() / spectralDim.getSf();
            }
            tol[iDim] = spectralDim.getIdTol();
            relation[iDim] = spectralDim.getRelation();
            if (relation[iDim].equals(".")) {
                relation[iDim] = "";
            }
            String pattern = spectralDim.getPattern();
            int dot = pattern.indexOf('.');

            if (dot < 0) {
                resPats[iDim] = new String[1];
                atomPats[iDim] = new String[1];
                resPats[iDim][0] = "";
                atomPats[iDim][0] = "*";
            } else {
                resPats[iDim] = pattern.substring(0, dot).split(",");
                atomPats[iDim] = pattern.substring(dot + 1).toLowerCase().split(",");
            }
            for (int j = 0; j < atomPats[iDim].length; j++) {
                if (Util.stringMatch(atomPats[iDim][j], "h*")) {
                    if (protonDim1 == -1) {
                        protonDim1 = iDim;
                    } else if (protonDim2 == -1) {
                        protonDim2 = iDim;
                    } else {
                        throw new IllegalArgumentException("Too many proton dimensions, check peakList patterns");
                    }
                }
            }
        }
        if ((protonDim1 == -1) || (protonDim2 == -1)) {
            throw new IllegalArgumentException("Too few proton dimensions, check peakList patterns");

        }

        for (int i = 0; i < nPeaks; i++) {
            peak = (Peak) peakList.getPeak(i);
            if ((peak != null) && (peak.getStatus() >= 0)) {
                for (int iDim = 0; iDim < peakList.nDim; iDim++) {
                    PeakDim peakDim = peak.getPeakDim(iDim);
                    SpectralDim spectralDim = peakList.getSpectralDim(iDim);
                    double ppm = peakDim.getChemShift();
                    matchCriteria[iDim] = new MatchCriteria(iDim, ppm, tol[iDim], atomPats[iDim], resPats[iDim], relation[iDim], folding[iDim], 0);
                }
                Vector[] matchList = idPeak.scan(matchCriteria);
                ArrayList<IdResult> idResults = idPeak.getIdResults(matchList, matchCriteria);
                // fixme filter duplicates ( stereo specific )
                int nPossible = idResults.size();
                ArrayList noeList = Noe.getPeakList(peak);
                for (IdResult idResult : idResults) {
                    Noe noe = new Noe(peak,
                            idResult.getSpatialSet(protonDim1),
                            idResult.getSpatialSet(protonDim2), scale);
                    noe.intensity = peak.getIntensity();
                    noe.volume = peak.getVolume1();
                    noe.ppmError = idResult.getPPMError(1.0);
                    noe.nPossible = nPossible;
                    noeList.add(noe);
                }

            }
        }
    }
    // mode == 0  only extract contraints for peaks with one assignment
    // mode == 1  extract constraints for peaks with one or more (ambiguous) assignments

    static class NoeMatch {

        final SpatialSet sp1;
        final SpatialSet sp2;
        final Constraint.GenTypes type;
        final double error;

        NoeMatch(SpatialSet sp1, SpatialSet sp2, Constraint.GenTypes type, double error) {
            this.sp1 = sp1;
            this.sp2 = sp2;
            this.type = type;
            this.error = error;
        }
    }

    public static List<String> extractNoePeaks2(final PeakList peakList, final int maxAmbig,
            final boolean getInfo, final boolean strict, final int ppmSet) throws InvalidMoleculeException {
        Peak peak;
        double scale = 1.0;
        int nPeaks;
        nPeaks = peakList.size();
        IdPeak idPeak = new IdPeak();
        idPeak.setPPMSet(ppmSet);
        idPeak.setMolecule(Molecule.getActive());

        if (idPeak.getMolecule() == null) {
            throw new InvalidMoleculeException("Can't find default molecule");
        }
        MatchCriteria[] matchCriteria = getMatchCriteria(peakList);
        idPeak.getProtons(0, matchCriteria[0].getAtomPats());
        idPeak.getProtons(1, matchCriteria[1].getAtomPats());
        int nTotal = 0;
        int nMaxAmbig = 0;
        int nAssigned = 0;
        Map<String, NoeMatch> map = new HashMap<String, NoeMatch>();
        for (int i = 0; i < nPeaks; i++) {
            peak = (Peak) peakList.getPeak(i);
            if ((peak != null) && (peak.getStatus() >= 0)) {
                map.clear();
                PeakDim peakDim = peak.getPeakDim(matchCriteria[0].getDim());
                double ppm = peakDim.getChemShift();
                matchCriteria[0].setPPM(ppm);
//                ArrayList res1s = peakDim.getResonances();
                ArrayList res1s = new ArrayList();

                peakDim = peak.getPeakDim(matchCriteria[1].getDim());
                ppm = peakDim.getChemShift();
                matchCriteria[1].setPPM(ppm);
//                ArrayList res2s = peakDim.getResonances();
                ArrayList res2s = new ArrayList();

                ArrayList noeList = Noe.getPeakList(peak);

                int nRes1 = res1s.size();
                int nRes2 = res2s.size();
                if ((nRes1 > 0) && (nRes2 > 0)) {
                    if ((nRes1 != 1) && (nRes2 != 1) && (nRes1 != nRes2)) {
                        throw new IllegalArgumentException("Peak \"" + peak.getName() + "\" has unbalanced assignments");
                    }
                    int maxN = nRes1 > nRes2 ? nRes1 : nRes2;

                    for (int iRes = 0; iRes < maxN; iRes++) {
                        AtomResonance r1 = null;
                        if (iRes < nRes1) {
                            r1 = (AtomResonance) res1s.get(iRes);
                        } else {
                            r1 = (AtomResonance) res1s.get(0);
                        }
                        AtomResonance r2 = null;
                        if (iRes < nRes2) {
                            r2 = (AtomResonance) res2s.get(iRes);
                        } else {
                            r2 = (AtomResonance) res2s.get(0);
                        }
                        Atom r1Atom = r1.getAtom();
                        SpatialSet sp1 = null;
                        SpatialSet sp2 = null;
                        if ((r1Atom != null)) {
                            sp1 = r1Atom.spatialSet;
                        }
                        Atom r2Atom = r2.getAtom();
                        if ((r2Atom != null)) {
                            sp2 = r2Atom.spatialSet;
                        }
                        if ((sp1 != null) && (sp2 != null)) {
                            String name = sp1.getFullName() + "_" + sp2.getFullName();
                            NoeMatch match = new NoeMatch(sp1, sp2, Constraint.GenTypes.MANUAL, 0.0);
                            map.put(name, match);
                        }
                    }
                }

                if (matchCriteria[2] != null) {
                    peakDim = peak.getPeakDim(matchCriteria[2].getDim());
                    ppm = peakDim.getChemShift();
                    matchCriteria[2].setPPM(ppm);
                }

                if (matchCriteria[3] != null) {
                    peakDim = peak.getPeakDim(matchCriteria[3].getDim());
                    ppm = peakDim.getChemShift();
                    matchCriteria[3].setPPM(ppm);
                }
                Atom[][] atoms = getAtoms(peak);
                int pDim1 = matchCriteria[0].getDim();
                int pDim2 = matchCriteria[1].getDim();
                if ((atoms[pDim1] != null) && (atoms[pDim2] != null)) {
                    int nProtons1 = atoms[pDim1].length;
                    int nProtons2 = atoms[pDim2].length;
                    if ((nProtons1 > 0) && (nProtons2 > 0)) {
                        if ((nProtons1 == nProtons2) || (nProtons1 == 1) || (nProtons2 == 1)) {
                            int maxN = nProtons1 > nProtons2 ? nProtons1 : nProtons2;
                            for (int iProton = 0; iProton < maxN; iProton++) {
                                SpatialSet sp1 = null;
                                SpatialSet sp2 = null;
                                int iProton1 = iProton;
                                int iProton2 = iProton;
                                if (iProton >= nProtons1) {
                                    iProton1 = 0;
                                }
                                if (iProton >= nProtons2) {
                                    iProton2 = 0;
                                }
                                if (atoms[pDim1][iProton1] != null) {
                                    sp1 = atoms[pDim1][iProton1].spatialSet;
                                }
                                if ((atoms[pDim2][iProton2] != null)) {
                                    sp2 = atoms[pDim2][iProton2].spatialSet;
                                }
                                if ((sp1 != null) && (sp2 != null)) {
                                    String name = sp1.getFullName() + "_" + sp2.getFullName();
                                    NoeMatch match = new NoeMatch(sp1, sp2, Constraint.GenTypes.MANUAL, 0.0);
                                    map.put(name, match);
                                }
                            }

                        }
                    }
                }

                Vector[] matchList = idPeak.scan3(matchCriteria, true);
                ArrayList<IdResult> idResults = idPeak.getResults2(matchList, matchCriteria);
                int nMan = map.size();
                if ((nMan == 0) || !strict) {
                    for (IdResult idResult : idResults) {
                        SpatialSet sp1 = idResult.getSpatialSet(0);
                        SpatialSet sp2 = idResult.getSpatialSet(1);
                        String name = sp1.getFullName() + "_" + sp2.getFullName();
                        if (!map.containsKey(name)) {
                            Constraint.GenTypes type = Constraint.GenTypes.AUTOMATIC;
                            if (nMan > 0) {
                                type = Constraint.GenTypes.AUTOPLUS;
                            }
                            NoeMatch match = new NoeMatch(sp1, sp2, type, idResult.getPPMError(1.0));
                            map.put(name, match);
                        }
                    }
                }

                int nPossible = map.size();
                if (nPossible > maxAmbig) {
                    nMaxAmbig++;
                } else if (nPossible > 0) {
                    nTotal += nPossible;
                    nAssigned++;
                    if (!getInfo) {
                        for (Map.Entry<String, NoeMatch> entry : map.entrySet()) {
                            NoeMatch nM = entry.getValue();
                            Noe noe = new Noe(peak, nM.sp1, nM.sp2, scale);
                            noe.intensity = peak.getIntensity();
                            noe.volume = peak.getVolume1();
                            noe.ppmError = nM.error;
                            noe.nPossible = nPossible;
                            noe.setGenType(nM.type);

                            noeList.add(noe);
                        }
                    }
                }
            }
        }

//                Vector[] matchList = idPeak.scan3(matchCriteria);
//                ArrayList<IdResult> idResults = idPeak.getResults2(matchList, matchCriteria);
//                // fixme filter duplicates ( stereo specific )
//                int nPossible = idResults.size();
//                if (nPossible <= maxAmbig) {
//                    nTotal += nPossible;
//                    if (nPossible > 0) {
//                        nAssigned++;
//                        if (!getInfo) {
//                            SpatialSetGroup[] spgSets = new SpatialSetGroup[2];
//                            for (IdResult idResult : idResults) {
//                                spgSets[0] = new SpatialSetGroup(idResult.spatialSets[matchCriteria[0].dim]);
//                                spgSets[1] = new SpatialSetGroup(idResult.spatialSets[matchCriteria[1].dim]);
//                                for (SpatialSetGroup spg : spgSets) {
//                                    SpatialSet sp1 = spg.getFirstSet();
//                                    Atom atom1 = sp1.atom;
//                                    if (atom1.isMethyl()) {
//                                        Atom[] partners = atom1.getPartners(1, 1);
//                                        for (Atom partner : partners) {
//                                            SpatialSet sp = partner.getSpatialSet(sp1.getName());
//                                            spg.add(sp);
//                                        }
//                                    }
//                                }
//
//                                Noe noe = new Noe(peak, spgSets[0], spgSets[1], scale);
//                                noe.intensity = peak.getIntensity();
//                                noe.volume = peak.getVolume1();
//                                noe.ppmError = idResult.getPPMError(1.0);
//                                noe.nPossible = nPossible;
//                                noe.setGenType(Constraint.GenTypes.AUTOMATIC);
//                                noeList.add(noe);
//                            }
//                        }
//                    }
//                } else {
//                    nMaxAmbig++;
//                }
        List<String> result = new ArrayList<>();
        result.add("nPeaks");
        result.add(String.valueOf(nPeaks));
        result.add("nAssigned");
        result.add(String.valueOf(nAssigned));
        result.add("nMaxAmbig");
        result.add(String.valueOf(nMaxAmbig));
        result.add("nTotal");
        result.add(String.valueOf(nTotal));
        return result;
    }
}
