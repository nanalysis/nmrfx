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
import org.apache.commons.math3.util.FastMath;

/**
 *
 * @author johnsonb
 */
public class SinebellApod extends Apodization implements Invertible {

    final double offset;
    final double end;
    final double power;
    final double c;
    final int apodSize;

    @Override
    public SinebellApod eval(Vec vector) throws ProcessingException {
        sb(vector);
        return this;
    }

    public SinebellApod(double offset, double end, double power, double c, int apodSize) {
        this(offset, end, power, c, apodSize, false);
    }

    public SinebellApod(double offset, double end, double power, double c, int apodSize, boolean inverse) {
        this.offset = offset;
        this.end = end;
        this.power = power;
        this.c = c;
        this.apodSize = apodSize;
        this.invertOp = inverse;
    }

    public void sb(Vec vector) {
        vector.makeApache();
        int apodSize = this.apodSize;
        if (this.apodSize > vector.getSize()) {
            apodSize = vector.getSize();
        }
        if (apodSize == 0) {
            apodSize = vector.getSize();
        }

        if (apodVec == null || vector.getSize() != apodVec.length) {
            resize(apodSize);
            int vStart = vector.getStart();
            initApod(vStart);

            double start = offset * Math.PI;
            double delta = ((end - offset) * Math.PI) / (apodSize - vStart - 1);

            if (power != 1.0) {
                for (int i = vStart; i < apodSize; i++) {
                    double deltaPos = i - vStart;
                    apodVec[i] = Math.pow(Math.sin(start + (deltaPos * delta)), power);
                }
            } else {
                for (int i = vStart; i < apodSize; i++) {
                    double deltaPos = i - vStart;
                    apodVec[i] = Math.sin(start + (deltaPos * delta));
                }
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
