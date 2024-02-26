/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.dataops;

import org.ejml.data.DMatrixRMaj;
import org.ejml.data.ZMatrixRMaj;

/**
 * @author brucejohnson
 */
public class KronProduct {

    public static DMatrixRMaj kronProd(DMatrixRMaj a, DMatrixRMaj b) {
        int m = a.getNumRows();
        int n = a.getNumCols();
        int p = b.getNumRows();
        int q = b.getNumCols();
        int nRows = m * p;
        int nCols = n * q;
        DMatrixRMaj c = new DMatrixRMaj(nRows, nCols);
        kronProd(a, b, c);
        return c;
    }

    public static ZMatrixRMaj kronProd(ZMatrixRMaj a, ZMatrixRMaj b) {
        int m = a.getNumRows();
        int n = a.getNumCols();
        int p = b.getNumRows();
        int q = b.getNumCols();
        int nRows = m * p;
        int nCols = n * q;
        ZMatrixRMaj c = new ZMatrixRMaj(nRows, nCols);
        kronProd(a, b, c);
        return c;
    }

    public static ZMatrixRMaj kronProd(DMatrixRMaj a, ZMatrixRMaj b) {
        int m = a.getNumRows();
        int n = a.getNumCols();
        int p = b.getNumRows();
        int q = b.getNumCols();
        int nRows = m * p;
        int nCols = n * q;
        ZMatrixRMaj c = new ZMatrixRMaj(nRows, nCols);
        kronProd(a, b, c);
        return c;
    }

    public static ZMatrixRMaj kronProd(ZMatrixRMaj a, DMatrixRMaj b) {
        int m = a.getNumRows();
        int n = a.getNumCols();
        int p = b.getNumRows();
        int q = b.getNumCols();
        int nRows = m * p;
        int nCols = n * q;
        ZMatrixRMaj c = new ZMatrixRMaj(nRows, nCols);
        kronProd(a, b, c);
        return c;
    }

    public static void kronProd(DMatrixRMaj a, DMatrixRMaj b, DMatrixRMaj c) {
        int m = a.getNumRows();
        int n = a.getNumCols();
        int p = b.getNumRows();
        int q = b.getNumCols();
        int nRows = c.getNumRows();
        int nCols = c.getNumCols();
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nCols; j++) {
                int iA = i / p;
                int jA = j / q;
                int iB = i % p;
                int jB = j % q;
                double aVal = a.get(iA, jA);
                double bVal = b.get(iB, jB);
                double v = aVal * bVal;
                c.set(i, j, v);
            }
        }
    }

    public static void kronProd(ZMatrixRMaj a, ZMatrixRMaj b, ZMatrixRMaj c) {
        int m = a.getNumRows();
        int n = a.getNumCols();
        int p = b.getNumRows();
        int q = b.getNumCols();
        int nRows = c.getNumRows();
        int nCols = c.getNumCols();
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nCols; j++) {
                int iA = i / p;
                int jA = j / q;
                int iB = i % p;
                int jB = j % q;
                double realA = a.getReal(iA, jA);
                double realB = b.getReal(iB, jB);
                double imagA = a.getImag(iA, jA);
                double imagB = b.getImag(iB, jB);

                double vr = realA * realB - imagA * imagB;
                double vi = realA * imagB + imagA * realB;
                c.set(i, j, vr, vi);
            }
        }
    }

    public static void kronProd(DMatrixRMaj a, ZMatrixRMaj b, ZMatrixRMaj c) {
        int m = a.getNumRows();
        int n = a.getNumCols();
        int p = b.getNumRows();
        int q = b.getNumCols();
        int nRows = c.getNumRows();
        int nCols = c.getNumCols();
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nCols; j++) {
                int iA = i / p;
                int jA = j / q;
                int iB = i % p;
                int jB = j % q;
                double realA = a.get(iA, jA);
                double realB = b.getReal(iB, jB);
                double imagB = b.getImag(iB, jB);

                double vr = realA * realB;
                double vi = realA * imagB;
                c.set(i, j, vr, vi);
            }
        }
    }

    public static void kronProd(ZMatrixRMaj a, DMatrixRMaj b, ZMatrixRMaj c) {
        int m = a.getNumRows();
        int n = a.getNumCols();
        int p = b.getNumRows();
        int q = b.getNumCols();
        int nRows = c.getNumRows();
        int nCols = c.getNumCols();
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nCols; j++) {
                int iA = i / p;
                int jA = j / q;
                int iB = i % p;
                int jB = j % q;
                double realA = a.getReal(iA, jA);
                double imagA = a.getImag(iA, jA);
                double realB = b.get(iB, jB);

                double vr = realA * realB;
                double vi = imagA * realB;
                c.set(i, j, vr, vi);
            }
        }
    }

}
