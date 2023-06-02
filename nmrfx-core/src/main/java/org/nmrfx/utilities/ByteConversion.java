/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.utilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author brucejohnson
 */
public class ByteConversion {

    public static Number[] convert(byte[] buffer, String sMode, int start, int n) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        Number[] numbers = new Number[n];
        int offset = start;
        for (int i = 0; i < n; i++) {
            if (sMode.equals("b")) {
                numbers[i] = byteBuffer.get(offset);
            } else {
                byteBuffer.order(ByteOrder.BIG_ENDIAN);
                switch (sMode) {
                    case "i":
                        int intVal = byteBuffer.getInt(offset);
                        numbers[i] = intVal;
                        offset += 4;
                        break;
                    case "f":
                        float floatVal = byteBuffer.getFloat(offset);
                        numbers[i] = floatVal;
                        offset += 4;
                        break;
                    case "d":
                        double doubleVal = byteBuffer.getDouble(offset);
                        numbers[i] = doubleVal;
                        offset += 8;
                        break;
                    case "s":
                        short shortVal = byteBuffer.getShort(offset);
                        numbers[i] = shortVal;
                        offset += 2;
                        break;
                    case "l":
                        long longVal = byteBuffer.getLong(offset);
                        numbers[i] = longVal;
                        offset += 8;
                        break;
                    default:
                        break;
                }
            }
        }
        return numbers;
    }

}
