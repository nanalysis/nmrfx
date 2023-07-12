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

public class PermuteExclusiveArrays extends Object {

    int[] indices;
    int[][] inputArrays;
    int[] data;
    int size = 0;
    boolean first = true;
    boolean hasNext = true;
    boolean[] used = null;

    public PermuteExclusiveArrays(int[][] inputArrays) {
        this.inputArrays = inputArrays;
        initialize();
    }

    void initialize() {
        indices = new int[inputArrays.length];
        data = new int[inputArrays.length];
        size = inputArrays.length;

        int max = 0;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < inputArrays[i].length; j++) {
                if (inputArrays[i][j] > max) {
                    max = inputArrays[i][j];
                }
            }
        }

        used = new boolean[max + 1];

        while (hasNext && !validArrangement()) {
            calculateNext();
        }
    }

    public boolean hasNext() {
        return hasNext;
    }

    public int[] next() {
        for (int i = 0; i < size; i++) {
            data[i] = inputArrays[i][indices[i]];
        }

        calculateNext();

        return data;
    }

    boolean validArrangement() {
        boolean validArrangement = true;

        for (int i = 0; i < used.length; i++) {
            used[i] = false;
        }

        for (int i = 0; i < size; i++) {
            int v = inputArrays[i][indices[i]];

            if (used[v]) {
                validArrangement = false;

                break;
            }

            used[v] = true;
        }

        return validArrangement;
    }

    void calculateNext() {
        boolean validArrangement = false;
        boolean ok = false;

        do {
            ok = false;

            for (int i = 0; i < size; i++) {
                indices[i]++;

                if (indices[i] < inputArrays[i].length) {
                    ok = true;

                    break;
                } else {
                    indices[i] = 0;
                }
            }

            if (ok) {
                validArrangement = validArrangement();
                ;
            }
        } while (ok && !validArrangement);

        hasNext = ok;
    }

    public static void main(String[] argv) {
        int[][] iArrays = new int[3][];
        iArrays[0] = new int[2];
        iArrays[1] = new int[2];
        iArrays[2] = new int[1];
        iArrays[0][0] = 0;
        iArrays[0][1] = 1;
        iArrays[1][0] = 1;
        iArrays[1][1] = 2;
        iArrays[2][0] = 3;

        PermuteExclusiveArrays perm = new PermuteExclusiveArrays(iArrays);

        while (perm.hasNext()) {
            int[] data = perm.next();

            for (int i = 0; i < data.length; i++) {
                System.out.print(data[i] + " ");
            }

            System.out.println(" ");
        }
    }
}
