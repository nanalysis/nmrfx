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
import org.nmrfx.structure.chemistry.energy.AngleBoundary;
import org.nmrfx.structure.chemistry.energy.AtomDistancePair;
import org.nmrfx.structure.chemistry.energy.Dihedral;
import org.nmrfx.structure.chemistry.energy.DistancePair;
import org.nmrfx.structure.chemistry.energy.EnergyLists;
import org.nmrfx.structure.utilities.Util;

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
                    String result = res.toNEFSequenceString(link);
                    if (result != null) {
                        chan.write(result + "\n");
                    }
                }
            }
        }
        chan.write("    stop_\n");
        chan.write("save_\n");
    }

    static int checkPartnerInGroup(Atom atom, List<List<Atom>> partners, Set<String> pairNames, boolean first) {
        int commonLevel = 0;
        for (List<Atom> shellAtoms : partners) {
            boolean shellSame = true;
            for (Atom atom2 : shellAtoms) {
                String pairName;
                if (first) {
                    pairName = atom2.getFullName() + "_" + atom.getFullName();
                } else {
                    pairName = atom.getFullName() + "_" + atom2.getFullName();

                }
                if (!pairNames.contains(pairName)) {
                    shellSame = false;
                    break;
                }
            }
            if (shellSame) {
                commonLevel++;
            } else {
                break;
            }
        }
        return commonLevel;
    }

    static int checkPartnerShifts(Atom atom, List<List<Atom>> partners) {
        int commonLevel = 0;
        for (List<Atom> shellAtoms : partners) {
            boolean shellSame = true;
            for (Atom atom2 : shellAtoms) {
                if (!Util.hasSameShift(atom, atom2)) {
                    shellSame = false;
                    break;
                }
            }
            if (shellSame) {
                commonLevel++;
            } else {
                break;
            }
        }
        return commonLevel;
    }

    static boolean checkFirstPartner(Atom atom, List<List<Atom>> partners, int level) {
        boolean isFirst = true;
        for (int iLevel = 0; iLevel < level; iLevel++) {
            List<Atom> shellAtoms = partners.get(iLevel);
            boolean firstAtom = true;
            for (Atom atom2 : shellAtoms) {
                if (atom.getIndex() > atom2.getIndex()) {
                        firstAtom = false;
                    break;
                }
            }
            if (!firstAtom) {
                isFirst = false;
                break;
            }
        }
        return isFirst;
    }

    static void writePPM(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n");
        chan.write("save_nef_chemical_shift_list\n"); //fixme dynamically get framecode
        chan.write("    _nef_chemical_shift_list.sf_category                ");
        chan.write("nef_chemical_shift_list\n");
        chan.write("    _nef_chemical_shift_list.sf_framecode               ");
        chan.write("nef_chemical_shift_list\n"); //fixme dynamically get framecode
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
        List<Atom> atomArray = molecule.getAtomArray();
        for (Atom atom : atomArray) {
            boolean writeLine = true;
            int collapse = atom.getStereo() <= 0 ? 1 : 0;
            int sameShift = 0;
            Optional<Atom> methylPartnerOpt = Optional.empty();
            Optional<Atom> partnerOpt = Optional.empty();
            if (atom.isMethyl()) {
                if (!atom.isFirstInMethyl()) {
                    continue;
                }
                collapse = 0;
                methylPartnerOpt = atom.getParent().getMethylCarbonPartner();
                if (methylPartnerOpt.isPresent()) {
                    if (atom.getParent().getStereo() == 0) {
                        collapse = 1;
                    } else if (atom.getStereo() == -1) {
                        collapse = 2;
                    }
                }
                if (collapse == 2) {
                    if (atom.getParent().getID() > methylPartnerOpt.get().getID()) {
                        continue;
                    }
                }
                //collapse = atom.getStereo() == -1 ? 2 : atom.getStereo() == 0 ? 1 : 0;
//                System.out.println("write  methyl  " + atom.getFullName() + " " + sameShift + " " + atom.getStereo() + " " + collapse);
            } else if (atom.isMethylene()) {
//                System.out.println(atom.getFullName() + " " + atom.getPartners(1).toString());
                List<List<Atom>> partners = atom.getPartners(1);
                sameShift = checkPartnerShifts(atom, partners);
                if (sameShift > 0) {
                    if (!checkFirstPartner(atom, partners, sameShift)) {
                        continue;
                    }
                }
                //partnerOpt = atom.getMethylenePartner();
            } else if (atom.isAromaticFlippable()) {
//                System.out.println(atom.getFullName() + " " + atom.getPartners(1).toString());
                List<List<Atom>> partners = atom.getPartners(-1);
                sameShift = checkPartnerShifts(atom, partners);
                if (sameShift > 0) {
                    if (!checkFirstPartner(atom, partners, sameShift)) {
                        continue;
                    }
                }
                //partnerOpt = atom.getMethylenePartner();
            } else {
                // check for aromatic atoms
                List<Object> equiv = atom.getEquivalency();
                //   System.out.println(atom.getFullName());
                if (equiv.size() > 3) {
                    Object obj = equiv.get(2);
                    int shell = (Integer) obj;
                    if ((shell == 5) || (shell == 4) || (shell == 2)) { // fixme  aromatic HE aren't getting right shells
                        List<String> names = (List<String>) equiv.get(3);
                        if (names.size() == 1) {
                            String partnerName = names.get(0);
                            Compound compound = (Compound) atom.getEntity();
                            Atom partnerAtom = compound.getAtom(partnerName);
                            partnerOpt = Optional.of(partnerAtom);
                        }
                    }
                }
//                for (Object obj : equiv) {
//                    System.out.println(obj.toString());
//                }
            }

            if (partnerOpt.isPresent()) {
                Atom partnerAtom = partnerOpt.get();
                sameShift = Util.hasSameShift(atom, partnerAtom) ? 1 : 0;
                if (sameShift > 0) {
                    if (atom.getIndex() > partnerAtom.getIndex()) {
                        continue;
                    }
                }
            }
            if (writeLine) {
                String result = atom.ppmToNEFString(iPPM, i, collapse, sameShift);
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
        chan.write("save_nef_distance_restraint_list\n"); //fixme dynamically get framecode
        chan.write("    _nef_distance_restraint_list.sf_category       ");
        chan.write("nef_distance_restraint_list\n");
        chan.write("    _nef_distance_restraint_list.sf_framecode      ");
        chan.write("nef_distance_restraint_list\n"); //fixme dynamically get framecode
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
        EnergyLists eLists = molecule.getEnergyLists();
        List<DistancePair> distList = eLists.getDistanceList();
        int idx = 1;
        int restraintID = 1;
        String result;
        for (int i = 0; i < distList.size(); i++) {
            DistancePair distPair = distList.get(i);
            AtomDistancePair[] pairAtoms = distPair.getAtomPairs();
            int nPairs = pairAtoms.length;
            int[][] collapse = new int[nPairs][2];
            boolean[] skipPair = new boolean[nPairs];
            Set<String> pairNames = new HashSet<>();
            for (int iPair = 0; iPair < nPairs; iPair++) {
                AtomDistancePair pair = pairAtoms[iPair];
                Atom atom1 = pair.getAtoms1()[0];
                Atom atom2 = pair.getAtoms2()[0];
                String pairName = atom1.getFullName() + "_" + atom2.getFullName();
                pairNames.add(pairName);
            }
            // System.out.println(pairNames.toString());

            for (int iPair = 0; iPair < nPairs; iPair++) {
                AtomDistancePair pair = pairAtoms[iPair];
                Atom atom1 = pair.getAtoms1()[0];
                Atom atom2 = pair.getAtoms2()[0];
                if (atom1.isMethyl()) {
                    if (!atom1.isFirstInMethyl()) {
                        skipPair[iPair] = true;
                    } else {
                        Atom[] partners = atom1.getPartners(1, 2);
                        collapse[iPair][0] = 1;
                        if (partners != null) {
                            if (partners.length > 5) {
                                Atom otherMethyl = partners[2];
                                String otherMethylName = otherMethyl.getFullName();  // fixme assumes first Methyl proton of other methyl always in pos 2         
                                String pairName = otherMethylName + "_" + atom2.getFullName();
                                if (pairNames.contains(pairName)) {
                                    collapse[iPair][0] = 2;
                                    if (atom1.getIndex() > otherMethyl.getIndex()) {
                                        skipPair[iPair] = true;
                                    }
                                }
                            }
                        }
                    }

                } else {
                    List<List<Atom>> partners = atom1.getPartners(1);
                    if (partners.size() > 0) {
                        int commonLevel = checkPartnerInGroup(atom2, partners, pairNames, true);
//                        System.out.println(atom2.getFullName() + " " + pairNames);
//                        System.out.println(partners + " " + commonLevel);
                        if (commonLevel > 0) {
                            if (!checkFirstPartner(atom1, partners, commonLevel)) {
                                skipPair[iPair] = true;
                            } else {
                                collapse[iPair][0] = commonLevel;

                            }
                        }

                    }

                }
                if (atom2.isMethyl()) {
                    if (!atom2.isFirstInMethyl()) {
                        skipPair[iPair] = true;
                    } else {
                        Atom[] partners = atom2.getPartners(1, 2);
                        collapse[iPair][1] = 1;
                        if (partners != null) {
                            if (partners.length > 5) {
                                Atom otherMethyl = partners[2];
                                String otherMethylName = otherMethyl.getFullName();  // fixme assumes first Methyl proton of other methyl always in pos 2         
                                String pairName = atom1.getFullName() + "_" + otherMethylName;
                                if (pairNames.contains(pairName)) {
                                    collapse[iPair][1] = 2;
                                    if (atom2.getIndex() > otherMethyl.getIndex()) {
                                        skipPair[iPair] = true;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    List<List<Atom>> partners = atom2.getPartners(1);
                    if (partners.size() > 0) {
                        int commonLevel = checkPartnerInGroup(atom1, partners, pairNames, false);
//                        System.out.println(atom2.getFullName() + " " + pairNames);
//                        System.out.println(partners + " " + commonLevel);
                        if (commonLevel > 0) {
                            if (!checkFirstPartner(atom2, partners, commonLevel)) {
                                skipPair[iPair] = true;
                            } else {
                                collapse[iPair][1] = commonLevel;

                            }
                        }

                    }
                }
            }
            for (int iPair = 0; iPair < nPairs; iPair++) {
                AtomDistancePair pair = pairAtoms[iPair];
                if (skipPair[iPair]) {
                    continue;
                }
                Atom atom1 = pair.getAtoms1()[0];
                Atom atom2 = pair.getAtoms2()[0];
                result = Atom.toNEFDistanceString(idx, collapse[iPair], restraintID, ".", distPair, atom1, atom2);
                chan.write(result + "\n");
                idx++;

            }
            restraintID++;
        }

        chan.write(
                "     stop_\n");
        chan.write(
                "save_\n");
    }

    static void writeDihedrals(FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n");
        chan.write("save_nef_dihedral_restraint_list\n"); //fixme dynamically get framecode
        chan.write("    _nef_dihedral_restraint_list.sf_category       ");
        chan.write("nef_dihedral_restraint_list\n");
        chan.write("    _nef_dihedral_restraint_list.sf_framecode      ");
        chan.write("nef_dihedral_restraint_list\n"); //fixme dynamically get framecode
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
        Dihedral dihedral = molecule.getDihedrals();
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
        String programName = NMRNEFWriter.class
                .getPackage().getName();
        String programVersion = NMRNEFWriter.class
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

        chan.write("\n");
        chan.write("save_nef_nmr_meta_data\n");
        chan.write("    _nef_nmr_meta_data.sf_category           ");
        chan.write("nef_nmr_meta_data\n");
        chan.write("    _nef_nmr_meta_data.sf_framecode          ");
        chan.write("nef_nmr_meta_data\n");
        chan.write("    _nef_nmr_meta_data.format_name           ");
        chan.write("nmr_exchange_format\n");
        chan.write("    _nef_nmr_meta_data.format_version        ");
        chan.write("1.1\n");
        chan.write("    _nef_nmr_meta_data.program_name          ");
        chan.write(programName + "\n");
        chan.write("    _nef_nmr_meta_data.program_version       ");
        chan.write(programVersion + "\n");
        chan.write("    _nef_nmr_meta_data.creation_date         ");
        chan.write(date.toString() + "\n");
        chan.write("    _nef_nmr_meta_data.uuid                  ");
        chan.write(".\n");
        chan.write("    _nef_nmr_meta_data.coordinate_file_name  ");
        chan.write(".\n");
        chan.write("\n");
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            writeMolSys(chan);
            writePPM(chan);
            writeDistances(chan);
            writeDihedrals(chan);
        }
    }

}
