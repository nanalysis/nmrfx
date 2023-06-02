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
import org.nmrfx.processor.math.Vec;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Dcfid extends Operation {

    private final double val;

    @Override
    public Dcfid eval(Vec vector) throws OperationException {
        dcfid(vector);
        return this;
    }

    public Dcfid(double val) {
        this.val = val;
    }

    private void dcfid(Vec vector) throws OperationException {
        int i;
        int width;
        double sum_re = 0.0;
        double sum_im = 0.0;
        double mean_re = 0.0;
        double mean_im = 0.0;

        if (!vector.isComplex()) {
            throw new OperationException("dcfid: vector must be complex");
        }

        if ((val < 0.0) || (val > 0.5)) {
            throw new OperationException("dcfid: bad value");
        }

        width = (int) (val * vector.getSize());

        for (i = (vector.getSize() - width); i < vector.getSize(); i++) {
            sum_re += vector.getReal(i);//[i].getReal();
            sum_im += vector.getImag(i);//.getImaginary();
        }

        mean_re = sum_re / width;
        mean_im = sum_im / width;
        int vStart = vector.getStart();
        for (i = vStart; i < vector.getSize(); i++) {
            vector.set(i, vector.getReal(i) - mean_re, vector.getImag(i) - mean_im);
        }
    }
}
