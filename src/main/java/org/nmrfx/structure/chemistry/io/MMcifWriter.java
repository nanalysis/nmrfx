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
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.star.ParseException;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Compound;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.SpatialSet;
import org.nmrfx.structure.chemistry.energy.AngleBoundary;
import org.nmrfx.structure.chemistry.energy.AngleProp;
import org.nmrfx.structure.chemistry.energy.AtomDistancePair;
import org.nmrfx.structure.chemistry.energy.Dihedral;
import org.nmrfx.structure.chemistry.energy.DistancePair;
import org.nmrfx.structure.chemistry.energy.EnergyLists;
import org.nmrfx.structure.utilities.Util;

/**
 *
 * @author brucejohnson, Martha
 */
public class MMcifWriter {

    private static final String[] SEQUENCE_LOOP_STRINGS = {"_entity_poly_seq.entity_id", "_entity_poly_seq.num", "_entity_poly_seq.mon_id", "_entity_poly_seq.hetero"};
    private static final String[] ATOM_SITE_LOOP_STRINGS = {"_atom_site.group_PDB", "_atom_site.id", "_atom_site.type_symbol", "_atom_site.label_atom_id", "_atom_site.label_alt_id", "_atom_site.label_comp_id", "_atom_site.label_asym_id", "_atom_site.label_entity_id", "_atom_site.label_seq_id", "_atom_site.pdbx_PDB_ins_code", "_atom_site.Cartn_x", "_atom_site.Cartn_y", "_atom_site.Cartn_z", "_atom_site.occupancy", "_atom_site.B_iso_or_equiv", "_atom_site.pdbx_formal_charge", "_atom_site.auth_seq_id", "_atom_site.auth_comp_id", "_atom_site.auth_asym_id", "_atom_site.auth_atom_id", "_atom_site.pdbx_PDB_model_num"};
    private static final String[] DISTANCE_LOOP_STRINGS = {"_pdbx_validate_close_contact.id", "_pdbx_validate_close_contact.PDB_model_num", "_pdbx_validate_close_contact.auth_atom_id_1", "_pdbx_validate_close_contact.auth_asym_id_1", "_pdbx_validate_close_contact.auth_comp_id_1", "_pdbx_validate_close_contact.auth_seq_id_1", "_pdbx_validate_close_contact.PDB_ins_code_1", "_pdbx_validate_close_contact.label_alt_id_1", "_pdbx_validate_close_contact.auth_atom_id_2", "_pdbx_validate_close_contact.auth_asym_id_2", "_pdbx_validate_close_contact.auth_comp_id_2", "_pdbx_validate_close_contact.auth_seq_id_2", "_pdbx_validate_close_contact.PDB_ins_code_2", "_pdbx_validate_close_contact.label_alt_id_2", "_pdbx_validate_close_contact.dist"};
    private static final String[] TORSION_LOOP_STRINGS = {"_pdbx_validate_torsion.id", "_pdbx_validate_torsion.PDB_model_num", "_pdbx_validate_torsion.auth_comp_id", "_pdbx_validate_torsion.auth_asym_id", "_pdbx_validate_torsion.auth_seq_id", "_pdbx_validate_torsion.PDB_ins_code", "_pdbx_validate_torsion.label_alt_id", "_pdbx_validate_torsion.phi", "_pdbx_validate_torsion.psi"};

    static void writeMolSys(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        for (String loopString : SEQUENCE_LOOP_STRINGS) {
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
                    String result = res.toMMCifSequenceString(molecule);
                    if (result != null) {
                        chan.write(result + "\n");
                    }
                }
            }
        }
        chan.write("#\n");
    }

    static void writeAtomSites(FileWriter chan) throws IOException, InvalidMoleculeException {
        int i;
        chan.write("loop_\n");
        for (String loopString : ATOM_SITE_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
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
        chan.write("\n");
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

    static void writeDihedrals(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("loop_\n");
        for (String loopString : TORSION_LOOP_STRINGS) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
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
//        Map<String, List<AngleBoundary>> angleBoundsMap = dihedral.getAngleBoundariesNEF();
//        List<AngleBoundary> angleBlock1 = new ArrayList<>();
//        List<AngleBoundary> angleBlock2 = new ArrayList<>();
//        for (List<AngleBoundary> boundList : angleBoundsMap.values()) {
//            for (AngleBoundary bound : boundList) {
//                if (bound.getTargetValue() % 1 == 0 || bound.getTargetValue() % 0.5 == 0) {
//                    angleBlock1.add(bound);
//                } else {
//                    angleBlock2.add(bound);
//                }
//            }
//        }
//
//        Comparator<AngleBoundary> aCmp = (AngleBoundary bound1, AngleBoundary bound2) -> { //sort atom1 sequence code
//            int i = 0;
//            int result = -1;
//            //sort by successive atom ID numbers
//            while (i >= 0 && i < 4) {
//                int bound1AtomIDNum = bound1.getAtoms()[i].entity.getIDNum();
//                int bound2AtomIDNum = bound2.getAtoms()[i].entity.getIDNum();
//                result = Integer.compare(bound1AtomIDNum, bound2AtomIDNum);
//                if (result == 0) {
//                    i++;
//                } else {
//                    break;
//                }
//            }
//            return result;
//        };
//
//        Collections.sort(angleBlock1, aCmp);
//        Collections.sort(angleBlock2, aCmp);
//        List<List<AngleBoundary>> boundBlocks = new ArrayList<>();
//        boundBlocks.add(angleBlock1);
//        boundBlocks.add(angleBlock2);
//        int i = 1;
//        for (List<AngleBoundary> block : boundBlocks) {
//            for (AngleBoundary bound : block) {
//                Atom[] atoms = bound.getAtoms();
//                String result = Atom.toNEFDihedralString(bound, atoms, i, i, ".");
//                if (result != null) {
//                    chan.write(result + "\n");
//                    i++;
//                }
//            }
//        }
        chan.write("#\n");
    }

    /**
     * Write molecular system, chemical shift, distance, and dihedral
     * information to a NEF formatted file.
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
     * Write molecular system, chemical shift, distance, and dihedral
     * information to a NEF formatted file.
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
     * Write molecular system, chemical shift, distance, and dihedral
     * information to a NEF formatted file.
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
            writeMolSys(chan);
            writeAtomSites(chan);
            writeDistances(chan);
            writeDihedrals(chan);
        }
    }

}
