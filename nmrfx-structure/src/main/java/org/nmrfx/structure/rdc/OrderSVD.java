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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.geometry.euclidean.threed.*;
import org.apache.commons.math3.linear.*;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.RDCConstraint;
import org.nmrfx.chemistry.constraints.RDCConstraintSet;
import org.nmrfx.star.ParseException;
import org.nmrfx.structure.chemistry.Molecule;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class OrderSVD {

    private static final Logger log = LoggerFactory.getLogger(OrderSVD.class);
    static double mu0 = 4.0e-7 * Math.PI;
    static double hbar = 1.054e-34;
    static double preFactor = -(mu0 * hbar) / (4 * (Math.PI * Math.PI));
    static HashMap<String, Double> maxRDCDict = new HashMap<>();
    static HashMap<String, Double> gammaIDict = new HashMap();
    static HashMap<String, Double> gammaSDict = new HashMap();

    static {
        maxRDCDict.put("HN", 24350.0);
        maxRDCDict.put("HC", -60400.0);
        maxRDCDict.put("HH", -240200.0);
        maxRDCDict.put("CN", 6125.0);
        maxRDCDict.put("CC", -15200.0);
        maxRDCDict.put("NH", 24350.0);
        maxRDCDict.put("CH", -60400.0);
        maxRDCDict.put("NC", 6125.0);
        gammaIDict.put("N", -2.71e7);
        gammaIDict.put("C", 6.73e7);
        gammaIDict.put("H", 2.68e8);
        gammaSDict.put("N", -2.71e7);
        gammaSDict.put("C", 6.73e7);
        gammaSDict.put("H", 2.68e8);
    }

    static double gammaH = 2.68e8;
    static double gammaN = -2.71e7;
    static double scaleHN = (gammaH * gammaN) / ((1.0e-10) * (1.0e-10) * (1.0e-10));

    RealMatrix AR;
    ArrayRealVector bVec;
    ArrayRealVector bVecUnNorm;
    RealVector xVec;
    RealMatrix SR;
    EigenDecomposition eig;
    RealMatrix Sdiag;
    double Q;
    double Qrhomb;
    double[] maxRDCs;
    RealVector dcDiffs;
    double[] bCalc;
    double[] bCalcNorm;
    double[][] euler;
    RDCConstraintSet rdcSet;

    /**
     * This function calculates residual dipolar couplings. Based on
     * orderten_svd_dipole.c
     *
     * @param vectors List of bond vectors
     * @param dc      List of unnormalized experimental dipolar couplings
     * @param maxRDC  List of maximum static dipolar coupling values (r^(-3))
     * @param error   List of error values
     */
    public OrderSVD(List<Vector3D> vectors, List<Double> dc, double[] maxRDC, List<Double> error) {
        int nVectors = vectors.size();
        double[][] A = new double[nVectors][5];
        int iRow = 0;
        // calculate the direction cosines and construct the matrix A. Based on orderten_svd_dipole.c
        for (Vector3D vec3D : vectors) {
            Vector3D normVec = vec3D.normalize();
            double dcosX = normVec.getX();
            double dcosY = normVec.getY();
            double dcosZ = normVec.getZ();
            double ddcosX = dcosX * dcosX;
            double ddcosY = dcosY * dcosY;
            double ddcosZ = dcosZ * dcosZ;
            A[iRow][0] = ddcosY - ddcosX;
            A[iRow][1] = ddcosZ - ddcosX;
            A[iRow][2] = 2 * dcosX * dcosY;
            A[iRow][3] = 2 * dcosX * dcosZ;
            A[iRow][4] = 2 * dcosY * dcosZ;
            iRow++;
        }

        AR = new Array2DRowRealMatrix(A);
        // perform SVD on the matrix A
        SingularValueDecomposition svd = new SingularValueDecomposition(AR);
        // construct the b vector, which contains the normalized dipolar couplings 
        double[] dcNorm = new double[dc.size()];
        double[] dcUnNorm = new double[dc.size()];
        for (int i = 0; i < dcNorm.length; i++) {
            dcNorm[i] = dc.get(i) / maxRDC[i];
            dcUnNorm[i] = dc.get(i);
        }
        bVec = new ArrayRealVector(dcNorm);
        bVecUnNorm = new ArrayRealVector(dcUnNorm);
        int Slen = 3;
        double[][] S = new double[Slen][Slen];
        Random random = new Random();
        double[] rand = new double[error.size()];
        int cycle = 0;
        while (true) {
            System.out.println("cycle " + cycle);
            // calculate the x vector for which |Ax - b| is minimized
            xVec = svd.getSolver().solve(bVec);
            // construct the order matrix S from the x vector and diagonalize it
            S[0][0] = -xVec.getEntry(0) - xVec.getEntry(1);
            S[0][1] = xVec.getEntry(2);
            S[0][2] = xVec.getEntry(3);
            S[1][0] = xVec.getEntry(2);
            S[1][1] = xVec.getEntry(0);
            S[1][2] = xVec.getEntry(4);
            S[2][0] = xVec.getEntry(3);
            S[2][1] = xVec.getEntry(4);
            S[2][2] = xVec.getEntry(1);

            if ((S[0][0] >= -0.5 && S[0][0] <= 1.0) && (S[1][1] >= -0.5 && S[1][1] <= 1.0) && (S[2][2] >= -0.5 && S[2][2] <= 1.0)
                    && (S[1][0] >= -0.75 && S[1][0] <= 0.75) && (S[2][0] >= -0.75 && S[2][0] <= 0.75) && (S[2][1] >= -0.75 && S[2][1] <= 0.75)) {
                SR = new Array2DRowRealMatrix(S);
                eig = new EigenDecomposition(SR);
                System.out.println("eig decomp getV = " + eig.getV().toString());
                System.out.println("eig decomp getVT = " + eig.getVT().toString());
                Sdiag = eig.getD();
                double Sxx = Sdiag.getEntry(0, 0);
                double Syy = Sdiag.getEntry(1, 1);
                double Szz = Sdiag.getEntry(2, 2);
                if ((Sxx >= -0.5 && Sxx <= 1.0) && (Syy >= -0.5 && Syy <= 1.0) && (Szz >= -0.5 && Szz <= 1.0)) {
                    System.out.println("Converged after " + cycle + " cycles");
                    System.out.println("x = " + xVec.toString());
                    System.out.println("S = " + SR.toString());
                    System.out.println("Seig = " + Sdiag.toString());
                    System.out.println("Sz'z' = " + Szz);
                    System.out.println("eta = " + (Syy - Sxx) / Szz);
                    double axial = 0.333 * (Szz - (Sxx + Syy) / 2);
                    double rhombic = 0.333 * (Sxx - Syy);
                    System.out.println("alignment tensor axial component = " + axial);
                    System.out.println("alignment tensor rhombic component = " + rhombic);
                    System.out.println("rhombicity = " + rhombic / axial);
                    break;
                } else {
                    for (int i = 0; i < error.size(); i++) {
                        rand[i] = random.nextGaussian() * error.get(i) + dcNorm[i];
                    }
                    ArrayRealVector randVec = new ArrayRealVector(rand);
                    bVec = randVec;
                    cycle++;
                }
            } else {
                for (int i = 0; i < error.size(); i++) {
                    rand[i] = random.nextGaussian() * error.get(i) + dcNorm[i];
                }
                ArrayRealVector randVec = new ArrayRealVector(rand);
                bVec = randVec;
                cycle++;
            }
        }
    }

    /**
     * Reads input files that are formatted in the same way as example.inp.
     * Example file located in orderten_svd_dipole.c
     *
     * @param file String of the name of the file.
     * @return List of Lists of vectors, unnormalized RDCConstraint values, maxRDC values,
     * error values, atom names, and XYZ coordinates for both atoms.
     */
    public static List readInputFile(String file) {
        ArrayList<Vector3D> vectors = new ArrayList<>();
        ArrayList<Double> dc = new ArrayList<>();
        ArrayList<Double> maxRDC = new ArrayList<>();
        ArrayList<Double> errors = new ArrayList<>();
        ArrayList<String> atoms = new ArrayList<>();
        ArrayList<Double[]> a1Coords = new ArrayList<>();
        ArrayList<Double[]> a2Coords = new ArrayList<>();

        try (BufferedReader bf = new BufferedReader(new FileReader(file));
             LineNumberReader lineReader = new LineNumberReader(bf)) {
            while (true) {
                String line = lineReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.length() == 0 || sline.charAt(0) == '#') {
                    continue;
                }
                String[] sfields = sline.split("\\s+", -1);
                if (sfields.length > 7 && StringUtils.isNumeric(sfields[7])) {
                    Point3 v1 = new Point3(Double.parseDouble(sfields[0]), Double.parseDouble(sfields[1]), Double.parseDouble(sfields[2]));
                    Point3 v2 = new Point3(Double.parseDouble(sfields[3]), Double.parseDouble(sfields[4]), Double.parseDouble(sfields[5]));
                    Vector3D vector = v1.subtract(v2);
                    vectors.add(vector);
                    dc.add(Double.parseDouble(sfields[6]));
                    maxRDC.add(Double.parseDouble(sfields[7]));
                    errors.add(Double.parseDouble(sfields[8]));
                    atoms.add(sfields[10]);
                    Double[] a1XYZ = {Double.parseDouble(sfields[0]), Double.parseDouble(sfields[1]), Double.parseDouble(sfields[2])};
                    Double[] a2XYZ = {Double.parseDouble(sfields[3]), Double.parseDouble(sfields[4]), Double.parseDouble(sfields[5])};
                    a1Coords.add(a1XYZ);
                    a2Coords.add(a2XYZ);
                }
            }
        } catch (IOException ioe) {
            log.warn(ioe.getMessage(), ioe);
        }

        List info = new ArrayList<>();
        info.add(0, vectors);
        info.add(1, dc);
        info.add(2, maxRDC);
        info.add(3, errors);
        info.add(4, atoms);
        info.add(5, a1Coords);
        info.add(6, a2Coords);

        return info;
    }

    /**
     * Returns the A matrix used in the SVD.
     *
     * @return RealMatrix A matrix containing the direction cosines used the
     * SVD: Ax = b.
     */
    public RealMatrix getAMatrix() {
        return AR;
    }

    /**
     * Returns the b vector used in the SVD.
     *
     * @return ArrayRealVector b vector containing the normalized experimental
     * RDCConstraint values used to solve for x in the SVD: Ax = b.
     */
    public ArrayRealVector getBVector() {
        return bVec;
    }

    /**
     * Returns the unnormalized experimental RDCConstraint value vector.
     *
     * @return ArrayRealVector b vector containing the unnormalized experimental
     * RDCConstraint values.
     */
    public ArrayRealVector getBUnNormVector() {
        return bVecUnNorm;
    }

    /**
     * Returns the x vector solved for in the SVD.
     *
     * @return RealVector x solved for in the SVD using the direction cosine A
     * matrix and the normalized experimental RDCConstraint b vector: Ax = b.
     */
    public RealVector getXVector() {
        return xVec;
    }

    /**
     * Returns the order matrix S.
     *
     * @return RealMatrix 3x3 order matrix S.
     */
    public RealMatrix getSMatrix() {
        return SR;
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
    public double calcMagnitude() {
        double[] absMaxRDCs = new double[maxRDCs.length];
        for (int i = 0; i < absMaxRDCs.length; i++) {
            absMaxRDCs[i] = Math.abs(maxRDCs[i]);
        }
        double maxAbsRDC = Arrays.stream(absMaxRDCs).max().getAsDouble();
        int maxIndex = Arrays.stream(absMaxRDCs).boxed().collect(Collectors.toList()).indexOf(maxAbsRDC);

        return 0.5 * maxRDCs[maxIndex] * calcSAxial();
    }

    /**
     * Returns the Q (rms) factor.
     *
     * @return double Q (rms) factor.
     */
    public double getQ() {
        return Q;
    }

    /**
     * Sets the Q (rms) factor.
     *
     * @param q double Q (rms) factor.
     */
    public void setQ(double q) {
        Q = q;
    }

    /**
     * Returns the Q (rhombicity) factor.
     *
     * @return double Q (rhombicity) factor.
     */
    public double getQrhomb() {
        return Qrhomb;
    }

    /**
     * Sets the Q (rhombicity) factor.
     *
     * @param q double Q (rhombicity) factor.
     */
    public void setQrhomb(double q) {
        Qrhomb = q;
    }

    /**
     * Returns the maximum RDCConstraint values associated with different atom types.
     *
     * @return HashMap of the maxRDC values for different atom types.
     */
    public HashMap getMaxRDCDict() {
        return maxRDCDict;
    }

    /**
     * Returns the maximum RDCConstraint values used for different pairs of atoms in the
     * molecule.
     *
     * @return double[] Array of the maxRDC values for the atom pairs in the
     * molecule.
     */
    public double[] getMaxRDCs() {
        return maxRDCs;
    }

    /**
     * Sets the maximum RDCConstraint values used for different pairs of atoms in the
     * molecule.
     *
     * @param maxRDC double[] Array of the maxRDC values for the atom pairs in
     *               the molecule.
     */
    public void setMaxRDCs(double[] maxRDC) {
        maxRDCs = maxRDC;
    }

    /**
     * Calculates the normalized RDCConstraint values using the A matrix and x vector from
     * the SVD.
     *
     * @return RealVector Normalized calculated RDCConstraint values b vector calculated
     * using the solved x vector from the SVD: Ax = b.
     */
    public RealVector calcBVectorNorm() {
        return AR.operate(xVec);
    }

    /**
     * Sets the normalized RDCConstraint values calculated using the A matrix and x vector
     * from the SVD.
     *
     * @param calcBVecNorm RealVector Normalized calculated RDCConstraint values b vector
     *                     calculated using the solved x vector from the SVD: Ax = b.
     */
    public void setCalcBVectorNorm(RealVector calcBVecNorm) {
        bCalcNorm = calcBVecNorm.toArray();
    }

    /**
     * Calculates the unnormalized RDCConstraint values using the A matrix and x vector
     * from the SVD.
     *
     * @return RealVector Unnormalized calculated RDCConstraint values b vector calculated
     * using the solved x vector from the SVD: Ax = b.
     */
    public RealVector calcBVector() {
        RealVector calcBVecNorm = calcBVectorNorm();
        RealVector tValsR = new ArrayRealVector(maxRDCs);
        RealVector calcBVec = calcBVecNorm.ebeMultiply(tValsR);
        return calcBVec;
    }

    /**
     * Sets the unnormalized RDCConstraint values calculated using the A matrix and x
     * vector from the SVD.
     *
     * @param calcBVec RealVector Unnormalized calculated RDCConstraint values b vector
     *                 calculated using the solved x vector from the SVD: Ax = b.
     */
    public void setCalcBVector(RealVector calcBVec) {
        bCalc = calcBVec.toArray();
    }

    /**
     * Returns the differences between the unnormalized RDCConstraint values (calculated -
     * experimental).
     *
     * @return RealVector of the differences between the unnormalized RDCConstraint values
     * (calculated b vector - experimental b vector).
     */
    public RealVector getRDCDiffs() {
        return dcDiffs;
    }

    /**
     * Sets the differences between the unnormalized RDCConstraint values (calculated -
     * experimental).
     *
     * @param rdcDiffs RealVector of the differences between the unnormalized
     *                 RDCConstraint values (calculated b vector - experimental b vector).
     */
    public void setRDCDiffs(RealVector rdcDiffs) {
        dcDiffs = rdcDiffs;
    }

    /**
     * Returns the normalized RDCConstraint values calculated using the A matrix and x
     * vector from the SVD.
     *
     * @return double[] Normalized b (calculated RDCConstraint) values calculated using
     * the solved x vector from the SVD: Ax = b.
     */
    public double[] getCalcBVectorNorm() {
        return bCalcNorm;
    }

    /**
     * Returns the unnormalized RDCConstraint values calculated using the A matrix and x
     * vector from the SVD.
     *
     * @return double[] Unnormalized b (calculated RDCConstraint) values calculated using
     * the solved x vector from the SVD: Ax = b.
     */
    public double[] getCalcBVector() {
        return bCalc;
    }

    /**
     * Returns the Eigenvalue Decomposition used to diagonalize the order matrix
     * S.
     *
     * @return
     */
    public EigenDecomposition getEig() {
        return eig;
    }

    /**
     * Sets the Euler angles.
     *
     * @param eulerVals
     */
    public void setEulerAngles(double[][] eulerVals) {
        euler = eulerVals;
    }

    /**
     * Returns the Euler angles.
     *
     * @return double[][] Arrays of Euler angles.
     */
    public double[][] getEulerAngles() {
        return euler;
    }

    /**
     * Sets the RDCConstraint constraint set.
     *
     * @param set
     */
    public void setRDCset(RDCConstraintSet set) {
        rdcSet = set;
    }

    /**
     * Returns the RDCConstraint constraint set.
     *
     * @return
     */
    public RDCConstraintSet getRDCset() {
        return rdcSet;
    }

    /**
     * Sets the RDCConstraint SVD results of the Molecule object.
     *
     * @param results OrderSVD results object.
     */
    public void setSVDResults(OrderSVD results) {
        Molecule mol = (Molecule) MoleculeFactory.getActive();
    }

    /**
     * Calculates the Euler angles for a rotation about z, y', z''.
     *
     * @param R double[][] Rotation matrix for clockwise rotation about z, y',
     *          z''.
     * @return double[][] Euler angles (alpha, beta, gamma) for the ZYZ
     * rotation.
     */
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
     * Calculates the vector associated with two atoms in a Molecule object.
     *
     * @param atomName1 String of the name of the first atom of the vector.
     * @param atomName2 String of the name of the second atom of the vector.
     * @param xyzCoords Optional List of Lists containing the XYZ coordinates of
     *                  the two atoms: [[atom1X, atom1Y, atom1Z], [atom2X, atom2Y, atomZ]]
     * @return Vector3D object that represents the vector associated with the
     * two atoms.
     */
    public static Vector3D calcVector(String atomName1, String atomName2, List<List<Double>> xyzCoords) {
        Point3 v1 = new Point3(0.0, 0.0, 0.0);
        Point3 v2 = new Point3(0.0, 0.0, 0.0);
        if (xyzCoords != null) {
            v1 = new Point3(xyzCoords.get(0).get(0), xyzCoords.get(0).get(1), xyzCoords.get(0).get(2));
            v2 = new Point3(xyzCoords.get(1).get(0), xyzCoords.get(1).get(1), xyzCoords.get(1).get(2));
        } else {
            Atom atom1 = MoleculeBase.getAtomByName(atomName1);
            Atom atom2 = MoleculeBase.getAtomByName(atomName2);
            if (atom1 != null & atom2 != null) {
                v1 = atom1.getPoint();
                v2 = atom2.getPoint();
            }
        }

        double r = Vector3D.distance(v1, v2) * 1e-10;
        Vector3D vector = null;
        if (r != 0.0) {
            vector = v1.subtract(v2);
        }
        return vector;
    }

    /**
     * Calculates the maximum RDCConstraint value associated with two atoms in a Molecule
     * object.
     *
     * @param vector     Vector3D object that represents the vector associated with
     *                   the two atoms.
     * @param atomName1  String of the name of the first atom of the vector.
     * @param atomName2  String of the name of the second atom of the vector.
     * @param calcMaxRDC Boolean of whether to calculate the max RDCConstraint value based
     *                   on the vector distance.
     * @param scale      Boolean of whether to calculate the max RDCConstraint value with the
     *                   scaling method used in CYANA.
     * @return double parameter that is the maxRDC value.
     */
    public static double calcMaxRDC(Vector3D vector, String atomName1, String atomName2, boolean calcMaxRDC, boolean scale) {
        double r = vector.getNorm() * 1e-10;

        String aType1 = atomName1.split("\\.")[1].substring(0, 1);
        String aType2 = atomName2.split("\\.")[1].substring(0, 1);
        double maxRDC = 1.0;
        if (maxRDCDict.containsKey(aType1 + aType2)) {
            maxRDC = maxRDCDict.get(aType1 + aType2);
        }
        double gammaI = gammaIDict.get(aType1);
        double gammaS = gammaSDict.get(aType2);
        if (r != 0) {
            if (calcMaxRDC) {
                maxRDC = preFactor * ((gammaI * gammaS) / (r * r * r));
            } else if (scale) {
                maxRDC = 24350.0 * (gammaI * gammaS) / ((r * r * r) * scaleHN);
            }
        }
        return maxRDC;
    }

    /**
     * Performs an Order SVD calculation to calculate molecular RDCs.
     *
     * @param atomPairs  List of List of Strings of atom name pairs: [[atom 1
     *                   name, atom 2 name], [atom 1 name, atom 2 name]...]
     * @param rdc        List of the unnormalized experimental RDCConstraint values.
     * @param errors     List of the errors in the experimental RDCConstraint values.
     * @param calcMaxRDC Boolean of whether to calculate the max RDCConstraint value based
     *                   on the vector distance.
     * @param scale      Boolean of whether to calculate the max RDCConstraint value with the
     *                   scaling method used in CYANA.
     * @param xyzCoords  Optional List of Lists containing the XYZ coordinates of
     *                   the two atoms: [[[atom1 X, atom1 Y, atom1 Z], [atom2 X, atom2 Y, atom2
     *                   Z]]...]
     */
    public static void calcRDC(List<List<String>> atomPairs, List<Double> rdc, List<Double> errors, boolean calcMaxRDC, boolean scale, List<List<List<Double>>> xyzCoords) {
        List<Vector3D> vectors = new ArrayList<>();
        List<Double> maxRDCList = new ArrayList<>();
        List<Double> rdc1 = new ArrayList<>();
        List<Double> errors1 = new ArrayList<>();
        for (int i = 0; i < atomPairs.size(); i++) {
            Vector3D vec = null;
            double maxRDC = 1.0;
            String atom1 = atomPairs.get(i).get(0);
            String atom2 = atomPairs.get(i).get(1);
            if (!atom1.equals(atom2)) {
                try {
                    vec = calcVector(atom1, atom2, null);
                    if (vec == null) {
                        continue;
                    }
                    maxRDC = calcMaxRDC(vec, atom1, atom2, calcMaxRDC, scale);
                } catch (IllegalArgumentException iae) {
                    if (i == 0) {
                        System.out.println("No Molecule object found. Using XYZ coordinates for vector calculation.");
                    }
                    if (xyzCoords != null) {
                        vec = calcVector(atom1, atom2, xyzCoords.get(i));
                        if (vec == null) {
                            continue;
                        }
                        maxRDC = calcMaxRDC(vec, atom1, atom2, calcMaxRDC, scale);
                    } else {
                        System.out.println("No XYZ coordinates provided. Stopping calculation.");
                        break;
                    }
                }
            }
            if (vec == null) {
                continue;
            }
            vectors.add(vec);
            maxRDCList.add(maxRDC);
            rdc1.add(rdc.get(i));
            errors1.add(errors.get(i));
        }

        runOrderSVD(vectors, rdc1, maxRDCList, errors1);
    }

    /**
     * Performs an Order SVD calculation to calculate molecular RDCs.
     *
     * @param rdcSet     RDCConstraintSet containing RDCConstraint information from the STAR
     *                   file (e.g. atoms, rdc values, rdc error values).
     * @param calcMaxRDC Boolean of whether to calculate the max RDCConstraint value based
     *                   on the vector distance.
     * @param scale      Boolean of whether to calculate the max RDCConstraint value with the
     *                   scaling method used in CYANA.
     * @param xyzCoords  Optional List of Lists containing the XYZ coordinates of
     *                   the two atoms: [[[atom1 X, atom1 Y, atom1 Z], [atom2 X, atom2 Y, atom2
     *                   Z]]...]
     * @return OrderSVD object containing information from the SVD calculation.
     */
    public static OrderSVD calcRDCs(RDCConstraintSet rdcSet, boolean calcMaxRDC, boolean scale, List<List<List<Double>>> xyzCoords) {
        List<Vector3D> vectors = new ArrayList<>();
        List<Double> maxRDCList = new ArrayList<>();
        List<Double> rdc1 = new ArrayList<>();
        List<Double> errors1 = new ArrayList<>();
        for (int i = 0; i < rdcSet.getSize(); i++) {
            String atom1 = rdcSet.get(i).getSpSets()[0].getFullName().split(":")[1];
            String atom2 = rdcSet.get(i).getSpSets()[1].getFullName().split(":")[1];

            Vector3D vec = null;
            Point3 v1;
            Point3 v2;
            double maxRDC = 1.0;
            if (xyzCoords != null) {
                v1 = new Point3(xyzCoords.get(i).get(0).get(0), xyzCoords.get(i).get(0).get(1), xyzCoords.get(i).get(0).get(2));
                v2 = new Point3(xyzCoords.get(i).get(1).get(0), xyzCoords.get(i).get(1).get(1), xyzCoords.get(i).get(1).get(2));
            } else {
                v1 = rdcSet.get(i).getSpSets()[0].getPoint();
                v2 = rdcSet.get(i).getSpSets()[1].getPoint();
            }
            if (!v1.equals(v2)) {
                double r = Vector3D.distance(v1, v2) * 1e-10;
                if (r != 0.0) {
                    vec = v1.subtract(v2);
                }
                if (vec == null) {
                    continue;
                }
                maxRDC = calcMaxRDC(vec, atom1, atom2, calcMaxRDC, scale);
            }
            if (vec == null) {
                continue;
            }
            vectors.add(vec);
            maxRDCList.add(maxRDC);
            rdc1.add(rdcSet.get(i).getValue());
            errors1.add(rdcSet.get(i).getErr());
        }

        return runOrderSVD(vectors, rdc1, maxRDCList, errors1);

    }

    /**
     * Runs an OrderSVD calculation.
     *
     * @param vectors List of bond vectors
     * @param rdc1    List of unnormalized experimental dipolar couplings
     * @param maxRDC  List of maximum static dipolar coupling values (r^(-3))
     * @param err     List of error values
     * @return OrderSVD object containing information from the SVD calculation.
     */
    public static OrderSVD runOrderSVD(List<Vector3D> vectors, List<Double> rdc1, List<Double> maxRDC, List<Double> err) {

        double[] maxRDCs = new double[maxRDC.size()];
        for (int i = 0; i < maxRDCs.length; i++) {
            maxRDCs[i] = maxRDC.get(i);
        }

        double[] rdc1a = new double[rdc1.size()];
        for (int i = 0; i < rdc1a.length; i++) {
            rdc1a[i] = rdc1.get(i);
        }

        OrderSVD orderSVD = null;
        if (!vectors.isEmpty()) {
            orderSVD = new OrderSVD(vectors, rdc1, maxRDCs, err);

            orderSVD.setMaxRDCs(maxRDCs);
            orderSVD.setSVDResults(orderSVD);

            double[] b = orderSVD.getBVector().toArray();
            RealVector bCalcVecNorm = orderSVD.calcBVectorNorm();
            orderSVD.setCalcBVectorNorm(bCalcVecNorm);
            RealVector bCalcVec = orderSVD.calcBVector();
            orderSVD.setCalcBVector(bCalcVec);

            RealVector rdcR = new ArrayRealVector(rdc1a);
            RealVector dcSq = rdcR.ebeMultiply(rdcR);
            RealVector dcDiff = bCalcVec.subtract(rdcR);
            orderSVD.setRDCDiffs(dcDiff);

            RealVector dcDiffs1 = orderSVD.getRDCDiffs();
            RealVector dcDiffSq = dcDiffs1.ebeMultiply(dcDiffs1);
            double dcDiffSqSum = 0;
            for (int i = 0; i < dcDiffSq.getDimension(); i++) {
                dcDiffSqSum += dcDiffSq.getEntry(i);
            }
            double dcSqSum = 0;
            for (int i = 0; i < dcSq.getDimension(); i++) {
                dcSqSum += dcSq.getEntry(i);
            }

            double[][] rotMat = orderSVD.getEig().getVT().getData();
            Rotation rot = null;

            try {
                rot = new Rotation(rotMat, 1e-6);
            } catch (NotARotationMatrixException nE) {
                System.out.println("Can't create rot mat:" + nE.getMessage());
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
                double[][] eulerCalc = orderSVD.calcEulerAngles(rotMat);
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

                orderSVD.setEulerAngles(eulerCalc);

            }

            double axial = orderSVD.calcSAxial();
            double axialFactor = 21585.19; //from PALES article: NATURE PROTOCOLS|VOL.3 NO.4|2008
            double axialNorm = axial * axialFactor;
            double rhombicity = orderSVD.calcRhombicity();

            double qRMS = Math.sqrt(dcDiffSqSum / dcDiffs1.getDimension()) / Math.sqrt(dcSqSum / rdc1.size());
            double qRhomb = Math.sqrt(dcDiffSqSum / dcDiffs1.getDimension()) / Math.sqrt(2.0 * (axialNorm) * (axialNorm) * (4.0 + 3.0 * rhombicity * rhombicity) / 5.0);
            orderSVD.setQ(qRMS);
            orderSVD.setQrhomb(qRhomb);
            System.out.println("Magnitude = " + orderSVD.calcMagnitude());
            System.out.println("Q (rms) = " + String.valueOf(qRMS));
            System.out.println("Q (rhomb) = " + String.valueOf(qRhomb));
        }

        return orderSVD;
    }

    /**
     * Saves SVD RDCConstraint results to a file.
     *
     * @param svdResults  OrderSVD object with the results of the SVD RDCConstraint
     *                    calculation.
     * @param resultsFile File to write.
     * @throws IOException
     */
    public static void writeToFile(OrderSVD svdResults, File resultsFile) throws IOException {
        if (svdResults != null) {
            double[] x = svdResults.getXVector().toArray();
            double qRMS = svdResults.getQ();
            double rhombicity = Math.abs(svdResults.calcRhombicity());
            double mag = Math.abs(svdResults.calcMagnitude());
            double axial = svdResults.calcSAxial();
            double rhombic = svdResults.calcSRhombic();
            double Sxx = svdResults.getSxx();
            double Syy = svdResults.getSyy();
            double Szz = svdResults.getSzz();
            double eta = svdResults.calcEta();
            double qRhomb = svdResults.getQrhomb();
            double[][] eulerAngles = svdResults.getEulerAngles();

            String[] atom1 = new String[svdResults.getRDCset().getSize()];
            String[] atom2 = new String[svdResults.getRDCset().getSize()];
            if (svdResults.getRDCset() != null) {
                for (int i = 0; i < svdResults.getRDCset().getSize(); i++) {
                    atom1[i] = svdResults.getRDCset().get(i).getSpSets()[0].getFullName().split(":")[1];
                    atom2[i] = svdResults.getRDCset().get(i).getSpSets()[1].getFullName().split(":")[1];
                }
            }
            double[] bmrbRDC = svdResults.getBUnNormVector().toArray();
            double[] svdRDC = svdResults.calcBVector().toArray();
            double[] bmrbRDCNorm = svdResults.getBVector().toArray();
            double[] svdRDCNorm = svdResults.calcBVectorNorm().toArray();
            double[] rdcDiff = new double[svdRDC.length];
            for (int i = 0; i < rdcDiff.length; i++) {
                rdcDiff[i] = svdRDC[i] - bmrbRDC[i];
            }

            if (resultsFile != null) {
                String[] headerFields = {"Atom_1", "Atom_2", "BMRB_RDC", "SVD_RDC", "RDC_Diff", "Normalized_BMRB_RDC", "Normalized_SVD_RDC"};
                String[][] atomFields = {atom1, atom2};
                double[][] valueFields = {bmrbRDC, svdRDC, rdcDiff, bmrbRDCNorm, svdRDCNorm};
                String[] formats = {"%.3f", "%.3f", "%.3f", "%.3E", "%.3E"};

                try (FileWriter writer = new FileWriter(resultsFile)) {
                    writer.write("Syy\tSzz\tSxy\tSxz\tSyz\n");
                    for (double value : x) {
                        writer.write(String.format("%.3E\t", value));
                    }
                    writer.write("\n\nSx'x'\tSy'y'\tSz'z'\n");
                    writer.write(String.format("%.3E\t%.3E\t%.3E\n\n", Sxx, Syy, Szz));
                    writer.write(String.format("Asymmetry parameter (eta) = %.3f \n", eta));
                    writer.write(String.format("Q (RMS) = %.3f \n", qRMS));
                    writer.write(String.format("Q (Rhombicity) = %.3f \n", qRhomb));
                    writer.write(String.format("Magnitude = %.3f \n\n", mag));
                    writer.write(String.format("Rhombic component = %.3E \n", rhombic));
                    writer.write(String.format("Axial component = %.3E \n", axial));
                    writer.write(String.format("Rhombicity = %.3f \n\n", rhombicity));
                    writer.write("Euler Angles for clockwise rotation about z, y', z''\n");
                    writer.write("Alpha\tBeta\tGamma\n");
                    writer.write(String.format("%.3f\t%.3f\t%.3f\n", eulerAngles[0][0], eulerAngles[0][1], eulerAngles[0][2]));
                    writer.write(String.format("%.3f\t%.3f\t%.3f\n", eulerAngles[0][0] + 180., eulerAngles[0][1], eulerAngles[0][2]));
                    writer.write(String.format("%.3f\t%.3f\t%.3f\n", eulerAngles[1][0], eulerAngles[1][1], eulerAngles[1][2] + 180.));
                    writer.write(String.format("%.3f\t%.3f\t%.3f\n\n", eulerAngles[1][0] + 180., eulerAngles[1][1], eulerAngles[1][2] + 180.));

                    for (String header : headerFields) {
                        writer.write(String.format("%s\t", header));
                    }
                    writer.write("\n");

                    for (int i = 0; i < bmrbRDC.length; i++) {
                        StringBuilder valueBuilder = new StringBuilder();
                        for (String[] atom : atomFields) {
                            valueBuilder.append(String.format("%s\t", atom[i]));
                        }
                        for (double[] value : valueFields) {
                            valueBuilder.append(String.format(formats[Arrays.asList(valueFields).indexOf(value)] + "\t", value[i]));
                        }
                        writer.write(valueBuilder.toString() + "\n");
                    }
                }
            }
        }
    }

    /**
     * Reads experimental RDCs from XPLOR or CYANA files and updates the RDCSet
     * object.
     *
     * @param file       File to read.
     * @param type       String of the file format: "xplor" or "cyana".
     * @param rdcSetName String of the name of the RDCConstraint set.
     * @throws IOException
     * @throws ParseException
     */
    public static void readRDCs(MoleculeBase molecule, File file, String type, String rdcSetName) throws IOException, ParseException {
        ArrayList<String> atom1 = new ArrayList<>();
        ArrayList<String> atom2 = new ArrayList<>();
        ArrayList<String> rdc = new ArrayList<>();
        ArrayList<String> err = new ArrayList<>();
        if (type.equals("xplor")) {
            try (PythonInterpreter interpreter = new PythonInterpreter()) {
                interpreter.exec("import refine");
                interpreter.exec("ref = refine.refine()");
                interpreter.set("rdcFilePath", file.getAbsolutePath());
                interpreter.exec("ref.addRDCFile(rdcFilePath, mode='xplor', keep=None)");
                interpreter.exec("ref.readRDCFiles()");
            }
        } else if (type.equals("cyana")) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.length() == 0) {
                        continue;
                    }
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split("\\s+");
                    atom1.add(fields[0] + "." + fields[2]);
                    atom2.add(fields[3] + "." + fields[5]);
                    rdc.add(fields[6]);
                    err.add(fields[7]);
                }
                updateRDCSet(molecule, atom1, atom2, rdc, err, rdcSetName);
            }
        }
    }

    /**
     * Updates the RDCSet object with RDCs read from XPLOR or CYANA files.
     *
     * @param atom1      List of atoms for the first atom in the RDCConstraint pair.
     * @param atom2      List of atoms for the second atom in the RDCConstraint pair.
     * @param rdc        List of XPLOR or CYANA RDCConstraint values.
     * @param err        List of XPLOR or CYANA RDCConstraint error values.
     * @param rdcSetName String of the name of the RDCConstraint set.
     */
    public static void updateRDCSet(MoleculeBase molecule, List<String> atom1, List<String> atom2, List<String> rdc, List<String> err, String rdcSetName) {
        RDCConstraintSet rdcSet = molecule.getMolecularConstraints().getRDCSet(rdcSetName);
        String[][] setAtoms = new String[2][rdcSet.getSize()];
        for (int i = 0; i < rdcSet.getSize(); i++) {
            RDCConstraint setRDC = rdcSet.get(i);
            SpatialSet[] spSets = setRDC.getSpSets();
            setAtoms[0][i] = spSets[0].getFullName().split(":")[1];
            setAtoms[1][i] = spSets[1].getFullName().split(":")[1];
        }
        for (int i = 0; i < atom1.size(); i++) {
            String newAtom1 = atom1.get(i);
            String newAtom2 = atom2.get(i);
            int newAtom1Ind = Arrays.asList(setAtoms[0]).indexOf(newAtom1);
            if (newAtom1Ind == -1) {
                newAtom1Ind = Arrays.asList(setAtoms[1]).indexOf(newAtom1);
            }
            if (newAtom1Ind >= 0) {
                SpatialSet[] spSets1 = rdcSet.get(newAtom1Ind).getSpSets();
                RDCConstraint aCon = new RDCConstraint(rdcSet, spSets1[0].getAtom(), spSets1[1].getAtom(), Double.parseDouble(rdc.get(i)), Double.parseDouble(err.get(i)));
                rdcSet.remove(newAtom1Ind);
                rdcSet.add(newAtom1Ind, aCon);
            } else {
                Atom a1 = MoleculeBase.getAtomByName(newAtom1);
                Atom a2 = MoleculeBase.getAtomByName(newAtom2);
                if (a1 != null & a2 != null) {
                    SpatialSet spSet1 = a1.getSpatialSet();
                    SpatialSet spSet2 = a2.getSpatialSet();
                    RDCConstraint rdcObj = new RDCConstraint(rdcSet, spSet1.getAtom(), spSet2.getAtom(), Double.parseDouble(rdc.get(i)), Double.parseDouble(err.get(i)));
                    rdcSet.add(rdcObj);
                }
            }
        }
    }
}
