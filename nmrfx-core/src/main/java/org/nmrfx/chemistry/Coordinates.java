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

import org.apache.commons.math3.util.FastMath;

public class Coordinates {

    double ux1, uy1, uz1, ux2, uy2, uz2, ux3, uy3, uz3;
    Point3 p1 = null;
    Point3 p2 = null;
    Point3 p3 = null;

    public Coordinates(Point3 p1, Point3 p2, Point3 p3) {
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
    }

    public boolean setup() {
        double small = 0.000001;
        double a, b, c, d1, d2;
        double d, e, f, x2, y2, z2;

        /*
         * Define aunit vector <uv1> with components ux1,uy1,uz1 colinear with
         * and having the same direction as <23>.
         */
        a = p3.getX() - p2.getX();
        b = p3.getY() - p2.getY();
        c = p3.getZ() - p2.getZ();
        if ((p3.getX() == p2.getX()) && (p3.getY() == p2.getY()) && (p3.getZ() == p2.getZ())) {
            System.out.println("d1 is zero");
            return false;
        }
        d1 = FastMath.sqrt(a * a + b * b + c * c);
        ux1 = a / d1;
        uy1 = b / d1;
        uz1 = c / d1;

        /*
         * Define a second unit vector <uv2> with components ux2,uy2,uz2 and
         * having the same direction as the cross product <23> x <21>.
         */
        d = p1.getX() - p2.getX();
        e = p1.getY() - p2.getY();
        f = p1.getZ() - p2.getZ();
        x2 = b * f - c * e;
        y2 = c * d - a * f;
        z2 = a * e - b * d;

        d2 = FastMath.sqrt(x2 * x2 + y2 * y2 + z2 * z2);
        while (d2 < small) {
            /* colinear points */

            /*
             * Construct normal to line. Assume that the line is formed by 2
             * intersecting planes having equations of the form:
             *
             * y = Mx + c1         M = b/a = e/d z = Nx + c2 N = c/a = f/d
             */
            if (FastMath.abs(a) <= small) {
                x2 = 0.0;
                if (FastMath.abs(b) <= small || FastMath.abs(c) <= small) {
                    y2 = c;
                    z2 = b;
                } else {
                    y2 = 1.0;
                    z2 = -b / c;
                }
            } else {
                x2 = -c / a;
                y2 = 0.0;
                z2 = 1.0;
            }
            /*
             * BAJ I inserted the following line. Without it the loop would
             * be endless, but I am not sure if this is what the author
             * intended.
             */
            d2 = FastMath.sqrt(x2 * x2 + y2 * y2 + z2 * z2);
        }
        ux2 = x2 / d2;
        uy2 = y2 / d2;
        uz2 = z2 / d2;

        /*
         * Define a third vector <uv3> with components ux3, uy3, uz3 defined
         * as the cross product: <uv3> = <uv2> x <uv1>
         */
        ux3 = uy2 * uz1 - uz2 * uy1;
        uy3 = uz2 * ux1 - ux2 * uz1;
        uz3 = ux2 * uy1 - uy2 * ux1;

        /*
         * If bond_len and angle are help fixed and dihedral is allowed to
         * vary over two-pi radians then the point 4 will describe a circle
         * in space with radius given below. The center of this circle is
         * denoted as point "e". The vector <34> is then found as the vector
         * sum of <3e> + <e4>.
         */
        return true;

    }

    public Point3 calculate(final double dihedral, final double bndcos, final double bndsin) {
        final double sinphi = FastMath.sin(dihedral);
        final double cosphi = FastMath.cos(dihedral);

        final double cdx = bndcos * ux1 + bndsin * (ux2 * sinphi + ux3 * cosphi);
        final double cdy = bndcos * uy1 + bndsin * (uy2 * sinphi + uy3 * cosphi);
        final double cdz = bndcos * uz1 + bndsin * (uz2 * sinphi + uz3 * cosphi);
        if (!Double.isFinite(cdx) || !Double.isFinite(cdy) || !Double.isFinite(cdz)) {
            System.out.println("non finite coords");
        }
        return new Point3(p3.getX() + cdx, p3.getY() + cdy, p3.getZ() + cdz);
    }
}
