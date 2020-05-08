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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.star.ParseException;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
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

    private static final String[] SEQUENCE_LOOP_STRINGS = {"_nef_sequence.index", "_nef_sequence.chain_code", "_nef_sequence.sequence_code", "_nef_sequence.residue_name", "_nef_sequence.linking", "_nef_sequence.residue_variant"};
    private static final String[] CHEM_SHIFT_LOOP_STRINGS = {"_nef_chemical_shift.chain_code", "_nef_chemical_shift.sequence_code", "_nef_chemical_shift.residue_name", "_nef_chemical_shift.atom_name", "_nef_chemical_shift.value", "_nef_chemical_shift.value_uncertainty"};
    private static final String[] DISTANCE_RESTRAINT_LOOP_STRINGS = {"_nef_distance_restraint.index", "_nef_distance_restraint.restraint_id", "_nef_distance_restraint.restraint_combination_id", "_nef_distance_restraint.chain_code_1", "_nef_distance_restraint.sequence_code_1", "_nef_distance_restraint.residue_name_1", "_nef_distance_restraint.atom_name_1", "_nef_distance_restraint.chain_code_2", "_nef_distance_restraint.sequence_code_2", "_nef_distance_restraint.residue_name_2", "_nef_distance_restraint.atom_name_2", "_nef_distance_restraint.weight", "_nef_distance_restraint.target_value", "_nef_distance_restraint.target_value_uncertainty", "_nef_distance_restraint.lower_limit", "_nef_distance_restraint.upper_limit"};
    private static final String[] DIHEDRAL_RESTRAINT_LOOP_STRINGS = {"_nef_dihedral_restraint.index", "_nef_dihedral_restraint.restraint_id", "_nef_dihedral_restraint.restraint_combination_id", "_nef_dihedral_restraint.chain_code_1", "_nef_dihedral_restraint.sequence_code_1", "_nef_dihedral_restraint.residue_name_1", "_nef_dihedral_restraint.atom_name_1", "_nef_dihedral_restraint.chain_code_2", "_nef_dihedral_restraint.sequence_code_2", "_nef_dihedral_restraint.residue_name_2", "_nef_dihedral_restraint.atom_name_2", "_nef_dihedral_restraint.chain_code_3", "_nef_dihedral_restraint.sequence_code_3", "_nef_dihedral_restraint.residue_name_3", "_nef_dihedral_restraint.atom_name_3", "_nef_dihedral_restraint.chain_code_4", "_nef_dihedral_restraint.sequence_code_4", "_nef_dihedral_restraint.residue_name_4", "_nef_dihedral_restraint.atom_name_4", "_nef_dihedral_restraint.weight", "_nef_dihedral_restraint.target_value", "_nef_dihedral_restraint.target_value_uncertainty", "_nef_dihedral_restraint.lower_limit", "_nef_dihedral_restraint.upper_limit", "_nef_dihedral_restraint.name"};

    static void writeMolSys(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n\n");
        chan.write("save_nef_molecular_system\n");
        chan.write("    _nef_molecular_system.sf_category   ");
        chan.write("nef_molecular_system\n");
        chan.write("    _nef_molecular_system.sf_framecode  ");
        chan.write("nef_molecular_system\n");
        chan.write("\n");

        chan.write("    loop_\n");
        for (String loopString : SEQUENCE_LOOP_STRINGS) {
            chan.write("         " + loopString + "\n");
        }
        chan.write("\n\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        Iterator entityIterator = molecule.entityLabels.values().iterator();
        while (entityIterator.hasNext()) {
            Entity entity = (Entity) entityIterator.next();
            if (entity instanceof Polymer) {
                List<Residue> resList = ((Polymer) entity).getResidues();
                Residue firstRes = ((Polymer) entity).getFirstResidue();
                Residue lastRes = ((Polymer) entity).getLastResidue();
                for (Residue res : resList) {
                    String link;
                    if (res.equals(firstRes)) {
                        link = "start";
                    } else if (res.equals(lastRes)) {
                        link = "end";
                    } else {
                        link = "middle";
                    }
                    String result = res.toNEFSequenceString(molecule, link, ".");
                    if (result != null) {
                        chan.write(result + "\n");
                    }
                }
            }
        }
        chan.write("    stop_\n");
        chan.write("save_\n");
    }

    static void writePPM(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n");
        chan.write("save_nef_chemical_shift_list_1pqx.mr\n"); //fixme dynamically get framecode
        chan.write("    _nef_chemical_shift_list.sf_category                ");
        chan.write("nef_chemical_shift_list\n");
        chan.write("    _nef_chemical_shift_list.sf_framecode               ");
        chan.write("nef_chemical_shift_list_1pqx.mr\n"); //fixme dynamically get framecode
        chan.write("\n");

        int i;
        chan.write("    loop_\n");
        for (String loopString : CHEM_SHIFT_LOOP_STRINGS) {
            chan.write("         " + loopString + "\n");
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
            boolean isFirstMethyl = atom.isFirstInMethyl();
            boolean collapse = false;
            boolean writeLine = true;
            if (isFirstMethyl) {
                collapse = true;
            } else if (atom.isMethyl() && !isFirstMethyl) {
                writeLine = false;
            }

            if (writeLine) {
                String result = atom.ppmToNEFString(iPPM, i, collapse);
                if (result != null) {
//                    System.out.println("writer writePPM: iPPM = " + iPPM + " i = " + i);
                    chan.write(result + "\n");
                    i++;
                }
            }
        }
        chan.write("    stop_\n");
        chan.write("save_\n");
    }

    static void writeDistances(FileWriter chan) throws IOException, InvalidMoleculeException {
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

        chan.write("     loop_\n");
        for (String loopString : DISTANCE_RESTRAINT_LOOP_STRINGS) {
            chan.write("         " + loopString + "\n");
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
        int restraintID = 1;
        String result;
        for (int i = 0; i < distList.size(); i++) {
            DistancePair distPair = distList.get(i);
            AtomDistancePair[] pairAtoms = distPair.getAtomPairs();
            Map<String, Set<Atom>> uniqA1Map = distPair.getUniqueAtoms(pairAtoms, 1);
            Map<String, Set<Atom>> uniqA2Map = distPair.getUniqueAtoms(pairAtoms, 2);
            for (AtomDistancePair pair : pairAtoms) {
                Atom atom1 = pair.getAtoms1()[0];
                Atom atom2 = pair.getAtoms2()[0];
                int[] polymerIDs = {((Residue) atom1.entity).polymer.entityID, ((Residue) atom2.entity).polymer.entityID};
                int[] seqCodes = {((Residue) atom1.entity).getIDNum(), ((Residue) atom2.entity).getIDNum()};
                String[] keys = {polymerIDs[0] + ":" + seqCodes[0], polymerIDs[1] + ":" + seqCodes[1]};
                Set<Atom> uniqA1 = uniqA1Map.get(keys[0]);
                Set<Atom> uniqA2 = uniqA2Map.get(keys[1]);
                boolean[] collapse = {false, false};
                boolean writeLine = false;
                if (uniqA1.size() == 1 && uniqA2.size() == 1) {
                    writeLine = true;
                    // if distPair has > 1 unique entry for atom1 or atom2, or both 
                    // (e.g. methyls, some methylenes), collapse into one line with 
                    // % in atom name(s)
                } else {
                    boolean[] isFirstMethyl = {atom1.isFirstInMethyl(), atom2.isFirstInMethyl()};
                    boolean[] isNonMethylHD = {!atom1.isMethyl() && atom1.name.contains("HD"), !atom2.isMethyl() && atom2.name.contains("HD")};
                    boolean a1WriteMethylene = ((atom1.isMethylene() || isNonMethylHD[0]) && uniqA1.size() > 1 && atom1.getStereo() == 1);
                    boolean a2WriteMethylene = ((atom2.isMethylene() || isNonMethylHD[1]) && uniqA2.size() > 1 && atom2.getStereo() == 1);
                    if ((isFirstMethyl[0] || a1WriteMethylene) && uniqA2.size() == 1) {
                        collapse[0] = true;
                        writeLine = true;
                    } else if ((isFirstMethyl[1] || a2WriteMethylene) && uniqA1.size() == 1) {
                        collapse[1] = true;
                        writeLine = true;
                    } else if ((isFirstMethyl[0] && isFirstMethyl[1]) || (a1WriteMethylene && a2WriteMethylene)
                            || (isFirstMethyl[0] && a2WriteMethylene) || (a1WriteMethylene && isFirstMethyl[1])) {
                        collapse[0] = true;
                        collapse[1] = true;
                        writeLine = true;
                    }
                }

                if (writeLine) {
                    result = Atom.toNEFDistanceString(idx, collapse, restraintID, ".", distPair, atom1, atom2);
                    chan.write(result + "\n");
                    idx++;
                }
            }
            restraintID++;
        }
        chan.write("     stop_\n");
        chan.write("save_\n");
    }

    static void writeDihedrals(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n");
        chan.write("save_nef_dihedral_restraint_list_1pqx.mr\n"); //fixme dynamically get framecode
        chan.write("    _nef_dihedral_restraint_list.sf_category       ");
        chan.write("nef_dihedral_restraint_list\n");
        chan.write("    _nef_dihedral_restraint_list.sf_framecode      ");
        chan.write("nef_dihedral_restraint_list_1pqx.mr\n"); //fixme dynamically get framecode
        chan.write("    _nef_dihedral_restraint_list.potential_type    ");
        chan.write(".\n");
        chan.write("    _nef_dihedral_restraint_list.restraint_origin  ");
        chan.write(".\n");
        chan.write("\n");

        chan.write("     loop_\n");
        for (String loopString : DIHEDRAL_RESTRAINT_LOOP_STRINGS) {
            chan.write("          " + loopString + "\n");
        }
        chan.write("\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        Dihedral dihedral = NMRNEFReader.dihedral;
        Map<String, List<AngleBoundary>> angleBoundsMap = dihedral.getAngleBoundariesNEF();
        List<AngleBoundary> angleBlock1 = new ArrayList<>();
        List<AngleBoundary> angleBlock2 = new ArrayList<>();
        for (List<AngleBoundary> boundList : angleBoundsMap.values()) {
            for (AngleBoundary bound : boundList) {
                if (bound.getTargetValue() % 1 == 0 || bound.getTargetValue() % 0.5 == 0) {
                    angleBlock1.add(bound);
                } else {
                    angleBlock2.add(bound);
                }
            }
        }

        Comparator<AngleBoundary> aCmp = (AngleBoundary bound1, AngleBoundary bound2) -> { //sort atom1 sequence code
            int i = 0;
            int result = -1;
            //sort by successive atom ID numbers
            while (i >= 0 && i < 4) {
                int bound1AtomIDNum = bound1.getAtoms()[i].entity.getIDNum();
                int bound2AtomIDNum = bound2.getAtoms()[i].entity.getIDNum();
                result = Integer.compare(bound1AtomIDNum, bound2AtomIDNum);
                if (result == 0) {
                    i++;
                } else {
                    break;
                }
            }
            return result;
        };

        Collections.sort(angleBlock1, aCmp);
        Collections.sort(angleBlock2, aCmp);
        List<List<AngleBoundary>> boundBlocks = new ArrayList<>();
        boundBlocks.add(angleBlock1);
        boundBlocks.add(angleBlock2);
        int i = 1;
        for (List<AngleBoundary> block : boundBlocks) {
            for (AngleBoundary bound : block) {
                Atom[] atoms = bound.getAtoms();
                String result = Atom.toNEFDihedralString(bound, atoms, i, i, ".");
                if (result != null) {
                    chan.write(result + "\n");
                    i++;
                }
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
            writeMolSys(chan);
            writePPM(chan);
            writeDistances(chan);
            writeDihedrals(chan);
        }
    }

}
