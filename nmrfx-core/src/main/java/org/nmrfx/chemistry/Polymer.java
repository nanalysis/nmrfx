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

import java.util.*;

import static java.util.Objects.requireNonNull;

@PluginAPI("ring")
public class Polymer extends Entity {
    private static final String[] CYCLIC_CLOSERS = {
            "CA", "N", "2.5",
            "C", "N", "1.32",
            "C", "H", "2.044",
            "C", "CA", "2.452",
            "O", "H", "3.174",
            "O", "N", "2.271"
    };

    private Map<String, Residue> residues;
    private List<Residue> residueList = new ArrayList<>();
    private Residue firstResidue = null;
    private Residue lastResidue = null;
    private String polymerType = "";
    private String strandID = "A";
    private String nomenclature = "IUPAC";
    private boolean capped = true;
    private boolean libraryMode = true;
    ArrayList<AtomSpecifier> deletedAtoms = new ArrayList<AtomSpecifier>();
    ArrayList<BondSpecifier> addedBonds = new ArrayList<BondSpecifier>();

    class PolymerIterator implements Iterator<Residue> {

        Residue current = getFirstResidue();

        public boolean hasNext() {
            return (current != null);
        }

        public Residue next() {
            Residue result = current;
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
        residues = new HashMap<>();
    }

    public Polymer(String label, String name) {
        this.name = name;
        this.label = label;
        residues = new HashMap<>();
    }


    /**
     * @return the firstResidue
     */
    public Residue getFirstResidue() {
        return firstResidue;
    }

    /**
     * @return the lastResidue
     */
    public Residue getLastResidue() {
        return lastResidue;
    }

    public Iterator<Residue> iterator() {
        return new PolymerIterator();
    }

    @Override
    public String toString() {
        return name;
    }

    public Residue getResidue(String name) {
        return residues.get(name.toLowerCase());
    }

    public Residue getResidue(int resNum) {
        return residueList.get(resNum);
    }

    public int size() {
        return residueList.size();
    }

    public void addResidue(Residue residue) {
        residue.polymer = this;
        residue.molecule = molecule;
        residue.iRes = residueList.size();

        if (firstResidue == null) {
            firstResidue = residue;
        }

        residue.previous = getLastResidue();

        if (lastResidue != null) {
            lastResidue.next = residue;
        }

        lastResidue = residue;
        residue.next = null;
        residues.put(residue.number.toLowerCase(), residue);
        residues.put(residue.name.toLowerCase(), residue);
        residueList.add(residue);
    }

    public void removeResidue(Residue residue) {
        if (residue != null) {
            if (residue == getFirstResidue()) {
                firstResidue = residue.next;
                firstResidue.previous = null;
            } else if (residue == getLastResidue()) {
                lastResidue = residue.previous;
                lastResidue.next = null;
            } else {
                residue.previous.next = residue.next;
                residue.next.previous = residue.previous;
            }
            residues.remove(residue.number.toLowerCase());
            residues.remove(residue.name.toLowerCase());

            residueList.remove(residue);
            renumber();
        }
    }

    private void renumber() {
        int iRes = 0;
        for (Residue res : residueList) {
            res.iRes = iRes++;
        }
    }

    public List<Residue> getResidues() {
        return Collections.unmodifiableList(residueList);
    }

    public void addResidueOffset(int offset) {
        List<Residue> tempResidues = new ArrayList<>(residueList);
        for (Residue residue : tempResidues) {
            Integer resNum = residue.getResNum();
            if (resNum == null) {
                resNum = Integer.parseInt(residue.getNumber());
            }
            resNum += offset;
            residue.setNumber(String.valueOf(resNum));
            residue.resNum = resNum;
        }
        residues.clear();
        residueList.clear();
        for (Residue residue : tempResidues) {
            addResidue(residue);
        }
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

    public void dumpResidues() {
        for (String num : residues.keySet()) {
            System.out.println(num + " " + residues.get(num).getName());
        }
    }

    public String getPolymerType() {
        if ((polymerType == null) || (polymerType.equals(""))) {
            if (isRNA()) {
                return "polyribonucleotide";
            } else if (isDNA()) {
                return "polynucleotide";
            } else {
                return "polypeptide";
            }
        }
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
        StringBuilder sBuf = new StringBuilder();
        for (Residue res : residueList) {
            sBuf.append(res.getOneLetter());
        }
        return sBuf.toString();
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

            if (residue1 != null && residue2 != null) {

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

    public boolean isPeptide() {
        return !isRNA() && !isDNA();
    }

    public boolean isRNA() {
        boolean rna = false;
        for (Residue residue : getResidues()) {
            String resName = residue.getName();
            if (resName.equals("A") || resName.equals("C") || resName.equals("G") || resName.equals("U")) {
                rna = true;
                break;
            }
            if (resName.equals("RADE") || resName.equals("RCYT") || resName.equals("RGUA") || resName.equals("RURA")) {
                rna = true;
                break;
            }
        }
        return rna;
    }

    public boolean isDNA() {
        boolean dna = false;
        for (Residue residue : getResidues()) {
            String resName = residue.getName();
            if (resName.equals("DA") || resName.equals("DC") || resName.equals("DG") || resName.equals("DT")) {
                dna = true;
                break;
            }
            if (resName.equals("ADE") || resName.equals("CYT") || resName.equals("GUA") || resName.equals("THY")) {
                dna = true;
                break;
            }
        }
        return dna;
    }

    /**
     * getCyclicConstraints returns a list of constraints necessary to create a
     * cyclic polymer.
     * <p>
     * Is called by addCyclicBond in refine.py
     */
    public List<String> getCyclicConstraints() {

        List<String> constraints = new ArrayList<>();

        if (getLastResidue() != null && getFirstResidue() != null) {
            return constraints;
        }
        for (int i = 0; i < CYCLIC_CLOSERS.length; i += 3) {
            Atom atom1 = getLastResidue().getAtom(CYCLIC_CLOSERS[i]);
            Atom atom2 = getFirstResidue().getAtom(CYCLIC_CLOSERS[i + 1]);
            if (atom1 == null) {
                System.out.println("no atom1 " + CYCLIC_CLOSERS[i]);
                atom1 = requireNonNull(getLastResidue().getAtom("H1"), "Failed 2 attempts to set atom1.");
            }
            if (atom2 == null) {
                System.out.println("no atom2 " + CYCLIC_CLOSERS[i + 1]);
                atom2 = requireNonNull(getFirstResidue().getAtom("H1"), "Failed 2 attempts to set atom2.");
            }
            String distance = CYCLIC_CLOSERS[i + 2];
            String constraint = atom2.getFullName() + " " + atom1.getFullName() + " " + distance;
            constraints.add(constraint);
        }
        return constraints;
    }
}
