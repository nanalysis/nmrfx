package org.nmrfx.structure.tools;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.Optional;

public class LACSCalculator {
    record PPMDiff(double ppmDDCA, double ppmDDCB, double ppmDDC) {
    }
    record LACSResult(double xMin, double xMax, double nMedian, double pMedian, double allMedian) {
    }

    public Optional<LACSResult> calculateLACS(String calcAtomName, int iGroup) {
        Molecule molecule = Molecule.getActive();
        final double pSlope;
        final double nSlope;
        if (calcAtomName.equalsIgnoreCase("CA")) {
            nSlope = 0.4167;
            pSlope = 0.7699;
        } else {
            nSlope = -0.655;
            pSlope = -0.254;
        }
        DescriptiveStatistics nStat = new DescriptiveStatistics();
        DescriptiveStatistics pStat = new DescriptiveStatistics();
        DescriptiveStatistics allStat = new DescriptiveStatistics();
        DescriptiveStatistics deltaStat = new DescriptiveStatistics();
        LACSResult lacsResult = null;
        if (molecule != null) {
            molecule.getPolymers().forEach(polymer -> polymer.getResidues().forEach(residue -> {
                if (!residue.getName().equalsIgnoreCase("pro")
                        && !residue.getName().equalsIgnoreCase("gly")
                        && !residue.getName().equalsIgnoreCase("cys")) {
                    var ppmOpt = getDeltas(residue, iGroup, calcAtomName);
                    ppmOpt.ifPresent(ppmDiff -> {
                        double delta = ppmDiff.ppmDDCA - ppmDiff.ppmDDCB;
                        deltaStat.addValue(delta);
                        double value;
                        if (delta > 0.0) {
                            value = ppmDiff.ppmDDC - delta * pSlope;
                            pStat.addValue(value);
                        } else {
                            value = ppmDiff.ppmDDC - delta * nSlope;
                            nStat.addValue(value);
                        }
                        allStat.addValue(value);
                    });
                }
            }));
            double xMin = deltaStat.getMin();
            double xMax = deltaStat.getMax();
            double nMedian = nStat.getPercentile(50);
            double pMedian = pStat.getPercentile(50);
            double allMedian = allStat.getPercentile(50);
            lacsResult = new LACSResult(xMin, xMax, nMedian, pMedian, allMedian);
        }
        return Optional.ofNullable(lacsResult);
    }

    Optional<PPMDiff> getDeltas(Residue residue, int iGroup, String calcAtomName) {
        Atom caAtom = residue.getAtom("CA");
        Atom cbAtom = residue.getAtom("CB");
        Atom calcAtom = residue.getAtom(calcAtomName);
        if ((caAtom != null) && (cbAtom != null)) {
            PPMv caPPMv = caAtom.getPPM(iGroup);
            Double caRef = caAtom.getRefPPM();

            PPMv cbPPMv = cbAtom.getPPM(iGroup);
            Double cbRef = cbAtom.getRefPPM();

            PPMv calcPPMv = calcAtom.getPPM(iGroup);
            Double cRef = calcAtom.getRefPPM();
            if (caPPMv.isValid() && cbPPMv.isValid() && calcPPMv.isValid() &&
                    caRef != null && cbRef != null && cRef != null) {
                double caPPM = caPPMv.getValue();
                double cbPPM = cbPPMv.getValue();
                double ppm = calcPPMv.getValue();
                double ppmDDCA = caPPM - caRef;
                double ppmDDCB = cbPPM - cbRef;
                double ppmDDC = ppm - cRef;
                PPMDiff ppmDiff = new PPMDiff(ppmDDCA, ppmDDCB, ppmDDC);
                return Optional.of(ppmDiff);
            }
        }
        return Optional.empty();
    }

}
