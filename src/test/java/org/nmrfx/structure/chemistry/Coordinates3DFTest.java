/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.structure.fastlinear.FastVector3D;

/**
 *
 * @author brucejohnson
 */
public class Coordinates3DFTest {

    @Test
    public void testCoordinates() {
        double[] dihedrals = getDihedrals();
        double[] zeros = new double[dihedrals.length];
        for (int j = 1; j < 180; j++) {
            double theta = Math.toRadians(j);
            double[] calcDihedral = calcDihedrals(dihedrals, false, theta);

            Assert.assertArrayEquals(zeros, calcDihedral, 1.0e-6);
        }
    }

    @Test
    public void testCoordinatesNew() {
        double[] dihedrals = getDihedrals();
        double[] zeros = new double[dihedrals.length];
        for (int j = 1; j < 180; j++) {
            double theta = Math.toRadians(j);
            double[] calcDihedral = calcDihedrals(dihedrals, true, theta);

            Assert.assertArrayEquals(zeros, calcDihedral, 1.0e-6);
        }
    }

    public static double[] getDihedrals() {
        int n = 361;
        double[] dihedrals = new double[n];
        for (int i = 0; i < n; i++) {
            double dihedral = Math.toRadians(i);
            dihedrals[i] = dihedral;
            if (dihedrals[i] >= Math.PI) {
                dihedrals[i] = dihedrals[i] - 2.0 * Math.PI;
            }
        }
        return dihedrals;
    }

    public static double[] calcDihedrals(double[] dihedrals, boolean newMode, double theta) {
        FastVector3D p1 = new FastVector3D(-1.0, -1.0, 0.0);
        FastVector3D p2 = new FastVector3D(0.0, -1.0, 0.0);
        FastVector3D p3 = new FastVector3D(0.0, 0.0, 0.0);
        FastVector3D p4 = new FastVector3D(0.0, 0.0, 0.0);

        Coordinates3DF c3d = new Coordinates3DF(p1, p2, p3);
        if (!newMode) {
            c3d.setup();
        } else {
            c3d.setupNeRF();
        }
        double bondLength = 1.5;
        double bndCos = bondLength * Math.cos(Math.PI - theta);
        double bndSin = bondLength * Math.sin(Math.PI - theta);
        int n = dihedrals.length;
        double[] calcDihedral = new double[n];
        for (int i = 0; i < n; i++) {
            if (!newMode) {
                c3d.calculate(dihedrals[i], bndCos, bndSin, p4);
            } else {
                c3d.calculateNeRF(dihedrals[i], bndCos, bndSin, p4);
            }
            calcDihedral[i] = FastVector3D.calcDihedral(p1, p2, p3, p4);
            if (calcDihedral[i] >= Math.PI) {
                calcDihedral[i] = calcDihedral[i] - 2.0 * Math.PI;
            }
//            System.out.println(Math.toDegrees(dihedrals[i]) + " " + Math.toDegrees(calcDihedral[i]));
            double delta = Math.abs(dihedrals[i] - calcDihedral[i]);
            if (delta > Math.PI) {
                delta = 2.0 * Math.PI - delta;
            }
            calcDihedral[i] = delta;
        }
        return calcDihedral;

    }

}
