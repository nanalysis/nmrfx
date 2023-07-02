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
package org.nmrfx.processor.optimization;

/**
 * @author graham
 */
public final class ConfidenceInterval {

    private final VecID paramName;
    private final double percent;
    private final double[] data;

    ConfidenceInterval(VecID paramName, double percent, double[] data) {
        this.paramName = paramName;
        this.percent = percent;
        this.data = data;
    }

    public double[] getData() {
        return data;
    }

    public double getBest() {
        return data[0];
    }

    public double getLower() {
        return data[1];
    }

    public double getUpper() {
        return data[2];
    }

    public double getStandardDeviation() {
        return data[3];
    }

    public VecID getParamName() {
        return paramName;
    }

    public String[] toTclString() {
        String[] str = new String[3];

        str[0] = paramName + ",best," + data[0];
        str[1] = paramName + ",lower," + data[1];
        str[2] = paramName + ",upper," + data[2];

        return str;
    }
}
