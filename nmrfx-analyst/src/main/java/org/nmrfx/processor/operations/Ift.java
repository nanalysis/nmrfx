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

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

import static org.nmrfx.processor.math.Vec.apache_ift;

/**
 * Inverse Fourier Transform.
 *
 * @author bfetler
 */
@PythonAPI("pyproc")
public class Ift extends Operation {

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        vector.setGroupDelay(0.0);
        ift(vector);
        return this;
    }

    /**
     * Create operation for inverse Fourier transform.
     *
     * @throws ProcessingException
     */
    public Ift() throws ProcessingException {
    }

    private void ift(Vec vector) throws ProcessingException {
        if (vector.isComplex()) {
            if (!vector.useApache()) {
                vector.makeApache();
            }
            vector.checkPowerOf2();
            Complex[] ftvector = new Complex[vector.getSize()];
            Complex[] cvec = vector.getCvec();
            System.arraycopy(cvec, 0, ftvector, 0, vector.getSize());
            Complex[] ftResult = apache_ift(ftvector);
            System.arraycopy(ftResult, 0, cvec, 0, vector.getSize());
            vector.setFreqDomain(false);
            vector.setGroupDelay(0.0);
        }
    }

}
