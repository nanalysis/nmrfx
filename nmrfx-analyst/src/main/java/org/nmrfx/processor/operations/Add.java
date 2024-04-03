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
public class Add extends Operation {

    private final int first;
    private final int last;
    private final double real;
    private final double imag;
    private final Complex complex;
    private final boolean isReal;

    public Add(double real, double imag) {
        this(real, imag, 0, -1);
    }

    public Add(double real) {
        this(real, 0, 0, -1);

    }

    public Add(Complex complex) {
        this(complex.getReal(), complex.getImaginary(), 0, -1);
    }

    public Add(double real, double imag, int first, int last) {
        if (imag == 0) {
            this.complex = null;
            this.real = real;
            this.imag = 0;
            this.isReal = true;
        } else {
            this.complex = new Complex(real, imag);
            this.real = real;
            this.imag = imag;
            this.isReal = false;
        }
        //if first is negative, make it zero, else make it first
        this.first = Math.max(0, first);
        //If max is given as positive, then set it, else set it as -1.
        // if first > last, then set first=last so nothing is changed.
        this.last = last;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();

        int last;
        if (this.last < 0) {
            last = vector.getSize() - 1;
        } else {
            last = this.last;
        }
        last = Math.min(vector.getSize() - 1, last);

        if (vector.isComplex()) {
            // If the Vector is complex and we are only adding a real value,
            // then only increment the real part.
            if (isReal) {
                for (int i = first; i <= last; ++i) {
                    vector.set(i, vector.getReal(i) + real, vector.getImag(i));
                }
            } else { // Vector is complex, adding complex.
                for (int i = first; i <= last; ++i) {
                    vector.set(i, vector.getComplex(i).add(this.complex));
                }
            }

        } else { //vector is Real, add only real part (ERROR if imag != 0?)
            for (int i = first; i <= last; ++i) {
                vector.set(i, vector.getReal(i) + real);
            }
        }

        return this;
    }

}
