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

/**
 *
 * @author johnsonb
 */
public class Extract extends Operation {

    private final int istart;
    private final int iend;
    private final double dstart;
    private final double dend;

    public Extract(int start, int end) {
        this.istart = start;
        this.iend = end;
        this.dstart = -1;
        this.dend = -1;
    }

    public Extract(double start, double end) {
        this.dstart = start;
        this.dend = end;
        this.istart = -1;
        this.iend = -1;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int start;
        int end;
        int size = vector.getSize();
        if (dstart >= 0.0) {
            start = (int) (dstart * size);
        } else {
            start = istart;
        }
        if (dend >= 0.0) {
            end = (int) (dend * size - 1);
            if (end >= size) {
                end = size - 1;
            }
        } else {
            end = iend;
        }

        if ((start < 0) || (start > (size - 1))) {
            throw new OperationException(
                    "Extract: start value must be > 0 and < " + (size - 1));
        }
        if ((end > (size - 1)) || (end <= start)) {
            throw new OperationException(
                    "Extract: end value must be > " + start + " and < " + (size - 1));
        }
        int newSize = end - start + 1;
        vector.trim(start, newSize);
        int[][] pt = vector.getPt();
        pt[0][1] = newSize - 1;
        vector.setPt(pt, vector.getDim());

        return this;
    }
}
