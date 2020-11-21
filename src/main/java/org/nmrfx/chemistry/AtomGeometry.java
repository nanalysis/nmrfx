package org.nmrfx.chemistry;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;

public class AtomGeometry {
    /**
     * Calculates the angle between two vectors
     *
     * @param pt1 first point
     * @param pt2 second point
     * @param pt3 third point
     */
    public static double calcAngle(final Vector3D pt1, final Vector3D pt2, final Vector3D pt3) {
        Vector3D v12 = pt1.subtract(pt2);
        Vector3D v32 = pt3.subtract(pt2);
        return Vector3D.angle(v12, v32);
    }

    /**
     * Calculates the triple using three points
     *
     * @param pt1 first point
     * @param pt2 second point
     * @param pt3 third point
     * @return triple scalar float
     */
    public static double calcTriple(final Vector3D pt1, final Vector3D pt2, final Vector3D pt3) {
        return Vector3D.dotProduct(pt1, Vector3D.crossProduct(pt2, pt3));
    }

    /**
     * Calculates the volume or space occupied between 4 points
     *
     * @param pt1 first point
     * @param pt2 second point
     * @param pt3 third point
     * @param pt4 fourth point
     * @return volume double
     */
    public static double calcVolume(final Point3 a, final Point3 b, final Point3 c, final Point3 d) {
        return calcTriple(a.subtract(d), b.subtract(d), c.subtract(d));
    }

    /**
     * Calculates the dihedral angle
     *
     * @param pt1 first point
     * @param pt2 second point
     * @param pt3 third point
     * @param pt4 fourth point
     * @return angle
     */
    public static double calcDihedral(final Point3 a, final Point3 b, final Point3 c, final Point3 d) {

        final double d12 = Vector3D.distance(a, b);
        final double sd13 = Vector3D.distanceSq(a, c);
        final double sd14 = Vector3D.distanceSq(a, d);
        final double sd23 = Vector3D.distanceSq(b, c);
        final double sd24 = Vector3D.distanceSq(b, d);
        final double d34 = Vector3D.distance(c, d);
        final double ang123 = Vector3D.angle(a.subtract(b), c.subtract(b));
        final double ang234 = Vector3D.angle(b.subtract(c), d.subtract(c));
        final double cosine = (sd13 - sd14 + sd24 - sd23 + 2.0 * d12 * d34 * FastMath.cos(ang123) * FastMath.cos(ang234))
                / (2.0 * d12 * d34 * FastMath.sin(ang123) * FastMath.sin(ang234));

        final double volume = calcVolume(a, b, c, d);

        final double sgn = (volume < 0.0) ? 1.0 : -1.0;
        double angle = 0.0;
        if (cosine > 1.0) {
            angle = 0.0;
        } else if (cosine < -1.0) {
            angle = FastMath.PI;
        } else {
            angle = sgn * FastMath.acos(cosine);
        }
        return (angle);

    }
}
