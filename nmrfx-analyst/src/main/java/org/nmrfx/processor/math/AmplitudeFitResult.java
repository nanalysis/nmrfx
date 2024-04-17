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
package org.nmrfx.processor.math;

import org.nmrfx.annotations.PluginAPI;

/**
 * @author brucejohnson
 */
@PluginAPI("parametric")
public class AmplitudeFitResult {

    private final double norm;
    private final double rss;
    private final double aic;
    private final double[] coefs;
    private final int k;
    private final int maxIndex;
    private final double maxValue;

    AmplitudeFitResult(final double norm, final double rss, final double aic, final double[] coefs, final int k, final int maxIndex, final double maxValue) {
        this.norm = norm;
        this.rss = rss;
        this.aic = aic;
        this.coefs = coefs;
        this.k = k;
        this.maxIndex = maxIndex;
        this.maxValue = maxValue;
    }

    /**
     * @return the norm
     */
    public double getNorm() {
        return norm;
    }

    /**
     * @return the rss
     */
    public double getRss() {
        return rss;
    }

    /**
     * @return the aic
     */
    public double getAic() {
        return aic;
    }

    /**
     * @return the coefs
     */
    public double[] getCoefs() {
        return coefs;
    }

    /**
     * @return the k
     */
    public int getK() {
        return k;
    }

    /**
     * @return the maxIndex
     */
    public int getMaxIndex() {
        return maxIndex;
    }

    /**
     * @return the maxValue
     */
    public double getMaxValue() {
        return maxValue;
    }

}
