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
package org.nmrfx.processor.math;

import org.apache.commons.math3.complex.Complex;

public class Cfft {
    private static double[] sineTable = null;
    private static double[] cosTable = null;

    static void fillTables(int n) {
        sineTable = new double[n];
        cosTable = new double[n];
        sineTable[1] = -1.0;
        cosTable[1] = 0.0;
        sineTable[2] = 0.0;
        cosTable[2] = 1.0;

        for (int li = 3; li < n; li++) {
            double theta = Math.PI / li;
            sineTable[li] = Math.sin(theta);
            cosTable[li] = Math.cos(theta);
        }
    }

    public static void cfft(Complex[] cvec, int size, int mode) {
        int i;
        int ip;
        int j;
        int k;
        int li;
        int n;
        int m;
        int length;

        n = 1;

        while (size > n) {
            n *= 2;
        }

        if (mode == 1) {
            j = n / 2;
            k = n - 1;
            m = (n / 2) + 1;

            for (i = 0; i < (n / 4); i++) {

                Complex hold = cvec[j];
                cvec[j] = cvec[i];
                cvec[i] = hold;
                j--;

                hold = cvec[m];
                cvec[m] = cvec[k];
                cvec[k] = hold;
                k--;
                m++;

            }
        }

        /* Decimation in time (DIT) algorithm */
        j = 0;

        for (i = 0; i < (n - 1); i++) {
            if (i < j) {
                Complex hold = cvec[i];
                cvec[i] = cvec[j];
                cvec[j] = hold;
            }

            k = n / 2;

            while (k <= j) {
                j -= k;
                k /= 2;
            }

            j += k;
        }

        /* Actual FFT */
        if ((sineTable == null) || (sineTable.length < n)) {
            fillTables(n);
        }

        for (li = 1; li < n; li *= 2) {
            length = 2 * li;
            Complex u = Complex.ONE;

            Complex w;

            switch (li) {
                case 1:
                    w = new Complex(-1.0, 0.0);
                    break;
                case 2:
                    w = new Complex(0.0, 1.0);
                    break;
                default:
                    w = new Complex(cosTable[li], sineTable[li]);
                    break;
            }

            for (j = 0; j < li; j++) {
                for (i = j; i < n; i += length) {
                    ip = i + li;

                    /* step 1 */
                    Complex t = cvec[ip].multiply(u);

                    /* step 2 */
                    cvec[ip] = cvec[i].subtract(t);

                    /* step 3 */
                    cvec[i] = cvec[i].add(t);
                }

                Complex tmp = u.multiply(w);
                u = new Complex(tmp.getReal(), tmp.getImaginary());
            }
        }

        if (mode == 0) {
            j = n / 2;
            k = n - 1;
            m = (n / 2) + 1;

            for (i = 0; i < (n / 4); i++) {
                Complex hold = cvec[j];
                cvec[j] = cvec[i];
                cvec[i] = hold;
                j--;

                hold = cvec[m];
                cvec[m] = cvec[k];
                cvec[k] = hold;
                k--;
                m++;
            }
        }
    }

    /* ifft -- inverse FFT using the same interface as fft() */
    public static void ift(Complex[] cvec, int size) {
        double mul = -1.0 / ((double) (size));
        int i;

        /* we just use complex conjugates */
        for (i = 0; i < size; i++) {
            cvec[i] = cvec[i].conjugate();
        }

        cfft(cvec, size, 1);

        for (i = 0; i < size; i++) {
            cvec[i] = new Complex((-cvec[i].getReal() * mul), cvec[i].getImaginary() * mul);

        }
    }
}
