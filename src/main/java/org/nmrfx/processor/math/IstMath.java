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

import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/**
 * Math routines for Iterative Soft Thresholding (IST) on a 1D vector. This is separate from the Ist.java operation so
 * that it may be used with data weaving schemes for nD IST.
 *
 * @author bfetler
 */
public class IstMath {

    /**
     * Cutoff threshold as a fraction of maximum height : e.g. 0.98.
     *
     * @see #ist
     * @see #cutAboveThreshold
     */
    private double threshold;

    /**
     * Number of loops to iterate over : e.g. 300.
     *
     * @see #ist
     */
    private int loops;

    /**
     * Adjust threshold based on loop count.
     *
     * @see #ist
     */
    private boolean adjustThreshold;

    private boolean allValues;

    private boolean scaleValues = false;

    /**
     * Sample schedule used for non-uniform sampling. Specifies array elements where data is present.
     *
     * @see #ist
     * @see #zero_samples
     * @see SampleSchedule
     */
    private SampleSchedule sampleSchedule = null;

    /**
     * Inverse list of sample schedule. Specifies array elements where data is not present.
     *
     * @see #zeroSample
     * @see #calcZeroes
     * @see SampleSchedule#getSamples
     */
    private int[] zero_samples = null;

    /**
     * Specifies one of several cutoff algorithms.
     *
     * @see Ist
     * @see #cutAboveThreshold
     */
    private String alg = "std";

    /**
     * Optional flag used with algorithm to return inverse-FT'ed data, instead of FT'ed data.
     */
    private boolean timeDomain = true;

    /**
     * Create calculation for Iterative Soft Threshold.
     *
     * @param threshold cutoff threshold as a fraction of maximum height
     * @param loops number of loops to iterate over
     * @param schedule sample schedule
     * @param alg alternate cutoff algorithm
     * @param timeDomain result is in timeDomain
     * @param adjustThreshold Use built-in protocol to reduce threshold during processing
     * @param allValues Replace all values (including sampled) processing
     * @throws ProcessingException if a processing error occurs
     */
    public IstMath(double threshold, int loops, SampleSchedule schedule, String alg,
            boolean timeDomain, boolean adjustThreshold, boolean allValues) throws ProcessingException {
        if (threshold <= 0.0 || threshold >= 1.0) {
            System.err.println("IST Warning: threshold " + threshold + " out of bounds, reset to 0.9");
            threshold = 0.9;
        }
        this.threshold = threshold;
        this.allValues = allValues;
        if (loops < 1) {
            System.out.println("IST Warning: number of iterations " + loops + " cannot be less than 1, reset to 1");
            loops = 1;
        }
        this.loops = loops;
        this.sampleSchedule = schedule;
        this.alg = alg;
        this.timeDomain = timeDomain;
        this.adjustThreshold = adjustThreshold;
    }

    public boolean isTimeDomain() {
        return timeDomain;
    }

    public void setSchedule(SampleSchedule schedule) {
        this.sampleSchedule = schedule;
    }

    public void calculate(Complex[] input) throws ProcessingException {
        if (sampleSchedule == null) {
            throw new ProcessingException("IST:no sample schedule");
        }
        if (alg.startsWith("std")) {
            calculateWithHFT(input);
        } else {
            calculateComplex(input);
        }
    }

    public void calculateComplex(Complex[] input) {
        int len = input.length;
        Complex[] orig = null;
        if (alg.startsWith("phase") || sampleSchedule.isDemo()) {
            zeroSample(input);  // if "phase" FT-PHASE-IFT already performed
        }
        if (timeDomain) {
            orig = new Complex[len];
            Vec.complexCopy(input, orig);
        }
        // fixme is this necessary and does it work
        //Vec.arrayCheckPowerOfTwo(input);

        Complex[] add = new Complex[len];
        for (int i = 0; i < len; i++) {
            add[i] = Complex.ZERO;
        }
        for (int loop = 0; loop < loops; loop++) {
            Vec.apache_fft(input);
            cutAboveThreshold(input, add, loop);
            if (loop < loops - 1) {
                Vec.apache_ift(input);
                zeroSample(input);  // rezero initial schedule
            }
        }

        if (timeDomain) {
            Vec.apache_ift(add);
            copyValues(orig, add);  // copy orig non-zero values
        }
        Vec.complexCopy(add, input);
    }

    public void calculateWithHFT(Complex[] input) {
        int len = input.length;
        zeroSample(input); // might have done phase or could be demo
        Complex[] orig = new Complex[len];
        Vec.complexCopy(input, orig);

        double[] add = new double[len];
        double[] realResidual = new double[len];
        for (int loop = 0; loop < loops; loop++) {
            Vec.apache_fft(input);
            for (int i = 0; i < len; i++) {
                realResidual[i] = input[i].getReal();
            }
            cutAboveThreshold(realResidual, add, loop);
            if (loop < loops - 1) {
                Complex[] cutFID = VecUtil.hift(realResidual, realResidual.length, 0.5f);
                Vec.complexCopy(cutFID, input);
                zeroSample(input);  // rezero initial schedule
            }
        }

        if (timeDomain) {
            Complex[] newFID = VecUtil.hift(add, realResidual.length, 0.5);
            Vec.complexCopy(newFID, input);
            if (scaleValues) {
                double scale = scale(orig, input, len / 2);
            }
            if (!allValues) {
                copyValues(orig, input);  // copy orig non-zero values
            }
        } else {
            Complex[] complexAdd = VecUtil.hft(add, add.length);
            Vec.complexCopy(complexAdd, input);
        }
    }

    private double scale(Complex[] origFid, Complex[] newFid, int n) {
        double sum = 0.0;
        int[][] samples = sampleSchedule.getSamples();
        for (int i = 0; i < samples.length; i++) {
            int j = samples[i][0];
            sum += newFid[j].abs() / origFid[j].abs();
        }
        double scale = sum / samples.length;
        for (int i = 0; i < newFid.length; i++) {
            newFid[i] = newFid[i].divide(scale);
        }

        return scale;
    }

    /**
     * Calculate inverse list of SampleSchedule. For 2D only.
     *
     * @param vsize
     * @see Ist#zero_samples
     * @see SampleSchedule
     * @see SampleSchedule#getSamples
     * @see SampleSchedule#v_samples
     */
    private void calcZeroes(int vsize) {
        if (zero_samples == null) {
            int[][] samples = sampleSchedule.getSamples();
            //zero_samples = new int[vsize / 2 - samples.length];
            zero_samples = new int[vsize - samples.length];
            //for (i = 0, k = 0; i < vsize / 2; i++) {
            for (int i = 0, k = 0; i < vsize; i++) {
                boolean found = false;
                for (int j = 0; j < samples.length; j++) {
                    if (i == samples[j][0]) {  // 2D index only
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    zero_samples[k++] = i;
                }
            }
        }
    }

    /**
     * Zero (or rezero) a vector with inverse samples.
     *
     * @param input
     * @see Vec
     * @see #calcZeroes
     */
    private void zeroSample(Complex[] input) {
        if (sampleSchedule != null) {
            calcZeroes(input.length);
            int k;
            for (k = 0; k < zero_samples.length; k++) {
                input[zero_samples[k]] = Complex.ZERO;
            }
        }
    }

    /**
     * Copy original non-zero values into add buffer.
     *
     * @param source
     * @param target
     */
    private void copyValues(Complex[] source, Complex[] target) {
        if (sampleSchedule != null) {
            int[][] samples = sampleSchedule.getSamples();
            for (int j = 0; j < samples.length; j++) {
                int k = samples[j][0];
                target[k] = source[k];
            }
        }
    }

    private double getThreshold(int nIterations) {
        double value = threshold;
        int nInitial = 50;
        double finalThreshold = 0.5;
        if (adjustThreshold) {
            if (nIterations > nInitial) {
                double fraction = (1.0 * loops - nIterations) / (loops - nInitial);
                value = fraction * (threshold - finalThreshold) + finalThreshold;
            }
        }
        return value;
    }

    /**
     * Perform cutoff algorithm. In general, a threshold is determined for an
     * <i>input</i> vector. Points above the threshold are summed into the
     * <i>add</i> vector, with the remainder of <i>input</i> set equal to the threshold. The method chooses between
     * different algorithms using the
     * <i>alg</i> parameter.
     *
     * @param input input buffer
     * @param addbuf add buffer
     * @see #alg
     */
    private void cutAboveThreshold(Complex[] input, Complex[] add, int nIterations) {
        if (alg.equals("phased")) {
            cutAboveComplexPhasedThreshold(input, add, nIterations);
        } else if (alg.equals("phasedpos")) {
            cutAboveComplexPhasedPosThreshold(input, add, nIterations);
        } else // if (alg.equals("abs"))
        {
            cutAboveComplexAbsThreshold(input, add, nIterations);
        }
    }

    /**
     * Get maximum of absolute value of a vector.
     *
     * @param input
     * @return position of maximum in input vector
     * @see Vec
     */
    private int getAbsMax(double[] input) {
        int pos = -1;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < input.length; i++) {
            double val = input[i];
            double aval = FastMath.abs(val);
            if (aval > max) {
                max = aval;
                pos = i;
            }
        }
        return pos;
    }

    /**
     * Get maximum of absolute value of a vector.
     *
     * @param input
     * @return position of maximum in input vector
     * @see Vec
     */
    private int getAbsMax(Complex[] input) {
        int i, pos = -1;
        double val, max = Double.NEGATIVE_INFINITY;
        for (i = 0; i < input.length; i++) {
            val = input[i].abs();
            if (val > max) {
                max = val;
                pos = i;
            }
        }
        return pos;
    }

    /**
     * Get maximum positive or negative value of a Complex array.
     *
     * @param input
     * @return position of maximum in array
     */
    private int getPhMax(Complex[] input) {
        int i, pos = -1;
        double val, max = Double.NEGATIVE_INFINITY;
        for (i = 0; i < input.length; i++) {
            val = Math.abs((input[i]).getReal());
            if (val > max) {
                max = val;
                pos = i;
            }
        }
        return pos;
    }

    /**
     * Get maximum positive value of a Complex array.
     *
     * @param vec
     * @return position of maximum in array
     */
    private int getPhPosMax(Complex[] vec) {
        int i, pos = -1;
        double val, max = Double.NEGATIVE_INFINITY;
        for (i = 0; i < vec.length; i++) {
            val = vec[i].getReal();
            if (val > max) {
                max = val;
                pos = i;
            }
        }
        return pos;
    }

    /**
     * Perform cutoff algorithm, comparing absolute values of a Complex array.
     *
     * @param input input vector
     * @param add add buffer
     * @see #getAbsMax
     * @see Complex
     */
    private void cutAboveComplexAbsThreshold(Complex[] input, Complex[] add, int nIterations) {
        double dx, dy;
        Complex v, pt;
        int maxpos = getAbsMax(input);
        Complex cutoff = input[maxpos];
        double currentThreshold = getThreshold(nIterations);
        double th = currentThreshold * cutoff.abs();
        cutoff = new Complex(
                currentThreshold * cutoff.getReal(),
                currentThreshold * cutoff.getImaginary()
        );
        for (int i = 0; i < input.length; i++) {
            pt = input[i];
            if (pt.abs() > th) {
                v = add[i];
                dx = pt.getReal() - cutoff.getReal();
                dy = pt.getImaginary() - cutoff.getImaginary();
                add[i] = new Complex(
                        dx + v.getReal(),
                        dy + v.getImaginary()
                );
                input[i] = new Complex(
                        cutoff.getReal(),
                        cutoff.getImaginary()
                );
            }
        }
    }

    /**
     * Perform cutoff algorithm, comparing positive and negative real values of a Complex phased array.
     *
     * @param input input buffer
     * @param add add buffer
     * @see #getPhMax
     * @see Complex
     */
    private void cutAboveComplexPhasedThreshold(Complex[] input, Complex[] add, int nIterations) {
        double dx, dy;
        Complex v, pt;
        int maxpos = getPhMax(input);
        Complex cutoff = input[maxpos];
        double currentThreshold = getThreshold(nIterations);
        double th = Math.abs(currentThreshold * cutoff.getReal()); // compare real
        cutoff = new Complex( // should work if real is big, imag is small
                currentThreshold * cutoff.getReal(),
                currentThreshold * cutoff.getImaginary()
        );
        for (int i = 0; i < input.length; i++) {
            pt = input[i];
            if (pt.getReal() > th) {  // compare real pos
                v = add[i];
                dx = pt.getReal() - cutoff.getReal();
                dy = pt.getImaginary() - cutoff.getImaginary();
                add[i] = new Complex(
                        dx + v.getReal(),
                        dy + v.getImaginary()
                );
                input[i] = new Complex(
                        cutoff.getReal(),
                        cutoff.getImaginary()
                );
            } else if (pt.getReal() < -th) {  // compare real neg
                v = add[i];
                dx = pt.getReal() + cutoff.getReal();
                dy = pt.getImaginary() + cutoff.getImaginary();
                add[i] = new Complex(
                        dx + v.getReal(),
                        dy + v.getImaginary()
                );
                input[i] = new Complex(
                        -cutoff.getReal(),
                        -cutoff.getImaginary()
                );
            }
        }
    }

    /**
     * Perform cutoff algorithm, comparing positive real values of a phased Complex array.
     *
     * @param input input buffer
     * @param add add buffer
     * @see #getPhPosMax
     * @see Complex
     */
    private void cutAboveComplexPhasedPosThreshold(Complex[] input, Complex[] add, int nIterations) {
        double dx, dy;
        Complex v, pt;
        int maxpos = getPhPosMax(input);
        Complex cutoff = input[maxpos];
        double th = getThreshold(nIterations) * cutoff.getReal(); // compare real
        cutoff = new Complex( // should work if real is big, imag is small
                getThreshold(nIterations) * cutoff.getReal(),
                getThreshold(nIterations) * cutoff.getImaginary()
        );
        for (int i = 0; i < input.length; i++) {
            pt = input[i];
            if (pt.getReal() > th) {  // compare real
                v = add[i];
                dx = pt.getReal() - cutoff.getReal();
                dy = pt.getImaginary() - cutoff.getImaginary();
                add[i] = new Complex(
                        dx + v.getReal(),
                        dy + v.getImaginary()
                );
                input[i] = new Complex(
                        cutoff.getReal(),
                        cutoff.getImaginary()
                );
            }
        }
    }

    /**
     * Perform cutoff algorithm, comparing absolute values of a double array.
     *
     * @param input input vector
     * @param add add buffer
     */
    private void cutAboveThreshold(double[] input, double[] add, int nIterations) {
        int maxpos = getAbsMax(input);
        double cutoff = FastMath.abs(input[maxpos]);
        double th = getThreshold(nIterations) * cutoff;
        if (nIterations == (loops - 1)) {
            // fixme should th go to 0.0 at end?  th = 0.0;
        }
        for (int i = 0; i < input.length; i++) {
            double value = input[i];
            double avalue = FastMath.abs(value);
            if (avalue > th) {
                if (value > 0.0) {
                    add[i] += (avalue - th);
                    input[i] = th;
                } else {
                    add[i] -= (avalue - th);
                    input[i] = -th;
                }
            }
        }
    }

}
