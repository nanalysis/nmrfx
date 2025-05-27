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
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.Point3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author brucejohnson
 */
public class RDCConstraintSet implements ConstraintSet, Iterable {
    private static final String[] rdcConstraintLoopStrings = {
            "_RDC.ID",
            "_RDC.Entity_assembly_ID_1",
            "_RDC.Entity_ID_1",
            "_RDC.Comp_index_ID_1",
            "_RDC.Seq_ID_1",
            "_RDC.Comp_ID_1",
            "_RDC.Atom_ID_1",
            "_RDC.Atom_type_1",
            "_RDC.Resonance_ID_1",
            "_RDC.Entity_assembly_ID_2",
            "_RDC.Entity_ID_2",
            "_RDC.Comp_index_ID_2",
            "_RDC.Seq_ID_2",
            "_RDC.Comp_ID_2",
            "_RDC.Atom_ID_2",
            "_RDC.Atom_type_2",
            "_RDC.Resonance_ID_2",
            "_RDC.Val",
            "_RDC.Val_err",
            "_RDC.RDC_list_ID",};

    public static char[] violCharArray = new char[0];
    private final MolecularConstraints molecularConstraints;
    private final ArrayList<RDCConstraint> constraints = new ArrayList<>(64);
    int nStructures = 0;
    private final String name;
    boolean dirty = true;

    private RDCConstraintSet(MolecularConstraints molecularConstraints,
                             String name) {
        this.name = name;
        this.molecularConstraints = molecularConstraints;
    }

    public static RDCConstraintSet newSet(MolecularConstraints molecularConstraints,
                                          String name) {
        RDCConstraintSet rdcSet = new RDCConstraintSet(molecularConstraints,
                name);
        molecularConstraints.addRDCSet(rdcSet);
        return rdcSet;
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
        return "_RDC_list";
    }

    @Override
    public String getType() {
        return "RDC";
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
        constraints.add((RDCConstraint) constraint);
        dirty = true;
    }

    public List<RDCConstraint> get() {
        if (dirty) {
            updateAngleData();
        }
        return constraints;
    }

    @Override
    public RDCConstraint get(int i) {
        return constraints.get(i);
    }

    @Override
    public MolecularConstraints getMolecularConstraints() {
        return molecularConstraints;
    }

    public void remove(int i) {
        constraints.remove(i);
    }

    public void add(int i, Constraint constraint) {
        constraints.add(i, (RDCConstraint) constraint);
        dirty = true;
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

        MoleculeBase mol = null;
        if (molecularConstraints != null) {
            mol = molecularConstraints.molecule;
        }
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
        for (RDCConstraint aConstraint : constraints) {
            sumStat.clear();
            int nInBounds = 0;
            BitSet violStructures = new BitSet(nStructures);
            Point3[] pts = new Point3[2];
            boolean okPoint = true;
            for (int iStruct : structures) {
                for (int i = 0; i < pts.length; i++) {
                    pts[i] = aConstraint.getSpSets()[i].getPoint(iStruct);
                    if (pts[i] == null) {
                        okPoint = false;
                    }
                }
                if (!okPoint) {
                    break;
                }
            }
            if (okPoint) {

                double stdDevDis = Math.sqrt(sumStat.getVariance());
                DistanceStat dStat = new DistanceStat(sumStat.getMin(), sumStat.getMax(), sumStat.getMean(), stdDevDis, (double) nInBounds / nStructures, violStructures);
                aConstraint.setDisStat(dStat);
            }

        }
        dirty = false;
    }

    @Override
    public String[] getLoopStrings() {
        return rdcConstraintLoopStrings;
    }

    public void readInputFile(String fileName) throws IOException {
        File file = new File(fileName);
        readInputFile(file);
    }

    public void readInputFile(File file) throws IOException {
        constraints.clear();
        int iStructure = 0;
        try (Stream<String> lines = Files.lines(file.toPath())) {
            lines.forEach(line -> {
                String sline = line.trim();
                String[] sfields = sline.split("\t");
                if (sfields.length == 4) {
                    Atom atom1 = MoleculeBase.getAtomByName(sfields[0]);
                    Atom atom2 = MoleculeBase.getAtomByName(sfields[1]);
                    double value = Double.parseDouble(sfields[2]);
                    double err = Double.parseDouble(sfields[3]);
                    RDCConstraint rdc = new RDCConstraint(this, atom1, atom2, iStructure, value, err);
                    constraints.add(rdc);
                } else if (sfields.length == 6) {
                    Atom atom1 = MoleculeBase.getAtomByName(sfields[0] + "." + sfields[1]);
                    Atom atom2 = MoleculeBase.getAtomByName(sfields[2] + "." + sfields[3]);
                    double value = Double.parseDouble(sfields[4]);
                    double err = Double.parseDouble(sfields[5]);
                    RDCConstraint rdc = new RDCConstraint(this, atom1, atom2, iStructure, value, err);
                    constraints.add(rdc);
                } else if (sfields.length == 8) {
                    Atom atom1 = MoleculeBase.getAtomByName(sfields[0] + "." + sfields[2]);
                    Atom atom2 = MoleculeBase.getAtomByName(sfields[3] + "." + sfields[5]);
                    double value = Double.parseDouble(sfields[6]);
                    double err = Double.parseDouble(sfields[7]);
                    RDCConstraint rdc = new RDCConstraint(this, atom1, atom2, iStructure, value, err);
                    constraints.add(rdc);
                }
            });
        }
    }
}
