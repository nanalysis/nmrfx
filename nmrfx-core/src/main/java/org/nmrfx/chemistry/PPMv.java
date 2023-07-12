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
package org.nmrfx.chemistry;

import java.io.Serializable;

public class PPMv implements Serializable {

    private double value;
    private double error;
    private short ambigCode;
    private boolean valid;

    public PPMv(double ppm) {
        value = ppm;
        error = 0;
        ambigCode = -1;
        valid = false;
    }

    public short getAmbigCode() {

        return ambigCode;
    }

    public void setAmbigCode(int ambigCode) {
        this.ambigCode = (short) ambigCode;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid, Atom atom) {
        if (ambigCode == -1) {
            ambigCode = (short) atom.getBMRBAmbiguity();
        }
        this.valid = valid;
    }

    /**
     * @return the value
     */
    public double getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(double value) {
        this.value = value;
    }

    /**
     * @return the error
     */
    public double getError() {
        return error;
    }

    /**
     * @param error the error to set
     */
    public void setError(double error) {
        this.error = error;
    }
}
