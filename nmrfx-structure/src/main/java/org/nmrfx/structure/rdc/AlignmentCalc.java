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
package org.nmrfx.structure.rdc;

import org.apache.commons.math3.geometry.enclosing.EnclosingBall;
import org.apache.commons.math3.geometry.enclosing.WelzlEncloser;
import org.apache.commons.math3.geometry.euclidean.threed.*;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomEnergyProp;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.Point3;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class AlignmentCalc {

    static Vector3D BVEC = new Vector3D(0.0, 0.0, 1.0);
    static Vector3D[] VECS = {
            new Vector3D(1.0, 0.0, 0.0),
            new Vector3D(0.0, 1.0, 0.0),
            new Vector3D(0.0, 0.0, 1.0)};

    List<Vector3D> vectors = new ArrayList<>();
    List<Double> masses = new ArrayList<>();
    List<AngleMinimum> angleMinimums = new ArrayList<>();
    Vector3D center;
    double radius;
    double atomRadius = 2.0;
    List<double[]> angles = new ArrayList<>();
    EnclosingBall ball;
    AlignmentMatrix alignmentMatrix;
    RealMatrix directionMatrix;
    int nFreePositions;
    int nConstrainedPositions;
    double globalScale;

    public class AngleMinimum {

        Rotation rot;
        double alpha;
        double beta;
        double min;
        double max;
        Vector3D[] vecs;
        int count = 0;
        double scale;

        public double getAlpha() {
            return alpha;
        }

        public double getBeta() {
            return beta;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public AngleMinimum(Rotation rot, double scale) {
            this.rot = rot;
            this.scale = scale;
        }

        public AngleMinimum(Rotation rot, double min, double max, Vector3D[] vecs) {
            this.rot = rot;
            this.min = min;
            this.max = max;
            this.vecs = vecs;
        }

        public AngleMinimum(double alpha, double beta, double min, double max, Vector3D[] vecs) {
            this.alpha = alpha;
            this.beta = beta;
            this.min = min;
            this.max = max;
            this.vecs = vecs;
        }

        public void incrCount() {
            count++;
        }

        public int getCount() {
            return count;
        }
    }

    public AlignmentCalc() {

    }

    public void makeSphere(int n, double r, double scale) {
        Vector3D zVec = new Vector3D(0.0, 0.0, 1.0);
        double a = 4.0 * Math.PI * r * r / n;
        double d = Math.sqrt(a);
        int mAlpha = (int) Math.round(Math.PI / d);
        double dAlpha = Math.PI / mAlpha;
        double dBeta = a / dAlpha;
        vectors.clear();
        for (int i = 0; i < mAlpha; i++) {
            double alpha = Math.PI * (i + 0.5) / mAlpha;
            int mBeta = (int) Math.round(2.0 * Math.PI * Math.sin(alpha) / dBeta);
            for (int j = 0; j < mBeta; j++) {
                double beta = 2.0 * Math.PI * (j + 0.5) / mBeta;
                Rotation rot = new Rotation(RotationOrder.XZX, RotationConvention.FRAME_TRANSFORM, alpha, beta, 0.0);
                Vector3D rZVec = rot.applyTo(zVec).scalarMultiply(scale);
                vectors.add(rZVec);
            }
        }
    }

    public void makeCylinder(int n, double rX, double rY, double h) {

        vectors.clear();
        int mBeta = 20;
        for (int i = 0; i < n; i++) {
            double z = i * h / n;
            double r = Math.min(rX, rY);
            double f = 1.0;
            double deltaEnd = 0;
            if (z < r) {
                f = (z + 0.1) / (r + 0.1);

            } else if (z > (h - r)) {
                deltaEnd = h - z;
                f = (deltaEnd + 0.1) / (r + 0.1);
            }
            f = Math.sqrt(f);
            for (int j = 0; j < mBeta; j++) {
                double beta = 2.0 * Math.PI * (j + 0.5) / mBeta;
                double x = Math.sin(beta) * f * rX;
                double y = Math.cos(beta) * f * rY;
                Vector3D rZVec = new Vector3D(x, y, z - h / 2.0);
                vectors.add(rZVec);
            }
        }
    }

    public AlignmentCalc(List<Vector3D> vectors) {
        this.vectors = vectors;
    }

    public AlignmentCalc(MoleculeBase molecule, boolean useH, double atomRadius) {
        this.atomRadius = atomRadius;
        for (Atom atom : molecule.getAtomArray()) {
            if (atom.isCoarse() || atom.isPlanarity()) {
                continue;
            }
            Point3 point3d = atom.getPoint();
            if (point3d != null) {
                if (useH || (atom.getAtomicNumber() != 1)) {
                    vectors.add(point3d);
                    AtomEnergyProp prop = AtomEnergyProp.get(atom.getType());
                    if (prop != null) {
                        atom.mass = prop.getMass();
                        masses.add(atom.mass);
                    } else {
                        masses.add(1.0);
                    }
                }
            }
        }
    }

    public void makeEnclosingSphere() {
        SphereGenerator sphereGen = new SphereGenerator();
        WelzlEncloser welzl = new WelzlEncloser(0.1, sphereGen);
        ball = welzl.enclose(vectors);
    }

    public EnclosingBall getBall() {
        return ball;
    }

    public Vector3D getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;

    }

    public void center() {
        Vector3D sum = new Vector3D(0.0, 0.0, 0.0);
        double totalMass = 0.0;
        int j = 0;
        for (Vector3D vector : vectors) {
            double mass = masses.isEmpty() ? 1.0 : masses.get(j);
            sum = sum.add(vector.scalarMultiply(mass));
            totalMass += mass;
            j++;
        }
        center = sum.scalarMultiply(1.0 / totalMass);
        double max = 0.0;
        for (int i = 0; i < vectors.size(); i++) {
            vectors.set(i, vectors.get(i).subtract(center));
            max = Math.max(max, Math.abs(vectors.get(i).getX()));
            max = Math.max(max, Math.abs(vectors.get(i).getY()));
            max = Math.max(max, Math.abs(vectors.get(i).getZ()));
        }
        radius = max;
    }

    public int getConstrained() {
        return nConstrainedPositions;
    }

    public List<Vector3D> getVectors() {
        return vectors;
    }

    public void genVectors() {
        Vector3D initialVec = new Vector3D(0.0, 0.0, 1.0);
        genVectors(initialVec);
    }

    public void genVectors(Vector3D initialVec) {
        for (AngleMinimum aMin : angleMinimums) {
            vectors.add(aMin.rot.applyTo(initialVec));
        }
    }

    public void genScaledVectors(Vector3D initialVec) {
        vectors.clear();
        for (AngleMinimum aMin : angleMinimums) {
            Vector3D vec = aMin.rot.applyTo(initialVec).scalarMultiply(aMin.getMin());
            vectors.add(vec);
        }
    }

    public double[] minMax() {
        double gmin = Double.MAX_VALUE;
        double gmax = 0.0;
        for (int i = 0; i < vectors.size() - 1; i++) {
            Vector3D vec1 = vectors.get(i);
            double min = Double.MAX_VALUE;
            for (int j = i + 1; j < vectors.size(); j++) {
                Vector3D vec2 = vectors.get(j);
                double dis = vec1.distance(vec2);
                min = Math.min(min, dis);

            }
            gmin = Math.min(gmin, min);
            gmax = Math.max(gmax, min);
        }
        double[] result = {gmin, gmax};
        return result;
    }

    public int genAngles(int n, int nGamma, double r) {
        Vector3D zVec = new Vector3D(0.0, 0.0, 1.0);
        int nAngles = 0;
        double a = 4.0 * Math.PI * r * r / n;
        double d = Math.sqrt(a);
        int mAlpha = (int) Math.round(Math.PI / d);
        double dAlpha = Math.PI / mAlpha;
        double dBeta = a / dAlpha;
        angleMinimums.clear();
        for (int i = 0; i < mAlpha; i++) {
            double alpha = Math.PI * (i + 0.5) / mAlpha;
            int mBeta = (int) Math.round(2.0 * Math.PI * Math.sin(alpha) / dBeta);
            double mBetaD = (2.0 * Math.PI * Math.sin(alpha) / dBeta);
            double scale = mBetaD / mBeta;
            for (int j = 0; j < mBeta; j++) {
                double beta = 2.0 * Math.PI * (j + 0.5) / mBeta;
                for (int h = 0; h < nGamma; h++) {
                    double gamma = (h + 0.5) * (2.0 * Math.PI) / nGamma;
                    nAngles++;
                    Rotation rot = new Rotation(RotationOrder.XZX, RotationConvention.FRAME_TRANSFORM, alpha, beta, 0.0);
                    Vector3D rZVec = rot.applyTo(zVec);
                    Rotation rot2 = new Rotation(rZVec, gamma, RotationConvention.FRAME_TRANSFORM);
                    Rotation rot3 = rot.compose(rot2, RotationConvention.FRAME_TRANSFORM);
                    AngleMinimum aMin = new AngleMinimum(rot3, scale);
                    angleMinimums.add(aMin);
                }
            }
        }
        return nAngles;
    }

    public void findMinimums() {
        for (AngleMinimum angleMin : angleMinimums) {
            Vector3D[] vecs = new Vector3D[3];
            double[] minMax = adjust(angleMin.rot);
            for (int i = 0; i < 3; i++) {
                vecs[i] = angleMin.rot.applyTo(VECS[i]);
            }
            angleMin.min = minMax[0];
            angleMin.max = minMax[1];
            angleMin.vecs = vecs;
        }

        angleMinimums.sort((a, b) -> Double.compare(b.min, a.min));
    }

    public void calcExclusions(double slabWidth, double f, double d, String mode) {
        double rMid;
        double delFree;
        if (mode.equals("bicelle")) {
            rMid = d / (2.0 * f);
            delFree = d / 2.0;
        } else {
            rMid = d / Math.sqrt(4.0 * f);
            delFree = d / 2.0;
        }
        System.out.println(f + " d " + d + " rmid " + rMid);
        AngleMinimum firstAMin = angleMinimums.get(0);
        AngleMinimum lastAMin = angleMinimums.get(angleMinimums.size() - 1);
        double firstMin = firstAMin.getMin();
        double lastMin = lastAMin.getMin();
        nFreePositions = (int) ((rMid - d / 2 + lastMin) / slabWidth);
        nConstrainedPositions = (int) ((firstMin - lastMin) / slabWidth);

        for (AngleMinimum aMin : angleMinimums) {
            double min1 = aMin.getMin();
            double min2 = aMin.getMax();
            double extra1 = firstMin - min1;
            double extra2 = firstMin + min2;
            int nSkip1 = (int) ((extra1 - slabWidth) / slabWidth);
            int nSkip2 = (int) ((extra2 - slabWidth) / slabWidth);
            int nSkip = nSkip1;
            nSkip = Math.max(0, nSkip);
            aMin.count = nSkip;
        }
        globalScale = 0.5 * (double) nConstrainedPositions / (nConstrainedPositions + nFreePositions);
    }

    public void calcCylExclusions(double slabWidth, double f, double d, String mode) {
        double rMid;
        if (mode.equals("bicelle")) {
            rMid = d / (2.0 * f);
        } else {
            rMid = d / Math.sqrt(f * 2.0);
        }
        System.out.println(f + " d " + d + " rmid " + rMid);
        int n = (int) ((rMid - d / 2.0) / slabWidth);
        int nAllOverlap = 0;
        for (int i = 0; i < n; i++) {
            boolean anyOverlap = false;
            boolean allOverlap = true;
            for (AngleMinimum aMin : angleMinimums) {
                double xOffset = d / 2.0 + i * slabWidth;
                boolean overlaps;
                if (mode.equals("bicelle")) {
                    overlaps = calcOverlap(aMin.rot, xOffset, d / 2.0);
                } else {
                    overlaps = calcCylOverlap(aMin.rot, xOffset, d / 2.0);
                }
                if (overlaps) {
                    anyOverlap = true;
                    aMin.count++;
                } else {
                    allOverlap = false;
                }
            }
            if (allOverlap) {
                nAllOverlap++;
            }
            if (!anyOverlap) {
                nConstrainedPositions = i;
                break;
            }
        }
        for (AngleMinimum aMin : angleMinimums) {
            aMin.count -= nAllOverlap;
        }
        nConstrainedPositions -= nAllOverlap;
        double constrainedSize;
        double freeSize;
        double fullSize;
        double gScale;
        double scale;
        if (mode.equals("bicelle")) {
            double halfWall = d / 2.0;
            double overlapThickness = nAllOverlap * slabWidth;
            double constrainedThickness = nConstrainedPositions * slabWidth;
            double overlapSize = 2.0 * overlapThickness;
            constrainedSize = 2.0 * constrainedThickness;
            fullSize = d / f;
            freeSize = fullSize - d - constrainedSize - overlapSize;
            scale = 0.5;

        } else {
            double r = d / 2.0;
            double areaPerCyl = Math.PI * r * r / f;
            double overlapThickness = nAllOverlap * slabWidth;
            double rR = r + overlapThickness;
            double constrainedThickness = nConstrainedPositions * slabWidth;
            double rC = r + overlapThickness + constrainedThickness;
            double cylArea = Math.PI * r * r;
            double cylRestrictedArea = Math.PI * rR * rR;
            double cylConstrainedArea = Math.PI * rC * rC;
            freeSize = areaPerCyl - cylConstrainedArea;
            constrainedSize = cylConstrainedArea - cylRestrictedArea;
            scale = 0.5;
        }
        globalScale = scale * constrainedSize / (constrainedSize + freeSize);

        nFreePositions = n - nAllOverlap - nConstrainedPositions;
        System.out.println("n " + n + " nPos " + nAllOverlap + " nFree "
                + nFreePositions + " nConstr " + nConstrainedPositions
                + " ggs " + globalScale);
    }

    public void calcTensor(double lcS) {
        double[][] sMat = new double[3][3];
        double[][] sMat2 = new double[3][3];
        double total = 0;
        double[] sumDots = {0.0, 0.0, 0.0};
        for (AngleMinimum aMin : angleMinimums) {
            total += nConstrainedPositions - aMin.getCount();
        }
        double totalScale = 0.0;
        for (AngleMinimum aMin : angleMinimums) {
            double scale = (nConstrainedPositions - aMin.getCount()) / total;
            double scaleUniform = 1.0 / angleMinimums.size();
            double[] dots = new double[3];
            for (int i = 0; i < 3; i++) {
                dots[i] = aMin.vecs[i].dotProduct(BVEC);// * aMin.scale;
            }
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    double k = i == j ? 1.0 : 0.0;
                    sMat[i][j] += -1.0 * ((3.0 * dots[i] * dots[j] - k) / 2.0) * scale * lcS;
                    sMat2[i][j] += -1.0 * ((3.0 * dots[i] * dots[j] - k) / 2.0) * scaleUniform * lcS;
                }
            }
            sumDots[0] += dots[0] * dots[0];
            sumDots[1] += dots[1] * dots[1];
            sumDots[2] += dots[2] * dots[2];
            totalScale += scale;
        }

        RealMatrix saupeMat2 = new Array2DRowRealMatrix(sMat2);
        RealMatrix saupeMat = new Array2DRowRealMatrix(sMat);
        saupeMat = saupeMat.subtract(saupeMat2);
        alignmentMatrix = new AlignmentMatrix(saupeMat, globalScale);
    }

    public AlignmentMatrix getAlignment() {
        return alignmentMatrix;
    }

    public List<AngleMinimum> getMinimums() {
        return angleMinimums;
    }

    public double[] adjust(Rotation rot) {
        double minX = Double.MAX_VALUE;
        double maxX = Double.NEGATIVE_INFINITY;
        for (Vector3D vector : vectors) {
            Vector3D rotVector = rot.applyTo(vector);
            minX = Math.min(minX, rotVector.getX() - atomRadius);
            maxX = Math.max(maxX, rotVector.getX() + atomRadius);
        }
        double[] result = {minX, maxX};
        return result;
    }

    public boolean calcCylOverlap(Rotation rot, double deltaX, double limit) {
        double limitSq = limit * limit;
        boolean result = false;
        for (Vector3D vector : vectors) {
            Vector3D rotVector = rot.applyTo(vector);
            double x = rotVector.getX() + deltaX;
            double y = rotVector.getY();
            double deltaSq = x * x + y * y;
            if (deltaSq < limitSq) {
                result = true;
                break;
            }
        }
        return result;
    }

    public boolean calcOverlap(Rotation rot, double deltaX, double limit) {
        boolean result = false;
        for (Vector3D vector : vectors) {
            Vector3D rotVector = rot.applyTo(vector);
            double x = rotVector.getX() + deltaX;
            if (x < limit) {
                result = true;
                break;
            }
        }
        return result;
    }

}
