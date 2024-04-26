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
public class NoeCalibrationBin extends NoeCalibration {

    final double[] bins;  // 2.2 100 3.0 20 5.0 
    final double lower;

    public static String validateBins(final double[] checkBins) {
        String result = "OK";
        int binLength = checkBins.length;
        if ((binLength % 2) != 1) {
            result = "Must have odd number of values in bin array";
        } else if (binLength < 3) {
            result = "Must have at least 3 values bin array";
        } else {
            for (int i = 2; i < binLength; i += 2) {
                if (checkBins[i] < checkBins[i - 2]) {
                    result = "Bin array distances must be monotonic increasing";
                    break;
                }
            }
            if (binLength > 3) {
                for (int i = 3; i < binLength; i += 2) {
                    if (checkBins[i] > checkBins[i - 2]) {
                        result = "Bin array intensities must be monotonic decreasing";
                        break;
                    }
                }
            }
        }
        return result;
    }

    NoeCalibrationBin(final String measurementMode, final double lower, final double[] bins, final boolean removeRedundant) {
        String validationString = validateBins(bins);
        if (!validationString.equals("OK")) {
            throw new IllegalArgumentException(validationString);
        }
        this.mMode = MeasurementMode.select(measurementMode);
        this.lower = lower;
        this.bins = bins.clone();
        this.removeRedundant = removeRedundant;
    }

    public void calibrate(Noe noe) {
        // fixme  what about negative NOE peaks?
        if (!noe.isActive()) {
            return;
        }
        // 2.2 100 3.0 20 5.0 
        double bound = bins[bins.length - 1];
        double intensity = Math.abs(mMode.measure(noe));
        double I = intensity / noe.getScale() / noe.getAtomScale();
        if (I > bins[1]) {
            bound = bins[0];
        }
        for (int i = 1; i < bins.length; i += 2) {
            if (I > bins[i]) {
                bound = bins[i - 1];
                break;
            }
        }
        noe.setUpper(bound);
        noe.setLower(lower);
        noe.setTarget((noe.getLower() + noe.getUpper()) / 2.0);
    }
}
