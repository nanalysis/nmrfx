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

import java.util.ArrayList;
import java.util.List;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class CoAdd extends Operation {

    private final double[] coef;
    private final int numInputVec;
    private double[] outVec;
    private double[] outVecI;

    public CoAdd(double[] coef) {
        this.coef = coef.clone();
        this.numInputVec = coef.length;
    }

    public CoAdd eval(Vec vector) {
        throw new OperationException("Cannot combine a single Vec");
    }

    @Override
    public CoAdd eval(List<Vec> vectors) throws OperationException {
        if (vectors.isEmpty()) {
            throw new OperationException("CoAdd cannot combine 0 vectors.");
        } else if (vectors.size() % (numInputVec) != 0) {
            throw new OperationException("CoAdd cannot combine a total of "
                    + vectors.size() + " vectors, taking " + numInputVec
                    + " Vectors.");
        } else {
            int iVec = 0;
            boolean isComplex = vectors.get(0).isComplex();
            ArrayList<Vec> subList = new ArrayList<>();
            for (int i = 0; i < vectors.size() / numInputVec; ++i) {
                subList.clear();
                int[][] pt = vectors.get(iVec).getPt();
                int[] dim = vectors.get(iVec).getDim();
                for (int j = 0; j < numInputVec; j++) {
                    subList.add(vectors.get(iVec++));
                }
                combine(subList);
                Vec vec = vectors.get(i);
                if (isComplex) {
                    vec.makeComplex();
                    vec.copy(outVec, outVecI);
                } else {
                    vec.copy(outVec);
                }
                vec.setPt(pt, dim);
            }
        }
        int newSize = vectors.size() / numInputVec;
        vectors.subList(newSize, vectors.size()).clear();
        return this;
    }

    public static ArrayList<Vec> getArrayList() {
        return new ArrayList<Vec>();
    }

    public void combine(List<Vec> vectors) {
        int n = vectors.get(0).getSize();
        for (int j = 1; j < numInputVec; j++) {
            if (vectors.get(j).getSize() != n) {
                throw new OperationException(
                        "CoAdd: input vectors are not the same size");
            }
        }
        if ((outVec == null) || (outVec.length != n)) {
            outVec = new double[n];
            outVecI = new double[n];
        }
        for (int i = 0; i < n; i++) {
            outVec[i] = 0.0;
            outVecI[i] = 0.0;
        }

        boolean inComplex = vectors.get(0).isComplex();

        for (int k = 0; k < numInputVec; k++) {
            if (coef[k] != 0.0) {
                for (int i = 0; i < n; i++) {
                    outVec[i] += (vectors.get(k).getReal(i) * coef[k]);
                    if (inComplex) {
                        outVecI[i] += (vectors.get(k).getImag(i) * coef[k]);
                    }
                }
            }
        }
    }

    public CoAdd clone() {
        CoAdd temp = new CoAdd(coef);
        return temp;
    }
}
