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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginAPI("ring")
public class PDBAtomParser extends AtomParser {

    private static final Logger log = LoggerFactory.getLogger(PDBAtomParser.class);
    String atomNum;

    public PDBAtomParser(String string) {
        this(string, false);
    }
// 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
// ****** recname                                                                                   X
//       ***** serial                                                                               X
//             **** aname                                                                           X
//                 * loc                                                                            X
//                  *** rname                                                                       X
//                      *  chain                                                                    X
//                       ***** seq                                                                  X
//                            *** space                                                             X
//                               ********  x                                                        X
//                                       ******** y                                                 X
//                                               ******** z                                         X
//                                                       ****** occ                                 X
//                                                             ****** bfactor                       X
//                                                                   ******     or                  X
//                                                                         ****    segment          X
//                                                                             **  element          X
//                                                                               ** charge          X
// ATOM      1  N   TYR A 104      23.779   2.277  46.922  1.00 16.26           N                   X
// ATOM     15  HO2'RGUAA 320       7.944  -0.592  -4.697  1.00 64.31      (from cyana)
// ATOM     74  N   SER A 151      18.362  -4.429   3.059  1.00  1.17      A
// ATOM     28 HO2'   C S   1       8.517  11.288   1.668  1.00  0.00           H  (from PDB)
// ATOM    638  CB ASER A  80      -0.514   7.537  -7.632  0.65 14.13           C
// ATOM      0 1HG1AILE A  73       5.060   0.096  -2.792  0.65 13.79           H   new

    // 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
    public PDBAtomParser(String string, boolean swapToIUPAC) {
        resNum = string.substring(22, 26).trim();
        insertCode = string.substring(26, 27);
// resName should only be in columns 17-19, but cyana uses four characters for rna/dna
        resName = string.substring(17, 21).trim();
        atomName = string.substring(12, 17);  // CYANA sticks ' char for atoms like HO2' in pos 16
        elemName = atomName.substring(0, 2).trim();
        if (Character.isDigit(elemName.charAt(0)) || ((elemName.charAt(0) == 'H') && (atomName.trim().length() == 4))) {
            elemName = "H";
        } else if ((elemName.charAt(0) == 'C') && (atomName.trim().length() == 4)) {
            elemName = "C";
        }


        atomNum = string.substring(7, 11).trim();
        if (atomName.charAt(4) != '\'') {     // kluge for cyana
            atomName = atomName.substring(0, 4);
        }
        atomName = atomName.trim();

        char firstChar = atomName.charAt(0);

        if (Character.isDigit(firstChar)) {
            atomName = atomName.substring(1) + firstChar;
        }
        resName = PDBAtomParser.pdbResToPRFName(resName, 'r').toUpperCase();
        if (swapToIUPAC) {
            String newName = (String) pdbToIUPAC.get(resName.toUpperCase() + "," + atomName.toUpperCase());
            if (newName != null) {
                atomName = newName;
            }
        } else {
            int nameLen = atomName.length();
            if (atomName.charAt(nameLen - 1) == '"') {
                atomName = atomName.substring(0, nameLen - 1) + "''";
            }
        }
        chainID = string.substring(21, 22).trim();
        loc = string.substring(16, 17);
        x = Double.valueOf(string.substring(30, 38)).doubleValue();
        y = Double.valueOf(string.substring(38, 46)).doubleValue();
        z = Double.valueOf(string.substring(46, 54)).doubleValue();

        if (string.length() > 59) {
            temp = string.substring(54, 60).trim();

            if (!temp.equals("")) {
                try {
                    occupancy = Double.parseDouble(temp);
                } catch (NumberFormatException nE) {
                    log.warn("Unable to parse occupancy.", nE);
                }
            }

            if (string.length() > 65) {
                temp = string.substring(60, 66).trim();

                if (!temp.equals("")) {
                    try {
                        bfactor = Double.parseDouble(temp);
                    } catch (NumberFormatException nE) {
                        log.warn("Unable to parse b factor.", nE);
                    }
                }

                if (string.length() > 75) {
                    segment = string.substring(72, 76).trim();

                    if (string.length() > 77) {
                        elemName = string.substring(76, 78).trim();

                        if (string.length() > 79) {
                            temp = string.substring(78, 80).trim();

                            if (!temp.equals("")) {
                                try {
                                    charge = Double.parseDouble(temp);
                                } catch (NumberFormatException nE) {
                                    log.warn("Unable to parse charge.", nE);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
