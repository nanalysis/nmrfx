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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.operations;

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.NESTAMath;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * @author Bruce Johnson
 */
@PythonAPI("pyproc")
public class NESTANMR extends MatrixOperation {
    private static final Logger log = LoggerFactory.getLogger(NESTANMR.class);

    /**
     * Number of outer iterations (continuations) to iterate over : e.g. 10.
     */
    private final int outerIterations;
    /**
     * Number of inner iterations to iterate over : e.g. 20.
     */
    private final int innerIterations;
    /**
     * Sample schedule used for non-uniform sampling. Specifies array elements
     * where data is present.
     *
     * @see SampleSchedule
     */
    private final double tolFinal;
    private final double muFinal;
    private final double threshold;
    private final boolean zeroAtStart;
    private final boolean extendMode;
    private final int extendFactor;
    /**
     * 2D phase array: [f1ph0, f1ph1, f2ph0, f2ph1].
     */
    private final double[] phase;  // init zero values

    private final SampleSchedule sampleSchedule;

    private final File logHome;

    private List<int[]> skipList = null;

    public NESTANMR(int outerIterations, int innerIterations, double tolFinal, double muFinal, SampleSchedule schedule,
                    List phaseList, boolean zeroAtStart, double threshold,
                    String logHomeName, boolean extendMode, int extendFactor) throws ProcessingException {
        this.outerIterations = outerIterations;
        this.innerIterations = innerIterations;
        this.sampleSchedule = schedule;
        if (!phaseList.isEmpty()) {
            this.phase = new double[phaseList.size()];
            for (int i = 0; i < phaseList.size(); i++) {
                this.phase[i] = (Double) phaseList.get(i);
            }
        } else {
            phase = null;
        }
        if (logHomeName == null) {
            this.logHome = null;
        } else {
            this.logHome = new File(logHomeName);
        }
        this.tolFinal = tolFinal;
        this.muFinal = muFinal;
        this.threshold = threshold;
        this.zeroAtStart = zeroAtStart;
        this.extendMode = extendMode;
        this.extendFactor = extendFactor;
    }

    public NESTANMR(int outerIterations, int innerIterations, double tolFinal, double muFinal, SampleSchedule schedule,
                    List phaseList, boolean zeroAtStart, double threshold,
                    String logHomeName) throws ProcessingException {
        this(outerIterations, innerIterations, tolFinal, muFinal, schedule, phaseList, zeroAtStart, threshold, logHomeName, false, 0);
    }

    public NESTANMR(int outerIterations, int innerIterations, double tolFinal, double muFinal,
                    List phaseList, boolean zeroAtStart, double threshold, int extendFactor, List<int[]> skipList) throws ProcessingException {
        this(outerIterations, innerIterations, tolFinal, muFinal, null, phaseList, zeroAtStart, threshold, null, true, extendFactor);
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

    public Operation evalExtend(Vec vector) throws ProcessingException {
        try {
            int origSize = vector.getSize();
            int zfSize = getZfSize(origSize, extendFactor);

            vector.resize(zfSize);

            MatrixND matrixND = new MatrixND(vector.getSize() * 2);
            for (int i = 0; i < vector.getSize(); i++) {
                matrixND.setValue(vector.getReal(i), i * 2);
                matrixND.setValue(vector.getImag(i), i * 2 + 1);
            }
            int[] origSizes = {origSize * 2};
            int[] zeroList = IstMatrix.genZFList(matrixND, origSizes, true, skipList);

            NESTAMath nesta = new NESTAMath(matrixND, zeroList, outerIterations, innerIterations, tolFinal, muFinal, phase, zeroAtStart, threshold, null);
            nesta.doNESTA();
            for (int i = 0; i < vector.getSize(); i++) {
                double real = matrixND.getValue(i * 2);
                double imag = matrixND.getValue(i * 2 + 1);
                vector.set(i, real, imag);
            }
        } catch (Exception e) {
            log.error("Error in NESTANMR extend", e);
            throw new ProcessingException(e.getLocalizedMessage());
        }
        return this;
    }

    public Operation evalNUS(Vec vector) throws ProcessingException {
        try {
            int origSize = vector.getSize();
            vector.checkPowerOf2();
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
            if (schedule == null) {
                return this;
            }
            int[] zeroList = IstMatrix.genZeroList(schedule, matrixND);

            NESTAMath nesta = new NESTAMath(matrixND, zeroList, outerIterations, innerIterations, tolFinal, muFinal, phase, zeroAtStart, threshold, logFile);
            nesta.doNESTA();
            if (vector.getSize() != origSize) {
                vector.resize(origSize);
            }
            for (int i = 0; i < vector.getSize(); i++) {
                double real = matrixND.getValue(i * 2);
                double imag = matrixND.getValue(i * 2 + 1);
                vector.set(i, real, imag);
            }
        } catch (Exception e) {
            log.error("Error in NESTANMR extend", e);
            throw new ProcessingException(e.getLocalizedMessage());
        }
        return this;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        if (extendMode) {
            return evalExtendMatrix(matrix);
        } else {
            return evalNUSMatrix(matrix);
        }

    }

    public static int getZfSize(double vecSize, int factor) {
        return (int) (Math.pow(2, Math.ceil((Math.log(vecSize) / Math.log(2)) + factor)));
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
                newSizes[i] = getZfSize(vSizes[i], extendFactor);
            }
            matrixND.zeroFill(newSizes);
            int[] zeroList = IstMatrix.genZFList(matrixND, vSizes, true, skipList);
            NESTAMath nesta = new NESTAMath(matrixND, zeroList, outerIterations, innerIterations, tolFinal, muFinal, phase, zeroAtStart, threshold, null);
            nesta.doNESTA();
            matrixND.setVSizes(newSizes);
        } catch (Exception e) {
            log.error("Error in NESTANMR extend", e);
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
            matrixND.ensurePowerOf2();
            for (int i = 0; i < matrixND.getNDim(); i++) {
                matrixND.setVSizes(matrixND.getSizes());
            }
            int[] zeroList = IstMatrix.genZeroList(schedule, matrixND);
            String logFile = null;
            if (logHome != null) {
                logFile = logHome.toString() + matrixND.getIndex() + ".log";
            }

            NESTAMath nesta = new NESTAMath(matrixND, zeroList, outerIterations, innerIterations, tolFinal, muFinal, phase, zeroAtStart, threshold, logFile);
            nesta.doNESTA();
        } catch (Exception e) {
            log.error("Error in NESTANMR extend", e);
            throw new ProcessingException(e.getLocalizedMessage());

        }

        return this;

    }
}
