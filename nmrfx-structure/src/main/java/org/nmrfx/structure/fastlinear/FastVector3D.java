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
package org.nmrfx.structure.fastlinear;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;

/**
 * @author Bruce Johnson
 */
public class FastVector3D extends FastVector {

    public FastVector3D(double[] data) {
        super(data);
    }

    public FastVector3D() {
        super(new double[3]);
    }

    public FastVector3D(double x, double y, double z) {
        super(new double[3]);
        set(x, y, z);
    }

    public FastVector3D copy() {
        return new FastVector3D(data[0], data[1], data[2]);
    }

    public double getX() {
        return data[0];
    }

    public double getY() {
        return data[1];
    }

    public double getZ() {
        return data[2];
    }

    public void set(double x, double y, double z) {
        data[0] = x;
        data[1] = y;
        data[2] = z;
    }

    public void set(FastVector3D v2) {
        double[] data2 = v2.data;
        data[0] = data2[0];
        data[1] = data2[1];
        data[2] = data2[2];
    }

    public double length() {
        return Math.sqrt(data[0] * data[0] + data[1] * data[1] + data[2] * data[2]);
    }

    public double sumSq() {
        return data[0] * data[0] + data[1] * data[1] + data[2] * data[2];
    }

    public static double distance(FastVector3D v1, FastVector3D v2) {
        return v1.dis(v2);
    }

    public static double distanceSq(FastVector3D v1, FastVector3D v2) {
        return v1.disSq(v2);
    }

    public double dis(FastVector3D v2) {
        return Math.sqrt(disSq(v2));
    }

    public double disSq(FastVector3D v2) {
        double delX = data[0] - v2.data[0];
        double delY = data[1] - v2.data[1];
        double delZ = data[2] - v2.data[2];
        return delX * delX + delY * delY + delZ * delZ;
    }

    public static boolean atomLimit(final FastVector3D a, final FastVector3D b, final double cutOff, final double cutOffSq) {
        double delX = Math.abs(a.data[0] - b.data[0]);
        boolean result = false;
        if (delX < cutOff) {
            double delY = Math.abs(a.data[1] - b.data[1]);
            if (delY < cutOff) {
                double delZ = Math.abs(a.data[2] - b.data[2]);
                if (delZ < cutOff) {
                    double sqDis = delX * delX + delY * delY + delZ * delZ;
                    if (sqDis < cutOffSq) {
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    public void add(double dx, double dy, double dz, FastVector3D target) {
        target.data[0] = data[0] + dx;
        target.data[1] = data[1] + dy;
        target.data[2] = data[2] + dz;
    }

    public void subtract(double dx, double dy, double dz, FastVector3D target) {
        target.data[0] = data[0] - dx;
        target.data[1] = data[1] - dy;
        target.data[2] = data[2] - dz;
    }

    public FastVector3D subtract(FastVector3D v2) {
        FastVector3D target = new FastVector3D();
        target.data[0] = data[0] - v2.getX();
        target.data[1] = data[1] - v2.getY();
        target.data[2] = data[2] - v2.getZ();
        return target;
    }

    public void subtract(FastVector3D v2, FastVector3D target) {
        target.data[0] = data[0] - v2.getX();
        target.data[1] = data[1] - v2.getY();
        target.data[2] = data[2] - v2.getZ();
    }

    public static void crossProduct(FastVector3D v1, FastVector3D v2, FastVector3D target) {
        target.data[0] = MathArrays.linearCombination(v1.data[1], v2.data[2], -v1.data[2], v2.data[1]);
        target.data[1] = MathArrays.linearCombination(v1.data[2], v2.data[0], -v1.data[0], v2.data[2]);
        target.data[2] = MathArrays.linearCombination(v1.data[0], v2.data[1], -v1.data[1], v2.data[0]);
    }

    public double norm() {
        return Math.sqrt(
                data[0] * data[0]
                        + data[1] * data[1]
                        + data[2] * data[2]);
    }

    public double dotProduct(FastVector3D v2) {
        return MathArrays.linearCombination(
                data[0], v2.data[0],
                data[1], v2.data[1],
                data[2], v2.data[2]);
    }

    public static double angle(FastVector3D v1, FastVector3D v2) {

        double normProduct = v1.norm() * v2.norm();
        double dot = v1.dotProduct(v2);
        return FastMath.acos(dot / normProduct);
    }

    public static double volume(FastVector3D a, FastVector3D b, FastVector3D c, FastVector3D d) {
        FastVector3D i = a.subtract(d);
        FastVector3D j = b.subtract(d);
        FastVector3D k = c.subtract(d);
        // triple product
        FastVector3D jXk = new FastVector3D();
        FastVector3D.crossProduct(j, k, jXk);
        return i.dotProduct(jXk);
    }

    public static double calcDihedral(FastVector3D a, FastVector3D b, FastVector3D c, FastVector3D d) {

        double d12 = FastVector3D.distance(a, b);
        double sd13 = FastVector3D.distanceSq(a, c);
        double sd14 = FastVector3D.distanceSq(a, d);
        double sd23 = FastVector3D.distanceSq(b, c);
        double sd24 = FastVector3D.distanceSq(b, d);
        double d34 = FastVector3D.distance(c, d);
        double ang123 = FastVector3D.angle(a.subtract(b), c.subtract(b));
        double ang234 = FastVector3D.angle(b.subtract(c), d.subtract(c));
        double cosine = (sd13 - sd14 + sd24 - sd23 + 2.0 * d12 * d34 * Math.cos(ang123) * Math.cos(ang234))
                / (2.0 * d12 * d34 * Math.sin(ang123) * Math.sin(ang234));

        double volume = volume(a, b, c, d);

        double sgn = (volume < 0.0) ? 1.0 : -1.0;
        final double angle;
        if (cosine > 1.0) {
            angle = 0.0;
        } else if (cosine < -1.0) {
            angle = Math.PI;
        } else {
            angle = sgn * FastMath.acos(cosine);
        }
        return (angle);

    }

}
