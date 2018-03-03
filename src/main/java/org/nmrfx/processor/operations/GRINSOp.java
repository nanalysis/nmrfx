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

import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.MatrixType;
import org.nmrfx.processor.math.GRINS;
import org.nmrfx.processor.math.Vec;
import static org.nmrfx.processor.operations.IstMatrix.genSrcTargetMap;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;
import java.io.File;

/**
 *
 * @author Bruce Johnson
 */
public class GRINSOp extends MatrixOperation {

    /**
     * Noise level of dataset
     *
     */
    private final double noise;
    private final double scale;
    /**
     * Preserve the residual noise
     *
     */
    private final boolean preserve;
    /**
     * Replace all values with synthetic
     *
     */
    private final boolean synthetic;
    /**
     * Sample schedule used for non-uniform sampling. Specifies array elements where data is present.
     *
     * @see #ist
     * @see #zero_samples
     * @see SampleSchedule
     */
    private final SampleSchedule sampleSchedule;

    private final File logHome;

    public GRINSOp(double noise, double scale, boolean preserve, boolean synthetic, SampleSchedule schedule, String logHomeName) throws ProcessingException {
        this.noise = noise;
        this.scale = scale;
        this.preserve = preserve;
        this.synthetic = synthetic;
        this.sampleSchedule = schedule;
        if (logHomeName == null) {
            this.logHome = null;
        } else {
            this.logHome = new File(logHomeName);
        }
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        try {
            MatrixND matrixND = new MatrixND(vector.getSize() * 2);
            for (int i = 0; i < vector.getSize(); i++) {
                matrixND.setValue(vector.getReal(i), i * 2);
                matrixND.setValue(vector.getImag(i), i * 2 + 1);
            }
            SampleSchedule schedule;
            String logFile = null;
            if (sampleSchedule == null) {
                schedule = vector.schedule;
            } else {
                schedule = sampleSchedule;
                if (logHome != null) {
                    logFile = logHome.toString() + vector.getIndex() + ".log";
                }

            }
            int[] zeroList = IstMatrix.genZeroList(schedule, matrixND);
            int[] srcTargetMap = genSrcTargetMap(schedule, matrixND);

            GRINS smile = new GRINS(matrixND, noise, scale, preserve, synthetic, zeroList, srcTargetMap, logFile);
            smile.exec();
            for (int i = 0; i < vector.getSize(); i++) {
                double real = matrixND.getValue(i * 2);
                double imag = matrixND.getValue(i * 2 + 1);
                vector.set(i, real, imag);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ProcessingException(e.getLocalizedMessage());
        }
        //PyObject obj = interpreter.get("a");
        return this;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        try {
            MatrixND matrixND = (MatrixND) matrix;
            int[] zeroList = IstMatrix.genZeroList(sampleSchedule, matrixND);
            int[] srcTargetMap = genSrcTargetMap(sampleSchedule, matrixND);
            String logFile = null;
            if (logHome != null) {
                logFile = logHome.toString() + matrixND.getIndex() + ".log";
            }
//            if (matrixND.getIndex() == 381) {
            GRINS smile = new GRINS(matrixND, noise, scale, preserve, synthetic, zeroList, srcTargetMap, logFile);
            smile.exec();
//            }
        } catch (Exception e) {
            throw new ProcessingException(e.getLocalizedMessage());
        }

        return this;

    }
}
