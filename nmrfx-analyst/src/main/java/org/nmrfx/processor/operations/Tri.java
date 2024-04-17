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
public class Tri extends Apodization implements Invertible {

    private final int pt1;
    private final double lHeight;
    private final double rHeight;

    public Tri(int pt1, double lHeight, double rHeight, boolean inverse) {
        this.pt1 = pt1;
        this.lHeight = lHeight;
        this.rHeight = rHeight;
        this.invertOp = inverse;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();

        if (apodVec == null || apodVec.length != size) {
            resize(size);
            int vStart = vector.getStart();
            initApod(vStart);
// fixme is this correct
            int pt1S = pt1 + vStart;
            for (int i = vStart; i < pt1S; i++) {
                apodVec[i] = lHeight + (((1.0 - lHeight) * (i - vStart)) / (pt1S - 1));
            }

            for (int i = pt1S; i < size; i++) {
                apodVec[i] = rHeight
                        + (((1.0 - rHeight) * (size - (i - vStart) - 1)) / (size - pt1S - 1));
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
