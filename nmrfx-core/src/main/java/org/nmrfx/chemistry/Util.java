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
package org.nmrfx.chemistry;

import org.apache.commons.math3.util.FastMath;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Bruce Johnson
 */
public class Util {

    private static boolean strictlyNEF;

    public static void setStrictlyNEF(boolean state) {
        strictlyNEF = state;
    }

    public static boolean hasSameShift(Atom atom, Atom partnerAtom) {
        PPMv ppmV = atom.getPPM(0);
        boolean result = false;
        Double ppm1 = null;
        Double ppm2 = null;
        PPMv ppmVPartner = partnerAtom.getPPM(0);
        if ((ppmV != null) && ppmV.isValid()) {
            ppm1 = ppmV.getValue();
        }
        if ((ppmVPartner != null) && ppmVPartner.isValid()) {
            ppm2 = ppmVPartner.getValue();
        }
        if ((ppm1 != null) && (ppm2 != null)) {
            double delta = Math.abs(ppm1 - ppm2);
            if (delta < 1.0e-5) {
                result = true;
            }
        }

        return result;
    }

    public static String getXYName(Atom atom) {
        Optional<Atom> partner = Optional.empty();
        String result;
        Atom atom1 = atom;
        String aName = atom.getName();
        int nameOffset = 1;
        if (atom.isMethylene()) {
            partner = atom.getMethylenePartner();
        } else if ((atom.getAtomicNumber() == 1) && (atom.parent != null) && atom.parent.isMethylCarbon()) {
            atom1 = atom.parent;
            nameOffset = 2;
            partner = atom1.getMethylCarbonPartner();
        } else if ((atom.getAtomicNumber() == 6) && atom.isMethylCarbon()) {
            partner = atom.getMethylCarbonPartner();
        } else if (atom.isAromaticFlippable()) {
            partner = atom.getAromaticPartner();
        }
        if (partner.isPresent()) {
            Atom partnerAtom = partner.get();
            boolean lessThan = atom1.getIndex() < partnerAtom.getIndex();
            String xy = lessThan ? "x" : "y";
            int nameLen = aName.length();
            result = aName.substring(0, nameLen - nameOffset) + xy;
        } else {
            result = aName;
        }

        return result;
    }

    public static boolean nefMatch(Atom atom, String pat) {
        boolean result = nefMatch(atom.name.toLowerCase(), pat);
        if (result && ((pat.contains("x") && !pat.equals("oxt") && !pat.startsWith("x")) || pat.contains("y"))) {
            Optional<Atom> partner = Optional.empty();
            Atom atom1 = atom;
            if (atom.isMethylene()) {
                partner = atom.getMethylenePartner();
            } else if ((atom.getAtomicNumber() == 1) && (atom.parent != null) && atom.parent.isMethylCarbon()) {
                atom1 = atom.parent;
                partner = atom1.getMethylCarbonPartner();
            } else if ((atom.getAtomicNumber() == 6) && atom.isMethylCarbon()) {
                partner = atom.getMethylCarbonPartner();
            } else if (atom.isAromaticFlippable()) {
                partner = atom.getAromaticPartner();
            }
            if (partner.isPresent()) {
                Atom partnerAtom = partner.get();
                boolean lessThan = atom1.getIndex() < partnerAtom.getIndex();
                result = pat.contains("x") ? lessThan : !lessThan;
            } else {
                result = false;
            }
        }
        return result;
    }

    private static boolean nefMatch(String str, String pat) {
        boolean result = false;
        int percentIndex = (pat.contains("x") && !pat.equals("oxt") && !pat.startsWith("x")) ? pat.indexOf("x") : (pat.contains("y") ? pat.indexOf("y") : pat.indexOf("%"));
        int singleWildIndex = pat.indexOf('#');
        int wildIndex = pat.indexOf('*');
        String rePat = null;
        if (percentIndex != -1) {
            rePat = pat.substring(0, percentIndex) + "[0-9']*";
        } else if (wildIndex != -1) {
            String substr = pat.substring(0, wildIndex);
            rePat = strictlyNEF ? substr + "\\S+" : substr + "\\S*";
        } else if (singleWildIndex != -1) {
            String substr = pat.substring(0, singleWildIndex);
            rePat = strictlyNEF ? substr + "[0-9]+" : substr + "[0-9]*";
        }
        if (rePat != null) {
            Pattern rePattern = Pattern.compile(rePat, Pattern.CASE_INSENSITIVE);
            Matcher matcher = rePattern.matcher(str);
            if (matcher.matches()) {
                result = true;
            }
        } else if (str.equals(pat)) {
            result = true;
        }
        return result;
    }

    /**
     * stringMatch --
     * <p>
     * See if a particular string matches a particular pattern. The matching
     * operation permits the following special characters in the pattern: *?\[]
     * (see the manual entry for details on what these mean).
     * <p>
     * Results: True if the string matches with the pattern.
     * <p>
     * Side effects: None.
     *
     * @param str String to compare pattern against
     * @param pat Pattern which may contain special characters.
     * @return true if string matches within the pattern
     */
    public static boolean stringMatch(String str, String pat) {
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

    public static InputStream getResourceStream(String fileName, boolean resourceMode) {
        InputStream iStream;
        try {
            if (resourceMode) {
                System.out.println("open " + fileName);
                iStream = ClassLoader.getSystemResourceAsStream(fileName);
            } else {
                iStream = new FileInputStream(fileName);
            }
        } catch (IOException ioE) {
            iStream = null;
        }
        return iStream;
    }

    public static double reduceAngle(double x) {
        if ((x > FastMath.PI) || (x < -FastMath.PI)) {
            double sine = FastMath.sin(x);
            double cosine = FastMath.cos(x);
            x = FastMath.atan2(sine, cosine);
        }
        return (x);
    }
}
