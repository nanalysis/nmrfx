/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.gui.spectra;

import org.nmrfx.processor.datasets.peaks.Multiplet;

/**
 *
 * @author Bruce Johnson
 */
public class MultipletSelection {

    final Multiplet multiplet;
    final double center;
    final double edge;
    final int line;

    MultipletSelection(Multiplet multiplet, double center, double edge, int line) {
        this.multiplet = multiplet;
        this.line = line;
        this.center = center;
        this.edge = edge;
    }

    public Multiplet getMultiplet() {
        return multiplet;
    }

    public int getLine() {
        return line;
    }

    public double getCenter() {
        return center;
    }

    public double getEdge() {
        return edge;
    }

}
