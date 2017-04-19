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

import org.nmrfx.processor.math.MatrixType;

/**
 * Phase a 2D Matrix.
 *
 * @author bfetler
 */
public class Phase2d extends MatrixOperation {

    private final double[] phase;
    private final boolean f1abs;
    private final boolean f2abs;

    /**
     * Constructor with phase array.
     *
     * @param ph phase array : f1 0th order, f1 1st order, f2 0th order, f2 1st order
     * @param absF1 f1 absolute phase
     * @param absF2 f2 absolute phase
     */
    public Phase2d(double[] ph, Boolean absF1, Boolean absF2) {
        int len = ph.length < 4 ? ph.length : 4;
        phase = new double[4];
        for (int i = 0; i < len; i++) {
            phase[i] = ph[i];
        }
        f1abs = absF1;
        f2abs = absF2;
    }

    /**
     * Constructor with four arguments.
     *
     * @param f1p0 f1 zero order phase
     * @param f1p1 f1 first order phase
     * @param f2p0 f2 zero order phase
     * @param f2p1 f2 first order phase
     * @param absF1 f1 absolute phase
     * @param absF2 f2 absolute phase
     */
    public Phase2d(double f1p0, double f1p1, double f2p0, double f2p1,
            Boolean absF1, Boolean absF2) {
        phase = new double[4];
        phase[0] = f1p0;
        phase[1] = f1p1;
        phase[2] = f2p0;
        phase[3] = f2p0;
        f1abs = absF1;
        f2abs = absF2;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        matrix.phase(phase);
        return this;
    }

}
