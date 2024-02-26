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

import org.nmrfx.processor.math.Vec;

/**
 * @author johnsonb
 */
public class FDSolventMinimizer extends BracketMinimizer {

    int icenter = 0;
    int start = 0;
    int end = 0;
    Vec testVec = null;
    Vec phaseVec = null;
    Vec vector = null;

    void genTest(double p0) {
        double degtorad = Math.PI / 180.0;
        double re = Math.cos(p0 * degtorad);
        double im = -Math.sin(p0 * degtorad);

        phaseVec.makeApache();

        for (int i = 0; i < vector.getSize(); i++) {
            testVec.set(i, (phaseVec.getReal(i) * re)
                    - (phaseVec.getImag(i) * im));
        }
    }

    public FDSolventMinimizer(Vec vector, double gridStart, double gridEnd, double gridDelta, double tol) {
        super(gridStart, gridEnd, gridDelta, tol);
        this.vector = vector;
        testVec = new Vec(vector.getSize());
        phaseVec = new Vec(vector.getSize());
        testVec.resize(vector.getSize(), false);
        vector.copy(phaseVec);

        phaseVec.hft();
    }

    public double getScore(double value) {
        genTest(value);
        return Util.calMedianDelta(testVec.getRvec(), icenter, start, end); //
    }
}
