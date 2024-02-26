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

import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.Matrix;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author bfetler
 */
@PythonAPI("pyproc")
public class IstMatrix extends MatrixOperation {

    private static final Logger log = LoggerFactory.getLogger(IstMatrix.class);

    /**
     * Cutoff threshold as a fraction of maximum height : e.g. 0.98.
     *
     * @see #ist
     * @see #cutAboveThreshold
     */
    private double threshold = 0.98;

    /**
     * Number of loops to iterate over : e.g. 300.
     *
     * @see #ist
     */
    private int loops = 100;

    /**
     * Sample schedule used for non-uniform sampling. Specifies array elements
     * where data is present.
     *
     * @see #ist
     * @see #zero_samples
     * @see SampleSchedule
     */
    private SampleSchedule sampleSchedule = null;

    /**
     * Sample schedule hash map.
     */
    private HashMap sampleHash = null;

    /**
     * Specifies one of several cutoff algorithms. Supported algorithms are:
     * <i>abs</i> <i>phased</i> <i>phasedpos</i>
     *
     * @see IstVec
     * @see #cutAboveThreshold
     */
    private String alg = "abs";

    /**
     * Optional flag used with algorithm to return inverse-FT'ed data, instead
     * of FT'ed data.
     */
    private boolean final_ift = true;

    /**
     * 2D matrix size.
     */
    private int[] msize;

    private final static int NPHASE = 4;

    /**
     * 2D phase array: [f1ph0, f1ph1, f2ph0, f2ph1].
     */
    private double[] phase = new double[NPHASE];  // init zero values

    /**
     * Calculate statistics.
     */
    boolean calcStats = false;

    boolean doSineBell = false;

    double fpMul = 0.5;

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        try {
            MatrixND matrixND = new MatrixND(vector.getSize() * 2);
            for (int i = 0; i < vector.getSize(); i++) {
                matrixND.setValue(vector.getReal(i), i * 2);
                matrixND.setValue(vector.getImag(i), i * 2 + 1);
            }
            if (sampleSchedule == null) {
                sampleSchedule = vector.schedule;
            }
            istMatrixNDWithHFT(matrixND);

            for (int i = 0; i < vector.getSize(); i++) {
                double real = matrixND.getValue(i * 2);
                double imag = matrixND.getValue(i * 2 + 1);
                vector.set(i, real, imag);
            }
        } catch (Exception e) {
            throw new ProcessingException(e.getLocalizedMessage());
        }
        return this;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        if (matrix instanceof MatrixND) {
            MatrixND matrixND = (MatrixND) matrix;
            matrixND.ensurePowerOf2();
            for (int i = 0; i < matrixND.getNDim(); i++) {
                matrixND.setVSizes(matrixND.getSizes());
            }
        }

        if (alg.equals("std")) {
            istMatrixNDWithHFT((MatrixND) matrix);
        } else {
            istMatrix((Matrix) matrix);
        }
        return this;
    }

    /**
     * Create calculation for Matrix Iterative Soft Threshold.
     *
     * @param threshold  cutoff threshold as a fraction of maximum height
     * @param loops      number of loops to iterate over
     * @param schedule   sample schedule
     * @param alg        alternate cutoff algorithm
     * @param timeDomain result is in timeDomain
     * @throws ProcessingException
     */
    public IstMatrix(double threshold, int loops, SampleSchedule schedule, String alg,
                     boolean timeDomain) throws ProcessingException {
        if (threshold <= 0.0 || threshold >= 1.0) {
            log.warn("IST Warning: threshold {} out of bounds, reset to 0.9", threshold);
            threshold = 0.9;
        }
        this.threshold = threshold;
        if (loops < 2) {
            log.warn("IST Warning: number of iterations {} cannot be less than 2, reset to 100", loops);
            loops = 100;
        }
        this.loops = loops;
        this.sampleSchedule = schedule;
        this.alg = alg;
        this.final_ift = timeDomain;
    }

    /**
     * Create calculation for Matrix Iterative Soft Threshold.
     *
     * @param threshold  cutoff threshold as a fraction of maximum height
     * @param loops      number of loops to iterate over
     * @param schedule   sample schedule
     * @param alg        alternate cutoff algorithm
     * @param timeDomain result is in timeDomain
     * @param ph         phase array : row p0, row p1, column p0, column p1
     * @throws ProcessingException
     */
    public IstMatrix(double threshold, int loops, SampleSchedule schedule, String alg,
                     boolean timeDomain, ArrayList phaseList) throws ProcessingException {
        this(threshold, loops, schedule, alg, timeDomain);
        if (phaseList.size() != 0) {
            this.phase = new double[phaseList.size()];
            for (int i = 0; i < phaseList.size(); i++) {
                this.phase[i] = (Double) phaseList.get(i);
            }
        }
    }

    /**
     * IST matrix calculation
     *
     * @param input matrix
     */
    private void istMatrix(Matrix input) {
        msize = input.getDims();

        if (alg.startsWith("phase")) {  // FT-phase-IFT
            input.ft2d();
            input.phase(phase);
            input.ift2d();
        }
        if (alg.startsWith("phase") || sampleSchedule.isDemo()) {
            zeroSample(input.getMatrix());  // zero input with schedule
        }
        Matrix orig = null;
        if (final_ift) {
            orig = new Matrix(msize[0], msize[1]);
            orig.copyMatrix(input);
        }

        double[][] addbuf = new double[msize[0]][msize[1]];
        for (int loop = 0; loop < loops; loop++) {
            input.ft2dNoswap();
            cutAboveThreshold(input.getMatrix(), addbuf);
            if (loop < loops - 1) {
                input.ift2dNoswap();
                zeroSample(input.getMatrix());  // rezero input with schedule
            }
        }

        input.copyMatrix(addbuf);
        if (final_ift) {
            input.ift2dNoswap();
            copyValues(orig.getMatrix(), input.getMatrix());  // copy orig non-zero values
        } else {
            input.swapHalfMatrix();
        }
    }

    /**
     * IST matrix calculation
     *
     * @param input matrix
     */
    private void istMatrixWithHFT(Matrix input) {
        msize = input.getDims();

        boolean doPhase = false;
        for (double phaseVal : phase) {
            if (Math.abs(phaseVal) > 1.0e-6) {
                doPhase = true;
                break;
            }
        }
        if (doPhase) {
            input.ft2d();
            input.phase(phase);
            input.ift2d();
        }
        zeroSample(input.getMatrix());  // zero input with schedule
        Matrix orig = null;
        orig = new Matrix(msize[0], msize[1]);
        orig.copyMatrix(input);

        double[][] addbuf = new double[msize[0]][msize[1]];
        for (int loop = 0; loop < loops; loop++) {
            input.ft2dNoswapZF();
            cutRealAboveThreshold(input.getMatrix(), addbuf);
            if (loop < loops - 1) {
                input.hift2dNoswapZF();
                zeroSample(input.getMatrix());  // rezero input with schedule
            }
        }

        input.copyMatrix(addbuf);
        input.hift2dNoswapZF();
        copyValues(orig.getMatrix(), input.getMatrix());  // copy orig non-zero values
    }

    public static int[] genSrcTargetMap(SampleSchedule sampleSchedule, MatrixND matrix) {
        int[][] samples = sampleSchedule.getSamples();
        int nComplex = (int) Math.round(Math.pow(2, matrix.getNDim()));
        int[] srcTargetMap = new int[samples.length * nComplex];
        int i = 0;
        for (int[] sample : samples) {
            int[] complexSample = new int[sample.length];
            for (int k = 0; k < nComplex; k++) {
                int divisor = 1;
                for (int j = 0; j < sample.length; j++) {
                    int cDelta = (k / divisor) % 2;
                    divisor *= 2;
                    complexSample[sample.length - j - 1] = sample[sample.length - j - 1] * 2 + cDelta;
                }
                int offset = matrix.getOffset(complexSample);
                srcTargetMap[i++] = offset;
            }
        }
        return srcTargetMap;
    }

    public static int[] genZFSrcTargetMap(MatrixND matrixND, int[] origSizes, boolean constrainEdges) {
        int nOrig = 1;
        for (int sz : origSizes) {
            nOrig *= sz;
        }
        int[] srcTargetMap = new int[nOrig];

        MultidimensionalCounter mdCounter = new MultidimensionalCounter(origSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            int offset = matrixND.getOffset(counts);
            srcTargetMap[i++] = offset;
        }
        return srcTargetMap;
    }

    static boolean isInSkipList(List<int[]> skipList, int[] counts) {
        for (int[] skip : skipList) {
            boolean inList = true;
            for (int i = 0; i < counts.length; i++) {
                if (skip[i] < 0) {
                    continue;
                }
                if (skip[i] != counts[i]) {
                    inList = false;
                    break;
                }
            }
            if (inList) {
                return true;
            }
        }
        return false;
    }

    public static int[] genZFList(MatrixND matrixND, int[] origSizes, boolean constrainEdges, List<int[]> skipList) {
        int nElems = matrixND.getNElems();
        boolean[] validPositions = new boolean[nElems];

        MultidimensionalCounter mdCounter = new MultidimensionalCounter(origSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            if ((skipList == null) ||skipList.isEmpty() || !isInSkipList(skipList, counts)) {
                int offset = matrixND.getOffset(counts);
                validPositions[offset] = true;
            }
        }
        if (constrainEdges) {
            int[] edge = new int[matrixND.getNDim()];
            if (edge.length == 1) {
                int offset = matrixND.getOffset(edge);
                validPositions[offset] = true;
            } else {
                for (int i = 0; i < matrixND.getNDim(); i++) {
                    for (int j = 0; j < matrixND.getNDim(); j++) {
                        if (i != j) {
                            edge[j] = matrixND.getSize(j) - 1;
                        }
                    }
                    for (int k = 0; k < matrixND.getSize(i); k++) {
                        edge[i] = k;
                        int offset = matrixND.getOffset(edge);
                        validPositions[offset] = true;
                    }
                }
            }
        }

        List<Integer> zeroList = new ArrayList<>();
        int i = 0;
        for (var valid : validPositions) {
            if (!valid) {
                zeroList.add(i);
            }
            i++;
        }
        int[] zeroArray = zeroList.stream().mapToInt(zv -> zv).toArray();

        return zeroArray;
    }

    public static int[] genZeroList(SampleSchedule sampleSchedule, MatrixND matrix) {
        int[][] samples = sampleSchedule.getSamples();
        boolean[] validPositions = new boolean[matrix.getNElems()];
        int nComplex = (int) Math.round(Math.pow(2, matrix.getNDim()));
        int nZeros = matrix.getNElems() - samples.length * nComplex;
        int nValid = 0;
        for (int[] sample : samples) {
            int[] complexSample = new int[sample.length];
            for (int k = 0; k < nComplex; k++) {
                int divisor = 1;
                for (int j = 0; j < sample.length; j++) {
                    int cDelta = (k / divisor) % 2;
                    divisor *= 2;
                    complexSample[sample.length - j - 1] = sample[sample.length - j - 1] * 2 + cDelta;
                }
                int offset = matrix.getOffset(complexSample);
                if (offset >= validPositions.length) {
                }
                validPositions[offset] = true;
                nValid++;
            }
        }
        int[] zeroList = new int[nZeros];
        int i = 0;
        int k = 0;
        for (boolean valid : validPositions) {
            if (!valid) {
                zeroList[k++] = i;
            }
            i++;
        }
        return zeroList;
    }

    private void istMatrixNDWithHFT(MatrixND matrix) {
        boolean report = false;
        int[] srcTargetMap = genSrcTargetMap(sampleSchedule, matrix);
        int[] zeroList = genZeroList(sampleSchedule, matrix);
        boolean doPhase = false;
        for (double phaseVal : phase) {
            if (Math.abs(phaseVal) > 1.0e-6) {
                doPhase = true;
                break;
            }
        }
        if (doPhase) {
            matrix.phase(phase);
        }
        if (doSineBell) {
            matrix.apodize();
        }
        matrix.zeroValues(zeroList);
        // could just copy the actually sample values to vector
        MatrixND matrixCopy = new MatrixND(matrix);
        double[] addBuffer = new double[matrix.getNElems()];
        double preValue = 0.0;
        double postValue = 0.0;
        for (int iteration = 0; iteration < loops; iteration++) {
            matrix.doFTtoReal();
            if (calcStats && (iteration == 0)) {
                preValue = matrix.calcSumAbs();
            }
            // could make buffer size of sample points
            matrix.cutRealAboveThreshold(addBuffer, threshold);
            if (iteration < loops - 1) {
                matrix.doHIFT(fpMul);
                matrix.zeroValues(zeroList);
            }
        }
        matrix.copyDataFrom(addBuffer);
        if (calcStats) {
            postValue = matrix.calcSumAbs();
        }
        matrix.doHIFT(fpMul);
        if (calcStats) {
            var deltaToOrig = matrix.calcDifference(matrixCopy, srcTargetMap);
            log.info("{} {} {} {}", loops, preValue, postValue, deltaToOrig.mabs());
        }
        matrix.copyValuesFrom(matrixCopy, srcTargetMap);
    }

    private void getSampleHash() {
        if (sampleHash == null) {
            sampleHash = sampleSchedule.getSampleHash();
        }
    }

    private void zeroSample(double[][] in) {
        if (sampleSchedule != null) {
            getSampleHash();
            for (int i = 0; i < msize[0] / 2; i++) {
                for (int j = 0; j < msize[1] / 2; j++) {
                    if (!sampleHash.containsKey(sampleSchedule.calcKey(j, i))) {
                        in[2 * i][2 * j] = 0.0;
                        in[2 * i + 1][2 * j] = 0.0;
                        in[2 * i][2 * j + 1] = 0.0;
                        in[2 * i + 1][2 * j + 1] = 0.0;
                    }
                }
            }
        }
    }

    /**
     * Copy original non-zero values into add buffer.
     *
     * @param source
     * @param target
     */
    private void copyValues(double[][] source, double[][] target) {
        if (sampleSchedule != null) {
            for (int[] s : sampleSchedule.getSamples()) {
                int i = s[1];
                int j = s[0];
                target[2 * i][2 * j] = source[2 * i][2 * j];
                target[2 * i + 1][2 * j] = source[2 * i + 1][2 * j];
                target[2 * i][2 * j + 1] = source[2 * i][2 * j + 1];
                target[2 * i + 1][2 * j + 1] = source[2 * i + 1][2 * j + 1];
            }
        }
    }

    /**
     * Perform cutoff algorithm. In general, a threshold is determined for an
     * <i>inbuf</i> input buffer. Points above the threshold are summed into the
     * <i>addbuf</i> buffer, with the remainder of <i>inbuf</i> set equal to the
     * threshold. The method chooses between different algorithms using the
     * <i>alg</i> parameter.
     * <p>
     * Current implementations for hyper-complex data only.
     *
     * @param inbuf  input buffer
     * @param addbuf add buffer
     * @see #alg
     */
    private void cutAboveThreshold(double[][] inbuf, double[][] addbuf) {
        if (alg.equals("phased")) {
            cutAboveComplexPhasedThreshold(inbuf, addbuf);
        } else if (alg.equals("phasedpos")) {
            cutAboveComplexPhasedPosThreshold(inbuf, addbuf);
        } else // if (alg.equals("abs"))
        {
            cutAboveComplexAbsThreshold(inbuf, addbuf);
        }
    }

    /**
     * Get quaternion absolute value of four doubles.
     *
     * @param rr
     * @param ri
     * @param ir
     * @param ii
     * @return absolute value
     */
    private double getQuadAbs(double rr, double ri, double ir, double ii) {
        return Math.sqrt(rr * rr + ri * ri + ir * ir + ii * ii);
    }

    /**
     * Get absolute value of double array. Usually quaternion.
     *
     * @param da
     * @return absolute value
     */
    private double getQuadAbs(double[] da) {
        double r = 0;
        for (double d : da) {
            r += d * d;
        }
        return Math.sqrt(r);
    }

    /**
     * Get maximum of absolute value of a matrix.
     *
     * @param input
     * @return position of maximum in input matrix
     * @see Matrix
     */
    private double[] getAbsMax(double[][] m) {
        double dd[] = new double[4];
        double val, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < msize[0]; i += 2) {
            for (int j = 0; j < msize[1]; j += 2) {
                val = getQuadAbs(m[i][j], m[i][j + 1], m[i + 1][j], m[i + 1][j + 1]);
                if (val > max) {
                    max = val;
                    dd[0] = m[i][j];
                    dd[1] = m[i][j + 1];
                    dd[2] = m[i + 1][j];
                    dd[3] = m[i + 1][j + 1];
                }
            }
        }
        return dd;
    }

    /**
     * Get maximum of absolute value of a matrix.
     *
     * @param input
     * @return position of maximum in input matrix
     * @see Matrix
     */
    private double getAbsMaxReal(double[][] m) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < msize[0]; i++) {
            for (int j = 0; j < msize[1]; j++) {
                double val = m[i][j];
                double aval = Math.abs(val);
                if (aval > max) {
                    max = aval;
                }
            }
        }
        return max;
    }

    /**
     * Get maximum positive or negative value of a matrix.
     *
     * @param in input matrix
     * @return value of maximum in input matrix
     */
    private double[] getPhMax(double[][] in) {
        double dd[] = new double[4];
        double val, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < msize[0]; i += 2) {
            for (int j = 0; j < msize[1]; j += 2) {
                val = Math.abs(in[i][j]);
                if (val > max) {
                    max = val;
                    dd[0] = in[i][j];
                    dd[1] = in[i][j + 1];
                    dd[2] = in[i + 1][j];
                    dd[3] = in[i + 1][j + 1];
                }
            }
        }
        return dd;
    }

    /**
     * Get maximum positive value of a matrix.
     *
     * @param in input matrix
     * @return value of maximum in input matrix
     */
    private double[] getPhPosMax(double[][] in) {
        double dd[] = new double[4];
        double val, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < msize[0]; i += 2) {
            for (int j = 0; j < msize[1]; j += 2) {
                val = in[i][j];
                if (val > max) {
                    max = val;
                    dd[0] = in[i][j];
                    dd[1] = in[i][j + 1];
                    dd[2] = in[i + 1][j];
                    dd[3] = in[i + 1][j + 1];
                }
            }
        }
        return dd;
    }

    private void cutAboveComplexAbsThreshold(double[][] in, double[][] add) {
        double[] cutoff = getAbsMax(in);
        double th = threshold * getQuadAbs(cutoff);
        for (int i = 0; i < cutoff.length; i++) {
            cutoff[i] *= threshold;
        }
        for (int i = 0; i < msize[0]; i += 2) {
            for (int j = 0; j < msize[1]; j += 2) {
                if (th < getQuadAbs(in[i][j], in[i][j + 1], in[i + 1][j], in[i + 1][j + 1])) {
                    // sum difference to add buffer
                    add[i][j] += (in[i][j] - cutoff[0]);
                    add[i][j + 1] += (in[i][j + 1] - cutoff[1]);
                    add[i + 1][j] += (in[i + 1][j] - cutoff[2]);
                    add[i + 1][j + 1] += (in[i + 1][j + 1] - cutoff[3]);
                    // save threshold to input
                    in[i][j] = cutoff[0];
                    in[i][j + 1] = cutoff[1];
                    in[i + 1][j] = cutoff[2];
                    in[i + 1][j + 1] = cutoff[3];
                }
            }
        }
    }

    private void cutAboveComplexPhasedThreshold(double[][] in, double[][] add) {
        double[] cutoff = getPhMax(in);
        double th = Math.abs(threshold * cutoff[0]);
        for (int i = 0; i < cutoff.length; i++) {
            cutoff[i] *= threshold;
        }
        for (int i = 0; i < msize[0]; i += 2) {
            for (int j = 0; j < msize[1]; j += 2) {
                if (in[i][j] > th) {
                    // sum difference to add buffer
                    add[i][j] += (in[i][j] - cutoff[0]);
                    add[i][j + 1] += (in[i][j + 1] - cutoff[1]);
                    add[i + 1][j] += (in[i + 1][j] - cutoff[2]);
                    add[i + 1][j + 1] += (in[i + 1][j + 1] - cutoff[3]);
                    // save threshold to input
                    in[i][j] = cutoff[0];
                    in[i][j + 1] = cutoff[1];
                    in[i + 1][j] = cutoff[2];
                    in[i + 1][j + 1] = cutoff[3];
                } else if (in[i][j] < -th) {
                    // sum difference to add buffer
                    add[i][j] += (in[i][j] + cutoff[0]);
                    add[i][j + 1] += (in[i][j + 1] + cutoff[1]);
                    add[i + 1][j] += (in[i + 1][j] + cutoff[2]);
                    add[i + 1][j + 1] += (in[i + 1][j + 1] + cutoff[3]);
                    // save -threshold to input
                    in[i][j] = -cutoff[0];
                    in[i][j + 1] = -cutoff[1];
                    in[i + 1][j] = -cutoff[2];
                    in[i + 1][j + 1] = -cutoff[3];
                }
            }
        }
    }

    private void cutAboveComplexPhasedPosThreshold(double[][] in, double[][] add) {
        double[] cutoff = getPhPosMax(in);
        double th = threshold * cutoff[0];
        for (int i = 0; i < cutoff.length; i++) {
            cutoff[i] *= threshold;
        }
        for (int i = 0; i < msize[0]; i += 2) {
            for (int j = 0; j < msize[1]; j += 2) {
                if (in[i][j] > th) {
                    // sum difference to add buffer
                    add[i][j] += (in[i][j] - cutoff[0]);
                    add[i][j + 1] += (in[i][j + 1] - cutoff[1]);
                    add[i + 1][j] += (in[i + 1][j] - cutoff[2]);
                    add[i + 1][j + 1] += (in[i + 1][j + 1] - cutoff[3]);
                    // save threshold to input
                    in[i][j] = cutoff[0];
                    in[i][j + 1] = cutoff[1];
                    in[i + 1][j] = cutoff[2];
                    in[i + 1][j + 1] = cutoff[3];
                }
            }
        }
    }

    private void cutRealAboveThreshold(double[][] in, double[][] add) {
        double cutoff = getAbsMaxReal(in);
        double th = threshold * cutoff;
        for (int i = 0; i < msize[0]; i++) {
            for (int j = 0; j < msize[1]; j++) {
                double value = in[i][j];
                double avalue = Math.abs(value);
                if (avalue > th) {
                    if (value > 0.0) {
                        add[i][j] += (avalue - th);
                        in[i][j] = th;
                    } else {
                        add[i][j] -= (avalue - th);
                        in[i][j] = -th;
                    }
                }
            }
        }
    }

}
