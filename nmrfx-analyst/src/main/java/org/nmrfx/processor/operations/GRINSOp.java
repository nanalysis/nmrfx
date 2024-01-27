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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.GRINS;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static org.nmrfx.processor.operations.IstMatrix.genSrcTargetMap;

/**
 * @author Bruce Johnson
 */
@PythonAPI("pyproc")
public class GRINSOp extends MatrixOperation {
    private static final Logger log = LoggerFactory.getLogger(GRINSOp.class);

    /**
     * Noise level of dataset
     */
    private final double noise;
    private final double scale;
    private final int zfFactor;

    private final int iterations;

    private final double shapeFactor;

    private final boolean apodize;
    private final double[] phase;  // init zero values
    /**
     * Preserve the residual noise
     */
    private final boolean preserve;
    /**
     * Replace all values with synthetic
     */
    private final boolean synthetic;
    /**
     * Sample schedule used for non-uniform sampling. Specifies array elements
     * where data is present.
     *
     * @see SampleSchedule
     */
    private final SampleSchedule sampleSchedule;

    private List<int[]> skipList = null;

    private final boolean extendMode;

    private final File logHome;

    public GRINSOp(double noise, double scale, int zfFactor, int iterations, double shapeFactor, boolean apodize,
                   List<Double> phaseList, boolean preserve, boolean synthetic, boolean extendMode,
                   SampleSchedule schedule, String logHomeName)
            throws ProcessingException {
        this.noise = noise;
        this.scale = scale;
        this.preserve = preserve;
        this.zfFactor = zfFactor;
        this.iterations = iterations;
        this.shapeFactor = shapeFactor;
        this.apodize = apodize;
        this.synthetic = synthetic;
        this.sampleSchedule = schedule;

        if (logHomeName == null) {
            this.logHome = null;
        } else {
            this.logHome = new File(logHomeName);
        }
        if (!phaseList.isEmpty()) {
            this.phase = new double[phaseList.size()];
            for (int i = 0; i < phaseList.size(); i++) {
                this.phase[i] = (Double) phaseList.get(i);
            }
        } else {
            phase = null;
        }
        this.extendMode = extendMode;
    }

    public GRINSOp(double noise, double scale, int zfFactor, int iterations, double shapeFactor, boolean apodize,
                   List<Double> phaseList, boolean preserve, boolean synthetic,
                   SampleSchedule schedule, String logHomeName) {
        this(noise, scale, zfFactor, iterations, shapeFactor, apodize, phaseList, preserve, synthetic, false,
                schedule, logHomeName);

    }

    public GRINSOp(double noise, double scale, int zfFactor, int iterations, double shapeFactor, boolean apodize,
                   List<Double> phaseList, boolean preserve, List<int[]> skipList)
            throws ProcessingException {
        this(noise, scale, zfFactor, iterations, shapeFactor, apodize, phaseList, preserve, false, true,
                null, null);
        this.skipList = skipList;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        if (extendMode) {
            return evalExtend(vector);
        } else {
            return evalNUS(vector);
        }
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        if (extendMode) {
            return evalExtendMatrix(matrix);
        } else {
            return evalNUSMatrix(matrix);
        }
    }


    public Operation evalExtend(Vec vector) throws ProcessingException {
        try {
            int origSize = vector.getSize();
            int zfSize = NESTANMR.getZfSize(origSize, zfFactor);
            vector.resize(zfSize);

            MatrixND matrixND = new MatrixND(vector.getSize() * 2);
            for (int i = 0; i < vector.getSize(); i++) {
                matrixND.setValue(vector.getReal(i), i * 2);
                matrixND.setValue(vector.getImag(i), i * 2 + 1);
            }
            matrixND.zeroFill(zfFactor);

            int[] origSizes = {origSize * 2};
            int[] srcTargetMap = IstMatrix.genZFSrcTargetMap(matrixND, origSizes, false);
            int[] zeroList = IstMatrix.genZFList(matrixND, origSizes, true, skipList);

            GRINS grins = new GRINS(matrixND, noise, scale, iterations, shapeFactor, apodize, phase, preserve, synthetic, zeroList, srcTargetMap, null);
            grins.exec();
            for (int i = 0; i < vector.getSize(); i++) {
                double real = matrixND.getValue(i * 2);
                double imag = matrixND.getValue(i * 2 + 1);
                vector.set(i, real, imag);
            }
        } catch (Exception e) {
            log.error("Error in GRINS extend", e);
            throw new ProcessingException(e.getLocalizedMessage());
        }
        return this;
    }

    public Operation evalNUS(Vec vector) throws ProcessingException {
        try {
            MatrixND matrixND = new MatrixND(vector.getSize() * 2);
            for (int i = 0; i < vector.getSize(); i++) {
                matrixND.setValue(vector.getReal(i), i * 2);
                matrixND.setValue(vector.getImag(i), i * 2 + 1);
            }
            matrixND.zeroFill(zfFactor);
            matrixND.setVSizes(matrixND.getSize(0));
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

            GRINS grins = new GRINS(matrixND, noise, scale, iterations, shapeFactor, apodize, phase, preserve, synthetic, zeroList, srcTargetMap, logFile);
            grins.exec();
           // vector.resize(matrixND.getSize(0) / 2, true);

            for (int i = 0; i < vector.getSize(); i++) {
                double real = matrixND.getValue(i * 2);
                double imag = matrixND.getValue(i * 2 + 1);
                vector.set(i, real, imag);
            }
        } catch (Exception e) {
            log.error("Error in GRINS nus", e);
            throw new ProcessingException(e.getLocalizedMessage());
        }
        return this;
    }

    public Operation evalExtendMatrix(MatrixType matrix) {
        try {
            MatrixND matrixND = (MatrixND) matrix;
            int[] origSizes = new int[((MatrixND) matrix).getNDim()];
            int[] vSizes = new int[((MatrixND) matrix).getNDim()];
            int[] newSizes = new int[((MatrixND) matrix).getNDim()];
            for (int i = 0; i < matrixND.getNDim(); i++) {
                origSizes[i] = matrixND.getSize(i);
                vSizes[i] = matrixND.getVSizes()[i]; //assumes complex
                newSizes[i] = NESTANMR.getZfSize(vSizes[i], zfFactor);
            }
            matrixND.zeroFill(newSizes);
            int[] zeroList = IstMatrix.genZFList(matrixND, vSizes, true, skipList);
            int[] srcTargetMap = IstMatrix.genZFSrcTargetMap(matrixND, vSizes, false);
            String logFile = null;
            if (logHome != null) {
                logFile = logHome.toString() + matrixND.getIndex() + ".log";
            }
            GRINS grins = new GRINS(matrixND, noise, scale, iterations, shapeFactor, apodize, phase, preserve, synthetic, zeroList, srcTargetMap, logFile);
            grins.exec();
            matrixND.setVSizes(newSizes);
        } catch (Exception e) {
            log.error("Error in GRINS extend", e);
            throw new ProcessingException(e.getLocalizedMessage());
        }

        return this;

    }

    public Operation evalNUSMatrix(MatrixType matrix) {
        MatrixND matrixND = (MatrixND) matrix;
        SampleSchedule schedule;
        if (sampleSchedule == null) {
            schedule = matrixND.schedule();
        } else {
            schedule = sampleSchedule;
        }

        if (schedule == null) {
            throw new ProcessingException("No sample schedule");
        }

        try {
            matrixND.zeroFill(zfFactor);
            int[] zeroList = IstMatrix.genZeroList(schedule, matrixND);
            int[] srcTargetMap = genSrcTargetMap(schedule, matrixND);
            String logFile = null;
            if (logHome != null) {
                logFile = logHome.toString() + matrixND.getIndex() + ".log";
            }
            GRINS grins = new GRINS(matrixND, noise, scale, iterations, shapeFactor, apodize, phase, preserve, synthetic, zeroList, srcTargetMap, logFile);
            grins.exec();
        } catch (Exception e) {
            log.error("Error in GRINS extend", e);
            throw new ProcessingException(e.getLocalizedMessage());
        }

        return this;

    }
}
