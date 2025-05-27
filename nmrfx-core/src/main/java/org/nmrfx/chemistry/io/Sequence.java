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

import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.Residue.RES_POSITION;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author brucejohnson
 */
public class Sequence {
    private static final Logger log = LoggerFactory.getLogger(Sequence.class);
    private static final boolean USE_COARSE = false;
    private static final Map<String, String> RESIDUE_ALIASES = new HashMap<>();
    private static final List<String> AMINO_ACID_NAMES = new ArrayList<>();

    static {
        RESIDUE_ALIASES.put("rade", "a");
        RESIDUE_ALIASES.put("ra", "a");
        RESIDUE_ALIASES.put("rgua", "g");
        RESIDUE_ALIASES.put("rg", "g");
        RESIDUE_ALIASES.put("rcyt", "c");
        RESIDUE_ALIASES.put("rc", "c");
        RESIDUE_ALIASES.put("rura", "u");
        RESIDUE_ALIASES.put("ura", "u");
        RESIDUE_ALIASES.put("ruri", "u");
        RESIDUE_ALIASES.put("ru", "u");
        RESIDUE_ALIASES.put("dade", "da");
        RESIDUE_ALIASES.put("dgua", "dg");
        RESIDUE_ALIASES.put("dcyt", "dc");
        RESIDUE_ALIASES.put("dthy", "dt");
        RESIDUE_ALIASES.put("dura", "du");
        RESIDUE_ALIASES.put("duri", "du");
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

    private Atom connectAtom = null;
    private Bond connectBond = null;
    private Atom connectBranch = null;
    private int connectPosition = -1;
    private MoleculeBase molecule;
    private String entryAtomName = null;
    private String exitAtomName = null;

    public Sequence() {
    }

    public Sequence(MoleculeBase molecule) {
        this.molecule = molecule;
    }

    public void newPolymer() {
        connectBranch = null;
    }

    /**
     * makeConnection creates a bond for @param residue to the previous
     * residue and sets this.connectAtom for the next residue in the
     * sequence.
     */
    public void makeConnection(Residue residue) {
        Atom firstAtom = residue.getFirstBackBoneAtom();
        Atom parent = connectAtom != null ? connectAtom : null;
        if (parent != null) {
            connectBond = new Bond(parent, firstAtom);
            Bond bond = connectBond;
            parent.bonds.add(connectPosition, bond);
            residue.addBond(bond);
        }
        firstAtom.parent = parent;
        boolean isProtein = residue.polymer.getPolymerType().contains("polypeptide");
        if (isProtein) {
            firstAtom.irpIndex = 0;
        }

        /* fixme This value was just chosen arbitrarily and worked. If set to 0, no longer works.
         Is this value always right*/
        this.connectPosition = 1;
        this.connectAtom = residue.getLastBackBoneAtom();

    }

    private void addNonStandardResidue(Residue residue) {
        residue.molecule.addNonStandardResidue(this, residue);
    }
    private static void createLinker(Atom atom1, Atom atom2, int numLinks,
                             double linkLen, double valAngle, double dihAngle) {
        /**
         * createLinker is a method to create a link between atoms in two
         * separate entities
         *
         * @param numLinks number of linker atoms to use
         * @param atom1
         * @param atom2
         */

        if (atom1 == null || atom2 == null) {
            return;
        }
        Atom curAtom = atom1;
        Atom newAtom;
        String linkRoot = "X";
        for (int i = 1; i <= numLinks; i++) {
            newAtom = curAtom.add(linkRoot + Integer.toString(i), "X", Order.SINGLE);
            newAtom.bondLength = (float) linkLen;
            newAtom.dihedralAngle = (float) (dihAngle * Math.PI / 180.0);
            newAtom.valanceAngle = (float) (valAngle * Math.PI / 180.0);
            newAtom.irpIndex = 1;
            newAtom.rotActive = true;
            newAtom.setType("XX");

            curAtom = newAtom;
            if ((i == numLinks) && (atom2 != null)) {
                Atom.addBond(curAtom, atom2, Order.SINGLE, 0, false);
                atom2.parent = curAtom;
                curAtom.daughterAtom = atom2;
            }
        }
    }


    public enum PRFFields {

        ATOM("ATOM", 7, 8) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, RES_POSITION resPos, String coordSetName)
                    throws MoleculeIOException {
                checkFieldCount(fields);
                String aName = fields[1];
                String aType = fields[2];
                if (aType.endsWith("cg") && !USE_COARSE) {
                    return;
                }
                if (fields.length > 7) {
                    if (!checkQualifier(fields[7], resPos)) {
                        return;
                    }
                }
                Atom atom = Atom.genAtomWithType(aName, aType);
                atom.entity = residue;
                atom.name = aName;
                residue.addAtom(atom);
                atom.setType(aType);

                atom.bondLength = Float.parseFloat(fields[3]);
                atom.valanceAngle = (float) (Float.parseFloat(fields[4]) * Math.PI / 180.0);
                atom.dihedralAngle = (float) (Float.parseFloat(fields[5]) * Math.PI / 180.0);
                atom.charge = Float.parseFloat(fields[6]);
            }
        },
        FAMILY("FAMILY", 3, 10) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, RES_POSITION resPos, String coordSetName)
                    throws MoleculeIOException {

                checkFieldCount(fields);
                int nFields = fields.length;
                String lastField = fields[nFields - 1];

                boolean hasQualifier = lastField.contains("middle")
                        || lastField.contains("start") || lastField.contains("end") || lastField.contains("nop");
                if (hasQualifier) {
                    if (!checkQualifier(lastField, resPos)) {
                        return;
                    } else {
                        nFields--;
                    }
                }
                final Atom parent;
                String parentField = fields[1];
                Atom refAtom = residue.getAtom(fields[2]);
                if (refAtom == null) {
                    log.error("{}", Arrays.asList(fields));
                    throw new MoleculeIOException("No refAtom " + fields[2]);
                }
                boolean connectee = false;
                boolean addedLinker = false;
                if (parentField.equals("-")) {
                    if (sequence.connectAtom != null) {
                        parent = sequence.connectAtom;
                        if (parent.getTopEntity() != refAtom.getTopEntity()) {
                            createLinker(parent, refAtom, 6, 5.0, 110.0, 135.0);
                            addedLinker = true;
                        } else {
                            Bond connectBond = new Bond(parent, refAtom);
                            parent.bonds.add(sequence.connectPosition, connectBond);
                            connectee = true;
                        }
                    } else {
                        parent = null;
                    }
                } else {
                    parent = residue.getAtom(parentField);
                }
                Bond bond = null;
                if ((parent != null) && !addedLinker){
                    if (connectee) {
                        bond = new Bond(parent, refAtom);
                        parent.addBond(bond);
                        residue.addBond(bond);
                    }
                    refAtom.parent = parent;
                    bond = new Bond(refAtom, parent);
                    refAtom.addBond(bond);
                }
                for (int iField = 3; iField < nFields; iField++) {
                    String atomName = fields[iField];
                    if (atomName.endsWith("c") && !USE_COARSE) {
                        continue;
                    }
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
                        throw new MoleculeIOException("Can't find daughter atom \""
                                + atomName + "\"" + " while adding "
                                + residue.getName() + residue.getNumber()
                                + " " + resPos);
                    }
                    //                    if (!connector && daughterAtom.getName().startsWith("H")) {
                    if (!connector && !ringClosure) {
                        bond = new Bond(daughterAtom, refAtom);
                        daughterAtom.addBond(bond);
                        daughterAtom.parent = refAtom;
                    }
                    if (!connector && ringClosure) {
                        bond = new Bond(daughterAtom, refAtom);
                        bond.setRingClosure(true);
                        daughterAtom.addBond(bond);
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
        ANGLE("ANGLE", 4, 5) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, RES_POSITION resPos, String coordSetName)
                    throws MoleculeIOException {
                checkFieldCount(fields);

                if (fields.length > 4) {
                    if (!checkQualifier(fields[4], resPos)) {
                        return;

                    }
                }
                Atom angleAtom = residue.getAtom(fields[1]);
                angleAtom.irpIndex = Integer.parseInt(fields[3]);
            }
        },
        PSEUDO("PSEUDO", 4, 12) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, RES_POSITION resPos, String coordSetName)
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
            public void processLine(Sequence sequence, String[] fields, Residue residue, RES_POSITION resPos, String coordSetName)
                    throws MoleculeIOException {
            }
        },
        ATREE("ATREE", 6, 6) {
            @Override
            public void processLine(Sequence sequence, String[] fields, Residue residue, RES_POSITION resPos, String coordSetName)
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
                            atom1.branchAtoms[iBranch] = atom;
                        }
                    }
                }
            }
        },
        ;
        private String description;
        private int minFields;
        private int maxFields;

        PRFFields(final String description, final int minFields, final int maxFields) {
            this.description = description;
            this.minFields = minFields;
            this.maxFields = maxFields;
        }

        public abstract void processLine(Sequence sequence, String[] fields, Residue residue, RES_POSITION resPos, String coordSetName)
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

        boolean checkQualifier(String field, RES_POSITION resPos) {
            boolean result = false;
            String[] qualifiers = field.split("\\|");
            for (String qualifier : qualifiers) {
                if (resPos.name().equalsIgnoreCase(qualifier)) {
                    result = true;
                    break;
                }
            }
            return result;
        }
    }

    public MoleculeBase getMolecule() {
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
        ArrayList<String[]> fieldArray = new ArrayList<>();
        bf = getLocalResidueReader(fileName);
        if (bf == null) {
            try {
                if (fileName.startsWith("resource:")) {
                    InputStream inputStream = this.getClass().getResourceAsStream(fileName.substring(9));
                    if (inputStream == null) {
                        log.warn("resource null {}", fileName);
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
            try (BufferedReader bufferedReader = bf) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    line = line.trim();
                    String[] fields = line.split("\\s+");
                    fieldArray.add(fields);
                }
            } catch (IOException ioe) {
                log.warn(ioe.getMessage(), ioe);
            }
        }
        return fieldArray;
    }

    public boolean addResidue(String fileName, Residue residue, RES_POSITION resPos, String coordSetName, boolean throwTclException)
            throws MoleculeIOException {
        try {
            // make sure parameters are in (if they are already this does nothing)
            // so is quick to call
            AtomEnergyProp.readPropFile();
        } catch (IOException ex) {
            throw new MoleculeIOException("Coudn't load energy parameter file" + ex.getMessage());
        }
        ArrayList<String[]> fieldArray = loadResidue(fileName, throwTclException);
        boolean result = false;
        if (fieldArray.size() > 0) {
            residue.libraryMode(residue.isStandard());
            result = true;
            for (String[] fields : fieldArray) {
                try {
                    PRFFields prfField = PRFFields.valueOf(fields[0]);
                    if (prfField != null) {
                        prfField.processLine(this, fields, residue, resPos, coordSetName);
                    }
                } catch (IllegalArgumentException iAE) {
                    // ignore field
                }

            }
            if (entryAtomName != null || exitAtomName != null) {
                throw new IllegalArgumentException("Start and entry point atoms specified for a standard residue with seq file");
            }
        } else {
            Compound compound = readResidue(fileName, coordSetName, residue);
            if (compound != null) {
                result = true;
                addNonStandardResidue(residue);
            } else {
                result = false;
                log.warn("cant read {}", fileName);
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
        residue.setFirstBackBoneAtom(entryAtomName);
        residue.setLastBackBoneAtom(exitAtomName);
        entryAtomName = null;
        exitAtomName = null;
        String localFile = localResLibDir + "/" + fileShortName.split(".prf")[0];
        String[] exts = {".pdb", ".sdf"};
        Compound compound = null;
        for (String ext : exts) {
            file = new File(localFile + ext);
            if (file.canRead()) {
                if (ext.equals(".pdb")) {
                    compound = PDBFile.readResidue(localFile + ext, null, molecule, coordSetName, residue);
                } else {
                    compound = SDFile.read(localFile + ext, null, molecule, coordSetName, residue);
                }
                break;
            }
        }
        return compound;
    }

    public MoleculeBase read(String fileName) throws MoleculeIOException {
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

    public MoleculeBase read(String fileName, String polymerName) throws MoleculeIOException {
        ArrayList<String> inputStrings = new ArrayList<>();
        File file = new File(fileName);
        try (Stream<String> lines = Files.lines(file.toPath())) {
            lines.forEach(inputStrings::add);
        } catch (FileNotFoundException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }

        String parentDir = file.getParent();
        read(polymerName, inputStrings, parentDir);
        return molecule;
    }

    public MoleculeBase read(String polymerName, List<String> inputStrings, String parentDir) throws MoleculeIOException {
        return read(polymerName, inputStrings, parentDir, "");

    }

    public MoleculeBase read(String polymerName, List<String> inputStrings, String parentDir, String molName)
            throws MoleculeIOException {
        Polymer polymer = null;
        Residue residue = null;
        boolean setPolymerType = false;
        String iRes = "1";
        String[] stringArg = new String[3];
        Pattern pattern = Pattern.compile("[-/\\w/\\.:]+");
        String coordSetName = molName;
        ArrayList<String> coordSetNames = new ArrayList<>();
        ArrayList<Polymer> polymers = new ArrayList<>();
        ArrayList<File> ligandFiles = new ArrayList<>();
        Set<String> isNotCapped = new TreeSet<>();
        String polymerType = null;
        molecule = MoleculeFactory.getActive();
        // First sequence is always a new polymer even if no '-' args are added
        boolean newPolymer = true;
        RES_POSITION resPos = RES_POSITION.START;

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
                newPolymer = true;
                iRes = "1";
                if ("-molecule".startsWith(stringArg[0])) {
                    molName = stringArg[1];
                    /* NOTE: This molName is never used and would always be overwritten
                       by polymerName. With this in mind, modifications to make this
                       function not default to creating a molecule without editing
                       initMolFromSeqFile
                     */
                } else if ("-polymer".startsWith(stringArg[0])) {
                    polymerName = stringArg[1];
                    coordSetNames.clear();
                    resPos = RES_POSITION.START;
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
                } else if ("-entry".startsWith(stringArg[0])) {
                    entryAtomName = stringArg[1];
                } else if ("-exit".startsWith(stringArg[0])) {
                    exitAtomName = stringArg[1];
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
                if (stringArg[2] != null) {
                    if (stringArg[2].equals("middle")) {
                        resPos = RES_POSITION.MIDDLE;
                    }
                }
            }

            if (!isResidue) {
                continue;
            } else if (newPolymer) {
                if (coordSetNames.isEmpty()) {
                    coordSetName = polymerName;
                    // Prevent new molecules from being created everytime 
                    // readSequence is called 
                    molName = molecule == null ? polymerName : molecule.getName();
                    coordSetNames.add(coordSetName);
                }

                polymer = initMolFromSeqFile(molName, polymerName, coordSetNames, polymerType);
                polymers.add(polymer);
                molecule = polymer.molecule;
                int coordID = 1;
                for (String cName : coordSetNames) {
                    if (!molecule.coordSetExists(cName)) {
                        molecule.addCoordSet(cName, coordID++, polymer);
                    }
                }

                newPolymer = false;
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
            int colonIndex = resName.indexOf(":");
            String changeMode = "";
            if (colonIndex != -1) {
                changeMode = resName.substring(colonIndex + 1);
                resName = resName.substring(0, colonIndex);
                resFileName = resName;
            }
            int underIndex = resName.indexOf("_");
            if (underIndex != -1) {
                resName = resName.substring(0, underIndex);
            }

            resName = PDBAtomParser.pdbResToPRFName(resName, 'r');

            if (!setPolymerType) {
                if (RESIDUE_ALIASES.values().contains(resName)) {
                    polymerType = "nucleicacid";
                    setPolymerType = true;
                } else if (AMINO_ACID_NAMES.contains(resName)) {
                    polymerType = "polypeptide(L)";
                    setPolymerType = true;
                }
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
            addResidue(reslibDir + "/" + Sequence.getAliased(resFileName) + ".prf", residue, resPos, coordSetName, true);
            if (changeMode.equalsIgnoreCase("d")) {
                residue.toDStereo();
            }
            resPos = RES_POSITION.MIDDLE;
            try {
                String[] matches = iRes.split("[^\\-0-9]+");

                if ((matches != null) && (matches.length > 0) && (matches[0] != null)) {
                    int nRes = Integer.parseInt(matches[0]);
                    nRes++;
                    iRes = String.valueOf(nRes);
                }
            } catch (NumberFormatException nfE) {
                log.warn(nfE.getMessage(), nfE);
            }
        }
        for (Polymer cpolymer : polymers) {
            if (!isNotCapped.contains(cpolymer.getName())) {
                PDBFile.capPolymer(cpolymer);
            }
        }
        if (molecule != null) {
            molecule.updateAtomArray();
            molecule.genCoords(false);
            molecule.setupRotGroups();
        }
        for (File ligandFile : ligandFiles) {
            if (ligandFile.getPath().endsWith("pdb")) {
                PDBFile.readResidue(ligandFile.getPath(), null, molecule, coordSetName);
            } else {
                SDFile.read(ligandFile.getPath(), null, molecule, coordSetName);
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
            if (MoleculeFactory.getActive() == null) {
                throw new MoleculeIOException("No default molecule");
            } else {
                molecule = MoleculeFactory.getActive();

                if (molecule == null) {
                    molecule = MoleculeFactory.newMolecule(polymerName);
                }
            }
        } else {
            molecule = MoleculeFactory.getMolecule(molName);

            if (molecule == null) {
                molecule = MoleculeFactory.newMolecule(molName);
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
                molecule.addEntity(polymer, "", polymer.assemblyID);
            } else {
                molecule.addEntity(polymer, (String) coordSetNames.get(0), polymer.assemblyID);
            }
        } else {
            polymer = (Polymer) entity;
        }
        if (polymerType != null) {
            polymer.setPolymerType(polymerType);
        }
        ProjectBase.getActive().putMolecule(molecule);

        return polymer;
    }

    public static String getAliased(String name) {
        String newName = RESIDUE_ALIASES.get(name);
        if (newName == null) {
            newName = name;
        }
        return newName;
    }

    public void createLinker(int numLinks,
                             double linkLen, double valAngle, double dihAngle) {
        /**
         * createLinker is a method to create a link between atoms in two
         * separate entities
         *
         * @param numLinks number of linker atoms to use
         * @param atom1
         * @param atom2
         */

        Atom newAtom;
        String linkRoot = "X";
        for (int i = 1; i <= numLinks; i++) {
            newAtom = connectAtom.add(linkRoot + Integer.toString(i), "X", Order.SINGLE);
            newAtom.bondLength = (float) linkLen;
            newAtom.dihedralAngle = (float) (dihAngle * Math.PI / 180.0);
            newAtom.valanceAngle = (float) (valAngle * Math.PI / 180.0);
            newAtom.irpIndex = 1;
            newAtom.setType("XX");
            connectAtom = newAtom;
        }

        log.info("create linker {}", numLinks);
    }

}
