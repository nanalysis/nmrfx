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
import java.util.StringTokenizer;
import java.util.Vector;

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

    public static Molecule read(String fileName, String fileContent)
            throws MoleculeIOException {
        String molName = null;
        Molecule molecule = null;
        Compound compound = null;
        boolean firstAtom = true;
        String token = null;
        double x;
        double y;
        double z;
        int iAtom;
        int iBond;
        int jBond;
        String aname = null;
        Point3 pt;
        int structureNumber = 0;
        Atom atom1 = null;
        Atom atom2 = null;
        String string;
        LineNumberReader lineReader;
        int order;
        int stereo;
        int nItems = 0;
        int nAtoms = 0;
        int nBonds = 0;
        int nLists = 0;
        int nStexts = 0;
        int nProps = 0;
        int nProds = 0;
        int nReacs = 0;
        int nInters = 0;
        int nMols = 0;
        String valueName = null;
        String value;
        boolean chiral = false;
        int state = MOLECULE;

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

        Vector atomList = new Vector();

        if (atomList == null) {
            atomList = new Vector(128);
        }

        StringTokenizer tokenizer;

        while (true) {
            try {
                string = lineReader.readLine();
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
                Molecule.makeAtomList();
                molecule.getAtomTypes();

                return molecule;
            }

            if (string == null) {
                Molecule.makeAtomList();
                molecule.getAtomTypes();

                return molecule;
            }

            if (state == MOLECULE) {
                if (nItems < 3) {
                    nItems++;

                    continue;
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

                if (nMols == 0) {
                    molName = fileRoot;
                } else {
                    molName = fileRoot + nMols;
                }

                nMols++;
                molecule = new Molecule(molName);
                compound = null;
                firstAtom = true;
                atomList.setSize(0);
                molecule.structures.add(Integer.valueOf(0));

                if (string.length() < 33) {
                    throw new MoleculeIOException(
                            "Read SD file: nPar line too short");
                }

                String valueString = null;
                nAtoms = Integer.parseInt(string.substring(0, 3).trim());
                nBonds = Integer.parseInt(string.substring(3, 6).trim());
                nLists = Integer.parseInt(string.substring(7, 9).trim());

                valueString = string.substring(14, 15).trim();

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
                state = ATOM;
                nItems = 0;

                continue;
            } else if (state == ATOM) {
                if (string.length() < 34) {
                    throw new MoleculeIOException(
                            "Read SD file: atom line too short");
                }

                x = Double.parseDouble(string.substring(0, 10).trim());
                y = Double.parseDouble(string.substring(10, 20).trim());
                z = Double.parseDouble(string.substring(20, 30).trim());

                iAtom = nItems;

                String atomSymbol = string.substring(31, 34).trim();
                aname = atomSymbol + (iAtom + 1);

                if (compound == null) {
                    compound = new Compound("1", molName);
                    compound.molecule = molecule;
                    molecule.addEntity(compound, molName);
                }

                Atom atom = new Atom(aname, molName);
                atom.aNum = Atom.getElementNumber(atomSymbol);
                atom.setPointValidity(structureNumber, true);
                pt = atom.getPoint(structureNumber);
                pt = new Point3(x, y, z);
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

                if (firstAtom) {
                    firstAtom = false;
                }

                atom.entity = compound;
                compound.addAtom(atom);
                atomList.addElement(atom);
                nItems++;

                if (nItems == nAtoms) {
                    nItems = 0;
                    state = BOND;

                    continue;
                }
            } else if (state == BOND) {
                iBond = Integer.parseInt(string.substring(0, 3).trim()) - 1;
                jBond = Integer.parseInt(string.substring(3, 6).trim()) - 1;
                order = Integer.parseInt(string.substring(6, 9).trim());
                stereo = Integer.parseInt(string.substring(9, 12).trim());

                if (stereo == 1) {
                    stereo = Bond.STEREO_BOND_UP;
                } else if (stereo == 4) {
                    stereo = Bond.STEREO_BOND_EITHER;
                } else if (stereo == 6) {
                    stereo = Bond.STEREO_BOND_DOWN;
                } else {
                    stereo = 0;
                }

                //System.err.println (iBond + " " + jBond + " " + atomList.size ());
                if (atomList != null) {
                    if ((iBond < atomList.size()) && (jBond < atomList.size())) {
                        atom1 = (Atom) atomList.elementAt(iBond);
                        atom2 = (Atom) atomList.elementAt(jBond);

                        Atom.addBond(atom1, atom2, order, stereo, false);
                    } else {
                        System.err.println("error in adding bond to molecule "
                                + molName);
                    }
                }

                nItems++;

                if (nItems == nBonds) {
                    nItems = 0;
                    state = LIST;
                }

                continue;
            }

            if (state == LIST) {
                if ((nLists == 0) || (nItems == nLists)) {
                    nItems = 0;
                    state = STEXT;
                } else {
                    nItems++;

                    continue;
                }
            }

            if (state == STEXT) {
                if ((nStexts == 0) || (nItems == nStexts)) {
                    nItems = 0;
                    state = PROP;
                } else {
                    nItems++;

                    continue;
                }
            }

            if (state == PROP) {
                if ((nProps == 0) || (nItems == nProps)) {
                    nItems = 0;
                    state = FREE;
                } else {
                    nItems++;

                    continue;
                }
            }

            if (state == FREE) {
                tokenizer = new StringTokenizer(string);

                int nTokens = tokenizer.countTokens();

                if (nTokens > 0) {
                    token = tokenizer.nextToken();

                    if (token.equals("$$$$")) {
                        state = MOLECULE;
                        nItems = 0;

                        continue;
                    } else if (token.equals(">")) {
                        if (nTokens > 1) {
                            state = VALUE;
                            token = tokenizer.nextToken();
                            valueName = token.substring(1, token.length() - 1);

                            if (nTokens > 2) {
                                token = tokenizer.nextToken();
                                value = token;
                            }
                        }

                        continue;
                    }
                }
            } else if (state == VALUE) {
                tokenizer = new StringTokenizer(string);

                token = tokenizer.nextToken();
                value = token;

                if (valueName.equals("MDLNUMBER")) {
                    molecule.reName(molecule, compound, molName, value);
                    molName = molecule.name;
                }
// fixme
//                TclObject valueObj = TclString.newInstance(value);
//                interp.setVar(molName + "(" + valueName + ")", valueObj,
//                        TCL.GLOBAL_ONLY);
                state = FREE;

                continue;
            }
        }
    }

    public static void readResidue(String fileName, String fileContent, Molecule molecule, String coordSetName)
            throws MoleculeIOException {
        String molName = molecule.name;

        if (coordSetName == null) {
            // XXX
            coordSetName = ((CoordSet) molecule.coordSets.values().iterator().next()).getName();
        }
        Compound compound = null;
        boolean firstAtom = true;
        String token = null;
        double x;
        double y;
        double z;
        int iAtom;
        int iBond;
        int jBond;
        String aname = null;
        Point3 pt;
        int structureNumber = 0;
        Atom atom1 = null;
        Atom atom2 = null;
        String string;
        LineNumberReader lineReader;
        int order;
        int stereo;
        int nItems = 0;
        int nAtoms = 0;
        int nBonds = 0;
        int nLists = 0;
        int nStexts = 0;
        int nProps = 0;
        int nProds = 0;
        int nReacs = 0;
        int nInters = 0;
        String valueName = null;
        String value = null;
        boolean chiral = false;
        int state = MOLECULE;

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

        Vector atomList = new Vector();

        if (atomList == null) {
            atomList = new Vector(128);
        }

        StringTokenizer tokenizer = null;
        String compoundName = "";
        while (true) {
            try {
                string = lineReader.readLine();
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
                Molecule.makeAtomList();
                molecule.getAtomTypes();

                return;
            }

            if (string == null) {
                Molecule.makeAtomList();
                molecule.getAtomTypes();

                return;
            }

            if (state == MOLECULE) {
                if (nItems < 3) {
                    nItems++;

                    continue;
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
                compoundName = fileRoot;
                compound = null;
                firstAtom = true;
                atomList.setSize(0);
                //  molecule.structures.add(Integer.valueOf(0));

                if (string.length() < 33) {
                    throw new MoleculeIOException(
                            "Read SD file: nPar line too short");
                }

                String valueString = null;
                nAtoms = Integer.parseInt(string.substring(0, 3).trim());
                nBonds = Integer.parseInt(string.substring(3, 6).trim());
                nLists = Integer.parseInt(string.substring(7, 9).trim());

                valueString = string.substring(14, 15).trim();

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
                state = ATOM;
                nItems = 0;

                continue;
            } else if (state == ATOM) {
                if (string.length() < 34) {
                    throw new MoleculeIOException(
                            "Read SD file: atom line too short");
                }

                x = Double.parseDouble(string.substring(0, 10).trim());
                y = Double.parseDouble(string.substring(10, 20).trim());
                z = Double.parseDouble(string.substring(20, 30).trim());

                iAtom = nItems;

                String atomSymbol = string.substring(31, 34).trim();
                aname = atomSymbol + (iAtom + 1);

                if (compound == null) {
                    compound = new Compound("1", compoundName);
                    compound.molecule = molecule;
                    compound.assemblyID = molecule.entityLabels.size() + 1;

                    molecule.addEntity(compound, coordSetName);
                }

                Atom atom = new Atom(aname);
                atom.aNum = Atom.getElementNumber(atomSymbol);
                atom.setPointValidity(structureNumber, true);
                pt = atom.getPoint(structureNumber);
                pt = new Point3(x, y, z);
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

                if (firstAtom) {
                    firstAtom = false;
                }

                atom.entity = compound;
                compound.addAtom(atom);
                atomList.addElement(atom);
                nItems++;

                if (nItems == nAtoms) {
                    nItems = 0;
                    state = BOND;

                    continue;
                }
            } else if (state == BOND) {
                iBond = Integer.parseInt(string.substring(0, 3).trim()) - 1;
                jBond = Integer.parseInt(string.substring(3, 6).trim()) - 1;
                order = Integer.parseInt(string.substring(6, 9).trim());
                stereo = Integer.parseInt(string.substring(9, 12).trim());

                if (stereo == 1) {
                    stereo = Bond.STEREO_BOND_UP;
                } else if (stereo == 4) {
                    stereo = Bond.STEREO_BOND_EITHER;
                } else if (stereo == 6) {
                    stereo = Bond.STEREO_BOND_DOWN;
                } else {
                    stereo = 0;
                }

                //System.err.println (iBond + " " + jBond + " " + atomList.size ());
                if (atomList != null) {
                    if ((iBond < atomList.size()) && (jBond < atomList.size())) {
                        atom1 = (Atom) atomList.elementAt(iBond);
                        atom2 = (Atom) atomList.elementAt(jBond);

                        Atom.addBond(atom1, atom2, order, stereo, false);
                    } else {
                        System.err.println("error in adding bond to molecule "
                                + molName);
                    }
                }

                nItems++;

                if (nItems == nBonds) {
                    nItems = 0;
                    state = LIST;
                }

                continue;
            }

            if (state == LIST) {
                if ((nLists == 0) || (nItems == nLists)) {
                    nItems = 0;
                    state = STEXT;
                } else {
                    nItems++;

                    continue;
                }
            }

            if (state == STEXT) {
                if ((nStexts == 0) || (nItems == nStexts)) {
                    nItems = 0;
                    state = PROP;
                } else {
                    nItems++;

                    continue;
                }
            }

            if (state == PROP) {
                if ((nProps == 0) || (nItems == nProps)) {
                    nItems = 0;
                    state = FREE;
                } else {
                    nItems++;

                    continue;
                }
            }

            if (state == FREE) {
                tokenizer = new StringTokenizer(string);

                int nTokens = tokenizer.countTokens();

                if (nTokens > 0) {
                    token = tokenizer.nextToken();

                    if (token.equals("$$$$")) {
                        state = MOLECULE;
                        nItems = 0;

                        continue;
                    } else if (token.equals(">")) {
                        if (nTokens > 1) {
                            state = VALUE;
                            token = tokenizer.nextToken();
                            valueName = token.substring(1, token.length() - 1);

                            if (nTokens > 2) {
                                token = tokenizer.nextToken();
                                value = token;
                            }
                        }

                        continue;
                    }
                }
            } else if (state == VALUE) {
                tokenizer = new StringTokenizer(string);

                token = tokenizer.nextToken();
                value = token;

                if (valueName.equals("MDLNUMBER")) {
                    molecule.reName(molecule, compound, molName, value);
                    molName = molecule.name;
                }
// fixme
//                TclObject valueObj = TclString.newInstance(value);
//                interp.setVar(molName + "(" + valueName + ")", valueObj,
//                        TCL.GLOBAL_ONLY);
                state = FREE;

                continue;
            }
        }
    }
}
