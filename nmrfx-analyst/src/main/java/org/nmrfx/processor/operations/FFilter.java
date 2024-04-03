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
import org.nmrfx.processor.math.FirFilter;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * Operation for Finite Impulse Response (FIR) Filter. Works best with factor >= 3 and ncoefs >= factor * 16 + 1 (odd
 * number of coefficients).
 * <p>
 * Filter type:
 * <ul>
 * <li>'l' or 'lowpass' (use with factor=4)</li>
 * <li>'n' or 'notch' (use with fc=0.95)</li>
 * </ul></p>
 * <p>
 * Filter mode choices:
 * <ul>
 * <li>'z' or 'zeropad' </li>
 * <li>'r' or 'reflect'  </li>
 * <li>'s' or 'simple' </li>
 * <li>'p' or 'profile' </li>
 * </ul></p>
 *
 * @author bfetler
 */
@PythonAPI("pyproc")
public class FFilter extends Operation {

    /**
     * number of filter coefficients
     */
    private final int ncoefs;

    // fixme does FirFilter get initialized during clone??
    /**
     * FIR filter
     */
    private final FirFilter firFilter;

    /**
     * filter mode
     */
    private final String mode;

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        filter(vector);
        return this;
    }

    /**
     * Create FIR Filter.
     *
     * @param type   filter type
     * @param mode   filter mode
     * @param fc     frequency cutoff in fraction of PI radians
     * @param ncoefs number of coefficients
     * @param offset frequency offset
     * @throws ProcessingException
     */
    public FFilter(String type, String mode, double fc, int ncoefs, double offset) {
        this.ncoefs = ncoefs;
        this.mode = mode;
        firFilter = new FirFilter(fc, ncoefs, type);
        firFilter.setOffset(offset);
    }

    /**
     * Create FIR Filter.
     *
     * @param type   filter type
     * @param mode   filter mode
     * @param factor decimation factor
     * @param ncoefs number of coefficients
     * @param offset frequency offset
     * @throws ProcessingException
     */
    public FFilter(String type, String mode, int factor, int ncoefs, double offset) {
        this.ncoefs = ncoefs;
        this.mode = mode;
        firFilter = new FirFilter(factor, ncoefs, type);
        firFilter.setOffset(offset);
    }

    /**
     * Select filter mode and perform filtering.
     *
     * @param vector input vector
     * @throws ProcessingException
     * @see Vec
     */
    private void filter(Vec vector) throws ProcessingException {
        switch (mode.charAt(0)) {
            case 'z':  // zeroPad style
                zeroPadFilter(vector);
                break;
            case 'r':  // reflect pad style 
                reflectPadFilter(vector);
                break;
            case 'p':  // profile
                showProfile(vector);
                break;
            case 's':
            default:  // simple
                simpleFilter(vector);
                break;
        }
    }

    /**
     * Show filter coefficients (add FT for profile).
     *
     * @param vector input vector
     */
    private void showProfile(Vec vector) {
        vector.resize(ncoefs);
        double[] cfs = firFilter.getCoefs();
        for (int i = 0; i < ncoefs; i++) {
            vector.set(i, cfs[i], 0.0);
        }
    }

    /**
     * Simple filter of vector. Final vector is resized. after operation.
     *
     * @param vector input vector
     */
    private void simpleFilter(Vec vector) throws ProcessingException {
        firFilter.filter(vector);
    }

    /**
     * Filter zeroPad-style. Final vector is resized.
     *
     * @param vector input vector
     */
    private void zeroPadFilter(Vec vector) {
        int nc2 = ncoefs / 2;
        int newSize = vector.getSize() + nc2;
        vector.resize(newSize);
        vector.shift(nc2);
        firFilter.filter(vector);
        vector.fixWithBrukerFilter();
    }

    /**
     * Filter reflect Pad-style. Final vector is resized.
     *
     * @param vector input vector
     */
    private void reflectPadFilter(Vec vector) {
        int nc2 = ncoefs / 2;
        int newSize = vector.getSize() + nc2;
        vector.resize(newSize);
        vector.shift(nc2);
        if (vector.isComplex()) {
            double x0, y0, c0, s0;
            x0 = vector.getReal(nc2);
            y0 = vector.getImag(nc2);
            c0 = (x0 * x0 - y0 * y0) / (x0 * x0 + y0 * y0);
            s0 = 2.0 * x0 * y0 / (x0 * x0 + y0 * y0);
            Complex c1 = new Complex(c0, s0);  // twice 1st pt phase
            // reflect backwards w/ complex conjugate, mult by phase correction
            for (int i = 0; i < nc2; i++) {
                Complex c = vector.getComplex(2 * nc2 - i);
                vector.set(i, c.conjugate().multiply(c1));
            }
        } else {
            for (int i = 0; i < nc2; i++) {
                vector.set(i, vector.getReal(2 * nc2 - i));
            }
        }
        firFilter.filter(vector);
    }

}
