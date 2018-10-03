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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmrfx.structure.chemistry.miner.NodeValidator;
import org.nmrfx.structure.chemistry.miner.PathIterator;

/**
 *
 * @author brucejohnson
 */
public class Sequence {

    private Atom connectAtom = null;
    private Bond connectBond = null;
    private Atom connectBranch = null;
    private int connectPosition = -1;
    private Molecule molecule;
    private final static Map<String, String> residueAliases = new HashMap<>();
    private final static List<String> AMINO_ACID_NAMES = new ArrayList<>();

    static {
        residueAliases.put("rade", "a");
        residueAliases.put("ra", "a");
        residueAliases.put("rgua", "g");
        residueAliases.put("rg", "g");
        residueAliases.put("rcyt", "c");
        residueAliases.put("rc", "c");
        residueAliases.put("rura", "u");
        residueAliases.put("ura", "u");
        residueAliases.put("ruri", "u");
        residueAliases.put("ru", "u");
        residueAliases.put("dade", "da");
        residueAliases.put("dgua", "dg");
        residueAliases.put("dcyt", "dc");
        residueAliases.put("dthy", "dt");
        residueAliases.put("dura", "du");
        residueAliases.put("duri", "du");
    }

    static {
        AMINO_ACID_NAMES.add("ala");
        AMINO_ACID_NAMES.add("arg");
        AMINO_ACID_NAMES.add("asn");
        AMINO_ACID_NAMES.add("asp");
        AMINO_ACID_NAMES.add("cys");
        AMINO_ACID_NAMES.add("gln");
        AMINO_ACID_NAMES.add("glu");
        AMINO_ACID_NAMES.add("gly");
        AMINO_ACID_NAMES.add("his");
        AMINO_ACID_NAMES.add("ile");
        AMINO_ACID_NAMES.add("leu");
        AMINO_ACID_NAMES.add("lys");
        AMINO_ACID_NAMES.add("met");
        AMINO_ACID_NAMES.add("phe");
        AMINO_ACID_NAMES.add("pro");
        AMINO_ACID_NAMES.add("pro_cis");
        AMINO_ACID_NAMES.add("ser");
        AMINO_ACID_NAMES.add("thr");
        AMINO_ACID_NAMES.add("trp");
        AMINO_ACID_NAMES.add("tyr");
        AMINO_ACID_NAMES.add("val");

    }

    private void addNonStandardResidue(Residue residue) {
        boolean isProtein = residue.polymer.getPolymerType().equals("polypeptide");
        residue.setNonStandard();
        Atom startAtom;
        if (residue.isCompliant()) {
            residue.addConnectors();
            startAtom = residue.getAtom("X");
        } else {
            startAtom = isProtein ? residue.getAtom("CAX") : residue.getAtom("C3'X");
        }
        //molecule.updateBondArray();
        residue.getLastBackBoneAtom().setProperty("connector", true);
        PathIterator pI = new PathIterator(residue);
        NodeValidator nV = new NodeValidator();
        pI.init(nV);
        pI.processPatterns();
        pI.setProperties("ar", "AROMATIC");
        pI.setProperties("res", "RESONANT");
        pI.setProperties("r", "RING");
        pI.setHybridization();
        residue.genMeasuredTree(startAtom);
        residue.removeConnectors();

        Atom firstAtom = residue.getFirstBackBoneAtom();
        Atom parent = connectAtom;
        connectBond = new Bond(parent, firstAtom);
        Bond bond = connectBond;
        parent.bonds.add(connectPosition, connectBond);
        parent.addBond(bond);
        residue.addBond(bond);

        firstAtom.parent = parent;
        if (isProtein) {
            firstAtom.irpIndex = 0;
        }
        bond = new Bond(firstAtom, parent);
        firstAtom.addBond(bond);
        this.connectAtom = residue.getLastBackBoneAtom();

        // fixme this needs to be changed for non-amino acid residue atoms
        if (isProtein) {
            residue.getAtom("O").dihedralAngle = (float) Math.PI;
        }
    }

    public enum PRFFields {

        ATOM("ATOM", 6, 6) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, String coordSetName)
                    throws MoleculeIOException {
                checkFieldCount(fields);
                String aName = fields[1];
                String aType = fields[2];
                String aTypeName = "H";
                if (aType.substring(0, 1).equals("M") && aName.startsWith("H")) {
                    aTypeName = "H";
                } else if (aType.substring(0, 1).equals("A") && aName.startsWith("C")) {
                    aTypeName = "C";
                } else {
                    // fixme wrong for two character elements
                    aTypeName = aType.substring(0, 1);
                }
                Atom atom = new Atom(aName, aTypeName);
                // atom.setPointValidity(true);
                atom.entity = residue;
                atom.name = aName;
                residue.addAtom(atom);
                atom.setType(aType);

                if (aType.substring(0, 1).equals("M") && atom.name.startsWith("H")) {
                    atom.setAtomicNumber("H");
                } else if (aType.substring(0, 1).equals("A") && atom.name.startsWith("C")) {
                    atom.setAtomicNumber("C");
                } else {
                    // fixme wrong for two character elements
                    atom.setAtomicNumber(aType.substring(0, 1));
                }
                atom.bondLength = Float.parseFloat(fields[3]);
                atom.valanceAngle = (float) (Float.parseFloat(fields[4]) * Math.PI / 180.0);
                atom.dihedralAngle = (float) (Float.parseFloat(fields[5]) * Math.PI / 180.0);
            }
        },
        FAMILY("FAMILY", 3, 10) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, String coordSetName)
                    throws MoleculeIOException {
                checkFieldCount(fields);
                final Atom parent;
                String parentField = fields[1];
                Atom refAtom = residue.getAtom(fields[2]);
                boolean connectee = false;
                if (parentField.equals("-")) {
                    if (sequence.connectAtom != null) {
                        parent = sequence.connectAtom;
                        Bond connectBond = new Bond(parent, refAtom);
                        parent.bonds.add(sequence.connectPosition, connectBond);
                        connectee = true;
                    } else {
                        parent = null;
                    }
                } else {
                    parent = residue.getAtom(parentField);
                }
                Bond bond = null;
                if (parent != null) {
                    if (connectee) {
                        bond = new Bond(parent, refAtom);
                        parent.addBond(bond);
                        residue.addBond(bond);
                    }
                    refAtom.parent = parent;
                    bond = new Bond(refAtom, parent);
                    refAtom.addBond(bond);
                }
                for (int iField = 3; iField < fields.length; iField++) {
                    String atomName = fields[iField];
                    Order order = Order.SINGLE;
                    if (atomName.charAt(0) == '=') {
                        atomName = atomName.substring(1);
                        order = Order.DOUBLE;
                    }
                    Atom daughterAtom = null;
                    boolean ringClosure = false;
                    boolean connector = false;
                    if (atomName.equals("+")) {
                        sequence.connectAtom = refAtom;
                        connector = true;
                        sequence.connectPosition = iField - 3;
                    } else if (atomName.startsWith("r")) { // close ring
                        daughterAtom = residue.getAtom(atomName.substring(1));
                        ringClosure = true;
                    } else if (atomName.startsWith("-")) { // close ring
                        daughterAtom = residue.getAtom(atomName.substring(1));
                        ringClosure = true;
                    } else {
                        daughterAtom = residue.getAtom(atomName);
                    }
                    if (!connector && (daughterAtom == null)) {
                        throw new MoleculeIOException("Can't find daughter atom \"" + atomName + "\"");
                    }
                    //                    if (!connector && daughterAtom.getName().startsWith("H")) {
                    if (!connector && !ringClosure) {
                        bond = new Bond(daughterAtom, refAtom);
                        daughterAtom.addBond(bond);
                        daughterAtom.parent = refAtom;
                    }
                    if (!connector) {
                        bond = new Bond(refAtom, daughterAtom, order);
                        bond.setRingClosure(ringClosure);
                        refAtom.addBond(bond);
                        if (!connector && !ringClosure) {
                            daughterAtom.parent = refAtom;
                        }
                        residue.addBond(bond);
                    }
                }
            }
        },
        ANGLE("ANGLE", 4, 4) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, String coordSetName)
                    throws MoleculeIOException {
                checkFieldCount(fields);
                Atom angleAtom = residue.getAtom(fields[1]);
                angleAtom.irpIndex = Integer.parseInt(fields[3]);
            }
        },
        PSEUDO("PSEUDO", 4, 12) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, String coordSetName)
                    throws MoleculeIOException {
                checkFieldCount(fields);
                String pseudoAtomName = fields[1];
                ArrayList<String> pseudoArray = new ArrayList<String>();
                for (int iField = 2; iField < fields.length; iField++) {
                    String atomName = fields[iField];
                    pseudoArray.add(atomName);
                }
                residue.addPseudoAtoms(pseudoAtomName, pseudoArray);
            }
        },
        CRAD("CRAD", 6, 6) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, String coordSetName)
                    throws MoleculeIOException {
            }
        },
        ATREE("ATREE", 6, 6) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, String coordSetName)
                    throws MoleculeIOException {
                Atom atom1 = residue.getAtom(fields[1]);
                if (sequence.connectBranch != null) {
                    sequence.connectBranch.branchAtoms[sequence.connectBranch.branchAtoms.length - 1] = atom1;
                    sequence.connectBranch = null;
                }
                if (atom1.getParent() != null) {
                    atom1.branchAtoms = new Atom[fields.length - 2];
                    for (int iBranch = 0; iBranch < (fields.length - 2); iBranch++) {
                        String branchField = fields[iBranch + 2];
                        if (branchField.equals("-")) {
                        } else if (branchField.equals("+")) {
                            sequence.connectBranch = atom1;
                        } else {
                            Atom atom = residue.getAtom(branchField);
                            //System.out.println("branch " + atom1.getFullName() + " " + atom.getFullName());
                            atom1.branchAtoms[iBranch] = atom;
                        }
                    }
                }
            }
        },;
        private String description;
        private int minFields;
        private int maxFields;

        PRFFields(final String description, final int minFields, final int maxFields) {
            this.description = description;
            this.minFields = minFields;
            this.maxFields = maxFields;
        }

        public abstract void processLine(Sequence sequence, String[] fields, Residue residue, String coordSetName)
                throws MoleculeIOException;

        String getFieldString(String[] fields) {
            StringBuilder sBuilder = new StringBuilder();
            for (String field : fields) {
                sBuilder.append(field);
                sBuilder.append(' ');
            }
            return sBuilder.toString();
        }

        int getMinFields() {
            return minFields;
        }

        void checkFieldCount(final String[] fields) throws IllegalArgumentException {
            if (fields.length < minFields) {
                throw new IllegalArgumentException(
                        "Must have at least \"" + minFields + "\" fields for \"" + description + "\"");
            }
            if (fields.length > maxFields) {
                throw new IllegalArgumentException(
                        "Must have less than \"" + maxFields + "\" fields for \"" + description + "\"");
            }

        }
    }

    public Molecule getMolecule() {
        return molecule;
    }

    public void removeBadBonds() {
        if ((connectBond != null) && (connectBond.end == null)) {
            connectBond.begin.removeBondTo(null);
        }
    }

    public BufferedReader getLocalResidueReader(final String fileName) {
        File file = new File(fileName);
        String reslibDir = PDBFile.getLocalReslibDir();
        BufferedReader bf = null;
        if (!reslibDir.equals("")) {
            String fileNameLocal = reslibDir + "/" + file.getName();
            File fileLocal = new File(fileNameLocal);
            if (fileLocal.canRead()) {
                try {
                    bf = new BufferedReader(new FileReader(fileLocal));
                } catch (IOException ioe) {
                    bf = null;
                }
            }
        }
        return bf;
    }

    public ArrayList<String[]> loadResidue(final String fileName, boolean throwTclException) throws MoleculeIOException {
        BufferedReader bf;
        ArrayList<String[]> fieldArray = new ArrayList<String[]>();
        bf = getLocalResidueReader(fileName);
        if (bf == null) {
            try {
                if (fileName.startsWith("resource:")) {
                    InputStream inputStream = this.getClass().getResourceAsStream(fileName.substring(9));
                    if (inputStream == null) {
                        System.out.println("reso null " + fileName);
                    } else {
                        bf = new BufferedReader(new InputStreamReader(inputStream));
                    }
                } else {
                    bf = new BufferedReader(new FileReader(fileName));
                }
            } catch (IOException ioe) {

                if (throwTclException) {
                    throw new MoleculeIOException("Cannot open the file " + fileName);
                }

            }
        }
        if (bf != null) {
            try {
                while (bf.ready()) {
                    String line = bf.readLine();
                    if (line == null) {
                        break;
                    }
                    line = line.trim();
                    String[] fields = line.split("\\s+");
                    fieldArray.add(fields);
                }
            } catch (IOException ioe) {
            }
        }
        return fieldArray;
    }

    public boolean addResidue(String fileName, Residue residue, String coordSetName, boolean throwTclException)
            throws MoleculeIOException {
        ArrayList<String[]> fieldArray = loadResidue(fileName, throwTclException);
        boolean result = false;
        if (fieldArray.size() > 0) {
            result = true;
            for (String[] fields : fieldArray) {
                try {
                    PRFFields prfField = PRFFields.valueOf(fields[0]);
                    if (prfField != null) {
                        prfField.processLine(this, fields, residue, coordSetName);
                    }
                } catch (IllegalArgumentException iAE) {
                    // ignore field
                }

            }
        } else {
            Compound compound = readResidue(fileName, coordSetName, residue);
            if (compound != null) {
                result = true;
                addNonStandardResidue(residue);
            }
            // FIXME : What happens if first residue is an unnatural residue
            if (this.connectAtom != null) {

                /* FIXME: must also change to accomodate whether nucleic acid or protein */
            }
        }
        return result;
    }

    public Compound readResidue(String fileName, String coordSetName, Residue residue) throws MoleculeIOException {
        String localResLibDir = PDBFile.getLocalReslibDir();
        File file = new File(fileName);
        String fileShortName = file.getName();
        String localFile = localResLibDir + "/" + fileShortName.split(".prf")[0];
        String[] exts = {".pdb", ".sdf"};
        Polymer polymer = (Polymer) molecule.getEntity(coordSetName);
        Compound compound = null;
        for (String ext : exts) {
            file = new File(localFile + ext);
            if (file.canRead()) {
                if (ext.equals(".pdb")) {
                    compound = PDBFile.readResidue(localFile + ext, null, molecule, coordSetName, residue);
                } else {
                    compound = SDFile.readResidue(localFile + ext, null, molecule, coordSetName, residue);
                }
                break;
            }
        }
        return compound;
    }

    public Molecule read(String fileName) throws MoleculeIOException {
        File file = new File(fileName);
        int dotPos = file.getName().lastIndexOf('.');
        String polymerName;
        if (dotPos == -1) {
            polymerName = file.getName();
        } else {
            polymerName = file.getName().substring(0, dotPos);
        }
        read(fileName, polymerName);
        return molecule;
    }

    public Molecule read(String fileName, String polymerName) throws MoleculeIOException {
        ArrayList<String> inputStrings = new ArrayList<>();
        LineNumberReader lineReader = null;
        try {
            BufferedReader bf = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bf);
        } catch (FileNotFoundException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        }

        File file = new File(fileName);
        while (true) {
            String inputString = null;
            try {
                inputString = lineReader.readLine();
                if (inputString == null) {
                    lineReader.close();
                }
            } catch (IOException ioE) {
            }
            if (inputString != null) {
                inputStrings.add(inputString);
            } else {
                break;
            }
        }
        String parentDir = file.getParent();
        read(polymerName, inputStrings, parentDir);
        return molecule;
    }

    public Molecule read(String polymerName, ArrayList<String> inputStrings, String parentDir)
            throws MoleculeIOException {
        LineNumberReader lineReader = null;
        Polymer polymer = null;
        Residue residue = null;
        boolean setPolymerType = false;
        String iRes = "1";
        String[] stringArg = new String[2];
        Pattern pattern = Pattern.compile("[-/\\w/\\.]+");
        boolean gotMolecule = false;
        String molName = "";
        String coordSetName = molName;
        ArrayList<String> coordSetNames = new ArrayList<>();
        ArrayList<Polymer> polymers = new ArrayList<>();
        ArrayList<File> ligandFiles = new ArrayList<>();
        Set<String> isNotCapped = new TreeSet<>();
        String polymerType = null;
        for (String inputString : inputStrings) {
            inputString = inputString.trim();

            if (inputString.length() == 0) {
                continue;
            }
            if (inputString.charAt(0) == '#') {
                continue;
            }

            Matcher matcher = pattern.matcher(inputString);
            int nMatches = 0;
            String resName = "";

            for (int i = 0; i < stringArg.length; i++) {
                stringArg[i] = null;
            }

            while ((nMatches < stringArg.length) && matcher.find()) {
                stringArg[nMatches++] = inputString.substring(matcher.start(), matcher.end());
            }
            if (stringArg[0] == null) {
                throw new MoleculeIOException("readseq: error in inputString \"" + inputString + "\"");
            }

            boolean isResidue = false;

            if (stringArg[0].startsWith("-")) {
                gotMolecule = false;
                iRes = "1";
                if ("-molecule".startsWith(stringArg[0])) {
                    molName = stringArg[1];
                } else if ("-polymer".startsWith(stringArg[0])) {
                    polymerName = stringArg[1];
                    coordSetNames.clear();
                } else if ("-ptype".startsWith(stringArg[0])) {
                    setPolymerType = true;
                    polymerType = stringArg[1];
                } else if ("-nocap".startsWith(stringArg[0])) {
                    isNotCapped.add(polymerName);
                } else if ("-coordset".startsWith(stringArg[0])) {
                    coordSetNames.add(stringArg[1]);
                    coordSetName = stringArg[1];
                } else if ("-sdfile".startsWith(stringArg[0])) {
                    if (parentDir != null) {
                        // fixme should add coordset and file
                        ligandFiles.add((new File(parentDir, stringArg[1])));
                    }
                } else if ("-pdbfile".startsWith(stringArg[0])) {
                    if (parentDir != null) {
                        // fixme should add coordset and file
                        ligandFiles.add((new File(parentDir, stringArg[1])));
                    }
                } else if ("-ligand".startsWith(stringArg[0])) {
                    if (parentDir != null) {
                        // fixme should add coordset and file
                        ligandFiles.add((new File(parentDir, stringArg[1])));
                    }
                } else {
                    throw new MoleculeIOException("unknown option \"" + stringArg[0] + "\" in sequence file");
                }
            } else {
                resName = stringArg[0];
                isResidue = true;

                if (stringArg[1] != null) {
                    try {
                        iRes = stringArg[1];
                    } catch (NumberFormatException nfE) {
                        throw new MoleculeIOException(nfE.toString());
                    }
                }
            }

            if (!isResidue) {
                continue;
            } else if (!gotMolecule) {
                if (coordSetNames.isEmpty()) {
                    coordSetName = polymerName;
                    molName = polymerName;
                    coordSetNames.add(coordSetName);
                }

                polymer = initMolFromSeqFile(molName, polymerName, coordSetNames, polymerType);
                polymers.add(polymer);
                molecule = polymer.molecule;
                for (String cName : coordSetNames) {
                    if (!molecule.coordSetExists(cName)) {
                        molecule.addCoordSet(cName, polymer);
                    }
                }

                gotMolecule = true;
            }

            if (molecule == null) {
                throw new MoleculeIOException("can't create molecule");
            }

            int minus = resName.indexOf('-');

            if (minus > 0) {
                resName = resName.substring(0, minus);
            }

            int plus = resName.indexOf('+');

            if (plus > 0) {
                resName = resName.substring(0, minus);
            }
            String resFileName = resName.toLowerCase();
            int underIndex = resName.indexOf("_");
            if (underIndex != -1) {
                resName = resName.substring(0, underIndex);
            }

            resName = PDBAtomParser.pdbResToPRFName(resName, 'r');

            if (!setPolymerType) {
                if (residueAliases.values().contains(resName)) {
                    polymerType = "nucleicacid";
                    setPolymerType = true;
                } else if (AMINO_ACID_NAMES.contains(resName)) {
                    polymerType = "polypeptide";
                    setPolymerType = true;
                }
                /* if all unnatural, maybe set this by a flag from the yaml file with ptype */
                if (setPolymerType) {
                    polymer.setPolymerType(polymerType);
                }
            }

            residue = new Residue(iRes, resName.toUpperCase());
            residue.molecule = molecule;
            polymer.addResidue(residue);

            String reslibDir = PDBFile.getReslibDir();
            if (PDBFile.isIUPACMode()) {
                polymer.setNomenclature("IUPAC");
            } else {
                polymer.setNomenclature("XPLOR");
            }
            if (isNotCapped.contains(polymer.getName())) {
                polymer.setCapped(false);
            } else {
                polymer.setCapped(true);
            }
            addResidue(reslibDir + "/" + Sequence.getAliased(resFileName) + ".prf", residue, coordSetName, true);
            try {
                String[] matches = iRes.split("[^\\-0-9]+");

                if ((matches != null) && (matches.length > 0) && (matches[0] != null)) {
                    int nRes = Integer.parseInt(matches[0]);
                    nRes++;
                    iRes = String.valueOf(nRes);
                }
            } catch (NumberFormatException nfE) {
            }
        }
        for (Polymer cpolymer : polymers) {
            if (!isNotCapped.contains(cpolymer.getName())) {
                PDBFile.capPolymer(cpolymer);
            }
        }
        Molecule.makeAtomList();
        if (molecule != null) {
            molecule.genCoords(false);
            molecule.setupRotGroups();
        }
        for (File ligandFile : ligandFiles) {
            if (ligandFile.getPath().endsWith("pdb")) {
                PDBFile.readResidue(ligandFile.getPath(), null, molecule, coordSetName);
            } else {
                SDFile.readResidue(ligandFile.getPath(), null, molecule, coordSetName);
            }
        }
        if ((connectBond != null) && (connectBond.end == null)) {
            connectBond.begin.removeBondTo(null);
        }
        
        return molecule;
    }

    Polymer initMolFromSeqFile(String molName, String polymerName, ArrayList<String> coordSetNames, String polymerType)
            throws MoleculeIOException {

        if ((molName == null) || molName.equals("")) {
            if (Molecule.getActive() == null) {
                throw new MoleculeIOException("No default molecule");
            } else {
                molecule = Molecule.getActive();

                if (molecule == null) {
                    molecule = new Molecule(polymerName);
                }
            }
        } else {
            molecule = Molecule.get(molName);

            if (molecule == null) {
                molecule = new Molecule(molName);
            }
        }

        if (molecule == null) {
            return null;
        }

        Polymer polymer = null;
        Entity entity = molecule.getEntity(polymerName);

        if (entity == null) {
            polymer = new Polymer(polymerName);
            polymer.molecule = molecule;
            polymer.assemblyID = molecule.entityLabels.size() + 1;

            if (coordSetNames.isEmpty()) {
                molecule.addEntity(polymer, "");
            } else {
                //System.err.println("add entity named" + polymerName + " with cset " + ((String) coordSetNames.get(0)));
                molecule.addEntity(polymer, (String) coordSetNames.get(0));
            }
        } else {
            polymer = (Polymer) entity;
        }
        if (polymerType != null) {
            polymer.setPolymerType(polymerType);
        }

        return polymer;
    }

    public static String getAliased(String name) {
        String newName = residueAliases.get(name);
        if (newName == null) {
            newName = name;
        }
        return newName;
    }
}
