/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.datasets;

import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author brucejohnson
 */
public class StorageCache {
    private static final Logger log = LoggerFactory.getLogger(StorageCache.class);

    Map<DatasetKey, ByteBuffer> buffers;
    ByteBuffer activeBuffer = null;
    DatasetKey activeKey = null;

    public static class DatasetKey {

        SubMatrixFile file;
        int blockNum;

        public DatasetKey(SubMatrixFile file, int blockNum) {
            this.file = file;
            this.blockNum = blockNum;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 89 * hash + Objects.hashCode(this.file);
            hash = 89 * hash + this.blockNum;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final DatasetKey other = (DatasetKey) obj;
            if (this.blockNum != other.blockNum) {
                return false;
            }
            if (!Objects.equals(this.file, other.file)) {
                return false;
            }
            return true;
        }

        public int getBlockNum() {
            return blockNum;
        }

        public String toString() {
            return String.valueOf(blockNum);
        }
    }

    static class TrackingLRUMap<K, V> extends LRUMap<DatasetKey, ByteBuffer> {

        TrackingLRUMap(int maxSize) {
            super(maxSize);
        }

        protected boolean removeLRU(LinkEntry<DatasetKey, ByteBuffer> entry) {
            //releaseResources(entry.getValue());  // release resources held by entry
            DatasetKey key = entry.getKey();

            if (key.file.writable) {
                try {
                    key.file.writeBlock(key.blockNum, entry.getValue());
                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
            return true;  // actually delete entry
        }
    }

    public StorageCache() {
        TrackingLRUMap lruMap = new TrackingLRUMap(1024);

        buffers = new ConcurrentHashMap<DatasetKey, ByteBuffer>(lruMap);

    }

    public ByteBuffer getBuffer(DatasetKey key) throws IOException {
        return buffers.get(key);
    }

    public void flush(SubMatrixFile file) throws IOException {
        activeBuffer = null;
        activeKey = null;
        Iterator<Entry<DatasetKey, ByteBuffer>> iter = buffers.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<DatasetKey, ByteBuffer> entry = iter.next();
            if (entry.getKey().file == file) {
                if (file.writable) {
                    file.writeBlock(entry.getKey().blockNum, entry.getValue());
                }
                iter.remove();
            }
        }
    }

    public synchronized void io(DatasetKey[] vecKeys, int[] offsets, double[] vec, int mode) throws IOException {
        DatasetKey lastKey = null;
        ByteBuffer buffer = null;
        for (int i = 0; i < vec.length; i++) {
            if ((i == 0) || (vecKeys[i] != vecKeys[i - 1])) {
                buffer = getABuffer(vecKeys[i]);
            }
            if (mode == 1) {
                vec[i] = buffer.getFloat(offsets[i] * Float.BYTES);
            } else {
                buffer.putFloat(offsets[i] * Float.BYTES, (float) vec[i]);
            }
        }
    }

    ByteBuffer getABuffer(DatasetKey key) throws IOException {
        ByteBuffer buffer;
        if (key == activeKey) {
            buffer = activeBuffer;
        } else {
            buffer = buffers.get(key);
        }
        if (buffer == null) {
            buffer = key.file.readBlock(key.blockNum);
            buffers.put(key, buffer);
        }
        activeBuffer = buffer;
        activeKey = key;
        return buffer;
    }

    public synchronized float io(DatasetKey key, int offset, float v, int mode) throws IOException {
        float value = 0.0f;
        switch (mode) {
            case 0: {
                ByteBuffer buffer;
                if (key == activeKey) {
                    buffer = activeBuffer;
                } else {
                    buffer = buffers.get(key);
                }
                if (buffer == null) {
                    buffer = key.file.readBlock(key.blockNum);
                    buffers.put(key, buffer);
                }
                activeBuffer = buffer;
                activeKey = key;
                value = buffer.getFloat(offset * Float.BYTES);
            }
            break;
            case 1: {
                ByteBuffer buffer;
                if (key == activeKey) {
                    buffer = activeBuffer;
                } else {
                    buffer = buffers.get(key);
                }
                if (buffer == null) {
                    buffer = key.file.readBlock(key.blockNum);
                    buffers.put(key, buffer);
                }
                activeBuffer = buffer;
                activeKey = key;
                buffer.putFloat(offset * Float.BYTES, v);
            }
            break;
        }
        return value;
    }

}
