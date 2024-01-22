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
import org.nmrfx.math.Bessel;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Kaiser extends Apodization implements Invertible {

    final double offset;
    final double beta;
    final double end;
    final double c;
    final int apodSize;
    final int dim;

    @Override
    public Kaiser eval(Vec vector) throws ProcessingException {
        apply(vector);
        return this;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        if (matrix instanceof MatrixND) {
            MatrixND matrixND = (MatrixND) matrix;
            int[] vSizes = matrixND.getVSizes();
            if (dim == 0) {
                for (int dim =0;dim < matrixND.getNDim();dim++) {
                    apply(matrixND, dim, vSizes[dim]);
                }
            } else {
                apply(matrixND, dim, vSizes[dim]);
            }
        }
        return this;
    }

    public Kaiser(double offset, double beta, double end, double c, int apodSize, int dim) {
        this(offset, beta, end, c, apodSize, dim, false);
    }

    public Kaiser(double offset, double beta, double end, double c, int apodSize, int dim, boolean inverse) {
        this.offset = offset;
        this.end = end;
        this.beta = beta;
        this.c = c;
        this.apodSize = apodSize;
        this.dim = dim;
        this.invertOp = inverse;
    }

    public void setupApod(int dataSize, int vStart) {
        int apodSize2 = this.apodSize;
        if (apodSize2 > dataSize) {
            apodSize2 = dataSize;
        }
        if (apodSize2 == 0) {
            apodSize2 = dataSize;
        }
        if (apodVec == null || apodSize2 != apodVec.length) {
            resize(apodSize2);
            initApod(vStart);
            for (int i = vStart; i < apodSize2; i++) {
                double deltaPos = i - vStart;
                double tt = (-1.0 + offset * 2.0) + end * 2.0 * (1.0 - offset) * deltaPos / (apodSize2 - 1.0);
                double v1 = beta * Math.sqrt(1.0 - Math.pow(tt, 2));
                double v2 = beta;
                apodVec[i] = Bessel.i0(v1) / Bessel.i0(v2);

            }
            apodVec[vStart] *= c;
        }
    }

    public void apply(Vec vector) {
        vector.makeApache();
        setupApod(vector.getSize(), vector.getStart());
        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }
    }

    private void apply(MatrixND matrix, int axis, int mApodSize) {
        setupApod(mApodSize, 0);
        matrix.applyApod(axis, apodVec);
    }
}

/*
 w = (0.42 - 0.5 * np.cos(2.0 * np.pi * n / (M - 1)) +
         0.08 * np.cos(4.0 * np.pi * n / (M - 1)))
    if not sym and not odd:
        w = w[:-1]
 */
