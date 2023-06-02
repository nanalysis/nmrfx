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

import javafx.scene.paint.Color;
import org.nmrfx.peaks.Peak;

import java.util.List;

/**
 * @author tedcolon
 */
public class ConnectPeakAttributes {

    private Color connColor = Color.BLACK;
    private double connWidth = 1.0;
    private List<Peak> peaks = null;

    public ConnectPeakAttributes(List<Peak> peaks) {
        this.peaks = peaks;
    }

    public void setColor(Color color) {
        setColor(color.toString(), 1.0);
    }

    public void setColor(String color, double opacity) {
        connColor = Color.web(color, opacity);
    }

    public void setWidth(double width) {
        connWidth = width;
    }

    public Color getColor() {
        return connColor;
    }

    public double getWidth() {
        return connWidth;
    }

    public List<Peak> getPeaks() {
        return peaks;
    }
}
