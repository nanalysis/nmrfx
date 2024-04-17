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
package org.nmrfx.utilities;

/**
 * @author Bruce Johnson
 */
public class Util {

    /**
     * stringMatch --
     * <p>
     * See if a particular string matches a particular pattern. The matching operation permits the following special
     * characters in the pattern: *?\[] (see the manual entry for details on what these mean).
     * <p>
     * Results: True if the string matches with the pattern.
     * <p>
     * Side effects: None.
     *
     * @param str String to compare pattern against
     * @param pat Pattern which may contain special characters.
     * @return true if string matches within the pattern
     */
    public static final boolean stringMatch(String str, String pat) {
        char[] strArr = str.toCharArray();
        char[] patArr = pat.toCharArray();
        int strLen = str.length(); // Cache the len of str.
        int patLen = pat.length(); // Cache the len of pat.
        int pIndex = 0; // Current index into patArr.
        int sIndex = 0; // Current index into patArr.
        char strch; // Stores current char in string.
        char ch1; // Stores char after '[' in pat.
        char ch2; // Stores look ahead 2 char in pat.
        boolean incrIndex = false; // If true it will incr both p/sIndex.

        while (true) {

            if (incrIndex == true) {
                pIndex++;
                sIndex++;
                incrIndex = false;
            }

            // See if we're at the end of both the pattern and the string.
            // If so, we succeeded. If we're at the end of the pattern
            // but not at the end of the string, we failed.
            if (pIndex == patLen) {
                return sIndex == strLen;
            }
            if ((sIndex == strLen) && (patArr[pIndex] != '*')) {
                return false;
            }

            // Check for a "*" as the next pattern character. It matches
            // any substring. We handle this by calling ourselves
            // recursively for each postfix of string, until either we
            // match or we reach the end of the string.
            if (patArr[pIndex] == '*') {
                pIndex++;
                if (pIndex == patLen) {
                    return true;
                }
                while (true) {
                    if (stringMatch(str.substring(sIndex), pat.substring(pIndex))) {
                        return true;
                    }
                    if (sIndex == strLen) {
                        return false;
                    }
                    sIndex++;
                }
            }

            // Check for a "?" as the next pattern character. It matches
            // any single character.
            if (patArr[pIndex] == '?') {
                incrIndex = true;
                continue;
            }

            // Check for a "[" as the next pattern character. It is followed
            // by a list of characters that are acceptable, or by a range
            // (two characters separated by "-").
            if (patArr[pIndex] == '[') {
                pIndex++;
                while (true) {
                    if ((pIndex == patLen) || (patArr[pIndex] == ']')) {
                        return false;
                    }
                    if (sIndex == strLen) {
                        return false;
                    }
                    ch1 = patArr[pIndex];
                    strch = strArr[sIndex];
                    if (((pIndex + 1) != patLen) && (patArr[pIndex + 1] == '-')) {
                        if ((pIndex += 2) == patLen) {
                            return false;
                        }
                        ch2 = patArr[pIndex];
                        if (((ch1 <= strch) && (ch2 >= strch)) || ((ch1 >= strch) && (ch2 <= strch))) {
                            break;
                        }
                    } else if (ch1 == strch) {
                        break;
                    }
                    pIndex++;
                }

                for (pIndex++; ((pIndex != patLen) && (patArr[pIndex] != ']')); pIndex++) {
                }
                if (pIndex == patLen) {
                    pIndex--;
                }
                incrIndex = true;
                continue;
            }

            // If the next pattern character is '\', just strip off the '\'
            // so we do exact matching on the character that follows.
            if (patArr[pIndex] == '\\') {
                pIndex++;
                if (pIndex == patLen) {
                    return false;
                }
            }

            // There's no special character. Just make sure that the next
            // characters of each string match.
            if ((sIndex == strLen) || (patArr[pIndex] != strArr[sIndex])) {
                return false;
            }
            incrIndex = true;
        }
    }

}
