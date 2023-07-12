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

package org.nmrfx.structure.noe;

import org.nmrfx.chemistry.constraints.Noe;

/**
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
    static final double INTENSITY_FLOOR = 1.0e-16;

    public NoeCalibrationExp(final String measurementMode, final double lower, final double referenceValue, final double referenceDist, final double expValue, final double minBound, final double maxBound, final double fError, final boolean removeRedundant) {
        this.mMode = MeasurementMode.select(measurementMode);
        this.referenceValue = referenceValue;
        this.referenceDist = referenceDist;
        this.expValue = expValue;
        this.maxBound = maxBound;
        this.minBound = minBound;
        this.lower = lower;
        this.fError = fError;
        this.removeRedundant = removeRedundant;
    }

    public void calibrate(Noe noe) {
        double C = referenceValue * Math.pow(referenceDist, expValue);
        double bound = maxBound;
        double target = maxBound;
        double intensity = Math.abs(mMode.measure(noe));
        if (intensity > INTENSITY_FLOOR) {
            double I = intensity / noe.getScale() / noe.atomScale / C;
            if (I > 0.0) {
                target = Math.pow(I, -1.0 / expValue);
            }
            target = Math.min(target, maxBound);
            target = Math.max(target, minBound);

            bound = target + target * target * fError;
            bound = Math.min(bound, maxBound);
            bound = Math.max(bound, minBound);
        }
        noe.setTarget(target);
        noe.setUpper(bound);
        noe.setLower(lower);
    }
}
