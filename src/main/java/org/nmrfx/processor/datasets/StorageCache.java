/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.collections4.map.LRUMap;

/**
 *
 * @author brucejohnson
 */
public class StorageCache {

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

//            System.out.println("remove " + key.blockNum);
            if (key.file.writable) {
                try {
                    key.file.writeBlock(key.blockNum, entry.getValue());
                } catch (IOException ex) {
                    Logger.getLogger(StorageCache.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return true;  // actually delete entry
        }
    }

    public StorageCache() {
        TrackingLRUMap lruMap = new TrackingLRUMap(16000);

        buffers = Collections.synchronizedMap(lruMap);

    }

    //                .removalListener((DatasetKey key, ByteBuffer buffer, RemovalCause cause)
//                        -> {
//                    System.out.println("remove " + key.blockNum);
//                    saveValues(buffer, key);
//                })
    public ByteBuffer getBuffer(DatasetKey key) {
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
//                    System.out.println("read io " + key.blockNum + " " + offset);
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
//                    System.out.println("write io " + key.blockNum + " " + offset);
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
