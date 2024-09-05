package org.nmrfx.structure.rna;

import jnr.a64asm.Ext;
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

    Set<BasePairProbability> extentBasePairs;
    List<Indices> indices = new ArrayList<>();
    List<Extent> uniqueExtents = new ArrayList<>();
    List<Extent> overlapExtents = new ArrayList<>();
    String rnaSequence;
    int delta = 4;

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

    record Extent(List<Integer> r, List<Integer> c, List<Double> values) {
        boolean contains(int i, int j) {
            for (int k = 0; k < r.size(); k++) {
                if ((r.get(k) == i) && (c.get(k) == j)) {
                    return true;
                }
            }
            return false;
        }

        double sum() {
            double sum = 0.0;
            for (double value : values) {
                sum += value;
            }
            return sum;
        }

        boolean overlaps(Extent extent) {
            int lastB = extent.r.size() - 1;
            int b1 = extent.r.get(0);
            int b2 = extent.r.get(lastB);
            int b3 = extent.c.get(lastB);
            int b4 = extent.c.get(0);
            return overlaps(b1, b2, b3, b4);
        }

        boolean overlaps(int b1, int b2, int b3, int b4) {
            int last = r.size() - 1;
            int a1 = r.get(0);
            int a2 = r.get(last);
            int a3 = c.get(last);
            int a4 = c.get(0);

            boolean bIna = (b1 > a2) && (b4 < a3);
            boolean aInb = (a1 > b2) && (a4 < b3);
            boolean bAftera = b1 > a4;
            boolean aAfterb = a1 > b4;
            return !(bIna || aInb || bAftera || aAfterb);
        }

        List<Extent> overlaps(List<Extent> extents) {
            return extents.stream().filter(e -> e != this).filter(e -> e.overlaps(this)).toList();
        }

        List<Integer> overlapIndices(List<Extent> extents) {
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < extents.size(); i++) {
                Extent extent = extents.get(i);
                if (extent != this) {
                    if (extent.overlaps(this)) {
                        result.add(i);
                    }
                }
            }
            return result;
        }
    }

    boolean isPresent(List<Extent> extents, int r, int c) {
        return extents.stream().filter(e -> e.contains(r, c)).findFirst().isPresent();
    }

    class Indices {
        final List<Integer> indexes = new ArrayList<>();
        final Set<BasePairProbability> basePairProbabilities = new HashSet<>();

        final double sum;

        Indices(List<Integer> indexes, double sum) {
            this.indexes.addAll(indexes);
            this.sum = sum;
        }

        void updateBasePairs() {
            basePairProbabilities.addAll(getExtentBasePairs(indexes));
        }

        void addBasePairs(List<Extent> extents) {
            Set<Extent> unusedExtents = new HashSet<>();
            unusedExtents.addAll(extents);
            for (var i : indexes) {
                Extent extent = extents.get(i);
                if (unusedExtents.contains(extent)) {
                    unusedExtents.remove(extent);
                }
            }
            Set<Integer> used = new HashSet<>();
            for (BasePairProbability basePairProbability : basePairProbabilities) {
                used.add(basePairProbability.i);
                used.add(basePairProbability.j);
            }
            for (Extent extent : unusedExtents) {
                int n = extent.r.size();
                int first = -1;
                int last = -1;
                List<BasePairProbability> newBPs = new ArrayList<>();
                boolean gap = false;
                for (int i = 0; i < n; i++) {
                    BasePairProbability basePairProbability = new BasePairProbability(extent.r.get(i), extent.c.get(i), extent.values.get(i));
                    if (!used.contains(basePairProbability.i) && !used.contains(basePairProbability.j)) {
                        if (first == -1) {
                            first = i;
                        }
                        last = i;
                        newBPs.add(basePairProbability);
                    } else {
                        if (first != -1) {
                            gap = true;
                        }
                    }
                }
                if (((first != -1) && (last - first + 1) > 3)) {
                    int b1 = newBPs.get(0).i;
                    int b2 = newBPs.get(newBPs.size() - 1).i;
                    int b3 = newBPs.get(0).j;
                    int b4 = newBPs.get(newBPs.size() - 1).j;
                    boolean ok = true;
                    for (Extent extent1 : extents) {
                        if (extent1.overlaps(b1, b2, b3, b4)) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        basePairProbabilities.addAll(newBPs);
                        for (BasePairProbability bp : newBPs) {
                            used.add(bp.i);
                            used.add(bp.j);
                        }
                    }
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Indices indices1 = (Indices) o;
            if (indices1.basePairProbabilities.size() != basePairProbabilities.size()) {
                return false;
            }
            for (BasePairProbability basePairProbability : basePairProbabilities) {
                if (!indices1.basePairProbabilities.contains(basePairProbability)) {
                    return false;
                }
            }
            return true;

        }

        @Override
        public int hashCode() {
            return basePairProbabilities.hashCode();
        }
    }

    void trimValues(List<Integer> r, List<Integer> c, List<Double> values) {
        int last = r.size() - 1;
        if (last > 0) {
            int dR = r.get(last) - r.get(last - 1);
            int dC = c.get(last - 1) - c.get(last);
            if ((dR != 1) || (dC != 1)) {
                r.remove(last);
                c.remove(last);
                values.remove(last);
            }
        }

    }

    public void findExtents(double threshold) {
        List<Extent> extents = new ArrayList<>();
        int n = rnaSequence.length();
        int[][] tries = {{1, 1}, {1, 2}, {2, 1}, {2, 2}};
        for (int r0 = 0; r0 < n; r0++) {
            for (int c0 = r0 + delta; c0 < n; c0++) {
                if (isPresent(extents, r0, c0)) {
                    continue;
                }
                int lastr = r0;
                int lastc = c0;
                List<Integer> rows = new ArrayList<>();
                List<Integer> columns = new ArrayList<>();
                List<Double> values = new ArrayList<>();
                if (predictions[r0][c0] > threshold) {
                    double sum = predictions[r0][c0];
                    rows.add(r0);
                    columns.add(c0);
                    values.add(predictions[r0][c0]);
                    int m = Math.min(n - r0, c0);
                    int addedSize = 0;
                    for (int i = 1; i < m; i++) {
                        double bestValue = 0.0;
                        int bestR = 0;
                        int bestC = 0;
                        int lastAdded = 0;
                        for (int itry = 0; itry < 4; itry++) {
                            int r = lastr + tries[itry][0];
                            int c = lastc - tries[itry][1];

                            if (((r) < n) && ((c) >= 0)) {
                                double value = predictions[r][c];
                                if (value > bestValue) {
                                    bestValue = value;
                                    bestR = r;
                                    bestC = c;
                                    lastAdded = itry;
                                    if ((itry == 0) && (value > threshold)) {
                                        break;
                                    }
                                }
//                                if ((itry == 0) && (value <= threshold)) {
//                                    if (rows.size() > 2) {
//                                        //trimValues(rows, columns, values);
//                                        Extent extent = new Extent(new ArrayList<>(rows), new ArrayList<>(columns), new ArrayList<>(values));
//                                        extents.add(extent);
//                                        addedSize = rows.size();
//                                    }
//                                }
                            }
                        }
                        if (bestValue > threshold) {
                            rows.add(bestR);
                            columns.add(bestC);
                            values.add(bestValue);
                            sum += bestValue;
                            lastc = bestC;
                            lastr = bestR;
                        } else {
                            trimValues(rows, columns, values);
                            if ((rows.size() > 2) && (rows.size() > addedSize)) {
                                Extent extent = new Extent(rows, columns, values);
                                extents.add(extent);
                            }
                            break;
                        }
                    }
                }
            }
        }
        extents.sort((a, b) -> Double.compare(b.sum(), a.sum()));
        overlapExtents.clear();
        uniqueExtents.clear();
        addExtents(extents);


        for (var extent : extents) {
            var overlaps = extent.overlaps(extents);
            if (overlaps.isEmpty()) {
                uniqueExtents.add(extent);
            } else {
                overlapExtents.add(extent);
            }
        }
        boolean[] used = new boolean[overlapExtents.size()];
        List<List<Integer>> allOverlaps = new ArrayList<>();
        for (var extent : overlapExtents) {
            List<Integer> overlapIndices = extent.overlapIndices(overlapExtents);
            allOverlaps.add(overlapIndices);
        }


        double best = 0.0;
        indices.clear();
        int m = Math.min(12, overlapExtents.size());
        int m2 = (int) Math.pow(2, m);
        for (int j = 0; j < m2; j++) {
            Arrays.fill(used, false);
            boolean[] use = new boolean[overlapExtents.size()];
            for (int k = 0; k < m; k++) {
                int mask = 1 << k;
                use[k] = (j & mask) == mask;
            }
            List<Integer> useIndices = new ArrayList<>();
            double sum = 0.0;
            for (int k = 0; k < m; k++) {
                if (use[k] && !used[k]) {
                    Extent extent = overlapExtents.get(k);
                    sum += addExtent(extent, allOverlaps, useIndices, used, k, sum);
                }
            }
            for (int i = 0; i < overlapExtents.size(); i++) {
                if (!used[i]) {
                    Extent extent = overlapExtents.get(i);
                    sum += addExtent(extent, allOverlaps, useIndices, used, i, sum);
                }
            }
            useIndices.sort(null);
            Indices currentIndices = new Indices(useIndices, sum);
            currentIndices.updateBasePairs();
            // currentIndices.addBasePairs(overlapExtents);
            if (!indices.contains(currentIndices)) {
                indices.add(currentIndices);
            }
        }
        indices.sort((a, b) -> Double.compare(b.sum, a.sum));
        for (var indice : indices) {
            indice.addBasePairs(overlapExtents);
        }
    }

    void addExtents(List<Extent> extents) {
        List<Extent> newExtents = new ArrayList<>();
        for (Extent extent : extents) {
            newExtents.addAll(splitExtent(extent));
        }
        extents.addAll(newExtents);
    }

    List<Extent> splitExtent(Extent extent) {
        int n = extent.r.size();
        List<Extent> newExtents = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            int rP = extent.r.get(i - 1);
            int cP = extent.c.get(i - 1);
            int r = extent.r.get(i);
            int c = extent.c.get(i);
            int dR = r - rP;
            int dC = cP - c;
            if ((dR != 1) || (dC != 1)) {
                Extent newExtent = new Extent(extent.r.subList(0, i), extent.c.subList(0, i), extent.values.subList(0, i));
                newExtents.add(newExtent);
                if ((n - i) > 2) {
                    Extent newExtent2 = new Extent(extent.r.subList(i, n), extent.c.subList(i, n), extent.values.subList(i, n));
                    newExtents.add(newExtent2);
                }
            }
        }
        return newExtents;
    }

    double addExtent(Extent extent, List<List<Integer>> allOverlaps, List<Integer> useIndices, boolean[] used, int i, double sum) {
        sum += extent.sum();
        useIndices.add(i);
        List<Integer> overlapIndices = allOverlaps.get(i);
        used[i] = true;
        for (int index : overlapIndices) {
            used[index] = true;
        }
        return sum;
    }

    public int getNExtents() {
        return indices.size();
    }

    public Set<BasePairProbability> getExtentBasePairs(int i) {
        Indices indices1 = indices.get(i);
        extentBasePairs = indices1.basePairProbabilities;
        return extentBasePairs;
    }

    public Set<BasePairProbability> getExtentBasePairs(List<Integer> indexes) {

        List<Extent> finalExtents = new ArrayList<>();
        finalExtents.addAll(uniqueExtents);
        for (var index : indexes) {
            finalExtents.add(overlapExtents.get(index));
        }
        extentBasePairs = new HashSet<>();
        for (Extent extent : finalExtents) {
            for (int k = 0; k < extent.r.size(); k++) {
                int r = extent.r.get(k);
                int c = extent.c.get(k);
                BasePairProbability basePairProbability = new BasePairProbability(r, c, predictions[r][c]);
                extentBasePairs.add(basePairProbability);
            }
        }

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

    public record BasePairProbability(int i, int j, double probability) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BasePairProbability that = (BasePairProbability) o;
            return ((i == that.i) && (j == that.j)) || ((i == that.j) && (j == that.i));
        }

        @Override
        public int hashCode() {
            if (i < j) {
                int result = i;
                result = 31 * result + j;
                return result;
            } else {
                int result = j;
                result = 31 * result + i;
                return result;

            }
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

    public String getDotBracket(Collection<BasePairProbability> basePairProbabilities) {
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