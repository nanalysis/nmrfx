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
 * PeakEvent.java
 *
 * Created on December 13, 2006, 11:39 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.nmrfx.peaks.events;

/**
 * @author brucejohnson
 */
public class PeakCountEvent extends PeakEvent {
    final int size;

    /**
     * Creates a new instance of PeakCountEvent
     *
     * @param object event object
     */
    public PeakCountEvent(Object object, int size) {
        super(object);
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}
