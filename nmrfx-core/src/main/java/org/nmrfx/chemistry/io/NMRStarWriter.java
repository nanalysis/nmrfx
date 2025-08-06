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

import javafx.beans.property.SimpleBooleanProperty;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.ConstraintSet;
import org.nmrfx.chemistry.relax.*;
import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.PeakPaths;
import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.peaks.io.PeakPathWriter;
import org.nmrfx.peaks.io.PeakWriter;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.STAR3;
import org.nmrfx.star.STAR3Base;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author brucejohnson
 */
@PluginAPI("ring")
public class NMRStarWriter {
    private static final String[] entityCompIndexLoopStrings = {"_Entity_comp_index.ID", "_Entity_comp_index.Auth_seq_ID", "_Entity_comp_index.Comp_ID", "_Entity_comp_index.Comp_label", "_Entity_comp_index.Entity_ID"};
    private static final String[] entityAssemblyLoopStrings = {"_Entity_assembly.ID", "_Entity_assembly.Entity_assembly_name", "_Entity_assembly.Entity_ID", "_Entity_assembly.Entity_label", "_Entity_assembly.Asym_ID", "_Entity_assembly.Experimental_data_reported", "_Entity_assembly.Physical_state", "_Entity_assembly.Conformational_isomer", "_Entity_assembly.Chemical_exchange_state", "_Entity_assembly.Magnetic_equivalence_group_code", "_Entity_assembly.Role", "_Entity_assembly.Details", "_Entity_assembly.Assembly_ID"};
    private static final String[] entityCommonNameLoopStrings = {"_Entity_common_name.Name", "_Entity_common_name.Type", "_Entity_common_name.Entity_ID"};
    private static final String[] entityBondLoopStrings = {"_Entity_bond.ID", "_Entity_bond.Type", "_Entity_bond.Value_order", "_Entity_bond.Comp_index_ID_1", "_Entity_bond.Comp_ID_1", "_Entity_bond.Atom_ID_1", "_Entity_bond.Comp_index_ID_2", "_Entity_bond.Comp_ID_2", "_Entity_bond.Atom_ID_2", "_Entity_bond.Entity_ID"};
    private static final String[] chemCompEntityIndexLoopStrings = {"_Entity_comp_index.ID", "_Entity_comp_index.Auth_seq_ID", "_Entity_comp_index.Comp_ID", "_Entity_comp_index.Comp_label", "_Entity_comp_index.Entry_ID", "_Entity_comp_index.Entity_ID"};
    private static final String[] entityChemCompDeletedLoopStrings = {"_Entity_chem_comp_deleted_atom.ID", "_Entity_chem_comp_deleted_atom.Comp_index_ID", "_Entity_chem_comp_deleted_atom.Comp_ID", "_Entity_chem_comp_deleted_atom.Atom_ID", "_Entity_chem_comp_deleted_atom.Entity_ID"};
    private static final String[] chemCompBondLoopStrings = {"_Chem_comp_bond.Bond_ID", "_Chem_comp_bond.Type", "_Chem_comp_bond.Value_order", "_Chem_comp_bond.Atom_ID_1", "_Chem_comp_bond.Atom_ID_2", "_Chem_comp_bond.PDB_atom_ID_1", "_Chem_comp_bond.PDB_atom_ID_2", "_Chem_comp_bond.Details", "_Chem_comp_bond.Entry_ID", "_Chem_comp_bond.Comp_ID"};
    private static final String[] entityPolySeqLoopStrings = {"_Entity_poly_seq.Hetero", "_Entity_poly_seq.Mon_ID", "_Entity_poly_seq.Num", "_Entity_poly_seq.Comp_index_ID", "_Entity_poly_seq.Entity_ID"};
    private static final String[] chemCompAtomLoopStrings = {"_Chem_comp_atom.Atom_ID", "_Chem_comp_atom.PDB_atom_ID", "_Chem_comp_atom.Alt_atom_ID", "_Chem_comp_atom.Auth_atom_ID", "_Chem_comp_atom.Type_symbol", "_Chem_comp_atom.Isotope_number", "_Chem_comp_atom.Chirality", "_Chem_comp_atom.Charge", "_Chem_comp_atom.Partial_charge", "_Chem_comp_atom.Oxidation_number", "_Chem_comp_atom.PDBx_aromatic_flag", "_Chem_comp_atom.PDBx_leaving_atom_flag", "_Chem_comp_atom.Substruct_code", "_Chem_comp_atom.Ionizable", "_Chem_comp_atom.Details", "_Chem_comp_atom.Entry_ID", "_Chem_comp_atom.Comp_ID", "_Chem_comp_atom.Unpaired_electron_number"};
    private static final String[] chemShiftAssignmentStrings = {"_Atom_chem_shift.ID", "_Atom_chem_shift.Assembly_atom_ID", "_Atom_chem_shift.Entity_assembly_ID", "_Atom_chem_shift.Entity_ID", "_Atom_chem_shift.Comp_index_ID", "_Atom_chem_shift.Seq_ID", "_Atom_chem_shift.Comp_ID", "_Atom_chem_shift.Atom_ID", "_Atom_chem_shift.Atom_type", "_Atom_chem_shift.Atom_isotope_number", "_Atom_chem_shift.Val", "_Atom_chem_shift.Val_err", "_Atom_chem_shift.Assign_fig_of_merit", "_Atom_chem_shift.Ambiguity_code", "_Atom_chem_shift.Occupancy", "_Atom_chem_shift.Resonance_ID", "_Atom_chem_shift.Auth_seq_ID", "_Atom_chem_shift.Auth_comp_ID", "_Atom_chem_shift.Auth_atom_ID", "_Atom_chem_shift.Details", "_Atom_chem_shift.Assigned_chem_shift_list_ID"};
    private static final String[] atomCoordinateLoopStrings = {"_Atom_site.Assembly_ID", "_Atom_site.Model_ID", "_Atom_site.Model_site_ID", "_Atom_site.ID", "_Atom_site.Assembly_atom_ID", "_Atom_site.Label_entity_assembly_ID", "_Atom_site.Label_entity_ID", "_Atom_site.Label_comp_index_ID", "_Atom_site.Label_comp_ID", "_Atom_site.Label_atom_ID", "_Atom_site.Type_symbol", "_Atom_site.Cartn_x", "_Atom_site.Cartn_y", "_Atom_site.Cartn_z", "_Atom_site.Cartn_x_esd", "_Atom_site.Cartn_y_esd", "_Atom_site.Cartn_z_esd", "_Atom_site.Occupancy", "_Atom_site.Occupancy_esd", "_Atom_site.Uncertainty", "_Atom_site.Ordered_flag", "_Atom_site.Footnote_ID", "_Atom_site.Details", "_Atom_site.Entry_ID", "_Atom_site.Conformer_family_coord_set_ID"};
    public enum StarTypes {
        MOLECULES("Molecules"),
        PEAKLISTS("Peak Lists"),
        RESONANCES("Resonances"),
        ASSIGNMENTS("Assignments"),
        COORDINATES("Coordinates"),
        CONSTRAINTS("Constraints"),
        PEAKPATHS("Peak Paths"),
        RELAXATION("Relaxation"),
        ORDERPARAMETERS("Order Parameters")
        ;
        public final String name;
        StarTypes(String name) {
            this.name = name;
        }
    }
    public static void initSaveFrameOutput(StringBuilder sBuilder, String category, String categoryName, String id) {
        sBuilder.append(STAR3Base.SAVE).append(categoryName).append("_").append(id).append("\n");
        NMRStarWriter.appendSTAR(sBuilder, category, "Sf_category", categoryName);
        NMRStarWriter.appendSTAR(sBuilder, category, "Sf_framecode", categoryName + "_" + id);
        NMRStarWriter.appendSTAR(sBuilder, category, "Entry_ID", ".");
        NMRStarWriter.appendSTAR(sBuilder, category, "ID", String.valueOf(id));
    }

    public static void appendSTAR(StringBuilder sBuilder, String category, String tag, String value) {
        String fullSpace = "                                        ";
        int length = 3 + category.length() + 1 + tag.length();
        int spaceLength = Math.max(2, 42 - length);
        sBuilder.append("   ").append(category).append(".").append(tag).
                append(fullSpace, 0, spaceLength + 1).append(value).append("\n");
    }

    public static void openLoop(StringBuilder sBuilder, String category, List<String> tags) {
        sBuilder.append("   loop_\n");
        for (String tag : tags) {
            appendLoopTag(sBuilder, category, tag);
        }
        sBuilder.append("\n");
    }

    public static void appendLoopTag(StringBuilder sBuilder, String category, String tag) {
        sBuilder.append("      ").append(category).append(".").append(tag).append("\n");
    }

    public static void endLoop(StringBuilder sBuilder) {
        sBuilder.append("\n").append("   ").append("stop_\n");
    }

    static String toSTAR3CompoundString(int ID, Atom atom, int entityID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(atom.getName());
        result.append(sep);
        result.append(atom.getName());
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append(Atom.getElementName(atom.getAtomicNumber()));
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append("N");
        result.append(sep);
        result.append(0);
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append("N");
        result.append(sep);
        result.append("N");
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append(entityID);
        result.append(sep);
        result.append(atom.entity.name);
        result.append(sep);
        result.append("?");
        return result.toString();
    }

    public static void writeEntityCommonNamesSTAR3(Writer chan, Entity entity, int entityID) throws IOException {
        if (!entity.getCommonNames().isEmpty()) {
            chan.write("loop_\n");
            for (String loopString : NMRStarWriter.entityCommonNameLoopStrings) {
                chan.write(loopString + "\n");
            }
            chan.write("\n");
            for (Entity.EntityCommonName eCN : entity.getCommonNames()) {
                chan.write(toSTAR3CommonNameString(eCN, entityID));
                chan.write("\n");
            }
            chan.write("stop_\n");
            chan.write("\n");
        }
    }

    static String toSTAR3String(Entity entity, String coordSetName, int assemblyID, int compID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(compID);
        result.append(sep);
        result.append(STAR3Base.quote(entity.getEntityAssemblyName()));
        result.append(sep);
        result.append(entity.entityID);
        result.append(sep);
        result.append("$").append(entity.label);
        result.append(sep);
        result.append(coordSetName);
        result.append(sep);
        result.append("yes");
        result.append(sep);
        result.append(entity.physicalState);
        result.append(sep);
        result.append(entity.conformationalIsomer);
        result.append(sep);
        result.append(entity.chemicalExchangeState);
        result.append(sep);
        result.append(entity.magneticEquivalenceGroupCode);
        result.append(sep);
        result.append(entity.role);
        result.append(sep);
        result.append(STAR3Base.quote(entity.details));
        result.append(sep);
        result.append(assemblyID);
        result.append(sep);
        return result.toString();
    }

    static String toSTAR3AtomIndexString(final AtomSpecifier atom) {
        String sep = " ";
        return atom.getResNum() +
                sep +
                atom.getResName() +
                sep +
                atom.getAtomName();
    }

    static void writeCompoundHeaderSTAR3(Writer chan, Compound compound, int entityID) throws ParseException, IOException {
        String name = compound.name;
        String label = compound.label;
        chan.write(STAR3Base.SAVE + name + "\n");
        chan.write("_Entity.Sf_category                 ");
        chan.write("entity\n");
        chan.write("_Entity.Sf_framecode                           ");
        chan.write(name + "\n");
        chan.write("_Entity.ID                           ");
        chan.write(entityID + "\n");
        chan.write("_Entity.Name                        ");
        chan.write(name + "\n");
        chan.write("_Entity.Type                          ");
        chan.write("non-polymer\n");
        chan.write("\n");
        STAR3Base.writeLoopStrings(chan, chemCompEntityIndexLoopStrings);
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append("1");
        result.append(sep);
        result.append(compound.getNumber());
        result.append(sep);
        result.append(STAR3Base.quote(label));
        result.append(sep);
        result.append(STAR3Base.quote(label));
        result.append(sep);
        result.append(".");
        result.append(sep);
        result.append(entityID);
        chan.write(result.toString());
        chan.write("\nstop_\n\n");
    }

    public static void writeComponentsSTAR3(Writer chan, Polymer polymer, Set<String> cmpdSet, boolean onlyNonStandard) throws IOException, ParseException {
        Iterator<Residue> residueIterator = polymer.iterator();
        int i = 1;
        while (residueIterator.hasNext()) {
            Residue residue = residueIterator.next();
            if (onlyNonStandard && residue.isStandard()) {
                continue;
            }
            if (!residue.libraryMode()) {
                String mode;
                if (i == 1) {
                    mode = "." + i;
                } else if (!residueIterator.hasNext()) {
                    mode = "." + i;
                } else {
                    mode = "";
                }
                mode = "";
                if (!cmpdSet.contains(residue.label + mode)) {
                    writeCompoundToSTAR3(chan, residue, i, mode);
                    cmpdSet.add(residue.label + mode);
                }
                i++;
            }
        }
    }

    /**
     * Writes the header information for the entity.
     *
     * @param chan        The Writer object to use to write the header.
     * @param entity      The entity for the header information.
     * @param entityID    The id of the entity.
     * @param nonStandard If true, write yes and if false write no after the "Nstd_monomer" entry.
     * @throws IOException
     */
    static void writeEntityHeaderSTAR3(Writer chan, Entity entity, int entityID, boolean nonStandard) throws IOException {
        String label = entity.label;
        chan.write(STAR3Base.SAVE + label + "\n");
        chan.write("_Entity.Sf_category                 ");
        chan.write("entity\n");
        chan.write("_Entity.Sf_framecode                           ");
        chan.write(label + "\n");
        chan.write("_Entity.ID                           ");
        chan.write(entityID + "\n");
        chan.write("_Entity.Name                        ");
        chan.write(label + "\n");
        chan.write("_Entity.Type                          ");
        if (entity instanceof Polymer polymer) {
            chan.write("polymer\n");
            chan.write("_Entity.Polymer_type                  ");
            chan.write(polymer.getPolymerType() + "\n");
            chan.write("_Entity.Polymer_strand_ID            ");
            String strandID = polymer.getStrandID();
            if (strandID.isEmpty()) {
                strandID = "?";
            }
            chan.write(strandID + "\n");
            chan.write("_Entity.Polymer_seq_one_letter_code_can  ");
            chan.write("?\n");
            chan.write("_Entity.Polymer_seq_one_letter_code       ");
            String oneLetterCode = polymer.getOneLetterCode();
            chan.write("\n;\n");
            int codeLen = oneLetterCode.length();
            int j = 0;
            while (j < codeLen) {
                int endIndex = j + 40;
                if (endIndex > codeLen) {
                    endIndex = codeLen;
                }
                String segment = oneLetterCode.substring(j, endIndex);
                chan.write(segment);
                chan.write("\n");
                j += 40;
            }
            chan.write(";\n");
            chan.write("_Entity.Number_of_monomers            ");
            chan.write(codeLen + "\n");
            chan.write("_Entity.Nstd_monomer                  ");
            if (nonStandard) {
                chan.write("yes\n");
            } else {
                chan.write("no\n");
            }
            chan.write("\n");
            chan.write("_Entity.Nomenclature                  ");
            chan.write(polymer.getNomenclature());
            chan.write("\n");
            chan.write("_Entity.Capped                  ");
            if (polymer.isCapped()) {
                chan.write("yes\n");
            } else {
                chan.write("no\n");
            }
        }
    }

    static void writeCompoundToSTAR3(Writer chan, Compound compound, int entityID, final String mode) throws IOException, ParseException {
        String label = compound.name;
        chan.write(STAR3Base.SAVE + "chem_comp_" + label + mode + "\n");
        chan.write("_Chem_comp.Sf_category                 ");
        chan.write("chem_comp\n");
        chan.write("_Chem_comp.Sf_framecode                           ");
        chan.write("chem_comp_" + label + mode + "\n");
        STAR3Base.writeLoopStrings(chan, chemCompAtomLoopStrings);
        int iAtom = 0;
        for (Atom atom : compound.getAtoms()) {
            chan.write(toSTAR3CompoundString(iAtom, atom, entityID));
            chan.write("\n");
        }
        chan.write("stop_\n");
        if (!compound.getBonds().isEmpty()) {
            STAR3Base.writeLoopStrings(chan, chemCompBondLoopStrings);
            int iBond = 1;
            for (Bond bond : compound.getBonds()) {
                if (bond.begin.entity == bond.end.entity) {
                    chan.write(toSTAR3CompoundBondString(iBond, bond, entityID));
                    chan.write("\n");
                    iBond++;
                }
            }
            chan.write("stop_\n");
        }
        chan.write(STAR3Base.SAVE + "\n\n");
    }

    static String toSTAR3CompoundBondString(int ID, Bond bond, int entityID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(ID);
        result.append(sep);
        result.append("?");
        result.append(sep);
        switch (bond.order) {
            case SINGLE:
                result.append("SING");
                break;
            case DOUBLE:
                result.append("DOUB");
                break;
            case TRIPLE:
                result.append("TRIP");
                break;
            default:
                result.append(" ?  ");
        }
        result.append(sep);
        result.append(bond.begin.getName());
        result.append(sep);
        result.append(bond.end.getName());
        result.append(sep);
        result.append(bond.begin.getName());
        result.append(sep);
        result.append(bond.end.getName());
        result.append(sep);
        result.append("?");
        result.append(sep);
        result.append(entityID);
        result.append(sep);
        result.append(bond.begin.entity.name);
        return result.toString();
    }

    static String toSTAR3CommonNameString(Entity.EntityCommonName eCN, int entityID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(STAR3Base.quote(eCN.getName()));
        result.append(sep);
        result.append(STAR3Base.quote(eCN.getType()));
        result.append(sep);
        result.append(entityID);
        return result.toString();
    }

    static String toSTAR3CompIndexString(int ID, Residue residue, int entityID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(ID);
        result.append(sep);
        result.append(residue.number);
        result.append(sep);
        result.append(residue.name);
        result.append(sep);
        result.append(".");
        result.append(sep);
        result.append(entityID);
        return result.toString();
    }

    public static void writeEntitySeqSTAR3(Writer chan, Polymer polymer, int entityID) throws IOException {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        String[] loopStrings = entityCompIndexLoopStrings;
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        Iterator<Residue> residueIterator = polymer.iterator();
        int i = 1;
        while (residueIterator.hasNext()) {
            Residue residue = residueIterator.next();
            chan.write(toSTAR3CompIndexString(i++, residue, entityID));
            chan.write("\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
        loopStrings = entityChemCompDeletedLoopStrings;
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        i = 1;
        for (AtomSpecifier atom : polymer.getDeletedAtoms()) {
            result.setLength(0);
            result.append(i++);
            result.append(sep);
            result.append(toSTAR3AtomIndexString(atom));
            result.append(sep);
            result.append(entityID);
            chan.write(result.toString());
            chan.write("\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
        String[] orders = {"single", "double", "triple"};
        loopStrings = entityBondLoopStrings;
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        //    1  peptide   single   1  ALA   N  10  GLU  C  ?  1
        i = 1;
        for (BondSpecifier bond : polymer.getAddedBonds()) {
            AtomSpecifier atom1 = bond.getAtom1();
            AtomSpecifier atom2 = bond.getAtom2();
            result.setLength(0);
            result.append(i++);
            result.append(sep);
            result.append('?');
            result.append(sep);
            result.append(orders[bond.getOrder().getOrderNum() - 1]);
            result.append(sep);
            result.append(toSTAR3AtomIndexString(atom1));
            result.append(sep);
            result.append(toSTAR3AtomIndexString(atom2));
            result.append(sep);
            result.append(entityID);
            chan.write(result.toString());
            chan.write("\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
    }

    public static void writeMoleculeSTAR3(Writer chan, MoleculeBase molecule, int assemblyID) throws IOException, ParseException {
        Iterator<Entity> entityIterator = molecule.entityLabels.values().iterator();
        Set<String> cmpdSet = new HashSet<>();
        int entityID = 1;
        chan.write("\n\n");
        chan.write("    ####################################\n");
        chan.write("    #  Biological polymers and ligands #\n");
        chan.write("    ####################################\n");
        chan.write("\n\n");
        var entities = molecule.entities.values().stream().sorted(Comparator.comparingInt(a -> a.entityID)) .toList();
        for (var entity : entities) {
            if (entity instanceof Polymer polymer) {
                writeEntityHeaderSTAR3(chan, entity, entityID, false);
                writeEntityCommonNamesSTAR3(chan, entity, entityID);
                writeEntitySeqSTAR3(chan, polymer, entityID);
                chan.write(STAR3Base.SAVE + "\n\n");
                if (!polymer.getNomenclature().equals("IUPAC") && !polymer.getNomenclature().equals("XPLOR")) {
                    writeComponentsSTAR3(chan, polymer, cmpdSet, false);
                } else {
                    writeComponentsSTAR3(chan, polymer, cmpdSet, true);
                }
            } else {
                writeCompoundHeaderSTAR3(chan, (Compound) entity, entityID);
                chan.write(STAR3Base.SAVE + "\n\n");
                writeCompoundToSTAR3(chan, (Compound) entity, entityID, "");
            }
            entityID++;
        }
        String name = molecule.getName();
        chan.write("\n\n");
        chan.write("    #############################################\n");
        chan.write("    #  Molecular system (assembly) description  #\n");
        chan.write("    #############################################\n");
        chan.write("\n\n");
        chan.write(STAR3Base.SAVE + "assembly\n");
        chan.write("_Assembly.Sf_category                 ");
        chan.write("assembly\n");
        chan.write("_Assembly.Sf_framecode                 ");
        chan.write("assembly\n");
        chan.write("_Assembly.Entry_ID                    ");
        chan.write(".\n");
        chan.write("_Assembly.ID                          ");
        chan.write(assemblyID + "\n");
        chan.write("_Assembly.Name               ");
        if (name == null) {
            chan.write("null\n");
        } else {
            chan.write(STAR3.quote(name) + "\n");
        }
        chan.write("_Assembly.Number_of_components                   ");
        int nEntities = molecule.entities.size();
        chan.write(nEntities + "\n");
        for (String key : molecule.getPropertyNames()) {
            String propValue = molecule.getProperty(key);
            if ((propValue != null) && (!propValue.isEmpty())) {
                chan.write("_Assembly.NvJ_prop_" + key + "                   ");
                STAR3.writeString(chan, propValue, 1024);
            }
        }
        chan.write("\n");
        STAR3.writeLoopStrings(chan, entityAssemblyLoopStrings);
        AtomicInteger compID = new AtomicInteger(1);
        List<IOException> ioExceptions = new ArrayList<>();
        for (CoordSet coordSet : molecule.coordSets.values()) {
            coordSet.entities.values().stream().sorted(Comparator.comparing(Entity::getIDNum)).forEach( entity -> {
                try {
                    chan.write(toSTAR3String(entity, coordSet.getName(), assemblyID, compID.getAndIncrement()) + "\n");
                } catch (IOException e) {
                    ioExceptions.add(e);
                }
            });
            if (!ioExceptions.isEmpty()) {
                throw ioExceptions.getFirst();
            }
        }
        chan.write("stop_\n");
        chan.write("\n");
        chan.write(STAR3Base.SAVE + "\n");
    }

    public static String toSTARChemShiftAssignmentString(final SpatialSet spatialSet, final int id, final int ppmSet) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        Atom atom = spatialSet.atom;
        result.append(id);
        result.append(sep);
        result.append(".");
        result.append(sep);
        Entity entity = atom.getEntity();
        int entityID = entity.getIDNum();
        int entityAssemblyID = entity.assemblyID;
        int number = 1;
        if (entity instanceof Residue) {
            entityID = ((Residue) entity).polymer.getIDNum();
            entityAssemblyID = ((Residue) entity).polymer.assemblyID;
            number = atom.getEntity().getIDNum();
        }
        result.append(entityAssemblyID);
        result.append(sep);
        result.append(entityID);
        result.append(sep);
        result.append(number);
        result.append(sep);
        result.append(number);
        result.append(sep);
        result.append(atom.getEntity().getName());
        result.append(sep);
        result.append(atom.getName());
        result.append(sep);
        String eName = AtomProperty.getElementName(atom.getAtomicNumber());
        result.append(eName);
        result.append(sep);
        result.append(".");
        result.append(sep);
        PPMv ppmv = spatialSet.getPPM(ppmSet);
        if ((ppmv != null) && ppmv.isValid()) {
            result.append(String.format("%.4f", ppmv.getValue()));
            result.append(sep);
            result.append(String.format("%.4f", ppmv.getError()));
        } else {
            result.append(".");
            result.append(sep);
            result.append(".");
        }
        result.append(sep);
        result.append(".");
        result.append(sep);
        if ((ppmv != null) && ppmv.isValid()) {
            int ambig = ppmv.getAmbigCode();
            if (ambig < 1) {
                ambig = atom.getBMRBAmbiguity();
            }
            result.append(ambig);
        } else {
            result.append(".");
        }
        result.append(sep);
        result.append(".");
        result.append(sep);
        String resIDStr = ".";
        if (atom.getResonance() != null) {
            long resID = atom.getResonance().getID();
            resIDStr = String.valueOf(resID);
        }
        result.append(resIDStr);
        result.append(sep);
        String rNum = ((Compound) atom.getEntity()).getNumber();
        if (rNum.trim().isEmpty()) {
            rNum = ".";
        }
        result.append(rNum);
        result.append(sep);
        result.append(atom.getEntity().getName());
        result.append(sep);
        result.append(atom.getName());
        result.append(sep);
        result.append(".");
        result.append(sep);
        result.append(1);
        return result.toString();
    }

    static void writeAssignmentsSTAR3(Writer chan, final int ppmSet) throws IOException, ParseException, InvalidMoleculeException {
        chan.write("\n\n");
        chan.write("    ###################################\n");
        chan.write("    #  Assigned chemical shift lists  #\n");
        chan.write("    ###################################\n");
        chan.write("\n\n");
        chan.write("###################################################################\n");
        chan.write("#       Chemical Shift Ambiguity Index Value Definitions          #\n");
        chan.write("#                                                                 #\n");
        chan.write("#   Index Value            Definition                             #\n");
        chan.write("#                                                                 #\n");
        chan.write("#      1             Unique (geminal atoms and geminal methyl     #\n");
        chan.write("#                         groups with identical chemical shifts   #\n");
        chan.write("#                         are assumed to be assigned to           #\n");
        chan.write("#                         stereospecific atoms)                   #\n");
        chan.write("#      2             Ambiguity of geminal atoms or geminal methyl #\n");
        chan.write("#                         proton groups                           #\n");
        chan.write("#      3             Aromatic atoms on opposite sides of          #\n");
        chan.write("#                         symmetrical rings (e.g. Tyr HE1 and HE2 #\n");
        chan.write("#                         protons)                                #\n");
        chan.write("#      4             Intraresidue ambiguities (e.g. Lys HG and    #\n");
        chan.write("#                         HD protons or Trp HZ2 and HZ3 protons)  #\n");
        chan.write("#      5             Interresidue ambiguities (Lys 12 vs. Lys 27) #\n");
        chan.write("#      9             Ambiguous, specific ambiguity not defined    #\n");
        chan.write("#                                                                 #\n");
        chan.write("###################################################################\n");
        chan.write("\n\n");
        chan.write(STAR3Base.SAVE + "assigned_chem_shift_list_" + ppmSet + "\n");
        chan.write("_Assigned_chem_shift_list.Sf_category                 ");
        chan.write("assigned_chemical_shifts\n");
        chan.write("_Assigned_chem_shift_list.Sf_framecode                 ");
        chan.write("assigned_chem_shift_list_" + ppmSet + "\n");
        chan.write("_Assigned_chem_shift_list.Sample_condition_list_ID      ");
        chan.write(".\n");
        chan.write("_Assigned_chem_shift_list.Sample_condition_list_label    ");
        chan.write(".\n");
        chan.write("_Assigned_chem_shift_list.Chem_shift_reference_ID        ");
        chan.write(".\n");
        chan.write("_Assigned_chem_shift_list.Chem_shift_reference_label      ");
        chan.write(".\n");
        chan.write("\n");
        STAR3.writeLoopStrings(chan, chemShiftAssignmentStrings);
        boolean wroteAtLeastOne = false;
        int iAtom = 1;
        List<Atom> atoms = new ArrayList<>();
        MolFilter molFilter = new MolFilter("*.*");
        MoleculeBase.selectAtomsForTable(molFilter, atoms);
        for (Atom atom : atoms) {
            SpatialSet spatialSet = atom.getSpatialSet();
            PPMv ppmv = spatialSet.getPPM(ppmSet);
            if ((ppmv != null) && ppmv.isValid()) {
                String string = NMRStarWriter.toSTARChemShiftAssignmentString(spatialSet, iAtom, ppmSet);
                chan.write(string + "\n");
                iAtom++;
                wroteAtLeastOne = true;
            }
        }
        if (!wroteAtLeastOne) {
            chan.write("? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ?\n");
        }
        chan.write("stop_\n");
        chan.write("\nsave_\n");
        chan.write("\n");
    }

    public static void writePPM(MoleculeBase molecule, Writer chan, int whichStruct) throws IOException, InvalidMoleculeException {
        int i;
        chan.write("loop_\n");
        chan.write("  _Atom_shift_assign_ID\n");
        chan.write("  _Residue_author_seq_code\n");
        chan.write("  _Residue_seq_code\n");
        chan.write("  _Residue_label\n");
        chan.write("  _Atom_name\n");
        chan.write("  _Atom_type\n");
        chan.write("  _Chem_shift_value\n");
        chan.write("  _Chem_shift_value_error\n");
        chan.write("  _Chem_shift_ambiguity_code\n");
        if (molecule == null) {
            throw new InvalidMoleculeException("No active mol");
        }
        int iPPM = 0;
        i = 0;
        molecule.updateAtomArray();
        for (Atom atom : molecule.getAtomArray()) {
            String result = atom.ppmToString(iPPM, i);
            if (result != null) {
                chan.write(result + "\n");
                i++;
            }
        }
        chan.write("\nstop_\n\n");
    }

    public static void writeXYZ(MoleculeBase molecule, Writer chan, int whichStruct) throws IOException {
        int i = 0;
        int iStruct;
        chan.write("loop_\n");
        chan.write("  _Atom_ID\n");
        if (whichStruct < 0) {
            chan.write("  _Conformer_number\n");
        }
        chan.write("  _Mol_system_component_name\n");
        chan.write("  _Residue_seq_code\n");
        chan.write("  _Residue_label\n");
        chan.write("  _Atom_name\n");
        chan.write("  _Atom_type\n");
        chan.write("  _Atom_coord_x\n");
        chan.write("  _Atom_coord_y\n");
        chan.write("  _Atom_coord_z\n");
        int[] structureList = molecule.getActiveStructures();
        for (int j : structureList) {
            iStruct = j;
            if ((whichStruct >= 0) && (iStruct != whichStruct)) {
                continue;
            }
            molecule.updateAtomArray();
            for (Atom atom : molecule.getAtomArray()) {
                SpatialSet spatialSet = atom.getSpatialSet();
                String result = atom.xyzToString(spatialSet, iStruct, i);
                if (result != null) {
                    chan.write(result + "\n");
                    i++;
                }
            }
        }
        chan.write("\nstop_\n\n");
    }

    public static String[] getCoordLoopStrings() {
        return atomCoordinateLoopStrings.clone();
    }

    String toSTAR3PolySeqString(int ID, Residue residue, int entityID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(".");
        result.append(sep);
        result.append(residue.name);
        result.append(sep);
        result.append(ID);
        result.append(sep);
        result.append(ID);
        result.append(sep);
        result.append(entityID);
        return result.toString();
    }
    // "Auth_entity_assembly_ID", "Auth_seq_ID", "Auth_comp_ID", "Auth_atom_ID",

    static void buildAuthorAtomString(StringBuilder sBuilder, Atom atom) {
        String resNum = ".";
        String resName = ".";
        String atomName = ".";
        if (atom != null) {
            resNum = String.valueOf((atom.getResidueNumber()));
            resName = atom.getResidueName();
            atomName = atom.getName();
        }
        sBuilder.append(String.format("%-3s", "."));
        sBuilder.append(String.format("%-6s ", resNum));
        sBuilder.append(String.format("%-4s ", resName));
        sBuilder.append(String.format("%-4s ", atomName));
    }

    static void buildAtomString(StringBuilder sBuilder, Atom atom, int entityID) {
        String resNum = ".";
        String resName = ".";
        String atomName = ".";
        String nucName = ".";
        int isotope = 1;
        int compID = 1;
        if (atom != null) {
            resNum = String.valueOf((atom.getResidueNumber()));
            Entity atomEntity = atom.getEntity();
            compID = atomEntity.getIDNum();
            resName = atom.getResidueName();
            atomName = atom.getName();
            nucName = atom.getElementName();
            isotope = switch (nucName) {
                case "C" -> 13;
                case "N" -> 15;
                case "F" -> 19;
                case "P" -> 31;
                default -> 1;
            };
        }

        sBuilder.append(String.format("%-4s ", "."));
        sBuilder.append(String.format("%-4d ", entityID));
        sBuilder.append(String.format("%-4d ", entityID));
        sBuilder.append(String.format("%-6d ", compID));
        sBuilder.append(String.format("%-6s ", resNum));
        sBuilder.append(String.format("%-6s ", resName));
        sBuilder.append(String.format("%-4s ", atomName));
        sBuilder.append(String.format("%-4s ", nucName));
        sBuilder.append(String.format("%-4d ", isotope));
    }

    void writeExperiment(Writer chan, String catName, int expID, String nmrExpType, int sampleID, String sampleLabel,
                         String idType, int listID) throws IOException {
        chan.write("   loop_\n");
        chan.write("   " + catName + "_experiment.Experiment_ID\n");
        chan.write("   " + catName + "_experiment.Experiment_name\n");
        chan.write("   " + catName + "_experiment.Sample_ID\n");
        chan.write("   " + catName + "_experiment.Sample_label\n");
        chan.write("   " + catName + "_experiment.Sample_state\n");
        chan.write("   " + catName + "_experiment.Entry_ID\n");
        chan.write("   " + catName + "_experiment." + idType + "\n");
        chan.write("\n");

        String result1 = String.format("%-2d %-7s %-7s %-9s %-2s %-2s %-2d", expID, STAR3.quote(nmrExpType), sampleID, sampleLabel, ".", ".", listID);

        chan.write("      " + result1 + "\n");
        chan.write("   stop_\n\n");

    }

    /**
     * Write out the NOE sections of the STAR file.
     *
     * @param chan        Writer. The FileWriter to use
     * @param molecule    Molecule. The molecule to use
     * @param relaxationSet Set of NOE relaxation data
     * @param listID      int. The number of the NOE block in the file.
     * @throws IOException
     */
    public static void writeNOE(Writer chan, MoleculeBase molecule, RelaxationSet relaxationSet, int listID) throws IOException {
        String frameName = relaxationSet.name();
        double field = relaxationSet.field();
        chan.write("    ########################################\n");
        chan.write("    #  Heteronuclear NOE values  #\n");
        chan.write("    ########################################\n");
        chan.write("\n\n");
        chan.write(STAR3Base.SAVE + frameName + "\n");
        chan.write("   _Heteronucl_NOE_list.Sf_category                    ");
        chan.write("heteronucl_NOEs\n");
        chan.write("   _Heteronucl_NOE_list.Sf_framecode                   ");
        chan.write(frameName + "\n");
        chan.write("   _Heteronucl_NOE_list.Entry_ID                       ");
        chan.write(".\n"); //fixme get dynamically
        chan.write("   _Heteronucl_NOE_list.ID                             ");
        chan.write(listID + "\n");
        chan.write("   _Heteronucl_NOE_list.Sample_condition_list_ID       ");
        chan.write(listID + "\n");
        chan.write("   _Heteronucl_NOE_list.Sample_condition_list_label    ");
        chan.write("$sample_conditions_" + listID + "\n");
        chan.write("   _Heteronucl_NOE_list.Spectrometer_frequency_1H      ");
        chan.write(field + "\n");
        chan.write("   _Heteronucl_NOE_list.Heteronuclear_NOE_val_type      ");
        chan.write(STAR3Base.quote("peak height") + "\n");
        chan.write("   _Heteronucl_NOE_list.ref_val      ");
        chan.write("0\n"); //fixme get dynamically
        chan.write("   _Heteronucl_NOE_list.ref_description      ");
        chan.write(".\n");
        chan.write("   _Heteronucl_NOE_list.Details                        ");
        chan.write(".\n");


        chan.write("\n");

        String[] loopStrings = {"ID", "Assembly_atom_ID_1", "Entity_assembly_ID_1", "Entity_ID_1", "Comp_index_ID_1", "Seq_ID_1",
                "Comp_ID_1", "Atom_ID_1", "Atom_type_1", "Atom_isotope_number_1", "Assembly_atom_ID_2", "Entity_assembly_ID_2", "Entity_ID_2",
                "Comp_index_ID_2", "Seq_ID_2", "Comp_ID_2", "Atom_ID_2", "Atom_type_2", "Atom_isotope_number_2", "Val", "Val_err",
                "Resonance_ID_1", "Resonance_ID_2", "Auth_entity_assembly_ID_1", "Auth_seq_ID_1", "Auth_comp_ID_1", "Auth_atom_ID_1",
                "Auth_entity_assembly_ID_2", "Auth_seq_ID_2", "Auth_comp_ID_2", "Auth_atom_ID_2", "Entry_ID", "Heteronucl_NOE_list_ID"};

        chan.write("   loop_\n");
        for (String loopString : loopStrings) {
            chan.write("      _Heteronucl_NOE." + loopString + "\n");
        }
        chan.write("\n");

        int idx = 1;

        List<String> prevRes = new ArrayList<>();
        for (RelaxationData noeData : relaxationSet.data().values()) {
            var resSource = noeData.getResonanceSource();
            var atom = resSource.getAtom();
            Entity entity = atom.getTopEntity();
            int entityID = entity.getIDNum();
            Double value = noeData.getValue();
            Double error = noeData.getError();
            Atom atom2 = resSource.getAtoms()[1];
            String outputLine = toStarNOEString(idx, listID, entityID, atom, atom2, value, error);
            if (outputLine != null && !prevRes.contains(entityID + "." + atom.getResidueNumber())) {
                chan.write("      " + outputLine + "\n");
                prevRes.add(entityID + "." + atom.getResidueNumber());
                idx++;
            }
        }

        chan.write("   stop_\n");
        chan.write(STAR3Base.SAVE + "\n\n");

    }

    /**
     * Write the data lines in the NOE Data block of the STAR file.
     *
     * @param idx      int. The line index
     * @param listID   int. The number of the R1/R2/T1rho/NOE block in the file.
     * @param entityID int. The number of the molecular entity.
     * @param atom1    Atom. The first atom in the NOE atom pair.
     * @param atom2    Atom. The second atom in the NOE atom pair.
     * @param value    Double. parameter value.
     * @param error    Double. error value.
     * @return
     */
    public static String toStarNOEString(int idx, int listID, int entityID, Atom atom1, Atom atom2, Double value, Double error) {

        Atom[] atoms = {atom1, atom2};

        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(String.format("%-5d", idx));
        for (Atom atom : atoms) {
            buildAtomString(sBuilder, atom, entityID);
        }
        sBuilder.append(String.format("%-8.3f", value));
        sBuilder.append(String.format("%-8.3f", error));
        sBuilder.append(String.format("%-3s", "."));
        sBuilder.append(String.format("%-3s", "."));
        for (Atom atom : atoms) {
            buildAuthorAtomString(sBuilder, atom);
        }
        sBuilder.append(String.format("%-4s", "."));
        sBuilder.append(String.format("%-4d", listID));

        return sBuilder.toString();

    }

    /**
     * Write out the Relaxation Data (R1, R2, T1rho) sections of the STAR file.
     *
     * @param chan          Writer. The Writer to use
     * @param molecule      Molecule. The molecule to use
     * @param  relaxationSet
     * @param listID        int. The number of the R1/R2/T1rho/NOE block in the file.
     * @throws IOException
     */
    public static void writeRelaxation(Writer chan, MoleculeBase molecule, RelaxationSet relaxationSet, int listID) throws IOException {
        RelaxTypes expType = relaxationSet.relaxType();
        String expName = expType.getName().toUpperCase();
        if (expName.equals("R1")) {
            expName = "T1";
        } else if (expName.equals("R2")) {
            expName = "T2";
        }

        String frameName = relaxationSet.name();
        double field = relaxationSet.field();
        String coherenceType = relaxationSet.extras().get("coherenceType");
        String units = relaxationSet.extras().get("units");
        chan.write("    ########################################\n");
        chan.write("    #  Heteronuclear " + expName + " relaxation values  #\n");
        chan.write("    ########################################\n");
        chan.write("\n\n");
        chan.write(STAR3Base.SAVE + frameName + "\n");
        chan.write("   _Heteronucl_" + expName + "_list.Sf_category                    ");
        chan.write("heteronucl_" + expName + "_relaxation\n");
        chan.write("   _Heteronucl_" + expName + "_list.Sf_framecode                   ");
        chan.write(frameName + "\n");
        chan.write("   _Heteronucl_" + expName + "_list.Entry_ID                       ");
        chan.write(".\n"); //fixme get dynamically
        chan.write("   _Heteronucl_" + expName + "_list.ID                             ");
        chan.write(listID + "\n");
        chan.write("   _Heteronucl_" + expName + "_list.Sample_condition_list_ID       ");
        chan.write(listID + "\n");
        chan.write("   _Heteronucl_" + expName + "_list.Sample_condition_list_label    ");
        chan.write("$sample_conditions_" + listID + "\n");
        chan.write("   _Heteronucl_" + expName + "_list.Temp_calibration_method        ");
        chan.write(STAR3.quote("no calibration applied") + "\n");
        chan.write("   _Heteronucl_" + expName + "_list.Temp_control_method            ");
        chan.write(STAR3.quote("no temperature control applied") + "\n");
        chan.write("   _Heteronucl_" + expName + "_list.Spectrometer_frequency_1H      ");
        chan.write(field + "\n");
        chan.write("   _Heteronucl_" + expName + "_list." + expName + "_coherence_type              ");
        chan.write(coherenceType + "\n");
        chan.write("   _Heteronucl_" + expName + "_list." + expName + "_val_units                   ");
        chan.write(units + "\n");
        chan.write("   _Heteronucl_" + expName + "_list.Rex_units                      ");
        chan.write(".\n");
        chan.write("   _Heteronucl_" + expName + "_list.Details                        ");
        chan.write(".\n");
        chan.write("   _Heteronucl_" + expName + "_list.Text_data_format               ");
        chan.write(".\n");
        chan.write("   _Heteronucl_" + expName + "_list.Text_data                      ");
        chan.write(".\n");

        chan.write("\n");

        String[] loopStrings = {"ID", "Assembly_atom_ID", "Entity_assembly_ID", "Entity_ID", "Comp_index_ID", "Seq_ID",
                "Comp_ID", "Atom_ID", "Atom_type", "Atom_isotope_number", "Val", "Val_err", "Resonance_ID", "Auth_entity_assembly_ID",
                "Auth_seq_ID", "Auth_comp_ID", "Auth_atom_ID", "Entry_ID", "Heteronucl_" + expName + "_list_ID"};
        if (expType.equals(RelaxTypes.R2) || expType.equals(RelaxTypes.R1RHO)) {
            loopStrings = new String[]{"ID", "Assembly_atom_ID", "Entity_assembly_ID", "Entity_ID", "Comp_index_ID", "Seq_ID",
                    "Comp_ID", "Atom_ID", "Atom_type", "Atom_isotope_number", expName + "_val", expName + "_val_err", "Rex_val", "Rex_err",
                    "Resonance_ID", "Auth_entity_assembly_ID", "Auth_seq_ID", "Auth_comp_ID", "Auth_atom_ID", "Entry_ID", "Heteronucl_" + expName + "_list_ID"};
        }
        chan.write("   loop_\n");
        for (String loopString : loopStrings) {
            chan.write("      _" + expName + "." + loopString + "\n");
        }
        chan.write("\n");

        int idx = 1;

        List<String> prevRes = new ArrayList<>();

        for (RelaxationData relaxData : relaxationSet.data().values()) {
            var resSource = relaxData.getResonanceSource();
            var atom = resSource.getAtom();
            Entity entity = atom.getTopEntity();
            int entityID = entity.getIDNum();
            Double value = relaxData.getValue();
            Double error = relaxData.getError();
            List<Double> results = new ArrayList<>();
            results.add(value);
            results.add(error);
            if (expType.equals(RelaxTypes.R2) || expType.equals(RelaxTypes.R1RHO)) {
                Double RexValue = null;
                Double RexError = null;
                if (relaxData instanceof RelaxationRex relaxationRex) {
                    RexValue = relaxationRex.getRexValue();
                    RexError = relaxationRex.getRexError();
                }
                results.add(RexValue);
                results.add(RexError);
            }
            String outputLine = toStarRelaxationString(idx, expType, listID, entityID, atom, results);
            chan.write("      " + outputLine + "\n");
            prevRes.add(entityID + "." + atom.getResidueNumber());
            idx++;

        }
        chan.write("   stop_\n");
        chan.write(STAR3Base.SAVE + "\n\n");

    }

    /**
     * Write the data lines in the Relaxation Data (R1, R2, T1rho) blocks of the
     * STAR file.
     *
     * @param idx      int. The line index
     * @param expType  RelaxTypes. The experiment type: R1, R2, T1rho.
     * @param listID   int. The number of the R1/R2/T1rho block in the file.
     * @param entityID int. The number of the molecular entity.
     * @param atom     Atom. The atom in the molecule.
     * @param results  The relaxation and error values: {value, error, RexValue,
     *                 RexError}.
     * @return String ready for STAR output
     */
    public static String toStarRelaxationString(int idx, RelaxTypes expType, int listID, int entityID, Atom atom, List<Double> results) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(String.format("%-5d", idx));
        buildAtomString(sBuilder, atom, entityID);

        results.forEach((value) -> {
            if (value != null) {
                sBuilder.append(String.format("%-8.3f", value));
            } else {
                sBuilder.append(String.format("%-3s", "."));
            }
        });
        sBuilder.append(String.format("%-3s", "."));

        buildAuthorAtomString(sBuilder, atom);
        sBuilder.append(String.format("%-4s", "."));
        sBuilder.append(String.format("%-4d", listID));

        return sBuilder.toString();

    }

    /**
     * Write out the Relaxation Data (R1, R2, T1rho) sections of the STAR file.
     *
     * @param chan         Writer. The Writer to use
     * @param molecule     Molecule. The molecule to use
     * @param orderParList The list of order parameters to write
     * @param listID       int. The number of the R1/R2/T1rho/NOE block in the file.
     * @throws IOException
     */
    public static void writeOrderPars(Writer chan, MoleculeBase molecule, OrderParSet orderParSet, List<OrderPar> orderParList, int listID, String frameName) throws IOException {
        String catName = "_Order_parameter_list";

        chan.write("    ########################################\n");
        chan.write("    #  Order parameters  #\n");
        chan.write("    ########################################\n");
        chan.write("\n\n");
        chan.write(STAR3Base.SAVE + frameName + "\n");
        chan.write("   " + catName + ".Sf_category                    ");
        chan.write("order_parameters\n");
        chan.write("   " + catName + ".Sf_framecode                   ");
        chan.write(frameName + "\n");
        chan.write("   " + catName + ".Entry_ID                       ");
        chan.write(".\n"); //fixme get dynamically
        chan.write("   " + catName + ".ID                             ");
        chan.write(listID + "\n");
        chan.write("   " + catName + ".Sample_condition_list_ID       ");
        chan.write(listID + "\n");
        chan.write("   " + catName + ".Sample_condition_list_label    ");
        chan.write("$sample_conditions_" + listID + "\n");
        chan.write("   " + catName + ".Tau_e_val_units                      ");
        chan.write(".\n");
        chan.write("   " + catName + ".Tau_f_val_units                      ");
        chan.write(".\n");
        chan.write("   " + catName + ".Tau_s_val_units                      ");
        chan.write(".\n");
        chan.write("   " + catName + ".Rex_field_strength                      ");
        chan.write(".\n");
        chan.write("   " + catName + ".Rex_val_units                      ");
        chan.write("s-1\n");
        chan.write("   " + catName + ".Details                        ");
        chan.write(".\n");
        chan.write("   " + catName + ".Text_data_format               ");
        chan.write(".\n");
        chan.write("   " + catName + ".Text_data                      ");
        chan.write(".\n");

        chan.write("\n");

        String[] atomIDLoopStrings = {"ID", "Assembly_atom_ID", "Entity_assembly_ID", "Entity_ID", "Comp_index_ID", "Seq_ID",
                "Comp_ID", "Atom_ID", "Atom_type", "Atom_isotope_number"};

        String[] authStrings = {"Auth_entity_assembly_ID",
                "Auth_seq_ID", "Auth_comp_ID", "Auth_atom_ID", "Entry_ID", "Order_parameter_list_ID"};

        chan.write("   loop_\n");
        catName = "_Order_param";
        for (String loopString : atomIDLoopStrings) {
            chan.write("      " + catName + "." + loopString + "\n");
        }
        for (String loopString : OrderPar.getOrderParLoopString()) {
            chan.write("      " + catName + "." + loopString + "\n");
        }
        for (String loopString : authStrings) {
            chan.write("      " + catName + "." + loopString + "\n");
        }

        chan.write("\n");

        int idx = 1;


        for (OrderPar orderPar : orderParList) {
            var resSource = orderPar.getResonanceSource();
            var atom = resSource.getAtom();
            Entity entity = atom.getTopEntity();
            int entityID = entity.getIDNum();

            String outputLine = toStarOrderParString(idx, listID, entityID, atom, orderPar);
            if (outputLine != null) {
                chan.write("      " + outputLine + "\n");
                idx++;
            }
        }

        chan.write("   stop_\n");
        chan.write(STAR3Base.SAVE + "\n\n");
    }

    /**
     * Write the data lines in the Order Parameter blocks of the
     * STAR file.
     *
     * @param idx      int. The line index
     * @param listID   int. The number of the R1/R2/T1rho block in the file.
     * @param entityID int. The number of the molecular entity.
     * @param atom     Atom. The atom in the molecule.
     * @param orderPar The order parameter.
     * @return String containing oreder parameter values
     */
    public static String toStarOrderParString(int idx, int listID, int entityID, Atom atom, OrderPar orderPar) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(String.format("%-5d", idx));
        buildAtomString(sBuilder, atom, entityID);
        orderPar.valuesToStarString(sBuilder);

        buildAuthorAtomString(sBuilder, atom);
        sBuilder.append(String.format("%-4s", "."));
        sBuilder.append(String.format("%-4d", listID));

        return sBuilder.toString();

    }

    public static void writeAll(String fileName) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(fileName)) {
            writeAll(writer,null);
        }
    }

    public static void writeAll(File file) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(file)) {
            writeAll(writer,null);
        }
    }

    public static StringWriter writeToString(Map<StarTypes, SimpleBooleanProperty> starTypesMap){
        try (StringWriter writer = new StringWriter()) {
            writeAll(writer, starTypesMap);
            return writer;
        } catch (IOException | ParseException | InvalidPeakException | InvalidMoleculeException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeSoftwareSTAR3(Writer chan) throws  IOException {
        String softwareSf = """
                save_software_1
                  _Software.Sf_category  software
                  _Software.Sf_framecode  software_1
                  _Software.ID            1
                  _Software.Name          NMRFx
                  _Software.Version       %s
                  
                  loop_
                  _Task.Task
                  
                  'chemical shift assignment'
                  'peak picking'
                  
                  stop_
                  
                save_
                
                """.formatted(NvUtil.getVersion());
        chan.write(softwareSf);
    }

    public static void writeAll(Writer chan, Map<StarTypes, SimpleBooleanProperty> starTypesMap) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {

        String projectName = "NMRFx_Project";
        if (ProjectBase.getActive().getDirectory() != null) {
            String filename = ProjectBase.getActive().getDirectory().getFileName().toString();
            if (!filename.isBlank()) {
                projectName = filename.replace(' ', '_');
            }
        }
        chan.write("data_" + projectName + "\n\n");
        ResonanceFactory resFactory = ProjectBase.activeResonanceFactory();
        resFactory.clean();

        writeSoftwareSTAR3(chan);
        
        MoleculeBase molecule = MoleculeFactory.getActive();
        if (molecule != null) {
            if (starTypesMap == null || starTypesMap.get(StarTypes.MOLECULES).get()) {
                writeMoleculeSTAR3(chan, molecule, 1);
            }
        }
        // fixme Dataset.writeDatasetsToSTAR3(channelName);
        if (starTypesMap == null || starTypesMap.get(StarTypes.PEAKLISTS).get()) {
            Iterator<PeakList> iter = PeakList.iterator();
            PeakWriter peakWriter = new PeakWriter();
            while (iter.hasNext()) {
                PeakList peakList = iter.next();
                peakWriter.writePeaksSTAR3(chan, peakList);
            }
        }
        if (starTypesMap == null || starTypesMap.get(StarTypes.RESONANCES).get()) {
            resFactory.writeResonancesSTAR3(chan);
        }

        if (molecule != null) {
            if (starTypesMap == null || starTypesMap.get(StarTypes.ASSIGNMENTS).get()) {
                int ppmSetCount = molecule.getPPMSetCount();
                for (int iSet = 0; iSet < ppmSetCount; iSet++) {
                    writeAssignmentsSTAR3(chan, iSet);
                }
            }
            if (starTypesMap == null || starTypesMap.get(StarTypes.COORDINATES).get()) {
                CoordinateSTARWriter.writeToSTAR3(chan, molecule, 1);
            }

            int setNum = 1;
            if (starTypesMap == null || starTypesMap.get(StarTypes.CONSTRAINTS).get()) {
                for (ConstraintSet cSet : molecule.getMolecularConstraints().noeSets()) {
                    if (cSet.getSize() > 0) {
                        ConstraintSTARWriter.writeConstraintsSTAR3(chan, cSet, setNum++);
                    }
                }

                setNum = 1;
                for (ConstraintSet cSet : molecule.getMolecularConstraints().angleSets()) {
                    if (cSet.getSize() > 0) {
                        ConstraintSTARWriter.writeConstraintsSTAR3(chan, cSet, setNum++);
                    }
                }
            }
        }
        if (starTypesMap == null || starTypesMap.get(StarTypes.PEAKPATHS).get()) {
            PeakPathWriter pathWriter = new PeakPathWriter();
            int iPath = 0;
            for (PeakPaths peakPath : PeakPaths.get()) {
                pathWriter.writeToSTAR3(chan, peakPath, iPath + 1);
                iPath++;
            }
        }
        if (molecule != null) {
            if (starTypesMap == null || starTypesMap.get(StarTypes.RELAXATION).get()) {
                var molRelaxData = RelaxationData.getRelaxationData(molecule.getAtomArray());
                // loop over types so they always end up in same order in star file (useful for testing)
                // also results in listID counting from 1 for each type
                for (var type : RelaxTypes.values()) {
                    int listID = 1;
                    for (var relaxEntry : molRelaxData.entrySet()) {
                        var relaxationSet = relaxEntry.getKey();
                        if (!relaxationSet.data().isEmpty()) {
                            var relaxType = relaxationSet.relaxType();
                            if (relaxType == type) {
                                if (relaxType == RelaxTypes.NOE) {
                                    writeNOE(chan, molecule, relaxationSet, listID);
                                } else {
                                    writeRelaxation(chan, molecule, relaxationSet, listID);
                                }
                                listID++;
                            }
                        }
                    }
                }
            }
            if (starTypesMap == null || starTypesMap.get(StarTypes.ORDERPARAMETERS).get()) {
                var orderParData = OrderPar.getOrderParameters(molecule.getAtomArray());
                int listID = 1;
                for (var relaxEntry : orderParData.entrySet()) {
                    var orderParList = relaxEntry.getValue();
                    var orderParSet = relaxEntry.getKey();
                    if (!orderParList.isEmpty()) {
                        writeOrderPars(chan, molecule, orderParSet, orderParList, listID, orderParSet.name());
                        listID++;
                    }
                }
            }
        }
        ProjectBase.getActive().writeSaveframes(chan);
    }
}
