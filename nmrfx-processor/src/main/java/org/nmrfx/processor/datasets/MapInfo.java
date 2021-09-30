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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.collections4.map.*;

public class MapInfo {

    public static int maxSize = 100;

    public static class MyLRUMap extends LRUMap {

        int ip = 0;
        int gcAt = 1024;

        public MyLRUMap(int maxSize) {
            super(maxSize);
        }

        @Override
        public boolean removeLRU(LinkEntry entry) {
            ip++;
            if (ip > gcAt) {
                System.gc();
                ip = 0;
            }
            MapInfo mapInfo = (MapInfo) entry.getValue();
            mapInfo.clean();
            return true;
        }
    }
    public MappedByteBuffer buffer = null;
    final long start;
    final long size;
    final FileChannel.MapMode mapMode;
    final ByteOrder byteOrder;
    static int nMaps = 0;
    static Map mapMap = null;
    static MapInfo lastTouched = null;

    public MapInfo(final long start, final long size, final FileChannel.MapMode mapMode, final ByteOrder byteOrder) {
        this.start = start;
        this.size = size;
        this.mapMode = mapMode;
        this.byteOrder = byteOrder;
    }

    public void mapIt(final RandomAccessFile raFile) throws IOException {
        try {
            buffer = raFile.getChannel().map(mapMode, start, size);
            buffer.order(byteOrder);
            if (size > 4 * 1024 * 1024) {
                nMaps++;
                if (mapMap == null) {
                    mapMap = Collections.synchronizedMap(new MyLRUMap(maxSize));
                }
                mapMap.put(this, this);
            }
//System.out.println(size + " " + nMaps + " " + start);
        } catch (IOException e) {
            raFile.close();
            throw e;
        }

    }

    public static int getMaxSize() {
        return maxSize;
    }

    public static boolean setMaxSize(final int newMaxSize) {
        if (mapMap != null) {
            return false;
        } else {
            maxSize = newMaxSize;
            return true;
        }
    }

    public void touch() {
        if (buffer == null) {
            return;
        }
        if (mapMap == null) {
            return;
        }
        if (this != lastTouched) {
            mapMap.get(this);
            lastTouched = this;
        }
    }

    public void force() {
        if (buffer == null) {
            return;
        }
        buffer.force();
    }

    public void clean() {
        if (buffer == null) {
            return;
        }
        if (mapMap != null) {
            mapMap.remove(this);
        }
        closeDirectBuffer(buffer);
        buffer = null;
        nMaps--;
    }

    // code from 
    // https://stackoverflow.com/questions/2972986/
    // how-to-unmap-a-file-from-memory-mapped-using-filechannel-in-java
    public static void closeDirectBuffer(ByteBuffer cb) {
        if (cb == null || !cb.isDirect()) {
            return;
        }
        // we could use this type cast and call functions without reflection code,
        // but static import from sun.* package is risky for non-SUN virtual machine.
        //try { ((sun.nio.ch.DirectBuffer)cb).cleaner().clean(); } catch (Exception ex) { }

        // JavaSpecVer: 1.6, 1.7, 1.8, 9, 10
        boolean isOldJDK = System.getProperty("java.specification.version", "99").startsWith("1.");
        try {
            if (isOldJDK) {
                Method cleaner = cb.getClass().getMethod("cleaner");
                cleaner.setAccessible(true);
                Method clean = Class.forName("sun.misc.Cleaner").getMethod("clean");
                clean.setAccessible(true);
                clean.invoke(cleaner.invoke(cb));
            } else {
                Class unsafeClass;
                try {
                    unsafeClass = Class.forName("sun.misc.Unsafe");
                } catch (ClassNotFoundException ex) {
                    // jdk.internal.misc.Unsafe doesn't yet have an invokeCleaner() method,
                    // but that method should be added if sun.misc.Unsafe is removed.
                    unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
                }
                Method clean = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
                clean.setAccessible(true);
                Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                Object theUnsafe = theUnsafeField.get(null);
                clean.invoke(theUnsafe, cb);
            }
        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | NoSuchFieldException | NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        }
    }
}
