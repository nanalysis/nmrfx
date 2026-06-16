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
package org.nmrfx.chart;

/**
 * @author brucejohnson
 */
//TODO uncomment once core & utils are merged
//@PluginAPI("ring")
public class XYValue {

    private final double x;
    private final double y;
    private Object extraValue;
    private boolean disabled;

    public XYValue(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public XYValue(double x, double y, Object extra) {
        this.x = x;
        this.y = y;
        this.extraValue = extra;
    }

    /**
     * @return the x
     */
    public double getXValue() {
        return x;
    }

    /**
     * @return the y
     */
    public double getMaxYValue() {
        return y;
    }

    /**
     * @return the x
     */
    public double getMinYValue() {
        return y;
    }

    /**
     * @return the y
     */
    public double getYValue() {
        return y;
    }

    public void setExtraValue(Object value) {
        this.extraValue = value;
    }

    public Object getExtraValue() {
        return extraValue;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean state) {
        disabled = state;
    }
}
