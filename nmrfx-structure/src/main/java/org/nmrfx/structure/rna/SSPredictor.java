package org.nmrfx.structure.rna;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.ndarray.NdArrays;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;

import java.io.File;
import java.util.*;

public class SSPredictor {
    public static final Set<String> validBPs = Set.of("GC", "CG", "AU", "UA", "GU", "UG");
    double[][] predictions;
    String rnaSequence;

    static SavedModelBundle graphModel;
    static String modelFilePath = null;

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

    public int  getIndex(int r, int c, int nCols, int d) {
        return r * nCols - (r - 1) * (r - 1 + 1)/2 + c - d - r * (d+1);
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

        var inputs = TInt32.tensorOf(matrix1);
        int delta = 4;
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
        }
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

    public record BasePairProbability(int i, int j, double probability) {
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

    Map<BasePairProbability, Integer> findCrossings(List<BasePairProbability> basePairs) {
        Map<BasePairProbability, Integer> crossings = new HashMap<>();
        for (BasePairProbability basePair1 : basePairs) {
            int i1 = basePair1.i;
            int j1 = basePair1.j;
            for (BasePairProbability basePair2 : basePairs) {
                int i2 = basePair2.i;
                int j2 = basePair2.j;
                boolean cross = (i1 < i2) && (i2 < j1) && (j1 < j2);
                if (cross) {
                    crossings.compute(basePair1, (basePair, value) -> (value == null) ? 1 : value + 1);
                    crossings.compute(basePair2, (basePair, value) -> (value == null) ? 1 : value + 1);
                }

            }
        }
        return crossings;
    }

    boolean removeLargestCrossing(List<BasePairProbability> basePairs, Map<BasePairProbability, Integer> crossings) {
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

    void filterAllCrossings(List<BasePairProbability> basePairs) {
        boolean removedOne = true;
        while (removedOne) {
            var crossings = findCrossings(basePairs);
            removedOne = removeLargestCrossing(basePairs, crossings);
        }
    }

    public String getDotBracket(List<BasePairProbability> basePairProbabilities) {
        int n = rnaSequence.length();
        List<String> dotBracketList = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            dotBracketList.add(".");
        }
        for (BasePairProbability basePairProbability : basePairProbabilities) {
            int i = basePairProbability.i;
            int j = basePairProbability.j;
            dotBracketList.set(i, "(");
            dotBracketList.set(j, ")");
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            stringBuilder.append(dotBracketList.get(i));
        }
        return stringBuilder.toString();
    }

}