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

import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Compound;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Point3;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.Water;
import org.nmrfx.processor.star.ParseException;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.regex.*;

public class MMcifReader {

    LineNumberReader lineReader = null;
    PrintWriter out = null;
    BufferedReader bfR;
    RETokenizer tokenizer = null;
    int jToken = 0;
    Object tokenObj = null;
    String token = null;
    String string;
    boolean usePrevious;
    boolean usePrevious2;

    MMcifReader(String fileName) {
        try {
            bfR = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bfR);
        } catch (IOException ioe) {
            System.out.println("Cannot open the STAR file.");
            System.out.println(ioe.getMessage());

            return;
        }

        usePrevious = false;
        usePrevious2 = false;
    }

    MMcifReader(String fileName, boolean writeMode) {
        if (writeMode) {
            try {
                out = new PrintWriter(new BufferedWriter(new FileWriter(fileName)));
            } catch (IOException ioe) {
                System.out.println("Cannot open the STAR file: " + fileName
                        + " for writing");
                System.out.println(ioe.getMessage());

                return;
            }
        }
    }

    // Converts the contents of a file into a CharSequence
    // suitable for use by the regex package.
    public CharSequence fromFile(String filename) throws IOException {
        FileInputStream fis = new FileInputStream(filename);
        java.nio.channels.FileChannel fc = fis.getChannel();

        // Create a read-only CharBuffer on the file
        ByteBuffer bbuf = fc.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,
                0, (int) fc.size());
        CharBuffer cbuf = java.nio.charset.Charset.forName("8859_1").newDecoder()
                .decode(bbuf);
        fc.close();

        return cbuf;
    }

    public void writeToken(String token) {
        out.print(token);
    }


    /*
     void setupTokenizer(StreamTokenizer tokenizer) {
     tokenizer.resetSyntax();
     tokenizer.wordChars('a', 'z');
     tokenizer.wordChars('A', 'Z');
     tokenizer.wordChars('\u00A0', '\u00FF');
     tokenizer.whitespaceChars(0000, 32);
     tokenizer.quoteChar('"');
     tokenizer.quoteChar('\'');
     tokenizer.wordChars('0', '9');
     tokenizer.wordChars('+', '+');
     tokenizer.wordChars('-', '-');
     tokenizer.wordChars('$', '$');
     tokenizer.wordChars('.', '.');
     tokenizer.wordChars('?', '?');
     tokenizer.commentChar('#');
     tokenizer.wordChars('_', '_');
     tokenizer.wordChars('@', '@');
     tokenizer.wordChars('(', '(');
     tokenizer.wordChars(')', ')');
     tokenizer.wordChars('*', '*');
     tokenizer.wordChars('<', '<');
     tokenizer.wordChars('>', '>');
     tokenizer.wordChars('%', '%');
     }
     */
    public String getToken() {
        StringBuffer text = new StringBuffer();
        String pattern = "[^ \t']+|'([^']|'[^ \t])+'";
        boolean inText = false;

        if (usePrevious) {
            usePrevious = false;

            return (token);
        }

        usePrevious = false;

        if (tokenizer != null) {
            if (tokenizer.hasNext()) {
                tokenObj = tokenizer.next();

                if (tokenObj != null) {
                    String tokenStr = (String) tokenObj;

                    if ((tokenStr.charAt(0) == '\'')
                            && (tokenStr.charAt(tokenStr.length() - 1) == '\'')) {
                        return tokenStr.substring(1, tokenStr.length() - 1);
                    } else {
                        return tokenStr;
                    }
                }
            } else {
                tokenizer = null;
            }
        }

        while (true) {
            string = getLine();

            if (string == null) {
                lineReader = null;
                bfR = null;

                return (null);
            }

            if (inText) {
                if (string.startsWith(";")) {
                    inText = false;

                    if (string.length() > 1) {
                        tokenizer = new RETokenizer(string.substring(1),
                                pattern, false);
                    } else {
                        tokenizer = null;
                    }

                    return (text.toString());
                } else {
                    text.append(string);
                }
            } else {
                if (string.startsWith("#")) {
                    continue;
                }

                if (string.startsWith(";")) {
                    text.setLength(0);
                    text.append(string.substring(1));
                    inText = true;
                } else {
                    tokenizer = new RETokenizer(string, pattern, false);

                    if (tokenizer.hasNext()) {
                        tokenObj = tokenizer.next();

                        if (tokenObj != null) {
                            String tokenStr = (String) tokenObj;

                            if ((tokenStr.charAt(0) == '\'')
                                    && (tokenStr.charAt(tokenStr.length() - 1) == '\'')) {
                                return tokenStr.substring(1,
                                        tokenStr.length() - 1);
                            } else {
                                return tokenStr;
                            }
                        }
                    } else {
                        tokenizer = null;
                    }
                }
            }
        }
    }

    public String getLine() {
        string = null;

        if (lineReader == null) {
            return (null);
        }

        try {
            string = lineReader.readLine();
        } catch (IOException e) {
            return (string);
        }

        return (string);
    }

    public void readMMcifAtomSite(int[] index,
            int structureNumber) throws InvalidMoleculeException, ParseException {
        //System.out.println("readStarXYZ");
        int nLines = 0;
        int i;
        int nTokens = index[0];
        int waterResidues = 1;
        Molecule molecule = null;
        Polymer polymer = null;
        Water water = null;
        Compound compound = null;
        Residue residue = null;
        Entity entity = null;

        Atom atom = null;
        Point3 pt = null;

        String resNum = null;
        String auth_resNum = null;
        String resType = null;
        String atomName = null;
        String elemName = null;
        String entityName = null;
        String asymSet = null;
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        double occupancy = 0.0;
        double bfactor = 0.0;
        int validCoord = 0;
        structureNumber = 0;
        molecule = Molecule.getActive();

        if (molecule == null) {
            throw new InvalidMoleculeException("No active molecule ");
        }

        while (true) {
            nLines++;
            validCoord = 0;

            for (i = 0; i < nTokens; i++) {
                token = getToken();

                if (token == null) {
                    return;
                } else if (token.equals("stop_")) {
                    return;
                } else if (token.equals("loop_")) {
                    usePrevious = true;

                    return;
                } else if (token.startsWith("_")) {
                    usePrevious = true;

                    return;
                }

                if (i == index[3]) {
                    entityName = token;
                } else if (i == index[4]) {
                    asymSet = token;
                } else if (i == index[2]) {
                    structureNumber = Integer.parseInt(token);
                } else if (i == index[5]) {
                    resNum = token;
                } else if (i == index[6]) {
                    resType = token;
                } else if (i == index[7]) {
                    atomName = token;
                } else if (i == index[8]) {
                    elemName = token;
                } else if (i == index[9]) {
                    if (!token.equals("?")) {
                        x = Double.valueOf(token).doubleValue();
                        validCoord++;
                    } else {
                        validCoord--;
                    }
                } else if (i == index[10]) {
                    if (!token.equals("?")) {
                        y = Double.valueOf(token).doubleValue();
                        validCoord++;
                    } else {
                        validCoord--;
                    }
                } else if (i == index[11]) {
                    if (!token.equals("?")) {
                        z = Double.valueOf(token).doubleValue();
                        validCoord++;
                    } else {
                        validCoord--;
                    }
                } else if (i == index[12]) {
                    if (!token.equals("?")) {
                        occupancy = Double.valueOf(token).doubleValue();
                    }
                } else if (i == index[13]) {
                    if (!token.equals("?")) {
                        bfactor = Double.valueOf(token).doubleValue();
                    }
                } else if (i == index[14]) {
                    if (!token.equals("?")) {
                        auth_resNum = token;
                    }
                }
            }

            if (entityName == null) {
                throw new ParseException("No entity specified at line " + nLines);
            }

            molecule.structures.add(Integer.valueOf(structureNumber));
            entity = (Entity) molecule.getEntity(entityName);

            if (entity == null) {
                if (resType.equals(".")) {
                    compound = new Compound(resNum, entityName);
                    entity = compound;
                    molecule.addEntity(compound);
                } else {
                    throw new ParseException("Couldn't find polymer (line " + nLines + ") "
                            + entityName + " " + resType + " " + resNum + " "
                            + atomName);
                }
            }

            String myResNum = null;

            if ((resNum != null) && !resNum.equals(".")) {
                myResNum = resNum;
            } else if ((auth_resNum != null) && !auth_resNum.equals(".")) {
                myResNum = auth_resNum;
            } else {
                myResNum = waterResidues + "";
                waterResidues++;
            }

            if (entity instanceof Water) {
                water = (Water) entity;
                residue = water.getResidue(myResNum);

                if (residue == null) {
                    //System.out.println("adding residue  (line "+nLines+") "+myResNum);
                    residue = new Residue(myResNum, resType);
                    residue.molecule = molecule;
                    water.addResidue(residue);
                }

                atom = residue.getAtom(atomName);
                compound = (Compound) residue;
            } else if (entity instanceof Polymer) {
                polymer = (Polymer) entity;
                residue = polymer.getResidue(myResNum);

                if (residue == null) {
                    System.out.println("adding residue  (line " + nLines
                            + ") " + myResNum);
                    residue = new Residue(resType, myResNum);
                    polymer.addResidue(residue);
                }

                atom = residue.getAtom(atomName);
                compound = (Compound) residue;
            } else {
                atom = ((Compound) entity).getAtom(atomName);
                compound = (Compound) entity;
            }

            if (atom == null) {
                atom = new Atom(atomName);
                atom.name = atomName;
                compound.addAtom(atom);
            }

            if (atom == null) {
                continue;
            }

            if (validCoord == 3) {
                molecule.addCoordSet(asymSet, entity);
                atom.setPointValidity(structureNumber, true);
                pt = atom.getPoint(structureNumber);

                if (pt == null) {
                    throw new ParseException("Couldn't find point " + atomName
                    );
                }
                pt = new Point3(x, y, z);
                atom.setPoint(structureNumber, pt);

                //fixme add bond for new atoms
            }

            atom.setOccupancy((float) occupancy);
            atom.setBFactor((float) bfactor);
            atom.aNum = Atom.getElementNumber(elemName);
        }
    }

    public void readMMcifCompAtoms(int[] index,
            Compound compound, String asymSet, int structureNumber) throws ParseException {
        //System.out.println("readStarXYZ");
        int i;
        int nTokens = index[0];
        Atom atom = null;
        Point3 pt = null;

        String elemName = null;
        String atomName = null;

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        int validCoord = 0;
        structureNumber = 0;

        while (true) {
            validCoord = 0;

            for (i = 0; i < nTokens; i++) {
                token = getToken();

                if (token == null) {
                    return;
                } else if (token.equals("stop_")) {
                    return;
                } else if (token.equals("loop_")) {
                    usePrevious = true;

                    return;
                } else if (token.startsWith("_")) {
                    usePrevious = true;

                    return;
                }

                // compid atom_id elem substruct x y z
                // fime unused resType?
                //if (i == index[1]) {
                //  String resType = token;
                //} else 
                if (i == index[2]) {
                    atomName = token;
                } else if (i == index[3]) {
                    elemName = token;
                    // fixme unused } else if (i == index[8]) {
                    // int align = Integer.valueOf(token).intValue();
                } else if (i == index[5]) {
                    if (!token.equals("?")) {
                        x = Double.valueOf(token).doubleValue();
                        validCoord++;
                    } else {
                        validCoord--;
                    }
                } else if (i == index[6]) {
                    if (!token.equals("?")) {
                        y = Double.valueOf(token).doubleValue();
                        validCoord++;
                    } else {
                        validCoord--;
                    }
                } else if (i == index[7]) {
                    if (!token.equals("?")) {
                        z = Double.valueOf(token).doubleValue();
                        validCoord++;
                    } else {
                        validCoord--;
                    }
                }
            }

            atom = new Atom(atomName);
            atom.name = atomName;
            compound.addAtom(atom);

            if ((asymSet != null) && (validCoord == 3)) {
                atom.setPointValidity(structureNumber, true);
                pt = atom.getPoint(structureNumber);

                if (pt == null) {
                    throw new ParseException("Couldn't find point " + atomName);
                }
                pt = new Point3(x, y, z);
                atom.setPoint(structureNumber, pt);

                //fixme add bond for new atoms
            }

            atom.aNum = Atom.getElementNumber(elemName);
        }
    }

    public void readMMcifCompBonds(int[] index, Compound compound) {
        //System.out.println("readStarXYZ");

        int i;
        int nTokens = index[0];

        String atomName1 = null;
        String atomName2 = null;
        String orderName = null;
        int order = 1;

        while (true) {

            for (i = 0; i < nTokens; i++) {
                token = getToken();

                if (token == null) {
                    return;
                } else if (token.equals("stop_")) {
                    return;
                } else if (token.equals("loop_")) {
                    usePrevious = true;

                    return;
                } else if (token.startsWith("_")) {
                    usePrevious = true;

                    return;
                }

                // compid atom_id elem substruct x y z
                //if (i == index[1]) {
                // fixme unused  String resType = new String(token);
                //} else 
                if (i == index[2]) {
                    atomName1 = token;
                } else if (i == index[3]) {
                    atomName2 = token;
                } else if (i == index[4]) {
                    orderName = token;

                    if (orderName.equalsIgnoreCase("sing")) {
                        order = 1;
                    } else if (orderName.equalsIgnoreCase("doub")) {
                        order = 2;
                    } else if (orderName.equalsIgnoreCase("trip")) {
                        order = 3;
                    } else if (orderName.equalsIgnoreCase("quad")) {
                        order = 4;
                    } else {
                        order = 2;
                    }
                }
            }

            Atom atom1 = compound.getAtom(atomName1);
            Atom atom2 = compound.getAtom(atomName2);
            Atom.addBond(atom1, atom2, order, false);
            Atom.addBond(atom2, atom1, order, false);
        }
    }

    static class RETokenizer implements Iterator {
        // Holds the original input to search for tokens

        private CharSequence input;
        // Used to find tokens
        private Matcher matcher;
        // If true, the String between tokens are returned
        private boolean returnDelims;
        // The current delimiter value. If non-null, should be returned
        // at the next call to next()
        private String delim;
        // The current matched value. If non-null and delim=null,
        // should be returned at the next call to next()
        private String match;
        // The value of matcher.end() from the last successful match.
        private int lastEnd = 0;

        // patternStr is a regular expression pattern that identifies tokens.
        // If returnDelims delim is false, only those tokens that match the
        // pattern are returned. If returnDelims true, the text between
        // matching tokens are also returned. If returnDelims is true, the
        // tokens are returned in the following sequence - delimiter, token,
        // delimiter, token, etc. Tokens can never be empty but delimiters might
        // be empty (empty string).
        public RETokenizer(CharSequence input, String patternStr,
                boolean returnDelims) {
            // Save values
            this.input = input;
            this.returnDelims = returnDelims;

            // Compile pattern and prepare input
            Pattern pattern = Pattern.compile(patternStr);
            matcher = pattern.matcher(input);
        }

        // Returns true if there are more tokens or delimiters.
        public boolean hasNext() {
            if (matcher == null) {
                return false;
            }

            if ((delim != null) || (match != null)) {
                return true;
            }

            if (matcher.find()) {
                if (returnDelims) {
                    delim = input.subSequence(lastEnd, matcher.start())
                            .toString();
                }

                match = matcher.group();
                lastEnd = matcher.end();
            } else if (returnDelims && (lastEnd < input.length())) {
                delim = input.subSequence(lastEnd, input.length()).toString();
                lastEnd = input.length();

                // Need to remove the matcher since it appears to automatically
                // reset itself once it reaches the end.
                matcher = null;
            }

            return (delim != null) || (match != null);
        }

        // Returns the next token (or delimiter if returnDelims is true).
        public Object next() {
            String result = null;

            if (delim != null) {
                result = delim;
                delim = null;
            } else if (match != null) {
                result = match;
                match = null;
            } else {
                throw new NoSuchElementException();
            }

            return result;
        }

        // Returns true if the call to next() will return a token rather
        // than a delimiter.
        public boolean isNextToken() {
            return (delim == null) && (match != null);
        }

        // Not supported.
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
