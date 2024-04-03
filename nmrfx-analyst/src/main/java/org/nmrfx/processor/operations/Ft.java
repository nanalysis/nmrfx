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
package org.nmrfx.processor.operations;

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Ft extends Operation implements Invertible {

    private final boolean negateImaginary;
    private final boolean negatePairs;

    @Override
    public Ft eval(Vec vector) throws ProcessingException {
        if (!invertOp) {
            ft(vector);
        } else {
            ift(vector);
        }
        return this;
    }

    /**
     * Fourier Transform Operation.
     *
     * @param negateImaginary
     * @param negatePairs
     * @throws ProcessingException
     * @see Vec
     */
    public Ft(boolean negateImaginary, boolean negatePairs) throws ProcessingException {
        this.negateImaginary = negateImaginary;
        this.negatePairs = negatePairs;
    }

    private void ft(Vec vector) throws ProcessingException {
        if (vector.isComplex()) {
            vector.fft(negatePairs, negateImaginary, true);
        }
    }

    private void ift(Vec vector) throws ProcessingException {
        if (vector.isComplex()) {
            vector.ifft(negatePairs, negateImaginary);
        }

    }
}
