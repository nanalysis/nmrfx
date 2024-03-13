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

import org.nmrfx.chemistry.SpatialSetGroup;
import org.nmrfx.peaks.Peak;

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

    public static Peak lastPeakWritten = null;
    public static int memberId = 0;
    public static int id = 0;

    private final MolecularConstraints molecularConstraints;

    private final List<Noe> constraints = new ArrayList<>(64);
    private final Map<Peak, List<Noe>> peakMap = new TreeMap<>();
    private final String name;
    private boolean calibratable = true;
    private boolean dirty = true;

    private NoeSet(MolecularConstraints molecularConstraints,
                   String name) {
        this.name = name;
        this.molecularConstraints = molecularConstraints;
    }

    public static NoeSet newSet(MolecularConstraints molecularConstraints,
                                String name) {
        NoeSet noeSet = new NoeSet(molecularConstraints,
                name);
        return noeSet;
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
        List<Noe> noeList = peakMap.get(peak);
        if (noeList == null) {
            noeList = new ArrayList<>();
            peakMap.put(peak, noeList);
        }
        return noeList;
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

    @Override
    public void resetWriting() {
        memberId = -1;
        lastPeakWritten = null;
        id = 0;
    }

    public void writeNMRFxFile(File file) throws IOException {
        Map<Peak, Integer> peakMap = new HashMap<>();
        try (FileWriter fileWriter = new FileWriter(file)) {
            int i = 0;
            for (Noe noe : constraints) {
                if (noe.isActive()) {
                    Integer iGroup = peakMap.computeIfAbsent(noe.getPeak(), peak -> peakMap.size());
                    double lower = noe.getLower();
                    double upper = noe.getUpper();
                    SpatialSetGroup spg1 = noe.spg1;
                    SpatialSetGroup spg2 = noe.spg2;
                    String aName1 = spg1.getFullName();
                    String aName2 = spg2.getFullName();
                    String outputString = String.format("%d\t%d\t%s\t%s\t%.3f\t%.3f\n", i, iGroup, aName1, aName2, lower, upper);
                    fileWriter.write(outputString);
                    i++;
                }
            }
        }
    }


}
