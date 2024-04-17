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
package org.nmrfx.processor.math;

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.translate.Base64;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;

/**
 * @author brucejohnson
 */
public class VecConverters {

    final private Vec vec;
    final boolean isComplex;

    public VecConverters(Vec vec) {
        this.vec = vec;
        this.isComplex = vec.isComplex();
    }

    void resize(int size, boolean complex) {
        vec.resize(size, complex);
    }

    public void convert(byte[] buffer, final boolean swap, final int nBytes) {
        int i;
        int j;
        int intVal0;
        int intVal1;
        int intVal2;
        int intVal3;
        int i0;
        int i1;
        int i2;
        int i3;
        boolean real = true;
        if (isComplex) {
            resize(nBytes / 8, isComplex);
        } else {
            resize(nBytes / 4, isComplex);
        }

        if (swap) {
            i0 = 3;
            i1 = 2;
            i2 = 1;
            i3 = 0;
        } else {
            i0 = 0;
            i1 = 1;
            i2 = 2;
            i3 = 3;
        }

        j = 0;
        double dReal = 0.0;

        for (i = 0; i < (nBytes / 4); i++) {
            intVal0 = (buffer[i0] << 24) & 0xFF000000;
            intVal1 = (buffer[i1] << 16) & 0x00FF0000;
            intVal2 = (buffer[i2] << 8) & 0x0000FF00;
            intVal3 = (buffer[i3]) & 0x000000FF;
            i0 += 4;
            i1 += 4;
            i2 += 4;
            i3 += 4;

            if (isComplex) {
                if (real) {
                    dReal = (intVal0) + (intVal1) + (intVal2) + intVal3;
                    real = false;
                } else {
                    double dImaginary = (intVal0) + (intVal1) + (intVal2) + intVal3;
                    vec.cvec[j] = new Complex(dReal, dImaginary);
                    j++;
                    real = true;
                }
            } else {
                vec.rvec[i] = (intVal0) + (intVal1) + (intVal2) + intVal3;
            }
        }
    }

    public void convertShort(byte[] buffer, final boolean swap, final int nBytes) {

        int i;
        int j;
        int intVal0;
        int intVal1;
        int i0;
        int i1;
        boolean real = true;

        if (isComplex) {
            resize(nBytes / 4, isComplex);
        } else {
            resize(nBytes / 2, isComplex);
        }

        if (swap) {
            i0 = 1;
            i1 = 0;
        } else {
            i0 = 0;
            i1 = 1;
        }

        j = 0;
        double dReal = 0.0;

        for (i = 0; i < (nBytes / 4); i++) {
            intVal0 = (buffer[i0] << 8) & 0x0000FF00;
            intVal1 = (buffer[i1]) & 0x000000FF;
            i0 += 2;
            i1 += 2;

            if (isComplex) {
                if (real) {
                    dReal = (intVal0) + (intVal1);
                    real = false;
                } else {
                    double dImaginary = (intVal0) + (intVal1);
                    vec.cvec[j] = new Complex(dReal, dImaginary);
                    j++;
                    real = true;
                }
            } else {
                vec.rvec[i] = (intVal0) + (intVal1);
            }
        }
    }

    public void convertDouble(byte[] buffer, final boolean swap, final int nBytes) {

        if (isComplex) {
            resize(nBytes / 16, isComplex);
        } else {
            resize(nBytes / 8, isComplex);
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, nBytes);
        if (swap) {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        } else {
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
        }
        DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
        if (isComplex) {
            for (int i = 0; i < vec.getSize(); i++) {
                double dReal = doubleBuffer.get(i * 2);
                double dImag = doubleBuffer.get(i * 2 + 1);
                vec.cvec[i] = new Complex(dReal, dImag);
            }
        } else {
            for (int i = 0; i < vec.getSize(); i++) {
                vec.rvec[i] = doubleBuffer.get(i);
            }
        }
    }

    public void convertFloat(byte[] buffer, final boolean swap, final int nBytes) {

        if (isComplex) {
            resize(nBytes / 8, isComplex);
        } else {
            resize(nBytes / 4, isComplex);
        }

        int i0;
        int i1;
        int i2;
        int i3;

        if (swap) {
            i0 = 3;
            i1 = 2;
            i2 = 1;
            i3 = 0;
        } else {
            i0 = 0;
            i1 = 1;
            i2 = 2;
            i3 = 3;
        }

        boolean real = true;
        double dReal = 0.0;

        for (int i = 0, j = 0; i < (nBytes / 4); i++) {
            int intVal0 = (buffer[i0] << 24) & 0xFF000000;
            int intVal1 = (buffer[i1] << 16) & 0x00FF0000;
            int intVal2 = (buffer[i2] << 8) & 0x0000FF00;
            int intVal3 = (buffer[i3]) & 0x000000FF;
            i0 += 4;
            i1 += 4;
            i2 += 4;
            i3 += 4;

            int intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
            if (isComplex) {
                if (real) {
                    dReal = Float.intBitsToFloat(intVal);
                    real = false;
                } else {
                    double dImaginary = Float.intBitsToFloat(intVal);
                    vec.set(j, dReal, dImaginary);
                    j++;
                    real = true;
                }
            } else {
                vec.set(i, Float.intBitsToFloat(intVal));
            }
        }
    }

    public void fromASDF(String asdfString) throws Exception {
        char ch;
        int stringLength = asdfString.length();
        StringBuilder sbuf = new StringBuilder();
        double value;
        double newValue;
        double oldValue = 0.0;
        boolean startLine = true;
        int i = 0;
        int iValue = 0;
        boolean diffMode = false;

        while (i < stringLength) {
            ch = asdfString.charAt(i);
            i++;

            if (startLine) {
                diffMode = false;

                if ((ch == '\n') || (ch == '\r')) {
                    continue;
                }

                //if ((ch == '+') || (ch == '-') || (ch == 'E') || (ch == '.') || Character.isDigit(ch)) {
                if (Character.isDigit(ch)) {
                    sbuf.append(ch);
                } else {
                    if (!((ch == '@') || (ch == ' ')
                            || ((ch >= 'A') && (ch <= 'I'))
                            || ((ch >= 'a') && (ch <= 'i')))) {
                        throw new Exception(
                                "fromasdf: Invalid SQZ char \"" + ch + "\"");
                    }

                    i--;
                    startLine = false;

                    if (sbuf.length() > 0) {
                        iValue = Integer.parseInt(sbuf.toString());

                        if (iValue >= vec.getSize()) {
                            resize(iValue + 1, false);
                        }

                        sbuf.setLength(0);
                    }
                }
            } else if (Character.isDigit(ch)) {
                sbuf.append(ch);
            } else {
                if (sbuf.length() > 0) {
                    value = Double.parseDouble(sbuf.toString());
                    sbuf.setLength(0);

                    if (diffMode) {
                        newValue = oldValue + value;
                    } else {
                        newValue = value;
                    }

                    if (iValue < 0) {
                        throw new Exception("fromasdf: Invalid index\n");
                    }

                    vec.set(iValue, newValue);
                    oldValue = newValue;
                    iValue--;
                }

                if (ch == '@') {
                    diffMode = false;
                    sbuf.append('0');
                } else if ((ch >= 'A') && (ch <= 'I')) {
                    diffMode = false;
                    sbuf.append(ch - 'A' + 1);
                } else if ((ch >= 'a') && (ch <= 'i')) {
                    diffMode = false;
                    sbuf.append('-');
                    sbuf.append(ch - 'a' + 1);
                } else if (ch == '%') {
                    diffMode = true;
                    sbuf.append('0');
                } else if ((ch >= 'J') && (ch <= 'R')) {
                    diffMode = true;
                    sbuf.append(ch - 'J' + 1);
                } else if ((ch >= 'j') && (ch <= 'r')) {
                    diffMode = true;
                    sbuf.append('-');
                    sbuf.append(ch - 'j' + 1);
                } else if ((ch == '\n') || (ch == '\r')) {
                    startLine = true;
                } else {
                    break;
                }
            }
        }
    }

    public static void bytesToFloat(byte[] byteArray, double[] fVec) {
        int n = byteArray.length / 4;

        if (fVec.length < n) {
            return;
        }

        int intVal;
        int intVal0;
        int intVal1;
        int intVal2;
        int intVal3;
        int i0;
        int i1;
        int i2;
        int i3;
        i0 = 0;
        i1 = 1;
        i2 = 2;
        i3 = 3;

        for (int i = 0; i < n; i++) {
            intVal0 = (byteArray[i0] << 24) & 0xFF000000;
            intVal1 = (byteArray[i1] << 16) & 0x00FF0000;
            intVal2 = (byteArray[i2] << 8) & 0x0000FF00;
            intVal3 = (byteArray[i3]) & 0x000000FF;
            i0 += 4;
            i1 += 4;
            i2 += 4;
            i3 += 4;
            intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
            fVec[i] = Float.intBitsToFloat(intVal);
        }
    }

    public Vec base64ToFloat(String s) {
        byte[] byteArray = Base64.decode(s);
        int n = byteArray.length / 4;
        resize(n, false);
        bytesToFloat(byteArray, vec.getRvec());
        vec.swapBytes();

        return vec;
    }

    public static void bytesToDouble(byte[] byteArray, double[] fVec) {
        int n = byteArray.length / 8;

        if (fVec.length < n) {
            return;
        }

        long mask = 0xFF;
        mask <<= 56;

        int i0 = 0;

        for (int i = 0; i < n; i++) {
            short shift = 64 - 8;
            long longVal = 0;
            mask = 0xFF;
            mask <<= 56;

            for (int j = 0; j < 8; j++) {
                long longVal0 = byteArray[i0];
                longVal += ((longVal0 << shift) & mask);
                i0++;
                mask >>= 8;
                shift -= 8;
            }

            fVec[i] = Double.longBitsToDouble(longVal);
        }
    }

    public Vec base64ToDouble(String s) {
        byte[] byteArray = Base64.decode(s);
        int n = byteArray.length / 8;
        resize(n, false);

        bytesToDouble(byteArray, vec.getRvec());
        vec.swapBytes8();

        return vec;
    }

}
