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

import org.nmrfx.utilities.NMRFxColor;
import org.nmrfx.utilities.NvUtil;

import java.util.List;

/**
 * All atoms from the periodic table, declared specific order so that each enum value's ordinal matches with the periodic table's element number.
 */
public enum AtomProperty {
    X(0.8f, 0.0f),
    H(1.2f, "light green", 0.0f),
    He(1.4f, "pink", 0.0f),
    Li(1.82f, "firebrick", 0.0f),
    B(1.0f, "green", 0.0f),
    Be(1.0f, "white", 0.0f),
    C(1.7f, "dark gray", 0.0f),
    N(1.55f, "light blue", 0.0f),
    O(1.52f, "red", 0.0f),
    F(1.47f, "goldenrod", 0.0f),
    Ne(1.54f, "white", 0.0f),
    Na(2.27f, "blue", 0.0f),
    Mg(1.73f, "forest green", 0.0f),
    Al(1.0f, "dark gray", 0.0f),
    Si(2.1f, "goldenrod", 0.0f),
    P(1.8f, "orange", 0.0f),
    S(1.8f, 1.0f, 0.8f, 0.2f, 0.0f),
    Cl(1.75f, "green", 0.0f),
    Ar(1.88f, 0.0f),
    K(2.75f, 0.0f),
    Ca(1.0f, "dark gray", 0.0f),
    Sc(1.0f, 0.0f),
    Ti(1.0f, "dark gray", 0.0f),
    V(1.0f, 0.0f),
    Cr(1.0f, "dark gray", 0.0f),
    Mn(1.0f, "dark gray", 0.0f),
    Fe(1.0f, "orange", 0.0f),
    Co(1.0f, 0.0f),
    Ni(1.0f, "brown", 0.0f),
    Cu(1.0f, "brown", 0.0f),
    Zn(1.0f, "brown", 0.0f),
    Ga(1.0f, 0.0f),
    Ge(1.0f, 0.0f),
    As(1.0f, 0.0f),
    Se(1.0f, 0.0f),
    Br(1.0f, "brown", 0.0f),
    Kr(1.0f, 0.0f),
    Rb(1.0f, 0.0f),
    Sr(1.0f, 0.0f),
    Y(1.0f, 0.0f),
    Zr(1.0f, 0.0f),
    Nb(1.0f, 0.0f),
    Mo(1.0f, 0.0f),
    Tc(1.0f, 0.0f),
    Ru(1.0f, 0.0f),
    Rh(1.0f, 0.0f),
    Pd(1.0f, 0.0f),
    Ag(1.0f, "dark gray", 0.0f),
    Cd(1.0f, 0.0f),
    In(1.0f, 0.0f),
    Sn(1.0f, 0.0f),
    Sb(1.0f, 0.0f),
    Te(1.0f, 0.0f),
    I(1.0f, "purple", 0.0f),
    Xe(1.0f, 0.0f),
    Cs(1.0f, 0.0f),
    Ba(1.0f, "orange", 0.0f),
    La(1.0f, 0.0f),
    Ce(1.0f, 0.0f),
    Pr(1.0f, 0.0f),
    Nd(1.0f, 0.0f),
    Pm(1.0f, 0.0f),
    Sm(1.0f, 0.0f),
    Eu(1.0f, 0.0f),
    Gd(1.0f, 0.0f),
    Tb(1.0f, 0.0f),
    Dy(1.0f, 0.0f),
    Ho(1.0f, 0.0f),
    Er(1.0f, 0.0f),
    Tm(1.0f, 0.0f),
    Yb(1.0f, 0.0f),
    Lu(1.0f, 0.0f),
    Hf(1.0f, 0.0f),
    Ta(1.0f, 0.0f),
    W(1.0f, 0.0f),
    Re(1.0f, 0.0f),
    Os(1.0f, 0.0f),
    Ir(1.0f, 0.0f),
    Pt(1.0f, 0.0f),
    Au(1.0f, "goldenrod", 0.0f),
    Hg(1.0f, 0.0f),
    Tl(1.0f, 0.0f),
    Pb(1.0f, 0.0f),
    Bi(1.0f, 0.0f),
    Po(1.0f, 0.0f),
    At(1.0f, 0.0f),
    Rn(1.0f, 0.0f),
    Fr(1.0f, 0.0f),
    Ra(1.0f, 0.0f),
    Ac(1.0f, 0.0f),
    Th(1.0f, 0.0f),
    Pa(1.0f, 0.0f),
    U(1.0f, 0.0f),
    Np(1.0f, 0.0f),
    Pu(1.0f, 0.0f),
    Am(1.0f, 0.0f),
    Cm(1.0f, 0.0f),
    Bk(1.0f, 0.0f),
    Cf(1.0f, 0.0f),
    Es(1.0f, 0.0f),
    Fm(1.0f, 0.0f),
    Md(1.0f, 0.0f),
    No(1.0f, 0.0f),
    Lr(1.0f, 0.0f);

    private static final String GENERIC_COLOR = "gray";
    private static final List<AtomProperty> VALUES = List.of(values());

    private final float radius;
    private final float red;
    private final float green;
    private final float blue;
    private final float mass;

    AtomProperty(float radius, float red, float green, float blue, float mass) {
        this.radius = radius;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.mass = mass;
    }

    AtomProperty(float radius, String colorName, float mass) {
        this.radius = radius;

        NMRFxColor color = NvUtil.color(colorName);
        this.red = color.getRed() / 255.0f;
        this.green = color.getGreen() / 255.0f;
        this.blue = color.getBlue() / 255.0f;
        this.mass = mass;
    }


    AtomProperty(float radius, float mass) {
        this(radius, GENERIC_COLOR, mass);
    }

    public float getRed() {
        return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }

    public float getMass() {
        return mass;
    }

    public float getRadius() {
        return radius;
    }

    public static String getElementName(int eNum) {
        if (eNum < 1 || eNum >= VALUES.size()) {
            return null;
        }

        return VALUES.get(eNum).name();
    }

    public byte getElementNumber() {
        return (byte) ordinal();
    }

    public static byte getElementNumber(String elemName) {
        return get(elemName).getElementNumber();
    }

    public static AtomProperty get(String elemName) {
        try {
            return AtomProperty.valueOf(normalizeName(elemName));
        } catch (IllegalArgumentException e) {
            return X;
        }
    }

    public static AtomProperty get(int eNum) {
        if (eNum < 0 || eNum >= VALUES.size()) {
            return null;
        }

        return VALUES.get(eNum);
    }

    public static String normalizeName(String elemName) {
        StringBuilder builder = new StringBuilder();
        builder.append(Character.toUpperCase(elemName.charAt(0)));
        for (int i = 1; i < elemName.length(); i++) {
            builder.append(Character.toLowerCase(elemName.charAt(i)));
        }
        return builder.toString();
    }
}
