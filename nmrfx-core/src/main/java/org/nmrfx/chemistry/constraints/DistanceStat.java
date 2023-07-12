/*
 * NMRFx Structure : A Program for Calculating Structures
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

package org.nmrfx.chemistry.constraints;

import java.util.BitSet;

public class DistanceStat {

    private final double min;
    private final double max;
    private final double mean;
    private final double stdDev;
    private final BitSet violStructures;
    private final double fracInBound;

    DistanceStat() {
        min = 0.0;
        max = 0.0;
        mean = 0.0;
        stdDev = 0.0;
        violStructures = null;
        fracInBound = 0.0;
    }

    public DistanceStat(final double min, final double max, final double mean, final double stdDev, final double fracInBound, final BitSet violStructures) {
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.stdDev = stdDev;
        this.violStructures = violStructures;
        this.fracInBound = fracInBound;
    }

    /**
     * @return the min
     */
    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getMean() {
        return mean;
    }

    public double getStdDev() {
        return stdDev;
    }

    public BitSet getViolStructures() {
        return violStructures;
    }

    /**
     * @return the fracInBound
     */
    public double getFracInBound() {
        return fracInBound;
    }
}
