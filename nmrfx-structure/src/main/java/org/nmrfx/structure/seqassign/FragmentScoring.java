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

    private FragmentScoring() {

    }

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
        var resNames = molecule.getPolymers().stream().
                flatMap(poly -> poly.getResidues().
                        stream()).map(Residue::getName).
                toList();
        for (String resName : resNames) {
            double[] means2 = new double[2];
            double[][] vars2 = new double[2][2];
            double[] meansCA = new double[1];
            double[][] varsCA = new double[1][1];
            double[] meansCB = new double[1];
            double[][] varsCB = new double[1][1];
            String[] aNames = {"CA", "CB"};
            for (String aName : aNames) {
                Optional<PPMv> ppmVOpt = BMRBStats.getValue(resName, aName);
                ppmVOpt.ifPresent(ppmV -> {
                    if (aName.equals("CA")) {
                        meansCA[0] = ppmV.getValue();
                        varsCA[0][0] = ppmV.getError() * ppmV.getError();
                    } else if (!resName.equalsIgnoreCase("GLY")) {
                        if (resName.equalsIgnoreCase("CYS")) {
                            meansCB[0] = 29.0;
                            varsCB[0][0] = 3.0;
                        } else {
                            meansCB[0] = ppmV.getValue();
                            varsCB[0][0] = ppmV.getError() * ppmV.getError();
                        }
                    }
                });
            }
            MultivariateNormalDistribution distCA = new MultivariateNormalDistribution(meansCA, varsCA);
            aaDistMap.put(resName + "_CA", distCA);
            if (!resName.equalsIgnoreCase("GLY")) {
                MultivariateNormalDistribution distCB = new MultivariateNormalDistribution(meansCB, varsCB);
                aaDistMap.put(resName + "_CB", distCB);
                double[] means = {meansCA[0], meansCB[0]};
                double[][] vars = {{varsCA[0][0], 0.0}, {0.0, varsCB[0][0]}};
                MultivariateNormalDistribution dist = new MultivariateNormalDistribution(means, vars);
                aaDistMap.put(resName, dist);
            }
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
        String mode;
        double[] ppm1 = new double[1];
        if (!Double.isNaN(ppms[0]) && Double.isNaN(ppms[1])) {
            mode = "_CA";
            ppm1[0] = ppms[0];
        } else if (Double.isNaN(ppms[0]) && !Double.isNaN(ppms[1])) {
            mode = "_CB";
            ppm1[0] = ppms[1];
        } else if (!Double.isNaN(ppms[0]) && !Double.isNaN(ppms[1])) {
            mode = "";
        } else {
            return scores;
        }

        for (Map.Entry<String, MultivariateNormalDistribution> entry : aaDistMap.entrySet()) {
            final double p;
            boolean ok = true;
            String key = entry.getKey();
            if (mode.contains("_") && key.endsWith(mode)) {
                p = entry.getValue().density(ppm1);
            }  else if (mode.equals("") && !key.contains("_")) {
                p = entry.getValue().density(ppms);
            } else {
                p = 0.0;
                ok = false;
            }
            if (ok) {
                String resName;
                int underPos = key.indexOf("_");
                if (underPos != -1) {
                    resName = key.substring(0, underPos);
                } else {
                    resName = key;
                }
                AAScore score = new AAScore(resName, p);
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

        if (!atomName.isEmpty() && !(resName.equalsIgnoreCase("gly")
                && atomName.equalsIgnoreCase("cb"))) {
            Atom atom = residue.getAtomLoose(atomName);
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
        return result;
    }

    private static Optional<Double> getPScore(int nValues, double resScore, double pOK) {
        double pValue;
        Optional<Double> result = Optional.empty();
        if (nValues > 0) {
            ChiSquaredDistribution chiSquare = new ChiSquaredDistribution(nValues);
            pValue = 1.0 - chiSquare.cumulativeProbability(resScore);
            if (!Double.isNaN(pValue) && (pValue > pOK)) {
                result = Optional.of(pValue);
            }
        }
        return result;
    }

    public static PPMScore scoreAtomPPM(final double pOK, final double sdevMul, final Residue residue, final List<AtomShiftValue> atomShiftValues) {
        PPMScore matchScore = new PPMScore(atomShiftValues);

        double resScore = 0.0;
        int nValues = 0;
        for (AtomShiftValue atomShiftValue : atomShiftValues) {
            String atomName = atomShiftValue.getAName();
            if (atomName.equalsIgnoreCase("H")  && (residue.getAtom(atomShiftValue.getAName()) == null)) { // fail residues like proline without HN
                nValues = 0;
                break;
            }
            Double score = scoreAtomPPM(residue, atomShiftValue.getAName(), atomShiftValue.getPPM(), sdevMul);
            if (score != null) {
                resScore += score;
                nValues++;
            }
        }
        Optional<Double> scoreOpt = getPScore(nValues, resScore, pOK);
        if (scoreOpt.isPresent()) {
            matchScore.ok = true;
            matchScore.totalScore = scoreOpt.get();
        } else {
            matchScore.ok = false;
            matchScore.totalScore = 0.0;
        }
        return matchScore;
    }

    public static Optional<Double> scoreResidueAtomPPM(final double pOK, final double sdevMul, final Residue rootResidue, final List<List<AtomShiftValue>> atomShiftValues) {
        double resScore = 0.0;
        int nValues = 0;

        for (int i = 0; i < 2; i++) {
            Residue residue = i == 0 ? rootResidue.getPrevious() : rootResidue;
            for (AtomShiftValue atomShiftValue : atomShiftValues.get(i)) {
                if (residue.getAtom(atomShiftValue.getAName()) == null) { // fail residues like proline without HN
                    nValues = 0;
                    break;
                }
                Double score = scoreAtomPPM(residue, atomShiftValue.getAName(), atomShiftValue.getPPM(), sdevMul);
                if (score != null) {
                    resScore += score;
                    nValues++;
                }
            }
        }

        return getPScore(nValues, resScore, pOK);
    }
}
