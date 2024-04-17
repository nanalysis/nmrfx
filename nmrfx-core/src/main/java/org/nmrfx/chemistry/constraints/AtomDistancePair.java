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

package org.nmrfx.chemistry.constraints;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Point3;

/**
 * This class represents a pair of atoms for which the distance will be computed
 */
public class AtomDistancePair {

    private final Atom[] atoms1;
    private final Atom[] atoms2;

    final double methylCorrection;

    /**
     * Simple Constructor
     *
     * @param atom1 first atom in the distance pair
     * @param atom2 second atom in the distance pair
     * @author brucejohnson
     */
    public AtomDistancePair(Atom atom1, Atom atom2) {
        double corrValue = 0.0;
        if (atom1.isMethyl()) {
            this.atoms1 = new Atom[3];
            this.atoms1[0] = atom1;
            Atom[] partners = atom1.getPartners(1, 1);
            this.atoms1[1] = partners[0];
            this.atoms1[2] = partners[1];
            corrValue += 0.5;
        } else {
            this.atoms1 = new Atom[1];
            this.atoms1[0] = atom1;
        }
        if (atom2.isMethyl()) {
            this.atoms2 = new Atom[3];
            this.atoms2[0] = atom2;
            Atom[] partners = atom2.getPartners(1, 1);
            this.atoms2[1] = partners[0];
            this.atoms2[2] = partners[1];
            corrValue += 0.5;
        } else {
            this.atoms2 = new Atom[1];
            this.atoms2[0] = atom2;
        }
        methylCorrection = corrValue;
    }

    /**
     * atom 1
     */
    public Atom[] getAtoms1() {
        return atoms1;
    }

    /**
     * atom 2
     */
    public Atom[] getAtoms2() {
        return atoms2;
    }

    public double getDistance() {
        return getSumAvgDistance();
    }

    public double getSumAvgDistance() {
        double avgN = 6.0;
        final double distance;
        if (methylCorrection == 0.0) {
            Point3 point1 = atoms1[0].getPoint();
            Point3 point2 = atoms2[0].getPoint();
            distance = Vector3D.distance(point1, point2);
        } else {
            double sum = 0.0;
            for (Atom atom1 : atoms1) {
                Point3 point1 = atom1.getPoint();
                for (Atom atom2 : atoms2) {
                    Point3 point2 = atom2.getPoint();
                    double distance12 = Vector3D.distance(point1, point2);
                    sum += FastMath.pow(distance12, -avgN);
                }
            }
            distance = FastMath.pow(sum, -1.0 / avgN);
        }
        return distance;
    }

    public double getDistanceToClosest() {
        final double distance;
        if (methylCorrection == 0.0) {
            Point3 point1 = atoms1[0].getPoint();
            Point3 point2 = atoms2[0].getPoint();
            distance = Vector3D.distance(point1, point2);
        } else {
            double closestDistance = Double.MAX_VALUE;
            for (Atom atom1 : atoms1) {
                Point3 point1 = atom1.getPoint();
                for (Atom atom2 : atoms2) {
                    Point3 point2 = atom2.getPoint();
                    double distance12 = Vector3D.distance(point1, point2);
                    if (distance12 < closestDistance) {
                        closestDistance = distance12;
                    }
                }
            }

            closestDistance -= methylCorrection;
            if (closestDistance < 0.01) {
                closestDistance = 0.01;
            }
            distance = closestDistance;
        }
        return distance;
    }

    public double getDistanceToCenters() {
        Point3 pt1 = getCenter1();
        Point3 pt2 = getCenter2();
        double distance = Vector3D.distance(pt1, pt2);
        if (methylCorrection != 0.0) {
            distance -= methylCorrection;
            if (distance < 0.1) {
                distance = 0.1;
            }
        }
        return distance;
    }

    public Point3 getCenter1() {
        if (atoms1.length == 1) {
            return atoms1[0].getPoint();
        } else {
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            int n = atoms1.length;
            for (Atom atom : atoms1) {
                Point3 point = atom.getPoint();
                x += point.getX();
                y += point.getY();
                z += point.getZ();
            }
            Point3 result = new Point3(x / n, y / n, z / n);
            return result;
        }
    }

    public Point3 getCenter2() {
        if (atoms2.length == 1) {
            return atoms2[0].getPoint();
        } else {
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            int n = atoms2.length;
            for (Atom atom : atoms2) {
                Point3 point = atom.getPoint();
                x += point.getX();
                y += point.getY();
                z += point.getZ();
            }
            Point3 result = new Point3(x / n, y / n, z / n);
            return result;
        }
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (Atom atom1 : atoms1) {
            sBuilder.append(atom1.getShortName());
            sBuilder.append(" ");
            sBuilder.append(atom1.getPoint().toString());
            sBuilder.append(" ");
        }
        for (Atom atom1 : atoms2) {
            sBuilder.append(atom1.getShortName());
            sBuilder.append(" ");
            sBuilder.append(atom1.getPoint().toString());
            sBuilder.append(" ");
        }
        sBuilder.append(methylCorrection);
        return sBuilder.toString();
    }

}
