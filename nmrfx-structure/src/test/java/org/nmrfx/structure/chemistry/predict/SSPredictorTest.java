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

        refCopy.removeAll(predictedSet); // missed hits (false negatives)
        predCopy.removeAll(referenceSet); // false hits (false positives)

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

    public record result(Set<SSPredictor.BasePairProbability> bpp, double[] scores) {}

    public result predict(SSPredictor ssPredictor, String seq, Set<SSPredictor.BasePairProbability>  referenceSet) {
        ssPredictor.predict(seq);
        ssPredictor.bipartiteMatch(0.7, 0.05, 20);
        int n = ssPredictor.getNExtents();
        assert n > 0;

        double max_f1 = -1.0;
        result best = null;
        for (int i = 0; i < n ; i++) {
            Set<SSPredictor.BasePairProbability> predictedSet = ssPredictor.getExtentBasePairs(i).basePairsSet();

            double[] results = evaluate(referenceSet, predictedSet);
            double f1 = results[2];
            if (f1 > max_f1) {
                best = new result(predictedSet, results);
            }
        }
        return best;
    }

    public void score(List<double[]> scores, StringBuilder sb) {
        double ppv_sum = 0.0;
        double recall_sum = 0.0;
        double f1_sum = 0.0;
        for (double[] result : scores) {
            ppv_sum += result[0];
            recall_sum += result[1];
            f1_sum += result[2];
        }
        double precision = ppv_sum / scores.size();
        double recall = recall_sum / scores.size();
        double f1 = f1_sum / scores.size();
        sb.append("PPV: ").append(String.format("%.3f", precision))
                .append(" Recall: ").append(String.format("%.3f", recall))
                .append(" F1: ").append(String.format("%.3f", f1));
    }

    public Set<SSPredictor.BasePairProbability> makeSet(String[] bps) {
        Set<SSPredictor.BasePairProbability> bppSet = new HashSet<>();
        for (int i = 0; i < bps.length - 2; i += 2) {
            int bp1 = Integer.parseInt(bps[i]);
            int bp2 = Integer.parseInt(bps[i+1]);
            SSPredictor.BasePairProbability bpp = new SSPredictor.BasePairProbability(bp1, bp2, 0.0);
            bppSet.add(bpp);
        }
        return bppSet;
    }

    public void evaluateOnly(String predFile, String expFile, String outFile) throws IOException {
        List<double[]> scores = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader1 = new BufferedReader(new FileReader(predFile));
             BufferedReader reader2 = new BufferedReader(new FileReader(expFile))) {
            String line1;
            String line2;

            while ((line1 = reader1.readLine()) != null && (line2 = reader2.readLine()) != null) {
                String[] items = line1.split(";");
                String seq1 = items[0];
                sb.append(seq1).append(" ");
                String[] bps1 = items[1].split(",");
                Set<SSPredictor.BasePairProbability> predSet = makeSet(bps1);

                String[] items2 = line2.split(";");
                String seq2 = items2[0];
                assert seq1.equals(seq2);
                String[] bps2 = items[1].split(",");
                Set<SSPredictor.BasePairProbability> expSet = makeSet(bps2);

                double[] results = evaluate(expSet, predSet);
                scores.add(results);
                Arrays.stream(results).forEach(result -> sb.append(String.format("%.3f", result)).append(" "));
                sb.append("\n");

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        score(scores, sb);
        try (FileWriter fileWriter = new FileWriter(outFile)) {
            fileWriter.write(sb.toString());
        }
    }

    public void inference(String testFile, String modelPath, String outFile, boolean doScoring) {
        List<double[]> scores = new ArrayList<>();
        SSPredictor.setModelFile(modelPath);
        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            String line;
            StringBuilder sb = new StringBuilder();
            while((line = reader.readLine()) != null) {
                String[] items = line.split(";");
                String seq = items[0];
                String[] bps = {};
                if (!items[1].isEmpty()) {
                    bps = items[1].split(",");
                }
                sb.append(seq).append(";");

                SSPredictor ssPredictor = new SSPredictor();
                Set<SSPredictor.BasePairProbability> referenceSet = makeSet(bps);
                result best = predict(ssPredictor, seq, referenceSet);

                if (best != null) {
                    if (doScoring) {
                        scores.add(best.scores);
                        Arrays.stream(best.scores).forEach(result -> sb.append(String.format("%.3f", result)).append(" "));
                        sb.append("\n");
                    } else {
                        best.bpp.forEach(bpp -> sb.append(bpp.c()).append(",").append(bpp.r()).append(","));
                        sb.deleteCharAt(sb.length() - 1);
                        sb.append("\n");
                    }
                }
            }
            if (doScoring) {
                score(scores, sb);
            }

            try (FileWriter fileWriter = new FileWriter(outFile)) {
                fileWriter.write(sb.toString());
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
@Test
public void runInference() {
        String testFile = "/Users/ekoag/rna_ss_models/RNAStrAlign_test.txt";
        String modelPath = "/Users/ekoag/rna_ss_models/model_224/fine_tune_model.export/";
        String outFile = "/Users/ekoag/rna_ss_models/predicted_bps.out";
        inference(testFile, modelPath, outFile, false);
    }
@Test
public void runEvaluation() throws IOException {
        String predFile = "/Users/ekoag/rna_ss_models/ufold_predictions.txt";
        String expFile = "/Users/ekoag/rna_ss_models/RNAStrAlign_test.txt";
        String outFile = "/Users/ekoag/rna_ss_models/ufold_scores.txt";
        evaluateOnly(predFile, expFile, outFile);
}
}
