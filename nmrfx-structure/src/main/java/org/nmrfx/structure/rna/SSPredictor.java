package org.nmrfx.structure.rna;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.NdArrays;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TInt32;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SSPredictor {
    public static final Set<String> validBPs = Set.of("GC", "CG", "AU", "UA", "GU");

    static SavedModelBundle graphModel;
    static String modelFilePath = null;

    public void setModelFile(String fileName) {
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

    public static void load() throws IOException, IllegalArgumentException {
        if (modelFilePath == null) {
            throw new IllegalArgumentException("No model file location set");
        }
        if (graphModel == null) {
            System.out.println(modelFilePath);
            graphModel = SavedModelBundle.load(modelFilePath);
        }
    }

    record DotValue(int position, double value) {
    }

    public String predict(String rnaSequence) throws IOException {
        if (graphModel == null) {
            load();

        }
        String rnaTokens = "AUGC";
        int maxLength = 512;
        int[] tokenized = new int[maxLength];
        int[] mask_positions = new int[maxLength];
        for (int i = 0; i < rnaSequence.length(); i++) {
            tokenized[i] = rnaTokens.indexOf(rnaSequence.charAt(i)) + 2;
            mask_positions[i] = i;
        }
        var inputTF = TInt32.vectorOf(tokenized);
        var maskPositions = TInt32.vectorOf(mask_positions);
        var matrix1 = NdArrays.ofInts(Shape.of(1, 512));
        var matrix2 = NdArrays.ofInts(Shape.of(1, 512));


        matrix1.set(inputTF, 0);
        matrix2.set(maskPositions, 0);

        var inputs = TInt32.tensorOf(matrix1);
        var maskInput = TInt32.tensorOf(matrix2);
        Map<String, Tensor> inputMap = new HashMap<>();
        inputMap.put("input_1", inputs);
        inputMap.put("input_2", maskInput);
        org.tensorflow.Result output = graphModel.function("serving_default").call(inputMap);
        StringBuilder dotBracketPrediction = new StringBuilder();
        System.out.println("shape " + output.get(0).shape());
        var tensor0 = ((TFloat32) output.get(0));
        int seqLen = rnaSequence.length();
        int nCols = 512;
        double[][] predictions = new double[seqLen][seqLen];
        for (int r = 0; r < seqLen; r++) {
            for (int c = r + 2; c < seqLen; c++) {
                int index = r * nCols - (r - 1) * (r - 1 + 1) / 2 + c - 2 - r * 3;
                double prediction = tensor0.getFloat(0, index);
                char rChar = rnaSequence.charAt(r);
                char cChar = rnaSequence.charAt(c);
                String bp = String.valueOf(rChar + String.valueOf(cChar));
                if (!validBPs.contains(bp)) {
                    prediction = 0.0;
                }
                if (prediction > 0.5) {
                    System.out.println(r + " " + c + " " + rChar + " " + cChar + " " + prediction);
                }
                predictions[r][c] = prediction;
            }
        }

        return (dotBracketPrediction.toString());
    }

    private void dotBracketPrediction(String rnaSequence, org.tensorflow.Result output) {
        String rnaTokens = "AUGC";
        String viennaTokens = "xx.ABCabc";
        String viennaBrackets = ".((()))";
        int nOutputs = viennaTokens.length();
        StringBuilder dotBracketPrediction = new StringBuilder();
        List<DotValue> dotValues = new ArrayList<>();
        int nLeft = 0;
        int nRight = 0;
        for (int i = 0; i < rnaSequence.length(); i++) {
            double maxPred = 0.0;
            int jMax = 0;
            double predDot = 0.0;
            for (int j = 2; j < nOutputs; j++) {
                double predValue = ((TFloat32) output.get(0)).getFloat(0, i, j);
                if (j == 2) {
                    predDot = predValue;
                }
                System.out.printf("%.3f ", predValue);
                if (predValue > maxPred) {
                    maxPred = predValue;
                    jMax = j - 2;
                }
            }
            if (jMax != 0) {
                var dotVar = new DotValue(i, predDot);
                dotValues.add(dotVar);
            }
            String token = String.valueOf(viennaBrackets.charAt(jMax));
            if (token.equals("(")) {
                nLeft++;
            } else if (token.equals(")")) {
                nRight++;
            }
            System.out.printf("%.3f %d %s\n", maxPred, jMax, token);
            dotBracketPrediction.append(viennaBrackets.charAt(jMax));
        }
        if (nLeft != nRight) {
            int nExtra = Math.abs(nLeft - nRight);
            dotValues.stream().sorted(Comparator.comparing(DotValue::value).reversed()).limit(nExtra).forEach(dotValue -> {
                dotBracketPrediction.setCharAt(dotValue.position(), '.');
            });
        }

    }

    public String correctPredictions(String rnaSequence, String dotBracket) {
        int n = rnaSequence.length();
        var ssLayout = new SSLayout(n);
        List<String> dotBracketList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            dotBracketList.add((String.valueOf(dotBracket.charAt(i))));
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            stringBuilder.append(dotBracketList.get(i));
        }
        return stringBuilder.toString();
    }

    record RCMax(int row, int column, double prediction) {
    }

    RCMax findAnRCMax(double[][] predictions) {
        int n = predictions.length;
        double max = 0.0;
        int rMax = -1;
        int cMax = -1;
        for (int r = 0; r < n; r++) {
            for (int c = r + 1; c < n; c++) {
                double v = predictions[r][c];
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
        int n = predicted.length;
        for (int i = 0; i < n; i++) {
            RCMax rcMax = findAnRCMax(predicted);
            if (rcMax.row() != -1) {
                int r = rcMax.row();
                int c = rcMax.column;
                for (int j = 0; i < n; j++) {
                    predicted[r][j] = 0.0;
                    predicted[j][r] = 0.0;
                    predicted[c][j] = 0.0;
                    predicted[j][c] = 0.0;
                }
                predicted[r][c] = -rcMax.prediction();
            }
        }
    }

    record BasePair(int i, int j, double probability) {
    }

    List<BasePair> getBasePairs(double[][] predicted, double pLimit) {
        int n = predicted.length;
        double max = 0.0;
        int rMax = -1;
        int cMax = -1;
        List<BasePair> bps = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = r + 2; c < n; c++) {
                if (predicted[r][c] > pLimit) {
                    BasePair bp = new BasePair(r, c, predicted[r][c]);

                }
            }
        }
        return bps;
    }

    void filterCrossings(List<BasePair> basePairs) {
        Map<BasePair, Integer> crossings = new HashMap<>();
        for (BasePair basePair1 : basePairs) {
            int i = basePair1.i;
            int j = basePair1.j;
            for (BasePair basePair2 : basePairs) {
                int m = basePair2.i;
                int n = basePair2.j;
                boolean crossA = false;
                        boolean crossB = false;
                if ((m>i) && (m<j) && (n > j)) {
                    crossA = true;
                }
                if ((j > m) && (j < n) && (i < m)) {
                    crossB = true;
                }
                if (crossA || crossB) {
                    crossings.compute(basePair1, (basePair, value) -> (value == null) ? 0 : value + 1);
                    crossings.compute(basePair2, (basePair, value) -> (value == null) ? 0 : value + 1);
                }

            }
        }

    }
    /*
    def filterCrossings(bps):
    crossings = {}
    crossings = {}
    for bp in bps:
        (i,j) = bp
        for bp2 in bps:
            crossA = False
            crossB = False
            (m,n) = bp2
            if m > i and m < j and n > j:
                 crossA= True
            if j > m and j < n and i < m:
                 crossB = True
            if crossA or crossB:
                if not bp in crossings:
                    crossings[bp] = 0
                if not bp2 in crossings:
                    crossings[bp2] = 0
                crossings[bp] += 1
                crossings[bp2] += 1
                print(i,j,m,n,crossA,crossB)
    bpMax = None
    maxValue = 0
    for bp in crossings:
        if crossings[bp] > maxValue:
            bpMax = bp
            maxValue = crossings[bp]
    if bpMax == None:
        return bps,bpMax
    else:
        fBPs = {}
        for bp in bps:
            if  bp != bpMax:
                fBPs[bp] = bps[bp]
        return fBPs,bpMax

def filterAllCrossings(bps):
    bpMax = True
    while(bpMax):
        bps,bpMax = filterCrossings(bps)
    return bps

     */
}
