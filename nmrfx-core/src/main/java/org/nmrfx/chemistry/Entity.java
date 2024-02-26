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
package org.nmrfx.chemistry;

import org.nmrfx.annotations.PluginAPI;

import java.io.Serializable;
import java.util.*;

@PluginAPI("ring")
public class Entity implements AtomContainer, Serializable, ITree {
    public String name = null;
    public String label = null;
    String pdbChain = "";
    public MoleculeBase molecule = null;
    public List<Atom> atoms = new ArrayList<Atom>();
    public List<Bond> bonds = new ArrayList<Bond>();
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
    HashMap<String, String> propertyMap = new HashMap<>();
    Map<String, Object> propertyObjectMap = new HashMap<>();
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

        ibonds.forEach((ibond) -> {
            if (ibond.getAtom(0) == atom) {
                result.add(ibond.getAtom(1));
            } else if (ibond.getAtom(1) == atom) {
                result.add(ibond.getAtom(0));
            }
        });
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

    public void changed(Atom atom) {
        if (molecule != null) {
            molecule.changed(atom);
        }
    }

    public void setPDBChain(String name) {
        pdbChain = name;
    }

    public String getPDBChain() {
        return pdbChain;
    }

    public void setProperty(String propName, String propValue) {
        propertyMap.put(propName, propValue);
    }

    public void setPropertyObject(String propID, Object propValue) {
        propertyObjectMap.put(propID, propValue);
    }

    public Object getPropertyObject(String propID) {
        return propertyObjectMap.get(propID);
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

    @Override
    public List<Atom> getAtomArray() {
        return getAtoms();
    }

    @Override
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
        atom.removeBonds();
        atoms.remove(atom);
        molecule.invalidateAtomArray();
    }

    public void addBond(final Bond bond) {
        bonds.add(bond);
    }

    public void removeBond(final Bond bond) {
        bonds.remove(bond);
    }

    @Override
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

}
