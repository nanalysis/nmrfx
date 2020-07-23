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
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.energy.AtomMath;
import static org.nmrfx.structure.chemistry.io.PDBFile.isIUPACMode;
import org.nmrfx.structure.chemistry.miner.IBond;

public class Residue extends Compound {

    public Residue previous = null;
    public Residue next = null;
    public Polymer polymer;
    private char oneLetter = 0;
    private boolean standard = false;
    static Map standardResSet = new TreeMap();
    Map<String, Atom[]> pseudoMap = new HashMap<String, Atom[]>();
    private final static String[] compliantAminoAcid = {"C", "CA", "N"};
    private final static String[] compliantNucleicAcid = {"C5'", "O5'", "P"};
    private String lastBackBoneAtomName = null;
    private String firstBackBoneAtomName = null;
    public Residue pairedTo = null;
    public SecondaryStructure secStruct = null;
    public final static Map<String, String> PSEUDO_MAP = new HashMap<>();

    static {
        String[] standardResidues = {
            "ala", "a", "arg", "r", "asn", "n", "asp", "d", "cys", "c", "gln", "q", "glu", "e",
            "gly", "g", "his", "h", "ile", "i", "leu", "l", "lys", "k", "met", "m", "phe", "f",
            "pro", "p", "ser", "s", "thr", "t", "trp", "w", "tyr", "y", "val", "v", "mse", "m",
            "dade", "a", "dcyt", "c", "dgua", "g", "dthy", "t",
            "da", "a", "dc", "c", "dg", "g", "dt", "t",
            "rade", "a", "rcyt", "c", "rgua", "g", "rura", "u",
            "ra", "a", "rc", "c", "rg", "g", "ru", "u",
            "a", "a", "c", "c", "g", "g", "u", "u"
        };
        for (int i = 0; i < standardResidues.length; i += 2) {
            standardResSet.put(standardResidues[i], standardResidues[i + 1]);
        }
        PSEUDO_MAP.put("ALA:QB", "MB");
        PSEUDO_MAP.put("ILE:QG2", "MG");
        PSEUDO_MAP.put("ILE:QD1", "MD");
        PSEUDO_MAP.put("ILE:QG1", "QG");
        PSEUDO_MAP.put("LEU:QD1", "MD1");
        PSEUDO_MAP.put("LEU:QD2", "MD2");
        PSEUDO_MAP.put("LEU:QQD", "QD");
        PSEUDO_MAP.put("MET:QE", "ME");
        PSEUDO_MAP.put("THR:QG2", "MG");
        PSEUDO_MAP.put("VAL:QG1", "MG1");
        PSEUDO_MAP.put("VAL:QG2", "MG2");
        PSEUDO_MAP.put("VAL:QQG", "QG");

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

    public Residue(String number, String name, String variant) {
        this.number = number;
        super.name = name;
        super.label = variant;
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
                if (polymer.isCapped() && (polymer.getFirstResidue() == this)) {
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
                if (polymer.isCapped() && (polymer.getLastResidue() == this)) {
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

    public boolean isStandard() {
        return standard;
    }

    public void setNonStandard() {
        standard = false;
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

    public Residue getPrevious() {
        return previous;
    }

    public Residue getNext() {
        return next;
    }

    @Override
    public int getIDNum() {
        return iRes + 1;
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
        if (!pseudoMap.containsKey(pseudoName)) {
            String testName = name.toUpperCase() + ":" + pseudoName;
            if (PSEUDO_MAP.containsKey(testName)) {
                pseudoName = PSEUDO_MAP.get(testName);
            }
        }
        return pseudoName != null ? pseudoMap.get(pseudoName) : null;
    }

    public void addBond(final Bond bond) {
        super.addBond(bond);
        polymer.addBond(bond);
    }

    public void removeBond(final Bond bond) {
        super.removeBond(bond);
        polymer.removeBond(bond);
    }

    public Double calcNu2() {
        return calcNu2(0);
    }

    public Double calcNu2(int structureNum) {
        Atom[] atoms = getNu2Atoms();
        return atoms != null ? Atom.calcDihedral(atoms, structureNum) : null;
    }

    public Atom[] getNu2Atoms() {
        if (name.equals("U") || name.equals("C") || name.equals("G") || name.equals("A")) {
            Atom[] atoms = new Atom[4];
            atoms[0] = getAtom("C1'");
            atoms[1] = getAtom("C2'");
            atoms[2] = getAtom("C3'");
            atoms[3] = getAtom("C4'");
            return atoms;
        } else {
            return null;
        }

    }

    public Double calcChi() {
        return calcChi(0);
    }

    public Atom[] getChiAtoms() {
        if (name.equals("ALA") || name.equals("GLY")) {
            return null;
        } else if (name.equals("U") || name.equals("C")) {
            Atom[] atoms = new Atom[4];
            atoms[0] = getAtom("O4'");
            atoms[1] = getAtom("C1'");
            atoms[2] = getAtom("N1");
            atoms[3] = getAtom("C2");
            return atoms;
        } else if (name.equals("G") || name.equals("A")) {
            Atom[] atoms = new Atom[4];
            atoms[0] = getAtom("O4'");
            atoms[1] = getAtom("C1'");
            atoms[2] = getAtom("N9");
            atoms[3] = getAtom("C4");
            return atoms;
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
            return atoms;
        }
    }

    public Double calcChi(int structureNum) {
        Atom[] atoms = getChiAtoms();

        return atoms != null ? Atom.calcDihedral(atoms, structureNum) : null;

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

    public void setFirstBackBoneAtom(String name) {
        firstBackBoneAtomName = name;
    }

    public void setLastBackBoneAtom(String name) {
        lastBackBoneAtomName = name;
    }

    public Atom getFirstBackBoneAtom() {
        if (firstBackBoneAtomName != null) {
            return this.getAtom(firstBackBoneAtomName);
        }
        String pType = polymer.getPolymerType(); // 'polypeptide' or 'nucleicacid'
        String searchString = pType.contains("polypeptide") ? "N" : "HO5'";

        Atom atom;
        if (pType.contains("polypeptide")) {
            atom = this.getAtom("N");
        } else {
            atom = this.getAtom("P");
            if (atom == null) {
                atom = this.getAtom("HO5'");
            }
        }
        return atom;
    }

    public Atom getLastBackBoneAtom() {
        if (lastBackBoneAtomName != null) {
            return this.getAtom(lastBackBoneAtomName);
        }
        String pType = polymer.getPolymerType();
        String searchString = pType.equals("polypeptide") ? "C" : "O3'";
        Atom atom = this.getAtom(searchString);
        return atom;
    }

    public void removeConnectors() {
        List<Atom> removeAtoms = new ArrayList<>();
        for (Atom atom : atoms) {
            if (atom.getName().endsWith("X")) {
                removeAtoms.add(atom);
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
            removeAtom(atom);
        }
        molecule.updateBondArray();
    }

    public boolean isCompliant() {
        /**
         * isCompliant tests if a nonstandard residue has atoms needed to
         * automatically add in temporary previous atoms.
         */

        String pType = polymer.getPolymerType(); // 'polypeptide' or 'nucleicacid'
        String[] atomStrings = pType.equals("polypeptide") ? compliantAminoAcid : compliantNucleicAcid;
        for (String atomString : atomStrings) {
            Atom atom = this.getAtom(atomString);
            if (atom == null) {
                return false;
            }
        }
        return true;

    }

    Point3 getNBoundPoint() {
        boolean isProtein = this.polymer.getPolymerType().equals("polypeptide");
        Atom atom = this.getFirstBackBoneAtom();
        String preferedLookup = isProtein ? "H" : "OP1";
        String[] exclusionList = new String[2];
        exclusionList[0] = isProtein ? "CA" : "O5'";
        exclusionList[1] = isProtein ? "C" : "O3'";
        Vector3D vec = new Vector3D(0, 0, 0);
        Vector3D vec0 = atom.getPoint();

        List<Atom> cAtoms = atom.getConnected();
        int count = 0;
        for (Atom cAtom : cAtoms) {
            if (!cAtom.getName().equals(exclusionList[0]) && !cAtom.getName().equals(exclusionList[1])) {
                Vector3D vec1 = cAtom.getPoint();
                vec1 = vec1.subtract(vec0).normalize().add(vec0);
                vec = vec.add(vec1);
                count++;
            }
        }
        double scalarMultiplier = 1.0 / count;
        return new Point3(vec.scalarMultiply(scalarMultiplier));
    }

    public void addConnectors() {
        /**
         * addConnectors adds two temporary atoms, X and XX, to represent atoms
         * from the previous residue.
         *
         */
        boolean isProtein = this.polymer.getPolymerType().equals("polypeptide");
        String[] compliantArray = isProtein ? compliantAminoAcid : compliantNucleicAcid;
        Point3[] pts = new Point3[4];
        for (int i = 0; i < compliantArray.length; i++) {
            pts[i] = this.getAtom(compliantArray[i]).getPoint();
        }

        pts[3] = getNBoundPoint();

        float refAngle = isProtein ? 180.0f : -116.4f;
        refAngle = 180.0f;
        refAngle *= (Math.PI / 180.0);
        float dih = (float) (AtomMath.calcDihedral(pts[0], pts[1], pts[2], pts[3]) + refAngle);

        float val = isProtein ? 123.0f : 104.3845f;  // comes from prf for CA/O5'
        val *= (Math.PI / 180.0);
        float dis = isProtein ? 1.32f : 1.6006f; // comes from prf for N/P
        Atom aXX = this.getFirstBackBoneAtom().add("XX", "X", Order.SINGLE);
        aXX.bndCos = (float) (dis * FastMath.cos(Math.PI - val));
        aXX.bndSin = (float) (dis * FastMath.sin(Math.PI - val));
        aXX.bondLength = dis;
        Coordinates coords = new Coordinates(pts[0], pts[1], pts[2]);
        coords.setup();
        Point3 pt = coords.calculate(dih, aXX.bndCos, aXX.bndSin);

        aXX.setPoint(pt);
        aXX.bondLength = 1.53f; // comes from prf for C/

        //for 1st atom X, representing 2nd to last atom of backbone of previous residue
        dih = isProtein ? 180.0f : -71.584f; // comes from prf for CA/O5'
        val = isProtein ? 114.0f : 120.577f; // comes from prf for N/P
        dih *= (Math.PI / 180.0);
        val *= (Math.PI / 180.0);
        dis = isProtein ? 1.53f : 1.4113f; //comes from prf for C/O3'
        Atom aX = aXX.add("X", "X", Order.SINGLE);
        aX.bndCos = (float) (dis * FastMath.cos(Math.PI - val));
        aX.bndSin = (float) (dis * FastMath.sin(Math.PI - val));
        coords = new Coordinates(pts[1], pts[2], pt);
        coords.setup();
        pt = coords.calculate(dih, aX.bndCos, aX.bndSin);
        aX.setPoint(pt);

    }

    public void capFirstResidue(String resVariant) {
        List<Atom> firstResidueAtoms = getAtoms();
        if (firstResidueAtoms.size() > 2) {
            Atom firstAtom = getAtoms().get(0);
            Atom secondAtom = getAtoms().get(1);
            Atom thirdAtom = getAtoms().get(2);
            if (firstAtom.getName().equals("N") && (secondAtom.getName().equals("H") || secondAtom.getName().equals("HN"))) {
                secondAtom.remove();
                String newRoot = "H";
                if (!isIUPACMode()) {
                    newRoot = "HT";
                }
                thirdAtom.valanceAngle = (float) (180.0 * Math.PI / 180.0);
                thirdAtom.dihedralAngle = (float) (0.0 * Math.PI / 180.0);
                Atom newAtom = firstAtom.add(newRoot + "1", "H", Order.SINGLE);
                newAtom.setType("H");
                newAtom.bondLength = 1.08f;
                newAtom.dihedralAngle = (float) (109.0 * Math.PI / 180.0);
                newAtom.valanceAngle = (float) (60.0 * Math.PI / 180.0);
                newAtom = firstAtom.add(newRoot + "2", "H", Order.SINGLE);
                newAtom.setType("H");
                newAtom.bondLength = 1.08f;
                newAtom.dihedralAngle = (float) (109.0 * Math.PI / 180.0);
                newAtom.valanceAngle = (float) (60.0 * Math.PI / 180.0);
                if (!resVariant.contains("-H3")) {
                    newAtom = firstAtom.add(newRoot + "3", "H", Order.SINGLE);
                    newAtom.setType("H");
                    newAtom.bondLength = 1.08f;
                    newAtom.dihedralAngle = (float) (109.0 * Math.PI / 180.0);
                    newAtom.valanceAngle = (float) (60.0 * Math.PI / 180.0);
                }
            }
            if (firstResidueAtoms.size() > 4) {
                Atom fourthAtom = getAtoms().get(4);
                if (fourthAtom.getName().equals("O5'")) {
                    Atom newAtom = firstAtom.add("OP3", "O", Order.SINGLE);
                    newAtom.setType("O");
                    newAtom.bondLength = 1.48f;
                    newAtom.dihedralAngle = (float) (71.58 * Math.PI / 180.0);
                    newAtom.valanceAngle = (float) (0 * Math.PI / 180.0);
                }
            }
        }
    }

    public void capLastResidue(String resVariant) {
        List<Atom> lastResidueAtoms = getAtoms();
        if (!lastResidueAtoms.isEmpty() && lastResidueAtoms.size() > 1) {
            Atom lastAtom = lastResidueAtoms.get(lastResidueAtoms.size() - 1);
            Atom secondAtom = lastResidueAtoms.get(lastResidueAtoms.size() - 2);
            if (lastAtom.getName().equals("O")) {
                if ((secondAtom.getName().equals("C"))) {
                    lastAtom.remove();
                    String newRoot = "O";
                    if (!isIUPACMode()) {
                        newRoot = "OT";
                    }
                    Atom newAtom;
                    if (!isIUPACMode()) {
                        newAtom = secondAtom.add(newRoot + "2", "O", Order.DOUBLE);
                    } else {
                        newAtom = secondAtom.add(newRoot + "''", "O", Order.DOUBLE);
                    }
                    newAtom.bondLength = 1.24f;
                    newAtom.dihedralAngle = (float) (180.0 * Math.PI / 180.0);
                    newAtom.valanceAngle = (float) (120.0 * Math.PI / 180.0);
                    newAtom.setType("O");

                    if (!isIUPACMode()) {
                        newAtom = secondAtom.add(newRoot + "XT", "O", Order.SINGLE);
                    } else {
                        newAtom = secondAtom.add(newRoot + "'", "O", Order.SINGLE);
                    }
                    newAtom.bondLength = 1.24f;
                    newAtom.valanceAngle = (float) (120.0 * Math.PI / 180.0);
                    newAtom.dihedralAngle = (float) (180.0 * Math.PI / 180.0);
                    newAtom.setType("O");
                    if (resVariant.contains("+HXT")) {
                        Atom newAtomProt = newAtom.add("HXT", "H", Order.SINGLE);
                        newAtomProt.bondLength = 1.00f;
                        newAtomProt.valanceAngle = (float) (110.0 * Math.PI / 180.0);
                        newAtomProt.dihedralAngle = (float) (0.0 * Math.PI / 180.0);
                        newAtomProt.setType("H");
                    }
                }
            } else if (lastAtom.getName().equals("O3'")) {
                Atom newAtom = lastAtom.add("HO3'", "H", Order.SINGLE);
                newAtom.setType("H");
                newAtom.bondLength = 0.98f;
                newAtom.dihedralAngle = (float) (109.0 * Math.PI / 180.0);
                newAtom.valanceAngle = (float) (120.0 * Math.PI / 180.0);
            }
        } else {
            System.out.println("\nUnable to get atoms for " + name);
        }
    }

    public int getBasePairType(Residue residue) {
        int bpCount;
        boolean valid = false;
        List<AllBasePairs> basePairs = new ArrayList<>();
        if (!name.matches("[GCAU]") || !residue.name.matches("[GCAU]")) {
            basePairs = AllBasePairs.getBasePairs();
        } else {
            for (int type = 0; type <= 12; type++) {
                AllBasePairs bp = AllBasePairs.getBasePair(type, name, residue.name);
                if (bp != null) {
                    basePairs.add(bp);
                }
            }
        }
        for (AllBasePairs bp : basePairs) {
            bpCount = 0;
            for (String atomPair : bp.atomPairs) {
                String[] atomPairs = atomPair.split(":");
                String[] atoms0 = atomPairs[0].split("/");
                String[] atoms1 = atomPairs[1].split("/");
                for (String atom1Str : atoms0) {
                    for (String atom2Str : atoms1) {
                        Atom atom1 = getAtom(atom1Str);
                        Atom atom2 = residue.getAtom(atom2Str);
                        if (atom1 != null && atom2 != null) {
                            if (atom1Str.contains("H")) {
                                valid = HydrogenBond.validateRNA(atom1.getSpatialSet(), atom2.getSpatialSet(), 0);
                            } else if (atom2Str.contains("H")) {
                                valid = HydrogenBond.validateRNA(atom2.getSpatialSet(), atom1.getSpatialSet(), 0);
                            }
                            if (valid) {
                                bpCount++;
                            }
                        }
                    }
                }
            }
            if (bpCount == bp.atomPairs.length) {
                return bp.type;
            }
        }
        return 0;
    }

    public String getSSType() {
        String type;
        if (secStruct != null) {
            type = secStruct.getName();
        } else {
            type = "";
        }
        return type;
    }

    public String toString() {
        return polymer.getName() + ":" + getName() + getNumber();
    }

    public String toNEFSequenceString(Molecule molecule, String link) {
        //index and sequence code
        int number = 1;
        //chain ID
        char chainID = ' ';
        number = this.getIDNum();
        String polymerName = this.polymer.getName();
        chainID = polymerName.charAt(0);

        //residue name
        String resName = this.name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }

        //residue variant
        String resVar = this.label;

        return String.format("%8d %7s %7d %9s %-14s %-7s", number, chainID, number, resName, link, resVar);
    }

}
