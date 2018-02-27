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
public class Blackman extends Apodization implements Invertible {

    final double end;
    final double c;
    final int apodSize;

    @Override
    public Blackman eval(Vec vector) throws ProcessingException {
        apply(vector);
        return this;
    }

    public Blackman(double end, double c, int apodSize) {
        this(end, c, apodSize, false);
    }

    public Blackman(double end, double c, int apodSize, boolean inverse) {
        this.end = end;
        this.c = c;
        this.apodSize = apodSize;
        this.invertOp = inverse;
    }

    public void apply(Vec vector) {
        vector.makeApache();
        int apodSize = this.apodSize;
        if (this.apodSize > vector.getSize()) {
            apodSize = vector.getSize();
        }
        if (apodSize == 0) {
            apodSize = vector.getSize();
        }
        double offset = 0.5;
        if (apodVec == null || vector.getSize() != apodVec.length) {
            resize(apodSize);
            int vStart = vector.getStart();
            initApod(vStart);
            double start = offset * Math.PI;

            double delta = ((end - offset) * Math.PI) / (apodSize - vStart - 1);
            for (int i = vStart; i < apodSize; i++) {
                double deltaPos = i - vStart;
                apodVec[i] = 0.42 - 0.5 * Math.cos(2.0* start + 2.0 * (deltaPos * delta)) + 0.08 * Math.cos(4.0 * (deltaPos * delta));
            }
            apodVec[vStart] *= c;
        }
        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }
    }
}

/*
 w = (0.42 - 0.5 * np.cos(2.0 * np.pi * n / (M - 1)) +
         0.08 * np.cos(4.0 * np.pi * n / (M - 1)))
    if not sym and not odd:
        w = w[:-1]
 */
