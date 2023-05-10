package org.nmrfx.processor.math;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.LineShapes;
import org.nmrfx.processor.datasets.peaks.PeakFitParameters;
import org.nmrfx.project.ProjectBase;

import java.io.IOException;

public class ConvolutionFitter {
    public static final double SQRT_TWO_LN2 = Math.sqrt(2.0 * Math.log(2.0));
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

    public double[] lrIteration(double[] signal, double[] values, boolean[] skip) {
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
            sum /= n;
            if (sum > 1.0e-8 && !skip[j + nh]) {
                result[j + nh] = values[j + nh] * signal[j + nh] / sum;
                double delta = Math.abs(result[j + nh] - values[j + nh]);
                sumDelta += delta * delta;
                nDelta++;
            }
        }
        double rms = Math.sqrt(sumDelta / nDelta);
        System.out.println("rms " + rms);
        return result;
    }

    void squash(double[] values, boolean[] skip) {
        int width = (int) Math.ceil(widths[0] * squash);
        int half = width;
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
            if (i == 10) {
                squash(values, skip);
            }
            values = lrIteration(signal, values, skip);
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

    public void lr(Dataset dataset, double threshold, int iterations) throws IOException {
        Vec vec = dataset.readVector(0, 0);

        double[] signal = new double[vec.getSize()];
        for (int i = 0; i < signal.length; i++) {
            signal[i] = vec.getReal(i);
        }
        boolean[] skip = new boolean[signal.length];

        double[] result = lr(signal, skip, threshold, iterations);
        double[] sim = convolve(result, skip);

        Vec vec2 = new Vec(vec.getSize());
        vec.copy(vec2);

        int nPeaks = 0;
        for (int i = 0; i < result.length; i++) {
            if (!skip[i]) {
                nPeaks++;
            }
            vec2.setReal(i, sim[i]);
        }

        String datasetName = dataset.getName();
        int dotIndex = datasetName.lastIndexOf(".");
        String simName = dotIndex >= 0 ? datasetName.substring(0, dotIndex)
                + "_sim" + datasetName.substring(dotIndex)
                : datasetName + "_sim";
        vec2.setName(simName);
        String rootName = dotIndex >= 0 ? datasetName.substring(0, dotIndex) + "_sim" : datasetName + "_sim";
        ProjectBase.getActive().removeDataset(simName);
        Dataset simDataset = new Dataset(vec2);
        PeakList peakList = PeakList.get(rootName);
        if (peakList != null) {
            peakList.remove();
        }
        peakList = new PeakList(rootName, 1);
        peakList.setDatasetName(simName);
        SpectralDim sDim = peakList.getSpectralDim(0);
        sDim.setSf(vec.centerFreq);
        sDim.setSw(vec.getSW());
        sDim.setDimName(dataset.getLabel(0));
        int psfSize = psf.length;
        for (int i = 0; i < result.length; i++) {
            if (!skip[i]) {
                Peak peak = peakList.getNewPeak();
                PeakDim peakDim = peak.getPeakDim(0);
                double shift = vec.pointToPPM(i);
                peakDim.setChemShiftValue((float) shift);
                double x1 = vec.pointToPPM(i - widths[0] / 2.0);
                double x2 = vec.pointToPPM(i + widths[0] / 2.0);
                double dx = Math.abs(x2 - x1);
                peakDim.setLineWidthValue((float) dx);
                peakDim.setBoundsValue((float) (dx * 2.0));
                double intensity = result[i] / psfSize;
                peak.setIntensity((float) intensity);
                double volume = intensity * dx * (Math.PI / 2.0) / 1.05;
                peak.setVolume1((float) volume);
            }
        }
        System.out.println("npeaks " + nPeaks);
    }
    public void lr(Dataset dataset, PeakList peakList, double threshold, int iterations) throws IOException {
        Vec vec = dataset.readVector(0, 0);

        double[] signal = new double[vec.getSize()];
        for (int i = 0; i < signal.length; i++) {
            signal[i] = vec.getReal(i);
        }
        boolean[] skip = new boolean[signal.length];

        double[] result = lr(signal, skip, threshold, iterations);
        double[] sim = convolve(result, skip);

        int psfSize = psf.length;
        for (int i = 0; i < result.length; i++) {
            if (!skip[i]) {
                Peak peak = peakList.getNewPeak();
                PeakDim peakDim = peak.getPeakDim(0);
                double shift = vec.pointToPPM(i);
                peakDim.setChemShiftValue((float) shift);
                double x1 = vec.pointToPPM(i - widths[0] / 2.0);
                double x2 = vec.pointToPPM(i + widths[0] / 2.0);
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
