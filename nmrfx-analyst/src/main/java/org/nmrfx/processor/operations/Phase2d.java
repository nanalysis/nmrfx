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

/**
 * Phase a 2D Matrix.
 *
 * @author bfetler
 */
@PythonAPI("pyproc")
public class Phase2d extends MatrixOperation {

    private final double[] phase;
    private final int dim;

    /**
     * Constructor with phase array.
     *
     * @param ph phase array : f1 0th order, f1 1st order, f2 0th order, f2 1st order
     */
    public Phase2d(double[] ph) {
        int len = ph.length;
        phase = new double[len];
        for (int i = 0; i < len; i++) {
            phase[i] = ph[i];
        }
        dim = -1;
    }

    /**
     * Constructor with four arguments.
     *
     * @param f1p0 f1 zero order phase
     * @param f1p1 f1 first order phase
     * @param dim  The dimension to phase
     */
    public Phase2d(double f1p0, double f1p1, int dim) {
        phase = new double[2];
        phase[0] = f1p0;
        phase[1] = f1p1;
        this.dim = dim;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        if ((matrix instanceof MatrixND) && (dim >= 0)) {
            MatrixND matrixND = (MatrixND) matrix;
            matrixND.doPhaseTD(dim, phase[0], phase[1]);
        } else {
            matrix.phase(phase);
        }
        return this;
    }

}
