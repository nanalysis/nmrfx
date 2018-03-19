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

 /*
 * FFDReader.java
 *
 * Created on September 2, 2003, 1:27 PM
 */
package org.nmrfx.structure.chemistry.io;

import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.AtomColors;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Point3;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import java.io.*;
import java.util.*;
import org.nmrfx.structure.chemistry.Order;

/**
 *
 * @author Johnbruc
 */
public class FFDReader {

    Vector atomList = new Vector();
    Hashtable atomHash = new Hashtable();
    Hashtable residueHash = new Hashtable();
    Vector residueList = new Vector();
    Vector bondList = new Vector();
    Vector freeformNames = new Vector(16);
    Hashtable freeformHash = new Hashtable();
    String molName = null;
    Molecule molecule = null;
    Polymer polymer = null;
    Atom prevAtom = null;
    boolean firstAtom = true;
    int structureNumber = 0;
    public LineNumberReader lineReader = null;
    String lastFileName = null;

    /**
     * Creates a new instance of FFDReader
     */
    public FFDReader() {
    }

    public void readMolecule(String fileName, String fileContent)
            throws MoleculeIOException {
        read(fileName, fileContent, true);
    }

    public void read(String fileName, String fileContent)
            throws MoleculeIOException {
        read(fileName, fileContent, false);
    }

    public void read(String fileName, String fileContent,
            boolean moleculeMode) throws MoleculeIOException {
        String string;

        try {
            if (fileContent == null) {
                if (fileName == null) {
                    throw new MoleculeIOException("null file name");
                }

                if (((lastFileName != null)
                        && (!lastFileName.equals(fileName)))
                        || (lineReader == null)) {
                    BufferedReader bf = null;

                    if (fileName.equals("stdin")) {
                        bf = new BufferedReader(new InputStreamReader(System.in));
                    } else {
                        bf = new BufferedReader(new FileReader(fileName));
                    }

                    lineReader = new LineNumberReader(bf);
                    lastFileName = fileName;
                }
            } else {
                StringReader sf = new StringReader(fileContent);
                lineReader = new LineNumberReader(sf);
            }
        } catch (IOException ioe) {
            throw new MoleculeIOException(ioe.getMessage());
        }

        if (atomList == null) {
            atomList = new Vector(128);
        }

        int status = 0;

        while (true) {
            try {
                string = lineReader.readLine();
            } catch (IOException ioe) {
                System.out.println(ioe.getMessage());
                Molecule.makeAtomList();
                lineReader = null;

                break;
            }

            if (string == null) {
                Molecule.makeAtomList();
                lineReader = null;

                break;
            }

            status = processLine(string);

            if (moleculeMode && (status == 2)) {

                return;
            }
        }

        lineReader = null;
    }

    int processLine(String string) throws MoleculeIOException {
        String token = null;

        StringTokenizer tokenizer = new StringTokenizer(string);
        int nTokens = tokenizer.countTokens();

        if (nTokens == 0) {
            return 0;
        }

        token = tokenizer.nextToken();

        if (token.equals("ATOM")) {
            if (molecule == null) {
                throw new MoleculeIOException("No molecule name in file");
            }

            processAtom(tokenizer, structureNumber);
        } else if (token.equals("BOND")) {
            bondList.addElement(Integer.valueOf(tokenizer.nextToken()));
            bondList.addElement(Integer.valueOf(tokenizer.nextToken()));
            bondList.addElement(Integer.valueOf(tokenizer.nextToken()));
        } else if (token.equals("RESIDUE")) {
            residueList.addElement(Integer.valueOf(tokenizer.nextToken()));
            residueList.addElement(tokenizer.nextToken());
            residueList.addElement(tokenizer.nextToken());

            if (tokenizer.hasMoreTokens()) {
                residueList.addElement(Integer.valueOf(tokenizer.nextToken()));

                if (tokenizer.hasMoreTokens()) {
                    residueList.addElement(Integer.valueOf(tokenizer.nextToken()));
                } else {
                    residueList.addElement(Integer.valueOf(0));
                }
            } else {
                residueList.addElement(Integer.valueOf(0));
                residueList.addElement(Integer.valueOf(0));
            }
        } else if (token.equals("MOLECULE")) {
            molName = tokenizer.nextToken();
            initMolecule(molName);
        } else if (token.equals("End_Of_Molecule")) {
            finishMolecule();

            return 2;
        } else if (token.equals("COMMENT")) {
            molecule.comment = string.substring(8);
        } else if (token.equals("SOURCE")) {
            molecule.source = string.substring(7);
        } else if (token.equals("DISPLAY")) {
            if (Molecule.displayTypes.contains(string.substring(8).toLowerCase())) {
                molecule.display = string.substring(8).toLowerCase();
            }
        } else if (token.equals("LABEL")) {
            Object labelObj = Molecule.labelTypes.get(string.substring(6)
                    .toLowerCase());

            if (((labelObj) != null) && (labelObj instanceof Integer)) {
                molecule.label = (byte) ((Integer) labelObj).intValue();
            }
        } else if (token.equals("M_COLOR")) {
            if (tokenizer.hasMoreTokens()) {
                molecule.color[0] = (float) Double.parseDouble(tokenizer.nextToken());
            }

            if (tokenizer.hasMoreTokens()) {
                molecule.color[1] = (float) Double.parseDouble(tokenizer.nextToken());
            }

            if (tokenizer.hasMoreTokens()) {
                molecule.color[2] = (float) Double.parseDouble(tokenizer.nextToken());
            }
        } else if (token.equals("COLOR")) {
            if (Molecule.colorTypes.contains(string.substring(6).toLowerCase())) {
                molecule.colorType = string.substring(6).toLowerCase();
            }
        } else if (token.equals("TITLE")) {
            molecule.title = string.substring(6);
        } else if (token.equals("TITLE_POSITION")) {
            if (tokenizer.hasMoreTokens()) {
                molecule.titlePosition[0] = (float) Double.parseDouble(tokenizer.nextToken());
            }

            if (tokenizer.hasMoreTokens()) {
                molecule.titlePosition[1] = (float) Double.parseDouble(tokenizer.nextToken());
            }

            if (tokenizer.hasMoreTokens()) {
                molecule.titlePosition[2] = (float) Double.parseDouble(tokenizer.nextToken());
            }
        } else if (token.equals("E_TYPE")) {
            molecule.energyType = string.substring(7);
        } else if (token.equals("ENERGY")) {
            molecule.values[Molecule.ENERGY] = (float) Double.parseDouble(tokenizer.nextToken());
        } else if (token.equals("SQ_SCORE")) {
            molecule.values[Molecule.SQ_SCORE] = (float) Double.parseDouble(tokenizer.nextToken());
        } else if (token.equals("GRADIENT")) {
            molecule.values[Molecule.GRADIENT] = (float) Double.parseDouble(tokenizer.nextToken());
        } else if (token.equals("R_FACTOR")) {
            molecule.values[Molecule.R_FACTOR] = (float) Double.parseDouble(tokenizer.nextToken());
        } else if (token.equals("MODEL")) {
            processTransformMatrix(tokenizer, molecule.model);
        } else if (token.equals("VIEW")) {
            processTransformMatrix(tokenizer, molecule.view);
        } else if (token.equals("CRYSTAL")) {
            molecule.crystal = string.substring(8);
            // fixme unused } else if (token.equals("DATE")) {
            // fime unused } else if (token.equals("DELETED")) {
            molecule.deleted = true;
        } else if (token.equals("ORIGINAL_NAME")) {
            molecule.originalName = string.substring(14);
            // fixme unused } else if (token.equals("CONF")) {
        } else {
            Vector freeformVector = (Vector) freeformHash.get(token);

            if (freeformVector == null) {
                freeformVector = new Vector(4);
                freeformHash.put(token, freeformVector);
                freeformNames.addElement(token);
            }

            freeformVector.add(string);
        }

        return 1;
    }

    private void initMolecule(String molName) {
        molecule = new Molecule(molName);
        polymer = new Polymer(molName);
        polymer.molecule = molecule;
        molecule.addEntity(polymer);
        molecule.addCoordSet(molName, polymer);

        firstAtom = true;
        prevAtom = null;
        atomList.setSize(0);
        atomHash.clear();
        residueHash.clear();
        residueList.setSize(0);
        bondList.setSize(0);
        freeformHash.clear();
        freeformNames.setSize(0);
    }

    private void processAtom(StringTokenizer tokenizer, int structureNumber) {
        int iAtom = 0;
        double x;
        double y;
        double z;
        int aNum;
        String token = null;
        String aname = null;
        iAtom = Integer.parseInt(tokenizer.nextToken());

        Integer atomInt = Integer.valueOf(iAtom);

        aNum = Integer.parseInt(tokenizer.nextToken());
        x = Double.parseDouble(tokenizer.nextToken());
        y = Double.parseDouble(tokenizer.nextToken());
        z = Double.parseDouble(tokenizer.nextToken());

        Integer residueNum = Integer.valueOf(0);

        if (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            residueNum = Integer.valueOf(token);

            if (tokenizer.hasMoreTokens()) {
                token = tokenizer.nextToken();
                aname = token;
            } else {
                aname = String.valueOf(iAtom);
            }
        }

        Atom atom = new Atom(aname);
        atom.aNum = aNum;

        float[] acolors = AtomColors.getAtomColor(aNum);
        atom.setColor(acolors[0], acolors[1], acolors[2]);
        atom.iAtom = iAtom - 1;

        molecule.addCoordSet(molecule.name, (Entity) polymer);

        atom.setPointValidity(structureNumber, true);

        Point3 pt = new Point3(x, y, z);
        atom.setPoint(structureNumber, pt);

        atomHash.put(atomInt, atom);

        Residue residue = getResidue(residueNum);

        Atom testAtom = residue.getAtom(aname);

        if (testAtom != null) {
            atom.name = aname + residue.labelNum;
            residue.labelNum++;
        }

        // fixme following not order independent
        if (firstAtom) {
            polymer.firstResidue = residue;
            firstAtom = false;
        }

        atom.entity = residue;
        residue.addAtom(atom);
        atomList.addElement(atom);
        processOptionalAtomFields(tokenizer, atom);
    }

    private Residue getResidue(Integer residueNum) {
        Residue residue = (Residue) residueHash.get(residueNum);

        if (residue != null) {
            return residue;
        }

        residue = new Residue(residueNum.toString(), molName);
        residueHash.put(residueNum, residue);
        polymer.addResidue(residue);

        return residue;
    }

    private void processOptionalAtomFields(StringTokenizer tokenizer, Atom atom) {
        int i = 0;
        String token = null;

        while (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();

            switch (i) {
                case 0:
                    atom.fcharge = (float) Double.parseDouble(token);

                    break;

                case 1:
                    atom.charge = (float) Double.parseDouble(token);

                    break;

                case 2:
                    atom.stereoStr = token;

                    break;

                case 3:
                    atom.forceFieldCode = token;

                    break;

                case 4:
                    atom.value = (float) Double.parseDouble(token);

                    break;

                case 5:

                    // not currently used  atom.setSelected(Integer.parseInt(token));
                    break;

                case 6:
                    atom.mass = (float) Double.parseDouble(token);

                    break;

                case 7:
                    atom.setOccupancy((float) Double.parseDouble(token));

                    break;

                case 8:
                    atom.setBFactor((float) Double.parseDouble(token));

                    break;

                case 9:

                    // fixme atom.altPos = token;
                    break;

                case 10:
                    atom.setRed((float) Double.parseDouble(token));

                    break;

                case 11:
                    atom.setGreen((float) Double.parseDouble(token));

                    break;

                case 12:
                    atom.setBlue((float) Double.parseDouble(token));

                    break;

                case 13:
                    atom.setDisplayStatus(Integer.parseInt(token));

                    break;

                case 14:
                    atom.setLabelStatus(Integer.parseInt(token));

                    break;
                default: // fixme should throw exception?
            }

            i++;
        }

        return;
    }

    private void finishMolecule() throws MoleculeIOException {

        for (int iBond = 0; iBond < bondList.size(); iBond += 3) {
            if (atomList != null) {
                Atom atom1 = (Atom) atomHash.get((Integer) bondList.elementAt(iBond));
                Atom atom2 = (Atom) atomHash.get((Integer) bondList.elementAt(iBond
                        + 1));
                int order = ((Integer) bondList.elementAt(iBond + 2)).intValue();

                if ((atom1 != null) && (atom2 != null)) {
                    Atom.addBond(atom1, atom2, Order.getOrder(order), false);
                }
            }
        }

        if (residueList.size() > 0) {
            polymer.residues.clear();
        }

        for (int iResidue = 0; iResidue < residueList.size(); iResidue += 5) {
            Residue residue = (Residue) residueHash.get((Integer) residueList.elementAt(
                    iResidue));

            if (residue == null) {
                continue;
            }

            String residueName = (String) residueList.elementAt(iResidue + 2);
            residue.name = residueName;
            residue.number = (String) residueList.elementAt(iResidue + 1);
            residue.iRes = ((Integer) residueList.elementAt(iResidue)).intValue();
            polymer.addResidue(residue);
        }

        Enumeration keys = residueHash.keys();

        while (keys.hasMoreElements()) {
            Integer residueNum = (Integer) keys.nextElement();
            int rNum = residueNum.intValue();
            int prevNum = rNum - 1;
            int nextNum = rNum + 1;
            Residue residue = (Residue) residueHash.get(Integer.valueOf(rNum));

            if (residue == null) {
                continue;
            }

            Residue prevRes = (Residue) residueHash.get(Integer.valueOf(prevNum));
            Residue nextRes = (Residue) residueHash.get(Integer.valueOf(nextNum));

            if (prevRes != null) {
                prevRes.next = residue;
                residue.previous = prevRes;
            }

            if (nextRes != null) {
                nextRes.previous = residue;
                residue.next = nextRes;
            }
        }

        for (int iName = 0; iName < freeformNames.size(); iName++) {
            String token = null;
            // fixme
//            TclObject list = TclList.newInstance();
//            token = (String) freeformNames.elementAt(iName);
//
//            Vector freeformVector = (Vector) freeformHash.get(token);
//
//            for (int iElem = 0; iElem < freeformVector.size(); iElem++) {
//                TclList.append(interp, list,
//                        TclString.newInstance(
//                        (String) freeformVector.elementAt(iElem)));
//            }
//
//            interp.setVar(molName + "(" + token + ")", list, TCL.GLOBAL_ONLY);
        }

        molName = null;
        molecule.structures.clear();
        molecule.structures.add(Integer.valueOf(0));
        molecule = null;
    }

    private boolean processTransformMatrix(StringTokenizer tokenizer,
            float[][] matrix) {
        int i = 0;
        int j = 0;
        String token = null;

        if (tokenizer.countTokens() != 16) {
            return false;
        }

        while (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            matrix[i][j] = (float) Double.parseDouble(token);
            i++;

            if (i == 4) {
                i = 0;
                j++;
            }
        }

        return true;
    }
}
