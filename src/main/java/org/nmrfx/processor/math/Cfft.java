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

//import com.imsl.math.Complex;
public class Cfft {

    static double[] sineTable = null;
    static double[] cosTable = null;

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
        Complex w = new Complex(0.0, 0.0);

        n = 1;

        while (size > n) {
            n *= 2;
        }

        //BUG x = zv_resize(x, n);
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

            if (li == 1) {
                w = new Complex(-1.0, 0.0);
            } else if (li == 2) {
                w = new Complex(0.0, 1.0);
            } else {
                w = new Complex(cosTable[li], sineTable[li]);
            }

            for (j = 0; j < li; j++) {
                for (i = j; i < n; i += length) {
                    ip = i + li;

                    /* step 1 */
                    Complex t = cvec[ip].multiply(u);
                    //  t.real = (cvec[ip].real * u.real) - (cvec[ip].imaginary * u.imaginary);
                    //  t.imaginary = (cvec[ip].real * u.imaginary) + (cvec[ip].imaginary * u.real);

                    /* step 2 */
                    cvec[ip] = cvec[i].subtract(t);
                    // cvec[ip].real = cvec[i].real - t.real;
                    // cvec[ip].imaginary = cvec[i].imaginary - t.imaginary;

                    /* step 3 */
                    cvec[i] = cvec[i].add(t);
                    //  cvec[i].real += t.real;
                    //  cvec[i].imaginary += t.imaginary;
                }

                Complex tmp = u.multiply(w);
                u = new Complex(tmp.getReal(), tmp.getImaginary());
                //tmp.real = (u.real * w.real) - (u.imaginary * w.imaginary);
                //tmp.imaginary = (u.imaginary * w.real) + (u.real * w.imaginary);
                //u.real = tmp.real;
                // u.imaginary = tmp.imaginary;
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

        return;
    }

    /* ifft -- inverse FFT using the same interface as fft() */
    public static void ift(Complex[] cvec, int size) {
        double mul = -1.0 / ((double) (size));
        int i;

        /* we just use complex conjugates */
        for (i = 0; i < size; i++) {
            cvec[i] = cvec[i].conjugate();
//            cvec[i].imaginary = -cvec[i].imaginary;
        }

        cfft(cvec, size, 1);

        for (i = 0; i < size; i++) {
            cvec[i] = new Complex((-cvec[i].getReal() * mul), cvec[i].getImaginary() * mul);
            //  cvec[i].imaginary = cvec[i].imaginary * mul;
            //  cvec[i].real = -cvec[i].real * mul;
        }

        return;
    }
    /*
     public static void rfft(double[] vec, int n, int mode) {
     int i;
     double c;
     double s;
     double angle;
     double d1r;
     double d1i;
     double d2r;
     double d2i;
     double cos_angle;
     double sin_angle;
     double t;
     double d;
     double[] data = null;

     if (n < 2) {
     return;
     }

     int m = n / 2;
     Complex[] cvec = new Complex[m];

     for (i = 0; i < n; i += 2) {
     cvec[i / 2] = new Complex(vec[i], vec[i + 1]);
     }

     cfft(cvec, m, mode);

     for (i = 0; i < (m / 2); i++) {
     double hold = cvec[i].real;
     cvec[i].real = cvec[i + (m / 2)].real;
     cvec[i + (m / 2)].real = hold;
     hold = cvec[i].imaginary;
     cvec[i].imaginary = cvec[i + (m / 2)].imaginary;
     cvec[i + (m / 2)].imaginary = hold;
     }

     angle = (2.0 * Math.PI) / n;
     c = cos_angle = Math.cos(angle);
     s = sin_angle = Math.sin(angle);

     for (i = 1; i < (m / 2); i++) {
     d1r = 0.5 * (cvec[i].real + cvec[m - i].real);
     d1i = 0.5 * (cvec[i].imaginary - cvec[m - i].imaginary);
     d2r = 0.5 * (cvec[i].imaginary + cvec[m - i].imaginary);
     d2i = -0.5 * (cvec[i].real - cvec[m - i].real);

     d = (c * d2r) - (s * d2i);
     cvec[i].real = d1r + d;
     cvec[m - i].real = d1r - d;

     d = (c * d2i) + (s * d2r);
     cvec[i].imaginary = d1i + d;
     cvec[m - i].imaginary = -d1i + d;

     t = (cos_angle * c) - (sin_angle * s);
     s = (sin_angle * c) + (cos_angle * s);
     c = t;
     }

     d = cvec[0].real;
     cvec[0].real = d + cvec[0].imaginary;
     cvec[0].imaginary = d - cvec[0].imaginary;
     }
     * */
}
