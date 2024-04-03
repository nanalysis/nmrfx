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
import org.nmrfx.math.VecBase;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * Finite Impulse Response (FIR) filter. See Oppenheim, "Digital Signal Processing", pp. 468 - 472.
 * <p>
 * Example: DECIM=1664 GRPDLY=67.98 NCOEF=226237 (DECIM = 52 * 32 may be multistage).</p>
 * <p>
 * Example: factor=4 groupDelay=68 ncoefs=544 (similar GRPDLY as above, groupDelay sets filter sharpness).</p>
 * <ul>
 * <li>A close to even integer group delay is usually found in the Bruker GRPDLY parameter (see Vec
 * fixWithBrukerFilter).</li>
 * <li>An odd number of coefficients seems to work best for FFilter operation.</li>
 * </ul>
 *
 * @author bfetler
 */
public class FirFilter {

    /**
     * coefficient array
     */
    private double[] coefs;

    /**
     * decimation factor, must be positive integer, usually > 2
     */
    private int factor;

    /**
     * number of coefficients, usually odd for FFilter, even for fixBrukerFilter
     */
    private int ncoefs;

    /**
     * filter cutoff, fraction of PI radians, usually 0.0 &lt; fc &lt; 1.0
     */
    private double fc = 1.0;

    /**
     * frequency offset in Hz from center of spectrum, usually -0.5sw &le; offset &le; 0.5sw
     * <p>
     * Alternate (not implemented): could be set as fraction of sw.</p>
     */
    private double offset = 0.0;

    /**
     * filter type: 'l' - 'lowpass', 'n' - 'notch
     */
    private String type = "lowpass";

    /**
     * Constructor
     *
     * @param factor     decimation factor
     * @param groupDelay group delay in points, sets number of coefficients
     * @param type       filter type 'lowpass' or 'notch'
     * @throws ProcessingException if a processing error occurs
     */
    public FirFilter(int factor, double groupDelay, String type) throws ProcessingException {
        this(factor, (int) (2.0 * groupDelay * factor + 0.5), type);
    }

    /**
     * Constructor
     *
     * @param factor decimation factor
     * @param ncoefs number of coefficients
     * @param type   filter type 'lowpass' or 'notch'
     * @throws ProcessingException if a processing error occurs
     */
    public FirFilter(int factor, int ncoefs, String type) throws ProcessingException {
        if (factor < 2) {
            throw new ProcessingException("filter factor must be greater than 1");
        }
        if (ncoefs < 2) {
            ncoefs = factor * 8 + 1;
            System.out.println("number of filter coefficients less than 2, reset to " + ncoefs);
        }
        if (ncoefs < 2 * factor) {
            ncoefs = factor * 8 + 1;
            System.out.println("number of filter coefficients too small, reset to " + ncoefs);
        }
        this.factor = factor;
        this.ncoefs = ncoefs;
        this.fc = 1.0 / factor;
        this.type = type;
        calcCoefs();
    }

    /**
     * Constructor
     *
     * @param fc     filter cutoff, usually 0.0 &lt; fc &lt; 1.0
     * @param ncoefs number of coefficients
     * @param type   filter type 'lowpass' or 'notch'
     * @throws ProcessingException if a processing error occurs
     */
    public FirFilter(double fc, int ncoefs, String type) throws ProcessingException {
        if (fc <= 0) {
            throw new ProcessingException("filter cutoff must be greater than 0");
        }
        if (ncoefs < 2) {
            ncoefs = 33;
            System.out.println("number of filter coefficients less than 2, reset to " + ncoefs);
        }
        this.fc = fc;  // usually 0 < fc < 1.0
        this.ncoefs = ncoefs;
        this.factor = 1;
        this.type = type;
        calcCoefs();
    }

    /**
     * Set offset frequency.
     *
     * @param offset frequency
     */
    public void setOffset(double offset) {
        this.offset = offset;
    }

    /**
     * Get coefficients.
     *
     * @return coefficients array
     */
    public double[] getCoefs() {
        return coefs;
    }

    /**
     * Calculate filter coefficients.
     * <p>
     * h[n] = w[n] * sin(wc(n-M/2)) / (pi * (n-M/2))</p>
     * <p>
     * M = ncoefs - 1; wc = pi / factor; i = n-M/2; w[n] = weighting</p>
     */
    private void calcCoefs() {
        coefs = new double[ncoefs];
        int nc2 = ncoefs / 2;
        int i;
        double d, f;
        for (i = 0; i < nc2 + 1; i++) {
            f = Math.PI * (i - 0.5 * (ncoefs - 1));
            if (f != 0.0) {
                d = Math.sin(f * fc) / f;
            } else {
                d = fc;
            }
            d *= hanning(f);  // weighting
            coefs[i] = d;
            coefs[ncoefs - i - 1] = d;
        }
        double sum = 0.0;
        for (i = 0; i < ncoefs; i++) {
            sum += coefs[i];
        }
        sum = 1.0 / sum;
        for (i = 0; i < ncoefs; i++) {
            coefs[i] *= sum;
        }
        if (type.startsWith("n")) {  // notch or highpass filter
            for (i = nc2 + 1; i < ncoefs; i += 2) {
                coefs[i] *= -1.0;
                coefs[ncoefs - i - 1] *= -1.0;
            }
        }
    }

    /**
     * Hamming weighting function. w[n] = 0.54 + 0.46 * cos(2*PI*n/M)
     *
     * @param g discrete frequency
     * @return weighting at point g
     */
    private double hamming(double g) {
        return 0.54 + 0.46 * Math.cos(2 * g / (ncoefs - 1));
    }

    /**
     * Hanning weighting function. w[n] = 0.5 + 0.5 * cos(2*PI*n/M)
     *
     * @param g discrete frequency
     * @return weighting at point g
     */
    private double hanning(double g) {
        return 0.5 + 0.5 * Math.cos(2 * g / (ncoefs - 1));
    }

    /**
     * Blackman weighting function. w[n] = 0.42 + 0.5 * cos(2*PI*n/M) + 0.08 * cos(4*PI*n/M)
     *
     * @param g discrete frequency
     * @return weighting at point g
     */
    private double blackman(double g) {
        return 0.42 + 0.5 * Math.cos(2 * g / (ncoefs - 1)) + 0.08 * Math.cos(4 * g / (ncoefs - 1));
    }

    /**
     * Perform filtering on a vector.
     *
     * @param vector input
     * @return vector output
     * @see Vec
     */
    public Vec filter(Vec vector) throws ProcessingException {
        int size = vector.getSize() - 2 * (ncoefs / 2);
        if (size <= 0) {
            throw new ProcessingException("filter error: number of coefficients " + ncoefs
                    + " greater than vector size " + vector.getSize());
        }
        size /= factor;
        if (size <= 0) {
            throw new ProcessingException("filter error: new vector size less than factor " + factor);
        }
        Vec outVec = new Vec(size, vector.isComplex());
        VecBase.copyRef(vector, outVec);
        if (offset != 0.0) {
            vector.multiplyByFrequency(offset, ncoefs / 2);
        }
        if (vector.isComplex()) {
            if (vector.useApache()) {
                Complex[] cvec = vector.getCvec();
                for (int i = 0; i < size; i++) {
                    double sumr = 0.0, sumi = 0.0;
                    for (int j = 0; j < ncoefs; j++) {
                        sumr += cvec[i * factor + j].getReal() * coefs[j];
                        sumi += cvec[i * factor + j].getImaginary() * coefs[j];
                    }
                    outVec.set(i, new Complex(sumr, sumi));
                }
            } else {
                double[] rvec = vector.getRvec();
                double[] ivec = vector.getIvec();
                for (int i = 0; i < size; i++) {
                    double sumr = 0.0, sumi = 0.0;
                    for (int j = 0; j < ncoefs; j++) {
                        sumr += rvec[i * factor + j] * coefs[j];
                        sumi += ivec[i * factor + j] * coefs[j];
                    }
                    outVec.set(i, sumr, sumi);
                }
            }
        } else {  // vector not Complex
            double[] rvec = vector.getRvec();
            for (int i = 0; i < size; i++) {
                double sumr = 0.0;
                for (int j = 0; j < ncoefs; j++) {
                    sumr += rvec[i * factor + j] * coefs[j];
                }
                outVec.set(i, sumr);
            }
        }
        if (offset != 0.0 && type.startsWith("n")) {  // notch filter
            outVec.multiplyByFrequency(-offset, 0);
        }
        vector.resize(size);
        outVec.copy(vector);
        return vector;
    }

    /**
     * Create simulated Bruker FID correction artifact.
     *
     * @param vector input vector
     * @return FID correction vector
     * @see Vec
     */
    public Vec brukerSimVec(Vec vector) {
// e.g. factor=4 ncoefs=544,  544/(2*4) = 68 = groupDelay
        int size = vector.getSize();
        double amp = 10000.0;
        double ph2 = 180.0;
        double m2 = -0.00005;  // filter not quite equiripple
        double dk = 0.9999;
        int newSize = factor * size + ncoefs;
        Vec base = new Vec(newSize, vector.isComplex());
        base.genSignal(0.0, dk, amp, 0.0);  // create signal
        int nc2 = ncoefs / 2;
        base.shift(nc2);  // shift by 1/2 filter length
        filter(base);     // apply filter
        base.resize(ncoefs / factor);
        base.genSignal(0.0, Math.pow(dk, factor), amp * (1.0 - m2), ph2);  // subtract ideal signal
        return base;  // return filter distortion
    }

}
