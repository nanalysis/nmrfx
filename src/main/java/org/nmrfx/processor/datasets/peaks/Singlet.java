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

import java.awt.geom.Line2D;
import java.util.ArrayList;

/**
 *
 * @author brucejohnson
 */
public class Singlet extends Coupling {

    public Singlet(Multiplet multiplet) {
        this.multiplet = multiplet;
    }

    Coupling update(double[] newValues, double[] newIntensities) {
        if (newIntensities.length == 1) {
            multiplet.setIntensity(newIntensities[0]);
        }
        return this;
    }

    Coupling update(double[] newValues, double intensity, double[] sin2Thetas) {
        multiplet.setIntensity(intensity);
        return this;
    }

    public boolean isCoupled() {
        return false;
    }

    public String getMultiplicity() {
        return "s";
    }

    public String getCouplingsAsString() {
        return String.valueOf(0.0);

    }

    public String getCouplingsAsSimpleString() {
        return "";
    }

    protected Coupling adjustCouplings(final Multiplet multiplet, final int iCoupling, final double newValue) {
        return this;
    }

    FreqIntensities getFreqIntensitiesFromSplittings() {
        FreqIntensities fiValues;

        fiValues = new FreqIntensities(1);
        fiValues.freqs[0] = 0;
        fiValues.intensities[0] = multiplet.getPeakDim().getPeak().getIntensity();
        return fiValues;
    }

    public ArrayList<Line2D> getSplittingGraph() {
        ArrayList<Line2D> lines = new ArrayList<Line2D>();
        lines.add(new Line2D.Double(0.0, 0.0, 0.0, 0.0));
        return lines;
    }
}
