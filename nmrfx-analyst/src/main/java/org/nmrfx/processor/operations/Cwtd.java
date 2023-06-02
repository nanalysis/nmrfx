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

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Cwtd extends Operation {

    private final int halfWin;

    @Override
    public Cwtd eval(Vec vector) throws ProcessingException {
        cwtd(vector);
        return this;
    }

    public Cwtd(int halfWin) {
        this.halfWin = halfWin;
    }

    private void cwtd(Vec vector) throws ProcessingException {
        int size = vector.getSize(); //operation specific or vector specific?
        boolean complex = vector.isComplex();

        int m = size;
        double[] reVec = new double[m];
        double[] imVec = new double[m];

        double reSum = 0.0;
        double imSum = 0.0;
        int fullWinSize = halfWin * 2;
        double scaleCorr = 1.0 / Math.sqrt(fullWinSize);

        for (int i = 0; i < m; i++) {
            reSum = 0.0;
            imSum = 0.0;
            int max = (i + fullWinSize);
            if (max > (m - 1)) {
                max = m - 1;
            }
            for (int j = i; j <= max; j++) {
                int dIJ = (j - i);
                double psi = 0.0;
                if (dIJ >= 0) {
                    if (dIJ < halfWin) {
                        psi = 1.0;
                    } else if (dIJ < fullWinSize) {
                        psi = -1.0;
                    }
                }
                if (complex) {
                    reSum += vector.getReal(j) * psi;
                    imSum += vector.getImag(j) * psi;
                } else {
                    reSum += vector.getReal(j) * psi;
                }
            }
            if (complex) {
                reVec[i] = reSum * scaleCorr;
                imVec[i] = imSum * scaleCorr;
            } else {
                reVec[i] = reSum * scaleCorr;
            }
        }
        if (complex) {
            for (int i = 0; i < halfWin; i++) {
                vector.set(i, Complex.ZERO);
            }
            for (int i = halfWin; i < m; i++) {
                vector.set(i, new Complex(reVec[i - halfWin], imVec[i - halfWin]));
            }
        } else {
            for (int i = 0; i < halfWin; i++) {
                vector.set(i, 0.0);
            }
            for (int i = halfWin; i < m; i++) {
                vector.set(i, reVec[i - halfWin]);
            }
        }
    }
}
