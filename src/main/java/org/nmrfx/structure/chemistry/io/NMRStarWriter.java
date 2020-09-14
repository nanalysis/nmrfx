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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.nmrfx.processor.datasets.peaks.AtomResonance;
import org.nmrfx.processor.datasets.peaks.AtomResonanceFactory;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakPath;
import org.nmrfx.processor.datasets.peaks.ResonanceFactory;
import org.nmrfx.processor.datasets.peaks.io.PeakPathWriter;
import org.nmrfx.processor.datasets.peaks.io.PeakWriter;
import org.nmrfx.processor.star.ParseException;
import org.nmrfx.processor.star.STAR3;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.AtomProperty;
import org.nmrfx.structure.chemistry.AtomSpecifier;
import org.nmrfx.structure.chemistry.Bond;
import org.nmrfx.structure.chemistry.BondSpecifier;
import org.nmrfx.structure.chemistry.Compound;
import org.nmrfx.structure.chemistry.CoordSet;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.MolFilter;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.SpatialSet;
import org.nmrfx.structure.chemistry.constraints.AngleConstraint;
import org.nmrfx.structure.chemistry.constraints.ConstraintSet;
import org.nmrfx.structure.chemistry.constraints.Noe;
import org.nmrfx.structure.chemistry.constraints.NoeSet;
import org.nmrfx.structure.utilities.Format;

/**
 *
 * @author brucejohnson
 */
public class NMRStarWriter {

    protected static final String[] entityCompIndexLoopStrings = {"_Entity_comp_index.ID", "_Entity_comp_index.Auth_seq_ID", "_Entity_comp_index.Comp_ID", "_Entity_comp_index.Comp_label", "_Entity_comp_index.Entity_ID"};
    protected static final String[] entityAssemblyLoopStrings = {"_Entity_assembly.ID", "_Entity_assembly.Entity_assembly_name", "_Entity_assembly.Entity_ID", "_Entity_assembly.Entity_label", "_Entity_assembly.Asym_ID", "_Entity_assembly.Experimental_data_reported", "_Entity_assembly.Physical_state", "_Entity_assembly.Conformational_isomer", "_Entity_assembly.Chemical_exchange_state", "_Entity_assembly.Magnetic_equivalence_group_code", "_Entity_assembly.Role", "_Entity_assembly.Details", "_Entity_assembly.Assembly_ID"};
    private static final String[] entityCommonNameLoopStrings = {"_Entity_common_name.Name", "_Entity_common_name.Type", "_Entity_common_name.Entity_ID"};
    protected static final String[] entityBondLoopStrings = {"_Entity_bond.ID", "_Entity_bond.Type", "_Entity_bond.Value_order", "_Entity_bond.Comp_index_ID_1", "_Entity_bond.Comp_ID_1", "_Entity_bond.Atom_ID_1", "_Entity_bond.Comp_index_ID_2", "_Entity_bond.Comp_ID_2", "_Entity_bond.Atom_ID_2", "_Entity_bond.Entity_ID"};
    private static final String[] chemCompEntityIndexLoopStrings = {"_Entity_comp_index.ID", "_Entity_comp_index.Auth_seq_ID", "_Entity_comp_index.Comp_ID", "_Entity_comp_index.Comp_label", "_Entity_comp_index.Entry_ID", "_Entity_comp_index.Entity_ID"};
    protected static final String[] entityChemCompDeletedLoopStrings = {"_Entity_chem_comp_deleted_atom.ID", "_Entity_chem_comp_deleted_atom.Comp_index_ID", "_Entity_chem_comp_deleted_atom.Comp_ID", "_Entity_chem_comp_deleted_atom.Atom_ID", "_Entity_chem_comp_deleted_atom.Entity_ID"};
    private static final String[] chemCompBondLoopStrings = {"_Chem_comp_bond.Bond_ID", "_Chem_comp_bond.Type", "_Chem_comp_bond.Value_order", "_Chem_comp_bond.Atom_ID_1", "_Chem_comp_bond.Atom_ID_2", "_Chem_comp_bond.PDB_atom_ID_1", "_Chem_comp_bond.PDB_atom_ID_2", "_Chem_comp_bond.Details", "_Chem_comp_bond.Entry_ID", "_Chem_comp_bond.Comp_ID"};
    public static String[] entityPolySeqLoopStrings = {"_Entity_poly_seq.Hetero", "_Entity_poly_seq.Mon_ID", "_Entity_poly_seq.Num", "_Entity_poly_seq.Comp_index_ID", "_Entity_poly_seq.Entity_ID"};
    private static final String[] chemCompAtomLoopStrings = {"_Chem_comp_atom.Atom_ID", "_Chem_comp_atom.PDB_atom_ID", "_Chem_comp_atom.Alt_atom_ID", "_Chem_comp_atom.Auth_atom_ID", "_Chem_comp_atom.Type_symbol", "_Chem_comp_atom.Isotope_number", "_Chem_comp_atom.Chirality", "_Chem_comp_atom.Charge", "_Chem_comp_atom.Partial_charge", "_Chem_comp_atom.Oxidation_number", "_Chem_comp_atom.PDBx_aromatic_flag", "_Chem_comp_atom.PDBx_leaving_atom_flag", "_Chem_comp_atom.Substruct_code", "_Chem_comp_atom.Ionizable", "_Chem_comp_atom.Details", "_Chem_comp_atom.Entry_ID", "_Chem_comp_atom.Comp_ID", "_Chem_comp_atom.Unpaired_electron_number"};
    static String[] chemShiftAssignmentStrings = {"_Atom_chem_shift.ID", "_Atom_chem_shift.Assembly_atom_ID", "_Atom_chem_shift.Entity_assembly_ID", "_Atom_chem_shift.Entity_ID", "_Atom_chem_shift.Comp_index_ID", "_Atom_chem_shift.Seq_ID", "_Atom_chem_shift.Comp_ID", "_Atom_chem_shift.Atom_ID", "_Atom_chem_shift.Atom_type", "_Atom_chem_shift.Atom_isotope_number", "_Atom_chem_shift.Val", "_Atom_chem_shift.Val_err", "_Atom_chem_shift.Assign_fig_of_merit", "_Atom_chem_shift.Ambiguity_code", "_Atom_chem_shift.Occupancy", "_Atom_chem_shift.Resonance_ID", "_Atom_chem_shift.Auth_seq_ID", "_Atom_chem_shift.Auth_comp_ID", "_Atom_chem_shift.Auth_atom_ID", "_Atom_chem_shift.Details", "_Atom_chem_shift.Assigned_chem_shift_list_ID"};
    private static String[] atomCoordinateLoopStrings = {"_Atom_site.Assembly_ID", "_Atom_site.Model_ID", "_Atom_site.Model_site_ID", "_Atom_site.ID", "_Atom_site.Assembly_atom_ID", "_Atom_site.Label_entity_assembly_ID", "_Atom_site.Label_entity_ID", "_Atom_site.Label_comp_index_ID", "_Atom_site.Label_comp_ID", "_Atom_site.Label_atom_ID", "_Atom_site.Type_symbol", "_Atom_site.Cartn_x", "_Atom_site.Cartn_y", "_Atom_site.Cartn_z", "_Atom_site.Cartn_x_esd", "_Atom_site.Cartn_y_esd", "_Atom_site.Cartn_z_esd", "_Atom_site.Occupancy", "_Atom_site.Occupancy_esd", "_Atom_site.Uncertainty", "_Atom_site.Ordered_flag", "_Atom_site.Footnote_ID", "_Atom_site.Details", "_Atom_site.Entry_ID", "_Atom_site.Conformer_family_coord_set_ID"};

    static String toSTAR3CompoundString(int ID, Atom atom, int entityID) {
        StringBuffer result = new StringBuffer();
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
        result.append(((Compound) atom.entity).label);
        result.append(sep);
        result.append("?");
        return result.toString();
    }

    public static void writeEntityCommonNamesSTAR3(FileWriter chan, Entity entity, int entityID) throws IOException {
        if (entity.getCommonNames().size() > 0) {
            String[] loopStrings = NMRStarWriter.entityCommonNameLoopStrings;
            chan.write("loop_\n");
            for (String loopString : loopStrings) {
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
        result.append("\"").append(entity.name).append("\"");
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
        result.append(entity.details);
        result.append(sep);
        result.append(assemblyID);
        result.append(sep);
        return result.toString();
    }

    static String toSTAR3String(Polymer polymer, String coordSetName, int assemblyID, int compID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(compID);
        result.append(sep);
        result.append("\"").append(polymer.name).append("\"");
        result.append(sep);
        result.append(polymer.entityID);
        result.append(sep);
        result.append("$").append(polymer.label);
        result.append(sep);
        result.append(coordSetName);
        result.append(sep);
        result.append("yes");
        result.append(sep);
        result.append(polymer.physicalState);
        result.append(sep);
        result.append(polymer.conformationalIsomer);
        result.append(sep);
        result.append(polymer.chemicalExchangeState);
        result.append(sep);
        result.append(polymer.magneticEquivalenceGroupCode);
        result.append(sep);
        result.append(polymer.role);
        result.append(sep);
        result.append(polymer.details);
        result.append(sep);
        result.append(assemblyID);
        result.append(sep);
        return result.toString();
    }

    static String toSTAR3AtomIndexString(final AtomSpecifier atom) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(atom.getResNum());
        result.append(sep);
        result.append(atom.getResName());
        result.append(sep);
        result.append(atom.getName());
        return result.toString();
    }

    static void writeEntityHeaderSTAR3(FileWriter chan, Compound compound, int entityID) throws ParseException, IOException {
        String label = compound.label;
        chan.write("save_" + label + "\n");
        chan.write("_Entity.Sf_category                 ");
        chan.write("entity\n");
        chan.write("_Entity.framecode                           ");
        chan.write(label + "\n");
        chan.write("_Entity.ID                           ");
        chan.write(entityID + "\n");
        chan.write("_Entity.Name                        ");
        chan.write(label + "\n");
        chan.write("_Entity.Type                          ");
        chan.write("non-polymer\n");
        chan.write("\n");
        STAR3.writeLoopStrings(chan, chemCompEntityIndexLoopStrings);
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append("1");
        result.append(sep);
        result.append(compound.getNumber());
        result.append(sep);
        result.append(label);
        result.append(sep);
        result.append(label);
        result.append(sep);
        result.append(".");
        result.append(sep);
        result.append(entityID);
        chan.write(result.toString());
        chan.write("\nstop_\n\n");
    }

    public static void writeComponentsSTAR3(FileWriter chan, Polymer polymer, Set<String> cmpdSet) throws IOException, ParseException {
        Iterator residueIterator = polymer.iterator();
        int i = 1;
        while (residueIterator.hasNext()) {
            Residue residue = (Residue) residueIterator.next();
            String mode;
            if (i == 1) {
                mode = "." + i;
            } else if (!residueIterator.hasNext()) {
                mode = "." + i;
            } else {
                mode = "";
            }
            if (!cmpdSet.contains(residue.label + mode)) {
                writeCompoundToSTAR3(chan, residue, i, mode);
                cmpdSet.add(residue.label + mode);
            }
            i++;
        }
    }

    static void writeEntityHeaderSTAR3(FileWriter chan, Entity entity, int entityID) throws IOException {
        String label = entity.label;
        chan.write("save_" + label + "\n");
        chan.write("_Entity.Sf_category                 ");
        chan.write("entity\n");
        chan.write("_Entity.framecode                           ");
        chan.write(label + "\n");
        chan.write("_Entity.ID                           ");
        chan.write(entityID + "\n");
        chan.write("_Entity.Name                        ");
        chan.write(label + "\n");
        chan.write("_Entity.Type                          ");
        if (entity instanceof Polymer) {
            Polymer polymer = (Polymer) entity;
            chan.write("polymer\n");
            chan.write("_Entity.Polymer_type                  ");
            chan.write(polymer.getPolymerType() + "\n");
            chan.write("_Entity.Polymer_strand_ID            ");
            String strandID = polymer.getStrandID();
            if (strandID.equals("")) {
                strandID = "?";
            }
            chan.write(strandID + "\n");
            chan.write("_Entity.Polymer_seq_one_letter_code_can  ");
            chan.write("?\n");
            chan.write("_Entity.Polymer_seq_one_letter_code       ");
            String oneLetterCode = polymer.getOneLetterCode();
            chan.write("\n;\n");
            int codeLen = oneLetterCode.length();
            boolean nonStandard = false;
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

    static void writeCompoundToSTAR3(FileWriter chan, Compound compound, int entityID, final String mode) throws IOException, ParseException {
        String label = compound.label;
        chan.write("save_chem_comp_" + label + mode + "\n");
        chan.write("_Chem_comp.Sf_category                 ");
        chan.write("chem_comp\n");
        chan.write("_Chem_comp.framecode                           ");
        chan.write("chem_comp_" + label + mode + "\n");
        STAR3.writeLoopStrings(chan, chemCompAtomLoopStrings);
        int iAtom = 0;
        for (Atom atom : compound.getAtoms()) {
            chan.write(toSTAR3CompoundString(iAtom, atom, entityID));
            chan.write("\n");
        }
        chan.write("stop_\n");
        if (compound.getBonds().size() > 0) {
            STAR3.writeLoopStrings(chan, chemCompBondLoopStrings);
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
        chan.write("save_\n\n");
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
        result.append(((Compound) bond.begin.entity).label);
        return result.toString();
    }

    static String toSTAR3CommonNameString(Entity.EntityCommonName eCN, int entityID) {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        result.append(STAR3.quote(eCN.getName()));
        result.append(sep);
        result.append(STAR3.quote(eCN.getType()));
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

    public static void writeEntitySeqSTAR3(FileWriter chan, Polymer polymer, int entityID) throws IOException {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        String[] loopStrings = Polymer.entityCompIndexLoopStrings;
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        Iterator residueIterator = polymer.iterator();
        int i = 1;
        while (residueIterator.hasNext()) {
            Residue residue = (Residue) residueIterator.next();
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

    public static void writeMoleculeSTAR3(FileWriter chan, Molecule molecule, int assemblyID) throws IOException, ParseException {
        Iterator entityIterator = molecule.entityLabels.values().iterator();
        Set<String> cmpdSet = new HashSet<>();
        int entityID = 1;
        chan.write("\n\n");
        chan.write("    ####################################\n");
        chan.write("    #  Biological polymers and ligands #\n");
        chan.write("    ####################################\n");
        chan.write("\n\n");
        while (entityIterator.hasNext()) {
            Entity entity = (Entity) entityIterator.next();
            if (entity instanceof Polymer) {
                writeEntityHeaderSTAR3(chan, entity, entityID);
                writeEntityCommonNamesSTAR3(chan, entity, entityID);
                Polymer polymer = (Polymer) entity;
                writeEntitySeqSTAR3(chan, polymer, entityID);
                chan.write("save_\n\n");
                if (!polymer.getNomenclature().equals("IUPAC") && !polymer.getNomenclature().equals("XPLOR")) {
                    writeComponentsSTAR3(chan, polymer, cmpdSet);
                }
            } else {
                writeEntityHeaderSTAR3(chan, (Compound) entity, entityID);
                chan.write("save_\n\n");
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
        chan.write("save_" + "assembly\n");
        chan.write("_Assembly.Sf_category                 ");
        chan.write("assembly\n");
        chan.write("_Assembly.Sf_framecode                 ");
        chan.write("assembly\n");
        chan.write("_Assembly.Entry_ID                    ");
        chan.write(".\n");
        chan.write("_Assembly.ID                          ");
        chan.write(assemblyID + "\n");
        chan.write("_Assembly.Name               ");
        chan.write(STAR3.quote(name) + "\n");
        chan.write("_Assembly.Number_of_components                   ");
        int nEntities = molecule.entities.size();
        chan.write(nEntities + "\n");
        for (String key : molecule.getPropertyNames()) {
            String propValue = molecule.getProperty(key);
            if ((propValue != null) && (!propValue.equals(""))) {
                chan.write("_Assembly.NvJ_prop_" + key + "                   ");
                STAR3.writeString(chan, propValue, 1024);
            }
        }
        chan.write("\n");
        STAR3.writeLoopStrings(chan, entityAssemblyLoopStrings);
        int compID = 1;
        for (CoordSet coordSet : molecule.coordSets.values()) {
            for (Entity entity : coordSet.entities.values()) {
                chan.write(toSTAR3String(entity, coordSet.getName(), assemblyID, compID) + "\n");
                compID++;
            }
        }
        chan.write("stop_\n");
        chan.write("\n");
        chan.write("save_\n");
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
        String seqNumber = "1";
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
        PPMv ppmv = (PPMv) spatialSet.getPPM(ppmSet);
        if ((ppmv != null) && ppmv.isValid()) {
            result.append(Format.format4(ppmv.getValue()));
            result.append(sep);
            result.append(Format.format4(ppmv.getError()));
        } else {
            result.append(".");
            result.append(sep);
            result.append(".");
        }
        result.append(sep);
        result.append(".");
        result.append(sep);
        if ((ppmv != null) && ppmv.isValid()) {
            int ambig = atom.getBMRBAmbiguity();
            if (ppmv.getAmbigCode() == 1) {
                ambig = 1;
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
        if (rNum.trim().length() == 0) {
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

    static void writeAssignmentsSTAR3(FileWriter chan, final int ppmSet) throws IOException, ParseException, InvalidMoleculeException {
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
        chan.write("save_assigned_chem_shift_list_" + ppmSet + "\n");
        chan.write("_Assigned_chem_shift_list.Sf_category                 ");
        chan.write("assigned_chemical_shifts\n");
        chan.write("_Assigned_chem_shift_list.Sf_framecode                 ");
        chan.write("assigned_chem_shift_list_1\n");
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
        List<Atom> atoms = new ArrayList();
        MolFilter molFilter = new MolFilter("*.*");
        Molecule.selectAtomsForTable(molFilter, atoms);
        for (int i = 0; i < atoms.size(); i++) {
            SpatialSet spatialSet = atoms.get(i).getSpatialSet();
            PPMv ppmv = (PPMv) spatialSet.getPPM(ppmSet);
            if ((ppmv != null) && ppmv.isValid()) {
                String string = NMRStarWriter.toSTARChemShiftAssignmentString(spatialSet, iAtom, ppmSet);
                if (string != null) {
                    chan.write(string + "\n");
                    iAtom++;
                    wroteAtLeastOne = true;
                }
            }
        }
        if (!wroteAtLeastOne) {
            chan.write("? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ? ?\n");
        }
        chan.write("stop_\n");
        chan.write("\nsave_\n");
        chan.write("\n");
    }

    public static void writePPM(FileWriter chan, int whichStruct) throws IOException, InvalidMoleculeException {
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
        Molecule molecule = Molecule.getActive();
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

    public static void writeXYZ(FileWriter chan, int whichStruct) throws IOException, InvalidMoleculeException {
        int i = 0;
        int iStruct = 0;
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
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new InvalidMoleculeException("No active molecule");
        }
        int[] structureList = molecule.getActiveStructures();
        for (int jStruct = 0; jStruct < structureList.length; jStruct++) {
            iStruct = structureList[jStruct];
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

    public static void writeAll(String fileName) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(fileName)) {
            writeAll(writer);
        }
    }

    public static void writeAll(File file) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        try (FileWriter writer = new FileWriter(file)) {
            writeAll(writer);
        }
    }

    public static void writeAll(FileWriter chan) throws IOException, ParseException, InvalidPeakException, InvalidMoleculeException {
        Date date = new Date(System.currentTimeMillis());
        chan.write("    ######################################\n");
        chan.write("    # Saved " + date.toString() + " #\n");
        chan.write("    ######################################\n");
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            writeMoleculeSTAR3(chan, molecule, 1);
        }
        // fixme Dataset.writeDatasetsToSTAR3(channelName);
        Iterator iter = PeakList.iterator();
        PeakWriter peakWriter = new PeakWriter();
        while (iter.hasNext()) {
            PeakList peakList = (PeakList) iter.next();
            peakWriter.writePeaksSTAR3(chan, peakList);
        }

        AtomResonanceFactory resFactory = (AtomResonanceFactory) PeakDim.resFactory();

        resFactory.writeResonancesSTAR3(chan);
        if (molecule != null) {
            int ppmSetCount = molecule.getPPMSetCount();
            for (int iSet = 0; iSet < ppmSetCount; iSet++) {
                writeAssignmentsSTAR3(chan, iSet);
            }
            CoordinateSTARWriter.writeToSTAR3(chan, molecule, 1);
            int setNum = 1;
            for (ConstraintSet cSet : NoeSet.getSets()) {
                if (cSet.getSize() > 0) {
                    ConstraintSTARWriter.writeConstraintsSTAR3(chan, cSet, setNum++);
                }
            }
            ConstraintSet cSet = AngleConstraint.getActiveSet();
            setNum = 1;
            if (cSet.getSize() > 0) {
                ConstraintSTARWriter.writeConstraintsSTAR3(chan, cSet, setNum++);
            }
        }
        PeakPathWriter pathWriter = new PeakPathWriter();
        int iPath = 0;
        for (PeakPath peakPath : PeakPath.get()) {
            pathWriter.writeToSTAR3(chan, peakPath, iPath+1);
            iPath++;
        }
    }

}
