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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.star.ParseException;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.AtomDistancePair;
import org.nmrfx.structure.chemistry.energy.DistancePair;
import org.nmrfx.structure.chemistry.energy.EnergyLists;

/**
 *
 * @author brucejohnson, Martha
 */
public class NMRNEFWriter {

    private static final String[] metaDataSaveStrings = {"_nef_nmr_meta_data.sf_category", "_nef_nmr_meta_data.sf_framecode", "_nef_nmr_meta_data.format_name", "_nef_nmr_meta_data.format_version", "_nef_nmr_meta_data.program_name", "_nef_nmr_meta_data.program_version", "_nef_nmr_meta_data.creation_date", "_nef_nmr_meta_data.uuid", "_nef_nmr_meta_data.coordinate_file_name"};
    private static final String[] molecularSystemSaveStrings = {"_nef_molecular_system.sf_category", "_nef_molecular_system.sf_framecode"};
    private static final String[] sequenceLoopStrings = {"_nef_sequence.index", "_nef_sequence.chain_code", "_nef_sequence.sequence_code", "_nef_sequence.residue_name", "_nef_sequence.linking", "_nef_sequence.residue_variant"};
    private static final String[] chemShiftSaveStrings = {"_nef_chemical_shift_list.sf_category", "_nef_chemical_shift_list.sf_framecode"};
    private static final String[] chemShiftLoopStrings = {"_nef_chemical_shift.chain_code", "_nef_chemical_shift.sequence_code", "_nef_chemical_shift.residue_name", "_nef_chemical_shift.atom_name", "_nef_chemical_shift.value", "_nef_chemical_shift.value_uncertainty"};
    private static final String[] distanceRestraintSaveStrings = {"_nef_distance_restraint_list.sf_category", "_nef_distance_restraint_list.sf_framecode", "_nef_distance_restraint_list.potential_type", "_nef_distance_restraint_list.restraint_origin"};
    private static final String[] distanceRestraintLoopStrings = {"_nef_distance_restraint.index", "_nef_distance_restraint.restraint_id", "_nef_distance_restraint.restraint_combination_id", "_nef_distance_restraint.chain_code_1", "_nef_distance_restraint.sequence_code_1", "_nef_distance_restraint.residue_name_1", "_nef_distance_restraint.atom_name_1", "_nef_distance_restraint.chain_code_2", "_nef_distance_restraint.sequence_code_2", "_nef_distance_restraint.residue_name_2", "_nef_distance_restraint.atom_name_2", "_nef_distance_restraint.weight", "_nef_distance_restraint.target_value", "_nef_distance_restraint.target_value_uncertainty", "_nef_distance_restraint.lower_limit", "_nef_distance_restraint.upper_limit"};
    private static final String[] dihedralRestraintSaveStrings = {"_nef_dihedral_restraint_list.sf_category", "_nef_dihedral_restraint_list.sf_framecode", "_nef_dihedral_restraint_list.potential_type", "_nef_dihedral_restraint_list.restraint_origin"};
    private static final String[] dihedralRestraintLoopStrings = {"_nef_dihedral_restraint.index", "_nef_dihedral_restraint.restraint_id", "_nef_dihedral_restraint.restraint_combination_id", "_nef_dihedral_restraint.chain_code_1", "_nef_dihedral_restraint.sequence_code_1", "_nef_dihedral_restraint.residue_name_1", "_nef_dihedral_restraint.atom_name_1", "_nef_dihedral_restraint.chain_code_2", "_nef_dihedral_restraint.sequence_code_2", "_nef_dihedral_restraint.residue_name_2", "_nef_dihedral_restraint.atom_name_2", "_nef_dihedral_restraint.chain_code_3", "_nef_dihedral_restraint.sequence_code_3", "_nef_dihedral_restraint.residue_name_3", "_nef_dihedral_restraint.atom_name_3", "_nef_dihedral_restraint.chain_code_4", "_nef_dihedral_restraint.sequence_code_4", "_nef_dihedral_restraint.residue_name_4", "_nef_dihedral_restraint.atom_name_4", "_nef_dihedral_restraint.weight", "_nef_dihedral_restraint.target_value", "_nef_dihedral_restraint.target_value_uncertainty", "_nef_dihedral_restraint.lower_limit", "_nef_dihedral_restraint.upper_limit", "_nef_dihedral_restraint.name"};

    public static void writeMolSys(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n\n");
        chan.write("save_nef_molecular_system\n");
        chan.write("    _nef_molecular_system.sf_category   ");
        chan.write("nef_molecular_system\n");
        chan.write("    _nef_molecular_system.sf_framecode  ");
        chan.write("nef_molecular_system\n");
        chan.write("\n");

        chan.write("\tloop_\n");
        for (String loopString : sequenceLoopStrings) {
            chan.write("\t\t  " + loopString + "\n");
        }
        chan.write("\n\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        int prevIdx = 0;
        String link = "start";
        for (Atom atom : molecule.getAtomArray()) {
            int idx = atom.getEntity().getIDNum();
            if (idx != prevIdx && prevIdx < idx) {
                if (idx > 0) {
                    link = "middle"; //fixme needs to be "end" for the last residue
                }
                String result = atom.toNEFSequenceString(link);
                if (result != null) {
                    chan.write(result + "\n");
                }
                prevIdx = idx;
            }
        }
        chan.write("\tstop_\n");
        chan.write("save_\n");
    }

    public static void writePPM(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n");
        chan.write("save_nef_chemical_shift_list_1pqx.mr\n"); //fixme dynamically get framecode
        chan.write("    _nef_chemical_shift_list.sf_category   ");
        chan.write("nef_chemical_shift_list\n");
        chan.write("    _nef_chemical_shift_list.sf_framecode  ");
        chan.write("nef_chemical_shift_list_1pqx.mr\n"); //fixme dynamically get framecode
        chan.write("\n");

        int i;
        chan.write("\tloop_\n");
        for (String loopString : chemShiftLoopStrings) {
            chan.write("\t\t  " + loopString + "\n");
        }
        chan.write("\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        int iPPM = 0;
        i = 0;
        molecule.updateAtomArray();
        Comparator<Atom> aCmp = new Comparator<Atom>() {
            @Override
            public int compare(Atom atom1, Atom atom2) { //sort by chain code
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
            }
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
        chan.write("\tstop_\n");
        chan.write("save_\n");
    }

    public static void writeDistances(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n");
        chan.write("save_nef_distance_restraint_list_1pqx.mr\n"); //fixme dynamically get framecode
        chan.write("    _nef_distance_restraint_list.sf_category       ");
        chan.write("nef_distance_restraint_list\n");
        chan.write("    _nef_distance_restraint_list.sf_framecode      ");
        chan.write("nef_distance_restraint_list_1pqx.mr\n"); //fixme dynamically get framecode
        chan.write("    _nef_distance_restraint_list.potential_type    ");
        chan.write(".\n");
        chan.write("    _nef_distance_restraint_list.restraint_origin  ");
        chan.write("noe\n");
        chan.write("\n");

        chan.write("\tloop_\n");
        for (String loopString : distanceRestraintLoopStrings) {
            chan.write("\t\t  " + loopString + "\n");
        }
        chan.write("\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        EnergyLists eLists = NMRNEFReader.energyList;
        List<DistancePair> distList = eLists.distanceList2;
        int idx = 1;
        Atom prevAtom1 = null;
        Atom prevAtom2 = null;
        for (int i = 0; i < distList.size(); i++) {
            DistancePair distPair = distList.get(i);
            AtomDistancePair[] pairAtoms = distPair.getAtomPairs();
            for (AtomDistancePair pair : pairAtoms) {
                Atom[] a1List = pair.getAtoms1();
                Atom[] a2List = pair.getAtoms2();
                for (Atom atom1 : a1List) {
//                    System.out.println("a1: " + atom1);
                    for (Atom atom2 : a2List) {
//                        System.out.println("a2: " + atom2);
                        String result = atom1.toNEFDistanceString(idx, distPair, atom2, prevAtom1, prevAtom2);
                        if (result != null) {
                            prevAtom1 = atom1;
                            prevAtom2 = atom2;
                            chan.write(result + "\n");
                            idx++;
                        }
                    }
                }
            }
        }
        chan.write("\tstop_\n");
        chan.write("save_\n");
    }

    public static void writeAll(String fileName) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(fileName)) {
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
            writeMolSys(chan);
            writePPM(chan);
            writeDistances(chan);
//            writeMoleculeSTAR3(chan, molecule, 1);
        }
//        // fixme Dataset.writeDatasetsToSTAR3(channelName);
//        Iterator iter = PeakList.iterator();
//        PeakWriter peakWriter = new PeakWriter();
//        while (iter.hasNext()) {
//            PeakList peakList = (PeakList) iter.next();
//            peakWriter.writePeaksSTAR3(chan, peakList);
//        }
//
//        AtomResonanceFactory resFactory = (AtomResonanceFactory) PeakDim.resFactory;
//
//        resFactory.writeResonancesSTAR3(chan);
//        if (molecule != null) {
//            int ppmSetCount = molecule.getPPMSetCount();
//            for (int iSet = 0; iSet < ppmSetCount; iSet++) {
////                writeAssignmentsSTAR3(chan, iSet);
//            }
//            CoordinateSTARWriter.writeToSTAR3(chan, molecule, 1);
//            int setNum = 1;
//            for (ConstraintSet cSet : NoeSet.getSets()) {
//                if (cSet.getSize() > 0) {
//                    ConstraintSTARWriter.writeConstraintsSTAR3(chan, cSet, setNum++);
//                }
//            }
//            ConstraintSet cSet = AngleConstraint.getActiveSet();
//            setNum = 1;
//            if (cSet.getSize() > 0) {
//                ConstraintSTARWriter.writeConstraintsSTAR3(chan, cSet, setNum++);
//            }
//        }
//        PeakPathWriter pathWriter = new PeakPathWriter();
//        int iPath = 0;
//        for (PeakPath peakPath : PeakPath.get()) {
//            pathWriter.writeToSTAR3(chan, peakPath, iPath);
//        }
    }

}
