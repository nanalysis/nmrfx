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
 * I don't think this should be a function, also accidentally implemented in Vec as well as here.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Real extends Operation implements Invertible {

    public Real() {
        this(false);
    }

    public Real(boolean inverse) {
        this.invertOp = inverse;
    }

    @Override
    public Real eval(Vec vector) throws ProcessingException {
        if (!invertOp) {
            vector.makeReal();
        } else {
            vector.hft();
        }
        return this;
    }
}
