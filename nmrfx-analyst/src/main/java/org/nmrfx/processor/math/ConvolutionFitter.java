package org.nmrfx.processor.math;

import com.google.common.util.concurrent.AtomicDouble;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.jtransforms.fft.DoubleFFT_3D;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.RegionData;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.LineShapes;

import org.jtransforms.fft.DoubleFFT_2D;

import java.util.*;
import java.io.IOException;

public class ConvolutionFitter {
    static Random rand = new Random();
    double psfMax;
    int[] psfDim;
    int psfTotalSize;
    double[] widths;
    MatrixND sim;
    MatrixND signalMatrix;
    MatrixND psfMatrix;
    BooleanMatrixND skip;
    double squash = 0.625;
    double threshold = 0.0;
    MultidimensionalCounter counter;
    MultidimensionalCounter neighborCounter;

    List<CPeak> cPeakList;
    List<CPeakD> allPeaks;

    public ConvolutionFitter(int n0, int width, double shapeFactor) {
        int[] n = {n0};
        widths = new double[1];
        widths[0] = width;
        makePSF(n, widths, shapeFactor);
    }

    public ConvolutionFitter(int[] n, double[] widths, double shapeFactor) {
        makePSF(n, widths, shapeFactor);
        this.widths = widths.clone();
    }

    void makePSF(int[] n, double[] width, double shapeFactor) {
        int nDim = n.length;
        psfDim = n.clone();
        double[][] yValues = new double[nDim][];
        double max = 0.0;
        int totalSize = 1;
        double sum = 0.0;
        for (int iDim = 0; iDim < nDim; iDim++) {
            yValues[iDim] = new double[n[iDim]];
            totalSize *= n[iDim];
            for (int i = 0; i < n[iDim]; i++) {
                double x = -(n[iDim] - 1) / 2.0 + i;
                yValues[iDim][i] = LineShapes.G_LORENTZIAN.calculate(x, 1.0, 0.0, width[iDim], shapeFactor);
                sum += yValues[iDim][i];
            }
        }
//        for (int iDim = 0; iDim < nDim; iDim++) {
//            for (int i = 0; i < n[iDim]; i++) {
//                yValues[iDim][i] /= sum;
//                if (yValues[iDim][i] > max) {
//                    max = yValues[iDim][i];
//                }
//            }
//        }
        psfTotalSize = totalSize;
        counter = new MultidimensionalCounter(n);
        psfMatrix = makePSFMatrix(yValues);
    }


    // used from Python for testing
    public void addSignal(double[] vec, double amplitude, double center, double width, double shapeFactor) {
        for (int i = 0; i < vec.length; i++) {
            double y = LineShapes.G_LORENTZIAN.calculate(i, amplitude, center, width, shapeFactor);
            vec[i] += y;
        }
    }

    // used from Python for testing
    public void addNoise(double[] vec, double scale) {
        for (int i = 0; i < vec.length; i++) {
            vec[i] += rand.nextGaussian() * scale;
        }
    }


    public MatrixND simVector() {
        return sim;
    }

    public double psfMax() {
        return psfMax;
    }

    public void squash(double value) {
        this.squash = value;
    }

    public double[] getValueArray(int[] start) {
        var iterator = counter.iterator();
        double[] vArray = new double[psfTotalSize];
        MatrixND matrixND = new MatrixND();
        int i = 0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            for (int j = 0; j < counts.length; j++) {
                counts[j] += start[j];
            }
            vArray[i] = matrixND.getValue(counts);
            i++;
        }
        return vArray;
    }

    public void putValueArray(int[] start, double[] vArray) {
        var iterator = counter.iterator();
        MatrixND matrixND = new MatrixND();
        int i = 0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            for (int j = 0; j < counts.length; j++) {
                counts[j] += start[j];
            }
            matrixND.setValue(vArray[i], counts);
            i++;
        }
    }

    public MatrixND makePSFMatrix(double[][] psfValues) {
        var iterator = counter.iterator();
        MatrixND matrixND = new MatrixND(psfDim);
        double max = 0.0;
        double sum = 0.0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            double value = 1.0;
            for (int j = 0; j < counts.length; j++) {
                value *= psfValues[j][counts[j]];
            }
            matrixND.setValue(value, counts);
            sum += value;
            max = Math.max(value, max);
        }
        if (sum != 0.0) {
            matrixND.scale(1.0 / sum);
            max /= sum;
        }
        psfMax = max;
        return matrixND;
    }

    boolean isHigherThanNeighbor(MatrixND values, int[] indices, int[] halfWidths) {
        boolean higher = true;
        int nDim = values.getNDim();
        double value = values.getValue(indices);
        int[] indices2 = new int[nDim];
        var iterator = neighborCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            boolean isCenter = true;
            boolean ok = true;
            for (int i = 0; i < indices.length; i++) {
                indices2[i] = indices[i] + counts[i] - halfWidths[i];
                if ((indices2[i] < 0) || (indices2[i] >= values.getSize(i))) {
                    ok = false;
                    break;
                }
                if (counts[i] != halfWidths[i]) {
                    isCenter = false;
                }
            }
            if (!ok) {
                continue;
            }
            double value2 = values.getValue(indices2);
            if (!isCenter && (value2 > value)) {
                higher = false;
                break;
            }
        }
        return higher;
    }

    CPeakD accumulateNeighbors(MatrixND values, BooleanMatrixND skipMatrix, int[] indices, int[] halfWidths) {
        int nDim = values.getNDim();
        int[] indices2 = new int[nDim];
        int[] ipsf = new int[nDim];
        var iterator = neighborCounter.iterator();
        double sumHeight = 0.0;
        double[] sumPositions = new double[nDim];
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            boolean isCenter = true;
            boolean ok = true;
            for (int i = 0; i < indices.length; i++) {
                indices2[i] = indices[i] + counts[i] - halfWidths[i];
                if ((indices2[i] < 0) || (indices2[i] >= values.getSize(i))) {
                    ok = false;
                    break;
                }
                ipsf[i] = (psfMatrix.getSize(i) - 1) / 2 - (halfWidths[i] - counts[i]);
                if (counts[i] != halfWidths[i]) {
                    isCenter = false;
                }
            }
            if (!ok) {
                continue;
            }
            double value2 = values.getValue(indices2);
            double psfValue = psfMatrix.getValue(ipsf);
            double height = value2 * psfValue / psfMax;
            sumHeight += height;
            for (int i = 0; i < sumPositions.length; i++) {
                sumPositions[i] += indices2[i] * height;
            }
            if (!isCenter) {
                skipMatrix.setValue(true, indices2);
            }
        }
        for (int i = 0; i < sumPositions.length; i++) {
            sumPositions[i] /= sumHeight;
        }

        return new CPeakD(sumPositions, sumHeight);
    }

    record CPeak(int[] position, double height) {
        double distance(CPeak cPeak) {
            double sum = 0.0;
            for (int i = 0; i < position.length; i++) {
                double d = position[i] - cPeak.position[i];
                sum += d * d;
            }
            return Math.sqrt(sum);
        }
    }

    record CPeakD(double[] position, double height) {
        double distance(CPeakD cPeak, double[] widths) {
            double sum = 0.0;
            for (int i = 0; i < position.length; i++) {
                double d = (position[i] - cPeak.position[i]) / widths[i] ;
                sum += d * d;
            }
            return Math.sqrt(sum);
        }

        CPeakD merge(CPeakD cPeakD) {
            double[] wAvgPosigion = new double[position.length];
            double sum = height + cPeakD.height;
            for (int i = 0; i < position.length; i++) {
                wAvgPosigion[i] = (height * position[i] + cPeakD.height * cPeakD.position[i]) / sum;
            }
            return new CPeakD(wAvgPosigion, sum);
        }

    }

    boolean inCore(int[] counts) {
        boolean ok = true;
        for (int i=0;i<counts.length;i++) {
            if (counts[i] > signalMatrix.getSize(i) - psfDim[i]) {
                ok = false;
                break;
            }
        }
        return ok;
    }

    void findPeaks(MatrixND values, BooleanMatrixND skipMat) {
        int[] neighborDims = new int[values.getNDim()];
        int[] halfWidths = new int[neighborDims.length];
        for (int i = 0; i < neighborDims.length; i++) {
            halfWidths[i] = (int) Math.ceil(widths[i] * squash) / 2;
            neighborDims[i] = 2 * halfWidths[i] + 1;
        }

        neighborCounter = new MultidimensionalCounter(neighborDims);


        values.stream().forEach(counts -> {
            double value = values.getValue(counts);
            if (Math.abs(value) < threshold) {
                skipMat.setValue(true, counts);
            } else {
                boolean highest = isHigherThanNeighbor(values, counts, halfWidths);
                if (highest) {
                    CPeak cPeak = new CPeak(counts, value);
                    cPeakList.add(cPeak);
                }
            }
        });
    }

    public void squashPeaks(MatrixND values, BooleanMatrixND skipMatrix, int[][] pt) {
        int[] neighborDims = new int[values.getNDim()];
        int[] halfWidths = new int[neighborDims.length];
        for (int i = 0; i < neighborDims.length; i++) {
            halfWidths[i] = (int) Math.ceil(widths[i] * squash) / 2;
            neighborDims[i] = 2 * halfWidths[i] + 1;
        }

        List<CPeak> newPeaks = new ArrayList<>();


        for (CPeak cPeak1 : cPeakList) {
            int[] center1 = cPeak1.position;
            if (skipMatrix.getValue(center1)) {
                continue;
            }
            CPeakD cPeakD = accumulateNeighbors(values, skipMatrix, center1, halfWidths);
            int[] newPosition = new int[cPeak1.position.length];
            for (int j = 0; j < newPosition.length; j++) {
                cPeakD.position[j] = cPeakD.position[j] + pt[j][0];
            }
            allPeaks.add(cPeakD);
            newPeaks.add(new CPeak(center1, cPeakD.height));
        }
        values.fill(0.0);
        skipMatrix.set(true);
        for (CPeak cPeak : newPeaks) {
            values.setValue(cPeak.height, cPeak.position);
            skipMatrix.setValue(false, cPeak.position);
        }
    }


    public static MatrixND convolve(MatrixND a, MatrixND b) {
        int nDim = a.getNDim();
        if (nDim == 1) {
            return convolve1D(a, b);
        } else if (nDim == 2) {
            return convolve2D(a, b);
        } else if (nDim == 3) {
            return convolve3D(a, b);
        } else {
            return null;
        }
    }

    public static MatrixND convolve1D(MatrixND a, MatrixND b) {
        int nDim = a.getNDim();

        int[] newSizes2 = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            int n = a.getSize(i) + b.getSize(i) - 1;
            int fftLen = nextPowerOfTwo(n);
            newSizes2[i] = 2 * fftLen;
        }

        MatrixND aPadded2 = new MatrixND(a);
        MatrixND bPadded2 = new MatrixND(b);


        aPadded2.zeroFill(newSizes2);
        aPadded2.toComplex(0);
        double[] c = new double[nDim];
        Arrays.fill(c, 1.0);
        aPadded2.doFourierTransformWithoutShift(c);
        bPadded2.zeroFill(newSizes2);
        bPadded2.toComplex(0);
        bPadded2.doFourierTransformWithoutShift(c);

        aPadded2.timesComplex(bPadded2);

        aPadded2.doInverseFourierTransformWithoutShift();
        int[] offsets = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            offsets[i] = (b.getSize(i) - 1) / 2;
        }
        MatrixND result = new MatrixND(a.getSizes());
        int[] indices = new int[nDim];
        result.stream().forEach(counts -> {
            for (int i = 0; i < nDim; i++) {
                indices[i] = 2 * (counts[i] + offsets[i]);
            }
            double value = aPadded2.getValue(indices);
            result.setValue(value, counts);
        });
        return result;
    }

    public static double[][] to2D(MatrixND a) {
        int rows = a.getSize(0);
        int cols = a.getSize(1);
        double[][] x = new double[rows][cols];
        int k = 0;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                x[j][i] = a.getValueAtIndex(k++);
            }
        }
        return x;
    }

    public static double[][][] to3D(MatrixND a) {
        int planes = a.getSize(0);
        int rows = a.getSize(1);
        int cols = a.getSize(2);
        double[][][] x = new double[planes][rows][cols];
        int kk = 0;
        for (int k = 0; k < planes; k++) {
            for (int j = 0; j < rows; j++) {
                for (int i = 0; i < cols; i++) {
                    x[k][j][i] = a.getValueAtIndex(kk++);
                }
            }
        }
        return x;
    }

    public static MatrixND convolve2D(MatrixND a, MatrixND b) {
        int rows = a.getSize(0);
        int cols = a.getSize(1);
        int kRows = b.getSize(0);
        int kCols = b.getSize(1);

        // Output size for linear convolution
        int outRows = rows + kRows - 1;
        int outCols = cols + kCols - 1;

        double[][] input = to2D(a);
        double[][] kernel = to2D(b);

        // Pad input and kernel to same size
        double[][] inputPadded = new double[outRows][outCols];
        double[][] kernelPadded = new double[outRows][outCols];

        for (int i = 0; i < rows; i++)
            System.arraycopy(input[i], 0, inputPadded[i], 0, cols);
        for (int i = 0; i < kRows; i++)
            System.arraycopy(kernel[i], 0, kernelPadded[i], 0, kCols);

        // Convert to complex arrays: real + imaginary interleaved
        double[][] inputComplex = realToComplex(inputPadded);
        double[][] kernelComplex = realToComplex(kernelPadded);

        DoubleFFT_2D fft = new DoubleFFT_2D(outRows, outCols);
        fft.complexForward(inputComplex);
        fft.complexForward(kernelComplex);

        // Point-wise complex multiplication
        double[][] resultComplex = new double[outRows][2 * outCols];
        for (int i = 0; i < outRows; i++) {
            for (int j = 0; j < outCols; j++) {
                int re = 2 * j;
                int im = 2 * j + 1;

                double aRe = inputComplex[i][re];
                double aIm = inputComplex[i][im];
                double bRe = kernelComplex[i][re];
                double bIm = kernelComplex[i][im];

                // (a + bi)(c + di) = (ac - bd) + (ad + bc)i
                resultComplex[i][re] = aRe * bRe - aIm * bIm;
                resultComplex[i][im] = aRe * bIm + aIm * bRe;
            }
        }

        // Inverse FFT
        fft.complexInverse(resultComplex, true);
        int[] offsets = new int[2];
        for (int i = 0; i < 2; i++) {
            offsets[i] = (b.getSize(i) - 1) / 2;
        }

        // Extract real part
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = resultComplex[i + offsets[0]][2 * (j + offsets[1])];
            }
        }

        return new MatrixND(result);
    }

    public static MatrixND convolve3D(MatrixND a, MatrixND b) {
        int planes = a.getSize(0);
        int rows = a.getSize(1);
        int cols = a.getSize(2);
        int kPlanes = b.getSize(0);
        int kRows = b.getSize(1);
        int kCols = b.getSize(2);

        // Output size for linear convolution
        int outPlanes = planes + kPlanes - 1;
        int outRows = rows + kRows - 1;
        int outCols = cols + kCols - 1;

        double[][][] input = to3D(a);
        double[][][] kernel = to3D(b);

        // Pad input and kernel to same size
        double[][][] inputPadded = new double[outPlanes][outRows][outCols];
        double[][][] kernelPadded = new double[outPlanes][outRows][outCols];

        for (int plane = 0; plane < planes; plane++)
            for (int row = 0; row < rows; row++)
                System.arraycopy(input[plane][row], 0, inputPadded[plane][row], 0, cols);
        for (int plane = 0; plane < kPlanes; plane++)
            for (int row = 0; row < kRows; row++)
                System.arraycopy(kernel[plane][row], 0, kernelPadded[plane][row], 0, kCols);

        // Convert to complex arrays: real + imaginary interleaved
        double[][][] inputComplex = realToComplex(inputPadded);
        double[][][] kernelComplex = realToComplex(kernelPadded);

        DoubleFFT_3D fft = new DoubleFFT_3D(outPlanes, outRows, outCols);
        fft.complexForward(inputComplex);
        fft.complexForward(kernelComplex);

        // Point-wise complex multiplication
        double[][][] resultComplex = new double[outPlanes][outRows][2 * outCols];
        for (int plane = 0; plane < outPlanes; plane++) {
            for (int row = 0; row < outRows; row++) {
                for (int col = 0; col < outCols; col++) {
                    int re = 2 * col;
                    int im = 2 * col + 1;

                    double aRe = inputComplex[plane][row][re];
                    double aIm = inputComplex[plane][row][im];
                    double bRe = kernelComplex[plane][row][re];
                    double bIm = kernelComplex[plane][row][im];

                    // (a + bi)(c + di) = (ac - bd) + (ad + bc)i
                    resultComplex[plane][row][re] = aRe * bRe - aIm * bIm;
                    resultComplex[plane][row][im] = aRe * bIm + aIm * bRe;
                }
            }
        }

        // Inverse FFT
        fft.complexInverse(resultComplex, true);
        int[] offsets = new int[3];
        for (int i = 0; i < 3; i++) {
            offsets[i] = (b.getSize(i) - 1) / 2;
        }

        // Extract real part
        double[][][] result = new double[planes][rows][cols];
        for (int plane = 0; plane < planes; plane++) {
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    result[plane][row][col] = resultComplex[plane + offsets[0]][row + offsets[1]][2 * (col + offsets[2])];
                }
            }
        }

        return new MatrixND(result);
    }

    private static double[][] realToComplex(double[][] real) {
        int rows = real.length;
        int cols = real[0].length;
        double[][] complex = new double[rows][2 * cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                complex[i][2 * j] = real[i][j]; // real part
                complex[i][2 * j + 1] = 0.0;    // imaginary part
            }
        }
        return complex;
    }

    private static double[][][] realToComplex(double[][][] real) {
        int planes = real.length;
        int rows = real[0].length;
        int cols = real[0][0].length;
        double[][][] complex = new double[planes][rows][2 * cols];
        for (int k = 0; k < planes; k++) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    complex[k][i][2 * j] = real[k][i][j]; // real part
                    complex[k][i][2 * j + 1] = 0.0;    // imaginary part
                }
            }
        }
        return complex;
    }


    public MatrixND convolutionTest(double[] observed) {
        MatrixND matrixND = new MatrixND(observed);
        return convolve(matrixND, psfMatrix);
    }

    public MatrixND convolutionTest2D(double[][] observed) {
        MatrixND matrixND = new MatrixND(observed);
        return convolve(matrixND, psfMatrix);
    }

    public static MatrixND iterativeConvolution(MatrixND observed, MatrixND estimate, MatrixND psf, int iterations) {
        for (int iter = 0; iter < iterations; iter++) {
            MatrixND convEstimate = convolve(estimate, psf);

            AtomicDouble maxValue2 = new AtomicDouble(0.0);
            AtomicDouble maxDelta = new AtomicDouble(0.0);
            observed.stream().forEach(counts -> {
                double oldValue = estimate.getValue(counts);
                double obsValue = observed.getValue(counts);
                double v = convEstimate.getValue(counts);
                if (Double.isNaN(v) || (Math.abs(v) < 1.0e-9)) {
                    v = 1.0e-9;
                }
                double ratio = obsValue / v;
                double newValue = oldValue * ratio;
                maxValue2.set(Math.max(Math.abs(obsValue), maxValue2.get()));
                double delta = Math.abs(newValue - oldValue);
                maxDelta.set(Math.max(delta, maxDelta.get()));
                estimate.setValue(newValue, counts);
            });
            double maxChange = maxDelta.get() / maxValue2.get();
        }

        return estimate;
    }

    private static int nextPowerOfTwo(int n) {
        int pow = 1;
        while (pow < n) pow *= 2;
        return pow;
    }

    public MatrixND iterativeConvolutions(double threshold, int iterations, int[][] pt, boolean doSquash) {
        this.threshold = threshold;
        MatrixND valueMatrix = new MatrixND(signalMatrix.getSizes());
        skip = new BooleanMatrixND(signalMatrix.getSizes());
        valueMatrix.stream().forEach(counts -> {
            if (signalMatrix.getValue(counts) > threshold) {
                valueMatrix.setValue(signalMatrix.getValue(counts), counts);
            } else {
                skip.setValue(true, counts);
            }

        });


        MatrixND estimates = iterativeConvolution(signalMatrix, valueMatrix, psfMatrix, iterations);

        findPeaks(estimates, skip);
        if (doSquash) {
            squashPeaks(estimates, skip, pt);
        }
        sim = convolve(estimates, psfMatrix);
        return estimates;
    }

    public MatrixND iterativeConvolutions(MatrixND data, double threshold, int iterations, boolean doSquash) {
        cPeakList = new ArrayList<>();
        allPeaks = new ArrayList<>();
        int[] sizes = {data.getSize(0)};
        signalMatrix = new MatrixND(sizes);
        skip = new BooleanMatrixND(sizes);
        for (int i = 0; i < data.getSize(0); i++) {
            signalMatrix.setValue(data.getValue(i), i);
        }
        int[][] pt = new int[signalMatrix.getNDim()][2];
        return iterativeConvolutions(threshold, iterations, pt, doSquash);
    }

    private List<DatasetRegion> getRegion(Dataset dataset, int nPeakDim) {
        List<DatasetRegion> regions = new ArrayList<>();
        double[] x = new double[nPeakDim * 2];
        DatasetRegion region = new DatasetRegion(x);
        for (int iDim = 0; iDim < nPeakDim; iDim++) {
            double ppm1 = dataset.pointToPPM(iDim, 0);
            double ppm2 = dataset.pointToPPM(iDim, dataset.getSizeReal(iDim) - 1.0);
            region.setRegionStart(iDim, ppm1);
            region.setRegionEnd(iDim, ppm2);
        }
        regions.add(region);
        return regions;
    }

    private List<DatasetRegion> getBlockRegions(Dataset dataset, int nPeakDim, double threshold) throws IOException {
        List<DatasetRegion> regions = new ArrayList<>();
        int[] nWins = new int[nPeakDim];
        int[] winSizes = new int[nPeakDim];
        for (int iDim = 0; iDim < nPeakDim; iDim++) {
            int size = dataset.getSizeReal(iDim);
            int psfSize = psfDim[iDim];
            winSizes[iDim] = (psfSize - 1) * 8;
            nWins[iDim] = (int) Math.ceil((double) size / winSizes[iDim]);
        }
        MultidimensionalCounter winCounter = new MultidimensionalCounter(nWins);
        var iterator = winCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            double[] x = new double[nPeakDim * 2];
            int[] counts = iterator.getCounts();
            boolean ok = true;
            for (int iDim = 0; iDim < nPeakDim; iDim++) {
                int start = counts[iDim] * winSizes[iDim];
                int end = start + winSizes[iDim] - 1;
                if (end >= dataset.getSizeReal(iDim)) {
                    end = dataset.getSizeReal(iDim) - 1;
                }
                int size = end - start + 1;
                if (size > 0) {
                    double ppm1 = dataset.pointToPPM(iDim, start);
                    double ppm2 = dataset.pointToPPM(iDim, end);
                    x[iDim * 2] = ppm1;
                    x[iDim * 2 + 1] = ppm2;
                } else {
                    ok = false;
                }

            }
            if (ok) {

                DatasetRegion datasetRegion = new DatasetRegion(x);
                RegionData rData = dataset.analyzeRegion(datasetRegion);
                if (rData.getMax() > threshold) {
                    regions.add(datasetRegion);
                }
            }
        }
        return regions;
    }

    public void iterativeConvolutions(Dataset dataset, PeakList peakList, double threshold, int iterations) throws IOException {
        var regions = dataset.getReadOnlyRegions();
        if (regions.isEmpty()) {
            regions = getBlockRegions(dataset, peakList.getNDim(), threshold);
        }
        System.out.println("nregions " + regions.size());

        allPeaks = new ArrayList<>();

        for (DatasetRegion region : regions) {
            cPeakList = new ArrayList<>();
            int nDim = dataset.getNDim();
            int[][] pt = new int[nDim][2];
            int[] dim = new int[nDim];
            int[] sizes = new int[nDim];
            for (int iDim = 0; iDim < nDim; iDim++) {
                int pt1 = dataset.ppmToPoint(iDim, region.getRegionStart(iDim));
                int pt2 = dataset.ppmToPoint(iDim, region.getRegionEnd(iDim));
                if (pt1 > pt2) {
                    int hold = pt1;
                    pt1 = pt2;
                    pt2 = hold;
                }
                pt[iDim][0] = pt1;
                pt[iDim][1] = pt2;
                sizes[iDim] = pt2 - pt1 + 1;
                dim[iDim] = iDim;
                System.out.println(iDim + " " + pt1 + " " + pt2 + " " + sizes[iDim]);
            }
            signalMatrix = new MatrixND(sizes);
            dataset.readMatrixND(pt, dim, signalMatrix);

            skip = new BooleanMatrixND(sizes);

            MatrixND result = iterativeConvolutions(threshold, iterations, pt, true);


        }
        peakDistances();
        allPeaks.stream().filter(cp -> cp != null).forEach(cPeakD -> {
            buildPeak(dataset, peakList, cPeakD);
        });
    }

    void peakDistances() {
        System.out.println("peak dist");
        double limit = squash;
        int nPeaks = allPeaks.size();
        for (int i = 0; i < nPeaks; i++) {
            CPeakD cPeak1 = allPeaks.get(i);
            if (cPeak1 == null) {
                continue;
            }
            for (int j = i + 1; j < nPeaks; j++) {
                CPeakD cPeak2 = allPeaks.get(j);
                if (cPeak2 != null) {
                    double distance = cPeak1.distance(cPeak2, widths);
                    if (distance < limit) {
                        allPeaks.set(j, null);
                        cPeak1 = cPeak1.merge(cPeak2);
                        allPeaks.set(i, cPeak1);
                        System.out.println(i + " " + j + " " + distance);
                    }
                }
            }
        }
    }


    void buildPeak(Dataset dataset, PeakList peakList, CPeakD cPeakD) {
        if (Double.isFinite(cPeakD.height)) {
            Peak peak = peakList.getNewPeak();
            double dxProduct = 1.0;
            for (int i = 0; i < cPeakD.position.length; i++) {
                PeakDim peakDim = peak.getPeakDim(i);
                double x1 = dataset.pointToPPM(i, cPeakD.position[i] - widths[i] / 2.0);
                double x2 = dataset.pointToPPM(i, cPeakD.position[i] + widths[i] / 2.0);
                double dx = Math.abs(x2 - x1);
                double shift = dataset.pointToPPM(i, cPeakD.position[i]);
                peakDim.setChemShiftValue((float) shift);
                peakDim.setLineWidthValue((float) dx);
                peakDim.setBoundsValue((float) (dx * 2.0));
                dxProduct *= dx;
            }
            double intensity = cPeakD.height * psfMax;
            peak.setIntensity((float) intensity);
            double volume = intensity * dxProduct * (Math.PI / 2.0) / 1.05;
            peak.setVolume1((float) volume);
        }
    }
}
