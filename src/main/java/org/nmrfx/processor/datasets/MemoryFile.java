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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.math.Vec;

public class MemoryFile implements MappedMatrixInterface, Closeable {

    private final int[] sizes;
    private final long[] strides;
    private final long totalSize;
    private final int dataType;
    final boolean writable;
    private final FloatBuffer floatBuffer;

    public MemoryFile(final Dataset dataset, final boolean writable) {
        dataType = dataset.getDataType();
        sizes = new int[dataset.getNDim()];
        strides = new long[dataset.getNDim()];
        this.writable = writable;
        long size = 1;
        for (int i = 0; i < dataset.getNDim(); i++) {
            sizes[i] = dataset.getSize(i);
            size *= sizes[i];
            if (i == 0) {
                strides[i] = 1;
            } else {
                strides[i] = strides[i - 1] * sizes[i - 1];
            }
            System.err.println("mem file " + i + " " + dataset.getSize(i) + " " + strides[i]);
        }
        //System.out.println("size " + totalSize);
        totalSize = size;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect((int) totalSize * Float.BYTES);
        floatBuffer = byteBuffer.asFloatBuffer();
        try {
            zero();
        } catch (IOException ex) {
            Logger.getLogger(MemoryFile.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public long position(int... offsets) {
        long position;
        position = offsets[0];
        for (int iDim = 1; iDim < offsets.length; iDim++) {
            position += offsets[iDim] * strides[iDim];
        }
        return position;
    }

    @Override
    public int getSize(final int dim) {
        return sizes[dim];
    }

    @Override
    public long getTotalSize() {
        return totalSize;
    }

    @Override
    public float getFloat(int... offsets) throws IOException {
        int p = (int) position(offsets);
        return floatBuffer.get(p);
    }

    @Override
    public void setFloat(float d, int... offsets) throws IOException {
        int p = (int) position(offsets);
        floatBuffer.put(p, d);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public double sumValues() throws IOException {
        double sum = 0.0;
        for (int i = 0; i < totalSize; i++) {
            sum += floatBuffer.get(i);
        }
        return sum;
    }

    @Override
    public double sumFast() throws IOException {
        double sum = 0.0;
        for (int i = 0; i < totalSize; i++) {
            sum += floatBuffer.get(i);
        }
        return sum;
    }

    @Override
    public void zero() throws IOException {
        for (int i = 0; i < totalSize; i++) {
            floatBuffer.put(i, 0.0f);
        }
    }

    @Override
    public void force() {
    }

    public void writeVector(int first, int last, int[] point, int dim, double scale, Vec vector) throws IOException {
        int j = 0;
        point[dim] = first;
        int position = (int) position(point);
        int stride = (int) strides[dim];
        if (vector.isComplex()) {
            for (int i = first; i <= last; i += 2) {
                Complex c = vector.getComplex(j++);
                floatBuffer.put(position, (float) (c.getReal() * scale));
                position += stride;
                floatBuffer.put(position, (float) (c.getImaginary() * scale));
                position += stride;
            }
        } else {
            for (int i = first; i <= last; i++) {
                floatBuffer.put(position, (float) (vector.getReal(j++) * scale));
                position += stride;
            }
        }
    }

    public void readVector(int first, int last, int[] point, int dim, double scale, Vec vector) throws IOException {
        int j = 0;
        point[dim] = first;
        int position = (int) position(point);
        int stride = (int) strides[dim];
        if (vector.isComplex()) {
            for (int i = first; i <= last; i += 2) {
                double real = floatBuffer.get(position) / scale;
                position += stride;
                double imag = floatBuffer.get(position) / scale;
                position += stride;
                vector.set(j++, new Complex(real, imag));
            }
        } else {
            for (int i = first; i <= last; i++) {
                double real = floatBuffer.get(position) / scale;
                position += stride;
                vector.set(j++, real);
            }
        }
    }
}
