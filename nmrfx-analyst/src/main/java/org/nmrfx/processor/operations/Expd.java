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
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Expd extends Apodization implements Invertible {

    private final double lb;
    private final double fPoint;

    @Override
    public Expd eval(Vec vector) throws ProcessingException {
        expd(vector);
        return this;
    }

    public Expd(double lb, double fPoint, boolean inverse) {
        this.lb = lb;
        this.fPoint = fPoint;
        this.invertOp = inverse;
    }

    private void setupApod(int size, int vStart, double dwellTime) {
        if (apodVec == null || apodVec.length != size) {
            resize(size);

            double decay = Math.PI * lb;
            initApod(vStart);
            for (int i = vStart; i < size; i++) {
                double x = (i - vStart) * decay * dwellTime;
                apodVec[i] = Math.exp(-x);
            }
            apodVec[vStart] *= fPoint;

        }
    }
        /**
         * Exponential Decay.
         *
         * @param vector
         * @throws ProcessingException
         */
    private void expd(Vec vector) throws ProcessingException {
        setupApod(vector.getSize(), vector.getStart(), vector.dwellTime);

        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        if (matrix instanceof MatrixND matrixND) {
            int[] vSizes = matrixND.getVSizes();
            int dim = -1;
            if (dim == -1) {
                for (int iDim = 0; iDim < matrixND.getNDim(); iDim++) {
                    apply(matrixND, iDim, vSizes[iDim] / 2, matrixND.getDwellTime(iDim));
                }
            } else {
                apply(matrixND, dim, vSizes[dim] / 2, matrixND.getDwellTime(dim));
            }
        }
        return this;
    }
    private void apply(MatrixND matrix, int axis, int mApodSize, double dwellTime) {
        setupApod(mApodSize, 0, dwellTime);
        matrix.applyApod(axis, apodVec);
    }
}
