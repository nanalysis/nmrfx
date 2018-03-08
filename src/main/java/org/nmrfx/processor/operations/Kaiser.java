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

import net.sourceforge.jdistlib.math.Bessel;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.MatrixType;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 *
 * @author johnsonb
 */
public class Kaiser extends Apodization implements Invertible {

    final double beta;
    final double end;
    final double c;
    final int apodSize;

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
            for (int i = 0; i < vSizes.length; i++) {
                kaiser(matrixND, i, vSizes[i]);
            }
        }
        return this;
    }

    public Kaiser(double beta, double end, double c, int apodSize) {
        this(beta, end, c, apodSize, false);
    }

    public Kaiser(double beta, double end, double c, int apodSize, boolean inverse) {
        this.end = end;
        this.beta = beta;
        this.c = c;
        this.apodSize = apodSize;
        this.invertOp = inverse;
    }

    public void apply(Vec vector) {
        vector.makeApache();
        int apodSize = this.apodSize;
        if (this.apodSize > vector.getSize()) {
            apodSize = vector.getSize();
        }
        if (apodSize == 0) {
            apodSize = vector.getSize();
        }
        double offset = 0.5;
        if (apodVec == null || vector.getSize() != apodVec.length) {
            resize(apodSize);
            int vStart = vector.getStart();
            initApod(vStart);
            double start = offset * Math.PI;

            double delta = ((end - offset)) / (apodSize - vStart - 1);
            for (int i = vStart; i < apodSize; i++) {
                double deltaPos = i - vStart;
                double v1 = beta * Math.sqrt(1.0 - Math.pow(2.0 * deltaPos * delta, 2));
                double v2 = beta;
                apodVec[i] = Bessel.i(v1, 0, false) / Bessel.i(v2, 0, false);
            }
            apodVec[vStart] *= c;
        }
        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }
    }

    private void kaiser(MatrixND matrix, int axis, int apodSize) {
        double offset = 0.5;
        double delta = ((end - offset)) / (apodSize - 1);
        double[] apodVec2 = new double[apodSize];
        for (int i = 0; i < apodSize; i++) {
            double deltaPos = i;
            double v1 = beta * Math.sqrt(1.0 - Math.pow(2.0 * deltaPos * delta, 2));
            double v2 = beta;
            double scale = Bessel.i(v1, 0, false) / Bessel.i(v2, 0, false);
            if (i == 0) {
                scale *= c;
            }
            apodVec2[i] = scale;
        }
        matrix.applyApod(axis, apodVec2);
    }

}

/*
 w = (0.42 - 0.5 * np.cos(2.0 * np.pi * n / (M - 1)) +
         0.08 * np.cos(4.0 * np.pi * n / (M - 1)))
    if not sym and not odd:
        w = w[:-1]
 */
