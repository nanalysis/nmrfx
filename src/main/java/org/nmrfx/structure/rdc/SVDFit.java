/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.rdc;

import java.util.List;
import java.util.Random;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;

/**
 *
 * @author brucejohnson
 */
public class SVDFit {

    RealMatrix AR;
    List<RDCVector> rdcVecs;
    RealVector expRDCNorm;
    RealVector expRDC;
    RealVector calcRDCNorm;
    RealVector calcRDC;
    RealVector normVec;
    RealVector orderMatrixElems;

    public SVDFit(RealMatrix AR, List<RDCVector> rdcVecs) {
        this.AR = AR;
        this.rdcVecs = rdcVecs;
        expRDCNorm = new ArrayRealVector(rdcVecs.size());
        expRDC = new ArrayRealVector(rdcVecs.size());
        normVec = new ArrayRealVector(rdcVecs.size());
        for (int i = 0; i < rdcVecs.size(); i++) {
            RDCVector rdcVec = rdcVecs.get(i);
            expRDCNorm.setEntry(i, rdcVec.getNormExpRDC());
            expRDC.setEntry(i, rdcVec.getExpRDC());
            normVec.setEntry(i, rdcVec.getMaxRDC());
        }
    }

    public AlignmentMatrix fit() {
        SingularValueDecomposition svd = new SingularValueDecomposition(AR);

        Random random = null;
        int cycle = 0;
        AlignmentMatrix aMat;
        while (true) {
            System.out.println("cycle " + cycle);
            // calculate the x vector for which |Ax - b| is minimized
            orderMatrixElems = svd.getSolver().solve(expRDCNorm);
            double sYY = orderMatrixElems.getEntry(0);
            double sZZ = orderMatrixElems.getEntry(1);
            double sXY = orderMatrixElems.getEntry(2);
            double sXZ = orderMatrixElems.getEntry(3);
            double sYZ = orderMatrixElems.getEntry(4);
            double sXX = -sZZ - sYY;
            aMat = AlignmentMatrix.getValidMatrix(sXX, sYY, sZZ, sXY, sXZ, sYZ);
            if (aMat != null) {
                System.out.println("complete svd");
                break;
            } else {
                cycle++;
                if (cycle > 10) {
                    break;
                }
                if (random == null) {
                    random = new Random();
                }
                for (int i = 0; i < rdcVecs.size(); i++) {
                    double value = random.nextGaussian() * rdcVecs.get(i).getError() + rdcVecs.get(i).getExpRDC();
                    expRDCNorm.setEntry(i, value / normVec.getEntry(i));
                }
            }
        }
        if (aMat != null) {
            calcRDCNorm = AR.operate(orderMatrixElems);
            calcRDC = calcRDCNorm.ebeMultiply(normVec);
            for (int i = 0; i < calcRDC.getDimension(); i++) {
                rdcVecs.get(i).setRDC(calcRDC.getEntry(i));
            }
        }
        return aMat;
    }

    public void evaluate() {
        RealVector dcDiff = calcRDC.subtract(expRDC);
        double dcSqSum = 0;
        for (int i = 0; i < dcDiff.getDimension(); i++) {
            System.out.println(normVec.getEntry(i) + " " + calcRDC.getEntry(i) + " " + expRDC.getEntry(i));
            double diff = dcDiff.getEntry(i);
            dcSqSum += diff * diff;
        }
        System.out.println(dcSqSum);
        double rms = Math.sqrt(dcSqSum / dcDiff.getDimension());
        System.out.println(rms);

    }
}
