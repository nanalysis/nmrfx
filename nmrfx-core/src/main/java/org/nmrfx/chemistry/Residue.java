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

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.annotations.PluginAPI;

import java.util.*;

import static org.nmrfx.chemistry.io.PDBFile.isIUPACMode;

@PluginAPI("ring")
public class Residue extends Compound {
    private static final double DELTA_V3 = 121.8084 * Math.PI / 180.0;
    private static final Map<String, String> STANDARD_RES_SET = new TreeMap<>();
    private static final String[] COMPLIANT_AMINO_ACID = {"C", "CA", "N"};
    private static final String[] COMPLIANT_NUCLEIC_ACID = {"C5'", "O5'", "P"};
    private static final Map<String, String> PSEUDO_MAP = new HashMap<>();

    public Residue previous = null;
    public Residue next = null;
    public Polymer polymer;
    private char oneLetter = 0;
    private boolean standard = false;
    Map<String, Atom[]> pseudoMap = new HashMap<>();
    private String lastBackBoneAtomName = null;
    private String firstBackBoneAtomName = null;
    public Residue pairedTo = null;
    public SecondaryStructure secStruct = null;
    boolean libraryMode = false;

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
            STANDARD_RES_SET.put(standardResidues[i], standardResidues[i + 1]);
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

    public enum RES_POSITION {
        START,
        MIDDLE,
        END;
    }


    public enum AngleAtoms {
        DELTAP(new String[]{"C5'", "C4'", "C3'", "O3'"}, 4),
        EPSILON(new String[]{"C4'", "C3'", "O3'", "P"}, 3),
        ZETA(new String[]{"C3'", "O3'", "P", "O5'"}, 2),
        ALPHA(new String[]{"O3'", "P", "O5'", "C5'"}, 1),
        BETA(new String[]{"P", "O5'", "C5'", "C4'"}, 0),
        GAMMA(new String[]{"O5'", "C5'", "C4'", "C3'"},0),
        DELTA(new String[]{"C5'", "C4'", "C3'", "O3'"}, 0),
        NU2(new String[]{"C1'", "C2'", "C3'", "C4'"}, 0);
        private String[] atomNames;
        private int nPrevious;
        private Residue residue;

        AngleAtoms(String[] atomNames, int nPrevious) {
            this.atomNames = atomNames;
            this.nPrevious = nPrevious;
        }

        private Atom[] getAtoms() {
            Atom[] atoms = new Atom[4];
            Residue previous = residue.getPrevious();
            int i = 0;
            for (String atomName : atomNames) {
                if (i < nPrevious) {
                    if (previous == null) {
                        return null;
                    }
                    atoms[i] = previous.getAtom(atomName);
                } else {
                    atoms[i] = residue.getAtom(atomName);
                }
                if (atoms[i] == null) {
                    return null;
                }
                i++;
            }
            return atoms;
        }

        public Double calcAngle(Residue residue) {
            this.residue = residue;
            Atom[] atoms = getAtoms();
            int structureNum = 0;
            try {
                return atoms != null ? Atom.calcDihedral(atoms, structureNum) : null;
            } catch (IllegalArgumentException iAE) {
                return null;
            }
        }
    }


    public Residue(String number, String name) {
        this.number = number;
        super.name = name;
        super.label = name;
        if (STANDARD_RES_SET.containsKey(name.toLowerCase())) {
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
        if (STANDARD_RES_SET.containsKey(name.toLowerCase())) {
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

    @Override
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
                    switch (lName) {
                        case "hn":
                            atom = (Atom) atomMap.get("h1");
                            break;
                        case "h":
                            atom = (Atom) atomMap.get("h1");
                            break;
                        case "ht1":
                            atom = (Atom) atomMap.get("h1");
                            break;
                        case "ht2":
                            atom = (Atom) atomMap.get("h2");
                            break;
                        case "ht3":
                            atom = (Atom) atomMap.get("h3");
                            break;
                        default:
                            break;
                    }
                } else if (lName.equals("hn")) {
                    atom = (Atom) atomMap.get("h");
                } else if (lName.equals("h")) {
                    atom = (Atom) atomMap.get("hn");
                }
            } else if (lName.charAt(0) == 'o') {
                if (polymer.isCapped() && (polymer.getLastResidue() == this)) {
                    switch (lName) {
                        case "o":
                            atom = (Atom) atomMap.get("o'");
                            break;
                        case "ot1":
                            atom = (Atom) atomMap.get("o'");
                            break;
                        case "o1":
                            atom = (Atom) atomMap.get("o'");
                            break;
                        case "ot2":
                            atom = (Atom) atomMap.get("o''");
                            break;
                        case "oxt":
                            atom = (Atom) atomMap.get("o''");
                            break;
                        case "o2":
                            atom = (Atom) atomMap.get("o''");
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        return atom;
    }

    public void libraryMode(boolean value) {
        this.libraryMode = value;
    }

    public boolean libraryMode() {
        return libraryMode;
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
                oneLetter = ((String) STANDARD_RES_SET.get(name.toLowerCase())).toUpperCase().charAt(0);
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

    public SecondaryStructure getSecondaryStructure() {
        return secStruct;
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

    @Override
    public void addBond(final Bond bond) {
        super.addBond(bond);
        polymer.addBond(bond);
    }

    @Override
    public void removeBond(final Bond bond) {
        super.removeBond(bond);
        polymer.removeBond(bond);
    }

    public Double calcNu2() {
        return calcNu2(0);
    }

    public Double calcNu2(int structureNum) {
        Atom[] atoms = getNu2Atoms();
        try {
            return atoms != null ? Atom.calcDihedral(atoms, structureNum) : null;
        } catch (IllegalArgumentException iAE) {
            return null;
        }
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

    public Double calcNu3() {
        return calcNu3(0);
    }

    public Double calcNu3(int structureNum) {
        Atom[] atoms = getNu3Atoms();
        try {
            return atoms != null ? Atom.calcDihedral(atoms, structureNum) - DELTA_V3 : null;
        } catch (IllegalArgumentException iAE) {
            return null;
        }
    }

    public Atom[] getNu3Atoms() {
        if (name.equals("U") || name.equals("C") || name.equals("G") || name.equals("A")) {
            Atom[] atoms = new Atom[4];
            atoms[0] = getAtom("C5'");
            atoms[1] = getAtom("C4'");
            atoms[2] = getAtom("C3'");
            atoms[3] = getAtom("O3'");
            return atoms;
        } else {
            return null;
        }

    }

    public Double calcChi() {
        return calcChi(0);
    }

    private Atom[] getAtoms(String... names) {
        Atom[] atoms = new Atom[names.length];
        for (int i = 0; i < names.length; i++) {
            atoms[i] = getAtom(names[i]);
        }
        return atoms;
    }

    public Atom[] getChiAtoms() {
        switch (name) {
            case "ALA":
            case "GLY":
                return null;
            case "U":
            case "C": {
                return getAtoms("O4'", "C1'", "N1", "C2");
            }
            case "G":
            case "A": {
                return getAtoms("O4'", "C1'", "N9", "C4");
            }
            default: {
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
    }

    public Double calcChi(int structureNum) {
        Atom[] atoms = getChiAtoms();
        try {
            return atoms != null ? Atom.calcDihedral(atoms, structureNum) : null;
        } catch (IllegalArgumentException iAE) {
            return null;
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
            switch (name) {
                case "PHE":
                case "TRP":
                case "TYR":
                    atoms[0] = getAtom("CA");
                    atoms[1] = getAtom("CB");
                    atoms[2] = getAtom("CG");
                    atoms[3] = getAtom("CD1");
                    break;
                case "HIS":
                    atoms[0] = getAtom("CA");
                    atoms[1] = getAtom("CB");
                    atoms[2] = getAtom("CG");
                    atoms[3] = getAtom("CD2");
                    break;
                case "MET":
                    atoms[0] = getAtom("CA");
                    atoms[1] = getAtom("CB");
                    atoms[2] = getAtom("CG");
                    atoms[3] = getAtom("SD");
                    break;
                case "ILE":
                    atoms[0] = getAtom("CA");
                    atoms[1] = getAtom("CB");
                    atoms[2] = getAtom("CG1");
                    atoms[3] = getAtom("CD1");
                    break;
                case "LEU":
                    atoms[0] = getAtom("CA");
                    atoms[1] = getAtom("CB");
                    atoms[2] = getAtom("CG");
                    atoms[3] = getAtom("CD1");
                    break;
                case "ASN":
                case "ASP":
                    atoms[0] = getAtom("CA");
                    atoms[1] = getAtom("CB");
                    atoms[2] = getAtom("CG");
                    atoms[3] = getAtom("OD1");
                    break;
                case "GLN":
                case "GLU":
                case "LYS":
                case "ARG":
                    atoms[0] = getAtom("CA");
                    atoms[1] = getAtom("CB");
                    atoms[2] = getAtom("CG");
                    atoms[3] = getAtom("CD");
                    break;
                default:
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
            atom = this.getAtom("OP1");
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
        removeAtoms.forEach((atom) -> {
            List<IBond> rBonds = atom.getBonds();
            atom.removeBonds();
            rBonds.forEach((rBond) -> {
                removeBond((Bond) rBond);
            });
        });

        removeAtoms.forEach((atom) -> {
            removeAtom(atom);
        });
        molecule.updateBondArray();
    }

    public boolean isCompliant() {
        /**
         * isCompliant tests if a nonstandard residue has atoms needed to
         * automatically add in temporary previous atoms.
         */

        String pType = polymer.getPolymerType(); // 'polypeptide' or 'nucleicacid'
        String[] atomStrings = pType.equals("polypeptide") ? COMPLIANT_AMINO_ACID : COMPLIANT_NUCLEIC_ACID;
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
        String[] compliantArray = isProtein ? COMPLIANT_AMINO_ACID : COMPLIANT_NUCLEIC_ACID;
        Point3[] pts = new Point3[4];
        for (int i = 0; i < compliantArray.length; i++) {
            pts[i] = this.getAtom(compliantArray[i]).getPoint();
        }

        pts[3] = getNBoundPoint();

        float refAngle = isProtein ? 180.0f : -116.4f;
        refAngle = 180.0f;
        refAngle *= (Math.PI / 180.0);
        float dih = (float) (AtomGeometry.calcDihedral(pts[0], pts[1], pts[2], pts[3]) + refAngle);

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
                thirdAtom.valanceAngle = (float) (120.0 * Math.PI / 180.0);
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

    public String getSSType() {
        String type;
        if (secStruct != null) {
            type = secStruct.getName();
        } else {
            type = "";
        }
        return type;
    }

    public void toDStereo() {
        Atom atomCB = getAtom("CB");
        Atom atomHA = getAtom("HA");
        if ((atomCB != null) && (atomHA != null)) {
            atomCB.setDihedral(-atomCB.getDihedral());
            atomHA.setDihedral(-atomHA.getDihedral());
        }
    }

    @Override
    public String toString() {
        return polymer.getName() + ":" + getName() + getNumber();
    }

    /**
     * Converts sequence information to a String in NEF format.
     *
     * @param idx  int. The line index.
     * @param link String. Linkage (e.g. start, end, single).
     * @return String in NEF format.
     */
    @Override
    public String toNEFSequenceString(int idx, String link) {
        //chain ID
        char chainID = ' ';
        //sequence code
        int num = Integer.parseInt(this.getNumber());
        String polymerName = this.polymer.getName();
        chainID = polymerName.charAt(0);

        //residue name
        String resName = this.name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }

        //residue variant
        String resVar = this.label;

        return String.format("%8d %7s %7d %9s %-14s %-7s", idx, chainID, num, resName, link, resVar);
    }

    /**
     * Converts sequence information to a String in mmCIF format.
     *
     * @param pdb boolean. Whether to write lines in PDBX format.
     * @return String in mmCIF format.
     */
    public String toMMCifSequenceString(boolean pdb) {
        //chain ID
        String polymerName = this.polymer.getName();
        char chainID = polymerName.charAt(0);

        //entity ID
        int entityIDNum = this.polymer.getIDNum();

        //seq ID
        int seqID = this.getIDNum();

        //residue name
        String resName = this.name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }

        //hetero
        String hetero = this.label;
        if (hetero.equals(this.name)) {
            hetero = "n";
        }
        if (hetero.length() > 1) {
            hetero = hetero.substring(0, 1);
        }

        if (pdb) {
            return String.format("%-2s %-2d %-3d %-4s %-3d %-3d %-3d %-4s %-4s %-2s %-2s %-2s", chainID, entityIDNum, seqID, resName, seqID, seqID, seqID, resName, resName, chainID, ".", hetero);
        } else {
            return String.format("%-2d %-3d %-4s %-2s", entityIDNum, seqID, resName, hetero);
        }
    }

    /**
     * Convert structure configuration information to a String in mmCIF format.
     *
     * @param idx     int. The line index.
     * @param lastRes Residue. The last residue in the sequence.
     * @return String in mmCIF format.
     */
    public String toMMCifStructConfString(int idx, Residue lastRes) {
        //type id
        String typeID = "HELX_P";

        //id
        String id = typeID + String.valueOf(idx);

        //first chain ID
        String polymerName = this.polymer.getName();
        char chainID = polymerName.charAt(0);

        //first entity ID
        int entityIDNum = chainID - 'A' + 1;

        //first seq ID
        int seqID = this.getIDNum();

        //first residue name
        String resName = this.name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }

        //last chain ID
        String polymerName1 = lastRes.polymer.getName();
        char chainID1 = polymerName1.charAt(0);

        //last seq ID
        int seqID1 = lastRes.getIDNum();

        //last residue name
        String resName1 = lastRes.name;
        if (resName1.length() > 3) {
            resName1 = resName1.substring(0, 3);
        }

        //first PDB ins code
        String insCode = "?";

        //last PDB ins code
        String insCode1 = "?";

        //details
        String details = "?";

        //length
        int length = seqID1 - seqID + 1;

        return String.format("%-6s %-6s %-1d %-3s %-1s %-2d %-1s %-3s %-1s %-2d %-1s %-3s %-1s %-2d %-3s %-1s %-2d %-1d %-1s %-2s", typeID, id, idx, resName, chainID, seqID, insCode, resName1, chainID1, seqID1, insCode1, resName, chainID, seqID, resName1, chainID1, seqID1, entityIDNum, details, length);
    }

    /**
     * Convert sheet information to a String in mmCIF format.
     *
     * @param idx     int. The line index.
     * @param lastRes Residue. The last residue in the sequence.
     * @return
     */
    public String toMMCifSheetRangeString(int idx, Residue lastRes) {
        //first chain ID
        String polymerName = this.polymer.getName();
        char chainID = polymerName.charAt(0);

        //first entity ID
        int entityIDNum = chainID - 'A' + 1;

        //first seq ID
        int seqID = this.getIDNum();

        //first residue name
        String resName = this.name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }

        //last chain ID
        String polymerName1 = lastRes.polymer.getName();
        char chainID1 = polymerName1.charAt(0);

        //last seq ID
        int seqID1 = lastRes.getIDNum();

        //last residue name
        String resName1 = lastRes.name;
        if (resName1.length() > 3) {
            resName1 = resName1.substring(0, 3);
        }

        //first PDB ins code
        String insCode = "?";

        //last PDB ins code
        String insCode1 = "?";

        return String.format("%-2s %-1d %-3s %-1s %-2d %-1s %-3s %-1s %-2d %-1s %-3s %-1s %-2d %-3s %-1s %-2d", chainID, idx, resName, chainID, seqID, insCode, resName1, chainID1, seqID1, insCode1, resName, chainID, seqID, resName1, chainID1, seqID1);
    }

    /**
     * Convert torsion angle information to a String in mmCIF format.
     *
     * @param angles      double[]. List of the torsion angles: [phi, psi].
     * @param idx         int. The line index.
     * @param pdbModelNum int. The PDB model number.
     * @return String in mmCIF format.
     */
    public String toMMCifTorsionString(double[] angles, int idx, int pdbModelNum) {

        StringBuilder sBuilder = new StringBuilder();
        if (this != null) {

            //index
            sBuilder.append(String.format("%-4d", idx));

            //PDB model num
            sBuilder.append(String.format("%-4d", pdbModelNum));

            // residue name 
            String resName = this.name;
            sBuilder.append(String.format("%-6s", resName));

            // chain code 
            String polymerName = this.polymer.getName();
            char chainID = polymerName.charAt(0);
            sBuilder.append(String.format("%-4s", chainID));

            // sequence code 
            int seqCode = this.getIDNum();
            sBuilder.append(String.format("%-4d", seqCode));

            // PDB ins code
            String code = "?"; //fixme need to get from file, not hard-code
            sBuilder.append(String.format("%-2s", code));

            // label alt id
            String altID = "?"; //fixme need to get from file, not hard-code
            sBuilder.append(String.format("%-2s", altID));

            // phi
            double phi = angles[0];
            sBuilder.append(String.format("%-8.2f", Math.toDegrees(phi)));

            // psi
            double psi = angles[1];
            sBuilder.append(String.format("%-8.2f", Math.toDegrees(psi)));

        }

        return sBuilder.toString();
    }

    public void setBackboneRotationActive(boolean state) {
        List<Atom> atoms = this.getAtoms();
        for (Atom iAtom : atoms) {
            if (iAtom.isBackbone()) {
                iAtom.rotActive = state;
            }
        }
    }
}
