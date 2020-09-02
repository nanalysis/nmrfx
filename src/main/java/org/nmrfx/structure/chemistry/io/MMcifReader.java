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

import org.nmrfx.processor.star.ParseException;
import java.io.*;
import java.util.*;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.star.Loop;
import org.nmrfx.processor.star.MMCIF;
import org.nmrfx.processor.star.Saveframe;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Compound;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.Helix;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.NonLoop;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.SpatialSet;
import org.nmrfx.structure.chemistry.energy.AngleProp;
import org.nmrfx.structure.chemistry.energy.Dihedral;
import org.nmrfx.structure.chemistry.energy.EnergyLists;
import static org.nmrfx.structure.chemistry.io.NMRNEFReader.DEBUG;
import org.nmrfx.structure.utilities.Util;

public class MMcifReader {

    final MMCIF mmcif;
    final File starFile;

    Map entities = new HashMap();
    boolean hasResonances = false;
    Map<Long, List<PeakDim>> resMap = new HashMap<>();
    Map<String, Character> chainCodeMap = new HashMap<>();
    Map<Integer, MMCIFEntity> entityMap = new HashMap<>();

    public MMcifReader(final File starFile, final MMCIF star3) {
        this.mmcif = star3;
        this.starFile = starFile;
        for (int i = 0; i <= 25; i++) {
            chainCodeMap.put(String.valueOf(i + 1), (char) ('A' + i));
        }
//        PeakDim.setResonanceFactory(new AtomResonanceFactory());
    }

    public static void read(String starFileName) throws ParseException {
        File file = new File(starFileName);
        read(file);
    }

    public static void read(File starFile) throws ParseException {
        FileReader fileReader;
        try {
            fileReader = new FileReader(starFile);
        } catch (FileNotFoundException ex) {
            return;
        }
        BufferedReader bfR = new BufferedReader(fileReader);

        MMCIF star = new MMCIF(bfR, "mmcif");

        try {
            star.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + star.getLastLine());
        }
        MMcifReader reader = new MMcifReader(starFile, star);
        reader.process();

    }

    void buildChains(final Saveframe saveframe, Molecule molecule, final String nomenclature, MMCIFPolymerEntity entity) throws ParseException {
        Loop loop = saveframe.getLoop("_entity_poly_seq");
        if (loop == null) {
            throw new ParseException("No \"_entity_poly_seq\" loop");
        } else {
            List<Integer> entityIDColumn = loop.getColumnAsIntegerList("entity_id", -1);
            List<Integer> numColumn = loop.getColumnAsIntegerList("num", 0);
            List<String> resNameColumn = loop.getColumnAsList("mon_id");
            List<String> heteroColumn = loop.getColumnAsListIfExists("hetero");

            List<Integer> startAndEnds = new ArrayList<>();
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

        public String toString() {
            return id + " " + type;
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

        public void build(Molecule molecule, String asymName) throws ParseException {
            String reslibDir = PDBFile.getReslibDir("IUPAC");
            Sequence sequence = new Sequence(molecule);
            sequence.newPolymer();
            Polymer polymer = new Polymer(asymName, asymName);
            polymer.setNomenclature("IUPAC");
            polymer.setIDNum(id);
            polymer.assemblyID = id;
            entities.put(asymName, polymer);
            molecule.addEntity(polymer, asymName, id);
//            System.out.println("mol build poly " + molecule.getPolymers().size() + " " + numbers.size());
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
                Sequence.RES_POSITION resPos = Sequence.RES_POSITION.MIDDLE;
                if (i == 0) {
                    resPos = Sequence.RES_POSITION.START;
                } else if (i == numbers.size() - 1) {
                    resPos = Sequence.RES_POSITION.END;
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
        }

    }
    
    void buildEntities(final Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_entity");
//        System.out.println("build entities " + loop);

        if (loop == null) {
            String type = saveframe.getValue("_entity", "type");
            Integer entityID = saveframe.getIntegerValue("_entity", "id");
            MMCIFEntity entity;
            if (type.equals("polymer")) {
                entity = new MMCIFPolymerEntity(entityID, type);
                buildChains(saveframe, Molecule.activeMol, type, (MMCIFPolymerEntity) entity);
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
                    buildChains(saveframe, Molecule.activeMol, type, (MMCIFPolymerEntity) entity);
                } else {
                    entity = new MMCIFEntity(entityID, type);
                }
                entityMap.put(entityID, entity);
            }
        }
//        System.out.println("asym info " + entityMap.toString());

    }
    
    void buildAsym(final Saveframe saveframe, Molecule molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_struct_asym");
//        System.out.println("build asym " + loop);
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
            if (entity instanceof MMCIFPolymerEntity) {
                MMCIFPolymerEntity polymerEntity = (MMCIFPolymerEntity) entity;
                polymerEntity.build(molecule, asymID);
            }
        }
//        System.out.println("asym info " + asymIDColumn.toString() + " " + entityIDColumn.toString());
    }

    void addResidues(Saveframe saveframe, Molecule molecule, List<String> entityIDColumn, List<String> numColumn, List<String> monIDColumn, List<String> heteroColumn) throws ParseException {
        String reslibDir = PDBFile.getReslibDir("IUPAC");
        Polymer polymer = null;
        Sequence sequence = new Sequence(molecule);
        int entityID = 1;
        String lastChain = "";
        double linkLen = 5.0;
        double valAngle = 90.0;
        double dihAngle = 135.0;
        for (int i = 0; i < entityIDColumn.size(); i++) {
            String chainCode = (String) String.valueOf(chainCodeMap.get(entityIDColumn.get(i)));
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
                System.out.println("reader " + chainCode + " " + chainID);

            }
            String resName = (String) monIDColumn.get(i);
            String iRes = (String) numColumn.get(i);
            String mapID = chainCode + "." + iRes;
            String hetero = heteroColumn.get(i);
            Residue residue = new Residue(iRes, resName.toUpperCase(), hetero);
            residue.molecule = polymer.molecule;
            addCompound(mapID, residue);
            polymer.addResidue(residue);
            Sequence.RES_POSITION resPos = Sequence.RES_POSITION.MIDDLE;
            if (i == 0) {
                resPos = Sequence.RES_POSITION.START;
                //residue.capFirstResidue();
            } else if (i == entityIDColumn.size() - 1) {
                resPos = Sequence.RES_POSITION.END;
                //residue.capLastResidue();
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
        Molecule.compoundMap.put(id, compound);
    }

    void buildChemComp(final Saveframe saveframe, Molecule molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_chem_comp");
        if (loop == null) {
            throw new ParseException("No \"_chem_comp\" loop");
        } else {
            List<String> idColumn = loop.getColumnAsList("id");
            List<String> nameColumn = loop.getColumnAsList("name");
            Iterator entityIterator = molecule.entityLabels.values().iterator();
            while (entityIterator.hasNext()) {
                Entity entity = (Entity) entityIterator.next();
                if (entity instanceof Polymer) {
                    List<Residue> resList = ((Polymer) entity).getResidues();
                    for (Residue res : resList) {
                        for (int i = 0; i < idColumn.size(); i++) {
                            String resName = idColumn.get(i);
                            String resFullName = nameColumn.get(i);
                            if (res.name.equals(resName) && resFullName.contains("ACID")) {
                                res.label += ",+H";
                            }
                        }
                    }
                }
            }

        }
    }

    Molecule buildConformation(final Saveframe saveframe, Molecule molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_struct_conf");
        if (loop == null) {
            molecule.setHelix(null);
        } else {
            List<String> idColumn = loop.getColumnAsList("id");
            List<String> begAsymIDColumn = loop.getColumnAsList("beg_label_asym_id");
            List<Integer> begSeqIDColumn = loop.getColumnAsIntegerList("beg_label_seq_id", 1);
            List<Integer> endSeqIDColumn = loop.getColumnAsIntegerList("end_label_seq_id", 1);
            List<Polymer> polymers = molecule.getPolymers();
            List<Residue> helixResList = new ArrayList<>();
            for (int i = 0; i < idColumn.size(); i++) {
                int iFirstRes = begSeqIDColumn.get(i) - 1;
                int iLastRes = endSeqIDColumn.get(i) - 1;
                char chainCode = begAsymIDColumn.get(i).charAt(0);
                int iPolymer = chainCode - 'A';
//                if (iPolymer > polymers.size() - 1) {
//                    List<Residue> p0Res = polymers.get(0).getResidues();
//                    List<String> entityIDs = new ArrayList<>();
//                    List<String> nums = new ArrayList<>();
//                    List<String> names = new ArrayList<>();
//                    List<String> heteros = new ArrayList<>();
//                    for (int iRes = 0; iRes < p0Res.size(); iRes++) {
//                        entityIDs.add(String.valueOf(iPolymer + 1));
//                        nums.add(String.valueOf(p0Res.get(iRes).number));
//                        names.add(String.valueOf(p0Res.get(iRes).name));
//                        heteros.add(String.valueOf(p0Res.get(iRes).label));
//                    }
//                    addResidues(saveframe, molecule, entityIDs, nums, names, heteros);
//                    polymers = molecule.getPolymers();
//                    molecule.updateAtomArray();
//                }
                Residue firstRes = polymers.get(iPolymer).getResidue(iFirstRes);
                Residue lastRes = polymers.get(iPolymer).getResidue(iLastRes);
                helixResList.add(firstRes);
                helixResList.add(lastRes);
            }
            molecule.setHelix(new Helix(helixResList));
        }
        return molecule;
    }

    void buildSheetRange(final Saveframe saveframe, Molecule molecule) throws ParseException {
        Loop loop = saveframe.getLoop("_struct_sheet_range");
        if (loop == null) {
            molecule.setSheets(null);
        } else {
            List<String> idColumn = loop.getColumnAsList("id");
            List<String> begAsymIDColumn = loop.getColumnAsList("beg_label_asym_id");
            List<Integer> begSeqIDColumn = loop.getColumnAsIntegerList("beg_label_seq_id", 1);
            List<Integer> endSeqIDColumn = loop.getColumnAsIntegerList("end_label_seq_id", 1);
            List<Polymer> polymers = molecule.getPolymers();
            List<Residue> sheetResList = new ArrayList<>();
            for (int i = 0; i < idColumn.size(); i++) {
                int iFirstRes = begSeqIDColumn.get(i) - 1;
                int iLastRes = endSeqIDColumn.get(i) - 1;
                char chainCode = begAsymIDColumn.get(i).charAt(0);
                int iPolymer = chainCode - 'A';
                Residue firstRes = polymers.get(iPolymer).getResidue(iFirstRes);
                Residue lastRes = polymers.get(iPolymer).getResidue(iLastRes);
                sheetResList.add(firstRes);
                sheetResList.add(lastRes);
            }
            molecule.setSheets(new NonLoop(sheetResList));
        }
    }

    void buildAtomSites(int fromSet, final int toSet) throws ParseException {
        Iterator iter = mmcif.getSaveFrames().values().iterator();
        int iSet = 0;
        Molecule molecule = Molecule.getActive();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (DEBUG) {
                System.err.println("process atom sites " + saveframe.getName());
            }
            if (fromSet < 0) {
                molecule.nullCoords(iSet);
                processAtomSites(saveframe, iSet);
            } else if (fromSet == iSet) {
                molecule.nullCoords(toSet);
                processAtomSites(saveframe, toSet);
                break;
            }
            iSet++;
        }
    }

    void buildTorsions(Dihedral dihedral) throws ParseException {
        for (Saveframe saveframe : mmcif.getSaveFrames().values()) {
            if (DEBUG) {
                System.err.println("process torsion angles " + saveframe.getName());
            }
            processTorsions(saveframe, dihedral);
        }
    }

    void buildDistanceRestraints(EnergyLists energyList) throws ParseException {
        for (Saveframe saveframe : mmcif.getSaveFrames().values()) {
            if (DEBUG) {
                System.err.println("process distances " + saveframe.getName());
            }
            processDistanceRestraints(saveframe, energyList);
        }
    }

    Molecule buildMolecule() throws ParseException {
        Molecule molecule = null;
        for (Saveframe saveframe : mmcif.getSaveFrames().values()) {
            if (DEBUG) {
                System.err.println(saveframe.getCategoryName());
            }
            if (DEBUG) {
                System.err.println("process molecule >>" + saveframe.getName() + "<<");
            }
            String molName = "noname";
            molecule = new Molecule(molName);
            buildEntities(saveframe);
            buildAsym(saveframe, molecule);
            molecule = buildConformation(saveframe, molecule);
            buildChemComp(saveframe, molecule);
            buildSheetRange(saveframe, molecule);
            molecule.updateSpatialSets();
            molecule.genCoords(false);

        }
        return molecule;
    }

    void processAtomSites(Saveframe saveframe, int ppmSet) throws ParseException {
        Loop loop = saveframe.getLoop("_atom_site");
        if (loop != null) {
//            List<String> groupPDBColumn = loop.getColumnAsList("group_PDB");
//            List<String> idColumn = loop.getColumnAsList("id");
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
            List<String> authSeqIDColumn = loop.getColumnAsList("auth_seq_id");
            List<String> authCompIDColumn = loop.getColumnAsList("auth_comp_id");
            List<String> authAsymIDColumn = loop.getColumnAsList("auth_asym_id");
            List<String> authAtomIDColumn = loop.getColumnAsList("auth_atom_id");
            List<Integer> pdbModelNumColumn = loop.getColumnAsIntegerList("pdbx_PDB_model_num", 0);

            Molecule molecule = Molecule.getActive();
            for (int i = 0; i < typeSymbolColumn.size(); i++) {
//                String groupPDB = (String) groupPDBColumn.get(i);
//                String idCode = (String) idColumn.get(i);
                String atomType = (String) typeSymbolColumn.get(i);
                String atomName = (String) labelAtomIDColumn.get(i);
                String atomAltName = (String) labelAltIDColumn.get(i);
                String resIDStr = ".";
//                System.out.println(sequenceCode + " " + atomName + " " + value);
                if (labelCompIDColumn != null) {
                    resIDStr = (String) labelCompIDColumn.get(i);
                }
                String chainCode = (String) labelAsymIDColumn.get(i);
                String entityID = (String) labelEntityIDColumn.get(i);
                String sequenceCode = (String) labelSeqIDColumn.get(i);
                int pdbModelNum = pdbModelNumColumn.get(i);
                float xCoord = Float.parseFloat((String) cartnXColumn.get(i));
                float yCoord = Float.parseFloat((String) cartnYColumn.get(i));
                float zCoord = Float.parseFloat((String) cartnZColumn.get(i));
                float occupancy = Float.parseFloat((String) occupancyColumn.get(i));
                float bFactor = Float.parseFloat((String) bIsoColumn.get(i));
                String mapID = chainCode + "." + sequenceCode;
                Compound compound = (Compound) Molecule.compoundMap.get(mapID);
                if (compound == null) {
                    //throw new ParseException("invalid compound in assignments saveframe \""+mapID+"\"");
                    System.err.println("invalid compound in assignments saveframe \"" + mapID + "\"");
                    continue;
                }
                String fullAtom = chainCode + ":" + sequenceCode + "." + atomName;
                //  System.out.println(fullAtom);

                if (!molecule.structures.contains(pdbModelNum - 1)) {
                    molecule.structures.add(pdbModelNum - 1);
                    molecule.setActiveStructures();
                }

                Atom atom = Molecule.getAtomByName(fullAtom);
//                System.out.println(fullAtom + " " + atoms);
                //  System.out.println(atoms.toString());

                if (atom == null) {
                    throw new ParseException("invalid atom in assignments saveframe \"" + mapID + "." + atomName + "\"");
                }

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

                }
            }
        }
    }

    void processTorsions(Saveframe saveframe, Dihedral dihedral) throws ParseException {
        Loop loop = saveframe.getLoop("_pdbx_validate_torsion");
        if (loop == null) {
            throw new ParseException("No \"_pdbx_validate_torsion\" loop");
        }

        List<Integer> pdbModelNumColumn = loop.getColumnAsIntegerList("PDB_model_num", 1);
        List<String> chainCodeColumn = loop.getColumnAsList("auth_asym_id");
        List<String> sequenceCodeColumn = loop.getColumnAsList("auth_seq_id");
        List<String> resNameColumn = loop.getColumnAsList("auth_comp_id");
        List<Double> phiColumn = loop.getColumnAsDoubleList("phi", 0.0);
        List<Double> psiColumn = loop.getColumnAsDoubleList("psi", 0.0);

        Molecule molecule = Molecule.getActive();
        List<Residue> resList = new ArrayList<>();
        List<String> resNames = new ArrayList<>();
        List<Polymer> polymers = molecule.getPolymers();
        for (Polymer polymer : polymers) {
            resList.addAll(polymer.getResidues());
        }
        for (Residue res : resList) {
            resNames.add(res.polymer.getName() + ":" + res.name + res.getIDNum());
        }
        int pdbModelNumPrev = 1;
        Map<Residue, AngleProp> torsionMap = new HashMap<>();
        for (int i = 0; i < pdbModelNumColumn.size(); i++) {
            int pdbModelNum = pdbModelNumColumn.get(i);
            int pdbModelNumNext = pdbModelNum;
            if (i < pdbModelNumColumn.size() - 1) {
                pdbModelNumNext = pdbModelNumColumn.get(i + 1);
            }
            if (pdbModelNum > pdbModelNumPrev) {
                torsionMap = new HashMap<>();
            }
            Double phiValue = phiColumn.get(i);
            Double psiValue = psiColumn.get(i);
            double[] target = {phiValue, psiValue};
            double[] sigma = {0.0, 0.0};

            String resName = (String) resNameColumn.get(i);
            String chainCode = (String) chainCodeColumn.get(i);
            String sequenceCode = (String) sequenceCodeColumn.get(i);
            String resFull = chainCode + ":" + resName + sequenceCode;
            Residue res = resList.get(resNames.indexOf(resFull));

            try {
                dihedral.addTorsion(torsionMap, res, target, sigma, null);
            } catch (InvalidMoleculeException imE) {

            }

            if (pdbModelNumNext > pdbModelNum || i == pdbModelNumColumn.size() - 1) {
                dihedral.getTorsionAngles().add(torsionMap);
            }
            pdbModelNumPrev = pdbModelNum;

        }
    }

    void processDistanceRestraints(Saveframe saveframe, EnergyLists energyList) throws ParseException {
        Loop loop = saveframe.getLoop("_pdbx_validate_close_contact");
        if (loop == null) {
            throw new ParseException("No \"_pdbx_validate_close_contact\" loop");
        }
        List<String>[] chainCodeColumns = new ArrayList[2];
        List<String>[] sequenceColumns = new ArrayList[2];
        List<String>[] residueNameColumns = new ArrayList[2];
        List<String>[] atomNameColumns = new ArrayList[2];

        List<Integer> idColumn = loop.getColumnAsIntegerList("id", 0);
        List<Integer> pdbModelNumColumn = loop.getColumnAsIntegerList("PDB_model_num", 0);

        chainCodeColumns[0] = loop.getColumnAsList("auth_asym_id_1");
        sequenceColumns[0] = loop.getColumnAsList("auth_seq_id_1");
        residueNameColumns[0] = loop.getColumnAsList("auth_comp_id_1");
        atomNameColumns[0] = loop.getColumnAsList("auth_atom_id_1");

        chainCodeColumns[1] = loop.getColumnAsList("auth_asym_id_2");
        sequenceColumns[1] = loop.getColumnAsList("auth_seq_id_2");
        residueNameColumns[1] = loop.getColumnAsList("auth_comp_id_2");
        atomNameColumns[1] = loop.getColumnAsList("auth_atom_id_2");

        List<Double> distColumn = loop.getColumnAsDoubleList("dist", 0.0);
        ArrayList<String> atomNames[] = new ArrayList[2];
//        String[] resNames = new String[2];
        atomNames[0] = new ArrayList<>();
        atomNames[1] = new ArrayList<>();
        ArrayList<Double> distList = new ArrayList<>();
        for (int i = 0; i < chainCodeColumns[0].size(); i++) {
            int pdbModelNum = pdbModelNumColumn.get(i);
            int pdbModelNumPrev = pdbModelNum;
            int pdbModelNumNext = pdbModelNum;
            boolean addConstraint = true;
            if (i >= 1) {
                pdbModelNumPrev = pdbModelNumColumn.get(i - 1);
            }
            if (i < chainCodeColumns[0].size() - 1) {
                pdbModelNumNext = pdbModelNumColumn.get(i + 1);
            }
            if (pdbModelNum != pdbModelNumPrev) {
                atomNames[0].clear();
                atomNames[1].clear();
                distList.clear();
                if (pdbModelNum == pdbModelNumNext
                        && i > 0 && i < chainCodeColumns[0].size() - 1) {
                    addConstraint = false;
                }
            } else if (pdbModelNum == pdbModelNumPrev
                    && pdbModelNum == pdbModelNumNext
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
                atomNames[iAtom].add(chainCode + ":" + seqNum + "." + atomName);
//                resNames[iAtom] = resName;
            }

            double upper = 1000000.0;
            double lower = 1.8;
            double weight = 1.0;
            double dist = distColumn.get(i);
            double distErr = upper - lower;
            distList.add(dist);

            Util.setStrictlyNEF(true);
            try {
                if (addConstraint) {
                    energyList.addDistance(pdbModelNum, atomNames[0], atomNames[1], lower, upper, weight, distList, distErr);
                }
            } catch (IllegalArgumentException iaE) {
                int index = idColumn.get(i);
                throw new ParseException("Error parsing mmCIF distances at index  \"" + index + "\" " + iaE.getMessage());
            }
            Util.setStrictlyNEF(false);
        }
    }

    void process() throws ParseException, IllegalArgumentException {
        String[] argv = {};
        process(argv);
    }

    public Dihedral process(String[] argv) throws ParseException, IllegalArgumentException {
        if ((argv.length != 0) && (argv.length != 3)) {
            throw new IllegalArgumentException("?shifts fromSet toSet?");
        }

        Dihedral dihedral = null;
        if (argv.length == 0) {
            hasResonances = false;
            Molecule.compoundMap.clear();
            if (DEBUG) {
                System.err.println("process molecule");
            }
            Molecule molecule = buildMolecule();
            molecule.setMethylRotationActive(true);
            molecule.setEnergyLists(new EnergyLists(molecule));
            EnergyLists energyList = molecule.getEnergyLists();
            molecule.setDihedrals(new Dihedral(energyList, false));
            dihedral = molecule.getDihedrals();
            energyList.clearDistanceMap();
            dihedral.getTorsionAngles().clear();

            energyList.makeCompoundList(molecule);
            if (DEBUG) {
                System.err.println("process atom sites");
            }
            buildAtomSites(-1, 0);
            if (DEBUG) {
                System.err.println("process distances");
            }
//            buildDistanceRestraints(energyList);
            if (DEBUG) {
                System.err.println("process torsion angles");
            }
            buildTorsions(dihedral);
        } else if ("shifts".startsWith(argv[2])) {
            int fromSet = Integer.parseInt(argv[3]);
            int toSet = Integer.parseInt(argv[4]);
            buildAtomSites(fromSet, toSet);
        }
        return dihedral;
    }

}
