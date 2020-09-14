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
 * AtomProperty.java
 *
 * Created on April 12, 2004, 11:02 PM
 */
package org.nmrfx.structure.chemistry;

import org.nmrfx.structure.utilities.NvUtil;
import java.awt.Color;
import java.util.*;

/**
 *
 * @author brucejohnson
 */
public class AtomProperty {

    static final HashMap map = new HashMap();
    static int nextAnum = 0;
    private static final Color GENERIC_COLOR = new Color(1.0f, 0.1f, 0.6f);
    public static final AtomProperty X = new AtomProperty("X", 0.8f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty H = new AtomProperty("H", 1.2f, "light green",
            0.0f);
    public static final AtomProperty He = new AtomProperty("He", 1.4f, "pink",
            0.0f);
    public static final AtomProperty Li = new AtomProperty("Li", 1.82f,
            "firebrick", 0.0f);
    public static final AtomProperty B = new AtomProperty("B", 1.0f, "green",
            0.0f);
    public static final AtomProperty Be = new AtomProperty("Be", 1.0f, "white",
            0.0f);
    public static final AtomProperty C = new AtomProperty("C", 1.7f,
            "dark gray", 0.0f);
    public static final AtomProperty N = new AtomProperty("N", 1.55f,
            "light blue", 0.0f);
    public static final AtomProperty O = new AtomProperty("O", 1.52f, "red", 0.0f);
    public static final AtomProperty F = new AtomProperty("F", 1.47f,
            "goldenrod", 0.0f);
    public static final AtomProperty Ne = new AtomProperty("Ne", 1.54f, "white",
            0.0f);
    public static final AtomProperty Na = new AtomProperty("Na", 2.27f, "blue",
            0.0f);
    public static final AtomProperty Mg = new AtomProperty("Mg", 1.73f,
            "forest green", 0.0f);
    public static final AtomProperty Al = new AtomProperty("Al", 1.0f,
            "dark gray", 0.0f);
    public static final AtomProperty Si = new AtomProperty("Si", 2.1f,
            "goldenrod", 0.0f);
    public static final AtomProperty P = new AtomProperty("P", 1.8f, "orange",
            0.0f);
    public static final AtomProperty S = new AtomProperty("S", 1.8f, 1.0f,
            0.8f, 0.2f, 0.0f);
    public static final AtomProperty Cl = new AtomProperty("Cl", 1.75f, "green",
            0.0f);
    public static final AtomProperty Ar = new AtomProperty("Ar", 1.88f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty K = new AtomProperty("K", 2.75f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ca = new AtomProperty("Ca", 1.0f,
            "dark gray", 0.0f);
    public static final AtomProperty Sc = new AtomProperty("Sc", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ti = new AtomProperty("Ti", 1.0f,
            "dark gray", 0.0f);
    public static final AtomProperty V = new AtomProperty("V", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Cr = new AtomProperty("Cr", 1.0f,
            "dark gray", 0.0f);
    public static final AtomProperty Mn = new AtomProperty("Mn", 1.0f,
            "dark gray", 0.0f);
    public static final AtomProperty Fe = new AtomProperty("Fe", 1.0f,
            "orange", 0.0f);
    public static final AtomProperty Co = new AtomProperty("Co", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ni = new AtomProperty("Ni", 1.0f, "brown",
            0.0f);
    public static final AtomProperty Cu = new AtomProperty("Cu", 1.0f, "brown",
            0.0f);
    public static final AtomProperty Zn = new AtomProperty("Zn", 1.0f, "brown",
            0.0f);
    public static final AtomProperty Ga = new AtomProperty("Ga", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ge = new AtomProperty("Ge", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty As = new AtomProperty("As", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Se = new AtomProperty("Se", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Br = new AtomProperty("Br", 1.0f, "brown",
            0.0f);
    public static final AtomProperty Kr = new AtomProperty("Kr", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Rb = new AtomProperty("Rb", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Sr = new AtomProperty("Sr", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Y = new AtomProperty("Y", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Zr = new AtomProperty("Zr", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Nb = new AtomProperty("Nb", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Mo = new AtomProperty("Mo", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Tc = new AtomProperty("Tc", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ru = new AtomProperty("Ru", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Rh = new AtomProperty("Rh", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Pd = new AtomProperty("Pd", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ag = new AtomProperty("Ag", 1.0f,
            "dark gray", 0.0f);
    public static final AtomProperty Cd = new AtomProperty("Cd", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty In = new AtomProperty("In", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Sn = new AtomProperty("Sn", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Sb = new AtomProperty("Sb", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Te = new AtomProperty("Te", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty I = new AtomProperty("I", 1.0f, "purple",
            0.0f);
    public static final AtomProperty Xe = new AtomProperty("Xe", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Cs = new AtomProperty("Cs", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ba = new AtomProperty("Ba", 1.0f,
            "orange", 0.0f);
    public static final AtomProperty La = new AtomProperty("La", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ce = new AtomProperty("Ce", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Pr = new AtomProperty("Pr", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Nd = new AtomProperty("Nd", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Pm = new AtomProperty("Pm", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Sm = new AtomProperty("Sm", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Eu = new AtomProperty("Eu", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Gd = new AtomProperty("Gd", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Tb = new AtomProperty("Tb", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Dy = new AtomProperty("Dy", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ho = new AtomProperty("Ho", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Er = new AtomProperty("Er", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Tm = new AtomProperty("Tm", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Yb = new AtomProperty("Yb", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Lu = new AtomProperty("Lu", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Hf = new AtomProperty("Hf", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ta = new AtomProperty("Ta", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty W = new AtomProperty("W", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Re = new AtomProperty("Re", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Os = new AtomProperty("Os", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ir = new AtomProperty("Ir", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Pt = new AtomProperty("Pt", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Au = new AtomProperty("Au", 1.0f,
            "goldenrod", 0.0f);
    public static final AtomProperty Hg = new AtomProperty("Hg", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Tl = new AtomProperty("Tl", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Pb = new AtomProperty("Pb", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Bi = new AtomProperty("Bi", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Po = new AtomProperty("Po", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty At = new AtomProperty("At", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Rn = new AtomProperty("Rn", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Fr = new AtomProperty("Fr", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ra = new AtomProperty("Ra", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Ac = new AtomProperty("Ac", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Th = new AtomProperty("Th", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Pa = new AtomProperty("Pa", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty U = new AtomProperty("U", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Np = new AtomProperty("Np", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Pu = new AtomProperty("Pu", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Am = new AtomProperty("Am", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Cm = new AtomProperty("Cm", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Bk = new AtomProperty("Bk", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Cf = new AtomProperty("Cf", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Es = new AtomProperty("Es", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Fm = new AtomProperty("Fm", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Md = new AtomProperty("Md", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty No = new AtomProperty("No", 1.0f,
            GENERIC_COLOR, 0.0f);
    public static final AtomProperty Lr = new AtomProperty("Lr", 1.0f,
            GENERIC_COLOR, 0.0f);
    private static final AtomProperty[] PRIVATE_VALUES = {
        X, H, He, Li, Be, B, C, N, O, F, Ne, Na, Mg, Al, Si, P, S, Cl, Ar, K, Ca,
        Sc, Ti, V, Cr, Mn, Fe, Co, Ni, Cu, Zn, Ga, Ge, As, Se, Br, Kr, Rb, Sr, Y,
        Zr, Nb, Mo, Tc, Ru, Rh, Pd, Ag, Cd, In, Sn, Sb, Te, I, Xe, Cs, Ba, La,
        Ce, Pr, Nd, Pm, Sm, Eu, Gd, Tb, Dy, Ho, Er, Tm, Yb, Lu, Hf, Ta, W, Re,
        Os, Ir, Pt, Au, Hg, Tl, Pb, Bi, Po, At, Rn, Fr, Ra, Ac, Th, Pa, U, Np,
        Pu, Am, Cm, Bk, Cf, Es, Fm, Md, No, Lr
    };
    public static final List VALUES = Collections.unmodifiableList(Arrays.asList(
            PRIVATE_VALUES));

    static {
        for (Iterator iter = VALUES.iterator(); iter.hasNext();) {
            AtomProperty atomProp = (AtomProperty) iter.next();
            map.put(atomProp.name, atomProp);
        }
    }
    String name = "";
    int aNum = 0;
    float radius = 0.6f;
    float red = 1.0f;
    float green = 0.1f;
    float blue = 0.7f;
    float mass = 0.0f;

    /**
     * Creates a new instance of AtomProperty
     */
    private AtomProperty(String name, float radius, float red, float green,
            float blue, float mass) {
        this.name = name;
        this.aNum = nextAnum++;
        this.radius = radius;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.mass = mass;
    }

    private AtomProperty(String name, float radius, String colorName, float mass) {
        this.name = name;
        this.aNum = nextAnum++;
        this.radius = radius;

        Color color = NvUtil.color(colorName);
        this.red = color.getRed() / 255.0f;
        this.green = color.getGreen() / 255.0f;
        this.blue = color.getBlue() / 255.0f;
        this.mass = mass;
    }

    private AtomProperty(String name, float radius, Color color, float mass) {
        this.name = name;
        this.aNum = nextAnum++;
        this.radius = radius;
        this.red = color.getRed() / 255.0f;
        this.green = color.getGreen() / 255.0f;
        this.blue = color.getBlue() / 255.0f;
        this.mass = mass;
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
        if ((eNum < 1) || (eNum >= VALUES.size())) {
            return null;
        }

        return ((AtomProperty) VALUES.get(eNum)).name;
    }

    public byte getElementNumber() {
        return getElementNumber(name);
    }

    public static byte getElementNumber(String elemName) {
        AtomProperty atomProp = (AtomProperty) map.get(elemName);

        if (atomProp != null) {
            return (byte) atomProp.aNum;
        } else {
            return 0;
        }
    }

    public static AtomProperty get(String elemName) {
        AtomProperty atomProp = (AtomProperty) map.get(elemName);

        if (atomProp == null) {
            return X;
        } else {
            return atomProp;
        }
    }

    public static AtomProperty get(int eNum) {
        if ((eNum < 0) || (eNum >= VALUES.size())) {
            return null;
        }

        return ((AtomProperty) VALUES.get(eNum));
    }
}
