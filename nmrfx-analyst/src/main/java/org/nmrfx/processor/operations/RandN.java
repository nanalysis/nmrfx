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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.operations;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * Adds a Gaussian distribution to a Vec.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class RandN extends Operation {

    private final double mean;
    private final double stdev;
    private final int seed;
    private final RandomGenerator random;

    public RandN(double mean, double stdev, int seed) {
        this.mean = mean;
        this.stdev = stdev;
        this.seed = seed;
        this.random = new Well19937c(seed);
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int size = vector.getSize();
        if (seed != 0) {
            random.setSeed(seed);
        }

        for (int i = 0; i < size; ++i) {
            vector.set(i, vector.getComplex(i).add(new Complex(random.nextGaussian() * stdev + mean,
                    random.nextGaussian() * stdev + mean)));
        }
        return this;
    }

}
