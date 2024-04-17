/*
 * NMRFx Processor : A Program for Processing NMR Data
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
package org.nmrfx.processor.gui;

import java.util.ArrayList;
import java.util.List;
// Adapted from http://agiletribe.wordpress.com/2012/11/23/the-only-class-you-need-for-csv-files/

public class CSVLineParse {

    public static List parseLine(String line) {
        ArrayList<String> store = new ArrayList<>();
        StringBuffer curVal = new StringBuffer();
        boolean inquotes = false;
        boolean started = false;
        char quoteChar = '\'';
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inquotes) {
                started = true;
                if (ch == quoteChar) {
                    inquotes = false;
                } else {
                    curVal.append((char) ch);
                }
            } else if ((ch == '\"') || (ch == '\'')) {
                inquotes = true;
                quoteChar = ch;
                if (started) {
                    // if this is the second quote in a value, add a quote
                    // this is for the double quote in the middle of a value
                    curVal.append(quoteChar);
                }
            } else if (ch == ',') {
                store.add(curVal.toString().trim());
                curVal = new StringBuffer();
                started = false;
            } else {
                curVal.append((char) ch);
            }
        }
        store.add(curVal.toString().trim());
        return store;
    }

    public static void main(String[] args) {
        List<String> result = parseLine(args[0]);
        System.out.println(result.toString());
    }
}
