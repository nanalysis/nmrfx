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
public class TDCombine extends Operation {

    private final int numInputVec;
    private final int numOutputVec;
    private double[][] outVec;
    private double[][] outVecI;
    private double[] coef;
    private int dim;

    public TDCombine(int dim, int numInputVec, int numOutputVec, double[] coef) {
        this.coef = coef.clone();
        this.dim = dim;
        this.numInputVec = numInputVec;
        this.numOutputVec = numOutputVec;
        outVec = new double[numOutputVec][];
        outVecI = new double[numOutputVec][];

    }

    public TDCombine eval(Vec vector) {
        throw new OperationException("Cannot combine a single Vec");
    }

    @Override
    public TDCombine eval(List<Vec> vectors) throws OperationException {
        if (vectors.isEmpty()) {
            throw new OperationException("TDCombine cannot combine 0 vectors.");
        } else if (vectors.size() % (numInputVec) != 0) {
            throw new OperationException("TDCombine cannot combine a total of "
                    + vectors.size() + " vectors, taking " + numInputVec
                    + " Vectors.");
        } else {
            boolean[] used = new boolean[vectors.size()];
            int offset = dim;
            ArrayList<Vec> subList = new ArrayList<>();
            for (int vec0 = 0; vec0 < vectors.size(); vec0++) {
                int vec1 = vec0 + offset;
                if (vec1 >= vectors.size()) {
                    break;
                }
                if (!used[vec0] && !used[vec1]) {
                    subList.clear();
                    used[vec0] = true;
                    used[vec1] = true;
                    subList.add(vectors.get(vec0));
                    subList.add(vectors.get(vec1));
                    combine(subList);
                    vectors.get(vec0).makeComplex();
                    vectors.get(vec0).copy(outVec[0], outVecI[0]);
                    vectors.get(vec1).makeComplex();
                    vectors.get(vec1).copy(outVec[1], outVecI[1]);
                }
            }
        }

        int newSize = vectors.size() * numOutputVec / numInputVec;
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
                        "TDCombine: input vectors are not the same size");
            }
        }

        for (int j = 0; j < numOutputVec; j++) {
            if (outVec.length == 0 || outVec.length < n) {
                outVec[j] = new double[n];
            } else //if we have not change the size then we have to zero it out.
            {
                for (int i = 0; i < outVec[j].length; ++i) {
                    outVec[j][i] = 0.0; //zero out between combine function calls
                }
            }
        }
        for (int j = 0; j < numOutputVec; j++) {
            if (outVecI.length == 0 || outVecI.length < n) {
                outVecI[j] = new double[n];
            } else //if we have not change the size then we have to zero it out.
            {
                for (int i = 0; i < outVecI[j].length; ++i) {
                    outVecI[j][i] = 0.0; //zero out between combine function calls
                }
            }
        }

        // Echo antiecho
        /* 1  0 -1  0
         0  1  0  1 */

        /* 1 0 0 0 0 0 1 0 */
 /* 2D states
         // RR1, IR1, RR2, IR2, RI1, II1, RI2, II2.
         iR1oR iI1oR iR2oR iI2oR
         1     0     0     0
         
         iR1oI iI1oI iR2oI iI2oI
         0     0     1     0
         
         iR1oR1 iI1oR1 iR2oR1 iI2oR1
         1      0      0      0
         
         iR1oR2 iI1oR2 iR2oR2 iI2oR2
         0      0      1      0
         
         iI1oI1 iR1oI1 iI2oI1 iR2oI1
         1      0      0      0
         
         iI1oI2 iR1oI2 iI2oI2 iR2oI2
         0      0      1      0
         
         */
        int iCoef = 0;
        boolean[] rcVals;

        boolean inComplex = vectors.get(0).isComplex();

        if (!inComplex) {
            rcVals = new boolean[]{false};
        } else {
            rcVals = new boolean[]{false, true};
        }

        int iSign = 1;

        for (int j = 0; j < numOutputVec; j++) {
            if (!inComplex || ((j % 2) == 0)) {
                iSign = 1;
            } else {
                iSign = -1;
            }
            iSign = 1;

            for (int k = 0; k < numInputVec; k++) {
                for (boolean rcVal : rcVals) {
                    if (coef[iCoef] != 0.0) {
                        for (int i = 0; i < n; i++) {
                            outVec[j][i] += (vectors.get(k).getRealOrImag(i, rcVal) * coef[iCoef]);
                        }
                    }
                    if (coef[iCoef] != 0.0) {
                        int jSign = 1;
                        // if swapping real into imaginary the direction will be changed so negate new imaginary
                        if (!inComplex || ((j % 2) == 1)) {
                            if (!rcVal) {
                                jSign = -1;
                            }
                            jSign = -1;
                        }
                        for (int i = 0; i < n; i++) {
                            outVecI[j][i] += (vectors.get(k).getRealOrImag(i, !rcVal) * coef[iCoef] * iSign * jSign);
                        }
                    }
                    iCoef++;
                }
            }
        }
    }

    public int getDim() {
        return dim;
    }

    public TDCombine clone() {
        TDCombine temp = new TDCombine(dim, numInputVec, numOutputVec, coef);
        return temp;
    }
}
