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

import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import java.util.ArrayList;

/**
 *
 * @author johnsonb
 */
public class Bcwhit extends Operation {

    private final double lambda;
    private final int order;
    private final boolean baselineMode;

    public Bcwhit(double lambda, int order, boolean baselineMode) {
        this.lambda = lambda;
        this.order = order;
        this.baselineMode = baselineMode;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int vecSize = vector.getSize();

        boolean[] isInSignalRegion = vector.getSignalRegion();

        if ((isInSignalRegion != null) && (isInSignalRegion.length > 4)) {
            double[] w = new double[vecSize + 1];
            double[] z = new double[vecSize + 1];
            double[] y = new double[vecSize + 1];
            if (vector.isComplex()) {
                vector.makeReal();
            }
            for (int i = 0; i < vecSize; i++) {
                y[i + 1] = vector.rvec[i];
                if (isInSignalRegion[i]) {
                    w[i + 1] = 0;
                } else {
                    w[i + 1] = 1;
                }
            }
            double[] a = new double[order + 1];

            Util.pascalrow(a, order);

            //is this fine?
            Util.asmooth(w, y, z, a, lambda, vecSize, order);
            if (baselineMode) {
                for (int i = 0; i < vecSize; i++) {
                    vector.rvec[i] = z[i + 1];
                }
            } else {
                for (int i = 0; i < vecSize; i++) {
                    vector.rvec[i] -= z[i + 1];
                }
            }
        }
        return this;
    }
}
