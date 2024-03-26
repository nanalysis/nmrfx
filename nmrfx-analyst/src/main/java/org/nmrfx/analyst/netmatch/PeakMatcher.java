package org.nmrfx.analyst.netmatch;

import io.jenetics.*;
import io.jenetics.engine.*;
import io.jenetics.internal.util.Requires;
import io.jenetics.util.ISeq;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.chemistry.*;
import org.nmrfx.processor.optimization.BipartiteMatcher;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.function.Function.identity;
import static io.jenetics.engine.EvolutionResult.toBestPhenotype;
import static io.jenetics.engine.Limits.bySteadyFitness;

/**
 *
 * @author brucejohnson
 */
public class PeakMatcher {

    static final double ERRMUL = 1.0;
    static final double weightPred = 1.0;
    static final double weightMulti = 2.0;
    int[][] bestMatches;
    int nEvaluations = 0;
    String rootDir = "";
    private int populationSize = 100;
    private double mutationRate = 0.01;
    private double crossoverRate = 0.1;
    private int steadyLimit = 30;
    private int multiMaxLimit = 30;
    private int nGenerations = 100;
    private boolean refineMulti = true;
    private boolean genStart = true;

    record GlobalScore(double fitness, double[] localScores) {

    }

    /**
         * Used to store an item number (for a peak or atom) and the probability of that item matching
         */
        record ScoreConstrained(int peak, int atom, double score,
                                int[] firstSetMatching) implements Comparable<ScoreConstrained> {

            ScoreConstrained(int peak, int atom, double score, int[] firstSetMatching) {
                this.atom = atom;
                this.peak = peak;
                this.score = score;
                this.firstSetMatching = firstSetMatching.clone();
            }

            @Override
            public int compareTo(ScoreConstrained o) {
                int result = 0;
                if (o == null) {
                    result = 1;
                } else if (!this.equals(o)) {
                    if (this.score > o.score) {
                        result = 1;
                    } else if (this.score < o.score) {
                        result = -1;
                    }
                }
                return result;
            }

            @Override
            public String toString() {
                return String.format("atom %d peak %d best %d score %.3f", atom, peak, firstSetMatching[atom], score);
            }
        }

    static class HistoryValue {

        private final int nEvaluations;
        private final int nBad;
        private final double fitness;
        private final double bestFitness;

        HistoryValue(int nEvaluations, int nBad, double fitness, double bestFitness) {
            this.nEvaluations = nEvaluations;
            this.nBad = nBad;
            this.fitness = fitness;
            this.bestFitness = bestFitness;
        }

        @Override
        public String toString() {
            return String.format("nEval %d nBad %d fitness %.4f best %.4f%n", nEvaluations, nBad, fitness, bestFitness);
        }

    }

    ArrayList<HistoryValue> history = new ArrayList<>();
    double[] tols = {2.0, 2.0};
    static HashMap<Atom, AtomShifts> atomIndexMap = new HashMap<>();
    static HashMap<String, PeakSets> peakSetsMap = new LinkedHashMap<>();
    PeakSets peakType;

    public void setDir(String dir) {
        rootDir = dir;
    }

    /**
     * @return the genStart
     */
    public boolean isGenStart() {
        return genStart;
    }

    /**
     * @param genStart the genStart to set
     */
    public void setGenStart(boolean genStart) {
        this.genStart = genStart;
    }

    /**
     * @return the refineMulti
     */
    public boolean isRefineMulti() {
        return refineMulti;
    }

    /**
     * @param refineMulti the refineMulti to set
     */
    public void setRefineMulti(boolean refineMulti) {
        this.refineMulti = refineMulti;
    }

    /**
     * @return the populationSize
     */
    public int getPopulationSize() {
        return populationSize;
    }

    /**
     * @param populationSize the populationSize to set
     */
    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    /**
     * @return the mutationRate
     */
    public double getMutationRate() {
        return mutationRate;
    }

    /**
     * @param mutationRate the mutationRate to set
     */
    public void setMutationRate(double mutationRate) {
        this.mutationRate = mutationRate;
    }

    /**
     * @return the crossoverRate
     */
    public double getCrossoverRate() {
        return crossoverRate;
    }

    /**
     * @param crossoverRate the crossoverRate to set
     */
    public void setCrossoverRate(double crossoverRate) {
        this.crossoverRate = crossoverRate;
    }

    /**
     * @return the steadyLimit
     */
    public int getSteadyLimit() {
        return steadyLimit;
    }

    /**
     * @param steadyLimit the steadyLimit to set
     */
    public void setSteadyLimit(int steadyLimit) {
        this.steadyLimit = steadyLimit;
    }

    /**
     * @return the multiMaxLimit
     */
    public int getMultiMaxLimit() {
        return multiMaxLimit;
    }

    /**
     * @param multiMaxLimit the multiMaxLimit to set
     */
    public void setMultiMaxLimit(int multiMaxLimit) {
        this.multiMaxLimit = multiMaxLimit;
    }

    /**
     * @return the nGenerations
     */
    public int getNGenerations() {
        return nGenerations;
    }

    public static void reset() {
        atomIndexMap.clear();
        peakSetsMap.clear();
    }

    /**
     * Get new list of AtomShifts object
     *
     * @return a copy of the AtomShifts list
     */
     static Map<Atom, AtomShifts> getAtomShiftList() {
        Map<Atom, AtomShifts> newList = new HashMap<>();
        for (var entry : atomIndexMap.entrySet()) {
            newList.put(entry.getKey(), entry.getValue().copy());
        }
        return newList;
    }

    /**
     * Calculate a global score for the current matches based on the weighted sum of the QPred and QMulti scores.
     *
     * @param dump If set then output print statistics to system.out
     * @return The global score
     */
    public static GlobalScore globalScore(Map<Atom, AtomShifts> aShifts, boolean dump) {
        return globalScore(aShifts, dump, "scores.txt");
    }

    public static GlobalScore globalScore(Map<Atom, AtomShifts> aShifts, boolean dump, String fileName) {
        double scoreSum = 0.0;
        double normSum = 0.0;
        double score = 0.0;
        PrintWriter out = null;
        double[] localScores = null;
        try {
            if (dump) {
                FileWriter outFile = new FileWriter(fileName);
                out = new PrintWriter(outFile);
            }

            if (out != null) {
                out.printf("%10s %3s %8s %8s %8s %8s %8s %8s %8s\n", "Atom", "nVa", "predppm", "avgppm", "stdev", "delta", "QPred", "QMulti", "qTotal");
            }
            //fixme  works for first set only
            PeakSets firstSet = getFirstSet();
            double[] norms = new double[firstSet.getAtoms().size()];
            double[] scores = new double[firstSet.getAtoms().size()];
            localScores = new double[scores.length];
            for (AtomShifts atomShifts : aShifts.values()) {
                long nValues = atomShifts.stats.getN();
                double qTotal;
                double norm = weightPred + weightMulti * nValues;

                if (nValues == 0) {
                    // fixme should there be a penalty for no assignment, here we set it to -1.0
                    qTotal = -1.0;
                    if (out != null) {
                        out.printf("%10s %3d %8.3f\n", atomShifts.getAtomName(), nValues, atomShifts.getPPM());
                    }
                } else {
                    // Calculate a value based on how similar the average shift of this object is to the predicted value
                    double QPred;
                    // fixme
                    if (atomShifts.getPPM() < -990.0) {
                        QPred = -10;
                    } else {
                        QPred = atomShifts.getQPred(atomShifts.getPPM());
                    }
                    // Calculate a value based on how similar the assigned shifts are to each other
                    double QMulti = atomShifts.getQMultiSum();
                    // A weighted sum of the above two values
                    qTotal = weightPred * QPred + weightMulti * QMulti;
                    if (Double.isInfinite(qTotal)) {
                        System.out.println("XXX " + QPred + " " + QMulti);
                    }
                    double delta = atomShifts.stats.getMax() - atomShifts.stats.getMin();
                    double stdev = atomShifts.stats.getStandardDeviation();
                    if (out != null) {
                        out.printf("%10s %3d %8.3f %8.3f %8.3f %8.3f %8.3f %8.3f %8.3f\n",
                                atomShifts.getAtomName(), nValues, atomShifts.getPredictedShift(), atomShifts.getPPM(),
                                stdev, delta, QPred, (QMulti / nValues), qTotal / norm);
                    }
                    for (PeakSetAtom peakSetAtom : atomShifts.peakSetAtoms) {
                        AtomValue aValue = peakSetAtom.peakSets.valuesAtom.get(peakSetAtom.atom);
                        norms[aValue.getIndex()] += norm;
                        scores[aValue.getIndex()] += qTotal;
                    }
                }
                scoreSum += qTotal;
                normSum += norm;
            }
            score = scoreSum / normSum;
            if (Double.isInfinite(score)) {
                System.out.println("XXX " + scoreSum + " " + normSum);
            }
            if (out != null) {
                out.println("score: " + score);
                out.close();
            }
            for (int i = 0; i < scores.length; i++) {
                if (norms[i] != 0.0) {
                    localScores[i] = scores[i] / norms[i];
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new GlobalScore(score, localScores);
    }

    /**
     * Load a file containing atom names and predicted chemical shifts and prediction errors Name Shift Error
     *
     * @param fileName The file to read.
     */
    public static void processPPMFile(String fileName) {
        File file = new File(fileName);
        try {
            Scanner in = new Scanner(file);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                String[] fields = line.split(" ");
                String atomName = fields[0];
                Atom atom = MoleculeBase.getAtomByName(atomName);
                double value = Double.parseDouble(fields[1]);
                double sigma = Double.parseDouble(fields[2]);
                if (atom.getRefPPM() == null) {
                    System.out.println("null " + atom.getShortName());
                    atom.setRefPPM(value);
                    atom.setRefError(sigma);
                } else {
                    if ((Math.abs(atom.getRefPPM() - value) / value) > 0.05) {
                        System.out.println(atom.getShortName() + " " + atom.getRefPPM() + " " + atom.getSDevRefPPM() + " " + value + " " + sigma);
                    }
                    if ((Math.abs(atom.getSDevRefPPM() - sigma) / sigma) > 0.1) {
                        System.out.println(atom.getShortName() + " " + atom.getRefPPM() + " " + atom.getSDevRefPPM() + " " + value + " " + sigma);
                    }
                }
               // atom.setRefPPM(value);
                atom.setRefError(sigma);
                AtomShifts atomShifts = new AtomShifts(atom);
                atomIndexMap.put(atom, atomShifts);
            }
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }
    }

    public static void processPPMs() {
        var mol = MoleculeFactory.getActive();
        for (var atom:mol.getAtomArray()) {
            AtomShifts atomShifts = new AtomShifts(atom);
            atomIndexMap.put(atom, atomShifts);
        }

    }

    public static PeakSets getFirstSet() {
        for (PeakSets peakSets : peakSetsMap.values()) {
            return peakSets;
        }
        return null;

    }

    public double getValue(List list) {
        int[] matching = new int[list.size()];
        int i = 0;
        for (Object o : list) {
            matching[i++] = (Integer) o;
        }
        return getValue(matching);
    }

    public static double getValue(int[] matching) {
//        System.out.println("getvalue " + matching.length);
        Map<Atom, AtomShifts> aShifts = getAtomShiftList();
        PeakSets peakSets = getFirstSet();

        assignMatches(peakSets, aShifts, false, matching);
        GlobalScore gScore = globalScore(aShifts, false);
        return gScore.fitness;
    }

    public void dumpHistory() {
        for (HistoryValue value : history) {
            System.out.println(value.toString());
        }
    }

    /**
     *
     * @param fileName
     */
    public void processFile(String fileName) {
        File file = new File(fileName);
        try {
            Scanner in = new Scanner(file);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                processLine(line);
            }
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }
    }

    /**
     * Return the number of peaks for the specified list type.
     *
     * @param type The type of list to get the peak count for.
     * @return The number of peaks.
     */
    public int nPeaks(String type) {
        PeakSets peakSets = peakSetsMap.get(type);
        List<PeakValue> valuesPeak = peakSets.getPeaks();
        return valuesPeak.size();
    }

    /**
     * Return the number of peaks for the specified list type.
     *
     * @param type The type of list to get the peak count for.
     * @return The number of peaks.
     */
    public int nAtoms(String type) {
        PeakSets peakSets = peakSetsMap.get(type);
        List<AtomValue> valuesAtoms = peakSets.getAtoms();
        return valuesAtoms.size();
    }

    /**
     * Perform a bipartite match of peaks in the specified type of peak list with the predicted peaks for that type.
     * Weighting is based on a comparison of the measured position of the peaks with the value associated with the the
     * atom type. The latter will be a predicted value at first, but if an earlier processType is done it might be
     * updated with the average of measured values from the previous matching. The matching from the Bipartite matcher
     * is stored in the PeakSets object.
     *
     * @param type The peak list type to be processed
     */
    public void processType(String type, List<Integer> correct) {
        PeakSets peakSets = peakSetsMap.get(type);
        List<PeakValue> valuesPeak = peakSets.getPeaks();
        List<AtomValue> valuesAtom = peakSets.getAtoms();

        int nPeaks = valuesPeak.size();
        int nAtoms = valuesAtom.size();
        int nExtraPeaks = 0;
        int nExtraAtoms = 0;
        if (nAtoms > nPeaks) {
            nExtraPeaks = nAtoms;
        } else {
            nExtraPeaks = nAtoms;
        }

        for (int i = 0; i < nExtraAtoms; i++) {
            int index = valuesAtom.size();
            Atom[] atoms = new Atom[0];
            valuesAtom.add(new AtomValue(index, atoms, this));
        }
        for (int i = 0; i < nExtraPeaks; i++) {
            int index = valuesPeak.size();
            Double[] values = new Double[0];
            valuesPeak.add(new PeakValue(index, values, values));
        }
        int nOrigPeaks = nPeaks;
        int nOrigAtoms = nAtoms;
        peakType.setNPeaks(nPeaks);
        peakType.setNAtoms(nAtoms);
        nPeaks = valuesPeak.size();
        nAtoms = valuesAtom.size();
        System.out.println("nOrigPeaks " + nOrigPeaks + " nPeaks " + nPeaks + " nOrigAtoms " + nOrigAtoms + " nAtoms " + nAtoms);

        peakSets.atomMatches.clear();
        peakSets.peakMatches.clear();
        for (int jPeak = 0; jPeak < nPeaks; jPeak++) {
            ArrayList<ItemMatch> itemMatch = new ArrayList<>();
            peakSets.atomMatches.add(itemMatch);
        }

        // Calculate the probability that the shifts of a group of atoms match the shifts of a peak
        //  Also, store a list of peaks that could match (have probability > 0.0) each atom
        //        and the atoms that could match each peak
        valuesAtom.forEach(atomValue -> {
            ArrayList<ItemMatch> atomPeakMatch = new ArrayList<>();
            peakSets.peakMatches.add(atomPeakMatch);
            valuesPeak.forEach(peakValue -> {
                double probability = 0.0;
                int iAtom = atomValue.getIndex();
                int jPeak = peakValue.getIndex();
                int extraAtom = iAtom - nOrigAtoms;
                int extraPeak = jPeak - nOrigPeaks;

                if ((extraAtom >= 0) || (extraPeak >= 0)) {
                    if ((iAtom == extraPeak) || (jPeak == extraAtom) || (extraPeak == extraAtom)) {
                        // System.out.println("add ext " + iAtom + " " + extraAtom + " " + extraPeak);
                        probability = 1.01e-6;
                    }
                } else {
                    probability = peakValue.getProbability(atomValue);
                }

                if (probability > 1.0e-6) {
                    ItemMatch peakMatch = new ItemMatch(peakValue.getIndex(), probability);
                    peakMatch.setGroupProbability(probability);
                    atomPeakMatch.add(peakMatch);
                    ItemMatch atomMatch = new ItemMatch(atomValue.getIndex(), probability);
                    atomMatch.setGroupProbability(probability);
                    peakSets.atomMatches.get(peakValue.getIndex()).add(atomMatch);
                }
                atomPeakMatch.sort(Comparator.comparingDouble(ItemMatch::getGroupProbability).reversed());
            });
        });
        dumpPeakMatchList("junk.txt", correct);
        List<List<ItemMatch>> peakMatchList = peakSets.peakMatches;
        for (int iAtom = 0; iAtom < nOrigAtoms; iAtom++) {
            List<ItemMatch> peakMatchPrev = null;
            if (iAtom > 1) {
                peakMatchPrev = peakMatchList.get(iAtom - 1);
            }
            List<ItemMatch> peakMatchSuc = null;
            if (iAtom < nAtoms - 1) {
                peakMatchSuc = peakMatchList.get(iAtom + 1);
            }
            List<ItemMatch> peakMatches = peakMatchList.get(iAtom);
            // fixme  need to get this from input file
            // used for checking if a fragment (several clusters) is possible
            int[][] map = null;

            List<ItemMatch> goodMatches = new ArrayList<>();
            for (ItemMatch itemMatch : peakMatches) {
                int thisPeakNum = itemMatch.itemNum;
                if (thisPeakNum >= nOrigPeaks) {
                    itemMatch.setGroupProbability(itemMatch.probability);
                    //System.out.println("skip " + iAtom + " " + thisPeakNum);
                    //goodMatches.add(itemMatch);
                    continue;
                }
                PeakValue peakValue = valuesPeak.get(thisPeakNum);
                int nPrev = 0;
                if (peakMatchPrev != null) {
                    for (ItemMatch itemMatchPrev : peakMatchPrev) {
                        int thatPeakNum = itemMatchPrev.itemNum;
                        if (thatPeakNum >= nOrigPeaks) {
                            continue;
                        }

                        PeakValue peakValuePrev = valuesPeak.get(thatPeakNum);
                        if (map != null) {
                            double overlap = peakValue.overlap(peakValuePrev, map);
                            if (overlap >= 0.0) {
                                nPrev++;
                            }
                        }
                    }
                } else {

                }
                int nSuc = 0;
                if (peakMatchSuc != null) {
                    for (ItemMatch itemMatchSuc : peakMatchSuc) {
                        int thatPeakNum = itemMatchSuc.itemNum;
                        if (thatPeakNum >= nOrigPeaks) {
                            continue;
                        }

                        PeakValue peakValueSuc = valuesPeak.get(thatPeakNum);
                        if (map != null) {
                            double overlap = peakValueSuc.overlap(peakValue, map);
                            if (overlap >= 0.0) {
//                            System.out.println(thatPeakNum + " s " + overlap);
                                nSuc++;
                            }
                        }
                    }
                }
                if ((nPrev > 0) && (nSuc > 0)) {
//                    System.out.printf("residue %5d cluster %5d %.5f\n", iAtom, itemMatch.itemNum, itemMatch.probability);
                    itemMatch.setGroupProbability(itemMatch.probability + 1);
                    goodMatches.add(itemMatch);
                } else if ((iAtom == 1) && (nSuc > 0)) {
//                    System.out.printf("residue %5d cluster %5d %.5f\n", iAtom, itemMatch.itemNum, itemMatch.probability);
                    itemMatch.setGroupProbability(itemMatch.probability + 2.0);
                    goodMatches.add(itemMatch);
                } else if (nSuc > 0) {
//                    System.out.printf("residue %5d cluster %5d %.5f\n", iAtom, itemMatch.itemNum, itemMatch.probability);
                    itemMatch.setGroupProbability(itemMatch.probability + 1.0);
                    goodMatches.add(itemMatch);
                } else if (nPrev > 0) {
//                    System.out.printf("residue %5d cluster %5d %.5f\n", iAtom, itemMatch.itemNum, itemMatch.probability);
                    itemMatch.setGroupProbability(itemMatch.probability + 1.0);
                    goodMatches.add(itemMatch);
                } else {
//                    System.out.printf("residue %5d cluster %5d %.9f %d %d\n", iAtom, itemMatch.itemNum, itemMatch.probability, nPrev, nSuc);
                    itemMatch.setGroupProbability(itemMatch.probability);
                }
            }
            if (!goodMatches.isEmpty()) {
                System.out.println("setemp");
                peakMatchList.set(iAtom, goodMatches);
            }
        }

    }

    public void dumpPeakMatchList(String fileName, List<Integer> correct) {
        PeakSets peakSets = getFirstSet();
        List<List<ItemMatch>> peakMatchList = peakSets.peakMatches;
        List<AtomValue> valuesAtom = peakSets.getAtoms();

        int nAtoms = valuesAtom.size();
        try (PrintWriter out = new PrintWriter(new FileWriter(fileName))) {
            for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
                out.printf("%5d", iAtom);
                int correctValue = correct != null ? correct.get(iAtom) : -2;
                out.printf("%5d", correctValue);
                List<ItemMatch> peakMatches = peakMatchList.get(iAtom);
                for (ItemMatch itemMatch : peakMatches) {
                    out.printf(" %5d %.5f %.5f", itemMatch.itemNum, itemMatch.probability, itemMatch.getGroupProbability());
                }
                out.println("");
            }
        } catch (IOException ioE) {

        }

    }

    public static int[] doBipartiteMatch(String type, int[] require) {
        PeakSets peakSets = peakSetsMap.get(type);
        List<PeakValue> valuesPeak = peakSets.getPeaks();
        List<AtomValue> valuesAtom = peakSets.getAtoms();
        BipartiteMatcher matcher = new BipartiteMatcher();

        int nPeaks = valuesPeak.size();
        int nAtoms = valuesAtom.size();
//        System.out.println("nat " + nAtoms + " npeak " + nPeaks);
        boolean maxOpts = false;
        if (maxOpts) {
            int nTotal = nAtoms + nPeaks;
            matcher.reset(nTotal, true);
            // should we allow duplicate peaks for overlap
            // fixme should we add reciprocol match
            for (int i = 0; i < nPeaks; i++) {
                matcher.setWeight(i, nAtoms + i, -1.0);
                matcher.setWeight(nAtoms + i, i, -1.0);
            }
            for (int j = 0; j < nAtoms; j++) {
                matcher.setWeight(nPeaks + j, j, -1.0);
                matcher.setWeight(j, nPeaks + j, -1.0);
            }
        } else {
            int nTotal = Math.max(nAtoms, nPeaks);
            matcher.reset(nTotal, true);
//            int nTotal = nAtoms;
//            int nExtra = nTotal - nPeaks;
//            matcher.reset(nTotal, true);
//            for (int j = 0; j < nAtoms; j++) {
//                for (int i = 0; i < nExtra; i++) {
//                    matcher.setWeight(j, nPeaks + i, -1.0);
//                    matcher.setWeight(nPeaks + i, j, -1.0);
//                }
//            }

        }
        int[] bestMatch = new int[nAtoms];

        for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
            bestMatch[iAtom] = -1;
            int nMatch = 0;
            List<ItemMatch> atomPeakMatch = peakSets.peakMatches.get(iAtom);
            for (ItemMatch peakAtomMatch : atomPeakMatch) {
                double probability = peakAtomMatch.getGroupProbability();
                int jPeak = peakAtomMatch.itemNum;
                //System.out.println("atom " + iAtom + " peak " + jPeak + " prob " + probability);
                if (probability > 0.0) {
                    if ((require != null) && require[iAtom] == jPeak) {
                        matcher.setWeight(iAtom, jPeak, 10.0);
//                        System.out.println(iAtom + " " + jPeak);
                    } else if ((require == null) || (require[iAtom] == -1) || (require[iAtom] == jPeak)) {
                        matcher.setWeight(iAtom, jPeak, probability);
                        nMatch++;
                    }
                }
            }
            if (nMatch < 2) {
                bestMatch[iAtom] = -1;
            }
        }
        // call the bipartite matcher and get optimal matching of peaks with atoms
        int[] tempMatching = matcher.getMatching();
        int[] matching = new int[nAtoms];
        System.arraycopy(tempMatching, 0, matching, 0, nAtoms);
        // store this matching in the peakSet
        peakSets.setMatching(matching);

        return matching;
    }

    /**
     * Generate a list of matchings by performing multiple bipartite matches of peaks with the predicted peaks. Each
     * matching is done after requiring that one potential peak-atom pair is included. Only pairs that aren't in the
     * original match are tested. This ensures that we get a score with each possible peak-atom pair tested at least
     * once.
     */
    public static List<ScoreConstrained> multiMatch(int[] bestMatching, String typeName) {
        List<List<ItemMatch>> firstMatches = null;
        List<ScoreConstrained> multiMatchList = new ArrayList<>();

        multiMatchList.clear();
        PeakSets firstSet = null;
        int nAtoms = 0;
        for (PeakSets peakSets : peakSetsMap.values()) {
            firstMatches = peakSets.atomMatches;
            nAtoms = peakSets.peakMatches.size();
            firstSet = peakSets;
            break;
        }
        // make an array of peak to atom matches
        int[][] matchPeaks = new int[firstMatches.size()][];
        int iPeak = 0;
        for (List<ItemMatch> matches : firstMatches) {
            matchPeaks[iPeak] = new int[matches.size()];
            int j = 0;
            for (ItemMatch itemMatch : matches) {
                int jAtom = itemMatch.itemNum;
                matchPeaks[iPeak][j++] = jAtom;
            }
            iPeak++;
        }
        for (iPeak = 0; iPeak < matchPeaks.length; iPeak++) {
            // only do a bipartite match if there is more than one possible atom match
            // for this peak
            if (matchPeaks[iPeak].length > 1) {
                for (int jAtom : matchPeaks[iPeak]) {
                    // if this peak - atom pair was present in original bipartite match
                    //     skip over it.  We only want to try peak - atom pairs that
                    //     weren't present
                    if (bestMatching[jAtom] == iPeak) {
                        continue;
                    }
                    // turn off peak-atom requirements for all atoms
                    int[] require = new int[nAtoms];
                    Arrays.fill(require, -1);
                    // turn on requirement for one peak-atom pair
                    require[jAtom] = iPeak;
                    // at present, we're only using a single match type so we'll
                    // only do one bipartite match

                    int[] matching = doBipartiteMatch(typeName, require);
                    boolean badMatch = false;
                    for (int iM : matching) {
                        if (iM == -1) {
                            badMatch = true;
                            break;
                        }
                    }
                    if (badMatch) {
                        continue;
                    }
                    Map<Atom, AtomShifts> aShifts = getAtomShiftList();
                    PeakSets peakSets = getFirstSet();
                    assignMatches(peakSets, aShifts, false, matching);
                    GlobalScore gScore = globalScore(aShifts, false);

                    multiMatchList.add(new ScoreConstrained(iPeak, jAtom, gScore.fitness, matching));
                }
            }
        }
        Collections.sort(multiMatchList, Collections.reverseOrder());
        return multiMatchList;
    }

    /**
     * Refine the results of the multimatch by s
     *
     */
    public static void refineMultiMatch(String typeName, List<ScoreConstrained> multiMatchList) {
        List<List<ItemMatch>> firstMatches = null;
        int[] bestMatching = null;
        int nAtoms = 0;
        int nPeaks = 0;
        for (PeakSets peakSets : peakSetsMap.values()) {
            firstMatches = peakSets.atomMatches;
            bestMatching = peakSets.bestMatching;
            nAtoms = peakSets.peakMatches.size();
            nPeaks = peakSets.atomMatches.size();
            break;
        }
        boolean[] usedAtoms = new boolean[nAtoms];
        boolean[] usedPeaks = new boolean[nPeaks];
        HashSet<String> skipPeakAtoms = new HashSet<>();
        int nScores = multiMatchList.size();
        int nTries = nScores / 4;
        double maxScore = 0.0;
        int[] bestRefined = null;
        int iPeakBest = 0;
        int jAtomBest = 0;

        for (int iTry = 0; iTry < nTries; iTry++) {
            int[] require = new int[nAtoms];
            Arrays.fill(require, -1);
            Arrays.fill(usedAtoms, false);
            Arrays.fill(usedPeaks, false);
            int nUsed = 0;
            int j = 0;
            int iPeak = 0;
            int jAtom = 0;
            while ((nUsed <= iTry) && (j < multiMatchList.size())) {
                ScoreConstrained scoreConstrained = multiMatchList.get(j++);
                iPeak = scoreConstrained.peak;
                jAtom = scoreConstrained.atom;
                if (usedPeaks[iPeak] || usedAtoms[jAtom]) {
                    continue;
                }
                if (skipPeakAtoms.contains(iPeak + "." + jAtom)) {
                    continue;
                }
                usedPeaks[iPeak] = true;
                usedAtoms[jAtom] = true;
//                System.out.println(jAtom + " " + iPeak);
                require[jAtom] = iPeak;
                nUsed++;
            }
            if (j >= multiMatchList.size()) {
                break;
            }

            int[] matching = doBipartiteMatch(typeName, require);
            Map<Atom, AtomShifts> aShifts = getAtomShiftList();
            PeakSets peakSets = getFirstSet();
            assignMatches(peakSets, aShifts, false, matching);
            GlobalScore gScore = globalScore(aShifts, false);
            double score = gScore.fitness;

            if (score > maxScore) {
                maxScore = score;
                bestRefined = matching.clone();
                iPeakBest = iPeak;
                jAtomBest = jAtom;
//                System.out.printf("gscore2 try %d atom %d peak %d nUsed %d score %.3f\n", iTry, jAtom, iPeak, nUsed, score);
            } else {
                skipPeakAtoms.add(iPeak + "." + jAtom);
                //System.out.println("gscore2 skip " + score);
                iTry--;
            }

        }
        if (bestRefined != null) {
            System.out.println("refined " + maxScore);
            multiMatchList.add(new ScoreConstrained(iPeakBest, jAtomBest, maxScore, bestRefined));
        }

    }

    static void assignMatches(PeakSets peakSets, Map<Atom,AtomShifts> aShifts, boolean dump, int[] matching) {
        assignMatches(peakSets, aShifts, dump, matching, "tempmatch.txt");
    }

    /**
     * Use an atom-peak matching to get a peak matched to a set of atoms and store the shifts in the matched peak with
     * the atoms in the group of atoms
     *
     * @param peakSets The set of peaks to be matched
     * @param dump Whether to output statistics about the matches
     * match modified from best set.
     */
    static void assignMatches(PeakSets peakSets, Map<Atom, AtomShifts> aShifts, boolean dump, int[] matching, String fileName) {
        List<AtomValue> valuesAtom = peakSets.getAtoms();
        List<PeakValue> valuesPeak = peakSets.getPeaks();
        int nAtoms = valuesAtom.size();
        String type = peakSets.type;
        PrintWriter out = null;
        try {
            if (dump) {
                FileWriter outFile = new FileWriter(fileName);
                out = new PrintWriter(outFile);
            }
            if (out != null) {
                out.printf("%s\t%4s\t%5s\t%4s\t%s\t%10s\t%s\n", "type", "iAtm", "clust", "prob", "atoms", "peak", "pkppms");
            }
            for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
                AtomValue atomValue = valuesAtom.get(iAtom);
                if (atomValue.getEmpty()) {
                    continue;
                }
                int jPeak = matching[iAtom];
                String pkname = "pkarti";
                String atomString = "";
                String peakString = "";
                int pkIndex = -1;
                int atomIndex = -1;
                double probability = peakSets.getProbability(iAtom, jPeak);
                if (iAtom < valuesAtom.size()) {
                    atomIndex = atomValue.getIndex();
                    atomString = atomValue.toString();
                    if ((jPeak >= 0) && (jPeak < valuesPeak.size())) {
                        PeakValue peakValue = valuesPeak.get(jPeak);
                        if (peakValue.values.length > 0) {
                            pkIndex = peakValue.getIndex();
                            peakString = peakValue.toString();
                            if (probability > 0.0) {
                                for (int iDim = 0; iDim < atomValue.size(); iDim++) {
                                    Atom kAtom = atomValue.getAtom(iDim);
                                    if (kAtom != null) {
                                        AtomShifts atomShifts = aShifts.get(kAtom);
                                        atomShifts.addPPM(new PeakSetAtom(peakSets, iAtom), peakValue.values[iDim], peakValue.tvalues[iDim]);
                                    }
                                }
                            }
                        }
                    }
                }
                if ((jPeak >= 0) && (jPeak < valuesPeak.size())) {
                    pkname = "pk" + jPeak;
                }
                if (out != null) {
                    out.printf("%s\t%4d\t%5d\t%4.5f\t%s\t%10s\t%s\n", type, atomIndex, pkIndex, probability, atomString, pkname, peakString);
                }
            }
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void processLine(String line) {
        String[] fields = line.split(" ");
        if (fields[0].equals("type")) {
            peakType = new PeakSets(fields[1]);
            peakSetsMap.put(fields[1], peakType);
        } else {
            String set = fields[0];
            Value value;
            if (fields[0].equals("Peak")) {
                int nDim = (fields.length - 2) / 2;
                Double[] dArray = new Double[nDim];
                Double[] tArray = new Double[nDim];
//                int index = Integer.parseInt(fields[1]);
                int index = peakType.valuesPeak.size();
                for (int i = 0; i < nDim; i++) {
                    dArray[i] = Double.parseDouble(fields[i * 2 + 2]);
                    tArray[i] = Double.parseDouble(fields[i * 2 + 3]);
                    if (tArray[i] < 0.05) {
                        tArray[i] = 0.05;
                    }
                }
                value = new PeakValue(index, dArray, tArray);
            } else {
                int nDim = fields.length - 2;
//                int index = Integer.parseInt(fields[1]);
                int index = peakType.valuesAtom.size();
                String[] sArray = new String[nDim];
                System.arraycopy(fields, 2, sArray, 0, nDim);
                Atom[] atoms = new Atom[sArray.length];
                for (int k=0;k<atoms.length;k++) {
                    atoms[k] = MoleculeBase.getAtomByName(sArray[k]);
                }
                value = new AtomValue(index, atoms, this);
            }
            if (set.equals("Peak")) {
                peakType.valuesPeak.add((PeakValue) value);
            } else {
                peakType.valuesAtom.add((AtomValue) value);
            }
        }
    }

    public void clearPeakSet(String type) {
        peakType = null;
        peakSetsMap.remove(type);
    }

    public void addPeakSet(String type) {
        peakType = new PeakSets(type);
        peakSetsMap.put(type, peakType);
    }

    public void addPeak(String type, String line) {
        PeakSets localPeakSet = peakSetsMap.get(type);
        String[] fields = line.split(" ");
        int nDim = (fields.length) / 2;
        Double[] dArray = new Double[nDim];
        Double[] tArray = new Double[nDim];
        int index = localPeakSet.valuesPeak.size();
        for (int i = 0; i < nDim; i++) {
            dArray[i] = Double.parseDouble(fields[i * 2]);
            tArray[i] = Double.parseDouble(fields[i * 2 + 1]);
            if (tArray[i] < 0.05) {
                tArray[i] = 0.05;
            }
        }
        Value value = new PeakValue(index, dArray, tArray);
        localPeakSet.valuesPeak.add((PeakValue) value);
    }

    public void addAtom(String type, String line) {
        PeakSets localPeakSet = peakSetsMap.get(type);
        String[] fields = line.split(" ");
        int nDim = fields.length;
        int index = localPeakSet.valuesAtom.size();
        String[] sArray = new String[nDim];
        System.arraycopy(fields, 0, sArray, 0, nDim);

        Atom[] atoms = new Atom[sArray.length];
        for (int k=0;k<atoms.length;k++) {
            atoms[k] = MoleculeBase.getAtomByName(sArray[k]);
        }

        AtomValue value = new AtomValue(index, atoms, this);
        localPeakSet.valuesAtom.add(value);
    }

    public void setupProtein() {
        System.out.println(peakSetsMap);
        PeakSets localPeakSet = peakSetsMap.get("rbclust");
        var mol = MoleculeFactory.getActive();
        String[] cNames = {"CA", "CB", "C"};
        int nAtoms = 2 + 2 * cNames.length;
        Atom[] atoms = new Atom[nAtoms];
        for (Polymer polymer : mol.getPolymers()) {
            for (Residue residue: polymer.getResidues()) {
                String hName = residue.previous == null ? hName = "H1" : "H";
                atoms[0] = residue.getAtom(hName);
                atoms[1] = residue.getAtom("N");
                int j = 2;
                for (String cName : cNames) {
                    atoms[j++] = residue.getAtom(cName);
                }
                if (residue.previous != null) {
                    for (String cName : cNames) {
                        atoms[j++] = residue.previous.getAtom(cName);
                    }
                }
                int index = localPeakSet.valuesAtom.size();
                AtomValue value = new AtomValue(index, atoms, this);
                localPeakSet.valuesAtom.add(value);
            }
        }
    }

    public void checkOverlaps(String type) {
        PeakSets peakSets = peakSetsMap.get(type);
        List<PeakValue> valuesPeak = peakSets.getPeaks();
    }

    private static void update(final EvolutionResult<EnumGene<Integer>, Double> result) {
        DescriptiveStatistics dStat = new DescriptiveStatistics();
        result.population().stream().forEach(pheno -> dStat.addValue(pheno.fitness()));
        System.out.printf("%5d %10.3f %10.3f %10.4f\n", result.generation(), dStat.getMin(), dStat.getMean(), dStat.getMax());
    }

    public Future<Double> doAllAsync(String typeName, int nGenerations) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        new Thread(() -> {
            double fitness = doAllJ(typeName, nGenerations);
            future.complete(fitness);

        }).start();
        return future;
    }

    public static InvertibleCodec<int[], EnumGene<Integer>>
    ofPermutation(final int length) {
        Requires.positive(length);

        final AssignmentChromosome<Integer> chromosome =
                AssignmentChromosome.ofInteger(length);

        final Map<Integer, EnumGene<Integer>> genes = chromosome.stream()
                .collect(Collectors.toMap(EnumGene::allele, identity()));

        return InvertibleCodec.of(
                Genotype.of(chromosome),
                gt -> {
                    return gt.chromosome().stream()
                            .mapToInt(EnumGene::allele)
                            .toArray();
                },
                val -> {
                    return Genotype.of(
                            new AssignmentChromosome<>(
                                    IntStream.of(val)
                                            .mapToObj(genes::get)
                                            .collect(ISeq.toISeq())
                            )
                    );
                }
        );
    }

    public double doAllJ(String typeName, int nGenerations) {
        this.nGenerations = nGenerations;
        String peakMatchFilename = Paths.get(rootDir, "peakmatch.txt").toString();
        String iMatchFilename = Paths.get(rootDir, "initialmatch.txt").toString();
        String iScoreFilename = Paths.get(rootDir, "initialscore.txt").toString();
        String fMatchFilename = Paths.get(rootDir, "finalmatch.txt").toString();
        String fScoreFilename = Paths.get(rootDir, "finalscore.txt").toString();
        dumpPeakMatchList(peakMatchFilename, null);
        int[] firstMatching = doBipartiteMatch(typeName, null);
        for (int iv : firstMatching) {
            System.out.print(iv + " ");
        }
        System.out.println();
        final int stops = firstMatching.length;
        Map<Atom, AtomShifts> aShifts = getAtomShiftList();
        PeakSets peakSets = getFirstSet();
        assignMatches(peakSets, aShifts, true, firstMatching, iMatchFilename);
        var mol = MoleculeFactory.getActive();
        for (var atm:mol.getAtomArray()) {
            System.out.println(atm.getFullName() + " " + atm.getRefPPM() + " " + atm.getSDevRefPPM());
        }
        GlobalScore gScore = globalScore(aShifts, true, iScoreFilename);
        double fitness = gScore.fitness;
        System.out.println("first bp value " + fitness);
        //globalScore(false);
        int eliteNumber = 100;
        int maximumPhenoTypeAge = 50;
        final Engine<EnumGene<Integer>, Double> engine = Engine
                .builder(
                        PeakMatcher::getValue,
                        PeakMatcher.ofPermutation(stops)
                )
                .optimize(Optimize.MAXIMUM)
                .maximalPhenotypeAge(maximumPhenoTypeAge)
                .populationSize(populationSize)
                .survivorsSelector(new EliteSelector<>(eliteNumber))
                .offspringSelector(new TournamentSelector<>())
                               // .constraint(MatchValidator::isValid)
                .alterers(
                        new ConstrainedSwapMutator<>(this, peakSets, mutationRate),
                        new PartiallyMatchedCrossover<>(crossoverRate))
                .build();
//        List<AssignmentChromosome<EnumGene<Integer>>> genotypes = new ArrayList<>();

        List<Genotype<EnumGene<Integer>>> genotypes = new ArrayList<>();

        if (genStart) {
            List<ScoreConstrained> multiMatchList = multiMatch(firstMatching, typeName);
            if (refineMulti) {
                refineMultiMatch(typeName, multiMatchList);
            }
            multiMatchList.add(new ScoreConstrained(-1, -1, gScore.fitness, firstMatching));
            multiMatchList.sort(Collections.reverseOrder());
            int nMulti = Math.min(multiMaxLimit, multiMatchList.size());
            System.out.println(" nMultiMatches " + multiMatchList.size() + " multiLimit " + multiMaxLimit + " nMulti " + nMulti);
            for (ScoreConstrained sConstrained : multiMatchList.subList(0, nMulti)) {
                System.out.println("multi value " + sConstrained.score);
                ArrayList<Integer> matchArray = new ArrayList<>();
                for (int i : sConstrained.firstSetMatching) {
                    matchArray.add(i);
                }
                final ISeq<Integer> alleles = ISeq.of(matchArray);
                System.out.println(alleles);
                AssignmentChromosome<EnumGene<Integer>> permCh = new AssignmentChromosome(IntStream.range(0, firstMatching.length).mapToObj(i -> EnumGene.of(i, alleles)).collect(ISeq.toISeq()));
                Genotype gtype = Genotype.of(permCh);
                genotypes.add(gtype);
            }
        }
        System.out.println("run " + genotypes.size());
        // Create evolution statistics consumer.
        final EvolutionStatistics<Double, ?> statistics = EvolutionStatistics.ofNumber();
        try {
            final Phenotype<EnumGene<Integer>, Double> best
                    = engine.stream(genotypes)
                            // Truncate the evolution stream after n "steady"
                            // generations.
                            .limit(bySteadyFitness(steadyLimit))
                            // The evolution will stop after maximal n
                            // generations.
                            .limit(nGenerations)
                            // Update the evaluation statistics after
                            // each generation
                            .peek(statistics)
                            .peek(PeakMatcher::update)
                            // Collect (reduce) the evolution stream to
                            // its best phenotype.
                            .collect(toBestPhenotype());

            System.out.println(statistics);
            System.out.println(best);
            int[] finalMatching = Codecs.ofPermutation(stops).decoder().apply(best.genotype());
            aShifts = getAtomShiftList();

            assignMatches(peakSets, aShifts, true, finalMatching, fMatchFilename);

            gScore = globalScore(aShifts, true, fScoreFilename);
            fitness = gScore.fitness;
            System.out.println("final value " + fitness);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return fitness;

    }
    public static int[] newValid(int size) {
        PeakSets peakSets = getFirstSet();
        List<PeakValue> valuesPeak = peakSets.getPeaks();
        List<AtomValue> valuesAtom = peakSets.getAtoms();

        int nPeaks = valuesPeak.size();
        int nAtoms = valuesAtom.size();
        List<List<ItemMatch>> peakMatches = peakSets.peakMatches;
        List<Integer> elements = new ArrayList<>(size);
        for (int i=0;i<size;i++) {
            elements.add(i);
        }
        int[] result = new int[size];
        boolean[] used = new boolean[peakSets.atomMatches.size()];
        Collections.shuffle(elements);
        for (int i=0;i<size;i++) {
            int index =elements.get(i);
            List<ItemMatch> itemMatches = peakMatches.get(index);
            int iPeak = -1;
            if (itemMatches.size() == 1) {
                iPeak = itemMatches.get(0).itemNum;
            } else {
                List<ItemMatch> sortedMatches = new ArrayList<>(itemMatches);
                Collections.shuffle(sortedMatches);
                for (int k=0;k<sortedMatches.size();k++) {
                    int testPeak = itemMatches.get(k).itemNum;
                    if (!used[testPeak]) {
                        iPeak = testPeak;
                        break;
                    }
                }
            }
            result[i] = iPeak;
            if (iPeak != -1) {
                used[iPeak] = true;
            }
        }
        return result;
    }

}
