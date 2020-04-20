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
import org.nmrfx.structure.chemistry.*;
import org.nmrfx.structure.chemistry.constraints.*;
import org.nmrfx.structure.chemistry.energy.Dihedral;
import org.nmrfx.structure.chemistry.energy.EnergyLists;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.AtomResonance;
import org.nmrfx.processor.datasets.peaks.AtomResonanceFactory;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.ResonanceFactory;
import org.nmrfx.processor.star.Loop;
 import org.nmrfx.processor.star.ParseException;
import org.nmrfx.processor.star.STAR3;
import org.nmrfx.processor.star.Saveframe;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.*;
import org.nmrfx.processor.datasets.peaks.Resonance;
import org.nmrfx.processor.datasets.peaks.SpectralDim;
import org.nmrfx.processor.utilities.NvUtil;
import org.nmrfx.structure.chemistry.io.Sequence.RES_POSITION;
import org.nmrfx.structure.utilities.Util;

/**
 *
 * @author brucejohnson
 */
public class NMRStarReader {

    static String[] polymerEntityStrings = {"_Entity.Sf_category", "_Entity.Sf_framecode", "_Entity.Entry_ID", "_Entity.ID", "_Entity.Name", "_Entity.Type", "_Entity.Polymer_type", "_Entity.Polymer_strand_ID", "_Entity.Polymer_seq_one_letter_code_can", "_Entity.Polymer_seq_one_letter_code"};

    final STAR3 star3;
    final File starFile;

    Map entities = new HashMap();
    boolean hasResonances = false;
    Map<Long, List<PeakDim>> resMap = new HashMap<>();
    public static boolean DEBUG = false;

    public NMRStarReader(final File starFile, final STAR3 star3) {
        this.star3 = star3;
        this.starFile = starFile;
//        PeakDim.setResonanceFactory(new AtomResonanceFactory());
    }

    public static STAR3 read(String starFileName) throws ParseException {
        File file = new File(starFileName);
        return read(file);
    }

    public static STAR3 read(File starFile) throws ParseException {
        FileReader fileReader;
        try {
            fileReader = new FileReader(starFile);
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
        NMRStarReader reader = new NMRStarReader(starFile, star);
        reader.process();
        return star;
    }

    static void updateFromSTAR3ChemComp(Saveframe saveframe, Compound compound) throws ParseException {
        Loop loop = saveframe.getLoop("_Chem_comp_atom");
        if (loop == null) {
            throw new ParseException("No \"_Chem_comp_atom\" loop in \"" + saveframe.getName() + "\"");
        }
        List<String> idColumn = loop.getColumnAsList("Atom_ID");
        List<String> typeColumn = loop.getColumnAsList("Type_symbol");
        for (int i = 0; i < idColumn.size(); i++) {
            String aName = (String) idColumn.get(i);
            String aType = (String) typeColumn.get(i);
            Atom atom = Atom.genAtomWithElement(aName, aType);
            compound.addAtom(atom);
        }
        compound.updateNames();
        loop = saveframe.getLoop("_Chem_comp_bond");
        if (loop != null) {
            List<String> id1Column = loop.getColumnAsList("Atom_ID_1");
            List<String> id2Column = loop.getColumnAsList("Atom_ID_2");
            List<String> orderColumn = loop.getColumnAsList("Value_order");
            for (int i = 0; i < id1Column.size(); i++) {
                String aName1 = (String) id1Column.get(i);
                String aName2 = (String) id2Column.get(i);
                String orderString = (String) orderColumn.get(i);
                Atom atom1 = compound.getAtom(aName1);
                Atom atom2 = compound.getAtom(aName2);
                Order order = Order.SINGLE;
                if (orderString.toUpperCase().startsWith("SING")) {
                    order = Order.SINGLE;
                } else if (orderString.toUpperCase().startsWith("DOUB")) {
                    order = Order.DOUBLE;
                } else if (orderString.toUpperCase().startsWith("TRIP")) {
                    order = Order.TRIPLE;
                } else {
                    order = Order.SINGLE;
                }
                int stereo = 0;
                Atom.addBond(atom1, atom2, order, stereo, false);
            }
            for (Atom atom : compound.getAtoms()) {
                if (atom.bonds == null) {
                    System.out.println("no bonds");
                } else {
                    System.out.println(atom.bonds.size());
                }
            }
        }
    }

    static void addComponents(Saveframe saveframe, List<String> idColumn, List<String> authSeqIDColumn, List<String> compIDColumn, List<String> entityIDColumn, Compound compound) throws ParseException {
        for (int i = 0; i < compIDColumn.size(); i++) {
            String resName = (String) compIDColumn.get(i);
            String seqNumber = (String) authSeqIDColumn.get(i);
            String ccSaveFrameName = "save_chem_comp_" + resName;
            Saveframe ccSaveframe = saveframe.getSTAR3().getSaveframe(ccSaveFrameName);
            if (ccSaveframe == null) {
                ccSaveFrameName = "save_" + resName;
                ccSaveframe = saveframe.getSTAR3().getSaveframe(ccSaveFrameName);
            }
            if (ccSaveframe != null) {
                compound.setNumber(seqNumber);
                updateFromSTAR3ChemComp(ccSaveframe, compound);
            } else {
                System.out.println("No save frame: " + ccSaveFrameName);
            }
        }

    }

    public static void processLoop(STAR3 star3, List tagList) {
    }

    static void finishSaveFrameProcessing(final NMRStarReader nmrStar, Saveframe saveframe, Compound compound, String mapID) throws ParseException {
        Loop loop = saveframe.getLoop("_Entity_comp_index");
        if (loop != null) {
            List<String> idColumn = loop.getColumnAsList("ID");
            List<String> authSeqIDColumn = loop.getColumnAsList("Auth_seq_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIDColumn = loop.getColumnAsList("Comp_ID");
            nmrStar.addCompound(mapID, compound);
            addComponents(saveframe, idColumn, authSeqIDColumn, compIDColumn, entityIDColumn, compound);
        } else {
            System.out.println("No \"_Entity_comp_index\" loop");
        }

    }

    public void finishSaveFrameProcessing(final Polymer polymer, final Saveframe saveframe, final String nomenclature, final boolean capped) throws ParseException {
        String lstrandID = saveframe.getOptionalValue("_Entity", "Polymer_strand_ID");
        if (lstrandID != null) {
            if (DEBUG) {
                System.out.println("set strand " + lstrandID);
            }
            polymer.setStrandID(lstrandID);
        }
        String type = saveframe.getOptionalValue("_Entity", "Polymer_type");
        if (type != null) {
            if (DEBUG) {
                System.out.println("set polytype " + type);
            }
            polymer.setPolymerType(type);
        }
        Loop loop = saveframe.getLoop("_Entity_comp_index");
        if (loop == null) {
            System.out.println("No \"_Entity_comp_index\" loop");
        } else {
            List<String> idColumn = loop.getColumnAsList("ID");
            List<String> authSeqIDColumn = loop.getColumnAsList("Auth_seq_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIDColumn = loop.getColumnAsList("Comp_ID");
            addResidues(polymer, saveframe, idColumn, authSeqIDColumn, compIDColumn, entityIDColumn, nomenclature);
            polymer.setCapped(capped);
            if (capped) {
                PDBFile.capPolymer(polymer);
            }
        }
        loop = saveframe.getLoop("_Entity_chem_comp_deleted_atom");
        if (loop != null) {
            List<String> idColumn = loop.getColumnAsList("ID");
            List<String> compIndexIDColumn = loop.getColumnAsList("Comp_index_ID");
            List<String> compIDColumn = loop.getColumnAsList("Comp_ID");
            List<String> atomIDColumn = loop.getColumnAsList("Atom_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            polymer.removeAtoms(compIndexIDColumn, atomIDColumn);
        }
        loop = saveframe.getLoop("_Entity_bond");
        if (loop != null) {
            List<String> orderColumn = loop.getColumnAsList("Value_order");
            List<String> comp1IndexIDColumn = loop.getColumnAsList("Comp_index_ID_1");
            List<String> atom1IDColumn = loop.getColumnAsList("Atom_ID_1");
            List<String> comp2IndexIDColumn = loop.getColumnAsList("Comp_index_ID_2");
            List<String> atom2IDColumn = loop.getColumnAsList("Atom_ID_2");
            polymer.addBonds(orderColumn, comp1IndexIDColumn, atom1IDColumn, comp2IndexIDColumn, atom2IDColumn);
        }
        polymer.molecule.genCoords(false);
        polymer.molecule.setupRotGroups();
    }

    public void buildNEFChains(final Saveframe saveframe, Molecule molecule, final String nomenclature) throws ParseException {
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
            List<String> variantColumn = loop.getColumnAsListIfExists("variant");
            List<String> cisPeptideColumn = loop.getColumnAsListIfExists("cis_peptide");
            addNEFResidues(saveframe, molecule, indexColumn, chainCodeColumn, seqCodeColumn, residueNameColumn, linkingColumn, variantColumn, cisPeptideColumn);
        }
    }

    public void addResidues(Polymer polymer, Saveframe saveframe, List<String> idColumn, List<String> authSeqIDColumn, List<String> compIDColumn, List<String> entityIDColumn, String frameNomenclature) throws ParseException {
        String reslibDir = PDBFile.getReslibDir(frameNomenclature);
        polymer.setNomenclature(frameNomenclature.toUpperCase());
        Sequence sequence = new Sequence();
        for (int i = 0; i < compIDColumn.size(); i++) {
            String resName = (String) compIDColumn.get(i);
            String iEntity = (String) entityIDColumn.get(i);
            String iRes = (String) idColumn.get(i);
            String mapID = polymer.assemblyID + "." + iEntity + "." + iRes;
            if (authSeqIDColumn != null) {
                String iResTemp = (String) authSeqIDColumn.get(i);
                if (!iResTemp.equals(".")) {
                    iRes = iResTemp;
                }
            }
            if (resName.length() == 1) {
                char RD = 'd';
                if (polymer.getPolymerType().equalsIgnoreCase("polyribonucleotide")) {
                    RD = 'r';
                }
                resName = AtomParser.pdbResToPRFName(resName, RD);
            }
            Residue residue = new Residue(iRes, resName.toUpperCase());
            residue.molecule = polymer.molecule;
            addCompound(mapID, residue);
            polymer.addResidue(residue);
            String ccSaveFrameName = "save_chem_comp_" + resName + "." + iRes;
            Saveframe ccSaveframe = saveframe.getSTAR3().getSaveframe(ccSaveFrameName);
            if (ccSaveframe == null) {
                ccSaveFrameName = "save_chem_comp_" + resName;
                ccSaveframe = saveframe.getSTAR3().getSaveframe(ccSaveFrameName);
            }
            if (ccSaveframe == null) {
                ccSaveFrameName = "save_" + resName;
                ccSaveframe = saveframe.getSTAR3().getSaveframe(ccSaveFrameName);
            }
            RES_POSITION resPos = RES_POSITION.MIDDLE;
            if (ccSaveframe != null) {
                updateFromSTAR3ChemComp(ccSaveframe, residue);
            } else {
                try {
                    if (!sequence.addResidue(reslibDir + "/" + Sequence.getAliased(resName.toLowerCase()) + ".prf", residue, resPos, "", false)) {
                        throw new ParseException("Can't find residue \"" + resName + "\" in residue libraries or STAR file");
                    }
                } catch (MoleculeIOException psE) {
                    throw new ParseException(psE.getMessage());
                }
            }
        }
        sequence.removeBadBonds();
    }

    public void addNEFResidues(Saveframe saveframe, Molecule molecule, List<String> indexColumn, List<String> chainCodeColumn, List<String> seqCodeColumn, List<String> residueNameColumn, List<String> linkingColumn, List<String> variantColumn, List<String> cisPeptideColumn) throws ParseException {
        String reslibDir = PDBFile.getReslibDir("IUPAC");
        Polymer polymer = null;
        Sequence sequence = new Sequence(molecule);
        int entityID = 1;
        String lastChain = "";
        double linkLen = 5.0;
        double valAngle = 90.0;
        double dihAngle = 135.0;
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
            String resName = (String) residueNameColumn.get(i);
            String iRes = (String) seqCodeColumn.get(i);
            String mapID = chainCode + "." + iRes;
            Residue residue = new Residue(iRes, resName.toUpperCase());
            residue.molecule = polymer.molecule;
            addCompound(mapID, residue);
            polymer.addResidue(residue);
            RES_POSITION resPos = Sequence.RES_POSITION.MIDDLE;
            if (linkType.equals("start")) {
                resPos = RES_POSITION.START;
                //residue.capFirstResidue();
            } else if (linkType.equals("end")) {
                resPos = RES_POSITION.END;
                //residue.capLastResidue();
            }
            try {
                if (!sequence.addResidue(reslibDir + "/" + Sequence.getAliased(resName.toLowerCase()) + ".prf", residue, resPos, "", false)) {
                    throw new ParseException("Can't find residue \"" + resName + "\" in residue libraries or STAR file");
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

    public void addCompound(String id, Compound compound) {
        Molecule.compoundMap.put(id, compound);
    }

    public void buildChemShifts(int fromSet, final int toSet) throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        int iSet = 0;
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("assigned_chemical_shifts")) {
                if (DEBUG) {
                    System.err.println("process chem shifts " + saveframe.getName());
                }
                if (fromSet < 0) {
                    processChemicalShifts(saveframe, iSet);
                } else if (fromSet == iSet) {
                    processChemicalShifts(saveframe, toSet);
                    break;
                }
                iSet++;
            }
        }
    }

    public void buildConformers() throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("conformer_family_coord_set")) {
                if (DEBUG) {
                    System.err.println("process conformers " + saveframe.getName());
                }
                processConformer(saveframe);
            }
        }
    }

    public void buildDataset(Map tagMap, String datasetName) throws ParseException, IOException {
        String name = STAR3.getTokenFromMap(tagMap, "Name");
        String path = STAR3.getTokenFromMap(tagMap, "Directory_path");
        String type = STAR3.getTokenFromMap(tagMap, "Type");
        File file = new File(path, name);
        if (datasetName.equals("")) {
            datasetName = file.getAbsolutePath();
        }
        try {
            if (DEBUG) {
                System.err.println("open " + file.getAbsolutePath());
            }
            if (!file.exists()) {
                file = FileSystems.getDefault().getPath(starFile.getParentFile().getParent(), "datasets", file.getName()).toFile();
            }
            Dataset dataset = new Dataset(file.getAbsolutePath(), datasetName, false, false);
        } catch (IOException | IllegalArgumentException tclE) {
            System.err.println(tclE.getMessage());
        }
    }

    public void buildDihedralConstraints() throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("torsion_angle_constraints")) {
                if (DEBUG) {
                    System.err.println("process torsion angle constraints " + saveframe.getName());
                }
                processDihedralConstraints(saveframe);
            }
        }
    }

    public void buildNEFDihedralConstraints(Dihedral dihedral) throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("nef_dihedral_restraint_list")) {
                if (DEBUG) {
                    System.err.println("process nef_dihedral_restraint_list " + saveframe.getName());
                }
                processNEFDihedralConstraints(saveframe, dihedral);
            }
        }
    }

    public void buildRDCConstraints() throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("RDCs")) {
                if (DEBUG) {
                    System.err.println("process RDC constraints " + saveframe.getName());
                }
                processRDCConstraints(saveframe);
            }
        }
    }

    public void buildEntity(Molecule molecule, Map tagMap, int index) throws ParseException {
        String entityAssemblyIDString = STAR3.getTokenFromMap(tagMap, "ID");
        String entityIDString = STAR3.getTokenFromMap(tagMap, "Entity_ID");
        String entitySaveFrameLabel = STAR3.getTokenFromMap(tagMap, "Entity_label").substring(1);
        String entityAssemblyName = STAR3.getTokenFromMap(tagMap, "Entity_assembly_name");
        String asymLabel = STAR3.getTokenFromMap(tagMap, "Asym_ID", entityAssemblyName);
        if (asymLabel.equals(".")) {
            asymLabel = String.valueOf((char) ('A' + index));
        }
        String pdbLabel = STAR3.getTokenFromMap(tagMap, "PDB_chain_ID", "A");
        if (pdbLabel.equals(".")) {
            pdbLabel = asymLabel;
        }
        int entityID = 1;
        int entityAssemblyID = 1;
        try {
            entityID = Integer.parseInt(entityIDString);
            entityAssemblyID = Integer.parseInt(entityAssemblyIDString);
        } catch (NumberFormatException nFE) {
            throw new ParseException(nFE.getMessage());
        }
        //int entityAssemblyID = Integer.parseInt(entityAssemblyIDString);
        // get entity saveframe
        String saveFrameName = "save_" + entitySaveFrameLabel;
        if (DEBUG) {
            System.err.println("process entity " + saveFrameName);
        }
        Saveframe saveframe = (Saveframe) star3.getSaveFrames().get(saveFrameName);
        if (saveframe != null) {

            String type = saveframe.getValue("_Entity", "Type");
            String name = saveframe.getValue("_Entity", "Name");
            String nomenclature = saveframe.getValue("_Entity", "Nomenclature", "");
            if (nomenclature.equals("")) {
                nomenclature = "IUPAC";
            }
            String cappedString = saveframe.getValue("_Entity", "Capped", "");
            boolean capped = true;
            if (cappedString.equalsIgnoreCase("no")) {
                capped = false;
            }
            if (type != null && type.equals("polymer")) {
                Entity entity = molecule.getEntity(entityAssemblyName);
                if (entity == null) {
                    Polymer polymer = new Polymer(entitySaveFrameLabel, entityAssemblyName);
                    polymer.setIDNum(entityID);
                    polymer.assemblyID = entityAssemblyID;
                    polymer.setPDBChain(pdbLabel);
                    entities.put(entityAssemblyIDString + "." + entityIDString, polymer);
                    molecule.addEntity(polymer, asymLabel, entityAssemblyID);
                    finishSaveFrameProcessing(polymer, saveframe, nomenclature, capped);
                } else {
                    molecule.addCoordSet(asymLabel, entityAssemblyID, entity);
                }
            } else {
                Entity entity = molecule.getEntity(name);
                if (entity == null) {
                    Compound compound = new Compound("1", entityAssemblyName, name);
                    compound.setIDNum(1);
                    compound.assemblyID = entityAssemblyID;
                    compound.setPDBChain(pdbLabel);
                    entities.put(entityAssemblyIDString + "." + entityIDString, compound);
                    molecule.addEntity(compound, asymLabel, entityAssemblyID);
                    String mapID = entityAssemblyID + "." + entityID + "." + 1;
                    finishSaveFrameProcessing(this, saveframe, compound, mapID);
                }
            }
        } else {
            System.out.println("Saveframe \"" + saveFrameName + "\" doesn't exist");
        }
    }

    public void buildExperiments() throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("experiment_list")) {
                if (DEBUG) {
                    System.err.println("process experiments " + saveframe.getName());
                }
                int nExperiments = 0;
                try {
                    nExperiments = saveframe.loopCount("_Experiment_file");
                } catch (ParseException tclE) {
                    nExperiments = 0;
                }
                for (int i = 0; i < nExperiments; i++) {
                    Map map = saveframe.getLoopRowMap("_Experiment_file", i);
                    String datasetName;
                    try {
                        Map nameMap = saveframe.getLoopRowMap("_Experiment", i);
                        datasetName = STAR3.getTokenFromMap(nameMap, "Name");
                    } catch (ParseException tclE) {
                        datasetName = "";
                    }
                    try {
                        buildDataset(map, datasetName);
                    } catch (IOException ioE) {
                        throw new ParseException(ioE.getMessage());
                    }
                }
            }
        }
    }

    public void buildGenDistConstraints() throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("general_distance_constraints")) {
                if (DEBUG) {
                    System.err.println("process general distance constraints " + saveframe.getName());
                }
                processGenDistConstraints(saveframe);
            }
        }
    }

    public void buildNEFDistanceRestraints(EnergyLists energyList) throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("nef_distance_restraint_list")) {
                if (DEBUG) {
                    System.err.println("process nef_distance_restraint_list " + saveframe.getName());
                }
                processNEFDistanceRestraints(saveframe, energyList);
            }
        }
    }

    public void buildMolecule() throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (DEBUG) {
                System.err.println(saveframe.getCategoryName());
            }
            if (saveframe.getCategoryName().equals("assembly")) {
                if (DEBUG) {
                    System.err.println("process molecule >>" + saveframe.getName() + "<<");
                }
                String molName = saveframe.getValue("_Assembly", "Name");
                if (molName.equals("?")) {
                    molName = "noname";
                }
                Molecule molecule = new Molecule(molName);
                int nEntities = saveframe.loopCount("_Entity_assembly");
                if (DEBUG) {
                    System.err.println("mol name " + molName + " nEntities " + nEntities);
                }
                for (int i = 0; i < nEntities; i++) {
                    Map map = saveframe.getLoopRowMap("_Entity_assembly", i);
                    buildEntity(molecule, map, i);
                }
                molecule.updateSpatialSets();
                molecule.genCoords(false);
                List<String> tags = saveframe.getTags("_Assembly");
                for (String tag : tags) {
                    if (tag.startsWith("NvJ_prop")) {
                        String propValue = saveframe.getValue("_Assembly", tag);
                        molecule.setProperty(tag.substring(9), propValue);
                    }
                }

            }
        }
    }

    public Molecule buildNEFMolecule() throws ParseException {
        Molecule molecule = null;
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (DEBUG) {
                System.err.println(saveframe.getCategoryName());
            }
            if (saveframe.getCategoryName().equals("nef_molecular_system")) {
                if (DEBUG) {
                    System.err.println("process molecule >>" + saveframe.getName() + "<<");
                }
                String molName = "noname";
                molecule = new Molecule(molName);
                buildNEFChains(saveframe, molecule, molName);
                molecule.updateSpatialSets();
                molecule.genCoords(false);

            }
        }
        return molecule;
    }

    public void buildPeakLists() throws ParseException {
        resMap.clear();
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("spectral_peak_list")) {
                if (DEBUG) {
                    System.err.println("process peaklists " + saveframe.getName());
                }
                processSTAR3PeakList(saveframe);
            }
        }
        linkResonances();
    }

    public void buildResonanceLists() throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().equals("resonance_linker")) {
                hasResonances = true;
                if (DEBUG) {
                    System.err.println("process resonances " + saveframe.getName());
                }
                AtomResonance.processSTAR3ResonanceList(this, saveframe);
            }
        }
    }

    public void buildRunAbout() throws ParseException {
        Iterator iter = star3.getSaveFrames().values().iterator();
        while (iter.hasNext()) {
            Saveframe saveframe = (Saveframe) iter.next();
            if (saveframe.getCategoryName().startsWith("nmrview_")) {
                String toolName = saveframe.getCategoryName().substring(8);
                if (DEBUG) {
                    System.err.println("process tool " + saveframe.getName());
                }
                // interp.eval("::star3::setupTool " + toolName);
            }
        }
    }

    public Entity getEntity(String entityAssemblyIDString, String entityIDString) {
        return (Entity) entities.get(entityAssemblyIDString + "." + entityIDString);
    }

    public SpatialSetGroup getSpatialSet(List<String> entityAssemblyIDColumn, List<String> entityIDColumn, List<String> compIdxIDColumn, List<String> atomColumn, List<String> resonanceColumn, int i) throws ParseException {
        SpatialSetGroup spg = null;
        String iEntity = (String) entityIDColumn.get(i);
        String entityAssemblyID = (String) entityAssemblyIDColumn.get(i);
        if (!iEntity.equals("?")) {
            String iRes = (String) compIdxIDColumn.get(i);
            String atomName = (String) atomColumn.get(i);
            Atom atom = null;
            if (entityAssemblyID.equals(".")) {
                entityAssemblyID = "1";
            }
            String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
            Compound compound1 = (Compound) Molecule.compoundMap.get(mapID);
            if (compound1 != null) {
                //throw new ParseException("invalid compound in assignments saveframe \""+mapID+"\"");
                if ((atomName.charAt(0) == 'Q') || (atomName.charAt(0) == 'M')) {
                    Atom[] pseudoAtoms = ((Residue) compound1).getPseudo(atomName);
                    spg = new SpatialSetGroup(pseudoAtoms);
                } else {
                    atom = compound1.getAtomLoose(atomName);
                    if (atom != null) {
                        spg = new SpatialSetGroup(atom.spatialSet);
                    }
                }
                if (spg == null) {
                    System.out.println("invalid spatial set in assignments saveframe \"" + mapID + "." + atomName + "\"");
                }
            } else {
                System.err.println("invalid compound in assignments saveframe \"" + mapID + "\"");
            }
        }
        return spg;
    }

    private void addResonance(long resID, PeakDim peakDim) {
        List<PeakDim> peakDims = resMap.get(resID);
        if (peakDims == null) {
            peakDims = new ArrayList<>();
            resMap.put(resID, peakDims);
        }
        peakDims.add(peakDim);
    }

    public void linkResonances() {
        ResonanceFactory resFactory = PeakDim.resFactory;
        for (Long resID : resMap.keySet()) {
            List<PeakDim> peakDims = resMap.get(resID);
            PeakDim firstPeakDim = peakDims.get(0);
            Resonance resonance = resFactory.build(resID);
            firstPeakDim.setResonance(resonance);
            resonance.add(firstPeakDim);
            if (peakDims.size() > 1) {
                for (PeakDim peakDim : peakDims) {
                    if (peakDim != firstPeakDim) {
                        PeakList.linkPeakDims(firstPeakDim, peakDim);
                    }
                }
            }
        }
    }

    public void processSTAR3PeakList(Saveframe saveframe) throws ParseException {
        ResonanceFactory resFactory = PeakDim.resFactory;
        String listName = saveframe.getValue("_Spectral_peak_list", "Sf_framecode");
        String sampleLabel = saveframe.getLabelValue("_Spectral_peak_list", "Sample_label");
        String sampleConditionLabel = saveframe.getOptionalValue("_Spectral_peak_list", "Sample_condition_list_label");
        String datasetName = saveframe.getLabelValue("_Spectral_peak_list", "Experiment_name");
        String nDimString = saveframe.getValue("_Spectral_peak_list", "Number_of_spectral_dimensions");
        String dataFormat = saveframe.getOptionalValue("_Spectral_peak_list", "Text_data_format");
        String details = saveframe.getOptionalValue("_Spectral_peak_list", "Details");
        String slidable = saveframe.getOptionalValue("_Spectral_peak_list", "Slidable");

        if (dataFormat.equals("text")) {
            System.out.println("Aaaack, peak list is in text format, skipping list");
            System.out.println(details);
            return;
        }
        if (nDimString.equals("?")) {
            return;
        }
        if (nDimString.equals(".")) {
            return;
        }
        int nDim = NvUtil.toInt(nDimString);

        PeakList peakList = new PeakList(listName, nDim);

        int nSpectralDim = saveframe.loopCount("_Spectral_dim");
        if (nSpectralDim > nDim) {
            throw new IllegalArgumentException("Too many _Spectral_dim values " + listName + " " + nSpectralDim + " " + nDim);
        }

        peakList.setSampleLabel(sampleLabel);
        peakList.setSampleConditionLabel(sampleConditionLabel);
        peakList.setDatasetName(datasetName);
        peakList.setDetails(details);
        peakList.setSlideable(slidable.equals("yes"));

        for (int i = 0; i < nSpectralDim; i++) {
            SpectralDim sDim = peakList.getSpectralDim(i);

            String value = null;
            value = saveframe.getValueIfPresent("_Spectral_dim", "Atom_type", i);
            if (value != null) {
                sDim.setAtomType(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Atom_isotope_number", i);
            if (value != null) {
                sDim.setAtomIsotopeValue(NvUtil.toInt(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Spectral_region", i);
            if (value != null) {
                sDim.setSpectralRegion(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Magnetization_linkage", i);
            if (value != null) {
                sDim.setMagLinkage(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Sweep_width", i);
            if (value != null) {
                sDim.setSw(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Spectrometer_frequency", i);
            if (value != null) {
                sDim.setSf(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Encoding_code", i);
            if (value != null) {
                sDim.setEncodingCode(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Encoded_source_dimension", i);
            if (value != null) {
                sDim.setEncodedSourceDim(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Dataset_dimension", i);
            if (value != null) {
                sDim.setDataDim(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Dimension_name", i);
            if (value != null) {
                sDim.setDimName(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "ID_tolerance", i);
            if (value != null) {
                sDim.setIdTol(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Pattern", i);
            if (value != null) {
                sDim.setPattern(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Relation", i);
            if (value != null) {
                sDim.setRelation(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Aliasing", i);
            if (value != null) {
                sDim.setAliasing(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Precision", i);
            if (value != null) {
                sDim.setPrecision(NvUtil.toInt(value));
            }
        }

        Loop loop = saveframe.getLoop("_Peak");
        if (loop != null) {
            List<String> idColumn = loop.getColumnAsList("ID");
            List<String> detailColumn = loop.getColumnAsListIfExists("Details");
            List<String> fomColumn = loop.getColumnAsListIfExists("Figure_of_merit");
            List<String> typeColumn = loop.getColumnAsListIfExists("Type");
            List<String> statusColumn = loop.getColumnAsListIfExists("Status");
            List<String> colorColumn = loop.getColumnAsListIfExists("Color");
            List<String> flagColumn = loop.getColumnAsListIfExists("Flag");
            List<String> cornerColumn = loop.getColumnAsListIfExists("Label_corner");

            for (int i = 0, n = idColumn.size(); i < n; i++) {
                int idNum = Integer.parseInt((String) idColumn.get(i));
                Peak peak = new Peak(peakList, nDim);
                peak.setIdNum(idNum);
                String value = null;
                if ((value = NvUtil.getColumnValue(fomColumn, i)) != null) {
                    float fom = NvUtil.toFloat(value);
                    peak.setFigureOfMerit(fom);
                }
                if ((value = NvUtil.getColumnValue(detailColumn, i)) != null) {
                    peak.setComment(value);
                }
                if ((value = NvUtil.getColumnValue(typeColumn, i)) != null) {
                    int type = Peak.getType(value);
                    peak.setType(type);
                }
                if ((value = NvUtil.getColumnValue(statusColumn, i)) != null) {
                    int status = NvUtil.toInt(value);
                    peak.setStatus(status);
                }
                if ((value = NvUtil.getColumnValue(colorColumn, i)) != null) {
                    value = value.equals(".") ? null : value;
                    peak.setColor(value);
                }
                if ((value = NvUtil.getColumnValue(flagColumn, i)) != null) {
                    for (int iFlag = 0; iFlag < Peak.NFLAGS; iFlag++) {
                        if (value.length() > iFlag) {
                            peak.setFlag(iFlag, (value.charAt(iFlag) == '1'));
                        } else {
                            peak.setFlag(iFlag, false);
                        }
                    }
                }
                if ((value = NvUtil.getColumnValue(cornerColumn, i)) != null) {
                    peak.setCorner(value);
                }
                peakList.addPeak(peak);  // old code added without creating resonance, but that caused problems with new
                // linking resonance code used here
            }

            loop = saveframe.getLoop("_Peak_general_char");
            if (loop != null) {
                List<String> peakidColumn = loop.getColumnAsList("Peak_ID");
                List<String> methodColumn = loop.getColumnAsList("Measurement_method");
                List<String> intensityColumn = loop.getColumnAsList("Intensity_val");
                List<String> errorColumn = loop.getColumnAsList("Intensity_val_err");
                for (int i = 0, n = peakidColumn.size(); i < n; i++) {
                    String value = null;
                    int idNum = 0;
                    if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                        idNum = NvUtil.toInt(value);
                    } else {
                        //throw new TclException("Invalid peak id value at row \""+i+"\"");
                        continue;
                    }
                    Peak peak = peakList.getPeakByID(idNum);
                    String method = "height";
                    if ((value = NvUtil.getColumnValue(methodColumn, i)) != null) {
                        method = value;
                    }
                    if ((value = NvUtil.getColumnValue(intensityColumn, i)) != null) {
                        float iValue = NvUtil.toFloat(value);
                        if (method.equals("height")) {
                            peak.setIntensity(iValue);
                        } else if (method.equals("volume")) {
                            // FIXME should set volume/evolume 
                            peak.setVolume1(iValue);
                        } else {
                            // FIXME throw error if don't know type, or add new type dynamically?
                            peak.setIntensity(iValue);
                        }
                    }
                    if ((value = NvUtil.getColumnValue(errorColumn, i)) != null) {
                        if (!value.equals(".")) {
                            float iValue = NvUtil.toFloat(value);
                            if (method.equals("height")) {
                                peak.setIntensityErr(iValue);
                            } else if (method.equals("volume")) {
                                // FIXME should set volume/evolume 
                                peak.setVolume1Err(iValue);
                            } else {
                                // FIXME throw error if don't know type, or add new type dynamically?
                                peak.setIntensityErr(iValue);
                            }
                        }
                    }
                    // FIXME set error value
                }
            }

            loop = saveframe.getLoop("_Peak_char");
            if (loop == null) {
                throw new ParseException("No \"_Peak_char\" loop");
            }
            if (loop != null) {
                List<String> peakIdColumn = loop.getColumnAsList("Peak_ID");
                List<String> sdimColumn = loop.getColumnAsList("Spectral_dim_ID");
                String[] peakCharStrings = Peak.getSTAR3CharStrings();
                for (int j = 0; j < peakCharStrings.length; j++) {
                    String tag = peakCharStrings[j].substring(peakCharStrings[j].indexOf(".") + 1);
                    if (tag.equals("Sf_ID") || tag.equals("Entry_ID") || tag.equals("Spectral_peak_list_ID")) {
                        continue;
                    }
                    if (tag.equals("Resonance_ID") || tag.equals("Resonance_count")) {
                        continue;
                    }
                    List<String> column = loop.getColumnAsListIfExists(tag);
                    if (column != null) {
                        for (int i = 0, n = column.size(); i < n; i++) {
                            int idNum = Integer.parseInt((String) peakIdColumn.get(i));
                            int sDim = Integer.parseInt((String) sdimColumn.get(i)) - 1;
                            String value = (String) column.get(i);
                            if (!value.equals(".") && !value.equals("?")) {
                                Peak peak = peakList.getPeakByID(idNum);
                                PeakDim peakDim = peak.getPeakDim(sDim);
                                if (peakDim != null) {
                                    peakDim.setAttribute(tag, value);
                                }
                            }
                        }
                    }
                }
                loop = saveframe.getLoop("_Assigned_peak_chem_shift");

                if (loop != null) {
                    List<String> peakidColumn = loop.getColumnAsList("Peak_ID");
                    List<String> spectralDimColumn = loop.getColumnAsList("Spectral_dim_ID");
                    List<String> valColumn = loop.getColumnAsList("Val");
                    List<String> resonanceColumn = loop.getColumnAsList("Resonance_ID");
                    for (int i = 0, n = peakidColumn.size(); i < n; i++) {
                        String value = null;
                        int idNum = 0;
                        if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                            idNum = NvUtil.toInt(value);
                        } else {
                            //throw new TclException("Invalid peak id value at row \""+i+"\"");
                            continue;
                        }
                        int sDim = 0;
                        long resonanceID = -1;
                        if ((value = NvUtil.getColumnValue(spectralDimColumn, i)) != null) {
                            sDim = NvUtil.toInt(value) - 1;
                        } else {
                            throw new ParseException("Invalid spectral dim value at row \"" + i + "\"");
                        }
                        if ((value = NvUtil.getColumnValue(valColumn, i)) != null) {
                            NvUtil.toFloat(value);  // fixme shouldn't we use this
                        }
                        if ((value = NvUtil.getColumnValue(resonanceColumn, i)) != null) {
                            resonanceID = NvUtil.toLong(value);
                        }
                        Peak peak = peakList.getPeakByID(idNum);
                        PeakDim peakDim = peak.getPeakDim(sDim);
                        if (resonanceID != -1) {
                            addResonance(resonanceID, peakDim);
                        }
//                    Resonance res = resFactory.get(resonanceID);
//                    if (res == null) {
//                        resFactory.build(resonanceID);
//                    }
//                    peakDim.setResonance(resonanceID);
                    }
                } else {
                    System.out.println("No \"Assigned Peak Chem Shift\" loop");
                }
            }
        }
    }

    public void processChemicalShifts(Saveframe saveframe, int ppmSet) throws ParseException {
        Loop loop = saveframe.getLoop("_Atom_chem_shift");
        if (loop != null) {
            List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID");
            List<String> atomColumn = loop.getColumnAsList("Atom_ID");
            List<String> typeColumn = loop.getColumnAsList("Atom_type");
            List<String> valColumn = loop.getColumnAsList("Val");
            List<String> valErrColumn = loop.getColumnAsList("Val_err");
            List<String> resColumn = loop.getColumnAsList("Resonance_ID");
            ResonanceFactory resFactory = PeakDim.resFactory;
            for (int i = 0; i < entityAssemblyIDColumn.size(); i++) {
                String iEntity = (String) entityIDColumn.get(i);
                String entityAssemblyID = (String) entityAssemblyIDColumn.get(i);
                if (iEntity.equals("?")) {
                    continue;
                }
                String iRes = (String) compIdxIDColumn.get(i);
                String atomName = (String) atomColumn.get(i);
                String atomType = (String) typeColumn.get(i);
                String value = (String) valColumn.get(i);
                String valueErr = (String) valErrColumn.get(i);
                String resIDStr = ".";
                if (resColumn != null) {
                    resIDStr = (String) resColumn.get(i);
                }
                if (entityAssemblyID.equals(".")) {
                    entityAssemblyID = "1";
                }
                String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
                Compound compound = (Compound) Molecule.compoundMap.get(mapID);
                if (compound == null) {
                    //throw new ParseException("invalid compound in assignments saveframe \""+mapID+"\"");
                    System.err.println("invalid compound in assignments saveframe \"" + mapID + "\"");
                    continue;
                }
                Atom atom = compound.getAtomLoose(atomName);
                if (atom == null) {
                    if (atomName.startsWith("H")) {
                        atom = compound.getAtom(atomName + "1");
                    }
                }
                if (atom == null) {
                    atom = Atom.genAtomWithElement(atomName, atomType);
                    compound.addAtom(atom);
                }
                if (atom == null) {
                    throw new ParseException("invalid atom in assignments saveframe \"" + mapID + "." + atomName + "\"");
                }
                SpatialSet spSet = atom.spatialSet;
                if (ppmSet < 0) {
                    ppmSet = 0;
                }
                int structureNum = ppmSet;
                if (spSet == null) {
                    throw new ParseException("invalid spatial set in assignments saveframe \"" + mapID + "." + atomName + "\"");
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
//                    ResonanceSet resonanceSet = resonance.getResonanceSet();
//                    if (resonanceSet == null) {
//                        resonanceSet = new ResonanceSet(resonance);
//                    }
                        atom.setResonance(resonance);
                        resonance.setAtom(atom);
                    }
                }
            }
        }
    }

    public void processConformer(Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_Atom_site");
        if (loop == null) {
            System.err.println("No \"_Atom_site\" loop");
            return;
        }
        List<String> entityAssemblyIDColumn = loop.getColumnAsList("Label_entity_assembly_ID");
        List<String> entityIDColumn = loop.getColumnAsList("Label_entity_ID");
        List<String> compIdxIDColumn = loop.getColumnAsList("Label_comp_index_ID");
        List<String> atomColumn = loop.getColumnAsList("Label_atom_ID");
        List<String> xColumn = loop.getColumnAsList("Cartn_x");
        List<String> yColumn = loop.getColumnAsList("Cartn_y");
        List<String> zColumn = loop.getColumnAsList("Cartn_z");
        List<String> resColumn = loop.getColumnAsListIfExists("Resonance_ID");
        List<String> modelColumn = loop.getColumnAsList("Model_ID");
        TreeSet<Integer> selSet = new TreeSet<Integer>();
        Molecule molecule = null;
        int lastStructure = -1;
        for (int i = 0; i < entityAssemblyIDColumn.size(); i++) {
            String iEntity = (String) entityIDColumn.get(i);
            String entityAssemblyID = (String) entityAssemblyIDColumn.get(i);
            if (iEntity.equals("?")) {
                continue;
            }
            String iRes = (String) compIdxIDColumn.get(i);
            String atomName = (String) atomColumn.get(i);
            String xStr = (String) xColumn.get(i);
            String yStr = (String) yColumn.get(i);
            String zStr = (String) zColumn.get(i);
            String modelStr = (String) modelColumn.get(i);
            String resIDStr = ".";
            if (resColumn != null) {
                resIDStr = (String) resColumn.get(i);
            }
            if (entityAssemblyID.equals(".")) {
                entityAssemblyID = "1";
            }
            String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
            Compound compound = (Compound) Molecule.compoundMap.get(mapID);
            if (compound == null) {
                //throw new ParseException("invalid compound in conformer saveframe \""+mapID+"\"");
                System.err.println("invalid compound in conformer saveframe \"" + mapID + "\"");
                continue;
            }
            if (molecule == null) {
                molecule = compound.molecule;
            }
            Atom atom = compound.getAtomLoose(atomName);
            if (atom == null) {
                System.err.println("No atom \"" + mapID + "." + atomName + "\"");
                continue;
                //throw new ParseException("invalid atom in conformer saveframe \""+mapID+"."+atomName+"\"");
            }
            int structureNumber = Integer.parseInt(modelStr);
            Integer intStructure = Integer.valueOf(structureNumber);
            if (intStructure != lastStructure) {
                molecule.nullCoords(structureNumber);
                selSet.add(intStructure);
                molecule.structures.add(intStructure);
            }
            lastStructure = intStructure;
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);
            double z = Double.parseDouble(zStr);
            String coordSetName = compound.molecule.getFirstCoordSet().getName();
            atom.setPointValidity(structureNumber, true);
            Point3 pt = new Point3(x, y, z);
            atom.setPoint(structureNumber, pt);
            //  atom.setOccupancy((float) atomParse.occupancy);
            //  atom.setBFactor((float) atomParse.bfactor);
        }
        if (molecule != null) {
            molecule.setActiveStructures(selSet);
            for (Integer iStructure : selSet) {
                molecule.genCoords(iStructure, true);
            }
        }
    }

    public void processDihedralConstraints(Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_Torsion_angle_constraint");
        if (loop == null) {
            throw new ParseException("No \"_Torsion_angle_constraint\" loop");
        }
        List<String>[] entityAssemblyIDColumns = new ArrayList[4];
        List<String>[] entityIDColumns = new ArrayList[4];
        List<String>[] compIdxIDColumns = new ArrayList[4];
        List<String>[] atomColumns = new ArrayList[4];
        List<String>[] resonanceColumns = new ArrayList[4];
        for (int i = 1; i <= 4; i++) {
            entityAssemblyIDColumns[i - 1] = loop.getColumnAsList("Entity_assembly_ID_" + i);
            entityIDColumns[i - 1] = loop.getColumnAsList("Entity_ID_" + i);
            compIdxIDColumns[i - 1] = loop.getColumnAsList("Comp_index_ID_" + i);
            atomColumns[i - 1] = loop.getColumnAsList("Atom_ID_" + i);
            resonanceColumns[i - 1] = loop.getColumnAsList("Resonance_ID_" + i);
        }
        List<String> angleNameColumn = loop.getColumnAsList("Torsion_angle_name");
        List<String> lowerColumn = loop.getColumnAsList("Angle_lower_bound_val");
        List<String> upperColumn = loop.getColumnAsList("Angle_upper_bound_val");
        AngleConstraintSet angleSet = AngleConstraintSet.addSet(saveframe.getName().substring(5));
        for (int i = 0; i < entityAssemblyIDColumns[0].size(); i++) {
            SpatialSet[] spSets = new SpatialSet[4];
            for (int iAtom = 0; iAtom < 4; iAtom++) {
                spSets[iAtom] = getSpatialSet(entityAssemblyIDColumns[iAtom], entityIDColumns[iAtom], compIdxIDColumns[iAtom], atomColumns[iAtom], resonanceColumns[iAtom], i).getFirstSet();
            }
            String upperValue = (String) upperColumn.get(i);
            String lowerValue = (String) lowerColumn.get(i);
            String name = (String) angleNameColumn.get(i);
            double upper = Double.parseDouble(upperValue);
            double lower = 1.8;
            if (!lowerValue.equals(".")) {
                lower = Double.parseDouble(lowerValue);
            }
            AngleConstraint aCon = new AngleConstraint(name, spSets, lower, upper);
            angleSet.add(aCon);
        }
    }

    public void processNEFDihedralConstraints(Saveframe saveframe, Dihedral dihedral) throws ParseException {
        Loop loop = saveframe.getLoop("_nef_dihedral_restraint");
        if (loop == null) {
            throw new ParseException("No \"_nef_dihedral_restraint\" loop");
        }
        List<String>[] chainCodeColumns = new ArrayList[4];
        List<String>[] sequenceCodeColumns = new ArrayList[4];
        List<String>[] residueNameColumns = new ArrayList[4];
        List<String>[] atomNameColumns = new ArrayList[4];
        for (int i = 1; i <= 4; i++) {
            chainCodeColumns[i - 1] = loop.getColumnAsList("chain_code_" + i);
            sequenceCodeColumns[i - 1] = loop.getColumnAsList("sequence_code_" + i);
            residueNameColumns[i - 1] = loop.getColumnAsList("residue_name_" + i);
            atomNameColumns[i - 1] = loop.getColumnAsList("atom_name_" + i);
        }
        List<String> lowerColumn = loop.getColumnAsList("lower_limit");
        List<String> upperColumn = loop.getColumnAsList("upper_limit");
        for (int i = 0; i < atomNameColumns[0].size(); i++) {
            String upperValue = (String) upperColumn.get(i);
            String lowerValue = (String) lowerColumn.get(i);
            double upper = Double.parseDouble(upperValue);
            double lower = Double.parseDouble(lowerValue);
            if (lower < -180) {
                lower += 360;
                upper += 360;
            }

            Atom[] atoms = new Atom[4];
            for (int atomIndex = 0; atomIndex < 4; atomIndex++) {
                String atomName = (String) atomNameColumns[atomIndex].get(i);
                String chainCode = (String) chainCodeColumns[atomIndex].get(i);
                String sequenceCode = (String) sequenceCodeColumns[atomIndex].get(i);
                String fullAtom = chainCode + ":" + sequenceCode + "." + atomName;
                atoms[atomIndex] = Molecule.getAtomByName(fullAtom);
            }
            double scale = 1.0;
            try {
                dihedral.addBoundary(atoms, lower, upper, scale);
            } catch (InvalidMoleculeException imE) {

            }

        }
    }

    public void processRDCConstraints(Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_RDC");
        if (loop == null) {
            throw new ParseException("No \"_RDC\" loop");
        }
        //saveframe.getTagsIgnoreMissing(tagCategory);
        List<String>[] entityAssemblyIDColumns = new ArrayList[2];
        List<String>[] entityIDColumns = new ArrayList[2];
        List<String>[] compIdxIDColumns = new ArrayList[2];
        List<String>[] atomColumns = new ArrayList[2];
        List<String>[] resonanceColumns = new ArrayList[2];
        for (int i = 1; i <= 2; i++) {
            entityAssemblyIDColumns[i - 1] = loop.getColumnAsList("Entity_assembly_ID_" + i);
            entityIDColumns[i - 1] = loop.getColumnAsList("Entity_ID_" + i);
            compIdxIDColumns[i - 1] = loop.getColumnAsList("Comp_index_ID_" + i);
            atomColumns[i - 1] = loop.getColumnAsList("Atom_ID_" + i);
            resonanceColumns[i - 1] = loop.getColumnAsList("Resonance_ID_" + i);
        }
        List<Double> valColumn = loop.getColumnAsDoubleList("Val", null);
        List<Double> errColumn = loop.getColumnAsDoubleList("Val_err", null);
        List<Double> lengthColumn = loop.getColumnAsDoubleList("Val_bond_length", null);
        RDCConstraintSet rdcSet = RDCConstraintSet.addSet(saveframe.getName().substring(5));
        for (int i = 0; i < entityAssemblyIDColumns[0].size(); i++) {
            SpatialSet[] spSets = new SpatialSet[4];
            for (int iAtom = 0; iAtom < 2; iAtom++) {
                SpatialSetGroup spG = getSpatialSet(entityAssemblyIDColumns[iAtom], entityIDColumns[iAtom], compIdxIDColumns[iAtom], atomColumns[iAtom], resonanceColumns[iAtom], i);
                if (spG != null) {
                    spSets[iAtom] = spG.getFirstSet();
                    if (errColumn.get(i) != null) {
                        RDC aCon = new RDC(rdcSet, spSets[0], spSets[1], valColumn.get(i), errColumn.get(i));
                        rdcSet.add(aCon);
                    }
                }
            }
        }
    }

    public void processGenDistConstraints(Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_Gen_dist_constraint");
        if (loop == null) {
            throw new ParseException("No \"_Gen_dist_constraint\" loop");
        }
        List<String>[] entityAssemblyIDColumns = new ArrayList[2];
        List<String>[] entityIDColumns = new ArrayList[2];
        List<String>[] compIdxIDColumns = new ArrayList[2];
        List<String>[] atomColumns = new ArrayList[2];
        List<String>[] resonanceColumns = new ArrayList[2];
        entityAssemblyIDColumns[0] = loop.getColumnAsList("Entity_assembly_ID_1");
        entityIDColumns[0] = loop.getColumnAsList("Entity_ID_1");
        compIdxIDColumns[0] = loop.getColumnAsList("Comp_index_ID_1");
        atomColumns[0] = loop.getColumnAsList("Atom_ID_1");
        resonanceColumns[0] = loop.getColumnAsList("Resonance_ID_1");
        entityAssemblyIDColumns[1] = loop.getColumnAsList("Entity_assembly_ID_2");
        entityIDColumns[1] = loop.getColumnAsList("Entity_ID_2");
        compIdxIDColumns[1] = loop.getColumnAsList("Comp_index_ID_2");
        atomColumns[1] = loop.getColumnAsList("Atom_ID_2");
        resonanceColumns[0] = loop.getColumnAsList("Resonance_ID_2");
        List<String> constraintIDColumn = loop.getColumnAsList("ID");
        List<String> lowerColumn = loop.getColumnAsList("Distance_lower_bound_val");
        List<String> upperColumn = loop.getColumnAsList("Distance_upper_bound_val");
        List<String> peakListIDColumn = loop.getColumnAsList("Spectral_peak_list_ID");
        List<String> peakIDColumn = loop.getColumnAsList("Spectral_peak_ID");
        Atom[] atoms = new Atom[2];
        SpatialSetGroup[] spSets = new SpatialSetGroup[2];
        String[] resIDStr = new String[2];
        PeakList peakList = null;
        String lastPeakListIDStr = "";
        NoeSet noeSet = NoeSet.addSet(saveframe.getName().substring(5));
        for (int i = 0; i < entityAssemblyIDColumns[0].size(); i++) {
            boolean okAtoms = true;
            for (int iAtom = 0; iAtom < 2; iAtom++) {
                spSets[iAtom] = null;
                String iEntity = (String) entityIDColumns[iAtom].get(i);
                String entityAssemblyID = (String) entityAssemblyIDColumns[iAtom].get(i);
                if (iEntity.equals("?")) {
                    continue;
                }
                String iRes = (String) compIdxIDColumns[iAtom].get(i);
                String atomName = (String) atomColumns[iAtom].get(i);
                resIDStr[iAtom] = ".";
                if (resonanceColumns[iAtom] != null) {
                    resIDStr[iAtom] = (String) resonanceColumns[iAtom].get(i);
                }
                if (entityAssemblyID.equals(".")) {
                    entityAssemblyID = "1";
                }
                String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
                Compound compound1 = (Compound) Molecule.compoundMap.get(mapID);
                if (compound1 == null) {
                    //throw new ParseException("invalid compound in distance constraints saveframe \""+mapID+"\"");
                    System.err.println("invalid compound in distance constraints saveframe \"" + mapID + "\"");
                } else if ((atomName.charAt(0) == 'Q') || (atomName.charAt(0) == 'M')) {
                    Residue residue = (Residue) compound1;
                    Atom[] pseudoAtoms = ((Residue) compound1).getPseudo(atomName);
                    if (pseudoAtoms == null) {
                        System.err.println(residue.getIDNum() + " " + residue.getNumber() + " " + residue.getName());
                        System.err.println("invalid pseudo in distance constraints saveframe \"" + mapID + "\" " + atomName);
                        okAtoms = false;
                    } else {
                        spSets[iAtom] = new SpatialSetGroup(pseudoAtoms);
                    }
                } else {
                    atoms[iAtom] = compound1.getAtomLoose(atomName);
                    if (atoms[iAtom] == null) {
                        throw new ParseException("invalid atom in distance constraints saveframe \"" + mapID + "." + atomName + "\"");
                    }
                    spSets[iAtom] = new SpatialSetGroup(atoms[iAtom].spatialSet);
                }
                if (spSets[iAtom] == null) {
                    throw new ParseException("invalid spatial set in distance constraints saveframe \"" + mapID + "." + atomName + "\"");
                }
            }
            String upperValue = (String) upperColumn.get(i);
            String lowerValue = (String) lowerColumn.get(i);
            String peakListIDStr = (String) peakListIDColumn.get(i);
            String peakID = (String) peakIDColumn.get(i);
            String constraintID = (String) constraintIDColumn.get(i);
            if (!peakListIDStr.equals(lastPeakListIDStr)) {
                if (peakListIDStr.equals(".")) {
                    if (peakList == null) {
                        peakList = new PeakList("gendist", 2);
                    }
                } else {
                    try {
                        int peakListID = Integer.parseInt(peakListIDStr);
                        peakList = PeakList.get(peakListID);
                    } catch (NumberFormatException nFE) {
                        throw new ParseException("Invalid peak list id (not int) \"" + peakListIDStr + "\"");
                    }
                }
            }
            lastPeakListIDStr = peakListIDStr;
            Peak peak = null;
            if (peakList != null) {
                if (peakID.equals(".")) {
                    peakID = constraintID;
                    int idNum = Integer.parseInt(peakID);
                    while ((peak = peakList.getPeak(idNum)) == null) {
                        peakList.addPeak();
                    }
                } else {
                    int idNum = Integer.parseInt(peakID);
                    peak = peakList.getPeakByID(idNum);
                }
                Noe noe = new Noe(peak, spSets[0], spSets[1], 1.0);
                double upper = 1000000.0;
                if (upperValue.equals(".")) {
                    System.err.println("Upper value is a \".\" at line " + i);
                } else {
                    upper = Double.parseDouble(upperValue);
                }
                noe.setUpper(upper);
                double lower = 1.8;
                if (!lowerValue.equals(".")) {
                    lower = Double.parseDouble(lowerValue);
                }
                noe.setLower(lower);
                noe.setPpmError(1.0);
                noe.setIntensity(Math.pow(upper, -6.0) * 10000.0);
                noe.setVolume(Math.pow(upper, -6.0) * 10000.0);
                noeSet.add(noe);
            }
        }
        noeSet.updateNPossible(null);
        noeSet.setCalibratable(false);
    }

    public void processNEFDistanceRestraints(Saveframe saveframe, EnergyLists energyList) throws ParseException {
        Loop loop = saveframe.getLoop("_nef_distance_restraint");
        if (loop == null) {
            throw new ParseException("No \"_nef_distance_restraint\" loop");
        }
        List<String>[] chainCodeColumns = new ArrayList[2];
        List<String>[] sequenceColumns = new ArrayList[2];
        List<String>[] residueNameColumns = new ArrayList[2];
        List<String>[] atomNameColumns = new ArrayList[2];
        List<Integer> indexColumn = new ArrayList<>();

        indexColumn = loop.getColumnAsIntegerList("index", 0);

        chainCodeColumns[0] = loop.getColumnAsList("chain_code_1");
        sequenceColumns[0] = loop.getColumnAsList("sequence_code_1");
        residueNameColumns[0] = loop.getColumnAsList("residue_name_1");
        atomNameColumns[0] = loop.getColumnAsList("atom_name_1");

        chainCodeColumns[1] = loop.getColumnAsList("chain_code_2");
        sequenceColumns[1] = loop.getColumnAsList("sequence_code_2");
        residueNameColumns[1] = loop.getColumnAsList("residue_name_2");
        atomNameColumns[1] = loop.getColumnAsList("atom_name_2");

        List<String> lowerColumn = loop.getColumnAsList("lower_limit");
        List<String> upperColumn = loop.getColumnAsList("upper_limit");
        ArrayList<String> atomNames[] = new ArrayList[2];
        atomNames[0] = new ArrayList<>();
        atomNames[1] = new ArrayList<>();

        for (int i = 0; i < chainCodeColumns[0].size(); i++) {
            atomNames[0].clear();
            atomNames[1].clear();
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
            }
            String upperValue = (String) upperColumn.get(i);
            String lowerValue = (String) lowerColumn.get(i);
            double upper = 1000000.0;
            if (upperValue.equals(".")) {
                System.err.println("Upper value is a \".\" at line " + i);
            } else {
                upper = Double.parseDouble(upperValue);
            }
            double lower = 1.8;
            if (!lowerValue.equals(".")) {
                lower = Double.parseDouble(lowerValue);
            }

            Util.setStrictlyNEF(true);
            try {
                energyList.addDistanceConstraint(atomNames[0], atomNames[1], lower, upper);
            } catch (IllegalArgumentException iaE) {
                int index = indexColumn.get(i);
                throw new ParseException("Error parsing NEF distance constraints at index  \"" + index + "\" " + iaE.getMessage());
            }
            Util.setStrictlyNEF(false);
        }
    }

    public void process() throws ParseException, IllegalArgumentException {
        String[] argv = {};
        process(argv);
    }

    public void process(String[] argv) throws ParseException, IllegalArgumentException {
        if ((argv.length != 0) && (argv.length != 3)) {
            throw new IllegalArgumentException("?shifts fromSet toSet?");
        }
        if (DEBUG) {
            System.out.println("nSave " + star3.getSaveFrameNames());
        }
        AtomResonanceFactory resFactory = (AtomResonanceFactory) PeakDim.resFactory;
        if (argv.length == 0) {
            hasResonances = false;
            Molecule.compoundMap.clear();
            buildExperiments();
            if (DEBUG) {
                System.err.println("process molecule");
            }
            buildMolecule();
            if (DEBUG) {
                System.err.println("process peak lists");
            }
            buildPeakLists();
            if (DEBUG) {
                System.err.println("process resonance lists");
            }
            buildResonanceLists();
            if (DEBUG) {
                System.err.println("process chem shifts");
            }
            buildChemShifts(-1, 0);
            if (DEBUG) {
                System.err.println("process conformers");
            }
            buildConformers();
            if (DEBUG) {
                System.err.println("process dist constraints");
            }
            buildGenDistConstraints();
            if (DEBUG) {
                System.err.println("process angle constraints");
            }
            buildDihedralConstraints();
            if (DEBUG) {
                System.err.println("process rdc constraints");
            }
            buildRDCConstraints();
            if (DEBUG) {
                System.err.println("process runabout");
            }
            buildRunAbout();
            if (DEBUG) {
                System.err.println("clean resonances");
            }
            resFactory.clean();
            if (DEBUG) {
                System.err.println("process done");
            }
        } else if ("shifts".startsWith(argv[2].toString())) {
            int fromSet = Integer.parseInt(argv[3]);
            int toSet = Integer.parseInt(argv[4]);
            buildChemShifts(fromSet, toSet);
        }
    }

    public Dihedral processNEF() throws ParseException, IllegalArgumentException {
        String[] argv = {};
        return processNEF(argv);
    }

    public Dihedral processNEF(String[] argv) throws ParseException, IllegalArgumentException {
        if ((argv.length != 0) && (argv.length != 3)) {
            throw new IllegalArgumentException("?shifts fromSet toSet?");
        }
        if (DEBUG) {
            System.out.println("nSave " + star3.getSaveFrameNames());
        }
        AtomResonanceFactory resFactory = (AtomResonanceFactory) PeakDim.resFactory;
        Dihedral dihedral = null;
        if (argv.length == 0) {
            hasResonances = false;
            Molecule.compoundMap.clear();
//            buildExperiments();
            if (DEBUG) {
                System.err.println("process molecule");
            }
            Molecule molecule = buildNEFMolecule();
            molecule.setMethylRotationActive(true);
            EnergyLists energyList = new EnergyLists(molecule);
            dihedral = new Dihedral(energyList, false);

            energyList.makeCompoundList(molecule);
//            System.err.println("process peak lists");
//            buildPeakLists();
//            System.err.println("process resonance lists");
//            buildResonanceLists();
//            System.err.println("process chem shifts");
//            buildChemShifts(-1, 0);
//            System.err.println("process conformers");
//            buildConformers();
            if (DEBUG) {
                System.err.println("process dist constraints");
            }
            buildNEFDistanceRestraints(energyList);
            if (DEBUG) {
                System.err.println("process angle constraints");
            }
            buildNEFDihedralConstraints(dihedral);
//            System.err.println("process runabout");
//            buildRunAbout();
//            System.err.println("clean resonances");
//            resFactory.clean();
//            System.err.println("process done");
        } else if ("shifts".startsWith(argv[2].toString())) {
            int fromSet = Integer.parseInt(argv[3]);
            int toSet = Integer.parseInt(argv[4]);
            buildChemShifts(fromSet, toSet);
        }
        return dihedral;
    }

}
