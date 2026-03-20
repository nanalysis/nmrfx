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
package org.nmrfx.chemistry.constraints;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MolFilter;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.SpatialSetGroup;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.star.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author brucejohnson
 */
public class NoeSet implements ConstraintSet, Iterable {
    private static final String[] noeLoopStrings = {
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

    boolean containsBonds = false;
    private final MolecularConstraints molecularConstraints;

    private final List<Noe> constraints = new ArrayList<>(64);
    private final Map<Peak, List<Noe>> peakMap = new TreeMap<>();
    private final String name;
    private boolean calibratable = true;
    private boolean dirty = true;
    PeakList peakList = null;

    private NoeSet(MolecularConstraints molecularConstraints,
                   String name) {
        this.name = name;
        this.molecularConstraints = molecularConstraints;
    }

    public static NoeSet newSet(MolecularConstraints molecularConstraints,
                                String name) {
        return new NoeSet(molecularConstraints, name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCategory() {
        return "general_distance_constraints";
    }

    @Override
    public String getListType() {
        return "_Gen_dist_constraint_list";
    }

    @Override
    public String getType() {
        return "NOE";
    }

    @Override
    public int getSize() {
        return constraints.size();
    }

    @Override
    public void clear() {
        constraints.clear();
        peakMap.clear();
    }

    public PeakList getPeakList() {
        return peakList;
    }

    private Peak getPeak() {
        try {
            peakList = NMRStarReader.getPeakList(getName(), ".", peakList);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
        return peakList.getNewPeak();

    }

    public void addDistanceConstraint(final String filterString1, final String filterString2, final double rLow,
                                      final double rUp, boolean isBond) throws IllegalArgumentException {
        addDistanceConstraint(getPeak(), List.of(filterString1), List.of(filterString2), rLow, rUp, isBond, 1.0, null, null);
    }

    public void addDistanceConstraint(final List<String> filterStrings1, final List<String> filterStrings2,
                                      final double rLow, final double rUp) throws IllegalArgumentException {
        addDistanceConstraint(getPeak(), filterStrings1, filterStrings2, rLow, rUp, false, 1.0, null, null);
    }

    public void addDistanceConstraint(final List<String> filterStrings1, final List<String> filterStrings2,
                                      final double rLow, final double rUp, boolean isBond) throws IllegalArgumentException {
        addDistanceConstraint(getPeak(), filterStrings1, filterStrings2, rLow, rUp, isBond, 1.0, null, null);
    }

    public void addDistanceConstraint(Peak peak, final List<String> filterStrings1, final List<String> filterStrings2,
                                      final double rLow, final double rUp, boolean isBond, Double weight, Double targetValue, Double targetErr) throws IllegalArgumentException {
        if (filterStrings1.size() != filterStrings2.size()) {
            throw new IllegalArgumentException("atoms group 1 and atoms group 2 should be same size");
        }
        MoleculeBase molecule = molecularConstraints.molecule;
        List<SpatialSetGroup> spatialSetGroups1 = new ArrayList<>();
        List<SpatialSetGroup> spatialSetGroups2 = new ArrayList<>();
        for (int i = 0; i < filterStrings1.size(); i++) {
            String filterString1 = filterStrings1.get(i);
            String filterString2 = filterStrings2.get(i);
            MolFilter molFilter1 = new MolFilter(filterString1);
            MolFilter molFilter2 = new MolFilter(filterString2);

            List<Atom> group1 = MoleculeBase.getNEFMatchedAtoms(molFilter1, molecule);
            List<Atom> group2 = MoleculeBase.getNEFMatchedAtoms(molFilter2, molecule);

            if (group1.isEmpty()) {
                throw new IllegalArgumentException("atoms1 null " + filterString1);
            }
            if (group2.isEmpty()) {
                throw new IllegalArgumentException("atoms2 null " + filterString2);
            }
            SpatialSetGroup spatialSetGroup1 = new SpatialSetGroup(group1);
            SpatialSetGroup spatialSetGroup2 = new SpatialSetGroup(group2);
            spatialSetGroups1.add(spatialSetGroup1);
            spatialSetGroups2.add(spatialSetGroup2);

        }
        for (int i = 0; i < spatialSetGroups1.size(); i++) {
            SpatialSetGroup spGroup1 = spatialSetGroups1.get(i);
            SpatialSetGroup spGroup2 = spatialSetGroups2.get(i);

            if (weight != null && targetValue != null && targetErr != null) {
                Noe noe = new Noe(peak, spGroup1, spGroup2, 1.0);
                noe.isBond(isBond);

                noe.setLower(rLow);
                noe.setUpper(rUp);
                noe.setTarget(targetValue);
                noe.targetErr = targetErr;
                noe.weight = weight;
                add(noe);
            } else {
                Noe noe = new Noe(peak, spGroup1, spGroup2, 1.0);
                noe.isBond(isBond);
                noe.setLower(rLow);
                noe.setUpper(rUp);
                add(noe);
            }
        }

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

    public List<Noe> getConstraints() {
        return constraints;
    }

    @Override
    public Noe get(int i) {
        return constraints.get(i);
    }

    @Override
    public MolecularConstraints getMolecularConstraints() {
        return molecularConstraints;
    }

    @Override
    public Iterator iterator() {
        return constraints.iterator();
    }

    public List<Noe> getConstraintsForPeak(Peak peak) {
        return peakMap.computeIfAbsent(peak, k -> new ArrayList<>());
    }

    public Set<Entry<Peak, List<Noe>>> getPeakMapEntries() {
        return peakMap.entrySet();
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void setDirty() {
        dirty = true;
    }

    public boolean containsBonds() {
        return containsBonds;
    }

    public void containsBonds(boolean state) {
        containsBonds = state;
    }

    public void setDirty(boolean state) {
        dirty = state;
    }

    public boolean isCalibratable() {
        return calibratable;
    }

    public void setCalibratable(final boolean state) {
        calibratable = state;
    }

    @Override
    public String[] getLoopStrings() {
        return noeLoopStrings;
    }

    public void writeNMRFxFile(File file) throws IOException {
        Map<Peak, Integer> myPeakMap = new HashMap<>();
        try (FileWriter fileWriter = new FileWriter(file)) {
            int i = 0;
            for (Noe noe : constraints) {
                if (noe.isActive()) {
                    Integer iGroup = myPeakMap.computeIfAbsent(noe.getPeak(), peak -> myPeakMap.size());
                    double lower = noe.getLower();
                    double upper = noe.getUpper();
                    SpatialSetGroup spg1 = noe.getSpg1();
                    SpatialSetGroup spg2 = noe.getSpg2();
                    String aName1 = spg1.getFullName();
                    String aName2 = spg2.getFullName();
                    String outputString = String.format("%d\t%d\t%s\t%s\t%.3f\t%.3f\n", i, iGroup, aName1, aName2, lower, upper);
                    fileWriter.write(outputString);
                    i++;
                }
            }
        }
    }

    private void addPeak(Noe noe, List<Atom> atoms, List<Integer> iDims) {
        List<Double> ppms = new ArrayList<>();
        for (Atom atom : atoms) {
            ppms.add(atom.getPPM());
        }
        boolean ok = ppms.size() == peakList.getNDim() && !ppms.stream().anyMatch(ppm -> ppm == null);

        if (ok) {
            Peak peak = peakList.getNewPeak();
            if (noe != null) {
                noe.peak(peak);
                double upper = noe.getUpper();
                double intensity = Math.pow(upper, -1.0 / 6.0);
                peak.setIntensity((float) intensity);
            } else {
                peak.setIntensity(1.0f);
            }
            for (int j = 0; j < ppms.size(); j++) {
                PeakDim peakDim = peak.getPeakDim(iDims.get(j));
                peakDim.setLabel(atoms.get(j).getShortName());
                peakDim.setChemShiftValue(ppms.get(j).floatValue());
                float width = j == 0 ? 20.0f : 50.0f;
                peakDim.setLineWidthHz(width);
                peakDim.setBoundsHz(width * 2.0f);
            }
        }
    }

    private void addPeak(Noe noe, Atom atom1, Atom atom2, Set<Atom> diagAtoms, List<Integer> dataDims) {
        final int idDimAtom;
        List<Atom> atoms = new ArrayList<>();
        if ((atom1 != null) && (atom2 != null) && atom1.getName().equalsIgnoreCase("H")) {
            atoms.add(atom1);
            atoms.add(atom2);
            idDimAtom = 0;
        } else if ((atom1 != null) && (atom2 != null) && atom2.getName().equalsIgnoreCase("H")) {
            atoms.add(atom2);
            atoms.add(atom1);
            idDimAtom = 1;
        } else {
            idDimAtom = -1;
        }
        if (!atoms.isEmpty()) {
            diagAtoms.add(atoms.getFirst());
            for (int i = 2; i < peakList.getNDim(); i++) {
                Atom idAtom = idDimAtom == 0 ? atom1.getParent() : atom2.getParent();
                if (idAtom.getAtomicNumber() == 7) {
                    atoms.add(idAtom);
                    dataDims.add(i);
                }
            }
        }
        addPeak(noe, atoms, dataDims);
    }
    public void genPeakList(DatasetBase dataset) {
        String peakListName = PeakList.getNameForDataset(dataset.getName());
        PeakList peakList1 = PeakList.get(peakListName);
        List<Integer> dataDims = new ArrayList<>();
        if (peakList1 == null) {
            peakList1 = new PeakList(peakListName, dataset.getNDim());
            peakList1.setDatasetName(dataset.getName());
            boolean firstProton = true;
            for (int i = 0; i < dataset.getNDim(); i++) {
                SpectralDim dim = peakList1.getSpectralDim(i);
                dim.setSf(dataset.getSf(i));
                dim.setSw(dataset.getSw(i));
                dim.setDimName(dataset.getLabel(i));
                Nuclei nuclei = dataset.getNucleus(i);
                if (nuclei.getNumberAsInt() == 1) {
                    dataDims.add(i);
                    if (firstProton) {
                        dim.setPattern("i.h*");
                        firstProton = false;
                    } else {
                        dim.setPattern("j.h*");
                    }
                }
                dim.setNucleus(dataset.getNucleus(i).getNumberName());
            }
        }
        peakList = peakList1;
        Set<Atom> diagAtoms = new HashSet<>();
        constraints.stream().filter(Noe::isActive).forEach(noe -> {
            SpatialSetGroup spg1 = noe.getSpg1();
            SpatialSetGroup spg2 = noe.getSpg2();
            String aName1 = spg1.getFullName();
            String aName2 = spg2.getFullName();
            Atom atom1 = MoleculeBase.getAtomByName(aName1);
            Atom atom2 = MoleculeBase.getAtomByName(aName2);
            addPeak(noe, atom1, atom2, diagAtoms, dataDims);

        });
        diagAtoms.forEach(atom -> {
            List<Atom> atoms = List.of(atom, atom, atom.getParent());
            addPeak(null, atoms, new ArrayList<>(dataDims));
        });

    }

    public void updateNPossible(PeakList whichList) {
        for (Map.Entry<Peak, List<Noe>> entry : getPeakMapEntries()) {
            PeakList entryPeakList = entry.getKey().getPeakList();
            if ((whichList != null) && (whichList != entryPeakList)) {
                continue;
            }
            List<Noe> noeList = entry.getValue();
            for (Noe noe : noeList) {
                noe.setNPossible(noeList.size());
            }
        }
    }
}
