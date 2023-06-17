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
 * Lorentz-to-Gauss Apodization
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Gm extends Apodization implements Invertible {

    private final double g1;
    private final double g3;
    private final double g2;
    private final double fPoint;
    private double apodDwellTime;

    public Gm(double g1, double g2, double g3, double fPoint, boolean inverse) {
        this.g1 = g1;
        this.g2 = g2;
        this.g3 = g3;
        this.fPoint = fPoint;
        this.invertOp = inverse;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        double dwellTime = vector.dwellTime;
        int size = vector.getSize();
        if (apodVec == null || apodVec.length != vector.getSize() || apodDwellTime != dwellTime) {
            resize(vector.getSize());

            apodDwellTime = dwellTime;
            double e = Math.PI * g1 * dwellTime;
            double ga = 0.6 * Math.PI * g2 * dwellTime;
            double gb = ga * g3 * (size - 1);
            int vStart = vector.getStart();
            initApod(vStart);
            for (int i = vStart; i < size; i++) {
                double g = gb - (ga * (i - vStart));
                apodVec[i] = Math.exp((e * (i - vStart)) - (g * g));
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
