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
package org.nmrfx.chemistry.io;

import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.protein.ProteinHelix;
import org.nmrfx.chemistry.protein.Sheet;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.star.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author brucejohnson, Martha
 */
public class MMcifWriter {

    private static final String[] SEQUENCE_LOOP_STRINGS = {"_entity_poly_seq.entity_id", "_entity_poly_seq.num", "_entity_poly_seq.mon_id", "_entity_poly_seq.hetero"};
    private static final String[] CHEM_COMP_LOOP_STRINGS = {"_chem_comp.id", "_chem_comp.type", "_chem_comp.mon_nstd_flag", "_chem_comp.name", "_chem_comp.pdbx_synonyms", "_chem_comp.formula", "_chem_comp.formula_weight"};
    private static final String[] STRUCT_ASYM_LOOP_STRINGS = {"_struct_asym.id", "_struct_asym.pdbx_blank_PDB_chainid_flag", "_struct_asym.pdbx_modified", "_struct_asym.entity_id", "_struct_asym.details"};
    private static final String[] STRUCT_CONF_LOOP_STRINGS = {"_struct_conf.conf_type_id", "_struct_conf.id", "_struct_conf.pdbx_PDB_helix_id", "_struct_conf.beg_label_comp_id", "_struct_conf.beg_label_asym_id", "_struct_conf.beg_label_seq_id", "_struct_conf.pdbx_beg_PDB_ins_code", "_struct_conf.end_label_comp_id", "_struct_conf.end_label_asym_id", "_struct_conf.end_label_seq_id", "_struct_conf.pdbx_end_PDB_ins_code", "_struct_conf.beg_auth_comp_id", "_struct_conf.beg_auth_asym_id", "_struct_conf.beg_auth_seq_id", "_struct_conf.end_auth_comp_id", "_struct_conf.end_auth_asym_id", "_struct_conf.end_auth_seq_id", "_struct_conf.pdbx_PDB_helix_class", "_struct_conf.details", "_struct_conf.pdbx_PDB_helix_length"};
    private static final String[] STRUCT_SHEET_RANGE_LOOP_STRINGS = {"_struct_sheet_range.sheet_id", "_struct_sheet_range.id", "_struct_sheet_range.beg_label_comp_id", "_struct_sheet_range.beg_label_asym_id", "_struct_sheet_range.beg_label_seq_id", "_struct_sheet_range.pdbx_beg_PDB_ins_code", "_struct_sheet_range.end_label_comp_id", "_struct_sheet_range.end_label_asym_id", "_struct_sheet_range.end_label_seq_id", "_struct_sheet_range.pdbx_end_PDB_ins_code", "_struct_sheet_range.beg_auth_comp_id", "_struct_sheet_range.beg_auth_asym_id", "_struct_sheet_range.beg_auth_seq_id", "_struct_sheet_range.end_auth_comp_id", "_struct_sheet_range.end_auth_asym_id", "_struct_sheet_range.end_auth_seq_id"};
    private static final String[] PDB_TRANSF_MATRIX_STRINGS = {"_database_PDB_matrix.entry_id", "_database_PDB_matrix.origx[1][1]", "_database_PDB_matrix.origx[1][2]", "_database_PDB_matrix.origx[1][3]", "_database_PDB_matrix.origx[2][1]", "_database_PDB_matrix.origx[2][2]", "_database_PDB_matrix.origx[2][3]", "_database_PDB_matrix.origx[3][1]", "_database_PDB_matrix.origx[3][2]", "_database_PDB_matrix.origx[3][3]", "_database_PDB_matrix.origx_vector[1]", "_database_PDB_matrix.origx_vector[2]", "_database_PDB_matrix.origx_vector[3]"};
    private static final String[] TRANSF_MATRIX_STRINGS = {"_atom_sites.entry_id", "_atom_sites.fract_transf_matrix[1][1]", "_atom_sites.fract_transf_matrix[1][2]", "_atom_sites.fract_transf_matrix[1][3]", "_atom_sites.fract_transf_matrix[2][1]", "_atom_sites.fract_transf_matrix[2][2]", "_atom_sites.fract_transf_matrix[2][3]", "_atom_sites.fract_transf_matrix[3][1]", "_atom_sites.fract_transf_matrix[3][2]", "_atom_sites.fract_transf_matrix[3][3]", "_atom_sites.fract_transf_vector[1]", "_atom_sites.fract_transf_vector[2]", "_atom_sites.fract_transf_vector[3]"};
    private static final String[] ATOM_SITE_LOOP_STRINGS = {"_atom_site.group_PDB", "_atom_site.id", "_atom_site.type_symbol", "_atom_site.label_atom_id", "_atom_site.label_alt_id", "_atom_site.label_comp_id", "_atom_site.label_asym_id", "_atom_site.label_entity_id", "_atom_site.label_seq_id", "_atom_site.pdbx_PDB_ins_code", "_atom_site.Cartn_x", "_atom_site.Cartn_y", "_atom_site.Cartn_z", "_atom_site.occupancy", "_atom_site.B_iso_or_equiv", "_atom_site.pdbx_formal_charge", "_atom_site.auth_seq_id", "_atom_site.auth_comp_id", "_atom_site.auth_asym_id", "_atom_site.auth_atom_id", "_atom_site.pdbx_PDB_model_num"};
    private static final String[] PDBX_SEQUENCE_LOOP_STRINGS = {"_pdbx_poly_seq_scheme.asym_id", "_pdbx_poly_seq_scheme.entity_id", "_pdbx_poly_seq_scheme.seq_id", "_pdbx_poly_seq_scheme.mon_id", "_pdbx_poly_seq_scheme.ndb_seq_num", "_pdbx_poly_seq_scheme.pdb_seq_num", "_pdbx_poly_seq_scheme.auth_seq_num", "_pdbx_poly_seq_scheme.pdb_mon_id", "_pdbx_poly_seq_scheme.auth_mon_id", "_pdbx_poly_seq_scheme.pdb_strand_id", "_pdbx_poly_seq_scheme.pdb_ins_code", "_pdbx_poly_seq_scheme.hetero"};
    private static final String[] STRUCT_OPER_STRINGS = {"_pdbx_struct_oper_list.id", "_pdbx_struct_oper_list.type", "_pdbx_struct_oper_list.name", "_pdbx_struct_oper_list.symmetry_operation", "_pdbx_struct_oper_list.matrix[1][1]", "_pdbx_struct_oper_list.matrix[1][2]", "_pdbx_struct_oper_list.matrix[1][3]", "_pdbx_struct_oper_list.vector[1]", "_pdbx_struct_oper_list.matrix[2][1]", "_pdbx_struct_oper_list.matrix[2][2]", "_pdbx_struct_oper_list.matrix[2][3]", "_pdbx_struct_oper_list.vector[2]", "_pdbx_struct_oper_list.matrix[3][1]", "_pdbx_struct_oper_list.matrix[3][2]", "_pdbx_struct_oper_list.matrix[3][3]", "_pdbx_struct_oper_list.vector[3]"};
    private static final String[] DISTANCE_LOOP_STRINGS = {"_pdbx_validate_close_contact.id", "_pdbx_validate_close_contact.PDB_model_num", "_pdbx_validate_close_contact.auth_atom_id_1", "_pdbx_validate_close_contact.auth_asym_id_1", "_pdbx_validate_close_contact.auth_comp_id_1", "_pdbx_validate_close_contact.auth_seq_id_1", "_pdbx_validate_close_contact.PDB_ins_code_1", "_pdbx_validate_close_contact.label_alt_id_1", "_pdbx_validate_close_contact.auth_atom_id_2", "_pdbx_validate_close_contact.auth_asym_id_2", "_pdbx_validate_close_contact.auth_comp_id_2", "_pdbx_validate_close_contact.auth_seq_id_2", "_pdbx_validate_close_contact.PDB_ins_code_2", "_pdbx_validate_close_contact.label_alt_id_2", "_pdbx_validate_close_contact.dist"};
    private static final String[] TORSION_LOOP_STRINGS = {"_pdbx_validate_torsion.id", "_pdbx_validate_torsion.PDB_model_num", "_pdbx_validate_torsion.auth_comp_id", "_pdbx_validate_torsion.auth_asym_id", "_pdbx_validate_torsion.auth_seq_id", "_pdbx_validate_torsion.PDB_ins_code", "_pdbx_validate_torsion.label_alt_id", "_pdbx_validate_torsion.phi", "_pdbx_validate_torsion.psi"};

    static String getMainDirectory() {
        String[] classPathSplit = MMcifWriter.class.getResource("MMcifWriter.class").toString().split(":");
        String classPath = classPathSplit[classPathSplit.length - 1];
        int mainIdx = classPath.indexOf("nmrfxstructure");
        return classPath.substring(0, mainIdx + "nmrfxstructure".length());
    }

    static void writeMolSys(MoleculeBase molecule, FileWriter chan, boolean pdb) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        String[] loopStrings = SEQUENCE_LOOP_STRINGS;
        if (pdb) {
            loopStrings = PDBX_SEQUENCE_LOOP_STRINGS;
        }

        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }

        Set<Integer> entityIDSet = new HashSet<>();
        Iterator entityIterator = molecule.entityLabels.values().iterator();
        while (entityIterator.hasNext()) {
            Entity entity = (Entity) entityIterator.next();
            if (entity instanceof Polymer) {
                int entityID = entity.getIDNum();
                if (!entityIDSet.contains(entityID)) {
                    entityIDSet.add(entityID);
                } else {
                    if (!pdb) {
                        continue;
                    }
                }
                List<Residue> resList = ((Polymer) entity).getResidues();
                for (Residue res : resList) {
                    String result = res.toMMCifSequenceString(pdb);
                    if (result != null) {
                        chan.write(result + "\n");
                    }
                }
            }
        }
        chan.write("#\n");
    }

    static void writeStructAsym(MoleculeBase molecule, FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        for (String loopString : STRUCT_ASYM_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }

        Iterator entityIterator = molecule.entityLabels.values().iterator();
        while (entityIterator.hasNext()) {
            Entity entity = (Entity) entityIterator.next();
            if (entity instanceof Polymer) {
                String polymerName = ((Polymer) entity).getName();
                char chainID = polymerName.charAt(0);
                int entityID = ((Polymer) entity).getIDNum();
                String blankPDBflag = "N"; //fixme get from file
                String pdbxMod = "N";
                List<Residue> resList = ((Polymer) entity).getResidues();
                for (Residue res : resList) {
                    if (!res.isStandard()) {
                        pdbxMod = "Y";
                        break;
                    }
                }
                String details = "?"; //fixme get from file
                chan.write(String.format("%-2s %-2s %-2s %-2d %-2s\n", chainID, blankPDBflag, pdbxMod, entityID, details));
            } else {
                String chainID = entity.getName();
                int entityID = entity.getIDNum();
                String blankPDBflag = "N"; //fixme get from file
                String pdbxMod = "N";
                String details = "?"; //fixme get from file
                chan.write(String.format("%-2s %-2s %-2s %-2d %-2s\n", chainID, blankPDBflag, pdbxMod, entityID, details));
            }
        }
        chan.write("#\n");
    }

    static void writeStructConf(MoleculeBase moleculeBase, FileWriter chan) throws IOException, InvalidMoleculeException {
        List<SecondaryStructure> secStruct = moleculeBase.getSecondaryStructure();
        if (!secStruct.isEmpty()) {
            int idx = 1;
            for (SecondaryStructure secStructElem : secStruct) {
                if (secStructElem instanceof ProteinHelix) {
                    if (idx == 1) {
                        chan.write("loop_\n");
                        for (String loopString : STRUCT_CONF_LOOP_STRINGS) {
                            chan.write(loopString + "\n");
                        }
                    }
                    Residue firstResidue = secStructElem.firstResidue();
                    Residue lastResidue = secStructElem.lastResidue();
                    String result = firstResidue.toMMCifStructConfString(idx, lastResidue);
                    if (result != null) {
                        chan.write(result + "\n");
                    }
                    idx++;
                }
            }
            if (idx > 1) {
                chan.write("#\n");
            }
        }
    }

    static void writeSheetRange(MoleculeBase moleculeBase, FileWriter chan) throws IOException, InvalidMoleculeException {
        List<SecondaryStructure> secStruct = moleculeBase.getSecondaryStructure();
        if (!secStruct.isEmpty()) {
            int idx = 1;
            for (SecondaryStructure secStructElem : secStruct) {
                if (secStructElem instanceof Sheet) {
                    if (idx == 1) {
                        chan.write("loop_\n");
                        for (String loopString : STRUCT_SHEET_RANGE_LOOP_STRINGS) {
                            chan.write(loopString + "\n");
                        }
                    }
                    Residue firstResidue = secStructElem.firstResidue();
                    Residue lastResidue = secStructElem.lastResidue();
                    String result = firstResidue.toMMCifSheetRangeString(idx, lastResidue);
                    if (result != null) {
                        chan.write(result + "\n");
                    }
                    idx++;
                }
            }
            if (idx > 1) {
                chan.write("#\n");
            }
        }
    }

    static void writeTransfMatrix(FileWriter chan, String name, boolean pdb) throws IOException, InvalidMoleculeException {

        String[] loopStrings = TRANSF_MATRIX_STRINGS;
        String pdbFormat = "%-39s %-2s\n";
        String mtxFormat = "%-39s %-2.6f\n";
        String vecFormat = "%-39s %-2.5f\n";
        if (pdb) {
            loopStrings = PDB_TRANSF_MATRIX_STRINGS;
            pdbFormat = "%-38s %-2s\n";
            mtxFormat = "%-38s %-2.6f\n";
            vecFormat = "%-38s %-2.5f\n";
        }

        chan.write(String.format(pdbFormat, loopStrings[0], name));
        Double[][] transfMatrix = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
        Double[] vector = {0.0, 0.0, 0.0};
        for (int i = 0; i < transfMatrix.length; i++) {
            for (int j = 0; j < transfMatrix[0].length; j++) {
                chan.write(String.format(mtxFormat, loopStrings[transfMatrix.length * i + j + 1], transfMatrix[i][j]));
            }
        }
        for (int v = 0; v < vector.length; v++) {
            chan.write(String.format(vecFormat, loopStrings[v + 10], vector[v]));
        }
        chan.write("#\n");
    }

    static void writeStructOper(FileWriter chan) throws IOException, InvalidMoleculeException {
        String id = "1\n"; //fixme get dynamically
        String type = "\"identity operation\"\n"; //fixme get dynamically
        String name = "1_555\n"; //fixme get dynamically
        String sym = "?\n"; //fixme get dynamically
        String[] loopStrings = STRUCT_OPER_STRINGS;

        chan.write(String.format("%-43s %-2s", loopStrings[0], id));
        chan.write(String.format("%-43s %-2s", loopStrings[1], type));
        chan.write(String.format("%-43s %-2s", loopStrings[2], name));
        chan.write(String.format("%-43s %-2s", loopStrings[3], sym));
        Double[][] transfMatrix = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
        Double[] vector = {0.0, 0.0, 0.0};
        for (int i = 0; i < transfMatrix.length; i++) {
            for (int j = 0; j < transfMatrix[0].length; j++) {
                chan.write(String.format("%-43s %-2.10f\n", loopStrings[transfMatrix.length * i + j + 4 + i], transfMatrix[i][j]));
            }
            chan.write(String.format("%-43s %-2.10f\n", loopStrings[4 * i + 7], vector[i]));
        }
        chan.write("#\n");
    }

    static void writeAtomTypes(MoleculeBase molecule, FileWriter chan) throws IOException, InvalidMoleculeException {
        int i;
        chan.write("loop_\n");
        chan.write("_atom_type.symbol\n");
        molecule.updateAtomArray();
        List<Atom> atomArray = molecule.getAtomArray();
        SortedSet<String> aTypeSet = new TreeSet<>();
        int[] structures = molecule.getActiveStructures();
        for (int iStruct : structures) {
            for (Atom atom : atomArray) {
                SpatialSet spSet = atom.getSpatialSet();
                if (atom.isCoarse()) {
                    continue;
                }
                if (spSet.getCoords(iStruct) == null) {
                    continue;
                }

                String aType = atom.getSymbol().toUpperCase();
                if (!aTypeSet.contains(aType)) {
                    aTypeSet.add(aType);
                }
            }
        }
        for (String aType : aTypeSet) {
            chan.write(aType + "\n");
        }
        chan.write("#\n");
    }

    static void writeAtomSites(MoleculeBase molecule, FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        for (String loopString : ATOM_SITE_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }
        int i = 0;
        molecule.updateAtomArray();
        int[] structures = molecule.getActiveStructures();
        for (int iStruct : structures) {
            for (Atom atom : molecule.getAtomArray()) {
                SpatialSet spSet = atom.getSpatialSet();
                if (atom.isCoarse()) {
                    continue;
                }

                String result = spSet.toMMCifString(i, iStruct);

                if (result != null) {
                    chan.write(result + "\n");
                    i++;
                }
            }
        }
        chan.write("#\n");
    }

    /**
     * Write molecular system, chemical shift, distance, and torsion information
     * to a mmCif formatted file.
     *
     * @param fileName String. Name of the file to write.
     * @param name     String. The name of the dataset.
     * @throws IOException
     * @throws ParseException
     * @throws InvalidPeakException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(String fileName, String name) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(fileName)) {
            File file = new File(fileName);
            file.getParentFile().mkdirs(); //create file if it doesn't already exist
            System.out.println("wrote " + fileName);
            writeAll(writer, name);
        }
    }

    /**
     * Write molecular system, chemical shift, distance, and torsion information
     * to a mmCif formatted file.
     *
     * @param file File. File to write.
     * @param name String. The name of the dataset.
     * @throws IOException
     * @throws ParseException
     * @throws InvalidPeakException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(File file, String name) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(file)) {
            writeAll(writer, name);
        }
    }

    /**
     * Write molecular system, chemical shift, distance, and torsion information
     * to a mmCif formatted file.
     *
     * @param chan FileWriter. Writer used for writing the file.
     * @param name String. The name of the dataset.
     * @throws IOException
     * @throws ParseException
     * @throws InvalidPeakException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(FileWriter chan, String name) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        //first line of the file must start with "data_"
        //otherwise can't load file in PyMOL, Chimera, etc.
        String title = "data_" + name;
        chan.write(title + "\n");
        chan.write("#\n");
        MoleculeBase molecule = MoleculeFactory.getActive();
        if (molecule != null) {
            writeMolSys(molecule, chan, false);
            writeStructAsym(molecule, chan);
            writeStructConf(molecule, chan);
            writeSheetRange(molecule, chan);
            writeTransfMatrix(chan, name, true);
            writeTransfMatrix(chan, name, false);
            writeAtomTypes(molecule, chan);
            writeAtomSites(molecule, chan);
            writeMolSys(molecule, chan, true);
            writeStructOper(chan);
            chan.flush();
        }
    }

}
