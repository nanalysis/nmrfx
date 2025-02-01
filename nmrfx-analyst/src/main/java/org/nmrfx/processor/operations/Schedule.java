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
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Schedule extends MatrixOperation {

    private static SampleSchedule schedule = null;
    private final double fraction;
    private int[] zero_samples;
    private final boolean endOnly;
    private final String fileName;

    /**
     * @param fraction The fraction of points to keep.
     */
    public Schedule(double fraction, boolean endOnly, String fileName) {
        if (fraction < 0.05) {
            fraction = 0.05;
        } else if (fraction > 1.0) {
            fraction = 1.0;
        }
        this.fraction = fraction;
        this.endOnly = endOnly;
        this.fileName = fileName;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        if ((fileName != null) && !fileName.equals("")) {
            if (schedule == null) {
                schedule = new SampleSchedule(fileName, false, false);
            }
            if (schedule.reloadFile(fileName, vector.getSize())) {
                zero_samples = null;
            }
        } else {
            int nZeros = (int) Math.round(vector.getSize() * fraction);
            if (schedule == null) {
                schedule = new SampleSchedule(nZeros, vector.getSize(), endOnly);
            }
            if (schedule.recreateArray(nZeros, vector.getSize(), endOnly)) {
                zero_samples = null;
            }
        }
        vector.schedule = schedule;
        zeroVec(vector);
        return this;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        if ((fileName != null) && !fileName.isEmpty()) {
            if (schedule == null) {
                schedule = new SampleSchedule(fileName, false, false);
            }
            MatrixND matrixND = (MatrixND) matrix;
            matrixND.schedule(schedule);
        }
        return this;
    }

    /**
     * Calculate inverse list of SampleSchedule. For 2D only.
     *
     * @param vsize
     * @see SampleSchedule
     * @see SampleSchedule#getSamples
     */
    private void calcZeroes(int vsize) {
        int[][] samples = schedule.getSamples();
        zero_samples = new int[vsize - samples.length];
        int i, j, k, start = 0;
        for (i = 0, k = 0; i < vsize; i++) {
            boolean found = false;
            for (j = 0; j < samples.length; j++) {
                if (i == samples[j][0]) {  // 2D index only
                    found = true;
                    start++;
                    break;
                }
            }
            if (!found) {
                zero_samples[k++] = i;
            }
        }
    }

    /**
     * Zero (or rezero) a vector with inverse samples.
     *
     * @see Vec
     * @see #calcZeroes
     */
    private void zeroVec(Vec vector) {
        if (zero_samples == null) {
            calcZeroes(vector.getSize());
        }
        for (int k = 0; k < zero_samples.length; k++) {
            vector.setComplex(zero_samples[k], Complex.ZERO);
        }
    }

}
