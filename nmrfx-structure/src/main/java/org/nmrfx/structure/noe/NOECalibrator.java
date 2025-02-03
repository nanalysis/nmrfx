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

package org.nmrfx.structure.noe;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.*;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.nmrfx.chemistry.constraints.Noe.avgDistance;
import static org.nmrfx.chemistry.constraints.Noe.getAtoms;

/**
 * @author brucejohnson
 */
public class NOECalibrator {

    private static final Logger log = LoggerFactory.getLogger(NOECalibrator.class);
    private static double SYM_BONUS = 10.0;
    private static double CMAX = 5.5;
    private static double CMAX_BONUS = 10.0;
    private static double MAX_BONUS = 20.0;

    private static boolean sumAverage = true;
    NoeSet noeSet;
    MoleculeBase molecule;
    private boolean useDistances = false;
    private char[] violCharArray = new char[0];
    private final Map<PeakList, NoeCalibration> scaleMap = new HashMap<>();

    public NOECalibrator(NoeSet noeSet) {
        this.noeSet = noeSet;
        this.molecule = noeSet.getMolecularConstraints().molecule;
    }

    public static void setSumAverage(boolean state) {
        sumAverage = state;
    }

    public static boolean getSumAverage() {
        return sumAverage;
    }

    public NoeCalibration getCalibration(PeakList peakList) {
        NoeCalibration noeCal = scaleMap.get(peakList);
        return noeCal;
    }

    public void clearScaleMap() {
        scaleMap.clear();
    }

    public void setScale(PeakList peakList, NoeCalibration noeCal) {
        scaleMap.put(peakList, noeCal);
    }

    public void setScale(String mMode, double referenceDist, double expValue, double minBound, double maxBound, double fError) {
        for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
            PeakList peakList = entry.getKey().getPeakList();
            HashMap<PeakList, List<Double>> medianMap = calcMedian(mMode, peakList);
            List<Double> valueList = medianMap.get(peakList);
            double referenceValue = valueList.get(2);
            double lower = 1.8;
            NoeCalibration noeCal = new NoeCalibrationExp(mMode, lower, referenceValue, referenceDist, expValue, minBound, maxBound, fError, true);
            scaleMap.put(peakList, noeCal);
        }
    }

    public void updateContributions(boolean useDistances, boolean requireActive, boolean calibrate) {
        this.useDistances = useDistances;
        inactivateDiagonal();
        updateDistances(requireActive);
        updateDistanceContribs();
        findSymmetrical();
        findNetworks(false);
        calculateContributions(useDistances);
        findNetworks(true);
        calculateContributions(useDistances);
        findNetworks(true);
        calculateContributions(useDistances);
        if (calibrate) {
            calibrateExp(null);
        }

        findRedundant();
        noeSet.setDirty(false);
    }

    public void inactivateDiagonal() {
        for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
            boolean hasDiagonal = false;
            List<Noe> noeList = entry.getValue();
            for (Noe noe : noeList) {
                if (noe.getSpg1().getSpatialSet() == noe.getSpg2().getSpatialSet()) {  // don't include diagonal peaks
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
        for (Noe noe : noeSet.getConstraints()) {
            noe.getSpg1().convertToMethyl();
            noe.getSpg2().convertToMethyl();
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

    public static void updateNOEListDistances(MoleculeBase mol, List<? extends Noe> noeList) {
        for (Noe distanceConstraint : noeList) {
            updateDistanceStat(mol, distanceConstraint);
        }
    }

    public static void updateDistanceStat(MoleculeBase mol, Noe distanceConstraint) {
        int[] structures = mol.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        int nStructures;
        ArrayList<Double> dList = new ArrayList<>();
        dList.clear();
        double bound = distanceConstraint.getUpper();
        int nInBounds = 0;
        nStructures = 1;
        BitSet violStructures = distanceConstraint.disStat.getViolStructures();
        if (structures.length > 0) {
            nStructures = structures.length;
            if (violStructures == null) {
                violStructures = new BitSet(nStructures);
            }
            violStructures.clear();
            SpatialSetGroup spg1 = distanceConstraint.getSpg1();
            SpatialSetGroup spg2 = distanceConstraint.getSpg2();

            for (int iStruct : structures) {
                double distance = Atom.calcWeightedDistance(spg1, spg2, iStruct, 6, false, sumAverage);
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
        distanceConstraint.disStat = dStat;

    }

    public void updateNOEListDistancesAvg(List<Noe> noeList, boolean requireActive) {
        int[] structures = molecule.getActiveStructures();
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
                    Atom.getDistances(noe.getSpg1(), noe.getSpg2(), iStruct, dArray);
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
        if (molecule == null) {
            return;
        }
        int[] structures = molecule.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        int lastStruct = 0;
        for (int iStruct : structures) {
            lastStruct = Math.max(iStruct, lastStruct);
        }
        violCharArray = new char[lastStruct + 1];
        if (noeSet.getPeakMapEntries().isEmpty()) {
            List<Noe> noeList = noeSet.getConstraints();
            updateNOEListDistances(noeSet.getMolecularConstraints().molecule, noeList);
        } else {
            for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
                List<Noe> noeList = entry.getValue();
                updateNOEListDistances(noeSet.getMolecularConstraints().molecule, noeList);
            }
        }
    }

    public void updateDistances(boolean requireActive) {
        if (molecule == null) {
            return;
        }
        updateDistancesIndividual();
        int[] structures = molecule.getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        int lastStruct = 0;
        for (int iStruct : structures) {
            lastStruct = iStruct > lastStruct ? iStruct : lastStruct;
        }
        violCharArray = new char[lastStruct + 1];
        if (noeSet.getPeakMapEntries().isEmpty()) {
            List<Noe> noeList = noeSet.getConstraints();
            for (Noe noe : noeList) {
                noe.setDisStatAvg(noe.getStat());
            }
        } else {
            for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
                List<Noe> noeList = entry.getValue();
                updateNOEListDistancesAvg(noeList, requireActive);
            }
        }
    }

    public void updateDistanceContribs() {
        double expNum = 6.0;
        double disMinLim = 2.2;
        for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
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
        int nNoe = noeSet.getSize();
        if (molecule == null) {
            log.warn("null mol");
            return;
        }
        Map<String, List<Noe>> dupMap = new TreeMap<>();
        StringBuilder cName1 = new StringBuilder();
        StringBuilder cName2 = new StringBuilder();

        for (int i = 0; i < nNoe; i++) {
            cName1.setLength(0);
            cName2.setLength(0);
            Noe iNoe = noeSet.get(i);
            iNoe.getSpg1().getName();
            if (iNoe.getSpg1().getSpatialSet() == iNoe.getSpg2().getSpatialSet()) {  // don't include diagonal peaks
                continue;
            }
            Entity e1 = iNoe.getSpg1().getAnAtom().getEntity();
            Entity e2 = iNoe.getSpg2().getAnAtom().getEntity();
            Residue r1 = checkEntityIsResidue(e1);
            Residue r2 = checkEntityIsResidue(e2);

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
                String a1 = iNoe.getSpg1().getAnAtom().getName();
                String a2 = iNoe.getSpg2().getAnAtom().getName();

                cName1.append(iNoe.getSpg1().getName());
                cName1.append('.');
                cName1.append(eName1);
                cName1.append(':');
                cName1.append(r1.getNumber());
                cName1.append('.');
                cName1.append(a1);

                cName2.append(iNoe.getSpg2().getName());
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
        for (Map.Entry<String, List<Noe>> eSet : dupMap.entrySet()) {
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

    /**
     * Checks if the entity is a residue and returns it as a residue, if not a message is logged and null is returned.
     *
     * @param entity The entity to check
     * @return The entity cast to a residue
     */
    private Residue checkEntityIsResidue(Entity entity) {
        if (entity instanceof Residue) {
            return (Residue) entity;
        } else {
            log.info("Entity is not a polymer: {}", entity.getName());
            return null;
        }
    }

    public void findNetworks(boolean useContrib) {
        Map<String, List<Noe>> resNoeMap = new HashMap<>();
        for (Noe noe : noeSet.getConstraints()) {
            if (noe.getSpg1().getAnAtom().getEntity() == noe.getSpg2().getAnAtom().getEntity()) {
                noe.setNetworkValue(1.0);
            } else {
                Optional<String> cNameOpt = getNOEKey(noe);
                cNameOpt.ifPresent(cName -> {
                    var noeList = resNoeMap.computeIfAbsent(cName, k -> new ArrayList<>());
                    noeList.add(noe);
                });
            }
        }
        for (var entry : resNoeMap.entrySet()) {
            List<Noe> noeList = entry.getValue();
            Map<String, Double> atomMap = new HashMap<>();
                for (Noe noe : noeList) {
                String atomKey = getAtomKey(noe);
                Double contrib = atomMap.get(atomKey);
                Double currentContribution = noe.getContribution();
                if ((contrib == null) || (currentContribution > contrib)) {
                    atomMap.put(atomKey, currentContribution);
                }
            }
            double scale = countAtoms(noeList.getFirst());
            double sum = 0.0;
            for (var atomEntry : atomMap.entrySet()) {
                if (useContrib) {
                    Double contrib = atomEntry.getValue();
                    sum += contrib;
                } else {
                    sum += 1.0;
                }
            }
            for (Noe noe : noeList) {
                noe.setNetworkValue(sum / scale);
            }
        }
    }

    double countAtoms(Noe iNoe) {
        Entity e1 = iNoe.getSpg1().getAnAtom().getEntity();
        Entity e2 = iNoe.getSpg2().getAnAtom().getEntity();
        Residue r1 = checkEntityIsResidue(e1);
        Residue r2 = checkEntityIsResidue(e2);
        double scale = 1.0;

        if ((r1 != null) && (r2 != null)) {

            int nAtoms1 = r1.getAtoms("H*").size();

            int nAtoms2 = r2.getAtoms("H*").size();
            scale = Math.sqrt(nAtoms1 * nAtoms2);
        }
        return scale;
    }

    private String getAtomKey(Noe noe) {
        String a1 = noe.getSpg1().getAnAtom().getName();
        String a2 = noe.getSpg2().getAnAtom().getName();
        String aName;
        if (a1.compareTo(a2) >= 0) {
            aName = a1 + "." + a2;
        } else {
            aName = a2 + "." + a1;
        }
        return aName;
    }

    private Optional<String> getNOEKey(Noe iNoe) {
        Entity e1 = iNoe.getSpg1().getAnAtom().getEntity();
        Entity e2 = iNoe.getSpg2().getAnAtom().getEntity();
        Residue r1 = checkEntityIsResidue(e1);
        Residue r2 = checkEntityIsResidue(e2);
        StringBuilder cName1 = new StringBuilder();
        StringBuilder cName2 = new StringBuilder();
        String cName = null;

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
            cName1.append(iNoe.getSpg1().getName());
            cName1.append('.');
            cName1.append(eName1);
            cName1.append(':');
            cName1.append(r1.getNumber());
            cName2.append(iNoe.getSpg2().getName());
            cName2.append('.');
            cName2.append(eName2);
            cName2.append(':');
            cName2.append(r2.getNumber());

            if (cName1.toString().compareTo(cName2.toString()) < 0) {
                cName1.append('_');
                cName1.append(cName2);
                cName = cName1.toString();
            } else {
                cName2.append('_');
                cName2.append(cName1);
                cName = cName2.toString();
            }
        }
        return Optional.ofNullable(cName);
    }

    public void limitToAssigned() {
        for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
            double sum = 0.0;
            List<Noe> noeList = entry.getValue();
            Peak peak = entry.getKey();
            Atom[][] atoms = getAtoms(peak);
            Atom[][] protons = Noe.getProtons(atoms);
            boolean isAssigned = false;
            for (int i = 0; i < protons[0].length; i++) {
                if ((protons[0][i] != null) && (protons[1][i] != null)) {
                    log.info(protons[0][i].spatialSet.getFullName());
                    log.info(protons[1][i].spatialSet.getFullName());
                    isAssigned = true;
                    break;
                }
            }
            log.info("{} {}", protons[0].length, isAssigned);
            for (Noe noe : noeList) {
                String spg1Name = noe.getSpg1().getFullName();
                String spg2Name = noe.getSpg2().getFullName();
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
                log.info("{} {} {}", spg1Name, spg2Name, consistent);
                if (isAssigned && !consistent) {
                    noe.inactivate(Flags.LABEL);
                } else {
                    noe.activate(Flags.LABEL);
                }
            }
        }
    }

    public void clearLimitToAssigned() {
        for (Noe noe : noeSet.getConstraints()) {
            noe.activate(Flags.LABEL);
        }
    }

    public void limitToMaxAmbig(int maxVal) {
        for (Noe noe : noeSet.getConstraints()) {
            if (noe.getNPossible() > maxVal) {
                noe.inactivate(Flags.MAXAMBIG);
            } else {
                noe.activate(Flags.MAXAMBIG);
            }
        }
    }

    public void limitToMinContrib(double minContrib) {
        for (Noe noe : noeSet.getConstraints()) {
            if (noe.getContribution() < minContrib) {
                noe.inactivate(Flags.MINCONTRIB);
            } else {
                noe.activate(Flags.MINCONTRIB);
            }
        }
    }

    public void limitToMinPPMError(double minPPM) {
        for (Noe noe : noeSet.getConstraints()) {
            if (noe.getPpmError() < minPPM) {
                noe.inactivate(Flags.MINPPM);
            } else {
                noe.activate(Flags.MINPPM);
            }
        }
    }

    public void limitToMaxViol(double maxViol) {
        for (Noe noe : noeSet.getConstraints()) {
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
        for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
            PeakList peakList = entry.getKey().getPeakList();
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
                    SpatialSetGroup sp = (i == 0) ? noe.getSpg1() : noe.getSpg2();
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
                noe.setAtomScale(scale);
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
        for (Map.Entry<PeakList, List<Double>> entry : valuesMap.entrySet()) {
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
        if (noeSet.getSize() == 0) {
            return;
        }
        if (!noeSet.isCalibratable()) {
            return;
        }
        for (Noe noe : noeSet.getConstraints()) {
            PeakList peakList = noe.getPeak().peakList;
            if ((whichList != null) && (whichList != peakList)) {
                continue;
            }
            NoeCalibration noeCal = getCalibration(peakList);
            if (noeCal == null) {
                noeCal = defaultCal(peakList);
            }
            noeCal.calibrate(noe);
        }
    }

    public void findSymmetrical() {
        int nNoe = noeSet.getSize();
        Map<String, Noe> symMap = new HashMap<>();
        for (int i = 0; i < nNoe; i++) {
            Noe iNoe = noeSet.get(i);
            iNoe.setSymmetrical(false);
            Peak iPeak = iNoe.peak;
            if (iPeak != null) {
                String listName = iPeak.getPeakList().getName();
                String spg1Name = iNoe.getSpg1().getFullName();
                String spg2Name = iNoe.getSpg2().getFullName();
                symMap.put(listName + "." + spg1Name + "." + spg2Name, iNoe);
            }
        }
        for (int i = 0; i < nNoe; i++) {
            Noe iNoe = noeSet.get(i);
            Peak iPeak = iNoe.peak;
            if ((iPeak != null) && (!iNoe.getSymmetrical())) {
                String listName = iPeak.getPeakList().getName();
                String spg1Name = iNoe.getSpg1().getFullName();
                String spg2Name = iNoe.getSpg2().getFullName();
                Noe jNoe = symMap.get(listName + "." + spg2Name + "." + spg1Name);
                if (jNoe != null) {
                    iNoe.setSymmetrical(true);
                    jNoe.setSymmetrical(true);
                }
            }
        }
    }

    public void calculateContributions(boolean useDistances) {
        for (Map.Entry<Peak, List<Noe>> entry : noeSet.getPeakMapEntries()) {
            double sum = 0.0;
            List<Noe> noeList = entry.getValue();
            for (Noe noe : noeList) {
                if (!(noe.activeFlags.contains(Flags.DIAGONAL))) {
                    double value = 1.0;
                    if (noe.getSymmetrical()) {
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
        if (noeSet.isDirty()) {
            updateContributions(useDistances, requireActive, true);
        }
        if (filter.trim().length() == 0) {
            for (Noe noe : noeSet.getConstraints()) {
                if (requireActive && !noe.isActive()) {
                    continue;
                }
                listCopy.add(noe);
            }
        } else {
            String filterVals[] = filter.split("\\s");
            for (Noe noe : noeSet.getConstraints()) {
                noe.setFilterSwapped(false);
                if (requireActive && !noe.isActive()) {
                    continue;
                }
                String name1 = noe.getSpg1().getFullName();
                String name2 = noe.getSpg2().getFullName();
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
