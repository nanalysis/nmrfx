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
import org.nmrfx.structure.chemistry.miner.IBond;

public class Residue extends Compound {

    public Residue previous = null;
    public Residue next = null;
    public Polymer polymer;
    private char oneLetter = 0;
    private boolean standard = false;
    static Map standardResSet = new TreeMap();
    Map<String, Atom[]> pseudoMap = new HashMap<String, Atom[]>();
    private final static Map<String, ArrayList<Double>> artAminoAcid = new HashMap<>();
    private final static Map<String, ArrayList<Double>> artNucleicAcid = new HashMap<>();

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

        // Following are dihedral values, valence values and distances respectively
        ArrayList<Double> tmp;

        tmp = new ArrayList<>();
        tmp.add(-120.0);
        tmp.add(114.00);
        tmp.add(1.32);
        artAminoAcid.put("N", tmp);

        tmp = new ArrayList<>();
        tmp.add(-120.0);
        tmp.add(114.00);
        tmp.add(1.32);
        artAminoAcid.put("N", tmp);

        tmp = new ArrayList<>();
        tmp.add(180.0);
        tmp.add(123.0);
        artAminoAcid.put("CA", tmp);

        tmp = new ArrayList<>();
        tmp.add(180.0);
        tmp.add(123.00);
        artAminoAcid.put("H", tmp);

        //Special case, both are dih values but first is with ring 2nd without
        tmp = new ArrayList<>();
        tmp.add(-360.0);
        tmp.add((double) 'H');
        tmp.add(-65.5);
        tmp.add(120.0);
        artAminoAcid.put("C", tmp);

        tmp = new ArrayList<>();
        tmp.add(-179.1);
        artAminoAcid.put("CB", tmp);

        tmp = new ArrayList<>();
        tmp.add(18.6);
        tmp.add(123.0);
        artAminoAcid.put("CD", tmp);

        tmp = new ArrayList<>();
        tmp.add(-161.9);
        artAminoAcid.put("CG", tmp);

        tmp = new ArrayList<>();
        tmp.add(-61.5);
        artAminoAcid.put("HD3", tmp);

        tmp = new ArrayList<>();
        tmp.add(120.0);
        artAminoAcid.put("HD2", tmp);

        tmp = new ArrayList<>();
        tmp.add(-120.0);
        artAminoAcid.put("HA", tmp);

        tmp = new ArrayList<>();
        tmp.add(120.58);
        tmp.add(1.60);
        artAminoAcid.put("P", tmp);

        tmp = new ArrayList<>();
        tmp.add(120.00);
        artAminoAcid.put("Pc", tmp);

        tmp = new ArrayList<>();
        tmp.add(109.68);
        artAminoAcid.put("OP1", tmp);

        tmp = new ArrayList<>();
        tmp.add(109.68);
        artAminoAcid.put("OP2", tmp);

        tmp = new ArrayList<>();
        tmp.add(104.38);
        artAminoAcid.put("O5'", tmp);

        /*
        artAminoAcid.put("N", Arrays.asList(-120.0, 114.00, 1.32));
        artAminoAcid.put("CA", Arrays.asList(180.0, 123));
        artAminoAcid.put("H", Arrays.asList(0.0, 123.00));

        //Special case, both are dih values but first is with ring 2nd without
        artAminoAcid.put("C", Arrays.asList(-360, (float) 'H', -65.5, 120.0));

        artAminoAcid.put("CB", Arrays.asList(-121.5));
        artAminoAcid.put("CD", Arrays.asList(18.6, 123.0));
        artAminoAcid.put("HD3", Arrays.asList(-61.5));
        artAminoAcid.put("HD2", Arrays.asList(120.0));

        artNucleicAcid.put("P", Arrays.asList(120.58, 1.60));
        artNucleicAcid.put("Pc", Arrays.asList(120.00));
        artNucleicAcid.put("OP1", Arrays.asList(109.68));
        artNucleicAcid.put("OP2", Arrays.asList(109.68));
        artNucleicAcid.put("O5'", Arrays.asList(104.38));*/
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

    public Polymer getPolymer() {
        return polymer;
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

    public Atom getFirstBackBoneAtom() {
        String pType = polymer.getPolymerType(); // 'polypeptide' or 'nucleicacid'
        String searchString = pType.equals("polypeptide") ? "N" : "P";
        Atom atom = this.getAtom(searchString);
        return atom;
    }

    public Atom getLastBackBoneAtom() {
        String pType = polymer.getPolymerType();
        String searchString = pType.equals("polypeptide") ? "C" : "O3'";
        Atom atom = this.getAtom(searchString);
        return atom;
    }

    private void alterAtomValue(Atom atom, double value, int counter) {
        if (counter < 2) {
            value *= (Math.PI / 180.0);
        }
        switch (counter) {
            case 0:
                System.out.println(atom.getShortName() + " dih " + value + " in deg: " + value * (180.0 / Math.PI));
                atom.dihedralAngle = (float) value;
                break;
            case 1:
                System.out.println(atom.getShortName() + " val " + value + " in deg: " + value * (180.0 / Math.PI));

                atom.valanceAngle = (float) value;
                break;
            case 2:
                atom.bondLength = (float) value;
                System.out.println(atom.getShortName() + " dis " + value);

                break;
        }
    }

    public void removeConnectors() {
        List<Atom> removeAtoms = new ArrayList<>();
        for (Atom atom : atoms) {
            if (atom.getName().endsWith("X")) {
                removeAtoms.add(atom);
            }
        }
        System.out.println(getName());
        for (Atom atom : atoms) {
            if (!atom.getName().endsWith("X")) {
                System.out.println(atom.getName() + " " + previous + " " + atom.parent);
                if ((previous != null) && (atom.parent != null)) {
                    String parName = atom.parent.getName();
                    System.out.println("look for " + parName);

                    if ((parName.endsWith("X"))) {
                        System.out.println("found for " + parName);
                        parName = parName.substring(0, parName.length() - 1);
                        Atom parent = previous.getAtom(parName);
                        if (parent == null) {
                            System.out.println("Doesn't exist " + parName);
                        } else {
                          //  atom.parent = parent;
                        }
                    }
                }
            }
        }
        for (Atom atom : removeAtoms) {
            List<IBond> rBonds = atom.getBonds();
            atom.removeBonds();
            for (IBond rBond : rBonds) {
                removeBond((Bond) rBond);
            }
        }

        for (Atom atom : removeAtoms) {
            System.out.println("remove " + atom.getShortName());
            removeAtom(atom);
        }
        for (Bond bond:bonds) {
            System.out.println(bond.toString());
        }
        for (Atom atom: atoms) {
            System.out.println("atom " + atom.getShortName());
        }
        molecule.updateBondArray();

    }

    public void adjustBorderingAtoms() {
        String pType = polymer.getPolymerType();
        Map<String, ArrayList<Double>> borderingAtomValues = pType.equals("polypeptide") ? artAminoAcid : artNucleicAcid;
        Set<String> atomNames = borderingAtomValues.keySet();
        int counter;
        for (String searchString : atomNames) {
            Atom atom = this.getAtom(searchString);
            if (atom != null) {
                System.out.println(atom.getShortName());
                List<Double> atomValues = (ArrayList) borderingAtomValues.get(searchString).clone();
                counter = 0;
                if (atomValues.get(0) == -360.0) { // Has dependency
                    String dependencySearch = Character.toString((char) atomValues.get(1).floatValue());
                    atomValues.remove(0);
                    atomValues.remove(0);
                    Atom dependencyAtom = this.getAtom(dependencySearch);
                    while (atomValues.size() > 0) {
                        double value = dependencyAtom == null ? atomValues.get(0) : atomValues.get(1);
                        atomValues.remove(0);
                        atomValues.remove(0);
                        alterAtomValue(atom, value, counter);
                        counter += 1;
                    }
                } else {
                    while (atomValues.size() > 0) {
                        double value = atomValues.get(0);
                        alterAtomValue(atom, value, counter);
                        atomValues.remove(0);
                        counter += 1;
                    }
                }
            }
        }
    }
}
