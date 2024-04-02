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
package org.nmrfx.processor.operations;

import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.util.MathArrays;
import org.nmrfx.math.VecException;
import org.nmrfx.processor.math.Vec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Utility functions involving Vectors, mostly pulled from VecMat and the Math
 * package.
 *
 * @author johnsonb
 */
public class Util {

    /**
     * If the caller provides a list of points they must also set the proper
     * flag.
     */
    static ArrayList<Integer> getBasePoints(final Vec vec, ArrayList<Double> realPoints, String type, boolean invert) throws OperationException {

        if ((realPoints.size() % 2) != 0) {
            throw new OperationException("getBasePoints: Must specify a list with even number of ppms");
        }
        boolean fromPPM = false;
        boolean fromFrac = false;
        if (type.startsWith("ppm")) {
            fromPPM = true;
        } else if (type.startsWith("frac")) {
            fromFrac = true;
        }
        int vecSize = vec.getSize();
        ArrayList<Integer> baseRegions = new ArrayList<>();
        int last = realPoints.size() - 2;
        for (int i = 0; i < realPoints.size(); i += 2) {
            int pt0;
            int pt1;
            if (fromFrac) {
                pt0 = (int) (realPoints.get(i) * (vecSize - 1) + 0.5);
                pt1 = (int) (realPoints.get(i + 1) * (vecSize - 1) + 0.5);
            } else if (fromPPM) {
                pt0 = vec.refToPt(realPoints.get(i));
                pt1 = vec.refToPt(realPoints.get(i + 1));
            } else {
                pt0 = (int) Math.round(realPoints.get(i));
                pt1 = (int) Math.round(realPoints.get(i + 1));
            }
            if (pt0 > pt1) {
                int hold = pt0;
                pt0 = pt1;
                pt1 = hold;
            }
            if (pt0 < 0) {
                pt0 = 0;
            }
            if (pt1 >= vec.getSize()) {
                pt1 = vec.getSize() - 1;
            }
            if (invert) {
                pt0--;
                pt1++;
                if (pt0 < 0) {
                    pt0 = 0;
                }
                if (pt1 >= vec.getSize()) {
                    pt1 = vec.getSize() - 1;
                }
                if (i == 0) {
                    if (pt0 != 0) {
                        baseRegions.add(0);
                        baseRegions.add(pt0);
                    }
                    baseRegions.add(pt1);
                    if (i == last) {
                        baseRegions.add(vecSize - 1);
                    }
                } else if (i == last) {
                    baseRegions.add(pt0);
                    if (pt1 != vecSize - 1) {
                        baseRegions.add(pt1);
                        baseRegions.add(vecSize - 1);
                    }
                } else {
                    baseRegions.add(pt0);
                    baseRegions.add(pt1);
                }

            } else {
                baseRegions.add(pt0);
                baseRegions.add(pt1);
            }
        }
        return baseRegions;
    }

    static ArrayList<Integer> getBasePoints(Vec vec, ArrayList<Integer> points, boolean invert) throws OperationException {

        if ((points.size() % 2) != 0) {
            throw new OperationException("getBasePoints: Must specify a list with even number of ppms");
        }
        ArrayList<Integer> baseRegions = new ArrayList<>();
        if (invert) {
            baseRegions.add(0);
        }
        int vecSize = vec.getSize();
        int last = points.size() - 2;
        for (int i = 0; i < points.size(); i += 2) {
            int pt0 = points.get(i);
            int pt1 = points.get(i + 1);

            if (pt0 > pt1) {
                int hold = pt0;
                pt0 = pt1;
                pt1 = hold;
            }
            if (pt0 < 0) {
                pt0 = 0;
            }
            if (pt1 >= vec.getSize()) {
                pt1 = vec.getSize() - 1;
            }
            if (invert) {
                if (i == 0) {
                    if (pt0 != 0) {
                        baseRegions.add(0);
                        baseRegions.add(pt0);
                    }
                    baseRegions.add(pt1);
                    if (i == last) {
                        baseRegions.add(vecSize - 1);
                    }
                } else if (i == last) {
                    baseRegions.add(pt0);
                    if (pt1 != vecSize - 1) {
                        baseRegions.add(pt1);
                        baseRegions.add(vecSize - 1);
                    }
                } else {
                    baseRegions.add(pt0);
                    baseRegions.add(pt1);
                }

            } else {
                baseRegions.add(pt0);
                baseRegions.add(pt1);
            }
        }
        return baseRegions;
    }

    public static boolean[] getSignalRegion(int vecSize, ArrayList<Integer> baseRegions) {
        boolean[] isInSignalRegion = new boolean[vecSize];
        for (int i = 0; i < isInSignalRegion.length; i++) {
            isInSignalRegion[i] = true;
        }

        for (int i = 0; i < baseRegions.size(); i += 2) {
            int first = baseRegions.get(i);
            int last = baseRegions.get(i + 1);
            if (first < 0) {
                first = 0;
            }
            if (last >= vecSize) {
                last = vecSize - 1;
            }
            for (int j = first; j <= last; j++) {
                isInSignalRegion[j] = false;
            }
        }
        return isInSignalRegion;

    }

    public static ArrayList<Integer> getSignalRegions(boolean[] isInSignalRegion) {
        ArrayList<Integer> signalRegions = new ArrayList<>();
        if (isInSignalRegion != null) {
            int size = isInSignalRegion.length;
            boolean wasNotInSignalRegion = true;
            int start = 0;
            int end = 0;
            for (int i = 0; i < size; i++) {
                if (isInSignalRegion[i]) {
                    if (wasNotInSignalRegion) {
                        start = i;
                    } else {
                        end = i;
                    }
                    wasNotInSignalRegion = false;
                    if (i == size - 1) {
                        signalRegions.add(start);
                        signalRegions.add(end);
                    }
                } else {
                    if (wasNotInSignalRegion) {
                    } else {
                        signalRegions.add(start);
                        signalRegions.add(end);
                    }
                    wasNotInSignalRegion = true;
                }
            }
        }
        return signalRegions;
    }

    public Util() {
    }

    public static boolean[] getSignalRegionByCWTD(Vec vector, int winSize, int minBase, double ratio, IDBaseline2.ThreshMode mode) {
        Vec dVec = new Vec(vector.getSize());
        dVec.resize(vector.getSize(), vector.isComplex());
        vector.copy(dVec);
        Cwtd cwtd = new Cwtd(winSize);
        cwtd.eval(dVec);
        dVec.power();
        int[] limits = new int[2];
        IDBaseline2 idbaseline = new IDBaseline2(minBase, limits, ratio, mode).eval(dVec);
        boolean[] isInSignalRegion = idbaseline.getResult();
        return isInSignalRegion;
    }

    public static void pascalrow(double[] a, int n) /* Construct row n of Pascal's triangle in a */ {
        int i, j;
        for (j = 0; j <= n; j++) {
            a[j] = 0;
        }
        a[0] = 1;
        for (j = 1; j <= n; j++) {
            for (i = n; i >= 1; i--) {
                a[i] = a[i] - a[i - 1];
            }
        }
    }

    /* Program 3. Smoothing and interpolation with any difference equation. */
    /* Contribution to Graphic Gems IV */
 /* Paul H. C. Eilers, DCMR Milieudienst Rijnmond, 's-Gravelandseweg 565,
     3119 XT Schiedam, The Netherlands, E-Mail: paul@dcmr.nl */
    public static void asmooth(double[] w, double[] y, double[] z, double[] a, double lambda, int m, int n) /* Smoothing and interpolation with any difference equation of order <=5.
     Input:  weights (w), data (y): vector from 1 to vecSize.
     Input:  smoothing parameter (lambda), length (vecSize).
     Input:  coefficients (a) and order of difference equation (n).
     Output: smoothed vector (z): vector from 1 to vecSize. */ {
        double[][] b = new double[m + 1][6];
        int[] v = new int[m + n + 1];
        int i, j, j1, j2, k, k1;
        double s;
        for (i = 1; i <= m + n; i++) {
            v[i] = 1;
            if ((i <= n) || (i > m)) {
                v[i] = 0;
            }
        }
        /*  construct band matrix  */
        for (i = 1; i <= m; i++) {
            j2 = m - i;
            if (j2 > n) {
                j2 = n;
            }
            for (j = 0; j <= j2; j++) {
                s = 0.0;
                if (j == 0) {
                    s = w[i] / lambda;
                }
                for (k = j; k <= n; k++) {
                    s = s + v[i + k] * a[k] * a[k - j];
                }
                b[i][j] = s;
            }
        }
        /*  compute cholesky-decomposition  */
        for (i = 1; i <= m; i++) {
            s = b[i][0];
            j1 = i - n;
            if (j1 < 1) {
                j1 = 1;
            }
            for (j = j1; j <= i - 1; j++) {
                s = s - b[j][0] * b[j][i - j] * b[j][i - j];
            }
            b[i][0] = (s);
            j2 = i + n;
            if (j2 > m) {
                j2 = m;
            }
            for (j = i + 1; j <= j2; j++) {
                s = b[i][j - i];
                k1 = j - n;
                if (k1 < 1) {
                    k1 = 1;
                }
                for (k = k1; k <= i - 1; k++) {
                    s = s - b[k][0] * b[k][i - k] * b[k][j - k];
                }
                b[i][j - i] = s / b[i][0];
            }
        }
        /*  solve triangular systems	*/
        for (i = 1; i <= m; i++) {
            s = w[i] * y[i] / lambda;
            j1 = i - n;
            if (j1 < 1) {
                j1 = 1;
            }
            for (j = j1; j <= i - 1; j++) {
                s = s - z[j] * b[j][i - j];
            }
            z[i] = s;
        }
        for (i = m; i >= 1; i--) {
            s = z[i] / b[i][0];
            j2 = i + n;
            if (j2 > m) {
                j2 = m;
            }
            for (j = i + 1; j <= j2; j++) {
                s = s - z[j] * b[i][j - i];
            }
            z[i] = s;
        }
    }

    public static double calMedianDelta(double[] vec, int icenter, int start, int end) {
        int nPoints = end - start + 1;
        if (((nPoints) % 2) == 0) {
            end++;
        }
        ArrayList lList = new ArrayList(nPoints);
        ArrayList rList = new ArrayList(nPoints);
        for (int i = start; i <= end; i++) {
            rList.add(Double.valueOf(vec[icenter + i]));
            lList.add(Double.valueOf(vec[icenter - i]));
        }
        Collections.sort(lList);
        Collections.sort(rList);
        double lMedian = ((Double) lList.get(nPoints / 2)).doubleValue();
        double rMedian = ((Double) rList.get(nPoints / 2)).doubleValue();
        double delta = lMedian - rMedian;
        return Math.abs(delta);
    }

    public static double phaseMin(double ph) {
        while (ph > 180) {
            ph -= 360.0;
        }
        while (ph < -180) {
            ph += 360.0;
        }
        return ph;
    }

    public static double sdev(Vec vector, int winSize, int nWindows) {
        double[] meanAndStd = getMeanAndStdDev(vector, winSize, nWindows);
        return meanAndStd[1];
    }

    public static double[] getMeanAndStdDev(Vec vector, int winSize, int nWindows) {
        int nRegions = (vector.getSize()) / winSize;

        double[] regionVec = new double[nRegions];
        double[] sdVec = new double[nRegions];

        /* Calculate means of each window */
        double max = Double.NEGATIVE_INFINITY;

        int k = 0;
        for (int j = 0; j < nRegions; j++) {
            double reSum = 0.0;

            for (int i = 0; i < winSize; i++) {
                if (vector.getReal(k) > max) {
                    max = vector.getReal(k);
                }

                reSum += vector.getReal(k);
                k++;
            }

            regionVec[j] = reSum / winSize;
        }

        /* Form centered vector and calculate st. dev. for window */
        k = 0;

        for (int j = 0; j < nRegions; j++) {
            double sumsq = 0.0;

            for (int i = 0; i < winSize; i++) {
                double dev = vector.getReal(k) - regionVec[j];
                sumsq += (dev * dev);
                k++;
            }

            sdVec[j] = sumsq;
        }

        /* Estimate standard deviation from sorted vector */
        Arrays.sort(sdVec);
        double threshold = max * 1.0e-8;
        double sd = 0.0;
        int n = 0;

        double sumMean = 0.0;
        for (int i = 0; i < nRegions; i++) {
            if (sdVec[i] > threshold) {
                sd += sdVec[i];
                sumMean += regionVec[i];
                n++;
            }

            if (n >= nWindows) {
                break;
            }
        }
        double sdev = Math.sqrt(sd / (nWindows * winSize));
        double mean = sumMean / nWindows;

        double[] result = {mean, sdev};
        return result;
    }

    public static ArrayList<Integer> idBaseLineBySDev(Vec vector, int winSize, double ratio) throws OperationException {
        double sumsq;
        double rmsd;
        double dev;
        int nRegions;
        int nNoise;

        int vecSize = vector.getSize();

        nRegions = (vecSize) / winSize;
        if ((nRegions * winSize) < vecSize) {
            nRegions++;
        }

        double[] reVec = new double[nRegions];
        double[] sdVec = new double[nRegions];
        double[] ceVec = new double[vecSize];

        /* Calculate means of each window */
        int k = 0;
        double reSum = 0.0;

        for (int j = 0; j < nRegions; j++) {
            reSum = 0.0;
            int pointsInRegion = 0;
            for (int i = 0; ((i < winSize) && (k < vector.getSize())); i++) {
                reSum += vector.getReal(k);
                pointsInRegion++;
                k++;
            }

            reVec[j] = reSum / pointsInRegion;
        }

        /* Form centered vector and calculate st. dev. for window */
        k = 0;

        double maxValue = Double.NEGATIVE_INFINITY;

        for (int j = 0; j < nRegions; j++) {
            sumsq = 0.0;
            int pointsInRegion = 0;

            for (int i = 0; ((i < winSize) && (k < vector.getSize())); i++) {
                dev = vector.getReal(k) - reVec[j];
                pointsInRegion++;

                if (Math.abs(vector.getReal(k)) > maxValue) {
                    maxValue = Math.abs(vector.getReal(k));
                }

                ceVec[k] = dev * dev;
                sumsq += ceVec[k];
                k++;
            }

            sdVec[j] = Math.sqrt(sumsq / pointsInRegion);
        }

        double[] sortVec = sdVec.clone();
        MathArrays.sortInPlace(sortVec);
        double sd = 0.0;
        nNoise = (int) (nRegions / 8.0);
        if (nNoise < 3) {
            nNoise = 3;
        }

        int n = 0;
        double threshold = maxValue * 1.0e-8;

        for (int i = 1; i < nNoise; i++) {
            if (sortVec[i] > threshold) {
                sd += sortVec[i];
                n++;
            }
        }

        rmsd = sd / n;

        threshold = rmsd * ratio;

        ArrayList<Integer> baseList = new ArrayList<>();
        boolean lastWasBase = false;
        for (int i = 0; i < nRegions; i++) {
            if (sdVec[i] < threshold) {
                int start = i * winSize;
                if (!lastWasBase) {
                    start += winSize / 2; // don't use edge or area close to signal as baseline
                }
                int end = i * winSize + winSize - 1;
                if (end >= vecSize - winSize / 2) { // don't include edge as baseline
                    end = vecSize - winSize / 2;
                }
                baseList.add(start);
                baseList.add(end);
                lastWasBase = true;
            } else {
                if ((i >= 0) && lastWasBase) {
                    int index = baseList.size() - 1;
                    baseList.set(index, baseList.get(index) - (winSize / 2));
                }
                lastWasBase = false;
            }
        }
        return baseList;
    }

    public static RealVector fitPoly(Vec vector, int order,
                                     RealMatrix xyVals) {
        int nRows = xyVals.getRowDimension();
        RealMatrix A = new Array2DRowRealMatrix(nRows, order);
        RealVector B = new ArrayRealVector(nRows);

        for (int i = 0; i < nRows; i++) {
            A.setEntry(i, 0, 1.0);

            for (int j = 1; j < order; j++) {
                A.setEntry(i, j, A.getEntry(i, j - 1) * (xyVals.getEntry(i, 0) / vector.getSize()));
            }

            B.setEntry(i, xyVals.getEntry(i, 1));
        }

        SingularValueDecomposition svd = new SingularValueDecomposition(A);
        RealMatrix U = svd.getU();
        RealMatrix V = svd.getV();
        double[] svs = svd.getSingularValues();
        double coef;
        DecompositionSolver solver = svd.getSolver();
        RealVector X = solver.solve(B);
        return X;
    }

    public static RealVector fitSine(Vec vector, int order,
                                     RealMatrix xyVals) throws VecException {
        int nRows = xyVals.getRowDimension();
        RealMatrix A = new Array2DRowRealMatrix(nRows, order);
        RealVector B = new ArrayRealVector(nRows);

        for (int i = 0; i < nRows; i++) {
            A.setEntry(i, 0, 1.0);

            for (int j = 1; j < order; j++) {
                double yVal = 0.0;
                int trigOrder = (j + 1) / 2;
                if ((j % 2) == 0) {
                    yVal = Math.sin(xyVals.getEntry(i, 0) * trigOrder * 2 * Math.PI / (vector.getSize() - 1));
                } else {
                    yVal = Math.cos(xyVals.getEntry(i, 0) * trigOrder * 2 * Math.PI / (vector.getSize() - 1));

                }
                A.setEntry(i, j, yVal);
            }

            B.setEntry(i, xyVals.getEntry(i, 1));
        }

        SingularValueDecomposition svd = new SingularValueDecomposition(A);
        RealMatrix U = svd.getU();
        RealMatrix V = svd.getV();
        double[] svs = svd.getSingularValues();
        double coef;
        DecompositionSolver solver = svd.getSolver();
        RealVector X = solver.solve(B);
        return X;
    }

}
