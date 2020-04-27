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
package org.nmrfx.structure.chemistry.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.star.ParseException;
import org.nmrfx.processor.star.STAR3;
import org.nmrfx.processor.star.Saveframe;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.AngleBoundary;
import org.nmrfx.structure.chemistry.energy.AtomDistancePair;
import org.nmrfx.structure.chemistry.energy.Dihedral;
import org.nmrfx.structure.chemistry.energy.DistancePair;
import org.nmrfx.structure.chemistry.energy.EnergyLists;

/**
 *
 * @author brucejohnson, Martha
 */
public class NMRNEFWriter {

    static STAR3 nef = null;

    static void setNEF(STAR3 nefObj) {
        nef = nefObj;
    }

    static void writeMetaData(FileWriter chan) throws IOException, ParseException {
        String frameName = "save_nef_nmr_meta_data";
        chan.write(frameName + "\n");
        chan.write("    _nef_nmr_meta_data.sf_category           "); //fixme figure out why sf_category isn't in the tag list
        chan.write("nef_nmr_meta_data\n");
        Saveframe frame = nef.getSaveframe(frameName);
        String categoryName = frame.getCategories().get(0);
        List<String> tags = frame.getCategory(categoryName).getTags();
        for (String tag : tags) {
            String value = frame.getValue(categoryName, tag);
            String result = String.format("    %s.%-21s %s", categoryName, tag, value);
            chan.write(result + "\n");
        }
        chan.write("save_" + "\n");
    }

    static void writeMolSys(FileWriter chan) throws IOException, ParseException, InvalidMoleculeException {
        String frameName = "save_nef_molecular_system";
        chan.write("\n\n\n" + frameName + "\n");
        chan.write("    _nef_molecular_system.sf_category   ");
        chan.write("nef_molecular_system\n");
        Saveframe frame = nef.getSaveframe(frameName);
        String categoryName = frame.getCategories().get(0);
        List<String> tags = frame.getCategory(categoryName).getTags();
        for (String tag : tags) {
            String value = frame.getValue(categoryName, tag);
            String result = String.format("    %s.%-13s %s", categoryName, tag, value);
            chan.write(result + "\n");
        }

        chan.write("\n    loop_\n");
        String loopName = "_nef_sequence";
        List<String> loopStrings = frame.getLoopTags(loopName);
        for (String loopString : loopStrings) {
            chan.write("          " + loopName + "." + loopString + "\n");
        }
        chan.write("\n\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        int prevIdx = 0;
        for (Atom atom : molecule.getAtomArray()) {
            int idx = atom.getEntity().getIDNum();
            if (idx != prevIdx && prevIdx < idx) {
                String result = atom.toNEFSequenceString(molecule);
                if (result != null) {
                    chan.write(result + "\n");
                }
                prevIdx = idx;
            }
        }
        chan.write("    stop_\n");
        chan.write("save_\n");
    }

    static void writePPM(FileWriter chan) throws IOException, ParseException, InvalidMoleculeException {
        String frameName = nef.getSaveFrameNames().get(4);
        chan.write("\n" + frameName + "\n");
        chan.write("    _nef_chemical_shift_list.sf_category                ");
        chan.write("nef_chemical_shift_list\n");
        Saveframe frame = nef.getSaveframe(frameName);
        String categoryName = frame.getCategories().get(0);
        List<String> tags = frame.getCategory(categoryName).getTags();
        for (String tag : tags) {
            String value = frame.getValue(categoryName, tag);
            String result = String.format("    %s.%-26s %s", categoryName, tag, value);
            chan.write(result + "\n");
        }

        int i;
        chan.write("\n    loop_\n");
        String loopName = "_nef_chemical_shift";
        List<String> loopStrings = frame.getLoopTags(loopName);
        for (String loopString : loopStrings) {
            chan.write("         " + loopName + "." + loopString + "\n");
        }
        chan.write("\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        int iPPM = 0;
        i = 0;
        molecule.updateAtomArray();
        Comparator<Atom> aCmp = (Atom atom1, Atom atom2) -> { //sort by chain code
            int entityID1 = atom1.getTopEntity().entityID;
            int entityID2 = atom2.getTopEntity().entityID;
            int result = Integer.compare(entityID1, entityID2);
            if (result == 0) { // sort by sequence code
                entityID1 = atom1.getEntity().getIDNum();
                entityID2 = atom2.getEntity().getIDNum();
                result = Integer.compare(entityID1, entityID2);
                if (result == 0) { // sort by atomic number
                    int aNum1 = atom1.getAtomicNumber();
                    int aNum2 = atom2.getAtomicNumber();
                    result = Integer.compare(aNum1, aNum2);
                    if (result == 0) { // sort by atom name
                        result = atom1.getName().compareTo(atom2.getName());
                    }
                }
            }
            return result;
        };

        List<Atom> atomArray = molecule.getAtomArray();
        Collections.sort(atomArray, aCmp);

        for (Atom atom : atomArray) {
            String result = atom.ppmToNEFString(iPPM, i);
            if (result != null) {
//                    System.out.println("writer writePPM: iPPM = " + iPPM + " i = " + i);
                chan.write(result + "\n");
                i++;
            }
        }
        chan.write("    stop_\n");
        chan.write("save_\n");
    }

    static void writeDistances(FileWriter chan) throws IOException, ParseException, InvalidMoleculeException {
        String frameName = nef.getSaveFrameNames().get(6);
        chan.write("\n" + frameName + "\n");
        chan.write("    _nef_distance_restraint_list.sf_category       ");
        chan.write("nef_distance_restraint_list\n");
        Saveframe frame = nef.getSaveframe(frameName);
        String categoryName = frame.getCategories().get(0);
        List<String> tags = frame.getCategory(categoryName).getTags();
        for (String tag : tags) {
            String value = frame.getValue(categoryName, tag);
            String result = String.format("    %s.%-17s %s", categoryName, tag, value);
            chan.write(result + "\n");
        }

        chan.write("\n     loop_\n");
        String loopName = "_nef_distance_restraint";
        List<String> loopStrings = frame.getLoopTags(loopName);
        for (String loopString : loopStrings) {
            chan.write("         " + loopName + "." + loopString + "\n");
        }
        chan.write("\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        EnergyLists eLists = NMRNEFReader.energyList;
        List<DistancePair> distList = eLists.getDistanceList();
        int idx = 1;
        for (int i = 0; i < distList.size(); i++) {
            DistancePair distPair = distList.get(i);
            AtomDistancePair[] pairAtoms = distPair.getAtomPairs();
            for (AtomDistancePair pair : pairAtoms) {
                Atom[] a1List = pair.getAtoms1();
                Atom[] a2List = pair.getAtoms2();
                Atom atom1 = a1List[0];
                Atom atom2 = a2List[0];
                String result = Atom.toNEFDistanceString(idx, distPair, atom1, atom2);
                if (result != null) {
                    chan.write(result + "\n");
                    idx++;
                }
            }
        }
        chan.write("     stop_\n");
        chan.write("save_\n");
    }

    static void writeDihedrals(FileWriter chan) throws IOException, ParseException, InvalidMoleculeException {
        String frameName = nef.getSaveFrameNames().get(8);
        chan.write("\n" + frameName + "\n");
        chan.write("    _nef_dihedral_restraint_list.sf_category       ");
        chan.write("nef_dihedral_restraint_list\n");
        Saveframe frame = nef.getSaveframe(frameName);
        String categoryName = frame.getCategories().get(0);
        List<String> tags = frame.getCategory(categoryName).getTags();
        for (String tag : tags) {
            String value = frame.getValue(categoryName, tag);
            String result = String.format("    %s.%-17s %s", categoryName, tag, value);
            chan.write(result + "\n");
        }

        chan.write("\n     loop_\n");
        String loopName = "_nef_dihedral_restraint";
        List<String> loopStrings = frame.getLoopTags(loopName);
        for (String loopString : loopStrings) {
            chan.write("         " + loopName + "." + loopString + "\n");
        }
        chan.write("\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        Dihedral dihedral = NMRNEFReader.dihedral;
        Map<Integer, AngleBoundary> angleBoundsMap = dihedral.getAngleBoundariesNEF();
        Comparator<AngleBoundary> angleCmp = (AngleBoundary bound1, AngleBoundary bound2) -> {
            int restraintID1 = bound1.getRestraintID();
            int restraintID2 = bound2.getRestraintID();
            int result = Integer.compare(restraintID1, restraintID2);
            return result;
        };
        List<AngleBoundary> angleBounds = new ArrayList(angleBoundsMap.values());
        Collections.sort(angleBounds, angleCmp);
        int i = 1;
        for (AngleBoundary bound : angleBounds) {
            Atom[] atoms = bound.getAtoms();
            String result = Atom.toNEFDihedralString(bound, atoms, i);
            if (result != null) {
                chan.write(result + "\n");
                i++;
            }
        }
        chan.write("    stop_\n");
        chan.write("save_\n");
    }

    public static void writeAll(String fileName) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(fileName)) {
            System.out.println("wrote " + fileName);
            writeAll(writer);
        }
    }

    public static void writeAll(File file) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(file)) {
            writeAll(writer);
        }
    }

    public static void writeAll(FileWriter chan) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        Date date = new Date(System.currentTimeMillis());
        chan.write("    ######################################\n");
        chan.write("    # Saved " + date.toString() + " #\n");
        chan.write("    ######################################\n");
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            writeMetaData(chan);
            writeMolSys(chan);
            writePPM(chan);
            writeDistances(chan);
            writeDihedrals(chan);
        }
    }

}
