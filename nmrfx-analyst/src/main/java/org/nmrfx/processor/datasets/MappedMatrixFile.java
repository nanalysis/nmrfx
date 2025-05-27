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
import org.nmrfx.datasets.DatasetHeaderIO;
import org.nmrfx.datasets.DatasetLayout;
import org.nmrfx.datasets.DatasetStorageInterface;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Create a memory-mapped interface to a Dataset file
 *
 * @author brucejohnson
 */
public class MappedMatrixFile implements DatasetStorageInterface, Closeable {

    private static final Logger log = LoggerFactory.getLogger(MappedMatrixFile.class);
    private RandomAccessFile raFile;
    private final Dataset dataset;
    private final File file;
    private final long[] strides;
    private long totalSize;
    private final int dataType;
    final boolean writable;
    private MappedByteBuffer mappedBuffer;
    DatasetLayout layout;
    FloatBuffer floatBuffer;
    private final long BYTES = 4;

    /**
     * An object that represents a mapping of specified dataset with a memory
     * map.
     *
     * @param dataset  Dataset object that uses this mapped matrix file
     * @param raFile   The Random access file that actually stores data
     * @param writable true if the mapping should be writable
     * @throws IOException if an I/O error occurs
     */
    public MappedMatrixFile(final Dataset dataset, File file, final DatasetLayout layout, final RandomAccessFile raFile, final boolean writable) throws IOException {
        this.raFile = raFile;
        this.dataset = dataset;
        this.file = file;
        this.layout = layout;
        dataType = dataset.getDataType();
        strides = new long[dataset.getNDim()];
        this.writable = writable;
        init();
    }

    public DatasetLayout getLayout() {
        return layout;
    }

    void init() throws IOException {
        long size = 1;
        for (int i = 0; i < layout.nDim; i++) {
            size *= layout.sizes[i];
            if (i == 0) {
                strides[i] = 1;
            } else {
                strides[i] = strides[i - 1] * layout.sizes[i - 1];
            }
            log.info("mapped {} {} {} {}", i, dataset.getSizeTotal(i), strides[i], dataset.getName());
        }
        totalSize = size;
        try {
            long size2 = totalSize * Float.BYTES;
            FileChannel.MapMode mapMode = FileChannel.MapMode.READ_ONLY;
            if (writable) {
                mapMode = FileChannel.MapMode.READ_WRITE;
            }
            mappedBuffer = this.raFile.getChannel().map(mapMode, layout.getFileHeaderSize(), size2);
            mappedBuffer.order(dataset.getByteOrder());
            floatBuffer = mappedBuffer.asFloatBuffer();
        } catch (IOException e) {
            this.raFile.close();
            throw e;
        }
    }

    @Override
    public final synchronized void writeHeader(boolean nvExtra) {
        if (file != null) {
            DatasetHeaderIO headerIO = new DatasetHeaderIO(dataset);
            if (file.getPath().contains(".ucsf")) {
                headerIO.writeHeaderUCSF(layout, raFile, nvExtra);
            } else {
                headerIO.writeHeader(layout, raFile);
            }
        }
    }

    @Override
    public void setWritable(boolean state) throws IOException {
        if (writable != state) {
            if (state) {
                raFile = new RandomAccessFile(file, "rw");
            } else {
                force();
                raFile = new RandomAccessFile(file, "r");
            }
            init();
        }
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    protected void startVecGet(int... offsets) {
        // return start position, block, stride, nPoints 
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
        return layout.sizes[dim];
    }

    @Override
    public long getTotalSize() {
        return totalSize;
    }

    @Override
    public float getFloat(int... offsets) {
        int p = (int) bytePosition(offsets);
        if (dataType == 0) {
            return mappedBuffer.getFloat(p);
        } else {
            return mappedBuffer.getInt(p);
        }
    }

    @Override
    public void setFloat(float d, int... offsets) {
        int p = (int) bytePosition(offsets);
        try {
            if (dataType == 0) {
                mappedBuffer.putFloat(p, d);
            } else {
                mappedBuffer.putInt(p, (int) d);
            }
        } catch (Exception e) {
            log.warn("map range error {} {} {}", p, totalSize, dataset.getName());
        }
    }

    @Override
    public void close() throws IOException {
        if (raFile != null) {
            clean(mappedBuffer);
            raFile.close();
        }
    }

    @Override
    public double sumValues() {
        return sumFast();
    }

    @Override
    public double sumFast() {
        double sum = 0.0;
        for (int i = 0; i < totalSize; i++) {
            sum += mappedBuffer.getFloat(i * Float.BYTES);
        }
        return sum;
    }

    @Override
    public void zero() {
        for (int i = 0; i < totalSize; i++) {
            if (dataType == 0) {
                mappedBuffer.putFloat(i * Float.BYTES, 0.0f);
            } else {
                mappedBuffer.putInt(i * Integer.BYTES, 0);
            }
        }
    }

    @Override
    public void force() {
        mappedBuffer.force();
    }

    private void clean(MappedByteBuffer mapping) {
        if (mapping == null) {
            return;
        }
        MapInfo.closeDirectBuffer(mapping);
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
