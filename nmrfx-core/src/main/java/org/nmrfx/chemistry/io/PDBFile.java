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
package org.nmrfx.chemistry.io;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.*;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.vecmath.Vector3d;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PluginAPI("ring")
public class PDBFile {
    private static final Logger log = LoggerFactory.getLogger(PDBFile.class);

    private static final boolean ALLOW_SEQUENCE_DIFF = true;
    private static final Map<String, String> reslibMap = new HashMap<>();
    private static boolean iupacMode = true;
    private static String localReslibDir = "";

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

    /**
     * setLocalResLibDir is used to specify a directory of user generated
     * residues.
     *
     * @param dirName provides a path to the directory
     *                 <p>
     *                 When specifying a sequence, if the residue name is not found within the
     *                 standard library, this path will be parsed for the necessary file.
     */
    public static void setLocalResLibDir(final String dirName) {
        localReslibDir = dirName;
    }

    public static String getLocalReslibDir() {
        return localReslibDir;
    }

    public static void putReslibDir(final String name, final String dirName) {
        reslibMap.put(name, dirName);
    }

    public static String getReslibDir(final String name) {
        if ((name == null) || name.isEmpty()) {
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

    public MoleculeBase read(String fileName) throws MoleculeIOException {
        return read(fileName, false);
    }

    public MoleculeBase read(String fileName, boolean strictMode)
            throws MoleculeIOException {
        String string;
        String lastRes = "";
        File file = new File(fileName);
        int dotPos = file.getName().lastIndexOf('.');
        int structureNumber = 0;
        Point3 pt;
        MoleculeBase molecule;
        String molName;

        if (dotPos >= 0) {
            molName = file.getName().substring(0, dotPos);
        } else {
            molName = file.getName();
        }

        molecule = MoleculeFactory.newMolecule(molName);

        Polymer polymer = null;
        Residue residue = null;
        Compound compound = null;
        String lastChain = null;
        String coordSetName = "";

        try (
                BufferedReader bf = new BufferedReader(new FileReader(fileName));
                LineNumberReader lineReader = new LineNumberReader(bf)
        ) {
            while (true) {
                string = lineReader.readLine();

                if ((string == null) || string.startsWith("ENDMDL")) {
                    molecule.updateAtomArray();
                    molecule.structures.add(structureNumber);
                    molecule.calcAllBonds();
                    molecule.getAtomTypes();
                    ProjectBase.getActive().putMolecule(molecule);
                    return molecule;
                }

                if (string.startsWith("ATOM  ") || (string.startsWith("HETATM") && !strictMode)) {
                    PDBAtomParser atomParse = new PDBAtomParser(string);

                    if (!lastRes.equals(atomParse.resNum)) {
                        lastRes = atomParse.resNum;
                        residue = new Residue(atomParse.resNum,
                                atomParse.resName);

                        String thisChain;

                        if (atomParse.segment.isEmpty()) {
                            thisChain = atomParse.chainID;
                        } else {
                            thisChain = atomParse.segment;
                        }

                        if (!thisChain.equals(lastChain)) {
                            lastChain = thisChain;

                            if (lastChain.trim().isEmpty()) {
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
                    atom.setEnergyProp();
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

                        if (atomParse.segment.isEmpty()) {
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
                    }

                    if (compound == null) {
                        throw new MoleculeIOException("didn't form compound");
                    }

                    Atom atom = new Atom(atomParse);
                    atom.setEnergyProp();
                    atom.setPointValidity(structureNumber, true);
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
        } catch (FileNotFoundException fnf) {
            throw new MoleculeIOException(fnf.getMessage());
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return molecule;
        }
    }

    // fixme change capping atom names to new PDB standard H,H2  O,OXT,HXT
    public static void capPolymer(Polymer polymer) {
        polymer.getFirstResidue().capFirstResidue("");
        polymer.getLastResidue().capLastResidue("");

    }

    public MoleculeBase readSequence(String fileName, int structureNum)
            throws MoleculeIOException {
        String lastRes = "";
        File file = new File(fileName);
        int dotPos = file.getName().lastIndexOf('.');
        String molName = file.getName().substring(0, dotPos);
        MoleculeBase moleculeBase;

        String string;
        String polymerName;
        String lastChain = null;
        ArrayList<String> residueList = new ArrayList<>();
        residueList.add("-molecule " + molName);
        try (
                BufferedReader bf = new BufferedReader(new FileReader(fileName));
                LineNumberReader lineReader = new LineNumberReader(bf)
        ) {
            while (true) {
                string = lineReader.readLine();

                if (string == null) {
                    break;
                }

                if (string.startsWith("ATOM  ") || string.startsWith("HETATM ")) {
                    PDBAtomParser atomParse = new PDBAtomParser(string);

                    if (string.startsWith("HETATM ") && !atomParse.resName.equals("MSE")) {
                        continue;
                    }

                    String thisChain;
                    if (atomParse.segment.trim().isEmpty()) {
                        thisChain = atomParse.chainID.trim();
                    } else {
                        thisChain = atomParse.segment.trim();
                    }
                    if (!thisChain.equals(lastChain)) {
                        lastChain = thisChain;

                        if (lastChain.trim().isEmpty()) {
                            polymerName = molName;
                        } else {
                            polymerName = lastChain;
                        }
                        residueList.add("-polymer " + polymerName);
                        residueList.add("-coordset " + polymerName);
                    }

                    if (!lastRes.equals(atomParse.resNum)) {
                        lastRes = atomParse.resNum;
                        atomParse.resName = atomParse.resName.toLowerCase();
                        residueList.add(atomParse.resName + " " + atomParse.resNum);
                    }
                    // fixme should we do anything here with MODEL
                } else if (string.startsWith("ENDMDL")) {
                    break;
                }
            }
            Sequence sequence = new Sequence();
            moleculeBase = sequence.read(molName, residueList, null);
            readCoordinates(moleculeBase, fileName, structureNum, true, true);

        } catch (FileNotFoundException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        } catch (IOException e) {
            log.warn(e.getMessage(), e);

            return null;
        }
        return moleculeBase;
    }

    double chiralVolume(Vector3d v1, Vector3d v2, Vector3d v3, Vector3d v4) {
        Vector3d vc = new Vector3d();
        v2.sub(v1);
        v3.sub(v1);
        v4.sub(v1);
        vc.cross(v2, v3);
        return v4.dot(vc);
    }

    public int checkPDBType(String fileName) throws MoleculeIOException {
        String lastRes = "";
        HashMap<String, Vector3d> atomMap = new HashMap<>();
        try (
                BufferedReader bf = new BufferedReader(new FileReader(fileName));
                LineNumberReader lineReader = new LineNumberReader(bf)
        ) {
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
                    if (!resNum.equals(lastRes)) {
                        if (atomMap.containsKey("CA") && atomMap.containsKey("CG")) {
                            if (atomMap.containsKey("HB1") && atomMap.containsKey("HB2")) {
                                return 1;
                            } else if (atomMap.containsKey("HB2") && atomMap.containsKey("HB3")) {

                                return 2;
                            }
                        }
                        lastRes = resNum;
                        atomMap.clear();
                    }
                }
            }

        } catch (FileNotFoundException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        } catch (Exception exc) {
            log.warn(exc.getMessage(), exc);
            return -1;
        }
        return 0;

    }

    public void readMultipleCoordinateFiles(File dir, final boolean noComplain) throws MoleculeIOException, IOException {
        MoleculeBase molecule = MoleculeFactory.getActive();
        boolean readMolSeq = false;
        if (molecule == null) {
            readMolSeq = true;
        } else {
            molecule.structures.clear();
        }
        Pattern pdbPattern = Pattern.compile(".+([0-9]+)\\.pdb");
        Path dirPath = dir.toPath();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            int iStruct = 0;
            for (Path entry : stream) {
                Matcher matcher = pdbPattern.matcher(entry.toString());
                if (matcher.matches()) {
                    if (readMolSeq) {
                        readSequence(entry.toString(), 0);
                        molecule = MoleculeFactory.getActive();
                        molecule.structures.clear();
                        readMolSeq = false;
                    }
                    readCoordinates(molecule, entry.toString(), iStruct++, noComplain, true);
                }
            }
        }
    }

    public void readMultipleCoordinateFiles(List<File> files, final boolean noComplain) throws MoleculeIOException, IOException {
        MoleculeBase molecule = MoleculeFactory.getActive();
        boolean readMolSeq = false;
        if (molecule == null) {
            readMolSeq = true;
        } else {
            molecule.structures.clear();
        }
        int iStruct = 0;
        for (File entry : files) {
            if (readMolSeq) {
                readSequence(entry.toString(), 0);
                molecule = MoleculeFactory.getActive();
                molecule.structures.clear();
                readMolSeq = false;
            }
            readCoordinates(molecule, entry.toString(), iStruct++, noComplain, true);
        }
    }

    public void readCoordinates(MoleculeBase molecule, String fileName, int structureNumber, final boolean noComplain, boolean genCoords)
            throws MoleculeIOException {
        String lastChain = "";

        String molName = molecule.getName();
        int type = checkPDBType(fileName);
        boolean coordsGen = false;
        TreeSet<Integer> selSet = new TreeSet<>();
        try (BufferedReader bf = new BufferedReader(new FileReader(fileName));
             LineNumberReader lineReader = new LineNumberReader(bf)) {
            boolean swap = (molecule.checkType() == 2) && (type == 1);
            boolean readJustOne = true;

            if (structureNumber < 0) {
                readJustOne = false;
                structureNumber = molecule.getActiveStructures().length > 0 ? molecule.getActiveStructureList().getLast() + 1 : 0;
            }

            if (readJustOne) {
                molecule.structures.add(structureNumber);
            }

            String polymerName;

            Iterator<CoordSet> e = molecule.coordSets.values().iterator();

            CoordSet coordSet;

            while (e.hasNext()) {
                coordSet = e.next();

                for (Entity entity : coordSet.getEntities().values()) {
                    for (Atom atom : entity.getAtoms()) {
                        atom.setPointValidity(structureNumber,
                                false);
                    }
                }
            }

            Polymer polymer;
            Residue residue;
            Point3 pt;
            String string;
            String coordSetName = "";

            while (true) {
                string = lineReader.readLine();

                if (string == null) {
                    break;
                }
                boolean hetAtom = false;
                if (string.startsWith("ATOM  ") || string.startsWith("HETATM")) {
                    PDBAtomParser atomParse = new PDBAtomParser(string, swap);
                    Entity compoundEntity = molecule.getEntity(atomParse.resName);
                    // fixme not propertly supporting insertCode
                    if (!atomParse.insertCode.equals(" ")) {
                        continue;
                    }
                    // PDB standard says all non-standard residue atoms should be HETATM
                    //   but some software makes everything an atom
                    //   so check to see if we've made an entity with residue name
                    //     if so we treat it as HETATM
                    if (string.startsWith("HETATM") || (compoundEntity instanceof Compound)) {
                        hetAtom = true;
                    }
                    if (compoundEntity == null) {
                        hetAtom = false;
                    }
                    Atom atom;
                    String thisChain;

                    if (atomParse.segment.isEmpty()) {
                        thisChain = atomParse.chainID;
                    } else {
                        thisChain = atomParse.segment.toLowerCase();
                    }
                    if (!thisChain.equals(lastChain)) {
                        lastChain = thisChain;
                    }

                    if (!hetAtom) {

                        if (lastChain.trim().isEmpty()) {
                            polymerName = molName;
                        } else {
                            polymerName = lastChain;
                        }

                        polymer = (Polymer) molecule.getEntity(polymerName);

                        if (polymer == null) {
                            polymer = (Polymer) molecule.getChain(polymerName);
                        }
                        if (polymer == null) {
                            log.warn("null polymer {} for line {}", polymerName, string);

                            continue;
                        }

                        if (!molecule.coordSetExists(coordSetName)) {
                            coordSetName = molecule.getFirstCoordSet().getName();
                        }

                        residue = polymer.getResidue(atomParse.resNum);

                        if (residue == null) {
                            continue;
                        }
                        if (!AtomParser.isResNameConsistant(residue.getName(), atomParse.resName)) {
                            String msg = "Residue " + polymerName + ":" + residue.getName() + " at " + atomParse.resNum + " is not same as in file " + atomParse.resName;
                            if (ALLOW_SEQUENCE_DIFF) {
                                log.warn(msg);
                                log.warn(string);
                                continue;
                            } else {
                                throw new MoleculeIOException(msg);
                            }
                        }
                        atom = residue.getAtomLoose(atomParse.atomName);

                        if (atom == null) {
                            continue;
                        }

                    } else {
                        coordSetName = thisChain;

                        if (compoundEntity == null) {
                            if (!noComplain) {
                                log.warn("no such compound as  {} in file line {}", atomParse.resName, string);
                            }
                            continue;
                        }
                        Compound compound = (Compound) compoundEntity;
                        atom = compound.getAtom(atomParse.atomName);

                        if (atom == null) {
                            if (!noComplain) {
                                log.warn("no such atom as {} in file line {}", atomParse.atomName, string);
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
                    if (readJustOne) {
                        String modString = string.substring(6).trim();
                        if (!modString.isEmpty()) {
                            structureNumber = Integer.parseInt(modString);
                        }
                    }
                    if (!molecule.structures.contains(structureNumber)) {
                        Integer intStructure = structureNumber;
                        selSet.add(intStructure);
                        molecule.structures.add(intStructure);
                    }

                    for (Entity entity : molecule.entities.values()) {
                        for (Atom atom : entity.getAtoms()) {
                            atom.setPointValidity(structureNumber, false);
                        }
                    }
                } else if (string.startsWith("ENDMDL")) {
                    if (genCoords) {
                        molecule.genCoords(structureNumber, true);
                        coordsGen = true;
                    }
                    if (readJustOne) {
                        break;
                    }

                    structureNumber++;
                }
            }
        } catch (FileNotFoundException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        } catch (MoleculeIOException psE) {
            throw psE;
        } catch (Exception exc) {
            log.warn(exc.getMessage(), exc);
            return;
        }
        if (genCoords && !coordsGen) {
            molecule.genCoords(structureNumber, true);
        }
        molecule.setActiveStructures(selSet);

    }

    public BufferedReader getLocalResidueReader(final String fileName) {
        File file = new File(fileName);
        String reslibDir = getLocalReslibDir();
        BufferedReader bf = null;
        if (!reslibDir.isEmpty()) {
            try {
                bf = new BufferedReader(new FileReader(reslibDir + '/' + file.getName()));
            } catch (IOException ignored) {
            }
        }
        return bf;
    }

    public boolean addResidue(String fileName, Residue residue, String coordSetName, boolean throwTclException) throws MoleculeIOException {
        StreamTokenizer tokenizer;
        String newState;
        String currentState = null;
        Atom atom = null;
        Atom parent = null;
        Atom refAtom = null;
        int iArgs = 0;
        BufferedReader bf;
        try {
            if (fileName.startsWith("/reslib")) {
                InputStream inputStream = this.getClass().getResourceAsStream(fileName);
                assert inputStream != null;
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
        ArrayList<String> pseudoArray = new ArrayList<>();
        try {
            while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
                if (tokenizer.ttype == StreamTokenizer.TT_WORD) {

                    newState = switch (tokenizer.sval) {
                        case "ATOM" -> "ATOM";
                        case "FAMILY" -> "FAMILY";
                        case "ANGLE" -> "ANGLE";
                        case "PSEUDO" -> "PSEUDO";
                        case "CRAD" -> "CRAD";
                        default -> null;
                    };
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

                        }
                    } else {
                        if (iArgs == 0) {
                            atom = Atom.genAtomWithElement("CA", "C");
                            atom.setPointValidity(0, true);
                            atom.entity = residue;
                            atom.name = tokenizer.sval;
                            residue.addAtom(atom);
                        } else if (iArgs == 1) {
                            if (atom != null) {
                                atom.setType(tokenizer.sval);
                            }

                            if (atom != null && tokenizer.sval.charAt(0) == 'M'
                                    && atom.name.startsWith("H")) {
                                atom.setAtomicNumber("H");
                            }

                            tokenizer.parseNumbers();
                        } else if (iArgs == 2) {
                            // bondLength
                            if (atom != null) {
                                atom.bondLength = (float) tokenizer.nval;
                            }
                        } else if (iArgs == 3) {
                            // valance angle
                            if (atom != null) {
                                atom.valanceAngle = (float) (tokenizer.nval * Math.PI / 180.0);
                            }
                        } else if (iArgs == 4) {
                            // dihedral angle
                            if (atom != null) {
                                atom.dihedralAngle = (float) (tokenizer.nval * Math.PI / 180.0);
                            }
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

                        Bond bond;

                        if (iArgs == 1) {
                            refAtom = residue.getAtom(tokenizer.sval);

                            if (parent != null) {
                                bond = new Bond(refAtom, parent);
                                refAtom.parent = parent;
                                refAtom.addBond(bond);

                                if (parent.entity == residue) {
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
                            Atom daughterAtom;
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
                            if (refAtom != null) {
                                refAtom.addBond(bond);
                            }
                            if (!ringClosure) {
                                daughterAtom.parent = refAtom;
                            }
                        }
                        residue.addBond(bond);
                        iArgs++;
                    }
                } else if (currentState.equals("ANGLE")) {
                    if ((newState != null)) {
                        if (iArgs < 2) {
                            throw new MoleculeIOException("too few arguments for ANGLE");
                        } else {
                            currentState = newState;
                            iArgs = 0;

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
            log.warn("Error reading \"{}\" : {}", fileName, e.getMessage(), e);
            return false;
        }
    }

    public static Compound readResidue(String fileName, String fileContent, MoleculeBase molecule, String coordSetName) throws MoleculeIOException {
        return readResidue(fileName, fileContent, molecule, coordSetName, null);
    }

    public static Compound readResidue(String fileName, String fileContent, MoleculeBase molecule, String coordSetName, Residue residue)
            throws MoleculeIOException {
        String molName;
        if (molecule == null) {
            coordSetName = coordSetName == null ? "mol" : coordSetName;
            molName = coordSetName;
            molecule = MoleculeFactory.newMolecule(molName);
        } else {
            molName = molecule.getName();
        }
        if (coordSetName == null) {
            // XXX
            coordSetName = molecule.coordSets.values().iterator().next().getName();
        }
        int structureNumber = 0;
        String string;
        Compound compound = null;
        Map<String, Atom> atomMap = new HashMap<>();
        boolean calcBonds = true;

        try (Reader reader = fileContent == null ? new FileReader(fileName) : new StringReader(fileContent);
             BufferedReader bufReader = new BufferedReader(reader)) {
            while ((string = bufReader.readLine()) != null) {

                if (string.startsWith("ATOM  ") || string.startsWith("HETATM")) {
                    PDBAtomParser atomParse = new PDBAtomParser(string);
                    if (compound == null) {
                        compound = residue != null ? residue : new Compound(atomParse.resNum, atomParse.resName);
                        compound.molecule = molecule;
                        compound.assemblyID = molecule.entityLabels.size() + 1;
                        if (residue == null) {
                            molecule.addEntity(compound, coordSetName);
                        }
                    }
                    String atomNum = atomParse.atomNum;
                    Atom atom = new Atom(atomParse);
                    atom.setEnergyProp();
                    atomMap.put(atomNum, atom);
                    atom.setPointValidity(structureNumber, true);

                    atom.entity = compound;
                    atom.getPoint(structureNumber);
                    Point3 pt = new Point3(atomParse.x, atomParse.y, atomParse.z);
                    atom.setPoint(structureNumber, pt);
                    atom.setOccupancy((float) atomParse.occupancy);
                    atom.setBFactor((float) atomParse.bfactor);
                    compound.addAtom(atom);
                }
                if (string.startsWith("CONECT")) {
                    calcBonds = false;
                    String[] arguments = string.split("\\s+");
                    Atom bondedAtom = atomMap.get(arguments[1]);
                    for (int i = 2; i < arguments.length; i++) {
                        Atom bondeeAtom = atomMap.get(arguments[i]);
                        if (!bondedAtom.isBonded(bondeeAtom)) {
                            // Prevent duplication of bonds
                            Bond bond = new Bond(bondedAtom, bondeeAtom);
                            if (residue != null) {
                                residue.addBond(bond);
                            } else if (compound != null) {
                                compound.addBond(bond);
                            }
                            bondedAtom.addBond(bond);
                            bondeeAtom.addBond(bond);
                        }
                    }
                }
            }
        } catch (FileNotFoundException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        } catch (IOException ioe) {
            log.warn(ioe.getMessage(), ioe);
            molecule.getAtomTypes();
            if (calcBonds && compound != null) {
                compound.calcAllBonds();
            }
            ProjectBase.getActive().putMolecule(molecule);
            return compound;
        }

        molecule.getAtomTypes();
        if (calcBonds && compound != null) {
            log.info("calculating bonds");
            compound.calcAllBonds();
        }
        return compound;
    }
}
