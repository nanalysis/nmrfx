package org.nmrfx.processor.math;

import com.google.common.util.concurrent.AtomicDouble;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.jtransforms.fft.DoubleFFT_2D;
import org.jtransforms.fft.DoubleFFT_3D;
import org.nmrfx.processor.datasets.peaks.LineShapes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IterativeConvolutions {

    double psfMax;
    int[] psfDim;
    int psfTotalSize;
    final double[] widths;
    MatrixND psfMatrix;
    public double squash = 0.625;
    double threshold = 0.0;
    MultidimensionalCounter counter;
    MultidimensionalCounter neighborCounter;

    public IterativeConvolutions(int n0, int width, double shapeFactor) {
        int[] n = {n0};
        widths = new double[1];
        widths[0] = width;
        makePSF(n, widths, shapeFactor);
    }

    public IterativeConvolutions(int[] n, double[] widths, double shapeFactor) {
        makePSF(n, widths, shapeFactor);
        this.widths = widths.clone();
    }

    void makePSF(int[] n, double[] width, double shapeFactor) {
        int nDim = n.length;
        psfDim = n.clone();
        double[][] yValues = new double[nDim][];
        int totalSize = 1;
        for (int iDim = 0; iDim < nDim; iDim++) {
            yValues[iDim] = new double[n[iDim]];
            totalSize *= n[iDim];
            for (int i = 0; i < n[iDim]; i++) {
                double x = -(n[iDim] - 1) / 2.0 + i;
                yValues[iDim][i] = LineShapes.G_LORENTZIAN.calculate(x, 1.0, 0.0, width[iDim], shapeFactor);
            }
        }
        psfTotalSize = totalSize;
        counter = new MultidimensionalCounter(n);
        psfMatrix = makePSFMatrix(yValues);
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

    private static int nextPowerOfTwo(int n) {
        int pow = 1;
        while (pow < n) pow *= 2;
        return pow;
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
        int[] padding = new int[nDim];
        boolean pad = false;
        for (int i = 0; i < nDim; i++) {
            int n = a.getSize(i) + b.getSize(i) - 1;
            int fftLen = nextPowerOfTwo(n);
            newSizes2[i] = i == 0 ? 2 * fftLen : fftLen;
            if (pad) {
                padding[i] = (b.getSize(i) - 1) / 2;
            }
        }

        MatrixND aPadded2 = new MatrixND(a);
        MatrixND bPadded2 = new MatrixND(b);

        if (pad) {
            aPadded2.pad(padding);
        }

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
                if (i == 0) {
                    indices[i] = 2 * (counts[i] + padding[i] + offsets[i]);
                } else {
                    indices[i] = (counts[i] + padding[i] + offsets[i]);
                }
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

        double[][] input = to2D(a);
        double[][] kernel = to2D(b);

        // Pad input and kernel to same size
        double[][] inputPadded = new double[rows][cols];
        double[][] kernelPadded = new double[rows][cols];

        for (int i = 0; i < rows; i++)
            System.arraycopy(input[i], 0, inputPadded[i], 0, cols);
        for (int i = 0; i < kRows; i++)
            System.arraycopy(kernel[i], 0, kernelPadded[i], 0, kCols);

        // Convert to complex arrays: real + imaginary interleaved
        double[][] inputComplex = realToComplex(inputPadded);
        double[][] kernelComplex = realToComplex(kernelPadded);

        DoubleFFT_2D fft = new DoubleFFT_2D(rows, cols);
        fft.complexForward(inputComplex);
        fft.complexForward(kernelComplex);

        // Point-wise complex multiplication
        double[][] resultComplex = new double[rows][2 * cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
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
            int ii = i + offsets[0];
            if (ii >= resultComplex.length) {
                continue;
            }
            for (int j = 0; j < cols; j++) {
                int jj = 2 * (j + offsets[1]);
                if (jj >= resultComplex[0].length) {
                    continue;
                }
                result[i][j] = resultComplex[ii][jj];
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

    public MatrixND convolutionTest(double[] observed) {
        MatrixND matrixND = new MatrixND(observed);
        return convolve(matrixND, psfMatrix);
    }

    public MatrixND convolutionTest2D(double[][] observed) {
        MatrixND matrixND = new MatrixND(observed);
        return convolve(matrixND, psfMatrix);
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

    static boolean inRegion(int[] counts, int[] limits) {
        boolean ok = true;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] >= limits[i]) {
                ok = false;
                break;
            }
        }
        return ok;
    }

    public static MatrixND iterativeConvolution(MatrixND observed, MatrixND estimate, MatrixND psf, int iterations) {
        int[] limits = new int[observed.getNDim()];
        for (int i = 0; i < limits.length; i++) {
            limits[i] = observed.getSize(i) - (psf.getSize(i) - 1) / 2;
        }
        for (int iter = 0; iter < iterations; iter++) {
            MatrixND convEstimate = convolve(estimate, psf);
            AtomicDouble maxValue2 = new AtomicDouble(0.0);
            AtomicDouble maxDelta = new AtomicDouble(0.0);
            observed.stream().filter(counts -> inRegion(counts, limits)).forEach(counts -> {
                double oldValue = estimate.getValue(counts);
                double obsValue = observed.getValue(counts);
                double v = convEstimate.getValue(counts);
                if (Double.isNaN(v) || (Math.abs(v) < 1.0e-6)) {
                    v = 1.0e-6;
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


    public  MatrixND iterativeConvolutions(MatrixND signalMatrix, BooleanMatrixND skip, double threshold, int iterations,
                                           int[][] pt, boolean doSquash, List<ConvolutionFitter.CPeakD> allPeaks) {
        MatrixND valueMatrix = new MatrixND(signalMatrix.getSizes());
        valueMatrix.stream().forEach(counts -> {
            if (signalMatrix.getValue(counts) > threshold) {
                valueMatrix.setValue(signalMatrix.getValue(counts), counts);
            } else {
                skip.setValue(true, counts);
            }
        });


        MatrixND estimates = iterativeConvolution(signalMatrix, valueMatrix, psfMatrix, iterations);

        List<ConvolutionFitter.CPeak> cPeakList = new ArrayList<>();
        findPeaks(estimates, skip, cPeakList);
        if (doSquash) {
            squashPeaks(estimates, skip, pt, cPeakList, allPeaks);
        }
        return estimates;
    }

    public MatrixND iterativeConvolutions(MatrixND data, double threshold, int iterations, boolean doSquash) {
        List<ConvolutionFitter.CPeakD> allPeaks = new ArrayList<>();
        int[] sizes = {data.getSize(0)};
        MatrixND signalMatrix = new MatrixND(sizes);
        BooleanMatrixND skip = new BooleanMatrixND(sizes);
        for (int i = 0; i < data.getSize(0); i++) {
            signalMatrix.setValue(data.getValue(i), i);
        }
        int[][] pt = new int[signalMatrix.getNDim()][2];
        return iterativeConvolutions(signalMatrix, skip, threshold, iterations, pt, doSquash, allPeaks);
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

    void findPeaks(MatrixND values, BooleanMatrixND skipMat, List<ConvolutionFitter.CPeak> cPeakList) {
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
                    ConvolutionFitter.CPeak cPeak = new ConvolutionFitter.CPeak(counts, value);
                    cPeakList.add(cPeak);
                }
            }
        });
    }

    ConvolutionFitter.CPeakD accumulateNeighbors(MatrixND values, BooleanMatrixND skipMatrix, int[] indices, int[] halfWidths) {
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

        return new ConvolutionFitter.CPeakD(sumPositions, sumHeight);
    }

    public void squashPeaks(MatrixND values, BooleanMatrixND skipMatrix, int[][] pt, List<ConvolutionFitter.CPeak> cPeakList, List<ConvolutionFitter.CPeakD> allPeaks ) {
        int[] neighborDims = new int[values.getNDim()];
        int[] halfWidths = new int[neighborDims.length];
        for (int i = 0; i < neighborDims.length; i++) {
            halfWidths[i] = (int) Math.ceil(widths[i] * squash) / 2;
            neighborDims[i] = 2 * halfWidths[i] + 1;
        }

        List<ConvolutionFitter.CPeak> newPeaks = new ArrayList<>();


        for (ConvolutionFitter.CPeak cPeak1 : cPeakList) {
            int[] center1 = cPeak1.position();
            if (skipMatrix.getValue(center1)) {
                continue;
            }
            ConvolutionFitter.CPeakD cPeakD = accumulateNeighbors(values, skipMatrix, center1, halfWidths);
            int[] newPosition = new int[cPeak1.position().length];
            for (int j = 0; j < newPosition.length; j++) {
                cPeakD.position()[j] = cPeakD.position()[j] + pt[j][0];
            }
            allPeaks.add(cPeakD);
            newPeaks.add(new ConvolutionFitter.CPeak(center1, cPeakD.height()));
        }
        values.fill(0.0);
        skipMatrix.set(true);
        for (ConvolutionFitter.CPeak cPeak : newPeaks) {
            values.setValue(cPeak.height(), cPeak.position());
            skipMatrix.setValue(false, cPeak.position());
        }
    }

    public void squash(double squash) {
        this.squash = squash;
    }
}
