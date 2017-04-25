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

import java.io.*;

public class Bond implements Serializable {

    static final public int SELECT = 0;
    static final public int DISPLAY = 1;
    static final public int STEREO_BOND_UP = 10;
    static final public int STEREO_BOND_DOWN = 11;
    static final public int STEREO_BOND_EITHER = 12;
    boolean[] properties;
    public float radius = 0.3f;
    public float red = 1.0f;
    public float green = 0.0f;
    public float blue = 0.0f;
    public int order = 1;
    public int stereo = 0;
    public Atom begin;
    public Atom end;

    public Bond(Atom begin, Atom end) {
        this.begin = begin;
        this.end = end;
        radius = 0.3f;
        properties = new boolean[16];
    }

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        if (begin != null) {
            sBuilder.append(begin.getName());
        } else {
            sBuilder.append('-');
        }
        if (end != null) {
            sBuilder.append('-');
            sBuilder.append(end.getName());
        } else {
            sBuilder.append('-');
        }
        sBuilder.append(' ');
        sBuilder.append(' ');
        sBuilder.append(order);
        sBuilder.append(' ');
        sBuilder.append(stereo);
        return sBuilder.toString();
    }

    public Atom getBeginAtom() {
        return begin;
    }

    public Atom getEndAtom() {
        return end;
    }

    public void setProperty(int propIndex) {
        if (properties.length <= propIndex) {
            return;
        }

        properties[propIndex] = true;
    }

    public void unsetProperty(int propIndex) {
        if (properties.length <= propIndex) {
            return;
        }

        properties[propIndex] = false;
    }

    public boolean getProperty(int propIndex) {
        if (properties.length <= propIndex) {
            return (false);
        } else {
            return (properties[propIndex]);
        }
    }
}
