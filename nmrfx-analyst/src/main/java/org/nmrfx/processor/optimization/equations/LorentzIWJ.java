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
package org.nmrfx.processor.optimization.equations;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.nmrfx.processor.optimization.NNLSMat;
import org.nmrfx.processor.optimization.SineSignal;

import java.util.ArrayList;
import java.util.Collections;

public class LorentzIWJ implements MultivariateFunction {

    int nfev = 0;
    public double[] xv = null;
    public double[] yv = null;
    double[] a = null;
    public int[] map = null;
    int nFit = 0;
    int ampStart = 0;
    int nPar = 0;
    double[] aCalc = null;
    int xy_nsig = 1;
    int xy_ndim = 1;
    double[] ys = new double[xy_ndim];
    double[] xs = new double[xy_ndim];
    int[] sigStarts = null;
    int[][] splitCount = null;
    double[][] couplings = null;
    double[][] amplitudes = null;
    double[][] freqs = null;
    double[] cfreqs = null;
    double[] lw = null;
    int[] ampMap = null;
    LwFrSorter lwfrSorter = null;

    public String getEquation() {
        return "Lineshape";
    }

    public int getN() {
        return nFit;
    }

    public void setN(int newN) {
        nPar = newN;
    }

    public void dumpMatrix(double[][] A) {
        for (int i = 0; i < A.length; i++) {
            System.out.print(i + " ");

            for (int j = 0; j < A[i].length; j++) {
                System.out.print(A[i][j] + " ");
            }

            System.out.println("");
        }
    }

    public void dumpSignals() {
        for (int i = 0; i < couplings.length; i++) {
            for (int j = 0; j < couplings[i].length; j++) {
                System.out.print("coup " + couplings[i][j]);
            }

            System.out.println("");
        }

        for (int i = 0; i < amplitudes.length; i++) {
            for (int j = 0; j < amplitudes[i].length; j++) {
                System.out.print("amp " + amplitudes[i][j]);
            }

            System.out.println("");
        }

        for (int i = 0; i < freqs.length; i++) {
            for (int j = 0; j < freqs[i].length; j++) {
                System.out.print("freq " + freqs[i][j]);
            }

            System.out.println("");
        }

        for (int i = 0; i < sigStarts.length; i++) {
            System.out.println("sigStarts " + sigStarts[i]);
        }
    }

    public void setSignals(int[][] splitCount) {
        xy_nsig = splitCount.length;
        lw = new double[xy_nsig];
        couplings = new double[xy_nsig][];
        amplitudes = new double[xy_nsig][];
        freqs = new double[xy_nsig][];
        cfreqs = new double[xy_nsig];
        this.splitCount = splitCount;
        sigStarts = new int[xy_nsig];

        int start = 0;
        int nAmps = 0;
        nFit = 0;

        for (int i = 0; i < xy_nsig; i++) {
            sigStarts[i] = start;
            start++; // allow for linewidth par
            nFit++;

            int nFreqs = 1;
            if ((splitCount[i].length == 1) && (splitCount[i][0] < 0)) { // generic multiplet
                nFreqs = -splitCount[i][0];
                couplings[i] = new double[0];
                start += nFreqs;
                nFit += nFreqs;
            } else {
                for (int j = 0; j < splitCount[i].length; j++) {
                    nFreqs = nFreqs * splitCount[i][j];
                }

                couplings[i] = new double[splitCount[i].length];
                start += (couplings[i].length + 1);
                nFit += (couplings[i].length + 1);
            }

            amplitudes[i] = new double[nFreqs];
            freqs[i] = new double[nFreqs];
            nAmps += nFreqs;
        }
        ampStart = nFit;
        nPar = nFit + nAmps;
    }

    public final void initpt(double[] a) {
        aCalc = new double[nPar];
        map = new int[a.length];

        for (int i = 0; i < a.length; i++) {
            aCalc[i] = a[i];
            map[i] = i;
        }
    }

    public final void initLWAmpFit(double[] a) {
        nFit = xy_nsig;
        map = new int[a.length];
        for (int iSig = 0; iSig < xy_nsig; iSig++) {
            int start = sigStarts[iSig];
            map[iSig + 1] = start;
        }
    }

    public final void initTestPt(double[] a) {
        aCalc = new double[nPar];
        aCalc[1] = 2.5;
        aCalc[2] = 6.0;
        aCalc[3] = 10.0;
        aCalc[4] = 2.0;
        aCalc[5] = 2.0;
        aCalc[6] = 2.0;
        map = new int[a.length];

        for (int i = 0; i < a.length; i++) {
            a[i] = aCalc[i];
            map[i] = i;
        }
    }

    public double[] guess() {
        return a;
    }

    public int getFuncEvals() {
        return nfev;
    }

    public void fcn(int m, int n, double[] a, double[] fvec) {
        for (int i = 0; i < n; i++) {
            aCalc[map[i]] = a[i];
        }

        double[][] A = fillMatrix(aCalc);
        double[][] A2 = new double[A.length][A[0].length];

        for (int i = 0; i < A2.length; i++) {
            for (int j = 0; j < A2[i].length; j++) {
                A2[i][j] = A[i][j];
            }
        }
        A = combineWeakDoubletsForAmplitudeCalc(A);
        A = lwfrSorter.check(A);

        double[] amps = fitSignalAmplitudesNN(A);
        int iAmp = 0;

        for (int j = ampStart; j < aCalc.length; j++) {
            double amp = amps[ampMap[iAmp]];

            if (aCalc[j] < 0) {
                amp = -amp;
            }

            aCalc[j] = amp;
            iAmp++;
        }

        nfev++;

        for (int i = 0; i < A2.length; i++) {
            double sum = 0.0;

            for (int j = 0; j < A2[i].length; j++) {
                sum += (A2[i][j] * aCalc[ampStart + j]);
            }

            fvec[i] = sum - yv[i];
        }
    }

    public void fcnAmps() {
        if (aCalc == null) {
            System.out.println("acalc null");
            return;
        }
        double[][] A = fillMatrix(aCalc);
        double[][] A2 = new double[A.length][A[0].length];

        for (int i = 0; i < A2.length; i++) {
            for (int j = 0; j < A2[i].length; j++) {
                A2[i][j] = A[i][j];
            }
        }
        A = combineWeakDoubletsForAmplitudeCalc(A);
        A = lwfrSorter.check(A);

        double[] amps = fitSignalAmplitudesNN(A);
        int iAmp = 0;

        for (int j = ampStart; j < aCalc.length; j++) {
            double amp = amps[ampMap[iAmp]];
            if (aCalc[j] < 0) {
                amp = -amp;
            }

            aCalc[j] = amp;
            iAmp++;
        }
    }

    public double rms() {
        if (aCalc == null) {
            System.out.println("acalc null");
            return 0.0;
        }

        double[][] A = fillMatrix(aCalc);
        double sumDevSq = 0.0;
        for (int i = 0; i < A.length; i++) {
            double sum = 0.0;

            for (int j = 0; j < A[i].length; j++) {
                sum += (A[i][j] * aCalc[ampStart + j]);
            }

            sumDevSq += (sum - yv[i]) * (sum - yv[i]);
        }
        if (sumDevSq == 0.0) {
            return 0.0;
        } else {
            return (Math.sqrt(sumDevSq / A.length));
        }
    }

    public int maxPosDev() {
        if (aCalc == null) {
            System.out.println("acalc null");
            return -1;
        }
        double[][] A = fillMatrix(aCalc);
        double maxPosDev = Double.NEGATIVE_INFINITY;
        int devLoc = 0;
        for (int i = 0; i < A.length; i++) {
            double sum = 0.0;

            for (int j = 0; j < A[i].length; j++) {
                sum += (A[i][j] * aCalc[ampStart + j]);
            }

            double dev = yv[i] - sum;
            if (dev > maxPosDev) {
                maxPosDev = dev;
                devLoc = (int) xv[i];
            }
        }
        return devLoc;
    }

    public double value(double[] a) {
        int m = xv.length;
        int n = a.length;
        ;
        double[] fvec = new double[m];
        fcn(m, n, a, fvec);
        double sumsq = 0.0;
        for (int i = 0; i < fvec.length; i++) {
            sumsq += fvec[i] * fvec[i];
        }
        return sumsq;
    }

    public double calculate(double[] a, double x) {
        double y = 0;
        xs[0] = x;

        for (int k = 0; k < xy_nsig; k++) {
            y += calculateOneSig(a, k, x);
        }

        return y;
    }

    public double calculateOneSig(double[] a, int iSig, double x) {
        int start = sigStarts[iSig];
        ;

        for (int i = 0; i < couplings[iSig].length; i++) {
            couplings[iSig][i] = a[start++];
        }

        freqs[iSig][0] = a[start];

        for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
            amplitudes[iSig][iLine] = a[start + 1 + iLine];
        }

        jSplittings(couplings[iSig], splitCount[iSig], freqs[iSig]);

        double y = 0.0;

        for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
            double yTemp = lShape(x, a[1], freqs[iSig][iLine]);

            if (amplitudes[iSig][iLine] < 0) {
                yTemp = -yTemp;
            }

            y += (yTemp * amplitudes[iSig][iLine]);
        }

        return y;
    }

    public double getLinewidth(int iSig) {
        return lw[iSig];
    }

    public double[] getCouplings(int iSig) {
        return couplings[iSig];
    }

    public double[] getFreqs(int iSig) {
        return freqs[iSig];
    }

    public double getCFreq(int iSig) {
        return cfreqs[iSig];
    }

    public double[] getAmps(int iSig) {
        return amplitudes[iSig];
    }

    public ArrayList getSignals() {
        int ampIndex = ampStart;
        ArrayList signalGroups = new ArrayList(xy_nsig);

        for (int iSig = 0; iSig < xy_nsig; iSig++) {
            ArrayList signals = new ArrayList();
            signalGroups.add(signals);

            int start = sigStarts[iSig];
            lw[iSig] = Math.abs(aCalc[start++]);

            if ((splitCount[iSig].length == 1) && (splitCount[iSig][0] < 0)) { // generic multiplet

                int nFreqs = -splitCount[iSig][0];
                double sum = 0.0;

                for (int i = 0; i < nFreqs; i++) {
                    freqs[iSig][i] = aCalc[start++];
                    sum += freqs[iSig][i];
                }

                cfreqs[iSig] = sum / nFreqs;
            } else {
                freqs[iSig][0] = cfreqs[iSig] = aCalc[start++];

                for (int i = 0; i < couplings[iSig].length; i++) {
                    couplings[iSig][i] = aCalc[start++];
                }

                jSplittings(couplings[iSig], splitCount[iSig], freqs[iSig]);
            }

            for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
                amplitudes[iSig][iLine] = aCalc[ampIndex++];

                SineSignal signal = new SineSignal(freqs[iSig][iLine],
                        lw[iSig], amplitudes[iSig][iLine]);
                signals.add(signal);
            }

            Collections.sort(signals);
        }

        return signalGroups;
    }

    private class LwFr implements Comparable<LwFr> {

        double freq1;
        double freq2 = Double.MAX_VALUE;
        double lw;
        int iCol;
        int partnerCol = -1;
        int iSig;
        int iLine;
        boolean inActive = false;

        LwFr(int iCol, int iSig, int iLine, double freq1, double lw) {
            this.iCol = iCol;
            this.iSig = iSig;
            this.iLine = iLine;
            this.freq1 = freq1;
            this.lw = lw;
        }

        @Override
        public int compareTo(LwFr lwfrB) {
            double ppmA = this.freq1;
            double ppmB = lwfrB.freq1;
            double lwA = this.lw;
            double lwB = lwfrB.lw;
            int result = (ppmA == ppmB) ? 0 : ((ppmA < ppmB) ? (-1) : 1);
            if (result == 0) {
                result = (lwA == lwB) ? 0 : ((lwA < lwB) ? (-1) : 1);
            }
            return result;
        }
    }

    private class LwFrSorter {

        ArrayList<LwFr> lwfrs = new ArrayList<>();

        int i = 0;

        LwFrSorter(int nFr) {
            lwfrs.ensureCapacity(nFr);
        }

        void add(LwFr lwfr) {
            lwfrs.add(lwfr);
        }

        void sort() {
            Collections.sort(lwfrs);
        }

        public boolean fuzzyOverlap(int a, int b) {
            double frac = 0.05;
            double fracLW = 0.3;
            LwFr lwfrA = lwfrs.get(a);
            LwFr lwfrB = lwfrs.get(b);
            double ppmA = lwfrA.freq1;
            double ppmB = lwfrB.freq1;
            double lwA = lwfrA.lw;
            double lwB = lwfrB.lw;
            double delta = Math.abs(ppmA - ppmB);
            double tol = 0.0;
            double tolLW = 0.0;
            if (lwA < lwB) {
                tol = lwA * frac;
                tolLW = lwA * fracLW;
            } else {
                tol = lwB * frac;
                tolLW = lwB * fracLW;
            }
            boolean result = false;
            double delta2 = 0.0;
            if ((lwfrA.freq2 == Double.MAX_VALUE) && (lwfrB.freq2 != Double.MAX_VALUE)) {
                delta2 = Double.MAX_VALUE;
            } else if ((lwfrA.freq2 != Double.MAX_VALUE) && (lwfrB.freq2 == Double.MAX_VALUE)) {
                delta2 = Double.MAX_VALUE;
            } else if ((lwfrA.freq2 != Double.MAX_VALUE) && (lwfrB.freq2 != Double.MAX_VALUE)) {
                delta2 = Math.abs(lwfrA.freq2 - lwfrB.freq2);
            }

            if ((delta2 < tol) && (delta < tol)) {
                double deltaLW = Math.abs(lwA - lwB);
                if (deltaLW < tolLW) {
                    result = true;
                }
            }
            return result;
        }

        double[][] check(double[][] A) {
            sort();
            ampMap = new int[lwfrs.size()];
            int nRows = A.length;
            int nCols = A[0].length;
            int newCols = 0;
            int i = 0;
            int iCol = 0;
            while (i < lwfrs.size()) {
                if (lwfrs.get(i).inActive) {
                    i++;
                    continue;
                }
                LwFr iLwFr = lwfrs.get(i);
                ampMap[iLwFr.iCol] = iCol;
                if (iLwFr.partnerCol != -1) {
                    ampMap[iLwFr.partnerCol] = iCol;
                }

                int j = i + 1;
                while (j < lwfrs.size()) {
                    LwFr jLwFr = lwfrs.get(j);

                    if (!jLwFr.inActive) {
                        if (fuzzyOverlap(i, j)) {
                            ampMap[jLwFr.iCol] = iCol;
                        } else {
                            break;
                        }
                    }
                    j++;
                }
                i = j;

                iCol++;
            }
            newCols = iCol;

            double[][] newA = new double[nRows][newCols];
            for (int j = 0; j < nCols; j++) {
                for (int iRow = 0; iRow < nRows; iRow++) {
                    newA[iRow][ampMap[j]] += A[iRow][j];
                }
            }

            return newA;
        }
    }

    double[][] fillMatrix(double[] a) {
        int nRows = xv.length;
        int nCols = 0;

        for (int iSig = 0; iSig < xy_nsig; iSig++) {
            nCols += freqs[iSig].length;
        }

        double[][] A = new double[nRows][nCols];

        int iCol = 0;
        lwfrSorter = new LwFrSorter(nCols);

        for (int iSig = 0; iSig < xy_nsig; iSig++) {
            int start = sigStarts[iSig];

            double lw = a[start++];
            start = splitToFreqList(start, a, iSig);

            for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
                double freq = freqs[iSig][iLine];

                LwFr lwfr = new LwFr((iCol + iLine), iSig, iLine, freq, lw);
                lwfrSorter.add(lwfr);
                for (int j = 0; j < nRows; j++) {
                    double yTemp = lShape(xv[j], lw, freq);
                    if (amplitudes[iSig][iLine] < 0) {
                        yTemp = -yTemp;
                    }

                    A[j][iCol + iLine] = yTemp;
                }
            }
            iCol += freqs[iSig].length;
        }

        return A;
    }

    private double[][] combineWeakDoubletsForAmplitudeCalc(double[][] A) {
        int iCol = 0;
        int jCol = 0;
        int nCols = A[0].length;
        double[][] newA = A;
        ampMap = new int[nCols];
        for (int iSig = 0; iSig < xy_nsig; iSig++) {
            int nSplits = splitCount[iSig].length;
            if ((nSplits > 1) && (splitCount[iSig][nSplits - 1] == 2)) {
                for (int j = 0; j < freqs[iSig].length; j += 2) {
                    LwFr lwfrsA = lwfrSorter.lwfrs.get(jCol + j);
                    LwFr lwfrsB = lwfrSorter.lwfrs.get(jCol + j + 1);
                    lwfrsA.freq2 = lwfrsB.freq1;
                    lwfrsB.inActive = true;
                    lwfrsA.partnerCol = lwfrsB.iCol;
                }
                iCol += freqs[iSig].length / 2;
            } else {
                iCol += freqs[iSig].length;
            }
            jCol += freqs[iSig].length;
        }
        return newA;
    }

    private int splitToFreqList(int start, double[] a, int iSig) {

        if ((splitCount[iSig].length == 1) && (splitCount[iSig][0] < 0)) {
            // generic multiplet
            int nFreqs = -splitCount[iSig][0];

            for (int i = 0; i < nFreqs; i++) {
                freqs[iSig][i] = a[start++];
            }
        } else {
            freqs[iSig][0] = a[start++];

            for (int i = 0; i < couplings[iSig].length; i++) {
                couplings[iSig][i] = a[start++];
            }

            jSplittings(couplings[iSig], splitCount[iSig], freqs[iSig]);
        }

        for (int iLine = 0; iLine < freqs[iSig].length; iLine++) {
            int iF = (int) Math.round(freqs[iSig][iLine]);

            if (iF < 0) {
                iF = 0;
            } else if (iF >= yv.length) {
                iF = yv.length - 1;
            }

            if (yv[iF] >= 0.0) {
                amplitudes[iSig][iLine] = 1.0;
            } else {
                amplitudes[iSig][iLine] = -1.0;
            }

        }

        return start;
    }

    public void jSplittings(double[] couplings, int[] splitCount, double[] freqs) {
        int current = 1;
        int nCouplings = couplings.length;

        for (int i = 0; i < nCouplings; i++) {
            int last = (splitCount[i] * current) - 1;

            for (int j = 0; j < current; j++) {
                double offset = couplings[i] * ((splitCount[i] / 2.0) - 0.5);

                for (int k = 0; k < splitCount[i]; k++) {
                    freqs[last--] = freqs[current - j - 1] + offset;
                    offset -= couplings[i];
                }
            }

            current *= splitCount[i];
        }
    }

    public double lShape(double x, double b, double freq) {
        double y = 0.0;
        b *= 0.5;
        y = (b * b) / ((b * b) + ((x - freq) * (x - freq)));

        return y;
    }

    public double[] fitSignalAmplitudesNN(double[][] A) {
        int nRows = xv.length;
        int nCols = A[0].length;
        double[] b = new double[nRows];

        for (int i = 0; i < nRows; i++) {
            b[i] = yv[i];
        }
        int[] pivot = new int[nCols];

        for (int j = 0; j < nCols; j++) {
            pivot[j] = j;
        }
        RealMatrix aMat = new Array2DRowRealMatrix(A);
        RealMatrix bMat = new Array2DRowRealMatrix(b);
        NNLSMat nnlsMat = new NNLSMat(aMat, bMat);

        double[] Xp = nnlsMat.getX();
        double[] X = new double[nCols];
        for (int i = 0; i < nCols; i++) {
            double amp = Xp[i];
            int iSig = i;
            X[iSig] = amp;
        }
        return X;
    }

    public static void main(String[] args) {
    }
}
