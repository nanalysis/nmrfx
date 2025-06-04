package org.nmrfx.processor.math;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.LineShapes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ConvolutionFitter {
    static Random rand = new Random();
    final double[][] psf;
    double psfMax;
    int psfTotalSize;
    double[] widths;
    double[] sim;
    double[] signal;
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
        psf = makePSF(n, widths, shapeFactor);
    }

    public ConvolutionFitter(int[] n, double[] widths, double shapeFactor) {
        psf = makePSF(n, widths, shapeFactor);
        this.widths = widths.clone();
    }

    double[][] makePSF(int[] n, double[] width, double shapeFactor) {
        int nDim = n.length;
        double[][] yValues = new double[nDim][];
        double max = 0.0;
        int totalSize = 1;
        for (int iDim = 0; iDim < nDim; iDim++) {
            yValues[iDim] = new double[n[iDim]];
            totalSize *= n[iDim];
            double sum = 0.0;
            for (int i = 0; i < n[iDim]; i++) {
                double x = -(n[iDim] - 1) / 2.0 + i;
                yValues[iDim][i] = LineShapes.G_LORENTZIAN.calculate(x, 1.0, 0.0, width[iDim], shapeFactor);
                sum += yValues[iDim][i];
            }
            for (int i = 0; i < n[iDim]; i++) {
                yValues[iDim][i] /= sum;
                if (yValues[iDim][i] > max) {
                    max = yValues[iDim][i];
                }
            }
        }
        psfTotalSize = totalSize;
        psfMax = max;
        counter = new MultidimensionalCounter(n);
        return yValues;
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

    // used from Python for testing
    public double[][] psf() {
        return psf;
    }

    public double[] simVector() {
        return sim;
    }

    public double[] signalVector() {
        return signal;
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

    boolean isHigherThanNeighbor(double[] values, int i, int searchWidth) {
        boolean higher = true;
        for (int j = -searchWidth; j <= searchWidth; j++) {
            int k = i + j;
            if ((j != 0) && (values[i] < values[k])) {
                higher = false;
                break;
            }
        }
        return higher;
    }

    record CPeak(int position, double height) {

    }

    void findPeaks(double[] values, boolean[] skip) {
        int half = (int) Math.ceil(widths[0] * squash) / 2;
        cPeakList = new ArrayList<>();
        for (int i = half; i < values.length - half; i++) {
            if (Math.abs(values[i]) < threshold) {
                skip[i] = true;
                continue;
            }
            if (!skip[i]) {
                boolean highest = isHigherThanNeighbor(values, i, half);
                if (highest) {
                    CPeak cPeak = new CPeak(i, values[i]);
                    cPeakList.add(cPeak);
                }
            }
        }
    }

    public void squashPeaks(double[] values, boolean[] skip) {
        int half = (int) Math.ceil(widths[0] * squash);

        List<CPeak> newPeaks = new ArrayList<>();

        for (int i = 0; i < cPeakList.size(); i++) {
            CPeak cPeak1 = cPeakList.get(i);
            int center1 = cPeak1.position;
            int center0 = Math.max(0, center1 - half);
            int center2 = Math.min(values.length - 1, center1 + half);
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
                int ipsf = (psf[0].length - 1) / 2 + j;
                if ((ipsf > 0) && (ipsf < psf[0].length)) {
                    sumHeight += values[k] * psf[0][ipsf] / psfMax;
                }
                values[k] = 0.0;
                skip[k] = true;
            }
            newPeaks.add(new CPeak(cPeak1.position, sumHeight));
            values[center1] = sumHeight;
            skip[center1] = false;
        }
        Arrays.fill(values, 0.0);
        Arrays.fill(skip, true);
        for (CPeak cPeak : newPeaks) {
            values[cPeak.position] = cPeak.height;
            skip[cPeak.position] = false;
        }
    }


    public static double[] convolve(double[] a, double[] b) {
        int n = a.length + b.length - 1;
        int fftLen = nextPowerOfTwo(n);

        double[] aPadded = Arrays.copyOf(a, fftLen);
        double[] bPadded = Arrays.copyOf(b, fftLen);

        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        Complex[] signalArray = fft.transform(aPadded, TransformType.FORWARD);
        Complex[] shapeArray = fft.transform(bPadded, TransformType.FORWARD);

        Complex[] productArray = new Complex[fftLen];
        for (int i = 0; i < fftLen; i++) {
            productArray[i] = signalArray[i].multiply(shapeArray[i]);
        }

        Complex[] cComplex = fft.transform(productArray, TransformType.INVERSE);

        double[] result = new double[a.length];
        int offset = (b.length - 1) / 2;
        for (int i = offset; i < a.length; i++) {
            result[i - offset] = cComplex[i].getReal();
        }

        return result;
    }

    public double[] convolutionTest(double[] observed) {
        return convolve(observed, psf[0]);
    }

    public static double[] iterativeConvolution(double[] observed, double[] estimate, double[] psf, int iterations) {
        int len = observed.length;

        for (int iter = 0; iter < iterations; iter++) {
            double[] convEstimate = convolve(estimate, psf);
            double[] ratio = new double[len];

            for (int i = 0; i < len; i++) {
                double v = (convEstimate[i] == 0.0) ? 1e-10 : convEstimate[i];
                ratio[i] = observed[i] / v;
            }

            for (int i = 0; i < len; i++) {
                estimate[i] *= ratio[i];
            }
        }

        return estimate;
    }

    private static int nextPowerOfTwo(int n) {
        int pow = 1;
        while (pow < n) pow *= 2;
        return pow;
    }

    public double[] iterativeConvolutions(double threshold, int iterations, boolean doSquash) {
        if (skip == null) {
            skip = new boolean[signal.length];
        }
        this.threshold = threshold;
        DescriptiveStatistics summaryStatistics = new DescriptiveStatistics(signal);
        double mean = summaryStatistics.getMean();
        double[] values = new double[signal.length];
        for (int i = 0; i < values.length; i++) {
            if (signal[i] > threshold) {
                values[i] = mean;
            } else {
                skip[i] = true;
                values[i] = 0.0;
            }
        }

        double[] estimates = iterativeConvolution(signal, values, psf[0], iterations);

        findPeaks(estimates, skip);
        if (doSquash) {
            squashPeaks(estimates, skip);
        }
        sim = convolve(estimates, psf[0]);
        return estimates;
    }

    public double[] iterativeConvolutions(double[] data, double threshold, int iterations, boolean doSquash) {
        signal = new double[data.length];
        skip = new boolean[data.length];
        System.arraycopy(data, 0, signal, 0, signal.length);
        return iterativeConvolutions(threshold, iterations, doSquash);
    }

    public void iterativeConvolutions(Dataset dataset, PeakList peakList, double threshold, int iterations) throws IOException {
        Vec vec = dataset.readVector(0, 0);
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
            MatrixND matrixND = new MatrixND(sizes);
            dataset.readMatrixND(pt, dim, matrixND);

            signal = new double[sizes[0]];
            skip = new boolean[signal.length];
            for (int i = 0; i < sizes[0]; i++) {
                signal[i] = matrixND.getValue(i);
            }

            start = pt[0][0];
            double[] result = iterativeConvolutions(threshold, iterations, true);
            Vec vec2 = new Vec(32);
            vec.copy(vec2);
            for (int i = 0; i < sim.length; i++) {
                vec2.set(i, sim[i]);
            }
            vec2.setName("simdata");
            Dataset _ = new Dataset(vec2);

            for (int i = 0; i < result.length; i++) {
                if (!skip[i]) {
                    double x1 = vec.pointToPPM(start + i - widths[0] / 2.0);
                    double x2 = vec.pointToPPM(start + i + widths[0] / 2.0);
                    double dx = Math.abs(x2 - x1);
                    double intensity = result[i] * psfMax;
                    double volume = intensity * dx * (Math.PI / 2.0) / 1.05;
                    if (Double.isFinite(result[i])) {
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
