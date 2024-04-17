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

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MolFilter;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.SpatialSetGroup;
import org.nmrfx.peaks.Peak;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author brucejohnson
 */
public class DistanceConstraintSet implements ConstraintSet, Iterable {

    private static final String[] angleConstraintLoopStrings = {
            "_Torsion_angle_constraint.ID",
            "_Torsion_angle_constraint.Torsion_angle_name",
            "_Torsion_angle_constraint.Assembly_atom_ID_1",
            "_Torsion_angle_constraint.Entity_assembly_ID_1",
            "_Torsion_angle_constraint.Entity_ID_1",
            "_Torsion_angle_constraint.Comp_index_ID_1",
            "_Torsion_angle_constraint.Seq_ID_1",
            "_Torsion_angle_constraint.Comp_ID_1",
            "_Torsion_angle_constraint.Atom_ID_1",
            "_Torsion_angle_constraint.Atom_type_1",
            "_Torsion_angle_constraint.Resonance_ID_1",
            "_Torsion_angle_constraint.Assembly_atom_ID_2",
            "_Torsion_angle_constraint.Entity_assembly_ID_2",
            "_Torsion_angle_constraint.Entity_ID_2",
            "_Torsion_angle_constraint.Comp_index_ID_2",
            "_Torsion_angle_constraint.Seq_ID_2",
            "_Torsion_angle_constraint.Comp_ID_2",
            "_Torsion_angle_constraint.Atom_ID_2",
            "_Torsion_angle_constraint.Atom_type_2",
            "_Torsion_angle_constraint.Resonance_ID_2",
            "_Torsion_angle_constraint.Assembly_atom_ID_3",
            "_Torsion_angle_constraint.Entity_assembly_ID_3",
            "_Torsion_angle_constraint.Entity_ID_3",
            "_Torsion_angle_constraint.Comp_index_ID_3",
            "_Torsion_angle_constraint.Seq_ID_3",
            "_Torsion_angle_constraint.Comp_ID_3",
            "_Torsion_angle_constraint.Atom_ID_3",
            "_Torsion_angle_constraint.Atom_type_3",
            "_Torsion_angle_constraint.Resonance_ID_3",
            "_Torsion_angle_constraint.Assembly_atom_ID_4",
            "_Torsion_angle_constraint.Entity_assembly_ID_4",
            "_Torsion_angle_constraint.Entity_ID_4",
            "_Torsion_angle_constraint.Comp_index_ID_4",
            "_Torsion_angle_constraint.Seq_ID_4",
            "_Torsion_angle_constraint.Comp_ID_4",
            "_Torsion_angle_constraint.Atom_ID_4",
            "_Torsion_angle_constraint.Atom_type_4",
            "_Torsion_angle_constraint.Resonance_ID_4",
            "_Torsion_angle_constraint.Angle_lower_bound_val",
            "_Torsion_angle_constraint.Angle_upper_bound_val",
            "_Torsion_angle_constraint.Entry_ID",
            "_Torsion_angle_constraint.Gen_dist_constraint_list_ID",};

    private final MolecularConstraints molecularConstraints;
    private final ArrayList<DistanceConstraint> constraints = new ArrayList<>();
    int nStructures = 0;
    private final String name;
    boolean dirty = true;
    public static char[] violCharArray = new char[0];
    public static int ID = 1;
    boolean containsBonds = false;

    private DistanceConstraintSet(MolecularConstraints molecularConstraints,
                                  String name) {
        this.name = name;
        this.molecularConstraints = molecularConstraints;
    }

    public static DistanceConstraintSet newSet(MolecularConstraints molecularConstraints,
                                               String name) {
        DistanceConstraintSet distanceSet = new DistanceConstraintSet(molecularConstraints,
                name);
        return distanceSet;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCategory() {
        return "distance_constraints";
    }

    @Override
    public String getListType() {
        return "_Distance_constraint_list";
    }

    @Override
    public String getType() {
        return "distance constraint";
    }

    @Override
    public int getSize() {
        return constraints.size();
    }

    @Override
    public void clear() {
        constraints.clear();
    }

    @Override
    public void add(Constraint constraint) {
        constraints.add((DistanceConstraint) constraint);
        dirty = true;
    }

    public ArrayList<DistanceConstraint> get() {
        if (dirty) {
            updateData();
        }
        return constraints;
    }

    @Override
    public DistanceConstraint get(int i) {
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

    public void updateData() {
        MoleculeBase mol = molecularConstraints.molecule;
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
        nStructures = structures.length;
        violCharArray = new char[lastStruct + 1];
        SummaryStatistics sumStat = new SummaryStatistics();
        constraints.forEach((_item) -> {
            sumStat.clear();
        });
        dirty = false;
    }

    @Override
    public String[] getLoopStrings() {
        return angleConstraintLoopStrings;
    }

    @Override
    public void resetWriting() {
        ID = 1;
    }

    public void addDistanceConstraint(final String filterString1, final String filterString2, final double rLow,
                                      final double rUp) throws IllegalArgumentException {
        addDistanceConstraint(filterString1, filterString2, rLow, rUp, false, 1.0, null, null);
    }

    public void addDistanceConstraint(final String filterString1, final String filterString2, final double rLow,
                                      final double rUp, boolean isBond) throws IllegalArgumentException {
        addDistanceConstraint(filterString1, filterString2, rLow, rUp, isBond, 1.0, null, null);
    }

    public void addDistanceConstraint(final String filterString1, final String filterString2, final double rLow,
                                      final double rUp, Double weight, Double targetValue, Double targetErr) throws IllegalArgumentException {
        addDistanceConstraint(filterString1, filterString2, rLow, rUp, false, weight, targetValue, targetErr);
    }

    public void addDistanceConstraint(final String filterString1, final String filterString2, final double rLow,
                                      final double rUp, boolean isBond, Double weight, Double targetValue, Double targetErr) throws IllegalArgumentException {
        MolFilter molFilter1 = new MolFilter(filterString1);
        MolFilter molFilter2 = new MolFilter(filterString2);
        MoleculeBase molecule = molecularConstraints.molecule;

        ArrayList<Atom> atoms1 = MoleculeBase.getMatchedAtoms(molFilter1, molecule);
        ArrayList<Atom> atoms2 = MoleculeBase.getMatchedAtoms(molFilter2, molecule);

        if (atoms1.isEmpty()) {
            throw new IllegalArgumentException("atom null " + filterString1);
        }
        if (atoms2.isEmpty()) {
            throw new IllegalArgumentException("atom null " + filterString2);
        }

        ArrayList<Atom> atoms1m = new ArrayList<>();
        ArrayList<Atom> atoms2m = new ArrayList<>();

        atoms1.forEach((atom1) -> {
            atoms2.forEach((atom2) -> {
                atoms1m.add(atom1);
                atoms2m.add(atom2);
            });
        });

        Atom[] atomsA1 = new Atom[atoms1m.size()];
        Atom[] atomsA2 = new Atom[atoms2m.size()];
        atoms1m.toArray(atomsA1);
        atoms2m.toArray(atomsA2);
        if (weight != null && targetValue != null && targetErr != null) {
            add(new DistanceConstraint(atomsA1, atomsA2, rLow, rUp, isBond, weight, targetValue, targetErr));
        } else {
            add(new DistanceConstraint(atomsA1, atomsA2, rLow, rUp, isBond));
        }
    }

    public void addDistanceConstraint(final List<String> filterStrings1, final List<String> filterStrings2,
                                      final double rLow, final double rUp) throws IllegalArgumentException {
        addDistanceConstraint(filterStrings1, filterStrings2, rLow, rUp, false, 1.0, null, null);
    }

    public void addDistanceConstraint(final List<String> filterStrings1, final List<String> filterStrings2,
                                      final double rLow, final double rUp, boolean isBond) throws IllegalArgumentException {
        addDistanceConstraint(filterStrings1, filterStrings2, rLow, rUp, isBond, 1.0, null, null);
    }

    public void addDistanceConstraint(final List<String> filterStrings1, final List<String> filterStrings2,
                                      final double rLow, final double rUp, boolean isBond, Double weight, Double targetValue, Double targetErr) throws IllegalArgumentException {
        if (filterStrings1.size() != filterStrings2.size()) {
            throw new IllegalArgumentException("atoms group 1 and atoms group 2 should be same size");
        }
        MoleculeBase molecule = molecularConstraints.molecule;
        ArrayList<Atom> atoms1m = new ArrayList<>();
        ArrayList<Atom> atoms2m = new ArrayList<>();
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

            group1.forEach((atom1) -> {
                group2.forEach((atom2) -> {
                    atoms1m.add(atom1);
                    atoms2m.add(atom2);
                });
            });
        }
        Atom[] atomsA1 = new Atom[atoms1m.size()];
        Atom[] atomsA2 = new Atom[atoms2m.size()];
        if (atoms1m.size() != atoms2m.size()) {
            throw new IllegalArgumentException("atoms group 1 and atoms group 2 should be same size");
        }
        atoms1m.toArray(atomsA1);
        atoms2m.toArray(atomsA2);
        if (weight != null && targetValue != null && targetErr != null) {
            add(new DistanceConstraint(atomsA1, atomsA2, rLow, rUp, isBond, weight, targetValue, targetErr));
        } else {
            add(new DistanceConstraint(atomsA1, atomsA2, rLow, rUp, isBond));
        }

    }

    public void addDistance(final int modelNum, final List<String> filterStrings1, final List<String> filterStrings2,
                            final double rLow, final double rUp, Double weight, List<Double> targetValues, Double targetErr) throws IllegalArgumentException {
        if (filterStrings1.size() != filterStrings2.size()) {
            throw new IllegalArgumentException("atoms group 1 and atoms group 2 should be same size");
        }
        MoleculeBase molecule = molecularConstraints.molecule;
        ArrayList<Atom> atoms1m = new ArrayList<>();
        ArrayList<Atom> atoms2m = new ArrayList<>();
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

            group1.forEach((atom1) -> {
                group2.forEach((atom2) -> {
                    atoms1m.add(atom1);
                    atoms2m.add(atom2);
                });
            });
        }
        Atom[] atomsA1 = new Atom[atoms1m.size()];
        Atom[] atomsA2 = new Atom[atoms2m.size()];
        if (atoms1m.size() != atoms2m.size()) {
            throw new IllegalArgumentException("atoms group 1 and atoms group 2 should be same size");
        }
        atoms1m.toArray(atomsA1);
        atoms2m.toArray(atomsA2);
        List<DistanceConstraint> distList = new ArrayList<>();
        for (int i = 0; i < targetValues.size(); i++) {
            Atom[] atomsA1a = {atomsA1[i]};
            Atom[] atomsA2a = {atomsA2[i]};
            add(new DistanceConstraint(atomsA1a, atomsA2a, rLow, rUp, false, weight, targetValues.get(i), targetErr));
            // fixme  distancePairMap.put(modelNum, distList);
        }

    }
    public void writeNMRFxFile(File file) throws IOException {
        Map<Peak, Integer> peakMap = new HashMap<>();
        try (FileWriter fileWriter = new FileWriter(file)) {
            int i = 0;
            for (DistanceConstraint distanceConstraint : constraints) {
                    double lower = distanceConstraint.getLower();
                    double upper = distanceConstraint.getUpper();
                    Atom atom1 = distanceConstraint.getAtomPairs()[0].getAtoms1()[0];
                    Atom atom2 = distanceConstraint.getAtomPairs()[0].getAtoms1()[1];
                    String aName1 = atom1.getFullName();
                    String aName2 = atom2.getFullName();
                    String outputString = String.format("%d\t%d\t%s\t%s\t%.3f\t%.3f\n", i, i, aName1, aName2, lower, upper);
                    fileWriter.write(outputString);
                    i++;
            }
        }
    }

}
