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

import org.nmrfx.annotations.PluginAPI;

import java.io.Serializable;

@PluginAPI("residuegen")
public class Bond implements IBond, Serializable {

    static final public int SELECT = 0;
    static final public int DISPLAY = 1;
    static final public int DEACTIVATE = 2;
    static final public int STEREO_BOND_UP = 10;
    static final public int STEREO_BOND_DOWN = 11;
    static final public int STEREO_BOND_EITHER = 12;
    static final public int STEREO_BOND_CROSS = 13;
    static final public int VISITED = 0;
    static final public int ISAROMATIC = 1;
    boolean[] properties;
    public float radius = 0.3f;
    public float red = 1.0f;
    public float green = 0.0f;
    public float blue = 0.0f;
    public Order order = Order.SINGLE;
    public int stereo = 0;
    public Atom begin;
    public Atom end;
    boolean[] flags = new boolean[2];
    boolean ringClosure = false;

    public Bond(Atom begin, Atom end) {
        this(begin, end, Order.SINGLE);
    }

    public Bond(Atom begin, Atom end, Order bondOrder) {
        order = bondOrder;
        this.begin = begin;
        this.end = end;
        radius = 0.3f;
        properties = new boolean[16];
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        if (begin != null) {
            sBuilder.append(begin.getFullName());
        } else {
            sBuilder.append('-');
        }
        if (end != null) {
            sBuilder.append('-');
            sBuilder.append(end.getFullName());
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

    @Override
    public Atom getAtom(int index) {
        return index == 0 ? begin : end;
    }

    public Atom getConnectedAtom(Atom atom) {
        if (atom == begin) {
            return end;
        } else if (atom == end) {
            return begin;
        } else {
            return null;
        }
    }

    @Override
    public void setFlag(int flag, boolean state) throws IllegalArgumentException {
        if (flag > flags.length) {
            throw new IllegalArgumentException("Invalid flag");
        }
        flags[flag] = state;
    }

    @Override
    public boolean getFlag(int flag) throws IllegalArgumentException {
        if (flag > flags.length) {
            throw new IllegalArgumentException("Invalid flag");
        }
        return flags[flag];
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

    @Override
    public IAtom getConnectedAtom(IAtom atom) {
        IAtom result = null;
        if (end == atom) {
            result = begin;
        }
        if (begin == atom) {
            result = end;
        }
        return result;
    }

    @Override
    public Order getOrder() {
        return order;
    }

    public boolean isRingClosure() {
        return ringClosure;
    }

    public void setRingClosure(boolean value) {
        ringClosure = value;
    }
}
