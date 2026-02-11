package org.nmrfx.math;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.ArithmeticUtils;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.datasets.DatasetLayout;
import org.nmrfx.datasets.DatasetStorageInterface;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.math.units.*;
import org.python.core.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VecBase extends PySequence implements MatrixType, DatasetStorageInterface {

    public static final PyType ATYPE = PyType.fromClass(VecBase.class);
    private static final Map<String, VecBase> vecMap = new HashMap<>();

    /**
     * Array of doubles used for storing data when the Vec is real or the real
     * part of complex data when a Complex array is not used
     */
    public double[] rvec;
    /**
     * Array of doubles used for storing imaginary values when the Vec is
     * Complex and a Complex array is not used
     */
    public double[] ivec;
    /**
     *
     */
    public Complex[] cvec;
    /**
     *
     */
    public double dwellTime = 1.0;
    /**
     *
     */
    public double centerFreq = 1.0;
    private double refValue = 0.0;
    // number of valid data values in arrays.
    protected int size;
    // original size of time domain data (need to keep track of this for undoing zero filling)
    protected int tdSize;
    /**
     * Does this vector have an imaginary part? If true, ivec or cvec exist.
     */
    protected boolean isComplex;
    /**
     * Flag of whether to use cvec or rvec/ivec when the Vec is complex.
     */
    protected boolean useApache;
    protected boolean freqDomain;
    /**
     * Location in dataset that the vector was read from, or should be written
     * to.
     */
    protected int[][] pt;
    /**
     * Dimensions in dataset that the vector was read from, or should be written
     * to.
     */
    protected int[] dim;
    protected double ph0 = 0.0;
    protected double ph1 = 0.0;
    protected int zfSize;
    protected int extFirst;
    protected int extLast;
    protected double groupDelay = 0.0;
    protected boolean[] inSignalRegion = null;
    String name = "";

    /**
     * Create a new named Vec object with the specified size and complex mode.
     *
     * @param name    Name of the vector. Used for retrieving vectors by name.
     * @param size    Size of vector.
     * @param complex true if the data stored in vector is Complex
     */
    public VecBase(int size, String name, boolean complex) {
        this(size, complex);
        this.name = name;
    }

    /**
     * Create a new named Vec object with the specified size and complex mode.
     *
     * @param name    Name of the vector. Used for retrieving vectors by name.
     * @param size    Size of vector.
     * @param complex true if the data stored in vector is Complex
     */
    public VecBase(int size, String name, boolean complex, PyType type) {
        this(size, complex, type);
        this.name = name;
    }

    /**
     * Create a new Vec object with the specified size and complex mode.
     *
     * @param size    Size of vector.
     * @param complex true if the data stored in vector is Complex
     */
    public VecBase(int size, boolean complex) {
        super(ATYPE);
        this.isComplex = complex;
        useApache = true;
        rvec = new double[size];
        freqDomain = false;

        resize(size, complex);
        tdSize = size;
    }

    /**
     * Create a new Vec object with the specified size and complex mode.
     *
     * @param size    Size of vector.
     * @param complex true if the data stored in vector is Complex
     */
    public VecBase(int size, boolean complex, PyType type) {
        super(type);
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
    public VecBase(int size) {
        this(size, false);
    }

    /**
     * Create a new Vec object for real data and with the specified size.
     *
     * @param size Size of vector.
     */
    public VecBase(int size, PyType type) {
        this(size, false, type);
    }

    public VecBase(double[] values) {
        this(values.length, false);
        System.arraycopy(values, 0, rvec, 0, values.length);
    }

    public VecBase(double[] realValues, double[] imaginaryValues) throws IllegalArgumentException {
        this(realValues.length, true);
        if (realValues.length != imaginaryValues.length) {
            throw new IllegalArgumentException("Real and imaginary values have different dimensions.");
        }
        ivec = new double[imaginaryValues.length];
        System.arraycopy(realValues, 0, rvec, 0, realValues.length);
        System.arraycopy(imaginaryValues, 0, ivec, 0, imaginaryValues.length);
        // Set useApache to false since ivec is being used.
        useApache = false;
        this.isComplex = true;
    }

    /**
     * Create a new Vec object for real data and with the specified size and
     * specified dataset location.
     *
     * @param size Size of vector.
     * @param pt   dataset location
     */
    public VecBase(int size, int[][] pt, int[] dim) {
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
     * Create a new Vec object with the specified size, complex mode and dataset
     * location
     *
     * @param size    Size of vector.
     * @param pt      dataset location
     * @param complex true if vector stores complex data
     */
    public VecBase(int size, int[][] pt, int[] dim, boolean complex) {
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
     * Return a vector from the map of named and stored vectors
     *
     * @param name lookup vector with this name
     * @return vector with the specified name (or null if it doesn't exist)
     */
    public static VecBase get(String name) {
        return vecMap.get(name);
    }

    /**
     * Return a vector from the map of named and stored vectors
     *
     * @param name lookup vector with this name
     */
    public static void put(String name, VecBase vec) {
        vecMap.put(name, vec);
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
        return new ArrayList<>(vecMap.keySet());
    }

    public DatasetLayout getLayout() {
        return null;
    }

    /**
     * Copy the dataset location of one vector to that of another vector
     *
     * @param inVec  source vector
     * @param outVec target vector
     */
    public static void copyLocation(VecBase inVec, VecBase outVec) {
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
     * Copy one complex array to another. Number of values copies is the smaller
     * of the two vector sizes. The target vector is not resized.
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

    /**
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
     * Copy the reference information from one vector to another vector.
     *
     * @param source the source vector
     * @param target the target vector
     */
    static public void copyRef(VecBase source, VecBase target) {
        target.dwellTime = source.dwellTime;
        target.centerFreq = source.centerFreq;
        target.refValue = source.refValue;
        target.freqDomain = source.getFreqDomain();
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
        VecBase.copyLocation(source, target);
    }

    /**
     * Add a one array to a multiple of a second array and store in a third
     * array
     *
     * @param avec  first array
     * @param size  number of values to add
     * @param bvec  multiply these values by scale before adding to avec
     * @param scale factor to multiply by
     * @param cvec  store result
     */
    public static void addMulVector(double[] avec, int size, double[] bvec, double scale, double[] cvec) {
        int i;

        for (i = 0; i < size; i++) {
            cvec[i] = avec[i] + bvec[i] * scale;
        }
    }

    /**
     * Add a one vector to a multiple of a second vector and store in a third
     * vector
     *
     * @param avec  first vector
     * @param size  number of values to add
     * @param bvec  multiply these values by scale before adding to avec
     * @param scale factor to multiply by
     * @param cvec  store result
     */
    public static void addMulVector(VecBase avec, int size, VecBase bvec, double scale,
                                    VecBase cvec) {
        int i;

        for (i = 0; i < size; i++) {
            double dReal = avec.getReal(i) + bvec.getReal(i) * scale;
            double dImaginary = avec.getImag(i) + bvec.getImag(i) * scale;
            cvec.set(i, new Complex(dReal, dImaginary));
        }
    }

    private static double sumVector(double[] vec, int size) {
        int i;
        double sum = 0.0;
        for (i = 0; i < size; i++) {
            sum += vec[i];
        }
        return sum;
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

        VecBase vecNew = new VecBase(newSize, this.isComplex);
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
     * Return true if values in vector are Complex.
     *
     * @return true if complex
     */
    public boolean isComplex() {
        return isComplex;
    }

    /**
     * Resize a vector and set the complex mode. Existing data will be preserved
     * (up to smaller of new and old sizes).
     *
     * @param newSize the new size of vector
     * @param complex true if the vector should be complex
     */
    public final void resize(int newSize, boolean complex) {
        isComplex = complex;
        resize(newSize);
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
         * If we added points new points to the Vec that contain "junk" data,
         * zero them.
         */
        if (size < newsize) {
            //this isn't an adequate way to know what's newly allocated
            this.zeros(size, newsize - 1);
        }
        this.size = newsize;
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

    public void expandRvec(int newsize) {
        double[] newarr = new double[newsize];
        if (rvec == null) {
            rvec = newarr;
        } else {
            //copy rvec from 0 to size
            //(because from size to rvec.length is junk data that we don't want)
            int n = Math.min(newsize, rvec.length);
            System.arraycopy(rvec, 0, newarr, 0, n);
        }
        rvec = newarr;
    }

    public void expandIvec(int newsize) {
        double[] newarr = new double[newsize];
        if (ivec == null) {
            ivec = newarr;
        } else {
            int n = Math.min(newsize, ivec.length);
            System.arraycopy(ivec, 0, newarr, 0, n);
        }
        ivec = newarr;
    }

    /**
     * Set values in a range to 0.0
     *
     * @param first first point of range
     * @param last  last point of range
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
     * Set the zeroth order phase correction that has been applied to this
     * vector.
     *
     * @param p0 the phase value
     */
    public void setPh0(double p0) {
        ph0 = p0;
    }

    /**
     * Set the first order phase correction that has been applied to this
     * vector.
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
     * Get an array of the real values of this vector. The values are copied so
     * changes in the returned array do not effect this vector.
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
     * Get an array of the real values in a region of this vector. The values
     * are copied so changes in the returned array do not effect this vector.
     *
     * @param values a double array in which to put the real values
     * @param start  the starting position of the Vec at which to read values
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
     * Return real or imaginary value at specified index. It's preferred to use
     * getReal or getImag unless choice of real or imaginary needs to be made
     * programmatically as its easy to use make errors by wrong choice of
     * true/false with this method.
     *
     * @param index position of value
     * @param imag  true to get imaginary, false to get real
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
                    + index + " in a Vec of size "
                    + size);
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
                    + index + " in a Vec of size "
                    + size);
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
                    + index + " in a Vec of size "
                    + size);
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
                    + index + " in a Vec of size "
                    + size);
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
                    + index + " in a Vec of size "
                    + size);
        }
    }

    /**
     * Set complex value at specified index. If vector is real, only use the
     * real part of value.
     *
     * @param index   position to set
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
                    + index + " in a Vec of size "
                    + size);
        }
    }

    /**
     * Set complex value at specified index. If vector is real, only use the
     * real part of value.
     *
     * @param index position to set
     * @param real  the real part to set
     * @param imag  the imaginary part to set
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
                    + index + " in a Vec of size "
                    + size);
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
     * Return an array of bytes that represent the single precision floating
     * point values of this vector. Note: values are normally stored in a Vec
     * object in double precision format so there will be some loss of
     * precision.
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
     * Set a real element of vector to a specified value.
     *
     * @param i   The element of the vector to set, which can be any number from 0
     *            to 'size - 1' of the Vec.
     * @param val value to set at specified index
     */
    public void set(int i, double val) {
        if (i < size && i >= 0) {
            rvec[i] = val;
        } else {
            throw new IllegalArgumentException("Cannot set element "
                    + i + " in a Vec of size "
                    + size);
        }
    }

    /**
     * Set the i'th element of the real and complex parts of the vector.
     *
     * @param i    The element of the vector to set, which can be any number from 0
     *             to 'size - 1' of the Vec.
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
                        + i + " in a Vec of size "
                        + size);
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
                        + i + " in a Vec of size "
                        + size);
            }

        } else {
            throw new IllegalArgumentException("Cannot set imaginary part "
                    + "of a Real Vector");
        }
    }

    /**
     * Set the dataset location pt for this vector
     *
     * @param pt  the new location
     * @param dim dataset dimensions for point location
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

    @Override
    public void setWritable(boolean state) {
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public long bytePosition(int... offsets) {
        return offsets[0] * Double.BYTES;
    }

    @Override
    public long pointPosition(int... offsets) {
        return offsets[0];
    }

    @Override
    public int getSize(int dim) {
        return size;
    }

    @Override
    public long getTotalSize() {
        return size;
    }

    @Override
    public float getFloat(int... offsets) throws IOException {
        return (float) getReal(offsets[0]);
    }

    @Override
    public void setFloat(float value, int... offsets) throws IOException {
        setReal(offsets[0], value);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public double sumValues() throws IOException {
        return sumFast();
    }

    @Override
    public double sumFast() throws IOException {
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            sum += getReal(i);
        }
        return sum;
    }

    @Override
    public void zero() throws IOException {
        zeros();
    }

    @Override
    public void force() {
    }

    /**
     * Print the location value (for reading/writing to datasets) for this
     * vector if it is set
     */
    public void printLocation() {
        if (pt != null) {
            for (int[] pt1 : pt) {
                System.out.print(pt1[0] + " " + pt1[1] + " ");
            }
            System.out.println();
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
    public void copyLocation(VecBase target) {
        VecBase.copyLocation(this, target);
    }

    /**
     * Copy contents of this vector to another vector. The target will be
     * resized and the complex mode changed to agree with this vector.
     *
     * @param target the target vector
     */
    public void copy(VecBase target) {
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
        VecBase.copyRef(this, target);
    }

    /**
     * Copy a portion of one vector to another vector.
     *
     * @param target the target vector
     * @param start  copy starting at this index
     * @param length copy this number of values
     */
    public void copy(VecBase target, int start, int length) {
        copy(target, start, 0, length);

    }

    /**
     * Copy portion of one vector to another
     *
     * @param target  the target vector
     * @param start   copy starting at this index
     * @param destPos starting position in target vector
     * @param length  copy this number of values
     */
    public void copy(VecBase target, int start, int destPos, int length) {
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
        VecBase.copyRef(this, target);
    }

    /**
     * Copy the reference information from this vector to another vector.
     *
     * @param target the target vector
     */
    public void copyRef(VecBase target) {
        VecBase.copyRef(this, target);
    }

    /**
     * Adjust the reference value because the vector was resized and/or points
     * at beginning removed
     *
     * @param shift   the starting position of new range.
     * @param newSize the new size of the vector
     */
    public void adjustRef(double shift, int newSize) {
        double newCenter = shift + newSize / 2.0;
        double deltaPt = size / 2.0 - newCenter;
        double delRef = ((deltaPt / (dwellTime * centerFreq)) / (size));
        refValue += delRef;
        dwellTime = (dwellTime * size) / (newSize);
    }

    /**
     *
     */
    public double getRefValue() {
        return refValue;
    }

    /**
     *
     */
    public double getRefValue(int refPt) {
        return refValue + getDeltaRef(refPt);
    }

    public double getZeroRefValue() {
        return refValue + getDeltaRef(0.0);
    }

    public void setRefValue(double refValue) {
        this.refValue = refValue;
    }

    public void setRefValue(double refValue, double refPt) {
        this.refValue = refValue - getDeltaRef(refPt);
    }

    public void setZeroRefValue(double refValue) {
        this.refValue = refValue - getDeltaRef(0.0);
    }

    public double getDeltaRef(double refPt) {
        double deltaFrac = 0.5 - refPt / size;
        return (deltaFrac / dwellTime) / centerFreq;
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
    public VecBase add(double addValue) {
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
     * Add a Complex value to this vector. The vector will be made complex if it
     * is not already.
     *
     * @param addValue the value to add
     * @return this vector
     */
    public VecBase add(Complex addValue) {
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
     * Add a vector to this vector. The vectors may be of different lengths. The
     * number of values added will be the smaller of the sizes of the two
     * vectors
     *
     * @param v2 the vector to add to this vector
     */
    public void add(VecBase v2) {
        int i;
        int sz = v2.getSize();
        sz = Math.min(sz, size);
        if (isComplex) {
            if (useApache) {
                v2.makeApache();
                Complex[] cvec2 = v2.getCvec();
                for (i = 0; i < sz; i++) {
                    cvec[i] = cvec[i].add(cvec2[i]);
                }
            } else {
                double[] rvec2 = v2.getRvec();
                double[] ivec2 = v2.getIvec();
                for (i = 0; i < sz; i++) {
                    rvec[i] += rvec2[i];
                    ivec[i] += ivec2[i];
                }
            }
        } else {
            double[] rvec2 = v2.getRvec();
            for (i = 0; i < sz; i++) {
                rvec[i] += rvec2[i];
            }
        }
    }

    /**
     * Add an array of values to this vector. Values must implement Java Number
     * interface. The number of values does not have to equal the number of
     * values between the start and end points in this vector. Interpolation of
     * the values to be added will be done to find the value to add at each
     * point. The values can be multiplied by a scale value before addition.
     *
     * @param addValue The values to add
     * @param start    the starting point in this vector
     * @param end      the ending point in this vector
     * @param scale    multiply values to be added by this scale factor.
     * @param lb       unused at present
     * @return this vector
     */
    public VecBase add(Object[] addValue, final double start, final double end, final double scale, final double lb) {
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
     * @param avec  the vector to add
     * @param scale multiply values to add by this amount
     * @return this vector
     */
    public VecBase addmul(VecBase avec, double scale) {

        if (isComplex) {
            VecBase.addMulVector(this, size, avec, scale, this);
        } else {
            VecBase.addMulVector(this.rvec, size, avec.rvec, scale, this.rvec);
        }

        return (this);
    }

    /**
     * Subtract real value from this vector
     *
     * @param subValue value to subtract
     * @return this vector
     */
    public VecBase sub(double subValue) {
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
     * Subtract Complex value from this vector. Vector will be converted to
     * Complex if it is not already.
     *
     * @param subValue value to subtract
     * @return this vector
     */
    public VecBase sub(Complex subValue) {
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
    public void sub(VecBase v2) {
        int i;
        int sz = v2.getSize();
        sz = Math.min(sz, size);
        if (isComplex) {
            if (useApache) {
                v2.makeApache();
                Complex[] cvec2 = v2.getCvec();
                for (i = 0; i < sz; i++) {
                    cvec[i] = cvec[i].subtract(cvec2[i]);
                }
            } else {
                double[] rvec2 = v2.getRvec();
                double[] ivec2 = v2.getIvec();
                for (i = 0; i < sz; i++) {
                    rvec[i] -= rvec2[i];
                    ivec[i] -= ivec2[i];
                }
            }
        } else {
            double[] rvec2 = v2.getRvec();
            for (i = 0; i < sz; i++) {
                rvec[i] -= rvec2[i];
            }
        }
    }

    /**
     * Divide this vector by a Complex value. If vector is not already Complex,
     * it will be converted to Complex.
     *
     * @param divisor divide by this value
     * @return this vector
     */
    public VecBase divide(Complex divisor) {
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
    public VecBase divide(double divisor) {
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
    public VecBase divide(VecBase divVec) {
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
     * Replace values in this vector by the specified value divided by the
     * current value. If the vector is not complex, make it so.
     *
     * @param value divide by this value
     * @return this vector
     */
    public VecBase rdivide(Complex value) {
        if (!isComplex) {
            makeApache();
        }
        for (int i = 0; i < size; i++) {
            set(i, value.divide(getComplex(i)));
        }

        return (this);
    }

    /**
     * Replace values in this vector by the specified value divided by the
     * current value.
     *
     * @param value divide this value by current values
     * @return this vector
     */
    public VecBase rdivide(double value) {
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

    public void decay(double lb, double gb, double fLorentzian) {
        for (int i = 0; i < size; i++) {
            double expDecay = Math.exp(-i * lb * Math.PI * dwellTime);
            double g = i * gb * dwellTime;
            double gaussDecay = Math.exp(-(g * g));
            double v = fLorentzian * expDecay + (1.0 - fLorentzian) * gaussDecay;
            multiply(i, v, v);
        }
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
     * Gets the norm of the vector, computed as the square root of the sum of
     * the squares.
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
     * Return whether vector is real
     *
     * @return true if vector real
     */
    public boolean isReal() {
        return !isComplex;
    }

    /**
     * Make the vector real. If vector was complex, the previous real values
     * become real components of new values.
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
     * Make the vector complex if not, and set the imaginary values to zero.
     */
    public void zeroImag() {
        resize(size, true);
        for (int i = 0; i < size; ++i) {
            setImag(i, 0.0);
        }
    }
    /**
     * Make the vector complex if not, and set the imaginary values to zero.
     */
    public void zeroReal() {
        resize(size, true);
        for (int i = 0; i < size; ++i) {
            setReal(i, 0.0);
        }
    }

    /**
     * Make the vector complex. If vector was real, the previous real values
     * become real components of new values.
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
     * If vector is complex and stores real/imag in separate arrays, change to
     * use an array of Complex.
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
     * If vector is complex and stores complex values in an array of Complex,
     * change to store in separate arrays of real and imaginary values.
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
     * Return true if vector is complex and values are stored in Complex objects
     * (not real and imaginary vectors)
     *
     * @return true if values stored as Apache Commons Mat Complex objects
     */
    public boolean useApache() {
        return useApache;
    }

    /**
     * Scale values in vector by multiplying by specified value.
     *
     * @param scaleValue multiply by this value
     * @return this vector
     */
    public VecBase scale(double scaleValue) {
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
     * @return the sum of points as a Complex number (whose imaginary part is
     * real if the vector is real)
     */
    public Complex sum() {
        final Complex result;
        if (isComplex) {
            result = sumVector(size);
        } else {
            double sum = VecBase.sumVector(this.rvec, size);
            result = new Complex(sum, 0.0);
        }

        return result;
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

    public String toLine(String format) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i=0;i<size;i++) {
            if (i > 0) {
                stringBuilder.append(" ");
            }
            stringBuilder.append(String.format(format,getReal(i)));
            if (isComplex) {
                stringBuilder.append(" ");
                stringBuilder.append(String.format(format,getImag(i)));
            }
        }
        return stringBuilder.toString();
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
        temp.append(size);

        return temp.toString();
    }

    @Override
    public int __len__() {
        return size;
    }

    @Override
    public VecBase __radd__(PyObject pyO) {
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
    public VecBase __add__(PyObject pyO) {
        VecBase vecNew = new VecBase(this.getSize(), this.isComplex);
        this.copy(vecNew);
        if (pyO instanceof VecBase vec) {
            //  fixme check sizes
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
    public VecBase __iadd__(PyObject pyO) {
        if (pyO instanceof VecBase vec) {
            //  fixme check sizes
            this.add(vec);
        } else if (pyO instanceof PyComplex pyComplex) {
            this.add(toComplex(pyComplex));
        } else if (pyO.isNumberType()) {
            this.add(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '+=' to object: " + pyO.getType().asString());
        }
        return this;
    }

    @Override
    public VecBase __rsub__(PyObject pyO) {
        if (pyO instanceof VecBase) {
            return ((VecBase) pyO).__sub__(this);
        } else {
            VecBase vecNew = new VecBase(this.getSize(), this.isComplex);
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
    public VecBase __sub__(PyObject pyO) {
        VecBase vecNew = new VecBase(this.getSize(), this.isComplex);
        this.copy(vecNew);
        if (pyO instanceof VecBase vec) {
            //  fixme check sizes
            vecNew.sub(vec);
        } else if (pyO instanceof PyComplex pyC) {
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
    public VecBase __isub__(PyObject pyO) {
        if (pyO instanceof VecBase vec) {
            //  fixme check sizes
            this.sub(vec);
        } else if (pyO instanceof PyComplex pyC) {
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
    public VecBase __rmul__(PyObject pyO) {
        return __mul__(pyO);
    }

    @Override
    public VecBase __mul__(PyObject pyO) {
        VecBase vecNew = new VecBase(this.getSize(), this.isComplex);
        this.copy(vecNew);

        if (pyO instanceof VecBase vec) {
            //  fixme check sizes
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
    public VecBase __imul__(PyObject pyO) {

        if (pyO instanceof VecBase vec) {
            //  fixme check sizes
            this.multiply(vec);
        } else if (pyO instanceof PyComplex pyComplex) {
            if (!this.isComplex) {
                this.makeApache();
            }
            this.multiply(toComplex(pyComplex));
        } else if (pyO.isNumberType()) {
            this.scale(pyO.asDouble());
        } else {
            throw Py.TypeError("can't apply '*' to object: " + pyO.getType().asString());
        }
        return this;
    }

    @Override
    public VecBase __rdiv__(PyObject pyO) {
        if (pyO instanceof VecBase) {
            return ((VecBase) pyO).__div__(this);
        } else {
            VecBase vecNew = new VecBase(this.getSize(), this.isComplex);
            this.copy(vecNew);
            if (pyO instanceof PyComplex pyComplex) {
                vecNew.rdivide(toComplex(pyComplex));
            } else if (pyO.isNumberType()) {
                vecNew.rdivide(pyO.asDouble());
            } else {
                throw Py.TypeError("can't apply '/' to object: " + pyO.getType().asString());
            }
            return vecNew;
        }
    }

    @Override
    public VecBase __div__(PyObject pyO) {
        VecBase vecNew = new VecBase(this.getSize(), this.isComplex);
        this.copy(vecNew);
        if (pyO instanceof VecBase vec) {
            //  fixme check sizes
            vecNew.divide(vec);
        } else if (pyO instanceof PyComplex pyC) {
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
    public VecBase __idiv__(PyObject pyO) {
        if (pyO instanceof VecBase vec) {
            //  fixme check sizes
            this.divide(vec);
        } else if (pyO instanceof PyComplex pyC) {
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
     * Set the values of this vector to be those in the provided Complex array.
     * Size of this vector will not be changed. The number of values used will
     * be the minimum of the size of vector and array
     *
     * @param newVec the complex values to set
     * @return this vector
     */
    public VecBase setComplex(Complex[] newVec) {
        if (!isComplex) {
            isComplex = true;
        }
        if (!useApache) {
            makeApache();
        }
        if ((cvec == null) || (cvec.length < size)) {
            cvec = new Complex[size];
        }
        int n = Math.min(newVec.length, size);
        System.arraycopy(newVec, 0, cvec, 0, n);
        return (this);
    }

    /**
     * Return the array of Complex values. Array is not copied so changes in
     * returned array will change the vector values.
     *
     * @return the Complex value array
     * @throws IllegalStateException if the vector is not Complex or doesn't use
     *                               a Complex array
     */
    public Complex[] getCvec() {
        if (isComplex && useApache) {
            return cvec;
        } else {
            throw new IllegalVecState(isComplex, useApache, true, true);
        }
    }

    /**
     * Return the array of double values that store real values. Array is not
     * copied so changes in returned array will change the vector values.
     *
     * @return the array of doubles that stores real values
     * @throws IllegalStateException if the vector is Complex and doesn't use
     *                               Complex array
     */
    public double[] getRvec() {
        if (!(isComplex && useApache)) {
            return rvec;
        } else {
            throw new IllegalVecState(false, true);
        }
    }

    /**
     * Return the array of double values that store imaginary values. Array is
     * not copied so changes in returned array will change the vector values.
     *
     * @return the array of doubles that stores imaginary values
     * @throws IllegalStateException if the vector is not Complex or uses
     *                               Complex array
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
     * Convert PPM value to delta position
     *
     * @param ppm position in ppm
     * @return position in points
     */
    public double getDoubleDelta(PPM ppm) {
        return ppm.doubleValue() * centerFreq * dwellTime * size;

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
        return (int) (refToPtD(ref) + 0.5);
    }

    /**
     * Convert position in points to PPM
     *
     * @param pt position in points
     * @return position in PPM
     */
    public double pointToPPM(double pt) {
        return (-(pt - size / 2.0)) / (centerFreq * dwellTime * size) + refValue;
    }

    /**
     * Convert position in reference units (chemical shift typically) to
     * position in points.
     *
     * @param ref position to convert
     * @return position in points
     */
    public double refToPtD(double ref) {
        return ((refValue - ref) * centerFreq * dwellTime * size) + size / 2.0;
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
     * Check if the size is a power of 2, if not resize the Vector so it is a
     * power of 2 in length
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

    public static double phaseMin(double ph) {
        while (ph > 180) {
            ph -= 360.0;
        }
        while (ph < -180) {
            ph += 360.0;
        }
        return ph;
    }

    /**
     * Apply the specified phase values to this vector.
     *
     * @param phases The phase values as an array
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
     * @param p0               The zeroth order phase value
     * @param p1               The first order phase value
     * @param phaseAbs         if false apply the specified values, if true subtract the
     *                         currently stored ph0 and ph1 values from the specified values and then
     * @param discardImaginary Discard the imaginary values and convert vector
     *                         to real. Phasing is a little faster if you do this (and saves calling a
     *                         seperate REAL operation.
     * @return this vector
     */
    public VecBase phase(double p0, double p1, boolean phaseAbs, boolean discardImaginary) {
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
            ph0 = phaseMin(ph0 + p0);

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
        ph0 = phaseMin(ph0 + p0);
        ph1 = ph1 + p1;

        return (this);
    }

    /**
     * Apply the specified phase values to this vector. This method can be used
     * when applying the same phase to multiple vectors. The phase corrections
     * are pre-calculated based on p0 and p1 and then applied to this method in
     * the pReal and pImag arguments. The specified p0 and p1 values are only
     * used here for updating the vector header, but are the values used for
     * setting up pReal and pImag.
     *
     * @param p0               The zeroth order phase value.
     * @param p1               The first order phase value from the specified values and then
     * @param discardImaginary Discard the imaginary values and convert vector
     *                         to real. Phasing is a little faster
     * @param pReal            Array of real values of phase corrections
     * @param pImag            Array of imaginary values of phase corrections
     * @return this vector
     */
    public VecBase phase(double p0, double p1, boolean discardImaginary, double[] pReal, double[] pImag) {

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
        ph0 = phaseMin(ph0 + p0);
        ph1 = ph1 + p1;

        return (this);
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
     * @param index  position to multiply
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
                    + index + " element of a Vec of size "
                    + size);
        }
    }

    /**
     * Multiply either rvec[index] and ivec[index] or cvec[index] by
     * (realFactor, imagFactor)
     *
     * @param index      position to multiply
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
                    + index + " of a Vec of size "
                    + size);
        }
    }

    /**
     * Multiply the values of this vector by those in another vector.
     *
     * @param mulVec multiply by this vector
     * @return this vector
     */
    public VecBase multiply(VecBase mulVec) {
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
     * Transform a Complex Vec into a real Vec, setting each point to the
     * Imaginary value.
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
     * Performs multiplication on the rvec and ivec elements at index with the
     * complex number (realFactor, imagFactor).
     *
     * @param imagFactor imaginary part of factor
     * @literal { Caller guarantees that 0 <= index < size, and the matrix is complex.}
     * @index position to multiply
     * @realFactor real part of factor
     */
    private void multiplyValue(int index, double realFactor, double imagFactor) {
        rvec[index] = rvec[index] * realFactor - ivec[index] * imagFactor;
        ivec[index] = rvec[index] * imagFactor + ivec[index] * realFactor;
    }

    /**
     * Sum real values in a region after applying a zeroth order phase
     * correction. Used in the autophase by max method.
     *
     * @param first start of region
     * @param last  end of region
     * @param p0    phase value
     * @return sum of values
     */
    protected double sumRealRegion(int first, int last, double p0) {
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
     * Copy an array of real values into vector. If the vector is smaller than
     * size of array it will be resized up to the size of the array.
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
     * Copy an array of real values and array of imaginary values into vector.
     * If the vector is smaller than size of array it will be resized up to the
     * size of the array.
     *
     * @param values  the array of real values values
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
     * Get the location for reading/writing this Vec from/to a dataset.
     *
     * @return the location
     */
    public int[][] getPt() {
        int[][] ptCopy = null;
        if (pt != null) {
            ptCopy = new int[pt.length][];
            for (int i = 0; i < pt.length; i++) {
                ptCopy[i] = pt[i].clone();
            }
        }

        return ptCopy;
    }

    /**
     * Get the dimensions for reading/writing this Vec from/to a dataset.
     *
     * @return the dimension
     */
    public int[] getDim() {
        int[] newDim = null;
        if (dim != null) {
            newDim = dim.clone();
        }
        return newDim;
    }

    public VecBase extract(int start, int end) {
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
     * @param start   Starting point in original size
     * @param newSize Size after trimming
     * @return this vector
     * @throws VecException if start is out of range of vector or vector doesn't
     *                      have valid data arrays
     */
    public VecBase trim(int start, int newSize)
            throws VecException {
        int end = (start + newSize) - 1;

        if ((start < 0) || (end >= size) || (start > end)) {
            throw new VecException("trim: error in parameters");
        }

        if (getFreqDomain()) {
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

            if (newSize >= 0) {
                System.arraycopy(rvec, start, rvec, 0, newSize);
            }
        }

        size = newSize;

        return (this);
    }

    /**
     * Subtract a vector from this vector. FIXME no check for size
     * compatability.
     *
     * @param subVec the vector to subtract
     * @return this vector
     */
    public VecBase subtract(VecBase subVec) {
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
     * Return whether vector is in frequency domain
     *
     * @return true if vector in frequency domain
     */
    public boolean freqDomain() {
        return getFreqDomain();
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
     * @param last  ending point of range
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
     * @param last  ending point of range
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
        try (FileWriter fw = fileWriter) {
            for (int i = 0; i < size; i++) {
                String dump = isComplex ? String.format("%3d %.5f %.5f%n", i, getReal(i), getImag(i)) : String.format("%3d %.5f%n", i, getReal(i));
                if (fw != null) {
                    fw.write(dump);
                } else {
                    System.out.println(dump);
                }
            }
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

    /**
     * Objects of this class store an index and value. Typically used for
     * getting the location and value of the maximum or minimum in the vector.
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

    public double[] autoPhase(boolean doFirst, int winSize, double ratio, int mode, double ph1Limit, double negativePenalty) {
        return null;
    }
}
