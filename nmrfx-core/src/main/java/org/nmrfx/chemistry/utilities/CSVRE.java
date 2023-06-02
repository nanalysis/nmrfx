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

package org.nmrfx.chemistry.utilities;

import org.nmrfx.annotations.PluginAPI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/* Simple demo of CSV matching using Regular Expressions.
 * Does NOT use the "CSV" class defined in the Java CookBook.
 * RE Pattern from Chapter 7, Mastering Regular Expressions (p. 205, first edn.)
 */
@PluginAPI("ring")
public class CSVRE {

    /**
     * The rather involved pattern used to match CSV's consists of three alternations: the first matches quoted fields,
     * the second unquoted, the third null fields
     */
    private static String sepStr = ",";
    public static final String CSV_PATTERN
            = //	"\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\",?|([^,]+),?|,";
            "\"(([^\"])|(\"\"))+\",?|([^,]+),?|,";
    public static final String TAB_PATTERN = "\"(([^\"])|(\"\"))+\"\t?|([^\t]+)\t?|\t";
    public static final String SPACE_PATTERN = "\"(([^\"])|(\"\"))+\" ?|([^ ]+) ?| ";
    public static final String GEN_PATTERN = "\"(([^\"])|(\"\"))+\"" + sepStr
            + "?|([^" + sepStr + "]+)" + sepStr + "?|" + sepStr;
    static Pattern tabPattern = null;
    static Pattern commaPattern = null;
    static Pattern spacePattern = null;
    static Pattern tabPattern2 = null;
    static Pattern commaPattern2 = null;
    static Pattern spacePattern2 = null;

    static {
        sepStr = "\t";
        tabPattern = makePattern("\t");
        tabPattern2 = makePattern2("\t");
        commaPattern = makePattern(",");
        commaPattern2 = makePattern2(",");

        //fixme space pattern should allow multiple spaces
        spacePattern = makePatternMulti(" ");
        spacePattern2 = makePattern2("\\s");
    }

    static Pattern makePattern(String sepStr) {
        return Pattern.compile("\"(([^\"])|(\"\"))+\"(" + sepStr + "|$)|([^" + sepStr + "]+)" + sepStr + "?|" + sepStr);
    }

    static Pattern makePatternMulti(String sepStr) {
        return Pattern.compile("\"(([^\"])|(\"\"))+\"" + sepStr + "*|([^"
                + sepStr + "]+)");
    }

    static Pattern makePattern2(String sepStr) {
        // remove extra quotes and sepStr at end of field
        return Pattern.compile("(" + sepStr + "$)");
    }

    public static void main(String[] argv) throws IOException {
        String line;

        // Construct a new Regular Expression parser.
        BufferedReader is = new BufferedReader(new InputStreamReader(System.in));

        // For each line...
        while ((line = is.readLine()) != null) {
            parseLine(" ", line);
        }
    }

    public static String[] parseLine(String sepStr, String line) {
        Pattern pattern = null;
        Pattern pattern2 = null;

        if (sepStr.equals(",")) {
            pattern = commaPattern;
            pattern2 = commaPattern2;
        } else if (sepStr.equals("\t")) {
            pattern = tabPattern;
            pattern2 = tabPattern2;
        } else if (sepStr.equals(" ")) {
            pattern = spacePattern;
            pattern2 = spacePattern2;
        } else {
            return null;
        }

        Matcher matcher = pattern.matcher(line);
        Matcher matcher2 = null;

        // For each field
        String field = null;
        Vector resultVec = new Vector();
        while (matcher.find()) {
            field = matcher.group().trim();
            matcher2 = pattern2.matcher(field);

            String result = matcher2.replaceAll("").replaceAll("\"\"", "\"");
            if ((result.length() > 1) && (result.charAt(0) == '"') && (result.charAt(result.length() - 1) == '"')) {
                result = result.substring(1, result.length() - 1);
            }

            resultVec.add(result);
        }

        if (field != null) {
            if (field.matches(".*" + sepStr + "$")) {
                resultVec.add("");
            }
        }

        String[] resultFields = new String[0];
        resultFields = (String[]) resultVec.toArray(resultFields);

        return resultFields;
    }
}
