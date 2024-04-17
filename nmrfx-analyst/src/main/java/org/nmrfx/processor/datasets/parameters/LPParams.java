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
 * Linear Prediction parameters
 *
 * @author bfetler
 */
public class LPParams implements Existence {

    protected boolean exists = false;
    protected boolean status = false;
    protected int ncoef = 0, predictstart, predictend, fitstart, fitend;

    // constructor is vendor dependent
    // use default constructor if not supported
    public LPParams() {
    }

    public LPParams(int dim) {
    }

    public boolean exists() {
        return exists;
    }

    public boolean status() {
        return status;
    }

    public int ncoef() {
        return ncoef;
    }

    public int predictstart() {
        return predictstart;
    }

    public int predictend() {
        return predictend;
    }

    public int fitstart() {
        return fitstart;
    }

    public int fitend() {
        return fitend;
    }

}
