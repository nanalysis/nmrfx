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

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.processor.processing.ProcessingException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class NESTAMath {

    final int outerIterations;
    final int innerIterations;
    final int statWinSize = 10;
    final boolean zeroAtStart;
    final double threshold;
    double tolFinal;
    double muFinal;
    final MatrixND matrix;
    final int[] zeroList;
    final double[] phase;

    int totalIterations = 0;
    double initialL1Norm = 0.0;
    double finalL1Norm = 0.0;
    FileWriter fileWriter = null;

    public NESTAMath(MatrixND matrix, int[] zeroList, int iterations, int innerIterations, double tolFinal, double muFinal, double[] phase, boolean zeroAtStart, double threshold, String logFileName) {
        this.matrix = matrix;
        this.zeroList = zeroList;
        this.outerIterations = iterations;
        this.innerIterations = innerIterations;
        this.tolFinal = tolFinal;
        this.muFinal = muFinal;
        this.phase = phase;
        this.zeroAtStart = zeroAtStart;
        this.threshold = threshold;
        if (logFileName != null) {
            try {
                fileWriter = new FileWriter(logFileName);
            } catch (IOException ioE) {
                throw new ProcessingException(ioE.getMessage());
            }
        }
    }

    public void doNESTA() throws IOException {
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
        if (zeroAtStart) {
            matrix.zeroValues(zeroList);
        }
        MatrixND gradMatrix = new MatrixND(matrix);

        gradMatrix.copyFrom(matrix);
        gradMatrix.doFTtoReal();
        SummaryStatistics sStats = gradMatrix.calcRealStats();
        initialL1Norm = sStats.getSum();
        double maxAbs = sStats.getMax();
        if (maxAbs < threshold) {
            return;
        }

        int n = matrix.getNElems();
        double[] zValues = new double[n];  // zk of page 5
        double[] yValues = new double[n];  // yk of page 5
        double[] wValues = new double[n];  // cumulative gradient
        double[] xPlug = new double[n];    // matrix values at beginning of inner loop

        double muStart = maxAbs * 0.9;  // based on page 11 of NESTA paper
        // fixme  good value for muFinal?/ should this be an argument?
        // or should it be set automatically from data (fraction of largest value accounting for dynamic range)
        //   or based on noise

        // Inverse of initial L1Norm recommended in NESTA-NMR Suppl. for tolFinal
        // but it says they adjust threshold for 4D??
        // NESTA paper uses set values like 1.0e-5 - 1.0e-8  page 10, near eq. 3.13
        double tolStart = 0.1;
        if (tolFinal < 0.0) {
            tolFinal = 1.0 / initialL1Norm;
        }
        if (muStart < muFinal) {
            muStart = muFinal;
        }
        if (tolStart < tolFinal) {
            tolStart = tolFinal;
        }

        double muMult = FastMath.pow(muFinal / muStart, 1.0 / outerIterations);
        double tolMult = FastMath.pow(tolFinal / tolStart, 1.0 / outerIterations);
        DescriptiveStatistics dStats = new DescriptiveStatistics(statWinSize);

        totalIterations = 0;
        double mu = muStart;
        double tol = tolStart;
        matrix.copyDataTo(xPlug);

        // Outer iterations are the so-called Continuation Steps of the NESTA paper
        for (int oIter = 0; oIter < outerIterations; oIter++) {
            mu *= muMult;
            tol *= tolMult;
            Arrays.fill(wValues, 0.0);
            dStats.clear();
            if (fileWriter != null) {
                fileWriter.write(String.format("iter %5d mu %7.3f tol %9.7f\n", oIter, mu, tol));
            }

            for (int iIter = 0; iIter < innerIterations; iIter++) {
                totalIterations++;
                gradMatrix.copyFrom(matrix);
                gradMatrix.doFTtoReal();

                // fixme think about the fact that mu used in this method (multiplying gradient) is time domain data
                // whereas in calcRealL1AndGradient its used on frequency domain data
                // should it be scaled in some way?
                double l1Norm = gradMatrix.calcRealL1AndGradient(mu);
                // Convergence criterion Section 3.5 of NESTAMath Paper
                // changed to require at least statWinSize innerIterations
                finalL1Norm = l1Norm;
                int minIter = 3;
                if (iIter >= minIter) {
                    double mean = dStats.getMean();
                    double delta = FastMath.abs(l1Norm - mean) / mean;
                    if (fileWriter != null) {
                        fileWriter.write(String.format("tIter %4d iIter %4d mean %9.5f delta %9.7f l1 %9.5f\n", totalIterations, iIter, mean, delta, l1Norm));
                    }
                    if ((iIter > minIter) && (delta < tol)) {
                        break;
                    }
                }

                dStats.addValue(l1Norm);
                gradMatrix.doHIFT(0.5);

                // values for alpha and tau shown on bottom of page 5 of NESTA paper
                double alpha = 0.5 * (iIter + 1.0);
                double tau = 2.0 / (iIter + 3.0);

                // Note:  equations for updating y and z are simpler than equations 3.6/3.11 in
                // NESTA paper because we are not constraining fit to agree with measured values
                // Instead we only update non-measured values and keep measured values fixed
                // Effectively then the Langrangian multiplier (lambda) is 0.0 and all we need is the value for q
                // fixme could just use zeroList here
                for (int i = 0; i < n; i++) {
                    double mValue = matrix.getValueAtIndex(i);
                    double gValue = gradMatrix.getValueAtIndex(i);
                    yValues[i] = mValue - mu * gValue; // compare to q of eq 3.7
                    wValues[i] += alpha * gValue;
                    zValues[i] = xPlug[i] - mu * wValues[i];  // compare to q of eq 3.12
                }
                for (int i : zeroList) {
                    matrix.setValueAtIndex(i, tau * zValues[i] + (1.0 - tau) * yValues[i]);
                }
            }
            matrix.copyDataTo(xPlug);
        }
        if (fileWriter != null) {
            fileWriter.write(String.format("iNorm %10.5f fNorm %10.5f nIter %d\n", initialL1Norm, finalL1Norm, totalIterations));
            fileWriter.close();
        }
    }
}
