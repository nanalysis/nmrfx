/*
 * NMRFx Processor : A Program for Processing NMR Data
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
package org.nmrfx.processor.optimization;

import org.apache.commons.math3.exception.TooManyIterationsException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;
import org.nmrfx.annotations.PluginAPI;

@PluginAPI("parametric")
public class NNLSMat {

    private final double[][] aMat;
    private final int nRows;
    private final int nCols;
    private final double[] b;
    private final double[] x;
    private final int[] indices;
    private int nPositive;
    private double norm;
    private final double[] w;
    private final double[] zz;
    private final int maxIterations;

    private static final double FACTOR = 0.01;

    public NNLSMat(RealMatrix A, ArrayRealVector B) {
        this.aMat = A.getData();
        this.nRows = A.getRowDimension();
        this.nCols = A.getColumnDimension();
        this.b = B.getDataRef();
        this.x = new double[nCols];
        this.indices = new int[nCols];

        this.w = new double[nCols];
        this.zz = new double[nRows];
        this.maxIterations = 3 * nCols;
        solve();
    }

    public NNLSMat(RealMatrix A, RealMatrix B) {

        this.aMat = A.getData();
        this.nRows = A.getRowDimension();
        this.nCols = A.getColumnDimension();
        this.b = B.getColumn(0);
        this.x = new double[nCols];
        this.indices = new int[nCols];

        this.w = new double[nCols];
        this.zz = new double[nRows];
        this.maxIterations = 3 * nCols;
        solve();
    }

    public double[] getX() {
        return x;
    }

    public double getNorm() {
        return norm;
    }

    public int getNPositive() {
        return nPositive;
    }

    private void solve() throws IllegalArgumentException, TooManyIterationsException {
        int nIterations = 0;

        // Initialize indices
        for (int iCol = 0; iCol < nCols; iCol++) {
            x[iCol] = 0.0;
            indices[iCol] = iCol;
        }
        nPositive = 0;

        // Loop until no candidates found or until number of positive
        // coefficients is greater than or equal to nCols or nRows
        while ((nPositive < nCols) && (nPositive < nRows)) {
            // Compute components of the dual (negative gradient) vector W.
            for (int iCol = nPositive; iCol < nCols; iCol++) {
                int index = indices[iCol];
                double sum = 0.0;
                for (int jRow = nPositive; jRow < nRows; jRow++) {
                    sum += aMat[jRow][index] * b[jRow];
                }
                w[index] = sum;
            }

            // find a candidate
            Candidate candidate = findCandidate();
            if (candidate == null) {
                break;
            }

            //     THE INDEX  J=INDEX(IZ)  HAS BEEN SELECTED TO BE MOVED FROM
            //     SET Z TO SET P.    UPDATE B,  UPDATE INDICES,  APPLY HOUSEHOLDER
            //     TRANSFORMATIONS TO COLS IN NEW SET Z,  ZERO SUBDIAGONAL ELTS IN
            //     COL J,  SET W(J)=0.
            // Use the candidate column and Householder transformations
            // update b and aMat and set indices
            copyVector(zz, b, nRows);

            indices[candidate.iz] = indices[nPositive];
            indices[nPositive++] = candidate.j;

            for (int iCol = nPositive; iCol < nCols; iCol++) {
                candidate.houseHolder.applyToMatrix(aMat, nPositive - 1, nPositive, candidate.j, aMat, indices[iCol]);
            }

            for (int jRow = nPositive; jRow < nRows; jRow++) {
                aMat[jRow][candidate.j] = 0.0;
            }

            w[candidate.j] = 0.0;

            // solve triangular system
            int jj = indices[nCols - 1];
            for (int i = 0; i < nPositive; i++) {
                int ip = nPositive - i;
                if (i != 0) {
                    for (int j = 0; j < ip; j++) {
                        zz[j] -= aMat[j][jj] * zz[ip];
                    }
                }
                ip--;
                jj = indices[ip];
                zz[ip] /= aMat[ip][jj];
            }

            // loop until all coefficients are feasible
            while (true) {
                nIterations++;
                if (nIterations > maxIterations) {
                    throw new TooManyIterationsException(maxIterations);
                }

                // Check coefficients to see if they are all feasible
                // Calculate alpha for interpolation if any are not feasible
                double alpha = 1.0;
                boolean allFeasible = true;
                for (int ip = 0; ip < nPositive; ip++) {
                    int index = indices[ip];
                    if (zz[ip] <= 0.0) {
                        double t = -x[index] / (zz[ip] - x[index]);
                        if (alpha > t) {
                            allFeasible = false;
                            alpha = t;
                            jj = ip;
                        }
                    }
                }

                // if all coefficients were feasible we're done here
                if (allFeasible) {
                    break;
                }

                // If any coefficients were not feasible use alpha to interpolate
                for (int i = 0; i < nPositive; i++) {
                    int index = indices[i];
                    x[index] += alpha * (zz[i] - x[index]);
                }

                // Modify aMat, b and indices to move coefficient from set P to set Z
                int iCoef = indices[jj];
                while (true) {
                    x[iCoef] = 0.0;
                    if (jj != nPositive - 1) {
                        jj++;
                        for (int j = jj; j < nPositive; j++) {
                            int index = indices[j];
                            indices[j - 1] = index;
                            Givens givens = new Givens(aMat[j - 1][index], aMat[j][index]);
                            aMat[j - 1][index] = givens.sig;
                            aMat[j][index] = 0.0;
                            for (int iCol = 0; iCol < nCols; iCol++) {
                                if (iCol != index) {
                                    givens.apply(aMat, j, iCol);
                                }
                            }
                            givens.apply(b, j);

                        }
                    }
                    nPositive--;
                    indices[nPositive] = iCoef;

                    // Check to see if all coefficents in set P are positive
                    // If not it was because of round-off error
                    // We'll loop again to fix this
                    boolean done = true;
                    for (int i = 0; i < nPositive; i++) {
                        int index = indices[i];
                        if (x[index] <= 0.0) {
                            done = false;
                            break;
                        }
                    }
                    if (done) {
                        break;
                    }
                }

                // copy b into zz and solve tridigonal system again
                copyVector(b, zz, nRows);
                for (int i = 0; i < nPositive; i++) {
                    int ip = nPositive - i;
                    if (i != 0) {
                        for (int j = 0; j < ip; j++) {
                            zz[j] -= aMat[j][jj] * zz[ip];
                        }
                    }
                    ip--;
                    jj = indices[ip];
                    zz[ip] /= aMat[ip][jj];
                }
            }

            // Update x from zz.
            for (int i = 0; i < nPositive; i++) {
                int index = indices[i];
                x[index] = zz[i];
            }

            // All coeficients should be positive, continue main loop
            // to search for more
        }

        // Calculate the norm of the residual
        double sumSq = 0.0;
        for (int jRow = nPositive; jRow < nRows; jRow++) {
            sumSq += b[jRow] * b[jRow];
        }
        norm = FastMath.sqrt(sumSq);
    }

    void copyVector(double[] src, double[] dest, int n) {
        System.arraycopy(src, 0, dest, 0, n);
    }

    class Givens {

        final double c;
        final double s;
        final double sig;

        Givens(double a, double b) {
            if (FastMath.abs(a) > FastMath.abs(b)) {
                double x = b / a;
                double y = FastMath.sqrt(1.0 + x * x);
                c = FastMath.copySign(1.0 / y, a);
                s = c * x;
                sig = FastMath.abs(a) * y;
            } else if (b != 0.0) {
                double x = a / b;
                double y = FastMath.sqrt(1.0 + x * x);
                s = FastMath.copySign(1.0 / y, b);
                c = s * x;
                sig = FastMath.abs(b) * y;
            } else {
                c = 0.0;
                s = 1.0;
                sig = 0.0;
            }

        }

        void apply(double[][] matrix, int jRow, int iCol) {
            double temp = matrix[jRow - 1][iCol];
            matrix[jRow - 1][iCol] = c * temp + s * matrix[jRow][iCol];
            matrix[jRow][iCol] = -s * temp + c * matrix[jRow][iCol];
        }

        void apply(double[] vector, int i) {
            double temp = vector[i - 1];
            vector[i - 1] = c * temp + s * vector[i];
            vector[i] = -s * temp + c * vector[i];
        }
    }

    class Candidate {

        final int iz;
        final int j;
        final HouseHolder houseHolder;

        Candidate(int iz, int j, HouseHolder houseHolder) {
            this.iz = iz;
            this.j = j;
            this.houseHolder = houseHolder;
        }
    }

    Candidate findCandidate() {
        while (true) {
            // Find largest positive value in w
            double wmax = 0.0;
            int izmax = -1;
            for (int iz = nPositive; iz < nCols; iz++) {
                int j = indices[iz];
                if (w[j] > wmax) {
                    wmax = w[j];
                    izmax = iz;
                }
            }

            // If wmax <= 0 we're done.
            // This indicates satisfaction of the Kuhn-Tucker conditions.
            if (wmax <= 0.0) {
                return null;
            }
            int j = indices[izmax];

            // The sign of w[index] is okay for index to be moved to set P.
            // Begin the transformation and check the new diagonal
            // element to avoid near linear independence.
            double asave = aMat[nPositive][j];
            HouseHolder houseHolder = new HouseHolder(aMat, nPositive, nPositive + 1, j);
            double unorm = 0.0;
            for (int l = 0; l < nPositive; l++) {
                unorm += aMat[l][j] * aMat[l][j];
            }
            unorm = FastMath.sqrt(unorm);
            // Check to see if column is sufficiently independent. 
            if (!Precision.equals(unorm + FastMath.abs(aMat[nPositive][j]) * FACTOR, unorm)) {

                // Copy b into zz,
                copyVector(b, zz, nRows);
                // Use Householder to update zz
                houseHolder.applyToVector(aMat, nPositive, nPositive + 1, j, zz);
                // Solve for ztest = proposed new value for x[index].
                double ztest = zz[nPositive] / aMat[nPositive][j];

                // If ztest is positive, we've found our candidate.
                if (ztest > 0.0) {
                    return new Candidate(izmax, j, houseHolder);
                }
            }

            // Reject index as aMat candidate to be moved from set Z to set P.
            // restore aMat value and loop again for another try
            aMat[nPositive][j] = asave;
            w[j] = 0.0;
        }

    }

    class HouseHolder {

        final double up;

        HouseHolder(double[][] u, int pivotRow, int startRow, int pivotCol) {
            up = construct(u, pivotRow, startRow, pivotCol);
        }

        private double construct(double[][] u, int pivotRow, int startRow, int pivotCol) {
            int nRows = u.length;

            double cl = FastMath.abs(u[pivotRow][pivotCol]);

            for (int jRow = startRow; jRow < nRows; jRow++) {
                cl = FastMath.max(FastMath.abs(u[jRow][pivotCol]), cl);
            }
            if (cl <= 0.0) {
                throw new IllegalArgumentException("Illegal pivot");
            }
            double clinv = 1.0 / cl;
            double temp = u[pivotRow][pivotCol] * clinv;
            double sum = temp * temp;
            for (int jRow = startRow; jRow < nRows; jRow++) {
                temp = u[jRow][pivotCol] * clinv;
                sum += temp * temp;
            }
            cl = cl * FastMath.sqrt(sum);
            if (u[pivotRow][pivotCol] > 0.0) {
                cl = -cl;
            }
            double delta = u[pivotRow][pivotCol] - cl;
            u[pivotRow][pivotCol] = cl;
            return delta;
        }

        double calcB(double[][] u, int pivotRow, int pivotCol) {
            if (FastMath.abs(u[pivotRow][pivotCol]) <= 0.0) {
                throw new IllegalArgumentException("Illegal pivot");
            }

            double b = up * u[pivotRow][pivotCol];
            if (b == 0.0) {
                return b;
            } else if (b > 0.0) {
                throw new IllegalArgumentException("Illegal pivot");
            }
            b = 1.0 / b;
            return b;

        }

        void applyToMatrix(double[][] u, int pivotRow, int startRow, int pivotCol, double[][] c, int targetCol) {
            double b = calcB(u, pivotRow, pivotCol);
            if (b == 0.0) {
                return;
            }
            int nRows = u.length;

            double sum = c[pivotRow][targetCol] * up;
            for (int iRow = startRow; iRow < nRows; iRow++) {
                sum += c[iRow][targetCol] * u[iRow][pivotCol];
            }
            if (sum != 0.0) {
                sum = sum * b;
                c[pivotRow][targetCol] += sum * up;
                for (int iRow = startRow; iRow < nRows; iRow++) {
                    c[iRow][targetCol] += sum * u[iRow][pivotCol];
                }
            }
        }

        void applyToVector(double[][] u, int pivotRow, int startRow, int pivotCol, double[] c) {
            double b = calcB(u, pivotRow, pivotCol);
            if (b == 0.0) {
                return;
            }
            int nRows = u.length;

            double sum = c[pivotRow] * up;
            for (int iRow = startRow; iRow < nRows; iRow++) {
                sum += c[iRow] * u[iRow][pivotCol];
            }
            if (sum != 0.0) {
                sum = sum * b;
                c[pivotRow] += sum * up;
                for (int iRow = startRow; iRow < nRows; iRow++) {
                    c[iRow] += sum * u[iRow][pivotCol];
                }
            }
        }
    }
}
