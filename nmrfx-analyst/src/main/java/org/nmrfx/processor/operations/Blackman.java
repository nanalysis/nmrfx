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
public class Blackman extends Apodization implements Invertible {

    final double offset;
    final double end;
    final double c;
    final int apodSize;
    final int dim;

    @Override
    public Blackman eval(Vec vector) throws ProcessingException {
        apply(vector);
        return this;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        if (matrix instanceof MatrixND) {
            MatrixND matrixND = (MatrixND) matrix;
            int[] vSizes = matrixND.getVSizes();
            apply(matrixND, dim, vSizes[dim]);
        }
        return this;
    }

    public Blackman(double offset, double end, double c, int apodSize, int dim) {
        this(offset, end, c, apodSize, dim, false);
    }

    public Blackman(double offset, double end, double c, int apodSize, int dim, boolean inverse) {
        this.offset = offset;
        this.end = end;
        this.c = c;
        this.apodSize = apodSize;
        this.invertOp = inverse;
        this.dim = dim;
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
            double start = offset * Math.PI;

            double delta = ((end - offset) * Math.PI) / (apodSize2 - vStart - 1);

            for (int i = vStart; i < apodSize2; i++) {
                double deltaPos = i - vStart;
                apodVec[i] = (0.42 - 0.5 * Math.cos(2.0 * start + 2.0 * (deltaPos * delta)) + 0.08 * Math.cos(4.0 * start + 4.0 * (deltaPos * delta)));

            }
            apodVec[vStart] *= c;
        }
    }

    public void apply(Vec vector) {
        vector.makeApache();
        setupApod(vector.getTDSize(), vector.getStart());
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
    if M < 1:
        return np.array([])
    if M == 1:
        return np.ones(1, 'd')
    odd = M % 2
    if not sym and not odd:
        M = M + 1
    n = np.arange(0, M)
    w = (0.42 - 0.5 * np.cos(2.0 * np.pi * n / (M - 1)) + 0.08 * np.cos(4.0 * np.pi * n / (M - 1)))
    if not sym and not odd:
        w = w[:-1]
    return w


 */
 /*
 w = (0.42 - 0.5 * np.cos(2.0 * np.pi * n / (M - 1)) +
         0.08 * np.cos(4.0 * np.pi * n / (M - 1)))
    if not sym and not odd:
        w = w[:-1]
 */
