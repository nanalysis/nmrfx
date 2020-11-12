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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DataUtilities {

    public void readBytes(byte[] dataBytes, long newPos, int length) {
    }

    public static void readBytes(RandomAccessFile raFile, byte[] dataBytes, long newPos, int length) {
        try {
            raFile.seek(newPos);
            raFile.read(dataBytes, 0, length);
        } catch (IOException e) {
            System.err.println("Unable to read from dataset.");
            System.err.println(e.getMessage());
        }
    }

    public static boolean writeBytes(RandomAccessFile raFile, byte[] dataBytes, long newDataPos,
            int length) {
        try {
            //write the data bytes to the data file
            raFile.seek(newDataPos);
            raFile.write(dataBytes, 0, length);

            //store current pointers
        } catch (IOException e) {
            System.err.println("Unable to write record to file.");
            System.err.println(e.getMessage());

            return false;
        }

        return true;
    }

    public static int readSwapInt(DataInputStream dis, boolean swap)
            throws IOException {
        int intVal;

        if (swap) {
            int intVal0;
            int intVal1;
            int intVal2;
            int intVal3;
            intVal3 = (dis.readByte()) & 0x000000FF;
            intVal2 = (dis.readByte() << 8) & 0x0000FF00;
            intVal1 = (dis.readByte() << 16) & 0x00FF0000;
            intVal0 = (dis.readByte() << 24) & 0xFF000000;
            intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
        } else {
            intVal = dis.readInt();
        }

        return (intVal);
    }

    public static float readSwapFloat(DataInputStream dis, boolean swap)
            throws IOException {
        float fVal;

        if (swap) {
            int intVal;
            int intVal0;
            int intVal1;
            int intVal2;
            int intVal3;
            intVal3 = (dis.readByte()) & 0x000000FF;
            intVal2 = (dis.readByte() << 8) & 0x0000FF00;
            intVal1 = (dis.readByte() << 16) & 0x00FF0000;
            intVal0 = (dis.readByte() << 24) & 0xFF000000;
            intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
            fVal = Float.intBitsToFloat(intVal);
        } else {
            fVal = dis.readFloat();
        }

        return (fVal);
    }
}
