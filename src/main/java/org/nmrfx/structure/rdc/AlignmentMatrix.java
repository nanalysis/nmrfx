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

import java.util.List;
import org.apache.commons.math3.geometry.euclidean.threed.NotARotationMatrixException;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention;
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 *
 * @author brucejohnson
 */
public class AlignmentMatrix {

    final RealMatrix saupeMat;
    final EigenDecomposition eig;
    final RealMatrix Sdiag;
    final double globalScale;
    double[][] euler;

    public AlignmentMatrix(RealMatrix saupeMat, double globalScale) {
        this.saupeMat = saupeMat;
        this.globalScale = globalScale;
        eig = new EigenDecomposition(saupeMat);
        Sdiag = eig.getD();
    }

    public AlignmentMatrix(double sZZ, double sXXminusYY, double sXY, double sXZ, double sYZ) {
        double sXX = (sXXminusYY - sZZ) / 2.0;
        double sYY = -sXX - sZZ;
        double[][] s = {{sXX, sXY, sXZ}, {sXY, sYY, sYZ}, {sXZ, sYZ, sZZ}};
        saupeMat = new Array2DRowRealMatrix(s);
        this.globalScale = 1.0;
        eig = new EigenDecomposition(saupeMat);
        Sdiag = eig.getD();
    }

    public EigenDecomposition getEig() {
        return eig;
    }

    public double getScale() {
        return globalScale;
    }

    public RealMatrix getScaledMatrix() {
        return saupeMat.scalarMultiply(globalScale);
    }

    /**
     * Returns the order matrix S.
     *
     * @return RealMatrix 3x3 order matrix S.
     */
    public RealMatrix getSMatrix() {
        return saupeMat;
    }

    /**
     * Returns the diagonalized order matrix S.
     *
     * @return RealMatrix Diagonalized 3x3 order matrix S.
     */
    public RealMatrix getSDiag() {
        return Sdiag;
    }

    /**
     * Returns the Szz element of the diagonalized order matrix S.
     *
     * @return double Sz'z' element of the diagonalized order matrix S.
     */
    public double getSzz() {
        return Sdiag.getEntry(2, 2);
    }

    /**
     * Returns the Syy element of the diagonalized order matrix S.
     *
     * @return double Sy'y' element of the diagonalized order matrix S.
     */
    public double getSyy() {
        return Sdiag.getEntry(1, 1);
    }

    /**
     * Returns the Sxx element of the diagonalized order matrix S.
     *
     * @return double Sx'x' element of the diagonalized order matrix S.
     */
    public double getSxx() {
        return Sdiag.getEntry(0, 0);
    }

    /**
     * Calculates the eta value.
     *
     * @return double Asymmetry parameter: eta = (Sy'y' - Sx'x')/Sz'z'. S is the
     * diagonalized order matrix.
     */
    public double calcEta() {
        return (getSyy() - getSxx()) / getSzz();
    }

    /**
     * Calculates the axial component of the diagonalized order matrix S.
     *
     * @return double axial component = (1/3)*(Sz'z' - (Sx'x' + Sy'y')/2)
     */
    public double calcSAxial() {
//        double axial = 0.333*(getSzz() - (getSxx() + getSyy())/2); //from J. Am. Chem. Soc., Vol. 121, No. 39, 1999
        double axial = 0.5 * (getSzz()); //from PALES article: NATURE PROTOCOLS|VOL.3 NO.4|2008
        return axial;
    }

    /**
     * Calculates the rhombic component of the diagonalized order matrix S.
     *
     * @return double rhombic component = (1/3)*(Sx'x' - Sy'y')
     */
    public double calcSRhombic() {
        double rhombic = 0.333 * (getSxx() - getSyy());
        return rhombic;
    }

    /**
     * Calculates the rhombicity of the diagonalized order matrix S.
     *
     * @return double rhombicity = (rhombic component of S)/(axial component of
     * S).
     */
    public double calcRhombicity() {
        return calcSRhombic() / calcSAxial();
    }

    /**
     * Calculates the magnitude of the diagonalized order matrix S.
     *
     * @return double magnitude = (1/2)*maxRDCs[index of
     * max(abs(maxRDCs))]*(axial component of S)
     */
//    public double calcMagnitude() {
//        double[] absMaxRDCs = new double[maxRDCs.length];
//        for (int i = 0; i < absMaxRDCs.length; i++) {
//            absMaxRDCs[i] = Math.abs(maxRDCs[i]);
//        }
//        double maxAbsRDC = Arrays.stream(absMaxRDCs).max().getAsDouble();
//        int maxIndex = Arrays.stream(absMaxRDCs).boxed().collect(Collectors.toList()).indexOf(maxAbsRDC);
//
//        return 0.5 * maxRDCs[maxIndex] * calcSAxial();
//    }
    public double[][] calcEulerAngles(double[][] R) {
        double Rxz = R[0][2];
        double Ryz = R[1][2];
        double Rzz = R[2][2];
        double Rzx = R[2][0];
        double Rzy = R[2][1];

        double alpha = Math.atan(Ryz / Rxz);
        double alpha2 = Math.atan(Ryz / -Rxz);
        double beta = Math.acos(Rzz);
        double beta2 = Math.acos(-Rzz);
        double gamma = Math.atan(Rzy / -Rzx);

        double[][] euler = {{alpha, beta, gamma}, {alpha2, beta2, gamma}};

        return euler;
    }

    /**
     * Sets the Euler angles.
     *
     * @param eulerVals
     */
    public void setEulerAngles(double[][] eulerVals) {
        euler = eulerVals;
    }

    public void calcAlignment() {

        double[][] rotMat = eig.getVT().getData();
        Rotation rot = null;

        try {
            rot = new Rotation(rotMat, 1e-6);
        } catch (NotARotationMatrixException nE) {
            for (int i = 0; i < 3; i++) {
                rotMat[1][i] = -rotMat[1][i];
            }
            try {
                rot = new Rotation(rotMat, 1e-6);
            } catch (NotARotationMatrixException nE2) {
                System.out.println("Can't create rot mat 2nd try:" + nE.getMessage());
                rot = null;
            }

        }
        if (rot != null) {
            double[] euler = rot.getAngles(RotationOrder.ZYZ, RotationConvention.VECTOR_OPERATOR);

            double[][] eulerRot = new double[2][3];
            double[][] eulerCalc = calcEulerAngles(rotMat);
            for (int i = 0; i < euler.length; i++) {
                double angle = euler[i] * 180.0 / Math.PI;
                double angle1 = eulerCalc[0][i] * 180.0 / Math.PI;
                double angle2 = eulerCalc[1][i] * 180.0 / Math.PI;
                if (i == 0) {
                    angle += 90.;
                    angle1 += 90.;
                    angle2 += 90.;
                } else if (i == 2) {
                    angle += 180.;
                }
                eulerRot[0][i] = angle;
                eulerRot[1][i] = angle2;
                eulerCalc[0][i] = angle1;
                eulerCalc[1][i] = angle2;
            }

            setEulerAngles(eulerCalc);

        }
    }

    public static RealMatrix setupDirectionMatrix(List<RDCVector> vectors) {
        int nVectors = vectors.size();
        double[][] A = new double[nVectors][5];
        int iRow = 0;
        // calculate the direction cosines and construct the matrix A. Based on orderten_svd_dipole.c
        for (RDCVector rdcVec : vectors) {
            Vector3D normVec = rdcVec.vector.normalize();
            double dcosX = normVec.getX();
            double dcosY = normVec.getY();
            double dcosZ = normVec.getZ();
            double ddcosX = dcosX * dcosX;
            double ddcosY = dcosY * dcosY;
            double ddcosZ = dcosZ * dcosZ;
            A[iRow][0] = ddcosY - ddcosX;
            A[iRow][1] = ddcosZ - ddcosX;
            A[iRow][2] = 2 * dcosX * dcosY; //2 * ddcosX * ddcosY;
            A[iRow][3] = 2 * dcosX * dcosZ; //2 * ddcosX * ddcosZ;
            A[iRow][4] = 2 * dcosY * dcosZ; //2 * ddcosY * ddcosZ;
            iRow++;
        }
        RealMatrix directionMatrix = new Array2DRowRealMatrix(A);
        return directionMatrix;
    }

    public void calcRDC(RealMatrix directionMatrix, double maxRDC, List<RDCVector> vectors) {
        RealMatrix scaledMat = getScaledMatrix();
        double sYY = scaledMat.getEntry(1, 1);
        double sZZ = scaledMat.getEntry(2, 2);
        double sXY = scaledMat.getEntry(0, 1);
        double sXZ = scaledMat.getEntry(0, 2);
        double sYZ = scaledMat.getEntry(1, 2);
        double[] vals = {sYY, sZZ, sXY, sXZ, sYZ};
        RealVector sVec = new ArrayRealVector(vals);
        RealVector result = directionMatrix.operate(sVec);
        result.mapMultiplyToSelf(maxRDC);
        for (int i = 0; i < vectors.size(); i++) {
            RDCVector rdcVec = vectors.get(i);
            double rdc = result.getEntry(i);
            rdcVec.setRDC(rdc);
        }
    }

    public void dump() {
        System.out.println("Euler Angles for clockwise rotation about z, y', z''\n");
        System.out.println("Alpha\tBeta\tGamma\n");
        System.out.println(String.format("%.3f\t%.3f\t%.3f\n", euler[0][0], euler[0][1], euler[0][2]));
        System.out.println(String.format("%.3f\t%.3f\t%.3f\n", euler[0][0] + 180., euler[0][1], euler[0][2]));
        System.out.println(String.format("%.3f\t%.3f\t%.3f\n", euler[1][0], euler[1][1], euler[1][2] + 180.));
        System.out.println(String.format("%.3f\t%.3f\t%.3f\n\n", euler[1][0] + 180., euler[1][1], euler[1][2] + 180.));

    }
}
