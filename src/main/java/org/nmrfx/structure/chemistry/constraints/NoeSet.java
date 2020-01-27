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

/**
 *
 * @author brucejohnson
 */
public class NoeSet implements ConstraintSet, Iterable {

    public static final HashMap<String, NoeSet> NOE_SETS = new HashMap<String, NoeSet>();
    private final List<Noe> constraints = new ArrayList<>(64);
    private final Map<Peak, ArrayList<Noe>> peakMap = new TreeMap<>();
    private final Map<PeakList, NoeCalibration> scaleMap = new HashMap<>();
    private final String name;
    public static Peak lastPeakWritten = null;
    public static int memberID = 0;
    public static int ID = 0;

    public NoeSet(String name) {
        this.name = name;
    }

    public static NoeSet addSet(String name) {
        NoeSet noeSet = new NoeSet(name);
        NOE_SETS.put(name, noeSet);
        Noe.setActive(noeSet);
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

    public void add(Constraint noe) {
        constraints.add((Noe) noe);
    }

    public List<Noe> get() {
        return constraints;
    }

    public Noe get(int i) {
        return constraints.get(i);
    }

    public Iterator iterator() {
        return constraints.iterator();
    }

    public ArrayList<Noe> getConstraintsForPeak(Peak peak) {
        ArrayList<Noe> peakList = peakMap.get(peak);
        if (peakList == null) {
            peakList = new ArrayList<Noe>();
            peakMap.put(peak, peakList);
        }
        return peakList;
    }

    public Set<Entry<Peak, ArrayList<Noe>>> getPeakMapEntries() {
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
        return Noe.isDirty();
    }

    public void setDirty() {
        Noe.setDirty();
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
}
