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
package org.nmrfx.processor.utilities;

public class Permutations extends Object {

    int size;
    int[] data;
    int totalNumber;
    int numLeft;

    public Permutations(int size) {
        this.size = size;
        initialize();
    }

    public void initialize() {
        totalNumber = factorial(size);
        numLeft = totalNumber;
        data = new int[size];

        for (int i = 0; i < size; i++) {
            data[i] = i;
        }
    }

    public boolean hasNext() {
        return numLeft > 0;
    }

    public int[] next() {
        if (numLeft < totalNumber) {
            calculateNext();
        }

        numLeft--;

        return data;
    }

    public void calculateNext() {
        int i = size - 1;

        while (data[i - 1] >= data[i]) {
            i = i - 1;
        }

        int j = size;

        while (data[j - 1] <= data[i - 1]) {
            j = j - 1;
        }

        swap(i - 1, j - 1);

        i++;
        j = size;

        while (i < j) {
            swap(i - 1, j - 1);
            i++;
            j--;
        }
    }

    public void swap(int a, int b) {
        int temp = data[a];
        data[a] = data[b];
        data[b] = temp;
    }

    public static int factorial(int a) {
        int temp = 1;

        if (a > 1) {
            for (int i = 1; i <= a; i++) {
                temp *= i;
            }
        }

        return temp;
    }
}
