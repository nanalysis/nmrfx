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

import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import java.util.*;
import java.util.Map.Entry;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.SpatialSetGroup;
import static org.nmrfx.structure.chemistry.constraints.Noe.avgDistance;
import static org.nmrfx.structure.chemistry.constraints.Noe.getAtoms;
import static org.nmrfx.structure.chemistry.constraints.Noe.getProtons;
import org.nmrfx.structure.utilities.Util;

/**
 *
 * @author brucejohnson
 */
public class NoeSet implements ConstraintSet, Iterable {

    private static double SYM_BONUS = 10.0;
    private static double CMAX = 5.5;
    private static double CMAX_BONUS = 10.0;
    private static double MAX_BONUS = 20.0;

    public static final HashMap<String, NoeSet> NOE_SETS = new HashMap<String, NoeSet>();
    private static boolean sumAverage = true;
    static NoeSet ACTIVE_SET = null;
    private final List<Noe> constraints = new ArrayList<>(64);
    private final Map<Peak, List<Noe>> peakMap = new TreeMap<>();
    private final Map<PeakList, NoeCalibration> scaleMap = new HashMap<>();
    private final String name;
    public static Peak lastPeakWritten = null;
    public static int memberID = 0;
    public static int ID = 0;
    private char[] violCharArray = new char[0];
    private boolean calibratable = true;
    private boolean dirty = true;
    private boolean useDistances = false;

    public NoeSet(String name) {
        this.name = name;
    }

    public static NoeSet addSet(String name) {
        NoeSet noeSet = new NoeSet(name);
        NOE_SETS.put(name, noeSet);
        ACTIVE_SET = noeSet;
        return noeSet;
    }

    public static void reset() {
        for (Map.Entry<String, NoeSet> cSet : NOE_SETS.entrySet()) {
            cSet.getValue().clear();
        }
        NOE_SETS.clear();
        addSet("default");
    }

    public static void remove(final String name) {
        NoeSet noeSet = NOE_SETS.get(name);
        if (noeSet != null) {
            noeSet.clear();
            NOE_SETS.remove(name);
        }
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return "general_distance_constraints";
    }

    public String getListType() {
        return "_Gen_dist_constraint_list";
    }

    public String getType() {
        return "NOE";
    }

    public static NoeSet getSet(String name) {
        NoeSet noeSet = NOE_SETS.get(name);
        return noeSet;
    }

    public static List<String> getNames() {
        List<String> names = new ArrayList<String>();
        for (String name : NOE_SETS.keySet()) {
            names.add(name);
        }
        return names;
    }

    public static List<NoeSet> getSets() {
        List<NoeSet> sets = new ArrayList<NoeSet>();
        sets.addAll(NOE_SETS.values());
        return sets;
    }

    public int getSize() {
        return constraints.size();
    }

    public void clear() {
        constraints.clear();
        peakMap.clear();
    }

    @Override
    public void add(Constraint constraint) {
        Noe noe = (Noe) constraint;
        noe.setID(constraints.size());
        constraints.add(noe);
        List<Noe> noeList = getConstraintsForPeak(noe.getPeak());
        noeList.add(noe);
        dirty = true;
    }

    public List<Noe> get() {
        return constraints;
    }
    
    public static NoeSet getActiveSet() {
        return ACTIVE_SET;
    }

    public Noe get(int i) {
        return constraints.get(i);
    }

    public Iterator iterator() {
        return constraints.iterator();
    }

    public List<Noe> getConstraintsForPeak(Peak peak) {
        List<Noe> noeList = peakMap.get(peak);
        if (noeList == null) {
            noeList = new ArrayList<Noe>();
            peakMap.put(peak, noeList);
        }
        return noeList;
    }

    public Set<Entry<Peak, List<Noe>>> getPeakMapEntries() {
        return peakMap.entrySet();
    }

    public void clearScaleMap() {
        scaleMap.clear();
    }

    public void setScale(PeakList peakList, NoeCalibration noeCal) {
        scaleMap.put(peakList, noeCal);
    }

    public NoeCalibration getCalibration(PeakList peakList) {
        NoeCalibration noeCal = scaleMap.get(peakList);
        return noeCal;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty() {
        dirty = true;
    }

    public boolean isCalibratable() {
        return calibratable;
    }

    public void setCalibratable(final boolean state) {
        calibratable = state;
    }

    public static void setSumAverage(boolean state) {
        sumAverage = state;
    }

    public static boolean getSumAverage() {
        return sumAverage;
    }

    static String[] noeLoopStrings = {
        "_Gen_dist_constraint.ID",
        "_Gen_dist_constraint.Member_ID",
        "_Gen_dist_constraint.Member_logic_code",
        "_Gen_dist_constraint.Assembly_atom_ID_1",
        "_Gen_dist_constraint.Entity_assembly_ID_1",
        "_Gen_dist_constraint.Entity_ID_1",
        "_Gen_dist_constraint.Comp_index_ID_1",
        "_Gen_dist_constraint.Seq_ID_1",
        "_Gen_dist_constraint.Comp_ID_1",
        "_Gen_dist_constraint.Atom_ID_1",
        "_Gen_dist_constraint.Atom_type_1",
        "_Gen_dist_constraint.Atom_isotope_number_1",
        "_Gen_dist_constraint.Resonance_ID_1",
        "_Gen_dist_constraint.Assembly_atom_ID_2",
        "_Gen_dist_constraint.Entity_assembly_ID_2",
        "_Gen_dist_constraint.Entity_ID_2",
        "_Gen_dist_constraint.Comp_index_ID_2",
        "_Gen_dist_constraint.Seq_ID_2",
        "_Gen_dist_constraint.Comp_ID_2",
        "_Gen_dist_constraint.Atom_ID_2",
        "_Gen_dist_constraint.Atom_type_2",
        "_Gen_dist_constraint.Atom_isotope_number_2",
        "_Gen_dist_constraint.Resonance_ID_2",
        "_Gen_dist_constraint.Intensity_val",
        "_Gen_dist_constraint.Intensity_lower_val_err",
        "_Gen_dist_constraint.Intensity_upper_val_err",
        "_Gen_dist_constraint.Distance_val",
        "_Gen_dist_constraint.Distance_lower_bound_val",
        "_Gen_dist_constraint.Distance_upper_bound_val",
        "_Gen_dist_constraint.Contribution_fractional_val",
        "_Gen_dist_constraint.Spectral_peak_ID",
        "_Gen_dist_constraint.Spectral_peak_list_ID",
        "_Gen_dist_constraint.Entry_ID",
        "_Gen_dist_constraint.Gen_dist_constraint_list_ID",};

    public String[] getLoopStrings() {
        return noeLoopStrings;
    }

    public void resetWriting() {
        memberID = -1;
        lastPeakWritten = null;
        ID = 0;
    }

    public void updateContributions(boolean useDistances, boolean requireActive) {
        this.useDistances = useDistances;
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

    public void inactivateDiagonal() {
        for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
            boolean hasDiagonal = false;
            List<Noe> noeList = entry.getValue();
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

    public void convertToMethyls() {
        for (Noe noe : constraints) {
            noe.spg1.convertToMethyl();
            noe.spg2.convertToMethyl();
        }
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

    public static void updateNOEListDistances(List<Noe> noeList) {
        double sum = 0.0;
        Molecule mol = Molecule.getActive();
        int[] structures = mol.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        int nStructures;
        ArrayList<Double> dList = new ArrayList<>();
        for (Noe noe : noeList) {
            dList.clear();
            double bound = noe.getUpper();
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

    public static void updateNOEListDistancesAvg(List<Noe> noeList, boolean requireActive) {
        Molecule mol = Molecule.getActive();
        int[] structures = mol.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        int nStructures = structures.length;
        SummaryStatistics sumStat = new SummaryStatistics();
        List<Double> dArray = new ArrayList<>();
        int nInBounds = 0;
        BitSet violStructures = new BitSet(nStructures);
        for (int iStruct : structures) {
            dArray.clear();
            double bound = 0.0;
            double max = 10.0;
            for (Noe noe : noeList) {
                bound = noe.getUpper();
                if (!requireActive || noe.isActive()) {
                    Atom.getDistances(noe.spg1, noe.spg2, iStruct, dArray);
                } else {
                    double distance = noe.disStat.getMax();
                    if (distance > max) {
                        max = distance;
                    }
                }
            }
            if (dArray.isEmpty()) {
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
            noe.setDisStatAvg(dStat);
        }
    }

    public void updateDistancesIndividual() {
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
        if (getPeakMapEntries().isEmpty()) {
            List<Noe> noeList = get();
            updateNOEListDistances(noeList);
        } else {
            for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
                List<Noe> noeList = entry.getValue();
                updateNOEListDistances(noeList);
            }
        }
    }

    public void updateDistances(boolean requireActive) {
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
        if (getPeakMapEntries().isEmpty()) {
            List<Noe> noeList = get();
            for (Noe noe : noeList) {
                noe.setDisStatAvg(noe.getStat());
            }
        } else {
            for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
                List<Noe> noeList = entry.getValue();
                updateNOEListDistancesAvg(noeList, requireActive);
            }
        }
    }

    public void updateDistanceContribs() {
        double expNum = 6.0;
        double disMinLim = 2.2;
        for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
            List<Noe> noeList = entry.getValue();
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
                noe.setDisContrib(Math.pow(disMin, -expNum) / sum);
            }
        }
    }

    public void findRedundant() {
        int nNoe = getSize();
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            System.out.println("null mol");
            return;
        }
        Map<String, List<Noe>> dupMap = new TreeMap<>();
        StringBuilder cName1 = new StringBuilder();
        StringBuilder cName2 = new StringBuilder();

        for (int i = 0; i < nNoe; i++) {
            cName1.setLength(0);
            cName2.setLength(0);
            Noe iNoe = get(i);
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

                List<Noe> noeList = dupMap.get(cName);
                if (noeList == null) {
                    noeList = new ArrayList<>();
                    dupMap.put(cName, noeList);
                }
                noeList.add(iNoe);

            }
        }
        for (Entry<String, List<Noe>> eSet : dupMap.entrySet()) {
            String cName = eSet.getKey();
            List<Noe> noeList = eSet.getValue();
            Noe weakestNoe = null;
            double maxBound = 0.0;
            for (Noe noe : noeList) {
                if (noe.getUpper() >= maxBound) {
                    maxBound = noe.getUpper();
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
                    NoeCalibration noeCal = getCalibration(peakList);
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

    public void findNetworks(boolean useContrib) {
        int nNoe = getSize();
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            System.out.println("null mol");
            return;
        }
        Map<String, Map<String, Noe>> resMap1 = new TreeMap<>();
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
            Noe iNoe = get(i);
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
                    resMap2 = new HashMap<>();
                    resMap1.put(cName, resMap2);
                }
                Noe testNoe = resMap2.get(aName);
                if ((testNoe == null) || (testNoe.getContribution() < iNoe.getContribution())) {
                    resMap2.put(aName, iNoe);
                }
                iNoe.resMap = resMap2;
            }
        }
        long mid = System.currentTimeMillis();

        Map<Residue, Integer> countMap = new HashMap<>();
        for (Noe iNoe : get()) {
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
                    iNoe.setNetworkValue(1.0);
                } else {
                    Integer count1 = countMap.get(r1);
                    if (count1 == null) {
                        int nAtoms1 = r1.getAtoms("H*").size();
                        count1 = nAtoms1;
                        countMap.put(r1, count1);
                    }
                    Integer count2 = countMap.get(r2);
                    if (count2 == null) {
                        int nAtoms2 = r2.getAtoms("H*").size();
                        count2 = nAtoms2;
                        countMap.put(r2, count2);
                    }
                    Map<String, Noe> resMap2 = iNoe.resMap;
                    double scale = Math.sqrt(count1 * count2);
                    double sum = 0.01;
                    if (resMap2 != null) {
                        for (Noe jNoe : resMap2.values()) {
                            if (useContrib) {
                                sum += jNoe.getContribution();
                            } else {
                                sum += 1.0;
                            }
                        }
                    }
                    iNoe.setNetworkValue(sum / scale);
                }
            }
        }
        long done = System.currentTimeMillis();
        System.out.println((mid - start) + " " + (done - mid));
    }

    public void limitToAssigned() {
        for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
            double sum = 0.0;
            List<Noe> noeList = entry.getValue();
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

    public void clearLimitToAssigned() {
        for (Noe noe : get()) {
            noe.activate(Flags.LABEL);
        }
    }

    public void limitToMaxAmbig(int maxVal) {
        for (Noe noe : get()) {
            if (noe.getNPossible() > maxVal) {
                noe.inactivate(Flags.MAXAMBIG);
            } else {
                noe.activate(Flags.MAXAMBIG);
            }
        }
    }

    public void limitToMinContrib(double minContrib) {
        for (Noe noe : get()) {
            if (noe.getContribution() < minContrib) {
                noe.inactivate(Flags.MINCONTRIB);
            } else {
                noe.activate(Flags.MINCONTRIB);
            }
        }
    }

    public void limitToMinPPMError(double minPPM) {
        for (Noe noe : get()) {
            if (noe.getPpmError() < minPPM) {
                noe.inactivate(Flags.MINPPM);
            } else {
                noe.activate(Flags.MINPPM);
            }
        }
    }

    public void limitToMaxViol(double maxViol) {
        for (Noe noe : get()) {
            double disMin = noe.disStat.getMin();
            if ((disMin - noe.getUpper()) > maxViol) {
                noe.inactivate(Flags.MAXVIOL);
            } else {
                noe.activate(Flags.MAXVIOL);
            }
        }
    }

    public HashMap<PeakList, List<Double>> calcMedian(String mMode, PeakList whichList) {
        Map<PeakList, List<Double>> valuesMap = new HashMap<>();
        for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
            PeakList peakList = entry.getKey().peakList;
            if ((whichList != null) && (whichList != peakList)) {
                continue;
            }
            List<Noe> noeList = entry.getValue();
            double maxContrib = 0.0;
            double scaledIntensity = 1.0;
            boolean foundActive = false;
            for (Noe noe : noeList) {
                if (!noe.isActive()) {
                    continue;
                }
                double scale = 1.0;
                double intensity;
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
                if (noe.getContribution() > maxContrib) {
                    if (mMode.startsWith("int")) {
                        intensity = Math.abs(noe.getIntensity());
                    } else {
                        intensity = Math.abs(noe.getVolume());
                    }
                    maxContrib = noe.getContribution();
                    scaledIntensity = intensity / noe.getScale() / scale;
                }
                foundActive = true;
            }
            List<Double> dList = valuesMap.get(peakList);
            if (dList == null) {
                dList = new ArrayList<>();
                valuesMap.put(peakList, dList);
            }
            if (foundActive) {
                dList.add(scaledIntensity);
            }
        }
        HashMap<PeakList, List<Double>> medianMap = new HashMap<>();
        for (Entry<PeakList, List<Double>> entry : valuesMap.entrySet()) {
            PeakList peakList = entry.getKey();
            List<Double> dList = entry.getValue();
            List<Double> valueList = new ArrayList<>();
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

    public void calibrateExp(PeakList whichList) {
        if (getSize() == 0) {
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
        for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
            PeakList peakList = entry.getKey().peakList;
            if ((whichList != null) && (whichList != peakList)) {
                continue;
            }
            List<Noe> noeList = entry.getValue();
            NoeCalibration noeCal = getCalibration(peakList);
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

    public void updateNPossible(PeakList whichList) {
        for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
            PeakList peakList = entry.getKey().peakList;
            if ((whichList != null) && (whichList != peakList)) {
                continue;
            }
            List<Noe> noeList = entry.getValue();
            NoeCalibration noeCal = getCalibration(peakList);
            if (noeCal == null) {
                noeCal = defaultCal(peakList);
            }
            for (Noe noe : noeList) {
                noe.setNPossible(noeList.size());
            }
        }
    }

    public void findSymmetrical() {
        int nNoe = getSize();
        Map<String, Noe> symMap = new HashMap<>();
        for (int i = 0; i < nNoe; i++) {
            Noe iNoe = get(i);
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
            Noe iNoe = get(i);
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

    public void calculateContributions(boolean useDistances) {
        for (Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
            double sum = 0.0;
            List<Noe> noeList = entry.getValue();
            for (Noe noe : noeList) {
                if (!(noe.activeFlags.contains(Flags.DIAGONAL))) {
                    double value = 1.0;
                    if (noe.symmetrical) {
                        value *= SYM_BONUS;
                    }
                    value *= noe.getNetworkValue();
                    if (value > MAX_BONUS) {
                        value = MAX_BONUS;
                    }
                    value *= noe.getPpmError();
                    if (useDistances) {
                        value *= noe.getDisContrib();
                    }
                    noe.setContribution(value);

                    sum += value;
                }
            }

            for (Noe noe : noeList) {
                if (sum == 0.0) {
                    noe.setContribution(0.0);
                } else {
                    noe.setContribution(noe.getContribution() / sum);
                }
            }
        }
    }

    public NoeCalibration defaultCal(PeakList peakList) {
        String mMode = "intensity";
        HashMap<PeakList, List<Double>> medianMap = calcMedian(mMode, peakList);
        List<Double> valueList = medianMap.get(peakList);
        double referenceValue = valueList.get(2);
        double referenceDist = 3.0;
        double expValue = 6.0;
        double lower = 1.8;
        double minBound = 2.0;
        double maxBound = 6.0;
        double fError = 0.125;
        NoeCalibration noeCal = new NoeCalibrationExp(mMode, lower, referenceValue, referenceDist, expValue, minBound, maxBound, fError, true);
        setScale(peakList, noeCal);
        return noeCal;
    }

    public synchronized List<Noe> getConstraints(String filter, boolean requireActive) {
        List listCopy = new ArrayList();
        if (dirty) {
            updateContributions(useDistances, requireActive);
        }
        if (filter.trim().length() == 0) {
            for (Noe noe : get()) {
                if (requireActive && !noe.isActive()) {
                    continue;
                }
                listCopy.add(noe);
            }
        } else {
            String filterVals[] = filter.split("\\s");
            for (Noe noe : get()) {
                noe.setFilterSwapped(false);
                if (requireActive && !noe.isActive()) {
                    continue;
                }
                String name1 = noe.spg1.getFullName();
                String name2 = noe.spg2.getFullName();
                if (!name1.contains(":")) {
                    name1 = "*.*:" + name1;
                }
                if (!name2.contains(":")) {
                    name2 = "*.*:" + name2;
                }
                boolean addNoe = false;
                if (filterVals.length == 1) {
                    if (Util.stringMatch(name1, filter)) {
                        addNoe = true;
                    } else if (Util.stringMatch(name2, filter)) {
                        noe.setFilterSwapped(true);
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
                            noe.setFilterSwapped(true);
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

}
