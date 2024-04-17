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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author brucejohnson
 */
public class VecIO {

    final private Vec vec;

    public VecIO(Vec vec) {
        this.vec = vec;
    }

    public void input(BufferedReader reader, int colNum) throws IOException, IllegalArgumentException {
        ArrayList<Double> rList = new ArrayList<>();
        ArrayList<Double> iList = null;
        if (vec.isComplex()) {
            iList = new ArrayList<>();
        }
        while (true) {
            String s = reader.readLine();
            if (s == null) {
                break;
            }
            String numString = null;
            String numStringI = null;
            if ((colNum < 0) && !vec.isComplex()) {
                numString = s;
            } else {
                if (colNum < 0) {
                    colNum = 0;
                }
                String[] sVals = s.split("\\s");
                if (vec.isComplex()) {
                    if (colNum >= (sVals.length - 1)) {
                        throw new IllegalArgumentException("Insufficient columns in string \"" + s + "\"");
                    }
                    numString = sVals[colNum];
                    numStringI = sVals[colNum + 1];
                } else {
                    if (colNum >= sVals.length) {
                        throw new IllegalArgumentException("Insufficient columns in string \"" + s + "\"");
                    }
                    numString = sVals[colNum];
                }
            }
            try {
                double value = Double.parseDouble(numString);
                rList.add(value);
                if (vec.isComplex() && (iList != null)) {
                    double valueI = Double.parseDouble(numStringI);
                    iList.add(valueI);
                }
            } catch (NumberFormatException nfE) {
                throw new IOException("Invalid double \"" + numString + "\"");
            }
        }
        int n = rList.size();
        vec.resize(n);
        if (!vec.isComplex()) {
            for (int i = 0; i < n; i++) {
                vec.rvec[i] = rList.get(i);
            }
        } else {
            for (int i = 0; i < n; i++) {
                double real = rList.get(i);
                double imag = iList.get(i);
                vec.cvec[i] = new Complex(real, imag);
            }
        }
    }

    public void output(BufferedWriter writer) throws IOException {
        if (vec.isComplex()) {
            for (int i = 0; i < vec.getSize(); i++) {
                String s = String.valueOf(vec.getReal(i)) + " " + String.valueOf(vec.getImag(i));
                writer.write(s, 0, s.length());
                writer.newLine();
            }
        } else {
            for (int i = 0; i < vec.getSize(); i++) {
                String s = String.valueOf(vec.getReal(i));
                writer.write(s, 0, s.length());
                writer.newLine();
            }
        }
    }

    public void outputXY(BufferedWriter writer) throws IOException {
        double delta = 1.0 / (vec.dwellTime * vec.centerFreq) / vec.getSize();
        double xVal;
        if (vec.isComplex()) {
            for (int i = 0; i < vec.getSize(); i++) {
                if (vec.freqDomain()) {
                    xVal = vec.getZeroRefValue() - i * delta;
                } else {
                    xVal = i * vec.dwellTime;
                }
                String s = String.valueOf(xVal) + " " + String.valueOf(vec.getReal(i)) + " " + String.valueOf(vec.getImag(i));
                writer.write(s, 0, s.length());
                writer.newLine();
            }
        } else {
            for (int i = 0; i < vec.getSize(); i++) {
                if (vec.freqDomain()) {
                    xVal = vec.getZeroRefValue() - i * delta;
                } else {
                    xVal = i * vec.dwellTime;
                }
                String s = String.valueOf(xVal) + " " + String.valueOf(vec.getReal(i));
                writer.write(s, 0, s.length());
                writer.newLine();
            }
        }
    }

    public void output() {
        if (vec.isComplex()) {
            for (int i = 0; i < vec.getSize(); i++) {
                System.out.println(vec.getReal(i) + " " + vec.getImag(i));
            }
        } else {
            for (int i = 0; i < vec.getSize(); i++) {
                System.out.println(vec.getReal(i));
            }
        }
    }

}
