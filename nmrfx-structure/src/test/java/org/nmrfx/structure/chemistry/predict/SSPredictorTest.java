package org.nmrfx.structure.chemistry.predict;

import org.junit.Test;
import org.nmrfx.structure.rna.SSPredictor;

import java.io.*;
import java.util.*;

public class SSPredictorTest {

    double[] evaluate(Set<SSPredictor.BasePairProbability> referenceSet, Set<SSPredictor.BasePairProbability> predictedSet) {
        if (predictedSet.isEmpty()) {
            return new double[]{0.0, 0.0, 0.0};
        }
        Set<SSPredictor.BasePairProbability> refCopy = new HashSet<>(referenceSet);
        Set<SSPredictor.BasePairProbability> predCopy = new HashSet<>(predictedSet);

        refCopy.removeAll(predictedSet);
        predCopy.removeAll(referenceSet);

        int fn = refCopy.size();
        int fp = predCopy.size();

        int tp = predictedSet.size() - fp;

        double ppv = (double) tp / (tp + fp);
        double recall = (double) tp / (tp + fn);
        double f1 = 0.0;
        if (ppv > 0.0 && recall > 0.0) {
            f1 = 2 * (ppv * recall) / (ppv + recall);
        }
        return new double[]{ppv, recall, f1};
    }

    public void predictSS(String testFile, String modelFile, String outFilePath) {
        List<double[]> scores = new ArrayList<>();
        SSPredictor.setModelFile(modelFile);
        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            String line;
            StringBuilder sb = new StringBuilder();
            while((line = reader.readLine()) != null) {
                SSPredictor ssPredictor = new SSPredictor();
                String[] items = line.split(";");
                String seq = items[0];
                String[] bps = {};
                if (!items[1].isEmpty()) {
                    bps = items[1].split(",");
                }
                System.out.println(seq);
                ssPredictor.predict(seq);
                ssPredictor.bipartiteMatch(0.7, 0.05, 20);

                Set<SSPredictor.BasePairProbability> referenceSet = new HashSet<>();
                for (int i = 0; i < bps.length - 2; i += 2) {
                    int bp1 = Integer.parseInt(bps[i]);
                    int bp2 = Integer.parseInt(bps[i+1]);
                    SSPredictor.BasePairProbability bpp = new SSPredictor.BasePairProbability(bp1, bp2, 0.0);
                    referenceSet.add(bpp);
                }

                int n = ssPredictor.getNExtents();
                assert n > 0;

                double max_f1 = -1.0;
                double[] max_result = null;
                for (int i = 0; i < n ; i++) {
                    Set<SSPredictor.BasePairProbability> predictedSet = ssPredictor.getExtentBasePairs(i).basePairsSet();

                    double[] results = evaluate(referenceSet, predictedSet);
                    System.out.println(Arrays.toString(results));
                    double f1 = results[2];
                    if (f1 > max_f1) {
                        max_result = results;
                    }
                }
                if (max_result != null) {
                    scores.add(max_result);
                    Arrays.stream(max_result).forEach(result -> sb.append(result).append(" "));
                    sb.append("\n");
                }
            }
            try (FileWriter fileWriter = new FileWriter(outFilePath)) {
                fileWriter.write(sb.toString());
            }

            double ppv_sum = 0.0;
            double recall_sum = 0.0;
            double f1_sum = 0.0;
            for (double[] result : scores) {
                ppv_sum += result[0];
                recall_sum += result[1];
                f1_sum += result[2];
            }
            double precision = ppv_sum/scores.size();
            double recall = recall_sum /scores.size();
            double f1 = f1_sum/scores.size();
            System.out.println("PPV: " + precision + " Recall: " +recall + "  F1: " + f1);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
