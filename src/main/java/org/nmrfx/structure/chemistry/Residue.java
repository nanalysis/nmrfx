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

public class Residue extends Compound {

    public Residue previous = null;
    public Residue next = null;
    public Polymer polymer;
    private char oneLetter = 0;
    private boolean standard = false;
    static Map standardResSet = new TreeMap();
    Map<String, Atom[]> pseudoMap = new HashMap<String, Atom[]>();

    static {
        String[] standardResidues = {
            "ala", "a", "arg", "r", "asn", "n", "asp", "d", "cys", "c", "gln", "q", "glu", "e",
            "gly", "g", "his", "h", "ile", "i", "leu", "l", "lys", "k", "met", "m", "phe", "f",
            "pro", "p", "ser", "s", "thr", "t", "trp", "w", "tyr", "y", "val", "v",
            "dade", "a", "dcyt", "c", "dgua", "g", "dthy", "t",
            "da", "a", "dc", "c", "dg", "g", "dt", "t",
            "rade", "a", "rcyt", "c", "rgua", "g", "rura", "u",
            "ra", "a", "rc", "c", "rg", "g", "ru", "u",
            "a", "a", "c", "c", "g", "g", "u", "u"
        };
        for (int i = 0; i < standardResidues.length; i += 2) {
            standardResSet.put(standardResidues[i], standardResidues[i + 1]);
        }

    }

    public Residue(String number, String name) {
        this.number = number;
        super.name = name;
        super.label = name;
        if (standardResSet.containsKey(name.toLowerCase())) {
            standard = true;
        }
        try {
            resNum = Integer.valueOf(number);
        } catch (NumberFormatException nfE) {
            System.out.println(number);
            resNum = null;
        }

        super.atomMap = new HashMap();
    }

    @Override
    public void addAtom(final Atom atom) {
        addAtom(null, atom);
    }

    @Override
    public void addAtom(final Atom afterAtom, final Atom atom) {
        super.addAtom(afterAtom, atom);
        atom.entity = this;
        polymer.addAtom(afterAtom, atom);

        setHasEquivalentAtoms(false);
    }

    public void removeAtom(final Atom atom) {
        super.removeAtom(atom);
        polymer.removeAtom(atom);
    }

    @Override
    public Atom getAtom(String name) {
        return ((Atom) atomMap.get(name.toLowerCase()));
    }

    @Override
    public Atom getAtomLoose(String name) {
        String lName = name.toLowerCase();
        Atom atom = (Atom) atomMap.get(lName);
        if (atom == null) {
            if (lName.charAt(0) == 'h') {
                if (polymer.isCapped() && (polymer.firstResidue == this)) {
                    if (lName.equals("hn")) {
                        atom = (Atom) atomMap.get("h1");
                    } else if (lName.equals("h")) {
                        atom = (Atom) atomMap.get("h1");
                    } else if (lName.equals("ht1")) {
                        atom = (Atom) atomMap.get("h1");
                    } else if (lName.equals("ht2")) {
                        atom = (Atom) atomMap.get("h2");
                    } else if (lName.equals("ht3")) {
                        atom = (Atom) atomMap.get("h3");
                    }
                } else if (lName.equals("hn")) {
                    atom = (Atom) atomMap.get("h");
                } else if (lName.equals("h")) {
                    atom = (Atom) atomMap.get("hn");
                }
            } else if (lName.charAt(0) == 'o') {
                if (polymer.isCapped() && (polymer.lastResidue == this)) {
                    if (lName.equals("o")) {
                        atom = (Atom) atomMap.get("o'");
                    } else if (lName.equals("ot1")) {
                        atom = (Atom) atomMap.get("o'");
                    } else if (lName.equals("o1")) {
                        atom = (Atom) atomMap.get("o'");
                    } else if (lName.equals("ot2")) {
                        atom = (Atom) atomMap.get("o''");
                    } else if (lName.equals("o2")) {
                        atom = (Atom) atomMap.get("o''");
                    }
                }
            }
        }
        return atom;
    }

    boolean isStandard() {
        return standard;
    }

    public char getOneLetter() {
        if (oneLetter == 0) {
            if (standard) {
                oneLetter = ((String) standardResSet.get(name.toLowerCase())).toUpperCase().charAt(0);
            } else {
                oneLetter = 'X';
            }
        }
        return oneLetter;
    }

    @Override
    public int getIDNum() {
        polymer.getResidues();
        return entityID;
    }

    public void addPseudoAtoms(String pseudoAtomName, ArrayList<String> atomGroup) {
        Atom[] pAtoms = new Atom[atomGroup.size()];
        int i = 0;
        for (String atomName : atomGroup) {
            pAtoms[i++] = getAtom(atomName);
        }
        pseudoMap.put(pseudoAtomName.toUpperCase(), pAtoms);
    }

    public Atom[] getPseudo(String pseudoName) {
        pseudoName = pseudoName.toUpperCase();
        return pseudoMap.get(pseudoName);
    }

    public void addBond(final Bond bond) {
        super.addBond(bond);
        polymer.addBond(bond);
    }

    public void removeBond(final Bond bond) {
        super.removeBond(bond);
        polymer.removeBond(bond);
    }

    public Double calcChi() {
        return calcChi(0);
    }

    public Double calcChi(int structureNum) {
        if (name.equals("ALA") || name.equals("GLY")) {
            return null;
        } else {
            Atom[] atoms = new Atom[4];
            atoms[0] = getAtom("N");
            atoms[1] = getAtom("CA");
            atoms[2] = getAtom("CB");
            String atom3Name;
            if (name.equals("THR")) {
                atom3Name = "OG1";
            } else if (name.equals("SER")) {
                atom3Name = "OG";
            } else if (name.equals("CYS")) {
                atom3Name = "SG";
            } else if (name.equals("ILE") || name.equals("VAL")) {
                atom3Name = "CG1";
            } else {
                atom3Name = "CG";
            }
            atoms[3] = getAtom(atom3Name);
            return Atom.calcDihedral(atoms, structureNum);
        }
    }

    public Double calcChi2() {
        return calcChi2(0);
    }

    public Double calcChi2(int structureNum) {
        if (name.equals("ALA") || name.equals("GLY")) {
            return null;
        } else {
            Atom[] atoms = new Atom[4];
            if (name.equals("PHE") || name.equals("TRP") || name.equals("TYR")) {
                atoms[0] = getAtom("CA");
                atoms[1] = getAtom("CB");
                atoms[2] = getAtom("CG");
                atoms[3] = getAtom("CD1");
            } else if (name.equals("HIS")) {
                atoms[0] = getAtom("CA");
                atoms[1] = getAtom("CB");
                atoms[2] = getAtom("CG");
                atoms[3] = getAtom("CD2");
            } else if (name.equals("MET")) {
                atoms[0] = getAtom("CA");
                atoms[1] = getAtom("CB");
                atoms[2] = getAtom("CG");
                atoms[3] = getAtom("SD");
            } else if (name.equals("ILE")) {
                atoms[0] = getAtom("CA");
                atoms[1] = getAtom("CB");
                atoms[2] = getAtom("CG1");
                atoms[3] = getAtom("CD1");
            } else if (name.equals("LEU")) {
                atoms[0] = getAtom("CA");
                atoms[1] = getAtom("CB");
                atoms[2] = getAtom("CG");
                atoms[3] = getAtom("CD1");
            } else if (name.equals("ASN") || name.equals("ASP")) {
                atoms[0] = getAtom("CA");
                atoms[1] = getAtom("CB");
                atoms[2] = getAtom("CG");
                atoms[3] = getAtom("OD1");
            } else if (name.equals("GLN") || name.equals("GLU") || name.equals("LYS") || name.equals("ARG")) {
                atoms[0] = getAtom("CA");
                atoms[1] = getAtom("CB");
                atoms[2] = getAtom("CG");
                atoms[3] = getAtom("CD");
            } else {
                return null;
            }
            return Atom.calcDihedral(atoms, structureNum);
        }
    }

    public Double calcPhi() {
        return calcPhi(0);
    }

    public Double calcPhi(int structureNum) {
        Residue residueP = previous;
        Double dihedral = null;
        if (residueP != null) {
            Atom atoms[] = new Atom[4];
            atoms[0] = residueP.getAtom("C");
            atoms[1] = getAtom("N");
            atoms[2] = getAtom("CA");
            atoms[3] = getAtom("C");
            dihedral = Atom.calcDihedral(atoms, structureNum);
        }
        return dihedral;
    }

    public Double calcPsi() {
        return calcPsi(0);
    }

    public Double calcPsi(int structureNum) {
        Residue residueS = next;
        Double dihedral = null;
        if (residueS != null) {
            Atom atoms[] = new Atom[4];
            atoms[0] = getAtom("N");
            atoms[1] = getAtom("CA");
            atoms[2] = getAtom("C");
            atoms[3] = residueS.getAtom("N");
            dihedral = Atom.calcDihedral(atoms, structureNum);
        }
        return dihedral;
    }

}
