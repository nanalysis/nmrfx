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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * Asmooth.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Asmooth extends Operation {

    private final double[] w;
    private final double[] y;
    private final double[] z;
    private final double[] a;
    private final double lambda;
    private final int m;
    private final int n;

    @Override
    public Asmooth eval(Vec vector) throws ProcessingException {
        Asmooth(vector);
        return this;
    }

    public Asmooth(double[] w, double[] y, double[] z, double[] a, double lambda, int m, int n) {
        this.w = w;
        this.y = y;
        this.z = z;
        this.a = a;
        this.lambda = lambda;
        this.m = m;
        this.n = n;
    }

    /* Program 3. Smoothing and interpolation with any difference equation. */
    /* Contribution to Graphic Gems IV */
 /* Paul H. C. Eilers, DCMR Milieudienst Rijnmond, 's-Gravelandseweg 565,
     3119 XT Schiedam, The Netherlands, E-Mail: paul@dcmr.nl */
    private void Asmooth(Vec vector) throws ProcessingException {
        /* Smoothing and interpolation with any difference equation of order <=5.
         Input:  weights (w), data (y): vector from 1 to m.
         Input:  smoothing parameter (lambda), length (m).
         Input:  coefficients (a) and order of difference equation (n).
         Output: smoothed vector (z): vector from 1 to m. */
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
}
