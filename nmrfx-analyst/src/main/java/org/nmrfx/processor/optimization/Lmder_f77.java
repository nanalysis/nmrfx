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

import org.apache.commons.math3.linear.*;

public class Lmder_f77 {
    // epsmch is the machine precision

    static final double epsmch = 2.22044604926e-16;
    static final double zero = 0.0;
    int nfev = 0;
    double[] xv = null;
    double[] yv = null;
    double[] a = null;
    int[] map = null;
    int nFit = 0;
    double[] aCalc = null;
    double xMin = 0.0;
    double yMin = 0.0;
    double xMax = 0.0;
    double yMax = 0.0;
    double xMid = 0.0;
    java.util.Random generator = null;
    Lmder_fcn lmderFunc = null;
    Lmder_fcn[] lmderFuncs = {
            new fexpc_f77(), new fexpc1_f77(), new fexpb_f77(), new fexpg_f77(),
            new flogistic_f77(), new fexp2_f77(), new funcgf_f77(), new fexpd_f77(),
            new flineshape_f77(), new flineshapeW_f77(), new flineshapeLorentz_f77(),
            new flineshapeLorentzIW_f77()
    };

    public void setFunc(int funcNum) {
        switch (funcNum) {
            case 0:
                lmderFunc = new fexpc_f77();

                break;

            case 1:
                lmderFunc = new fexpc1_f77();

                break;

            case 2:
                lmderFunc = new fexpb_f77();

                break;

            case 3:
                lmderFunc = new fexpg_f77();

                break;

            case 4:
                lmderFunc = new flogistic_f77();

                break;

            case 5:
                lmderFunc = new fexp2_f77();

                break;

            case 6:
                lmderFunc = new funcgf_f77();

                break;

            case 7:
                lmderFunc = new fexpd_f77();

                break;

            case 8:
                lmderFunc = new flineshape_f77();

                break;

            case 9:
                lmderFunc = new flineshapeW_f77();

                break;

            case 10:
                lmderFunc = new flineshapeLorentz_f77();

                break;

            case 11:
                lmderFunc = new flineshapeLorentzIW_f77();

                break;
            default:
                throw new IllegalArgumentException("Invalid function type");
        }
    }

    public String[] getEquations() {
        String[] equations = new String[lmderFuncs.length];

        for (int i = 0; i < lmderFuncs.length; i++) {
            equations[i] = lmderFuncs[i].getEquation();
        }

        return equations;
    }

    public double[] guess() {
        initpt();

        if (lmderFunc == null) {
            return null;
        } else {
            return lmderFunc.guess();
        }
    }

    public int getN() {
        if (lmderFunc == null) {
            return 0;
        } else {
            return lmderFunc.getN();
        }
    }

    public void setN(int newN) {
        if (lmderFunc != null) {
            lmderFunc.setN(newN);
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
        if (map != null) {
            return aCalc;
        } else {
            return a;
        }
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
        nFit = 0;
        a = new double[lmderFunc.getN() + 1];
        lmderFunc.initpt(a);
    }

    public String initpt(double[] guess) {
        nFit = 0;
        a = new double[lmderFunc.getN() + 1];

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
        nFit = 0;

        int n = lmderFunc.getN() + 1;

        a = new double[n];
        aCalc = new double[n];
        map = new int[n];

        if (a.length != (guess.length + 1)) {
            return "number of guesses not equal to number of pars";
        } else {
            for (int i = 1; i < a.length; i++) {
                a[i] = guess[i - 1];
                aCalc[i] = guess[i - 1];
                map[i] = i;
            }

            return null;
        }
    }

    public String setMap(int[] newMap) {
        map = new int[newMap.length + 1];
        this.nFit = newMap.length;

        for (int i = 1; i < map.length; i++) {
            map[i] = newMap[i - 1] + 1;
            a[i] = aCalc[map[i]];
        }

        return null;
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

        int n = lmderFunc.getN();

        if (a.length != (n + 1)) {
            initpt();
        }

        for (int i = 0; i < xv.length; i++) {
            yv[i] = lmderFunc.calculate(a, xv[i])
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
            yvec[i] = lmderFunc.calculate(a, xvec[i]);
        }

        return null;
    }

    public double rms() {
        double[] fvec = new double[xv.length + 1];
        int m = yv.length;
        int n = lmderFunc.getN();
        int[] iflag = {0, 1};
        lmderFunc.fcn(m, n, a, fvec, null, iflag);

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
        int i;
        int ic;
        int k;
        int m;
        int n;
        int ntries;

        int[] info = new int[2];

        int[] ma = new int[61];
        int[] na = new int[61];
        int[] nf = new int[61];

        double factor;
        double fnorm2;

        double[] fnm = new double[61];
        double[] fvec = new double[xv.length + 1];

        double finalTol = Math.sqrt(epsmch);
        double tol = finalTol * 1.0e4;
        ic = 0;
        m = yv.length;
        n = lmderFunc.getN();

        int[] ipvt = new int[n + 1];
        ntries = 3;

        factor = 1.0;

        int[] iflag = {0, 1};
        double[][] fjac = new double[m + 1][n + 1];

        for (k = 1; k <= ntries; k++) {
            ic++;
            lmderFunc.fcn(m, n, a, fvec, null, iflag);

            nfev = 0;
            lmder1_f77(lmderFunc, m, n, a, fvec, fjac, tol, info, ipvt);
            lmderFunc.fcn(m, n, a, fvec, null, iflag);
            fnorm2 = Minpack_f77.enorm_f77(m, fvec);

            if (map != null) {
                for (i = 1; i < map.length; i++) {
                    aCalc[map[i]] = a[i];
                }
            }

            if (lmderFunc instanceof flineshapeLorentzIW_f77) {
                double[] amps = ((flineshapeLorentzIW_f77) lmderFunc).fitSignalAmplitudesNN(aCalc);
                int iAmp = 0;

                for (int j = 2; j < aCalc.length; j += 2) {
                    aCalc[j] = amps[iAmp];
                    iAmp++;
                }
            }

            na[ic] = n;
            ma[ic] = m;
            nf[ic] = nfev;
            na[ic] = info[1];

            fnm[ic] = fnorm2;

            // Note: variable meanings are Initial L2 norm of the residuals: fnorm1
            // Final L2 norm of the residuals: fnorm2
            // Number of function evaluations: nf[ic]
            // Number of Jacobian evaluations: nj[ic]
            // tol /= Math.pow(1.0e4,1.0/ntries);
            // if ((ntries-k) <2) {
            //   tol = finalTol;
            //}
            factor *= 10.0;
        }
    }

    public void doLinearNN() {
        if (lmderFunc instanceof flineshapeLorentzIW_f77) {
            double[] amps = ((flineshapeLorentzIW_f77) lmderFunc).fitSignalAmplitudesNN(aCalc);
            int iAmp = 0;

            for (int j = 2; j < aCalc.length; j += 2) {
                aCalc[j] = amps[iAmp];
                iAmp++;
            }
        }
    }

    public static void lmder1_f77(Lmder_fcn nlls, int m, int n, double[] x,
                                  double[] fvec, double[][] fjac, double tol, int[] info, int[] ipvt) {
        /*

         Here is a copy of the lmder1 FORTRAN documentation:


         subroutine lmder2(fcn,m,n,x,fvec,fjac,ldfjac,tol,info,ipvt,wa,
         *                  lwa)

         integer m,n,ldfjac,info,lwa
         integer ipvt(n)
         double precision tol
         double precision x(n),fvec(m),fjac(ldfjac,n),wa(lwa)
         external fcn

         c     **********
         c
         c     subroutine lmder1
         c
         c     the purpose of lmder1 is to minimize the sum of the squares of
         c     m nonlinear functions in n variables by a modification of the
         c     levenberg-marquardt algorithm. this is done by using the more
         c     general least-squares solver lmder. the user must provide a
         c     subroutine which calculates the functions and the jacobian.
         c
         c     the subroutine statement is
         c
         c       subroutine lmder1(fcn,m,n,x,fvec,fjac,ldfjac,tol,info,
         c                         ipvt,wa,lwa)
         c
         c     where
         c
         c       fcn is the name of the user-supplied subroutine which
         c         calculates the functions and the jacobian. fcn must
         c         be declared in an external statement in the user
         c         calling program, and should be written as follows.
         c
         c         subroutine fcn(m,n,x,fvec,fjac,ldfjac,iflag)
         c         integer m,n,ldfjac,iflag
         c         double precision x(n),fvec(m),fjac(ldfjac,n)
         c         ----------
         c         if iflag = 1 calculate the functions at x and
         c         return this vector in fvec. do not alter fjac.
         c         if iflag = 2 calculate the jacobian at x and
         c         return this matrix in fjac. do not alter fvec.
         c         ----------
         c         return
         c         end
         c
         c         the value of iflag should not be changed by fcn unless
         c         the user wants to terminate execution of lmder1.
         c         in this case set iflag to a negative integer.
         c
         c       m is a positive integer input variable set to the number
         c         of functions.
         c
         c       n is a positive integer input variable set to the number
         c         of variables. n must not exceed m.
         c
         c       x is an array of length n. on input x must contain
         c         an initial estimate of the solution vector. on output x
         c         contains the final estimate of the solution vector.
         c
         c       fvec is an output array of length m which contains
         c         the functions evaluated at the output x.
         c
         c       fjac is an output m by n array. the upper n by n submatrix
         c         of fjac contains an upper triangular matrix r with
         c         diagonal elements of nonincreasing magnitude such that
         c
         c                t     t           t
         c               p *(jac *jac)*p = r *r,
         c
         c         where p is a permutation matrix and jac is the final
         c         calculated jacobian. column j of p is column ipvt(j)
         c         (see below) of the identity matrix. the lower trapezoidal
         c         part of fjac contains information generated during
         c         the computation of r.
         c
         c       ldfjac is a positive integer input variable not less than m
         c         which specifies the leading dimension of the array fjac.
         c
         c       tol is a nonnegative input variable. termination occurs
         c         when the algorithm estimates either that the relative
         c         error in the sum of squares is at most tol or that
         c         the relative error between x and the solution is at
         c         most tol.
         c
         c       info is an integer output variable. if the user has
         c         terminated execution, info is set to the (negative)
         c         value of iflag. see description of fcn. otherwise,
         c         info is set as follows.
         c
         c         info = 0  improper input parameters.
         c
         c         info = 1  algorithm estimates that the relative error
         c                   in the sum of squares is at most tol.
         c
         c         info = 2  algorithm estimates that the relative error
         c                   between x and the solution is at most tol.
         c
         c         info = 3  conditions for info = 1 and info = 2 both hold.
         c
         c         info = 4  fvec is orthogonal to the columns of the
         c                   jacobian to machine precision.
         c
         c         info = 5  number of calls to fcn with iflag = 1 has
         c                   reached 100*(n+1).
         c
         c         info = 6  tol is too small. no further reduction in
         c                   the sum of squares is possible.
         c
         c         info = 7  tol is too small. no further improvement in
         c                   the approximate solution x is possible.
         c
         c       ipvt is an integer output array of length n. ipvt
         c         defines a permutation matrix p such that jac*p = q*r,
         c         where jac is the final calculated jacobian, q is
         c         orthogonal (not stored), and r is upper triangular
         c         with diagonal elements of nonincreasing magnitude.
         c         column j of p is column ipvt(j) of the identity matrix.
         c
         c       wa is a work array of length lwa.
         c
         c       lwa is a positive integer input variable not less than 5*n+m.
         c
         c     subprograms called
         c
         c       user-supplied ...... fcn
         c
         c       minpack-supplied ... lmder
         c
         c     argonne national laboratory. minpack project. march 1980.
         c     burton s. garbow, kenneth e. hillstrom, jorge j. more
         c
         c     **********

         */
        int maxfev;

        /*

         Here is a copy of the lmder1 FORTRAN documentation:


         subroutine lmder2(fcn,m,n,x,fvec,fjac,ldfjac,tol,info,ipvt,wa,
         *                  lwa)

         integer m,n,ldfjac,info,lwa
         integer ipvt(n)
         double precision tol
         double precision x(n),fvec(m),fjac(ldfjac,n),wa(lwa)
         external fcn

         c     **********
         c
         c     subroutine lmder1
         c
         c     the purpose of lmder1 is to minimize the sum of the squares of
         c     m nonlinear functions in n variables by a modification of the
         c     levenberg-marquardt algorithm. this is done by using the more
         c     general least-squares solver lmder. the user must provide a
         c     subroutine which calculates the functions and the jacobian.
         c
         c     the subroutine statement is
         c
         c       subroutine lmder1(fcn,m,n,x,fvec,fjac,ldfjac,tol,info,
         c                         ipvt,wa,lwa)
         c
         c     where
         c
         c       fcn is the name of the user-supplied subroutine which
         c         calculates the functions and the jacobian. fcn must
         c         be declared in an external statement in the user
         c         calling program, and should be written as follows.
         c
         c         subroutine fcn(m,n,x,fvec,fjac,ldfjac,iflag)
         c         integer m,n,ldfjac,iflag
         c         double precision x(n),fvec(m),fjac(ldfjac,n)
         c         ----------
         c         if iflag = 1 calculate the functions at x and
         c         return this vector in fvec. do not alter fjac.
         c         if iflag = 2 calculate the jacobian at x and
         c         return this matrix in fjac. do not alter fvec.
         c         ----------
         c         return
         c         end
         c
         c         the value of iflag should not be changed by fcn unless
         c         the user wants to terminate execution of lmder1.
         c         in this case set iflag to a negative integer.
         c
         c       m is a positive integer input variable set to the number
         c         of functions.
         c
         c       n is a positive integer input variable set to the number
         c         of variables. n must not exceed m.
         c
         c       x is an array of length n. on input x must contain
         c         an initial estimate of the solution vector. on output x
         c         contains the final estimate of the solution vector.
         c
         c       fvec is an output array of length m which contains
         c         the functions evaluated at the output x.
         c
         c       fjac is an output m by n array. the upper n by n submatrix
         c         of fjac contains an upper triangular matrix r with
         c         diagonal elements of nonincreasing magnitude such that
         c
         c                t     t           t
         c               p *(jac *jac)*p = r *r,
         c
         c         where p is a permutation matrix and jac is the final
         c         calculated jacobian. column j of p is column ipvt(j)
         c         (see below) of the identity matrix. the lower trapezoidal
         c         part of fjac contains information generated during
         c         the computation of r.
         c
         c       ldfjac is a positive integer input variable not less than m
         c         which specifies the leading dimension of the array fjac.
         c
         c       tol is a nonnegative input variable. termination occurs
         c         when the algorithm estimates either that the relative
         c         error in the sum of squares is at most tol or that
         c         the relative error between x and the solution is at
         c         most tol.
         c
         c       info is an integer output variable. if the user has
         c         terminated execution, info is set to the (negative)
         c         value of iflag. see description of fcn. otherwise,
         c         info is set as follows.
         c
         c         info = 0  improper input parameters.
         c
         c         info = 1  algorithm estimates that the relative error
         c                   in the sum of squares is at most tol.
         c
         c         info = 2  algorithm estimates that the relative error
         c                   between x and the solution is at most tol.
         c
         c         info = 3  conditions for info = 1 and info = 2 both hold.
         c
         c         info = 4  fvec is orthogonal to the columns of the
         c                   jacobian to machine precision.
         c
         c         info = 5  number of calls to fcn with iflag = 1 has
         c                   reached 100*(n+1).
         c
         c         info = 6  tol is too small. no further reduction in
         c                   the sum of squares is possible.
         c
         c         info = 7  tol is too small. no further improvement in
         c                   the approximate solution x is possible.
         c
         c       ipvt is an integer output array of length n. ipvt
         c         defines a permutation matrix p such that jac*p = q*r,
         c         where jac is the final calculated jacobian, q is
         c         orthogonal (not stored), and r is upper triangular
         c         with diagonal elements of nonincreasing magnitude.
         c         column j of p is column ipvt(j) of the identity matrix.
         c
         c       wa is a work array of length lwa.
         c
         c       lwa is a positive integer input variable not less than 5*n+m.
         c
         c     subprograms called
         c
         c       user-supplied ...... fcn
         c
         c       minpack-supplied ... lmder
         c
         c     argonne national laboratory. minpack project. march 1980.
         c     burton s. garbow, kenneth e. hillstrom, jorge j. more
         c
         c     **********

         */
        int mode;

        /*

         Here is a copy of the lmder1 FORTRAN documentation:


         subroutine lmder2(fcn,m,n,x,fvec,fjac,ldfjac,tol,info,ipvt,wa,
         *                  lwa)

         integer m,n,ldfjac,info,lwa
         integer ipvt(n)
         double precision tol
         double precision x(n),fvec(m),fjac(ldfjac,n),wa(lwa)
         external fcn

         c     **********
         c
         c     subroutine lmder1
         c
         c     the purpose of lmder1 is to minimize the sum of the squares of
         c     m nonlinear functions in n variables by a modification of the
         c     levenberg-marquardt algorithm. this is done by using the more
         c     general least-squares solver lmder. the user must provide a
         c     subroutine which calculates the functions and the jacobian.
         c
         c     the subroutine statement is
         c
         c       subroutine lmder1(fcn,m,n,x,fvec,fjac,ldfjac,tol,info,
         c                         ipvt,wa,lwa)
         c
         c     where
         c
         c       fcn is the name of the user-supplied subroutine which
         c         calculates the functions and the jacobian. fcn must
         c         be declared in an external statement in the user
         c         calling program, and should be written as follows.
         c
         c         subroutine fcn(m,n,x,fvec,fjac,ldfjac,iflag)
         c         integer m,n,ldfjac,iflag
         c         double precision x(n),fvec(m),fjac(ldfjac,n)
         c         ----------
         c         if iflag = 1 calculate the functions at x and
         c         return this vector in fvec. do not alter fjac.
         c         if iflag = 2 calculate the jacobian at x and
         c         return this matrix in fjac. do not alter fvec.
         c         ----------
         c         return
         c         end
         c
         c         the value of iflag should not be changed by fcn unless
         c         the user wants to terminate execution of lmder1.
         c         in this case set iflag to a negative integer.
         c
         c       m is a positive integer input variable set to the number
         c         of functions.
         c
         c       n is a positive integer input variable set to the number
         c         of variables. n must not exceed m.
         c
         c       x is an array of length n. on input x must contain
         c         an initial estimate of the solution vector. on output x
         c         contains the final estimate of the solution vector.
         c
         c       fvec is an output array of length m which contains
         c         the functions evaluated at the output x.
         c
         c       fjac is an output m by n array. the upper n by n submatrix
         c         of fjac contains an upper triangular matrix r with
         c         diagonal elements of nonincreasing magnitude such that
         c
         c                t     t           t
         c               p *(jac *jac)*p = r *r,
         c
         c         where p is a permutation matrix and jac is the final
         c         calculated jacobian. column j of p is column ipvt(j)
         c         (see below) of the identity matrix. the lower trapezoidal
         c         part of fjac contains information generated during
         c         the computation of r.
         c
         c       ldfjac is a positive integer input variable not less than m
         c         which specifies the leading dimension of the array fjac.
         c
         c       tol is a nonnegative input variable. termination occurs
         c         when the algorithm estimates either that the relative
         c         error in the sum of squares is at most tol or that
         c         the relative error between x and the solution is at
         c         most tol.
         c
         c       info is an integer output variable. if the user has
         c         terminated execution, info is set to the (negative)
         c         value of iflag. see description of fcn. otherwise,
         c         info is set as follows.
         c
         c         info = 0  improper input parameters.
         c
         c         info = 1  algorithm estimates that the relative error
         c                   in the sum of squares is at most tol.
         c
         c         info = 2  algorithm estimates that the relative error
         c                   between x and the solution is at most tol.
         c
         c         info = 3  conditions for info = 1 and info = 2 both hold.
         c
         c         info = 4  fvec is orthogonal to the columns of the
         c                   jacobian to machine precision.
         c
         c         info = 5  number of calls to fcn with iflag = 1 has
         c                   reached 100*(n+1).
         c
         c         info = 6  tol is too small. no further reduction in
         c                   the sum of squares is possible.
         c
         c         info = 7  tol is too small. no further improvement in
         c                   the approximate solution x is possible.
         c
         c       ipvt is an integer output array of length n. ipvt
         c         defines a permutation matrix p such that jac*p = q*r,
         c         where jac is the final calculated jacobian, q is
         c         orthogonal (not stored), and r is upper triangular
         c         with diagonal elements of nonincreasing magnitude.
         c         column j of p is column ipvt(j) of the identity matrix.
         c
         c       wa is a work array of length lwa.
         c
         c       lwa is a positive integer input variable not less than 5*n+m.
         c
         c     subprograms called
         c
         c       user-supplied ...... fcn
         c
         c       minpack-supplied ... lmder
         c
         c     argonne national laboratory. minpack project. march 1980.
         c     burton s. garbow, kenneth e. hillstrom, jorge j. more
         c
         c     **********

         */
        int nprint;

        int[] nfev = new int[2];
        int[] njev = new int[2];

        double[] diag = new double[n + 1];
        double[] qtf = new double[n + 1];

        double factor;
        double ftol;
        double gtol;
        double xtol;

        factor = 1.0e+2;
        info[1] = 0;

        // Check the input parameters for errors.
        if ((n <= 0) || (m < n) || (tol < zero)) {
            return;
        } else {
            // Call lmder_f77.
            maxfev = 20 * (n + 1);
            ftol = tol;
            xtol = tol;
            gtol = zero;
            mode = 1;
            nprint = 0;

            Minpack_f77.lmder_f77(nlls, m, n, x, fvec, fjac, ftol, xtol, gtol,
                    maxfev, diag, mode, factor, nprint, info, nfev, njev, ipvt, qtf);

            if (info[1] == 8) {
                info[1] = 4;
            }

            return;
        }
    }

    public double gShapeDer(double[] a, int start, double x, int jcal,
                            double[] dc) {
        double y = 0.0;
        double b = a[1] / 1.66;
        double freq = a[start + 1];

        if (jcal == 1) {
            double arg1 = (x - freq + (a[start + 2] / 2.0)) / b;
            double arg2 = (x - freq - (a[start + 2] / 2.0)) / b;
            y = Math.exp(-arg1 * arg1) + Math.exp(-arg2 * arg2);
        } else if (jcal == -1) {
            double arg1 = (x - freq + (a[start + 2] / 2.0)) / b;
            double arg2 = (x - freq - (a[start + 2] / 2.0)) / b;
            y = Math.exp(-arg1 * arg1) - Math.exp(-arg2 * arg2);
        } else if (jcal == 2) {
            double arg1 = (x - freq + (a[start + 2] / 2.0)) / b;
            double arg2 = (x - freq) / b;
            double arg3 = (x - freq - (a[start + 2] / 2.0)) / b;
            y = (Math.exp(-arg1 * arg1) / 2.0) + Math.exp(-arg2 * arg2)
                    + (Math.exp(-arg3 * arg3) / 2.0);
        } else {
            double arg1 = (x - freq) / b;
            y = Math.exp(-arg1 * arg1);
            dc[start + 1] = (2 * arg1 * y) / b; //dy/df (without a)
            dc[1] = (2 * y * arg1 * arg1) / b;
        }

        return y;
    }

    public static void main(String[] args) {
        Lmder_f77 lmder = new Lmder_f77();
        lmder.setFunc(7);
        lmder.setLengths(64);

        for (int i = 0; i < lmder.xv.length; i++) {
            lmder.xv[i] = i;
        }

        lmder.initpt();
        lmder.simulate(0.02);
        lmder.dumpXY();
        lmder.randomizeGuess(0.5);
        lmder.doMin();
        System.out.println(lmder.rms());
    }

    class fexpc_f77 implements Lmder_fcn {

        static final int nPar = 3;

        public fexpc_f77() {
        }

        public String getEquation() {
            return "A*exp(-x/B)+C";
        }

        public int getN() {
            return nPar;
        }

        public void setN(int newN) {
        }

        public final void initpt(double[] a) {
            a[1] = 1.3;
            a[2] = 2.0;
            a[3] = 0.0;
        }

        public double[] guess() {
            getStatsForGuess();
            a[1] = yMax;
            a[2] = xMid / 0.693;
            a[3] = 0.0;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
            if (iflag[1] == 1) {
                nfev++;

                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    double yval = calculate(a, t);
                    fvec[i] = yval - yv[i - 1];
                }
            } else if (iflag[1] == 2) {
                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    derivative(a, t, fjac, i);
                }
            }
        }

        public double calculate(double[] a, double x) {
            return (a[1] * Math.exp(-x / a[2])) + a[3];
        }

        public void derivative(double[] a, double x, double[][] fjac, int i) {
            double eVal = Math.exp(-x / a[2]);
            fjac[i][1] = eVal;
            fjac[i][2] = -a[1] * x * eVal;
            fjac[i][3] = 1.0;
        }
    }

    class fexpc1_f77 implements Lmder_fcn {

        static final int nPar = 2;

        public String getEquation() {
            return "A*exp(-x/B)";
        }

        public int getN() {
            return nPar;
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
            for (int i = 1; i <= m; i++) {
                double t = xv[i - 1];
                double yval = calculate(a, t);
                fvec[i] = yval - yv[i - 1];
            }
        }

        public double calculate(double[] a, double x) {
            return a[1] * Math.exp(-x / a[2]);
        }
    }

    class fexpb_f77 implements Lmder_fcn {

        static final int nPar = 2;

        public String getEquation() {
            return "A*x*exp(-x*B)";
        }

        public int getN() {
            return nPar;
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
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

    class fexpg_f77 implements Lmder_fcn {

        static final int nPar = 3;

        public String getEquation() {
            return "A*exp(-((x-B)/C)^2)";
        }

        public int getN() {
            return nPar;
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
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

    class flogistic_f77 implements Lmder_fcn {

        static final int nPar = 4;

        public String getEquation() {
            return "((D-A)*x^C)/(x^C+B^C)+A";
        }

        public int getN() {
            return nPar;
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
            a[1] = yMax;
            a[2] = xMid;
            a[3] = 1.0;
            a[4] = yMin;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
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

    class fexp2_f77 implements Lmder_fcn {

        static final int nPar = 3;

        public String getEquation() {
            return "A*(exp(-x/B)+exp(-x/C))";
        }

        public int getN() {
            return nPar;
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
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

    class funcgf_f77 implements Lmder_fcn {

        static final int nPar = 3;

        public int getN() {
            return nPar;
        }

        public void setN(int newN) {
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
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

    class fexpd_f77 implements Lmder_fcn {

        static final int nPar = 2;

        public String getEquation() {
            return "-2.0*A*exp(-x/B)+A";
        }

        public int getN() {
            return nPar;
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
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

    class flineshape_f77 implements Lmder_fcn {

        int xy_nsig = 1;
        int xy_ndim = 1;
        int nPar = xy_nsig * ((xy_ndim * 2) + 1);
        int[] iJCal = new int[xy_ndim];
        int[] iNPar = new int[xy_ndim];
        double[] ys = new double[xy_ndim];
        double[] xs = new double[xy_ndim];

        public String getEquation() {
            return "Lineshape";
        }

        public int getN() {
            return nPar;
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
            a[2] = xMid / 0.693 / 2.0;
            a[2] = (5.0 * xMid) / 0.693;

            return a;
        }

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
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

    class flineshapeW_f77 implements Lmder_fcn {

        int xy_nsig = 1;
        int xy_ndim = 1;
        int nPar = (xy_nsig * (xy_ndim + 1)) + 1;
        int[] iJCal = new int[xy_ndim];
        int[] iNPar = new int[xy_ndim];
        double[] ys = new double[xy_ndim];
        double[] xs = new double[xy_ndim];

        public String getEquation() {
            return "Lineshape";
        }

        public int getN() {
            return nPar;
        }

        public void setN(int newN) {
            nPar = newN;
            xy_nsig = (nPar - 1) / (xy_ndim + 1);
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
            if (iflag[1] == 1) {
                nfev++;

                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    double yval = calculate(a, t);
                    fvec[i] = yval - yv[i - 1];
                }
            } else if (iflag[1] == 2) {
                double[] dc = new double[a.length]; // derivative component

                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    derivative(a, t, dc, fjac, i);
                }
            }
        }

        public void derivative(double[] a, double x, double[] dc,
                               double[][] fjac, int i) {
            xs[0] = x;

            int kk = 1;
            iNPar[0] = 1;
            fjac[i][1] = 0.0;

            for (int k = 0; k < xy_nsig; k++) {
                int start = kk + 1;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ys[iDim] = gShapeDer(a, start, xs[iDim], iJCal[iDim], dc);
                }

                double ypeak = 1.0;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ypeak = ypeak * ys[iDim];
                }

                fjac[i][1] += dc[1];
                fjac[i][start] = ypeak;
                fjac[i][start + 1] = dc[start + 1] * a[start];

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    kk += iNPar[iDim];
                }

                kk++;
            }
        }

        public double calculate(double[] a, double x) {
            double y = 0;
            xs[0] = x;

            int kk = 1;
            iNPar[0] = 1;

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
            double b = a[1] / 1.66;
            double freq = a[start + 1];

            if (jcal == 1) {
                double arg1 = (x - freq + (a[start + 2] / 2.0)) / b;
                double arg2 = (x - freq - (a[start + 2] / 2.0)) / b;
                y = Math.exp(-arg1 * arg1) + Math.exp(-arg2 * arg2);
            } else if (jcal == -1) {
                double arg1 = (x - freq + (a[start + 2] / 2.0)) / b;
                double arg2 = (x - freq - (a[start + 2] / 2.0)) / b;
                y = Math.exp(-arg1 * arg1) - Math.exp(-arg2 * arg2);
            } else if (jcal == 2) {
                double arg1 = (x - freq + (a[start + 2] / 2.0)) / b;
                double arg2 = (x - freq) / b;
                double arg3 = (x - freq - (a[start + 2] / 2.0)) / b;
                y = (Math.exp(-arg1 * arg1) / 2.0) + Math.exp(-arg2 * arg2)
                        + (Math.exp(-arg3 * arg3) / 2.0);
            } else {
                double arg1 = (x - freq) / b;
                y = Math.exp(-arg1 * arg1);
            }

            return y;
        }
    }

    class flineshapeLorentz_f77 implements Lmder_fcn {

        int xy_nsig = 1;
        int xy_ndim = 1;
        int nPar = (xy_nsig * (xy_ndim + 1)) + 1;
        int[] iJCal = new int[xy_ndim];
        int[] iNPar = new int[xy_ndim];
        double[] ys = new double[xy_ndim];
        double[] xs = new double[xy_ndim];

        public String getEquation() {
            return "Lineshape";
        }

        public int getN() {
            return nPar;
        }

        public void setN(int newN) {
            nPar = newN;
            xy_nsig = (nPar - 1) / (xy_ndim + 1);
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
            if (iflag[1] == 1) {
                nfev++;

                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    double yval = calculate(a, t);
                    fvec[i] = yval - yv[i - 1];
                }
            } else if (iflag[1] == 2) {
                double[] dc = new double[a.length]; // derivative component

                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    derivative(a, t, dc, fjac, i);
                }
            }
        }

        public void derivative(double[] a, double x, double[] dc,
                               double[][] fjac, int i) {
            xs[0] = x;

            int kk = 1;
            iNPar[0] = 1;
            fjac[i][1] = 0.0;

            for (int k = 0; k < xy_nsig; k++) {
                int start = kk + 1;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ys[iDim] = lShapeDer(a, start, xs[iDim], iJCal[iDim], dc);
                }

                double ypeak = 1.0;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ypeak = ypeak * ys[iDim];
                }

                fjac[i][1] += dc[1];
                fjac[i][start] = ypeak;
                fjac[i][start + 1] = dc[start + 1] * a[start];

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    kk += iNPar[iDim];
                }

                kk++;
            }
        }

        public double calculate(double[] a, double x) {
            double y = 0;
            xs[0] = x;

            int kk = 1;
            iNPar[0] = 1;

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
            double b = a[1] * 0.5;
            double freq = a[start + 1];

            y = (b * b) / ((b * b) + ((x - freq) * (x - freq)));

            return y;
        }

        public double lShapeDer(double[] a, int start, double x, int jcal,
                                double[] dc) {
            double y = 0.0;
            double b = a[1] * 0.5;
            double freq = a[start + 1];

            double denom = ((b * b) + ((x - freq) * (x - freq)));
            y = (b * b) / denom;
            dc[start + 1] = (2 * y * (x - freq)) / denom;
            dc[1] = (2 * b * (x - freq) * (x - freq)) / (denom * denom);

            return y;
        }
    }

    class flineshapeLorentzIW_f77 implements Lmder_fcn {

        int xy_nsig = 1;
        int xy_ndim = 1;
        int nPar = (xy_nsig * (xy_ndim + 1)) + 1;
        int[] iJCal = new int[xy_ndim];
        int[] iNPar = new int[xy_ndim];
        double[] ys = new double[xy_ndim];
        double[] xs = new double[xy_ndim];

        public String getEquation() {
            return "Lineshape";
        }

        public int getN() {
            if (nFit != 0) {
                return nFit;
            } else {
                return nPar;
            }
        }

        public void setN(int newN) {
            nPar = newN;
            xy_nsig = (nPar - 1) / (xy_ndim + 1);
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

        public void fcn(int m, int n, double[] a, double[] fvec,
                        double[][] fjac, int[] iflag) {
            for (int i = 1; i <= n; i++) {
                aCalc[map[i]] = a[i];
            }

            double[] amps = ((flineshapeLorentzIW_f77) lmderFunc).fitSignalAmplitudesNN(aCalc);
            int iAmp = 0;

            for (int j = 2; j < aCalc.length; j += 2) {
                aCalc[j] = amps[iAmp];
                iAmp++;
            }

            if (iflag[1] == 1) {
                nfev++;

                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    double yval = calculate(aCalc, t);
                    fvec[i] = yval - yv[i - 1];
                }
            } else if (iflag[1] == 2) {
                double[] dc = new double[aCalc.length]; // derivative component
                double[] jacCol = new double[aCalc.length]; // derivative component

                for (int i = 1; i <= m; i++) {
                    double t = xv[i - 1];
                    derivative(aCalc, t, dc, jacCol, i);

                    for (int j = 1; j <= n; j++) {
                        fjac[i][j] = jacCol[map[j]];
                    }
                }
            }
        }

        public void derivative(double[] a, double x, double[] dc,
                               double[] jacCol, int i) {
            xs[0] = x;

            int kk = 1;
            iNPar[0] = 1;
            jacCol[1] = 0.0;

            for (int k = 0; k < xy_nsig; k++) {
                int start = kk + 1;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ys[iDim] = lShapeDer(a, start, xs[iDim], iJCal[iDim], dc);
                }

                double ypeak = 1.0;

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    ypeak = ypeak * ys[iDim];
                }

                jacCol[1] += dc[1];
                jacCol[start] = ypeak;
                jacCol[start + 1] = dc[start + 1] * a[start];

                for (int iDim = 0; iDim < xy_ndim; iDim++) {
                    kk += iNPar[iDim];
                }

                kk++;
            }
        }

        public double calculate(double[] a, double x) {
            double y = 0;
            xs[0] = x;

            int kk = 1;
            iNPar[0] = 1;

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

        public double calculateOneSig(double[] a, int iSig, double x) {
            int start = (iSig * 2) + 2;
            double y = lShape(a, start, x, 0);

            if (a[start] < 0) {
                y = -y;
            }

            return y;
        }

        public double lShape(double[] a, int start, double x, int jcal) {
            double y = 0.0;
            double b = a[1] * 0.5;
            double freq = a[start + 1];

            y = (b * b) / ((b * b) + ((x - freq) * (x - freq)));

            return y;
        }

        public double lShapeDer(double[] a, int start, double x, int jcal,
                                double[] dc) {
            double y = 0.0;
            double b = a[1] * 0.5;
            double freq = a[start + 1];

            double denom = ((b * b) + ((x - freq) * (x - freq)));
            y = (b * b) / denom;
            dc[start + 1] = (2 * y * (x - freq)) / denom;
            dc[1] = (2 * b * (x - freq) * (x - freq)) / (denom * denom);

            return y;
        }

        public RealVector fitSignalAmplitudes(double[] a) {
            int nSigs = xy_nsig;
            int nRows = xv.length;
            RealMatrix A = new Array2DRowRealMatrix(nRows, nSigs);
            RealVector B = new ArrayRealVector(nRows);

            for (int i = 0; i < nRows; i++) {
                for (int j = 0; j < nSigs; j++) {
                    A.setEntry(i, j, calculateOneSig(a, j, xv[i]));
                }
                B.setEntry(i, yv[i]);
            }

            SingularValueDecomposition svd = new SingularValueDecomposition(A);
            RealVector X = svd.getSolver().solve(B);

            return X;
        }

        public double[] fitSignalAmplitudesNN(double[] a) {
            int nSigs = xy_nsig;
            int nRows = xv.length;
            double[][] A = new double[nRows][nSigs];
            double[] b = new double[nRows];
            int[] pivot = new int[nSigs];

            for (int i = 0; i < nRows; i++) {
                for (int j = 0; j < nSigs; j++) {
                    A[i][j] = calculateOneSig(a, j, xv[i]);
                }

                b[i] = yv[i];
            }

            for (int j = 0; j < nSigs; j++) {
                pivot[j] = j;
            }

            RealMatrix aMat = new Array2DRowRealMatrix(A);
            RealMatrix bMat = new Array2DRowRealMatrix(b);
            NNLSMat nnlsMat = new NNLSMat(aMat, bMat);

            double[] Xp = nnlsMat.getX();
            double[] X = new double[nSigs];

            for (int i = 0; i < nSigs; i++) {
                double amp = Xp[i];
                int iSig = i;
                int start = (iSig * 2) + 2;

                if (a[start] < 0) {
                    amp = -amp;
                }

                X[iSig] = amp;
            }

            return X;
        }
    }

}
