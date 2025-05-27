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
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author brucejohnson
 */
public class Mol2File {
    private static final Logger log = LoggerFactory.getLogger(Mol2File.class);

    private static final int MOLECULE = 0;
    private static final int ATOM = 1;
    private static final int BOND = 2;
    private static final int LIST = 3;
    private static final int STEXT = 4;
    private static final int PROP = 5;
    private static final int FREE = 6;
    private static final int VALUE = 7;
    private static final Pattern PATTERN = Pattern.compile("> +<(.*)>");

    int nMols = 0;
    MoleculeBase molecule = null;
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
        String[] header = new String[6];
        try {
            while (true) {
                String line = lineReader.readLine();
                if (line == null) {
                    return;
                }
                line = line.trim();
                if (line.contains("@<TRIPOS>MOLECULE")) {
                    break;
                }
            }

            for (int i = 0; i < header.length; i++) {
                header[i] = lineReader.readLine();
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
            molecule = MoleculeFactory.newMolecule(molName);
            this.compound = new Compound("1", molName);
            this.compound.entityID = 1;
            this.compound.assemblyID = 1;
            this.compound.molecule = molecule;
            molecule.addEntity(this.compound, molName);
            molecule.structures.add(0);
        }

        String string = header[1];
        setupItemCounts(string);
    }

    private void setupItemCounts(String string) {
        String[] fields = string.split(" +");
        nAtoms = Integer.parseInt(fields[0].trim());
        if (fields.length > 1) {
            nBonds = Integer.parseInt(fields[1].trim());
        }
    }

    public void readAtoms(LineNumberReader lineReader) throws IOException, MoleculeIOException {
        try {
            while (true) {
                String line = lineReader.readLine();

                if (line == null) {
                    return;
                }
                line = line.trim();

                if (line.contains("@<TRIPOS>ATOM")) {
                    break;
                }
            }

        } catch (IOException ioe) {
            throw new MoleculeIOException("Couldn't read atom lines");
        }

        for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
            String string = lineReader.readLine().trim();
            String[] fields = string.split(" +");

            if (fields.length < 6) {
                throw new MoleculeIOException(
                        "Read Mol2 file: too few atom fields");
            }

            double x = Double.parseDouble(fields[2].trim());
            double y = Double.parseDouble(fields[3].trim());
            double z = Double.parseDouble(fields[4].trim());
            String aName = fields[1].trim();
            String atomType = fields[5];
            int dotPos = atomType.indexOf(".");

            String atomSymbol;
            boolean aromatic = false;
            if (dotPos == -1) {
                atomSymbol = atomType;
            } else {
                atomSymbol = atomType.substring(0, dotPos);
                String type = atomType.substring(dotPos + 1);
                if (type.equals("ar")) {
                    aromatic = true;
                }
            }

            Atom atom = Atom.genAtomWithElement(aName, atomSymbol);
            // fixme should do this elsewhere
            atom.setPointValidity(structureNumber, true);
            Point3 pt = new Point3(x, y, z);
            atom.setPoint(structureNumber, pt);
            atom.setFlatPoint(pt);
            atom.setFlag(Atom.AROMATIC, aromatic);

            if (fields.length > 8) {
                String chargeString = fields[8].trim();
                try {
                    float charge = Float.parseFloat(chargeString);
                    atom.setCharge(charge);

                } catch (NumberFormatException nfE) {
                    log.warn("Unable to parse atom charge.", nfE);
                }
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
        try {
            while (true) {
                String line = lineReader.readLine();
                if (line == null) {
                    return;
                }
                line = line.trim();

                if (line.contains("@<TRIPOS>BOND")) {
                    break;
                }
            }

        } catch (IOException ioe) {
            throw new MoleculeIOException("Couldn't read first bond lines");
        }
        for (int i = 0; i < nBonds; i++) {
            String string = lineReader.readLine();
            string = string.trim();

            try {
                String[] fields = string.split(" +");

                if (fields.length < 4) {
                    throw new MoleculeIOException(
                            "Read Mol2 file: too few bond fields");
                }

                int iBond = Integer.parseInt(fields[1].trim()) - 1;
                int jBond = Integer.parseInt(fields[2].trim()) - 1;
                String type = fields[3];
                int order = 1;
                int stereo = 0;
                boolean aromatic = false;

                switch (type) {
                    case "1":
                        order = 1;
                        break;
                    case "2":
                        order = 2;
                        break;
                    case "3":
                        order = 3;
                        break;
                    case "ar":
                        aromatic = true;
                        break;
                    default:
                        break;
                }

                if (atomList != null) {
                    if ((iBond < atomList.size()) && (jBond < atomList.size())) {
                        Atom atom1 = (Atom) atomList.get(iBond);
                        Atom atom2 = (Atom) atomList.get(jBond);
                        if (aromatic) {
                            atom1.setFlag(Atom.AROMATIC, aromatic);
                            atom2.setFlag(Atom.AROMATIC, aromatic);
                        }

                        Atom.addBond(atom1, atom2, Order.getOrder(order), stereo, false);
                    } else {
                        log.warn("error in adding bond to molecule {}", molName);
                    }
                }
            } catch (NumberFormatException nFE) {
                throw new MoleculeIOException("Bad bond data " + string);
            }
        }
    }

    public MoleculeBase readMol(String fileName, String fileContent) throws MoleculeIOException {
        return readMol(fileName, fileContent, null);
    }

    public MoleculeBase readMol(String fileName, String fileContent, Compound compound)
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
        } catch (IOException ioE) {
            int iLine = lineReader == null ? -1 : lineReader.getLineNumber();
            throw new MoleculeIOException("error reading at line " + iLine);
        }
        ProjectBase.getActive().putMolecule(molecule);
        molecule.getAtomTypes();
        return molecule;
    }

    public static Compound read(String fileName, String fileContent, MoleculeBase molecule, String coordSetName) throws MoleculeIOException {
        return read(fileName, fileContent, molecule, coordSetName, null);
    }

    public static Compound read(String fileName, String fileContent, MoleculeBase molecule, String coordSetName, Residue residue) throws MoleculeIOException {
        if (coordSetName == null) {
            if (molecule != null) {
                coordSetName = ((CoordSet) molecule.coordSets.values().iterator().next()).getName();
            } else {
                coordSetName = "mol";
            }
        }
        Compound compound = null;
        Mol2File sdFile = new Mol2File();
        if (molecule != null) {
            sdFile.getMolName(fileName);
            String compoundName = sdFile.molName;
            compound = residue != null ? residue : new Compound("1", compoundName);
            ;
            compound.molecule = molecule;
            compound.assemblyID = molecule.entityLabels.size() + 1;
            if (residue == null) {
                molecule.addEntity(compound, coordSetName);
            }
            sdFile.readMol(fileName, fileContent, compound);
        } else {
            molecule = sdFile.readMol(fileName, fileContent);
            compound = molecule.getLigands().get(0);
        }
        return compound;

    }

    public static MoleculeBase read(String fileName, String fileContent)
            throws MoleculeIOException {
        Mol2File sdFile = new Mol2File();
        return sdFile.readMol(fileName, fileContent);
    }
}
