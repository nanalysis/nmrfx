package org.nmrfx.structure.chemistry.predict;

import org.junit.Test;
import org.nmrfx.structure.rna.SSPredictor;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class SSPredictorTest {

    @Test
    public void predict() {
        String modelFile = "/Users/ekoag/model_143/fine_tune_model.export";
        SSPredictor.setModelFile(modelFile);
        SSPredictor ssPredictor = new SSPredictor();
        String sequence = "CCCUUAUCAAGAGAGGCGGAGGGAACAGACCCUGUGAUGCCCGGCAACCUGCGGUUUGCAAGGUGCCAAUUUCUGCGGGAAAACCGGAAGAUGAGGU";
        ssPredictor.predict(sequence);
        ssPredictor.bipartiteMatch(0.7, 0.05, 20);
        int n = ssPredictor.getNExtents();
        System.out.println(n);
        for (int i = 0; i < n ; i++) {
            Set<SSPredictor.BasePairProbability> bpps = ssPredictor.getExtentBasePairs(i).basePairsSet();
            System.out.println(bpps.stream().toList());
        }

    }
    @Test
    public void predictSS() {
        String testFile = "/Users/ekoag/ssPredictorTest/mfold_validate_basepairs.txt";
        List<Float> scores = new ArrayList<>();
        String modelFile = "/Users/ekoag/model_143/fine_tune_model.export";
        SSPredictor.setModelFile(modelFile);
        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            String line;
            while((line = reader.readLine()) != null) {
                SSPredictor ssPredictor = new SSPredictor();
                String[] items = line.split(";");
                String seq = items[0];
                String[] bps = items[1].split(",");
                ssPredictor.predict(seq);
                ssPredictor.bipartiteMatch(0.7, 0.05, 20);

                System.out.println("seq " + seq);

                Set<SSPredictor.BasePairProbability> basepairSet = new HashSet<>();
                for (int i = 0; i < bps.length - 2; i += 2) {
                    int bp1 = Integer.parseInt(bps[i]);
                    int bp2 = Integer.parseInt(bps[i+1]);
                    SSPredictor.BasePairProbability bpp = new SSPredictor.BasePairProbability(bp1, bp2, 0.0);
                    basepairSet.add(bpp);
                }

                int n = ssPredictor.getNExtents();
                if (n < 1) {
                    System.out.println("no nExtents");
                    System.exit(0);
                }

                int min = 10000;
                int nPairs = basepairSet.size();
                for (int i = 0; i < n ; i++) {
                    Set<SSPredictor.BasePairProbability> pbpps = ssPredictor.getExtentBasePairs(i).basePairsSet();
                    basepairSet.removeAll(pbpps);
                    int nIncorrect = basepairSet.size();
                    if (nIncorrect < min) {
                        min = nIncorrect;
                    }
                }
                float score = (float) (nPairs- min) / nPairs;
                scores.add(score);
                System.out.println(score);
            }
            float sum = 0;
            for (float score : scores) {
                sum += score;
            }
            System.out.println(sum/scores.size());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
