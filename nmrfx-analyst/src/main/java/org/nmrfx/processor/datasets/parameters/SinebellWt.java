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
 * Sinebell weighting parameters
 *
 * @author bfetler
 */
public abstract class SinebellWt implements Existence {

    protected int power, size;
    protected double sb = 0.0;
    protected double sbs, offset, end;

    // constructor is vendor dependent, e.g.
    //   SinebellWt(int dim) { }
    public boolean exists() {
        if (sb > 1.0e-6) {
            return true;
        } else {
            return false;
        }
    }

    public int power() {
        return power;
    }

    public double sb() {
        return sb;
    }

    public double sbs() {
        return sbs;
    }

    public int size() {
        return size;
    }

    public double offset() {
        return offset;
    }

    public double end() {
        return end;
    }

}
