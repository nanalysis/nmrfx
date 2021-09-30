package org.nmrfx.processor.utilities;

import java.io.*;
import java.util.*;
import java.util.regex.*;
// FIXME BAJ should replace with opencsv?
/*
 * Copyright (c) Ian F. Darwin, http://www.darwinsys.com/, 1996-2002.
 * All rights reserved. Software written by Ian F. Darwin and others.
 * $Id: LICENSE,v 1.8 2004/02/09 03:33:38 ian Exp $
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * Java, the Duke mascot, and all variants of Sun's Java "steaming coffee
 * cup" logo are trademarks of Sun Microsystems. Sun's, and James Gosling's,
 * pioneering role in inventing and promulgating (and standardizing) the Java 
 * language and environment is gratefully acknowledged.
 * 
 * The pioneering role of Dennis Ritchie and Bjarne Stroustrup, of AT&T, for
 * inventing predecessor languages C and C++ is also gratefully acknowledged.
 */

 /* Simple demo of CSV matching using Regular Expressions.
 * Does NOT use the "CSV" class defined in the Java CookBook.
 * RE Pattern from Chapter 7, Mastering Regular Expressions (p. 205, first edn.)
 */
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
        //       return Pattern.compile(

        //    "\"(([^\"])|(\"\"))+\""+sepStr+"?|([^"+sepStr+"]+)"+sepStr+"?|"+sepStr);
        return Pattern.compile("\"(([^\"])|(\"\"))+\"(" + sepStr + "|$)|([^" + sepStr + "]+)" + sepStr + "?|" + sepStr);

        // return Pattern.compile(
        // "\"(([^\"])|(\"\"))+\""+sepStr+"|([^"+sepStr2+"]+)"+sepStr);
    }

    static Pattern makePatternMulti(String sepStr) {
        return Pattern.compile("\"(([^\"])|(\"\"))+\"" + sepStr + "*|([^"
                + sepStr + "]+)");
    }

    static Pattern makePattern2(String sepStr) {
        // remove extra quotes and sepStr at end of field
        //return Pattern.compile("(^\")|(\"" + sepStr + "$)|(\"$)|(" + sepStr + "$)");
        return Pattern.compile("(" + sepStr + "$)");

        //   return Pattern.compile("(^\")|(\""+sepStr+"$)|(\"$)|("+sepStr+"$)");
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
