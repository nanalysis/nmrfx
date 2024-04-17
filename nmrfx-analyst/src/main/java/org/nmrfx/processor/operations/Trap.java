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

import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
public class Trap extends Apodization implements Invertible {

    private final int pt1;
    private final int pt2;

    public Trap(int pt1, int pt2, boolean inverse) {
        this.pt1 = pt1;
        this.pt2 = pt2;
        this.invertOp = inverse;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();

        if (apodVec == null || apodVec.length != size) {
            resize(size);
            initApod(0);

            for (int i = 0; i < pt1; i++) {
                apodVec[i] = ((double) i) / pt1;
            }
            for (int i = pt1; i <= pt2; i++) {
                apodVec[i] = 1.0;
            }
            for (int i = (pt2 + 1); i < size; i++) {
                apodVec[i] = ((double) (size - i - 1)) / (size - pt2 - 1);
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
