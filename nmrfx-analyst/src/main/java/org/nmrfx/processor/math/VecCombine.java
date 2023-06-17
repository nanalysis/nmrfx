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
package org.nmrfx.processor.math;

import org.nmrfx.math.VecBase;

/**
 * @author brucejohnson
 */
// redundant with Combine.java,  kept for use from VecCmd
public class VecCombine {

    public static void comb2(double[] coef, Vec[] inVec,
                             boolean inComplex, Vec[] outVec) throws IllegalArgumentException {
        int nIn = inVec.length;
        int nOut = outVec.length;

        int n = inVec[0].getSize();

        for (int j = 1; j < nIn; j++) {
            if (inVec[j].getSize() != n) {
                throw new IllegalArgumentException("Input vectors are not the same size");
            }
        }
        for (int j = 0; j < nIn; j++) {
            if (inVec[j].isComplex() && !inVec[j].useApache()) {
                inVec[j].makeApache();
            }
        }

        for (int j = 0; j < nOut; j++) {
            outVec[j].resize(n, false);
            outVec[j].zeros();
            VecBase.copyRef(inVec[0], outVec[j]);
        }

        /* 1 0 0 0 0 0 1 0 */
 /* 2D states
         iR1oR iI1oR iR1oI iI1oI
         1     0     0     0
         
         iR2oR iI2oR iR2oI iI2oI
         0     0     1     0
         
         */
        int iCoef = 0;
        int nComp = 2;

        if (!inComplex) {
            nComp = 1;
        }

        int iSign;

        for (int j = 0; j < nOut; j++) {
            if ((j % 2) == 0) {
                iSign = 1;
            } else {
                iSign = -1;
            }

            for (int k = 0; k < nIn; k++) {
                for (int irIn = 0; irIn < nComp; irIn++) {
                    if (coef[iCoef] != 0.0) {
                        if (!inComplex) {
                            for (int i = 0; i < n; i++) {
                                outVec[j].rvec[i] += (inVec[k].rvec[i] * coef[iCoef]);
                            }
                        } else if (irIn != 1) {
                            for (int i = 0; i < n; i++) {
                                outVec[j].rvec[i] += (inVec[k].cvec[i].getReal() * coef[iCoef] * iSign);
                            }
                        } else {
                            for (int i = 0; i < n; i++) {
                                outVec[j].rvec[i] += (inVec[k].cvec[i].getImaginary() * coef[iCoef] * iSign);
                            }
                        }
                    }

                    iCoef++;
                }
            }
        }
    }

}
