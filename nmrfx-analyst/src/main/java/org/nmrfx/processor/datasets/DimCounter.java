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
package org.nmrfx.processor.datasets;

public class DimCounter implements Iterable<int[]> {

    private final int[] size;
    private final int nDim;
    private final int counterSize;

    public class Iterator<T> implements java.util.Iterator<int[]> {

        private final int[] counter = new int[nDim];

        public Iterator() {
            counter[0] = -1;
        }

        @Override
        public boolean hasNext() {
            for (int i = 0; i < nDim; i++) {
                if (counter[i] < (size[i] - 1)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int[] next() {
            for (int i = 0; i < nDim; i++) {
                if (counter[i] == size[i] - 1) {
                    counter[i] = 0;
                } else {
                    ++counter[i];
                    break;
                }
            }

            return counter.clone();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public DimCounter(int... size) {
        nDim = size.length;
        this.size = size.clone();
        int sizeTemp = 1;
        for (int i = 0; i < nDim; i++) {
            sizeTemp *= size[i];
        }
        counterSize = sizeTemp;
    }

    @Override
    public Iterator<int[]> iterator() {
        return new Iterator();
    }

    public int getSize() {
        return counterSize;
    }
}
