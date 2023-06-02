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
public abstract class NoeCalibration {

    enum MeasurementMode {

        INTENSITY("intensity") {
            @Override
            public double measure(Noe noe) {
                return noe.getIntensity();
            }
        },
        VOLUME("volume") {
            @Override
            public double measure(Noe noe) {
                return noe.getVolume();
            }
        };
        private final String description;

        MeasurementMode(String description) {
            this.description = description;

        }

        static MeasurementMode select(String name) {
            if (name.startsWith("vol")) {
                return VOLUME;
            } else {
                return INTENSITY;
            }
        }

        abstract double measure(Noe noe);
    }

    MeasurementMode mMode;
    boolean removeRedundant = true;

    public abstract void calibrate(Noe noe);

    public boolean removeRedundant() {
        return removeRedundant;
    }
}
