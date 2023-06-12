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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All atoms from the periodic table, declared specific order so that each enum value's ordinal matches with the periodic table's element number.
 */
public enum AtomProperty {
    X("X", 0.8f, 0.0f),
    H("H", 1.2f, "light green", 0.0f),
    He("He", 1.4f, "pink", 0.0f),
    Li("Li", 1.82f, "firebrick", 0.0f),
    B("B", 1.0f, "green", 0.0f),
    Be("Be", 1.0f, "white", 0.0f),
    C("C", 1.7f, "dark gray", 0.0f),
    N("N", 1.55f, "light blue", 0.0f),
    O("O", 1.52f, "red", 0.0f),
    F("F", 1.47f, "goldenrod", 0.0f),
    Ne("Ne", 1.54f, "white", 0.0f),
    Na("Na", 2.27f, "blue", 0.0f),
    Mg("Mg", 1.73f, "forest green", 0.0f),
    Al("Al", 1.0f, "dark gray", 0.0f),
    Si("Si", 2.1f, "goldenrod", 0.0f),
    P("P", 1.8f, "orange", 0.0f),
    S("S", 1.8f, 1.0f, 0.8f, 0.2f, 0.0f),
    Cl("Cl", 1.75f, "green", 0.0f),
    Ar("Ar", 1.88f, 0.0f),
    K("K", 2.75f, 0.0f),
    Ca("Ca", 1.0f, "dark gray", 0.0f),
    Sc("Sc", 1.0f, 0.0f),
    Ti("Ti", 1.0f, "dark gray", 0.0f),
    V("V", 1.0f, 0.0f),
    Cr("Cr", 1.0f, "dark gray", 0.0f),
    Mn("Mn", 1.0f, "dark gray", 0.0f),
    Fe("Fe", 1.0f, "orange", 0.0f),
    Co("Co", 1.0f, 0.0f),
    Ni("Ni", 1.0f, "brown", 0.0f),
    Cu("Cu", 1.0f, "brown", 0.0f),
    Zn("Zn", 1.0f, "brown", 0.0f),
    Ga("Ga", 1.0f, 0.0f),
    Ge("Ge", 1.0f, 0.0f),
    As("As", 1.0f, 0.0f),
    Se("Se", 1.0f, 0.0f),
    Br("Br", 1.0f, "brown", 0.0f),
    Kr("Kr", 1.0f, 0.0f),
    Rb("Rb", 1.0f, 0.0f),
    Sr("Sr", 1.0f, 0.0f),
    Y("Y", 1.0f, 0.0f),
    Zr("Zr", 1.0f, 0.0f),
    Nb("Nb", 1.0f, 0.0f),
    Mo("Mo", 1.0f, 0.0f),
    Tc("Tc", 1.0f, 0.0f),
    Ru("Ru", 1.0f, 0.0f),
    Rh("Rh", 1.0f, 0.0f),
    Pd("Pd", 1.0f, 0.0f),
    Ag("Ag", 1.0f, "dark gray", 0.0f),
    Cd("Cd", 1.0f, 0.0f),
    In("In", 1.0f, 0.0f),
    Sn("Sn", 1.0f, 0.0f),
    Sb("Sb", 1.0f, 0.0f),
    Te("Te", 1.0f, 0.0f),
    I("I", 1.0f, "purple", 0.0f),
    Xe("Xe", 1.0f, 0.0f),
    Cs("Cs", 1.0f, 0.0f),
    Ba("Ba", 1.0f, "orange", 0.0f),
    La("La", 1.0f, 0.0f),
    Ce("Ce", 1.0f, 0.0f),
    Pr("Pr", 1.0f, 0.0f),
    Nd("Nd", 1.0f, 0.0f),
    Pm("Pm", 1.0f, 0.0f),
    Sm("Sm", 1.0f, 0.0f),
    Eu("Eu", 1.0f, 0.0f),
    Gd("Gd", 1.0f, 0.0f),
    Tb("Tb", 1.0f, 0.0f),
    Dy("Dy", 1.0f, 0.0f),
    Ho("Ho", 1.0f, 0.0f),
    Er("Er", 1.0f, 0.0f),
    Tm("Tm", 1.0f, 0.0f),
    Yb("Yb", 1.0f, 0.0f),
    Lu("Lu", 1.0f, 0.0f),
    Hf("Hf", 1.0f, 0.0f),
    Ta("Ta", 1.0f, 0.0f),
    W("W", 1.0f, 0.0f),
    Re("Re", 1.0f, 0.0f),
    Os("Os", 1.0f, 0.0f),
    Ir("Ir", 1.0f, 0.0f),
    Pt("Pt", 1.0f, 0.0f),
    Au("Au", 1.0f, "goldenrod", 0.0f),
    Hg("Hg", 1.0f, 0.0f),
    Tl("Tl", 1.0f, 0.0f),
    Pb("Pb", 1.0f, 0.0f),
    Bi("Bi", 1.0f, 0.0f),
    Po("Po", 1.0f, 0.0f),
    At("At", 1.0f, 0.0f),
    Rn("Rn", 1.0f, 0.0f),
    Fr("Fr", 1.0f, 0.0f),
    Ra("Ra", 1.0f, 0.0f),
    Ac("Ac", 1.0f, 0.0f),
    Th("Th", 1.0f, 0.0f),
    Pa("Pa", 1.0f, 0.0f),
    U("U", 1.0f, 0.0f),
    Np("Np", 1.0f, 0.0f),
    Pu("Pu", 1.0f, 0.0f),
    Am("Am", 1.0f, 0.0f),
    Cm("Cm", 1.0f, 0.0f),
    Bk("Bk", 1.0f, 0.0f),
    Cf("Cf", 1.0f, 0.0f),
    Es("Es", 1.0f, 0.0f),
    Fm("Fm", 1.0f, 0.0f),
    Md("Md", 1.0f, 0.0f),
    No("No", 1.0f, 0.0f),
    Lr("Lr", 1.0f, 0.0f);

    private static final String GENERIC_COLOR = "gray";
    private static final List<AtomProperty> VALUES = List.of(values());
    private static final Map<String, AtomProperty> byName = new HashMap<>();

    static {
        for (AtomProperty atomProp : VALUES) {
            byName.put(atomProp.name, atomProp);
        }
    }

    private final String name;
    private final float radius;
    private final float red;
    private final float green;
    private final float blue;
    private final float mass;

    AtomProperty(String name, float radius, float red, float green, float blue, float mass) {
        this.name = name;
        this.radius = radius;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.mass = mass;
    }

    AtomProperty(String name, float radius, String colorName, float mass) {
        this.name = name;
        this.radius = radius;

        NMRFxColor color = NvUtil.color(colorName);
        this.red = color.getRed() / 255.0f;
        this.green = color.getGreen() / 255.0f;
        this.blue = color.getBlue() / 255.0f;
        this.mass = mass;
    }


    AtomProperty(String name, float radius, float mass) {
        this(name, radius, GENERIC_COLOR, mass);
    }

    @Override
    public String toString() {
        return name;
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

        return VALUES.get(eNum).name;
    }

    public byte getElementNumber() {
        return (byte) ordinal();
    }

    public static byte getElementNumber(String elemName) {
        elemName = elemName.toUpperCase();
        if (elemName.length() > 1) {
            elemName = elemName.replace(elemName.substring(1), elemName.substring(1).toLowerCase());
        }
        AtomProperty atomProp = byName.get(elemName);

        if (atomProp != null) {
            return (byte) atomProp.ordinal();
        } else {
            return 0;
        }
    }

    public static AtomProperty get(String elemName) {
        elemName = elemName.toUpperCase();
        if (elemName.length() > 1) {
            elemName = elemName.replace(elemName.substring(1), elemName.substring(1).toLowerCase());
        }
        AtomProperty atomProp = byName.get(elemName);

        return atomProp == null ? X : atomProp;
    }

    public static AtomProperty get(int eNum) {
        if (eNum < 0 || eNum >= VALUES.size()) {
            return null;
        }

        return VALUES.get(eNum);
    }
}
