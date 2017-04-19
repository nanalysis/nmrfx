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

import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.apache.commons.math3.complex.Complex;

/**
 *
 * @author johnsonb
 */
public class CShift extends Operation {

    private final int shiftValue;

    /**
     *
     * @param shift The amount of points to shift by.
     */
    public CShift(int shift) {
        this.shiftValue = shift;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();
        int shiftValue = this.shiftValue;

        if ((shiftValue != 0) && (((int) Math.abs(shiftValue)) < size)) {
            if (vector.isComplex()) {
                vector.makeApache();

                if (shiftValue > 0) {
                    int marker = size - shiftValue;
                    Complex[] temp = new Complex[shiftValue];
                    System.arraycopy(vector.cvec, marker, temp, 0, shiftValue);
                    System.arraycopy(vector.cvec, 0, vector.cvec, shiftValue,
                            size - shiftValue);
                    System.arraycopy(temp, 0, vector.cvec, 0, shiftValue);
                } else {
                    shiftValue = -shiftValue;

                    int marker = size - shiftValue;
                    Complex[] temp = new Complex[shiftValue];
                    System.arraycopy(vector.cvec, 0, temp, 0, shiftValue);
                    System.arraycopy(vector.cvec, shiftValue, vector.cvec, 0,
                            size - shiftValue);
                    System.arraycopy(temp, 0, vector.cvec, marker, shiftValue);
                }
            } else {
                vector.makeNotApache();
                if (shiftValue > 0) {
                    int marker = size - shiftValue;
                    double[] temp = new double[shiftValue];
                    System.arraycopy(vector.rvec, marker, temp, 0, shiftValue);
                    System.arraycopy(vector.rvec, 0, vector.rvec, shiftValue, size
                            - shiftValue);
                    System.arraycopy(temp, 0, vector.rvec, 0, shiftValue);
                } else {
                    shiftValue = -shiftValue;

                    int marker = size - shiftValue;
                    double[] temp = new double[shiftValue];
                    System.arraycopy(vector.rvec, 0, temp, 0, shiftValue);
                    System.arraycopy(vector.rvec, shiftValue, vector.rvec, 0, size
                            - shiftValue);
                    System.arraycopy(temp, 0, vector.rvec, marker, shiftValue);
                }
            }
        }

        return this;
    }

}
