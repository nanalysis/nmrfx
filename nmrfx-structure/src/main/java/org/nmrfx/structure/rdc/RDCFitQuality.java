/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.rdc;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.nmrfx.chemistry.RDC;

import java.util.List;

/**
 * @author brucejohnson
 */
public class RDCFitQuality {

    int n;
    double rms;
    double qRMS;
    double qRhomb;
    double chiSq;
    double slope;
    double slopeErr;
    double intercept;
    double interceptErr;
    double r;
    double r2;

    public int getN() {
        return n;
    }

    public double getRMS() {
        return rms;
    }

    public double getQRMS() {
        return qRMS;
    }

    public double getQRhomb() {
        return qRhomb;
    }

    public double getChiSq() {
        return chiSq;
    }

    public double getSlope() {
        return slope;
    }

    public double getIntercept() {
        return intercept;
    }

    public double getR() {
        return r;
    }

    public double getR2() {
        return r2;
    }

    public void evaluate(AlignmentMatrix aMat, List<RDC> rdcVecs) {
        SimpleRegression sReg = new SimpleRegression();
        double dcDiffSqSum = 0;
        double dcSqSum = 0;
        n = rdcVecs.size();
        chiSq = 0.0;
        for (RDC rdcVec : rdcVecs) {
            double rdc = rdcVec.getRDC();
            double rdcExp = rdcVec.getExpRDC();
            double delta = rdc - rdcExp;
            double err = rdcVec.getError();
            dcDiffSqSum += delta * delta;
            dcSqSum += rdcExp * rdcExp;
            chiSq += (delta * delta) / err;
            sReg.addData(rdcExp, rdc);
        }
        rms = Math.sqrt(dcDiffSqSum / n);
        double axial = aMat.calcSAxial();
        double axialFactor = 21585.19; //from PALES article: NATURE PROTOCOLS|VOL.3 NO.4|2008
        double axialNorm = axial * axialFactor;
        double rhombicity = aMat.calcRhombicity();
        double rmsD = Math.sqrt(dcSqSum / n);
        double rhombD = Math.sqrt(2.0 * (axialNorm) * (axialNorm) * (4.0 + 3.0 * rhombicity * rhombicity) / 5.0);
        qRMS = rms / rmsD;
        qRhomb = rms / rhombD;

        sReg.regress();
        slope = sReg.getSlope();
        slopeErr = sReg.getSlopeStdErr();
        intercept = sReg.getIntercept();
        interceptErr = sReg.getInterceptStdErr();
        r = sReg.getR();
        r2 = sReg.getRSquare();

    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("n ").append(n).append("\n");
        sBuilder.append(String.format("rms %.3f", rms)).append("\n");
        sBuilder.append(String.format("chiSq %.3f", chiSq)).append("\n");
        sBuilder.append(String.format("Q-rms %.3f", qRMS)).append("\n");
        sBuilder.append(String.format("Q-AxRh %.3f", qRhomb)).append("\n");
        sBuilder.append(String.format("slope %.3f +/- %.3f", slope, slopeErr)).append("\n");
        sBuilder.append(String.format("intercept %.3f +/- %.3f", intercept, interceptErr)).append("\n");
        sBuilder.append(String.format("r %.3f", r)).append("\n");
        sBuilder.append(String.format("r2 %.3f", r2));

        return sBuilder.toString();
    }

}
