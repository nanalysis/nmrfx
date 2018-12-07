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
package org.nmrfx.structure.chemistry;

import org.nmrfx.structure.chemistry.miner.AtomContainer;

import java.io.*;
import java.util.*;
import org.nmrfx.structure.chemistry.energy.AngleTreeGenerator;
import org.nmrfx.structure.chemistry.miner.IAtom;
import org.nmrfx.structure.chemistry.miner.IBond;

public class Entity implements AtomContainer, Serializable, ITree {

    public static String[] entityStrings = {
        "_Entity.Ambiguous_conformational_states",
        "_Entity.Ambiguous_chem_comp_sites",
        "_Entity.Nstd_monomer",
        "_Entity.Nstd_chirality",
        "_Entity.Nstd_linkage",
        "_Entity.Nonpolymer_comp_ID",
        "_Entity.Nonpolymer_comp_label",
        "_Entity.Number_of_monomers",
        "_Entity.Paramagnetic",
        "_Entity.Thiol_state",
        "_Entity.Src_method",
        "_Entity.Fragment",};
    public final static String[] entityCompIndexLoopStrings = {
        "_Entity_comp_index.ID",
        "_Entity_comp_index.Auth_seq_ID",
        "_Entity_comp_index.Comp_ID",
        "_Entity_comp_index.Comp_label",
        "_Entity_comp_index.Entity_ID",};
    public final static String[] entityPolySeqLoopStrings = {
        "_Entity_poly_seq.Hetero",
        "_Entity_poly_seq.Mon_ID",
        "_Entity_poly_seq.Num",
        "_Entity_poly_seq.Comp_index_ID",
        "_Entity_poly_seq.Entity_ID",};
    public String name = null;
    public String label = null;
    public Molecule molecule = null;
    List<Atom> atoms = new ArrayList<Atom>();
    List<Bond> bonds = new ArrayList<Bond>();
    boolean hasEquivalentAtoms = false;
    public CoordSet coordSet = null;
    public int entityID = 0;
    public int assemblyID = 0;
    public String physicalState = "native";
    public String conformationalIsomer = "no";
    public String chemicalExchangeState = "no";
    public String magneticEquivalenceGroupCode = "?";
    public String role = "?";
    public String details = "?";
    private HashMap<String, String> propertyMap = new HashMap<String, String>();
    ArrayList<EntityCommonName> commonNames = new ArrayList<>();

    @Override
    public int getAtomCount() {
        return atoms.size();
    }

    @Override
    public int getBondCount() {
        return bonds.size();
    }

    @Override
    public IBond getBond(int i) {
        return bonds.get(i);
    }

    @Override
    public IBond getBond(IAtom atom1, IAtom atom2) {
        Bond result = null;
        for (Bond bond : bonds) {
            if ((bond.begin == atom1) && (bond.end == atom2)) {
                result = bond;
            } else if ((bond.begin == atom2) && (bond.end == atom1)) {
                result = bond;
            }
        }
        return result;
    }

    @Override
    public List<IAtom> atoms() {
        List<IAtom> result = new ArrayList<>();
        result.addAll(atoms);
        return result;
    }

    @Override
    public List<IBond> getBonds(IAtom atom) {
        return atom.getBonds();
    }

    @Override
    public List<IBond> bonds() {
        List<IBond> result = new ArrayList<>();
        result.addAll(bonds);
        return result;
    }

    @Override
    public List<IAtom> getConnectedAtomsList(IAtom atom) {
        List<IBond> ibonds = atom.getBonds();
        List<IAtom> result = new ArrayList<>();

        for (IBond ibond : ibonds) {
            if (ibond.getAtom(0) == atom) {
                result.add(ibond.getAtom(1));
            } else if (ibond.getAtom(1) == atom) {
                result.add(ibond.getAtom(0));
            }
        }
        return result;
    }

    @Override
    public List<IBond> getConnectedBondsList(IAtom atom) {
        List<IBond> result = atom.getBonds();
        return result;
    }

    @Override
    public int getAtomNumber(IAtom atom) {
        return atoms.indexOf(atom);
    }

    @Override
    public int getBondNumber(IBond bond) {
        int index = bonds.indexOf(bond);
        if (index == -1) {
            int i = 0;
            for (Bond bond2 : bonds) {
                if ((bond.getAtom(0) == bond2.getAtom(0)) && (bond.getAtom(1) == bond2.getAtom(1))) {
                    index = i;
                    break;
                } else if ((bond.getAtom(0) == bond2.getAtom(1)) && (bond.getAtom(1) == bond2.getAtom(0))) {
                    index = i;
                    break;
                }
                i++;
            }
        }
        if (index == -1) {
            System.out.println("Bond not present " + ((Bond) bond).toString());
        }
        return index;
    }

    public static class EntityCommonName {

        String name = "";
        String type = "?";

        EntityCommonName(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    public String getName() {
        return name;
    }

    public int getIDNum() {
        return entityID;
    }

    public void setIDNum(int entityID) {
        this.entityID = entityID;
    }

    public void changed() {
        if (molecule != null) {
            molecule.changed();
        }
    }

    public void setProperty(String propName, String propValue) {
        propertyMap.put(propName, propValue);
    }

    public String getProperty(String propName) {
        return propertyMap.get(propName);
    }

    public void addCommonName(String name, String type) {
        commonNames.add(new EntityCommonName(name, type));
    }

    public ArrayList<EntityCommonName> getCommonNames() {
        ArrayList<EntityCommonName> copyList = new ArrayList<>(commonNames);
        return copyList;
    }

    public List<Atom> getAtoms() {
        return atoms;
    }

    public List<Atom> getAtomArray() {
        return getAtoms();
    }

    public Atom getAtom(int index) {
        return atoms.get(index);
    }

    public Atom getLastAtom() {
        return getAtom(atoms.size() - 1);
    }

    public boolean hasEquivalentAtoms() {
        return hasEquivalentAtoms;
    }

    public void addAtom(final Atom afterAtom, final Atom atom) {
        int index = -1;
        if (afterAtom != null) {
            index = atoms.indexOf(afterAtom);
        }
        if (index != -1) {
            atoms.add(index + 1, atom);
        } else {
            atoms.add(atom);
        }
        molecule.invalidateAtomArray();
    }

    public void removeAtom(final Atom atom) {
        atoms.remove(atom);
        molecule.invalidateAtomArray();
    }

    public void addBond(final Bond bond) {
        bonds.add(bond);
    }

    public void removeBond(final Bond bond) {
        bonds.remove(bond);
    }

    public List<Bond> getBondList() {
        return bonds;
    }

    public void setHasEquivalentAtoms(boolean state) {
        hasEquivalentAtoms = state;
    }

    public Atom getFirstAtom() {
        return atoms.get(0);
    }

    public CoordSet getCoordSet() {
        return coordSet;
    }

    public void sortByIndex() {
        Collections.sort(atoms, Atom::compareByIndex);
    }

    public void genMeasuredTree(Atom startAtom) {
        if (startAtom == null) {
            startAtom = atoms.get(0);
        }

        AngleTreeGenerator aTreeGen = new AngleTreeGenerator();
        List<List<Atom>> atomTree = aTreeGen.genTree(this, startAtom, null);
        aTreeGen.measureAtomTree(this, atomTree);
        molecule.setRingClosures(aTreeGen.getRingClosures());
    }

}
