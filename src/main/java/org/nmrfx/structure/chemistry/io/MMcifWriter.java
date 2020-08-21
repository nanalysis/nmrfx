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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.star.ParseException;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.Helix;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.SpatialSet;
import org.nmrfx.structure.chemistry.energy.AngleProp;
import org.nmrfx.structure.chemistry.energy.AtomDistancePair;
import org.nmrfx.structure.chemistry.energy.Dihedral;
import org.nmrfx.structure.chemistry.energy.DistancePair;
import org.nmrfx.structure.chemistry.energy.EnergyLists;

/**
 *
 * @author brucejohnson, Martha
 */
public class MMcifWriter {

    private static final String[] SEQUENCE_LOOP_STRINGS = {"_entity_poly_seq.entity_id", "_entity_poly_seq.num", "_entity_poly_seq.mon_id", "_entity_poly_seq.hetero"};
    private static final String[] CHEM_COMP_LOOP_STRINGS = {"_chem_comp.id", "_chem_comp.type", "_chem_comp.mon_nstd_flag", "_chem_comp.name", "_chem_comp.pdbx_synonyms", "_chem_comp.formula", "_chem_comp.formula_weight"};
    private static final String[] STRUCT_CONF_LOOP_STRINGS = {"_struct_conf.conf_type_id", "_struct_conf.id", "_struct_conf.pdbx_PDB_helix_id", "_struct_conf.beg_label_comp_id", "_struct_conf.beg_label_asym_id", "_struct_conf.beg_label_seq_id", "_struct_conf.pdbx_beg_PDB_ins_code", "_struct_conf.end_label_comp_id", "_struct_conf.end_label_asym_id", "_struct_conf.end_label_seq_id", "_struct_conf.pdbx_end_PDB_ins_code", "_struct_conf.beg_auth_comp_id", "_struct_conf.beg_auth_asym_id", "_struct_conf.beg_auth_seq_id", "_struct_conf.end_auth_comp_id", "_struct_conf.end_auth_asym_id", "_struct_conf.end_auth_seq_id", "_struct_conf.pdbx_PDB_helix_class", "_struct_conf.details", "_struct_conf.pdbx_PDB_helix_length"};
    private static final String[] PDB_TRANSF_MATRIX_STRINGS = {"_database_PDB_matrix.entry_id", "_database_PDB_matrix.origx[1][1]", "_database_PDB_matrix.origx[1][2]", "_database_PDB_matrix.origx[1][3]", "_database_PDB_matrix.origx[2][1]", "_database_PDB_matrix.origx[2][2]", "_database_PDB_matrix.origx[2][3]", "_database_PDB_matrix.origx[3][1]", "_database_PDB_matrix.origx[3][2]", "_database_PDB_matrix.origx[3][3]", "_database_PDB_matrix.origx_vector[1]", "_database_PDB_matrix.origx_vector[2]", "_database_PDB_matrix.origx_vector[3]"};
    private static final String[] TRANSF_MATRIX_STRINGS = {"_atom_sites.entry_id", "_atom_sites.fract_transf_matrix[1][1]", "_atom_sites.fract_transf_matrix[1][2]", "_atom_sites.fract_transf_matrix[1][3]", "_atom_sites.fract_transf_matrix[2][1]", "_atom_sites.fract_transf_matrix[2][2]", "_atom_sites.fract_transf_matrix[2][3]", "_atom_sites.fract_transf_matrix[3][1]", "_atom_sites.fract_transf_matrix[3][2]", "_atom_sites.fract_transf_matrix[3][3]", "_atom_sites.fract_transf_vector[1]", "_atom_sites.fract_transf_vector[2]", "_atom_sites.fract_transf_vector[3]"};
    private static final String[] ATOM_SITE_LOOP_STRINGS = {"_atom_site.group_PDB", "_atom_site.id", "_atom_site.type_symbol", "_atom_site.label_atom_id", "_atom_site.label_alt_id", "_atom_site.label_comp_id", "_atom_site.label_asym_id", "_atom_site.label_entity_id", "_atom_site.label_seq_id", "_atom_site.pdbx_PDB_ins_code", "_atom_site.Cartn_x", "_atom_site.Cartn_y", "_atom_site.Cartn_z", "_atom_site.occupancy", "_atom_site.B_iso_or_equiv", "_atom_site.pdbx_formal_charge", "_atom_site.auth_seq_id", "_atom_site.auth_comp_id", "_atom_site.auth_asym_id", "_atom_site.auth_atom_id", "_atom_site.pdbx_PDB_model_num"};
    private static final String[] PDBX_SEQUENCE_LOOP_STRINGS = {"_pdbx_poly_seq_scheme.asym_id", "_pdbx_poly_seq_scheme.entity_id", "_pdbx_poly_seq_scheme.seq_id", "_pdbx_poly_seq_scheme.mon_id", "_pdbx_poly_seq_scheme.ndb_seq_num", "_pdbx_poly_seq_scheme.pdb_seq_num", "_pdbx_poly_seq_scheme.auth_seq_num", "_pdbx_poly_seq_scheme.pdb_mon_id", "_pdbx_poly_seq_scheme.auth_mon_id", "_pdbx_poly_seq_scheme.pdb_strand_id", "_pdbx_poly_seq_scheme.pdb_ins_code", "_pdbx_poly_seq_scheme.hetero"};
    private static final String[] STRUCT_OPER_STRINGS = {"_pdbx_struct_oper_list.id", "_pdbx_struct_oper_list.type", "_pdbx_struct_oper_list.name", "_pdbx_struct_oper_list.symmetry_operation", "_pdbx_struct_oper_list.matrix[1][1]", "_pdbx_struct_oper_list.matrix[1][2]", "_pdbx_struct_oper_list.matrix[1][3]", "_pdbx_struct_oper_list.vector[1]", "_pdbx_struct_oper_list.matrix[2][1]", "_pdbx_struct_oper_list.matrix[2][2]", "_pdbx_struct_oper_list.matrix[2][3]", "_pdbx_struct_oper_list.vector[2]", "_pdbx_struct_oper_list.matrix[3][1]", "_pdbx_struct_oper_list.matrix[3][2]", "_pdbx_struct_oper_list.matrix[3][3]", "_pdbx_struct_oper_list.vector[3]"};
    private static final String[] DISTANCE_LOOP_STRINGS = {"_pdbx_validate_close_contact.id", "_pdbx_validate_close_contact.PDB_model_num", "_pdbx_validate_close_contact.auth_atom_id_1", "_pdbx_validate_close_contact.auth_asym_id_1", "_pdbx_validate_close_contact.auth_comp_id_1", "_pdbx_validate_close_contact.auth_seq_id_1", "_pdbx_validate_close_contact.PDB_ins_code_1", "_pdbx_validate_close_contact.label_alt_id_1", "_pdbx_validate_close_contact.auth_atom_id_2", "_pdbx_validate_close_contact.auth_asym_id_2", "_pdbx_validate_close_contact.auth_comp_id_2", "_pdbx_validate_close_contact.auth_seq_id_2", "_pdbx_validate_close_contact.PDB_ins_code_2", "_pdbx_validate_close_contact.label_alt_id_2", "_pdbx_validate_close_contact.dist"};
    private static final String[] TORSION_LOOP_STRINGS = {"_pdbx_validate_torsion.id", "_pdbx_validate_torsion.PDB_model_num", "_pdbx_validate_torsion.auth_comp_id", "_pdbx_validate_torsion.auth_asym_id", "_pdbx_validate_torsion.auth_seq_id", "_pdbx_validate_torsion.PDB_ins_code", "_pdbx_validate_torsion.label_alt_id", "_pdbx_validate_torsion.phi", "_pdbx_validate_torsion.psi"};

    static void writeMolSys(FileWriter chan, boolean pdb) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        String[] loopStrings = SEQUENCE_LOOP_STRINGS;
        if (pdb) {
            loopStrings = PDBX_SEQUENCE_LOOP_STRINGS;
        } 
        
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        Iterator entityIterator = molecule.entityLabels.values().iterator();
        while (entityIterator.hasNext()) {
            Entity entity = (Entity) entityIterator.next();
            if (entity instanceof Polymer) {
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
    
    static void writeChemComp(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        for (String loopString : CHEM_COMP_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }
        
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        
        String paramFile = String.join(File.separator, "src", "main", "resources", "reslib_iu", "params.txt");
        BufferedReader reader = new BufferedReader(new FileReader(paramFile));
        Map<String, Double> weightMap = new HashMap<>();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String[] lineS = line.trim().split("\\s+");
            if (!lineS[0].startsWith("AtomType")) {
                String aName = lineS[0];
                Double weight = Double.parseDouble(lineS[5]);
                weightMap.put(aName, weight);
            }
        }
        
        Iterator entityIterator = molecule.entityLabels.values().iterator();
        while (entityIterator.hasNext()) {
            Entity entity = (Entity) entityIterator.next();
            if (entity instanceof Polymer) {
                List<Residue> resList = ((Polymer) entity).getResidues();
                List<String> resNames = new ArrayList<>();
                Set<Residue> resSet = new HashSet<>();
                for (Residue res : resList) {                    
                    if (!resSet.contains(res) && !resNames.contains(res.name)) {
                        resSet.add(res);
                        resNames.add(res.name);
                    }
                }
                List<Residue> sortResSet =  new ArrayList<>(resSet);
                Collections.sort(sortResSet, (r1,r2) -> r1.name.compareTo(r2.name));
                for (Residue res : sortResSet) {
                    String prfFile = String.join(File.separator, "src", "main", "resources", "reslib_iu", res.name.toLowerCase() + ".prf");
                    reader = new BufferedReader(new FileReader(prfFile));
                    String fullResName = "";
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        String lineS = line.trim();
                        String match = "LNAME";
                        if (lineS.startsWith(match)) {
                            fullResName = lineS.substring(match.length()).trim();
                            break;
                        }
                    }
                    boolean lastRes = false;
                    if (res.name.equals(resList.get(resList.size() - 1).name)) {
                        lastRes = true;
                    }
                    String result = res.toMMCifChemCompString(weightMap, lastRes, fullResName.toUpperCase());
                    if (result != null) {
                        chan.write(result + "\n");
                    }
                }
            }
        }
        chan.write("#\n");
    }
    
    static void writeStructConf(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        for (String loopString : STRUCT_CONF_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }
        
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        Helix conf = MMcifReader.helix;
        List<Residue> resList = conf.secResidues;
        int idx = 1;
        for (int i = 0; i < resList.size(); i += 2) {
            Residue firstRes = resList.get(i);
            Residue lastRes = resList.get(i + 1);
            String result = firstRes.toMMCifStructConfString(idx, lastRes);
            if (result != null) {
                chan.write(result + "\n");
            }
            idx++;
        }
        chan.write("#\n");
    }
    
    static void writeTransfMatrix(FileWriter chan, boolean pdb) throws IOException, InvalidMoleculeException {
        String id = "1PQX\n"; //fixme get id dynamically
        String[] loopStrings = TRANSF_MATRIX_STRINGS;
        String pdbFormat = "%-39s %-2s";
        String mtxFormat = "%-39s %-2.6f\n";
        String vecFormat = "%-39s %-2.5f\n";
        if (pdb) {
            loopStrings = PDB_TRANSF_MATRIX_STRINGS;
            pdbFormat = "%-38s %-2s";
            mtxFormat = "%-38s %-2.6f\n";
            vecFormat = "%-38s %-2.5f\n";
        } 
        
        chan.write(String.format(pdbFormat, loopStrings[0], id));
        Double[][] transfMatrix = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
        Double[] vector = {0.0, 0.0, 0.0};
        for (int i=0; i<transfMatrix.length; i++) {
            for (int j=0; j<transfMatrix[0].length; j++) {
                chan.write(String.format(mtxFormat, loopStrings[transfMatrix.length*i + j + 1], transfMatrix[i][j]));
            }
        }
        for (int v=0; v<vector.length; v++) {
            chan.write(String.format(vecFormat, loopStrings[v + 10], vector[v]));
        }
        chan.write("#\n");
    }
    
    static void writeStructOper(FileWriter chan) throws IOException, InvalidMoleculeException {
        String id = "1\n"; //fixme get dynamically
        String type = "identity operation\n"; //fixme get dynamically
        String name = "1_555\n"; //fixme get dynamically
        String sym = "?\n"; //fixme get dynamically
        String[] loopStrings = STRUCT_OPER_STRINGS;
        
        chan.write(String.format("%-43s %-2s", loopStrings[0], id));
        chan.write(String.format("%-43s %-2s", loopStrings[1], type));
        chan.write(String.format("%-43s %-2s", loopStrings[2], name));
        chan.write(String.format("%-43s %-2s", loopStrings[3], sym));
        Double[][] transfMatrix = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
        Double[] vector = {0.0, 0.0, 0.0};
        for (int i=0; i<transfMatrix.length; i++) {
            for (int j=0; j<transfMatrix[0].length; j++) {
                chan.write(String.format("%-43s %-2.10f\n", loopStrings[transfMatrix.length*i + j + 4 + i], transfMatrix[i][j]));
            }
            chan.write(String.format("%-43s %-2.10f\n", loopStrings[4*i + 7], vector[i]));
        }
        chan.write("#\n");
    }
    
    static void writeAtomTypes(FileWriter chan) throws IOException, InvalidMoleculeException {
        int i;
        chan.write("loop_\n");
        chan.write("_atom_type.symbol\n");
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        List<Atom> atomArray = molecule.getAtomArray();
        SortedSet<String> aTypeSet = new TreeSet<>();
        for (Atom atom : atomArray) {
            if (!aTypeSet.contains(atom.getSymbol())) {
                aTypeSet.add(atom.getSymbol());
            }
        }
        for (String aType : aTypeSet) {
            chan.write(aType + "\n");
        }
        chan.write("#\n");
    }

    static void writeAtomSites(FileWriter chan) throws IOException, InvalidMoleculeException {
        int i;
        chan.write("loop_\n");
        for (String loopString : ATOM_SITE_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        i = 0;
        molecule.updateAtomArray();
        List<Atom> atomArray = molecule.getAtomArray();
        Atom a0 = atomArray.get(0);
        int nSpSets = a0.getSpatialSetMap().get(a0.getFullName()).size();
        for (int iSet = 0; iSet < nSpSets; iSet++)  {
            for (Atom atom : atomArray) {
                Map<String, List<SpatialSet>> spSetMap = atom.getSpatialSetMap();
                List<SpatialSet> spSets = spSetMap.get(atom.getFullName());
                if (spSets != null) {
                    SpatialSet spSet = spSets.get(iSet);
                    String result = atom.atomSitesToMMCifString(spSet, iSet + 1, i);
                    if (result != null) {
                        chan.write(result + "\n");
                        i++;
                    }
                }
            }
        }
        chan.write("#\n");
    }
    
    static void writeDistances(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        for (String loopString : DISTANCE_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        EnergyLists eLists = MMcifReader.energyList;
        List<DistancePair> distList = eLists.getDistanceList();
        int idx = 1;
        int pdbModelNum = 1;
        String result;
        for (int i = 0; i < distList.size(); i++) {
            DistancePair distPair = distList.get(i);
            AtomDistancePair[] pairAtoms = distPair.getAtomPairs();
            int nPairs = pairAtoms.length;
            for (int iPair = 0; iPair < nPairs; iPair++) {
                AtomDistancePair pair = pairAtoms[iPair];
                Atom atom1 = pair.getAtoms1()[0];
                Atom atom2 = pair.getAtoms2()[0];
                result = Atom.toMMCifDistanceString(idx, pdbModelNum, distPair, atom1, atom2);
                chan.write(result + "\n");
                idx++;

            }
            pdbModelNum++;
        }

        chan.write("#\n");
    }

    static void writeTorsions(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        for (String loopString : TORSION_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        molecule.updateAtomArray();
        Dihedral dihedral = MMcifReader.dihedral;
        List<Map<Residue, AngleProp>> torsionList = dihedral.getTorsionAngles();
        int idx = 1;
        for (int i = 0; i < torsionList.size(); i++) {
            Map<Residue, AngleProp> torsionMap = torsionList.get(i);
            for (Residue res : torsionMap.keySet()) {
                AngleProp aProp = torsionMap.get(res);
                double[] angles = aProp.getTarget();
                String result = res.toMMCifTorsionString(angles, idx, i + 1);
                if (result != null) {
                    chan.write(result + "\n");
                    idx++;
                }
            }
        }
        chan.write("#\n");
    }

    /**
     * Write molecular system, chemical shift, distance, and torsion
     * information to a mmCif formatted file.
     *
     * @param fileName String. Name of the file to write.
     * @throws IOException
     * @throws ParseException
     * @throws InvalidPeakException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(String fileName) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(fileName)) {
            File file = new File(fileName);
            file.getParentFile().mkdirs(); //create file if it doesn't already exist
            System.out.println("wrote " + fileName);
            writeAll(writer);
        }
    }

    /**
     * Write molecular system, chemical shift, distance, and torsion
     * information to a mmCif formatted file.
     *
     * @param file File. File to write.
     * @throws IOException
     * @throws ParseException
     * @throws InvalidPeakException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(File file) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(file)) {
            writeAll(writer);
        }
    }

    /**
     * Write molecular system, chemical shift, distance, and torsion
     * information to a mmCif formatted file.
     *
     * @param chan FileWriter. Writer used for writing the file.
     * @throws IOException
     * @throws ParseException
     * @throws InvalidPeakException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(FileWriter chan) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        Date date = new Date(System.currentTimeMillis());
        String programName = MMcifWriter.class
                .getPackage().getName();
        String programVersion = MMcifWriter.class
                .getPackage().getImplementationVersion();

        String[] programNameS = programName.split("\\.");
        int nameIdx1 = Arrays.asList(programNameS).indexOf("nmrfx");
        int nameIdx2 = Arrays.asList(programNameS).indexOf("structure");
        programName = programNameS[nameIdx1] + programNameS[nameIdx2];
        if (programVersion == null) {
            BufferedReader reader = new BufferedReader(new FileReader("pom.xml"));
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String lineS = line.trim();
                String match = "<version>";
                if (lineS.startsWith(match)) {
                    programVersion = lineS.substring(match.length(), lineS.indexOf("/") - 1);
                    break;
                }
            }
        }

//        chan.write("\n");
//        chan.write("save_nef_nmr_meta_data\n");
//        chan.write("    _nef_nmr_meta_data.sf_category           ");
//        chan.write("nef_nmr_meta_data\n");
//        chan.write("    _nef_nmr_meta_data.sf_framecode          ");
//        chan.write("nef_nmr_meta_data\n");
//        chan.write("    _nef_nmr_meta_data.format_name           ");
//        chan.write("nmr_exchange_format\n");
//        chan.write("    _nef_nmr_meta_data.format_version        ");
//        chan.write("1.1\n");
//        chan.write("    _nef_nmr_meta_data.program_name          ");
//        chan.write(programName + "\n");
//        chan.write("    _nef_nmr_meta_data.program_version       ");
//        chan.write(programVersion + "\n");
//        chan.write("    _nef_nmr_meta_data.creation_date         ");
//        chan.write(date.toString() + "\n");
//        chan.write("    _nef_nmr_meta_data.uuid                  ");
//        chan.write(".\n");
//        chan.write("    _nef_nmr_meta_data.coordinate_file_name  ");
//        chan.write(".\n");
//        chan.write("\n");
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            writeMolSys(chan, false);
            writeChemComp(chan);
            writeStructConf(chan);
            writeTransfMatrix(chan, true);
            writeTransfMatrix(chan, false);
            writeAtomTypes(chan);
            writeAtomSites(chan);
            writeMolSys(chan, true);
            writeStructOper(chan);
            writeDistances(chan);
            writeTorsions(chan);
        }
    }

}
