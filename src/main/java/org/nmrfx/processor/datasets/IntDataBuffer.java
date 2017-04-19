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

import java.nio.IntBuffer;

public class IntDataBuffer extends DataBuffer {

    IntBuffer buffer = null;

    public IntDataBuffer(IntBuffer buffer) {
        this.buffer = buffer;
    }

    public double get(int index) {
        return buffer.get(index);
    }

    public float[] getBlock(int size) {
        float[] vector = new float[size];
        int[] ivector = new int[size];
        buffer.rewind();
        buffer.get(ivector);
        for (int i = 0; i < size; i++) {
            vector[i] = ivector[i];
        }
        return vector;

    }

    public void put(int index, double value) {
        buffer.put(index, (int) value);
    }

    public int capacity() {
        return buffer.capacity();
    }
}
