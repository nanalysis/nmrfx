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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.math;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.UncorrelatedRandomVectorGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.ResizableDoubleArray;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.math.VecBase;
import org.nmrfx.math.VecException;
import org.nmrfx.processor.datasets.peaks.LineShapes;
import org.nmrfx.processor.operations.TestBasePoints;
import org.nmrfx.processor.operations.Util;
import org.nmrfx.processor.processing.SampleSchedule;
import org.python.core.Py;
import org.python.core.PyComplex;
import org.python.core.PyObject;
import org.python.core.PyType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.nmrfx.processor.math.VecUtil.nnlsFit;

/**
 * A class for representing vectors of data (typically for NMR). The data is
 * stored as real or complex values. If complex, the data can be stored in two
 * formats. In the first, the real values are stored in one array of doubles and
 * the imaginary in a second array of doubles. In the second format the complex
 * values are stored in an array of Complex objects. The storage arrays can be
 * resized and may have a capacity larger than the number of valid data values
 * they contain. Because of this the user must always pay attention to the size
 * field which indicates the number of valid points.
 * <p>
 * The class extends the Jython PySequence class which allows it to be used in
 * basic Python operations (addition, subtraction etc.).
 *
 * @author michael
 */
@PythonAPI({"autoscript", "dscript", "pyproc", "simfid"})
@PluginAPI("parametric")
public class Vec extends VecBase {

    public static final PyType VTYPE = PyType.fromClass(Vec.class);
    public static final String TYPE_NAME = "nmrfxvector";

    private static final GaussianRandomGenerator randGen = new GaussianRandomGenerator(new SynchronizedRandomGenerator(new Well19937c()));

    private double[] annotationData = null;

    /**
     * Sample schedule that applies to this vector when NUS data acquisition was
     * used.
     */
    public SampleSchedule schedule = null;

    public Vec(int size) {
        super(size, VTYPE);
    }

    public Vec(int size, boolean complex) {
        super(size, complex, VTYPE);
    }

    public Vec(int size, String name, boolean complex) {
        super(size, name, complex, VTYPE);
    }

    public Vec(int size, int[][] pt, int[] dim, boolean complex) {
        super(size, pt, dim, complex);
    }

    /**
     * Create a real vector.
     *
     * @param real The real data to store in the vector.
     */
    public Vec(double[] real) {
        super(real);
    }

    /**
     * Create a complex vector that stores real and imaginary in separate arrays.
     *
     * @param real
     * @param imaginary
     */
    public Vec(double[] real, double[] imaginary) {
        super(real, imaginary);
    }

    /**
     * Create a vector with the specified name, size and complex mode and store
     * it in a Map of Vec objects.
     *
     * @param size    Size of vector.
     * @param name    name of vector
     * @param complex true if vector stores complex data
     * @return new Vec object
     */
    public static final Vec createNamedVector(int size, String name, boolean complex) {
        var vec = new Vec(size, name, complex);
        put(name, vec);
        return vec;
    }

    public Vec copyVec() {
        Vec newVec = new Vec(size, isComplex);
        copy(newVec);
        return newVec;
    }

    @Override
    public int __len__() {
        return size;
    }

    @Override
    public Vec __radd__(PyObject pyO) {
        return __add__(pyO);
    }

    /**
     * Convert PyComplex value to Apache Commons Math Complex value
     *
     * @param pyC the value as PyComplex object
     * @return the value as Commons Math Complex value
     */
    public Complex toComplex(PyComplex pyC) {
        return new Complex(pyC.real, pyC.imag);
    }

    @Override
    public Vec __add__(PyObject pyO) {
        Vec vecNew = new Vec(this.getSize(), this.isComplex);
        this.copy(vecNew);
        if (pyO instanceof Vec) {
            //  fixme check sizes
            Vec vec = (Vec) pyO;
            vecNew.add(vec);
        } else if (pyO instanceof PyComplex) {
            vecNew.add(toComplex((PyComplex) pyO));
        } else if (pyO.isNumberType()) {
            vecNew.add(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '+' to object: " + pyO.getType().asString());
        }
        return vecNew;
    }

    @Override
    public Vec __iadd__(PyObject pyO) {
        if (pyO instanceof Vec) {
            //  fixme check sizes
            Vec vec = (Vec) pyO;
            this.add(vec);
        } else if (pyO instanceof PyComplex) {
            this.add(toComplex((PyComplex) pyO));
        } else if (pyO.isNumberType()) {
            this.add(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '+=' to object: " + pyO.getType().asString());
        }
        return this;
    }

    @Override
    public Vec __rsub__(PyObject pyO) {
        if (pyO instanceof Vec) {
            return ((Vec) pyO).__sub__(this);
        } else {
            Vec vecNew = new Vec(this.getSize(), this.isComplex);
            this.copy(vecNew);
            if (pyO instanceof PyComplex) {
                vecNew.scale(-1.0);
                vecNew.add(toComplex((PyComplex) pyO));
            } else if (pyO.isNumberType()) {
                vecNew.scale(-1.0);
                vecNew.add(pyO.asDouble());
            } else {
                throw Py.TypeError("can't apply '-' to object: " + pyO.getType().asString());
            }
            return vecNew;
        }
    }

    @Override
    public Vec __sub__(PyObject pyO) {
        Vec vecNew = new Vec(this.getSize(), this.isComplex);
        this.copy(vecNew);
        if (pyO instanceof Vec) {
            //  fixme check sizes
            Vec vec = (Vec) pyO;
            vecNew.sub(vec);
        } else if (pyO instanceof PyComplex) {
            PyComplex pyC = (PyComplex) pyO;
            Complex addValue = new Complex(pyC.real, pyC.imag);
            vecNew.sub(addValue);
        } else if (pyO.isNumberType()) {
            vecNew.sub(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '-' to object: " + pyO.getType().asString());
        }
        return vecNew;
    }

    @Override
    public Vec __isub__(PyObject pyO) {
        if (pyO instanceof Vec) {
            //  fixme check sizes
            Vec vec = (Vec) pyO;
            this.sub(vec);
        } else if (pyO instanceof PyComplex) {
            PyComplex pyC = (PyComplex) pyO;
            Complex addValue = new Complex(pyC.real, pyC.imag);
            this.sub(addValue);
        } else if (pyO.isNumberType()) {
            this.sub(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '-=' to object: " + pyO.getType().asString());
        }
        return this;
    }

    @Override
    public Vec __rmul__(PyObject pyO) {
        return __mul__(pyO);
    }

    @Override
    public Vec __mul__(PyObject pyO) {
        Vec vecNew = new Vec(this.getSize(), this.isComplex);
        this.copy(vecNew);

        if (pyO instanceof Vec) {
            //  fixme check sizes
            Vec vec = (Vec) pyO;
            vecNew.multiply(vec);
        } else if (pyO instanceof PyComplex) {
            if (!vecNew.isComplex) {
                vecNew.makeApache();
            }
            vecNew.multiply(toComplex((PyComplex) pyO));
        } else if (pyO.isNumberType()) {
            vecNew.scale(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '*' to object: " + pyO.getType().asString());
        }
        return vecNew;
    }

    @Override
    public Vec __imul__(PyObject pyO) {

        if (pyO instanceof Vec) {
            //  fixme check sizes
            Vec vec = (Vec) pyO;
            this.multiply(vec);
        } else if (pyO instanceof PyComplex) {
            if (!this.isComplex) {
                this.makeApache();
            }
            this.multiply(toComplex((PyComplex) pyO));
        } else if (pyO.isNumberType()) {
            this.scale(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '*' to object: " + pyO.getType().asString());
        }
        return this;
    }

    @Override
    public Vec __rdiv__(PyObject pyO) {
        if (pyO instanceof Vec) {
            return ((Vec) pyO).__div__(this);
        } else {
            Vec vecNew = new Vec(this.getSize(), this.isComplex);
            this.copy(vecNew);
            if (pyO instanceof PyComplex) {
                vecNew.rdivide(toComplex((PyComplex) pyO));
            } else if (pyO.isNumberType()) {
                vecNew.rdivide(pyO.asDouble());
            } else {
                throw Py.TypeError("can't apply '/' to object: " + pyO.getType().asString());
            }
            return vecNew;
        }
    }

    @Override
    public Vec __div__(PyObject pyO) {
        Vec vecNew = new Vec(this.getSize(), this.isComplex);
        this.copy(vecNew);
        if (pyO instanceof Vec) {
            //  fixme check sizes
            Vec vec = (Vec) pyO;
            vecNew.divide(vec);
        } else if (pyO instanceof PyComplex) {
            PyComplex pyC = (PyComplex) pyO;
            Complex addValue = new Complex(pyC.real, pyC.imag);
            vecNew.divide(addValue);
        } else if (pyO.isNumberType()) {
            vecNew.divide(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '/' to object: " + pyO.getType().asString());
        }
        return vecNew;
    }

    @Override
    public Vec __idiv__(PyObject pyO) {
        if (pyO instanceof Vec) {
            //  fixme check sizes
            Vec vec = (Vec) pyO;
            this.divide(vec);
        } else if (pyO instanceof PyComplex) {
            PyComplex pyC = (PyComplex) pyO;
            Complex addValue = new Complex(pyC.real, pyC.imag);
            this.divide(addValue);
        } else if (pyO.isNumberType()) {
            this.divide(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '/' to object: " + pyO.getType().asString());
        }
        return this;
    }

    /**
     * Get array of boolean values indicating whether each point is signal or
     * baseline. True values indicate that the corresponding point is signal.
     *
     * @return boolean array with true values for signals.
     */
    public boolean[] getSignalRegion() {
        return inSignalRegion;
    }

    /**
     * Set the signal regions. Signal regions are typically used by the baseline
     * correction algorithms.
     *
     * @param region boolean array where true values indicate that point is
     *               signal.
     */
    public void setSignalRegion(boolean[] region) {
        inSignalRegion = region;
    }

    /**
     * Get array of double values that can be used for drawing a line. Often
     * used for displaying apodization.
     *
     * @return double array with values.
     */
    public double[] getAnnotation() {
        return annotationData;
    }

    /**
     * Set the annotation values. Values are typically used for display of
     * apodization.
     *
     * @param data double array.
     */
    public void setAnnotation(double[] data) {
        if ((annotationData == null) || (annotationData.length != data.length)) {
            annotationData = new double[data.length];
        }
        System.arraycopy(data, 0, annotationData, 0, data.length);
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < annotationData.length; i++) {
            max = Math.max(max, annotationData[i]);
        }
        for (int i = 0; i < annotationData.length; i++) {
            annotationData[i] /= max;
        }
    }

    public void clearAnnotation() {
        annotationData = null;
    }

    // fixme what the he?? does this do

    /**
     * Get start of "valid" data in vectors that have DSP "charge-up" at
     * beginning. This value is calculated based on the vectors stored
     * groupDelay parameter.
     *
     * @return first point of "valid" data
     */
    public int getStart() {
        int start = (int) (groupDelay + 0.5);
        if (start < 0) {
            start = 0;
        }
        return start;
    }

    /**
     * Get the group delay value resulting from DSP processing
     *
     * @return the group delay
     */
    public double getGroupDelay() {
        return groupDelay;
    }

    /**
     * Set the group delay value resulting from DSP processing
     *
     * @param groupDelay the group delay value
     */
    public void setGroupDelay(double groupDelay) {
        this.groupDelay = groupDelay;
    }

    /**
     * Fix DSP charge-up
     *
     * @param groupDelay the group delay of the DSP filter
     */
    public void fixWithShifted(double groupDelay) {
        int start = (int) (groupDelay + 0.5); // usually 67.98 or so
        double hold = ph1;
        fft();
        phase(0.0, -360.0 * groupDelay, false, false);  // oldStyle is false
        ifft();
        setGroupDelay(0.0);
        Vec stub = new Vec(start, isComplex());
        for (int i = 0; i < start; i++) {
            stub.set(i, getComplex(size - start + i));
        }
        stub.reverse();
        resize(size - start);
        add(stub);
        ph1 = hold;
    }

    /**
     * Fix DSP charge-up
     */
    public void fixGroupDelay() {
        if (groupDelay != 0.0) {
            double dspph = -groupDelay * 360.0;
            double hold = ph1;
            phase(0.0, dspph, false, false);
            ph1 = hold;
        }
    }

    /**
     * Fix DSP charge-up with a HFT
     */
    public void fixWithPhasedHFT() {
        fixWithPhasedHFT(0.0);
    }

    /**
     * Fix DSP charge-up with an HFT. FID is Fourier transformed, phased with
     * specified zero order phase and calculated (from group delay) first order
     * phase, made real, and then a Hilbert transform is done to regenerate
     * imaginary values without the effect of charge-up. Finally, the spectrum
     * is inverse transformed to return to time domain.
     *
     * @param phase apply this phase value
     */
    public void fixWithPhasedHFT(double phase) {
        if (groupDelay != 0.0) {
            int start = (int) (groupDelay + 0.5);
            Complex initPt = findBrukerInitialPoint(start);
            double p1 = initPt.getArgument() * 180 / Math.PI;
            phase += p1;
            int currentSize = size;
            checkPowerOf2();
            resize(size * 2);
            double dspph = -360.0 * groupDelay;
            groupDelay = 0.0;
            fft();
            double hold = ph1;
            phase(phase, dspph, false, false);
            makeReal();
            hft();
            phase(-phase, 0.0, false, false);
            ph1 = hold;
            ifft();
            resize(currentSize - start, true);
        }
    }

    /**
     * Fix DSP charge-up
     */
    public void fixWithBrukerFilter() {
        fixWithBrukerFilter(-1.0, 0.0);
    }

    /**
     * Fix DSP charge-up
     *
     * @param amp   amplitude of filter
     * @param phase phase of filter
     */
    public void fixWithBrukerFilter(double amp, double phase) {
        int start = (int) (groupDelay + 0.5);  // often 68
        if (start > 1) {
            if (isComplex) {
                groupDelay = 0.0;
                int factor = 4;
                int ncoefs = 2 * factor * start;   // often 544
                FirFilter filt = new FirFilter(factor, ncoefs, "lowpass");
                Vec simVec = filt.brukerSimVec(this);    // simulate bruker distortion
                if (isComplex) {    // find phase and amplitude
                    Complex initPt = findBrukerInitialPoint(start);
                    double a1 = initPt.abs() * amp / simVec.getComplex(0).abs();
                    double p1 = initPt.getArgument() + phase;
                    initPt = new Complex(a1 * Math.cos(p1), a1 * Math.sin(p1));
                    simVec.multiply(initPt);   // set phase and amplitude
                    simVec.set(0, 0.0, 0.0);   // remove 1st point dc offset
                }
                this.trim(start, size - start);  // remove precharge points
                this.add(simVec);    // subtract bruker filter distortion
            } else {
                this.trim(start, size - start);  // remove precharge points
            }
        }
    }

    /**
     * Calculate simulated filter
     */
    public void showBrukerFilterSim() {
        if (groupDelay != 0.0) {
            int initSize = size;
            int start = (int) (groupDelay + 0.5);
            int factor = 4;
            int ncoefs = 2 * factor * start;
            FirFilter filt = new FirFilter(factor, ncoefs, "lowpass");
            Vec simVec = filt.brukerSimVec(this);
            simVec.set(0, 0.5 * simVec.getReal(0) / factor, 0.0);  // set dc offset
            simVec.resize(initSize);
            simVec.copy(this);
        }
    }

    /**
     * Search vector for initial amplitude and phase of the DSP charge-up.
     *
     * @param start zero time position of Bruker fid
     * @return initial amplitude and phase
     */
    public Complex findBrukerInitialPoint(int start) {
        Complex c = Complex.I;
        if (isComplex) {
            c = getComplex(start);  // amp from 1st real point
            double ph = 0.0;
            double amp = c.abs();
// only even points in sync with 1st real point, 1st half of precharge
// average the phase for the preceding points (in charge-up)
            int n = 0;
            for (int i = start - 2; i > start / 2; i -= 2) {
                c = getComplex(i);
                if (c.abs() > 0.0) {
                    ph += c.getArgument();
                    n++;
                }
            }
            if (n > 0) {
                ph /= n;
                c = ComplexUtils.polar2Complex(amp, ph);
            } else {
                c = Complex.I;
            }
        }
        return c;
    }

    /**
     * Scale first point. Useful for artifacts from initial time. Typically 0.5
     * for indirect dimensions.
     *
     * @param fPoint multiply first point by this value
     * @return this vector
     */
    public Vec fp(double fPoint) {
        if (isComplex) {
            set(0, new Complex(getReal(0) * fPoint, getImag(0) * fPoint));
        } else {
            set(0, getReal(0) * fPoint);
        }

        return (this);
    }

    /**
     * ZeroFill a vector. Original values smaller than new size are preserved.
     *
     * @param newsize the new size of the vector
     */
    public void zf(int newsize) {
        zfSize = newsize;
        resize(newsize);
    }

    /**
     * Perform a Fast Fourier Transform (FFT) of the specified real data.
     *
     * @param ftvec an array of Complex values to be transformed
     * @return The original array with now containing the FFT
     */
    public static Complex[] apache_rfft(final double[] ftvec) {
        FastFourierTransformer ffTrans = new FastFourierTransformer(DftNormalization.STANDARD);
        return ffTrans.transform(ftvec, TransformType.FORWARD);
    }

    /**
     * Perform a Fast Fourier Transform (FFT) of the vector using the Apache
     * Commons Math library.
     *
     * @param negate if true negate imaginary values before the transform
     * @return this vector
     */
    public Vec apache_fft(final boolean negate) {
        if (isComplex()) {
            checkPowerOf2();
            Complex[] ftvec = new Complex[size];
            if (negate) {
                for (int i = 0; i < size; i++) {
                    ftvec[i] = new Complex(cvec[i].getReal(), -cvec[i].getImaginary());
                }
            } else {
                System.arraycopy(cvec, 0, ftvec, 0, size);
            }
            Complex[] ftResult = apache_fft(ftvec);
            System.arraycopy(ftResult, 0, cvec, 0, size);
            freqDomain = true;
        }

        return (this);
    }

    /**
     * Perform a Fast Fourier Transform (FFT) of the specified complex data.
     *
     * @param ftvec an array of Complex values to be transformed
     * @return The original array with now containing the FFT
     */
    public static Complex[] apache_fft(final Complex[] ftvec) {
        FastFourierTransformer ffTrans = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] ftResult = ffTrans.transform(ftvec, TransformType.FORWARD);
        final int ftSize = ftvec.length;
        final int mid = ftSize / 2;
        System.arraycopy(ftResult, 0, ftvec, mid, ftSize / 2);
        System.arraycopy(ftResult, mid, ftvec, 0, ftSize / 2);
        return ftvec;
    }

    /*
     * Perform a inverse Fast Fourier Transform (FFT) of the vector using the Apache Commons Math library.
     *
     * @return this vector
     */
    public Vec apache_ift() {
        if (isComplex()) {
            checkPowerOf2();
            Complex[] ftvec = new Complex[size];
            System.arraycopy(cvec, 0, ftvec, 0, size);

            Complex[] ftResult = apache_ift(ftvec);
            System.arraycopy(ftResult, 0, cvec, 0, size);
            freqDomain = true;
        }
        return this;
    }

    /**
     * Perform an inverse Fast Fourier Transform (FFT) of the specified complex
     * data.
     *
     * @param ftIn an array of Complex values to be transformed
     * @return The original array with now containing the FFT
     */
    public static Complex[] apache_ift(final Complex[] ftIn) {
        final int ftSize = ftIn.length;
        Complex[] ftvec = new Complex[ftSize];
        int mid = ftSize / 2;
        System.arraycopy(ftIn, 0, ftvec, mid, ftSize / 2);
        System.arraycopy(ftIn, mid, ftvec, 0, ftSize / 2);
        FastFourierTransformer ffTrans = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] ftResult = ffTrans.transform(ftvec, TransformType.INVERSE);
        System.arraycopy(ftResult, 0, ftIn, 0, ftSize);
        return ftIn;
    }

    /**
     * Generate damped sinusoidal signal, and add to Vec instance.
     *
     * @param freq frequency in Hz
     * @param lw   Linewidth in Hz
     * @param amp  amplitude
     * @param ph   phase in degrees
     */
    public void genSignalHz(double freq, double lw, double amp, double ph) {
        double f = freq / (1.0 / dwellTime);
        double d = Math.exp(-Math.PI * lw * dwellTime);
        Complex w = ComplexUtils.polar2Complex(d, f * Math.PI * 2.0);
        Complex tempC = new Complex(amp * Math.cos(ph * Math.PI / 180.0), amp * Math.sin(ph * Math.PI / 180.0));
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; i++) {
                    cvec[i] = cvec[i].add(tempC);
                    tempC = tempC.multiply(w);
                }
            } else {
                for (int i = 0; i < size; i++) {
                    rvec[i] += tempC.getReal();
                    ivec[i] += tempC.getImaginary();
                    tempC = tempC.multiply(w);
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                rvec[i] += tempC.getReal();
                tempC = tempC.multiply(w);
            }
        }
    }

    /**
     * Generate damped sinusoidal signal, and add to Vec instance.
     *
     * @param freq  frequency in degrees per point
     * @param decay exponential decay per point
     * @param amp   amplitude
     * @param ph    phase in degrees
     */
    public void genSignal(double freq, double decay, double amp, double ph) {
        Complex w = ComplexUtils.polar2Complex(decay, freq * Math.PI / 180.0);
        Complex tempC = new Complex(amp * Math.cos(ph * Math.PI / 180.0), amp * Math.sin(ph * Math.PI / 180.0));
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; i++) {
                    cvec[i] = cvec[i].add(tempC);
                    tempC = tempC.multiply(w);
                }
            } else {
                for (int i = 0; i < size; i++) {
                    rvec[i] += tempC.getReal();
                    ivec[i] += tempC.getImaginary();
                    tempC = tempC.multiply(w);
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                rvec[i] += tempC.getReal();
                tempC = tempC.multiply(w);
            }
        }
    }

    /**
     * Add Lorentzian line shapes to this vector using parameters stored in
     * another Vec object.
     *
     * @param par vector of parameters
     * @return this vec
     */
    @Deprecated
    public Vec genSpec(Vec par) {
        int i;
        int j;
        int k;
        double amp;
        double phase;

        int halfWidth;
        int center;
        resize(size, false);

        for (j = 3; j < par.size; j += 4) {
            amp = par.rvec[j];
            phase = par.rvec[j + 1];
            Complex cAmp = new Complex(Math.cos(phase) * amp, Math.sin(phase) * amp);
            Complex cFreq = new Complex(-par.rvec[j + 2], -par.rvec[j + 3]);
            center = (int) Math.round((size * (cFreq.getReal() + Math.PI)) / (2.0 * Math.PI));
            halfWidth = (int) Math.round(4 * Math.abs(
                    cFreq.getImaginary() * 2.0 * Math.PI * size * 2));

            for (i = -halfWidth; i <= halfWidth; i++) {
                k = center + i;

                if (k < 0) {
                    continue;
                }

                if (k >= size) {
                    continue;
                }

                double f1Real = (((1.0 * k) / size) * 2.0 * Math.PI) - Math.PI;
                double f1Imaginary = 0.0;
                Complex f1 = new Complex(f1Real, f1Imaginary);
                Complex sig = cAmp.divide(f1.subtract(cFreq));

                rvec[k] += -sig.getImaginary();
            }
        }

        freqDomain = true;

        return (this);
    }

    /**
     * Generate random noise vector, and add to Vec instance.
     *
     * @param level noise amplitude multiplier
     * @see UncorrelatedRandomVectorGenerator
     * @see GaussianRandomGenerator
     * @see Well19937c
     */
    public void genNoise(double level) {
        int i;
        if (isComplex) {
            if (useApache) {
                for (i = 0; i < size; i++) {
                    double reRand = randGen.nextNormalizedDouble();
                    double imRand = randGen.nextNormalizedDouble();
                    Complex cpx = new Complex(reRand * level, imRand * level);
                    cvec[i] = cvec[i].add(cpx);
                }
            } else {
                for (i = 0; i < size; i++) {
                    double reRand = randGen.nextNormalizedDouble();
                    double imRand = randGen.nextNormalizedDouble();
                    rvec[i] += reRand * level;
                    ivec[i] += imRand * level;
                }
            }
        } else {
            for (i = 0; i < size; i++) {
                double reRand = randGen.nextNormalizedDouble();
                rvec[i] += reRand * level;
            }
        }
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in
     * TPPI data collection.
     *
     * @param rvec vector of doubles to process
     */
    public static void negatePairs(double[] rvec) {
        negatePairs(rvec, rvec.length);
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in
     * TPPI data collection.
     *
     * @param rvec    real values
     * @param vecSize size of vector
     */
    public static void negatePairs(double[] rvec, int vecSize) {
        for (int i = 3; i < vecSize; i += 4) {
            rvec[i - 1] = -rvec[i - 1];
            rvec[i] = -rvec[i];
        }
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in
     * TPPI data collection.
     *
     * @param rvec real values
     * @param ivec imaginary values
     */
    public static void negatePairs(double[] rvec, double[] ivec) {
        negatePairs(rvec, ivec, rvec.length);
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in
     * TPPI data collection.
     *
     * @param rvec    real values
     * @param ivec    imaginary values
     * @param vecSize size of vector
     */
    public static void negatePairs(double[] rvec, double[] ivec, int vecSize) {
        for (int i = 1; i < vecSize; i += 2) {
            rvec[i] = -rvec[i];
            ivec[i] = -ivec[i];
        }
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in
     * TPPI data collection.
     *
     * @param cvec complex values
     */
    public static void negatePairs(Complex[] cvec) {
        negatePairs(cvec, cvec.length);
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in
     * TPPI data collection.
     *
     * @param cvec    complex values
     * @param vecSize size of vector
     */
    public static void negatePairs(Complex[] cvec, int vecSize) {
        for (int i = 1; i < vecSize; i += 2) {
            cvec[i] = new Complex(-cvec[i].getReal(), -cvec[i].getImaginary());
        }
    }

    /**
     * Negate every other pair of points. The effect is to shift a spectrum by
     * sw/2, moving the center frequency to the edge of the spectrum. Used for
     * States-TPPI processing.
     */
    public void negatePairs() {
        if (!isComplex) {
            negatePairs(rvec, size);
        } else if (useApache) {
            negatePairs(cvec, size);
        } else {
            negatePairs(rvec, ivec, size);
        }
    }

    /**
     * Negate the imaginary values of this vector. The effect is to reverse the
     * spectrum resulting from FFT. Negate imaginary values.
     */
    public void negateImaginary() {
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; ++i) {
                    cvec[i] = new Complex(getReal(i), -getImag(i));
                }
            } else {
                for (int i = 0; i < size; ++i) {
                    ivec[i] = -ivec[i];
                }
            }
        } else {
            for (int i = 1; i < size; i += 2) {
                rvec[i] = -rvec[i];
            }
        }
    }

    /**
     * Negate real values.
     */
    public void negateReal() {
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; ++i) {
                    cvec[i] = new Complex(-getReal(i), getImag(i));
                }
            } else {
                for (int i = 0; i < size; ++i) {
                    rvec[i] = -rvec[i];
                }
            }
        } else {
            for (int i = 0; i < size; ++i) {
                rvec[i] = -rvec[i];
            }
        }
    }

    /**
     * Negate real and imaginary (if vector complex) values.
     */
    public void negateAll() {
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; ++i) {
                    cvec[i] = new Complex(-getReal(i), -getImag(i));
                }
            } else {
                for (int i = 0; i < size; ++i) {
                    rvec[i] = -rvec[i];
                    ivec[i] = -ivec[i];
                }
            }
        } else {
            for (int i = 0; i < size; ++i) {
                rvec[i] = -rvec[i];
            }
        }
    }

    //print a string of the value(s) at index i

    /**
     * Return String representation of value at specified index
     *
     * @param index position of value
     * @return String representation
     */
    public String getString(int index) {
        if (isComplex && useApache) {
            return cvec[index].getReal() + " " + cvec[index].getImaginary();
        } else if (isComplex) {
            return rvec[index] + " " + ivec[index];
        } else {
            return Double.toString(rvec[index]);
        }
    }

    /**
     * Perform Fast Fourier Transform (FFT) of this vector.
     */
    public void fft() {
        fft(false, false, false);
    }

    /**
     * Perform Fast Fourier Transform (FFT) of this vector with various options.
     *
     * @param negatePairs     negate alternate real/imaginary pairs
     * @param negateImaginary negate imaginary pairs
     * @param fixGroupDelay   modify vector to remove DSP charge-up at front of
     *                        vector
     */
    public void fft(boolean negatePairs, boolean negateImaginary, boolean fixGroupDelay) {
        if (isComplex()) {
            if (!useApache()) {
                makeApache();
            }
            if (negatePairs) {
                negatePairs();
            }
            checkPowerOf2();
            Complex[] ftvector = new Complex[getSize()];
            if (negateImaginary) {
                for (int i = 0; i < getSize(); i++) {
                    ftvector[i] = new Complex(cvec[i].getReal(), -cvec[i].getImaginary());
                }
            } else {
                System.arraycopy(cvec, 0, ftvector, 0, getSize());
            }
            Complex[] ftResult = apache_fft(ftvector);
            System.arraycopy(ftResult, 0, cvec, 0, getSize());
            setFreqDomain(true);
            if (fixGroupDelay) {
                fixGroupDelay();
            }
        }
    }

    /**
     * Perform inverse Fast Fourier Transform (FFT) of this vector.
     */
    public void ifft() {
        ifft(false, false);
    }

    /**
     * Perform inverse Fast Fourier Transform (FFT) of this vector with various
     * options.
     *
     * @param negatePairs     negate alternate real/imaginary pairs
     * @param negateImaginary negate imaginary pairs
     */
    public void ifft(boolean negatePairs, boolean negateImaginary) {
        if (isComplex()) {
            if (!useApache()) {
                makeApache();
            }
            checkPowerOf2();
            Complex[] ftvector = new Complex[getSize()];
            System.arraycopy(cvec, 0, ftvector, 0, getSize());
            Complex[] ftResult = apache_ift(ftvector);

            if (negateImaginary) {
                for (int i = 0; i < getSize(); i++) {
                    cvec[i] = new Complex(ftResult[i].getReal(), -ftResult[i].getImaginary());
                }
            } else {
                System.arraycopy(ftResult, 0, cvec, 0, getSize());
            }

            setFreqDomain(false);
            setGroupDelay(0.0);

            if (negatePairs) {
                negatePairs();
            }
        }
    }

    /**
     *
     */
    public void ft() {
        if (isComplex) {
            if (!useApache) {
                makeApache();
            }
            Cfft.cfft(cvec, size, 0);
            freqDomain = true;
        }
    }

    /**
     * Perform Real Fast Fourier Transform (FFT) of this vector.
     */
    public void rft(boolean inverse) {
        rft(inverse, false, false);
    }


    /**
     * Real FT.
     * <p>
     * Vec must be real. If a Vec is using cvec it will do a RFT of the real
     * part and copy back to cvec, if a Vec is using rvec it will copy RFT back
     * to rvec and ivec.
     *
     * @param inverse     If true do the inverse FFT.
     * @param negatePairs negate alternate real/imaginary pairs
     * @param negateOdd   negate alternate values
     */
    public void rft(boolean inverse, boolean negatePairs, boolean negateOdd) {
        if (!isComplex) {
            checkPowerOf2();
            double[] ftvec = new double[size];
            if (negatePairs) {
                negatePairs();
            }
            if (negateOdd) {
                negateImaginary();
            }
            System.arraycopy(rvec, 0, ftvec, 0, size);

            Complex[] ftResult = apache_rfft(ftvec);

            makeComplex();

            int newSize = ftResult.length / 2;
            resize(newSize, true);
            if (useApache) {
                System.arraycopy(ftResult, 0, cvec, 0, newSize);
            } else {
                for (int i = 0; i < size; ++i) {
                    rvec[i] = ftResult[i].getReal();
                    ivec[i] = ftResult[i].getImaginary();
                }
            }
            freqDomain = true;
        }
    }

    /**
     * Hilbert transform of this vector. Converts real vector into complex. No
     * effect if vector is already complex
     *
     * @return this vector
     */
    public Vec hft() {
        if (isComplex) {
            return (this);
        }


        /* old method
         interp();
         complex();
         ift();
         resize(size / 2);
         for (int i = 1; i < size; i++) {
         cvec[i].re *= 2.0;
         cvec[i].im *= 2.0;
         }
         ft();
         */
        int origSize = size;
        int factor = 0;
        int newSize = (int) Math.round(Math.pow(2,
                Math.ceil((Math.log(size) / Math.log(2)) + factor)));
        resize(newSize);

        scale(2.0);
        makeComplex();
        makeApache();

        ifft();
        set(0, new Complex(getReal(0) / 2, getImag(0)));

        int osize2 = size / 2;

        for (int i = osize2; i < size; i++) {
            set(i, Complex.ZERO);
        }

        fft();
        resize(origSize);

        return (this);
    }

    /**
     * Take absolute value of values in vector.
     */
    public void abs() {
        if (isComplex) {
            if (useApache) {
                makeReal();
                for (int i = 0; i < size; ++i) {
                    rvec[i] = cvec[i].abs();
                }
            } else {
                makeReal();
                for (int i = 0; i < size; i++) {
                    double real = rvec[i];
                    double imaginary = ivec[i];
                    double result;
                    if (FastMath.abs(real) < FastMath.abs(imaginary)) {
                        if (imaginary == 0.0) {
                            result = FastMath.abs(real);
                        } else {
                            double q = real / imaginary;
                            result = FastMath.abs(imaginary) * FastMath.sqrt(1 + q * q);
                        }
                    } else if (real == 0.0) {
                        result = FastMath.abs(imaginary);
                    } else {
                        double q = imaginary / real;
                        result = FastMath.abs(real) * FastMath.sqrt(1 + q * q);
                    }
                    rvec[i] = result;
                }

            }
        } else {
            for (int i = 0; i < size; ++i) {
                rvec[i] = FastMath.abs(rvec[i]);
            }

        }
    }

    /**
     * Take square root of values in vector
     */
    public void sqrt() {
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; ++i) {
                    cvec[i] = cvec[i].sqrt();
                }
            } else {
                for (int i = 0; i < size; i++) {
                    Complex cValue = new Complex(rvec[i], ivec[i]);
                    cValue = cValue.sqrt();
                    rvec[i] = cValue.getReal();
                    ivec[i] = cValue.getImaginary();
                }
            }
        } else {
            boolean hasNegative = false;
            for (int i = 0; i < size; ++i) {
                if (rvec[i] < 0.0) {
                    hasNegative = true;
                    break;
                }
            }
            if (hasNegative) {
                makeApache();
                makeComplex();
                for (int i = 0; i < size; ++i) {
                    cvec[i] = cvec[i].sqrt();
                }
            } else {
                for (int i = 0; i < size; i++) {
                    rvec[i] = FastMath.sqrt(rvec[i]);
                }
            }

        }
    }

    /**
     * Take exponential value of values in vector.
     */
    public void exp() {
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; ++i) {
                    cvec[i] = cvec[i].exp();
                }
            } else {
                for (int i = 0; i < size; i++) {
                    Complex cValue = new Complex(rvec[i], ivec[i]);
                    cValue = cValue.exp();
                    rvec[i] = cValue.getReal();
                    ivec[i] = cValue.getImaginary();
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                rvec[i] = FastMath.exp(rvec[i]);
            }
        }
    }

    /**
     * All points in the vector are set to Math.random(). Values will be
     * uniformly and randomly distributed between 0.0 and 1.0
     */
    public void rand() {
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; ++i) {
                    cvec[i] = cvec[i].add(new Complex(Math.random(), Math.random()));
                }
            } else {
                for (int i = 0; i < size; ++i) {
                    rvec[i] += Math.random();
                    ivec[i] += Math.random();
                }
            }
        } else {
            for (int i = 0; i < size; ++i) {
                rvec[i] += Math.random();
            }
        }
    }

    /**
     * Inverse Fast Fourier Transform of vector
     */
    public void ift() {
        if (isComplex) {
            if (!useApache) {
                makeApache();
            }

            Cfft.ift(cvec, size);
            freqDomain = false;
        }
    }

    /**
     * Calculate the first four moments of distribution in the specified region
     * of this vector. FIXME check moments
     *
     * @param start first point of region
     * @param end   last point of region
     * @return an array of four values containing the first four moments.
     * @throws IllegalArgumentException if not vector not real, data is null, or
     *                                  the range is invalid
     */
    public double[] moments(int start, int end)
            throws IllegalArgumentException {
        if (isComplex) {
            throw new IllegalArgumentException("vector not real");
        }
        if (rvec == null) {
            throw new IllegalArgumentException("moments: no data in vector");
        }
        int nValues = end - start + 1;
        if (nValues < 4) {
            throw new IllegalArgumentException("moments: invalid range");
        }
        double[] values = new double[nValues];
        int j = 0;
        for (int i = start; i <= end; i++) {
            double value = getReal(i);
            values[j++] = value;
        }
        double integral = 0.0;
        double weightedSum = 0.0;
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            integral += value;
            weightedSum += value * i;
        }
        double mean = weightedSum / integral;
        double[] sums = new double[5];
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            double delta = i - mean;
            for (int iSum = 0; iSum < sums.length; iSum++) {
                sums[iSum] += value * Math.pow(delta, iSum);
            }
        }
        double[] moments = new double[4];
        double variance = sums[2] / integral;
        double stdDev = Math.sqrt(variance);
        moments[0] = mean + start;
        moments[1] = variance;
        for (int iSum = 2; iSum < moments.length; iSum++) {
            moments[iSum] = (sums[iSum + 1] / integral) / Math.pow(stdDev, (iSum + 1));
        }
        return moments;
    }

    public double getSNRatio() throws IllegalArgumentException {
        return getSNRatio(0, size - 1, 0);
    }

    public double getSNRatio(int first, int last, int winSize) throws IllegalArgumentException {
        if (first > last) {
            int hold = first;
            first = last;
            last = hold;
        }
        if (first < 0) {
            first = 0;
        }
        if (last >= size) {
            last = size - 1;
        }
        int nValues = last - first + 1;
        if (nValues < 3) {
            throw new IllegalArgumentException("moments: invalid range");
        }
        if (winSize < 4) {
            winSize = size / 128;
            if (winSize < 8) {
                winSize = 8;
            }
        }

        double max = Double.NEGATIVE_INFINITY;
        for (int i = first; i <= last; i++) {
            double value = getReal(i);
            if (value > max) {
                max = value;
            }
        }
        double sdev = sdev(winSize);
        return max / sdev;
    }

    /**
     * Calculate standard deviation in a region that gives minimum standard
     * deviation.
     *
     * @param winSize Number of points to include in calculation
     * @return the standard deviation
     * @throws IllegalArgumentException if winSize < 4
     */
    public double sdev(int winSize)
            throws IllegalArgumentException {

        if (winSize < 4) {
            throw new IllegalArgumentException("moments: invalid winSize");
        }
        DescriptiveStatistics dStat = new DescriptiveStatistics(winSize);
        int start = 0;
        int end = size - 1;
        double minSdev = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            double value = getReal(i);
            dStat.addValue(value);
            double sdev = dStat.getStandardDeviation();
            if ((i - start + 1) >= winSize) {
                if (sdev < minSdev) {
                    minSdev = sdev;
                }
            }
        }
        return minSdev;
    }

    /**
     * Calculate mean, standard deviation, skewness and kurtosis in a specified
     * region of this vector
     *
     * @param start starting point (inclusive)
     * @param end   ending point (inclusive)
     * @return an array of four doubles containing the statistics
     * @throws IllegalArgumentException if vector not real or doesn't have at
     *                                  least 4 values in range
     */
    public double[] regionStats(int start, int end)
            throws IllegalArgumentException {

        if (rvec == null) {
            throw new IllegalArgumentException("moments: no data in vector");
        }
        int nValues = end - start + 1;
        if (nValues < 4) {
            throw new IllegalArgumentException("moments: invalid range");
        }
        DescriptiveStatistics dStat = new DescriptiveStatistics(nValues);
        for (int i = start; i <= end; i++) {
            double value = getReal(i);
            dStat.addValue(value);
        }
        double[] statValues = new double[4];
        statValues[0] = dStat.getMean();
        statValues[1] = dStat.getStandardDeviation();
        statValues[2] = dStat.getSkewness();
        statValues[3] = dStat.getKurtosis();
        return statValues;
    }

    /**
     * Split a complex vector into two vectors containing the real and imaginary
     * values. Caution this method assumes the data is stored in separate double
     * arrays. FIXME
     *
     * @param rVec this vector will be a real vector containing the real values
     *             of the original vector
     * @param iVec this vector will be a real vector containing the imaginary
     *             values of the original vector.
     */
    public void split(Vec rVec, Vec iVec) {
        rVec.resize(size, false);
        iVec.resize(size, false);

        for (int i = 0; i < size; i++) {
            rVec.set(i, getReal(i));
            iVec.set(i, getImag(i));
        }

        rVec.isComplex = false;
        rVec.freqDomain = getFreqDomain();
        rVec.dwellTime = dwellTime;
        rVec.centerFreq = centerFreq;
        rVec.setRefValue(getRefValue());
        rVec.ph0 = ph0;
        rVec.ph1 = ph1;
        iVec.isComplex = false;
        iVec.freqDomain = getFreqDomain();
        iVec.dwellTime = dwellTime;
        iVec.centerFreq = centerFreq;
        iVec.setRefValue(getRefValue());

    }

    /**
     * Exchange the real and imaginary components of a complex vector
     *
     * @return this vector
     * @throws IllegalArgumentException if vector is real
     */
    public Vec exchange() throws IllegalArgumentException {
        if (isComplex) {
            for (int i = 0; i < size; i++) {
                double dReal = getReal(i);
                double dImaginary = getImag(i);
                set(i, new Complex(dImaginary, dReal));
            }
        } else {
            throw new IllegalArgumentException("exchange: vector is not complex");
        }

        return (this);
    }

    /**
     * Convert a real vector with real and imaginary values in alternating
     * positons of array into a Complex vector.
     *
     * @return this vector
     * @throws IllegalArgumentException if vector is already complex
     */
    public Vec merge() throws IllegalArgumentException {
        if (isComplex()) {
            throw new IllegalArgumentException("merge: vector is complex");
        } else {
            useApache = true;
            resize(size / 2, true);
            for (int i = 0; i < size; i++) {
                double dReal = rvec[i];
                double dImaginary = rvec[size + i];
                cvec[i] = new Complex(dReal, dImaginary);
            }
        }

        return (this);
    }

    /**
     * Convert a Complex vector into real vector with the real and imaginary
     * values in alternating positions of array.
     *
     * @return this vector
     * @throws IllegalArgumentException if vector is already complex
     */
    public Vec alternate() throws IllegalArgumentException {
        if (!isComplex()) {
            throw new IllegalArgumentException("merge: vector is not complex");
        } else {
            int newSize = size * 2;
            double[] newarr = new double[newSize];
            if (rvec != null) {
                //copy rvec from 0 to size
                //(because from size to rvec.length is junk data that we don't want)
                System.arraycopy(rvec, 0, newarr, 0, Math.min(size, rvec.length));
            }
            rvec = newarr;
            if (useApache) {
                for (int i = 0; i < size; i++) {
                    rvec[2 * i] = getReal(i);
                    rvec[2 * i + 1] = getImag(i);
                }
            } else {
                for (int i = size - 1; i >= 0; i--) {
                    rvec[2 * i] = getReal(i);
                    rvec[2 * i + 1] = getImag(i);
                }
            }
            size = newSize;
            isComplex = false;
        }
        return (this);
    }

    /**
     * Swap byte order of stored values
     */
    public void swapBytes() {
        int intVal;
        int intVal0;
        int intVal1;
        int intVal2;
        int intVal3;

        // note: conversion to float
        if (isComplex) {
            for (int i = 0; i < size; i++) {
                intVal = Float.floatToIntBits((float) getReal(i));
                intVal0 = ((intVal >> 24) & 0xFF);
                intVal1 = ((intVal >> 8) & 0xFF00);
                intVal2 = ((intVal << 8) & 0xFF0000);
                intVal3 = ((intVal << 24) & 0xFF000000);
                intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
                double dReal = Float.intBitsToFloat(intVal);
                intVal = Float.floatToIntBits((float) getImag(i));
                intVal0 = ((intVal >> 24) & 0xFF);
                intVal1 = ((intVal >> 8) & 0xFF00);
                intVal2 = ((intVal << 8) & 0xFF0000);
                intVal3 = ((intVal << 24) & 0xFF000000);
                intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
                double dImaginary = Float.intBitsToFloat(intVal);
                set(i, new Complex(dReal, dImaginary));
            }
        } else {
            for (int i = 0; i < size; i++) {
                intVal = Float.floatToIntBits((float) getReal(i));
                intVal0 = ((intVal >> 24) & 0xFF);
                intVal1 = ((intVal >> 8) & 0xFF00);
                intVal2 = ((intVal << 8) & 0xFF0000);
                intVal3 = ((intVal << 24) & 0xFF000000);
                intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
                set(i, Float.intBitsToFloat(intVal));
            }
        }
    }

    /**
     *
     */
    public void swapBytes8() {

        // note: conversion to float
        if (isComplex) {
            for (int i = 0; i < size; i++) {
                int intVal = Float.floatToIntBits((float) getReal(i));
                int intVal0 = ((intVal >> 24) & 0xFF);
                int intVal1 = ((intVal >> 8) & 0xFF00);
                int intVal2 = ((intVal << 8) & 0xFF0000);
                int intVal3 = ((intVal << 24) & 0xFF000000);
                intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
                double dReal = Float.intBitsToFloat(intVal);
                intVal = Float.floatToIntBits((float) getImag(i));
                intVal0 = ((intVal >> 24) & 0xFF);
                intVal1 = ((intVal >> 8) & 0xFF00);
                intVal2 = ((intVal << 8) & 0xFF0000);
                intVal3 = ((intVal << 24) & 0xFF000000);
                intVal = (intVal0) + (intVal1) + (intVal2) + intVal3;
                double dImaginary = Float.intBitsToFloat(intVal);
                set(i, new Complex(dReal, dImaginary));
            }
        } else {
            long mask = 0xFF;
            short shift = 56;

            for (int i = 0; i < size; i++) {
                long longVal0 = Double.doubleToLongBits(getReal(i));
                long longVal = 0;

                for (int j = 0; j < 4; j++) {
                    longVal += ((longVal0 >> shift) & mask);
                    mask <<= 8;
                    shift -= 16;
                }

                shift = 8;

                for (int j = 0; j < 4; j++) {
                    longVal += ((longVal0 << shift) & mask);
                    mask <<= 8;
                    shift += 16;
                }

                set(i, Double.longBitsToDouble(longVal));
            }
        }
    }

    /**
     * Automatically calculate phase values for this vector using an one of two
     * algorithms. One based on flattening baseline regions adjacent to peaks
     * and one based on entropy minimization
     *
     * @param doFirst  Set to true to include first order phase correction
     * @param winSize  Window size used for analyzing for baseline region
     * @param ratio    Ratio Intensity to noise ratio used for indentifying
     *                 baseline reginos
     * @param mode     Set to 0 for flattening mode and 1 for entropy mode
     * @param ph1Limit Set limit on first order phase. Can prevent unreasonable
     *                 results
     * @return an array of 1 or two phase values (depending on whether first
     * order mode is used)
     */
    public double[] autoPhase(boolean doFirst, int winSize, double ratio, int mode, double ph1Limit, double negativePenalty) {
        return autoPhase(doFirst, winSize, ratio, mode, ph1Limit, negativePenalty, false, null, null);
    }

    /**
     * Automatically calculate phase values for this vector using an one of two
     * algorithms. One based on flattening baseline regions adjacent to peaks
     * and one based on entropy minimization
     *
     * @param doFirst  Set to true to include first order phase correction
     * @param winSize  Window size used for analyzing for baseline region
     * @param ratio    Ratio Intensity to noise ratio used for indentifying
     *                 baseline reginos
     * @param mode     Set to 0 for flattening mode and 1 for entropy mode
     * @param ph1Limit Set limit on first order phase. Can prevent unreasonable
     *                 results
     * @param useRegion Autophase using siganl in specified region
     * @param ppmStart Starting point for region.  Find region if null;
     * @param ppmEnd Ending point for region. Find region if null;
     * @return an array of 1 or two phase values (depending on whether first
     * order mode is used)
     */
    public double[] autoPhase(boolean doFirst, int winSize, double ratio, int mode,
                              double ph1Limit, double negativePenalty, boolean useRegion, Double ppmStart, Double ppmEnd) {

        int pivot = 0;
        double p1PenaltyWeight = 1.0;
        if (winSize < 1) {
            winSize = 2;
        }
        if (ratio <= 0.0) {
            ratio = 25.0;
        }
        double[] phaseResult;
        if (!doFirst || useRegion) {
            TestBasePoints tbPoints = new TestBasePoints(this, winSize, ratio, mode, negativePenalty, useRegion, ppmStart, ppmEnd);
            phaseResult = tbPoints.autoPhaseZero();
        } else {
            TestBasePoints tbPoints = new TestBasePoints(this, winSize, ratio, mode, negativePenalty, useRegion, ppmStart, ppmEnd);
            tbPoints.setP1PenaltyWeight(p1PenaltyWeight);
            phaseResult = tbPoints.autoPhase(ph1Limit);
        }
        return phaseResult;
    }

    public double testAutoPhase(int winSize, double ratio, int mode,
                                double negativePenalty) {
        return testAutoPhase(winSize, ratio, mode, negativePenalty, 0.0, 0.0);
    }

    public double testAutoPhase(int winSize, double ratio, int mode,
                                double negativePenalty, double phase0, double phase1) {
        TestBasePoints tbPoints = new TestBasePoints(this, winSize, ratio, mode, negativePenalty, false,  null, null);
        return tbPoints.testFit(phase0, phase1);
    }

    /**
     * Automatically phase spectrum by maximizing the sum of intensities
     *
     * @return zeroth order phase value
     */
    public double autoPhaseByMax() {
        return autoPhaseByMax(0, size - 1);
    }

    /**
     * Automatically phase spectrum by maximizing the sum of intensities
     *
     * @param first starting point of region to analyze
     * @param last  ending point of region to analyze
     * @return zeroth order phase value
     */
    public double autoPhaseByMax(int first, int last) {
        double minPhase = 0.0;

        double stepSize = 45.0;
        int nSteps = (int) Math.round(180.0 / stepSize);
        double minSum = Double.MAX_VALUE;
        for (int i = -nSteps; i <= nSteps; i++) {
            double phase = i * stepSize;
            double sum = -sumRealRegion(first, last, phase);

            if (sum < minSum) {
                minSum = sum;
                minPhase = phase;
            }
        }

        double x1;
        double x2;
        double f1;
        double f2;
        double r = 0.3819660;
        double c = 1.0 - r;
        double ax = minPhase - stepSize;
        double bx = minPhase;
        double cx = minPhase + stepSize;
        double x0 = ax;
        double x3 = cx;

        if (Math.abs(cx - bx) > Math.abs(bx - ax)) {
            x1 = bx;
            x2 = bx + (c * (cx - bx));
        } else {
            x2 = bx;
            x1 = bx - (c * (bx - ax));
        }

        f1 = -sumRealRegion(first, last, x1);
        f2 = -sumRealRegion(first, last, x2);

        while (Math.abs(x3 - x0) > 1.0) {
            if (f2 < f1) {
                x0 = x1;
                x1 = x2;
                x2 = (r * x1) + (c * x3);
                f1 = f2;
                f2 = -sumRealRegion(first, last, x2);
            } else {
                x3 = x2;
                x2 = x1;
                x1 = (r * x2) + (c * x0);
                f2 = f1;
                f1 = -sumRealRegion(first, last, x1);
            }
        }

        if (f1 < f2) {
            minPhase = x1;
        } else {
            minPhase = x2;
        }
        return minPhase;
    }

    /**
     * Apply the specified phase values to this vector.
     *
     * @param p0               The zeroth order phase value
     * @param p1               The first order phase value
     * @param pivot            The pivot value at which the first order phase correction
     *                         has no effect on data
     * @param phaseAbs         if false apply the specified values, if true subtract the
     *                         currently stored ph0 and ph1 values from the specified values and then
     * @param discardImaginary Discard the imaginary values and convert vector
     *                         to real. Phasing is a little faster if you do this (and saves calling a
     *                         seperate REAL operation.
     * @return this vector
     */
    public Vec phase(double p0, double p1, int pivot, boolean phaseAbs, boolean discardImaginary) {
        double frac = ((double) pivot) / size;
        if (Double.isNaN(p0)) {
            if (phaseAbs) {
                p0 = ph0;
            } else {
                p0 = 0.0;
            }
        }
        if (!Double.isNaN(p1)) {
            if (phaseAbs) {
                double deltaPH0 = (p1 - ph1) * frac;
                p0 = p0 - deltaPH0;
            } else {
                double deltaPH0 = p1 * frac;
                p0 = p0 - deltaPH0;
            }
        } else if (phaseAbs) {
            p1 = ph1;
        } else {
            p1 = 0.0;
        }
        return (Vec) phase(p0, p1, phaseAbs, false);
    }

    /**
     * Apply the specified phase values to this vector.
     *
     * @param p0 The zeroth order phase value
     * @param p1 The first order phase value
     * @return this vector
     */
    public Vec phase(double p0, double p1) {
        return (Vec) phase(p0, p1, false, false);
    }

    /**
     * Update this vector by applying coefficients to combine adjacent values.
     * Used by NMRFx Processor to display vectors of the FID along indirect
     * dimensions
     *
     * @param coefs The coefficients to use in combining values
     * @return this vector
     * @throws IllegalArgumentException if the length of coefficients isn't 8
     */
    public Vec eaCombine(double[] coefs) throws IllegalArgumentException {
        if (!isComplex) {
            return this;
        }
        if (coefs.length != 8) {
            throw new IllegalArgumentException("Coeficent length != 8");
        }
        for (int i = 0; i < size; i += 2) {
            Complex value1 = getComplex(i);
            Complex value2 = getComplex(i + 1);

            double real1 = value1.getReal();
            double real2 = value2.getReal();
            double imag1 = value1.getImaginary();
            double imag2 = value2.getImaginary();

            double newReal = real1 * coefs[0] + imag1 * coefs[1] + real2 * coefs[2] + imag2 * coefs[3];
            double newImag = real1 * coefs[4] + imag1 * coefs[5] + real2 * coefs[6] + imag2 * coefs[7];
            set(i / 2, new Complex(newReal, newImag));
        }
        resize(size / 2, true);
        return (this);
    }

    /**
     * Update this vector by combining adjacent values for hyper-complex
     * acquistion. Used by NMRFx Processor to display vectors of the FID along
     * indirect dimensions
     *
     * @return this vector
     */
    public Vec hcCombine() {
        if (!isComplex) {
            return this;
        }

        for (int i = 0; i < size; i += 2) {
            Complex value1 = getComplex(i);
            Complex value2 = getComplex(i + 1);

            double real1 = value1.getReal();
            double real2 = value2.getReal();

            set(i / 2, new Complex(real1, real2));
        }
        resize(size / 2, true);
        return (this);
    }

    /**
     * Multiply vector by a frequency, relative to sweep width. Used with
     * digital filter with group delay (shift) of ncoefs/2.
     *
     * @param freq  frequency in Hz (up to +/-0.5 sweep width)
     * @param shift number of points to shift scale by
     * @see FirFilter
     */
    public void multiplyByFrequency(double freq, double shift) {
        double scale = freq;
        scale *= 2 * Math.PI;
        double fpt;
        for (int i = 0; i < size; i++) {
            fpt = scale * (i - shift);
            multiply(i, Math.cos(fpt), -Math.sin(fpt));
        }
    }

    /**
     * Apply a correction (typically for baseline correction) to this vector by
     * subtracting a polynomial of the specified order and with the specified
     * coefficients. The first (0 position) coefficient is the constant term. X
     * values for the polynomial are in fractions of the vector size.
     *
     * @param order the polynomial order
     * @param X     the coefficients.
     */
    public void correctVec(int order, RealVector X) {
        double xval;
        double yval;

        for (int i = 0; i < size; i++) {
            yval = X.getEntry(0);
            xval = (1.0 * i) / size;

            for (int j = 1; j < order; j++) {
                yval += (xval * X.getEntry(j));
                xval *= ((1.0 * i) / size);
            }

            rvec[i] -= yval;
        }
    }

    public void calcBaseLineRegions() {
        int vecSize = getSize();
        int winSize = 16;
        double ratio = 10.0;
        if (winSize > (vecSize / 4)) {
            winSize = vecSize / 4;
        }

        boolean[] isInSignalRegion;

        ArrayList<Integer> positions = Util.idBaseLineBySDev(this, winSize, ratio);
        isInSignalRegion = Util.getSignalRegion(vecSize, positions);
        setSignalRegion(isInSignalRegion);
    }

    public Vec bcWhit(double lambda, int order, boolean baselineMode) {
        int vecSize = getSize();

        boolean[] isInSignalRegion = getSignalRegion();
        if ((isInSignalRegion == null) || (isInSignalRegion.length <= 4)) {
            calcBaseLineRegions();
            isInSignalRegion = getSignalRegion();
        }
        if ((isInSignalRegion != null) && (isInSignalRegion.length > 4)) {
            double[] w = new double[vecSize + 1];
            double[] z = new double[vecSize + 1];
            double[] y = new double[vecSize + 1];
            if (isComplex()) {
                makeReal();
            }

            int nSig = 0;
            for (int i = 0; i < vecSize; i++) {
                y[i + 1] = rvec[i];
                if (isInSignalRegion[i]) {
                    w[i + 1] = 0;
                    nSig++;
                } else {
                    w[i + 1] = 1;
                }
            }
            double[] a = new double[order + 1];
            if (nSig < (vecSize - 4)) {
                Util.pascalrow(a, order);
                Util.asmooth(w, y, z, a, lambda, vecSize, order);
                if (baselineMode) {
                    if (vecSize >= 0) {
                        System.arraycopy(z, 1, rvec, 0, vecSize);
                    }
                } else {
                    for (int i = 0; i < vecSize; i++) {
                        rvec[i] -= z[i + 1];
                    }
                }
            }
        }
        return this;
    }

    /**
     * Bucket a vector into a smaller size by summing adjacent points (within
     * each bucket)
     *
     * @param nBuckets The number of buckets (will be the new size of vector).
     * @throws IllegalArgumentException if vector is complex or number of
     *                                  buckets larger than size or not an integer fraction of size
     */
    public void bucket(int nBuckets) throws IllegalArgumentException {
        if (getFreqDomain()) {
            adjustRef(0, nBuckets);
        }

        if (size < nBuckets) {
            throw new IllegalArgumentException("bucket: nBuckets must be smaller than size");
        }

        if ((size % nBuckets) != 0) {
            throw new IllegalArgumentException("bucket: size must be multiple of nBuckets");
        }

        int bucketSize = size / nBuckets;

        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < nBuckets; i++) {
                    Complex bucketVal = Complex.ZERO;
                    int k = i * bucketSize;
                    for (int j = 0; j < bucketSize; j++) {
                        bucketVal = bucketVal.add(cvec[k++]);
                    }
                    cvec[i] = bucketVal;
                }
            } else {
                for (int i = 0; i < nBuckets; i++) {
                    double rVal = 0.0;
                    double iVal = 0.0;
                    int k = i * bucketSize;
                    for (int j = 0; j < bucketSize; j++) {
                        rVal += rvec[k];
                        iVal += ivec[k++];
                    }
                    rvec[i] = rVal;
                    ivec[i] = iVal;
                }
            }
        } else {
            for (int i = 0; i < nBuckets; i++) {
                double bucketVal = 0.0;
                int k = i * bucketSize;

                for (int j = 0; j < bucketSize; j++) {
                    bucketVal += rvec[k++];
                }

                rvec[i] = bucketVal;
            }
        }
        size = nBuckets;
    }

    /**
     * Time-domain solvent suppression
     *
     * @param winSize Size of window. Larger the window the narrower the region
     *                of suppression
     * @param nPasses How many passes of filter to perform. Performing 3 passes
     *                is optimal.
     * @return this vector
     * @throws VecException if winSize larger than size of vector
     */
    public Vec tdSSWithFilter(int winSize, int nPasses)
            throws VecException {
        if (winSize >= size) {
            throw new VecException("movingAverageFilter: error in parameters");
        }
        Vec tempVec = new Vec(size, isComplex);
        int vStart = getStart();
        copy(tempVec, vStart, size - vStart);
        tempVec.movingAverageFilter(winSize, nPasses);
        if (vStart != 0) {
            tempVec.shiftWithExpand(vStart);
        }
        return (Vec) subtract(tempVec);
    }

    /**
     * Moving average filter.
     *
     * @param winSize Size of window. Larger the window the narrower the region
     *                of suppression when used for solvent suppression
     * @param nPasses How many passes of filter to perform. Performing 3 passes
     *                is optimal.
     * @return this vector
     * @throws VecException if winSize larger than size of vector
     */
    public Vec movingAverageFilter(int winSize, int nPasses)
            throws VecException {
        // multiple pass idea from http://climategrog.wordpress.com/2013/05/19/triple-running-mean-filters/
        if (winSize >= size) {
            throw new VecException("movingAverageFilter: error in parameters");
        }
        double filterDivisor = 1.2067;
        for (int i = 0; i < nPasses; i++) {
            if ((winSize % 2) != 1) {
                winSize += 1;
            }
            if (isComplex) {
                if (useApache) {
                    movingAverageFilter(cvec, size, winSize);
                } else {
                    movingAverageFilter(rvec, ivec, size, winSize);
                }
            } else {
                movingAverageFilter(rvec, size, winSize);
            }
            winSize = (int) Math.ceil(winSize / filterDivisor);
        }
        return this;
    }

    /**
     * Moving average filter for array of real values
     *
     * @param rValues The real values
     * @param vecSize Number of values to use
     * @param winSize window size of filter
     * @throws VecException if windows size bigger than vector or values are
     *                      null
     */
    public static void movingAverageFilter(double[] rValues, int vecSize, int winSize)
            throws VecException {
        if (winSize >= vecSize) {
            throw new VecException("movingAverageFilter: error in parameters");
        }
        ResizableDoubleArray rWin = new ResizableDoubleArray(winSize);

        if (rValues == null) {
            throw new VecException("movingAverageFilter: no data in vector");
        }
        int winHalf = winSize / 2;
        double rSum = 0.0;
        for (int i = 0; i < winSize; i++) {
            double rValue = rValues[i];
            rWin.addElement(rValue);
            rSum += rValue;
        }
        double rAverage = rSum / winSize;
        for (int i = winHalf; i < winSize; i++) {
            rValues[i - winHalf] = rAverage;
        }
        for (int i = winSize; i < vecSize; i++) {
            double rValue = rValues[i];
            double rOld = rWin.addElementRolling(rValue);
            rAverage = rAverage - rOld / winSize + rValue / winSize;
            rValues[i - winHalf] = rAverage;
        }
        for (int i = (vecSize - winHalf); i < vecSize; i++) {
            rValues[i] = rAverage;
        }
    }

    /**
     * Moving average filter for two arrays containing real and imaginary values
     *
     * @param rValues The real values
     * @param iValues The imaginary values
     * @param vecSize Number of values to use
     * @param winSize window size of filter
     * @throws VecException if windows size bigger than vector or values are
     *                      null
     */
    public static void movingAverageFilter(double[] rValues, double[] iValues, int vecSize, int winSize)
            throws VecException {
        if (winSize >= vecSize) {
            throw new VecException("movingAverageFilter: error in parameters");
        }
        ResizableDoubleArray rWin = new ResizableDoubleArray(winSize);
        ResizableDoubleArray iWin = new ResizableDoubleArray(winSize);
        if (rValues == null) {
            throw new VecException("movingAverageFilter: no data in vector");
        }
        if (iValues == null) {
            throw new VecException("movingAverageFilter: no data in vector");
        }
        int winHalf = winSize / 2;
        double rSum = 0.0;
        double iSum = 0.0;
        for (int i = 0; i < winSize; i++) {
            double rValue = rValues[i];
            double iValue = iValues[i];
            rWin.addElement(rValue);
            iWin.addElement(iValue);
            rSum += rValue;
            iSum += iValue;
        }
        double rAverage = rSum / winSize;
        double iAverage = iSum / winSize;
        for (int i = winHalf; i < winSize; i++) {
            rValues[i - winHalf] = rAverage;
            iValues[i - winHalf] = iAverage;
        }
        for (int i = winSize; i < vecSize; i++) {
            double rValue = rValues[i];
            double iValue = iValues[i];
            double rOld = rWin.addElementRolling(rValue);
            double iOld = iWin.addElementRolling(iValue);
            rAverage = rAverage - rOld / winSize + rValue / winSize;
            iAverage = iAverage - iOld / winSize + iValue / winSize;
            rValues[i - winHalf] = rAverage;
            iValues[i - winHalf] = iAverage;
        }
        for (int i = (vecSize - winHalf); i < vecSize; i++) {
            rValues[i] = rAverage;
            iValues[i] = iAverage;
        }
    }

    /**
     * Moving average filter for array of Complex values
     *
     * @param cValues The Complex values
     * @param vecSize Number of values to use
     * @param winSize window size of filter
     * @throws VecException if windows size bigger than vector or values are
     *                      null
     */
    public static void movingAverageFilter(Complex[] cValues, int vecSize, int winSize)
            throws VecException {
        if (winSize >= vecSize) {
            throw new VecException("movingAverageFilter: error in parameters");
        }
        ResizableDoubleArray rWin = new ResizableDoubleArray(winSize);
        ResizableDoubleArray iWin = new ResizableDoubleArray(winSize);
        if (cValues == null) {
            throw new VecException("movingAverageFilter: no data in vector");
        }
        int winHalf = winSize / 2;
        double rSum = 0.0;
        double iSum = 0.0;
        for (int i = 0; i < winSize; i++) {
            double rValue = cValues[i].getReal();
            double iValue = cValues[i].getImaginary();
            rWin.addElement(rValue);
            iWin.addElement(iValue);
            rSum += rValue;
            iSum += iValue;
        }
        double rAverage = rSum / winSize;
        double iAverage = iSum / winSize;
        for (int i = winHalf; i < winSize; i++) {
            cValues[i - winHalf] = new Complex(rAverage, iAverage);
        }
        for (int i = winSize; i < vecSize; i++) {
            double rValue = cValues[i].getReal();
            double iValue = cValues[i].getImaginary();
            double rOld = rWin.addElementRolling(rValue);
            double iOld = iWin.addElementRolling(iValue);
            rAverage = rAverage - rOld / winSize + rValue / winSize;
            iAverage = iAverage - iOld / winSize + iValue / winSize;
            cValues[i - winHalf] = new Complex(rAverage, iAverage);
        }
        for (int i = (vecSize - winHalf); i < vecSize; i++) {
            cValues[i] = new Complex(rAverage, iAverage);
        }
    }

    /**
     * Apply soft thresholding to vector by setting values with absolute value
     * below threshold to 0.0 and subtracting threshold from positive values
     * (greater than threshold) or adding threshold to negative values (less
     * than threshold).
     *
     * @param threshold
     */
    public Vec softThreshold(double threshold) throws VecException {
        if (isComplex) {
            throw new VecException("Vec must be real");
        } else {
            for (int i = 0; i < size; ++i) {
                double value = FastMath.abs(rvec[i]);
                if (value < threshold) {
                    rvec[i] = 0.0;
                } else {
                    if (rvec[i] > 0.0) {
                        rvec[i] -= threshold;
                    } else {
                        rvec[i] += threshold;
                    }

                }
            }
        }
        return this;
    }

    /**
     * Time domain polynomial correction. Can be used for solvent suppression by
     * fitting a polynomial to the FID.
     *
     * @param order   The polynomial order
     * @param winSize Size of regions to fit
     * @param start   Start the fit at this point. Using value greater than 0
     *                allows skipping artifacts
     * @throws VecException if window size or polynomial order invalid for
     *                      vector size
     */
    public void tdpoly(int order, int winSize, int start) throws VecException {
        int m;
        int n;
        int i;
        int j;
        int k;
        double reSum;
        double imSum;
        double xval;
        int nRegions;

        if (cvec == null) {
            throw new VecException("tdpoly: no data in vector");
        }

        m = size;

        if (start < 0) {
            start = 0;
        }

        if (start >= m) {
            start = m - 2;
        }

        if ((winSize <= 0) || (winSize > m)) {
            throw new VecException("NvZPoly: winSize");
        }

        nRegions = (m - start) / winSize;

        if ((order < 1) || (order > 16) || (order > (nRegions / 2))) {
            throw new VecException("NvZPoly: order");
        }

        RealMatrix B = new Array2DRowRealMatrix(nRegions, 2);

        k = start;

        for (j = 0; j < nRegions; j++) {
            reSum = 0.0;
            imSum = 0.0;

            for (i = 0; i < winSize; i++) {
                reSum += cvec[k].getReal();
                imSum += cvec[k].getImaginary();
                k++;
            }

            B.setEntry(j, 0, reSum / winSize);
            B.setEntry(j, 1, imSum / winSize);
        }

        n = order;

        RealMatrix A = new Array2DRowRealMatrix(nRegions, n);

        for (i = 0; i < nRegions; i++) {
            A.setEntry(i, 0, 1.0);

            for (j = 1; j < n; j++) {
                A.setEntry(i, j,
                        A.getEntry(i, j - 1) * (((i * winSize) + (winSize / 2)) - 0.5));
            }
        }
        SingularValueDecomposition svd = new SingularValueDecomposition(A);
        RealMatrix X = svd.getSolver().solve(B);

        double rVal;
        double iVal;

        for (i = 0; i < m; i++) {
            rVal = X.getEntry(0, 0);
            iVal = X.getEntry(0, 1);
            xval = i;

            for (j = 1; j < n; j++) {
                rVal += (xval * X.getEntry(j, 0));
                iVal += (xval * X.getEntry(j, 1));
                xval *= i;
            }
            cvec[i] = new Complex(cvec[i].getReal() - rVal, cvec[i].getImaginary() - iVal);
        }
    }

    /**
     * Correct vector by subtracting a sum of sines using coefficients stored in
     * provided vector.
     *
     * @param order The order of correction function
     * @param X     an array of coefficients
     */
    public void correctVecSine(int order, RealVector X) {
        double yval;

        for (int i = 0; i < size; i++) {
            yval = X.getEntry(0);
            for (int j = 1; j < order; j++) {
                int trigOrder = (j + 1) / 2;
                if ((j % 2) == 0) {
                    yval += Math.sin(i * trigOrder * 2 * Math.PI / (size - 1)) * X.getEntry(j);
                } else {
                    yval += Math.cos(i * trigOrder * 2 * Math.PI / (size - 1)) * X.getEntry(j);
                }
            }

            rvec[i] -= yval;
        }
    }

    /**
     * Baseline correction by fitting a smooth envelope below vector points
     *
     * @param winSize      window size
     * @param lambda       smoothing parameter
     * @param order        order of fit (0 or 1)
     * @param baselineMode if true set the vector to be the fitted baseline,
     *                     rather than correcting the values. Useful for diagnostics
     * @throws VecException if vector complex
     */
    public void esmooth(int winSize, double lambda, int order, boolean baselineMode) throws VecException {
        if (isComplex) {
            throw new VecException("esmooth: vector complex");
        }
        int nRegions = size / winSize;
        double[] w = new double[size + 1];
        double[] z = new double[size + 1];
        double[] y = new double[size + 1];
        double[] vecY = new double[size];
        for (int i = 0; i < size; i++) {
            y[i + 1] = getReal(i);
        }
        VecUtil.psmooth(y, size, 500);
        if (size >= 0) {
            System.arraycopy(y, 1, vecY, 0, size);
        }
        ArrayList<Integer> xValues = new ArrayList<>();
        ArrayList<Double> yValues = new ArrayList<>();
        for (int i = 0; i < nRegions; i++) {
            double minValue = Double.MAX_VALUE;
            int minK = 0;
            for (int j = 0; j < winSize; j++) {
                int k = i * winSize + j;
                double value = vecY[k];

                if (value < minValue) {
                    minValue = value;
                    minK = k;
                }
            }
            xValues.add(minK);
            yValues.add(minValue);
        }
        ArrayList<Integer> xValues3 = new ArrayList<>();
        ArrayList<Double> yValues3 = new ArrayList<>();

        int nCycles = 3;
        for (int iCycle = 0; iCycle < nCycles; iCycle++) {
            ArrayList<Integer> xValues2 = new ArrayList<>();
            ArrayList<Double> yValues2 = new ArrayList<>();
            int m = xValues.size();
            for (int k = 0; k < (m - 1); k++) {
                int x1 = (xValues.get(k));
                int x2 = (xValues.get(k + 1));
                double y1 = (yValues.get(k));
                double y2 = (yValues.get(k + 1));
                double minDelta = Double.MAX_VALUE;
                double minValue = 0.0;
                int minJ = 0;
                for (int j = x1; j < x2; j++) {
                    double yTest = (1.0 * j - x1) / (1.0 * x2 - x1) * (y2 - y1) + y1;
                    double value = vecY[j];
                    double delta = value - yTest;
                    if (delta < minDelta) {
                        minDelta = delta;
                        minValue = value;
                        minJ = j;

                    }
                }
                xValues2.add(x1);
                yValues2.add(y1);
                if (minDelta < 0.0) {
                    xValues2.add(minJ);
                    yValues2.add(minValue);
                }

            }
            xValues3.clear();
            yValues3.clear();
            xValues3.add(xValues2.get(0));
            yValues3.add(yValues2.get(0));
            m = xValues2.size();
            for (int k = 0; k < (m - 2); k++) {
                int x1 = (xValues2.get(k));
                int x2 = (xValues2.get(k + 1));
                int x3 = (xValues2.get(k + 2));
                double y1 = (yValues2.get(k));
                double y2 = (yValues2.get(k + 1));
                double y3 = (yValues2.get(k + 2));
                double yTest = (1.0 * x2 - x1) / (1.0 * x3 - x1) * (y3 - y1) + y1;
                if (yTest < y2) {
                    y2 = (yTest * 3.0 + y2) / 4.0;
                }
                xValues3.add(x2);
                yValues3.add(y2);
            }
            xValues3.add(xValues2.get(m - 1));
            yValues3.add(yValues2.get(m - 1));

            xValues = xValues3;
            yValues = yValues3;
        }
        if (isComplex) {
            makeReal();
        }
        int m = xValues3.size();
        for (int k = 0; k < (m - 1); k++) {
            int x1 = (xValues3.get(k));
            double y1 = (yValues3.get(k));
            w[x1 + 1] = 1;
            y[x1 + 1] = y1;
        }

        double[] a = new double[order + 1];
        Util.pascalrow(a, order);
        Util.asmooth(w, y, z, a, lambda, size, order);
        boolean adjNeg = false;
        for (int i = 0; i < size; i++) {
            if (z[i + 1] > rvec[i]) {
                adjNeg = true;
                z[i + 1] = rvec[i];
            }
        }
        if (adjNeg) {
            VecUtil.psmooth(z, size, 500);
        }
        if (baselineMode) {
            if (size >= 0) {
                System.arraycopy(z, 1, rvec, 0, size);
            }
        } else {
            for (int i = 0; i < size; i++) {
                rvec[i] -= z[i + 1];
            }
        }
    }

    /**
     * Replace a region of a vector with a smoothed line across region. Useful
     * for removing large (solvent) signals. Values at edge of region will
     * retain a decreasing contribution from the original values so that the
     * transition to the interpolated region will be gradual. Remaining values
     * will be interpolated between the average of the left and right edge
     * regions.
     *
     * @param icenter   center of region. If value less than 0 the position of
     *                  maximum intensity will be used.
     * @param nInterp   Number of points within each side of region that will be
     *                  fully interpolated.
     * @param halfWidth half width of region be zero within region.
     * @throws VecException if vector complex
     */
    public void gapSmooth(int icenter, int nInterp, int halfWidth) throws VecException {
        if (isComplex) {
            throw new VecException("vector must be real");
        }
        if (icenter < 0) {
            IndexValue indexValue = maxIndex();
            icenter = indexValue.getIndex();
        }
        int m = 2 * halfWidth + 1;
        int nKeep = halfWidth - nInterp;
        double left = 0.0;
        double right = 0.0;
        for (int i = 0; i < nKeep; i++) {
            left += rvec[icenter - halfWidth + i];
            right += rvec[icenter + halfWidth - i];
        }
        left /= nKeep;
        right /= nKeep;
        for (int i = 0; i < m; i++) {
            double f = (double) i / (m - 1.0);
            double g = 1.0;
            if (i < nKeep) {
                g = (double) i / (nKeep - 1.0);
            } else if ((m - i) < nKeep) {
                g = (double) (m - i) / (nKeep - 1.0);
            }
            double value = (1 - f) * left + f * right;
            rvec[icenter - halfWidth + i] = g * value + (1 - g) * rvec[icenter - halfWidth + i];
        }
    }

    public double polyMax(int intMax) {
        double adjust = 0.0;
        if ((intMax > 0) && (intMax < (size - 1))) {

            double f2 = getReal(intMax + 1);
            double f1 = getReal(intMax);
            double f0 = getReal(intMax - 1);
            if ((f1 > f0) && (f1 > f2)) {
                adjust = ((f2 - f0) / (2.0 * ((2.0 * f1) - f2 - f0)));
            }
        }
        return intMax + adjust;
    }

    /**
     * Shift values in vector to right (if shift positive) or left (if shift
     * negative). Unoccupied positions will be set to zero. The size of vector
     * will be expanded to accomadate shifted values so no values will be lost
     *
     * @param shiftValue the number of points to shift vector values by
     */
    public void shiftWithExpand(int shiftValue) {
        if (shiftValue != 0) {
            resize(size + shiftValue);
        }
        shift(shiftValue);
    }

    /**
     * Shift values in vector to right (if shift positive) or left (if shift
     * negative). Unoccupied positions will be set to zero. This is not a
     * circular shift so values will be lost.
     *
     * @param shiftValue the number of points to shift vector values by
     */
    public void shift(int shiftValue) {
        if ((shiftValue != 0) && (Math.abs(shiftValue) < size)) {
            if (isComplex) {
                if (useApache) {
                    if (shiftValue > 0) {
                        System.arraycopy(cvec, 0, cvec, shiftValue, size - shiftValue);
                        for (int i = 0; i < shiftValue; i++) {
                            cvec[i] = new Complex(0.0, 0.0);
                        }
                    } else {
                        shiftValue = -shiftValue;
                        System.arraycopy(cvec, shiftValue, cvec, 0, size - shiftValue);
                        for (int i = 0; i < shiftValue; i++) {
                            cvec[size - shiftValue + i] = new Complex(0.0, 0.0);
                        }
                    }
                } else if (shiftValue > 0) {
                    System.arraycopy(rvec, 0, rvec, shiftValue, size - shiftValue);
                    System.arraycopy(ivec, 0, ivec, shiftValue, size - shiftValue);
                    for (int i = 0; i < shiftValue; i++) {
                        rvec[i] = 0.0;
                        ivec[i] = 0.0;
                    }
                } else {
                    shiftValue = -shiftValue;
                    System.arraycopy(rvec, shiftValue, rvec, 0, size - shiftValue);
                    System.arraycopy(ivec, shiftValue, ivec, 0, size - shiftValue);
                    for (int i = 0; i < shiftValue; i++) {
                        rvec[size - shiftValue + i] = 0.0;
                        ivec[size - shiftValue + i] = 0.0;
                    }
                }
            } else if (shiftValue > 0) {
                System.arraycopy(rvec, 0, rvec, shiftValue, size - shiftValue);
                for (int i = 0; i < shiftValue; i++) {
                    rvec[i] = 0.0;
                }
            } else {
                shiftValue = -shiftValue;
                System.arraycopy(rvec, shiftValue, rvec, 0, size - shiftValue);
                for (int i = 0; i < shiftValue; i++) {
                    rvec[size - shiftValue + i] = 0.0;
                }
            }
        }
    }

    /**
     * Construct row n of Pascal's triangle in
     *
     * @param a   array to store result in
     * @param row the row to calculate
     */
    public static void pascalrow(double[] a, int row) {
        int i, j;
        for (j = 0; j <= row; j++) {
            a[j] = 0;
        }
        a[0] = 1;
        for (j = 1; j <= row; j++) {
            for (i = row; i >= 1; i--) {
                a[i] = a[i] - a[i - 1];
            }
        }
    }

    /**
     * Calculate the value of a Lorentzian lineshape function
     *
     * @param x    the frequency position
     * @param b    the linewidth
     * @param freq the frequency
     * @return the value at x
     */
    public static double lShape(double x, double b, double freq) {
        b *= 0.5;
        return (1.0 / Math.PI) * b / ((b * b) + ((x - freq) * (x - freq)));
    }

    static double[][] fillMatrix(final double[] f, final double[] d, final int nRows) {
        int nCols = f.length;
        double[][] A = new double[nRows][nCols];
        int iCol = 0;
        for (int iSig = 0; iSig < nCols; iSig++) {
            for (int j = 0; j < nRows; j++) {
                double yTemp = lShape(j, d[iSig], f[iSig]);
                A[j][iSig] = yTemp;
            }
        }
        return A;
    }

    /**
     * Fill a vector with Lorentzian lineshapes as specified in signals list
     *
     * @param signals        the list of signal objects
     * @param signalInPoints if true the frequency and decay in signals are in
     *                       data points, otherwise they are in ppm and Hz.
     * @return this vector
     */
    public Vec fillVec(List<? extends Signal> signals, boolean signalInPoints) {
        makeReal();
        zeros();

        int nWidths = 40;
        signals.stream().forEach((signal) -> {
            double d = signal.decay;
            double f = signal.frequency;
            if (!signalInPoints) {
                d = d / getSW() * getSize();
                f = refToPtD(f);
            }
            double a = signal.amplitude;
            int start = (int) Math.round(f - nWidths / 2 * d);
            int end = (int) Math.round(f + nWidths / 2 * d);
            if (start < 0) {
                start = 0;
            }
            if (end > (size - 1)) {
                end = size - 1;
            }
            for (int j = start; j <= end; j++) {
                double yTemp = a * lShape(j, d, f);
                add(j, yTemp);
            }
        });
        return this;
    }

    static double[] fillVec(double[] x, int vecSize, ArrayList<Signal> signals) {
        for (int j = 0; j < vecSize; j++) {
            x[j] = 0.0;
        }
        int nWidths = 40;
        signals.stream().forEach((signal) -> {
            double d = signal.decay;
            double f = signal.frequency;
            double a = signal.amplitude;
            int start = (int) Math.round(f - nWidths / 2 * d);
            int end = (int) Math.round(f + nWidths / 2 * d);
            if (start < 0) {
                start = 0;
            }
            if (end > (vecSize - 1)) {
                end = vecSize - 1;
            }
            for (int j = start; j <= end; j++) {
                double yTemp = a * lShape(j, d, f);
                x[j] += yTemp;
            }
        });
        return x;
    }

    public static class OptimizeLineWidth implements UnivariateFunction {

        final double[] signal;
        final Complex[] fd;
        final int[] useColumns;

        public OptimizeLineWidth(final double[] signal, final Complex[] fd, final int[] useColumns) {
            this.signal = signal;
            this.fd = fd;
            this.useColumns = useColumns;
        }

        @Override
        public double value(final double x) {
            AmplitudeFitResult afR = fitAmplitudes(signal, fd, useColumns, signal.length, true, x);
            return afR.getRss();
        }
    }

    /**
     * Find amplitudes that optimize the fit of signals to an array of
     * intensities.
     *
     * @param x            The array of intensities
     * @param fd           A complex array whose values represent frequency and decay rate
     * @param useColumns   Only use signals whose indexed value is set to true in
     *                     this array
     * @param winSize      Size of window frequencies came from
     * @param uniformWidth If true use the same linewidth for all frequencies
     * @param lineWidth    If uniformWidth is true, use this line width
     * @return an AmplitudeFitResult with amplitudes and quality measures
     */
    public static AmplitudeFitResult fitAmplitudes(final double[] x, final Complex[] fd, final int[] useColumns, final int winSize, final boolean uniformWidth, final double lineWidth) {
        int nCols = 0;
        for (int j = 0; j < fd.length; j++) {
            if (useColumns[j] != -1) {
                nCols++;
            }
        }
        double[] f = new double[nCols];
        double[] d = new double[nCols];
        int iSig = 0;
        for (int j = 0; j < fd.length; j++) {
            if (useColumns[j] != -1) {
                Complex zFD = fd[j];
                double fR = -Math.atan2(zFD.getImaginary(), zFD.getReal());
                double fPoints = (winSize * (Math.PI - fR)) / (2 * Math.PI);
                f[iSig] = fPoints;
                if (uniformWidth) {
                    d[iSig] = lineWidth;
                } else {
                    d[iSig] = -1.0 * Math.log(zFD.abs()) * winSize / Math.PI;
                }
                iSig++;
            }
        }

        RealMatrix AR = new Array2DRowRealMatrix(fillMatrix(f, d, winSize));
        RealMatrix BR = new Array2DRowRealMatrix(AR.getRowDimension(), 1);
        for (int i = 0; i < winSize; i++) {
            BR.setEntry(i, 0, x[i]);
        }
        RealMatrix redAR = AR.copy();
        return nnlsFit(redAR, BR.copy());
    }

    /**
     * Continuous wavelet derivative
     *
     * @param winSize size of window to use
     * @return this vector
     */
    public Vec cwtd(int winSize) {
        if (isComplex()) {
            // fixme check for apache mode
            cwtd(cvec, size, winSize);
        } else {
            cwtd(rvec, size, winSize);
        }
        return this;
    }

    static void cwtd(Object vecObject, int size, int winSize) {
        boolean complex = false;
        double[] vec = null;
        Complex[] cvec = null;
        if (vecObject instanceof double[]) {
            vec = (double[]) vecObject;
        } else if (vecObject instanceof Complex[]) {
            cvec = (Complex[]) vecObject;
            complex = true;
        }

        double[] reVec = new double[size];
        double[] imVec = new double[size];

        double reSum;
        double imSum;
        int halfWin = winSize / 2;
        double scaleCorr = 1.0 / Math.sqrt(winSize);

        for (int i = 0; i < size; i++) {
            reSum = 0.0;
            imSum = 0.0;
            int max = (i + winSize);
            if (max > (size - 1)) {
                max = size - 1;
            }
            for (int j = i; j <= max; j++) {
                int dIJ = (j - i);
                double psi = 0.0;
                if (dIJ >= 0) {
                    if (dIJ < halfWin) {
                        psi = 1.0;
                    } else if (dIJ < winSize) {
                        psi = -1.0;
                    }
                }
                if (complex) {
                    reSum += cvec[j].getReal() * psi;
                    imSum += cvec[j].getImaginary() * psi;
                } else {
                    reSum += vec[j] * psi;
                }
            }
            if (complex) {
                reVec[i] = reSum * scaleCorr;
                imVec[i] = imSum * scaleCorr;
            } else {
                reVec[i] = reSum * scaleCorr;
            }
        }
        if (complex) {
            for (int i = 0; i < halfWin; i++) {
                cvec[i] = Complex.ZERO;
            }
            for (int i = halfWin; i < size; i++) {
                cvec[i] = new Complex(reVec[i - halfWin], imVec[i - halfWin]);
            }
        } else {
            for (int i = 0; i < halfWin; i++) {
                vec[i] = 0.0;
            }
            if (size - halfWin >= 0) {
                System.arraycopy(reVec, halfWin, vec, halfWin, size - halfWin);
            }
        }
    }

    /**
     * Integrate this vector over the specified range
     *
     * @param first starting point of range
     * @param last  ending point of range
     */
    public void integrate(int first, int last) {
        integrate(first, last, 0.0, 0.0);
    }

    /**
     * Integrate this vector over the specified range. Subtract a linear range
     * of values between start and end. The values in vector are replaced with
     * their integral.
     *
     * @param first          starting point of range
     * @param last           ending point of range
     * @param firstIntensity Starting value for linear baseline
     * @param lastIntensity  Ending value for linear baseline
     */
    public void integrate(int first, int last, double firstIntensity, double lastIntensity) {
        if (first < 0) {
            first = 0;
        }

        if (first >= size) {
            first = size - 1;
        }

        if (last < 0) {
            last = 0;
        }

        if (last >= size) {
            last = size - 1;
        }

        if (first > last) {
            first = last;
        }
        if (last > first) {
            double offset = firstIntensity;
            double delta = (lastIntensity - firstIntensity) / (last - first);
            if (!isComplex) {
                rvec[first] -= offset;
                for (int i = first; i < last; i++) {
                    offset += delta;
                    rvec[i + 1] += rvec[i] - offset;
                }
            } else {
                makeApache();
                cvec[first] = cvec[first].subtract(offset);
                for (int i = first; i < last; i++) {
                    offset += delta;
                    cvec[i + 1] = cvec[i + 1].add(cvec[i].subtract(offset));
                }
            }
        }
    }

    /**
     * Identify signal regions.
     *
     * @param winSize      Size of window used in assessing standard deviation
     * @param ratio        Threshold ratio of intensities to noise
     * @param regionWidth  Minimum width for regions
     * @param joinWidth    Regions are joined if separation is less than this
     * @param extend       Increase width of region edges by this amount
     * @param minThreshold Threshold is the larger of this and ratio times noise
     * @return matrix of results. Each row is a region. Columns are the start,
     * end and mean intensity.
     * @throws IllegalArgumentException if real value array is null
     */
    public RealMatrix idIntegrals(int winSize, double ratio,
                                  int regionWidth, int joinWidth, int extend, double minThreshold)
            throws IllegalArgumentException {
        double sumsq;
        double rmsd;
        double dev;
        int nRegions;

        if (rvec == null) {
            throw new IllegalArgumentException("idintegrals: no data in vector");
        }

        if (isComplex()) {
            makeReal();
        }

        int m = size;

        nRegions = (m) / winSize;
        if ((nRegions * winSize) < m) {
            nRegions++;
        }
        double[] reVec = new double[nRegions];
        double[] sdVec = new double[nRegions];

        /* Calculate means of each window */
        int k = 0;
        double reSum;
        double maxValue = Double.NEGATIVE_INFINITY;

        for (int j = 0; j < nRegions; j++) {
            reSum = 0.0;
            int pointsInRegion = 0;
            for (int i = 0; ((i < winSize) && (k < size)); i++) {
                reSum += rvec[k];
                pointsInRegion++;
                if (rvec[k] > maxValue) {
                    maxValue = rvec[k];
                }

                k++;
            }

            reVec[j] = reSum / pointsInRegion;
        }

        /* Form centered vector and calculate st. dev. for window */
        k = 0;

        for (int j = 0; j < nRegions; j++) {
            sumsq = 0.0;
            int pointsInRegion = 0;

            for (int i = 0; ((i < winSize) && (k < size)); i++) {
                dev = rvec[k] - reVec[j];
                pointsInRegion++;
                sumsq += (dev * dev);
                k++;
            }

            sdVec[j] = Math.sqrt(sumsq / pointsInRegion);
        }
        /* Estimate standard deviation from sorted vector */
        Arrays.sort(sdVec);

        // If possible, skip first region (and any near zero in value) to avoid some spectra that have an artificially low value at edge
        rmsd = sdVec[0];

        double threshold = maxValue * 1.0e-8;
        int j = 0;

        if (nRegions > 16) {
            while (j < (nRegions - 2)) {
                if (sdVec[j + 2] > threshold) {
                    rmsd = sdVec[j + 2];
                    break;
                }

                j++;
            }
        }

        /* Identify Baseline regions */
        int nPeakRegions = 0;
        boolean lastWasBase = false;
        threshold = rmsd * ratio;
        if ((minThreshold > 0.0) && (threshold < minThreshold)) {
            threshold = minThreshold;
        }
        DescriptiveStatistics dStat = new DescriptiveStatistics(winSize + 1);
        int halfWin = winSize / 2;
        for (int i = 0; i < halfWin; i++) {
            dStat.addValue(rvec[i]);
            dStat.addValue(rvec[size - i - 1]);
        }
        double regionAvg = dStat.getMean();
        for (int i = 0; i < size; i++) {
            if (Math.abs(rvec[i] - regionAvg) < threshold) {
                lastWasBase = true;
            } else {
                if (lastWasBase) {
                    nPeakRegions++;
                }

                lastWasBase = false;
            }
        }

        int iIntRegion;
        lastWasBase = true;
        RealMatrix xyVals = new Array2DRowRealMatrix(nPeakRegions, 3);
        iIntRegion = -1;

        int begin = 0;
        int end = joinWidth - 1;
        boolean lastNarrow = false;
        double regionSum = 0.0;
        int nPoints = 0;
        int nPos = 0;
        int nNeg = 0;
        boolean addedRegion = false;
        for (int i = 0; i < size; i++) {
            if (Math.abs(rvec[i] - regionAvg) < threshold) { // baseline point
                if (!lastWasBase) {
                    lastNarrow = addedRegion && ((i - begin) < regionWidth);
                }
                addedRegion = false;
                lastWasBase = true;
            } else { // potential integral region point
                if (rvec[i] < 0) {
                    nNeg++;
                } else {
                    nPos++;
                }

                if (lastWasBase) {
                    if ((i - end) > joinWidth) { // start new region
                        regionSum = 0.0;
                        nPoints = 0;
                        begin = i - extend;

                        if (begin < 0) {
                            begin = 0;
                        }

                        if ((iIntRegion >= 0) && (begin <= (end + 1))) { // if regions overlap (but not within joinwidth then
                            xyVals.setEntry(iIntRegion, 1, ((end + begin) / 2) - 1); // put dividing point half way between them
                            begin = ((end + begin) / 2) + 1;
                        }

                        if (!lastNarrow) { // if too narrow reuse last xyVals slot for next region
                            iIntRegion++;
                            addedRegion = true;
                        }

                        if (iIntRegion >= 0) {
                            xyVals.setEntry(iIntRegion, 0, begin);
                        }
                        nNeg = 0;
                        nPos = 0;
                    }
                }

                regionSum += rvec[i];
                nPoints++;
                end = i + extend;

                if (end >= size) {
                    end = size - 1;
                }

                if (iIntRegion >= 0) {
                    xyVals.setEntry(iIntRegion, 1, end);
                    xyVals.setEntry(iIntRegion, 2, regionSum / nPoints);
                    if ((nPos > regionWidth) && (nNeg > regionWidth)) {
                        if (((1.0 * Math.abs(nPos - nNeg)) / (nPos + nNeg)) < 0.2) {
                            xyVals.setEntry(iIntRegion, 2, 0.0);
                        }
                    }
                }

                lastWasBase = false;
            }
        }

        RealMatrix xyValsFinal = new Array2DRowRealMatrix(iIntRegion + 1, 3);

        for (int i = 0; i <= iIntRegion; i++) {
            xyValsFinal.setEntry(i, 0, xyVals.getEntry(i, 0));
            xyValsFinal.setEntry(i, 1, xyVals.getEntry(i, 1));
            xyValsFinal.setEntry(i, 2, xyVals.getEntry(i, 2));
        }

        return xyValsFinal;
    }

    /**
     * Reference deconvolution
     *
     * @param ref Reference signal
     * @param exp target decay
     * @return this vector
     * @throws IllegalArgumentException if vectors aren't all the same size and
     *                                  complex
     */
    public Vec deconv(Vec ref, Vec exp)
            throws IllegalArgumentException {
        if ((size != ref.size) && (size != exp.size)) {
            throw new IllegalArgumentException("deconv:  vectors must all be same size");
        }

        if (!isComplex || !ref.isComplex || !exp.isComplex) {
            throw new IllegalArgumentException("deconv:  vectors must all be complex");
        }
        for (int i = 0; i < size; i++) {
            Complex c = new Complex(getReal(i), getImag(i));
            Complex cR = new Complex(ref.getReal(i), ref.getImag(i));
            Complex cE = new Complex(exp.getReal(i), exp.getImag(i));
            Complex c0 = cR.divide(cE);
            c = c.multiply(c0);
            set(i, new Complex(c.getReal(), c.getImaginary()));
        }

        return (this);
    }

    public Vec convolveLorentzian(double lw, double mult) {
        double ptWidth = getSize() / getSW() * lw;
        int halfWidth = (int) (ptWidth * mult / 2.0);
        double[] shape = new double[halfWidth * 2 + 1];

        for (int j=0, i = -halfWidth;i <= halfWidth;i++) {
            shape[j++] = LineShapes.LORENTZIAN.calculate(i, 1.0, 0, ptWidth);
        }
        return convolveSame(shape);
    }
    /**
     * Calculates convolution where output length equals signal length.
     *
     * @param impulse The impulse response array (length M)
     * @return this Vec after convolution
     */
    public Vec convolveSame(double[] impulse) {
        if (isComplex()) {
            throw new VecException("esmooth: vector complex");
        }
        int m = impulse.length;
        double[] result = new double[size];

        // Calculate the offset to center the impulse response
        // This effectively "skips" the edges of the full convolution
        int offset = (m - 1) / 2;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < m; j++) {
                // Determine the corresponding index in a "full" convolution
                int fullIdx = i + j - offset;

                // Only add to result if the index falls within the signal's bounds
                if (fullIdx >= 0 && fullIdx < size) {
                    result[fullIdx] += rvec[i] * impulse[j];
                }
            }
        }
        System.arraycopy(result, 0, rvec, 0, size);
        return this;
    }

    /**
     * Check vector for large value as a test for artifacts.
     *
     * @param limit threshold
     * @return true if any value larger than limit
     */
    public boolean checkExtreme(double limit) {
        boolean result = false;
        if (isComplex) {
            for (int i = 0; i < size; i++) {
                if (FastMath.abs(getReal(i)) > limit) {
                    System.out.println(i + " extreme r " + getReal(i));
                    printLocation();
                    result = true;
                    break;
                }
                if (FastMath.abs(getImag(i)) > limit) {
                    System.out.println(i + " extreme i " + getImag(i));
                    printLocation();
                    result = true;
                    break;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (FastMath.abs(getReal(i)) > limit) {
                    System.out.println(i + " extreme r " + getReal(i));
                    printLocation();
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    public void max(Vec vec) {
        for (int i = 0; i < size; i++) {
            setReal(i, Math.max(getReal(i), vec.getReal(i)));
        }
    }

    public byte[] toFloatBytes() {
        return toFloatBytes(ByteOrder.BIG_ENDIAN);
    }

    public byte[] toFloatBytes(ByteOrder byteOrder) {
        int nBytes = size * (isComplex ? 2 : 1) * Float.BYTES;
        byte[] array = new byte[nBytes];
        FloatBuffer buffer = ByteBuffer.wrap(array).order(byteOrder).asFloatBuffer();
        for (int i = 0; i < size; i++) {
            buffer.put((float) getReal(i));
            if (isComplex) {
                buffer.put((float) getImag(i));
            }
        }
        return array;
    }
}
