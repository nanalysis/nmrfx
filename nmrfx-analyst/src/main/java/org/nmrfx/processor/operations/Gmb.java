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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * Gaussian Broaden Apodization
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Gmb extends Apodization implements Invertible {

    private final double gb;
    private final double lb;
    private final double fPoint;
    private double apodDwellTime;

    public Gmb(double gb, double lb, double fPoint) {
        this(gb, lb, fPoint, false);
    }

    public Gmb(double gb, double lb, double fPoint, boolean inverse) {
        this.gb = gb;
        this.lb = lb;
        this.fPoint = fPoint;
        this.invertOp = inverse;
        apodDwellTime = Double.NEGATIVE_INFINITY;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();
        double dwellTime = vector.dwellTime;

        if (apodVec == null || apodVec.length != size || dwellTime != apodDwellTime) {
            resize(size);
            int vStart = vector.getStart();
            initApod(vStart);
            apodDwellTime = vector.dwellTime;
            double aq = size * dwellTime;
            double a = Math.PI * lb;
            double b = 0.0;
            if (gb != 0.0) {
                b = -a / (2.0 * gb * aq);
            }

            for (int i = vStart; i < size; i++) {
                double t = (i - vStart) * dwellTime;
                apodVec[i] = Math.exp((-a * t) - (b * t * t));
            }

            apodVec[vStart] *= fPoint;
        }
        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }

        return this;
    }
}
