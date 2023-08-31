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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class Compound extends Entity implements AtomIterable {

    protected HashMap atomMap;
    public String number = "";
    Integer resNum;
    public int iRes = 0;
    public int labelNum = 1;

    protected Compound() {
    }

    public Compound(String number, String name) {
        this(number, name, name);
    }

    public Compound(String number, String name, String label) {
        this.name = name;
        this.number = number;
        try {
            resNum = Integer.valueOf(number);
        } catch (NumberFormatException nfE) {
            System.out.println(number);
            resNum = null;
        }
        this.label = label;
        atomMap = new HashMap();
    }

    @Override
    public Iterator iterator() {
        return atoms.iterator();
    }

    @Override
    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public Integer getResNum() {
        return resNum;
    }

    public void setNumber(final String number) {
        this.number = number;
    }

    @Override
    public int getIDNum() {
        return entityID;
    }

    @Override
    public void removeAtom(Atom atom) {
        super.removeAtom(atom);
        atomMap.remove(atom.getName().toLowerCase());
        molecule.invalidateAtomArray();
        setHasEquivalentAtoms(false);
    }

    public void removeAllAtoms() {
        int nAtoms = atoms.size();
        for (int i = nAtoms - 1; i >= 0; i--) {
            Atom atom = atoms.get(i);
            removeAtom(atom);
        }
        bonds.clear();
    }

    @Override
    public void addAtom(Atom afterAtom, Atom atom) {
        super.addAtom(afterAtom, atom);
        atom.entity = this;
        atomMap.put(atom.name.toLowerCase(), atom);
        molecule.invalidateAtomArray();
        setHasEquivalentAtoms(false);
    }

    public void addAtom(Atom atom) {
        addAtom(null, atom);
    }

    public Atom getAtom(String name) {
        return ((Atom) atomMap.get(name.toLowerCase()));
    }

    public Atom getAtomLoose(String name) {
        return getAtom(name);
    }

    public ArrayList<Atom> getAtoms(String match) {
        ArrayList<Atom> atomList = new ArrayList<>();
        match = match.toLowerCase();
        for (Atom atom : atoms) {
            String aName = atom.getName().toLowerCase();
            if (Util.stringMatch(aName, match)) {
                atomList.add(atom);
            }
        }
        return atomList;
    }

    public ArrayList<Bond> getBonds() {
        ArrayList<Bond> bondList = new ArrayList<>(bonds);
        return bondList;
    }

    public void calcAllBonds() {
        int result;
        int nBonds = 0;
        for (int i = 0; i < atoms.size(); i++) {
            for (int j = i + 1; j < atoms.size(); j++) {
                Atom atom1 = atoms.get(i);
                Atom atom2 = atoms.get(j);
                if (!atom1.isBonded(atom2)) {
                    result = Atom.calcBond(atom1, atom2, Order.SINGLE);
                    if (result == 2) {
                        break;
                    }
                    if (result == 0) {
                        nBonds++;
                    }
                }
            }
        }
    }

    public int renameAtom(String oldName, String newName) {
        Atom test = getAtom(newName);

        if (test != null) {
            return (1);
        } else {
            Atom atom = getAtom(oldName);
            if (atom == null) {
                return (1);
            }
            atom.name = newName;
            atomMap.remove(oldName.toLowerCase());
            atomMap.put(atom.name.toLowerCase(), atom);

            return (0);
        }
    }

    public void updateNames() {
        HashMap newMap = new HashMap();
        atoms.forEach((atom) -> {
            newMap.put(atom.name.toLowerCase(), atom);
        });
        atomMap = newMap;
    }

    public String toNEFSequenceString(int idx, String link) {
        //sequence code
        int num = Integer.parseInt(this.getNumber());
        //chain ID
        String chainCode = this.getPropertyObject("chain").toString();
        char chainID = chainCode.charAt(0);

        //residue name
        String resName = this.name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }

        //residue variant
        String resVar = this.label;

        return String.format("%8d %7s %7d %9s %-14s %-7s", idx, chainID, num, resName, link, resVar);
    }

}
