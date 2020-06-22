package org.nmrfx.structure.seqassign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.io.AtomParser;
import org.nmrfx.structure.chemistry.predict.BMRBStats;
import smile.stat.distribution.ChiSquareDistribution;
import smile.stat.distribution.MultivariateGaussianDistribution;

/**
 *
 * @author brucejohnson
 */
public class FragmentScoring {

    static Map<String, MultivariateGaussianDistribution> aaDistMap = new HashMap<>();

    public static class AAScore {

        final double score;
        final String name;
        double norm;

        public AAScore(String name, double score) {
            this.score = score;
            this.name = name;
        }

        public Double getScore() {
            return score;
        }

        public Double getNorm() {
            return norm;
        }

        public String getName() {
            return name;
        }

        public void normalize(double sum) {
            norm = score / sum;
        }

    }

    static void initAADistMap() {
        BMRBStats.loadAllIfEmpty();
        for (String aaName : AtomParser.getAANames()) {

            double[] means;
            double[] vars;
            if (aaName.equalsIgnoreCase("gly")) {
                means = new double[1];
                vars = new double[1];
            } else {
                means = new double[2];
                vars = new double[2];
            }
            String[] aNames = {"CA", "CB"};
            for (String aName : aNames) {
                Optional<PPMv> ppmVOpt = BMRBStats.getValue(aaName, aName);
                ppmVOpt.ifPresent(ppmV -> {
                    int i = aName.equals("CA") ? 0 : 1;
                    means[i] = ppmV.getValue();
                    vars[i] = ppmV.getError() * ppmV.getError();
                });
            }
            MultivariateGaussianDistribution dist = new MultivariateGaussianDistribution(means, vars);
            aaDistMap.put(aaName, dist);
        }
    }

    public static List<AAScore> scoreAA(double[] ppms) {
        if (aaDistMap.isEmpty()) {
            initAADistMap();
        }
        List<AAScore> scores = new ArrayList<>();
        for (Map.Entry<String, MultivariateGaussianDistribution> entry : aaDistMap.entrySet()) {
            double p;
            if (entry.getKey().equalsIgnoreCase("gly")) {
                double[] ppmGly = new double[1];
                ppmGly[0] = ppms[0];
                p = entry.getValue().p(ppmGly);
            } else {
                p = entry.getValue().p(ppms);
            }
            AAScore score = new AAScore(entry.getKey(), p);
            scores.add(score);
        }
        double sum = 0.0;
        for (AAScore aaScore : scores) {
            sum += aaScore.score;
        }
        for (AAScore aaScore : scores) {
            aaScore.normalize(sum);
        }

        scores.sort((o1, o2) -> o2.getScore().compareTo(o1.getScore()));
        return scores;
    }

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
