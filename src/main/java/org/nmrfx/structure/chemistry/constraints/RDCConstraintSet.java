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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import org.nmrfx.project.StructureProject;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Point3;
import java.util.*;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.structure.chemistry.Atom;

/**
 *
 * @author brucejohnson
 */
public class RDCConstraintSet implements ConstraintSet, Iterable {

    private static HashMap<String, RDCConstraintSet> rdcSets () {
        return StructureProject.getActive().rdcSets;
    }
    private static RDCConstraintSet activeSet () {
        return StructureProject.getActive().activeRDCSet;
    }
    private ArrayList<RDC> constraints = new ArrayList<>(64);
    int nStructures = 0;
    private final String name;
    boolean dirty = true;
    public static char[] violCharArray = new char[0];
    public static int ID = 1;

    public RDCConstraintSet(String name) {
        this.name = name;
    }

    public static RDCConstraintSet addSet(String name) {
        RDCConstraintSet rdcSet = new RDCConstraintSet(name);
        rdcSets().put(name, rdcSet);
        StructureProject.getActive().activeRDCSet = rdcSet;
        return rdcSet;

    }

    public String getName() {
        return name;
    }

    public static void reset() {
        for (Map.Entry<String, RDCConstraintSet> cSet : rdcSets().entrySet()) {
            cSet.getValue().clear();
        }
        rdcSets().clear();
        addSet("default");
    }

    public String getCategory() {
        return "torsion_angle_constraints";
    }

    public String getListType() {
        return "_RDC_list";
    }

    public String getType() {
        return "RDC";
    }

    public static RDCConstraintSet getSet(String name) {
        RDCConstraintSet noeSet = rdcSets().get(name);
        return noeSet;
    }

    public static ArrayList<String> getNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (String name : rdcSets().keySet()) {
            names.add(name);
        }
        return names;
    }

    public int getSize() {
        return constraints.size();
    }

    public void clear() {
        constraints.clear();
    }

    public void add(Constraint constraint) {
        constraints.add((RDC) constraint);
        dirty = true;
    }
    
    public ArrayList<RDC> get() {
        if (dirty) {
            updateAngleData();
        }
        return constraints;
    }

    public RDC get(int i) {
        return constraints.get(i);
    }
    
    public void remove(int i) {
        constraints.remove(i);
    }

    public void add(int i, Constraint constraint) {
        constraints.add(i, (RDC) constraint);
        dirty = true;
    }
    
    public Iterator iterator() {
        return constraints.iterator();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty() {
        dirty = true;
    }

    public void updateAngleData() {
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
        nStructures = structures.length;
        violCharArray = new char[lastStruct + 1];
        SummaryStatistics sumStat = new SummaryStatistics();
        for (RDC aConstraint : constraints) {
            sumStat.clear();
            int nInBounds = 0;
            BitSet violStructures = new BitSet(nStructures);
            Point3[] pts = new Point3[2];
            boolean okPoint = true;
            for (int iStruct : structures) {
                for (int i = 0; i < pts.length; i++) {
                    pts[i] = aConstraint.getSpSets()[i].getPoint(iStruct);
                    if (pts[i] == null) {
                        System.out.println(i + " " + aConstraint.getSpSets()[i].getFullName() + " has null point");
                        okPoint = false;
                    }
                }
                if (!okPoint) {
                    break;
                }
//                double angle = Atom.calcDihedral(pts[0], pts[1], pts[2], pts[3]) * 180.0 / Math.PI;
//                double value = aConstraint.getValue();
//                double err = aConstraint.getErr();
//                boolean ok = false;
//                if (upper > lower) {
//                    if ((angle >= lower) && (angle <= upper)) {
//                        ok = true;
//                    }
//                } else if ((angle >= lower) || (angle <= upper)) {
//                    ok = true;
//                }
//                if (ok) {
//                    nInBounds++;
//                } else {
//                    violStructures.set(iStruct);
//                }
//                sumStat.addValue(angle);
            }
            if (okPoint) {

                double stdDevDis = Math.sqrt(sumStat.getVariance());
                DistanceStat dStat = new DistanceStat(sumStat.getMin(), sumStat.getMax(), sumStat.getMean(), stdDevDis, (double) nInBounds / nStructures, violStructures);
                aConstraint.setDisStat(dStat);
            }

        }
        dirty = false;
    }
    private static String[] rdcConstraintLoopStrings = {
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

    public String[] getLoopStrings() {
        return rdcConstraintLoopStrings;
    }

    public void resetWriting() {
        ID = 1;
    }

    public void readInputFile(File file) throws IOException {
        constraints.clear();
        BufferedReader bf = new BufferedReader(new FileReader(file));
        LineNumberReader lineReader = new LineNumberReader(bf);
        while (true) {
            String line = lineReader.readLine();
            if (line == null) {
                break;
            }
            String sline = line.trim();
            String[] sfields = sline.split("\t");
            System.out.println("nfields " + sfields.length);
            if (sfields.length == 4) {
                Atom atom1 = Molecule.getAtomByName(sfields[0]);
                Atom atom2 = Molecule.getAtomByName(sfields[1]);
                double value = Double.parseDouble(sfields[2]);
                double err = Double.parseDouble(sfields[3]);
                RDC rdc = new RDC(this, atom1.getSpatialSet(), atom2.getSpatialSet(), value, err);
                constraints.add(rdc);
            } else if (sfields.length == 6) {
                Atom atom1 = Molecule.getAtomByName(sfields[0] + "." + sfields[1]);
                Atom atom2 = Molecule.getAtomByName(sfields[2] + "." + sfields[3]);
                double value = Double.parseDouble(sfields[4]);
                double err = Double.parseDouble(sfields[5]);
                RDC rdc = new RDC(this, atom1.getSpatialSet(), atom2.getSpatialSet(), value, err);
                constraints.add(rdc);
                System.out.println("add con " + getSize() + " " + atom1.getShortName() + " " + atom2.getShortName() + " " + value);
            }
        }
    }
}
