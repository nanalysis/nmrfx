/* Translated from the original pzero.f file of Dario Bini, the README
 * of which contains the following notice
 ***************************************************************************
 * All the software  contained in this library  is protected by copyright. *
 * Permission  to use, copy, modify, and  distribute this software for any *
 * purpose without fee is hereby granted, provided that this entire notice *
 * is included  in all copies  of any software which is or includes a copy *
 * or modification  of this software  and in all copies  of the supporting *
 * documentation for such software.                                        *
 ***************************************************************************
 * THIS SOFTWARE IS BEING PROVIDED "AS IS", WITHOUT ANY EXPRESS OR IMPLIED *
 * WARRANTY. IN NO EVENT, NEITHER  THE AUTHORS, NOR THE PUBLISHER, NOR ANY *
 * MEMBER  OF THE EDITORIAL BOARD OF  THE JOURNAL  "NUMERICAL ALGORITHMS", *
 * NOR ITS EDITOR-IN-CHIEF, BE  LIABLE FOR ANY ERROR  IN THE SOFTWARE, ANY *
 * MISUSE  OF IT  OR ANY DAMAGE ARISING OUT OF ITS USE. THE ENTIRE RISK OF *
 * USING THE SOFTWARE LIES WITH THE PARTY DOING SO.                        *
 ***************************************************************************
 * ANY USE  OF THE SOFTWARE  CONSTITUTES  ACCEPTANCE  OF THE TERMS  OF THE *
 * ABOVE STATEMENT.                                                        *
 ***************************************************************************
 */
package org.nmrfx.processor.math;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author brucejohnson
 */
public class Polynomial {
    private static final Logger log = LoggerFactory.getLogger(Polynomial.class);

    private static final double ROOT_TOL = 1.0e-6;

    double eps = 1.1102230246252e-16;
    double big = Double.MAX_VALUE;
    double small = Double.MIN_VALUE;
    int nitmax = 500;
    public Complex[] root = null;
    double[] radius = null;
    double[] apoly = null;
    double[] apolyr = null;
    boolean[] err = null;

    class NewtonResult {

        double radius = 0.0;
        boolean again = false;
        Complex corr = null;
    }

    static class ComplexComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            int result = 0;
            if (o1 == null) {
                if (o2 != null) {
                    result = 1;
                }
            } else if (o2 == null) {
                result = -1;
            } else if (o1.equals(o2)) {
                result = 1;
            } else {
                Complex c1 = (Complex) o1;
                Complex c2 = (Complex) o2;
                if (c1.getReal() > c2.getReal()) {
                    result = -1;
                } else if (c1.getReal() < c2.getReal()) {
                    result = 1;
                } else if (c1.getImaginary() > c2.getImaginary()) {
                    result = -1;
                } else {
                    result = 1;
                }
            }

            return result;
        }

        public boolean equals(Object o1, Object o2) {
            return compare(o1, o2) == 0;
        }
    }

    /**
     * Creates a new instance of Polynomial object
     *
     * @param n the order of the polynomial
     */
    public Polynomial(int n) {
        root = new Complex[n - 1];
        radius = new double[n];
        apoly = new double[n + 1];
        apolyr = new double[n + 1];
        err = new boolean[n + 1];
    }

    void polzeros(int n, Complex[] poly) {

        // check consistency of data
        if (poly[n].abs() == 0.0) {

            throw new IllegalArgumentException("inconsistent data: the leading coefficient is zero");
        }
        if (poly[0].abs() == 0.0) {
            throw new IllegalArgumentException("the constant term is zero: deflate the polynomial");
        }
        // compute the moduli of the coefficients
        double amax = 0.0;
        for (int i = 0; i <= n; i++) {
            apoly[i] = poly[i].abs();
            amax = Math.max(amax, apoly[i]);
            apolyr[i] = apoly[i];
        }
        if ((amax) >= (big / (n + 1))) {
            log.warn("warning: coefficients too big, overflow is likely");
        }
        // initialize
        for (int i = 0; i < n; i++) {
            radius[i] = 0;
            err[i] = true;
        }
        // select the starting points
        start(n, apolyr, root, radius, 0, small, big, err);
        // compute the coefficients of the backward-error polynomial
        for (int i = 0; i <= n; i++) {
            apolyr[n - i] = eps * apoly[i] * (3.8 * (n - i) + 1);
            apoly[i] = eps * apoly[i] * (3.8 * i + 1);
        }
        if ((apoly[0] == 0) || (apoly[n] == 0)) {
            log.warn("warning: the computation of some inclusion radius may fail. this is reported by radius=0");
        }
        for (int i = 0; i < n; i++) {
            err[i] = true;
            if (radius[i] == -1) {
                err[i] = false;
            }
        }
        int nzeros = 0;
        // starts aberth's iterations
        for (int iter = 0; iter < nitmax; iter++) {
            for (int i = 0; i < n; i++) {
                if (err[i]) {
                    NewtonResult newtonResult = newton(n, poly, apoly, apolyr, root[i], small, radius[i]);
                    if (newtonResult.again) {
                        Complex abcorr = aberth(n, i, root);
                        Complex one = new Complex(1.0, 0.0);
                        root[i] = root[i].subtract(newtonResult.corr.divide(one.subtract(newtonResult.corr.multiply(abcorr))));
                    } else {
                        err[i] = false;
                        radius[i] = newtonResult.radius;
                        nzeros = nzeros + 1;
                        if (nzeros == n) {
                            return;
                        }
                    }
                }
            }
        }
    }

    //************************************************************************
    //                             subroutine start                          *
    //************************************************************************
    // compute  the starting approximations of the roots                     *
    //************************************************************************
    // input variables:                                                      *
    //     n     :  number of the coefficients of the polynomial             *
    //     a     :  moduli of the coefficients of the polynomial             *
    //     small : the min positive double, small=2**(-1074) for the ieee.   *
    //     big   : the max double, big=2**1023 for the ieee standard.        *
    // output variables:                                                     *
    //     y     :  starting approximations                                  *
    //     radius:  if a component is -1 then the corresponding root has a   *
    //              too big or too small modulus in order to be represented  *
    //              as double float with no overflow/underflow               *
    //     nz    :  number of roots which cannot be represented without      *
    //              overflow/underflow                                       *
    // auxiliary variables:                                                  *
    //     h     :  needed for the computation of the convex hull            *
    //************************************************************************
    // this routines selects starting approximations along circles center at *
    // 0 and having suitable radii. the computation of the number of circles *
    // and of the corresponding radii is performed by computing the upper    *
    // convex hull of the set (i,log(a(i))), i=1,...,n+1.                    *
    //************************************************************************
    void start(int n, double[] a, Complex[] y, double[] radius, int nz, double small, double big, boolean[] h) {
        // integer n,nz,i,iold,nzeros,j,jj
        // logical h(n+1)
        // Complex y(n)
        // double a(n+1),radius(n)
        // double r,th,ang,temp,sigma,pi2
        // double small,big,xsmall,xbig
        // parameter(pi2=6.2831853071796,sigma=0.7)
        double sigma = 0.7;
        double xsmall = Math.log(small);
        double xbig = Math.log(big);
        // nz=0
        // compute the logarithm a(i) of the moduli of the coefficients of
        // the polynomial and then the upper covex hull of the set (a(i),i)
        for (int i = 0; i <= n; i++) {
            if (a[i] != 0.0) {
                a[i] = Math.log(a[i]);
            } else {
                a[i] = -1.0e30;
            }
        }
        for (int i = 0; i < n; i++) {
            y[i] = new Complex(0, 0);
        }

        cnvex(n + 1, a, h);
        // given the upper convex hull of the set (a(i),i) compute the moduli
        // of the starting approximations by means of rouche's theorem
        int iold = 0;
        String warningMessage = "warning: {} zero(s) are too small too represent their inverses as Complex, they are replaced by small numbers, the corresponding radii are set to -1";
        double th = 2.0 * Math.PI / n;
        double r = 0.0;
        for (int i = 1; i <= n; i++) {
            if (h[i]) {
                int nzeros = i - iold;
                double temp = (a[iold] - a[i]) / nzeros;
                // check if the modulus is too small
                if ((temp < -xbig) && (temp >= xsmall)) {
                    log.warn(warningMessage, nzeros);
                    nz = nz + nzeros;
                    r = 1.0 / big;
                }
                if (temp < xsmall) {
                    nz = nz + nzeros;
                    log.warn(warningMessage, nzeros);
                }
                // check if the modulus is too big
                if (temp > xbig) {
                    r = big;
                    nz = nz + nzeros;
                    log.warn(warningMessage, nzeros);
                }
                if ((temp <= xbig) && (temp > Math.max(-xbig, xsmall))) {
                    r = Math.exp(temp);
                }
                // compute nzeros approximations equally distributed in the disk of
                // radius r
                double ang = 2.0 * Math.PI / nzeros;
                for (int j = iold; j <= (i - 1); j++) {
                    int jj = j - iold + 1;
                    if ((r <= (1.0 / big)) || (r == big)) {
                        radius[j] = -1;
                    }
                    double yjReal = r * Math.cos(ang * jj + th * (i + 1) + sigma);
                    double yjImag = r * Math.sin(ang * jj + th * (i + 1) + sigma);
                    y[j] = new Complex(yjReal, yjImag);
                }
                iold = i;
            }
        }
    }

    //************************************************************************
    //                             subroutine newton                         *
    //************************************************************************
    // compute  the newton's correction, the inclusion radius (4) and checks *
    // the stop condition (3)                                                *
    //************************************************************************
    // input variables:                                                      *
    //     n     : degree of the polynomial p(x)                             *
    //     poly  : coefficients of the polynomial p(x)                       *
    //     apoly : upper bounds on the backward perturbations on the         *
    //             coefficients of p(x) when applying ruffini-horner's rule  *
    //     apolyr: upper bounds on the backward perturbations on the         *
    //             coefficients of p(x) when applying (2), (2')              *
    //     z     : value at which the newton correction is computed          *
    //     small : the min positive double, small=2**(-1074) for the ieee.   *
    //************************************************************************
    // output variables:                                                     *
    //     radius: upper bound to the distance of z from the closest root of *
    //             the polynomial computed according to (4).                 *
    //     corr  : newton's correction                                       *
    //     again : this variable is .true. if the computed value p(z) is     *
    //             reliable, i.e., (3) is not satisfied in z. again is       *
    //             .false., otherwise.                                       *
    //************************************************************************
    NewtonResult newton(int n, Complex[] poly, double[] apoly, double[] apolyr, Complex z, double small, double radius) {
        //integer n,i
        //Complex poly(n+1)
        //Complex z,p,p1,corr,zi,den,ppsp
        //double radius,apoly(n+1),apolyr(n+1)
        //double ap,az,azi,absp
        //double small
        //logical again
        final Complex corr;
        double az = z.abs();
        // if |z|<=1 then apply ruffini-horner's rule for p(z)/p'(z)
        // and for the computation of the inclusion radius
        boolean again;
        if (az <= 1) {
            Complex p = new Complex(poly[n].getReal(), poly[n].getImaginary());
            Complex p1 = new Complex(p.getReal(), p.getImaginary());
            double ap = apoly[n];
            for (int i = n - 1; i >= 1; i--) {
                p = p.multiply(z).add(poly[i]);
                p1 = p1.multiply(z).add(p);
                ap = ap * az + apoly[i];
            }
            p = p.multiply(z);
            p = p.add(poly[0]);
            ap = ap * az + apoly[0];
            corr = p.divide(p1);
            double absp = p.abs();

            again = (absp > (small + ap));

            if (!again) {
                radius = n * (absp + ap) / p1.abs();
            }
        } else {
            // if |z|>1 then apply ruffini-horner's rule to the reversed polynomial
            // and use formula (2) for p(z)/p'(z). analogously do for the inclusion
            // radius.
            Complex zi = Complex.ONE.divide(z);

            double azi = 1 / az;
            Complex p = new Complex(poly[0].getReal(), poly[0].getImaginary());
            Complex p1 = new Complex(p.getReal(), p.getImaginary());
            double ap = apolyr[n];

            for (int i = n - 1; i >= 1; i--) {
                p = p.multiply(zi).add(poly[n - i]);
                p1 = p1.multiply(zi).add(p);
                ap = ap * azi + apolyr[i];
            }
            p = p.multiply(zi).add(poly[n]);

            ap = ap * azi + apolyr[0];
            double absp = p.abs();
            again = (absp > (small + ap));
            Complex ppsp = p.multiply(z).divide(p1);

            Complex den = new Complex(ppsp.getReal() * n - 1.0, ppsp.getImaginary() * n);
            corr = z.multiply(ppsp.divide(den));

            if (!again) {
                radius = ppsp.abs() + (ap * az) / p1.abs();
                radius = n * radius / den.abs();
                radius = radius * az;
            }
        }
        NewtonResult result = new NewtonResult();
        result.again = again;
        result.radius = radius;
        result.corr = corr;
        return result;
    }

    //************************************************************************
    //                             subroutine aberth                         *
    //************************************************************************
    // compute  the aberth correction. to save time, the reciprocation of    *
    // root(j)-root(i) could be performed in single precision (complex*8)    *
    // in principle this might cause overflow if both root(j) and root(i)    *
    // have too small moduli.                                                *
    //************************************************************************
    // input variables:                                                      *
    //     n     : degree of the polynomial                                  *
    //     root  : vector containing the current approximations to the roots *
    //     j     : index of the component of root with respect to which the  *
    //             aberth correction is computed                             *
    //************************************************************************
    // output variable:                                                      *
    //     abcorr: aberth's correction (compare (1))                         *
    //************************************************************************
    Complex aberth(int n, int j, Complex[] root) {

        // the next variable z could be defined as complex*8 to speed up the
        // computation, this slightly reduces the robustness of the program
        Complex abcorr = Complex.ZERO;
        Complex zj = root[j];
        for (int i = 0; i < j; i++) {
            Complex z = zj.subtract(root[i]);
            abcorr = abcorr.add(Complex.ONE.divide(z));
        }
        for (int i = j + 1; i < n; i++) {
            Complex z = zj.subtract(root[i]);
            abcorr = abcorr.add(Complex.ONE.divide(z));
        }
        return abcorr;
    }

    //************************************************************************
    //                             subroutine cnvex                          *
    //************************************************************************
    // compute  the upper convex hull of the set (i,a(i)), i.e., the set of  *
    // vertices (i_k,a(i_k)), k=1,2,...,m, such that the points (i,a(i)) lie *
    // below the straight lines passing through two consecutive vertices.    *
    // the abscissae of the vertices of the convex hull equal the indices of *
    // the true  components of the logical output vector h.                  *
    // the used method requires o(nlog n) comparisons and is based on a      *
    // divide-and-conquer technique. once the upper convex hull of two       *
    // contiguous sets  (say, {(1,a(1)),(2,a(2)),...,(k,a(k))} and           *
    // {(k,a(k)), (k+1,a(k+1)),...,(q,a(q))}) have been computed, then       *
    // the upper convex hull of their union is provided by the subroutine    *
    // cmerge. the program starts with sets made up by two consecutive       *
    // points, which trivially constitute a convex hull, then obtains sets   *
    // of 3,5,9... points,  up to  arrive at the entire set.                 *
    // the program uses the subroutine  cmerge; the subroutine cmerge uses   *
    // the subroutines left, right and ctest. the latter tests the convexity *
    // of the angle formed by the points (i,a(i)), (j,a(j)), (k,a(k)) in the *
    // vertex (j,a(j)) up to within a given tolerance toler, where i<j<k.    *
    //************************************************************************
    void cnvex(int n, double[] a, boolean[] h) {
        //integer n,i,j,k,m,nj,jc
        //logical h(n)
        //double a(n)
        for (int i = 0; i < n; i++) {
            h[i] = true;
        }
        // compute k such that n-2<=2**k<n-1
        int k = (int) (Math.log(n - 2.0) / Math.log(2.0));
        if (Math.pow(2, k + 1) <= (n - 2)) {
            k = k + 1;
        }
        // for each m=1,2,4,8,...,2**k, consider the nj pairs of consecutive
        // sets made up by m+1 points having the common vertex
        // (jc,a(jc)), where jc=m*(2*j+1)+1 and j=0,...,nj,
        // nj=max(0,int((n-2-m)/(m+m))).
        // compute the upper convex hull of their union by means of the
        // subroutine cmerge
        int m = 1;
        for (int i = 0; i <= k; i++) {
            int nj = Math.max(0, (n - 2 - m) / (m + m));
            for (int j = 0; j <= nj; j++) {
                int jc = (j + j + 1) * m;
                cmerge(n, a, jc, m, h);
            }
            m = m + m;
        }
    }

    //*****************************************
    //************************************************************************
    //                             subroutine left                           *
    //************************************************************************
    // given as input the integer i and the vector h of logical, compute the *
    // the maximum integer il such that il<i and h(il) is true.              *
    //************************************************************************
    // input variables:                                                      *
    //     n   : length of the vector h                                      *
    //     h   : vector of logical                                           *
    //     i   : integer                                                     *
    //************************************************************************
    // output variable:                                                      *
    //     il  : maximum integer such that il<i, h(il)=.true.                *
    //************************************************************************
    int left(boolean[] h, int i) {
        for (int i1 = i - 1; i1 >= 0; i1--) {
            if (h[i1]) {
                return i1;
            }
        }
        return 0;
    }

    //************************************************************************
    //                             subroutine right                          *
    //************************************************************************
    //************************************************************************
    // given as input the integer i and the vector h of logical, compute the *
    // the minimum integer ir such that ir>i and h(il) is true.              *
    //************************************************************************
    //************************************************************************
    // input variables:                                                      *
    //     n   : length of the vector h                                      */
    //     h   : vector of logical                                           *
    //     i   : integer                                                     *
    //************************************************************************
    // output variable:                                                      *
    //     ir  : minimum integer such that ir>i, h(ir)=.true.                *
    //************************************************************************
    int right(boolean[] h, int i) {
        int n = h.length;
        for (int ir = i + 1; ir < n; ir++) {
            if (h[ir]) {
                return ir;
            }
        }
        return n - 1;
    }

    //************************************************************************
    //                             subroutine cmerge                         *
    //************************************************************************
    // given the upper convex hulls of two consecutive sets of pairs         *
    // (j,a(j)), compute the upper convex hull of their union                *
    //************************************************************************
    // input variables:                                                      *
    //     n    : length of the vector a                                     *
    //     a    : vector defining the points (j,a(j))                        *
    //     i    : abscissa of the common vertex of the two sets              *
    //     m    : the number of elements of each set is m+1                  *
    //************************************************************************
    // input/output variable:                                                *
    //     h    : vector defining the vertices of the convex hull, i.e.,     *
    //            h(j) is .true. if (j,a(j)) is a vertex of the convex hull  *
    //            this vector is used also as output.                        *
    //************************************************************************
    void cmerge(int n, double[] a, int i, int m, boolean[] h) {
        // integer n,m,i,ir,il,irr,ill
        // logical h(n)
        //  logical tstl,tstr,ctest
        //  double a(n)
        // at the left and the right of the common vertex (i,a(i)) determine
        // the abscissae il,ir, of the closest vertices of the upper convex
        // hull of the left and right sets, respectively
        int il = left(h, i);
        int ir = right(h, i);
        // check the convexity of the angle formed by il,i,ir
        if (ctest(n, a, il, i, ir)) {
        } else {
            // continue the search of a pair of vertices in the left and right
            // sets which yield the upper convex hull
            h[i] = false;
            while (true) {
                boolean tstl;
                boolean tstr;
                int ill = 0;
                int irr = 0;
                if (il == (i - m)) {
                    tstl = true;
                } else {
                    ill = left(h, il);
                    tstl = ctest(n, a, ill, il, ir);
                }
                if (ir == Math.min(n - 1, i + m)) {
                    tstr = true;
                } else {
                    irr = right(h, ir);

                    tstr = ctest(n, a, il, ir, irr);

                }

                h[il] = tstl;
                h[ir] = tstr;
                if (tstl && tstr) {
                    return;
                }
                if (!tstl) {
                    il = ill;
                }
                if (!tstr) {
                    ir = irr;
                    //goto 10
                }
            }
        }
    }

    //************************************************************************
    //                             function ctest                            *
    //************************************************************************
    // test the convexity of the angle formed by (il,a(il)), (i,a(i)),       *
    // (ir,a(ir)) at the vertex (i,a(i)), up to within the tolerance         *
    // toler. if convexity holds then the function is set to .true.,         *
    // otherwise ctest=.false. the parameter toler is set to 0.4 by default. *
    //************************************************************************
    // input variables:                                                      *
    //     n       : length of the vector a                                  *
    //     a       : vector of double                                        *
    //     il,i,ir : integers such that il<i<ir                              *
    //************************************************************************
    // output:                                                               *
    //     .true. if the angle formed by (il,a(il)), (i,a(i)), (ir,a(ir)) at *
    //            the vertex (i,a(i)), is convex up to within the tolerance  *
    //            toler, i.e., if                                            *
    //            (a(i)-a(il))*(ir-i)-(a(ir)-a(i))*(i-il)>toler.             *
    //     .false.,  otherwise.                                              *
    //************************************************************************
    boolean ctest(int n, double[] a, int il, int i, int ir) {

        double s1;
        double s2;
        double toler = 0.4;

        s1 = a[i] - a[il];
        s2 = a[ir] - a[i];
        s1 = s1 * (ir - i);
        s2 = s2 * (i - il);
        boolean result = false;

        if (s1 > (s2 + toler)) {
            result = true;
        }
        return result;
    }

    public void reflectRoots() {
        int n = root.length;
        for (int i = 0; i < n; i++) {
            double rR = root[i].getReal();
            double rI = root[i].getImaginary();
            double rSqr = rR * rR + rI * rI;
            if (rSqr > 1.0) {
                rSqr = 1.0 / rSqr;
                rR = rR * rSqr;
                rI = rI * rSqr;
                root[i] = new Complex(rR, rI);
            }
        }
    }

    // replace polzeros(ocoefB.length - 1, ocoefB) with svejgardRoot(ocoefB.length, ocoefB)
    // Svejgaard's specc() routine, translated from Algol
    //   root a polynomial  a[0]z**n + a[1]z**(n-1) + ... + a[n]
    //   input coeffs ar[0:n] ai[0:n], output roots ar[1:n] ai[1:n]
    public void svejgardRoot(int nn, Complex[] poly) {
        int i, j;
        double u, v, w, f, fm = 0.0, fc = 0.0, xm = 0.0, ym = 0.0,
                xr, yr, xc = 0.0, yc = 0.0, dx = 0.0, dy = 0.0, us, vs, d;
        boolean gotoIter = false;
        int n = nn + 1;

        // n is usually poly.length
        double[] ar = new double[n];
        double[] ai = new double[n];
        for (i = 0; i < n; i++) {  // input data in reverse order
            ar[i] = poly[n - i - 1].getReal();
            ai[i] = poly[n - i - 1].getImaginary();
        }
        // calculate
        us = ar[0];
        vs = ai[0];  // us, vs, d never change
        d = Math.abs(us) + Math.abs(vs);
        int maxIterations = n * 200;
        n = n - 1;  // start loop at ar[n-1] ai[n-1]
        int nIterations = 0;
        while (n > 0) {  // red label
            nIterations++;
            if (gotoIter) {
                gotoIter = false;
            } else {
                fm = Math.abs(ar[n]) + Math.abs(ai[n]);
                fc = fm;
                if (fm == 0.0) {
                    n = n - 1;
                    continue;  // go to red
                }
                xc = 0.0;
                yc = 0.0;
                dy = 0.0;
                dx = Math.pow(fm / d, 1.0 / n);
            }
            for (i = 1; i <= 4; i++) {  // iter label
                u = -dy;
                dy = dx;
                dx = u;
                xr = xc + dx;
                yr = yc + dy;
                u = us;
                v = vs;
                for (j = 1; j <= n; j++) {  // sum of complex multiply
                    w = ar[j] + u * xr - v * yr;
                    v = ai[j] + u * yr + v * xr;
                    u = w;
                }
                f = Math.abs(u) + Math.abs(v);
                if (f < fm) {  // set fm to f minimum
                    xm = xr;
                    ym = yr;
                    fm = f;
                }
            }
            if (fm < fc) {  // expand dx dy, set xc yc fc
                dx = 1.5 * dx;
                dy = 1.5 * dy;
                xc = xm;
                yc = ym;
                fc = fm;
            } else {        // rotate cross, dx dy
                u = 0.4 * dx - 0.3 * dy;
                dy = 0.4 * dy + 0.3 * dx;
                dx = u;
            }
            u = Math.abs(xc) + Math.abs(yc);
            double checkValue = u + Math.abs(dx) + Math.abs(dy);
            if (!Precision.equals(u, checkValue, ROOT_TOL) && !Precision.equals(fc, 0.0, ROOT_TOL)) {
                gotoIter = true;
                continue;  // go to iter
            }
            u = us;
            v = vs;
            ar[n] = xc;
            ai[n] = yc;
            // each n repeats about 120 times in while loop
            n = n - 1;
            for (j = 1; j <= n; j++) {  // partial sum, complex multiply
                ar[j] = ar[j] + u * xc - v * yc;
                w = ar[j];
                ai[j] = ai[j] + u * yc + v * xc;
                v = ai[j];
                u = w;
            }
            // go to red
        } // end while(n > 0)
        // set results into root
        for (i = 0; i < root.length; i++) {
            root[i] = new Complex(ar[i + 1], ai[i + 1]);
        }
    }

    public Complex[] makeCoeffs() {
        int n = root.length;
        int j;
        Complex z;
        Complex[] polyCoef = new Complex[n];
        polyCoef[0] = new Complex(root[0].getReal(), root[0].getImaginary());
        for (int i = 1; i < n; i++) {
            polyCoef[i] = polyCoef[i - 1].add(root[i]);
            for (j = i - 1; j > 0; j--) {
                z = polyCoef[j].multiply(root[i]);
                polyCoef[j] = polyCoef[j - 1].subtract(z);
            }
            z = root[i].multiply(polyCoef[0]);
            polyCoef[0] = new Complex(-z.getReal(), -z.getImaginary());
        }

        for (int i = 0; i < n; i++) {
            polyCoef[i] = new Complex(-polyCoef[i].getReal(), -polyCoef[i].getImaginary());
        }

        return polyCoef;
    }

    public static void testSpecRoots() {
        double[] rvals = {1, -1, 1, 1};
        double[] ivals = {0, 1, 0, 0};

        log.info("specRoots");

        Complex[] polyVals = new Complex[rvals.length];
        for (int i = 0; i < rvals.length; i++) {
            polyVals[i] = new Complex(rvals[i], ivals[i]);
        }
        Polynomial poly = new Polynomial(polyVals.length);
        log.info("  rvals length={} polyVals length={}", rvals.length, polyVals.length);

        poly.svejgardRoot(polyVals.length, polyVals);

        ComplexComparator cCompare = new ComplexComparator();
        Arrays.sort(poly.root, cCompare);  // is sort needed?
        for (int i = 0; i < poly.root.length; i++) {
            log.info("  {} root {} i {}", i, poly.root[i].getReal(), poly.root[i].getImaginary());
        }
        Complex[] coef2 = poly.makeCoeffs();
        for (int i = 0; i < poly.root.length; i++) {
            log.info("  {} coef {} i {}", i, coef2[i].getReal(), coef2[i].getImaginary());
        }

    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        double[] rvals = {1, -1, 1, 1};
        double[] ivals = {0, 1, 0, 0};

        Complex[] polyVals = new Complex[rvals.length];
        for (int i = 0; i < rvals.length; i++) {
            polyVals[i] = new Complex(rvals[i], ivals[i]);
        }
        Polynomial poly = new Polynomial(polyVals.length);

        poly.polzeros(polyVals.length - 1, polyVals);
        System.out.println("initial algorithm");
        ComplexComparator cCompare = new ComplexComparator();
        Arrays.sort(poly.root, cCompare);
        for (int i = 0; i < poly.root.length; i++) {
            System.out.println("  " + i + " root " + poly.root[i].getReal() + " i " + poly.root[i].getImaginary());
        }
        Complex[] coef2 = poly.makeCoeffs();
        for (int i = 0; i < poly.root.length; i++) {
            System.out.println("  " + i + " coef " + coef2[i].getReal() + " i " + coef2[i].getImaginary());
        }
        System.out.println("");

        testSpecRoots();
    }
}
