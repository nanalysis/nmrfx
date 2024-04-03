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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Gen extends Operation {

    private final double freq;
    private final double lw;
    private final double amp;
    private final double ph;

    @Override
    public Gen eval(Vec vector) throws ProcessingException {
        genSignal(vector);
        return this;
    }

    public Gen(double freq, double lw, double amp, double ph) {
        this.freq = freq;
        this.lw = lw;
        this.amp = amp;
        this.ph = ph;
    }

    /**
     * Generate Signal.
     *
     * @param vector
     * @throws ProcessingException
     */
    private void genSignal(Vec vector) throws ProcessingException {
        vector.genSignalHz(freq, lw, amp, ph);
    }
}
