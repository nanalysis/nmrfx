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

/*
 * AtomColors.java
 *
 * Created on September 6, 2003, 1:23 PM
 */
package org.nmrfx.chemistry;

/**
 * @author Johnbruc
 */

/* fixme  following neither complete or correct */
public final class AtomColors {

    private final static float[][] atomColors = {
            {0.2f, 0.2f, 0.2f}, // 0 none
            {0.8f, 0.8f, 0.8f}, // 1 H white
            {0.0f, 0.0f, 0.0f}, // 2 He
            {0.0f, 0.0f, 0.0f}, // 3 Li tan
            {0.0f, 1.0f, 0.0f}, // 4 Be green
            {0.0f, 0.0f, 0.0f}, // 5 B tan
            {0.5f, 0.5f, 0.5f}, // 6 C grey
            {0.2f, 0.2f, 0.8f}, // 7 N blue-grey
            {1.0f, 0.0f, 0.0f}, // 8 O red
            {0.0f, 1.0f, 0.0f}, // 9 F green
            {0.0f, 0.0f, 0.0f}, // 10 Ne
            {1.0f, 1.0f, 0.0f}, // 11 Na yellow
            {0.0f, 0.0f, 1.0f}, // 12 Mg blue
            {0.8f, 0.0f, 1.0f}, // 13 Al purple
            {0.4f, 0.4f, 0.4f}, // 14 Si grey
            {0.0f, 0.0f, 0.0f}, // 15 P tan
            {1.0f, 1.0f, 0.0f}, // 16 S yellow
            {0.0f, 0.0f, 0.0f}, // 17 Cl tan
            {0.0f, 0.0f, 0.0f}, // 18 Ar
            {0.0f, 0.0f, 0.0f}, // 19 K red
            {0.0f, 0.0f, 0.0f}, // 20 Ca
            {0.0f, 0.0f, 0.0f}, // 21 Sc
    };

    /**
     * Creates a new instance of AtomColors
     */
    public AtomColors() {
    }

    public static float[] getAtomColor(int i) {
        if ((i >= 0) && (i < atomColors.length)) {
            return atomColors[i];
        } else {
            return atomColors[0];
        }
    }
}
