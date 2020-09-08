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
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author brucejohnson
 */
public class SDFile {

    static final int MOLECULE = 0;
    static final int ATOM = 1;
    static final int BOND = 2;
    static final int LIST = 3;
    static final int STEXT = 4;
    static final int PROP = 5;
    static final int FREE = 6;
    static final int VALUE = 7;
    static Pattern pattern = Pattern.compile("> +<(.*)>");

    int nMols = 0;
    Molecule molecule = null;
    Compound compound = null;
    String molName = "";
    int nItems = 0;
    int nAtoms = 0;
    int nBonds = 0;
    int nLists = 0;
    int nStexts = 0;
    int nProps = 0;
    int nProds = 0;
    int nReacs = 0;
    int nInters = 0;
    boolean chiral = false;
    int structureNumber = 0;
    List<Atom> atomList = new ArrayList<>();

    void getMolName(String fileName) {
        File file = new File(fileName);

        String fileTail = file.getName();
        String fileRoot;
        int dot = fileTail.indexOf(".");

        if (dot != -1) {
            fileRoot = fileTail.substring(0, dot);
        } else {
            fileRoot = fileTail;
        }
        if (nMols == 0) {
            molName = fileRoot;
        } else {
            molName = fileRoot + nMols;
        }
    }

    void readHeader(String fileName, LineNumberReader lineReader, Compound compound) throws MoleculeIOException {
        String[] header = new String[4];
        try {
            for (int i = 0; i < header.length; i++) {
                header[i] = lineReader.readLine();
                System.out.println(header[i]);
            }
        } catch (IOException ioe) {
            throw new MoleculeIOException("Couldn't read first four lines");
        }
        getMolName(fileName);

        nMols++;
        if (compound != null) {
            this.compound = compound;
            this.molecule = compound.molecule;
        } else {
            molecule = new Molecule(molName);
            this.compound = new Compound("1", molName);
            this.compound.entityID = 1;
            this.compound.assemblyID = 1;
            this.compound.molecule = molecule;
            molecule.addEntity(this.compound, molName);
            molecule.structures.add(0);
        }

        String string = header[3];
        if (string.length() < 33) {
            throw new MoleculeIOException(
                    "Read SD file: nPar line too short " + header[0] + "\n" + header[1] + "\n" + header[2] + "\n" + header[3]);
        }
        setupItemCounts(string);
    }

    private void setupItemCounts(String string) {
        nAtoms = Integer.parseInt(string.substring(0, 3).trim());
        nBonds = Integer.parseInt(string.substring(3, 6).trim());
        nLists = Integer.parseInt(string.substring(7, 9).trim());

        String valueString = string.substring(14, 15).trim();

        if (valueString.length() > 0) {
            chiral = (Integer.parseInt(valueString) == 1);
        }

        valueString = string.substring(15, 18).trim();

        if (valueString.length() > 0) {
            nStexts = Integer.parseInt(valueString);
        }

        valueString = string.substring(18, 21).trim();

        if (valueString.length() > 0) {
            nReacs = Integer.parseInt(valueString);
        }

        valueString = string.substring(21, 24).trim();

        if (valueString.length() > 0) {
            nProds = Integer.parseInt(valueString);
        }

        valueString = string.substring(24, 27).trim();

        if (valueString.length() > 0) {
            nInters = Integer.parseInt(valueString);
        }

        valueString = string.substring(27, 30).trim();

        if (valueString.length() > 0) {
            nProps = Integer.parseInt(valueString);
        }
        if ((nProds != 0) || (nReacs != 0) || (nInters != 0)) {
            System.err.println("unused nProd, nReacs, or nInters");
        }
    }

    public void readAtoms(LineNumberReader lineReader) throws IOException, MoleculeIOException {
        for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
            String string = lineReader.readLine();
            if (string.length() < 34) {
                throw new MoleculeIOException(
                        "Read SD file: atom line too short");
            }

            double x = Double.parseDouble(string.substring(0, 10).trim());
            double y = Double.parseDouble(string.substring(10, 20).trim());
            double z = Double.parseDouble(string.substring(20, 30).trim());

            String atomSymbol = string.substring(31, 34).trim();
            String aname = atomSymbol + (iAtom + 1);

            Atom atom = Atom.genAtomWithElement(aname, atomSymbol);
            // fixme should do this elsewhere
            atom.setPointValidity(structureNumber, true);
            Point3 pt = new Point3(x, y, z);
            atom.setPoint(structureNumber, pt);

            String massDiffString = string.substring(34, 36).trim();
            int massDiff = 0;
            try {
                massDiff = Integer.parseInt(massDiffString);
            } catch (NumberFormatException nfE) {
            }
            if (massDiff != 0) {
                System.err.println("unused massDiff");
            }

            String chargeString = string.substring(36, 39).trim();

            try {
                int chargeValue = Integer.parseInt(chargeString);

                switch (chargeValue) {
                    case 1: {
                        atom.setFormalCharge(3);

                        break;
                    }

                    case 2: {
                        atom.setFormalCharge(2);

                        break;
                    }

                    case 3: {
                        atom.setFormalCharge(1);

                        break;
                    }

                    case 5: {
                        atom.setFormalCharge(-1);

                        break;
                    }

                    case 6: {
                        atom.setFormalCharge(-2);

                        break;
                    }

                    case 7: {
                        atom.setFormalCharge(-3);

                        break;
                    }

                    default: {
                        atom.setFormalCharge(0);

                        break;
                    }
                }
            } catch (NumberFormatException nfE) {
            }
            /*
                 * String stereoString = string.substring(39, 42).trim(); int
                 * stereoValue = 0; try { stereoValue =
                 * Integer.parseInt(stereoString); } catch
                 * (NumberFormatException nfE) { } atom.setStereo(stereoValue);
             */

            atom.entity = compound;
            compound.addAtom(atom);
            atomList.add(atom);
            nItems++;
        }
    }

    void readBonds(LineNumberReader lineReader) throws MoleculeIOException, IOException {
        for (int i = 0; i < nBonds; i++) {
            String string = lineReader.readLine();
            try {
                int iBond = Integer.parseInt(string.substring(0, 3).trim()) - 1;
                int jBond = Integer.parseInt(string.substring(3, 6).trim()) - 1;
                int order = Integer.parseInt(string.substring(6, 9).trim());
                int stereo = Integer.parseInt(string.substring(9, 12).trim());

                switch (stereo) {
                    case 1:
                        stereo = Bond.STEREO_BOND_UP;
                        break;
                    case 4:
                        stereo = Bond.STEREO_BOND_EITHER;
                        break;
                    case 6:
                        stereo = Bond.STEREO_BOND_DOWN;
                        break;
                    default:
                        stereo = 0;
                        break;
                }

                //System.err.println (iBond + " " + jBond + " " + atomList.size ());
                if (atomList != null) {
                    if ((iBond < atomList.size()) && (jBond < atomList.size())) {
                        Atom atom1 = (Atom) atomList.get(iBond);
                        Atom atom2 = (Atom) atomList.get(jBond);

                        Atom.addBond(atom1, atom2, Order.getOrder(order), stereo, false);
                    } else {
                        System.err.println("error in adding bond to molecule "
                                + molName);
                    }
                }
            } catch (NumberFormatException nFE) {
                throw new MoleculeIOException("Bad bond data " + string);
            }
        }
    }

    void readLists(LineNumberReader lineReader) throws IOException {
        for (int iList = 0; iList < nLists; iList++) {
            String string = lineReader.readLine();
        }
    }

    void readStexts(LineNumberReader lineReader) throws IOException {
        for (int iList = 0; iList < nStexts; iList++) {
            String string = lineReader.readLine();
        }
    }

    void readProps(LineNumberReader lineReader) throws IOException {
        String string;
        while ((string = lineReader.readLine()) != null) {
            string = string.trim();
            if (string.equals("M  END")) {
                break;
            }
            if (string.charAt(0) == 'V') {
                String atomSpec = string.substring(3, 6).trim();
                Integer index = Integer.parseInt(atomSpec);
                if (index < 1) {
                    System.out.println("no index at " + string);
                } else if (string.length() > 7) {
                    atomList.get(index - 1).setProperty("V", string.substring(7));
                }
            }

        }

    }

    void readFreeForm(LineNumberReader lineReader) throws IOException {
        String string;
        StringBuilder sBuilder = new StringBuilder();
        while ((string = lineReader.readLine()) != null) {
            String valueName;
            string = string.trim();
            if (string.length() != 0) {
                if (string.equals("$$$$")) {
                    break;
                } else if (string.startsWith(">")) {
                    Matcher matcher = pattern.matcher(string);
                    sBuilder.setLength(0);
                    if (matcher.matches()) {
                        valueName = matcher.group(1);
                        while (true) {
                            string = lineReader.readLine();
                            if (string == null) {
                                break;
                            }
                            String value = string.trim();
                            if (value.length() == 0) {
                                break;
                            }
                            if (sBuilder.length() > 0) {
                                sBuilder.append("\n");
                            }
                            
                            if (value.endsWith("\\")) {
                                value = value.substring(0, value.length() - 1);
                            }
                            sBuilder.append(value);
                        }
                        if (valueName.equals("MDLNUMBER")) {
                            molecule.reName(molecule, compound, molName, sBuilder.toString());
                            molName = molecule.name;
                        } else {
                            molecule.setProperty(valueName, sBuilder.toString());
                        }

                    }

                }
            }
        }

    }

    public Molecule readMol(String fileName, String fileContent) throws MoleculeIOException {
        return readMol(fileName, fileContent, null);
    }

    public Molecule readMol(String fileName, String fileContent, Compound compound)
            throws MoleculeIOException {
        LineNumberReader lineReader = null;

        try {
            if (fileContent == null) {
                BufferedReader bf = new BufferedReader(new FileReader(fileName));
                lineReader = new LineNumberReader(bf);
            } else {
                StringReader sf = new StringReader(fileContent);
                lineReader = new LineNumberReader(sf);
            }
            readHeader(fileName, lineReader, compound);
            readAtoms(lineReader);
            readBonds(lineReader);
            readLists(lineReader);
            readStexts(lineReader);
            readProps(lineReader);
            readFreeForm(lineReader);
        } catch (IOException ioE) {
            int iLine = lineReader == null ? -1 : lineReader.getLineNumber();
            throw new MoleculeIOException("error reading at line " + iLine);
        }
        System.out.println("read mol " + nAtoms);
        molecule.getAtomTypes();
        return molecule;
    }

    public static Compound read(String fileName, String fileContent, Molecule molecule, String coordSetName) throws MoleculeIOException {
        return read(fileName, fileContent, molecule, coordSetName, null);
    }

    public static Compound read(String fileName, String fileContent, Molecule molecule, String coordSetName, Residue residue) throws MoleculeIOException {
        if (coordSetName == null) {
            if (molecule != null) {
                coordSetName = ((CoordSet) molecule.coordSets.values().iterator().next()).getName();
            } else {
                coordSetName = "mol";
            }
        }
        Compound compound = null;
        SDFile sdFile = new SDFile();
        if (molecule != null) {
            sdFile.getMolName(fileName);
            String compoundName = sdFile.molName;
            compound = residue != null ? residue : new Compound("1", compoundName);;
            compound.molecule = molecule;
            compound.assemblyID = molecule.entityLabels.size() + 1;
            if (residue == null) {
                molecule.addEntity(compound, coordSetName);
            }
            sdFile.readMol(fileName, fileContent, compound);
        } else {
            System.out.println("Creating molecule");
            molecule = sdFile.readMol(fileName, fileContent);
            compound = molecule.getLigands().get(0);
        }
        return compound;

    }

    public static Molecule read(String fileName, String fileContent)
            throws MoleculeIOException {
        SDFile sdFile = new SDFile();
        return sdFile.readMol(fileName, fileContent);
    }
}
