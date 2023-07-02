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

import org.nmrfx.datasets.DatasetHeaderIO;
import org.nmrfx.datasets.DatasetLayout;
import org.nmrfx.datasets.DatasetStorageInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
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
public class BigMappedMatrixFile implements DatasetStorageInterface, Closeable {

    private static final Logger log = LoggerFactory.getLogger(BigMappedMatrixFile.class);
    private static final int MAPPING_SIZE = 1 << 30;
    private File file;
    Dataset dataset;
    private RandomAccessFile raFile;
    DatasetLayout layout;
    private final long[] strides;
    private long totalSize;
    private final int dataType;
    final boolean writable;
    private final int mapSize;
    private final List<MapInfo> mappings = new ArrayList<>();
    private final int BYTES = 4;

    /**
     * Create a memory-mapped interface to a large Dataset file that will
     * require multiple mappings to span whole file.
     *
     * @param dataset  Dataset object that uses this mapped matrix file
     * @param raFile   The Random access file that actually stores data
     * @param writable true if the mapping should be writable
     * @throws java.io.IOException
     */
    public BigMappedMatrixFile(final Dataset dataset, File file, DatasetLayout layout, final RandomAccessFile raFile, final boolean writable) throws IOException {
        this.dataset = dataset;
        this.raFile = raFile;
        this.file = file;
        this.layout = layout;
        dataType = dataset.getDataType();
        strides = new long[dataset.getNDim()];
        this.writable = writable;
        mapSize = MAPPING_SIZE;
        init();
    }

    void init() throws IOException {
        int blockHeaderSize = layout.getBlockHeaderSize() / BYTES;
        long matSize = BYTES;
        log.info(dataset.getFileName());
        log.info("header size {}", layout.getFileHeaderSize());
        strides[0] = 1;
        for (int i = 0; i < dataset.getNDim(); i++) {
            log.info("big map {} {} {} {}", i, layout.blockSize[i], layout.nBlocks[i], dataset.getSizeTotal(i));
            matSize *= (layout.blockSize[i] + blockHeaderSize) * layout.nBlocks[i];
            // strides only relevant if no block header and not submatrix
            if (i > 0) {
                strides[i] = strides[i - 1] * layout.sizes[i - 1];
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
            MapInfo mapInfo = new MapInfo(offset + layout.getFileHeaderSize(), size2, mapMode, byteOrder);
            mapInfo.mapIt(raFile);
            mappings.add(mapInfo);
        }
    }

    public DatasetLayout getLayout() {
        return layout;
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
        }
        return blockNum * (layout.blockPoints * BYTES + layout.blockHeaderSize) + offsetInBlock * BYTES;
    }

    @Override
    public long pointPosition(int... offsets) {
        long blockNum = 0;
        long offsetInBlock = 0;
        for (int iDim = 0; iDim < offsets.length; iDim++) {
            blockNum += ((offsets[iDim] / layout.blockSize[iDim]) * layout.offsetBlocks[iDim]);
            offsetInBlock += ((offsets[iDim] % layout.blockSize[iDim]) * layout.offsetPoints[iDim]);
        }
        long position = blockNum * layout.blockPoints + offsetInBlock;
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
        long p = bytePosition(offsets);
        int mapN = (int) (p / mapSize);
        int offN = (int) (p % mapSize);
        try {
            if (dataType == 0) {
                return getMapping(mapN).getFloat(offN);
            } else {
                return getMapping(mapN).getInt(offN);
            }
        } catch (IOException e) {
            StringBuilder sBuilder = new StringBuilder();
            for (int offset : offsets) {
                sBuilder.append(offset).append(" ");
            }
            for (int size : layout.sizes) {
                sBuilder.append(size).append(" ");
            }
            throw new IOException("getFloat map range error offsets " + sBuilder.toString() + "pos " + p + " " + mapN + " " + offN + " " + totalSize + " " + e.getMessage());
        }
    }

    @Override
    public void setFloat(float d, int... offsets) throws IOException {
        long p = bytePosition(offsets);
        int mapN = (int) (p / mapSize);
        int offN = (int) (p % mapSize);

        try {
            if (dataType == 0) {
                getMapping(mapN).putFloat(offN, d);
            } else {
                getMapping(mapN).putInt(offN, (int) d);
            }
        } catch (IOException e) {
            StringBuilder sBuilder = new StringBuilder();
            for (int offset : offsets) {
                sBuilder.append(offset).append(" ");
            }
            for (int size : layout.sizes) {
                sBuilder.append(size).append(" ");
            }
            throw new IOException("setFloat: map range error offsets " + sBuilder.toString() + "pos " + p + " " + mapN + " " + offN + " " + totalSize);
        }
    }

    @Override
    public void close() throws IOException {
        if (raFile != null) {
            try {
                for (MapInfo mapInfo : mappings) {
                    mapInfo.clean();
                }
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            } finally {
                log.info("close rafile");
                raFile.close();
                raFile = null;
            }
        }
    }

    @Override
    public double sumValues() throws IOException {
        double sum = 0.0;
        for (int i = 0; i < totalSize; i++) {
            long p = i * BYTES;
            int mapN = (int) (p / mapSize);
            int offN = (int) (p % mapSize);
            try {
                sum += getMapping(mapN).getFloat(offN);
            } catch (IOException e) {
                MappedByteBuffer mapping = getMapping(mapN);
                log.error("{} Err {} {} {}", mapN, offN, mapping.capacity(), mapping.limit());
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
                log.error("{} Err {} {}", p, mapping.capacity(), mapping.limit());
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
                log.warn("out of bounds at {} {} {}", i, mapN, offN);
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
