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
package org.nmrfx.processor.gui.spectra;

import org.nmrfx.chart.AxisLimits;

/**
 * @author brucejohnson
 */
public class NMRAxisIO extends NMRAxisBase implements AxisLimits {

    private double lowerBound;
    private double upperBound;
    private double start;
    private double end;
    private String label = "";

    public NMRAxisIO(double lowerBound, double upperBound, double start, double end) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.start = start;
        this.end = end;
    }

    public double getDisplayPosition(Number value) {
        double f = (value.doubleValue() - lowerBound) / (upperBound - lowerBound);
        double displayPosition = f * (end - start) + start;
        if (reverse) {
            displayPosition = end - displayPosition + start;
        }
        return displayPosition;
    }

    /**
     * @return the lowerBound
     */
    public double getLowerBound() {
        return lowerBound;
    }

    /**
     * @param lowerBound the lowerBound to set
     */
    public void setLowerBound(double lowerBound) {
        this.lowerBound = lowerBound;
    }

    /**
     * @return the upperBound
     */
    public double getUpperBound() {
        return upperBound;
    }

    /**
     * @param upperBound the upperBound to set
     */
    public void setUpperBound(double upperBound) {
        this.upperBound = upperBound;
    }

    /**
     * @return the start
     */
    public double getStart() {
        return start;
    }

    /**
     * @param start the start to set
     */
    public void setStart(double start) {
        this.start = start;
    }

    /**
     * @return the end
     */
    public double getEnd() {
        return end;
    }

    /**
     * @param end the end to set
     */
    public void setEnd(double end) {
        this.end = end;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

}
