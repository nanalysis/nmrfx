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
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

/**
 * Create a memory-mapped interface to a Dataset file
 *
 * @author brucejohnson
 */
public class MappedMatrixFile implements MappedMatrixInterface, Closeable {

    private final RandomAccessFile raFile;
    private final int[] sizes;
    private final long[] strides;
    private final long totalSize;
    private final int[] blockSize;
    private final int[] nBlocks;
    private final int[] offsetBlocks;
    private final int[] offsetPoints;
    private final long blockElements;
    private final long blockPoints;
    private final int dataType;
    private final int headerSize;
    final boolean writable;
    private final MappedByteBuffer mappedBuffer;
    private final long BYTES = 4;

    /**
     * An object that represents a mapping of specified dataset with a memory map.
     *
     * @param dataset Dataset object that uses this mapped matrix file
     * @param raFile The Random access file that actually stores data
     * @param writable true if the mapping should be writable
     * @throws IOException if an I/O error occurs
     */
    public MappedMatrixFile(final Dataset dataset, final RandomAccessFile raFile, final boolean writable) throws IOException {
        this.raFile = raFile;
        blockSize = dataset.getBlockSizes();
        dataType = dataset.getDataType();
        offsetBlocks = dataset.getOffsetBlocks();
        offsetPoints = dataset.getOffsetPoints();
        nBlocks = dataset.getNBlocks();
        blockElements = dataset.getBlockElements();
        blockPoints = blockElements / BYTES;
        headerSize = dataset.getFileHeaderSize();
        sizes = new int[dataset.getNDim()];
        strides = new long[dataset.getNDim()];
        this.writable = writable;
        try {
            long matSize = BYTES;
            System.out.println(dataset.getFileName());
            for (int i = 0; i < dataset.getNDim(); i++) {
                System.out.println(i + " " + blockSize[i] + " " + nBlocks[i] + " " + dataset.getSize(i));
                matSize *= blockSize[i] * nBlocks[i];
                strides[i] = blockSize[i] * nBlocks[i];
            }
            totalSize = matSize / BYTES;
            long size2 = matSize;
            FileChannel.MapMode mapMode = FileChannel.MapMode.READ_ONLY;
            if (writable) {
                mapMode = FileChannel.MapMode.READ_WRITE;
            }

            mappedBuffer = this.raFile.getChannel().map(mapMode, headerSize, size2);
            mappedBuffer.order(dataset.getByteOrder());
        } catch (IOException e) {
            this.raFile.close();
            throw e;
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
    public long position(int... offsets) {
        long position;
        boolean subMatrix = true;
        if (subMatrix) {
            long blockNum = 0;
            long offsetInBlock = 0;
            for (int iDim = 0; iDim < offsets.length; iDim++) {
                blockNum += ((offsets[iDim] / blockSize[iDim]) * offsetBlocks[iDim]);
                offsetInBlock += ((offsets[iDim] % blockSize[iDim]) * offsetPoints[iDim]);
//System.out.println(iDim + " " + offsets[iDim] + " " + blockNum + " " + offsetInBlock);
            }
            position = blockNum * blockPoints + offsetInBlock;
//System.out.println(position);
            return position;
        } else {
            position = offsets[0];
            for (int iDim = 1; iDim < offsets.length; iDim++) {
                position += offsets[iDim] * strides[iDim];
            }
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
    public float getFloat(int... offsets) {
        int p = (int) (position(offsets) * BYTES);
        if (dataType == 0) {
            return mappedBuffer.getFloat(p);
        } else {
            return mappedBuffer.getInt(p);
        }
    }

    @Override
    public void setFloat(float d, int... offsets) {
        int p = (int) (position(offsets) * BYTES);
        try {
            if (dataType == 0) {
                mappedBuffer.putFloat(p, d);
            } else {
                mappedBuffer.putInt(p, (int) d);
            }
        } catch (Exception e) {
            System.out.println("map range error " + p + " " + totalSize);
        }
    }

    @Override
    public void close() throws IOException {
        clean(mappedBuffer);
        raFile.close();
    }

    @Override
    public double sum() {
        return sumFast();
    }

    @Override
    public double sumFast() {
        double sum = 0.0;
        for (int i = 0; i < totalSize; i++) {
            sum += mappedBuffer.getFloat(i);
        }
        return sum;
    }

    @Override
    public void zero() {
        for (int i = 0; i < totalSize; i++) {
            if (dataType == 0) {
                mappedBuffer.putFloat(i, 0.0f);
            } else {
                mappedBuffer.putInt(i, 0);
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
        Cleaner cleaner = ((DirectBuffer) mapping).cleaner();
        if (cleaner != null) {
            cleaner.clean();
        }
    }
}
