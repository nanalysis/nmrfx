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

package org.nmrfx.structure.chemistry.search;

import java.io.*;

public class MBTree {

    static public void readBTree(String fileName) {
        LineNumberReader lineReader;
        String string = null;

        try {
            BufferedReader bf = new BufferedReader(new FileReader(fileName));
            lineReader = new LineNumberReader(bf);
        } catch (IOException ioe) {
            System.out.println("Cannot open the file " + fileName);
            System.out.println(ioe.getMessage());

            return;
        }

        try {
            while (true) {
                string = lineReader.readLine();

                if (string == null) {
                    return;
                }

                System.out.println(string);
            }
        } catch (IOException ioe) {
            System.out.println("Cannot read the file " + fileName);
            System.out.println(ioe.getMessage());

            return;
        }
    }
}
