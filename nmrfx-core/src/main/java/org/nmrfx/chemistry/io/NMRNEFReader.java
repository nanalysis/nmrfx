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

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.Residue.RES_POSITION;
import org.nmrfx.chemistry.constraints.AngleConstraintSet;
import org.nmrfx.chemistry.constraints.DistanceConstraintSet;
import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.STAR3;
import org.nmrfx.star.Saveframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileSystems;
import java.util.*;

/**
 * @author brucejohnson, Martha
 */
@PluginAPI("ring")
public class NMRNEFReader {
    private static final Logger log = LoggerFactory.getLogger(NMRNEFReader.class);

    final STAR3 nef;
    final File nefFile;
    final File nefDir;

    Map entities = new HashMap();
    boolean hasResonances = false;

    public NMRNEFReader(final File nefFile, final STAR3 nef) {
        this.nef = nef;
        this.nefFile = nefFile;
        this.nefDir = nefFile.getAbsoluteFile().getParentFile();
    }

    /**
     * Read a NEF formatted file.
     *
     * @param nefFileName String. Name of the NEF file to read.
     * @throws ParseException
     */
    public static MoleculeBase read(String nefFileName) throws ParseException, IOException {
        File file = new File(nefFileName);
        log.info("read {}", nefFileName);
        return read(file);
    }

    /**
     * Read a NEF formatted file.
     *
     * @param nefFile File. NEF file to read.
     * @throws ParseException
     */
    public static MoleculeBase read(File nefFile) throws ParseException {
        FileReader fileReader;
        try {
            fileReader = new FileReader(nefFile);
        } catch (FileNotFoundException ex) {
            return null;
        }
        BufferedReader bfR = new BufferedReader(fileReader);

        STAR3 star = new STAR3(bfR, "star3");

        try {
            star.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + star.getLastLine());
        }
        NMRNEFReader reader = new NMRNEFReader(nefFile, star);
        return reader.processNEF();
    }

    void buildNEFChains(final Saveframe saveframe, MoleculeBase molecule, final String nomenclature) throws ParseException {
        Loop loop = saveframe.getLoop("_nef_sequence");
        if (loop == null) {
            throw new ParseException("No \"_nef_sequence\" loop");
        } else {
            // fixme ?? NEF specification says index column mandatory, but their xplor example doesn't have it
            List<String> indexColumn = loop.getColumnAsListIfExists("index");
            List<String> chainCodeColumn = loop.getColumnAsList("chain_code");
            List<String> seqCodeColumn = loop.getColumnAsList("sequence_code");
            List<String> residueNameColumn = loop.getColumnAsList("residue_name");
            List<String> linkingColumn = loop.getColumnAsListIfExists("linking");
            List<String> variantColumn = loop.getColumnAsListIfExists("residue_variant");
            addNEFResidues(saveframe, molecule, indexColumn, chainCodeColumn, seqCodeColumn, residueNameColumn, linkingColumn, variantColumn);
        }
    }

    void addNEFResidues(Saveframe saveframe, MoleculeBase molecule, List<String> indexColumn, List<String> chainCodeColumn, List<String> seqCodeColumn, List<String> residueNameColumn, List<String> linkingColumn, List<String> variantColumn) throws ParseException {
        String reslibDir = PDBFile.getReslibDir("IUPAC");
        Polymer polymer = null;
        Compound compound = null;
        Sequence sequence = new Sequence(molecule);
        int entityID = 1;
        String lastChain = "";
        double linkLen = 5.0;
        double valAngle = 120.0;
        double dihAngle = 135.0;
        Polymer lastPolymer = null;
        for (int i = 0; i < chainCodeColumn.size(); i++) {
            String linkType = linkingColumn.get(i);
            if (linkType.equals("dummy")) {
                continue;
            }
            String chainCode = (String) chainCodeColumn.get(i);
            if (chainCode.equals(".")) {
                chainCode = "A";
            }
            int chainID = chainCode.charAt(0) - 'A' + 1;
            String resName = (String) residueNameColumn.get(i);
            String resVariant = (String) variantColumn.get(i);
            String seqCode = (String) seqCodeColumn.get(i);
            String mapID = chainCode + "." + seqCode;
            if (linkType.equals("start")) {
                lastPolymer = polymer;
                polymer = null;
            } else if (linkType.equals("single")) {
                lastPolymer = polymer;
                compound = null;
            }
            if ((!chainCode.equals(lastChain))) {
                lastChain = chainCode;
                if (polymer == null) {
                    sequence.newPolymer();
                    polymer = new Polymer(chainCode, chainCode);
                    polymer.setNomenclature("IUPAC");
                    polymer.setIDNum(entityID);
                    polymer.assemblyID = entityID++;
                    entities.put(chainCode, polymer);
                    molecule.addEntity(polymer, chainCode, chainID);
                    if (lastPolymer != null) {
                        sequence.createLinker(9, linkLen, valAngle, dihAngle);
                        polymer.molecule.genCoords(false);
                        polymer.molecule.setupRotGroups();
                    }
                } else if (compound == null) {
                    compound = new Compound(seqCode, resName, resVariant);
                    compound.molecule = molecule;
                    addCompound(mapID, compound);
                    compound.setIDNum(entityID);
                    compound.assemblyID = entityID;
                    compound.setPropertyObject("chain", chainCode);
                    entities.put(chainCode, compound);
                    molecule.addEntity(compound, chainCode, entityID);
                }

            }
            if (polymer != null && compound == null) {
                try {
                    Residue residue = new Residue(seqCode, resName.toUpperCase(), resVariant);
                    residue.molecule = polymer.molecule;
                    addCompound(mapID, residue);
                    polymer.addResidue(residue);
                    RES_POSITION resPos = RES_POSITION.MIDDLE;
                    if (linkType.equals("start")) {
                        resPos = RES_POSITION.START;
                    } else if (linkType.equals("end")) {
                        resPos = RES_POSITION.END;
                    }
                    String extension = "";
                    if (resVariant.replace("-H3", "").contains("-H")) {
                        extension = "_deprot";
                    } else if (resVariant.replace("+HXT", "").contains("+H")) {
                        extension = "_prot";
                    }
                    if (!sequence.addResidue(reslibDir + "/" + Sequence.getAliased(resName.toLowerCase()) + extension + ".prf", residue, resPos, "", false)) {
                        log.warn("Can't find residue \"{}{}\" in residue libraries or STAR file", resName, extension);
                        try {
                            String cifFile = FileSystems.getDefault().getPath(nefDir.toString(), resName + ".cif").toString();
                            log.info("read residue {} {} from {}", chainCode, seqCode, cifFile);
                            MMcifReader.readChemComp(cifFile, molecule, chainCode, seqCode);
                        } catch (Exception ex) {
                            log.warn(ex.getMessage(), ex);
                        }
                    }
                } catch (MoleculeIOException ex) {
                    log.error(ex.getMessage(), ex);
                }
            } else if (compound != null) {
                String cifFileName = FileSystems.getDefault().getPath(nefDir.toString(), resName + ".cif").toString();
                File cifFile = new File(cifFileName);
                if (!cifFile.exists()) {
                    throw new ParseException("File " + cifFileName + " doesn't exist");
                }
                log.info("read residue {} {} from {}", chainCode, seqCode, cifFile);

                MMcifReader.readChemComp(cifFileName, molecule, chainCode, seqCode);
            }
        }
        if (polymer != null) {
            polymer.molecule.genCoords(false);
            polymer.molecule.setupRotGroups();
        }
        if (compound != null) {
            compound.molecule.genCoords(false);
            compound.molecule.setupRotGroups();
        }
        sequence.removeBadBonds();
    }

    void addCompound(String id, Compound compound) {
        var compoundMap = MoleculeBase.compoundMap();
        compoundMap.put(id, compound);
    }

    void buildNEFChemShifts(MoleculeBase moleculeBase, int fromSet, final int toSet) throws ParseException {
        Iterator iter = nef.getSaveFrames().values().iterator();
        int iSet = 0;
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("nef_chemical_shift_list")) {
                log.debug("process chem shifts {}", saveframe.getName());
                if (fromSet < 0) {
                    processNEFChemicalShifts(moleculeBase, saveframe, iSet);
                } else if (fromSet == iSet) {
                    processNEFChemicalShifts(moleculeBase, saveframe, toSet);
                    break;
                }
                iSet++;
            }
        }
    }

    void buildNEFDihedralConstraints(MoleculeBase molecule) throws ParseException {
        for (Saveframe saveframe : nef.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("nef_dihedral_restraint_list")) {
                log.debug("process nef_dihedral_restraint_list {}", saveframe.getName());
                processNEFDihedralConstraints(saveframe, molecule);
            }
        }
    }

    void buildNEFDistanceRestraints(MoleculeBase molecule) throws ParseException {
        for (Saveframe saveframe : nef.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("nef_distance_restraint_list")) {
                log.debug("process nef_distance_restraint_list {}", saveframe.getName());
                processNEFDistanceRestraints(saveframe, molecule);
            }
        }
    }

    MoleculeBase buildNEFMolecule() throws ParseException {
        MoleculeBase molecule = null;
        for (Saveframe saveframe : nef.getSaveFrames().values()) {
            log.debug(saveframe.getCategoryName());
            if (saveframe.getCategoryName().equals("nef_molecular_system")) {
                log.debug("process molecule >>{}<<", saveframe.getName());
                String molName = "noname";
                molecule = MoleculeFactory.newMolecule(molName);
                buildNEFChains(saveframe, molecule, molName);
                molecule.updateSpatialSets();
                molecule.genCoords(false);
            }
        }
        return molecule;
    }

    void setStereo(List<Atom> atoms, Atom atom, String atomName) {
        if (atom.isMethyl()) {
            if (atoms.size() == 3) {
                if (atomName.contains("x") || atomName.contains("y")) {
                    atom.setStereo(0);
                } else {
                    atom.setStereo(1);
                }
            } else if (atoms.size() == 6) {
                atom.setStereo(0);
            }
        } else {
            if (atomName.contains("x") || atomName.contains("y") || atomName.contains("%")) {
                atom.setStereo(0);
            } else {
                atom.setStereo(1);
            }
        }

    }

    void setPPM(Atom atom, String resIDStr, int ppmSet, String value, String valueErr) throws ParseException {
        ResonanceFactory resFactory = ProjectBase.activeResonanceFactory();
        SpatialSet spSet = atom.spatialSet;
        if (ppmSet < 0) {
            ppmSet = 0;
        }
        int structureNum = ppmSet;
        if (spSet == null) {
            throw new ParseException("invalid spatial set in assignments saveframe \"" + atom.getFullName() + "\"");
        }
        try {
            spSet.setPPM(structureNum, Double.parseDouble(value), false);
            if (!valueErr.equals(".")) {
                spSet.setPPM(structureNum, Double.parseDouble(valueErr), true);
            }
        } catch (NumberFormatException nFE) {
            throw new ParseException("Invalid chemical shift value (not double) \"" + value + "\" error \"" + valueErr + "\"");
        }
        if (hasResonances && !resIDStr.equals(".")) {
            long resID = Long.parseLong(resIDStr);
            if (resID >= 0) {
                AtomResonance resonance = (AtomResonance) resFactory.get(resID);
                if (resonance == null) {
                    throw new ParseException("atom elem resonance " + resIDStr + ": invalid resonance");
                }
                atom.setResonance(resonance);
                resonance.setAtom(atom);
            }
        }
    }

    Compound getCompound(Map<String, Compound> compoundMap, String chainCode, String sequenceCode) {
        String mapID = chainCode + "." + sequenceCode;
        Compound compound = compoundMap.get(mapID);
        if (compound == null) {
            for (int e = 1; e <= entities.size(); e++) {
                chainCode = String.valueOf((char) (e + 'A' - 1));
                mapID = chainCode + "." + sequenceCode;
                compound = compoundMap.get(mapID);
                if (compound != null) {
                    break;
                }
            }
        }
        return compound;
    }

    void processNEFChemicalShifts(MoleculeBase moleculeBase, Saveframe saveframe, int ppmSet) throws ParseException {
        Loop loop = saveframe.getLoop("_nef_chemical_shift");
        if (loop != null) {
            var compoundMap = MoleculeBase.compoundMap();
            List<String> chainCodeColumn = loop.getColumnAsList("chain_code");
            List<String> sequenceCodeColumn = loop.getColumnAsList("sequence_code");
            List<String> resColumn = loop.getColumnAsList("residue_name");
            List<String> atomColumn = loop.getColumnAsList("atom_name");
            List<String> valColumn = loop.getColumnAsList("value");
            List<String> valErrColumn = loop.getColumnAsList("value_uncertainty");
            for (int i = 0; i < chainCodeColumn.size(); i++) {
                String sequenceCode = sequenceCodeColumn.get(i);
                String chainCode = chainCodeColumn.get(i);
                String atomName = atomColumn.get(i);
                String value = valColumn.get(i);
                String valueErr = valErrColumn.get(i);
                String resIDStr = ".";
                if (resColumn != null) {
                    resIDStr = resColumn.get(i);
                }
                Compound compound = getCompound(compoundMap, chainCode, sequenceCode);
                if (compound == null) {
                    log.warn("invalid compound in assignments saveframe \"{} {}\"", chainCode, sequenceCode);
                    continue;
                }
                String fullAtom = chainCode + ":" + sequenceCode + "." + atomName;
                List<Atom> atoms = MoleculeBase.getNEFMatchedAtoms(new MolFilter(fullAtom), moleculeBase);
                for (Atom atom : atoms) {
                    setStereo(atoms, atom, atomName);
                    setPPM(atom, resIDStr, ppmSet, value, valueErr);
                }
            }
        }
    }

    void processNEFDihedralConstraints(Saveframe saveframe, MoleculeBase molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_nef_dihedral_restraint");
        if (loop == null) {
            throw new ParseException("No \"_nef_dihedral_restraint\" loop");
        }
        var compoundMap = MoleculeBase.compoundMap();
        List<String>[] chainCodeColumns = new ArrayList[4];
        List<String>[] sequenceCodeColumns = new ArrayList[4];
        List<String>[] atomNameColumns = new ArrayList[4];

        List<Integer> restraintIDColumn = loop.getColumnAsIntegerList("restraint_id", 0);
        for (int i = 1; i <= 4; i++) {
            chainCodeColumns[i - 1] = loop.getColumnAsList("chain_code_" + i);
            sequenceCodeColumns[i - 1] = loop.getColumnAsList("sequence_code_" + i);
            atomNameColumns[i - 1] = loop.getColumnAsList("atom_name_" + i);
        }
        List<String> weightColumn = loop.getColumnAsList("weight");
        List<String> targetValueColumn = loop.getColumnAsList("target_value");
        List<Double> targetErrColumn = loop.getColumnAsDoubleList("target_value_uncertainty", 0.0);
        List<String> lowerColumn = loop.getColumnAsList("lower_limit");
        List<String> upperColumn = loop.getColumnAsList("upper_limit");
        List<String> nameColumn = loop.getColumnAsListIfExists("name");
        AngleConstraintSet angleSet = molecule.getMolecularConstraints().newAngleSet(saveframe.getName());
        for (int i = 0; i < atomNameColumns[0].size(); i++) {
            int restraintID = restraintIDColumn.get(i);
            String weightValue = (String) weightColumn.get(i);
            String targetValue = (String) targetValueColumn.get(i);
            String upperValue = (String) upperColumn.get(i);
            String lowerValue = (String) lowerColumn.get(i);
            String nameValue = nameColumn != null ? nameColumn.get(i) : "";
            double upper = Double.parseDouble(upperValue);
            double lower = Double.parseDouble(lowerValue);
            if (lower < -180) {
                lower += 360;
                upper += 360;
            }
            double weight = 0.0;
            if (!weightValue.equals(".")) {
                weight = Double.parseDouble(weightValue);
            }
            double target = 0.0;
            if (!targetValue.equals(".")) {
                target = Double.parseDouble(targetValue);
            }
            double targetErr = targetErrColumn.get(i);
            String name = " ";
            if (!nameValue.equals(".")) {
                name = nameValue;
            }
            Atom[] atoms = new Atom[4];
            for (int atomIndex = 0; atomIndex < 4; atomIndex++) {
                String atomName = (String) atomNameColumns[atomIndex].get(i);
                String chainCode = (String) chainCodeColumns[atomIndex].get(i);
                String sequenceCode = (String) sequenceCodeColumns[atomIndex].get(i);
                String fullAtom = chainCode + ":" + sequenceCode + "." + atomName;
                String mapID = chainCode + "." + sequenceCode;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    for (int e = 1; e <= entities.size(); e++) {
                        chainCode = String.valueOf((char) (e + 'A' - 1));
                        mapID = chainCode + "." + sequenceCode;
                        compound = compoundMap.get(mapID);
                        if (compound != null) {
                            fullAtom = chainCode + ":" + sequenceCode + "." + atomName;
                            break;
                        }
                    }
                }
                atoms[atomIndex] = molecule.findAtom(fullAtom);
                if (atoms[atomIndex] == null) {
                    throw new ParseException("Atom not found " + fullAtom);
                }
            }
            double scale = 1.0;
            try {
                angleSet.addAngleConstraint(atoms, lower, upper, scale, weight, target, targetErr, name);
            } catch (InvalidMoleculeException imE) {
                log.warn(imE.getMessage(), imE);
            }

        }
    }

    void processNEFDistanceRestraints(Saveframe saveframe, MoleculeBase molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_nef_distance_restraint");
        if (loop == null) {
            throw new ParseException("No \"_nef_distance_restraint\" loop");
        }
        String origin = saveframe.getValue("_nef_distance_restraint_list", "restraint_origin", "noe");
        var compoundMap = MoleculeBase.compoundMap();
        List<String>[] chainCodeColumns = new ArrayList[2];
        List<String>[] sequenceColumns = new ArrayList[2];
        List<String>[] residueNameColumns = new ArrayList[2];
        List<String>[] atomNameColumns = new ArrayList[2];

        List<Integer> indexColumn = loop.getColumnAsIntegerList("index", 0);
        List<Integer> restraintIDColumn = loop.getColumnAsIntegerList("restraint_id", 0);

        chainCodeColumns[0] = loop.getColumnAsList("chain_code_1");
        sequenceColumns[0] = loop.getColumnAsList("sequence_code_1");
        residueNameColumns[0] = loop.getColumnAsList("residue_name_1");
        atomNameColumns[0] = loop.getColumnAsList("atom_name_1");

        chainCodeColumns[1] = loop.getColumnAsList("chain_code_2");
        sequenceColumns[1] = loop.getColumnAsList("sequence_code_2");
        residueNameColumns[1] = loop.getColumnAsList("residue_name_2");
        atomNameColumns[1] = loop.getColumnAsList("atom_name_2");

        List<Double> weightColumn = loop.getColumnAsDoubleList("weight", 1.0);
        List<String> targetValueColumn = loop.getColumnAsList("target_value");
        List<Double> targetErrColumn = loop.getColumnAsDoubleList("target_value_uncertainty", 0.0);
        List<String> lowerColumn = loop.getColumnAsList("lower_limit");
        List<String> upperColumn = loop.getColumnAsList("upper_limit");
        ArrayList<String> atomNames[] = new ArrayList[2];
        atomNames[0] = new ArrayList<>();
        atomNames[1] = new ArrayList<>();
        DistanceConstraintSet distanceSet = molecule.getMolecularConstraints().newDistanceSet(saveframe.getName());
        if (origin.contains("bond")) {
            distanceSet.containsBonds(true);
        }

        for (int i = 0; i < chainCodeColumns[0].size(); i++) {
            int restraintIDValue = restraintIDColumn.get(i);
            int restraintIDValuePrev = restraintIDValue;
            int restraintIDValueNext = restraintIDValue;
            boolean addConstraint = true;
            if (i >= 1) {
                restraintIDValuePrev = restraintIDColumn.get(i - 1);
            }
            if (i < chainCodeColumns[0].size() - 1) {
                restraintIDValueNext = restraintIDColumn.get(i + 1);
            }
            if (restraintIDValue != restraintIDValuePrev) {
                atomNames[0].clear();
                atomNames[1].clear();
                if (restraintIDValue == restraintIDValueNext
                        && i > 0 && i < chainCodeColumns[0].size() - 1) {
                    addConstraint = false;
                }
            } else if (restraintIDValue == restraintIDValuePrev
                    && restraintIDValue == restraintIDValueNext
                    && i >= 0 && i < chainCodeColumns[0].size() - 1) {
                addConstraint = false;
            }

            for (int iAtom = 0; iAtom < 2; iAtom++) {
                String seqNum = (String) sequenceColumns[iAtom].get(i);
                String chainCode = (String) chainCodeColumns[iAtom].get(i);
                if (chainCode.equals(".")) {
                    chainCode = "A";
                }
                if (seqNum.equals("?")) {
                    continue;
                }
                String resName = (String) residueNameColumns[iAtom].get(i);
                String atomName = (String) atomNameColumns[iAtom].get(i);
                String fullAtomName = chainCode + ":" + seqNum + "." + atomName;
                String mapID = chainCode + "." + seqNum;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    for (int e = 1; e <= entities.size(); e++) {
                        chainCode = String.valueOf((char) (e + 'A' - 1));
                        mapID = chainCode + "." + seqNum;
                        compound = compoundMap.get(mapID);
                        if (compound != null) {
                            fullAtomName = chainCode + ":" + seqNum + "." + atomName;
                            break;
                        }
                    }
                }
                atomNames[iAtom].add(fullAtomName);
            }
            String targetValue = (String) targetValueColumn.get(i);
            String upperValue = (String) upperColumn.get(i);
            String lowerValue = (String) lowerColumn.get(i);
            double upper = 1000000.0;
            if (upperValue.equals(".")) {
                log.warn("Upper value is a \".\" at line {}", i);
            } else {
                upper = Double.parseDouble(upperValue);
            }
            double lower = 1.8;
            if (!lowerValue.equals(".")) {
                lower = Double.parseDouble(lowerValue);
            }
            double weight = weightColumn.get(i);
            double target = 0.0;
            if (!targetValue.equals(".")) {
                target = Double.parseDouble(targetValue);
            }
            double targetErr = targetErrColumn.get(i);

            Util.setStrictlyNEF(true);
            try {
                if (addConstraint) {
                    distanceSet.addDistanceConstraint(atomNames[0], atomNames[1], lower, upper, distanceSet.containsBonds(), weight, target, targetErr);
                }
            } catch (IllegalArgumentException iaE) {
                int index = indexColumn.get(i);
                throw new ParseException("Error parsing NEF distance constraints at index  \"" + index + "\" " + iaE.getMessage());
            }
            Util.setStrictlyNEF(false);
        }
    }

    /**
     * Process a NEF formatted file.
     *
     * @return processNEF(argv)
     * @throws ParseException
     * @throws IllegalArgumentException
     */
    public MoleculeBase processNEF() throws ParseException, IllegalArgumentException {
        String[] argv = {};
        return processNEF(argv);
    }

    /**
     * Process a NEF formatted file.
     *
     * @param argv String[]. List of arguments. Default is empty.
     * @return Dihedral object.
     * @throws ParseException
     * @throws IllegalArgumentException
     */
    public MoleculeBase processNEF(String[] argv) throws ParseException, IllegalArgumentException {
        if ((argv.length != 0) && (argv.length != 3)) {
            throw new IllegalArgumentException("?shifts fromSet toSet?");
        }

        MoleculeBase molecule = null;
        if (argv.length == 0) {
            hasResonances = false;
            var compoundMap = MoleculeBase.compoundMap();
            compoundMap.clear();
            log.debug("process molecule");
            molecule = buildNEFMolecule();
            ProjectBase.getActive().putMolecule(molecule);
            log.debug("process chem shifts");
            buildNEFChemShifts(molecule, -1, 0);
            log.debug("process dist constraints");
            buildNEFDistanceRestraints(molecule);
            log.warn("process angle constraints");
            buildNEFDihedralConstraints(molecule);
        } else if ("shifts".startsWith(argv[2])) {
            int fromSet = Integer.parseInt(argv[3]);
            int toSet = Integer.parseInt(argv[4]);
            MoleculeBase moleculeBase = MoleculeFactory.getActive();
            buildNEFChemShifts(moleculeBase, fromSet, toSet);
        }
        return molecule;
    }

}
