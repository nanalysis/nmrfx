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

package org.nmrfx.structure.chemistry.constraints;

/**
 *
 * @author brucejohnson
 */
public class NoeCalibrationExp extends NoeCalibration {

    final double lower;
    final double referenceValue;
    final double referenceDist;
    final double expValue;
    final double minBound;
    final double maxBound;
    final double fError;
    static final double floor = 1.0e-16;

    NoeCalibrationExp(final String measurementMode, final double lower, final double referenceValue, final double referenceDist, final double expValue, final double minBound, final double maxBound, final double fError, final boolean removeRedundant) {
        this.mMode = MeasurementMode.select(measurementMode);
        this.referenceValue = referenceValue;
        this.referenceDist = referenceDist;
        this.expValue = expValue;
        this.maxBound = maxBound;
        this.minBound = minBound;
        this.lower = lower;
        this.fError = fError;
        this.removeRedundant = removeRedundant;
        System.out.println(referenceValue + " " + referenceDist + " " + expValue + " " + minBound + " " + maxBound);
    }

    public void calibrate(Noe noe) {
        // fixme  what about negative NOE peaks?
        if (!noe.isActive()) {
            return;
        }
        double C = referenceValue * Math.pow(referenceDist, expValue);
        double bound = maxBound;
        double target = maxBound;
        double intensity = Math.abs(mMode.measure(noe));
        if (intensity > floor) {
            double I = intensity / noe.getScale() / noe.atomScale / C;
            if (I > 0.0) {
                target = Math.pow(I, -1.0 / expValue);
            }
            target = target > maxBound ? maxBound : target;
            target = target < minBound ? minBound : target;

            bound = target + target * target * fError;
            bound = bound > maxBound ? maxBound : bound;
            bound = bound < minBound ? minBound : bound;
        }
        noe.target = target;
        noe.setUpper(bound);
        noe.setLower(lower);
    }
}
