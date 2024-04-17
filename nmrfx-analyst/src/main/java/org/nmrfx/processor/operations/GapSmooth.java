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
public class GapSmooth extends Operation {

    /**
     * Center point.
     */
    //Not final because in the event that values are not passed in and
    // autoCenter is false, they will only be calculated once.
    private int center;
    private int start;
    private int end;
    private final boolean autoCenter;

    /**
     * @param center
     * @param start
     * @param end
     * @param autoCenter If true, the passed in center value is ignored.
     */
    public GapSmooth(int center, int start, int end, boolean autoCenter) {
        if (autoCenter) {
            this.center = -1;
        } else {
            this.center = center;
        }
        this.start = start;
        this.end = end;
        this.autoCenter = autoCenter;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        if (vector.isComplex()) {
            vector.makeReal();
        }
        int vCenter = center;
        int vStart = start;
        int vEnd = end;
        if ((vCenter < 0) && !autoCenter) {
            vCenter = vector.getSize() / 2;
        }
        if (vStart < 0) {
            vStart = vector.getSize() / 256;
        }
        if (vEnd < 0) {
            vEnd = vStart * 3;
        }
        if (vStart > vEnd) {
            vEnd = vStart * 3;
        }
        vector.gapSmooth(vCenter, vStart, vEnd);

        return this;
    }

}
