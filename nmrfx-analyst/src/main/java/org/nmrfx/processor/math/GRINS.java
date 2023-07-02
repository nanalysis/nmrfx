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
package org.nmrfx.processor.math;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.processor.processing.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author Bruce Johnson
 */
public class GRINS {
    private static final Logger log = LoggerFactory.getLogger(GRINS.class);

    private static final double THRESHOLD_SCALE = 0.8;
    private static final double NOISE_SCALE = 5.0;

    final MatrixND matrix;
    final double noise;
    final boolean preserve;
    final boolean synthetic;
    final int[] zeroList;
    final int[] srcTargetMap;
    final double scale;
    final double[] phase;
    final String logFileName;

    boolean calcLorentz = true;
    boolean calcGauss = false;
    double fracLorentz = 1.0;
    /**
     * Calculate statistics.
     */
    boolean calcStats = true;
    boolean tdMode = false;  // only freq mode is currently functional

    public GRINS(MatrixND matrix, double noise, double scale, double[] phase, boolean preserve, boolean synthetic, int[] zeroList, int[] srcTargetMap, String logFileName) {
        this.matrix = matrix;
        this.noise = noise;
        this.scale = scale;
        this.phase = phase;
        this.preserve = preserve;
        this.synthetic = synthetic;
        this.zeroList = zeroList;
        this.srcTargetMap = srcTargetMap;
        this.logFileName = logFileName;
    }

    public void exec() {
        try (FileWriter fileWriter = logFileName == null ? null : new FileWriter(logFileName)) {
            int iterations = 16;
            matrix.zeroValues(zeroList);
            double preValue = 0.0;
            double postValue = 0.0;
            double deltaToOrig = 0.0;
            boolean calcNoise = noise < 1.0e-6;
            double noiseValue = noise;
            boolean doPhase = false;
            if (phase != null) {
                for (double phaseVal : phase) {
                    if (Math.abs(phaseVal) > 1.0e-6) {
                        doPhase = true;
                        break;
                    }
                }
            }
            if (doPhase) {
                matrix.phase(phase);
            }

            // could just copy the actually sample values to vector
            MatrixND matrixCopy = new MatrixND(matrix);
            double[] addBuffer = new double[matrix.getNElems()];
            int nPeaks = 0;
            int iteration;
            double lastThreshold = Double.MAX_VALUE;
            for (iteration = 0; iteration < iterations; iteration++) {
                matrix.doFTtoReal();
                if (iteration == 0) {

                    if (calcStats) {
                        preValue = matrix.calcSumAbs();
                    }
                }
                if (calcNoise) {
                    double[] measure = matrix.measure(false, 0.0, Double.MAX_VALUE);
                    measure = matrix.measure(false, measure[2], measure[3]);
                    measure = matrix.measure(false, measure[2], measure[3]);
                    measure = matrix.measure(false, measure[2], measure[3]);
                    measure = matrix.measure(false, measure[2], measure[3]);
                    measure = matrix.measure(false, measure[2], measure[3]);
                    noiseValue = measure[3];
                }

                double[] measure = matrix.measure(false, 0.0, Double.MAX_VALUE);
                double max = Math.max(FastMath.abs(measure[0]), FastMath.abs(measure[1]));
                // fixme threshold based on abs value
                double globalThreshold = max * THRESHOLD_SCALE;
                if (globalThreshold > lastThreshold * THRESHOLD_SCALE) {
                    globalThreshold = lastThreshold * THRESHOLD_SCALE;
                }
                lastThreshold = globalThreshold;
                double noiseThreshold = noiseValue * NOISE_SCALE;
                if (globalThreshold < noiseThreshold) {
                    break;
                }
                ArrayList<MatrixPeak> peaks = matrix.peakPick(globalThreshold, noiseThreshold, true, false, scale);
                Collections.sort(peaks, (a, b) -> Double.compare(Math.abs(b.height), Math.abs(a.height)));
                if (peaks.size() > 1) {
                    peaks = filterPeaks(peaks);
                }
                int nPeaksTemp = peaks.size();
                nPeaks += peaks.size();
                if (!tdMode && !peaks.isEmpty()) {
                    subtractSignals(matrix, peaks, addBuffer, fileWriter);
                }
                double[] measure2 = matrix.measure(false, 0.0, Double.MAX_VALUE);
                double max2 = Math.max(FastMath.abs(measure2[0]), FastMath.abs(measure2[1]));

                if (fileWriter != null) {
                    String outLine = String.format("%4d %4d %10.3f %10.3f %10.3f %10.3f\n", iteration, nPeaksTemp, globalThreshold, noiseThreshold, max, max2);
                    fileWriter.write(outLine);
                    for (MatrixPeak peak : peaks) {
                        fileWriter.write(peak.toString() + '\n');
                    }
                }
                if (iteration < iterations - 1) {
                    matrix.doHIFT(0.5);
                    matrix.zeroValues(zeroList);
                }
                if (tdMode && !peaks.isEmpty()) {
                    doPeaks(peaks, matrix);
                }
            }
            if (preserve) {
                matrix.addDataFrom(addBuffer);
            } else {
                matrix.copyDataFrom(addBuffer);
            }
            if (calcStats) {
                postValue = matrix.calcSumAbs();
            }
            matrix.doHIFT(1.0);
            if (calcStats) {
                deltaToOrig = matrix.calcDifference(matrixCopy, srcTargetMap);
            }
            if (fileWriter != null) {
                String outLine = String.format("%4d %4d %10.3f %10.3f %10.3f\n", (iteration + 1), nPeaks, preValue, postValue, deltaToOrig);
                fileWriter.write(outLine);
            }
            if (!synthetic) {
                matrix.copyValuesFrom(matrixCopy, srcTargetMap);
            }

        } catch (IOException ioE) {
            throw new ProcessingException(ioE.getMessage());
        }

    }

    ArrayList<MatrixPeak> filterPeaks(ArrayList<MatrixPeak> peaks) {
        int nPeaks = peaks.size();
        ArrayList<MatrixPeak> keepPeaks = new ArrayList<>();
        for (MatrixPeak iPeak : peaks) {
            boolean ok = true;
            for (MatrixPeak jPeak : keepPeaks) {
                if (iPeak.overlap(jPeak)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                keepPeaks.add(iPeak);
            }

        }
        return keepPeaks;
    }

    void doPeaks(ArrayList<MatrixPeak> peaks, MatrixND matrix) {
        int nDim = matrix.nDim;
        double[][] vecs = new double[nDim][];
        for (int i = 0; i < nDim; i++) {
            vecs[i] = new double[matrix.getSize(i)];
        }
        for (MatrixPeak peak : peaks) {
            for (int i = 0; i < nDim; i++) {
                int size = vecs[i].length;
                double freq = (peak.centers[i + 1] - size / 2) / size;
                double lw = peak.widths[i + 1];
                double decay = Math.exp(-Math.PI * lw);
                double amp = 1.0;
                double phase = 0.0;
                genSignal(vecs[i], freq, decay, amp, phase);
            }
        }
    }

    /**
     * Generate damped sinusoidal signal, and add to Vec instance.
     *
     * @param vec   array of double in which to put signal with real and imaginary
     *              in alternate positions
     * @param freq  frequency in degrees per point
     * @param decay exponential decay per point
     * @param amp   amplitude
     * @param ph    phase in degrees
     */
    public void genSignal(double[] vec, double freq, double decay, double amp, double ph) {
        Complex w = ComplexUtils.polar2Complex(decay, freq * Math.PI);
        Complex tempC = new Complex(amp * Math.cos(ph * Math.PI / 180.0), amp * Math.sin(ph * Math.PI / 180.0));
        int size = vec.length / 2;
        for (int i = 0; i < size; i++) {
            vec[2 * i] = tempC.getReal();
            vec[2 * i + 1] = tempC.getImaginary();
            tempC = tempC.multiply(w);
        }
    }

    double[][] getPositions(MatrixPeak peak) {
        double[] freqs = peak.centers;
        int nDim = freqs.length;
        double[][] positions = new double[nDim][2];
        for (int iDim = 0; iDim < nDim; iDim++) {

        }
        return positions;

    }

    public void subtractSignals(MatrixND matrix, ArrayList<MatrixPeak> peaks, double[] buffer, FileWriter fileWriter) {
        int nDim = matrix.getNDim();
        double[] positions = new double[nDim];
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(matrix.getSizes());
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        double maxInt = 0.0;
        double ySub = 0.0;
        int maxIndex = 0;
        for (int index = 0; iterator.hasNext(); index++) {
            iterator.next();
            int[] counts = iterator.getCounts();
            for (int i = 0; i < nDim; i++) {
                positions[i] = counts[i];
            }
            double y = 0.0;
            for (MatrixPeak peak : peaks) {
                double[] freqs = peak.centers;
                double[] widths = peak.widths;
                y += calculateOneSig(positions, peak.height, freqs, widths);
            }
            double value = matrix.getValueAtIndex(index);
            if (Math.abs(value) > Math.abs(maxInt)) {
                maxInt = value;
                ySub = y;
                maxIndex = index;
            }
            double newValue = value - y;
            buffer[index] += y;
            matrix.setValueAtIndex(index, newValue);
        }
        if (fileWriter != null) {
            int nElems = matrix.getNElems();
            double afterMax = 0.0;
            double afterMin = 0.0;
            int afterMaxIndex = 0;
            int afterMinIndex = 0;
            for (int i = 0; i < nElems; i++) {
                double value = matrix.getValueAtIndex(i);
                if (value > afterMax) {
                    afterMax = value;
                    afterMaxIndex = i;
                }
                if (value < afterMin) {
                    afterMin = value;
                    afterMinIndex = i;
                }
            }
            String outLine = String.format("maxInt %10.3f ySub %10.3f %5d %10.3f %5d %10.3f %5d\n", maxInt, ySub, maxIndex, afterMin, afterMinIndex, afterMax, afterMaxIndex);
            try {
                fileWriter.write(outLine);
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }

    }

    public double calculateOneSig(double[] positions, double amplitude, double[] freqs, double[] widths) {
        double y = 1.0;
        int nDim = freqs.length - 1;
        for (int iDim = 0; iDim < nDim; iDim++) {
            int jDim = iDim + 1;
            double lw = widths[jDim];
            double freq = freqs[jDim];
            y *= lShape(positions[iDim], lw, freq);
        }
        y *= amplitude;
        return y;
    }

    public double lShape(double x, double b, double freq) {
        double yL = 0.0;
        double yG = 0.0;
        if (calcLorentz) {
            b *= 0.5;
            yL = fracLorentz * ((b * b) / ((b * b) + ((x - freq) * (x - freq))));
        }
        if (calcGauss) {
            double dX = (x - freq);
            yG = (1.0 - fracLorentz) * Math.exp(-dX * dX / b);
        }

        return yL + yG;
    }
}
