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
import java.util.Arrays;


/**
 * @author Simon Hulse
 */
@PythonAPI("pyproc")
public class ProcIndirectOp extends MatrixOperation {
    private static final Logger log = LoggerFactory.getLogger(ProcIndirectOp.class);

    private double[] ph0;
    private double[] ph1;
    private boolean[] negateImag;
    private boolean[] negatePairs;

    public ProcIndirectOp(
        double[] ph0,
        double[] ph1,
        boolean[] negateImag,
        boolean[] negatePairs
    ) {
        // TODO: include apodization and ZF
        this.ph0 = ph0;
        this.ph1 = ph1;
        this.negateImag = negateImag;
        this.negatePairs = negatePairs;
        // this.apodize = apodize
    }

    public Operation evalMatrix(MatrixType matrix) throws ProcessingException {
        try {
            MatrixND matrixND = (MatrixND) matrix;
            int nDim = matrixND.getNDim();

            // Assert that each attribute is of the correct dimension
            // If it is `null` set to default value:
            //     all `false` for `boolean[]`
            //     all `0.0` for `double[]`
            negateImag = processAttributeBoolean(negateImag, nDim, "negateImag");
            negatePairs = processAttributeBoolean(negatePairs, nDim, "negatePairs");
            ph0 = processAttributeDouble(ph0, nDim, "ph0");
            ph1 = processAttributeDouble(ph1, nDim, "ph1");

            // If ph1 = 0 -> c = 0.5
            // If ph1 = 180 -> c = 1.0
            // For any ph1 value in between, c is interpolated
            double[] cValues = new double[nDim];
            for (int i = 0; i < nDim; i++) {
                cValues[i] = (ph1[i] + 180.0) / 360.0;
            }

            matrixND.doNegateImag(negateImag);
            matrixND.doNegatePairs(negatePairs);
            // Zero fill to the next power of 2 (needed for FFT/IFFT)
            matrixND.zeroFill(0);
            matrixND.doFourierTransform(cValues);
            matrixND.doPhaseCorrection(ph0, ph1);
            matrixND.doInverseFourierTransform();
        } catch (Exception e) {
            log.error("Error in ProcIndirect", e);
            throw new ProcessingException(e.getLocalizedMessage());
        }

        return this;
    }

    // TODO: would be nice to make this generic (i.e. provide type of class as
    // an additional argument)
    // My Java skills are too weak to figure out how to make the compiler happy
    private static boolean[] processAttributeBoolean(
        boolean[] array,
        int nDim,
        String name
    ) throws ProcessingException {
        if (array == null) {
            // Returns an array of `false`
            return new boolean[nDim];
        }
        else {
            if (array.length == nDim) {
                return array;
            } else {
                throw new ProcessingException(
                    String.format(
                        "Invalid value provided for %s: Should be of length %d",
                        name,
                        nDim
                    )
                );
            }
        }
    }

    // TODO: see processAttributeBoolean above about making a generic method
    private static double[] processAttributeDouble(
        double[] array,
        int nDim,
        String name
    ) throws ProcessingException {
        if (array == null) {
            // Returns an array of `0.0`
            return new double[nDim];
        }
        else {
            if (array.length == nDim) {
                return array;
            } else {
                throw new ProcessingException(
                    String.format(
                        "Invalid value provided for %s: Should be of length %d",
                        name,
                        nDim
                    )
                );
            }
        }
    }
}
