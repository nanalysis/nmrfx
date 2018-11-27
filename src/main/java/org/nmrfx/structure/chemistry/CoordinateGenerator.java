package org.nmrfx.structure.chemistry;

import java.util.List;
import org.apache.commons.math3.util.FastMath;

public class CoordinateGenerator {

    public static void prepareAtoms(List<Atom> atoms) {
        for (Atom atom : atoms) {
            if (!atom.getPointValidity()) {
                atom.setPointValidity(true);
            }
            double bondLength = atom.bondLength;
            double valanceAngle = atom.valanceAngle;
            atom.bndSin = (float) (bondLength * FastMath.sin(Math.PI - valanceAngle));
            atom.bndCos = (float) (bondLength * FastMath.cos(Math.PI - valanceAngle));
        }
    }

    public static int[][] setupCoords(List<List<Atom>> atomTree) {
        int[][] genVecs = new int[atomTree.size()][];
        int iAtom = 0;
        for (List<Atom> branch : atomTree) {
            genVecs[iAtom] = new int[branch.size()];
            int jAtom = 0;
            int oStart = -1;
            if (branch.get(1) == null) {
                oStart = -2;
            }
            for (Atom atom : branch) {
                int index = atom == null ? oStart++ : atom.iAtom;
                genVecs[iAtom][jAtom++] = index;
                if (jAtom == 2) {
                    if (atom != null) {
                        System.out.println(atom.getFullName() + " " + atom.iAtom);
                    }
                }
            }
            iAtom++;
        }
        return genVecs;
    }

    public static int genCoords(int[][] genVecs, List<Atom> atoms) {
        return genCoords(genVecs, atoms, 0);
    }

    public static int genCoords(int[][] genVecs, List<Atom> atoms, int iStruct) {
        return genCoords(genVecs, atoms, iStruct, null);
    }

    public static int genCoords(int[][] genVecs, List<Atom> atoms, int iStruct, final double[] dihedrals) {
        int nAngles = 0;
        Atom a3 = atoms.get(genVecs[0][2]);
        if (!a3.getPointValidity(iStruct)) {
            a3.setPointValidity(iStruct, true);
        }
        Point3[] origins = new Point3[3];
        origins[0] = new Point3(-1.0, -1.0, 0.0);
        origins[1] = new Point3(-1.0, 0.0, 0.0);
        origins[2] = new Point3(0.0, 0.0, 0.0);

        Point3[] pts = new Point3[4];
        for (int i = 0; i < genVecs.length; i++) {
            if (genVecs[i].length > 3) {
                if (genVecs[i][0] < 0) {
//                    if (fillCoords) {
//                        //continue;
//                    }
                    pts[0] = origins[genVecs[i][0] + 2];
                } else {
                    pts[0] = atoms.get(genVecs[i][0]).spatialSet.getPoint(iStruct);
                }
                if (genVecs[i][1] < 0) {
//                    if (fillCoords) {
//                        //continue;
//                    }
                    pts[1] = origins[genVecs[i][1] + 2];
                } else {
                    pts[1] = atoms.get(genVecs[i][1]).spatialSet.getPoint(iStruct);
                }
                pts[2] = atoms.get(genVecs[i][2]).spatialSet.getPoint(iStruct);
                for (int j = 0; j < 3; j++) {
                    if (pts[j] == null) {
                        System.out.println(i + " " + j + " " + atoms.get(genVecs[i][j]).getShortName());
                    }
                }
                Coordinates coords = new Coordinates(pts[0], pts[1], pts[2]);
                if (!coords.setup()) {
                    //                    if (fillCoords) {
                    //                       continue;
                    //                    }
                    throw new RuntimeException("genCoords: coordinates the same for " + i + " " + pts[2].toString());
                }
                double dihedralAngle = 0;
                for (int j = 3; j < genVecs[i].length; j++) {
                    Atom a4 = atoms.get(genVecs[i][j]);
                    if (dihedrals == null) {
                        dihedralAngle += a4.dihedralAngle;
                    } else {
                        dihedralAngle += dihedrals[nAngles];
                    }
                    nAngles++;
                    if (!a4.getPointValidity()) {
                        a4.setPointValidity(true);
                        Point3 p4 = coords.calculate(dihedralAngle, a4.bndCos, a4.bndSin);
                        a4.setPoint(iStruct, p4);
                    }
                }
            }

        }
        return nAngles;
    }
}
