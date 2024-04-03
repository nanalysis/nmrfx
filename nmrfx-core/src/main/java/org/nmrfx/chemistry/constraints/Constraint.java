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

/**
 * @author brucejohnson
 */
public interface Constraint {

    enum GenTypes {

        MANUAL("manual", "Man") {
        },
        AUTOMATIC("automatic", "Auto") {
        },
        AUTOPLUS("autoplus", "AutoP") {
        },
        SEMIAUTOMATIC("semiautomatic", "Semi") {
        };
        private final String description;
        private final String shortDesc;

        public String getDescription() {
            return description;
        }

        public String getShortDescription() {
            return shortDesc;
        }

        GenTypes(String description, String shortDesc) {
            this.description = description;
            this.shortDesc = shortDesc;
        }
    }

    public int getID();

    public boolean isUserActive();

    public DistanceStat getStat();

    public double getValue();

    public String toSTARString();
}
