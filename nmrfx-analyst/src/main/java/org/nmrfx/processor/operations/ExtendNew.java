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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.operations;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.FieldMatrix;
import org.nmrfx.processor.math.LinearPrediction;
import org.nmrfx.processor.math.Polynomial;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.VecUtil;
import org.nmrfx.processor.processing.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Linear Prediction
 *
 * @author johnsonb
 */
public class ExtendNew extends Operation {
    private static final Logger log = LoggerFactory.getLogger(ExtendNew.class);

    private final int fitStart;
    private final int fitEnd;
    private final int ncoef;
    private final int predictStart;
    private final int predictEnd;
    private final double threshold;
    private final boolean calculateForward;
    private final boolean calculateBackward;

    /**
     * Extend a Vec using linear prediction.
     *
     * @param fitStart
     * @param fitEnd
     * @param predictStart
     * @param predictEnd
     * @param ncoefgetEntry
     * @param threshold
     * @param mode
     * @param forward
     * @param backward
     */
    public ExtendNew(int fitStart, int fitEnd, int predictStart, int predictEnd,
                     int ncoef, double threshold, boolean backward,
                     boolean forward) {
        this.fitStart = fitStart;
        this.fitEnd = fitEnd;
        this.predictStart = predictStart;
        this.predictEnd = predictEnd;
        this.ncoef = ncoef;
        this.threshold = threshold;
        this.calculateForward = forward;
        this.calculateBackward = backward;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        try {
            svdPredLP(vector, fitStart, fitEnd, ncoef, threshold, predictStart, predictEnd, calculateBackward, calculateForward);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

        return this;
    }

    private void svdPredLP(Vec vector, int start, int fitEnd, int ncoef, double threshold, int startPred, int endPred, boolean calculateBackward, boolean calculateForward) throws OperationException, Exception {
        if (ncoef <= 0) {
            ncoef = vector.getSize() / 4;
        }
        if (fitEnd <= 0) {
            fitEnd = vector.getSize() - 1;
        }
        if (predictEnd < 0) {
            endPred = vector.getSize() - 1;
        }
        if (ncoef == 0) {
            ncoef = vector.getSize() / 4;
        }
        if (fitEnd <= start) {
            throw new OperationException("svdPredLP: fitEnd <= fitStart");
        }
        int n = ncoef;
        if (start < 1) {
            start = 1;
        }
        int m = fitEnd - n - start + 1;
        if (m < n) {
            throw new OperationException("svdPredLP: m < n");
        }

        boolean insertion = true;
        if (startPred > start) {
            insertion = false;
        }

        Complex[] ocoefB = null;
        Complex[] ocoefF = null;
        Polynomial polyF = null;
        Polynomial polyB = null;
        boolean tlsMode = true;
        FieldMatrix<Complex> zInv = null;
        if (calculateBackward) {
            Complex[] coefB = null;
            if (tlsMode) {
                coefB = LinearPrediction.getCoefsByTLS(vector.getCvec(), start, m, n, threshold, true);
            } else {
                //FIXME implement this function
                throw new OperationException("tls mode cannot be false");
            }
            ocoefB = new Complex[coefB.length + 1];
            // negate,copy and reverse terms
            for (int i = 0; i < coefB.length; i++) {
                ocoefB[i] = new Complex(-coefB[coefB.length - 1 - i].getReal(), -coefB[coefB.length - 1 - i].getImaginary());
            }
            // add highest order term
            ocoefB[ocoefB.length - 1] = Complex.ONE;

            polyB = new Polynomial(ocoefB.length);
            polyB.svejgardRoot(ocoefB.length - 1, ocoefB);
        }

        if (calculateForward) {
            Complex[] coefF = null;
            if (tlsMode) {
                coefF = LinearPrediction.getCoefsByTLS(vector.getCvec(), start, m, n, threshold, false);
            } else {
                //FIXME implement this function
                throw new OperationException("tls mode cannot be false");
            }
            ocoefF = new Complex[coefF.length + 1];
            // negate and copy terms
            for (int i = 0; i < coefF.length; i++) {
                ocoefF[i] = new Complex(-coefF[i].getReal(), -coefF[i].getImaginary());
            }
            // add highest order term
            ocoefF[ocoefF.length - 1] = Complex.ONE;

            polyF = new Polynomial(ocoefF.length);
            polyF.svejgardRoot(ocoefF.length - 1, ocoefF);
        }

        if (!insertion) {
            if (polyF != null) {
                VecUtil.reflectRoots(polyF.root, false);
            }
            if (polyB != null) {
                VecUtil.conjugate(polyB.root);
                VecUtil.reflectRoots(polyB.root, false);
            }
        } else {
            if (polyF != null) {
                VecUtil.conjugate(polyF.root);
                VecUtil.reflectRoots(polyF.root, true);
            }
            if (polyB != null) {
                VecUtil.reflectRoots(polyB.root, true);
            }
        }

        Complex[] coefFinal = null;
        Complex[] coefF2 = null;
        Complex[] coefB2 = null;

        if (polyF != null) {
            coefF2 = polyF.makeCoeffs();
            VecUtil.negate(coefF2);
            coefFinal = coefF2;
        }

        if (polyB != null) {
            coefB2 = polyB.makeCoeffs();
            VecUtil.negate(coefB2);
            coefFinal = coefB2;
        }

        if ((polyF != null) && (polyB != null)) {
            VecUtil.addVector(coefF2, coefF2.length, coefB2, coefF2);
            for (int i = 0; i < coefF2.length; i++) {
                coefFinal[i] = new Complex(coefF2[i].getReal() / 2, coefF2[i].getImaginary() / 2);
            }
        }
        if (insertion) {
            VecUtil.reverse(coefFinal);
            insertWithPrediction(vector, coefFinal, endPred, startPred, ncoef);
        } else {
            extendWithPrediction(vector, coefFinal, endPred, startPred, ncoef);
        }
    }

    private void insertWithPrediction(Vec vector, Complex[] coef,
                                      int endPred, int startPred, int ncoef) {
        int nPredict = endPred - startPred + 1;
        if (startPred < 0) {
            System.arraycopy(vector.getCvec(), 0, vector.getCvec(), -startPred, vector.getCvec().length + startPred);
            endPred += -startPred;
        }

        for (int i = 0; i < nPredict; i++) {
            Complex sum = Complex.ZERO;
            for (int j = 0; j < ncoef; j++) {
                sum = sum.add(coef[j].multiply(vector.getComplex(endPred + 1 - i + j)));
            }
            vector.set(endPred - i, sum);
        }
    }

    private void extendWithPrediction(Vec vector, Complex[] coef, int endPred, int startPred, int ncoef) {
        int nPredict = endPred - startPred + 1;
        int newSize = endPred + 1;
        if (newSize > vector.getSize()) {
            vector.resize(newSize);
        }

        for (int i = 0; i < nPredict; i++) {
            Complex sum = Complex.ZERO;
            for (int j = 0; j < ncoef; j++) {
                sum = sum.add(coef[j].multiply(vector.getComplex(startPred - ncoef + i + j)));
            }
            vector.set(startPred + i, sum);
        }
    }

}
