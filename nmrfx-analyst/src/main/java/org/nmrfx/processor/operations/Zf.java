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
public class Zf extends Operation implements Invertible {

    private final int factor;
    private final int newSize;
    private final int pad;

    @Override
    public Zf eval(Vec vector) throws OperationException {
        if (invertOp) {
            izf(vector);
        } else {
            zf(vector);
        }
        return this;
    }

    public Zf(Integer factor, Integer newSize, Integer pad) throws OperationException {
        this(factor, newSize, pad, false);
    }

    /**
     * Zero Fill. Arguments are optional. If factor is provided, it will zero
     * fill the vector to have a total number of points equal to 'factor' powers
     * of 2. If newSize is provided it will increase the vector to have a total
     * of 'newSize' number of points, all new points being zero in both cases.
     * If pad is provided and greater than 0 size will be increased by this
     * amount from old size.
     *
     * @param newSize
     * @throws OperationException
     */
    public Zf(Integer factor, Integer newSize, Integer pad, boolean inverse) throws OperationException {
        if (factor == null) {
            this.factor = 0;
        } else {
            this.factor = factor;
        }
        if (newSize == null) {
            this.newSize = -1;
        } else {
            this.newSize = newSize;
        }
        if (pad == null) {
            this.pad = -1;
        } else {
            this.pad = pad;
        }
        if (factor != null && factor < 0) {
            throw new OperationException("ZF: factor must be >= 0");
        }

        if (factor != null && factor > 10) {
            throw new OperationException("ZF: factor must be <= 10");
        }
        this.invertOp = inverse;
    }

    private void zf(Vec vector) throws OperationException {
        int size = vector.getSize();

        int newSize = this.newSize;
        if (pad > 0) {
            newSize = size + pad;
        } else if (newSize < 0) {
            newSize = (int) Math.round(Math.pow(2,
                    Math.ceil((Math.log(size) / Math.log(2)) + factor)));
        }

        vector.zf(newSize);
    }

    private void izf(Vec vector) throws OperationException {
        int size = vector.getTDSize();
        vector.resize(size);
    }
}
