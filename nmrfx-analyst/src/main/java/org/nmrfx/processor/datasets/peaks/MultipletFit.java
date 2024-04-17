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
package org.nmrfx.processor.datasets.peaks;

class MultipletFit {

    double freq = 0.0;
    double width = 0.0;
    double[] amplitudes = null;
    double[] jValues = null;
    int[] nPartners = null;
    int nLines = 0;

    MultipletFit(double freq, double width, double[] values, int[] nVals, double scale) {
        this.freq = freq;
        this.width = width;

        nLines = 1;
        nPartners = new int[nVals.length];
        jValues = new double[nVals.length];

        for (int i = 0; i < nVals.length; i++) {
            nPartners[i] = nVals[i];
            jValues[i] = values[i] * scale;
            System.out.println(values[i] + " " + scale + " " + jValues[i]);
            nLines *= (nPartners[i] + 1);
        }

        amplitudes = new double[nLines];
    }

    public double[] getJValues() {
        return jValues;
    }

    public int[] getNPartners() {
        return nPartners;
    }

    public int getNLines() {
        return nLines;
    }

    public double getFreq() {
        return freq;
    }

    public double getWidth() {
        return width;
    }
}
