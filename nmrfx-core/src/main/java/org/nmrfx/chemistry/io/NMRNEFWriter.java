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
import org.nmrfx.chemistry.constraints.*;
import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.peaks.Peak;
import org.nmrfx.star.STAR3Base;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author brucejohnson, Martha
 */
public class NMRNEFWriter {

    private static final String[] SEQUENCE_LOOP_STRINGS = {"_nef_sequence.index", "_nef_sequence.chain_code", "_nef_sequence.sequence_code", "_nef_sequence.residue_name", "_nef_sequence.linking", "_nef_sequence.residue_variant"};
    private static final String[] CHEM_SHIFT_LOOP_STRINGS = {"_nef_chemical_shift.chain_code", "_nef_chemical_shift.sequence_code", "_nef_chemical_shift.residue_name", "_nef_chemical_shift.atom_name", "_nef_chemical_shift.value", "_nef_chemical_shift.value_uncertainty"};
    private static final String[] DISTANCE_RESTRAINT_LOOP_STRINGS = {"_nef_distance_restraint.index", "_nef_distance_restraint.restraint_id", "_nef_distance_restraint.restraint_combination_id", "_nef_distance_restraint.chain_code_1", "_nef_distance_restraint.sequence_code_1", "_nef_distance_restraint.residue_name_1", "_nef_distance_restraint.atom_name_1", "_nef_distance_restraint.chain_code_2", "_nef_distance_restraint.sequence_code_2", "_nef_distance_restraint.residue_name_2", "_nef_distance_restraint.atom_name_2", "_nef_distance_restraint.weight", "_nef_distance_restraint.target_value", "_nef_distance_restraint.target_value_uncertainty", "_nef_distance_restraint.lower_limit", "_nef_distance_restraint.upper_limit"};
    private static final String[] DIHEDRAL_RESTRAINT_LOOP_STRINGS = {"_nef_dihedral_restraint.index", "_nef_dihedral_restraint.restraint_id", "_nef_dihedral_restraint.restraint_combination_id", "_nef_dihedral_restraint.chain_code_1", "_nef_dihedral_restraint.sequence_code_1", "_nef_dihedral_restraint.residue_name_1", "_nef_dihedral_restraint.atom_name_1", "_nef_dihedral_restraint.chain_code_2", "_nef_dihedral_restraint.sequence_code_2", "_nef_dihedral_restraint.residue_name_2", "_nef_dihedral_restraint.atom_name_2", "_nef_dihedral_restraint.chain_code_3", "_nef_dihedral_restraint.sequence_code_3", "_nef_dihedral_restraint.residue_name_3", "_nef_dihedral_restraint.atom_name_3", "_nef_dihedral_restraint.chain_code_4", "_nef_dihedral_restraint.sequence_code_4", "_nef_dihedral_restraint.residue_name_4", "_nef_dihedral_restraint.atom_name_4", "_nef_dihedral_restraint.weight", "_nef_dihedral_restraint.target_value", "_nef_dihedral_restraint.target_value_uncertainty", "_nef_dihedral_restraint.lower_limit", "_nef_dihedral_restraint.upper_limit", "_nef_dihedral_restraint.name"};

    static void writeMolSys(MoleculeBase molecule, FileWriter chan) throws IOException {
        chan.write("\n\n");
        chan.write(STAR3Base.SAVE + "nef_molecular_system\n");
        chan.write("    _nef_molecular_system.Sf_category   ");
        chan.write("nef_molecular_system\n");
        chan.write("    _nef_molecular_system.Sf_framecode  ");
        chan.write("nef_molecular_system\n");
        chan.write("\n");

        chan.write("    loop_\n");
        for (String loopString : SEQUENCE_LOOP_STRINGS) {
            chan.write("         " + loopString + "\n");
        }
        chan.write("\n\n");
        Iterator<Entity> entityIterator = molecule.entityLabels.values().iterator();
        int idx = 1;
        while (entityIterator.hasNext()) {
            Entity entity = entityIterator.next();
            String link;
            if (entity instanceof Polymer polymer) {
                List<Residue> resList = polymer.getResidues();
                Residue firstRes = polymer.getFirstResidue();
                Residue lastRes = polymer.getLastResidue();
                for (Residue res : resList) {
                    if (res.equals(firstRes)) {
                        link = "start";
                    } else if (res.equals(lastRes)) {
                        link = "end";
                    } else {
                        link = "middle";
                    }
                    String result = res.toNEFSequenceString(idx, link);
                    if (result != null) {
                        chan.write(result + "\n");
                    }
                    idx++;
                }
            } else if (entity instanceof Compound compound) {
                link = "single";
                String result = compound.toNEFSequenceString(idx, link);
                if (result != null) {
                    chan.write(result + "\n");
                }
                idx++;
            }
        }
        chan.write("    " + STAR3Base.STOP + "\n");
        chan.write(STAR3Base.SAVE + "\n");
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

    static void writePPM(MoleculeBase molecule, FileWriter chan) throws IOException, InvalidMoleculeException {
        chan.write("\n");
        chan.write(STAR3Base.SAVE + "nef_chemical_shift_list\n"); //fixme dynamically get framecode
        chan.write("    _nef_chemical_shift_list.Sf_category                ");
        chan.write("nef_chemical_shift_list\n");
        chan.write("    _nef_chemical_shift_list.Sf_framecode               ");
        chan.write("nef_chemical_shift_list\n"); //fixme dynamically get framecode
        chan.write("\n");

        int i;
        chan.write("    loop_\n");
        for (String loopString : CHEM_SHIFT_LOOP_STRINGS) {
            chan.write("         " + loopString + "\n");
        }
        chan.write("\n");
        int iPPM = 0;
        i = 0;
        molecule.updateAtomArray();
        List<Atom> atomArray = molecule.getAtomArray();
        for (Atom atom : atomArray) {
            boolean writeLine = true;
            int collapse = atom.getStereo() <= 0 ? 1 : 0;
            int sameShift = 0;
            Optional<Atom> methylPartnerOpt;
            Optional<Atom> partnerOpt = Optional.empty();
            if (atom.isMethyl()) {
                if (!atom.isFirstInMethyl()) {
                    continue;
                }

                collapse = 0;
                methylPartnerOpt = atom.getParent().getMethylCarbonPartner();
                if (methylPartnerOpt.isPresent()) {
                    if (atom.getStereo() == 0) {
                        collapse = 1;
                    } else {
                        collapse = 0;
                    }
                }
                if (collapse == 2) {
                    if (atom.getParent().getID() > methylPartnerOpt.get().getID()) {
                        continue;
                    }
                }
            } else if (atom.isMethylene()) {
                List<List<Atom>> partners = atom.getPartners(1);
                sameShift = checkPartnerShifts(atom, partners);
                if (sameShift > 0) {
                    if (!checkFirstPartner(atom, partners, sameShift)) {
                        continue;
                    }
                }
            } else if (atom.isAromaticFlippable()) {
                List<List<Atom>> partners = atom.getPartners(-1);
                sameShift = checkPartnerShifts(atom, partners);
                if (sameShift > 0) {
                    if (!checkFirstPartner(atom, partners, sameShift)) {
                        continue;
                    }
                }
            } else {
                // check for aromatic atoms
                List<Object> equiv = atom.getEquivalency();
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
                    chan.write(result + "\n");
                    i++;
                }
            }
        }
        chan.write("    stop_\n");
        chan.write(STAR3Base.SAVE + "\n");
    }
    static List<AtomDistancePair> getAtomDistancePairs(List<Noe> noes) {
        List<AtomDistancePair> atomDistancePairs = new ArrayList<>();
        for (Noe noe:noes) {
            SpatialSetGroup spg1 = noe.getSpg1();
            SpatialSetGroup spg2 = noe.getSpg2();
            for (var sp1 : spg1.getSpSets()) {
                for (var sp2 : spg2.getSpSets()) {
                    AtomDistancePair atomDistancePair = new AtomDistancePair(sp1.getAtom(), sp2.getAtom());
                    atomDistancePairs.add(atomDistancePair);
                }
            }
        }
        return atomDistancePairs;
    }
    static void writeDistances(MoleculeBase molecule, NoeSet distSet, FileWriter chan) throws IOException {
        String saveFrameName = distSet.getName();
        chan.write("\n");
        chan.write(STAR3Base.SAVE + saveFrameName + "\n"); //fixme dynamically get framecode
        chan.write("    _nef_distance_restraint_list.Sf_category       ");
        chan.write("nef_distance_restraint_list\n");
        chan.write("    _nef_distance_restraint_list.Sf_framecode      ");
        chan.write(saveFrameName + "\n"); //fixme dynamically get framecode
        chan.write("    _nef_distance_restraint_list.potential_type    ");
        chan.write(".\n");
        chan.write("    _nef_distance_restraint_list.restraint_origin  ");
        String restraintOrigin = distSet.containsBonds() ? "bond" : "noe";
        chan.write(restraintOrigin + "\n");
        chan.write("\n");

        chan.write("     loop_\n");
        for (String loopString : DISTANCE_RESTRAINT_LOOP_STRINGS) {
            chan.write("         " + loopString + "\n");
        }
        chan.write("\n");
        molecule.updateAtomArray();
        int idx = 1;
        int restraintID = 1;
        String result;
        for (Map.Entry<Peak, List<Noe>> entry : distSet.getPeakMapEntries()) {
            List<Noe> noes = entry.getValue();
            List<AtomDistancePair> pairAtoms = getAtomDistancePairs(noes);
            int nPairs = pairAtoms.size();
            int[][] collapse = new int[nPairs][2];
            boolean[] skipPair = new boolean[nPairs];
            Set<String> pairNames = new HashSet<>();
            for (AtomDistancePair pair : pairAtoms) {
                Atom atom1 = pair.getAtoms1()[0];
                Atom atom2 = pair.getAtoms2()[0];
                String pairName = atom1.getFullName() + "_" + atom2.getFullName();
                pairNames.add(pairName);
            }

            for (int iPair = 0; iPair < nPairs; iPair++) {
                AtomDistancePair pair = pairAtoms.get(iPair);
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
                    if (!partners.isEmpty()) {
                        int commonLevel = checkPartnerInGroup(atom2, partners, pairNames, true);

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
                    if (!partners.isEmpty()) {
                        int commonLevel = checkPartnerInGroup(atom1, partners, pairNames, false);
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
                AtomDistancePair pair = pairAtoms.get(iPair);
                if (skipPair[iPair]) {
                    continue;
                }
                Atom atom1 = pair.getAtoms1()[0];
                Atom atom2 = pair.getAtoms2()[0];
                result = Atom.toNEFDistanceString(idx, collapse[iPair], restraintID, ".", noes.get(0), atom1, atom2);
                chan.write(result + "\n");
                idx++;

            }
            restraintID++;
        }

        chan.write(
                "     stop_\n");
        chan.write(
                STAR3Base.SAVE + "\n");
    }

    static void writeDihedrals(MoleculeBase molecule, List<AngleConstraint> angleConstraints, FileWriter chan) throws IOException {
        chan.write("\n");
        chan.write(STAR3Base.SAVE + "nef_dihedral_restraint_list\n"); //fixme dynamically get framecode
        chan.write("    _nef_dihedral_restraint_list.Sf_category       ");
        chan.write("nef_dihedral_restraint_list\n");
        chan.write("    _nef_dihedral_restraint_list.Sf_framecode      ");
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
        molecule.updateAtomArray();
        List<AngleConstraint> angleBlock1 = new ArrayList<>();
        List<AngleConstraint> angleBlock2 = new ArrayList<>();
        angleConstraints.forEach(bound -> {
            if (bound.getTargetValue() % 1 == 0 || bound.getTargetValue() % 0.5 == 0) {
                angleBlock1.add(bound);
            } else {
                angleBlock2.add(bound);
            }
        });

        Comparator<AngleConstraint> aCmp = (AngleConstraint bound1, AngleConstraint bound2) -> { //sort atom1 sequence code
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

        angleBlock1.sort(aCmp);
        angleBlock2.sort(aCmp);
        List<List<AngleConstraint>> boundBlocks = new ArrayList<>();
        boundBlocks.add(angleBlock1);
        boundBlocks.add(angleBlock2);
        int i = 1;
        for (List<AngleConstraint> block : boundBlocks) {
            for (AngleConstraint bound : block) {
                Atom[] atoms = bound.getAtoms();
                String result = Atom.toNEFDihedralString(bound, atoms, i, i, ".");
                chan.write(result + "\n");
                i++;
            }
        }
        chan.write("    stop_\n");
        chan.write(STAR3Base.SAVE + "\n");
    }

    /**
     * Write molecular system, chemical shift, distance, and dihedral
     * information to a NEF formatted file.
     *
     * @param fileName String. Name of the file to write.
     * @throws IOException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(String fileName) throws IOException, InvalidMoleculeException {
        File file = new File(fileName);
        writeAll(file);
    }

    /**
     * Write molecular system, chemical shift, distance, and dihedral
     * information to a NEF formatted file.
     *
     * @param file File. File to write.
     * @throws IOException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(File file) throws IOException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(file)) {
            String name = file.getName();
            if (name.endsWith(".nef")) {
                name = name.substring(0, name.length() - 4);
            }
            writeAll(writer, name);
        }
    }

    /**
     * Write molecular system, chemical shift, distance, and dihedral
     * information to a NEF formatted file.
     *
     * @param chan FileWriter. Writer used for writing the file.
     * @param name String. The dataset name.
     * @throws IOException
     * @throws InvalidMoleculeException
     */
    public static void writeAll(FileWriter chan, String name) throws IOException, InvalidMoleculeException {
        Date date = new Date(System.currentTimeMillis());

        String programVersion = NvUtil.getVersion();

        chan.write("data_" + name + "\n\n");
        chan.write(STAR3Base.SAVE + "nef_nmr_meta_data\n");
        chan.write("    _nef_nmr_meta_data.Sf_category           ");
        chan.write("nef_nmr_meta_data\n");
        chan.write("    _nef_nmr_meta_data.Sf_framecode          ");
        chan.write("nef_nmr_meta_data\n");
        chan.write("    _nef_nmr_meta_data.format_name           ");
        chan.write("nmr_exchange_format\n");
        chan.write("    _nef_nmr_meta_data.format_version        ");
        chan.write("1.1\n");
        chan.write("    _nef_nmr_meta_data.program_name          ");
        chan.write("NMRFx" + "\n");
        chan.write("    _nef_nmr_meta_data.program_version       ");
        chan.write(programVersion + "\n");
        chan.write("    _nef_nmr_meta_data.creation_date         ");
        chan.write(STAR3Base.quote(date.toString()) + "\n");
        chan.write("    _nef_nmr_meta_data.uuid                  ");
        chan.write(".\n");
        chan.write("    _nef_nmr_meta_data.coordinate_file_name  ");
        chan.write(".\n");
        chan.write(STAR3Base.SAVE + "\n\n");
        MoleculeBase molecule = MoleculeFactory.getActive();
        if (molecule != null) {
            writeMolSys(molecule, chan);
            writePPM(molecule, chan);
            for (NoeSet distanceSet : molecule.getMolecularConstraints().noeSets()) {
                writeDistances(molecule, distanceSet, chan);
            }
            for (AngleConstraintSet angleSet : molecule.getMolecularConstraints().angleSets()) {
                writeDihedrals(molecule, angleSet.get(), chan);
            }
        }
    }

}
