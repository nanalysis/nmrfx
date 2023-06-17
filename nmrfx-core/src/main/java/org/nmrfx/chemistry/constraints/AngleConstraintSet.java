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
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.Point3;

import java.util.*;

/**
 * @author brucejohnson
 */
public class AngleConstraintSet implements ConstraintSet, Iterable {
    public static char[] violCharArray = new char[0];
    public static int id = 1;

    private final MolecularConstraints molecularConstraints;
    private final ArrayList<AngleConstraint> constraints = new ArrayList<>();
    private final Map<String, Integer> map = new HashMap<>();
    int nStructures = 0;
    private final String name;
    boolean dirty = true;

    private AngleConstraintSet(MolecularConstraints molecularConstraints,
                               String name) {
        this.name = name;
        this.molecularConstraints = molecularConstraints;
    }

    public static AngleConstraintSet newSet(MolecularConstraints molecularConstraints,
                                            String name) {
        AngleConstraintSet angleSet = new AngleConstraintSet(molecularConstraints,
                name);
        return angleSet;
    }

    public void addAngleConstraint(Atom[] atoms, double lower, double upper, final double scale) throws InvalidMoleculeException {
        AngleConstraint angleConstraint = new AngleConstraint(atoms, lower, upper, scale, 1.0, (lower + upper) / 2.0, upper - lower, "");
        add(angleConstraint);
    }

    public void addAngleConstraint(Atom[] atoms, double lower, double upper, final double scale,
                                   Double weight, Double target, Double targetErr, String name) throws InvalidMoleculeException {
        AngleConstraint angleConstraint = new AngleConstraint(atoms, lower, upper, scale, weight, target, targetErr, name);
        add(angleConstraint);
    }

    public void add(AngleConstraint constraint) {
        constraint.idNum = getSize();
        String key = constraint.getRefAtom().getFullName();
        if (map.containsKey(key)) {
            int index = map.get(key);
            constraints.set(index, constraint);
        } else {
            constraints.add(constraint);
            map.put(key, constraints.size() - 1);
        }
        dirty = true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCategory() {
        return "torsion_angle_constraints";
    }

    @Override
    public String getListType() {
        return "_Torsion_angle_constraint_list";
    }

    @Override
    public String getType() {
        return "dihedral angle";
    }

    @Override
    public int getSize() {
        return constraints.size();
    }

    @Override
    public void clear() {
        constraints.clear();
        map.clear();
    }

    @Override
    public void add(Constraint constraint) {
        add((AngleConstraint) constraint);
    }

    public ArrayList<AngleConstraint> get() {
        if (dirty) {
            updateAngleData();
        }
        return constraints;
    }

    @Override
    public AngleConstraint get(int i) {
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

    public void updateAngleData() {
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
        for (AngleConstraint aConstraint : constraints) {
            sumStat.clear();
            int nInBounds = 0;
            BitSet violStructures = new BitSet(nStructures);
            Point3[] pts = new Point3[4];
            boolean okPoint = true;
            for (int iStruct : structures) {
                for (int i = 0; i < 4; i++) {
                    pts[i] = aConstraint.getSpSet(i).getPoint(iStruct);
                    if (pts[i] == null) {
                        okPoint = false;
                    }
                }
                if (!okPoint) {
                    break;
                }
                double angle = Atom.calcDihedral(pts[0], pts[1], pts[2], pts[3]) * 180.0 / Math.PI;
                double lower = aConstraint.getLower();
                double upper = aConstraint.getUpper();
                boolean ok = false;
                if (upper > lower) {
                    if ((angle >= lower) && (angle <= upper)) {
                        ok = true;
                    }
                } else if ((angle >= lower) || (angle <= upper)) {
                    ok = true;
                }
                if (ok) {
                    nInBounds++;
                } else {
                    violStructures.set(iStruct);
                }
                sumStat.addValue(angle);
            }
            if (okPoint) {

                double stdDevDis = Math.sqrt(sumStat.getVariance());
                DistanceStat dStat = new DistanceStat(sumStat.getMin(), sumStat.getMax(), sumStat.getMean(), stdDevDis, (double) nInBounds / nStructures, violStructures);
                aConstraint.setDisStat(dStat);
            }

        }
        dirty = false;
    }

    private final static String[] angleConstraintLoopStrings = {
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
            "_Torsion_angle_constraint.Atom_ID_3      _Torsion_angle_constraint.Atom_type_3",
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

    @Override
    public String[] getLoopStrings() {
        return angleConstraintLoopStrings;
    }

    @Override
    public void resetWriting() {
        id = 1;
    }
}
