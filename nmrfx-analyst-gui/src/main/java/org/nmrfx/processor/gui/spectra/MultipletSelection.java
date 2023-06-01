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

import javafx.geometry.Bounds;
import org.nmrfx.peaks.Multiplet;


/**
 * @author Bruce Johnson
 */
public class MultipletSelection {

    final Multiplet multiplet;
    final double center;
    final double edge;
    final int line;
    final Bounds bounds;

    MultipletSelection(Multiplet multiplet, Bounds bounds) {
        this.multiplet = multiplet;
        this.line = -1;
        this.center = 0;
        this.edge = 0;
        this.bounds = bounds;
    }

    MultipletSelection(Multiplet multiplet, double center, double edge, int line) {
        this.multiplet = multiplet;
        this.line = line;
        this.center = center;
        this.edge = edge;
        this.bounds = null;
    }

    public Multiplet getMultiplet() {
        return multiplet;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public boolean isLine() {
        return line >= 0;
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
