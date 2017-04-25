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

import org.nmrfx.structure.fastlinear.FastVector3D;
import org.apache.commons.math3.util.FastMath;

class Coordinates3DF {

    double ux1, uy1, uz1, ux2, uy2, uz2, ux3, uy3, uz3;
    FastVector3D p1 = null;
    FastVector3D p2 = null;
    FastVector3D p3 = null;

    Coordinates3DF(FastVector3D p1, FastVector3D p2, FastVector3D p3) {
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
    }

    boolean setup() {
        double small = 0.000001;
        double a, b, c, d1, d2;
        double d, e, f, x2, y2, z2;

        /*
         * Define aunit vector <uv1> with components ux1,uy1,uz1 colinear with
         * and having the same direction as <23>. 
         */
        double[] data1 = p1.getValues();
        double[] data2 = p2.getValues();
        double[] data3 = p3.getValues();
        a = data3[0] - data2[0];
        b = data3[1] - data2[1];
        c = data3[2] - data2[2];

        if ((a == 0.0) && (b == 0.0) && (c == 0.0)) {
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
        d = data1[0] - data2[0];
        e = data1[1] - data2[1];
        f = data1[2] - data2[2];

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

    void calculate(final double dihedral, final double bndcos, final double bndsin, FastVector3D p4) {
        final double sinphi = FastMath.sin(dihedral);
        final double cosphi = FastMath.cos(dihedral);

        final double cdx = bndcos * ux1 + bndsin * (ux2 * sinphi + ux3 * cosphi);
        final double cdy = bndcos * uy1 + bndsin * (uy2 * sinphi + uy3 * cosphi);
        final double cdz = bndcos * uz1 + bndsin * (uz2 * sinphi + uz3 * cosphi);

        /*
         * Set the coordinates of p4. 
         */
        p3.add(cdx,cdy,cdz,p4);
    }
}
