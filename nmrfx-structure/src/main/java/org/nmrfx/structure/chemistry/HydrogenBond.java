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

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Point3;
import org.nmrfx.chemistry.SpatialSet;

/**
 * @author brucejohnson
 */
public class HydrogenBond {

    static final double toleranceHN = 3.5;
    static final double toleranceHA = 2.77;
    static final double toleranceRNA = 2.9;

    final SpatialSet hydrogen;
    final SpatialSet acceptor;

    HydrogenBond(final SpatialSet hydrogen, final SpatialSet acceptor) {
        this.hydrogen = hydrogen;
        this.acceptor = acceptor;
    }

    public boolean validate(int structureNum) {
        return validate(hydrogen, acceptor, structureNum);
    }

    /**
     * Test if two atoms have the correct geometry to be in a hydrogen bond
     *
     * @param hydrogen     SpatialSet for the hydrogen
     * @param acceptor     SpatialSet for the hydrogen bond acceptor (typically O or
     *                     N)
     * @param structureNum
     * @return boolean depending on whether the geometry is correct
     */
    public static boolean validateRNA(SpatialSet hydrogen, SpatialSet acceptor, int structureNum) {
        boolean valid = false;
        Atom donor = hydrogen.atom.getParent();
        Atom acceptorParent = acceptor.atom.getParent();
        if ((acceptorParent != null) && (donor != null)) {
            if (acceptorParent.entity != donor.entity) {
                Point3 donorPt = donor.getPoint(structureNum);
                Point3 hydrogenPt = hydrogen.atom.getPoint(structureNum);
                if ((hydrogenPt != null) && (donorPt != null)) {
                    Point3 acceptorPt = acceptor.getPoint(structureNum);
                    if (acceptorPt != null) {
                        double distance = Atom.calcDistance(hydrogenPt, acceptorPt);
                        if (distance < toleranceRNA) {
                            double angle = getRNAAngle(hydrogen, acceptor, structureNum);
                            if (angle > 1.85) {
                                valid = true;

                            }
                        }
                    }
                }
            }

        }
        return valid;
    }

    public static boolean validate(SpatialSet hydrogen, SpatialSet acceptor, int structureNum) {
        boolean valid = false;
        final double tolerance;
        Atom donor = hydrogen.atom.getParent();
        Atom acceptorParent = acceptor.atom.getParent();
        if ((acceptorParent != null) && (donor != null)) {
            if (acceptorParent.entity != donor.entity) {
                if (hydrogen.atom.getName().equals("HA")) {
                    tolerance = toleranceHA;
                } else {
                    tolerance = toleranceHN;
                }
                Point3 donorPt = donor.getPoint(structureNum);
                Point3 hydrogenPt = hydrogen.atom.getPoint(structureNum);
                if ((donorPt != null) && (hydrogenPt != null)) {
                    Point3 acceptorPt = acceptor.getPoint(structureNum);
                    if (acceptorPt != null) {
                        double distance = Atom.calcDistance(hydrogenPt, acceptorPt);
                        if (distance < tolerance) {
                            double angle = getAngle(hydrogen, acceptor, structureNum);

                            if (angle > Math.PI / 2.0) {
                                valid = true;
                            }
                        }
                    }
                }
            }
        }
        return valid;
    }

    public static double getAngle(SpatialSet hydrogen, SpatialSet acceptor, int structureNum) {
        Point3 hydrogenPt = hydrogen.getPoint(structureNum);
        Point3 acceptorPt = acceptor.getPoint(structureNum);
        SpatialSet hydrogenParent = hydrogen.atom.getParent().spatialSet;
        SpatialSet acceptorParent = acceptor.atom.getParent().spatialSet;
        Point3 hydrogenParentPt = hydrogenParent.getPoint(structureNum);
        Point3 acceptorParentPt = acceptorParent.getPoint(structureNum);
        if ((hydrogenPt != null) && (acceptorPt != null) && (acceptorParentPt != null) && (hydrogenParentPt != null)) {
            double dx = acceptorParentPt.getX() - hydrogenParentPt.getX();
            double dy = acceptorParentPt.getY() - hydrogenParentPt.getY();
            double dz = acceptorParentPt.getZ() - hydrogenParentPt.getZ();
            Point3 acceptorOffsetPt = new Point3(acceptorPt.getX() - dx, acceptorPt.getY() - dy, acceptorPt.getZ() - dz);
            double angle = Atom.calcAngle(hydrogenPt, hydrogenParentPt, acceptorOffsetPt);
            return angle;
        } else {
            return 0.0;
        }

    }

    public static double getRNAAngle(SpatialSet hydrogen, SpatialSet acceptor, int structureNum) {
        Point3 hydrogenPt = hydrogen.getPoint(structureNum);
        Point3 acceptorPt = acceptor.getPoint(structureNum);
        SpatialSet hydrogenParent = hydrogen.atom.getParent().spatialSet;
        Point3 hydrogenParentPt = hydrogenParent.getPoint(structureNum);
        double angle = Atom.calcAngle(acceptorPt, hydrogenPt, hydrogenParentPt);
        return angle;

    }

    public double getHADistance(int structureNum) {
        return getHADistance(hydrogen, acceptor, structureNum);
    }

    public double getAngle(int structureNum) {
        return getAngle(hydrogen, acceptor, structureNum);
    }

    public static double getHADistance(SpatialSet hydrogen, SpatialSet acceptor, int structureNum) {
        Point3 hydrogenPt = hydrogen.getPoint(structureNum);
        Point3 acceptorPt = acceptor.getPoint(structureNum);
        double distance = Atom.calcDistance(hydrogenPt, acceptorPt);
        return distance;
    }

    public static double getDistance(SpatialSet donor, SpatialSet acceptor, int structureNum) {
        Point3 donorPt = donor.getPoint(structureNum);
        Point3 acceptorPt = acceptor.getPoint(structureNum);
        double distance = Atom.calcDistance(donorPt, acceptorPt);
        return distance;
    }

    public String toString() {
        StringBuilder sBuild = new StringBuilder();
        sBuild.append(hydrogen.getFullName());
        sBuild.append(" ");
        sBuild.append(acceptor.getFullName());
        sBuild.append(" ");
        double distance = getHADistance(hydrogen, acceptor, 0);
        sBuild.append(distance);
        sBuild.append(" ");
        double angle = getAngle(hydrogen, acceptor, 0) * 180.0 / Math.PI;
        sBuild.append(angle);
        sBuild.append(" ");
        double shift = getShift(0);
        sBuild.append(shift);
        return sBuild.toString();
    }

    public double getShiftOld(int structureNum) {
        SpatialSet donor = hydrogen.atom.getParent().spatialSet;
        Atom donorAtom = hydrogen.atom.getParent();
        double shift = 0.0;
        if (donorAtom.getAtomicNumber() == 7) {
            double distance = getHADistance(hydrogen, acceptor, structureNum);
            double dis3 = distance * distance * distance;
            shift = 0.75 / dis3 - 0.99;
        } else {
            double distance = getDistance(donor, acceptor, structureNum);
            double dis3 = distance * distance * distance;
            if (distance < 2.77) {
                if (distance > 2.61) {
                    distance = 2.61;
                }
                if (distance < 2.27) {
                    distance = 2.27;
                }
                shift = 15.69 / dis3 - 0.67;
            }
        }
        return shift;
    }

    public double getShift(int structureNum) {
        return getShift(structureNum, 3.0);
    }

    public double getShift(int structureNum, double power) {
        Atom donorAtom = hydrogen.atom.getParent();
        double distance = getHADistance(hydrogen, acceptor, structureNum);
        double angle = getAngle(hydrogen, acceptor, structureNum);
        double shift = 0.0;
        double tolerance;
        double maxDistance;
        double minDistance;
        if (donorAtom.getAtomicNumber() == 7) {
            tolerance = toleranceHN;
            maxDistance = toleranceHN;
            minDistance = 1.5;
        } else {
            tolerance = toleranceHA;
            maxDistance = 2.61;
            minDistance = 2.27;
        }
        if (distance < tolerance) {
            if (distance > maxDistance) {
                distance = maxDistance;
            }
            if (distance < minDistance) {
                distance = minDistance;
            }

            double disP = Math.pow(distance, power);
            double maxP = Math.pow(maxDistance, power);
            shift = 1.0 / disP - 1.0 / maxP;
            double cos = Math.abs(Math.cos(angle));
            shift = shift * (1.0 + 1.0 * (cos * cos - 1.0));
        }
        return shift;
    }

}
