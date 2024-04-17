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
package org.nmrfx.processor.optimization;

public class SineSignal implements Comparable {

    double freq = 0.0;
    double width = 0.0;
    double amplitude = 0.0;
    double phase = 0.0;

    public SineSignal(double freq, double width, double amplitude) {
        this.freq = freq;
        this.width = width;
        this.amplitude = amplitude;
        this.phase = 0.0;
    }

    public SineSignal(double freq, double width, double amplitude, double phase) {
        this.freq = freq;
        this.width = width;
        this.amplitude = amplitude;
        this.phase = phase;
    }

    public double getFreq() {
        return freq;
    }

    public double getAmplitude() {
        return amplitude;
    }

    public double getWidth() {
        return width;
    }

    public int compareTo(Object o) {
        SineSignal so = (SineSignal) o;
        int result = 0;

        if (o == null) {
            result = 1;
        } else if (freq > so.freq) {
            result = 1;
        } else if (freq == so.freq) {
            result = 0;
        } else {
            result = -1;
        }

        return result;
    }

    public String toString() {
        StringBuffer sBuf = new StringBuffer();
        sBuf.append(freq);
        sBuf.append(" ");
        sBuf.append(width);
        sBuf.append(" ");
        sBuf.append(amplitude);
        sBuf.append(" ");
        sBuf.append(phase);

        return sBuf.toString();
    }

    public double diff(SineSignal signal1) {
        return Math.abs(freq - signal1.freq);
    }

    // assumes lines are virtually overlapping so no need to change linewidth
    public void merge(SineSignal signal1) {
        freq = (freq + signal1.freq) / 2.0;
        amplitude = amplitude + signal1.amplitude;
    }
}
