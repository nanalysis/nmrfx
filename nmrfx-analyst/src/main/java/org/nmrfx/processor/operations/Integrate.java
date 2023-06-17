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
 * Performs numerical integration on the Vec. First and last points provide the region of the Vec to integrate. If last
 * point is larger than the (size-1) of the vector or less than or equal too the first point, the whole Vec will be
 * integrated by default, and if the first point is less than zero or greater than (size - 1) then it will default to
 * zero.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Integrate extends Operation {

    private final int first;
    private final int last;
    private final double firstIntensity = 0.0;
    private final double lastIntensity = 0.0;

    public Integrate(int first, int last) {
        this.first = Math.max(first, 0);
        this.last = last;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();
        int last = this.last;
        int first = this.first;

        if (this.first >= size) {
            first = 0;
        }

        // if provided last point is larger than Vec, default to size-1
        if (this.last >= size) {
            last = size - 1;
        } // if last point not specified, use size - 1 as the end.
        else if (this.last < 0) {
            last = size - 1;
        }

        if (first > last) {
            first = last;
        }

        if (last > first) {
            double offset = firstIntensity;
            double delta = (lastIntensity - firstIntensity) / (last - first);
            double[] vec = vector.getRvec();
            if (!vector.isComplex()) {
                vec[first] -= offset;
                for (int i = first; i < last; i++) {
                    offset += delta;
                    vec[i + 1] += vec[i] - offset;
                }
            } else if (vector.useApache()) {
                Complex[] cvec = vector.cvec;
                cvec[first] = cvec[first].subtract(offset);
                for (int i = first; i < last; i++) {
                    offset += delta;
                    cvec[i + 1] = cvec[i + 1].add(cvec[i].subtract(offset));
                }
            } else {
                vector.rvec[first] = vector.rvec[first] - offset;
                for (int i = first; i < last; ++i) {
                    offset += delta;
                    vector.rvec[i + 1] = vector.rvec[i + 1] + vector.rvec[i] - offset;
                    vector.ivec[i + 1] = vector.ivec[i + 1] + vector.ivec[i];
                }
            }
        }

        return this;
    }

}
