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

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class BcPoly extends Operation {

    private final int order;
    private final int winSize;

    public BcPoly(int order, int winSize) {
        this.order = order;
        this.winSize = winSize;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        if ((winSize < 0) || (winSize > vector.getSize())) {
            throw new OperationException("bcpoly: error in winSize");
        }

        int vecSize = vector.getSize();
        int nRegions = vecSize / winSize;
        if ((order < 1) || (order > 16) || (order > (nRegions / 2))) {
            throw new OperationException(
                    "bcpoly: order must be <= 16 and >= 1 and <= (nRegions/2)");
        }
        if (vector.isComplex()) {
            vector.makeReal();
        }

        boolean[] isInSignalRegion = vector.getSignalRegion();
        if ((isInSignalRegion == null) || (isInSignalRegion.length <= 4)) {
            vector.calcBaseLineRegions();
            isInSignalRegion = vector.getSignalRegion();
        }

        if ((isInSignalRegion != null) && (isInSignalRegion.length > 4)) {
            double[] reVec = new double[nRegions];
            boolean[] baseVec = new boolean[nRegions];
            double reSum = 0.0;
            int k = 0;
            int nBaseRegions = 0;
            for (int j = 0; j < nRegions; j++) {
                reSum = 0.0;
                int pointsInRegion = 0;
                boolean justBase = true;
                for (int i = 0; ((i < winSize) && (k < vector.getSize())); i++) {
                    if (isInSignalRegion[k]) {
                        justBase = false;
                    }
                    reSum += vector.getReal(k);
                    pointsInRegion++;
                    k++;
                }
                if (justBase) {
                    nBaseRegions++;
                    baseVec[j] = true;
                }
                reVec[j] = reSum / pointsInRegion;
            }
            RealMatrix xyVals = new Array2DRowRealMatrix(nBaseRegions, 2);
            int iBase = 0;
            for (int i = 0; i < nRegions; i++) {
                if (baseVec[i]) {
                    xyVals.setEntry(iBase, 0, (((i * winSize) + (winSize / 2)) - 0.5));
                    xyVals.setEntry(iBase, 1, reVec[i]);
                    iBase++;
                }
            }

            if (xyVals.getRowDimension() > (2 * order)) {
                RealVector X = Util.fitPoly(vector, order, xyVals);
                vector.correctVec(order, X);
            }
        }
        return this;
    }

}
