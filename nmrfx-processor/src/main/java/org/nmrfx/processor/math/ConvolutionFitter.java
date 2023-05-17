package org.nmrfx.processor.math;

import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.LineShapes;

import java.io.IOException;

public class ConvolutionFitter {
    static final int SQUASH_AT = 10;
    final double[] psf;
    double[] widths;
    double squash = 0.625;

    double[] makePSF(int n, double width, double shapeFactor) {
        double[] yValues = new double[n];
        for (int i = 0; i < n; i++) {
            double x = -n / 2.0 + i;
            yValues[i] = LineShapes.G_LORENTZIAN.calculate(x, 1.0, 0.0, width, shapeFactor);
        }
        return yValues;
    }

    public ConvolutionFitter(int n, int width, double shapeFactor) {
        psf = makePSF(n, width, shapeFactor);
        widths = new double[1];
        widths[0] = width;
    }

    public void squash(double value) {
        this.squash = value;
    }

    public double[] convolve(double[] values, boolean[] skip) {
        int n = psf.length;
        int nh = n / 2;
        int size = values.length;
        int end = size - n;
        double[] result = new double[size];
        for (int j = 0; j < end; j++) {
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                if (!skip[j + i]) {
                    sum += values[j + i] * psf[i];
                }
            }
            sum /= n;
            result[j + nh] = sum;
        }
        return result;
    }

    public PointValuePair lrIteration(double[] signal, double[] values, boolean[] skip) {
        int n = psf.length;
        int nh = n / 2;
        int size = values.length;
        int end = size - n;
        double[] result = new double[size];
        double sumDelta = 0.0;
        int nDelta = 0;
        for (int j = 0; j < end; j++) {
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                if (!skip[j + i]) {
                    sum += values[j + i] * psf[i];
                }
            }
            if ((sum > 1.0e-8) && !skip[j + nh]) {
                sum /= n;
                result[j + nh] = values[j + nh] * signal[j + nh] / sum;
                double delta = Math.abs(result[j + nh] - values[j + nh]);
                sumDelta += delta * delta;
                nDelta++;
            }
        }
        double rms = nDelta > 0 ? Math.sqrt(sumDelta / nDelta) : 0.0;
        return new PointValuePair(result, rms);
    }

    void squashNeighbors(double[] values, boolean[] skip) {
        int half = (int) Math.ceil(widths[0] * squash);
        for (int i = half; i < values.length - half; i++) {
            for (int j = -half; j <= half; j++) {
                if ((i != j) && (values[i] < values[i + j])) {
                    skip[i] = true;
                }
            }
        }
    }

    double[] lr(double[] signal, boolean[] skip, double threshold, int iterations) {
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
        for (int i = 0; i < iterations; i++) {
            if (i == SQUASH_AT) {
                squashNeighbors(values, skip);
            }
            PointValuePair pointValuePair = lrIteration(signal, values, skip);
            values = pointValuePair.getPoint();
            if ((i > SQUASH_AT) && (pointValuePair.getValue() < 1.0e-6)) {
                break;
            }
        }
        return values;
    }

    public double[] lr(Vec vec, boolean[] skip, double threshold, int iterations) {
        double[] signal = new double[vec.getSize()];
        for (int i = 0; i < signal.length; i++) {
            signal[i] = vec.getReal(i);
        }
        double[] result = lr(signal, skip, threshold, iterations);
        double[] sim = convolve(result, skip);
        for (int i = 0; i < signal.length; i++) {
            vec.setReal(i, sim[i]);
        }
        return result;
    }

    public void lr(Dataset dataset, PeakList peakList, double threshold, int iterations) throws IOException {
        Vec vec = dataset.readVector(0, 0);
        var regions = dataset.getReadOnlyRegions();
        if (regions.isEmpty()) {
            double ppm1 = dataset.pointToPPM(0, 0);
            double ppm2 = dataset.pointToPPM(0, dataset.getSizeReal(0) - 1.0);
            DatasetRegion region = new DatasetRegion(ppm1, ppm2);
            regions.add(region);
        }
        for (DatasetRegion region:regions) {
            int pt1 = dataset.ppmToPoint(0, region.getRegionStart(0));
            int pt2 = dataset.ppmToPoint(0, region.getRegionEnd(0));
            if (pt1 > pt2) {
                int hold = pt1;
                pt1 = pt2;
                pt2 = hold;
            }
            int size = pt2 - pt1 + 1;

            int psfSize = psf.length;
            double[] signal = new double[size + 2 * psfSize];
            for (int i = 0; i < size; i++) {
                signal[i + psfSize] = vec.getReal(i + pt1);
            }
            boolean[] skip = new boolean[signal.length];

            double[] result = lr(signal, skip, threshold, iterations);
            int start = pt1 - psfSize;
            for (int i = 0; i < result.length; i++) {
                if (!skip[i]) {
                    Peak peak = peakList.getNewPeak();
                    PeakDim peakDim = peak.getPeakDim(0);
                    double shift = vec.pointToPPM((double) start + i);
                    peakDim.setChemShiftValue((float) shift);
                    double x1 = vec.pointToPPM(start + i - widths[0] / 2.0);
                    double x2 = vec.pointToPPM(start + i + widths[0] / 2.0);
                    double dx = Math.abs(x2 - x1);
                    peakDim.setLineWidthValue((float) dx);
                    peakDim.setBoundsValue((float) (dx * 2.0));
                    double intensity = result[i] / psfSize;
                    peak.setIntensity((float) intensity);
                    double volume = intensity * dx * (Math.PI / 2.0) / 1.05;
                    peak.setVolume1((float) volume);
                }
            }
        }
    }
}
