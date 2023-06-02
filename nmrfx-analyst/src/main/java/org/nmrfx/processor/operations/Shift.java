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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.operations;

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Shift extends Operation {

    private final int shiftValue;
    private final boolean adjustRef;

    public Shift(int shift, boolean adjustRef) {
        this.shiftValue = shift;
        this.adjustRef = adjustRef;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();

        if ((shiftValue != 0) && (((int) Math.abs(shiftValue)) < size)) {
            if (vector.isComplex()) {
                Complex[] cvec = vector.cvec;
                if (shiftValue > 0) {
                    System.arraycopy(cvec, 0, cvec, shiftValue, size - shiftValue);
                    for (int i = 0; i < shiftValue; i++) {
                        cvec[i] = new Complex(0.0, 0.0);
                    }
                } else {
                    int shiftValue = -this.shiftValue;
                    System.arraycopy(cvec, shiftValue, cvec, 0, size - shiftValue);
                    for (int i = 0; i < shiftValue; i++) {
                        cvec[size - shiftValue + i] = new Complex(0.0, 0.0);
                    }
                }
            } else {
                double[] vec = vector.rvec;
                if (shiftValue > 0) {
                    System.arraycopy(vec, 0, vec, shiftValue, size - shiftValue);
                    for (int i = 0; i < shiftValue; i++) {
                        vec[i] = 0.0;
                    }
                } else {
                    int shiftValue = -this.shiftValue;
                    System.arraycopy(vec, shiftValue, vec, 0, size - shiftValue);
                    for (int i = 0; i < shiftValue; i++) {
                        vec[size - shiftValue + i] = 0.0;
                    }
                }
            }
            if (adjustRef) {
                vector.adjustRef(-shiftValue, size);
            }

        }

        return this;
    }

}
