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
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SpatialSet;

/**
 * @author brucejohnson
 */
public class ElectrostaticInteraction {

    static final double tolerance = 3.5;
    final SpatialSet target;
    final SpatialSet source;

    ElectrostaticInteraction(final SpatialSet target, final SpatialSet source) {
        this.target = target;
        this.source = source;
    }

    public boolean validate(int structureNum) {
        return validate(target, source, structureNum);
    }

    public static boolean validate(SpatialSet target, SpatialSet source, int structureNum) {
        boolean valid = false;
        Atom donor = target.atom.getParent();
        boolean hasLigand = false;
        boolean adjacent = false;
        if (!(source.atom.entity instanceof Residue) || !(target.atom.entity instanceof Residue)) {
            hasLigand = true;
        } else {
            Residue sourceRes = (Residue) source.atom.entity;
            Residue targetRes = (Residue) target.atom.entity;
            adjacent = (targetRes.previous == sourceRes) || (sourceRes.previous == targetRes);
        }
        Atom sourceParent = source.atom.getParent();
        if ((sourceParent != null) && (donor != null)) {
            if ((sourceParent.entity != donor.entity) && (!adjacent || hasLigand)) {
                Point3 donorPt = donor.getPoint(structureNum);
                Point3 targetPt = target.atom.getPoint(structureNum);
                if (donorPt != null) {
                    Point3 sourcePt = source.getPoint(structureNum);
                    if (sourcePt != null) {
                        double distance = Atom.calcDistance(targetPt, sourcePt);
                        if (target.atom.getName().equals("H") && source.atom.getName().equals("O")) {
                            if ((distance > tolerance) && (distance < 20.0)) {
                                valid = true;
                            }
                        } else if (target.atom.getName().equals("H") && source.atom.getName().equals("C")) {
                            if ((distance > tolerance) && (distance < 20.0)) {
                                valid = true;
                            }
                        } else if (target.atom.getName().equals("H") && source.atom.getName().equals("N")) {
                            if ((distance > tolerance) && (distance < 20.0)) {
                                valid = true;
                            }
                        } else if (target.atom.getName().equals("H") && source.atom.getName().equals("H")) {
                            if ((distance > tolerance) && (distance < 20.0)) {
                                valid = true;
                            }
                        } else if (distance < tolerance) {
                            valid = true;
                        }
                    }
                }
            }
        }
        return valid;
    }

    public static double getAngle(SpatialSet target, SpatialSet source, int structureNum) {
        SpatialSet targetParent = target.atom.getParent().spatialSet;
        Point3 targetPt = target.getPoint(structureNum);
        Point3 sourcePt = source.getPoint(structureNum);
        Point3 targetParentPt = targetParent.getPoint(structureNum);
        double angle = Atom.calcAngle(sourcePt, targetPt, targetParentPt);
        return angle;
    }

    public double getHADistance(int structureNum) {
        return getHADistance(target, source, structureNum);
    }

    public double getAngle(int structureNum) {
        return getAngle(target, source, structureNum);
    }

    public static double getHADistance(SpatialSet target, SpatialSet source, int structureNum) {
        Point3 targetPt = target.getPoint(structureNum);
        Point3 sourcePt = source.getPoint(structureNum);
        double distance = Atom.calcDistance(targetPt, sourcePt);
        return distance;
    }

    public static double getDistance(SpatialSet donor, SpatialSet source, int structureNum) {
        Point3 donorPt = donor.getPoint(structureNum);
        Point3 sourcePt = source.getPoint(structureNum);
        double distance = Atom.calcDistance(donorPt, sourcePt);
        return distance;
    }

    public String toString() {
        StringBuilder sBuild = new StringBuilder();
        sBuild.append(target.getFullName());
        sBuild.append(" ");
        sBuild.append(source.getFullName());
        sBuild.append(" ");
        sBuild.append(source.getFullName());
        sBuild.append(" ");
        double distance = getHADistance(target, source, 0);
        sBuild.append(distance);
        sBuild.append(" ");
        distance = getDistance(target, source, 0);
        sBuild.append(distance);
        sBuild.append(" ");
        double angle = getAngle(target, source, 0);
        sBuild.append(angle);
        sBuild.append(" ");
        double shift = getShift(0);
        sBuild.append(shift);
        return sBuild.toString();
    }

    public double getShift(int structureNum) {
        Atom donorAtom = target.atom.getParent();
        double distance = getHADistance(target, source, structureNum);
        double angle = getAngle(target, source, structureNum);
        double shift = 0.0;
        double minDistance = 1.5;
        if (distance < tolerance) {
            if (distance < minDistance) {
                distance = minDistance;
            }
            String sourceName = source.atom.getName();
            double pCharge = 0.0;
            if (sourceName.charAt(0) == 'O') {
                pCharge = -0.51;
            } else if (sourceName.charAt(0) == 'C') {
                pCharge = 0.51;
            } else if (sourceName.charAt(0) == 'N') {
                pCharge = -0.47;
            } else if (sourceName.charAt(0) == 'H') {
                pCharge = 0.31;
            }

            double cos = Math.abs(Math.cos(angle));
            shift = pCharge * cos / (distance * distance);
        }
        return shift;
    }

}
