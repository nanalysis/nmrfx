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

import static org.nmrfx.processor.math.VecUtil.nnlsFit;
import org.nmrfx.processor.operations.Util;
import org.nmrfx.processor.operations.TestBasePoints;
import org.nmrfx.processor.math.units.*;
import java.util.ArrayList;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.processing.SampleSchedule;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.complex.ComplexUtils;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.UncorrelatedRandomVectorGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.ArithmeticUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.ResizableDoubleArray;
import org.python.core.Py;
import org.python.core.PyComplex;
import org.python.core.PyObject;
import org.python.core.PySequence;
import org.python.core.PyType;
import org.renjin.sexp.AttributeMap;

/**
 * A class for representing vectors of data (typically for NMR). The data is stored as real or complex values. If
 * complex, the data can be stored in two formats. In the first, the real values are stored in one array of doubles and
 * the imaginary in a second array of doubles. In the second format the complex values are stored in an array of Complex
 * objects. The storage arrays can be resized and may have a capacity larger than the number of valid data values they
 * contain. Because of this the user must always pay attention to the size field which indicates the number of valid
 * points.
 *
 * The class extends the Jython PySequence class which allows it to be used in basic Python operations (addition,
 * subtraction etc.).
 *
 * @author michael
 */
public class Vec extends PySequence implements MatrixType {

    static GaussianRandomGenerator randGen = new GaussianRandomGenerator(new Well19937c());

    public static final String TYPE_NAME = "nmrfxvector";

    protected AttributeMap attributes;

    String name = "";

    /**
     * Array of doubles used for storing data when the Vec is real or the real part of complex data when a Complex array
     * is not used
     */
    public double[] rvec;

    /**
     * Array of doubles used for storing imaginary values when the Vec is Complex and a Complex array is not used
     */
    public double[] ivec;
    /**
     *
     */
    public Complex[] cvec;

    // number of valid data values in arrays.
    private int size;
    // original size of time domain data (need to keep track of this for undoing zero filling)
    private int tdSize;
    /**
     * Does this vector have an imaginary part? If true, ivec or cvec exist.
     */
    private boolean isComplex;

    /**
     * Flag of whether to use cvec or rvec/ivec when the Vec is complex.
     */
    private boolean useApache;
    private boolean freqDomain;
    /**
     * Location in dataset that the vector was read from, or should be written to.
     */
    private int[][] pt;

    /**
     * Dimensions in dataset that the vector was read from, or should be written to.
     */
    private int[] dim;

    /**
     *
     */
    public double dwellTime = 1.0;

    /**
     *
     */
    public double centerFreq = 1.0;

    /**
     *
     */
    public double refValue = 0.0;
    private double ph0 = 0.0;
    private double ph1 = 0.0;
    private double groupDelay = 0.0;
    private boolean[] inSignalRegion = null;
    private double[] annotationData = null;
    private int zfSize;
    private int extFirst;
    private int extLast;

    /**
     * Sample schedule that applies to this vector when NUS data acquisition was used.
     */
    public SampleSchedule schedule = null;
    private static Map<String, Vec> vecMap = new HashMap<>();

    /**
     *
     */
    public static final PyType ATYPE = PyType.fromClass(Vec.class);

    /**
     * Create a new named Vec object with the specified size and complex mode.
     *
     * @param name Name of the vector. Used for retrieving vectors by name.
     * @param size Size of vector.
     * @param complex true if the data stored in vector is Complex
     */
    private Vec(int size, String name, boolean complex) {
        this(size, complex);
        this.name = name;
        this.attributes = AttributeMap.EMPTY;
    }

    /**
     * Create a new Vec object with the specified size and complex mode.
     *
     * @param size Size of vector.
     * @param complex true if the data stored in vector is Complex
     */
    public Vec(int size, boolean complex) {
        super(ATYPE);
        this.attributes = AttributeMap.EMPTY;
        this.isComplex = complex;
        useApache = true;
        rvec = new double[size];
        freqDomain = false;

        resize(size, complex);
        tdSize = size;
    }

    /**
     * Create a new Vec object for real data and with the specified size.
     *
     * @param size Size of vector.
     */
    public Vec(int size) {
        this(size, false);
    }

    public Vec(double[] values) {
        this(values.length, false);
        System.arraycopy(values, 0, rvec, 0, values.length);
    }

    /**
     * Create a new Vec object for real data and with the specified size and specified dataset location.
     *
     * @param size Size of vector.
     * @param pt dataset location
     */
    public Vec(int size, int[][] pt, int[] dim) {
        this(size);
        if (pt != null) {
            this.pt = new int[pt.length][2];
            for (int i = 0; i < pt.length; i++) {
                this.pt[i][0] = pt[i][0];
                this.pt[i][1] = pt[i][1];
            }
        }
        if (dim != null) {
            this.dim = new int[dim.length];
            System.arraycopy(dim, 0, this.dim, 0, dim.length);
        }

    }

    /**
     * Create a new Vec object with the specified size, complex mode and dataset location
     *
     * @param size Size of vector.
     * @param pt dataset location
     * @param complex true if vector stores complex data
     */
    public Vec(int size, int[][] pt, int[] dim, boolean complex) {
        this(size, complex);
        if (pt != null) {
            this.pt = new int[pt.length][2];
            for (int i = 0; i < pt.length; i++) {
                this.pt[i][0] = pt[i][0];
                this.pt[i][1] = pt[i][1];
            }
        }
        if (dim != null) {
            this.dim = new int[dim.length];
            System.arraycopy(dim, 0, this.dim, 0, dim.length);
        }
    }

    /**
     * Create a vector with the specified name, size and complex mode and store it in a Map of Vec objects.
     *
     * @param size Size of vector.
     * @param name name of vector
     * @param complex true if vector stores complex data
     * @return new Vec object
     */
    public static final Vec createNamedVector(int size, String name, boolean complex) {
        Vec vec = new Vec(size, name, complex);
        vecMap.put(name, vec);
        return vec;
    }

    @Override
    protected PyObject pyget(int i) {
        if (isComplex) {
            return new PyComplex(getReal(i), getImag(i));
        } else {
            return Py.newFloat(getReal(i));
        }
    }

    @Override
    protected PyObject getslice(int start, int stop, int step) {
        // fixme this (whole getslice method) is probably not yet correct
        System.out.println(start + " " + stop + " " + step + " " + getSize());
        if (start < 0) {
            start = 0;
        }
        if (stop > getSize()) {
            stop = getSize();
        }
        if (step <= 0) {
            step = 1;
        }
        int newSize = (stop - start) / step;

        Vec vecNew = new Vec(newSize, this.isComplex);
        if (isComplex) {
            for (int i = 0; i < newSize; i += step) {
                vecNew.set(i, this.getComplex(i + start));
            }
        } else {
            for (int i = 0; i < newSize; i += step) {
                vecNew.set(i, this.getReal(i + start));
            }

        }

        return vecNew;

    }

    @Override
    protected PyObject repeat(int i) {
        throw Py.TypeError("can't apply '*' to Vec");
    }

    /**
     * Get array values as a list of PyObject elements
     *
     * @return the list of values
     */
    public ArrayList<PyObject> getList() {
        ArrayList<PyObject> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(pyget(i));
        }
        return result;
    }

    /**
     * Get array values as a list of PyComplex elements
     *
     * @return list of complex values
     */
    public ArrayList<PyComplex> getComplexList() {
        ArrayList<PyComplex> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Complex c = getComplex(i);
            result.add(new PyComplex(c.getReal(), c.getImaginary()));
        }
        return result;
    }

    /**
     * Get array values as a list of Complex (Apache Commons Math) elements
     *
     * @return list of complex values
     */
    public ArrayList<Complex> getApacheComplexList() {
        ArrayList<Complex> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Complex c = getComplex(i);
            result.add(c);
        }
        return result;
    }

    /**
     * Objects of this class store an index and value. Typically used for getting the location and value of the maximum
     * or minimum in the vector.
     */
    public class IndexValue {

        final int index;
        final double value;

        IndexValue(int index, double value) {
            this.index = index;
            this.value = value;
        }

        /**
         * Return the value
         *
         * @return the value
         */
        public double getValue() {
            return value;
        }

        /**
         * Return the index at which the value was obtained.
         *
         * @return the index
         */
        public int getIndex() {
            return index;
        }
    }

    /**
     * Return a vector from the map of named and stored vectors
     *
     * @param name lookup vector with this name
     * @return vector with the specified name (or null if it doesn't exist)
     */
    public static Vec get(String name) {
        return vecMap.get(name);
    }

    /**
     * Remove a vector (if present) from the map of named and stored vectors
     *
     * @param name lookup vector with this name
     * @return true if a vector with that name existed
     */
    public static boolean remove(String name) {
        return vecMap.remove(name) != null;
    }

    /**
     * Return a list of names of stored vectors.
     *
     * @return the list of names
     */
    public static ArrayList<String> getVectorNames() {
        ArrayList<String> names = new ArrayList<>();
        names.addAll(vecMap.keySet());
        return names;
    }

    /**
     * Set the name of this vector.
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return the name of this vector
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Return the zeroth order phase value that has been applied to this vector.
     *
     * @return the phase value
     */
    public double getPH0() {
        return ph0;
    }

    /**
     * Return the first order phase value that has been applied to this vector.
     *
     * @return the phase value
     */
    public double getPH1() {
        return ph1;
    }

    /**
     * Set the zeroth order phase correction that has been applied to this vector.
     *
     * @param p0 the phase value
     */
    public void setPh0(double p0) {
        ph0 = p0;
    }

    /**
     * Set the first order phase correction that has been applied to this vector.
     *
     * @param p1 the phase value
     */
    public void setPh1(double p1) {
        ph1 = p1;
    }

    /**
     * Return the dwell time for this vector
     *
     * @return the dwell time
     */
    public double getDwellTime() {
        return dwellTime;
    }

    /**
     * Set the dwell time for this vector ( 1.0 / sweepwidth)
     *
     * @param value the dwell time
     */
    public void setDwellTime(double value) {
        dwellTime = value;
    }

    /**
     * Set the sweep width for this vector (1.0 / dwellTime)
     *
     * @param value the sweep width
     */
    public void setSW(double value) {
        dwellTime = 1.0 / value;
    }

    /**
     * Return the sweep width for this vector
     *
     * @return the sweep width
     */
    public double getSW() {
        return 1.0 / dwellTime;
    }

    /**
     * Set the spectrometer frequency for this vector
     *
     * @param value the spectrometer frequency
     */
    public void setSF(double value) {
        centerFreq = value;
    }

    /**
     * Return the spectrometer frequency
     *
     * @return the spectrometer frequency
     */
    public double getSF() {
        return centerFreq;
    }

    /**
     * Print the location value (for reading/writing to datasets) for this vector if it is set
     */
    public void printLocation() {
        if (pt != null) {
            for (int[] pt1 : pt) {
                System.out.print(pt1[0] + " " + pt1[1] + " ");
            }
            System.out.println("");
        }
    }

    public int getIndex() {
        int index = 0;
        if ((pt != null) && (pt.length > 1) && (pt[1] != null)) {
            index = pt[1][0];
        }
        return index;
    }

    /**
     * Copy the dataset location of this vector to that of another vector
     *
     * @param target copy location to this vector
     */
    public void copyLocation(Vec target) {
        copyLocation(this, target);
    }

    /**
     * Copy the dataset location of one vector to that of another vector
     *
     * @param inVec source vector
     * @param outVec target vector
     */
    static public void copyLocation(Vec inVec, Vec outVec) {
        if (inVec.pt != null) {
            outVec.pt = new int[inVec.pt.length][2];
            for (int i = 0; i < inVec.pt.length; i++) {
                outVec.pt[i][0] = inVec.pt[i][0];
                outVec.pt[i][1] = inVec.pt[i][1];
            }
        }
        if (inVec.dim != null) {
            outVec.dim = new int[inVec.dim.length];
            System.arraycopy(inVec.dim, 0, outVec.dim, 0, inVec.dim.length);
        }
    }

    /**
     * Resize a vector and set the complex mode. Existing data will be preserved (up to smaller of new and old sizes).
     *
     * @param newSize the new size of vector
     * @param complex true if the vector should be complex
     */
    public final void resize(int newSize, boolean complex) {
        isComplex = complex;
        resize(newSize);
    }

    /**
     * Get array of boolean values indicating whether each point is signal or baseline. True values indicate that the
     * corresponding point is signal.
     *
     * @return boolean array with true values for signals.
     */
    public boolean[] getSignalRegion() {
        return inSignalRegion;
    }

    /**
     * Set the signal regions. Signal regions are typically used by the baseline correction algorithms.
     *
     * @param region boolean array where true values indicate that point is signal.
     */
    public void setSignalRegion(boolean[] region) {
        inSignalRegion = region;
    }

    /**
     * Get array of double values that can be used for drawing a line. Often used for displaying apodization.
     *
     * @return double array with values.
     */
    public double[] getAnnotation() {
        return annotationData;
    }

    /**
     * Set the annotation values. Values are typically used for display of annotation.
     *
     * @param region double array.
     */
    public void setAnnotation(double[] data) {
        if ((annotationData == null) || (annotationData.length != data.length)) {
            annotationData = new double[data.length];
        }
        System.arraycopy(data, 0, annotationData, 0, data.length);
    }

    /**
     * Copy one complex array to another. Number of values copies is the smaller of the two vector sizes. The target
     * vector is not resized.
     *
     * @param source the source array
     * @param target the target array
     */
    public static void complexCopy(Complex[] source, Complex[] target) {
        int csize = source.length;
        if (target.length < csize) {
            csize = target.length;
        }
        System.arraycopy(source, 0, target, 0, csize);
    }

    // fixme what the he?? does this do
    /**
     *
     * @param orig array to check
     * @return original array
     */
    public static Complex[] arrayCheckPowerOfTwo(Complex[] orig) {
        int asize = orig.length;
        if (!ArithmeticUtils.isPowerOfTwo(asize)) {
            int n = 1;
            while (asize > n) {
                n *= 2;
            }
            Complex[] copy = new Complex[n];
            System.arraycopy(orig, 0, copy, 0, asize);
            System.arraycopy(copy, 0, orig, 0, asize);  // seems a little silly
        }
        return orig;
    }

    /**
     * Copy contents of this vector to another vector. The target will be resized and the complex mode changed to agree
     * with this vector.
     *
     * @param target the target vector
     */
    public void copy(Vec target) {
        target.resize(size, isComplex);

        if (isComplex) {
            target.makeComplex();
            if (useApache) {
                target.makeApache();
                for (int i = 0; i < size; ++i) {
                    target.set(i, getComplex(i));
                }
            } else {
                for (int i = 0; i < size; ++i) {
                    target.set(i, getReal(i), getImag(i));
                }
            }
        } else {
            for (int i = 0; i < size; ++i) {
                target.set(i, getReal(i));
            }
        }
        copyRef(this, target);
    }

    /**
     * Copy a portion of one vector to another vector.
     *
     * @param target the target vector
     * @param start copy starting at this index
     * @param length copy this number of values
     */
    public void copy(Vec target, int start, int length) {
        copy(target, start, 0, length);

    }

    /**
     * Copy portion of one vector to another
     *
     * @param target the target vector
     * @param start copy starting at this index
     * @param destPos starting position in target vector
     * @param length copy this number of values
     */
    public void copy(Vec target, int start, int destPos, int length) {
        int reqSize = destPos + length;
        if (reqSize > target.size) {
            target.resize(length + destPos, isComplex);
        }

        if (isComplex) {
            target.makeComplex();
            if (useApache) {
                target.makeApache();
                for (int i = 0; i < length; ++i) {
                    target.set(i + destPos, getComplex(i + start));
                }
            } else {
                for (int i = 0; i < length; ++i) {
                    target.set(i + destPos, getReal(i + start), getImag(i + start));
                }
            }
        } else {
            for (int i = 0; i < length; ++i) {
                target.set(i + destPos, getReal(i + start));
            }
        }
        copyRef(this, target);
    }

    /**
     * Copy the reference information from this vector to another vector.
     *
     * @param target the target vector
     */
    public void copyRef(Vec target) {
        copyRef(this, target);
    }

    /**
     * Copy the reference information from one vector to another vector.
     *
     * @param source the source vector
     * @param target the target vector
     */
    static public void copyRef(Vec source, Vec target) {
        target.dwellTime = source.dwellTime;
        target.centerFreq = source.centerFreq;
        target.refValue = source.refValue;
        target.freqDomain = source.freqDomain;
        target.ph0 = source.ph0;
        target.ph1 = source.ph1;
        target.groupDelay = source.groupDelay;
        target.zfSize = source.zfSize;
        target.tdSize = source.tdSize;
        target.extFirst = source.extFirst;
        target.extLast = source.extLast;
        if (source.inSignalRegion != null) {
            target.inSignalRegion = source.inSignalRegion.clone();
        } else {
            target.inSignalRegion = null;
        }
        copyLocation(source, target);
    }

    /**
     * Adjust the reference value because the vector was resized and/or points at beginning removed
     *
     * @param shift the starting position of new range.
     * @param newSize the new size of the vector
     */
    public void adjustRef(double shift, int newSize) {
        refValue -= ((shift / (dwellTime * centerFreq)) / ((double) size));
        dwellTime = (dwellTime * size) / ((double) newSize);
    }

    /**
     * Get start of "valid" data in vectors that have DSP "charge-up" at beginning. This value is calculated based on
     * the vectors stored groupDelay parameter.
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
     * Fix DSP charge-up with an HFT. FID is Fourier transformed, phased with specified zero order phase and calculated
     * (from group delay) first order phase, made real, and then a Hilbert transform is done to regenerate imaginary
     * values without the effect of charge-up. Finally, the spectrum is inverse transformed to return to time domain.
     *
     * @param phase apply this phase value
     */
    public void fixWithPhasedHFT(double phase) {
        if (groupDelay != 0.0) {
            int start = (int) (groupDelay + 0.5);
            Complex initPt = findBrukerInitialPoint(start);
            double p1 = initPt.getArgument() * 180 / Math.PI;
            phase += p1;
//            System.out.println("fix phase=" + phase);
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
     * @param amp amplitude of filter
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
//                System.out.println("fix phase=" + (initPt.getArgument() * 180 / Math.PI));
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
            double ph, amp = c.abs();
//            c = getComplex(start-2);  // start-2 within Bruker precharge
//            ph = c.getArgument();
            ph = 0;
// only even points in sync with 1st real point, 1st half of precharge
            int n = 0;
            for (int i = start - 2; i > start / 2; i -= 2) {
                c = getComplex(i);
                if (c.abs() > 0.0) {
                    ph += c.getArgument();
                    n++;
                }
            }
            ph /= n;
            c = ComplexUtils.polar2Complex(amp, ph);
        }
        return c;
    }

    /**
     * Scale first point. Useful for artifacts from initial time. Typically 0.5 for indirect dimensions.
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

    private void expandRvec(int newsize) {
        double[] newarr = new double[newsize];
        if (rvec == null) {
            rvec = newarr;
        } else {
            //copy rvec from 0 to size 
            //(because from size to rvec.length is junk data that we don't want)
            System.arraycopy(rvec, 0, newarr, 0, rvec.length);
        }
        rvec = newarr;
    }

    private void expandIvec(int newsize) {
        double[] newarr = new double[newsize];
        if (ivec == null) {
            ivec = newarr;
        } else {
            System.arraycopy(ivec, 0, newarr, 0, ivec.length);
        }
        ivec = newarr;
    }

    private void cexpand(int length) {
        Complex[] newarr = new Complex[length];
        if (cvec == null) {
            cvec = newarr;
            for (int i = 0; i < length; ++i) {
                cvec[i] = Complex.ZERO;
            }
        } else {
            System.arraycopy(cvec, 0, newarr, 0, cvec.length);
        }
        cvec = newarr;
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
     * Resize a vector. Original values smaller than new size are preserved.
     *
     * @param newsize the new size of the vector
     */
    public void resize(int newsize) {
        if (pt != null) {
            if (isComplex()) {
                pt[0][1] = newsize * 2 - 1;
            } else {
                pt[0][1] = newsize - 1;
            }
        }

        if (newsize > 0) {
            if (isComplex) {
                if (useApache) {
                    if ((cvec == null) || (cvec.length < newsize)) {
                        int oldsize = cvec != null ? cvec.length : 0;
                        cexpand(newsize);

                        this.zeros(oldsize, newsize - 1);
                    }
                } else if ((rvec == null) || (ivec == null)
                        || (rvec.length < newsize) || (ivec.length < newsize)) {
                    expandRvec(newsize);
                    expandIvec(newsize);
                }
            } else if ((rvec == null) || (rvec.length < newsize)) {
                expandRvec(newsize);
            }
        }

        /**
         * If we added points new points to the Vec that contain "junk" data, zero them.
         */
        if (size < newsize) {
            //this isn't an adequate way to know what's newly allocated
            this.zeros(size, newsize - 1);
        }
        this.size = newsize;
    }

    /**
     * Perform a Fast Fourier Transform (FFT) of the vector using the Apache Commons Math library.
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
     * Perform an inverse Fast Fourier Transform (FFT) of the specified complex data.
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
     * Add a real value v to the i'th value in the Vector and modify the value.
     *
     * @param i index of element
     * @param v value to add
     */
    public void add(int i, double v) {
        if (isComplex) {
            if (useApache) {
                cvec[i] = cvec[i].add(v);
            } else {
                rvec[i] += v;
            }
        } else {
            rvec[i] += v;
        }
    }

    /**
     * Add a real value to this vector.
     *
     * @param addValue the value to add
     * @return this vector
     */
    public Vec add(double addValue) {
        int i;

        if (isComplex) {
            for (i = 0; i < size; i++) {
                set(i, new Complex(getReal(i) + addValue, getImag(i)));
            }
        } else {
            for (i = 0; i < size; i++) {
                set(i, getReal(i) + addValue);
            }
        }

        return (this);
    }

    /**
     * Add a Complex value to this vector. The vector will be made complex if it is not already.
     *
     * @param addValue the value to add
     * @return this vector
     */
    public Vec add(Complex addValue) {
        int i;
        double real = addValue.getReal();
        double imag = addValue.getImaginary();
        if (!isComplex) {
            makeApache();
        }

        for (i = 0; i < size; i++) {
            set(i, new Complex(getReal(i) + real, getImag(i) + imag));
        }

        return (this);
    }

    /**
     * Add a vector to this vector. The vectors may be of different lengths. The number of values added will be the
     * smaller of the sizes of the two vectors
     *
     * @param v2 the vector to add to this vector
     */
    public void add(Vec v2) {
        int i;
        int sz = v2.getSize();
        sz = sz < size ? sz : size;
        if (isComplex) {
            if (useApache) {
                v2.makeApache();
                Complex cvec2[] = v2.getCvec();
                for (i = 0; i < sz; i++) {
                    cvec[i] = cvec[i].add(cvec2[i]);
                }
            } else {
                double rvec2[] = v2.getRvec();
                double ivec2[] = v2.getIvec();
                for (i = 0; i < sz; i++) {
                    rvec[i] += rvec2[i];
                    ivec[i] += ivec2[i];
                }
            }
        } else {
            double rvec2[] = v2.getRvec();
            for (i = 0; i < sz; i++) {
                rvec[i] += rvec2[i];
            }
        }
    }

    /**
     * Add an array of values to this vector. Values must implement Java Number interface. The number of values does not
     * have to equal the number of values between the start and end points in this vector. Interpolation of the values
     * to be added will be done to find the value to add at each point. The values can be multiplied by a scale value
     * before addition.
     *
     * @param addValue The values to add
     * @param start the starting point in this vector
     * @param end the ending point in this vector
     * @param scale multiply values to be added by this scale factor.
     * @param lb unused at present
     * @return this vector
     */
    public Vec add(Object[] addValue, final double start, final double end, final double scale, final double lb) {
        if ((addValue.length + Math.round(start)) > size) {
            throw new IllegalArgumentException("add array: too many values");
        }
        double[] values = new double[addValue.length];
        for (int i = 0; i < addValue.length; i++) {
            values[i] = ((Number) addValue[i]).doubleValue();
        }

        values = Interpolator.getInterpolated(values, start, end);
        int iStart = (int) Math.ceil(start);
        if (isComplex) {
            for (int i = 0; i < values.length; i++) {
                int j = i + iStart;
                set(j, new Complex(getReal(j) + values[i] * scale, getImag(j)));
            }
        } else {
            for (int i = 0; i < values.length; i++) {
                int j = i + iStart;
                set(j, values[i] * scale);
            }
        }

        return (this);
    }

    /**
     * Add a multiple of a vector to this vector
     *
     * @param avec the vector to add
     * @param scale multiply values to add by this amount
     * @return this vector
     */
    public Vec addmul(Vec avec, double scale) {

        if (isComplex) {
            addMulVector(this, size, avec, scale, this);
        } else {
            addMulVector(this.rvec, size, avec.rvec, scale, this.rvec);
        }

        return (this);
    }

    /**
     * Add a one array to a multiple of a second array and store in a third array
     *
     * @param avec first array
     * @param size number of values to add
     * @param bvec multiply these values by scale before adding to avec
     * @param scale factor to multiply by
     * @param cvec store result
     */
    public static void addMulVector(double[] avec, int size, double[] bvec, double scale, double[] cvec) {
        int i;

        for (i = 0; i < size; i++) {
            cvec[i] = avec[i] + bvec[i] * scale;
        }
    }

    /**
     * Add a one vector to a multiple of a second vector and store in a third vector
     *
     * @param avec first vector
     * @param size number of values to add
     * @param bvec multiply these values by scale before adding to avec
     * @param scale factor to multiply by
     * @param cvec store result
     */
    public static void addMulVector(Vec avec, int size, Vec bvec, double scale,
            Vec cvec) {
        int i;

        for (i = 0; i < size; i++) {
            double dReal = avec.getReal(i) + bvec.getReal(i) * scale;
            double dImaginary = avec.getImag(i) + bvec.getImag(i) * scale;
            cvec.set(i, new Complex(dReal, dImaginary));
        }
    }

    /**
     * Subtract real value from this vector
     *
     * @param subValue value to subtract
     * @return this vector
     */
    public Vec sub(double subValue) {
        int i;

        if (isComplex) {
            for (i = 0; i < size; i++) {
                set(i, new Complex(getReal(i) - subValue, getImag(i)));
            }
        } else {
            for (i = 0; i < size; i++) {
                set(i, getReal(i) - subValue);
            }
        }

        return (this);
    }

    /**
     * Subtract Complex value from this vector. Vector will be converted to Complex if it is not already.
     *
     * @param subValue value to subtract
     * @return this vector
     */
    public Vec sub(Complex subValue) {
        int i;
        double real = subValue.getReal();
        double imag = subValue.getImaginary();
        if (!isComplex) {
            makeApache();
        }

        for (i = 0; i < size; i++) {
            set(i, new Complex(getReal(i) - real, getImag(i) - imag));
        }

        return (this);
    }

    /**
     * Subtract a vector from current vector. Vectors may be different length.
     *
     * @param v2 Vector to subtract
     */
    public void sub(Vec v2) {
        int i;
        int sz = v2.getSize();
        sz = sz < size ? sz : size;
        if (isComplex) {
            if (useApache) {
                v2.makeApache();
                Complex cvec2[] = v2.getCvec();
                for (i = 0; i < sz; i++) {
                    cvec[i] = cvec[i].subtract(cvec2[i]);
                }
            } else {
                double rvec2[] = v2.getRvec();
                double ivec2[] = v2.getIvec();
                for (i = 0; i < sz; i++) {
                    rvec[i] -= rvec2[i];
                    ivec[i] -= ivec2[i];
                }
            }
        } else {
            double rvec2[] = v2.getRvec();
            for (i = 0; i < sz; i++) {
                rvec[i] -= rvec2[i];
            }
        }
    }

    /**
     * Divide this vector by a Complex value. If vector is not already Complex, it will be converted to Complex.
     *
     * @param divisor divide by this value
     * @return this vector
     */
    public Vec divide(Complex divisor) {
        if (!isComplex) {
            makeApache();
        }
        for (int i = 0; i < size; i++) {
            set(i, getComplex(i).divide(divisor));
        }

        return (this);
    }

    /**
     * Divide this vector by a real value
     *
     * @param divisor divide by this value
     * @return this vector
     */
    public Vec divide(double divisor) {
        if (isComplex) {
            for (int i = 0; i < size; i++) {
                set(i, getComplex(i).divide(divisor));
            }
        } else {
            for (int i = 0; i < size; i++) {
                set(i, getReal(i) / divisor);
            }
        }

        return (this);
    }

    /**
     * Divide the values of this vector by those in another vector.
     *
     * @param divVec divide by this vector
     * @return this vector
     */
    public Vec divide(Vec divVec) {
        if (isComplex) {
            if (divVec.isComplex) {
                for (int i = 0; i < size; i++) {
                    set(i, getComplex(i).divide(divVec.getComplex(i)));
                }
            } else {
                for (int i = 0; i < size; i++) {
                    set(i, getComplex(i).divide(divVec.getReal(i)));
                }
            }
        } else if (divVec.isComplex) {
            makeApache();
            for (int i = 0; i < size; i++) {
                set(i, getComplex(i).divide(divVec.getComplex(i)));
            }
        } else {
            for (int i = 0; i < size; i++) {
                set(i, getReal(i) / divVec.getReal(i));
            }
        }

        return (this);
    }

    /**
     * Replace values in this vector by the specified value divided by the current value. If the vector is not complex,
     * make it so.
     *
     * @param value divide by this value
     * @return this vector
     */
    public Vec rdivide(Complex value) {
        if (!isComplex) {
            makeApache();
        }
        for (int i = 0; i < size; i++) {
            set(i, value.divide(getComplex(i)));
        }

        return (this);
    }

    /**
     * Replace values in this vector by the specified value divided by the current value.
     *
     * @param value divide this value by current values
     * @return this vector
     */
    public Vec rdivide(double value) {
        if (isComplex) {
            for (int i = 0; i < size; i++) {
                set(i, getComplex(i).reciprocal().multiply(value));
            }
        } else {
            for (int i = 0; i < size; i++) {
                set(i, value / getReal(i));
            }
        }

        return (this);
    }

    /**
     * Set this vector to the square of the existing values
     */
    public void power() {
        int i;
        if (isComplex) {
            resize(size, false);
            if (useApache) {
                for (i = 0; i < size; i++) {
                    rvec[i] = cvec[i].getReal() * cvec[i].getReal() + (cvec[i].getImaginary() * cvec[i].getImaginary());
                }
            } else {
                for (i = 0; i < size; i++) {
                    rvec[i] = rvec[i] * rvec[i] + ivec[i] * ivec[i];
                }
            }

        } else {
            for (i = 0; i < size; i++) {
                rvec[i] = rvec[i] * rvec[i];
            }
        }
    }

    /**
     * Gets the norm of the vector, computed as the square root of the sum of the squares.
     *
     * @return norm
     */
    public double getNorm() {
        double sum = 0.0;
        int i;

        if (isComplex) {

            if (useApache) {
                for (i = 0; i < size; i++) {
                    sum += (cvec[i].getReal() * cvec[i].getReal())
                            + (cvec[i].getImaginary() * cvec[i].getImaginary());
                }
            } else {
                for (i = 0; i < size; i++) {
                    sum += rvec[i] * rvec[i] + ivec[i] * ivec[i];
                }
            }

        } else {
            for (i = 0; i < size; i++) {
                sum += rvec[i] * rvec[i];
            }
        }
        return Math.sqrt(sum);
    }

    /**
     * Generate damped sinusoidal signal, and add to Vec instance.
     *
     * @param freq frequency in Hz
     * @param lw Linewidth in Hz
     * @param amp amplitude
     * @param ph phase in degrees
     */
    public void genSignalHz(double freq, double lw, double amp, double ph) {
        double f = freq / (1.0 / dwellTime);
        double d = Math.exp(-Math.PI * lw * dwellTime);
        Complex w = ComplexUtils.polar2Complex(d, f * Math.PI * 2.0);
        //System.out.println(w.getReal() + " " + w.getImaginary());
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
     * @param freq frequency in degrees per point
     * @param decay exponential decay per point
     * @param amp amplitude
     * @param ph phase in degrees
     */
    public void genSignal(double freq, double decay, double amp, double ph) {
        Complex w = ComplexUtils.polar2Complex(decay, freq * Math.PI / 180.0);
        //System.out.println(w.getReal() + " " + w.getImaginary());
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
     * Add Lorentzian line shapes to this vector using parameters stored in another Vec object.
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

        // zero ();
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
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in TPPI data collection.
     *
     * @param rvec vector of doubles to process
     */
    public static void negatePairs(double rvec[]) {
        negatePairs(rvec, rvec.length);
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in TPPI data collection.
     *
     * @param rvec real values
     * @param vecSize size of vector
     */
    public static void negatePairs(double rvec[], int vecSize) {
        for (int i = 3; i < vecSize; i += 4) {
            rvec[i - 1] = -rvec[i - 1];
            rvec[i] = -rvec[i];
        }
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in TPPI data collection.
     *
     * @param rvec real values
     * @param ivec imaginary values
     */
    public static void negatePairs(double rvec[], double ivec[]) {
        negatePairs(rvec, ivec, rvec.length);
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in TPPI data collection.
     *
     * @param rvec real values
     * @param ivec imaginary values
     * @param vecSize size of vector
     */
    public static void negatePairs(double rvec[], double ivec[], int vecSize) {
        for (int i = 1; i < vecSize; i += 2) {
            rvec[i] = -rvec[i];
            ivec[i] = -ivec[i];
        }
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in TPPI data collection.
     *
     * @param cvec complex values
     */
    public static void negatePairs(Complex cvec[]) {
        negatePairs(cvec, cvec.length);
    }

    /**
     * Multiply alternate real/imaginary pairs of values by -1.0. Often used in TPPI data collection.
     *
     * @param cvec complex values
     * @param vecSize size of vector
     */
    public static void negatePairs(Complex cvec[], int vecSize) {
        for (int i = 1; i < vecSize; i += 2) {
            cvec[i] = new Complex(-cvec[i].getReal(), -cvec[i].getImaginary());
        }
    }

    /**
     * Negate every other pair of points. The effect is to shift a spectrum by sw/2, moving the center frequency to the
     * edge of the spectrum. Used for States-TPPI processing.
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
     * Negate the imaginary values of this vector. The effect is to reverse the spectrum resulting from FFT. Negate
     * imaginary values.
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
            for (int i = 0; i < size; ++i) {
                rvec[i] = 1.0;
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

    /**
     * Set values in vector to 1.0 (1.0, 0.0 if complex)
     */
    public void ones() {
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; ++i) {
                    cvec[i] = Complex.ONE;
                }
            } else {
                for (int i = 0; i < size; ++i) {
                    rvec[i] = 1.0;
                    ivec[i] = 0;
                }
            }
        } else {
            for (int i = 0; i < size; ++i) {
                rvec[i] = 1.0;
            }
        }
    }

    /**
     * Resize vector and set values in vector to 1.0 (1.0, 0.0 if complex)
     *
     * @param size new size of vector
     */
    public void ones(int size) {
        resize(size);
        ones();
    }

    /**
     * Reverse the order of the data in vector.
     */
    public void reverse() {
        int n = size;
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < n / 2; i++) {
                    Complex hold = cvec[i];
                    cvec[i] = cvec[n - i - 1];
                    cvec[n - i - 1] = hold;
                }
            } else {
                for (int i = 0; i < n / 2; i++) {
                    double hold = rvec[i];
                    rvec[i] = rvec[n - i - 1];
                    rvec[n - i - 1] = hold;
                    hold = ivec[i];
                    ivec[i] = ivec[n - i - 1];
                    ivec[n - i - 1] = hold;
                }
            }
        } else {
            for (int i = 0; i < n / 2; i++) {
                double hold = rvec[i];
                rvec[i] = rvec[n - i - 1];
                rvec[n - i - 1] = hold;
            }
        }
    }

    /**
     * Set a real element of vector to a specified value.
     *
     * @param i The element of the vector to set, which can be any number from 0 to 'size - 1' of the Vec.
     * @param val value to set at specified index
     */
    public void set(int i, double val) {
        if (i < size && i >= 0) {
            rvec[i] = val;
        } else {
            throw new IllegalArgumentException("Cannot set element "
                    + Integer.toString(i) + " in a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Set the i'th element of the real and complex parts of the vector.
     *
     * @param i The element of the vector to set, which can be any number from 0 to 'size - 1' of the Vec.
     * @param real The real value to set.
     * @param imag The imaginary value to set.
     */
    public void set(int i, double real, double imag) {
        if (isComplex) {

            if (i < size && i >= 0) {
                if (useApache) {
                    cvec[i] = new Complex(real, imag);
                } else {
                    rvec[i] = real;
                    ivec[i] = imag;
                }
            } else {
                throw new IllegalArgumentException("Cannot set element "
                        + Integer.toString(i) + " in a Vec of size "
                        + Integer.toString(size));
            }
        } else {
            throw new IllegalArgumentException("Cannot set imaginary part "
                    + "of a Real Vector");
        }
    }

    /**
     * Set the i'th element of a complex vector to the specified complex value.
     *
     * @param i the index to set
     * @param c the new value to set
     * @throws IllegalArgumentException if vector is not complex
     */
    public void set(int i, Complex c) {
        if (isComplex) {
            if (i < size && i >= 0) {
                if (useApache) {
                    cvec[i] = c;
                } else {
                    rvec[i] = c.getReal();
                    ivec[i] = c.getImaginary();
                }
            } else {
                throw new IllegalArgumentException("Cannot set element "
                        + Integer.toString(i) + " in a Vec of size "
                        + Integer.toString(size));
            }

        } else {
            throw new IllegalArgumentException("Cannot set imaginary part "
                    + "of a Real Vector");
        }
    }

    /**
     * Set the dataset location pt for this vector
     *
     * @param pt the new location
     */
    public void setPt(int[][] pt, int[] dim) {
        if (pt != null) {
            this.pt = new int[pt.length][];
            for (int i = 0; i < pt.length; i++) {
                this.pt[i] = pt[i].clone();
            }
        }
        if (null != dim) {
            this.dim = new int[dim.length];
            System.arraycopy(dim, 0, this.dim, 0, dim.length);
        } else {
            this.dim = null;
        }
    }

    /**
     * Get an array of the real values of this vector. The values are copied so changes in the returned array do not
     * effect this vector.
     *
     * @return the array of real values
     */
    public double[] getReal() {
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            values[i] = getReal(i);
        }
        return values;
    }

    /**
     * Get an array of the real values in a region of this vector. The values are copied so changes in the returned
     * array do not effect this vector.
     *
     * @param values a double array in which to put the real values
     * @param start the starting position of the Vec at which to read values
     */
    public void getReal(double[] values, int start) throws IllegalArgumentException {
        if (values.length + start >= size) {
            throw new IllegalArgumentException("invalid positions for getReal");
        }
        for (int i = 0, n = values.length; i < n; i++) {
            values[i] = getReal(i + start);
        }
    }

    /**
     * Return real or imaginary value at specified index. It's preferred to use getReal or getImag unless choice of real
     * or imaginary needs to be made programmatically as its easy to use make errors by wrong choice of true/false with
     * this method.
     *
     * @param index position of value
     * @param imag true to get imaginary, false to get real
     * @return value the value at the index
     */
    public double getRealOrImag(int index, boolean imag) {
        if (imag) {
            return getImag(index);
        } else {
            return getReal(index);
        }
    }

    /**
     * Return real value at specified index
     *
     * @param index position of value
     * @return value real value at the index
     */
    public double getReal(int index) {
        if (index < size && index >= 0) {
            if (isComplex && useApache) {
                return cvec[index].getReal();
            } else {
                return rvec[index];
            }
        } else {
            throw new IllegalArgumentException("Cannot get real element "
                    + Integer.toString(index) + " in a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Return imaginary value at specified index
     *
     * @param index position of value
     * @return value imaginary value at index
     */
    public double getImag(int index) {
        if (index < size && index >= 0) {
            if (isComplex) {
                if (useApache) {
                    return cvec[index].getImaginary();
                } else {
                    return ivec[index];
                }
            } else {
                throw new VecException("Cannot get an imaginary value from a real vector!");
            }
        } else {
            throw new IllegalArgumentException("Cannot get imaginary element "
                    + Integer.toString(index) + " in a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Get complex value at specified index
     *
     * @param index position of value
     * @return value Complex value at index
     */
    public Complex getComplex(int index) {
        if (index < size && index >= 0) {
            if (isComplex) {
                if (useApache) {
                    return cvec[index];
                } else {
                    return new Complex(rvec[index], ivec[index]);
                }
            } else {
                return new Complex(rvec[index]);
            }
        } else {
            throw new IllegalArgumentException("Cannot get Complex number "
                    + Integer.toString(index) + " in a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Set real value at specified index.
     *
     * @param index position to set
     * @param value set this value
     */
    public void setReal(int index, double value) {
        if (index < size && index >= 0) {
            if (isComplex) {
                if (useApache) {
                    cvec[index] = new Complex(value, cvec[index].getImaginary());
                } else {
                    rvec[index] = value;
                }
            } else {
                rvec[index] = value;
            }
        } else {
            throw new IllegalArgumentException("Cannot set real element "
                    + Integer.toString(index) + " in a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Set imaginary value at specified index
     *
     * @param index position to set
     * @param value set this value
     * @throws VecException if vector is not complex
     */
    public void setImag(int index, double value) {
        if (index < size && index >= 0) {
            if (isComplex) {
                if (useApache) {
                    cvec[index] = new Complex(cvec[index].getReal(), value);
                } else {
                    ivec[index] = value;
                }
            } else {
                throw new VecException("Cannot set an imaginary value in a real vector!");
            }
        } else {
            throw new IllegalArgumentException("Cannot set imaginary element "
                    + Integer.toString(index) + " in a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Set complex value at specified index. If vector is real, only use the real part of value.
     *
     * @param index position to set
     * @param complex value to set
     */
    public void setComplex(int index, Complex complex) {
        if (index < size && index >= 0) {
            if (isComplex) {
                if (useApache) {
                    cvec[index] = complex;
                } else {
                    rvec[index] = complex.getReal();
                    ivec[index] = complex.getImaginary();
                }
            } else {
                rvec[index] = complex.getReal();
            }
        } else {
            throw new IllegalArgumentException("Cannot set complex element "
                    + Integer.toString(index) + " in a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Set complex value at specified index. If vector is real, only use the real part of value.
     *
     * @param index position to set
     * @param real the real part to set
     * @param imag the imaginary part to set
     */
    public void setComplex(int index, double real, double imag) {
        if (index < size && index >= 0) {
            if (isComplex) {
                if (useApache) {
                    cvec[index] = new Complex(real, imag);
                } else {
                    rvec[index] = real;
                    ivec[index] = imag;
                }
            } else {
                rvec[index] = real;
            }
        } else {
            throw new IllegalArgumentException("Cannot set real element "
                    + Integer.toString(index) + " in a Vec of size "
                    + Integer.toString(size));
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
     * Return size of this vector
     *
     * @return size
     */
    public int getSize() {
        return size;
    }

    /**
     * Return time-domain size of original vector
     *
     * @return size
     */
    public int getTDSize() {
        return tdSize;
    }

    /**
     * Return zero-filling size of the vector
     *
     * @return size
     */
    public int getZFSize() {
        return zfSize;
    }

    /**
     * Return the first point of the xtracted region of the vector
     *
     * @return first point
     */
    public int getExtFirst() {
        return extFirst;
    }

    /**
     * Return last point of the extracted region of the vector
     *
     * @return last point
     */
    public int getExtLast() {
        return extLast;
    }

    /**
     * Set time-domain size of vector
     *
     * @param newSize new time-domain size
     */
    public void setTDSize(int newSize) {
        tdSize = newSize;
    }

    /**
     * Return whether vector is real
     *
     * @return true if vector real
     */
    public boolean isReal() {
        return !isComplex;
    }

    /**
     * Make the vector real. If vector was complex, the previous real values become real components of new values.
     */
    public void makeReal() {
        boolean copyFromCvec = useApache && isComplex;

        resize(size, false);

        // if we were using cvec before, then copy real over to rvec
        if (copyFromCvec) {
            for (int i = 0; i < size; ++i) {
                rvec[i] = cvec[i].getReal();
            }
        }
    }

    /**
     * Make the vector complex. If vector was real, the previous real values become real components of new values.
     */
    public void makeComplex() {
        if (!isComplex) {
            resize(size, true);
            if (useApache) {
                for (int i = 0; i < size; ++i) {
                    cvec[i] = new Complex(rvec[i]);
                }
            } else {
                for (int i = 0; i < size; ++i) {
                    ivec[i] = 0.0;
                }
            }
        }
    }

    /**
     * If vector is complex and stores real/imag in separate arrays, change to use an array of Complex.
     */
    public void makeApache() {
        if (!useApache) {
            useApache = true;
            if (isComplex) {
                resize(size);
                for (int i = 0; i < size; ++i) {
                    cvec[i] = new Complex(rvec[i], ivec[i]);
                }
            }
        }
    }

    /**
     *
     * If vector is complex and stores complex values in an array of Complex, change to store in separate arrays of real
     * and imaginary values.
     */
    public void makeNotApache() {
        if (useApache) {
            useApache = false;
            if (isComplex) {
                resize(size);
                for (int i = 0; i < size; ++i) {
                    rvec[i] = cvec[i].getReal();
                    ivec[i] = cvec[i].getImaginary();
                }
            }
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
     * @param negatePairs negate alternate real/imaginary pairs
     * @param negateImaginary negate imaginary pairs
     * @param fixGroupDelay modify vector to remove DSP charge-up at front of vector
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
     * Perform inverse Fast Fourier Transform (FFT) of this vector with various options.
     *
     * @param negatePairs negate alternate real/imaginary pairs
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
     * Real FT.
     *
     * Vec must be real. If a Vec is using cvec it will do a RFT of the real part and copy back to cvec, if a Vec is
     * using rvec it will copy RFT back to rvec and ivec.
     *
     * @param inverse If true do the inverse FFT.
     */
    public void rft(boolean inverse) {
        if (!isComplex) {
            checkPowerOf2();
            double[] ftvec = new double[size];

            if (!useApache) {
                System.arraycopy(rvec, 0, ftvec, 0, size);
            } else {
                for (int i = 0; i < size; ++i) {
                    ftvec[i] = cvec[i].getReal();
                }
            }
            FastFourierTransformer ffTrans = new FastFourierTransformer(DftNormalization.STANDARD);
            Complex[] ftResult;
            if (!inverse) {
                ftResult = ffTrans.transform(ftvec, TransformType.FORWARD);
            } else {
                ftResult = ffTrans.transform(ftvec, TransformType.INVERSE);
            }

            makeComplex();

            int newSize = size / 2;
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
     * Hilbert transform of this vector. Converts real vector into complex. No effect if vector is already complex
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
     * Return true if values in vector are Complex.
     *
     * @return true if complex
     */
    public boolean isComplex() {
        return isComplex;
    }

    /**
     * Return true if vector is complex and values are stored in Complex objects (not real and imaginary vectors)
     *
     * @return true if values stored as Apache Commons Mat Complex objects
     */
    public boolean useApache() {
        return useApache;
    }

    /**
     * All points in the vector are set to Math.random(). Values will be uniformly and randomly distributed between 0.0
     * and 1.0
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
     * Scale values in vector by multiplying by specified value.
     *
     * @param scaleValue multiply by this value
     * @return this vector
     */
    public Vec scale(double scaleValue) {
        int i;

        if (isComplex) {
            if (useApache) {
                for (i = 0; i < size; i++) {
                    cvec[i] = new Complex(cvec[i].getReal() * scaleValue, cvec[i].getImaginary() * scaleValue);

                }
            } else {
                for (i = 0; i < size; i++) {
                    rvec[i] *= scaleValue;
                    ivec[i] *= scaleValue;
                }
            }
        } else {
            for (i = 0; i < size; i++) {
                rvec[i] *= scaleValue;
            }
        }

        return (this);
    }

    /**
     * Return the sum of points in the vector.
     *
     * @return the sum of points as a Complex number (whose imaginary part is real if the vector is real)
     */
    public Complex sum() {
        final Complex result;
        if (isComplex) {
            result = sumVector(size);
        } else {
            double sum = sumVector(this.rvec, size);
            result = new Complex(sum, 0.0);
        }

        return result;
    }

    private static double sumVector(double[] vec, int size) {
        int i;
        double sum = 0.0;
        for (i = 0; i < size; i++) {
            sum += vec[i];
        }
        return sum;
    }

    private Complex sumVector(int size) {
        int i;

        double sumR = 0.0;
        double sumI = 0.0;
        for (i = 0; i < size; i++) {
            sumR += getReal(i);
            sumI += getImag(i);
        }
        return new Complex(sumR, sumI);
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
     * Calculate the first four moments of distribution in the specified region of this vector. FIXME check moments
     *
     * @param start first point of region
     * @param end last point of region
     * @return an array of four values containing the first four moments.
     * @throws IllegalArgumentException if not vector not real, data is null, or the range is invalid
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
     * Calculate standard deviation in a region that gives minimum standard deviation.
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
     * Calculate mean, standard deviation, skewness and kurtosis in a specified region of this vector
     *
     * @param start starting point (inclusive)
     * @param end ending point (inclusive)
     * @return an array of four doubles containing the statistics
     * @throws IllegalArgumentException if vector not real or doesn't have at least 4 values in range
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
     * Split a complex vector into two vectors containing the real and imaginary values. Caution this method assumes the
     * data is stored in separate double arrays. FIXME
     *
     * @param rVec this vector will be a real vector containing the real values of the original vector
     * @param iVec this vector will be a real vector containing the imaginary values of the original vector.
     */
    public void split(Vec rVec, Vec iVec) {
        rVec.resize(size, false);
        iVec.resize(size, false);

        for (int i = 0; i < size; i++) {
            rVec.set(i, getReal(i));
            iVec.set(i, getImag(i));
        }

        rVec.isComplex = false;
        rVec.freqDomain = freqDomain;
        rVec.dwellTime = dwellTime;
        rVec.centerFreq = centerFreq;
        rVec.refValue = refValue;
        rVec.ph0 = ph0;
        rVec.ph1 = ph1;
        iVec.isComplex = false;
        iVec.freqDomain = freqDomain;
        iVec.dwellTime = dwellTime;
        iVec.centerFreq = centerFreq;
        iVec.refValue = refValue;

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
     * Convert a real vector with real and imaginary values in alternating positons of array into a Complex vector.
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

    @Override
    public String toString() {
        StringBuilder temp = new StringBuilder();
        if (isComplex) {
            temp.append("Complex vector");
        } else {
            temp.append("Vector");
        }

        temp.append(" size: ");
        temp.append(Integer.toString(size));

        return temp.toString();
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
     * Set the values of this vector to be those in the provided Complex array. Size of this vector will not be changed.
     * The number of values used will be the minimum of the size of vector and array
     *
     * @param newVec the complex values to set
     * @return this vector
     */
    public Vec setComplex(Complex[] newVec) {
        if (!isComplex) {
            isComplex = true;
        }
        if (!useApache) {
            makeApache();
        }
        if ((cvec == null) || (cvec.length < size)) {
            cvec = new Complex[size];
        }
        int n = newVec.length < size ? newVec.length : size;
        System.arraycopy(newVec, 0, cvec, 0, n);
        return (this);
    }

    /**
     * Return the array of Complex values. Array is not copied so changes in returned array will change the vector
     * values.
     *
     * @return the Complex value array
     * @throws IllegalStateException if the vector is not Complex or doesn't use a Complex array
     */
    public Complex[] getCvec() {
        if (isComplex && useApache) {
            return cvec;
        } else {
            throw new IllegalVecState(isComplex, useApache, true, true);
        }
    }

    /**
     * Return the array of double values that store real values. Array is not copied so changes in returned array will
     * change the vector values.
     *
     * @return the array of doubles that stores real values
     * @throws IllegalStateException if the vector is Complex and doesn't use Complex array
     */
    public double[] getRvec() {
        if (!(isComplex && useApache)) {
            return rvec;
        } else {
            throw new IllegalVecState(false, true);
        }
    }

    /**
     * Return the array of double values that store imaginary values. Array is not copied so changes in returned array
     * will change the vector values.
     *
     * @return the array of doubles that stores imaginary values
     * @throws IllegalStateException if the vector is not Complex or uses Complex array
     */
    public double[] getIvec() {
        if (isComplex && !useApache) {
            return ivec;
        } else {
            throw new IllegalVecState(isComplex, useApache, true, false);
        }
    }

    /**
     * Converts fractional position in vector to point
     *
     * @param frac the fractional position
     * @return the point
     */
    public double getDoublePosition(Fraction frac) {
        return frac.doubleValue() * (size - 1);
    }

    /**
     * Converts point position to point
     *
     * @param point positoin as point
     * @return the point
     */
    public double getDoublePosition(Point point) {
        return point.doubleValue();
    }

    /**
     * Convert Index position to point
     *
     * @param index position as Index
     * @return the point
     */
    public double getDoublePosition(Index index) {
        return index.doubleValue();
    }

    /**
     * Convert time position to point
     *
     * @param time position as time
     * @return position as point
     */
    public double getDoublePosition(Time time) {
        return time.doubleValue() / dwellTime;
    }

    /**
     * Convert PPM position to point
     *
     * @param ppm position in ppm
     * @return position in points
     */
    public double getDoublePosition(PPM ppm) {
        return refToPt(ppm.doubleValue());
    }

    /**
     * Convert frequency position to point
     *
     * @param freq position as frequency
     * @return position in points
     */
    public double getDoublePosition(Frequency freq) {
        return freq.doubleValue() * dwellTime * (size - 1);
    }

    /**
     * Convert position (typically in PPM) to integer point
     *
     * @param ref position
     * @return position in points
     */
    public int refToPt(double ref) {
        return (int) ((refValue - ref) * centerFreq * dwellTime * size + 0.5);
    }

    /**
     * Convert position in points to PPM
     *
     * @param pt position in points
     * @return position in PPM
     */
    public double pointToPPM(double pt) {
        return refValue - (pt / (centerFreq * dwellTime * size));
    }

    /**
     * Convert position in reference units (chemical shift typically) to position in points.
     *
     * @param ref position to convert
     * @return position in points
     */
    public double refToPtD(double ref) {
        // why add 1.0, empircally necessary in show wsvd table
        return ((refValue - ref) * centerFreq * dwellTime * size) + 1.0;
    }

    /**
     * Convert width in Hz to width in points
     *
     * @param lw width in Hz
     * @return width in points
     */
    public double lwToPtD(double lw) {
        return lw * size * dwellTime;
    }

    /**
     * Convert position in time to position in points
     *
     * @param time position in time
     * @return position in points
     */
    public int timeToPt(double time) {
        return (int) (time / dwellTime);
    }

    /**
     * Set mode of data to be in Frequency Domain or Time Domain
     *
     * @param state use true to set Frequency Domain
     */
    public void setFreqDomain(boolean state) {
        freqDomain = state;
    }

    /**
     * Return whether data is in Frequency Domain
     *
     * @return true if in Frequency Domain
     */
    public boolean getFreqDomain() {
        return freqDomain;
    }

    /**
     * Check if the size is a power of 2, if not resize the Vector so it is a power of 2 in length
     */
    public void checkPowerOf2() {
        if (!ArithmeticUtils.isPowerOfTwo(size)) {
            int n = 1;
            while (size > n) {
                n *= 2;
            }
            resize(n);
        }
    }

    /**
     * Return the first power of 2 equal to or greater than specified size
     *
     * @param mySize test size
     * @return power of 2 size
     */
    public static int checkPowerOf2(int mySize) {
        int n = mySize;
        if (!ArithmeticUtils.isPowerOfTwo(mySize)) {
            n = 1;
            while (mySize > n) {
                n *= 2;
            }
        }
        return n;
    }

    /**
     * Automatically calculate phase values for this vector using an one of two algorithms. One based on flattening
     * baseline regions adjacent to peaks and one based on entropy minimization
     *
     * @param doFirst Set to true to include first order phase correction
     * @param winSize Window size used for analyzing for baseline region
     * @param ratio Ratio Intensity to noise ratio used for indentifying baseline reginos
     * @param mode Set to 0 for flattening mode and 1 for entropy mode
     * @param ph1Limit Set limit on first order phase. Can prevent unreasonable results
     * @return an array of 1 or two phase values (depending on whether first order mode is used)
     */
    public double[] autoPhase(boolean doFirst, int winSize, double ratio, int mode, double ph1Limit, double negativePenalty) {
        int pivot = 0;
        double p1PenaltyWeight = 1.0;
        if (winSize < 1) {
            winSize = 2;
        }
        if (ratio <= 0.0) {
            ratio = 25.0;
        }
        double[] phaseResult;
        if (!doFirst) {
            TestBasePoints tbPoints = new TestBasePoints(this, winSize, ratio, mode, negativePenalty);
            phaseResult = tbPoints.autoPhaseZero();
        } else {
            TestBasePoints tbPoints = new TestBasePoints(this, winSize, ratio, mode, negativePenalty);
            tbPoints.setP1PenaltyWeight(p1PenaltyWeight);
            phaseResult = tbPoints.autoPhase(ph1Limit);
        }
        return phaseResult;
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
     * @param last ending point of region to analyze
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
     * @param p0 The zeroth order phase value
     * @param p1 The first order phase value
     * @param pivot The pivot value at which the first order phase correction has no effect on data
     * @param phaseAbs if false apply the specified values, if true subtract the currently stored ph0 and ph1 values
     * from the specified values and then
     * @param discardImaginary Discard the imaginary values and convert vector to real. Phasing is a little faster if
     * you do this (and saves calling a seperate REAL operation.
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
        return phase(p0, p1, phaseAbs, false);
    }

    /**
     * Apply the specified phase values to this vector.
     *
     * @param phases The phase values as an array
     * @return this vector
     */
    public void phase(double[] phases) {
        double ph0 = 0.0;
        double ph1 = 0.0;
        if (phases.length > 0) {
            ph0 = phases[0];
        }
        if (phases.length > 1) {
            ph1 = phases[1];
        }
        phase(ph0, ph1, false, false);
    }

    /**
     * Apply the specified phase values to this vector.
     *
     * @param p0 The zeroth order phase value
     * @param p1 The first order phase value
     * @return this vector
     */
    public Vec phase(double p0, double p1) {
        return phase(p0, p1, false, false);
    }

    /**
     * Apply the specified phase values to this vector.
     *
     * @param p0 The zeroth order phase value
     * @param p1 The first order phase value
     * @param phaseAbs if false apply the specified values, if true subtract the currently stored ph0 and ph1 values
     * from the specified values and then
     * @param discardImaginary Discard the imaginary values and convert vector to real. Phasing is a little faster if
     * you do this (and saves calling a seperate REAL operation.
     * @return this vector
     */
    public Vec phase(double p0, double p1, boolean phaseAbs, boolean discardImaginary) {
        double degtorad = Math.PI / 180.0;
        double dDelta;
        int i;

        if (!isComplex) {
            return this;
        }

        double tol = 0.0001;
        if (phaseAbs) {
            p0 = p0 - ph0;
            p1 = p1 - ph1;
        }

        if (Math.abs(p1) < tol) {
            if (Math.abs(p0) < tol) {
                if (discardImaginary) {
                    makeReal();
                }
                return (this);
            }
            if (Math.abs(p0 - Math.PI) < tol) {
            }

            double pReal = FastMath.cos(p0 * degtorad);
            double pImag = -FastMath.sin(p0 * degtorad);
            if (useApache) {
                if (discardImaginary) {
                    resize(size, false);
                    for (i = 0; i < size; i++) {
                        double real = cvec[i].getReal();
                        double imag = cvec[i].getImaginary();
                        rvec[i] = real * pReal - imag * pImag;
                    }
                } else {
                    for (i = 0; i < size; i++) {
                        double real = cvec[i].getReal();
                        double imag = cvec[i].getImaginary();
                        cvec[i] = new Complex(real * pReal - imag * pImag, real * pImag + imag * pReal);
                    }
                }
            } else {
                Complex cmpPhas = new Complex(FastMath.cos(p0 * degtorad), -FastMath.sin(p0 * degtorad));
                if (discardImaginary) {
                    resize(size, false);
                    for (i = 0; i < size; i++) {
                        rvec[i] = rvec[i] * pReal - ivec[i] * pImag;
                    }
                } else {
                    for (i = 0; i < size; i++) {
                        double real = rvec[i];
                        double imag = ivec[i];
                        rvec[i] = real * pReal - imag * pImag;
                        ivec[i] = real * pImag + imag * pReal;
                    }

                }
            }
            ph0 = Util.phaseMin(ph0 + p0);

            return (this);
        }

        dDelta = p1 / (size - 1);
        if (useApache) {
            if (discardImaginary) {
                resize(size, false);
                for (i = 0; i < size; i++) {
                    double p = p0 + i * dDelta;
                    double pReal = FastMath.cos(p * degtorad);
                    double pImag = -FastMath.sin(p * degtorad);
                    double real = cvec[i].getReal();
                    double imag = cvec[i].getImaginary();
                    rvec[i] = real * pReal - imag * pImag;
                }
            } else {
                for (i = 0; i < size; i++) {
                    double p = p0 + i * dDelta;
                    double pReal = FastMath.cos(p * degtorad);
                    double pImag = -FastMath.sin(p * degtorad);
                    double real = cvec[i].getReal();
                    double imag = cvec[i].getImaginary();
                    cvec[i] = new Complex(real * pReal - imag * pImag, real * pImag + imag * pReal);
                }
            }
        } else if (discardImaginary) {
            resize(size, false);
            for (i = 0; i < size; i++) {
                double p = p0 + i * dDelta;
                double pReal = FastMath.cos(p * degtorad);
                double pImag = -FastMath.sin(p * degtorad);
                rvec[i] = rvec[i] * pReal - ivec[i] * pImag;
            }
        } else {
            for (i = 0; i < size; i++) {
                double p = p0 + i * dDelta;
                double pReal = FastMath.cos(p * degtorad);
                double pImag = -FastMath.sin(p * degtorad);
                double real = rvec[i];
                double imag = ivec[i];
                rvec[i] = real * pReal - imag * pImag;
                ivec[i] = real * pImag + imag * pReal;
            }
        }
        ph0 = Util.phaseMin(ph0 + p0);
        ph1 = ph1 + p1;

        return (this);
    }

    /**
     * Apply the specified phase values to this vector. This method can be used when applying the same phase to multiple
     * vectors. The phase corrections are pre-calculated based on p0 and p1 and then applied to this method in the pReal
     * and pImag arguments. The specified p0 and p1 values are only used here for updating the vector header, but are
     * the values used for setting up pReal and pImag.
     *
     * @param p0 The zeroth order phase value.
     * @param p1 The first order phase value from the specified values and then
     * @param discardImaginary Discard the imaginary values and convert vector to real. Phasing is a little faster
     *
     * @param pReal Array of real values of phase corrections
     * @param pImag Array of imaginary values of phase corrections
     * @return this vector
     */
    public Vec phase(double p0, double p1, boolean discardImaginary, double[] pReal, double[] pImag) {

        if (!isComplex) {
            return this;
        }

        if (useApache) {
            if (discardImaginary) {
                resize(size, false);
                for (int i = 0; i < size; i++) {
                    double real = cvec[i].getReal();
                    double imag = cvec[i].getImaginary();
                    rvec[i] = real * pReal[i] - imag * pImag[i];
                }
            } else {
                for (int i = 0; i < size; i++) {
                    double real = cvec[i].getReal();
                    double imag = cvec[i].getImaginary();
                    cvec[i] = new Complex(real * pReal[i] - imag * pImag[i], real * pImag[i] + imag * pReal[i]);
                }
            }
        } else if (discardImaginary) {
            resize(size, false);
            for (int i = 0; i < size; i++) {
                rvec[i] = rvec[i] * pReal[i] - ivec[i] * pImag[i];
            }
        } else {
            for (int i = 0; i < size; i++) {
                double real = rvec[i];
                double imag = ivec[i];
                rvec[i] = real * pReal[i] - imag * pImag[i];
                ivec[i] = real * pImag[i] + imag * pReal[i];
            }
        }
        ph0 = Util.phaseMin(ph0 + p0);
        ph1 = ph1 + p1;

        return (this);
    }

    /**
     * Update this vector by applying coefficients to combine adjacent values. Used by NMRFx Processor to display
     * vectors of the FID along indirect dimensions
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
     * Update this vector by combining adjacent values for hyper-complex acquistion. Used by NMRFx Processor to display
     * vectors of the FID along indirect dimensions
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
     * Multiply vector by a frequency, relative to sweep width. Used with digital filter with group delay (shift) of
     * ncoefs/2.
     *
     * @param freq frequency in Hz (up to +/-0.5 sweep width)
     * @param shift number of points to shift scale by
     * @see FirFilter
     */
    public void multiplyByFrequency(double freq, double shift) {
//        double scale = freq * dwellTime;  // freq / SW
        double scale = freq;
        scale *= 2 * Math.PI;
        double fpt;
        for (int i = 0; i < size; i++) {
            fpt = scale * (i - shift);
            multiply(i, Math.cos(fpt), -Math.sin(fpt));
        }
    }

    /**
     * Multiply values in vector
     *
     * @param real real part of factor
     * @param imag imaginary value of factor
     */
    public void multiply(double real, double imag) {
        multiply(new Complex(real, imag));
    }

    /**
     * Multiply entire vector by factor
     *
     * @param factor multiply by this Complex value
     */
    public void multiply(Complex factor) {
        if (isComplex && useApache) {
            for (int i = 0; i < size; i++) {
                multiply(i, factor);
            }
        } else {
            double realFactor = factor.getReal();
            double imagFactor = factor.getImaginary();
            for (int i = 0; i < size; i++) {
                multiply(i, realFactor, imagFactor);
            }
        }
    }

    /**
     * Multiply either rvec[index] and ivec[index] or cvec[index] by factor
     *
     * @param index position to multiply
     * @param factor multiply by this value
     */
    public void multiply(int index, Complex factor) {
        if (index >= 0 && index < size) {
            if (isComplex) {
                if (useApache) {
                    cvec[index] = cvec[index].multiply(factor);
                } else {
                    multiplyValue(index, factor.getReal(),
                            factor.getImaginary());
                }
            }
        } else {
            throw new IllegalArgumentException("Cannot multiply the "
                    + Integer.toString(index) + " element of a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Multiply either rvec[index] and ivec[index] or cvec[index] by (realFactor, imagFactor)
     *
     * @param index position to multiply
     * @param realFactor real part of value to multiply by
     * @param imagFactor imaginary part of value to multiply by
     */
    public void multiply(int index, double realFactor, double imagFactor) throws IllegalArgumentException {
        if (index >= 0 && index < size) {
            if (isComplex) {
                if (useApache) {
                    cvec[index] = cvec[index].multiply(new Complex(realFactor, imagFactor));
                } else {
                    multiplyValue(index, realFactor, imagFactor);
                }
            } else {
                rvec[index] = rvec[index] * realFactor;
            }
        } else {
            throw new IllegalArgumentException("Cannot multiply the "
                    + Integer.toString(index) + " of a Vec of size "
                    + Integer.toString(size));
        }
    }

    /**
     * Multiply the values of this vector by those in another vector.
     *
     * @param mulVec multiply by this vector
     * @return this vector
     */
    public Vec multiply(Vec mulVec) {
        if (isComplex) {
            if (mulVec.isComplex) {
                for (int i = 0; i < size; i++) {
                    set(i, getComplex(i).multiply(mulVec.getComplex(i)));
                }
            } else {
                for (int i = 0; i < size; i++) {
                    set(i, getComplex(i).multiply(mulVec.getReal(i)));
                }
            }
        } else if (mulVec.isComplex) {
            makeApache();
            for (int i = 0; i < size; i++) {
                set(i, getComplex(i).multiply(mulVec.getComplex(i)));
            }
        } else {
            for (int i = 0; i < size; i++) {
                set(i, getReal(i) * mulVec.getReal(i));
            }
        }

        return (this);
    }

    /**
     * Transform a Complex Vec into a real Vec, setting each point to the Imaginary value.
     */
    public void imag() {
        if (isComplex) {
            makeNotApache();
            for (int i = 0; i < size; ++i) {
                set(i, getImag(i));
            }
            makeReal();
        }
    }

    /**
     * Performs multiplication on the rvec and ivec elements at index with the complex number (realFactor, imagFactor).
     * @literal { Caller guarantees that 0 <= index < size, and the matrix is complex.}
     *
     * @index position to multiply
     * @realFactor real part of factor
     *
     * @param imagFactor imaginary part of factor
     */
    private void multiplyValue(int index, double realFactor, double imagFactor) {
        rvec[index] = rvec[index] * realFactor - ivec[index] * imagFactor;
        ivec[index] = rvec[index] * imagFactor + ivec[index] * realFactor;
    }

    /**
     * Sum real values in a region after applying a zeroth order phase correction. Used in the autophase by max method.
     *
     * @param first start of region
     * @param last end of region
     * @param p0 phase value
     * @return sum of values
     */
    private double sumRealRegion(int first, int last, double p0) {
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
        double sum = 0.0;
        if (!isComplex()) {
            for (int i = first; i < last; i++) {
                sum += rvec[i];
            }
        } else {
            double degtorad = Math.PI / 180.0;
            double re = Math.cos(p0 * degtorad);
            double im = -Math.sin(p0 * degtorad);
            for (int i = first; i < last; i++) {
                sum += cvec[i].getReal() * re - cvec[i].getImaginary() * im;
            }
        }
        return sum;
    }

    /**
     * Set values in a range to 0.0
     *
     * @param first first point of range
     * @param last last point of range
     */
    public void zeros(int first, int last) {
        if (isComplex) {
            if (useApache) {
                for (int i = first; i <= last; ++i) {
                    cvec[i] = Complex.ZERO;
                }
            } else {
                for (int i = first; i <= last; ++i) {
                    rvec[i] = 0.0;
                    ivec[i] = 0.0;
                }
            }
        } else {
            for (int i = first; i <= last; ++i) {
                rvec[i] = 0.0;
            }
        }
    }

    /**
     * Copy an array of real values into vector. If the vector is smaller than size of array it will be resized up to
     * the size of the array.
     *
     * @param values the array of values
     */
    public void copy(double[] values) {
        if (values.length > getSize()) {
            resize(values.length);
        }
        for (int i = 0; i < getSize(); ++i) {
            set(i, values[i]);
        }
    }

    /**
     * Copy an array of real values and array of imaginary values into vector. If the vector is smaller than size of
     * array it will be resized up to the size of the array.
     *
     * @param values the array of real values values
     * @param valuesI the array of imaginary values
     */
    public void copy(double[] values, double[] valuesI) {
        if (values.length > getSize()) {
            resize(values.length, true);
        }
        for (int i = 0; i < getSize(); ++i) {
            set(i, values[i], valuesI[i]);
        }
    }

    /**
     * Return an array of bytes that represent the single precision floating point values of this vector. Note: values
     * are normally stored in a Vec object in double precision format so there will be some loss of precision.
     *
     * @return the array of bytes
     */
    public byte[] getBytes() {
        int nBytes = size * 4;
        if (isComplex) {
            nBytes *= 2;
        }
        byte[] buffer = new byte[nBytes];
        int j = 0;

        // note: conversion to float
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; i++) {
                    int intVal = Float.floatToIntBits((float) cvec[i].getReal());
                    buffer[j++] = (byte) ((intVal >> 24) & 0xFF);
                    buffer[j++] = (byte) ((intVal >> 16) & 0xFF);
                    buffer[j++] = (byte) ((intVal >> 8) & 0xFF);
                    buffer[j++] = (byte) (intVal & 0xFF);
                    intVal = Float.floatToIntBits((float) cvec[i].getImaginary());
                    buffer[j++] = (byte) ((intVal >> 24) & 0xFF);
                    buffer[j++] = (byte) ((intVal >> 16) & 0xFF);
                    buffer[j++] = (byte) ((intVal >> 8) & 0xFF);
                    buffer[j++] = (byte) (intVal & 0xFF);
                }
            } else {
                for (int i = 0; i < size; i++) {
                    int intVal = Float.floatToIntBits((float) rvec[i]);
                    buffer[j++] = (byte) ((intVal >> 24) & 0xFF);
                    buffer[j++] = (byte) ((intVal >> 16) & 0xFF);
                    buffer[j++] = (byte) ((intVal >> 8) & 0xFF);
                    buffer[j++] = (byte) (intVal & 0xFF);
                    intVal = Float.floatToIntBits((float) ivec[i]);
                    buffer[j++] = (byte) ((intVal >> 24) & 0xFF);
                    buffer[j++] = (byte) ((intVal >> 16) & 0xFF);
                    buffer[j++] = (byte) ((intVal >> 8) & 0xFF);
                    buffer[j++] = (byte) (intVal & 0xFF);
                }
            }
        } else if (useApache) {
            for (int i = 0; i < size; i++) {
                int intVal = Float.floatToIntBits((float) cvec[i].getReal());
                buffer[j++] = (byte) ((intVal >> 24) & 0xFF);
                buffer[j++] = (byte) ((intVal >> 16) & 0xFF);
                buffer[j++] = (byte) ((intVal >> 8) & 0xFF);
                buffer[j++] = (byte) (intVal & 0xFF);
            }
        } else {
            for (int i = 0; i < size; i++) {
                int intVal = Float.floatToIntBits((float) rvec[i]);
                buffer[j++] = (byte) ((intVal >> 24) & 0xFF);
                buffer[j++] = (byte) ((intVal >> 16) & 0xFF);
                buffer[j++] = (byte) ((intVal >> 8) & 0xFF);
                buffer[j++] = (byte) (intVal & 0xFF);
            }
        }

        return buffer;
    }

    /**
     * Get the location for reading/writing this Vec from/to a dataset.
     *
     * @return the location
     */
    public int[][] getPt() {
        return pt;
    }

    /**
     * Get the dimensions for reading/writing this Vec from/to a dataset.
     *
     * @return the dimension
     */
    public int[] getDim() {
        return dim;
    }

    /**
     * Apply a correction (typically for baseline correction) to this vector by subtracting a polynomial of the
     * specified order and with the specified coefficients. The first (0 position) coefficient is the constant term. X
     * values for the polynomial are in fractions of the vector size.
     *
     * @param order the polynomial order
     * @param X the coefficients.
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

    public Vec extract(int start, int end) {
        int newSize = end - start + 1;
        trim(start, newSize);
        int[][] pt = getPt();
        int[] dim = getDim();
        if (pt == null) {
            pt = new int[1][2];
            dim = new int[1];
        }
        pt[0][1] = newSize - 1;
        setPt(pt, dim);
        extFirst = start;
        extLast = end;
        return this;
    }

    /**
     * Trim a vector to a new size and starting point
     *
     * @param start Starting point in original size
     * @param newSize Size after trimming
     * @return this vector
     * @throws VecException if start is out of range of vector or vector doesn't have valid data arrays
     */
    public Vec trim(int start, int newSize)
            throws VecException {
        int end = (start + newSize) - 1;

        if ((start < 0) || (end >= size) || (start > end)) {
            throw new VecException("trim: error in parameters");
        }

        if (freqDomain) {
            adjustRef(start, newSize);
        }

        if (isComplex) {
            if (useApache) {
                if (cvec == null) {
                    throw new VecException("trim: no data in vector");
                }
                for (int i = 0; i < newSize; i++) {
                    cvec[i] = new Complex(cvec[i + start].getReal(), cvec[i + start].getImaginary());
                }
            } else {
                if (rvec == null) {
                    throw new VecException("trim: no data in vector");
                }
                if (ivec == null) {
                    throw new VecException("trim: no data in vector");
                }
                for (int i = 0; i < newSize; i++) {
                    rvec[i] = rvec[i + start];
                    ivec[i] = ivec[i + start];
                }

            }
        } else {
            if (rvec == null) {
                throw new VecException("trim: no data in vector");
            }

            for (int i = 0; i < newSize; i++) {
                rvec[i] = rvec[i + start];
            }
        }

        size = newSize;

        return (this);
    }

    /**
     * Subtract a vector from this vector. FIXME no check for size compatability.
     *
     * @param subVec the vector to subtract
     * @return this vector
     */
    public Vec subtract(Vec subVec) {
        if (isComplex) {
            if (useApache) {
                for (int i = 0; i < size; i++) {
                    cvec[i] = cvec[i].subtract(subVec.getComplex(i));
                }
            } else {
                for (int i = 0; i < size; i++) {
                    rvec[i] -= subVec.getReal(i);
                    ivec[i] -= subVec.getImag(i);

                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                rvec[i] -= subVec.getReal(i);
            }
        }
        return this;
    }

    /**
     * Bucket a vector into a smaller size by summing adjacent points (within each bucket)
     *
     * @param nBuckets The number of buckets (will be the new size of vector).
     * @throws IllegalArgumentException if vector is complex or number of buckets larger than size or not an integer
     * fraction of size
     */
    public void bucket(int nBuckets) throws IllegalArgumentException {
        if (freqDomain) {
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
     * @param winSize Size of window. Larger the window the narrower the region of suppression
     * @param nPasses How many passes of filter to perform. Performing 3 passes is optimal.
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
//        System.out.println("vStart " + vStart);
        if (vStart != 0) {
            tempVec.shiftWithExpand(vStart);
        }
        return subtract(tempVec);
    }

    /**
     * Moving average filter.
     *
     * @param winSize Size of window. Larger the window the narrower the region of suppression when used for solvent
     * suppression
     * @param nPasses How many passes of filter to perform. Performing 3 passes is optimal.
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
     * @throws VecException if windows size bigger than vector or values are null
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
     * @throws VecException if windows size bigger than vector or values are null
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
     * @throws VecException if windows size bigger than vector or values are null
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
     * Return whether vector is in frequency domain
     *
     * @return true if vector in frequency domain
     */
    public boolean freqDomain() {
        return freqDomain;
    }

    /**
     * Resize vector and set all values to 0.0
     *
     * @param size new size for vector
     */
    public void zeros(int size) {
        this.resize(size);
        zeros();
    }

    /**
     * Set all values to zero
     */
    public void zeros() {
        if (useApache && isComplex) {
            for (int i = 0; i < size; ++i) {
                cvec[i] = Complex.ZERO;
            }
        } else if (isComplex) {
            for (int i = 0; i < size; ++i) {
                rvec[i] = 0.0;
                ivec[i] = 0.0;
            }
        } else {
            for (int i = 0; i < size; ++i) {
                rvec[i] = 0.0;
            }
        }
    }

    /**
     * Apply soft thresholding to vector by setting values with absolute value below threshold to 0.0 and subtracting
     * threshold from positive values (greater than threshold) or adding threshold to negative values (less than
     * threshold).
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
     * Time domain polynomial correction. Can be used for solvent suppression by fitting a polynomial to the FID.
     *
     * @param order The polynomial order
     * @param winSize Size of regions to fit
     * @param start Start the fit at this point. Using value greater than 0 allows skipping artifacts
     * @throws VecException if window size or polynomial order invalid for vector size
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
     * Correct vector by subtracting a sum of sines using coefficients stored in provided vector.
     *
     * @param order The order of correction function
     * @param X an array of coefficients
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
     * @param winSize window size
     * @param lambda smoothing parameter
     * @param order order of fit (0 or 1)
     * @param baselineMode if true set the vector to be the fitted baseline, rather than correcting the values. Useful
     * for diagnostics
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
        for (int i = 0; i < size; i++) {
            vecY[i] = y[i + 1];
        }
        ArrayList<Integer> xValues = new ArrayList<>();
        ArrayList<Double> yValues = new ArrayList<>();
        for (int i = 0; i < nRegions; i++) {
            double minValue = Double.MAX_VALUE;
            int minK = 0;
            for (int j = 0; j < winSize; j++) {
                int k = i * winSize + j;
                double value = vecY[k];
                //if (value < 0.0) {
                //   value = 0.0;
                //}

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
                    y2 = (yTest * 3.0 + y2 * 1.0) / 4.0;
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
            //  int x2 = ((Integer) xValues3.get(k + 1)).intValue();
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
            for (int i = 0; i < size; i++) {
                rvec[i] = z[i + 1];
            }
        } else {
            for (int i = 0; i < size; i++) {
                //if (z[i+1] > vec[i]) {
                //   z[i+1] = vec[i];
                //}
                rvec[i] -= z[i + 1];
            }
        }
    }

    /**
     * Replace a region of a vector with a smoothed line across region. Useful for removing large (solvent) signals.
     * Values at edge of region will retain a decreasing contribution from the original values so that the transition to
     * the interpolated region will be gradual. Remaining values will be interpolated between the average of the left
     * and right edge regions.
     *
     * @param icenter center of region. If value less than 0 the position of maximum intensity will be used.
     * @param nInterp Number of points within each side of region that will be fully interpolated.
     * @param halfWidth half width of region be zero within region.
     * @throws VecException if vector complex
     */
    public void gapSmooth(int icenter, int nInterp, int halfWidth) throws VecException {
        if (isComplex) {
            throw new VecException("vector must be real");
        }
        if (icenter < 0) {
            IndexValue indexValue = maxIndex();
            icenter = indexValue.index;
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

    /**
     * Return the location and value of the maximum in vector
     *
     * @return IndexValue object with information about the max
     */
    public IndexValue maxIndex() {
        return (maxIndex(0, size - 1));
    }

    /**
     * Return the location and value of the maximum in specified range of vector
     *
     * @param first starting point of range
     * @param last ending point of range
     * @return IndexValue object with information about the max
     */
    public IndexValue maxIndex(int first, int last) {
        double testVal;
        int iMax = 0;

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

        double maxVal = Double.NEGATIVE_INFINITY;

        if (!isComplex) {
            for (int i = first; i <= last; i++) {
                if (rvec[i] > maxVal) {
                    iMax = i;
                    maxVal = rvec[i];
                }
            }
        } else {
            for (int i = first; i <= last; i++) {
                testVal = (cvec[i].getReal() * cvec[i].getReal())
                        + (cvec[i].getImaginary() * cvec[i].getImaginary());

                if (testVal > maxVal) {
                    iMax = i;
                    maxVal = testVal;
                }
            }

            maxVal = Math.sqrt(maxVal);
        }

        return new IndexValue(iMax, maxVal);
    }

    /**
     * Return the location and value of the minimum in vector
     *
     * @return IndexValue object with information about the min
     */
    public IndexValue minIndex() {
        return (minIndex(0, size - 1));
    }

    /**
     * Return the location and value of the minimum in specified range of vector
     *
     * @param first starting point of range
     * @param last ending point of range
     * @return IndexValue object with information about the min
     */
    public IndexValue minIndex(int first, int last) {
        double testVal;
        int iMin = 0;

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

        double minValue = Double.MAX_VALUE;

        if (!isComplex) {
            for (int i = first; i <= last; i++) {
                if (rvec[i] < minValue) {
                    iMin = i;
                    minValue = rvec[i];
                }
            }
        } else {
            for (int i = first; i <= last; i++) {
                testVal = (getReal(i) * getReal(i))
                        + (getImag(i) * getImag(i));

                if (testVal < minValue) {
                    iMin = i;
                    minValue = testVal;
                }
            }

            minValue = Math.sqrt(minValue);
        }

        return new IndexValue(iMin, minValue);
    }

    /**
     * Shift values in vector to right (if shift positive) or left (if shift negative). Unoccupied positions will be set
     * to zero. The size of vector will be expanded to accomadate shifted values so no values will be lost
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
     * Shift values in vector to right (if shift positive) or left (if shift negative). Unoccupied positions will be set
     * to zero. This is not a circular shift so values will be lost.
     *
     * @param shiftValue the number of points to shift vector values by
     */
    public void shift(int shiftValue) {
        if ((shiftValue != 0) && (((int) Math.abs(shiftValue)) < size)) {
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
     * @param a array to store result in
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
     * @param x the frequency position
     * @param b the linewidth
     * @param freq the frequency
     * @return the value at x
     */
    public static double lShape(double x, double b, double freq) {
        b *= 0.5;
        double y = (1.0 / Math.PI) * b / ((b * b) + ((x - freq) * (x - freq)));
        return y;
    }

    static double[][] fillMatrix(final double[] f, final double d[], final int nRows) {
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
     * @param signals the list of signal objects
     * @return this vector
     */
    public Vec fillVec(ArrayList<Signal> signals) {
        makeReal();
        fillVec(rvec, size, signals);
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

    static class OptimizeLineWidth implements UnivariateFunction {

        final double[] signal;
        final Complex[] fd;
        final int[] useColumns;

        OptimizeLineWidth(final double[] signal, final Complex[] fd, final int[] useColumns) {
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
     * Find amplitudes that optimize the fit of signals to an array of intensities.
     *
     * @param x The array of intensities
     * @param fd A complex array whose values represent frequency and decay rate
     * @param useColumns Only use signals whose indexed value is set to true in this array
     * @param winSize Size of window frequencies came from
     * @param uniformWidth If true use the same linewidth for all frequencies
     * @param lineWidth If uniformWidth is true, use this line width
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
        int nMax = AR.getColumnDimension();
        RealMatrix redAR = AR.copy();
        AmplitudeFitResult afR = nnlsFit(redAR, BR.copy());
        System.out.println("nCols " + nCols + " rss " + afR.getRss() + " fit max " + afR.getMaxValue() + " indx " + afR.getMaxIndex() + " lw " + lineWidth);
        return afR;
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
            cwtd((Object) cvec, size, winSize);
        } else {
            cwtd((Object) rvec, size, winSize);
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

        int m = size;
        double[] reVec = new double[m];
        double[] imVec = new double[m];

        double reSum;
        double imSum;
        int halfWin = winSize / 2;
        double scaleCorr = 1.0 / Math.sqrt(winSize);

        for (int i = 0; i < m; i++) {
            reSum = 0.0;
            imSum = 0.0;
            int max = (i + winSize);
            if (max > (m - 1)) {
                max = m - 1;
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
            for (int i = halfWin; i < m; i++) {
                cvec[i] = new Complex(reVec[i - halfWin], imVec[i - halfWin]);
            }
        } else {
            for (int i = 0; i < halfWin; i++) {
                vec[i] = 0.0;
            }
            for (int i = halfWin; i < m; i++) {
                vec[i] = reVec[i - halfWin];
            }
        }
    }

    /**
     * Integrate this vector over the specified range
     *
     * @param first starting point of range
     * @param last ending point of range
     */
    public void integrate(int first, int last) {
        integrate(first, last, 0.0, 0.0);
    }

    /**
     * Integrate this vector over the specified range. Subtract a linear range of values between start and end. The
     * values in vector are replaced with their integral.
     *
     * @param first starting point of range
     * @param last ending point of range
     * @param firstIntensity Starting value for linear baseline
     * @param lastIntensity Ending value for linear baseline
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
     * @param winSize Size of window used in assessing standard deviation
     * @param ratio Threshold ratio of intensities to noise
     * @param regionWidth Minimum width for regions
     * @param joinWidth Regions are joined if separation is less than this
     * @param extend Increase width of region edges by this amount
     * @param minThreshold Threshold is the larger of this and ratio times noise
     * @return matrix of results. Each row is a region. Columns are the start, end and mean intensity.
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
            //System.out.println(j+" "+reVec[j]+" "+sdVec[j]);
        }
        /* Estimate standard deviation from sorted vector */
        Arrays.sort(sdVec);

        // If possible, skip first region (and any near zero in value) to avoid some spectra that have an artificially low value at edge
        rmsd = sdVec[0];

        double threshold = maxValue * 1.0e-8;
        int j = 0;

        if (nRegions > 16) {
            while (j < (nRegions - 2)) {
                //System.out.println(j+" "+threshold+" "+ceVec1.get(j));
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
        //System.out.println(rmsd+" "+ratio+" "+threshold); 
        if ((minThreshold > 0.0) && (threshold > minThreshold)) {
            threshold = minThreshold;
        }
        DescriptiveStatistics dStat = new DescriptiveStatistics(winSize + 1);
        int halfWin = winSize / 2;
        for (int i = 0; i < halfWin; i++) {
            dStat.addValue(rvec[i]);
        }
        for (int i = 0; i < size; i++) {
            if ((i + halfWin) < size) {
                dStat.addValue(rvec[i + halfWin]);
            }
            double regionAvg = dStat.getMean();
            if (Math.abs(rvec[i] - regionAvg) < threshold) {
                lastWasBase = true;
            } else {
                if (lastWasBase) {
                    //System.out.println("base at "+i+" "+vec[i]);
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
        dStat.clear();
        for (int i = 0; i < halfWin; i++) {
            dStat.addValue(rvec[i]);
        }
        for (int i = 0; i < size; i++) {
            if ((i + halfWin) < size) {
                dStat.addValue(rvec[i + halfWin]);
            }
            double regionAvg = dStat.getMean();
            if (Math.abs(rvec[i] - regionAvg) < threshold) { // baseline point

                if (!lastWasBase) {
                    lastNarrow = addedRegion && ((i - begin) < regionWidth); //System.out.println("narrow at "+i);
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
                    //System.out.println("maybe start new at "+i);
                    if ((i - end) > joinWidth) { // start new region
                        //System.out.println("start new at "+i+" "+iIntRegion+" "+lastNarrow);
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
            //System.out.println(i+" "+xyVals.get(i, 0)+" "+ xyVals.get(i, 1)+" "+xyVals.get(i, 2));
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
     * @throws IllegalArgumentException if vectors aren't all the same size and complex
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

    @Override
    public String exportData(String rootName, String suffix) throws IOException {
        return exportData(rootName, suffix, false);
    }

    public String exportData(String rootName, String suffix, boolean littleEndian) throws IOException {
        int index = 0;
        if ((pt != null) && (pt.length > 1)) {
            index = pt[1][0];
        }
        String outFileName = String.format("%s%04d.%s", rootName, index + 1, suffix);

        try (FileOutputStream oStream = new FileOutputStream(outFileName)) {
            int nElem = isComplex ? 2 : 1;
            ByteBuffer byteBuffer = ByteBuffer.allocate(size * Double.SIZE / 8 * nElem);
            if (littleEndian) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            if (isComplex) {
                for (int i = 0; i < size; i++) {
                    doubleBuffer.put(getReal(i));
                    doubleBuffer.put(getImag(i));
                }
            } else {
                doubleBuffer.put(rvec, 0, size);
            }
            FileChannel channel = oStream.getChannel();
            channel.write(byteBuffer);
        } catch (IOException ioE) {
            throw ioE;
        }
        String parFileName = String.format("%s%04d.%s.par", rootName, index + 1, suffix);
        try (FileOutputStream oStream = new FileOutputStream(parFileName)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(2 * Integer.SIZE / 8);
            if (littleEndian) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            IntBuffer intBuffer = byteBuffer.asIntBuffer();
            intBuffer.put(0, 1);
            intBuffer.put(1, size);
            FileChannel channel = oStream.getChannel();
            channel.write(byteBuffer);
        } catch (IOException ioE) {
            throw ioE;
        }
        return outFileName;
    }

    public void dump() throws IOException {
        dump(null);
    }

    public void dump(String outName) throws IOException {

        FileWriter fileWriter = null;
        if (outName != null) {
            fileWriter = new FileWriter(outName);
        }
        if (fileWriter != null) {
            for (int i = 0; i < size; i++) {
                if (isComplex) {
                    fileWriter.write(String.format("%3d %.5f %.5f\n", i, getReal(i), getImag(i)));
                } else {
                    fileWriter.write(String.format("%3d %.5f\n", i, getReal(i)));
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (isComplex) {
                    System.out.printf(String.format("%3d %.5f %.5f\n", i, getReal(i), getImag(i)));
                } else {
                    System.out.printf(String.format("%3d %.5f\n", i, getReal(i)));
                }
            }
        }

        if (fileWriter != null) {
            fileWriter.close();
        }
    }

    public String importData(String rootName, String suffix) throws IOException {
        return importData(rootName, suffix, false);
    }

    public String importData(String rootName, String suffix, boolean littleEndian) throws IOException {
        int index = 0;
        if ((pt != null) && (pt.length > 1)) {
            index = pt[1][0];
        }
        String inFileName = String.format("%s%04d.%s", rootName, index + 1, suffix);

        try (FileInputStream oStream = new FileInputStream(inFileName)) {
            int nElem = isComplex ? 2 : 1;
            ByteBuffer byteBuffer = ByteBuffer.allocate(size * Double.SIZE / 8 * nElem);
            if (littleEndian) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            FileChannel channel = oStream.getChannel();
            channel.read(byteBuffer);
            if (isComplex) {
                for (int i = 0; i < size; i++) {
                    double rValue = doubleBuffer.get(i * 2);
                    double iValue = doubleBuffer.get(i * 2 + 1);
                    setComplex(i, rValue, iValue);
                }

            } else {
                doubleBuffer.get(rvec);
            }
        } catch (IOException ioE) {
            throw ioE;
        }
        return inFileName;
    }

}
