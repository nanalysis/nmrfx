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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

/**
 *
 * @author brucejohnson
 */
public class CouplingItem implements Comparable {

    private double coupling;
    private double freq;
    private int nSplits;
    private double sin2Theta;

    public CouplingItem(final double coupling, final double sin2Theta, final double freq, final int nSplits) {
        this.coupling = coupling;
        this.sin2Theta = sin2Theta;
        this.nSplits = nSplits;
        this.freq = freq;
    }

    public CouplingItem(final double coupling, final double sin2Theta, final int nSplits) {
        this(coupling, sin2Theta, 0.0, nSplits);
    }

    public CouplingItem(final double coupling, final int nSplits) {
        this(coupling, 0.0, 0.0, nSplits);
    }

    public double getCoupling() {
        return coupling;
    }

    public double getFrequency() {
        return freq;
    }

    public double getSin2Theta() {
        return sin2Theta;
    }

    public int getNSplits() {
        return nSplits;
    }

    @Override
    public int compareTo(Object o) {
        int result = -1;
        if (o instanceof CouplingItem) {
            CouplingItem couplingItem2 = (CouplingItem) o;
            if (coupling > couplingItem2.coupling) {
                result = -1;
            } else if (coupling < couplingItem2.coupling) {
                result = 1;
            } else {
                result = 0;
            }
        }
        return result;
    }
}
