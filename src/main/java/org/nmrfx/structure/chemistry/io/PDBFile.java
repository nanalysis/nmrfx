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

import org.nmrfx.structure.chemistry.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;
import javax.vecmath.Vector3d;

public class PDBFile {

    static public boolean allowSequenceDiff = true;
    static private boolean iupacMode = true;
    static private String localReslibDir = "";
    static private HashMap<String, String> reslibMap = new HashMap<String, String>();

    static {
        reslibMap.put("IUPAC", "resource:/reslib_iu");
        reslibMap.put("XPLOR", "resource:/reslib");
    }
    Atom connectAtom = null;

    public PDBFile() {
    }

    public static boolean isIUPACMode() {
        return iupacMode;
    }

    public static void setIUPACMode(final boolean newValue) {
        iupacMode = newValue;
    }

    public static void setLocalReslibDir(final String dirName) {
        localReslibDir = dirName;
    }

    public static String getLocalReslibDir() {
        return localReslibDir;
    }

    public static void putReslibDir(final String name, final String dirName) {
        reslibMap.put(name, dirName);
    }

    public static String getReslibDir(final String name) {
        if ((name == null) || name.equals("")) {
            return getReslibDir();
        } else {
            return reslibMap.get(name);
        }
    }

    public static String getReslibDir() {
        if (iupacMode) {
            return reslibMap.get("IUPAC");
        } else {
            return reslibMap.get("XPLOR");
        }
    }

    public Molecule read(String fileName)
            throws MoleculeIOException {
        String string;
        LineNumberReader lineReader;

        try {
            BufferedReader bf = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bf);
        } catch (IOException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        }

        String lastRes = "";
        File file = new File(fileName);
        int dotPos = file.getName().lastIndexOf('.');
        int structureNumber = 0;
        Point3 pt;
        Molecule molecule;
        String molName;

        if (dotPos >= 0) {
            molName = file.getName().substring(0, dotPos);
        } else {
            molName = file.getName();
        }

        molecule = new Molecule(molName);

        Polymer polymer = null;
        Atom prevAtom = null;
        Residue residue = null;
        Compound compound = null;
        String lastChain = null;
        String coordSetName = "";

        try {
            while (true) {
                string = lineReader.readLine();

                if ((string == null) || string.startsWith("ENDMDL")) {
                    Molecule.makeAtomList();
                    molecule.structures.add(Integer.valueOf(structureNumber));
                    Molecule.calcAllBonds();
                    lineReader.close();
                    return molecule;
                }

                if (string.startsWith("ATOM  ")) {
                    PDBAtomParser atomParse = new PDBAtomParser(string);

                    if (!lastRes.equals(atomParse.resNum)) {
                        lastRes = atomParse.resNum;
                        residue = new Residue(atomParse.resNum,
                                atomParse.resName);

                        if (residue == null) {
                            throw new MoleculeIOException("didn't form residue");
                        }

                        String thisChain;

                        if (atomParse.segment.equals("")) {
                            thisChain = atomParse.chainID;
                        } else {
                            thisChain = atomParse.segment;
                        }

                        if ((lastChain == null)
                                || !thisChain.equals(lastChain)) {
                            lastChain = thisChain;

                            if (lastChain.trim().equals("")) {
                                coordSetName = molName;
                                polymer = new Polymer(molName);
                            } else {
                                coordSetName = lastChain;
                                polymer = new Polymer(lastChain);
                            }
                            polymer.molecule = molecule;
                            molecule.addEntity(polymer, coordSetName);
                        }

                        if (polymer == null) {
                            throw new MoleculeIOException("didn't form polymer");
                        }

                        polymer.addResidue(residue);
                    }

                    Atom atom = new Atom(atomParse);
                    atom.setPointValidity(structureNumber, true);
                    atom.entity = residue;
                    pt = atom.getPoint(structureNumber);
                    pt = new Point3(atomParse.x, atomParse.y, atomParse.z);
                    atom.setPoint(structureNumber, pt);
                    atom.setOccupancy((float) atomParse.occupancy);
                    atom.setBFactor((float) atomParse.bfactor);
                    residue.addAtom(atom);
                } else if (string.startsWith("HETATM")) {
                    PDBAtomParser atomParse = new PDBAtomParser(string);

                    if (!lastRes.equals(atomParse.resNum)) {
                        lastRes = atomParse.resNum;
                        compound = new Compound(atomParse.resNum,
                                atomParse.resName);

                        String thisChain;

                        if (atomParse.segment.equals("")) {
                            thisChain = atomParse.chainID;
                        } else {
                            thisChain = atomParse.segment;
                        }

                        if (!thisChain.equals(lastChain)) {
                            lastChain = thisChain;

                            if (lastChain.equals(" ")) {
                                coordSetName = molName;
                            } else {
                                coordSetName = lastChain;
                            }
                        }

                        molecule.addEntity(compound, coordSetName);
                        molecule.nResidues++;
                    }

                    Atom atom = new Atom(atomParse);
                    atom.setPointValidity(structureNumber, true);
                    pt = atom.getPoint(structureNumber);
                    pt = new Point3(atomParse.x, atomParse.y, atomParse.z);
                    atom.setPoint(structureNumber, pt);
                    atom.setOccupancy((float) atomParse.occupancy);
                    atom.setBFactor((float) atomParse.bfactor);
                    atom.entity = compound;
                    compound.addAtom(atom);
                }
            }
        } catch (MoleculeIOException tclE) {
            throw tclE;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();

            return molecule;
        }
    }

    public static void capPolymer(Polymer polymer) {
        Residue residue = polymer.firstResidue;
        ArrayList<Atom> atoms = residue.getAtoms();
        if (atoms.size() > 2) {
            Atom firstAtom = residue.getAtoms().get(0);
            Atom secondAtom = residue.getAtoms().get(1);
            Atom thirdAtom = residue.getAtoms().get(2);
            if (firstAtom.getName().equals("N") && (secondAtom.getName().equals("H") || secondAtom.getName().equals("HN"))) {
                secondAtom.remove();
                String newRoot = "H";
                if (!isIUPACMode()) {
                    newRoot = "HT";
                }
                thirdAtom.valanceAngle = (float) (180.0 * Math.PI / 180.0);
                thirdAtom.dihedralAngle = (float) (0.0 * Math.PI / 180.0);
                Atom newAtom = firstAtom.add(newRoot + "3", "H", 1);
                newAtom.setType("H");
                newAtom.bondLength = 1.08f;
                newAtom.dihedralAngle = (float) (109.0 * Math.PI / 180.0);
                newAtom.valanceAngle = (float) (60.0 * Math.PI / 180.0);
                newAtom = firstAtom.add(newRoot + "2", "H", 1);
                newAtom.setType("H");
                newAtom.bondLength = 1.08f;
                newAtom.dihedralAngle = (float) (109.0 * Math.PI / 180.0);
                newAtom.valanceAngle = (float) (60.0 * Math.PI / 180.0);
                newAtom = firstAtom.add(newRoot + "1", "H", 1);
                newAtom.setType("H");
                newAtom.bondLength = 1.08f;
                newAtom.dihedralAngle = (float) (109.0 * Math.PI / 180.0);
                newAtom.valanceAngle = (float) (60.0 * Math.PI / 180.0);
            }
            if (atoms.size() > 4) {
                Atom fourthAtom = residue.getAtoms().get(4);
                if (fourthAtom.getName().equals("O5'")) {
                    Atom newAtom = firstAtom.add("OP3", "O", 1);
                    newAtom.setType("O");
                    newAtom.bondLength = 1.48f;
                    newAtom.dihedralAngle = (float) (71.58 * Math.PI / 180.0);
                    newAtom.valanceAngle = (float) (0 * Math.PI / 180.0);
                }
            }

            residue = polymer.lastResidue;
            atoms = residue.getAtoms();
            Atom lastAtom = atoms.get(atoms.size() - 1);
            secondAtom = atoms.get(atoms.size() - 2);
            if (lastAtom.getName().equals("O")) {
                if ((secondAtom.getName().equals("C"))) {
                    lastAtom.remove();
                    String newRoot = "O";
                    if (!isIUPACMode()) {
                        newRoot = "OT";
                    }
                    Atom newAtom;
                    if (!isIUPACMode()) {
                        newAtom = secondAtom.add(newRoot + "2", "O", 2);
                    } else {
                        newAtom = secondAtom.add(newRoot + "''", "O", 2);
                    }
                    newAtom.bondLength = 1.24f;
                    newAtom.dihedralAngle = (float) (180.0 * Math.PI / 180.0);
                    newAtom.valanceAngle = (float) (120.0 * Math.PI / 180.0);
                    newAtom.setType("O");

                    if (!isIUPACMode()) {
                        newAtom = secondAtom.add(newRoot + "1", "O", 1);
                    } else {
                        newAtom = secondAtom.add(newRoot + "'", "O", 1);
                    }
                    newAtom.bondLength = 1.24f;
                    newAtom.valanceAngle = (float) (120.0 * Math.PI / 180.0);
                    newAtom.dihedralAngle = (float) (180.0 * Math.PI / 180.0);
                    newAtom.setType("O");
                }
            } else if (lastAtom.getName().equals("O3'")) {
                Atom newAtom = lastAtom.add("HO3'", "H", 1);
                newAtom.setType("H");
                newAtom.bondLength = 0.98f;
                newAtom.dihedralAngle = (float) (109.0 * Math.PI / 180.0);
                newAtom.valanceAngle = (float) (120.0 * Math.PI / 180.0);
            }
        }

    }

    public ArrayList<String> readSequence(String fileName, boolean listMode)
            throws MoleculeIOException {
        LineNumberReader lineReader;
        String lastRes = "";
        String lastLoc = "";
        File file = new File(fileName);
        int dotPos = file.getName().lastIndexOf('.');
        String molName = file.getName().substring(0, dotPos);

        try {
            BufferedReader bf = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bf);
        } catch (IOException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        }
        String string;
        String polymerName = "";
        String lastChain = null;
        ArrayList<String> residueList = new ArrayList<>();
        residueList.add("-molecule " + molName);
        try {
            while (true) {
                string = lineReader.readLine();

                if (string == null) {
                    break;
                }

                if (string.startsWith("ATOM  ")) {
                    PDBAtomParser atomParse = new PDBAtomParser(string);

                    String thisChain = "";
                    if (atomParse.segment.trim().equals("")) {
                        thisChain = atomParse.chainID.trim();
                    } else {
                        thisChain = atomParse.segment.trim();
                    }
                    if ((lastChain == null) || !thisChain.equals(lastChain)) {
                        lastChain = thisChain;

                        if (lastChain.trim().equals("")) {
                            polymerName = molName;
                        } else {
                            polymerName = lastChain;
                        }
                        residueList.add("-polymer " + polymerName);
                        residueList.add("-coordset " + molName);
                    }

                    if (!lastRes.equals(atomParse.resNum)) {
                        lastRes = atomParse.resNum;
                        lastLoc = atomParse.loc;
                        atomParse.resName = atomParse.resName.toLowerCase();
                        residueList.add(atomParse.resName + " " + atomParse.resNum);
                    }
                    // fixme should we do anything here with MODEL
                    //} else if (string.startsWith("MODEL ")) {
                } else if (string.startsWith("ENDMDL")) {
                    break;
                } else if (string.startsWith("TER   ")) {
                    //break;
                }
            }
            lineReader.close();
            if (!listMode) {
                Sequence sequence = new Sequence();
                sequence.read(molName, residueList, null);
                readCoordinates(fileName, 0, true);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());

            return null;
        }
        return residueList;
    }

    double chiralVolume(Vector3d v1, Vector3d v2, Vector3d v3, Vector3d v4) {
        Vector3d vc = new Vector3d();
        v2.sub(v1);
        v3.sub(v1);
        v4.sub(v1);
        vc.cross(v2, v3);
        double dot = v4.dot(vc);
        return dot;
    }

    public int checkPDBType(String fileName) throws MoleculeIOException {
        LineNumberReader lineReader;
        try {
            BufferedReader bf = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bf);
        } catch (IOException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        }
        String lastRes = "";
        HashMap atomMap = new HashMap();
        try {
            while (true) {
                String string = lineReader.readLine();

                if (string == null) {
                    break;
                }

                if (string.startsWith("ATOM  ")) {
                    PDBAtomParser atomParse = new PDBAtomParser(string);
                    String resName = atomParse.resName;
                    if (resName.equals("ALA")) {
                        continue;
                    }
                    if (resName.equals("GLY")) {
                        continue;
                    }
                    String aName = atomParse.atomName;
                    String resNum = atomParse.resNum;
                    Vector3d vector3 = new Vector3d(atomParse.x, atomParse.y, atomParse.z);
                    atomMap.put(aName, vector3);
                    boolean gotAtoms = false;
                    if (!resNum.equals(lastRes)) {
                        if (atomMap.containsKey("CA") && atomMap.containsKey("CG")) {
                            Vector3d vectorCA = (Vector3d) atomMap.get("CA");
                            Vector3d vectorCG = (Vector3d) atomMap.get("CG");
                            if (atomMap.containsKey("HB1") && atomMap.containsKey("HB2")) {
                                Vector3d vectorHB1 = (Vector3d) atomMap.get("HB1");
                                Vector3d vectorHB2 = (Vector3d) atomMap.get("HB1");
                                double volume = chiralVolume(vectorCA, vectorCG, vectorHB1, vectorHB2);
                                gotAtoms = true;
                                lineReader.close();
                                return 1;
                            } else if (atomMap.containsKey("HB2") && atomMap.containsKey("HB3")) {
                                Vector3d vectorHB2 = (Vector3d) atomMap.get("HB2");
                                Vector3d vectorHB3 = (Vector3d) atomMap.get("HB3");
                                double volume = chiralVolume(vectorCA, vectorCG, vectorHB2, vectorHB3);
                                gotAtoms = true;
                                lineReader.close();
                                return 2;
                            }
                        }
                        if (gotAtoms) {
                            break;
                        }
                        lastRes = resNum;
                        atomMap.clear();
                    }
                }
            }
            lineReader.close();

        } catch (Exception exc) {
            System.err.println(exc.getMessage());
            exc.printStackTrace();
            return -1;
        }
        return 0;

    }

    public void readCoordinates(String fileName, int structureNumber, final boolean noComplain)
            throws MoleculeIOException {
        LineNumberReader lineReader;
        String lastChain = "";

        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            throw new MoleculeIOException("No molecule");
        }
        String molName = molecule.getName();
        int type = checkPDBType(fileName);

        try {
            BufferedReader bf = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bf);
        } catch (IOException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        }

        boolean swap = false;
        if ((molecule.checkType() == 2) && (type == 1)) {
            swap = true;
        }
        boolean readJustOne = true;

        if (structureNumber < 0) {
            readJustOne = false;
            structureNumber = 0;
        }

        molecule.structures.add(Integer.valueOf(structureNumber));

        String polymerName = null;
        polymerName = molName;

        Iterator e = molecule.coordSets.values().iterator();

        CoordSet coordSet;

        while (e.hasNext()) {
            coordSet = (CoordSet) e.next();

            Iterator entIterator = coordSet.getEntities().values().iterator();

            while (entIterator.hasNext()) {
                Entity entity = (Entity) entIterator.next();

                if (entity instanceof Polymer) {
                    polymerName = entity.getName();
                }
                for (Atom atom : entity.getAtoms()) {
                    atom.setPointValidity(structureNumber,
                            false);
                }
            }
        }

        Polymer polymer = null;
        Residue residue = null;
        Point3 pt = null;
        String string;
        String coordSetName = "";
        TreeSet selSet = new TreeSet();
        boolean coordsGen = false;
        try {
            while (true) {
                string = lineReader.readLine();

                if (string == null) {
                    break;
                }
                boolean hetAtom = false;
                if (string.startsWith("ATOM  ") || string.startsWith("HETATM")) {
                    PDBAtomParser atomParse = new PDBAtomParser(string, swap);
                    Entity compoundEntity = (Entity) molecule.getEntity(atomParse.resName);
                    // PDB standard says all non-standard residue atoms should be HETATM
                    //   but some software makes everything an atom
                    //   so check to see if we've made an entity with residue name
                    //     if so we treat it as HETATM
                    if (string.startsWith("HETATM") || ((compoundEntity != null) && (compoundEntity instanceof Compound))) {
                        hetAtom = true;
                    }
                    Atom atom = null;
                    String thisChain;

                    if (atomParse.segment.equals("")) {
                        thisChain = atomParse.chainID;
                    } else {
                        thisChain = atomParse.segment.toLowerCase();
                    }
                    if (!hetAtom) {

                        if (!thisChain.equals(lastChain)) {
                            lastChain = thisChain;
                        }

                        if (lastChain.trim().equals("")) {
                            polymerName = molName;
                        } else {
                            polymerName = lastChain;
                        }

                        polymer = (Polymer) molecule.getEntity(polymerName);

                        if (polymer == null) {
                            polymerName = molecule.getDefaultEntity();
                            polymer = (Polymer) molecule.getEntity(polymerName);
                        }
                        if (polymer == null) {
                            System.err.println("null polymer " + polymerName + " for line: " + string);

                            continue;
                        }

                        if (!molecule.coordSetExists(coordSetName)) {
                            coordSetName = molecule.getFirstCoordSet().getName();
                            //  molecule.addCoordSet(coordSetName, polymer);
                        }

                        residue = polymer.getResidue(atomParse.resNum);

                        if (residue == null) {
                            System.err.println("null residue " + atomParse.resNum + " for polymer " + polymerName);
                            System.err.println(string);
                            continue;
                        }
                        if (!AtomParser.isResNameConsistant(residue.getName(), atomParse.resName)) {
                            String msg = "Residue " + polymerName + ":" + residue.getName() + " at " + atomParse.resNum + " is not same as in file " + atomParse.resName;
                            if (allowSequenceDiff) {
                                System.err.println(msg);
                                continue;
                            } else {
                                throw new MoleculeIOException(msg);
                            }
                        }
                        atom = residue.getAtomLoose(atomParse.atomName);

                        if (atom == null) {
                            System.err.println("null atom " + atomParse.atomName);
                            System.err.println("null atom " + string);
                            continue;
                        }

                    } else {
                        coordSetName = thisChain;

                        if (compoundEntity == null) {
                            if (!noComplain) {
                                System.err.println("no such compound as  "
                                        + atomParse.resName);
                                System.err.println("in file line  " + string);
                            }
                            continue;
                        }
                        Compound compound = (Compound) compoundEntity;
                        atom = compound.getAtom(atomParse.atomName);

                        if (atom == null) {
                            if (!noComplain) {
                                System.err.println("no such atom as "
                                        + atomParse.atomName);
                                System.err.println("in file line " + string);
                            }
                            continue;
                        }
                    }
                    atom.setPointValidity(structureNumber, true);
                    pt = new Point3(atomParse.x, atomParse.y, atomParse.z);
                    atom.setPoint(structureNumber, pt);
                    atom.setOccupancy((float) atomParse.occupancy);
                    atom.setBFactor((float) atomParse.bfactor);

                } else if (string.startsWith("MODEL ")) {
                    if (!readJustOne) {
                        String modString = string.substring(6).trim();
                        if (modString.length() > 0) {
                            structureNumber = Integer.parseInt(modString);
                        }
                    }
                    Integer intStructure = Integer.valueOf(structureNumber);
                    selSet.add(intStructure);
                    molecule.structures.add(intStructure);

                    Iterator iterator = molecule.entities.values().iterator();

                    while (iterator.hasNext()) {
                        Entity entity = (Entity) iterator.next();
                        for (Atom atom : entity.getAtoms()) {
                            atom.setPointValidity(structureNumber, false);
                        }
                    }
                } else if (string.startsWith("ENDMDL")) {
                    molecule.genCoords(structureNumber, true);
                    coordsGen = true;
                    if (readJustOne) {
                        break;
                    }

                    structureNumber++;
                } else if (string.startsWith("TER   ")) {
                    continue;
                }
            }
            lineReader.close();
        } catch (MoleculeIOException psE) {
            System.out.println("err " + psE.getMessage());
            throw psE;
        } catch (Exception exc) {
            System.err.println(exc.getMessage());
            exc.printStackTrace();

            return;
        }
        if (!coordsGen) {
            molecule.genCoords(structureNumber, true);
        }
        molecule.setActiveStructures(selSet);

    }

    public BufferedReader getLocalResidueReader(final String fileName) {
        File file = new File(fileName);
        String reslibDir = getLocalReslibDir();
        BufferedReader bf = null;
        if (!reslibDir.equals("")) {
            try {
                bf = new BufferedReader(new FileReader(reslibDir + '/' + file.getName()));
            } catch (IOException ioe) {
                bf = null;
            }
        }
        return bf;
    }

    public boolean addResidue(String fileName, Residue residue, String coordSetName, boolean throwTclException) throws MoleculeIOException {
        StreamTokenizer tokenizer;
        String newState = null;
        String currentState = null;
        Atom atom = null;
        Atom parent = null;
        Atom refAtom = null;
        int iArgs = 0;
        BufferedReader bf;
        try {
            if (fileName.startsWith("/reslib")) {
                InputStream inputStream = this.getClass().getResourceAsStream(fileName);
                bf = new BufferedReader(new InputStreamReader(inputStream));
            } else {
                bf = new BufferedReader(new FileReader(fileName));
            }
            tokenizer = new StreamTokenizer(bf);
        } catch (IOException ioe) {
            bf = getLocalResidueReader(fileName);
            if (bf == null) {
                if (throwTclException) {
                    throw new MoleculeIOException("the Cannot open the file " + fileName);
                } else {
                    return false;
                }
            }
            tokenizer = new StreamTokenizer(bf);
        }

        tokenizer.resetSyntax();
        tokenizer.wordChars('a', 'z');
        tokenizer.wordChars('A', 'Z');
        tokenizer.wordChars('0', '9');
        tokenizer.wordChars('\'', '\'');
        tokenizer.wordChars('-', '-');
        tokenizer.wordChars('+', '+');
        tokenizer.whitespaceChars(0000, 32);
        Atom angleAtom = null;
        String pseudoAtomName = null;
        ArrayList<String> pseudoArray = new ArrayList<String>();
        try {
            while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
                if (tokenizer.ttype == StreamTokenizer.TT_WORD) {

                    if (tokenizer.sval.equals("ATOM")) {
                        newState = "ATOM";
                    } else if (tokenizer.sval.equals("FAMILY")) {
                        newState = "FAMILY";
                    } else if (tokenizer.sval.equals("ANGLE")) {
                        newState = "ANGLE";
                    } else if (tokenizer.sval.equals("PSEUDO")) {
                        newState = "PSEUDO";
                    } else if (tokenizer.sval.equals("CRAD")) {
                        newState = "CRAD";
                    } else {
                        newState = null;
                    }
                } else if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) {
                    newState = null;
                } else if (tokenizer.ttype == '-') {
                    newState = null;
                } else if (tokenizer.ttype == '+') {
                    newState = null;
                } else {
                    continue;
                }

                if (currentState == null) {
                    currentState = newState;
                    iArgs = 0;

                    continue;
                } else if (currentState.equals("ATOM")) {
                    if ((newState != null)) {
                        if (iArgs < 5) {
                            throw new MoleculeIOException("too few arguments for ATOM");
                        } else {
                            tokenizer.resetSyntax();
                            tokenizer.wordChars('a', 'z');
                            tokenizer.wordChars('A', 'Z');
                            tokenizer.wordChars('\'', '\'');
                            tokenizer.wordChars('-', '-');
                            tokenizer.wordChars('+', '+');
                            tokenizer.wordChars('0', '9');
                            tokenizer.whitespaceChars(0000, 32);
                            currentState = newState;
                            iArgs = 0;

                            continue;
                        }
                    } else {
                        if (iArgs == 0) {
                            atom = new Atom("CA");
                            atom.setPointValidity(0, true);
                            atom.entity = residue;
                            atom.name = tokenizer.sval;
                            residue.addAtom(atom);
                        } else if (iArgs == 1) {
                            atom.setType(tokenizer.sval);

                            if (tokenizer.sval.substring(0, 1).equals("M")
                                    && atom.name.startsWith("H")) {
                                atom.setAtomicNumber("H");
                            } else if (tokenizer.sval.substring(0, 1).equals("A")
                                    && atom.name.startsWith("C")) {
                                atom.setAtomicNumber("C");
                            } else {
                                atom.setAtomicNumber(tokenizer.sval.substring(
                                        0, 1));
                            }

                            tokenizer.parseNumbers();
                        } else if (iArgs == 2) {
                            // bondLength
                            atom.bondLength = (float) tokenizer.nval;
                        } else if (iArgs == 3) {
                            // valance angle
                            atom.valanceAngle = (float) (tokenizer.nval * Math.PI / 180.0);
                        } else if (iArgs == 4) {
                            // dihedral angle
                            atom.dihedralAngle = (float) (tokenizer.nval * Math.PI / 180.0);
                        }

                        iArgs++;
                    }
                } else if (currentState.equals("FAMILY")) {
                    if ((newState != null)) {
                        if (iArgs < 2) {
                            throw new MoleculeIOException("too few arguments for FAMILY");
                        } else {
                            currentState = newState;
                            iArgs = 0;

                            continue;
                        }
                    } else {
                        if (iArgs == 0) {
                            if (tokenizer.sval.equals("-")) {
                                if (connectAtom != null) {
                                    parent = connectAtom;

                                } else {
                                    parent = null;
                                }
                            } else {
                                parent = residue.getAtom(tokenizer.sval);
                            }

                            iArgs++;

                            continue;
                        }

                        Bond bond = null;

                        if (iArgs == 1) {
                            refAtom = residue.getAtom(tokenizer.sval);

                            if (parent != null) {
                                bond = new Bond(refAtom, parent);
                                refAtom.parent = parent;
                                refAtom.addBond(bond);

                                if ((Residue) parent.entity == residue) {
                                    iArgs++;

                                    continue;
                                } else {
                                    Bond bond2 = new Bond(parent, refAtom);
                                    parent.addBond(bond2);
                                }
                            } else {
                                iArgs++;

                                continue;
                            }
                        } else if (tokenizer.sval.equals("+")) {
                            connectAtom = refAtom;
                            iArgs++;

                            continue;
                        } else {
                            Atom daughterAtom = null;
                            boolean ringClosure = false;
                            if (tokenizer.sval.startsWith("r")) { // close ring
                                daughterAtom = residue.getAtom(tokenizer.sval.substring(1));
                                ringClosure = true;
                            } else if (tokenizer.sval.startsWith("-")) { // close ring
                                daughterAtom = residue.getAtom(tokenizer.sval.substring(1));
                                ringClosure = true;
                            } else {
                                daughterAtom = residue.getAtom(tokenizer.sval);
                            }
                            if (daughterAtom == null) {
                                throw new MoleculeIOException("Can't find atom \"" + tokenizer.sval + "\"");
                            }

                            if (daughterAtom.getName().startsWith("H")) {
                                bond = new Bond(daughterAtom, refAtom);
                                daughterAtom.addBond(bond);
                                daughterAtom.parent = refAtom;
                            }

                            bond = new Bond(refAtom, daughterAtom);
                            refAtom.addBond(bond);
                            if (!ringClosure) {
                                daughterAtom.parent = refAtom;
                            }
                        }
                        residue.addBond(bond);
                        iArgs++;
                    }
                } else if (currentState.equals("ANGLE")) {
                    //System.err.println(iArgs + " " + tokenizer.toString());
                    if ((newState != null)) {
                        if (iArgs < 2) {
                            throw new MoleculeIOException("too few arguments for ANGLE");
                        } else {
                            currentState = newState;
                            iArgs = 0;

                            continue;
                        }
                    } else {
                        if (iArgs == 0) {
                            angleAtom = residue.getAtom(tokenizer.sval);
                            tokenizer.parseNumbers();
                            // angleAtom.
                            iArgs++;
                            continue;
                        }
                        if (iArgs == 2) {
                            if (angleAtom != null) {
                                angleAtom.irpIndex = (int) (tokenizer.nval + 0.5);
                            }
                        }
                        iArgs++;
                    }
                } else if (currentState.equals("PSEUDO")) {
                    if ((newState != null)) {
                        if (iArgs < 3) {
                            throw new MoleculeIOException("too few arguments for PSEUDO");
                        } else {
                            currentState = newState;
                            iArgs = 0;
                            residue.addPseudoAtoms(pseudoAtomName, pseudoArray);
                            continue;
                        }
                    } else {
                        if (iArgs == 0) {
                            pseudoAtomName = tokenizer.sval;
                            pseudoArray.clear();
                            iArgs++;
                            continue;
                        } else {
                            pseudoArray.add(tokenizer.sval);
                        }
                        iArgs++;
                    }
                }
            }

            bf.close();
            return true;
        } catch (IOException e) {
            System.err.println("Error reading \"" + fileName + "\"");
            System.err.println(e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void readResidue(String fileName, String fileContent, Molecule molecule, String coordSetName)
            throws MoleculeIOException {
        String molName = molecule.name;

        if (coordSetName == null) {
            // XXX
            coordSetName = ((CoordSet) molecule.coordSets.values().iterator().next()).getName();
        }
        int structureNumber = 0;
        String string;
        LineNumberReader lineReader;

        try {
            if (fileContent == null) {
                BufferedReader bf = new BufferedReader(new FileReader(fileName));
                lineReader = new LineNumberReader(bf);
            } else {
                StringReader sf = new StringReader(fileContent);
                lineReader = new LineNumberReader(sf);
            }
        } catch (IOException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        }
        File file = new File(fileName);

        String fileTail = file.getName();
        String fileRoot;
        int dot = fileTail.indexOf(".");

        if (dot != -1) {
            fileRoot = fileTail.substring(0, dot);
        } else {
            fileRoot = fileTail;
        }

        // Make atom list so lastResNum is calculated
        molecule.makeAtomList();
        Compound compound = null;

        while (true) {
            try {
                string = lineReader.readLine();
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
                molecule.makeAtomList();
                molecule.getAtomTypes();
                compound.calcAllBonds();
                return;
            }

            if (string == null) {
                molecule.makeAtomList();
                molecule.getAtomTypes();
                compound.calcAllBonds();
                return;
            }
            if (string.startsWith("ATOM  ") || string.startsWith("HETATM")) {
                PDBAtomParser atomParse = new PDBAtomParser(string);
                if (compound == null) {
                    System.out.println("add compound with " + String.valueOf(molecule.lastResNum + 1));
                    compound = new Compound(String.valueOf(molecule.lastResNum + 1), atomParse.resName);
                    compound.molecule = molecule;
                    compound.assemblyID = molecule.entityLabels.size() + 1;
                    molecule.addEntity(compound, coordSetName);
                }
                Atom atom = new Atom(atomParse);
                atom.setPointValidity(structureNumber, true);
                atom.entity = compound;
                atom.getPoint(structureNumber);
                Point3 pt = new Point3(atomParse.x, atomParse.y, atomParse.z);
                atom.setPoint(structureNumber, pt);
                atom.setOccupancy((float) atomParse.occupancy);
                atom.setBFactor((float) atomParse.bfactor);
                compound.addAtom(atom);
            }
        }
    }
}
