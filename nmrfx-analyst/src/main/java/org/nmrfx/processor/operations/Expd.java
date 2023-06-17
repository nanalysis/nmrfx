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
public class Expd extends Apodization implements Invertible {

    private final double lb;
    private final double fPoint;

    @Override
    public Expd eval(Vec vector) throws ProcessingException {
        expd(vector);
        return this;
    }

    public Expd(double lb, double fPoint, boolean inverse) {
        this.lb = lb;
        this.fPoint = fPoint;
        this.invertOp = inverse;
    }

    /**
     * Exponential Decay.
     *
     * @param vector
     * @throws ProcessingException
     */
    private void expd(Vec vector) throws ProcessingException {
        if (apodVec == null || apodVec.length != vector.getSize()) {
            resize(vector.getSize());

            double decay = Math.PI * lb;
            int vStart = vector.getStart();
            initApod(vStart);
            for (int i = vStart; i < vector.getSize(); i++) {
                double x = (i - vStart) * decay * vector.dwellTime;
                apodVec[i] = Math.exp(-x);
            }

            apodVec[vStart] *= fPoint;

        }

        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }
    }
}
