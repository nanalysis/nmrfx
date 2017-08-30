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
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * An object that represents a mapping of specified dataset with a memory map.
 *
 * @author brucejohnson
 */
public class BigMappedMatrixFile implements MappedMatrixInterface, Closeable {

    private static int MAPPING_SIZE = 1 << 30;
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
    private final int blockHeaderSize;
    final boolean writable;
    private final int mapSize;
    private final List<MapInfo> mappings = new ArrayList<MapInfo>();
    private final int BYTES = 4;

    /**
     * Create a memory-mapped interface to a large Dataset file that will require multiple mappings to span whole file.
     *
     * @param dataset Dataset object that uses this mapped matrix file
     * @param raFile The Random access file that actually stores data
     * @param writable true if the mapping should be writable
     */
    public BigMappedMatrixFile(final Dataset dataset, final RandomAccessFile raFile, final boolean writable) throws IOException {
        this.raFile = raFile;
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
        mapSize = MAPPING_SIZE;
        long matSize = BYTES;
        System.err.println(dataset.getFileName());
        strides[0] = 1;
        for (int i = 0; i < dataset.getNDim(); i++) {
            System.err.println(i + " " + blockSize[i] + " " + nBlocks[i] + " " + dataset.getSize(i));
            matSize *= (blockSize[i] + blockHeaderSize) * nBlocks[i];
            sizes[i] = dataset.getSize(i);
            // strides only relevant if no block header and not submatrix
            if (i > 0) {
                strides[i] = strides[i - 1] * sizes[i - 1];
            }
        }
        totalSize = matSize / BYTES;
        for (long offset = 0; offset < matSize; offset += mapSize) {
            long size2 = Math.min(matSize - offset, mapSize);
            FileChannel.MapMode mapMode = FileChannel.MapMode.READ_ONLY;
            if (writable) {
                mapMode = FileChannel.MapMode.READ_WRITE;
            }
            ByteOrder byteOrder = dataset.getByteOrder();
            MapInfo mapInfo = new MapInfo(offset + headerSize, size2, mapMode, byteOrder);
            mapInfo.mapIt(raFile);
            mappings.add(mapInfo);
        }
    }

    /**
     * Set the mapping size which determines how many map segments are used.
     *
     * @param newMapSize the mapping size in MBytes (1024 x 1024 bytes)
     */
    public static void setMapSize(final int newMapSize) {
        MAPPING_SIZE = newMapSize * 1024 * 1024;
    }

    /**
     * Return the mapping size which determines how many map segments are used.
     *
     * @return the mapping size
     */
    public static int getMapSize() {
        return MAPPING_SIZE / 1024 / 1024;
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

    private MappedByteBuffer getMapping(final int index) throws IOException {
        MapInfo mapInfo = mappings.get(index);
        if (mapInfo.buffer == null) {
            mapInfo.mapIt(raFile);
        } else {
            mapInfo.touch();
        }
        return mapInfo.buffer;
    }

    @Override
    public float getFloat(int... offsets) throws IOException {
        long p = position(offsets) * BYTES;
        int mapN = (int) (p / mapSize);
        int offN = (int) (p % mapSize);
        try {
            if (dataType == 0) {
                return getMapping(mapN).getFloat(offN);
            } else {
                return getMapping(mapN).getInt(offN);
            }
        } catch (Exception e) {
            StringBuilder sBuilder = new StringBuilder();
            for (int offset : offsets) {
                sBuilder.append(offset).append(" ");
            }
            for (int size : sizes) {
                sBuilder.append(size).append(" ");
            }
            throw new IOException("getFloat map range error offsets " + sBuilder.toString() + "pos " + p + " " + mapN + " " + offN + " " + totalSize + " " + e.getMessage());
        }
    }

    @Override
    public void setFloat(float d, int... offsets) throws IOException {
        long p = position(offsets) * BYTES;
        int mapN = (int) (p / mapSize);
        int offN = (int) (p % mapSize);
//        if (mapN > 0) {
//            System.err.println(p);
//        }
        try {
            if (dataType == 0) {
                getMapping(mapN).putFloat(offN, d);
            } else {
                getMapping(mapN).putInt(offN, (int) d);
            }
        } catch (Exception e) {
            StringBuilder sBuilder = new StringBuilder();
            for (int offset : offsets) {
                sBuilder.append(offset).append(" ");
            }
            for (int size : sizes) {
                sBuilder.append(size).append(" ");
            }
            throw new IOException("setFloat: map range error offsets " + sBuilder.toString() + "pos " + p + " " + mapN + " " + offN + " " + totalSize);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            for (MapInfo mapInfo : mappings) {
                mapInfo.clean();
            }
        } catch (Exception e) {
        } finally {
            System.out.println("close rafile");
            raFile.close();
        }
    }

    @Override
    public double sum() throws IOException {
        double sum = 0.0;
        for (int i = 0; i < totalSize; i++) {
            long p = i * BYTES;
            int mapN = (int) (p / mapSize);
            int offN = (int) (p % mapSize);
            try {
                sum += getMapping(mapN).getFloat(offN);
            } catch (Exception e) {
                MappedByteBuffer mapping = getMapping(mapN);
                System.out.println(mapN + " Err " + offN + " " + mapping.capacity() + " " + mapping.limit());
                e.printStackTrace();
                System.exit(0);
            }
        }
        return sum;
    }

    @Override
    public double sumFast() throws IOException {
        double sum = 0.0;
        MappedByteBuffer mapping = getMapping(0);
        long n = totalSize / (mapSize / BYTES);
        for (int i = 0; i < n; i++) {
            int p = i * BYTES;
            try {
                sum += mapping.getFloat(p);
            } catch (Exception e) {
                System.out.println(p + " Err " + mapping.capacity() + " " + mapping.limit());
                e.printStackTrace();
                System.exit(0);
            }
        }
        return sum;
    }

    @Override
    public void zero() throws IOException {
        for (long i = 0; i < totalSize; i++) {
            int mapN = (int) ((i * BYTES) / mapSize);
            int offN = (int) ((i * BYTES) % mapSize);
            try {
                if (dataType == 0) {
                    getMapping(mapN).putFloat(offN, 0.0f);
                } else {
                    getMapping(mapN).putInt(offN, 0);
                }
            } catch (java.lang.IndexOutOfBoundsException iOBE) {
                System.err.println("out of bounds at " + i + " " + mapN + " " + offN);
                throw iOBE;
            }
        }
    }

    @Override
    public void force() {
        for (MapInfo mapInfo : mappings) {
            mapInfo.force();
        }
    }
}
