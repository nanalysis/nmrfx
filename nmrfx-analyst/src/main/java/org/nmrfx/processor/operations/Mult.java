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
public class Mult extends Operation {

    private final boolean isReal;
    private final double real;
    private final double imag;
    private final Complex complex;
    private final int first;
    private final int last;

    public Mult(double real, double imag, int first, int last) {
        if (imag == 0) {
            this.isReal = true;
            this.real = real;
            this.imag = 0;
            this.complex = null;
        } else {
            this.isReal = false;
            this.real = real;
            this.imag = imag;
            this.complex = new Complex(real, imag);
        }
        this.last = Math.max(-1, last);
        this.first = Math.max(0, first);
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int last;
        if (this.last < this.first) {
            last = vector.getSize() - 1;
        } else {
            last = this.last;
        }

        if (isReal) {
            for (int i = first; i <= last; ++i) {
                vector.multiply(i, real, 0);
            }
        } else if (vector.useApache()) {
            for (int i = first; i <= last; ++i) {
                vector.multiply(i, complex);
            }
        } else {
            for (int i = first; i <= last; ++i) {
                vector.multiply(i, real, imag);
            }
        }

        return this;
    }

}
