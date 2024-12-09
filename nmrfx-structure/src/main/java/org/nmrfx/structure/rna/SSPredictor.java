package org.nmrfx.structure.rna;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.MatchingAlgorithm;
import org.jgrapht.alg.matching.MaximumWeightBipartiteMatching;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.ndarray.NdArrays;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;

import java.io.File;
import java.util.*;

public class SSPredictor {
    static final Random random = new Random();
    public static final Set<String> validBPs = Set.of("GC", "CG", "AU", "UA", "GU", "UG");
    double[][] predictions;

    double graphThreshold = 0.7;

    ParitionedGraph paritionedGraph = null;
    Set<BasePairProbability> extentBasePairs;

    List<BasePairsMatching> extentBasePairsList = new ArrayList<>();
    String rnaSequence;
    int delta = 4;

    static SavedModelBundle graphModel;
    static String modelFilePath = null;

    record BasePairsMatching(double value,Set<BasePairProbability> basePairsSet) {}
    public static void setModelFile(String fileName) {
        modelFilePath = fileName;
    }

    public boolean hasValidModelFile() {
        boolean ok = false;
        if (modelFilePath != null) {
            File file = new File(modelFilePath);
            ok = file.exists();
        }
        return ok;
    }

    public static void load() throws IllegalArgumentException {
        if (modelFilePath == null) {
            throw new IllegalArgumentException("No model file location set");
        }
        if (graphModel == null) {
            graphModel = SavedModelBundle.load(modelFilePath, "serve");
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
        String rnaTokens = "AUGC";
        int nCols = 512;
        int[] tokenized = new int[nCols];
        for (int i = 0; i < rnaSequence.length(); i++) {
            tokenized[i] = rnaTokens.indexOf(rnaSequence.charAt(i)) + 2;
        }
        var inputTF = TInt32.vectorOf(tokenized);
        var matrix1 = NdArrays.ofInts(Shape.of(1, nCols));

        matrix1.set(inputTF, 0);
        double threshold = 0.4;

        var inputs = TInt32.tensorOf(matrix1);
        try (TFloat32 tensor0 = (TFloat32) graphModel.function("serving_default").call(inputs)) {
            int seqLen = rnaSequence.length();
            predictions = new double[seqLen][seqLen];
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
    }

    record Extent(List<BasePairProbability> basePairProbabilities) {

        boolean overlaps(Extent extent) {
            int b1 = extent.basePairProbabilities.get(0).r;
            int b2 = extent.basePairProbabilities.reversed().get(0).r;
            int b3 = extent.basePairProbabilities.reversed().get(0).c;
            int b4 = extent.basePairProbabilities.get(0).c;
            return overlaps(b1, b2, b3, b4);
        }

        boolean overlaps(int b1, int b2, int b3, int b4) {
            int a1 = basePairProbabilities.get(0).r;
            int a2 = basePairProbabilities.reversed().get(0).r;
            int a3 = basePairProbabilities.reversed().get(0).c;
            int a4 = basePairProbabilities.get(0).c;

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

    public Set<BasePairProbability> getExtentBasePairs(int i) {
        extentBasePairs = extentBasePairsList.get(i).basePairsSet;
        return extentBasePairs;
    }

    public Set<BasePairProbability> getExtentBasePairs() {
        return extentBasePairs;
    }

    public double[][] getPredictions() {
        return predictions;
    }

    public List<BasePairProbability> getBasePairs(double pLimit) {
        int n = predictions.length;
        double[][] predicted = new double[n][n];
        for (int i = 0; i < n; i++) {
            predicted[i] = Arrays.copyOf(predictions[i], n);
        }
        List<BasePairProbability> basePairs = getBasePairs(predicted, pLimit);
        filterAllCrossings(basePairs);
        return basePairs;
    }

    record RCMax(int row, int column, double prediction) {
    }

    RCMax findAnRCMax(double[][] predicted) {
        int n = rnaSequence.length();
        double max = 0.0;
        int rMax = -1;
        int cMax = -1;
        for (int r = 0; r < n; r++) {
            for (int c = r + 1; c < n; c++) {
                double v = predicted[r][c];
                if (v > max) {
                    max = v;
                    rMax = r;
                    cMax = c;
                }
            }
        }
        return new RCMax(rMax, cMax, max);
    }

    void findRCMax(double[][] predicted) {
        int n = rnaSequence.length();
        for (int i = 0; i < n; i++) {
            RCMax rcMax = findAnRCMax(predicted);
            if (rcMax.row() != -1) {
                int r = rcMax.row();
                int c = rcMax.column;
                for (int j = 0; j < n; j++) {
                    predicted[r][j] = 0.0;
                    predicted[j][r] = 0.0;
                    predicted[c][j] = 0.0;
                    predicted[j][c] = 0.0;
                }
                predicted[r][c] = -rcMax.prediction();
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (predicted[i][j] < 0.0) {
                    predicted[i][j] *= -1.0;
                }
            }
        }
    }

    public record BasePairProbability(int r, int c, double probability) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BasePairProbability that = (BasePairProbability) o;
            return ((r == that.r) && (c == that.c)) || ((r == that.c) && (c == that.r));
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

    List<BasePairProbability> getBasePairs(double[][] predicted, double pLimit) {
        findRCMax(predicted);
        int n = rnaSequence.length();
        List<BasePairProbability> bps = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = r + 2; c < n; c++) {
                if (predicted[r][c] > pLimit) {
                    BasePairProbability bp = new BasePairProbability(r, c, predicted[r][c]);
                    bps.add(bp);
                }
            }
        }
        return bps;
    }

    public List<BasePairProbability> getAllBasePairs(double pLimit) {
        double[][] predicted = predictions;
        int n = rnaSequence.length();
        List<BasePairProbability> bps = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = r + 2; c < n; c++) {
                if (predicted[r][c] > pLimit) {
                    BasePairProbability bp = new BasePairProbability(r, c, predicted[r][c]);
                    bps.add(bp);
                }
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
                if (cross) {
                    crossings.compute(basePair1, (basePair, value) -> (value == null) ? 1 : value + 1);
                    crossings.compute(basePair2, (basePair, value) -> (value == null) ? 1 : value + 1);
                }

            }
        }
        return crossings;
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
                        BasePairProbability basePairProbability = new BasePairProbability(r, c, predictions[r][c]);
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

            int nFound = extentBasePairsList.size();
            boolean foundMatch = false;
            for (int j = 0; j < nFound; j++) {
                int[] matchTest = new int[n];
                Arrays.fill(matchTest, -1);
                for (BasePairProbability basePairProbability : matches) {
                    matchTest[basePairProbability.r] = basePairProbability.c;
                    matchTest[basePairProbability.c] = basePairProbability.r;
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

            if (!foundMatch) {
                Set<BasePairProbability> newExtentBasePairs = new HashSet<>();
                for (BasePairProbability basePairProbability : matches) {
                    newExtentBasePairs.add(basePairProbability);
                    matchTries[nFound][basePairProbability.r] = basePairProbability.c;
                    matchTries[nFound][basePairProbability.c] = basePairProbability.r;
                }

                filterAllCrossings(newExtentBasePairs);
                double sum = newExtentBasePairs.stream().mapToDouble(ebp -> ebp.probability).sum();
                BasePairsMatching basePairsMatching = new BasePairsMatching(sum, newExtentBasePairs);
                extentBasePairsList.add(basePairsMatching);
            }
        }
        Collections.sort(extentBasePairsList, (a,b) -> Double.compare(b.value, a.value));
    }

}