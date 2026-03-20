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

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.datasets.DatasetLayout;
import org.nmrfx.datasets.DatasetStorageInterface;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class MemoryFile implements DatasetStorageInterface, Closeable {
    private static final Logger log = LoggerFactory.getLogger(MemoryFile.class);

    private final int[] sizes;
    private final long[] strides;
    private final long totalSize;
    private final int dataType;
    private final DatasetLayout layout;
    final boolean writable;
    private final FloatBuffer floatBuffer;
    private final IntBuffer intBuffer;
    int BYTES = Float.BYTES;

    public MemoryFile(final Dataset dataset, DatasetLayout layout, final boolean writable) {
        dataType = dataset.getDataType();
        sizes = new int[dataset.getNDim()];
        strides = new long[dataset.getNDim()];
        this.layout = layout;
        this.writable = writable;
        long size = 1;
        for (int i = 0; i < dataset.getNDim(); i++) {
            sizes[i] = layout.getSize(i);
            size *= sizes[i];
            if (i == 0) {
                strides[i] = 1;
            } else {
                strides[i] = strides[i - 1] * sizes[i - 1];
            }
        }
        totalSize = size;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect((int) totalSize * Float.BYTES);
        if (dataType == 1) {
            intBuffer = byteBuffer.asIntBuffer();
            floatBuffer = null;
        } else {
            floatBuffer = byteBuffer.asFloatBuffer();
            intBuffer = null;
        }
        try {
            zero();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    public DatasetLayout getLayout() {
        return layout;
    }

    @Override
    public void setWritable(boolean state) {
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public long bytePosition(int... offsets) {
        long position;
        position = offsets[0];
        for (int iDim = 1; iDim < offsets.length; iDim++) {
            position += offsets[iDim] * strides[iDim];
        }
        return position * BYTES;
    }

    @Override
    public long pointPosition(int... offsets) {
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
        int p = (int) pointPosition(offsets);
        if (p >= totalSize) {
            throw new PositionException("Out of range in MemoryFile setFloat", totalSize, p, offsets);
        }
        return floatBuffer != null ? floatBuffer.get(p) : intBuffer.get(p);
    }

    @Override
    public void setFloat(float d, int... offsets) throws IOException {
        int p = (int) pointPosition(offsets);
        if (p >= totalSize) {
            throw new PositionException("Out of range in MemoryFile setFloat", totalSize, p, offsets);
        }
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
        int position = (int) pointPosition(point);
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
        int position = (int) pointPosition(point);
        int stride = (int) strides[dim];
        if (vector.isComplex()) {
            for (int i = first; i <= last; i += 2) {
                double real = dataType == 0 ? floatBuffer.get(position) / scale
                        : intBuffer.get(position) / scale;
                position += stride;
                double imag = dataType == 0 ? floatBuffer.get(position) / scale
                        : intBuffer.get(position) / scale;
                position += stride;
                vector.set(j++, new Complex(real, imag));
            }
        } else {
            for (int i = first; i <= last; i++) {
                double real = dataType == 0 ? floatBuffer.get(position) / scale
                        : intBuffer.get(position) / scale;
                position += stride;
                vector.set(j++, real);
            }
        }
    }

    /**
     * Get iterator that allows iterating over all the points in the file
     *
     * @return iterator an Iterator to iterate over points in dataset
     * @throws IOException if an I/O error occurs
     */
    synchronized public MultidimensionalCounter.Iterator pointIterator() {
        int nDim = sizes.length;
        int[] mPoint = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            mPoint[i] = sizes[i];
        }
        MultidimensionalCounter counter = new MultidimensionalCounter(mPoint);
        return counter.iterator();
    }

}
