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
import ai.onnxruntime.*;

public class SSPredictor {
    private static final Logger log = LoggerFactory.getLogger(SSPredictor.class);

    static final Random random = new Random();
    public static final Set<String> validBPs = Set.of("GC", "CG", "AU", "UA", "GU", "UG");
    double[][] predictions;

    double graphThreshold = 0.7;

    ParitionedGraph paritionedGraph = null;
    Set<BasePairProbability> extentBasePairs;
    Map<BPKey, BasePairProbability> allBasePairs;

    List<BasePairsMatching> extentBasePairsList = new ArrayList<>();
    String rnaSequence;
    int delta = 4;

    static SavedModelBundle graphModel;
    static String modelFilePath = null;

    public record BasePairsMatching(double value, Set<BasePairProbability> basePairsSet) {

        boolean exists(List<BasePairsMatching> extentBasePairsList) {
            for (BasePairsMatching basePairsMatching : extentBasePairsList) {
                if (basePairsMatching.basePairsSet.equals(basePairsSet)) {
                    return true;
                }
            }
            return false;
        }
    }

    record BPKey(int row, int col) {
        static Map<Integer, Map<Integer, BPKey>> bpMap = new HashMap<>();

        static BPKey getBPKey(int row, int col) {
            Map<Integer, BPKey> colMap = bpMap.computeIfAbsent(row, key -> new HashMap<>());
            return colMap.computeIfAbsent(col, key -> new BPKey(row, col));
        }

    }

    public static void setModelFile(String fileName) {
        modelFilePath = fileName;
    }

    public boolean hasValidModelFile() {
        if (modelFilePath != null) {
            File file = new File(modelFilePath);
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

    public void predict(String rnaSequence) {
        this.rnaSequence = rnaSequence;
        if (graphModel == null) {
            load();
        }
        int nCols = 512;
        String rnaTokens = "AUGC";
        int[] tokenized = new int[nCols];
        for (int i = 0; i < rnaSequence.length(); i++) {
            tokenized[i] = rnaTokens.indexOf(rnaSequence.charAt(i)) + 2;
        }
        var inputTF = TInt32.vectorOf(tokenized);
        var matrix1 = NdArrays.ofInts(Shape.of(1, nCols));
        matrix1.set(inputTF, 0);

        var lenInput = NdArrays.ofInts(Shape.of(1));
        lenInput.setInt(rnaSequence.length(), 0);

        Map<String, Tensor> inputs = new HashMap<>();
        var input1 = TInt32.tensorOf(matrix1);
        var input2 = TInt32.tensorOf(lenInput);
        inputs.put("seq", input1);
        inputs.put("len", input2);

        double threshold = 0.4;
        allBasePairs = new HashMap<>();
        try (Result tensor0 = graphModel.function("serving_default").call(inputs)) {
            TFloat32 output = tensor0.get("output_0").isPresent() ? (TFloat32) tensor0.get("output_0").get() : null;
            int seqLen = rnaSequence.length();
            predictions = new double[seqLen][seqLen];
            setPredictions(rnaSequence, seqLen, nCols, output, threshold);
        }
    }

    public void predictOnnx(String rnaSequence) throws OrtException {
        var env = OrtEnvironment.getEnvironment();
        var session = env.createSession("/Users/ekoag/model144.onnx",new OrtSession.SessionOptions());
        int nCols = 512;
        String rnaTokens = "AUGC";
        int[] tokenized = new int[nCols];
        for (int i = 0; i < rnaSequence.length(); i++) {
            tokenized[i] = rnaTokens.indexOf(rnaSequence.charAt(i)) + 2;
        }
        int[] seqLen = new int[]{rnaSequence.length()};
        var input1 = OnnxTensor.createTensor(env, tokenized);
        var input2 = OnnxTensor.createTensor(env, seqLen);

        var inputs = Map.of("seq", input1,"len", input2);
        double threshold = 0.4;
        try(var output = session.run(inputs)) {
            System.out.println(output);
        }
    }

    private void setPredictions(String rnaSequence, int seqLen, int nCols, TFloat32 tensor0, double threshold) {
        for (int r = 0; r < seqLen; r++) {
            for (int c = r + delta; c < seqLen; c++) {
                int index = getIndex(r, c, nCols, delta);
                double prediction = tensor0.getFloat(0, index);
                char rChar = rnaSequence.charAt(r);
                char cChar = rnaSequence.charAt(c);
                String bp = rChar + String.valueOf(cChar);
                if (!validBPs.contains(bp)) {
                    prediction = 0.0;
                }
                predictions[r][c] = prediction;
            }
        }
        filterPredictions(seqLen, threshold);
        for (int r = 0; r < seqLen; r++) {
            for (int c = r + delta; c < seqLen; c++) {
                if (predictions[r][c] > threshold) {
                    BPKey bpKey = BPKey.getBPKey(r, c);
                    BasePairProbability basePairProbability = new BasePairProbability(r, c, predictions[r][c]);
                    allBasePairs.put(bpKey, basePairProbability);
                }
            }
        }
    }

    private void filterPredictions(int seqLen, double threshold) {
        for (int r = 1; r < seqLen - 1; r++) {
            for (int c = r + delta; c < seqLen - 1; c++) {
                double v0 = predictions[r - 1][c + 1];
                double v1 = predictions[r][c];
                double v2 = predictions[r + 1][c - 1];
                if ((v1 > threshold) && (v0 < threshold) && (v2 < threshold)) {
                    predictions[r][c] = 0.0;
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

    public Set<BasePairProbability> getExtentBasePairs() {
        return extentBasePairs;
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
            return BPKey.getBPKey(r, c);
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
        List<String> dotBracketList = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            dotBracketList.add(".");
        }
        for (BasePairProbability basePairProbability : basePairProbabilities) {
            int i = basePairProbability.r;
            int j = basePairProbability.c;
            dotBracketList.set(i, "(");
            dotBracketList.set(j, ")");
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            stringBuilder.append(dotBracketList.get(i));
        }
        return stringBuilder.toString();
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

    private void setGraphWeights(Graph<Integer, DefaultWeightedEdge> graph, double threshold, double randomScale) {
        int n = predictions.length;
        for (var edge : graph.edgeSet()) {
            int i = graph.getEdgeSource(edge);
            int j = graph.getEdgeTarget(edge) - n;
            double weight = 0.0;
            if (i == j) {
                weight = 1.0;
            } else if (((i + 2) < j) && (predictions[i][j] > threshold)) {
                double adjustment = randomScale * random.nextGaussian();
                double prediction = predictions[i][j] + adjustment;
                prediction = Math.min(prediction, 1.0);
                weight = 100.0 + prediction;
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
                        BPKey bpKey = BPKey.getBPKey(r, c);
                        BasePairProbability basePairProbability = allBasePairs.get(bpKey);
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
            if (!crosses(basePairProbabilities, bp) &&  hasNeighbor(matches, bp)) {
                basePairProbabilities.add(bp);
                matches[bp.r] = bp;
                matches[bp.c] = bp;
            }
        }
    }

    boolean checkForExisting(List<BasePairProbability> matches, int[][] matchTries) {
        int nFound = extentBasePairsList.size();
        int n = predictions.length;
        boolean foundMatch = false;
        for (int j = 0; j < nFound; j++) {
            int[] matchTest = new int[n];
            Arrays.fill(matchTest, -1);
            for (BasePairProbability basePairProbability : matches) {
                if (basePairProbability != null) {
                    matchTest[basePairProbability.r] = basePairProbability.c;
                    matchTest[basePairProbability.c] = basePairProbability.r;
                }
            }

            boolean ok = true;
            for (int i = 0; i < n; i++) {
                if (matchTest[i] != matchTries[j][i]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                foundMatch = true;
                break;
            }
        }
        return foundMatch;
    }

    void refineMatches(List<BasePairProbability> matches, int[][] matchTries) {
        Set<BasePairProbability> newExtentBasePairs = new HashSet<>();
        int nFound = extentBasePairsList.size();
        for (BasePairProbability basePairProbability : matches) {
            if (basePairProbability != null) {
                newExtentBasePairs.add(basePairProbability);
                matchTries[nFound][basePairProbability.r] = basePairProbability.c;
                matchTries[nFound][basePairProbability.c] = basePairProbability.r;
            }
        }

        filterAllCrossings(newExtentBasePairs);
        addMissing(newExtentBasePairs);
        double sum = newExtentBasePairs.stream().mapToDouble(ebp -> ebp.probability).sum();
        BasePairsMatching basePairsMatching = new BasePairsMatching(sum, newExtentBasePairs);
        if (!basePairsMatching.exists(extentBasePairsList)) {
            extentBasePairsList.add(basePairsMatching);
        }

    }

    public void bipartiteMatch(double threshold, double randomScale, int nTries) {
        int n = predictions.length;
        if (paritionedGraph == null) {
            buildGraph(threshold);
        }
        graphThreshold = threshold;
        int[][] matchTries = new int[nTries][n];
        for (int i = 0; i < nTries; i++) {
            Arrays.fill(matchTries[i], -1);
        }
        extentBasePairsList.clear();
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph = paritionedGraph.simpleGraph;

        for (int iTry = 0; iTry < nTries; iTry++) {
            double randomValue = iTry == 0 ? 0.0 : randomScale;
            setGraphWeights(simpleGraph, threshold, randomValue);
            var matcher = new MaximumWeightBipartiteMatching<>(simpleGraph,
                    paritionedGraph.partition1, paritionedGraph.partition2);
            MatchingAlgorithm.Matching<Integer, DefaultWeightedEdge> matchResult = matcher.getMatching();
            List<BasePairProbability> matches = getMatches(matchResult);
            boolean foundMatch = checkForExisting(matches, matchTries);
            if (!foundMatch) {
                refineMatches(matches, matchTries);
            }
        }
        extentBasePairsList.sort((a, b) -> Double.compare(b.value, a.value));
    }

}