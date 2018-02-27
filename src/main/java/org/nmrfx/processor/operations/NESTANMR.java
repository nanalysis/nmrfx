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

import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.MatrixType;
import org.nmrfx.processor.math.NESTAMath;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;
import java.io.File;
import java.util.ArrayList;

/**
 *
 * @author Bruce Johnson
 */
public class NESTANMR extends MatrixOperation {

    /**
     * Number of iterations to iterate over : e.g. 300.
     *
     * @see #ist
     */
    private final int iterations;
    /**
     * Sample schedule used for non-uniform sampling. Specifies array elements where data is present.
     *
     * @see #ist
     * @see #zero_samples
     * @see SampleSchedule
     */
    private final double tolFinal;
    private final double muFinal;
    private final boolean apodize;
    private final boolean zeroAtStart;
    /**
     * 2D phase array: [f1ph0, f1ph1, f2ph0, f2ph1].
     */
    private final double[] phase;  // init zero values

    private final SampleSchedule sampleSchedule;

    private final File logHome;

    public NESTANMR(int iterations, double tolFinal, double muFinal, SampleSchedule schedule, ArrayList phaseList, boolean apodize, boolean zeroAtStart, String logHomeName) throws ProcessingException {
        this.iterations = iterations;
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
        this.apodize = apodize;
        this.zeroAtStart = zeroAtStart;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
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
            int[] zeroList = IstMatrix.genZeroList(schedule, matrixND);

            NESTAMath nesta = new NESTAMath(matrixND, zeroList, iterations, tolFinal, muFinal, phase, apodize, zeroAtStart, logFile);
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
            String logFile = null;
            if (logHome != null) {
                logFile = logHome.toString() + matrixND.getIndex() + ".log";
            }

            NESTAMath nesta = new NESTAMath(matrixND, zeroList, iterations, tolFinal, muFinal, phase, apodize, zeroAtStart, logFile);
            nesta.doNESTA();
        } catch (Exception e) {
            throw new ProcessingException(e.getLocalizedMessage());
            
        }

        return this;

    }
}
