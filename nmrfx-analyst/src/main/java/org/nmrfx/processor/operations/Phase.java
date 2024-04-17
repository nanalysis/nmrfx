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

import org.apache.commons.math3.util.FastMath;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;

/**
 * Performs a Phase Operation on a Vec.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Phase extends Operation implements Invertible {

    private final double p0;
    private final double p1;
    private final Integer pivot;
    private final Boolean phaseAbs;
    private final boolean discardImaginary;
    private double[] pReal = null;
    private double[] pImag = null;
    private double ph0save;
    private double ph1save;

    private final static double degtorad = Math.PI / 180.0;

    public Phase(Double p0, Double p1) {
        this(p0, p1, false, false);
    }

    public Phase(Double p0, Double p1, boolean discardImaginary) {
        this(p0, p1, discardImaginary, false);
    }

    public Phase(Double p0, Double p1, boolean discardImaginary, boolean inverse) {
        this(p0, p1, null, null, discardImaginary, inverse);
    }

    /**
     * Note: right now this is ONLY implementing a flat Phase.
     */
    public Phase(Double p0, Double p1, Boolean phaseAbs, Integer pivot, boolean discardImaginary, boolean inverse) {
        this.p0 = p0;
        this.p1 = p1;

        if (phaseAbs == null) {
            this.phaseAbs = false;
        } else {
            this.phaseAbs = phaseAbs;
        }

        this.pivot = pivot;
        this.invertOp = inverse;
        this.discardImaginary = discardImaginary;
    }

    @Override
    public Phase eval(Vec vector) throws OperationException {
        double ph0;
        double ph1;
        if (invertOp) {
            ph0 = -p0;
            ph1 = -p1;
            vector.hft();
        } else {
            ph0 = p0;
            ph1 = p1;
        }
        if ((pivot == null) && (phaseAbs == false)) {
            int size = vector.getSize();
            if ((pReal == null) || (pReal.length != size) || (ph0 != ph0save) || (ph1 != ph1save)) {
                ph0save = ph0;
                ph1save = ph1;
                pReal = new double[size];
                pImag = new double[size];
                if (FastMath.abs(ph1) < 0.0001) {
                    double pRealVal = FastMath.cos(ph0 * degtorad);
                    double pImagVal = -FastMath.sin(ph0 * degtorad);
                    for (int i = 0; i < size; i++) {
                        pReal[i] = pRealVal;
                        pImag[i] = pImagVal;
                    }
                } else {
                    double dDelta = ph1 / (size - 1);
                    for (int i = 0; i < size; i++) {
                        double p = ph0 + i * dDelta;
                        pReal[i] = FastMath.cos(p * degtorad);
                        pImag[i] = -FastMath.sin(p * degtorad);
                    }
                }
            }
            vector.phase(ph0, ph1, discardImaginary, pReal, pImag);
        } else if (pivot != null) {
            vector.phase(ph0, ph1, pivot, phaseAbs, discardImaginary);
        } else {
            vector.phase(ph0, ph1, phaseAbs, discardImaginary);
        }
        return this;
    }
}
