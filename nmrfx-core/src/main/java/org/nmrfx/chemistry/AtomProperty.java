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
    H(1.2f, 0.0f, "light green"),
    He(1.4f, 0.0f, "pink"),
    Li(1.82f, 0.0f, "firebrick"),
    B(1.0f, 0.0f, "green"),
    Be(1.0f, 0.0f, "white"),
    C(1.7f, 0.0f, "dark gray"),
    N(1.55f, 0.0f, "light blue"),
    O(1.52f, 0.0f, "red"),
    F(1.47f, 0.0f, "goldenrod"),
    Ne(1.54f, 0.0f, "white"),
    Na(2.27f, 0.0f, "blue"),
    Mg(1.73f, 0.0f, "forest green"),
    Al(1.0f, 0.0f, "dark gray"),
    Si(2.1f, 0.0f, "goldenrod"),
    P(1.8f, 0.0f, "orange"),
    S(1.8f, 0.0f, 1.0f, 0.8f, 0.2f),
    Cl(1.75f, 0.0f, "green"),
    Ar(1.88f, 0.0f),
    K(2.75f, 0.0f),
    Ca(1.0f, 0.0f, "dark gray"),
    Sc(1.0f, 0.0f),
    Ti(1.0f, 0.0f, "dark gray"),
    V(1.0f, 0.0f),
    Cr(1.0f, 0.0f, "dark gray"),
    Mn(1.0f, 0.0f, "dark gray"),
    Fe(1.0f, 0.0f, "orange"),
    Co(1.0f, 0.0f),
    Ni(1.0f, 0.0f, "brown"),
    Cu(1.0f, 0.0f, "brown"),
    Zn(1.0f, 0.0f, "brown"),
    Ga(1.0f, 0.0f),
    Ge(1.0f, 0.0f),
    As(1.0f, 0.0f),
    Se(1.0f, 0.0f),
    Br(1.0f, 0.0f, "brown"),
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
    Ag(1.0f, 0.0f, "dark gray"),
    Cd(1.0f, 0.0f),
    In(1.0f, 0.0f),
    Sn(1.0f, 0.0f),
    Sb(1.0f, 0.0f),
    Te(1.0f, 0.0f),
    I(1.0f, 0.0f, "purple"),
    Xe(1.0f, 0.0f),
    Cs(1.0f, 0.0f),
    Ba(1.0f, 0.0f, "orange"),
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
    Au(1.0f, 0.0f, "goldenrod"),
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

    AtomProperty(float radius, float mass, float red, float green, float blue) {
        this.radius = radius;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.mass = mass;
    }

    AtomProperty(float radius, float mass, String colorName) {
        this.radius = radius;

        NMRFxColor color = NvUtil.color(colorName);
        this.red = color.getRed() / 255.0f;
        this.green = color.getGreen() / 255.0f;
        this.blue = color.getBlue() / 255.0f;
        this.mass = mass;
    }

    AtomProperty(float radius, float mass) {
        this(radius, mass, GENERIC_COLOR);
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
            return "X";
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
