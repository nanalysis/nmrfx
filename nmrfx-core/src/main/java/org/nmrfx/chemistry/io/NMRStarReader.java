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
import org.nmrfx.chemistry.constraints.*;
import org.nmrfx.chemistry.relax.*;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.*;
import org.nmrfx.peaks.io.PeakPathReader;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.STAR3;
import org.nmrfx.star.Saveframe;
import org.nmrfx.utilities.NvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileSystems;
import java.util.*;

/**
 * @author brucejohnson
 */
@PluginAPI("ring")
public class NMRStarReader {
    private static final Logger log = LoggerFactory.getLogger(NMRStarReader.class);

    final STAR3 star3;
    final File starFile;

    Map<String, Entity> entities = new HashMap<>();
    boolean hasResonances = false;
    List<PeakDim> peakDimsWithoutResonance = new ArrayList<>();
    MoleculeBase molecule = null;

    public NMRStarReader(final File starFile, final STAR3 star3) {
        this.star3 = star3;
        this.starFile = starFile;
    }

    public static STAR3 read(String starFileName) throws ParseException {
        File file = new File(starFileName);
        return read(file);
    }

    public static void readFromString(String starData) throws ParseException {
        StringReader stringReader;
        stringReader = new StringReader(starData);
        read(stringReader, null);
    }


    public static STAR3 read(File starFile) throws ParseException {
        STAR3 star3;
        try (FileReader fileReader = new FileReader(starFile)) {
            star3 = read(fileReader, starFile);
        } catch (IOException e) {
            throw new ParseException(e.getMessage());
        }
        return star3;
    }
    public static STAR3 read(Reader reader, File starFile) throws ParseException {
        BufferedReader bfR = new BufferedReader(reader);
        STAR3 star = new STAR3(bfR, "star3");

        try {
            star.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + star.getLastLine());
        }
        NMRStarReader nmrStarReader = new NMRStarReader(starFile, star);
        nmrStarReader.process();
        return star;
    }

    public static void readChemicalShiftsFromString(String starData, int ppmSet) throws ParseException {
        StringReader stringReader;
        stringReader = new StringReader(starData);
        BufferedReader bfR = new BufferedReader(stringReader);
        STAR3 star = new STAR3(bfR, "star3");
        try {
            star.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + star.getLastLine());
        }
        NMRStarReader reader = new NMRStarReader(null, star);
        reader.buildChemShifts(0, ppmSet);
    }

    public static void readChemicalShifts(File starFile, int ppmSet) throws ParseException {
        FileReader fileReader;
        try {
            fileReader = new FileReader(starFile);
        } catch (FileNotFoundException ex) {
            return;
        }
        BufferedReader bfR = new BufferedReader(fileReader);

        STAR3 star = new STAR3(bfR, "star3");

        try {
            star.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + star.getLastLine());
        }
        NMRStarReader reader = new NMRStarReader(starFile, star);
        reader.buildChemShifts(0, ppmSet);
    }

    static void updateFromSTAR3ChemComp(Saveframe saveframe, Compound compound) throws ParseException {
        Loop loop = saveframe.getLoop("_Chem_comp_atom");
        if (loop == null) {
            throw new ParseException("No \"_Chem_comp_atom\" loop in \"" + saveframe.getName() + "\"");
        }
        List<String> idColumn = loop.getColumnAsList("Atom_ID");
        List<String> typeColumn = loop.getColumnAsList("Type_symbol");
        for (int i = 0; i < idColumn.size(); i++) {
            String aName = idColumn.get(i);
            String aType = typeColumn.get(i);
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
                String aName1 = id1Column.get(i);
                String aName2 = id2Column.get(i);
                String orderString = orderColumn.get(i);
                Atom atom1 = compound.getAtom(aName1);
                Atom atom2 = compound.getAtom(aName2);
                Order order;
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
            compound.getAtoms().stream().filter((atom) -> (atom.bonds == null)).forEachOrdered((_item) -> log.info("no bonds"));
        }
    }

    static void addComponents(Saveframe saveframe, List<String> idColumn, List<String> authSeqIDColumn, List<String> compIDColumn, List<String> entityIDColumn, Compound compound) throws ParseException {
        for (int i = 0; i < compIDColumn.size(); i++) {
            String resName = compIDColumn.get(i);
            String seqNumber = authSeqIDColumn.get(i);
            if ((seqNumber == null) || seqNumber.isBlank()) {
                seqNumber = resName;
            }
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
                log.warn("No save frame: {}", ccSaveFrameName);
            }
        }

    }

    static void finishSaveFrameProcessing(final NMRStarReader nmrStar, Saveframe saveframe, Compound compound, String mapID) throws ParseException {
        Loop loop = saveframe.getLoop("_Entity_comp_index");
        if (loop != null) {
            List<String> idColumn = loop.getColumnAsList("ID");
            List<String> authSeqIDColumn = loop.getColumnAsList("Auth_seq_ID", "");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIDColumn = loop.getColumnAsList("Comp_ID");
            nmrStar.addCompound(mapID, compound);
            addComponents(saveframe, idColumn, authSeqIDColumn, compIDColumn, entityIDColumn, compound);
        } else {
            log.info("No \"_Entity_comp_index\" loop");
        }

    }

    public void finishSaveFrameProcessing(final Polymer polymer, final Saveframe saveframe, final String nomenclature, final boolean capped) throws ParseException {
        String lstrandID = saveframe.getOptionalValue("_Entity", "Polymer_strand_ID");
        if (lstrandID != null) {
            log.debug("set strand {}", lstrandID);
            polymer.setStrandID(lstrandID);
        }
        String type = saveframe.getOptionalValue("_Entity", "Polymer_type");
        if (type != null) {
            log.debug("set polytype {}", type);

            polymer.setPolymerType(type);
        }
        Loop loop = saveframe.getLoop("_Entity_comp_index");
        if (loop == null) {
            log.info("No \"_Entity_comp_index\" loop");
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
            List<String> compIndexIDColumn = loop.getColumnAsList("Comp_index_ID");
            List<String> atomIDColumn = loop.getColumnAsList("Atom_ID");
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

    public void addResidues(Polymer polymer, Saveframe saveframe, List<String> idColumn, List<String> authSeqIDColumn, List<String> compIDColumn, List<String> entityIDColumn, String frameNomenclature) throws ParseException {
        String reslibDir = PDBFile.getReslibDir(frameNomenclature);
        polymer.setNomenclature(frameNomenclature.toUpperCase());
        Sequence sequence = new Sequence();
        for (int i = 0; i < compIDColumn.size(); i++) {
            String resName = compIDColumn.get(i);
            String iEntity = entityIDColumn.get(i);
            String iRes = idColumn.get(i);
            String mapID = polymer.assemblyID + "." + iEntity + "." + iRes;
            if (authSeqIDColumn != null) {
                String iResTemp = authSeqIDColumn.get(i);
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
                    sequence.addResidue(reslibDir + "/" + Sequence.getAliased(resName.toLowerCase()) + ".prf", residue, resPos, "", false);
                } catch (MoleculeIOException psE) {
                    throw new ParseException(psE.getMessage());
                }
            }
        }
        sequence.removeBadBonds();
    }

    public void addCompound(String id, Compound compound) {
        var compoundMap = MoleculeBase.compoundMap();
        compoundMap.put(id, compound);
    }

    public void buildChemShifts(int fromSet, final int toSet) throws ParseException {
        Iterator<Saveframe> iter = star3.getSaveFrames().values().iterator();
        int iSet = 0;
        while (iter.hasNext()) {
            Saveframe saveframe = iter.next();
            if (saveframe.getCategoryName().equals("assigned_chemical_shifts")) {
                log.debug("process chem shifts {}", saveframe.getName());
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
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("conformer_family_coord_set")) {
                log.debug("process conformers {}", saveframe.getName());

                processConformer(saveframe);
            }
        }
    }

    public void buildNOE() throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("heteronucl_NOEs")) {
                log.debug("process NOEs {}", saveframe.getName());

                processNOE(saveframe);
            }
        }
    }

    public void buildRelaxation(RelaxTypes expType) throws ParseException {
        String expName = expType.getName().toUpperCase();
        if (expName.equals("R1")) {
            expName = "T1";
        } else if (expName.equals("R2")) {
            expName = "T2";
        }
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("heteronucl_" + expName + "_relaxation")) {
                log.debug("process {} relaxation {}", expName, saveframe.getName());

                processRelaxation(saveframe, expType);
            }
        }
    }

    public void buildOrder() throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("order_parameters")) {
                log.debug("process order pars {}", saveframe.getName());

                processOrder(saveframe);
            }
        }
    }

    public void buildDataset(Map tagMap, String datasetName) throws ParseException, IOException {
        String name = STAR3.getTokenFromMap(tagMap, "Name");
        String path = STAR3.getTokenFromMap(tagMap, "Directory_path");
        File file = new File(path, name);
        if (datasetName.equals("")) {
            datasetName = file.getAbsolutePath();
        }
        try {
            log.debug("open {}", file.getAbsolutePath());

            if (!file.exists()) {
                file = FileSystems.getDefault().getPath(starFile.getParentFile().getParent(), "datasets", file.getName()).toFile();
            }
            DatasetBase dataset = new DatasetBase(file.getAbsolutePath(), datasetName, false, false);
        } catch (IOException | IllegalArgumentException tclE) {
            log.warn(tclE.getMessage(), tclE);
        }
    }

    public void buildDihedralConstraints() throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("torsion_angle_constraints")) {
                log.debug("process torsion angle constraints {}", saveframe.getName());
                processDihedralConstraints(saveframe);
            }
        }
    }

    public void buildRDCConstraints() throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("RDC_constraints")) {
                log.debug("process RDC constraints {}", saveframe.getName());
                processRDCConstraints(saveframe);
            }
        }
    }

    public void buildEntity(MoleculeBase molecule, Map tagMap, int index) throws ParseException {
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
        log.debug("process entity {}", saveFrameName);
        Saveframe saveframe = star3.getSaveFrames().get(saveFrameName);
        if (saveframe != null) {

            String type = saveframe.getValue("_Entity", "Type");
            String name = saveframe.getValue("_Entity", "Name");
            String nomenclature = saveframe.getValue("_Entity", "Nomenclature", "");
            if (nomenclature.equals("")) {
                nomenclature = "IUPAC";
            }
            String cappedString = saveframe.getValue("_Entity", "Capped", "");
            boolean capped = !cappedString.equalsIgnoreCase("no");
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
                name = saveframe.getValue("_Entity","Nonpolymer_comp_ID", name);
                Entity entity = molecule.getEntity(name);
                if (entity == null) {
                    Compound compound = new Compound("1", name, entityAssemblyName);
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
            log.warn("Saveframe \"{}\" doesn't exist", saveFrameName);
        }
    }

    public void buildExperiments() throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("experiment_list")) {
                log.debug("process experiments {}", saveframe.getName());
                int nExperiments;
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
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("general_distance_constraints")) {
                log.debug("process general distance constraints {}", saveframe.getName());
                processGenDistConstraints(saveframe);
            }
        }
    }

    public void buildMolecule() throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            log.debug(saveframe.getCategoryName());
            if (saveframe.getCategoryName().equals("assembly")) {
                log.debug("process molecule >>{}<<", saveframe.getName());
                String molName = saveframe.getValue("_Assembly", "Name");
                if (molName.equals("?")) {
                    molName = "noname";
                }
                molecule = MoleculeFactory.newMolecule(molName);
                int nEntities = saveframe.loopCount("_Entity_assembly");
                log.debug("mol name {} nEntities {}", molName, nEntities);
                for (int i = 0; i < nEntities; i++) {
                    Map map = saveframe.getLoopRowMap("_Entity_assembly", i);
                    buildEntity(molecule, map, i);
                }
                molecule.updateSpatialSets();
                try {
                    molecule.genCoords(false);
                } catch (IllegalArgumentException iAE) {
                    log.warn(iAE.getMessage(), iAE);
                }
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

    public void buildPeakLists() throws ParseException {
        peakDimsWithoutResonance.clear();
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("spectral_peak_list")) {
                log.debug("process peaklists {}", saveframe.getName());
                processSTAR3PeakList(saveframe);
            }
        }
        addMissingResonances();
    }

    public void addMissingResonances() {
        ResonanceFactory resFactory = ProjectBase.activeResonanceFactory();
        peakDimsWithoutResonance.forEach((peakDim) -> {
            AtomResonance resonance = resFactory.build();
            resonance.add(peakDim);
        });
    }

    public void buildPeakPaths() throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("nmrfx_peak_path")) {
                log.debug("process nmrfx_peak_path {}", saveframe.getName());
                PeakPathReader peakPathReader = new PeakPathReader();
                peakPathReader.processPeakPaths(saveframe);
            }
        }
    }

    public void buildResonanceLists() throws ParseException {
        var compoundMap = MoleculeBase.compoundMap();
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("resonance_linker")) {
                hasResonances = true;
                log.debug("process resonances {}", saveframe.getName());
                AtomResonance.processSTAR3ResonanceList(saveframe, compoundMap);
            }
        }
    }

    public void buildRunAbout() throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().startsWith("nmrview_")) {
                log.debug("process tool {}", saveframe.getName());
            }
        }
    }

    public Entity getEntity(String entityAssemblyIDString, String entityIDString) {
        return entities.get(entityAssemblyIDString + "." + entityIDString);
    }

    public SpatialSetGroup getSpatialSet(List<String> entityAssemblyIDColumn, List<String> entityIDColumn, List<String> compIdxIDColumn, List<String> atomColumn, List<String> resonanceColumn, int i) throws ParseException {
        SpatialSetGroup spg = null;
        String iEntity = entityIDColumn.get(i);
        String entityAssemblyID = entityAssemblyIDColumn.get(i);
        var compoundMap = MoleculeBase.compoundMap();
        if (!iEntity.equals("?")) {
            String iRes = compIdxIDColumn.get(i);
            String atomName = atomColumn.get(i);
            Atom atom;
            if (entityAssemblyID.equals(".")) {
                entityAssemblyID = "1";
            }
            String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
            Compound compound1 = compoundMap.get(mapID);
            if (compound1 != null) {
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
                    log.warn("invalid spatial set in assignments saveframe \"{}.{}\"", mapID, atomName);
                }
            } else {
                log.warn("invalid compound in assignments saveframe \"{}\"", mapID);
            }
        }
        return spg;
    }

    public void processSTAR3PeakList(Saveframe saveframe) throws ParseException {
        ResonanceFactory resFactory = ProjectBase.activeResonanceFactory();
        String listName = saveframe.getValue("_Spectral_peak_list", "Sf_framecode");
        String id = saveframe.getValue("_Spectral_peak_list", "ID");
        String sampleLabel = saveframe.getLabelValue("_Spectral_peak_list", "Sample_label");
        String sampleConditionLabel = saveframe.getOptionalLabelValue("_Spectral_peak_list", "Sample_condition_list_label");
        String datasetName = saveframe.getLabelValue("_Spectral_peak_list", "Experiment_name");
        String nDimString = saveframe.getValue("_Spectral_peak_list", "Number_of_spectral_dimensions");
        String dataFormat = saveframe.getOptionalValue("_Spectral_peak_list", "Text_data_format");
        String expType = saveframe.getOptionalValue("_Spectral_peak_list", "Experiment_type");
        String details = saveframe.getOptionalValue("_Spectral_peak_list", "Details");
        String slidable = saveframe.getOptionalValue("_Spectral_peak_list", "Slidable");
        String scaleStr = saveframe.getOptionalValue("_Spectral_peak_list", "Scale");

        if (dataFormat.equals("text")) {
            log.warn("Peak list is in text format, skipping list");
            log.warn(details);
            return;
        }
        if (nDimString.equals("?")) {
            return;
        }
        if (nDimString.equals(".")) {
            return;
        }
        int nDim = NvUtil.toInt(nDimString);

        PeakList peakList = new PeakList(listName, nDim, NvUtil.toInt(id));

        int nSpectralDim = saveframe.loopCount("_Spectral_dim");
        if (nSpectralDim > nDim) {
            throw new IllegalArgumentException("Too many _Spectral_dim values " + listName + " " + nSpectralDim + " " + nDim);
        }

        peakList.setSampleLabel(sampleLabel);
        peakList.setSampleConditionLabel(sampleConditionLabel);
        peakList.setDatasetName(datasetName);
        peakList.setDetails(details);
        peakList.setExperimentType(expType);
        peakList.setSlideable(slidable.equals("yes"));
        if (scaleStr.length() > 0) {
            peakList.setScale(NvUtil.toDouble(scaleStr));
        }

        for (int i = 0; i < nSpectralDim; i++) {
            SpectralDim sDim = peakList.getSpectralDim(i);

            String value;
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
                int idNum = Integer.parseInt(idColumn.get(i));
                Peak peak = new Peak(peakList, nDim);
                peak.setIdNum(idNum);
                String value;
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
                peakList.addPeakWithoutResonance(peak);
            }

            loop = saveframe.getLoop("_Peak_general_char");
            if (loop != null) {
                List<String> peakidColumn = loop.getColumnAsList("Peak_ID");
                List<String> methodColumn = loop.getColumnAsList("Measurement_method");
                List<String> intensityColumn = loop.getColumnAsList("Intensity_val");
                List<String> errorColumn = loop.getColumnAsList("Intensity_val_err");
                for (int i = 0, n = peakidColumn.size(); i < n; i++) {
                    String value;
                    int idNum;
                    if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                        idNum = NvUtil.toInt(value);
                    } else {
                        //Invalid peak id value
                        continue;
                    }
                    Peak peak = peakList.getPeakByID(idNum);
                    String method = "height";
                    if ((value = NvUtil.getColumnValue(methodColumn, i)) != null) {
                        method = value;
                    }
                    if ((value = NvUtil.getColumnValue(intensityColumn, i)) != null) {
                        float iValue = NvUtil.toFloat(value);
                        switch (method) {
                            case "height":
                                peak.setIntensity(iValue);
                                break;
                            case "volume":
                                // FIXME should set volume/evolume
                                peak.setVolume1(iValue);
                                break;
                            default:
                                // FIXME throw error if don't know type, or add new type dynamically?
                                peak.setIntensity(iValue);
                                break;
                        }
                    }
                    if ((value = NvUtil.getColumnValue(errorColumn, i)) != null) {
                        if (!value.equals(".")) {
                            float iValue = NvUtil.toFloat(value);
                            switch (method) {
                                case "height":
                                    peak.setIntensityErr(iValue);
                                    break;
                                case "volume":
                                    // FIXME should set volume/evolume
                                    peak.setVolume1Err(iValue);
                                    break;
                                default:
                                    // FIXME throw error if don't know type, or add new type dynamically?
                                    peak.setIntensityErr(iValue);
                                    break;
                            }
                        }
                    }
                    // FIXME set error value
                }
            }

            loop = saveframe.getLoop("_Peak_char");
            if (loop == null) {
                throw new ParseException("No \"_Peak_char\" loop");
            } else {
                List<String> peakIdColumn = loop.getColumnAsList("Peak_ID");
                List<String> sdimColumn = loop.getColumnAsList("Spectral_dim_ID");
                String[] peakCharStrings = Peak.getSTAR3CharStrings();
                for (String peakCharString : peakCharStrings) {
                    String tag = peakCharString.substring(peakCharString.indexOf(".") + 1);
                    if (tag.equals("Sf_ID") || tag.equals("Entry_ID") || tag.equals("Spectral_peak_list_ID")) {
                        continue;
                    }
                    if (tag.equals("Resonance_ID") || tag.equals("Resonance_count")) {
                        continue;
                    }
                    List<String> column = loop.getColumnAsListIfExists(tag);
                    if (column != null) {
                        for (int i = 0, n = column.size(); i < n; i++) {
                            int idNum = Integer.parseInt(peakIdColumn.get(i));
                            int sDim = Integer.parseInt(sdimColumn.get(i)) - 1;
                            String value = column.get(i);
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
                        String value;
                        int idNum;
                        if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                            idNum = NvUtil.toInt(value);
                        } else {
                            //Invalid peak id value
                            continue;
                        }
                        int sDim;
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
                        if (resonanceID != -1L) {
                            AtomResonance resonance = resFactory.build(resonanceID);
                            resonance.add(peakDim);
                        } else {
                            peakDimsWithoutResonance.add(peakDim);
                        }
                    }
                } else {
                    log.info("No \"Assigned Peak Chem Shift\" loop");
                }
            }
            loop = saveframe.getLoop("_Peak_coupling");
            if (loop != null) {
                List<Integer> peakIdColumn = loop.getColumnAsIntegerList("Peak_ID", null);
                List<Integer> sdimColumn = loop.getColumnAsIntegerList("Spectral_dim_ID", null);
                List<Double> couplingColumn = loop.getColumnAsDoubleList("Coupling_val", null);
                List<Double> strongCouplingColumn = loop.getColumnAsDoubleList("Strong_coupling_effect_val", null);
                List<Double> intensityColumn = loop.getColumnAsDoubleList("Intensity_val", null);
                List<String> couplingTypeColumn = loop.getColumnAsList("Type");
                int from = 0;
                int to;
                for (int i = 0; i < peakIdColumn.size(); i++) {
                    int currentID = peakIdColumn.get(from);
                    int currentDim = sdimColumn.get(i) - 1;
                    if ((i == (peakIdColumn.size() - 1))
                            || (peakIdColumn.get(i + 1) != currentID)
                            || (sdimColumn.get(i + 1) - 1 != currentDim)) {
                        Peak peak = peakList.getPeakByID(currentID);
                        to = i + 1;
                        Multiplet multiplet = peak.getPeakDim(currentDim).getMultiplet();
                        CouplingPattern couplingPattern = new CouplingPattern(multiplet,
                                couplingColumn.subList(from, to),
                                couplingTypeColumn.subList(from, to),
                                strongCouplingColumn.subList(from, to),
                                intensityColumn.get(from)
                        );
                        multiplet.setCoupling(couplingPattern);
                        from = to;
                    }
                }
            }
            processTransitions(saveframe, peakList);
        }
    }

    void processTransitions(Saveframe saveframe, PeakList peakList) throws ParseException {
        Loop loop = saveframe.getLoop("_Spectral_transition");
        if (loop != null) {
            List<Integer> idColumn = loop.getColumnAsIntegerList("ID", null);
            List<Integer> peakIdColumn = loop.getColumnAsIntegerList("Peak_ID", null);

            for (int i = 0, n = idColumn.size(); i < n; i++) {
                int idNum = idColumn.get(i);
                int peakIdNum = peakIdColumn.get(i);
                Peak peak = peakList.getPeakByID(peakIdNum);
                peak.setIdNum(idNum);
            }
            loop = saveframe.getLoop("_Spectral_transition_general_char");

            if (loop != null) {
                Map<Integer, Double> intMap = new HashMap<>();
                Map<Integer, Double> volMap = new HashMap<>();
                idColumn = loop.getColumnAsIntegerList("Spectral_transition_ID", null);
                List<Double> intensityColumn = loop.getColumnAsDoubleList("Intensity_val", null);
                List<String> methodColumn = loop.getColumnAsList("Measurement_method");

                for (int i = 0, n = idColumn.size(); i < n; i++) {
                    int idNum = idColumn.get(i);
                    Double value = intensityColumn.get(i);
                    if (value != null) {
                        String mode = methodColumn.get(i);
                        if (mode.equals("height")) {
                            intMap.put(idNum, value);
                        } else {
                            volMap.put(idNum, value);
                        }
                    }

                }

                loop = saveframe.getLoop("_Spectral_transition_char");
                if (loop != null) {
                    idColumn = loop.getColumnAsIntegerList("Spectral_transition_ID", null);
                    peakIdColumn = loop.getColumnAsIntegerList("Peak_ID", null);
                    List<Double> shiftColumn = loop.getColumnAsDoubleList("Chem_shift_val", null);
                    List<Double> lwColumn = loop.getColumnAsDoubleList("Line_width_val", null);
                    List<Integer> sdimColumn = loop.getColumnAsIntegerList("Spectral_dim_ID", null);

                    List<AbsMultipletComponent> comps = new ArrayList<>();
                    for (int i = 0; i < peakIdColumn.size(); i++) {
                        int currentID = peakIdColumn.get(i);
                        int transID = idColumn.get(i);
                        int currentDim = sdimColumn.get(i) - 1;
                        double sf = peakList.getSpectralDim(currentDim).getSf();
                        Peak peak = peakList.getPeakByID(currentID);
                        Multiplet multiplet = peak.getPeakDim(currentDim).getMultiplet();
                        AbsMultipletComponent comp = new AbsMultipletComponent(
                                multiplet, shiftColumn.get(i), intMap.get(transID), volMap.get(transID), lwColumn.get(i) / sf);
                        comps.add(comp);
                        if ((i == (peakIdColumn.size() - 1))
                                || (peakIdColumn.get(i + 1) != currentID)
                                || (sdimColumn.get(i + 1) - 1 != currentDim)) {
                            ComplexCoupling complexCoupling = new ComplexCoupling(multiplet, comps);
                            multiplet.setCoupling(complexCoupling);
                            comps.clear();
                        }
                    }
                }
            }
        }

    }

    public void processChemicalShifts(Saveframe saveframe, int ppmSet) throws ParseException {
        Loop loop = saveframe.getLoop("_Atom_chem_shift");
        if (loop != null) {
            boolean refMode = false;
            if (ppmSet < 0) {
                refMode = true;
                ppmSet = -1 - ppmSet;
            }
            var compoundMap = MoleculeBase.compoundMap();
            // map may be empty if we're importing shifts into new project
            // without reading star file with entity
            if (compoundMap.isEmpty()) {
                if (molecule == null) {
                    molecule = MoleculeFactory.getActive();
                }
                molecule.buildCompoundMap();
            }
            List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID");
            List<String> compIDColumn = loop.getColumnAsList("Comp_ID");
            List<String> atomColumn = loop.getColumnAsList("Atom_ID");
            List<String> typeColumn = loop.getColumnAsList("Atom_type");
            List<String> valColumn = loop.getColumnAsList("Val");
            List<String> valErrColumn = loop.getColumnAsList("Val_err");
            List<String> resColumn = loop.getColumnAsList("Resonance_ID");
            List<Integer> ambigColumn = loop.getColumnAsIntegerList("Ambiguity_code", -1);
            ResonanceFactory resFactory = ProjectBase.activeResonanceFactory();
            for (int i = 0; i < entityAssemblyIDColumn.size(); i++) {
                String iEntity = entityIDColumn.get(i);
                String entityAssemblyID = entityAssemblyIDColumn.get(i);
                if (iEntity.equals("?")) {
                    continue;
                }
                String iRes = compIdxIDColumn.get(i);
                String atomName = atomColumn.get(i);
                String atomType = typeColumn.get(i);
                String value = valColumn.get(i);
                String valueErr = valErrColumn.get(i);
                String resIDStr = ".";
                if (resColumn != null) {
                    resIDStr = resColumn.get(i);
                }
                if (entityAssemblyID.equals(".")) {
                    entityAssemblyID = "1";
                }
                String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    log.warn("invalid compound in assignments saveframe \"{}\"", mapID);
                    continue;
                }
                String compID = compIDColumn.get(i);
            if (!compound.getName().equals(compID)) {
                log.warn("sequence mismatch expected: " + compound.getName() + " got: " + compID);
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
                if (spSet == null) {
                    throw new ParseException("invalid spatial set in assignments saveframe \"" + mapID + "." + atomName + "\"");
                }
                try {
                    if (refMode) {
                        spSet.setRefPPM(ppmSet, Double.parseDouble(value));
                        if (!valueErr.equals(".")) {
                            spSet.setRefError(ppmSet, Double.parseDouble(valueErr));
                        }
                    } else {
                        spSet.setPPM(ppmSet, Double.parseDouble(value), false);
                        spSet.getPPM(ppmSet).setAmbigCode(ambigColumn.get(i));
                        if (!valueErr.equals(".")) {
                            spSet.setPPM(ppmSet, Double.parseDouble(valueErr), true);
                        }
                    }
                } catch (NumberFormatException nFE) {
                    throw new ParseException("Invalid chemical shift value (not double) \"" + value + "\" error \"" + valueErr + "\"");
                }
                if (!refMode && hasResonances && !resIDStr.equals(".")) {
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
        }
    }

    public void processConformer(Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_Atom_site");
        if (loop == null) {
            log.warn("No \"_Atom_site\" loop");
            return;
        }
        var compoundMap = MoleculeBase.compoundMap();
        List<String> entityAssemblyIDColumn = loop.getColumnAsList("Label_entity_assembly_ID");
        List<String> entityIDColumn = loop.getColumnAsList("Label_entity_ID");
        List<String> compIdxIDColumn = loop.getColumnAsList("Label_comp_index_ID");
        List<String> atomColumn = loop.getColumnAsList("Label_atom_ID");
        List<String> xColumn = loop.getColumnAsList("Cartn_x");
        List<String> yColumn = loop.getColumnAsList("Cartn_y");
        List<String> zColumn = loop.getColumnAsList("Cartn_z");
        List<String> modelColumn = loop.getColumnAsList("Model_ID");
        TreeSet<Integer> selSet = new TreeSet<>();
        MoleculeBase molecule = null;
        int lastStructure = -1;
        for (int i = 0; i < entityAssemblyIDColumn.size(); i++) {
            String iEntity = entityIDColumn.get(i);
            String entityAssemblyID = entityAssemblyIDColumn.get(i);
            if (iEntity.equals("?")) {
                continue;
            }
            String iRes = compIdxIDColumn.get(i);
            String atomName = atomColumn.get(i);
            String xStr = xColumn.get(i);
            String yStr = yColumn.get(i);
            String zStr = zColumn.get(i);
            String modelStr = modelColumn.get(i);
            if (entityAssemblyID.equals(".")) {
                entityAssemblyID = "1";
            }
            String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
            Compound compound = compoundMap.get(mapID);
            if (compound == null) {
                log.warn("invalid compound in conformer saveframe \"{}\"", mapID);
                continue;
            }
            if (molecule == null) {
                molecule = compound.molecule;
            }
            Atom atom = compound.getAtomLoose(atomName);
            if (atom == null) {
                log.warn("No atom \"{}.{}\"", mapID, atomName);
                continue;
            }
            int structureNumber = Integer.parseInt(modelStr);
            Integer intStructure = structureNumber;
            if (intStructure != lastStructure) {
                molecule.nullCoords(structureNumber);
                selSet.add(intStructure);
                molecule.structures.add(intStructure);
            }
            lastStructure = intStructure;
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);
            double z = Double.parseDouble(zStr);
            atom.setPointValidity(structureNumber, true);
            Point3 pt = new Point3(x, y, z);
            atom.setPoint(structureNumber, pt);
        }
        if (molecule != null) {
            for (Integer iStructure : selSet) {
                molecule.genCoords(iStructure, true);
            }
            molecule.setActiveStructures(selSet);
        }
    }

    public void processNOE(Saveframe saveframe) throws ParseException {
        String frameName = saveframe.getCategory("_Heteronucl_NOE_list").get("Sf_framecode");
        String field = saveframe.getCategory("_Heteronucl_NOE_list").get("Spectrometer_frequency_1H");
        Map<String, String> extras = new HashMap<>();
        String refVal = saveframe.getCategory("_Heteronucl_NOE_list").get("ref_val");
        String refDescription = saveframe.getCategory("_Heteronucl_NOE_list").get("ref_description");
        extras.put("refVal", refVal);
        extras.put("refDescription", refDescription);

        MoleculeBase mol = MoleculeFactory.getActive();
        var compoundMap = MoleculeBase.compoundMap();
        Loop loop = saveframe.getLoop("_Heteronucl_NOE");
        if (loop == null) {
            log.warn("No \"NOE\" loop");
            return;
        }
        List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID_1");
        List<String> entityIDColumn = loop.getColumnAsList("Entity_ID_1");
        List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID_1");
        List<String> atomColumn = loop.getColumnAsList("Atom_ID_1");
        List<String> entityID2Column = loop.getColumnAsList("Entity_ID_2");
        List<String> atom2Column = loop.getColumnAsList("Atom_ID_2");
        List<String> valColumn = loop.getColumnAsList("Val");
        List<String> errColumn = loop.getColumnAsList("Val_err");
        double fieldValue = Double.valueOf(field);
        double temperature = 25.0;
        RelaxationSet relaxationSet = new RelaxationSet(frameName, RelaxTypes.NOE, fieldValue, temperature, extras);

        for (int i = 0; i < entityAssemblyIDColumn.size(); i++) {
            String iEntity = entityIDColumn.get(i);
            String entityAssemblyID = entityAssemblyIDColumn.get(i);
            if (iEntity.equals("?")) {
                continue;
            }
            String iRes = compIdxIDColumn.get(i);
            String atomName = atomColumn.get(i);
            String iEntity2 = entityID2Column.get(i);
            if (iEntity2.equals("?")) {
                continue;
            }
            String atomName2 = atom2Column.get(i);
            double value = 0.0;
            double error = 0.0;
            if (!valColumn.get(i).equals(".")) {
                value = Double.parseDouble(valColumn.get(i));
            }
            if (!errColumn.get(i).equals(".")) {
                error = Double.parseDouble(errColumn.get(i));
            }

            if (entityAssemblyID.equals(".")) {
                entityAssemblyID = "1";
            }
            String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
            Compound compound = compoundMap.get(mapID);
            if (compound == null) {
                log.warn("invalid compound in NOE saveframe \"{}\"", mapID);
                continue;
            }
            if (mol == null) {
                mol = compound.molecule;
            }
            Atom atom = compound.getAtomLoose(atomName);
            if (atom == null) {
                atom = Atom.genAtomWithElement(atomName, atomName.substring(0, 1));
                compound.addAtom(atom);
            }

            Atom atom2 = compound.getAtomLoose(atomName2);
            if (atom2 == null) {
                atom2 = Atom.genAtomWithElement(atomName2, atomName2.substring(0, 1));
                compound.addAtom(atom2);
            }

            ResonanceSource resSource = new ResonanceSource(atom, atom2);

            RelaxationData relaxData = new RelaxationData(relaxationSet, resSource, value, error);
            atom.addRelaxationData(relaxationSet, relaxData);
        }
    }

    public void processRelaxation(Saveframe saveframe, RelaxTypes expType) throws ParseException {
        String catName = saveframe.getCategoryName();
        String frameName = saveframe.getName().substring(5);
        for (String cat : saveframe.getCategories()) {
            log.info(cat);
        }
        String expName = expType.getName().toUpperCase();
        if (expName.equals("R1")) {
            expName = "T1";
        } else if (expName.equals("R2")) {
            expName = "T2";
        }

        String mainCat = "_Heteronucl_" + expName + "_list";
        log.info("{} {}", catName, frameName);
        double field = saveframe.getDoubleValue(mainCat, "Spectrometer_frequency_1H");
        log.info("{}", field);
        String coherenceType = saveframe.getValue(mainCat, expName + "_coherence_type");
        String units = saveframe.getValue(mainCat, expName + "_val_units");
        Map<String, String> extras = new HashMap<>();
        extras.put("coherenceType", coherenceType);
        extras.put("units", units);

        MoleculeBase mol = MoleculeFactory.getActive();
        var compoundMap = MoleculeBase.compoundMap();
        Loop loop = saveframe.getLoop("_" + expName);
        if (loop == null) {
            log.warn("No \"{}\" loop", expName);
            return;
        }
        List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID");
        List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
        List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID");
        List<String> atomColumn = loop.getColumnAsList("Atom_ID");
        List<String> valColumn = loop.getColumnAsListIfExists("Val");
        List<String> errColumn = loop.getColumnAsListIfExists("Val_err");
        List<String> RexValColumn = loop.getColumnAsListIfExists("Rex_val");
        List<String> RexErrColumn = loop.getColumnAsListIfExists("Rex_err");
        if (expType.equals(RelaxTypes.R2) || expType.equals(RelaxTypes.R1RHO)) {
            valColumn = loop.getColumnAsList(expName + "_val");
            errColumn = loop.getColumnAsList(expName + "_val_err");
        }

        double temperature = 25.0;
        RelaxationSet relaxationSet = new RelaxationSet(frameName, expType, field, temperature, extras);

        for (int i = 0; i < entityAssemblyIDColumn.size(); i++) {
            String iEntity = entityIDColumn.get(i);
            String entityAssemblyID = entityAssemblyIDColumn.get(i);
            if (iEntity.equals("?")) {
                continue;
            }
            String iRes = compIdxIDColumn.get(i);
            String atomName = atomColumn.get(i);
            Double value = null;
            Double error = null;
            double RexValue = 0.0;
            double RexError = 0.0;
            if (!valColumn.get(i).equals(".")) {
                value = Double.parseDouble(valColumn.get(i));
            }
            if (!errColumn.get(i).equals(".")) {
                error = Double.parseDouble(errColumn.get(i));
            }
            if ((expType.equals(RelaxTypes.R2) || expType.equals(RelaxTypes.R1RHO))
                    && (RexValColumn != null)) {
                if (!RexValColumn.get(i).equals(".")) {
                    RexValue = Double.parseDouble(RexValColumn.get(i));
                }
                if (!RexErrColumn.get(i).equals(".")) {
                    RexError = Double.parseDouble(RexErrColumn.get(i));
                }
            }

            if (entityAssemblyID.equals(".")) {
                entityAssemblyID = "1";
            }
            String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
            Compound compound = compoundMap.get(mapID);
            if (compound == null) {
                log.warn("invalid compound in {} saveframe \"{}\"", expName, mapID);
                continue;
            }
            if (mol == null) {
                mol = compound.molecule;
            }
            Atom atom = compound.getAtomLoose(atomName);
            if (atom == null) {
                atom = Atom.genAtomWithElement(atomName, atomName.substring(0, 1));
                compound.addAtom(atom);
            }
            ResonanceSource resSource = new ResonanceSource(atom);

            if (expType.equals(RelaxTypes.R1)) {
                RelaxationData relaxData = new RelaxationData(relaxationSet, resSource, value, error);
                atom.addRelaxationData(relaxationSet, relaxData);
            } else {
                RelaxationRex relaxData = new RelaxationRex(relaxationSet, resSource,  value, error, RexValue, RexError);
                atom.addRelaxationData(relaxationSet, relaxData);
            }
        }
    }

    Optional<Atom> getAtom(MoleculeBase mol,
                           Saveframe saveframe,
                           List<String> entityAssemblyIDColumn,
                           List<String> entityIDColumn,
                           List<String> compIdxIDColumn,
                           List<String> atomColumn, int i
    ) {
        Optional<Atom> result = Optional.empty();
        String iEntity = entityIDColumn.get(i);
        String entityAssemblyID = entityAssemblyIDColumn.get(i);
        if (entityAssemblyID.equals(".")) {
            entityAssemblyID = "1";
        }
        if (iEntity.equals("?")) {
            return result;
        }
        String iRes = compIdxIDColumn.get(i);
        String atomName = atomColumn.get(i);

        var compoundMap = MoleculeBase.compoundMap();
        String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
        Compound compound = compoundMap.get(mapID);
        if (compound == null) {
            log.warn("invalid compound in {} saveframe \"{}\"", saveframe.getName(), mapID);
        } else {
            Atom atom = compound.getAtomLoose(atomName);
            if (atom == null) {
                log.warn("No atom \"{}.{}\"", mapID, atomName);
            } else {
                result = Optional.of(atom);
            }
        }
        return result;
    }

    public void processOrder(Saveframe saveframe) throws ParseException {
        String frameName = saveframe.getName().substring(5);
        String mainCat = "_Order_parameter_list";
        String[] unitVars = {"Tau_e", "Tau_s", "Tau_f", "Rex"};
        Map<String, String> extras = new HashMap<>();
        for (var unitVar : unitVars) {
            String units = saveframe.getValue(mainCat, unitVar + "_val_units");
            extras.put(unitVar + "_units", units);
        }

        MoleculeBase mol = MoleculeFactory.getActive();
        Loop loop = saveframe.getLoop("_Order_param");
        if (loop == null) {
            log.warn("No _Order_param loop");
            return;
        }
        List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID");
        List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
        List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID");
        List<String> atomColumn = loop.getColumnAsList("Atom_ID");

        Map<String, List<Double>> valueColumns = new HashMap<>();
        Map<String, List<Double>> errColumns = new HashMap<>();
        for (int i = 0; i < OrderPar.orderParLoopStrings.length; i += 2) {
            String fullName = OrderPar.orderParLoopStrings[i];
            if (fullName.endsWith("_val")) {
                var column = loop.getColumnAsDoubleList(fullName, null);
                String parName = fullName.substring(0, fullName.length() - 4);
                valueColumns.put(parName, column);
            } else if (fullName.endsWith("_val_fit_err")) {
                var column = loop.getColumnAsDoubleList(fullName, null);
                String parName = fullName.substring(0, fullName.length() - 12);
                errColumns.put(parName, column);
            }
        }
        OrderParSet orderParSet = new OrderParSet(frameName);

        var modelfreeErrorColumn = loop.getColumnAsDoubleList("Model_free_sum_squared_errs", null);
        var modelfreeNValuesColumn = loop.getColumnAsIntegerList("Model_free_n_values", null);
        var modelfreeNParsColumn = loop.getColumnAsIntegerList("Model_free_n_pars", null);
        var modelNameColumn = loop.getColumnAsList("Model_fit");
        for (int i = 0; i < entityAssemblyIDColumn.size(); i++) {
            Optional<Atom> atomOpt = getAtom(mol, saveframe,
                    entityAssemblyIDColumn, entityIDColumn, compIdxIDColumn,
                    atomColumn, i);
            if (atomOpt.isPresent()) {
                Double modelSSErr = modelfreeErrorColumn.get(i);
                Integer modelNValues = modelfreeNValuesColumn.get(i);
                Integer modelNPars = modelfreeNParsColumn.get(i);
                String modelName = modelNameColumn.get(i);
                ResonanceSource resSource = new ResonanceSource(atomOpt.get());
                OrderPar orderPar = new OrderPar(orderParSet, resSource, modelSSErr, modelNValues, modelNPars, modelName);
                for (var parName : valueColumns.keySet()) {
                    var valueColumn = valueColumns.get(parName);
                    var errColumn = errColumns.get(parName);
                    Double value = valueColumn.get(i);
                    Double err = errColumn != null ? errColumn.get(i) : null;
                    if (value != null) {
                        orderPar = orderPar.set(parName, value, err);
                    }
                }
                atomOpt.get().addOrderPar(orderParSet, orderPar);
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
        AngleConstraintSet angleSet = molecule.getMolecularConstraints().
                newAngleSet(saveframe.getName().substring(5));
        for (int i = 0; i < entityAssemblyIDColumns[0].size(); i++) {
            Atom[] atoms = new Atom[4];
            for (int iAtom = 0; iAtom < 4; iAtom++) {
                atoms[iAtom] = getSpatialSet(entityAssemblyIDColumns[iAtom],
                        entityIDColumns[iAtom], compIdxIDColumns[iAtom],
                        atomColumns[iAtom], resonanceColumns[iAtom], i).
                        getSpatialSet().atom;
            }
            String upperValue = upperColumn.get(i);
            String lowerValue = lowerColumn.get(i);
            String name = angleNameColumn.get(i);
            double upper = Double.parseDouble(upperValue);
            double lower = 1.8;
            if (!lowerValue.equals(".")) {
                lower = Double.parseDouble(lowerValue);
            }
            try {
                AngleConstraint aCon = new AngleConstraint(atoms, lower, upper, name);
                angleSet.add(aCon);
            } catch (IllegalArgumentException | InvalidMoleculeException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    public void processRDCConstraints(Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_RDC_constraint");
        if (loop == null) {
            throw new ParseException("No \"_RDC\" loop");
        }
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
        List<Double> valColumn = loop.getColumnAsDoubleList("RDC_val", null);
        List<Double> errColumn = loop.getColumnAsDoubleList("RDC_val_err", null);
        RDCConstraintSet rdcSet = molecule.getMolecularConstraints().newRDCSet(saveframe.getName().substring(5));
        for (int i = 0; i < entityAssemblyIDColumns[0].size(); i++) {
            SpatialSet[] spSets = new SpatialSet[2];
            boolean ok = true;
            for (int iAtom = 0; iAtom < 2; iAtom++) {
                SpatialSetGroup spG = getSpatialSet(entityAssemblyIDColumns[iAtom], entityIDColumns[iAtom], compIdxIDColumns[iAtom], atomColumns[iAtom], resonanceColumns[iAtom], i);
                if (spG != null) {
                    spSets[iAtom] = spG.getSpatialSet();
                    if (spSets[iAtom] == null) {
                        log.warn("null spset id  {} iatom {} {}", i, iAtom, spG.getFullName());
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) {
                double err;
                if (errColumn.get(i) == null) {
                    err = valColumn.get(i) * 0.05;
                } else {
                    err = errColumn.get(i);
                }
                RDCConstraint aCon = new RDCConstraint(rdcSet, spSets[0].getAtom(), spSets[1].getAtom(), valColumn.get(i), err);
                rdcSet.add(aCon);

            }
        }
    }

    public static PeakList getPeakList(String saveframeName, String peakListIDStr, PeakList peakList) throws ParseException {
        if (peakListIDStr.equals(".")) {
            if (peakList == null) {
                peakList = new PeakList(saveframeName, 2);
            }
        } else {
            try {
                int peakListID = Integer.parseInt(peakListIDStr);
                Optional<PeakList> peakListOpt = PeakList.get(peakListID);
                if (peakListOpt.isPresent()) {
                    peakList = peakListOpt.get();
                }
            } catch (NumberFormatException nFE) {
                throw new ParseException("Invalid peak list id (not int) \"" + peakListIDStr + "\"");
            }
        }
        return peakList;
    }

    public void processGenDistConstraints(Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_Gen_dist_constraint");
        if (loop == null) {
            throw new ParseException("No \"_Gen_dist_constraint\" loop");
        }
        var compoundMap = MoleculeBase.compoundMap();
        List<String>[] entityAssemblyIDColumns = new ArrayList[2];
        List<String>[] entityIDColumns = new ArrayList[2];
        List<String>[] compIdxIDColumns = new ArrayList[2];
        List<String>[] atomColumns = new ArrayList[2];
        List<String>[] resonanceColumns = new ArrayList[2];
        entityAssemblyIDColumns[0] = loop.getColumnAsList("Entity_assembly_ID_1");
        entityIDColumns[0] = loop.getColumnAsList("Entity_ID_1");
        compIdxIDColumns[0] = loop.getColumnAsList("Comp_index_ID_1");
        atomColumns[0] = loop.getColumnAsList("Atom_ID_1");
        resonanceColumns[0] = loop.getColumnAsList("Resonance_ID_1", null);
        entityAssemblyIDColumns[1] = loop.getColumnAsList("Entity_assembly_ID_2");
        entityIDColumns[1] = loop.getColumnAsList("Entity_ID_2");
        compIdxIDColumns[1] = loop.getColumnAsList("Comp_index_ID_2");
        atomColumns[1] = loop.getColumnAsList("Atom_ID_2");
        resonanceColumns[1] = loop.getColumnAsList("Resonance_ID_2", null);
        List<String> constraintIDColumn = loop.getColumnAsList("ID");
        List<String> lowerColumn = loop.getColumnAsList("Distance_lower_bound_val");
        List<String> upperColumn = loop.getColumnAsList("Distance_upper_bound_val");
        List<String> peakListIDColumn = loop.getColumnAsList("Spectral_peak_list_ID", ".");
        List<String> peakIDColumn = loop.getColumnAsList("Spectral_peak_ID", ".");
        Atom[] atoms = new Atom[2];
        SpatialSetGroup[] spSets = new SpatialSetGroup[2];
        String[] resIDStr = new String[2];
        PeakList peakList = null;
        String lastPeakListIDStr = "";
        NoeSet noeSet = molecule.getMolecularConstraints().newNOESet(saveframe.getName().substring(5));

        for (int i = 0; i < entityAssemblyIDColumns[0].size(); i++) {
            for (int iAtom = 0; iAtom < 2; iAtom++) {
                spSets[iAtom] = null;
                String iEntity = entityIDColumns[iAtom].get(i);
                String entityAssemblyID = entityAssemblyIDColumns[iAtom].get(i);
                if (iEntity.equals("?")) {
                    continue;
                }
                String iRes = compIdxIDColumns[iAtom].get(i);
                String atomName = atomColumns[iAtom].get(i);
                resIDStr[iAtom] = ".";
                if ((resonanceColumns[iAtom] != null) && (resonanceColumns[iAtom].get(i) != null)) {
                    resIDStr[iAtom] = resonanceColumns[iAtom].get(i);
                }
                if (entityAssemblyID.equals(".")) {
                    entityAssemblyID = "1";
                }
                String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
                Compound compound1 = compoundMap.get(mapID);
                if (compound1 == null) {
                    log.warn("invalid compound in distance constraints saveframe \"{}\"", mapID);
                } else if ((atomName.charAt(0) == 'Q') || (atomName.charAt(0) == 'M')) {
                    Residue residue = (Residue) compound1;
                    Atom[] pseudoAtoms = ((Residue) compound1).getPseudo(atomName);
                    if (pseudoAtoms == null) {
                        log.warn("{} {} {}", residue.getIDNum(), residue.getNumber(), residue.getName());
                        log.warn("invalid pseudo in distance constraints saveframe \"{}\" {}", mapID, atomName);
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
            String upperValue = upperColumn.get(i);
            String lowerValue = lowerColumn.get(i);
            String peakListIDStr = peakListIDColumn.get(i);
            String peakID = peakIDColumn.get(i);
            String constraintID = constraintIDColumn.get(i);
            if (!peakListIDStr.equals(lastPeakListIDStr)) {
                peakList = getPeakList(saveframe.getName(), peakListIDStr, peakList);
            }
            lastPeakListIDStr = peakListIDStr;
            Peak peak;
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
                    log.warn("Upper value is a \".\" at line {}", i);
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
        noeSet.updateNPossible(peakList);
        noeSet.setCalibratable(false);
    }

    public void process() throws ParseException, IllegalArgumentException {
        String[] argv = {};
        process(argv);
    }

    public void process(String[] argv) throws ParseException, IllegalArgumentException {
        if ((argv.length != 0) && (argv.length != 3)) {
            throw new IllegalArgumentException("?shifts fromSet toSet?");
        }
        log.debug("nSave " + star3.getSaveFrameNames());
        ResonanceFactory resFactory = ProjectBase.activeResonanceFactory();
        if (argv.length == 0) {
            hasResonances = false;
            var compoundMap = MoleculeBase.compoundMap();
            compoundMap.clear();
            buildExperiments();
            log.debug("process molecule");
            buildMolecule();
            if (molecule != null) {
                ProjectBase.getActive().putMolecule(molecule);
                MoleculeFactory.setActive(molecule);
            }
            log.debug("process peak lists");
            buildPeakLists();
            log.debug("process resonance lists");
            buildResonanceLists();
            log.debug("process chem shifts");
            buildChemShifts(-1, 0);
            log.debug("process conformers");
            buildConformers();
            log.debug("process dist constraints");
            buildGenDistConstraints();
            log.debug("process angle constraints");
            buildDihedralConstraints();
            log.debug("process rdc constraints");
            buildRDCConstraints();
            log.debug("process NOE");
            buildNOE();
            for (var relaxType : RelaxTypes.values()) {
                if ((relaxType != RelaxTypes.NOE) && (relaxType != RelaxTypes.S2)) {
                    log.debug("process {}", relaxType);
                    buildRelaxation(relaxType);
                }
            }
            log.debug("process Order");
            buildOrder();
            log.debug("process runabout");
            buildRunAbout();
            log.debug("process paths");
            buildPeakPaths();

            ProjectBase.processExtraSaveFrames(star3);

            log.debug("clean resonances");
            resFactory.clean();

            log.debug("process done");
        } else if ("shifts".startsWith(argv[0])) {
            int fromSet = Integer.parseInt(argv[1]);
            int toSet = Integer.parseInt(argv[2]);
            buildChemShifts(fromSet, toSet);
        }
    }
}
