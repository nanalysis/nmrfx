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

package org.nmrfx.chemistry;

public class EnergyPair {

    public final double a1;
    public final double b1;
    public final double c1;
    public final double r;
    public final double r2;
    public final double rh;
    public final double ea;

    public EnergyPair(final double a1, final double b1, final double c1, final double r, final double r2, final double rh, final double ea) {
        this.a1 = a1;
        this.b1 = b1;
        this.c1 = c1;
        this.r = r;
        this.r2 = r2;
        this.rh = rh;
        this.ea = ea;
    }

    public double getRh() {
        return rh;
    }
}
