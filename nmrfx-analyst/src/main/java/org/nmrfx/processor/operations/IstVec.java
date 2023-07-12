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
import org.nmrfx.processor.math.IstMath;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;

/**
 * Iterative Soft Thresholding (IST) is used to process non-uniformly sampled data sets, acquired with a particular
 * SampleSchedule. It repeats a loop of
 * <i>fft-cutoff-ift-rezero</i> until a criterion is met, either a termination condition or total number of loops
 * complete.
 * <p>
 * The IST method is implemented following Hyberts et al., J. Biomol. NMR 52, 315 (2012).
 * <p>
 * Several cutoff algorithms are available with an optional <i>alg</i>
 * parameter:
 * <ul>
 * <li> 'abs' or 'absolute' : absolute value above a threshold (default)
 * <li> 'phased' : cutoff of positive signals above or negative signals below a threshold
 * <li> 'phasedpos' : cutoff of positive signals above a threshold only
 * </ul>
 * If 'phased' or 'phasedpos' is specified, 0th- and 1st-order phase parameters
 * <i>ph0</i> and <i>ph1</i> must also be supplied.
 * <p>
 * IST : in python script <i>pyproc</i> - read a sample schedule from a file, and perform IST processing.
 *
 * @author bfetler
 * @see SampleSchedule
 * @since NMRViewJ 9.0
 */
@PythonAPI("pyproc")
public class IstVec extends Operation {

    /**
     * Specifies one of several cutoff algorithms.
     *
     * @see IstMath
     */
    private String alg = "std";

    /**
     * Zeroth-order or first-order phasing. Used with phased cutoff algorithms.
     */
    private double ph0 = 0.0, ph1 = 0.0;

    /**
     * Optional flag used with algorithm to temporarily double Vector size during IST calculation.
     */
    private boolean zeroFill = false;

    /**
     * IST math calculations on 1D vector.
     */
    private IstMath istMath;

    @Override
    public IstVec eval(Vec vector) throws ProcessingException {
        ist(vector);
        return this;
    }

    /**
     * Create Vec operation for Iterative Soft Threshold.
     *
     * @param threshold  cutoff threshold as a fraction of maximum height
     * @param loops      number of loops to iterate over
     * @param schedule   sample schedule
     * @param alg        alternate cutoff algorithm
     * @param timeDomain result is in timeDomain
     * @param zeroFill   vector size is doubled during timeDomain calculation
     * @param allValues  replace all values (including actually sampled)
     * @throws ProcessingException
     */
    public IstVec(double threshold, int loops, SampleSchedule schedule, String alg,
                  boolean timeDomain, boolean zeroFill, boolean allValues, boolean adjustThreshold)
            throws ProcessingException {
        this.alg = alg;
        this.zeroFill = zeroFill;
        this.istMath = new IstMath(threshold, loops, schedule, alg, timeDomain, adjustThreshold, allValues);
    }

    /**
     * Create operation for Iterative Soft Threshold.
     *
     * @param threshold       cutoff threshold as a fraction of maximum height
     * @param loops           number of loops to iterate over
     * @param schedule        sample schedule
     * @param alg             alternate cutoff algorithm
     * @param timeDomain      result is in timeDomain
     * @param zeroFill        vector size is doubled during timeDomain calculation
     * @param adjustThreshold threshold is adjusted during ealculation
     * @param ph0             zeroth-order phase
     * @param ph1             first-order phase
     * @throws ProcessingException
     */
    public IstVec(double threshold, int loops, SampleSchedule schedule, String alg,
                  boolean timeDomain, boolean zeroFill, boolean allValues, boolean adjustThreshold, double ph0, double ph1)
            throws ProcessingException {
        this(threshold, loops, schedule, alg, timeDomain, zeroFill, allValues, adjustThreshold);
        this.ph0 = ph0;
        this.ph1 = ph1;
    }

    /**
     * Perform IST operation on a vector. For most algorithms, it consists of a <i>fft-cutoff-ift-rezero</i> loop,
     * repeated until a condition is met.
     *
     * @param vector
     * @throws ProcessingException
     * @see Vec
     */
    private void ist(Vec vector) throws ProcessingException {
        if (vector.schedule != null) {
            istMath.setSchedule(vector.schedule);
        }
        int oldSize = vector.getSize();
        vector.checkPowerOf2();
        if (zeroFill) {
            vector.resize(vector.getSize() * 2);
        }
        if (alg.equals("std") || alg.startsWith("phase")) {
            if ((Math.abs(ph0) > 1.0e-6) || (Math.abs(ph1) > 1.0e-6)) {
                vector.fft();
                vector.phase(ph0, ph1, false, false);  // oldStyle 
                vector.ifft();
            }
        }

        // make sure it's apache mode before copying Complex array
        vector.makeApache();

        // Use care in sizes, vectors complex array could be longer than actual size
        Complex[] cvec = new Complex[vector.getSize()];
        System.arraycopy(vector.getCvec(), 0, cvec, 0, vector.getSize());

        istMath.calculate(cvec);
        if (istMath.isTimeDomain()) {
            vector.resize(oldSize);  // either non-power of two or resize flag is set
            for (int i = 0; i < oldSize; i++) {
                vector.setComplex(i, cvec[i]);
            }
            vector.setFreqDomain(false);
        } else {
            for (int i = 0; i < cvec.length; i++) {
                vector.setComplex(i, cvec[i]);
            }
            vector.setFreqDomain(true);
        }
    }

    /**
     * Print vector contents, with optional label.
     *
     * @param label Label
     * @param vec   Vector
     * @see Vec
     */
    static void printVec(String label, Vec vec) {
        System.out.print("  " + label + " ");
        for (int i = 0; i < vec.getSize(); i += 1) {
            System.out.print(vec.getComplex(i) + "; ");
        }
        System.out.println("");
    }

    static void testVec() {
        int size = 16;
        System.out.println("test Vec");
        Vec vec = new Vec(size, true);
        vec.genSignal(15, 0.97, 100, 0);
        vec.genSignal(50, 0.97, 20, 0);
        vec.genNoise(1);

        printVec("test vec", vec);
        Complex[] cv = vec.getCvec();
        double avgRe = 0.0, avgIm = 0.0;
        for (int i = 0; i < vec.getSize(); i++) {
            avgRe += cv[i].getReal();
            avgIm += cv[i].getImaginary();
        }
        System.out.println("  test vec: mean=(" + avgRe / size + ", " + avgIm / size
                + ") std_dev=" + vec.getNorm() / Math.sqrt(size));

        Vec copy = new Vec(vec.getSize(), vec.isComplex());
        vec.copy(copy);

        String ff = "/tmp/sample_schedule.txt";
        SampleSchedule ss = new SampleSchedule(4, size / 2, ff, true);
        ss.display();

        IstVec ist;
        int[] iters
                = {2, 5, 10, 20, 50, 100, 200, 500, 1000};

        for (int iter : iters) {
            ist = new IstVec(0.96, iter, ss, "abs", false, false, false, false);
            copy.copy(vec);
            ist.eval(vec);
            printVec("out vec " + iter, vec);
        }

        copy.copy(vec);
        vec.fft();
        printVec("ft vec", vec);

        System.out.println("test Vec done");
    }

    /**
     * Simple tests.
     */
    public static void main(String[] args) {
        int vsize = 8;
        System.out.println("IST started");
        Vec vec = new Vec(vsize, true);
        for (int i = 0; i < vsize; i += 2) {
            vec.set(i, 1.0 / (i + 1), 0.0);
            vec.set(i + 1, 0.0, 1.0 / (i + 2));
        }
        printVec("init", vec);
        vec.fft();
        printVec("init ft", vec);
        vec.ifft();
        Vec vec2 = new Vec(vsize, true);
        vec.copy(vec2);

        String ff = "/tmp/sample_schedule.txt";
        SampleSchedule ss = new SampleSchedule(3, vsize / 2, ff, true);
        ss.display();
        IstVec ist = new IstVec(0.75, 24, ss, "std", true, false, false, false);
        ist.ist(vec);  // time domain
        printVec("time out", vec);
        ist = new IstVec(0.75, 24, ss, "std", false, false, false, false);
        ist.ist(vec2);  // frequency domain
        printVec("freq out", vec2);

        testVec();

        System.out.println("IST done");
    }

}
