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
package org.nmrfx.processor.datasets.vendor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.apache.commons.math3.complex.Complex;

public class DataConvert {
// copy read data into double array

    public enum DataType {
        FLOAT,
        DOUBLE,
        SHORT,
        INT;
    }

    public static void scale(double[] data, double scale) {
        for (int i = 0; i < data.length; i++) {
            data[i] /= scale;
        }
    }

    public static void negatePairs(double[] data) {
        for (int i = 0; i < data.length; i += 4) {
            data[i + 2] *= -1.0;
            data[i + 3] *= -1.0;
        }
    }

    public static void toArrays(double[] data, double[] rdata, double[] idata, int size) {
        for (int i = 0; i < size; i += 2) {
            rdata[i / 2] = data[i];
            idata[i / 2] = data[i + 1];
        }
    }

    public static void toComplex(double[] data, Complex[] cData, int size) {
        for (int i = 0; i < size; i += 2) {
            cData[i / 2] = new Complex(data[i], data[i + 1]);
        }
    }

    public static void swapXY(double[] data, int size) {
        for (int i = 0; i < size; i += 2) {
            double hold = data[i];
            data[i] = data[i + 1];
            data[i + 1] = hold;
        }
    }

    public static double[] copyVecData(byte[] dataBuf, double[] data, int size, DataType type) {
        ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
        if (data == null) {
            data = new double[size];
        }
        switch (type) {
            case DOUBLE: {
                DoubleBuffer dbuf = ByteBuffer.wrap(dataBuf).order(byteOrder).asDoubleBuffer();
                for (int j = 0; j < size; j++) {
                    data[j] = dbuf.get(j);
                }
            }
            break;
            case FLOAT: {
                FloatBuffer fbuf = ByteBuffer.wrap(dataBuf).order(byteOrder).asFloatBuffer();
                for (int j = 0; j < size; j++) {
                    data[j] = fbuf.get(j);
                }
            }
            break;
            case INT: {
                IntBuffer ibuf = ByteBuffer.wrap(dataBuf).order(byteOrder).asIntBuffer();
                for (int j = 0; j < size; j++) {
                    data[j] = ibuf.get(j);
                }
            }
            break;
            case SHORT: {
                ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).order(byteOrder).asShortBuffer();
                for (int j = 0; j < size; j++) {
                    data[j] = sbuf.get(j);
                }
            }
            break;
        }
        return data;
    }
}
