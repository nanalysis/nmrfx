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
 * Trapezoid Multiply
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Tm extends Apodization implements Invertible {

    private final int pt1;
    private Integer pt2;
    private double apodDwellTime;

    public Tm(int pt1, Integer pt2) {
        this(pt1, pt2, false);
    }

    public Tm(int pt1, Integer pt2, boolean inverse) {
        if (pt1 < 0) {
            this.pt1 = 0;
        } else {
            this.pt1 = pt1;
        }

        if (pt2 == null) {
            this.pt2 = -1;
        } else {
            this.pt2 = pt2;
        }
        this.invertOp = inverse;

        apodDwellTime = Double.NEGATIVE_INFINITY;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();
        double dwellTime = vector.dwellTime;

        if (pt2 < 0) {
            pt2 = vector.getSize() - 1;
        }

        if (apodVec == null || apodVec.length != size || dwellTime != apodDwellTime) {
            resize(size);
            int vStart = vector.getStart();
            initApod(vStart);

            // fixme  is this right
            int pt1S = pt1 + vStart;
            int pt2S = pt2 + vStart;

            for (int i = vStart; i < pt1S; i++) {
                apodVec[i] = ((double) (i - vStart + 1.0e-6)) / (pt1S + 1.0e-6);
            }
            for (int i = pt1S; i <= pt2S; i++) {
                apodVec[i] = 1.0;
            }

            for (int i = (pt2S + 1); i < size; i++) {
                apodVec[i] = ((double) (size - (i - vStart) - 1 + 1.0e-6)) / (size - pt2S - 1 + 1.0e-6);
            }

        }
        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }
        return this;
    }

}
