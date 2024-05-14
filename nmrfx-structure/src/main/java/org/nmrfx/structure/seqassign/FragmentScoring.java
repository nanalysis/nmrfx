package org.nmrfx.structure.seqassign;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.AtomParser;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.predict.BMRBStats;

import java.util.*;

/**
 * @author brucejohnson
 */
public class FragmentScoring {

    static Map<String, MultivariateNormalDistribution> aaDistMap = new HashMap<>();

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

    static void initMolAaDistMap() {
        BMRBStats.loadAllIfEmpty();
        Molecule molecule = Molecule.getActive();
        var names = molecule.getPolymers().stream().
                flatMap(poly -> poly.getResidues().
                        stream()).map(Residue::getName).
                toList();
        for (String aaName : names) {
            double[] means;
            double[][] vars;
            if (aaName.equalsIgnoreCase("gly")) {
                means = new double[1];
                vars = new double[1][1];
            } else {
                means = new double[2];
                vars = new double[2][2];
            }
            String[] aNames = {"CA", "CB"};
            for (String aName : aNames) {
                Optional<PPMv> ppmVOpt = BMRBStats.getValue(aaName, aName);
                ppmVOpt.ifPresent(ppmV -> {
                    int i = aName.equals("CA") ? 0 : 1;
                    means[i] = ppmV.getValue();
                    vars[i][i] = ppmV.getError() * ppmV.getError();
                    if (aaName.equalsIgnoreCase("CYS") && aName.equalsIgnoreCase("CB")) {
                        means[i] = 29.0;
                        vars[i][i] = 3.0;
                    }
                });
            }
            MultivariateNormalDistribution dist = new MultivariateNormalDistribution(means, vars);
            aaDistMap.put(aaName, dist);
        }
    }

    static void initAADistMap() {
        BMRBStats.loadAllIfEmpty();
        for (String aaName : AtomParser.getAANames()) {

            double[] means;
            double[][] vars;
            if (aaName.equalsIgnoreCase("gly")) {
                means = new double[1];
                vars = new double[1][1];
            } else {
                means = new double[2];
                vars = new double[2][2];
            }
            String[] aNames = {"CA", "CB"};
            for (String aName : aNames) {
                Optional<PPMv> ppmVOpt = BMRBStats.getValue(aaName, aName);
                ppmVOpt.ifPresent(ppmV -> {
                    int i = aName.equals("CA") ? 0 : 1;
                    means[i] = ppmV.getValue();
                    vars[i][i] = ppmV.getError() * ppmV.getError();
                });
            }
            MultivariateNormalDistribution dist = new MultivariateNormalDistribution(means, vars);
            aaDistMap.put(aaName, dist);
        }
    }

    public static List<AAScore> scoreAA(double[] ppms) {
        if (aaDistMap.isEmpty()) {
            initMolAaDistMap();
        }
        List<AAScore> scores = new ArrayList<>();
        for (Map.Entry<String, MultivariateNormalDistribution> entry : aaDistMap.entrySet()) {
            double p;
            boolean ok = true;
            if (entry.getKey().equalsIgnoreCase("gly")) {
                double[] ppmGly = new double[1];
                ppmGly[0] = ppms[0];
                if (Double.isNaN(ppms[0])) {
                    ok = false;
                    p = 0.0;
                } else {
                    p = entry.getValue().density(ppmGly);
                }
            } else {
                if (Double.isNaN(ppms[0]) || Double.isNaN(ppms[1])) {
                    ok = false;
                    p = 0.0;
                } else {
                    p = entry.getValue().density(ppms);
                }
            }
            if (ok) {
                AAScore score = new AAScore(entry.getKey(), p);
                scores.add(score);
            }
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

        if (!atomName.isEmpty()) {
            if (!(resName.equalsIgnoreCase("gly")
                    && atomName.equalsIgnoreCase("cb"))) {
                Atom atom = residue.getAtom(atomName);
                if (atom != null) {
                    StandardPPM stdShift = null;
                    PPMv ppmV = atom.spatialSet.getPPM(0);
                    if ((ppmV != null) && ppmV.isValid()) {
                        stdShift = new StandardPPM(ppmV.getValue(), 0.05);
                    } else {
                        PPMv refPPMV = atom.spatialSet.getRefPPM();
                        if ((refPPMV != null) && refPPMV.isValid()) {
                            stdShift = new StandardPPM(refPPMV.getValue(), refPPMV.getError());
                        }
                    }
                    if (stdShift != null) {
                        double normDev = (stdShift.getAvg() - ppm) / (stdShift.getSdev() * sdevMul);
                        result = normDev * normDev;
                    }
                }
            }
        }
        return result;
    }

    public static PPMScore scoreAtomPPM(final double pOK, final double sdevMul, final Residue residue, final List<AtomShiftValue> atomShiftValues) {
        PPMScore matchScore = new PPMScore(atomShiftValues);

        double resScore = 0.0;
        int nValues = 0;
        for (AtomShiftValue atomShiftValue : atomShiftValues) {
            Double score = scoreAtomPPM(residue, atomShiftValue.getAName(), atomShiftValue.getPPM(), sdevMul);
            if (score != null) {
                resScore += score;
                nValues++;
            }
        }
        double pValue;
        if (nValues < 1) {
            matchScore.ok = false;
            pValue = 0.0;
        } else {
            ChiSquaredDistribution chiSquare = new ChiSquaredDistribution(nValues);
            pValue = 1.0 - chiSquare.cumulativeProbability(resScore);
            if (Double.isNaN(pValue) || (pValue < pOK)) {
                matchScore.ok = false;
            }
        }

        matchScore.totalScore = pValue;

        return matchScore;
    }

}
