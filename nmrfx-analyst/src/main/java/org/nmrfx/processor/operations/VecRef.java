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
 * Zero a vector and resize it if a positive size is given.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class VecRef extends Operation {

    private final Integer size;
    private final Double sf;
    private final Double sw;

    public VecRef(Integer size, Double sf, Double sw) {
        this.size = size;
        this.sf = sf;
        this.sw = sw;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        if (size != null) {
            vector.resize(size);
            vector.setTDSize(size);
        }
        if (sf != null) {
            vector.centerFreq = sf;
        }
        if ((sw != null) && (sw > 1.0)) {
            vector.dwellTime = 1.0 / sw;
        }
        vector.schedule = null;
        vector.clearAnnotation();
        return this;
    }

}
