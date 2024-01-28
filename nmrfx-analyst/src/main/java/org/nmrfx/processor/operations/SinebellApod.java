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
public class SinebellApod extends Apodization implements Invertible {

    final double offset;
    final double end;
    final double power;
    final double c;
    final int apodSize;

    final int dim;

    @Override
    public SinebellApod eval(Vec vector) throws ProcessingException {
        sb(vector);
        return this;
    }

    public SinebellApod(double offset, double end, double power, double c, int apodSize, int dim) {
        this(offset, end, power, c, apodSize, dim, false);
    }

    public SinebellApod(double offset, double end, double power, double c, int apodSize, int dim, boolean inverse) {
        this.offset = offset;
        this.end = end;
        this.power = power;
        this.c = c;
        this.apodSize = apodSize;
        this.dim = dim;
        this.invertOp = inverse;
    }

    private void setupApod(int size, int vStart) {
        int localApodSize = Math.min(this.apodSize, size);
        if (localApodSize == 0) {
            localApodSize = size;
        }

        if (apodVec == null || size != apodVec.length) {
            resize(localApodSize);
            initApod(vStart);

            double start = offset * Math.PI;
            double delta = ((end - offset) * Math.PI) / (localApodSize - vStart - 1);

            if (power != 1.0) {
                for (int i = vStart; i < localApodSize; i++) {
                    double deltaPos = i - vStart;
                    apodVec[i] = Math.pow(Math.sin(start + (deltaPos * delta)), power);
                }
            } else {
                for (int i = vStart; i < localApodSize; i++) {
                    double deltaPos = i - vStart;
                    apodVec[i] = Math.sin(start + (deltaPos * delta));
                }
            }

            apodVec[vStart] *= c;
        }
    }
    public void sb(Vec vector) {
        vector.makeApache();
        setupApod(vector.getSize(), vector.getStart());
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
            if (dim == -1) {
                for (int iDim = 0; iDim < matrixND.getNDim(); iDim++) {
                    apply(matrixND, iDim, vSizes[iDim] / 2);
                }
            } else {
                apply(matrixND, dim, vSizes[dim] / 2);
            }
        }
        return this;
    }
    private void apply(MatrixND matrix, int axis, int mApodSize) {
        setupApod(mApodSize, 0);
        matrix.applyApod(axis, apodVec);
    }

}
