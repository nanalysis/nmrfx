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
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Gf extends Apodization implements Invertible {

    private final double gf;
    private final double gfs;
    private final double fPoint;
    private double apodDwellTime;

    public Gf(double gf, double gfs, double fPoint) {
        this(gf, gfs, fPoint, false);
    }

    public Gf(double gf, double gfs, double fPoint, boolean inverse) {
        this.gf = gf;
        this.gfs = gfs;
        this.fPoint = fPoint;
        this.invertOp = inverse;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        double dwellTime = vector.dwellTime;
        int size = vector.getSize();
        if (apodVec == null || apodVec.length != vector.getSize() || apodDwellTime != dwellTime) {
            resize(vector.getSize());

            int vStart = vector.getStart();
            initApod(vStart);
            for (int i = vStart; i < size; i++) {
                double t = (i - vStart) * dwellTime;
                double x = ((t - gfs) / gf);
                apodVec[i] = Math.exp(-(x * x));
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
