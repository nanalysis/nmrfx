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
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmrfx.processor.datasets.StorageCache.DatasetKey;

/**
 * Create a memory-mapped interface to a Dataset file
 *
 * @author brucejohnson
 */
public class SubMatrixFile implements DatasetStorageInterface, Closeable {

    StorageCache cache = new StorageCache();
    private RandomAccessFile raFile;
    FileChannel fc;
    private final Dataset dataset;
    private final File file;
    private long totalSize;
    private final int dataType;
    final boolean writable;
    DatasetLayout layout;
    private final int BYTES = Float.BYTES;
    ByteBuffer[] byteBuffers = new ByteBuffer[1];
    int currentBuffer = -1;
    DatasetKey[] keys;

    /**
     * An object that represents a mapping of specified dataset with a memory
     * map.
     *
     * @param dataset Dataset object that uses this mapped matrix file
     * @param raFile The Random access file that actually stores data
     * @param writable true if the mapping should be writable
     * @throws IOException if an I/O error occurs
     */
    public SubMatrixFile(final Dataset dataset, File file, final DatasetLayout layout, final RandomAccessFile raFile, final boolean writable) throws IOException {
        this.raFile = raFile;
        this.dataset = dataset;
        this.file = file;
        this.layout = layout;
        dataType = dataset.getDataType();
        this.writable = writable;
        init();
    }

    void init() throws IOException {
        fc = raFile.getChannel();
        int blockHeaderSize = layout.getBlockHeaderSize() / BYTES;
        long matSize = BYTES;
        System.err.println(dataset.getFileName());
        System.err.println("header size " + layout.getFileHeaderSize());
        for (int i = 0; i < dataset.getNDim(); i++) {
            System.err.println("sub cache " + i + " " + layout.blockSize[i] + " " + layout.nBlocks[i] + " " + dataset.getSize(i));
            matSize *= (layout.blockSize[i] + blockHeaderSize) * layout.nBlocks[i];
        }
        totalSize = matSize / BYTES;
        int nBlocks = layout.totalBlocks;
        keys = new DatasetKey[nBlocks];
        for (int i = 0; i < nBlocks; i++) {
            keys[i] = new DatasetKey(this, i);
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
        long blockNum = 0;
        long offsetInBlock = 0;
        for (int iDim = 0; iDim < offsets.length; iDim++) {
            blockNum += ((offsets[iDim] / layout.blockSize[iDim]) * layout.offsetBlocks[iDim]);
            offsetInBlock += ((offsets[iDim] % layout.blockSize[iDim]) * layout.offsetPoints[iDim]);
//                System.out.println(iDim + " " + offsets[iDim] + " " + blockNum + " " + offsetInBlock + " " + layout.offsetPoints[iDim] + " " + layout.offsetBlocks[iDim]);
        }
        long position = blockNum * (layout.blockPoints * BYTES + layout.blockHeaderSize) + offsetInBlock * BYTES + layout.fileHeaderSize;
//            System.out.println(position + " " + layout.blockPoints);
        return position;
    }

    @Override
    public long pointPosition(int... offsets) {
        long blockNum = 0;
        long offsetInBlock = 0;
        for (int iDim = 0; iDim < offsets.length; iDim++) {
            blockNum += ((offsets[iDim] / layout.blockSize[iDim]) * layout.offsetBlocks[iDim]);
            offsetInBlock += ((offsets[iDim] % layout.blockSize[iDim]) * layout.offsetPoints[iDim]);
//                System.out.println(iDim + " " + offsets[iDim] + " " + blockNum + " " + offsetInBlock + " " + layout.offsetPoints[iDim] + " " + layout.offsetBlocks[iDim]);
        }
        long position = blockNum * layout.blockPoints + offsetInBlock;
//            System.out.println(position + " " + layout.blockPoints);
        return position;
    }

    int getBlockPosition(int... offsets) {
        int blockNum = 0;
        for (int iDim = 0; iDim < offsets.length; iDim++) {
            blockNum += ((offsets[iDim] / layout.blockSize[iDim]) * layout.offsetBlocks[iDim]);
        }
        return blockNum;
    }

    public int getOffsetInBlock(int... offsets) {
        int offsetInBlock = 0;
        for (int iDim = 0; iDim < offsets.length; iDim++) {
            offsetInBlock += ((offsets[iDim] % layout.blockSize[iDim]) * layout.offsetPoints[iDim]);
        }
        return offsetInBlock;
    }

    @Override
    public int getSize(final int dim) {
        return layout.sizes[dim];
    }

    @Override
    public long getTotalSize() {
        return totalSize;
    }

    synchronized ByteBuffer readBlock(long iBlock) throws IOException {
//        System.out.println("read block " + iBlock);
        long blockPos = iBlock * (layout.blockPoints * BYTES + layout.blockHeaderSize) + layout.fileHeaderSize;
        ByteBuffer buffer = ByteBuffer.allocate((int) (layout.blockPoints * BYTES));
        buffer.order(dataset.getByteOrder());

        int nc = fc.read(buffer, blockPos);
        if (nc != layout.blockPoints * BYTES) {
            System.out.println("read failed " + nc);
        }
        return buffer;
    }

    synchronized void writeBlock(int iBlock, ByteBuffer buffer) throws IOException {
//        System.out.println("write block " + iBlock);
        long blockPos = iBlock * (layout.blockPoints * BYTES + layout.blockHeaderSize) + layout.fileHeaderSize;
        buffer.position(0);
        int nw = fc.write(buffer, blockPos);
    }

    @Override
    public float getFloat(int... offsets) throws IOException {
        int blockPos = getBlockPosition(offsets);
        int offset = getOffsetInBlock(offsets);

        //  if (blockPos != currentBuffer) {
        DatasetKey key = keys[blockPos];

        return cache.io(key, offset, 0.0f, 0);

//        ByteBuffer buffer = cache.getBuffer(key);
//        currentBuffer = blockPos;
//        // }
//        if (dataType == 0) {
//            return buffer.getFloat(offset * BYTES);
//        } else {
//            return buffer.getInt(offset * BYTES);
//
//        }
////        long p = bytePosition(offsets);
////        raFile.seek(p);
////        if (dataType == 0) {
////            return raFile.readFloat();
////        } else {
////            return raFile.readInt();
////        }
    }

    @Override
    public void setFloat(float d, int... offsets) throws IOException {
        int blockPos = getBlockPosition(offsets);
        int offset = getOffsetInBlock(offsets);
//        if (blockPos != currentBuffer) {
        DatasetKey key = keys[blockPos];
        cache.io(key, offset, d, 1);
//        ByteBuffer buffer = cache.getBuffer(key);
//        currentBuffer = blockPos;
////        }
//        if (dataType == 0) {
//            buffer.putFloat(offset * BYTES, d);
//        } else {
//            buffer.putInt(offset * BYTES, (int) d);
//        }
////        long p = bytePosition(offsets);
////        try {
////            raFile.seek(p);
////            if (dataType == 0) {
////                raFile.writeFloat(d);
////            } else {
////                raFile.writeInt((int) d);
////            }
////        } catch (Exception e) {
////            System.out.println("map range error " + p + " " + totalSize);
////        }
    }

    @Override
    public void close() throws IOException {
        if (raFile != null) {
            force();
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
        try {
            for (int blockNum = 0; blockNum < layout.getTotalBlocks(); blockNum++) {
                ByteBuffer buffer = readBlock(blockNum);
                for (int j = 0; j < layout.blockPoints; j++) {
                    sum += buffer.getFloat(j);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(SubMatrixFile.class.getName()).log(Level.SEVERE, null, ex);
        }

        return sum;
    }

    @Override
    public void zero() {
        try {
            for (int blockNum = 0; blockNum < layout.getTotalBlocks(); blockNum++) {
                ByteBuffer buffer = readBlock(blockNum);
                for (int j = 0; j < layout.blockPoints; j++) {
                    buffer.putFloat(0, j);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(SubMatrixFile.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void force() {
        try {
            cache.flush(this);
        } catch (IOException ex) {
            Logger.getLogger(SubMatrixFile.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
