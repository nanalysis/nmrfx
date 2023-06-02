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
package org.nmrfx.structure.chemistry.energy;

import org.apache.commons.math3.geometry.euclidean.threed.Line;
import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.chemistry.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RingCurrentShift {

    private ArrayList<FusedRing> fusedRingList = new ArrayList<FusedRing>();
    private static HashMap<String, Ring> stdRings = new HashMap<String, Ring>();
    private static final HashMap<String, PPMv> refShifts = new HashMap<String, PPMv>();

    static {
        refShifts.put("U.H6", new PPMv(8.00));
        refShifts.put("U.H3'", new PPMv(4.56));
        refShifts.put("U.H5", new PPMv(5.80));
        refShifts.put("U.H5'", new PPMv(4.36));
        refShifts.put("A.H5'", new PPMv(4.36));
        refShifts.put("G.H5''", new PPMv(4.11));
        refShifts.put("U.H1'", new PPMv(5.49));
        refShifts.put("A.H3'", new PPMv(4.56));
        refShifts.put("G.H1'", new PPMv(5.43));
        refShifts.put("G.H3'", new PPMv(4.56));
        refShifts.put("G.H5'", new PPMv(4.36));
        refShifts.put("A.H5''", new PPMv(4.11));
        refShifts.put("C.H2'", new PPMv(4.48));
        refShifts.put("C.H4'", new PPMv(4.38));
        refShifts.put("G.H8", new PPMv(7.77));
        refShifts.put("A.H1'", new PPMv(5.51));
        refShifts.put("U.H4'", new PPMv(4.38));
        refShifts.put("A.H8", new PPMv(8.21));
        refShifts.put("C.H6", new PPMv(7.94));
        refShifts.put("C.H5''", new PPMv(4.11));
        refShifts.put("C.H5", new PPMv(5.85));
        refShifts.put("U.H2'", new PPMv(4.48));
        refShifts.put("A.H4'", new PPMv(4.38));
        refShifts.put("G.H2'", new PPMv(4.48));
        refShifts.put("A.H2", new PPMv(7.79));
        refShifts.put("C.H5'", new PPMv(4.36));
        refShifts.put("G.H4'", new PPMv(4.38));
        refShifts.put("U.H5''", new PPMv(4.11));
        refShifts.put("C.H1'", new PPMv(5.46));
        refShifts.put("C.H3'", new PPMv(4.56));
        refShifts.put("A.H2'", new PPMv(4.48));
    }

    static class RingType {

        final String name;
        final String[] atomNames;
        double ringFactor = 1.0;

        RingType(final String name, final String[] atomNames, final double ringFactor) {
            this.name = name;
            this.atomNames = atomNames;
            this.ringFactor = ringFactor;
        }

        void setRingFactor(final double ringFactor) {
            this.ringFactor = ringFactor;
        }
    }

    static class FusedRing {

        ArrayList<Ring> rings = new ArrayList<Ring>();

        void add(Ring ring) {
            rings.add(ring);
        }

        boolean hasSpatialSet(final SpatialSet targetParent) {
            boolean sameRing = false;
            for (Ring ring : rings) {
                if (ring.hasSpatialSet(targetParent)) {
                    sameRing = true;
                    break;
                }
            }
            return sameRing;
        }
    }

    static class Ring {

        final RingType type;
        ArrayList<SpatialSet> spatialSets = new ArrayList<SpatialSet>();
        ArrayList<Vector3D> points = new ArrayList<Vector3D>();
        Plane plane = null;
        Vector3D normal = null;

        Ring(final RingType type, final ArrayList<SpatialSet> spatialSets) {
            this.type = type;
            this.spatialSets = spatialSets;
        }

        void setPoints(final ArrayList<Vector3D> points) {
            this.points = points;
        }

        void setPlane(final Plane plane, final Vector3D normal) {
            this.plane = plane;
            this.normal = normal;
        }

        boolean isPlaneSet() {
            return !(plane == null);
        }

        boolean hasSpatialSet(final SpatialSet targetParent) {
            boolean sameRing = false;
            for (SpatialSet spatialSet : spatialSets) {
                if (spatialSet == targetParent) {
                    sameRing = true;
                    break;
                }
            }
            return sameRing;
        }
    }

    //￼1.05 0.92 1.04 0.90 0.43
    static final String[] pheAtoms0 = {"CG", "CD2", "CE2", "CZ", "CE1", "CD1"};
    static final String[] tyrAtoms0 = {"CG", "CD2", "CE2", "CZ", "CE1", "CD1"};
    static final String[] trpAtoms0 = {"CD2", "CE3", "CZ3", "CH2", "CZ2", "CE2"};
    static final String[] trpAtoms1 = {"CG", "CD2", "CE2", "NE1", "CD1"};
    static final String[] hisAtoms0 = {"CG", "ND1", "CE1", "NE2", "CD2"};
    static final String[] radeAtoms0 = {"N1", "C2", "N3", "C4", "C5", "C6"};
    static final String[] radeAtoms1 = {"C4", "C5", "N7", "C8", "N9"};
    static final String[] rguaAtoms0 = {"N1", "C2", "N3", "C4", "C5", "C6"};
    static final String[] rguaAtoms1 = {"C4", "C5", "N7", "C8", "N9"};
    static final String[] rcytAtoms0 = {"N1", "C2", "N3", "C4", "C5", "C6"};
    static final String[] ruraAtoms0 = {"N1", "C2", "N3", "C4", "C5", "C6"};
    static final String[] rthyAtoms0 = {"N1", "C2", "N3", "C4", "C5", "C6"};
    static final HashMap<String, RingType> ringAtoms = new HashMap<String, RingType>();
// Nucleic acid rings from Case, J. Bio. NMR 6:341
// Protein rings from Wishart, J. Bio. NMR 26:215

    static {
        ringAtoms.put("PHE0", new RingType("PHE0", pheAtoms0, 1.05));
        ringAtoms.put("TYR0", new RingType("TYR0", tyrAtoms0, 0.92));
        ringAtoms.put("TRP0", new RingType("TRP0", trpAtoms0, 1.04));
        ringAtoms.put("TRP1", new RingType("TRP1", trpAtoms1, 0.90));
        ringAtoms.put("HIS0", new RingType("HIS0", hisAtoms0, 0.43));
        ringAtoms.put("A0", new RingType("A0", radeAtoms0, 0.90));
        ringAtoms.put("A1", new RingType("A1", radeAtoms1, 1.14));
        ringAtoms.put("G0", new RingType("G0", rguaAtoms0, 0.51));
        ringAtoms.put("G1", new RingType("G1", rguaAtoms1, 1.00));
        ringAtoms.put("C0", new RingType("C0", rcytAtoms0, 0.37));
        ringAtoms.put("U0", new RingType("U0", ruraAtoms0, 0.30));
        ringAtoms.put("T0", new RingType("T0", rthyAtoms0, 0.35));
        makeBenzene();
    }

    private MoleculeBase molecule;

    static void makeBenzene() {
        final String[] benAtoms0 = {"C1", "C2", "C3", "C4", "C5", "C6"};
        RingType benzeneType = new RingType("BENZENE", benAtoms0, 1.0);
        Vector3D pt1 = new Vector3D(-0.0103, -1.3948, 0.0);
        Vector3D pt2 = new Vector3D(1.2028, -0.7063, 0.0);
        Vector3D pt3 = new Vector3D(1.2131, 0.6884, 0.00);
        Vector3D pt4 = new Vector3D(0.0104, 1.3948, -0.0);
        Vector3D pt5 = new Vector3D(-1.2028, 0.7064, 0.0);
        Vector3D pt6 = new Vector3D(-1.2131, -0.6884, 0.0);
        ArrayList<Vector3D> points = new ArrayList<Vector3D>();
        points.add(pt1);
        points.add(pt2);
        points.add(pt3);
        points.add(pt4);
        points.add(pt5);
        points.add(pt6);
        points.add(pt1);
        Ring ring = new Ring(benzeneType, null);
        ring.setPoints(points);
        setRingConformationFromPoints(ring);
        stdRings.put("benzene", ring);
    }

    public static void setRingFactor(final String ringName, final double ringFactor) {
        RingType ringType = ringAtoms.get(ringName);
        ringType.setRingFactor(ringFactor);
    }

    public static double getRingFactor(final String ringName) {
        RingType ringType = ringAtoms.get(ringName);
        return ringType.ringFactor;
    }

    public static void setRingConformation(Ring ring, int iStruct) {
        ArrayList<SpatialSet> spatialSets = ring.spatialSets;
        ring.points.clear();
        for (SpatialSet spatialSet : spatialSets) {
            Point3 pt = spatialSet.getPoint(iStruct);
            if (pt == null) {
                System.out.println("Null point for " + spatialSet.getFullName() + " for struct " + iStruct);
            }
            ring.points.add(pt);
        }
        setRingConformationFromPoints(ring);
    }

    static void setRingConformationFromPoints(Ring ring) {
        Vector3D pt0 = ring.points.get(0);
        Vector3D pt1 = ring.points.get(1);
        Vector3D pt2 = ring.points.get(ring.points.size() - 2);
        Plane plane = new Plane(pt0, pt1, pt2);
        Vector3D normal = plane.getNormal();
        ring.setPlane(plane, normal);
    }

    public double test(double x, double y, double z, double targetFactor) {
        int iStruct = 0;
        Vector3D pt = new Vector3D(x, y, z);
        Ring ring = stdRings.get("benzene");
        return calcRingContributions(ring, pt, targetFactor, iStruct);
    }

    public static List<SpatialSet> refSP = null;

    public void setBasePPMs(List<SpatialSet> targetSpatialSets) {
        for (SpatialSet sp : targetSpatialSets) {
            String nucName = sp.atom.getEntity().getName();
            String aName = sp.atom.getName();
            PPMv ppm = refShifts.get(nucName + "." + aName);
            if (ppm != null) {
                sp.setPPM(1, ppm.getValue(), false);
            }
        }
        refSP = targetSpatialSets;
    }

    public void predictShifts() {
        for (SpatialSet sp : refSP) {
            double ringRatio = 0.475;
            double basePPM = sp.getPPM(1).getValue();
            double ringPPM = calcRingContributions(sp, 0, ringRatio);
            double ppm = basePPM + ringPPM;
            sp.setRefPPM(0, ppm);
        }
    }

    public void calcRingContributions(ArrayList<SpatialSet> targetSpatialSets, ArrayList<Integer> structs, final int ppmSet, final double ringRatio) {
        double shifts[] = new double[targetSpatialSets.size()];
        for (Integer iStruct : structs) {
            for (FusedRing fusedRing : fusedRingList) {
                for (Ring ring : fusedRing.rings) {
                    setRingConformation(ring, iStruct);
                }
            }
            int i = 0;
            for (SpatialSet spatialSet : targetSpatialSets) {
                shifts[i++] += calcRingContributions(spatialSet, iStruct, ringRatio);
            }
        }
        if (structs.size() > 0) {
            int i = 0;
            for (SpatialSet spatialSet : targetSpatialSets) {
                shifts[i] /= structs.size();
                PPMv ppmV;
                if (ppmSet >= 0) {
                    spatialSet.setPPMValidity(ppmSet, true);
                    ppmV = spatialSet.getPPM(ppmSet);
                } else {
                    ppmV = spatialSet.getRefPPM();
                }
                if (ppmV != null) {
                    double newPPM = ppmV.getValue() + shifts[i];
                    ppmV.setValue(newPPM);
                }
                i++;
            }
        }
    }

    /**
     * Calculate the chemical shift contribution to this atom from ring current
     * shifts of surrounding aromatic rings. The output of this method should be
     * added to the calibrated reference shift for the atoms type. Before
     * calling this method the list of aromatic rings in the molecule needs to
     * me set up.
     *
     * @param targetSpatialSet The spatial set for the target atom
     * @param iStruct          The structure set to get coordinates from
     * @param ringRatio        An empirically calibrated ratio from our fitting
     *                         algorithm
     * @return
     * @see makeRingList
     */
    public double calcRingContributions(SpatialSet targetSpatialSet, int iStruct, final double ringRatio) {
        double targetFactor = 5.45 * ringRatio;  // 5.45 from Osapay & Case JACS 1991
        Vector3D targetPoint = targetSpatialSet.getPoint(iStruct);
        double sum = 0.0;
        if (targetPoint != null) {
            Atom parent = targetSpatialSet.atom.getParent();
            if (parent != null) {
                SpatialSet targetParent = parent.getSpatialSet();
                for (FusedRing fusedRing : fusedRingList) {
                    if (!fusedRing.hasSpatialSet(targetParent)) {
                        for (Ring ring : fusedRing.rings) {
                            sum += calcRingContributions(ring, targetPoint, targetFactor, iStruct);
                        }
                    }
                }
            }
        }
        return sum;
    }

    private double calcRingContributions(Ring ring, Vector3D targetPoint, final double targetFactor, final int iStruct) {
        if (!ring.isPlaneSet()) {
            setRingConformation(ring, iStruct);
        }
        Line line = new Line(targetPoint, targetPoint.add(ring.normal));
        Vector3D pointOnPlane = ring.plane.intersection(line);
        double ringCurrentSum = 0.0;
        for (int iPoint = 1; iPoint < ring.points.size(); iPoint++) {
            Vector3D ptStart = ring.points.get(iPoint - 1);
            Vector3D ptEnd = ring.points.get(iPoint);
            Vector3D crossProd = Vector3D.crossProduct(ptStart.subtract(pointOnPlane), ptEnd.subtract(pointOnPlane));
            double mag = crossProd.getNorm();
            double dotProd = ring.normal.dotProduct(crossProd);
            double area = -0.5 * Math.copySign(mag, dotProd);
            double rStart = Vector3D.distance(targetPoint, ptStart);
            double rEnd = Vector3D.distance(targetPoint, ptEnd);
            double distance = 1.0 / (rStart * rStart * rStart) + 1.0 / (rEnd * rEnd * rEnd);
            double G = distance * area;
            double ringCurrent = G * ring.type.ringFactor * targetFactor;
            ringCurrentSum += ringCurrent;
        }
        return ringCurrentSum;
    }

    public HashMap<String, Double> calcRingGeometricFactors(SpatialSet targetSpatialSet, int iStruct) {
        Vector3D targetPoint = targetSpatialSet.getPoint(iStruct);
        SpatialSet targetParent = targetSpatialSet.atom.getParent().getSpatialSet();
        HashMap<String, Double> geoFactors = new HashMap<String, Double>();
        for (FusedRing fusedRing : fusedRingList) {
            if (!fusedRing.hasSpatialSet(targetParent)) {
                for (Ring ring : fusedRing.rings) {
                    String ringType = ring.type.name;
                    Double dSum = geoFactors.get(ringType);
                    if (dSum == null) {
                        dSum = new Double(0.0);
                    }
                    dSum += calcRingGeometricFactor(ring, targetPoint, iStruct);
                    geoFactors.put(ringType, dSum);
                }
            }
        }
        return geoFactors;
    }

    public double calcRingGeometricFactor(Ring ring, Vector3D targetPoint, final int iStruct) {
        if (!ring.isPlaneSet()) {
            setRingConformation(ring, iStruct);
        }
        Line line = new Line(targetPoint, targetPoint.add(ring.normal));
        Vector3D pointOnPlane = ring.plane.intersection(line);
        double geoSum = 0.0;
        for (int iPoint = 1; iPoint < ring.points.size(); iPoint++) {
            Vector3D ptStart = ring.points.get(iPoint - 1);
            Vector3D ptEnd = ring.points.get(iPoint);
            Vector3D crossProd = Vector3D.crossProduct(ptStart.subtract(pointOnPlane), ptEnd.subtract(pointOnPlane));
            double mag = crossProd.getNorm();
            double dotProd = ring.normal.dotProduct(crossProd);
            double area = -0.5 * Math.copySign(mag, dotProd);
            double rStart = Vector3D.distance(targetPoint, ptStart);
            double rEnd = Vector3D.distance(targetPoint, ptEnd);
            double distance = 1.0 / (rStart * rStart * rStart) + 1.0 / (rEnd * rEnd * rEnd);
            double G = distance * area;
            geoSum += G;
        }
        return geoSum;
    }

    public void makeRingList(MoleculeBase molecule) {
        Residue firstResidue;
        Residue lastResidue = null;
        Compound compound;
        fusedRingList.clear();
        for (CoordSet coordSet : molecule.coordSets.values()) {
            for (Entity entity : coordSet.getEntities().values()) {
                if (entity instanceof Polymer) {
                    Polymer polymer = (Polymer) entity;
                    firstResidue = polymer.getFirstResidue();

                    lastResidue = polymer.getLastResidue();
                    compound = (Compound) firstResidue;
                } else {
                    compound = (Compound) entity;
                }

                while (compound != null) {
                    FusedRing fusedRing = null;
                    String[] atomNames;
                    int iRing = 0;
                    while (true) {
                        String testName = compound.getName() + iRing;
                        RingType ringType = ringAtoms.get(testName);
                        if (ringType != null) {
                            atomNames = ringType.atomNames;
                            ArrayList<SpatialSet> spatialSetList = new ArrayList<SpatialSet>();
                            for (String atomName : atomNames) {
                                Atom atom = compound.getAtom(atomName);
                                SpatialSet spatialSet = atom.getSpatialSet();
                                spatialSetList.add(spatialSet);
                            }
                            spatialSetList.add(spatialSetList.get(0));
                            if (fusedRing == null) {
                                fusedRing = new FusedRing();
                                fusedRingList.add(fusedRing);
                            }
                            Ring ring = new Ring(ringType, spatialSetList);
                            fusedRing.add(ring);
                        } else {
                            break;
                        }
                        iRing++;
                    }
                    if (entity instanceof Polymer) {
                        if (compound == lastResidue) {
                            break;
                        }
                        compound = ((Residue) compound).next;
                    } else {
                        break;
                    }
                }
            }
        }
    }
}
