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
import org.nmrfx.processor.processing.Processor;

/**
 * Writes a Vec to a file. The function will call the Processor and ask it to write the Vec to the open dataset.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class WriteVector extends IO {

    //should I check to see if the file is writeable before this is created?
    // that would require the file to be opened before the operation is added
    final boolean makeReal;
    final int index;

    public WriteVector() {
        this(true, -1);
    }

    public WriteVector(int index) {
        this(true, index);
    }

    public WriteVector(boolean makeReal) {
        this(makeReal, -1);
    }

    public WriteVector(boolean makeReal, int index) {
        this.makeReal = makeReal;
        this.index = index;
    }

    @Override
    public Operation eval(Vec vector) {
        // fixme always writes real, what if we want to write complex
        if (makeReal) {
            vector.makeReal();
        } else {
            // updates location point even though it doesn't change size
            vector.resize(vector.getSize(), vector.isComplex());
        }
        if (index >= 0) {
            int[][] pt = vector.getPt();
            int[] dim = vector.getDim();
            int[][] newPt = new int[pt.length + 1][2];
            int[] newDim = new int[pt.length + 1];
            for (int i = 0; i < pt.length; i++) {
                newPt[i][0] = pt[i][0];
                newPt[i][1] = pt[i][1];
                newDim[i] = dim[i];
            }
            newPt[pt.length][0] = index;
            newPt[pt.length][1] = index;
            newDim[pt.length] = pt.length;
            vector.setPt(newPt, newDim);
        }
        Processor.getProcessor().writeVector(vector);
        return this;
    }
}
