/*
 * NMRFx Structure : A Program for Calculating Structures
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

package org.nmrfx.structure.chemistry.energy;


import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.nmrfx.chemistry.Util;

public class DihedralGradient implements MultivariateVectorFunction {

    final GradientRefinement gradRefine;

    public DihedralGradient(GradientRefinement gradRefine) {
        this.gradRefine = gradRefine;
    }

    public double[] value(double[] point) {
        for (int i = 0; i < point.length; i++) {
            point[i] = Util.reduceAngle(point[i]);
        }

        double[] derivatives = gradRefine.nonNormDeriv(point);
        double max = 0.0;
        for (double value : derivatives) {
            if (Math.abs(value) > max) {
                max = Math.abs(value);
            }
        }
        return derivatives;
    }
}
