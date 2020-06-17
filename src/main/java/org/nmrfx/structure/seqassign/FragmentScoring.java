package org.nmrfx.structure.seqassign;

import java.util.List;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Residue;
import smile.stat.distribution.ChiSquareDistribution;

/**
 *
 * @author brucejohnson
 */
public class FragmentScoring {

    public static Double scoreAtomPPM(final Residue residue, final String atomName,
            final double ppm, final double sdevMul) {
        Double result = null;
        String resName = residue.getName();
        resName = resName.toLowerCase();

        if (!atomName.equals("")) {
            if (!(resName.equalsIgnoreCase("gly")
                    && atomName.equalsIgnoreCase("cb"))) {
                Atom atom = residue.getAtom(atomName);
                StandardPPM stdShift = null;
                PPMv ppmV = atom.spatialSet.getRefPPM();
                if ((ppmV != null) && ppmV.isValid()) {
                    stdShift = new StandardPPM(ppmV.getValue(), ppmV.getError());

                }
                if (stdShift != null) {
                    //result = Math.abs(stdShift.avg-ppm)/stdShift.sdev;
                    double normDev = (stdShift.getAvg() - ppm) / (stdShift.getSdev() * sdevMul);
                    result = new Double(normDev * normDev);
                }
            }
        }

        return result;
    }

    public static PPMScore scoreAtomPPM(final double pOK, final double sdevMul, final Residue residue, final List<AtomShiftValue> atomShiftValues) {
        //Standard stdVals = (Standard) scoreMap.get(atomName);
        //System.out.println("scoreAtomPPM "+resName);
        PPMScore matchScore = new PPMScore(atomShiftValues);

        double resScore = 0.0;
        int nValues = 0;
        for (AtomShiftValue atomShiftValue : atomShiftValues) {
            Double score = scoreAtomPPM(residue, atomShiftValue.getAName(), atomShiftValue.getPPM(), sdevMul);
            if (score != null) {
                resScore += score.doubleValue();
                nValues++;
            }
        }

        ChiSquareDistribution chiSquare = new ChiSquareDistribution(nValues);
        double pValue = chiSquare.p(resScore);

        if (pValue < pOK) {
            matchScore.ok = false;
        }

        matchScore.totalScore = pValue;

        return matchScore;
    }

}
