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
import org.nmrfx.chemistry.protein.ProteinHelix;
import org.nmrfx.chemistry.protein.Sheet;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.Loop;
import org.nmrfx.star.MMCIF;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

@PluginAPI("ring")
public class MMcifReader {
    private static final Logger log = LoggerFactory.getLogger(MMcifReader.class);
    private static final String INVALID_ATOM_WARN_MSG_TEMPLATE = "invalid atom in chem comp atom saveframe \"{}.{}\"";

    final MMCIF mmcif;
    final File cifFile;

    Map<String, Entity> entities = new HashMap<>();
    boolean hasResonances = false;
    Map<String, Character> chainCodeMap = new HashMap<>();
    Map<Integer, MMCIFEntity> entityMap = new HashMap<>();


    public MMcifReader(final File cifFile, final MMCIF star3) {
        this.mmcif = star3;
        this.cifFile = cifFile;
        for (int i = 0; i <= 25; i++) {
            chainCodeMap.put(String.valueOf(i + 1), (char) ('A' + i));
        }
    }

    public static MoleculeBase read(String cifFileName) throws ParseException {
        File file = new File(cifFileName);
        return read(file);
    }

    public static MoleculeBase read(File cifFile) throws ParseException {
        FileReader fileReader;
        try {
            fileReader = new FileReader(cifFile);
        } catch (FileNotFoundException ex) {
            return null;
        }
        BufferedReader bfR = new BufferedReader(fileReader);

        MMCIF cif = new MMCIF(bfR, "mmcif");

        try {
            cif.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + cif.getLastLine());
        }
        MMcifReader reader = new MMcifReader(cifFile, cif);
        return reader.process();
    }

    public static void readChemComp(String cifFileName, MoleculeBase molecule, String chainCode, String sequenceCode) throws ParseException {
        File file = new File(cifFileName);
        readChemComp(file, molecule, chainCode, sequenceCode);
    }

    public static void readChemComp(File cifFile, MoleculeBase molecule, String chainCode, String sequenceCode) throws ParseException {
        FileReader fileReader;
        try {
            fileReader = new FileReader(cifFile);
        } catch (FileNotFoundException ex) {
            return;
        }
        BufferedReader bfR = new BufferedReader(fileReader);

        MMCIF cif = new MMCIF(bfR, "mmcif");

        try {
            cif.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + cif.getLastLine());
        }
        MMcifReader reader = new MMcifReader(cifFile, cif);
        reader.processChemComp(molecule, chainCode, sequenceCode);

    }

    void buildChains(final Saveframe saveframe, MMCIFPolymerEntity entity) throws ParseException {
        Loop loop = saveframe.getLoop("_entity_poly_seq");
        if (loop == null) {
            throw new ParseException("No \"_entity_poly_seq\" loop");
        } else {
            List<Integer> entityIDColumn = loop.getColumnAsIntegerList("entity_id", -1);
            List<Integer> numColumn = loop.getColumnAsIntegerList("num", 0);
            List<String> resNameColumn = loop.getColumnAsList("mon_id");
            List<String> heteroColumn = loop.getColumnAsListIfExists("hetero");

            for (int i = 0; i < numColumn.size(); i++) {
                if (entityIDColumn.get(i) == entity.id) {
                    entity.add(numColumn.get(i), resNameColumn.get(i), heteroColumn.get(i).equals("y"));
                }
            }
        }
    }

    class MMCIFEntity {

        int id;
        String type;

        public MMCIFEntity(int id, String type) {
            this.id = id;
            this.type = type;
        }

        @Override
        public String toString() {
            return id + " " + type;
        }

        public void build(MoleculeBase molecule, String asymName) throws ParseException {
            String mapID = asymName + "." + "0";
            Compound ligand = new Compound("0", asymName);
            ligand.molecule = molecule;
            addCompound(mapID, ligand);
            ligand.setIDNum(id);
            ligand.assemblyID = id;
            entities.put(asymName, ligand);
            molecule.addEntity(ligand, asymName, id);
        }
    }

    class MMCIFPolymerEntity extends MMCIFEntity {

        List<Integer> numbers = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Boolean> hetero = new ArrayList<>();

        public MMCIFPolymerEntity(int id, String type) {
            super(id, type);
        }

        public void addChain(List<Integer> numColumn, List<String> nameColumn, List<Boolean> heteroColumn) {
            numbers.addAll(numColumn);
            names.addAll(nameColumn);
            hetero.addAll(heteroColumn);

        }

        public void add(Integer num, String name, boolean isHetero) {
            numbers.add(num);
            names.add(name);
            hetero.add(isHetero);
        }

        @Override
        public void build(MoleculeBase molecule, String asymName) throws ParseException {
            String reslibDir = PDBFile.getReslibDir("IUPAC");
            Sequence sequence = new Sequence(molecule);
            sequence.newPolymer();
            Polymer polymer = new Polymer(asymName, asymName);
            polymer.setNomenclature("IUPAC");
            polymer.setIDNum(id);
            polymer.assemblyID = id;
            entities.put(asymName, polymer);
            molecule.addEntity(polymer, asymName, id);
            for (int i = 0; i < numbers.size(); i++) {
                String resName = names.get(i);
                String iRes = numbers.get(i).toString();
                boolean hetBool = hetero.get(i);
                String hetStr = "n";
                if (hetBool) {
                    hetStr = "y";
                }
                Residue residue = new Residue(iRes, resName.toUpperCase(), hetStr);
                residue.molecule = polymer.molecule;
                String mapID = asymName + "." + iRes;
                addCompound(mapID, residue);
                polymer.addResidue(residue);
                Residue.RES_POSITION resPos = Residue.RES_POSITION.MIDDLE;
                if (i == 0) {
                    resPos = Residue.RES_POSITION.START;
                } else if (i == numbers.size() - 1) {
                    resPos = Residue.RES_POSITION.END;
                }

                try {
                    String extension = "";
                    if (resName.equals("HIS")) {
                        extension += "_prot";
                    }
                    if (!sequence.addResidue(reslibDir + "/" + Sequence.getAliased(resName.toLowerCase()) + extension + ".prf", residue, resPos, "", false)) {
                        log.warn("Can't find residue \"{}{}\" in residue libraries or STAR file", resName, extension);
                    }
                } catch (MoleculeIOException psE) {
                    throw new ParseException(psE.getMessage());
                }
            }
        }
    }

    void buildEntities(final Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_entity");

        if (loop == null) {
            String type = saveframe.getValue("_entity", "type");
            int entityID = saveframe.getIntegerValue("_entity", "id");
            MMCIFEntity entity;
            if (type.equals("polymer")) {
                entity = new MMCIFPolymerEntity(entityID, type);
                buildChains(saveframe, (MMCIFPolymerEntity) entity);
            } else {
                entity = new MMCIFEntity(entityID, type);
            }
            entityMap.put(entityID, entity);

        } else {
            List<String> typeColumn = loop.getColumnAsList("type");
            List<Integer> entityIDColumn = loop.getColumnAsIntegerList("id", -1);
            for (int i = 0; i < typeColumn.size(); i++) {
                String type = typeColumn.get(i);
                int entityID = entityIDColumn.get(i);
                MMCIFEntity entity;
                if (type.equals("polymer")) {
                    entity = new MMCIFPolymerEntity(entityID, type);
                    buildChains(saveframe, (MMCIFPolymerEntity) entity);
                } else {
                    entity = new MMCIFEntity(entityID, type);
                }
                entityMap.put(entityID, entity);
            }
        }

    }

    void buildAsym(final Saveframe saveframe, MoleculeBase molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_struct_asym");
        final List<String> asymIDColumn;
        final List<Integer> entityIDColumn;
        if (loop == null) {
            asymIDColumn = new ArrayList<>();
            entityIDColumn = new ArrayList<>();
            String asymID = saveframe.getValue("_struct_asym", "id");
            Integer entityID = saveframe.getIntegerValue("_struct_asym", "entity_id");
            asymIDColumn.add(asymID);
            entityIDColumn.add(entityID);
        } else {
            asymIDColumn = loop.getColumnAsList("id");
            entityIDColumn = loop.getColumnAsIntegerList("entity_id", -1);
        }
        for (int i = 0; i < asymIDColumn.size(); i++) {
            String asymID = asymIDColumn.get(i);
            int entityID = entityIDColumn.get(i);
            MMCIFEntity entity = entityMap.get(entityID);
            if (entity instanceof MMCIFPolymerEntity polymerEntity) {
                polymerEntity.build(molecule, asymID);
            } else {
                entity.build(molecule, asymID);
            }
        }
    }

    //Note: currently not used.
    void addResidues(Saveframe saveframe, MoleculeBase molecule, List<String> entityIDColumn, List<String> numColumn, List<String> monIDColumn, List<String> heteroColumn) throws ParseException {
        String reslibDir = PDBFile.getReslibDir("IUPAC");
        Polymer polymer = null;
        Sequence sequence = new Sequence(molecule);
        int entityID = 1;
        String lastChain = "";
        double linkLen = 5.0;
        double valAngle = 90.0;
        double dihAngle = 135.0;
        for (int i = 0; i < entityIDColumn.size(); i++) {
            String chainCode = String.valueOf(chainCodeMap.get(entityIDColumn.get(i)));
            if (chainCode.equals(".")) {
                chainCode = "A";
            }
            int chainID = chainCode.charAt(0) - 'A' + 1;
            if ((polymer == null) || (!chainCode.equals(lastChain))) {
                lastChain = chainCode;
                if (polymer != null) {
                    sequence.createLinker(9, linkLen, valAngle, dihAngle);
                    polymer.molecule.genCoords(false);
                    polymer.molecule.setupRotGroups();
                }
                sequence.newPolymer();
                polymer = new Polymer(chainCode, chainCode);
                polymer.setNomenclature("IUPAC");
                polymer.setIDNum(entityID);
                polymer.assemblyID = entityID++;
                entities.put(chainCode, polymer);
                molecule.addEntity(polymer, chainCode, chainID);

            }
            String resName = monIDColumn.get(i);
            String iRes = numColumn.get(i);
            String mapID = chainCode + "." + iRes;
            String hetero = heteroColumn.get(i);
            Residue residue = new Residue(iRes, resName.toUpperCase(), hetero);
            residue.molecule = polymer.molecule;
            addCompound(mapID, residue);
            polymer.addResidue(residue);
            Residue.RES_POSITION resPos = Residue.RES_POSITION.MIDDLE;
            if (i == 0) {
                resPos = Residue.RES_POSITION.START;
            } else if (i == entityIDColumn.size() - 1) {
                resPos = Residue.RES_POSITION.END;
            }

            try {
                String extension = "";
                if (resName.equals("HIS")) {
                    extension += "_prot";
                }
                if (!sequence.addResidue(reslibDir + "/" + Sequence.getAliased(resName.toLowerCase()) + extension + ".prf", residue, resPos, "", false)) {
                    throw new ParseException("Can't find residue \"" + resName + extension + "\" in residue libraries or STAR file");
                }
            } catch (MoleculeIOException psE) {
                throw new ParseException(psE.getMessage());
            }

        }
        if (polymer != null) {
            polymer.molecule.genCoords(false);
            polymer.molecule.setupRotGroups();
        }
        sequence.removeBadBonds();
    }

    void addCompound(String id, Compound compound) {
        MoleculeBase.compoundMap().put(id, compound);
    }

    MoleculeBase buildConformation(final Saveframe saveframe, MoleculeBase molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_struct_conf");
        if (loop != null) {
            List<String> idColumn = loop.getColumnAsList("id");
            List<String> begAsymIDColumn = loop.getColumnAsList("beg_label_asym_id");
            List<Integer> begSeqIDColumn = loop.getColumnAsIntegerList("beg_label_seq_id", 1);
            List<Integer> endSeqIDColumn = loop.getColumnAsIntegerList("end_label_seq_id", 1);
            for (int i = 0; i < idColumn.size(); i++) {
                int iFirstRes = begSeqIDColumn.get(i) - 1;
                int iLastRes = endSeqIDColumn.get(i) - 1;
                String asymID = begAsymIDColumn.get(i);
                Polymer polymer = (Polymer) molecule.getEntity(asymID);
                Residue firstRes = polymer.getResidue(iFirstRes);
                Residue lastRes = polymer.getResidue(iLastRes);
                molecule.addSecondaryStructure(new ProteinHelix(firstRes, lastRes));
            }
        }
        return molecule;
    }

    void buildSheetRange(final Saveframe saveframe, MoleculeBase molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_struct_sheet_range");
        if (loop != null) {
            List<String> idColumn = loop.getColumnAsList("id");
            List<String> begAsymIDColumn = loop.getColumnAsList("beg_label_asym_id");
            List<Integer> begSeqIDColumn = loop.getColumnAsIntegerList("beg_label_seq_id", 1);
            List<Integer> endSeqIDColumn = loop.getColumnAsIntegerList("end_label_seq_id", 1);
            for (int i = 0; i < idColumn.size(); i++) {
                int iFirstRes = begSeqIDColumn.get(i) - 1;
                int iLastRes = endSeqIDColumn.get(i) - 1;
                String asymID = begAsymIDColumn.get(i);
                Polymer polymer = (Polymer) molecule.getEntity(asymID);
                Residue firstRes = polymer.getResidue(iFirstRes);
                Residue lastRes = polymer.getResidue(iLastRes);
                molecule.addSecondaryStructure(new Sheet(firstRes, lastRes));
            }
        }
    }

    void buildAtomSites(MoleculeBase molecule, int fromSet, final int toSet) throws ParseException {
        Iterator<Saveframe> iter = mmcif.getSaveFrames().values().iterator();
        int iSet = 0;
        while (iter.hasNext()) {
            Saveframe saveframe = iter.next();
            log.debug("process atom sites {}", saveframe.getName());
            if (fromSet < 0) {
                molecule.nullCoords(iSet);
                processAtomSites(molecule, saveframe, iSet);
            } else if (fromSet == iSet) {
                molecule.nullCoords(toSet);
                processAtomSites(molecule, saveframe, toSet);
                break;
            }
            iSet++;
        }
    }

    MoleculeBase buildMolecule() throws ParseException {
        MoleculeBase molecule = null;
        for (Saveframe saveframe : mmcif.getSaveFrames().values()) {
            log.debug(saveframe.getCategoryName());
            log.debug("process molecule >>{}<<", saveframe.getName());
            String molName = "noname";
            molecule = MoleculeFactory.newMolecule(molName);
            buildEntities(saveframe);
            buildAsym(saveframe, molecule);
            molecule = buildConformation(saveframe, molecule);
            buildSheetRange(saveframe, molecule);
            molecule.updateSpatialSets();
            molecule.genCoords(false);
        }
        return molecule;
    }

    void buildChemCompAtom(int fromSet, final int toSet, MoleculeBase molecule, String chainCode, String sequenceCode) throws ParseException {
        Iterator<Saveframe> iter = mmcif.getSaveFrames().values().iterator();
        int iSet = 0;
        while (iter.hasNext()) {
            Saveframe saveframe = iter.next();
            log.debug("process chem comp atom {}", saveframe.getName());
            if (fromSet < 0) {
                processChemCompAtom(saveframe, iSet, molecule, chainCode, sequenceCode);
            } else if (fromSet == iSet) {
                processChemCompAtom(saveframe, toSet, molecule, chainCode, sequenceCode);
                break;
            }
            iSet++;
        }
    }

    void buildChemCompBond(int fromSet, final int toSet, MoleculeBase molecule, String chainCode, String sequenceCode) throws ParseException {
        Iterator<Saveframe> iter = mmcif.getSaveFrames().values().iterator();
        int iSet = 0;
        while (iter.hasNext()) {
            Saveframe saveframe = iter.next();
            log.debug("process chem comp bond {}", saveframe.getName());
            if (fromSet < 0) {
                processChemCompBond(saveframe, iSet, molecule, chainCode, sequenceCode);
            } else if (fromSet == iSet) {
                processChemCompBond(saveframe, toSet, molecule, chainCode, sequenceCode);
                break;
            }
            iSet++;
        }
    }

    void processAtomSites(MoleculeBase molecule, Saveframe saveframe, int ppmSet) throws ParseException {
        Loop loop = saveframe.getLoop("_atom_site");
        var compoundMap = MoleculeBase.compoundMap();
        if (loop != null) {
            List<String> typeSymbolColumn = loop.getColumnAsList("type_symbol");
            List<String> labelAtomIDColumn = loop.getColumnAsList("label_atom_id");
            List<String> labelAltIDColumn = loop.getColumnAsList("label_alt_id");
            List<String> labelCompIDColumn = loop.getColumnAsList("label_comp_id");
            List<String> labelAsymIDColumn = loop.getColumnAsList("label_asym_id");
            List<String> labelEntityIDColumn = loop.getColumnAsList("label_entity_id");
            List<String> labelSeqIDColumn = loop.getColumnAsList("label_seq_id");
            List<String> pdbInsCodeColumn = loop.getColumnAsList("pdbx_PDB_ins_code");
            List<String> cartnXColumn = loop.getColumnAsList("Cartn_x");
            List<String> cartnYColumn = loop.getColumnAsList("Cartn_y");
            List<String> cartnZColumn = loop.getColumnAsList("Cartn_z");
            List<String> occupancyColumn = loop.getColumnAsList("occupancy");
            List<String> bIsoColumn = loop.getColumnAsList("B_iso_or_equiv");
            List<Double> pdbFormalChargeColumn = loop.getColumnAsDoubleList("pdbx_formal_charge", 0.0);
            List<Integer> authSeqIDColumn = loop.getColumnAsIntegerList("auth_seq_id", 0);
            List<String> authCompIDColumn = loop.getColumnAsList("auth_comp_id");
            List<String> authAsymIDColumn = loop.getColumnAsList("auth_asym_id");
            List<String> authAtomIDColumn = loop.getColumnAsList("auth_atom_id");
            List<Integer> pdbModelNumColumn = loop.getColumnAsIntegerList("pdbx_PDB_model_num", 0);

            for (int i = 0; i < typeSymbolColumn.size(); i++) {
                String atomType = typeSymbolColumn.get(i);
                String atomName = labelAtomIDColumn.get(i);
                String atomAltName = labelAltIDColumn.get(i);
                String resIDStr = ".";
                if (labelCompIDColumn != null) {
                    resIDStr = labelCompIDColumn.get(i);
                }
                String chainCode = labelAsymIDColumn.get(i);
                String entityID = labelEntityIDColumn.get(i);
                String sequenceCode = labelSeqIDColumn.get(i);
                if (sequenceCode.equals(".")) {
                    sequenceCode = "0";
                }
                int pdbModelNum = pdbModelNumColumn.get(i);
                float xCoord = Float.parseFloat(cartnXColumn.get(i));
                float yCoord = Float.parseFloat(cartnYColumn.get(i));
                float zCoord = Float.parseFloat(cartnZColumn.get(i));
                float occupancy = Float.parseFloat(occupancyColumn.get(i));
                float bFactor = Float.parseFloat(bIsoColumn.get(i));
                String mapID = chainCode + "." + sequenceCode;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    log.warn("invalid compound in assignments saveframe \"{}\"", mapID);
                    continue;
                }
                String fullAtom = chainCode + ":" + sequenceCode + "." + atomName;

                if (!molecule.structures.contains(pdbModelNum - 1)) {
                    molecule.structures.add(pdbModelNum - 1);
                    molecule.setActiveStructures();
                }

                Atom atom = molecule.findAtom(fullAtom);

                String pdbInsCode = pdbInsCodeColumn.get(i);
                int authSeq = authSeqIDColumn.get(i);
                String authComp = authCompIDColumn.get(i);
                String authAsym = authAsymIDColumn.get(i);
                String authAtom = authAtomIDColumn.get(i);

                if (atom == null) {
                    atom = Atom.genAtomWithElement(atomName, atomType);
                    if (compound.label.equals(chainCode)) {
                        compound.label = resIDStr;
                    }
                    compound.addAtom(atom);
                    compound.updateNames();
                    atom.setAtomicNumber(atomType);
                }

                Entity entity = atom.getEntity();
                entity.setPropertyObject("pdbInsCode", pdbInsCode);
                entity.setPropertyObject("authSeqID", authSeq);
                entity.setPropertyObject("authResName", authComp);
                entity.setPropertyObject("authChainCode", authAsym);

                atom.setProperty("authAtomName", authAtom);

                SpatialSet spSet = atom.getSpatialSet();
                if (molecule.getActiveStructures().length == 1) {
                    spSet.clearCoords();
                }

                if (spSet == null) {
                    throw new ParseException("invalid spatial set in assignments saveframe \"" + mapID + "." + atomName + "\"");
                }

                try {
                    atom.addCoords(xCoord, yCoord, zCoord, occupancy, bFactor);
                } catch (InvalidMoleculeException imE) {
                    log.warn(imE.getMessage(), imE);
                }
            }
        }
        molecule.updateAtomArray();
    }

    void processChemCompAtom(Saveframe saveframe, int ppmSet, MoleculeBase molecule, String chainCode, String sequenceCode) throws ParseException {
        Loop loop = saveframe.getLoop("_chem_comp_atom");
        if (loop != null) {
            var compoundMap = MoleculeBase.compoundMap();

            List<String> typeSymbolColumn = loop.getColumnAsList("type_symbol");
            List<String> atomIDColumn = loop.getColumnAsList("atom_id");
            List<String> labelAltIDColumn = loop.getColumnAsList("alt_atom_id");
            List<String> compIDColumn = loop.getColumnAsList("comp_id");
            List<String> cartnXColumn = loop.getColumnAsList("model_Cartn_x");
            List<String> cartnYColumn = loop.getColumnAsList("model_Cartn_y");
            List<String> cartnZColumn = loop.getColumnAsList("model_Cartn_z");
            List<String> cartnXIdealColumn = loop.getColumnAsList("pdbx_model_Cartn_x_ideal");
            List<String> cartnYIdealColumn = loop.getColumnAsList("pdbx_model_Cartn_y_ideal");
            List<String> cartnZIdealColumn = loop.getColumnAsList("pdbx_model_Cartn_z_ideal");

            for (int i = 0; i < typeSymbolColumn.size(); i++) {
                String atomType = typeSymbolColumn.get(i);
                String atomName = atomIDColumn.get(i);
                String atomAltName = labelAltIDColumn.get(i);
                String resIDStr = ".";
                if (compIDColumn != null) {
                    resIDStr = compIDColumn.get(i);
                }

                float xCoord = Float.parseFloat(cartnXColumn.get(i));
                float yCoord = Float.parseFloat(cartnYColumn.get(i));
                float zCoord = Float.parseFloat(cartnZColumn.get(i));
                float occupancy = Float.parseFloat("1.0");
                float bFactor = Float.parseFloat("0.0");
                String mapID = chainCode + "." + sequenceCode;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    log.warn("invalid compound in chem comp atom saveframe \"{}\"", mapID);
                    continue;
                }
                String fullAtom = chainCode + ":" + sequenceCode + "." + atomName;

                Atom atom = molecule.findAtom(fullAtom);

                if (atom == null) {
                    atom = Atom.genAtomWithElement(atomName, atomType);
                    atom.setAtomicNumber(atomType);
                    compound.addAtom(atom);
                    molecule.updateAtomArray();
                }

                SpatialSet spSet = atom.getSpatialSet();
                if (molecule.getActiveStructures().length == 1) {
                    spSet.clearCoords();
                }

                if (spSet == null) {
                    throw new ParseException("invalid spatial set in chem comp atom saveframe \"" + mapID + "." + atomName + "\"");
                }

                try {
                    atom.addCoords(xCoord, yCoord, zCoord, occupancy, bFactor);
                } catch (InvalidMoleculeException imE) {
                    log.warn(imE.getMessage(), imE);
                }
            }
        }
    }

    void processChemCompBond(Saveframe saveframe, int ppmSet, MoleculeBase molecule, String chainCode, String sequenceCode) throws ParseException {
        Loop loop = saveframe.getLoop("_chem_comp_bond");
        if (loop != null) {
            var compoundMap = MoleculeBase.compoundMap();
            List<String> idColumn = loop.getColumnAsList("comp_id");
            List<String> atom1IDColumn = loop.getColumnAsList("atom_id_1");
            List<String> atom2IDColumn = loop.getColumnAsList("atom_id_2");
            List<String> bondOrderColumn = loop.getColumnAsList("value_order");
            List<String> aromaticFlagColumn = loop.getColumnAsList("pdbx_aromatic_flag");
            List<String> stereoConfigColumn = loop.getColumnAsList("pdbx_stereo_config");

            for (int i = 0; i < atom1IDColumn.size(); i++) {
                String idCode = idColumn.get(i);
                String atom1Name = atom1IDColumn.get(i);
                String atom2Name = atom2IDColumn.get(i);
                String bondOrder = bondOrderColumn.get(i);
                String aromaticFlag = aromaticFlagColumn.get(i);
                String stereoConfig = stereoConfigColumn.get(i);

                String mapID = chainCode + "." + sequenceCode;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    log.warn("invalid compound in chem comp bond saveframe \"{}\"", mapID);
                    continue;
                }
                String fullAtom1 = chainCode + ":" + sequenceCode + "." + atom1Name;
                String fullAtom2 = chainCode + ":" + sequenceCode + "." + atom2Name;

                Atom parent = molecule.findAtom(fullAtom1);
                Atom refAtom = molecule.findAtom(fullAtom2);

                if (parent == null) {
                    log.warn(INVALID_ATOM_WARN_MSG_TEMPLATE, mapID, atom1Name);
                }
                if (refAtom == null) {
                    log.warn(INVALID_ATOM_WARN_MSG_TEMPLATE, mapID, atom2Name);
                }

                if (parent != null && refAtom != null) {
                    final Order order;
                    switch (bondOrder) {
                        case "DOUB" -> order = Order.DOUBLE;
                        case "TRIP" -> order = Order.TRIPLE;
                        case "QUAD" -> order = Order.QUAD;
                        default -> order = Order.SINGLE;
                    }
                    Bond bond = new Bond(refAtom, parent, order);
                    refAtom.parent = parent;
                    refAtom.addBond(bond);
                    Bond bond2 = new Bond(parent, refAtom, order);
                    parent.addBond(bond2);
                    compound.addBond(bond2);
                    molecule.updateBondArray();
                }
            }
        }
    }

    void processChemComp(MoleculeBase molecule, String chainCode, String sequenceCode) throws ParseException, IllegalArgumentException {
        String[] argv = {};
        processChemComp(argv, molecule, chainCode, sequenceCode);
    }

    public void processChemComp(String[] argv, MoleculeBase molecule, String chainCode, String sequenceCode) throws ParseException, IllegalArgumentException {
        if ((argv.length != 0) && (argv.length != 5)) {
            throw new IllegalArgumentException("?shifts fromSet toSet?");
        }

        if (argv.length == 0) {
            hasResonances = false;

            log.debug("process chem comp atom");
            buildChemCompAtom(-1, 0, molecule, chainCode, sequenceCode);
            log.debug("process chem comp bond");
            buildChemCompBond(-1, 0, molecule, chainCode, sequenceCode);

        } else if ("shifts".startsWith(argv[2])) {
            int fromSet = Integer.parseInt(argv[3]);
            int toSet = Integer.parseInt(argv[4]);
            buildChemCompAtom(fromSet, toSet, molecule, chainCode, sequenceCode);
            buildChemCompBond(fromSet, toSet, molecule, chainCode, sequenceCode);
        }
    }

    MoleculeBase process() throws ParseException, IllegalArgumentException {
        String[] argv = {};
        return process(argv);
    }

    public MoleculeBase process(String[] argv) throws ParseException, IllegalArgumentException {
        if ((argv.length != 0) && (argv.length != 5)) {
            throw new IllegalArgumentException("?shifts fromSet toSet?");
        }
        var compoundMap = MoleculeBase.compoundMap();
        MoleculeBase molecule = null;

        if (argv.length == 0) {
            hasResonances = false;
            compoundMap.clear();
            log.debug("process molecule");
            molecule = buildMolecule();
            molecule.setMethylRotationActive(true);

            log.debug("process atom sites");
            buildAtomSites(molecule, -1, 0);
            molecule.fillEntityCoords();

            ProjectBase.getActive().putMolecule(molecule);

        } else if ("shifts".startsWith(argv[2])) {
            int fromSet = Integer.parseInt(argv[3]);
            int toSet = Integer.parseInt(argv[4]);
            molecule = MoleculeFactory.getActive();
            buildAtomSites(molecule, fromSet, toSet);
        }
        return molecule;

    }

}
