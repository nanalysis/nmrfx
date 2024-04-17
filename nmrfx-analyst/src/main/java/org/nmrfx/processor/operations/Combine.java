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
public class Combine extends Operation {

    private final int numInputVec;
    private final int numOutputVec;
    private final double[] coef;
    private final boolean keepImag;
    private double[][] outVec;
    private double[][] outVecI;

    public Combine(int numInputVec, int numOutputVec, double[] coef, boolean keepImag) {
        this.coef = coef;
        this.keepImag = keepImag;
        this.numInputVec = numInputVec;
        this.numOutputVec = numOutputVec;
        outVec = new double[numOutputVec][];
        if (keepImag) {
            outVecI = new double[numOutputVec][];
        }

    }

    public Combine eval(Vec vector) {
        throw new OperationException("Cannot combine a single Vec");
    }

    @Override
    public Combine eval(List<Vec> vectors) throws OperationException {
        if (vectors.isEmpty()) {
            throw new OperationException("Combine cannot combine 0 vectors.");
        } else if (vectors.size() % (numInputVec) != 0) {
            throw new OperationException("Combine cannot combine a total of "
                    + vectors.size() + " vectors, taking " + numInputVec
                    + " Vectors.");
        } else {
            List<Vec> outVectors = null;
            if (numOutputVec > numInputVec) {
                int newSize = vectors.size() * numOutputVec / numInputVec;
                outVectors = new ArrayList<>(newSize);
                Vec copyVec = vectors.get(0);
                for (int i = 0; i < newSize; i++) {
                    Vec newVec = new Vec(copyVec.getSize(), false);
                    copyVec.copyRef(newVec);
                    outVectors.add(newVec);
                }
            } else {
                outVectors = vectors;
            }
            for (int i = 0; i < vectors.size() / numInputVec; ++i) {
                combine(vectors.subList(i * numInputVec, (i + 1) * numInputVec));
                for (int j = 0; j < numOutputVec; ++j) {
                    Vec newVec = outVectors.get(j + i * numOutputVec);
                    if (numOutputVec > numInputVec) {
                        Vec oldVec = vectors.get(i * numInputVec);
                        if ((oldVec.getPt() != null) && (oldVec.getPt().length > 1)) {
                            oldVec.copyLocation(newVec);
                            int[][] newPt = newVec.getPt();
                            int newLoc = newPt[1][0] * 2 + j;
                            newPt[1][0] = newLoc;
                            newPt[1][1] = newLoc;
                            newVec.setPt(newPt, newVec.getDim());
                        }
                    }
                    if (!keepImag) {
                        newVec.makeReal();
                        newVec.copy(outVec[j]);
                    } else {
                        newVec.makeComplex();
                        newVec.copy(outVec[j], outVecI[j]);
                    }
                }
            }

            int newSize = vectors.size() * numOutputVec / numInputVec;
            if (numOutputVec > numInputVec) {
                vectors.clear();
                vectors.addAll(outVectors);
            } else {
                outVectors.subList(newSize, vectors.size()).clear();
            }
        }

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
                        "Combine: input vectors are not the same size");
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
        if (keepImag) {
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

            for (int k = 0; k < numInputVec; k++) {
                for (boolean rcVal : rcVals) {
                    if (coef[iCoef] != 0.0) {
                        for (int i = 0; i < n; i++) {
                            outVec[j][i] += (vectors.get(k).getRealOrImag(i, rcVal) * coef[iCoef] * iSign);
                        }
                    }
                    if (coef[iCoef] != 0.0) {
                        if (keepImag) {
                            for (int i = 0; i < n; i++) {
                                outVecI[j][i] += (vectors.get(k).getRealOrImag(i, !rcVal) * coef[iCoef] * iSign);
                            }
                        }
                    }
                    iCoef++;
                }
            }
        }
    }

    public Combine clone() {
        Combine temp = new Combine(numInputVec, numOutputVec, coef, keepImag);
        return temp;
    }
}
