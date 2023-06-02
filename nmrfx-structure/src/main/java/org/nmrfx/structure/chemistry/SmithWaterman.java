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

package org.nmrfx.structure.chemistry;

import java.util.ArrayList;

public class SmithWaterman {

    private final double[][] H;
    private final String aString;
    private final String bString;
    private static final double MISMATCH_PENALTY = -0.5333;
    private final int nRows;
    private final int nCols;
    private final ArrayList<Integer> indexA = new ArrayList<Integer>();
    private final ArrayList<Integer> indexB = new ArrayList<Integer>();
    private int offsetA = 0;
    private int offsetB = 0;

    public SmithWaterman(String aString, String bString) {
        int n = aString.length();
        int m = bString.length();
        nRows = n + 1;
        nCols = m + 1;
        H = new double[1 + n][1 + m];
        this.aString = aString;
        this.bString = bString;
    }

    public int getOffsetA() {
        return offsetA;
    }

    public int getOffsetB() {
        return offsetB;
    }

    public ArrayList<Integer> getA() {
        return indexA;
    }

    public ArrayList<Integer> getB() {
        return indexB;
    }

    public void dumpH() {
        for (int row = 0; row < nRows; row++) {
            for (int col = 0; col < nCols; col++) {
                System.out.print(H[row][col] + " ");
            }
            System.out.println("");
        }
    }

    private double compareChar(char a, char b) {
        final double score;
        if (a == b) {
            score = 1.0;
        } else {
            score = MISMATCH_PENALTY;
        }
        return score;
    }

    private double gapScore(int k) {
        return 1.0 + (0.25) * k;
    }

    public void buildMatrix() {
        int n = aString.length();
        int m = bString.length();
        for (int i = 0; i <= n; i++) {
            H[i][0] = 0;
        }

        for (int j = 0; j <= m; j++) {
            H[0][j] = 0;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double c1 = 0.0;
                double c2 = 0.0;
                double c3 = 0.0;
                double c4 = 0.0;

                c1 = H[i - 1][j - 1] + compareChar(aString.charAt(i - 1), bString.charAt(j - 1));
                c2 = Math.max(c2, H[i - 1][j] - gapScore(1));
                c3 = Math.max(c3, H[i][j - 1] - gapScore(1));

                H[i][j] = Math.max(Math.max(c1, c2), Math.max(c3, c4));
            }
        }

    }

    public void processMatrix() {
        boolean skipA, skipB;
        double diag, left, top;

        int maxRow = 0;
        int maxCol = 0;

        for (int row = 0; row < nRows; row++) {
            for (int col = 0; col < nCols; col++) {
                if (H[row][col] > H[maxRow][maxCol]) {
                    maxRow = row;
                    maxCol = col;
                }
            }
        }

        skipA = skipB = false;

        int row = maxRow;
        int col = maxCol;
        while (H[row][col] > 0) {
            diag = H[row - 1][col - 1];
            top = H[row - 1][col];
            left = H[row][col - 1];

            if ((!skipA) && (!skipB)) {
                indexA.add(0, row - 1);
                indexB.add(0, col - 1);
            } else if (skipA) {
                indexA.add(0, null);
                indexB.add(0, col - 1);
            } else {
                indexA.add(0, row - 1);
                indexB.add(0, null);
            }

            if ((diag >= top) && (diag >= left)) {
                row--;
                col--;
                skipA = skipB = false;
            } else if ((top >= diag) && (top >= left)) {
                row--;
                skipA = false;
                skipB = true;
            } else {
                col--;
                skipA = true;
                skipB = false;
            }
        }
        offsetA = row;
        offsetB = col;
    }

    public static void main(String args[]) {
        SmithWaterman smithWaterman = new SmithWaterman(args[0], args[1]);
        smithWaterman.buildMatrix();
        smithWaterman.dumpH();
        smithWaterman.processMatrix();
    }
}
