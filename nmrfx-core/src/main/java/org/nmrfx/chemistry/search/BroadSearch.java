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

package org.nmrfx.chemistry.search;

public class BroadSearch {

    byte[] nMatch = null;
    byte[][] codes = null;
    public byte[] scores = null;
    byte[][] codeDictionary = null;

    public BroadSearch(int size) {
        nMatch = new byte[size];
        codes = new byte[size][];
        scores = new byte[size];
        codeDictionary = new byte[256][];
    }

    public void set(int index, int nMatch, byte[] code) {
        this.nMatch[index] = (byte) nMatch;
        this.codes[index] = new byte[code.length];

        for (int i = 0; i < code.length; i++) {
            this.codes[index][i] = code[i];
        }
    }

    public int getScore(int index) {
        if (index >= scores.length) {
            return -1;
        } else {
            return scores[index];
        }
    }

    public void setDictionary(int index, byte[] code) {
        if (index >= codeDictionary.length) {
            System.out.println("dictionary index too big");
        }

        this.codeDictionary[index] = new byte[code.length];

        for (int i = 0; i < code.length; i++) {
            this.codeDictionary[index][i] = code[i];
        }
    }

    public int[] uncompressCodes(byte[] code, int n) {
        int subCode = 0;
        int lastShellFlag = 0;
        int shell = 0;
        int[] intCode = new int[1024];
        int k = 0;

        for (int i = 0; i < n; i++) {
            subCode = code[i];

            int shellFlag = ((subCode >> 7) & 0x1);

            if (shellFlag != lastShellFlag) {
                lastShellFlag = shellFlag;
                shell++;
            }

            int index = subCode & 127;
            byte[] ecodes = codeDictionary[index];

            for (int j = 0; j < ecodes.length; j++) {
                intCode[k++] = (shell * 4096) + ecodes[j];
            }
        }

        intCode[intCode.length - 1] = -1;

        return intCode;
    }

    public void listStartPoints() {
        int prevCode = -1;

        for (int i = 0; i < nMatch.length; i++) {
            if (nMatch[i] == 0) {
                int index = codes[i][0] & 127;
                int code = codeDictionary[index][0];

                if (code != prevCode) {
                    System.out.println(i + " " + code);
                    prevCode = code;
                }
            }
        }
    }

    public int score(int iStart, int[] search) {
        for (int i = 0; i < scores.length; i++) {
            scores[i] = 0;
        }

        byte[] testCode = new byte[1024];
        int scoreVal = 0;
        int m = search.length;

        if (iStart >= codes.length) {
            System.out.println("invalid iStart (too big)");

            return -1;
        }

        if (nMatch[iStart] != 0) {
            System.out.println("invalid iStart (nMatch != 0)");

            return -1;
        }

        int nHits = 0;

        while (iStart <= codes.length) {
            int n = 0;

            if (codes[iStart] == null) {
                System.out.println("null codes at " + iStart);

                return -1;
            }

            for (int i = 0; i < codes[iStart].length; i++) {
                testCode[i + nMatch[iStart]] = codes[iStart][i];
                n = i + nMatch[iStart] + 1;
            }

            int[] cTestCode = uncompressCodes(testCode, n);
            scoreVal = 0;

            for (int i = 0; (cTestCode[i] >= 0) && (i < m); i++) {
                if (cTestCode[i] == search[i]) {
                    scoreVal++;
                } else {
                    break;
                }
            }

            scores[iStart] = (byte) scoreVal;

            if (scoreVal == 0) {
                break;
            } else {
                nHits++;
            }

            iStart++;
        }

        return nHits;
    }
}
