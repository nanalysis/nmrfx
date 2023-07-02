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

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * Numerical derivative using Central Difference Approximation. Update a Vec, setting each point to the derivative.
 * new[0] = Vec[1] - Vec[0] for interior points: new[i] = Vec[i+1] - Vec[i-1] new[size-1] = new[size-1] - new[size-2];
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Dx extends Operation {

    public Dx() {
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();
        if (vector.isComplex()) {
            if (vector.useApache()) {
                Complex dCvec[] = new Complex[vector.getSize()];
                Complex cvec[] = vector.getCvec();

                dCvec[0] = cvec[1].subtract(cvec[0]);
                Complex denominator = new Complex(1.0 / vector.getSize());
                for (int i = 1; i < size - 1; ++i) {
                    dCvec[i] = (cvec[i + 1].subtract(cvec[i - 1])).divide(denominator);
                }
                dCvec[size - 1] = cvec[size - 1].subtract(cvec[size - 2]);

                vector.cvec = dCvec;
            } else {
                double dRvec[] = new double[vector.getSize()];
                double dIvec[] = new double[vector.getSize()];
                double rVec[] = vector.rvec;
                double iVec[] = vector.ivec;

                double denominator = 1.0 / vector.getSize();

                dRvec[0] = rVec[1] - rVec[0];
                dIvec[0] = iVec[1] - iVec[0];

                for (int i = 1; i < size - 1; ++i) {
                    dRvec[i] = (rVec[i + 1] - rVec[i - 1]) / denominator;
                    dIvec[i] = (iVec[i + 1] - iVec[i - 1]) / denominator;
                }
                dRvec[size - 1] = rVec[size - 1] - rVec[size - 2];
                dIvec[size - 1] = iVec[size - 1] - iVec[size - 2];

                vector.rvec = dRvec;
                vector.ivec = dIvec;
            }
        } else {
            double dRvec[] = new double[vector.getSize()];
            double rVec[] = vector.rvec;

            dRvec[0] = rVec[1] - rVec[0];

            double denominator = 1.0 / vector.getSize();

            for (int i = 1; i < size - 1; ++i) {
                dRvec[i] = (rVec[i + 1] - rVec[i - 1]) / denominator;
            }
            dRvec[size - 1] = rVec[size - 1] - rVec[size - 2];

            vector.rvec = dRvec;
        }

        return this;
    }

}
