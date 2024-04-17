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
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.Point3;
import org.nmrfx.chemistry.SpatialSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SuperMol {

    double[][] x;
    double[][] y;
    double[][] rotMatrix;
    double[] xCenter;
    double[] yCenter;
    double rms;
    MoleculeBase molecule;

    public SuperMol(MoleculeBase molecule) {
        this.molecule = molecule;
    }

    public SuperMol(String molName) {
        this.molecule = Molecule.get(molName);
    }

    public ArrayList<SuperResult> doSuper(int fixMol, int moveMol, boolean changeCoordinates) {
        int j = 0;
        SpatialSet spatialSet;
        Point3 pt1;
        Point3 pt2;
        double t_rms = 0.0;
        List<SpatialSet> selected = molecule.getAtomsByProp(Atom.SUPER);
        x = new double[selected.size()][3];
        y = new double[selected.size()][3];
        ArrayList<SuperResult> superRMS = new ArrayList<SuperResult>();
        int moveStructures[] = molecule.getActiveStructures();
        int fixStructures[] = moveStructures;
        if (fixMol >= 0) {
            fixStructures = new int[1];
            fixStructures[0] = fixMol;
        }
        if (moveMol >= 0) {
            moveStructures = new int[1];
            moveStructures[0] = moveMol;
        }

        for (int iFix : fixStructures) {
            for (int iMov : moveStructures) {
                if (iFix == iMov) {
                    continue;
                }
                j = 0;
                for (int i = 0; i < selected.size(); i++) {
                    spatialSet = selected.get(i);

                    pt1 = (Point3) spatialSet.getPoint(iFix);

                    if (pt1 == null) {
                        continue;
                    }

                    pt2 = (Point3) spatialSet.getPoint(iMov);

                    if (pt2 == null) {
                        continue;
                    }

                    x[j][0] = pt1.getX();
                    x[j][1] = pt1.getY();
                    x[j][2] = pt1.getZ();
                    y[j][0] = pt2.getX();
                    y[j][1] = pt2.getY();
                    y[j][2] = pt2.getZ();
                    j++;
                }

                if (j >= 3) {
                    cal_super(x, y, j);
                    SuperResult sResult = new SuperResult(iFix, iMov, rms);
                    superRMS.add(sResult);
                    t_rms += rms;
                    if (changeCoordinates) {
                        double[] t = new double[3];
                        double[] s = new double[3];
                        Iterator iter = molecule.getSpatialSetIterator();
                        while (iter.hasNext()) {
                            SpatialSet sSet = (SpatialSet) iter.next();
                            pt2 = sSet.getPoint(iMov);
                            if (pt2 != null) {
                                s[0] = pt2.getX();
                                s[1] = pt2.getY();
                                s[2] = pt2.getZ();
                                for (j = 0; j < 3; j++) {
                                    double temp = 0.0;
                                    for (int k = 0; k < 3; k++) {
                                        temp = temp + (rotMatrix[j][k] * (s[k] - yCenter[k]));
                                    }
                                    t[j] = temp + xCenter[j];
                                }
                                pt2 = new Point3(t[0], t[1], t[2]);
                                sSet.setPoint(iMov, pt2);
                            }
                        }
                    }
                }
            }
        }
        return superRMS;
    }

    public void cal_super(double[][] x, double[][] y, int n) {
        int i;
        int j;
        int k;
        int p;
        int q;
        int r;
        int iter;
        boolean done;
        double sigma;
        double gamm;
        double sig_gam;
        double temp_qk;
        double temp_rk;
        double temp;
        double del;
        double y00;
        double tol = 0.001;
        double[] t;

        double[][] corr;
        corr = new double[3][3];
        t = new double[3];

        rotMatrix = new double[3][3];
        xCenter = new double[3];
        yCenter = new double[3];

        for (i = 0; i < 3; i++) {
            xCenter[i] = 0.0;
            yCenter[i] = 0.0;
        }

        for (i = 0; i < n; i++) {
            for (j = 0; j < 3; j++) {
                xCenter[j] = xCenter[j] + x[i][j];
                yCenter[j] = yCenter[j] + y[i][j];
            }
        }

        for (j = 0; j < 3; j++) {
            xCenter[j] = xCenter[j] / n;
            yCenter[j] = yCenter[j] / n;
        }

        for (j = 0; j < 3; j++) {
            for (i = 0; i < 3; i++) {
                corr[i][j] = 0.0;
                rotMatrix[i][j] = 0.0;
            }

            rotMatrix[j][j] = 1.0;
        }

        for (k = 0; k < n; k++) {
            for (i = 0; i < 3; i++) {
                y00 = y[k][i] - yCenter[i];

                for (j = 0; j < 3; j++) {
                    corr[i][j] = corr[i][j] + (y00 * (x[k][j] - xCenter[j]));
                }
            }
        }

        p = -1;
        done = false;

        for (iter = 0; iter < 1024; iter++) {
            p++;

            if (p == 3) {
                if (done) {
                    break;
                } else {
                    p = 0;
                    done = true;
                }
            }

            q = p + 1;

            if (q == 3) {
                q = 0;
            }

            r = q + 1;

            if (r == 3) {
                r = 0;
            }

            sigma = corr[r][q] - corr[q][r];
            gamm = corr[q][q] + corr[r][r];
            sig_gam = Math.sqrt((sigma * sigma) + (gamm * gamm));

            if ((sig_gam != 0.0) && (Math.abs(sigma) > (tol * Math.abs(gamm)))) {
                sig_gam = 1.0 / sig_gam;

                for (k = 0; k < 3; k++) {
                    temp_qk = ((gamm * corr[q][k]) + (sigma * corr[r][k])) * sig_gam;
                    temp_rk = ((gamm * corr[r][k]) - (sigma * corr[q][k])) * sig_gam;
                    corr[q][k] = temp_qk;
                    corr[r][k] = temp_rk;
                    temp_qk = ((gamm * rotMatrix[q][k])
                            + (sigma * rotMatrix[r][k])) * sig_gam;
                    temp_rk = ((gamm * rotMatrix[r][k])
                            - (sigma * rotMatrix[q][k])) * sig_gam;
                    rotMatrix[q][k] = temp_qk;
                    rotMatrix[r][k] = temp_rk;
                }

                done = false;
            }
        }

        for (i = 0; i < n; i++) {
            for (j = 0; j < 3; j++) {
                temp = 0.0;

                for (k = 0; k < 3; k++) {
                    temp = temp + (rotMatrix[j][k] * (y[i][k] - yCenter[k]));
                }

                t[j] = temp + xCenter[j];
            }

            for (j = 0; j < 3; j++) {
                y[i][j] = t[j];
            }
        }

        rms = 0.0;

        for (i = 0; i < n; i++) {
            for (j = 0; j < 3; j++) {
                del = x[i][j] - y[i][j];
                rms = rms + (del * del);
            }
        }

        rms = Math.sqrt(rms / n);

        return;
    }
}
