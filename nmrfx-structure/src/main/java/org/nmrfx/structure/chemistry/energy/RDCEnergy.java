package org.nmrfx.structure.chemistry.energy;

import org.apache.commons.math3.linear.RealMatrix;
import org.nmrfx.chemistry.RDC;
import org.nmrfx.chemistry.constraints.RDCConstraintSet;
import org.nmrfx.structure.rdc.AlignmentMatrix;
import org.nmrfx.structure.rdc.RDCFitQuality;
import org.nmrfx.structure.rdc.SVDFit;

import java.util.ArrayList;
import java.util.List;

public class RDCEnergy {
    List<RDC> rdcValues;
    RDCEnergy(RDCConstraintSet rdcConstraintSet) {
        rdcValues = new ArrayList<>(rdcConstraintSet.get());
    }
    double calcEnergy() {
        int iStructure = 0;
        RealMatrix directionMatrix = AlignmentMatrix.setupDirectionMatrix(rdcValues, iStructure);
        SVDFit svdFit = new SVDFit(directionMatrix, rdcValues);
        AlignmentMatrix aMat = svdFit.fit();
        aMat.calcAlignment();
        RDCFitQuality fitQuality = new RDCFitQuality();
        fitQuality.evaluate(aMat, rdcValues);
        return fitQuality.getQRMS();
    }
}
