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

package org.nmrfx.processor.gui.spectra;

//TODO to move closer to Axis, because Axis is actually the implementation, even if hidden behind NMRAxis
//TODO see if regrouping Axis / NMRAxis would be a problem
public interface NMRAxisLimits {

    double getLowerBound();

    double getUpperBound();

    boolean getReverse();

    void setReverse(boolean state);

    void setLabel(String label);

    String getLabel();

    double getDisplayPosition(Number value);

}
