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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Minimizer {
    private static final Logger log = LoggerFactory.getLogger(Minimizer.class);

    // epsmch is the machine precision
    static final double EPSMCH = 2.22044604926e-9;
    int nfev = 0;
    public double[] xv = null;
    public double[] yv = null;
    double[] a = null;
    java.util.Random generator = null;
    Lmdif_fcn lmdifFunc = null;

    public Minimizer(Lmdif_fcn lmdifFunc) {
        this.lmdifFunc = lmdifFunc;
    }

    public void initRandom(long seed) {
        generator = new java.util.Random(seed);
    }

    public void initpt() {
        a = new double[lmdifFunc.getN() + 1];
        lmdifFunc.initpt(a);
    }

    public String initpt(double[] guess) {
        a = new double[lmdifFunc.getN() + 1];
        if (a.length != (guess.length + 1)) {
            System.out.println("number of guesses not equal to number of pars");
            lmdifFunc.initpt(a);

            return "number of guesses not equal to number of pars";
        } else {
            for (int i = 0; i < guess.length; i++) {
                a[i + 1] = guess[i];
            }

            lmdifFunc.initpt(a);

            return null;
        }
    }

    public String initpt0(double[] guess, int[] map) {
        a = new double[lmdifFunc.getN() + 1];
        for (int i = 1; i < a.length; i++) {
            a[i] = guess[map[i] - 1];
        }

        return null;

    }

    public String simulate(double[] a, double sdev) {
        if (generator == null) {
            initRandom(0);
        }

        if (xv.length != yv.length) {
            return "X and Y arrays do not have equal length";
        }

        for (int i = 0; i < xv.length; i++) {
            yv[i] = lmdifFunc.calculate(a, xv[i])
                    + (generator.nextGaussian() * sdev);
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

    public void dumpXY() {
        for (int i = 0; i < xv.length; i++) {
            System.out.println(xv[i] + " " + yv[i]);
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

        tol = Math.sqrt(EPSMCH);

        m = yv.length;
        n = lmdifFunc.getN();
        ntries = 1;

        factor = 1.0;

        int[] iflag = {0, 0};

        try {
            for (k = 1; k <= ntries; k++) {
                if (k > 1) {
                    lmdifFunc.initpt(a);
                }
                lmdifFunc.fcn(m, n, a, fvec, iflag);

                lmdifFunc.clearEvaluationCount();
                Minpack_f77.lmdif1_f77(lmdifFunc, m, n, a, fvec, tol, info);
                lmdifFunc.getEvaluationCount();
                lmdifFunc.fcn(m, n, a, fvec, iflag);
                factor *= 10.0;
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }
}
