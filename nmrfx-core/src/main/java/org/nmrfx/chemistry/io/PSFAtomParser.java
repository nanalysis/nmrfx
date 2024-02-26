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

public class PSFAtomParser extends AtomParser {

    public PSFAtomParser(String string) {
        this(string, false);
    }
// 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
//    ***** serial                                                                               X
//               **** seq                                                                           X
//                    *** rname                                                                       X
//                         **** aname                                                                       X
//                              *** atype                                                       X
//       29      2    THR  HG21 HA     0.100000       1.00800           0

    public PSFAtomParser(String string, boolean swapToIUPAC) {
        resNum = string.substring(14, 18).trim();
        resName = string.substring(19, 22).trim();
        atomName = string.substring(24, 28).trim();
    }
}
