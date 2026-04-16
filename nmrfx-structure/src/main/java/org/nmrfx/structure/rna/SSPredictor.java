package org.nmrfx.structure.rna;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.MatchingAlgorithm;
import org.jgrapht.alg.matching.MaximumWeightBipartiteMatching;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Result;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.NdArrays;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SSPredictor {
    private static final Logger log = LoggerFactory.getLogger(SSPredictor.class);

    static final Random random = new Random();
    public static final Set<String> validBPs = Set.of("GC", "CG", "AU", "UA", "GU", "UG");
    double[][] savedPredictions;
    double[][] predictions;
    Map<BPKey, BPRegion> regionsMap = new HashMap<>();
    List<BPRegion> regionsList = new ArrayList<>();
    boolean predictPseudoKnots = true;
    boolean requireCanonical = true;
    double graphThreshold = 0.7;

    ParitionedGraph paritionedGraph = null;
    Set<BasePairProbability> extentBasePairs;
    Map<BPKey, BasePairProbability> allBasePairs;

    List<BasePairsMatching> extentBasePairsList = new ArrayList<>();
    String rnaSequence;
    int delta = 4;

    static SavedModelBundle graphModel;
    static String modelFilePath = null;

    public record BasePairsMatching(double value, Set<BasePairProbability> basePairsSet)
            implements Comparable<BasePairsMatching> {
        boolean exists(List<BasePairsMatching> extentBasePairsList) {
            for (BasePairsMatching basePairsMatching : extentBasePairsList) {
                if (basePairsMatching.basePairsSet.equals(basePairsSet)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int compareTo(BasePairsMatching other) {
            // First compare on set size
            int sizeCompare = Integer.compare(this.basePairsSet.size(), other.basePairsSet.size());
            if (sizeCompare != 0) {
                return sizeCompare;
            }
            // Then compare on double value
            return Double.compare(this.value, other.value);
        }
    }

    record BPKey(int row, int col) {
    }

    public static void setModelFile(String fileName) {
        modelFilePath = fileName;
    }

    public boolean hasValidModelFile() {
        if (modelFilePath != null) {
            File file = new File(modelFilePath);
            if (!file.exists()) {
                return false;
            }
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:saved_model.pb");
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(file.toPath())) {
                for (Path path : paths) {
                    if (matcher.matches(path.getFileName())) {
                        return file.exists();
                    }
                }
            } catch (IOException e) {
                log.error("Error finding model file", e);
            }
        }
        return false;
    }

    public static void load() throws IllegalArgumentException {
        if (modelFilePath == null) {
            throw new IllegalArgumentException("No model file location set");
        }
        if (graphModel == null) {
            try {
                graphModel = SavedModelBundle.load(modelFilePath, "serve");
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to load model. File may be corrupt.");
            }
        }
    }

    public int getIndex(int r, int c, int nCols, int d) {
        return r * nCols - (r - 1) * (r - 1 + 1) / 2 + c - d - r * (d + 1);
    }

    public void predict(String rnaSequence, double threshold, boolean predictPseudoKnots, boolean requireCanonical) {
        this.rnaSequence = rnaSequence;
        this.predictPseudoKnots = predictPseudoKnots;
        this.requireCanonical = requireCanonical;
        if (graphModel == null) {
            load();
        }
        String rnaTokens = "AUGC";
        int nCols = 512;
        int[] tokenized = new int[nCols];
        for (int i = 0; i < rnaSequence.length(); i++) {
            tokenized[i] = rnaTokens.indexOf(rnaSequence.charAt(i)) + 2;
        }
        var inputTF = TInt32.vectorOf(tokenized);
        var matrix1 = NdArrays.ofInts(Shape.of(1, nCols));
        var lenInput = NdArrays.ofInts(Shape.of(1));
        lenInput.setInt(rnaSequence.length(), 0);
        matrix1.set(inputTF, 0);
        Map<String, Tensor> inputs = new HashMap<>();
        var input1 = TInt32.tensorOf(matrix1);
        var input2 = TInt32.tensorOf(lenInput);
        inputs.put("seq", input1);
        inputs.put("len", input2);
        try (Result tensor0 = graphModel.function("serving_default").call(inputs)) {
            var tensorDataOpt = tensor0.get("output_0");
            tensorDataOpt.ifPresent(tensorData -> {
                TFloat32 output = (TFloat32) tensorData;
                int seqLen = rnaSequence.length();
                setPredictions(rnaSequence, seqLen, nCols, output, threshold);
            });
        }
    }

    // end pairs are not always predicted correctly
    void fixEnd(String rnaSequence, int seqLen, double limit) {
        int r = 0;
        int c = seqLen - 1;
        char rChar = rnaSequence.charAt(r);
        char cChar = rnaSequence.charAt(c);
        String bp = rChar + String.valueOf(cChar);
        double thisP = savedPredictions[r][c];
        if ((thisP < limit) && validBPs.contains(bp)) {
            double nextP = savedPredictions[r + 1][c - 1];
            if (thisP < nextP) {
                savedPredictions[r][c] = nextP;
            }
        }

    }

    private void setPredictions(String rnaSequence, int seqLen, int nCols, TFloat32 tensor0, double threshold) {
        savedPredictions = new double[seqLen][seqLen];
        predictions = new double[seqLen][seqLen];
        double maxPred = 1.0e-6;
        for (int r = 0; r < seqLen; r++) {
            for (int c = r + delta; c < seqLen; c++) {
                int index = getIndex(r, c, nCols, delta);
                savedPredictions[r][c] = tensor0.getFloat(0, index);
                maxPred = Math.max(maxPred, savedPredictions[r][c]);
            }
        }
        double scale = 1.0;
        if (((maxPred > 1.0e-6) && maxPred < 0.9)) {
            scale = 0.9 / maxPred;
        }
        for (int r = 0; r < seqLen; r++) {
            for (int c = r + delta; c < seqLen; c++) {
                savedPredictions[r][c] *= scale;
            }
        }
        fixEnd(rnaSequence, seqLen, threshold);
        getSavedPredictions();
        updateBasePairs(threshold, predictPseudoKnots, requireCanonical);
    }

    private void getSavedPredictions() {
        int seqLen = predictions.length;

        for (int r = 0; r < seqLen; r++) {
            for (int c = r + delta; c < seqLen; c++) {
                char rChar = rnaSequence.charAt(r);
                char cChar = rnaSequence.charAt(c);
                String bp = rChar + String.valueOf(cChar);
                if (requireCanonical && !validBPs.contains(bp)) {
                    predictions[r][c] = 0.0;
                } else {
                    predictions[r][c] = savedPredictions[r][c];
                }
            }
        }
    }

    public void updateBasePairs(double threshold, boolean predictPseudoKnots, boolean requireCanonical) {
        this.predictPseudoKnots = predictPseudoKnots;
        this.requireCanonical = requireCanonical;
        getSavedPredictions();
        findRegions(threshold);
        int seqLen = predictions.length;
        allBasePairs = new HashMap<>();

        filterPredictions(seqLen, threshold);
        for (int r = 0; r < seqLen; r++) {
            for (int c = r + delta; c < seqLen; c++) {
                double prediction = predictions[r][c];
                if (prediction > threshold) {
                    BasePairProbability basePairProbability = new BasePairProbability(r, c, prediction);
                    allBasePairs.put(new BPKey(r, c), basePairProbability);
                }
            }
        }
    }

    private void filterPredictions(int seqLen, double threshold) {
        for (int r = 2; r < seqLen - 2; r++) {
            for (int c = 2 + delta; c < seqLen - 2; c++) {
                double vC = predictions[r][c];
                if (vC > threshold) {
                    boolean ok = false;
                    for (int i = -2; i <= 2; i++) {
                        if ((i != 0) && (predictions[r - i][c + i] > threshold)) {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) {
                        predictions[r][c] = 0.0;
                    }
                }
            }
        }
    }

    record Extent(List<BasePairProbability> basePairProbabilities) {

        boolean overlaps(Extent extent) {
            int b1 = extent.basePairProbabilities.getFirst().r;
            int b2 = extent.basePairProbabilities.reversed().getFirst().r;
            int b3 = extent.basePairProbabilities.reversed().getFirst().c;
            int b4 = extent.basePairProbabilities.getFirst().c;
            return overlaps(b1, b2, b3, b4);
        }

        boolean overlaps(int b1, int b2, int b3, int b4) {
            int a1 = basePairProbabilities.getFirst().r;
            int a2 = basePairProbabilities.reversed().getFirst().r;
            int a3 = basePairProbabilities.reversed().getFirst().c;
            int a4 = basePairProbabilities.getFirst().c;

            boolean bIna = (b1 > a2) && (b4 < a3);
            boolean aInb = (a1 > b2) && (a4 < b3);
            boolean bAftera = b1 > a4;
            boolean aAfterb = a1 > b4;
            return !(bIna || aInb || bAftera || aAfterb);
        }

    }

    public int getNExtents() {
        return extentBasePairsList.size();
    }

    public BasePairsMatching getExtentBasePairs(int i) {
        BasePairsMatching basePairsMatching = extentBasePairsList.get(i);
        extentBasePairs = basePairsMatching.basePairsSet;
        return basePairsMatching;
    }

    public double[][] getPredictions() {
        return predictions;
    }

    public record BasePairProbability(int r, int c, double probability) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BasePairProbability that = (BasePairProbability) o;
            return ((r == that.r) && (c == that.c)) || ((r == that.c) && (c == that.r));
        }

        BPKey bpKey() {
            return new BPKey(r, c);
        }

        @Override
        public int hashCode() {
            int result;
            if (r < c) {
                result = r;
                result = 31 * result + c;
            } else {
                result = c;
                result = 31 * result + r;
            }
            return result;
        }
    }

    public List<BasePairProbability> getAllBasePairs(double pLimit) {
        List<BasePairProbability> bps = new ArrayList<>();
        for (BasePairProbability basePairProbability : allBasePairs.values()) {
            if (basePairProbability.probability > pLimit) {
                bps.add(basePairProbability);
            }
        }
        return bps;
    }

    Map<BasePairProbability, Integer> findCrossings(Collection<BasePairProbability> basePairs) {
        Map<BasePairProbability, Integer> crossings = new HashMap<>();
        for (BasePairProbability basePair1 : basePairs) {
            int i1 = basePair1.r;
            int j1 = basePair1.c;
            for (BasePairProbability basePair2 : basePairs) {
                int i2 = basePair2.r;
                int j2 = basePair2.c;
                boolean cross = (i1 < i2) && (i2 < j1) && (j1 < j2);
                addCrossing(basePair1, basePair2, cross, crossings);
            }
        }
        return crossings;
    }

    private static void addCrossing(BasePairProbability basePair1, BasePairProbability basePair2, boolean cross, Map<BasePairProbability, Integer> crossings) {
        if (cross) {
            crossings.compute(basePair1, (basePair, value) -> (value == null) ? 1 : value + 1);
            crossings.compute(basePair2, (basePair, value) -> (value == null) ? 1 : value + 1);
        }
    }

    boolean crosses(Collection<BasePairProbability> basePairs, BasePairProbability basePair1) {
        int i1 = basePair1.r;
        int j1 = basePair1.c;
        for (BasePairProbability basePair2 : basePairs) {
            int i2 = basePair2.r;
            int j2 = basePair2.c;
            boolean cross1 = (i1 < i2) && (i2 < j1) && (j1 < j2);
            boolean cross2 = (i2 < i1) && (i1 < j2) && (j2 < j1);
            if (cross1 || cross2) {
                return true;
            }
        }
        return false;
    }

    boolean removeLargestCrossing(Collection<BasePairProbability> basePairs, Map<BasePairProbability, Integer> crossings) {
        BasePairProbability basePairMax = null;
        int max = 0;
        for (var crossing : crossings.entrySet()) {
            if (crossing.getValue() > max) {
                max = crossing.getValue();
                basePairMax = crossing.getKey();
            }
        }
        boolean removedOne = false;
        if (basePairMax != null) {
            basePairs.remove(basePairMax);
            removedOne = true;

        }
        return removedOne;
    }

    void filterAllCrossings(Collection<BasePairProbability> basePairs) {
        boolean removedOne = true;
        while (removedOne) {
            var crossings = findCrossings(basePairs);
            removedOne = removeLargestCrossing(basePairs, crossings);
        }
    }

    public String getDotBracket(Collection<BasePairProbability> basePairProbabilities) {
        int n = rnaSequence.length();
        int[] ssin = new int[n];
        Arrays.fill(ssin, -1);

        for (BasePairProbability basePairProbability : basePairProbabilities) {
            int i = basePairProbability.r;
            int j = basePairProbability.c;
            ssin[i] = j;
            ssin[j] = i;
        }
        return RNADotBracketCalculator.fcfs(ssin);
    }

    record ParitionedGraph(SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph,
                           Set<Integer> partition1, Set<Integer> partition2) {
    }

    private void buildGraph(double threshold) {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph
                = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        int n = predictions.length;
        Set<Integer> partition1 = new HashSet<>();
        Set<Integer> partition2 = new HashSet<>();
        for (int i = 0; i < n; i++) {
            simpleGraph.addVertex(i);
            partition1.add(i);
            simpleGraph.addVertex(i + n);
            partition2.add(i + n);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + delta; j < n; j++) {
                if ((predictions[i][j] > threshold)) {
                    DefaultWeightedEdge weightedEdge1 = new DefaultWeightedEdge();
                    simpleGraph.addEdge(i, j + n, weightedEdge1);
                }
            }
        }
        paritionedGraph = new ParitionedGraph(simpleGraph, partition1, partition2);
    }

    private void setGraphWeights(Graph<Integer, DefaultWeightedEdge> graph, double threshold, boolean[] inRegion, double randomScale, int iTry) {
        int n = predictions.length;
        for (var edge : graph.edgeSet()) {
            int i = graph.getEdgeSource(edge);
            int j = graph.getEdgeTarget(edge) - n;
            BPRegion bpRegion = regionsMap.get(new BPKey(i, j));
            double regionWeight = bpRegion != null ? bpRegion.delta : 0.0;
            double weight = 0.0;
            if (i == j) {
                weight = 1.0e-6;
            } else if (((i + 2) < j) && (predictions[i][j] > threshold)) {
                double prediction;
                if ((inRegion[i] || inRegion[j]) && (regionWeight < 1.0)) {
                    prediction = -1.0;
                } else {
                    double adjustment = iTry == 0 ? 0.0 : randomScale * (random.nextDouble() - 0.5);
                    prediction = predictions[i][j] + adjustment + regionWeight;
                }
                if (prediction < 0.0) {
                    weight = 1.0e-6;
                } else {
                    prediction = Math.min(prediction, 500.0);
                    prediction = Math.max(prediction, 0.0);
                    weight = 100.0 + prediction;
                }
            }


            graph.setEdgeWeight(i, j + n, weight);
        }
    }

    List<BasePairProbability> getMatches(MatchingAlgorithm.Matching<Integer, DefaultWeightedEdge> matchResult) {
        int n = predictions.length;
        var simpleGraph = matchResult.getGraph();
        List<BasePairProbability> matches = new ArrayList<>();
        BasePairProbability[] matchUsed = new BasePairProbability[n];
        matchResult.getEdges().stream()
                .sorted((a, b) -> Double.compare(simpleGraph.getEdgeWeight(b), simpleGraph.getEdgeWeight(a)))
                .forEach(edge -> {
                    int r = simpleGraph.getEdgeSource(edge);
                    int c = simpleGraph.getEdgeTarget(edge) - n;
                    if (((r + 3) < c) && (matchUsed[r] == null) && (matchUsed[c] == null)) {
                        BasePairProbability basePairProbability = allBasePairs.get(new BPKey(r, c));
                        matches.add(basePairProbability);
                        matchUsed[r] = basePairProbability;
                        matchUsed[c] = basePairProbability;
                    }
                });
        return matches;
    }

    public double getGraphThreshold() {
        return graphThreshold;
    }

    boolean hasNeighbor(BasePairProbability[] matches, BasePairProbability bp) {
        int last = predictions.length - 1;
        boolean available = (matches[bp.r] == null) && (matches[bp.c] == null);
        boolean neighbored = ((bp.r > 0) && (matches[bp.r - 1] != null))
                || ((bp.r < last) && (matches[bp.r + 1] != null))
                || ((bp.c > 0) && (matches[bp.c - 1] != null))
                || ((bp.c < last) && (matches[bp.c + 1] != null));
        return available && neighbored;
    }

    private void addMissing(Set<BasePairProbability> basePairProbabilities) {
        Map<BPKey, BasePairProbability> bps = new HashMap<>();
        BasePairProbability[] matches = new BasePairProbability[predictions.length];
        for (BasePairProbability basePairProbability : basePairProbabilities) {
            bps.put(basePairProbability.bpKey(), basePairProbability);
            matches[basePairProbability.r] = basePairProbability;
            matches[basePairProbability.c] = basePairProbability;
        }
        List<BasePairProbability> extras = new ArrayList<>();
        for (var entry : allBasePairs.entrySet()) {
            BasePairProbability bp = entry.getValue();
            if (!bps.containsKey(entry.getKey()) && hasNeighbor(matches, bp)) {
                extras.add(entry.getValue());
            }
        }
        for (BasePairProbability bp : extras) {
            if (!crosses(basePairProbabilities, bp) && hasNeighbor(matches, bp)) {
                basePairProbabilities.add(bp);
                matches[bp.r] = bp;
                matches[bp.c] = bp;
            }
        }
    }

    int checkForExisting(List<BasePairProbability> matches, List<int[]> matchTries) {
        int nFound = extentBasePairsList.size();
        int n = predictions.length;
        int foundMatch = -1;
        int[] matchTest = new int[n];
        Arrays.fill(matchTest, -1);
        for (BasePairProbability basePairProbability : matches) {
            if (basePairProbability != null) {
                matchTest[basePairProbability.r] = basePairProbability.c;
                matchTest[basePairProbability.c] = basePairProbability.r;
            }
        }

        for (int j = 0; j < nFound; j++) {
            boolean ok = true;
            int[] testTry = matchTries.get(j);
            for (int i = 0; i < n; i++) {
                if (matchTest[i] != testTry[i]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                foundMatch = j;
                break;
            }
        }
        return foundMatch;
    }

    boolean checkRC(boolean[][] exists, int r, int c) {
        int n = exists.length;
        return (r >= 0) && (r < n) && (c >= 0) && (c < n) && exists[r][c];
    }

    void removeLonely(Set<BasePairProbability> extentBasePairs) {
        Set<BasePairProbability> temp = new HashSet<>();
        int n = predictions.length;
        boolean[][] exists = new boolean[n][n];
        for (BasePairProbability basePairProbability : extentBasePairs) {
            int r = basePairProbability.r;
            int c = basePairProbability.c;
            exists[r][c] = true;
        }
        for (BasePairProbability basePairProbability : extentBasePairs) {
            int r = basePairProbability.r;
            int c = basePairProbability.c;
            boolean found = false;
            for (int i = 1; i <= 2; i++) {
                if (checkRC(exists, r - i, c + i)) {
                    found = true;
                }
                if (checkRC(exists, r + i, c - i)) {
                    found = true;
                }
            }
            if (checkRC(exists, r - 2, c + 1)) {
                found = true;
            }
            if (checkRC(exists, r - 1, c + 2)) {
                found = true;
            }
            if (checkRC(exists, r + 2, c - 1)) {
                found = true;
            }
            if (checkRC(exists, r + 1, c - 2)) {
                found = true;
            }

            if (found) {
                temp.add(basePairProbability);
            }
        }
        extentBasePairs.clear();
        extentBasePairs.addAll(temp);
    }

    void refineMatches(List<BasePairProbability> matches, List<int[]> matchTries) {
        Set<BasePairProbability> newExtentBasePairs = new HashSet<>();
        int[] testTry = new int[predictions.length];
        Arrays.fill(testTry, -1);
        matchTries.add(testTry);
        for (BasePairProbability basePairProbability : matches) {
            if (basePairProbability != null) {
                newExtentBasePairs.add(basePairProbability);
                testTry[basePairProbability.r] = basePairProbability.c;
                testTry[basePairProbability.c] = basePairProbability.r;
            }
        }

        if (!predictPseudoKnots) {
            filterAllCrossings(newExtentBasePairs);
        }
        addMissing(newExtentBasePairs);
        removeLonely(newExtentBasePairs);
        double sum = newExtentBasePairs.stream().mapToDouble(ebp -> ebp.probability).sum();
        BasePairsMatching basePairsMatching = new BasePairsMatching(sum, newExtentBasePairs);
        if (!basePairsMatching.exists(extentBasePairsList)) {
            extentBasePairsList.add(basePairsMatching);
        }

    }

    private void matchRegion(SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph, List<int[]> matchTries,
                             double threshold, boolean[] inRegion, int iTry,
                             double randomScale) {
        setGraphWeights(simpleGraph, threshold, inRegion, randomScale, iTry);
        var matcher = new MaximumWeightBipartiteMatching<>(simpleGraph,
                paritionedGraph.partition1, paritionedGraph.partition2);
        MatchingAlgorithm.Matching<Integer, DefaultWeightedEdge> matchResult = matcher.getMatching();
        List<BasePairProbability> matches = getMatches(matchResult);
        int foundMatch = checkForExisting(matches, matchTries);
        if (foundMatch == -1) {
            refineMatches(matches, matchTries);
        }
    }

    private void setupRegion(BPRegion bpRegion, boolean[] inRegion) {
        for (int i = 0; i < bpRegion.size; i++) {
            int r = bpRegion.start.row + i;
            int c = bpRegion.start.col - i;
            inRegion[r] = true;
            inRegion[c] = true;
        }
        bpRegion.delta = 400.0;
    }

    public void bipartiteMatch(double threshold, double randomScale, int nTries) {
        int n = predictions.length;
        buildGraph(threshold);

        graphThreshold = threshold;
        List<int[]> matchTries = new ArrayList<>();
        extentBasePairsList.clear();
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph = paritionedGraph.simpleGraph;

        int nRegions = regionsList.size();
        boolean[] inRegion = new boolean[n];
        for (int i = 0; i < nRegions; i++) {
            for (int j = 0; j < nRegions; j++) {
                for (int k = 0; k < nRegions; k++) {
                    for (BPRegion bpRegion : regionsList) {
                        bpRegion.delta = 0.0;
                    }
                    Arrays.fill(inRegion, false);
                    setupRegion(regionsList.get(i), inRegion);
                    setupRegion(regionsList.get(j), inRegion);
                    setupRegion(regionsList.get(k), inRegion);
                    matchRegion(simpleGraph, matchTries, threshold, inRegion, 0, 0.0);
                }
            }
        }
        for (BPRegion bpRegion : regionsList) {
            bpRegion.delta = 0.0;
        }
        Arrays.fill(inRegion, false);
        for (int iTry = 0; iTry < nTries; iTry++) {
            matchRegion(simpleGraph, matchTries, threshold, inRegion, iTry, randomScale);
        }
        extentBasePairsList.sort(Comparator.reverseOrder());
    }


    public static class BPRegion {
        final BPKey start;
        int size;
        int index;
        double delta = 0.0;

        BPRegion(BPKey start, int index) {
            this.start = start;
            size = 1;
            this.index = index;
        }

        void incr() {
            size++;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return index + " " + start.row + " " + start.col + " " + size + " " + delta;
        }
    }

    void findRegions(double threshold) {
        int seqLen = predictions.length;
        List<BPRegion> localRegions = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(0);

        for (int r = 0; r < seqLen; r++) {
            for (int c = r + delta; c < seqLen; c++) {
                boolean aboveThreshold = predictions[r][c] > threshold;
                if (aboveThreshold) {
                    BPKey bpKey = new BPKey(r, c);
                    BPKey neighborKey = new BPKey(r - 1, c + 1);
                    boolean neighbor = r > 0 && c < seqLen - 1 && regionsMap.containsKey(neighborKey);
                    if (neighbor) {
                        BPRegion bpRegion = regionsMap.get(neighborKey);
                        bpRegion.incr();
                        regionsMap.put(bpKey, bpRegion);
                    } else {
                        BPRegion bpRegion = new BPRegion(bpKey, index.getAndIncrement());
                        regionsMap.put(bpKey, bpRegion);
                        localRegions.add(bpRegion);
                    }
                }
            }
        }
        regionsList.clear();
        for (BPRegion bpRegion : localRegions) {
            if (bpRegion.size > 2) {
                regionsList.add(bpRegion);
            }
        }
    }

    public BPRegion getBPRegion(int r, int c) {
        return regionsMap.get(new BPKey(r, c));
    }
}