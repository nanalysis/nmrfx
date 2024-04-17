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

import org.apache.commons.math3.complex.Complex;

import java.nio.*;

/**
 * @author brucejohnson
 */
public class DataConvert {

// copy read data into double array

    /**
     *
     */
    public enum DataType {

        /**
         *
         */
        FLOAT,
        /**
         *
         */
        DOUBLE,
        /**
         *
         */
        SHORT,
        /**
         *
         */
        INT;
    }

    private DataConvert() {
    }

    /**
     * Scale values (divide) in array by specified parameter
     *
     * @param data  array of data values
     * @param scale divide values by this value
     */
    public static void scale(double[] data, double scale) {
        for (int i = 0; i < data.length; i++) {
            data[i] /= scale;
        }
    }

    /**
     * Multiply alternate pairs of data by -1.0
     *
     * @param data array of data values
     */
    public static void negatePairs(double[] data) {
        for (int i = 0; i < data.length; i += 4) {
            data[i + 2] *= -1.0;
            data[i + 3] *= -1.0;
        }
    }

    /**
     * Copy data values from a complex (alternate values) array into two arrays
     * of real and imaginary values
     *
     * @param data  array of data values
     * @param rdata the array to receive the real values
     * @param idata the array to receive the imaginary values
     * @param size  the number of complex values to copy
     */
    public static void toArrays(double[] data, double[] rdata, double[] idata, int size) {
        for (int i = 0; i < size; i += 2) {
            rdata[i / 2] = data[i];
            idata[i / 2] = data[i + 1];
        }
    }

    /**
     * Copy values from a complex (alternate values) array into an array
     * containing Complex objects
     *
     * @param data  array of data values
     * @param cData the array of complex objects
     * @param size  the number complex values to copy
     */
    public static void toComplex(double[] data, Complex[] cData, int size) {
        for (int i = 0; i < size; i += 2) {
            cData[i / 2] = new Complex(data[i], data[i + 1]);
        }
    }

    /**
     * Swap the real and imaginary values of a complex (alternate values) array
     *
     * @param data array of data values
     * @param size the number of complex values to swap
     */
    public static void swapXY(double[] data, int size) {
        for (int i = 0; i < size; i += 2) {
            double hold = data[i];
            data[i] = data[i + 1];
            data[i + 1] = hold;
        }
    }

    /**
     * Copy an array of bytes into an array of double values, converting the
     * correct number of bytes into each data value, based on the DataType. The
     * DataType can be DOUBLE, FLOAT, INT, or SHORT
     *
     * @param dataBuf the array of bytes to convert
     * @param data    the array of double values to receive the converted values.
     *                If null a new array will be created
     * @param size    The number of data values to create
     * @param type    The type of the data in the data buffer.
     * @return the new array of double values
     */
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
