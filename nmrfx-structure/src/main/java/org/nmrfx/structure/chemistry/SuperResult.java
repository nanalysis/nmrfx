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

package org.nmrfx.structure.chemistry;

public class SuperResult {

    private final int iFix;
    private final int iMove;
    private final double rms;

    SuperResult(final int iFix, final int iMove, final double rms) {
        this.iFix = iFix;
        this.iMove = iMove;
        this.rms = rms;
    }

    /**
     * @return the iFix
     */
    public int getiFix() {
        return iFix;
    }

    /**
     * @return the iMove
     */
    public int getiMove() {
        return iMove;
    }

    /**
     * @return the rms
     */
    public double getRms() {
        return rms;
    }
}
