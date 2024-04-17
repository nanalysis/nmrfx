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
package org.nmrfx.processor.datasets.parameters;

/**
 * First point parameter
 *
 * @author bfetler
 */
public class FPMult implements Existence {

    protected boolean exists = false;
    protected double fpmult = 1.0;

    // constructor is vendor dependent
    // use default constructor if not supported
    public FPMult() {
    }

    public FPMult(int dim) {
    }

    public boolean exists() {
        return exists;
    }

    public double fpmult() {
        return fpmult;
    }

}
