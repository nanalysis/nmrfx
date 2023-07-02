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
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Dc extends Operation {

    private final double frac;

    public Dc() {
        this(0.05);
    }

    public Dc(double frac) {
        this.frac = frac;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int i;
        int width;
        double sum = 0.0;
        double mean = 0.0;

        if (vector.isComplex()) {
            vector.makeReal();
        }

        int size = vector.getSize();

        width = (int) (frac * size);

        if ((width < 1) || (width > (size / 3))) {
            throw new OperationException(
                    "Dc: range value must be > 0.0 and <= 0.33");
        }

        for (i = 0; i < width; i++) {
            sum += vector.getReal(i);
        }

        for (i = (size - width); i < size; i++) {
            sum += vector.getReal(i);
        }

        mean = sum / (2 * width);

        for (i = 0; i < size; i++) {
            vector.set(i, vector.getReal(i) - mean);
        }

        return this;
    }

}
