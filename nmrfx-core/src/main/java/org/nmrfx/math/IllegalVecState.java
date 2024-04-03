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
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.math;

/**
 * Thrown when a Vec should be real / complex or use the Apache formatting.
 *
 * @author johnsonb
 */
public class IllegalVecState extends VecException {

    /**
     * @param isComplex       The Vec is complex
     * @param isApache        The Vec is using Apache formatting.
     * @param shouldBeComplex The Vec should be Complex
     * @param shouldBeApache  The Vec should be using the Apache formatting.
     */
    public IllegalVecState(boolean isComplex, boolean isApache,
                           boolean shouldBeComplex, boolean shouldBeApache) {
        super("Illegal Vec State: complex: " + isComplex + ", apache: " + isApache + ". Required: " + "complex: " + shouldBeComplex + ", apache: " + shouldBeApache + ".");
    }

    /**
     * Constructor where the Vec could either be Apache or not (this is when the Vec is real -- only the rvec can be
     * accessed).
     *
     * @param isComplex       The Vec is complex
     * @param shouldBeComplex The Vec should be Complex
     */
    public IllegalVecState(boolean isComplex, boolean shouldBeComplex) {
        super("Illegal Vec State: complex: " + isComplex + ". Required: " + "complex: " + shouldBeComplex + ".");
    }

}
