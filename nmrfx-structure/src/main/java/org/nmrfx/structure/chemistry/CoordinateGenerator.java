package org.nmrfx.structure.chemistry;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Coordinates;
import org.nmrfx.chemistry.Point3;

import java.util.List;

public class CoordinateGenerator {

    public static void prepareAtoms(List<Atom> atoms, boolean fillCoords) {
        for (Atom atom : atoms) {
            if (!fillCoords) {
                if (!atom.getPointValidity()) {
                    atom.setPointValidity(true);
                }
            }
            double bondLength = atom.bondLength;
            double valanceAngle = atom.valanceAngle;
            atom.bndSin = (float) (bondLength * Math.sin(Math.PI - valanceAngle));
            atom.bndCos = (float) (bondLength * Math.cos(Math.PI - valanceAngle));
            atom.bndSinNR = (float) (bondLength * Math.sin(valanceAngle));
            atom.bndCosNR = (float) (bondLength * Math.cos(valanceAngle));
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
        if (genVecs.length == 0) {
            return 0;
        }
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
                    pts[0] = origins[genVecs[i][0] + 2];
                } else {
                    pts[0] = atoms.get(genVecs[i][0]).spatialSet.getPoint(iStruct);
                }
                if (genVecs[i][1] < 0) {
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
                    throw new RuntimeException("genCoords: coordinates the same for "
                            + i + " " + pts[0].toString() + " "
                            + " " + pts[1].toString()
                            + " " + pts[2].toString()
                            + atoms.get(genVecs[i][2]).getFullName());
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
                    if (!a4.getPointValidity(iStruct)) {
                        a4.setPointValidity(true);
                        Point3 p4 = coords.calculate(dihedralAngle, a4.bndCos, a4.bndSin);
                        a4.setPoint(iStruct, p4);
                    }
                }
            }

        }
        return nAngles;
    }

    public static void dumpCoordsGen(int[][] genVecs, List<Atom> atomList) {
        System.out.printf("    %8s %8s %8s %8s %10s %10s %10s \n", "GPName", "PName", "Name", "DName", "BondL", "ValAng",
                "DihAng");
        if (genVecs == null) {
            return;
        }
        for (int i = 0; i < genVecs.length; i++) {
            if (genVecs[i].length > 3) {
                Atom atom = atomList.get(genVecs[i][2]);
                String angleDaughterName = atom.daughterAtom != null ? atom.daughterAtom.getShortName() : "____";
                String atomParentName = atom.parent != null ? atom.parent.getShortName() : "___";
                System.out.printf("%8s %8s %8s %4d %3d %3d\n", atomParentName, atom.getShortName(), angleDaughterName,
                        atom.irpIndex, atom.rotUnit, atom.iAtom);
                double dihedralAngle = 0;
                int a3Index = genVecs[i][2];
                int a2Index = genVecs[i][1];
                int a1Index = genVecs[i][0];
                Atom a3 = a3Index >= 0 ? atomList.get(a3Index) : null;
                Atom a2 = a2Index >= 0 ? atomList.get(a2Index) : null;
                Atom a1 = a1Index >= 0 ? atomList.get(a1Index) : null;
                for (int j = 3; j < genVecs[i].length; j++) {
                    int a4Index = genVecs[i][j];
                    if (a4Index < 0) {
                        continue;
                    }
                    Atom a4 = atomList.get(a4Index);

                    String name = a4.getShortName();
                    String parentName = a3 != null ? a3.getShortName() : a3Index + "";
                    String grandParentName = a2 != null ? a2.getShortName() : a2Index + "";
                    String greatGrandParentName = a1 != null ? a1.getShortName() : a1Index + "";
                    dihedralAngle += a4.dihedralAngle;
                    double dihedralAnglePrint = dihedralAngle * (180.0 / Math.PI);
                    double bondLength = a4.bondLength;
                    double valenceAngle = a4.valanceAngle * (180.0 / Math.PI);
                    System.out.printf("    %8s %8s %8s %8s %10.2f %10.3f %10.3f %3d\n", greatGrandParentName, grandParentName,
                            parentName, name, bondLength, valenceAngle, dihedralAnglePrint, a4.iAtom);

                }
            }
        }
    }
}
