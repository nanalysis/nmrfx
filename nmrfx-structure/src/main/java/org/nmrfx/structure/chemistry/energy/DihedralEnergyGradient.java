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

import org.nmrfx.chemistry.Util;
import smile.math.DifferentiableMultivariateFunction;

public class DihedralEnergyGradient {
        Dihedral dihedral;
    final GradientRefinement gradRefine;

    public DihedralEnergyGradient(Dihedral dihedral, GradientRefinement gradRefine) {
        this.dihedral = dihedral;
        this.gradRefine = gradRefine;
    }

    public double f(double[] point) {
        for (int i = 0; i < point.length; i++) {
            point[i] = Util.reduceAngle(point[i]);
        }
        double energy = dihedral.nonNormValue(point);
        System.out.println(energy);
        return energy;
    }

    public double g(double[] point, double[] gradient) {
        for (int i = 0; i < point.length; i++) {
            point[i] = Util.reduceAngle(point[i]);
        }
        double energy = dihedral.nonNormValue(point);
        System.out.println("g " + energy);
        double[] derivatives = gradRefine.nonNormDeriv(point);
        System.arraycopy(derivatives, 0, gradient, 0, gradient.length);
        return energy;
    }
}
