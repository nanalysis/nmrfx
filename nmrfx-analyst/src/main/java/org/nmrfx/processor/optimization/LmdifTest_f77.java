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

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

public class LmdifTest_f77 {
    // epsmch is the machine precision
    static final double epsmch = 2.22044604926e-16;
    int evaluationCount = 0;
    double[] xv = null;
    double[] yv = null;
    double[] a = null;
    double[] auxValues = null;
    double xMin = 0.0;
    double yMin = 0.0;
    double xMax = 0.0;
    double yMax = 0.0;
    double xMid = 0.0;
    java.util.Random generator = null;
    Lmdif_fcn lmdifFunc = null;
    Lmdif_fcn[] lmdifFuncs = {
            new fexpc_f77(),
            new fexpc1_f77(),
            new fexpb_f77(),
            new fexpg_f77(),
            new flogistic_f77(),
            new flogisticC1_f77(),
            new quadratic_f77(),
            new fexp2_f77(),
            new funcgf_f77(),
            new fexpd_f77(),
            new flineshapeG_f77(),
            new flineshapeL_f77()
    };

    public void setFunc(int funcNum) {
        if ((funcNum < 0) || (funcNum >= lmdifFuncs.length)) {
            System.out.println("Invalid function number " + funcNum);
        } else {
            lmdifFunc = lmdifFuncs[funcNum];
        }
    }

    public String[] getEquations() {
        String[] equations = new String[lmdifFuncs.length];

        for (int i = 0; i < lmdifFuncs.length; i++) {
            equations[i] = lmdifFuncs[i].getEquation();
        }

        return equations;
    }

    public void setAuxValues(double[] auxValues) {
        this.auxValues = auxValues;
    }

    public double[] guess() {
        initpt();

        if (lmdifFunc == null) {
            return null;
        } else {
            return lmdifFunc.guess();
        }
    }

    public int getN() {
        if (lmdifFunc == null) {
            return 0;
        } else {
            return lmdifFunc.getN();
        }
    }

    public String[] getAuxNames() {
        String[] auxNames = new String[0];
        if (lmdifFunc != null) {
            auxNames = lmdifFunc.getAuxNames();
        }
        return auxNames;
    }

    public void setN(int newN) {
        if (lmdifFunc != null) {
            lmdifFunc.setN(newN);
        }
    }

    public void setLengths(int n) {
        xv = new double[n];
        yv = new double[n];
    }

    public void setArrays(double[] x, double[] y) {
        xv = x;
        yv = y;
    }

    public void initRandom(long seed) {
        generator = new java.util.Random(seed);
    }

    public double[] getPars() {
        return a;
    }

    public double[] getArray(int iDim) {
        if (iDim == 0) {
            return xv;
        } else if (iDim == 1) {
            return yv;
        } else {
            throw new RuntimeException("invalid dimension in getArray");
        }
    }

    public void dumpXY() {
        for (int i = 0; i < xv.length; i++) {
            System.out.println(xv[i] + " " + yv[i]);
        }
    }

    public void initpt() {
        a = new double[lmdifFunc.getN() + 1];
        lmdifFunc.initpt(a);
    }

    public String initpt(double[] guess) {
        a = new double[lmdifFunc.getN() + 1];

        if (a.length != guess.length) {
            return "number of guesses not equal to number of pars";
        } else {
            for (int i = 1; i < guess.length; i++) {
                a[i] = guess[i];
            }

            return null;
        }
    }

    public String initpt0offset(double[] guess) {
        a = new double[lmdifFunc.getN() + 1];

        if (a.length != (guess.length + 1)) {
            return "number of guesses not equal to number of pars";
        } else {
            for (int i = 1; i < a.length; i++) {
                a[i] = guess[i - 1];
            }

            return null;
        }
    }

    public void randomizeGuess(double sdev) {
        if (generator == null) {
            initRandom(0);
        }

        for (int i = 1; i < a.length; i++) {
            a[i] += (generator.nextGaussian() * sdev);
        }
    }

    public String simulate(double sdev) {
        if (generator == null) {
            initRandom(0);
        }

        if (xv.length != yv.length) {
            return "X and Y arrays do not have equal length";
        }

        int n = lmdifFunc.getN();

        if (a.length != (n + 1)) {
            initpt();
        }

        for (int i = 0; i < xv.length; i++) {
            yv[i] = lmdifFunc.calculate(a, xv[i])
                    + (generator.nextGaussian() * sdev);
        }

        return null;
    }

    void getStatsForGuess() {
        RealVector xVec = new ArrayRealVector(xv, false);
        RealVector yVec = new ArrayRealVector(yv, false);
        int ixMin = xVec.getMinIndex();
        int ixMax = xVec.getMaxIndex();
        int iyMin = yVec.getMinIndex();
        int iyMax = yVec.getMaxIndex();
        xMin = xv[ixMin];
        yMin = yv[iyMin];
        xMax = xv[ixMax];
        yMax = yv[iyMax];

        double halfHeight = yMax / 2.0;
        double deltaUp = Double.MAX_VALUE;
        double deltaDown = Double.MAX_VALUE;
        double upX = 0.0;
        double downX = 0.0;
        double upY = 0.0;
        double downY = 0.0;

        for (int i = 0; i < yv.length; i++) {
            double dy = yv[i] - halfHeight;

            if ((dy >= 0) && (dy < deltaUp)) {
                deltaUp = dy;
                upX = xv[i];
                upY = yv[i];
            }

            if ((dy < 0) && ((-dy) < deltaDown)) {
                deltaDown = -dy;
                downX = xv[i];
                downY = yv[i];
            }
        }

        if (upY == downY) {
            xMid = (upX + downX) / 2.0;
        } else {
            xMid = ((halfHeight - downY) / (upY - downY) * (upX - downX))
                    + downX;
        }
    }

    public String getFittedVec(double[] xvec, double[] yvec) {
        if (xvec.length != yvec.length) {
            return "X and Y arrays do not have equal length";
        }

        for (int i = 0; i < xvec.length; i++) {
            yvec[i] = lmdifFunc.calculate(a, xvec[i]);
        }

        return null;
    }

    public double rms() {
        double[] fvec = new double[xv.length + 1];
        int m = yv.length;
        int n = lmdifFunc.getN();
        int[] iflag = {0, 0};
        lmdifFunc.fcn(m, n, a, fvec, iflag);

        double sum = 0.0;

        for (int i = 1; i <= m; i++) {
            sum += (fvec[i] * fvec[i]);
        }

        if (sum == 0.0) {
            return 0.0;
        } else {
            return (Math.sqrt(sum / m));
        }
    }

    public void doMin() {
        int k;
        int m;
        int n;
        int ntries;

        int[] info = new int[2];

        double factor;
        double tol;

        double[] fvec = new double[xv.length + 1];

        tol = Math.sqrt(epsmch);

        m = yv.length;
        n = lmdifFunc.getN();
        ntries = 1;

        factor = 1.0;

        int[] iflag = {0, 0};

        for (k = 1; k <= ntries; k++) {
            if (k > 1) {
                lmdifFunc.initpt(a);
            }

            lmdifFunc.fcn(m, n, a, fvec, iflag);

            Minpack_f77.lmdif1_f77(lmdifFunc, m, n, a, fvec, tol, info);
            lmdifFunc.fcn(m, n, a, fvec, iflag);
            factor *= 10.0;
        }
    }

    public static void main(String[] args) {
        LmdifTest_f77 lmdifTest = new LmdifTest_f77();
        lmdifTest.setFunc(7);
        lmdifTest.setLengths(64);

        for (int i = 0; i < lmdifTest.xv.length; i++) {
            lmdifTest.xv[i] = i;
        }

        lmdifTest.initpt();
        lmdifTest.simulate(0.02);
        lmdifTest.dumpXY();
        lmdifTest.randomizeGuess(0.5);
        lmdifTest.doMin();
        System.out.println(lmdifTest.rms());
    }

    class fexpc_f77 implements Lmdif_fcn {

        static final int nPar = 3;
        String[] auxNames = new String[0];

        public fexpc_f77() {
        }

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "A*exp(-x*B)+C";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 0.5;
            a[3] = 0.0;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = 1.0 / (xMid / 0.693);
            a[3] = 0.0;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            return (a[1] * Math.exp(-x * a[2])) + a[3];
        }
    }

    class fexpc_f77der implements Lmder_fcn {

        static final int nPar = 3;
        String[] auxNames = new String[0];

        public fexpc_f77der() {
        }

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "A*exp(-x*B)+C";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 0.5;
            a[3] = 0.0;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = 1.0 / (xMid / 0.693);
            a[3] = 0.0;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
            if (iflag[1] == 0) {
                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    double yval = calculate(a, t);
                    fvec[i] = yval - yv[i - 1];
                }
            } else {
                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    derivative(a, t, fjac, i);
                }
            }
        }

        public double calculate(double[] a, double x) {
            return (a[1] * Math.exp(-x * a[2])) + a[3];
        }

        public void derivative(double[] a, double x, double[][] fjac, int i) {
            double eVal = Math.exp(-x * a[2]);
            fjac[i][1] = eVal;
            fjac[i][2] = -a[1] * x * eVal;
            fjac[i][3] = 1.0;
        }
    }

    class fexpc1_f77 implements Lmdif_fcn {

        static final int nPar = 2;
        String[] auxNames = new String[0];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "A*exp(-x*B)";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 0.5;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = 1.0 / (xMid / 0.693);

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            return a[1] * Math.exp(-x * a[2]);
        }
    }

    class fexpb_f77 implements Lmdif_fcn {

        static final int nPar = 2;
        String[] auxNames = new String[0];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "A*x*exp(-x*B)";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 1.0;
            a[2] = 0.5;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yv[2] / xv[2];
            a[2] = 0.0;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            return a[1] * x * Math.exp(-x * a[2]);
        }
    }

    class fexpg_f77 implements Lmdif_fcn {

        static final int nPar = 3;
        String[] auxNames = new String[0];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "A*exp(-((x-B)/C)^2)";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 0.0;
            a[3] = 1.0;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = xMax;

            if (xMax > xMid) {
                a[3] = (xMax - xMid) / 0.693;
            } else {
                a[3] = (xMid - xMax) / 0.693;
            }

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            double arg = (x - a[2]) / a[3];
            double ex = Math.exp(-arg * arg);

            return a[1] * ex;
        }
    }

    class flogistic_f77 implements Lmdif_fcn {

        static final int nPar = 4;
        String[] auxNames = new String[0];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "((D-A)*x^C)/(x^C+B^C)+A";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 0.0;
            a[2] = 0.4;
            a[3] = 1.0;
            a[4] = 1.0;
        }

        public double[] guess() {
            getStatsForGuess();
            if (xMin > xMax) {
                a[1] = yMax;
                a[4] = yMin;
            } else {
                a[1] = yMin;
                a[4] = yMax;
            }
            a[2] = xMid;
            a[3] = 1.0;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            return (((a[4] - a[1]) * Math.pow(x, a[3])) / (Math.pow(x, a[3])
                    + Math.pow(a[2], a[3]))) + a[1];
        }
    }

    class flogisticC1_f77 implements Lmdif_fcn {

        static final int nPar = 3;
        String[] auxNames = new String[0];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "((C-A)*x)/(x+B)+A";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 0.0;
            a[2] = 0.4;
            a[3] = 1.0;
        }

        public double[] guess() {
            getStatsForGuess();
            if (xMin > xMax) {
                a[1] = yMax;
                a[3] = yMin;
            } else {
                a[1] = yMin;
                a[3] = yMax;
            }
            a[2] = xMid;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            return (((a[3] - a[1]) * x) / (x + a[2])) + a[1];
        }
    }

    class quadratic_f77 implements Lmdif_fcn {

        static final int nPar = 3;
        final String auxNames[] = {"Pt"};

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "A+(C-A)*((Pt+10^x+B)+((Pt+10^x+B)^2-4*Pt*10^x)^1/2))/(2*Pt)";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 0.0;
            a[2] = 0.4;
            a[3] = 1.0;
        }

        public double[] guess() {
            getStatsForGuess();
            if (xMin > xMax) {
                a[1] = yMax;
                a[3] = yMin;
            } else {
                a[1] = yMin;
                a[3] = yMax;
            }
            a[2] = Math.log(xMid) / Math.log(10.0);

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            double delta = a[3] - a[1];
            double Pt = auxValues[0];
            double n1 = Pt + x + Math.pow(10.0, a[2]);
            double s1 = Math.sqrt(n1 * n1 - 4 * Pt * x);
            double f = (n1 - s1) / (2 * Pt);
            return a[1] + delta * f;
        }
    }

    class fexp2_f77 implements Lmdif_fcn {

        static final int nPar = 3;
        String[] auxNames = new String[0];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "A*(exp(-x/B)+exp(-x/C))";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 1.0;
            a[3] = 0.5;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = xMid / 0.693 / 2.0;
            a[2] = (5.0 * xMid) / 0.693;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            return a[1] * (Math.exp(-x / a[2]) + Math.exp(-x / a[3]));
        }
    }

    class funcgf_f77 implements Lmdif_fcn {

        static final int nPar = 3;
        String[] auxNames = new String[0];

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "A*exp(-x*C)*exp(-i*x*B)";
        }

        public final void initpt(double[] a) {
            a[1] = 1.0;
            a[2] = 0.1;
            a[3] = 0.1;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = 1.0;
            a[2] = 0.1;
            a[3] = 0.1;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            int itstl;
            boolean even;
            double freq;
            double decay;
            double amp;
            double cosx;
            double sinx;
            double expx;
            double time;
            itstl = (int) (x + 0.5);

            if ((itstl % 2) == 1) {
                even = false;
            } else {
                even = true;
            }

            time = itstl / 2;

            double yval = 0.0;

            for (int j = 1; j < a.length; j += 3) {
                amp = a[j];
                freq = a[j + 1];
                decay = a[j + 2];
                cosx = Math.cos(time * freq);
                sinx = Math.sin(time * freq);
                expx = Math.exp(-decay * time);

                /*printf("%f %f %f %f\n",time,cosx,sinx,expx); */
                if (even) {
                    yval += (amp * expx * cosx);
                } else {
                    yval += (amp * expx * sinx);
                }
            }

            return yval;
        }
    }

    class fexpd_f77 implements Lmdif_fcn {

        static final int nPar = 2;
        String[] auxNames = new String[0];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "-2.0*A*exp(-x/B)+A";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 2.0;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = xMid / 0.693;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            return ((-2.0 * a[1] * Math.exp(-x / a[2])) + a[1]);
        }
    }

    class flineshapeG_f77 implements Lmdif_fcn {

        int xy_nsig = 1;
        int xy_ndim = 1;
        int nPar = xy_nsig * ((xy_ndim * 2) + 1);
        String[] auxNames = new String[0];
        int[] iJCal = new int[xy_ndim];
        int[] iNPar = new int[xy_ndim];
        double[] ys = new double[xy_ndim];
        double[] xs = new double[xy_ndim];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "Gaussian Line";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
            nPar = newN;
            xy_nsig = nPar / ((xy_ndim * 2) + 1);
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 1.0;
            a[3] = 0.5;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = (xMin + xMax) / 2.0;
            a[3] = (xMax - xMin) / 3.0;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            double y = 0;
            xs[0] = x;

            int kk = 0;
            iNPar[0] = 2;

            for (int k = 0; k < xy_nsig; k++) {
                int start = kk + 1;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ys[iDim] = gShape(a, start, xs[iDim], iJCal[iDim]);
                }

                double ypeak = a[start];

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ypeak = ypeak * ys[iDim];
                }

                y += ypeak;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    kk += iNPar[iDim];
                }

                kk++;
            }

            return y;
        }

        public double gShape(double[] a, int start, double x, int jcal) {
            double y = 0.0;
            double b = a[start + 2] / 1.66;
            double freq = a[start + 1];

            if (jcal == 1) {
                double arg1 = (x - freq + (a[start + 3] / 2.0)) / b;
                double arg2 = (x - freq - (a[start + 3] / 2.0)) / b;
                y = Math.exp(-arg1 * arg1) + Math.exp(-arg2 * arg2);
            } else if (jcal == -1) {
                double arg1 = (x - freq + (a[start + 3] / 2.0)) / b;
                double arg2 = (x - freq - (a[start + 3] / 2.0)) / b;
                y = Math.exp(-arg1 * arg1) - Math.exp(-arg2 * arg2);
            } else {
                double arg1 = (x - freq) / b;
                y = Math.exp(-arg1 * arg1);
            }

            return y;
        }
    }

    class flineshapeL_f77 implements Lmdif_fcn {

        int xy_nsig = 1;
        int xy_ndim = 1;
        int nPar = xy_nsig * ((xy_ndim * 2) + 1);
        String[] auxNames = new String[0];
        int[] iJCal = new int[xy_ndim];
        int[] iNPar = new int[xy_ndim];
        double[] ys = new double[xy_ndim];
        double[] xs = new double[xy_ndim];

        public int getEvaluationCount() {
            return evaluationCount;
        }

        public void clearEvaluationCount() {
            evaluationCount = 0;
        }

        public String getEquation() {
            return "Lorentzian Line";
        }

        public int getN() {
            return nPar;
        }

        public String[] getAuxNames() {
            return auxNames;
        }

        public void setN(int newN) {
            nPar = newN;
            xy_nsig = nPar / ((xy_ndim * 2) + 1);
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 1.0;
            a[3] = 0.5;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = (xMin + xMax) / 2.0;
            a[3] = (xMax - xMin) / 3.0;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            double y = 0;
            xs[0] = x;

            int kk = 0;
            iNPar[0] = 2;

            for (int k = 0; k < xy_nsig; k++) {
                int start = kk + 1;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ys[iDim] = lShape(a, start, xs[iDim], iJCal[iDim]);
                }

                double ypeak = a[start];

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ypeak = ypeak * ys[iDim];
                }

                y += ypeak;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    kk += iNPar[iDim];
                }

                kk++;
            }

            return y;
        }

        public double lShape(double[] a, int start, double x, int jcal) {
            double y = 0.0;
            double freq = a[start + 1];
            double b = a[start + 2] * 0.5;

            if (jcal == 1) {
                double f1 = freq - (a[start + 3] / 2.0);
                double f2 = freq - (a[start + 3] / 2.0);
                double y1 = (b * b) / ((b * b) + ((x - f1) * (x - f1)));
                double y2 = (b * b) / ((b * b) + ((x - f2) * (x - f2)));
                y = y1 + y2;
            } else if (jcal == -1) {
                double f1 = freq - (a[start + 3] / 2.0);
                double f2 = freq - (a[start + 3] / 2.0);
                double y1 = (b * b) / ((b * b) + ((x - f1) * (x - f1)));
                double y2 = (b * b) / ((b * b) + ((x - f2) * (x - f2)));
                y = y1 - y2;
            } else {
                y = (b * b) / ((b * b) + ((x - freq) * (x - freq)));
            }

            return y;
        }
    }

}
