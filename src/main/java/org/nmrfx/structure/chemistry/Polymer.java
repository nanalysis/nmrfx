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

import java.util.*;

public class Polymer extends Entity {

    public Hashtable residues;
    public ArrayList<Residue> residueList = null;
    public Residue firstResidue = null;
    public Residue lastResidue = null;
    private String polymerType = "polypeptide(L)";
    private String strandID = "A";
    private String oneLetterCode = "";
    private String nomenclature = "IUPAC";
    private boolean capped = true;
    private boolean libraryMode = true;
    ArrayList<AtomSpecifier> deletedAtoms = new ArrayList<AtomSpecifier>();
    ArrayList<BondSpecifier> addedBonds = new ArrayList<BondSpecifier>();

    class PolymerIterator implements Iterator {

        Residue current = firstResidue;

        public boolean hasNext() {
            return (current != null);
        }

        public Object next() {
            Object result = current;
            if (current == null) {
                throw new NoSuchElementException();
            }
            current = current.next;
            return result;
        }

        public void remove() {
            if (current != null) {
                Residue hold = current.next;
                removeResidue(current);
                current = hold;
            }
        }
    }

    public Polymer(String name) {
        this.name = name;
        this.label = name;
        residues = new Hashtable();
    }

    public Polymer(String label, String name) {
        this.name = name;
        this.label = label;
        residues = new Hashtable();
    }

    public Iterator iterator() {
        return new PolymerIterator();
    }

    public void addResidueOld(Residue residue) {
        residues.put(residue.number.toLowerCase(), residue);
        residues.put(residue.name.toLowerCase(), residue);
        residueList = null;
    }

    public Residue getResidue(String name) {
        return ((Residue) residues.get(name.toLowerCase()));
    }

    public Residue getResidue(int resNum) {
        ArrayList<Residue> vec = getResidues();

        if ((resNum < 0) || (resNum >= vec.size())) {
            return null;
        }

        return ((Residue) vec.get(resNum));
    }

    public int size() {
        getResidues();
        return residueList.size();
    }

    public void addResidue(Residue residue) {
        residue.polymer = this;
        residue.molecule = molecule;

        if (firstResidue == null) {
            firstResidue = residue;
        }

        residue.previous = lastResidue;

        if (lastResidue != null) {
            lastResidue.next = residue;
        }

        lastResidue = residue;
        residue.next = null;
        residues.put(residue.number.toLowerCase(), residue);
        residues.put(residue.name.toLowerCase(), residue);
        molecule.nResidues++;
        residueList = null;
    }

    public void removeResidue(Residue residue) {
        if (residue != null) {
            if (residue == firstResidue) {
                firstResidue = residue.next;
                firstResidue.previous = null;
            } else if (residue == lastResidue) {
                lastResidue = residue.previous;
                lastResidue.next = null;
            } else {
                residue.previous.next = residue.next;
                residue.next.previous = residue.previous;
            }
            residues.remove(residue.number.toLowerCase());
            residues.remove(residue.name.toLowerCase());

            molecule.nResidues--;
            residueList = null;
        }
    }

    public ArrayList<Residue> getResidues() {
        StringBuffer sBuf = new StringBuffer();
        int idNum = 1;
        if (residueList == null) {
            residueList = new ArrayList<>();

            Residue res = firstResidue;

            while (res != null) {
                residueList.add(res);
                res.entityID = idNum++;
                sBuf.append(res.getOneLetter());
                res = res.next;
            }
            oneLetterCode = sBuf.toString();
        }

        return residueList;
    }

    public int renumberResidue(String oldNumber, String newNumber) {
        oldNumber = oldNumber.toLowerCase();
        newNumber = newNumber.toLowerCase();
        Residue test = getResidue(newNumber);
        if (test != null) {
            return (1);
        } else {
            Residue residue = getResidue(oldNumber);
            residue.number = newNumber;
            residues.remove(oldNumber);
            residues.put(newNumber, residue);
            return (0);
        }
    }

    public String getPolymerType() {
        return polymerType;
    }

    public boolean isCapped() {
        return capped;
    }

    public void setCapped(final boolean capped) {
        this.capped = capped;
    }

    public boolean isLibraryMode() {
        return libraryMode;
    }

    public void setLibraryMode(final boolean libraryMode) {
        this.libraryMode = libraryMode;
    }

    public String getNomenclature() {
        if (nomenclature.equals("")) {
            nomenclature = "IUPAC";
        }
        return nomenclature;
    }

    public void setNomenclature(final String nomenclature) {
        if (nomenclature.equals("")) {
            this.nomenclature = "IUPAC";
        } else {
            this.nomenclature = nomenclature;
        }
    }

    public String getStrandID() {
        return strandID;
    }

    public void setStrandID(String s) {
        strandID = s;
    }

    public void setPolymerType(String s) {
        polymerType = s;
    }

    public String getOneLetterCode() {
        getResidues();
        return oneLetterCode;
    }

    public ArrayList<AtomSpecifier> getDeletedAtoms() {
        return deletedAtoms;
    }

    public ArrayList<BondSpecifier> getAddedBonds() {
        return addedBonds;
    }

    public void addBonds(List<String> orderColumn, List<String> comp1IndexIDColumn, List<String> atom1IDColumn, List<String> comp2IndexIDColumn, List<String> atom2IDColumn) {
        for (int i = 0; i < orderColumn.size(); i++) {
            String orderString = (String) orderColumn.get(i);
            String comp1 = (String) comp1IndexIDColumn.get(i);
            String comp2 = (String) comp2IndexIDColumn.get(i);
            String atom1Name = (String) atom1IDColumn.get(i);
            String atom2Name = (String) atom2IDColumn.get(i);
            Residue residue1 = getResidue(comp1);
            Residue residue2 = getResidue(comp2);
            Atom atom1 = residue1.getAtom(atom1Name);
            Atom atom2 = residue2.getAtom(atom2Name);
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

            Atom.addBond(atom1, atom2, order, true);
        }
    }

    public void removeAtoms(List<String> compIndexIDColumn, List<String> atomIDColumn) {
        for (int i = 0; i < compIndexIDColumn.size(); i++) {
            String comp = (String) compIndexIDColumn.get(i);
            String atomName = (String) atomIDColumn.get(i);
            Residue residue = getResidue(comp);
            Atom atom = residue.getAtom(atomName);
            atom.remove(true);
        }
    }

    public void freezeResidueRange(int start, int end, boolean state) {
        molecule.updateAtomArray();
        residueList.stream().filter(res -> (res.getResNum() >= start) && (res.getResNum() <= end)).forEach(res -> {
            res.atoms.stream().filter(atom -> atom.irpIndex > 0).forEach(atom -> atom.rotActive = state);
        });
        molecule.setupRotGroups();
        molecule.setupAngles();
    }

}
