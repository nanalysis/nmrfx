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
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.processing.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO:
// Used for debugging. Remove when commiting


/**
 * @author Simon Hulse
 */
@PythonAPI("pyproc")
public class ZfMatrix extends MatrixOperation {
    private static final Logger log = LoggerFactory.getLogger(ZfMatrix.class);

    private int[] factors;

    public ZfMatrix(int[] factors) {
        this.factors = new int[factors.length];
        System.arraycopy(factors, 0, this.factors, 0, factors.length);
    }

    public Operation evalMatrix(MatrixType matrix) throws ProcessingException {
        try {
            MatrixND matrixND = (MatrixND) matrix;
            int nDim = matrixND.getNDim();
            int[] zfSizes = new int[nDim];
            for (int axis = 0; axis < nDim; axis++) {
                zfSizes[axis] = MatrixND.getZfSize(matrixND.getSize(axis), factors[axis]);
            }
            matrixND.zeroFill(zfSizes);
            matrixND.setVSizes(zfSizes);
        } catch (Exception e) {
            log.error("Error in ZfMat", e);
            throw new ProcessingException(e.getLocalizedMessage());
        }

        return this;
    }

}
