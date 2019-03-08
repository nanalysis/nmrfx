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
    private final boolean adjustRef;

    /**
     *
     * @param shift The amount of points to shift by.
     */
    public CShift(int shift, boolean adjustRef) {
        this.shiftValue = shift;
        this.adjustRef = adjustRef;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();

        int iShift = this.shiftValue;

        if ((iShift != 0) && (((int) Math.abs(iShift)) < size)) {
            if (vector.isComplex()) {
                vector.makeApache();

                if (iShift > 0) {
                    int marker = size - iShift;
                    Complex[] temp = new Complex[iShift];
                    System.arraycopy(vector.cvec, marker, temp, 0, iShift);
                    System.arraycopy(vector.cvec, 0, vector.cvec, iShift,
                            size - iShift);
                    System.arraycopy(temp, 0, vector.cvec, 0, iShift);
                } else {
                    iShift = -iShift;

                    int marker = size - iShift;
                    Complex[] temp = new Complex[iShift];
                    System.arraycopy(vector.cvec, 0, temp, 0, iShift);
                    System.arraycopy(vector.cvec, iShift, vector.cvec, 0,
                            size - iShift);
                    System.arraycopy(temp, 0, vector.cvec, marker, iShift);
                }
            } else {
                vector.makeNotApache();
                if (iShift > 0) {
                    int marker = size - iShift;
                    double[] temp = new double[iShift];
                    System.arraycopy(vector.rvec, marker, temp, 0, iShift);
                    System.arraycopy(vector.rvec, 0, vector.rvec, iShift, size
                            - iShift);
                    System.arraycopy(temp, 0, vector.rvec, 0, iShift);
                } else {
                    iShift = -iShift;

                    int marker = size - iShift;
                    double[] temp = new double[iShift];
                    System.arraycopy(vector.rvec, 0, temp, 0, iShift);
                    System.arraycopy(vector.rvec, iShift, vector.rvec, 0, size
                            - iShift);
                    System.arraycopy(temp, 0, vector.rvec, marker, iShift);
                }
            }
            if (adjustRef) {
                vector.adjustRef(-shiftValue, size);
            }
        }

        return this;
    }

}
