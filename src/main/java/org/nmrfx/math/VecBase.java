package org.nmrfx.math;

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.VecException;
import org.python.core.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VecBase extends PySequence {

    public static final PyType ATYPE = PyType.fromClass(VecBase.class);
    private static Map<String, VecBase> vecMap = new HashMap<>();
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
    /**
     *
     */
    public double refValue = 0.0;
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
    String name = "";

    /**
     * Create a new named Vec object with the specified size and complex mode.
     *
     * @param name Name of the vector. Used for retrieving vectors by name.
     * @param size Size of vector.
     * @param complex true if the data stored in vector is Complex
     */
    public VecBase(int size, String name, boolean complex) {
        this(size, complex);
        this.name = name;
    }

    /**
     * Create a new Vec object with the specified size and complex mode.
     *
     * @param size Size of vector.
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
     * Create a new Vec object for real data and with the specified size.
     *
     * @param size Size of vector.
     */
    public VecBase(int size) {
        this(size, false);
    }

    public VecBase(double[] values) {
        this(values.length, false);
        System.arraycopy(values, 0, rvec, 0, values.length);
    }

    /**
     * Create a new Vec object for real data and with the specified size and
     * specified dataset location.
     *
     * @param size Size of vector.
     * @param pt dataset location
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
     * @param size Size of vector.
     * @param pt dataset location
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
     * @return vector with the specified name (or null if it doesn't exist)
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
        ArrayList<String> names = new ArrayList<>();
        names.addAll(vecMap.keySet());
        return names;
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
     * Return real or imaginary value at specified index. It's preferred to use
     * getReal or getImag unless choice of real or imaginary needs to be made
     * programmatically as its easy to use make errors by wrong choice of
     * true/false with this method.
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
     * Set complex value at specified index. If vector is real, only use the
     * real part of value.
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
     * Set complex value at specified index. If vector is real, only use the
     * real part of value.
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
}
