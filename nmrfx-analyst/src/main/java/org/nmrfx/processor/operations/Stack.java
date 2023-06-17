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
 * @author Bruce Johnson
 */
@PythonAPI("pyproc")
public class Stack extends Operation {

    final int nVectors;
    final int nCombine;

    public Stack(int nCombine, int nVectors) {
        this.nCombine = nCombine;
        this.nVectors = nVectors;

    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int[][] pt = vector.getPt();

        int oldRow = pt[1][0];
        int index = oldRow % (nVectors * nCombine);
        int plane = index / nCombine;
        int offset = oldRow / (nVectors * nCombine);
        int row = nCombine * offset + (oldRow % nCombine);
        pt[1][0] = row;
        pt[1][1] = row;
        pt[2][0] = plane;
        pt[2][1] = plane;

        int[] dim = vector.getDim();
        int[][] newPt = new int[pt.length + 1][2];
        int[] newDim = new int[pt.length + 1];
        for (int i = 0; i < pt.length; i++) {
            newPt[i][0] = pt[i][0];
            newPt[i][1] = pt[i][1];
            newDim[i] = dim[i];
        }
        System.out.println("oldrow " + oldRow + " index " + index + " row " + row + " plane " + plane);
        newPt[pt.length][0] = plane;
        newPt[pt.length][1] = plane;
        newPt[1][0] = row;
        newPt[1][1] = row;
        newDim[pt.length] = pt.length - 1;
        vector.setPt(newPt, newDim);

        return this;
    }

}
