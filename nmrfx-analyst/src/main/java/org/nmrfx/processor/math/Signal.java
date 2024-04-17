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
package org.nmrfx.processor.math;

import org.nmrfx.annotations.PluginAPI;

@PluginAPI("parametric")
public class Signal implements Comparable<Signal> {

    public double amplitude = 0.0;
    public double phase = 0.0;
    public double frequency = 0.0;
    public double decay = 0.0;

    public Signal(double amplitude, double phase, double frequency, double decay) {
        this.amplitude = amplitude;
        this.phase = phase;
        this.frequency = frequency;
        this.decay = decay;
    }

    public Signal(Signal signal) {
        this.amplitude = signal.amplitude;
        this.phase = signal.phase;
        this.frequency = signal.frequency;
        this.decay = signal.decay;
    }

    @Override
    public String toString() {
        String result = String.format("%8.4f %8.4f %8.4f %8.4f", amplitude, phase, frequency, decay);
        return result;
    }

    @Override
    public int compareTo(Signal o) {
        int result = 1;
        if (o != null) {
            if (frequency < o.frequency) {
                result = -1;
            } else if (frequency == o.frequency) {
                result = 0;
            }
        }
        return result;
    }
}
