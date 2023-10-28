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
        String viennaTokens = "xx.ABCabc";
        String viennaBrackets = ".((()))";
        int nOutputs = viennaTokens.length();
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
        var output = graphModel.function("serving_default").call(inputMap);
        StringBuilder dotBracketPrediction = new StringBuilder();
        int nLeft = 0;
        int nRight = 0;
        List<DotValue> dotValues = new ArrayList<>();
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

        return (dotBracketPrediction.toString());
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
}
