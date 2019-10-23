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

public class MemoryFile implements MappedMatrixInterface, Closeable {

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
    private final int blockHeaderSize;
    final boolean writable;
    private final int BYTES = 4;
    private final FloatBuffer floatBuffer;
    boolean subMatrix = false;

    public MemoryFile(final Dataset dataset, final boolean writable) {
        blockSize = dataset.getBlockSizes();
        dataType = dataset.getDataType();
        offsetBlocks = dataset.getOffsetBlocks();
        offsetPoints = dataset.getOffsetPoints();
        nBlocks = dataset.getNBlocks();
        blockElements = dataset.getBlockElements();
        blockPoints = blockElements / BYTES;
        headerSize = dataset.getFileHeaderSize();
        blockHeaderSize = dataset.getBlockHeaderSize() / BYTES;
        sizes = new int[dataset.getNDim()];
        strides = new long[dataset.getNDim()];
        this.writable = writable;
        long matSize = BYTES;
        for (int i = 0; i < dataset.getNDim(); i++) {
            System.err.println(i + " " + blockSize[i] + " " + nBlocks[i] + " " + dataset.getSize(i));
            matSize *= (blockSize[i] + blockHeaderSize) * nBlocks[i];
            strides[i] = (blockSize[i] + blockHeaderSize) * nBlocks[i];
        }
        totalSize = matSize / BYTES;
        //System.out.println("size " + totalSize);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect((int) totalSize * BYTES);
        floatBuffer = byteBuffer.asFloatBuffer();
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public long position(int... offsets) {
        long position;
        if (subMatrix) {
            long blockNum = 0;
            long offsetInBlock = 0;
            for (int iDim = 0; iDim < offsets.length; iDim++) {
                blockNum += ((offsets[iDim] / blockSize[iDim]) * offsetBlocks[iDim]);
                offsetInBlock += ((offsets[iDim] % blockSize[iDim]) * offsetPoints[iDim]);
//System.out.println(iDim + " " + offsets[iDim] + " " + blockNum + " " + offsetInBlock);
            }
            position = blockNum * (blockPoints + blockHeaderSize) + offsetInBlock + blockHeaderSize;
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
    public double sum() throws IOException {
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
}
