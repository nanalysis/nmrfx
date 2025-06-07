package org.nmrfx.processor.math;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.LineShapes;

import org.jtransforms.fft.DoubleFFT_2D;

import java.util.Arrays;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ConvolutionFitter {
    static Random rand = new Random();
    double psfMax;
    int[] psfDim;
    int psfTotalSize;
    double[] widths;
    MatrixND sim;
    MatrixND signalMatrix;
    MatrixND psfMatrix;
    boolean[] skip;
    double squash = 0.625;
    double threshold = 0.0;
    int start = 0;

    MultidimensionalCounter counter;

    List<CPeak> cPeakList;

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

    public boolean[] skipVector() {
        return skip;
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

    boolean isHigherThanNeighbor(MatrixND values, int i, int searchWidth) {
        boolean higher = true;
        for (int j = -searchWidth; j <= searchWidth; j++) {
            int k = i + j;
            if ((j != 0) && (values.getValue(i) < values.getValue(k))) {
                higher = false;
                break;
            }
        }
        return higher;
    }

    record CPeak(int position, double height) {

    }

    void findPeaks(MatrixND values, boolean[] skip) {
        int half = (int) Math.ceil(widths[0] * squash) / 2;
        cPeakList = new ArrayList<>();
        for (int i = half; i < values.getSize(0) - half; i++) {
            if (Math.abs(values.getValue(i)) < threshold) {
                skip[i] = true;
                continue;
            }
            if (!skip[i]) {
                boolean highest = isHigherThanNeighbor(values, i, half);
                if (highest) {
                    CPeak cPeak = new CPeak(i, values.getValue(i));
                    cPeakList.add(cPeak);
                }
            }
        }
    }

    public void squashPeaks(MatrixND values, boolean[] skip) {
        int half = (int) Math.ceil(widths[0] * squash);

        List<CPeak> newPeaks = new ArrayList<>();

        for (int i = 0; i < cPeakList.size(); i++) {
            CPeak cPeak1 = cPeakList.get(i);
            int center1 = cPeak1.position;
            int center0 = Math.max(0, center1 - half);
            int center2 = Math.min(values.getSize(0) - 1, center1 + half);
            if (i > 0) {
                CPeak cPeak0 = cPeakList.get(i - 1);
                if ((cPeak0.position + half) > center0) {
                    center0 = (cPeak0.position + center1) / 2;
                }
            }
            if (i < cPeakList.size() - 1) {
                CPeak cPeak2 = cPeakList.get(i + 1);
                if ((cPeak2.position - half) < center2) {
                    center2 = (cPeak2.position + center1) / 2;
                }
            }
            double sumHeight = 0.0;
            for (int k = center0; k <= center2; k++) {
                int j = center1 - k;
                int ipsf = (psfMatrix.getSize(0) - 1) / 2 + j;
                if ((ipsf > 0) && (ipsf < psfMatrix.getSize(0))) {
                    sumHeight += values.getValue(k) * psfMatrix.getValue(ipsf) / psfMax;
                }
                values.setValue(0.0, k);
                skip[k] = true;
            }
            newPeaks.add(new CPeak(cPeak1.position, sumHeight));
            values.setValue(sumHeight, center1);
            skip[center1] = false;
        }
        values.fill(0.0);
        Arrays.fill(skip, true);
        for (CPeak cPeak : newPeaks) {
            values.setValue(cPeak.height, cPeak.position);
            skip[cPeak.position] = false;
        }
    }


    public static MatrixND convolve(MatrixND a, MatrixND b) {
        int nDim = a.getNDim();
        if (nDim == 2) {
            return convolve2D(a, b);
        }
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
        int[] counterSizes = new int[nDim];
        for (int i = 0; i < counterSizes.length; i++) {
            counterSizes[i] = a.getSize(i) - offsets[i];
        }
        MultidimensionalCounter mCounter = new MultidimensionalCounter(counterSizes);
        var iterator = mCounter.iterator();
        int[] indices = new int[nDim];
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            for (int i = 0; i < nDim; i++) {
                indices[i] = 2 * (counts[i] + offsets[i]);
            }
            double value = aPadded2.getValue(indices);
            result.setValue(value, counts);
        }
        return result;
    }

    public static double[][] to2D(MatrixND a) {
        int rows = a.getSize(0);
        int cols = a.getSize(1);
        double[][] x = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            x[i] = a.getVector(0, i);
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
        double[][] result = new double[outRows][outCols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = resultComplex[i + offsets[0]][2 * (j + offsets[1])];
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


    public MatrixND convolutionTest(double[] observed) {
        MatrixND matrixND = new MatrixND(observed);
        return convolve(matrixND, psfMatrix);
    }

    public MatrixND convolutionTest2D(double[][] observed) {
        MatrixND matrixND = new MatrixND(observed);
        return convolve(matrixND, psfMatrix);
    }

    public static MatrixND iterativeConvolution(MatrixND observed, MatrixND estimate, MatrixND psf, int iterations) {
        int len = observed.getSize(0);

        for (int iter = 0; iter < iterations; iter++) {
            MatrixND convEstimate = convolve(estimate, psf);
            double[] ratio = new double[len];

            for (int i = 0; i < len; i++) {
                double v = (convEstimate.getValue(i) == 0.0) ? 1e-10 : convEstimate.getValue(i);
                ratio[i] = observed.getValue(i) / v;
            }

            for (int i = 0; i < len; i++) {
                estimate.setValue(estimate.getValue(i) * ratio[i], i);
            }
        }

        return estimate;
    }

    private static int nextPowerOfTwo(int n) {
        int pow = 1;
        while (pow < n) pow *= 2;
        return pow;
    }

    public MatrixND iterativeConvolutions(double threshold, int iterations, boolean doSquash) {
        if (skip == null) {
            skip = new boolean[signalMatrix.getSize(0)];
        }
        this.threshold = threshold;
        DescriptiveStatistics summaryStatistics = new DescriptiveStatistics();
        for (int i = 0; i < signalMatrix.getSize(0); i++) {
            summaryStatistics.addValue(signalMatrix.getValue(i));
        }
        double mean = summaryStatistics.getMean();
        MatrixND valueMatrix = new MatrixND(signalMatrix.getSizes());
        double[] values = new double[signalMatrix.getSize(0)];
        for (int i = 0; i < values.length; i++) {
            if (signalMatrix.getValue(i) > threshold) {
                valueMatrix.setValue(signalMatrix.getValue(i), i);
            } else {
                skip[i] = true;
            }
        }

        MatrixND estimates = iterativeConvolution(signalMatrix, valueMatrix, psfMatrix, iterations);

        findPeaks(estimates, skip);
        if (doSquash) {
            squashPeaks(estimates, skip);
        }
        sim = convolve(estimates, psfMatrix);
        return estimates;
    }

    public MatrixND iterativeConvolutions(MatrixND data, double threshold, int iterations, boolean doSquash) {
        int[] sizes = {data.getSize(0)};
        signalMatrix = new MatrixND(sizes);
        skip = new boolean[data.getSize(0)];
        for (int i = 0; i < data.getSize(0); i++) {
            signalMatrix.setValue(data.getValue(i), i);
        }
        return iterativeConvolutions(threshold, iterations, doSquash);
    }

    public void iterativeConvolutions(Dataset dataset, PeakList peakList, double threshold, int iterations) throws IOException {
        var vec = dataset.readVector(0, 0);
        var regions = dataset.getReadOnlyRegions();
        if (regions.isEmpty()) {
            regions = new ArrayList<>();
            double[] x = new double[peakList.getNDim() * 2];
            int nDim = peakList.getNDim();
            DatasetRegion region = new DatasetRegion(x);
            for (int iDim = 0; iDim < nDim; iDim++) {
                double ppm1 = dataset.pointToPPM(iDim, 0);
                double ppm2 = dataset.pointToPPM(iDim, dataset.getSizeReal(iDim) - 1.0);
                region.setRegionStart(iDim, ppm1);
                region.setRegionEnd(iDim, ppm2);
            }
            regions.add(region);
        }
        int nDim = dataset.getNDim();
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        int[] sizes = new int[nDim];
        for (DatasetRegion region : regions) {
            for (int iDim = 0; iDim < nDim; iDim++) {
                int pt1 = dataset.ppmToPoint(0, region.getRegionStart(0));
                int pt2 = dataset.ppmToPoint(0, region.getRegionEnd(0));
                if (pt1 > pt2) {
                    int hold = pt1;
                    pt1 = pt2;
                    pt2 = hold;
                }
                pt[iDim][0] = pt1;
                pt[iDim][1] = pt2;
                sizes[iDim] = pt2 - pt1 + 1;
                dim[iDim] = iDim;
            }
            signalMatrix = new MatrixND(sizes);
            dataset.readMatrixND(pt, dim, signalMatrix);

            skip = new boolean[sizes[0]];

            start = pt[0][0];
            MatrixND result = iterativeConvolutions(threshold, iterations, true);
            Vec vec2 = new Vec(32);
            vec.copy(vec2);
            for (int i = 0; i < sim.getSize(0); i++) {
                vec2.set(i, sim.getValue(i));
            }
            vec2.setName("simdata");
            Dataset _ = new Dataset(vec2);

            for (int i = 0; i < result.getSize(0); i++) {
                if (!skip[i]) {
                    double x1 = vec.pointToPPM(start + i - widths[0] / 2.0);
                    double x2 = vec.pointToPPM(start + i + widths[0] / 2.0);
                    double dx = Math.abs(x2 - x1);
                    double intensity = result.getValue(i) * psfMax;
                    double volume = intensity * dx * (Math.PI / 2.0) / 1.05;
                    if (Double.isFinite(result.getValue(i))) {
                        Peak peak = peakList.getNewPeak();
                        PeakDim peakDim = peak.getPeakDim(0);
                        double shift = vec.pointToPPM((double) start + i);
                        peakDim.setChemShiftValue((float) shift);
                        peakDim.setLineWidthValue((float) dx);
                        peakDim.setBoundsValue((float) (dx * 2.0));
                        peak.setIntensity((float) intensity);
                        peak.setVolume1((float) volume);
                    }

                }
            }
        }
    }
}
