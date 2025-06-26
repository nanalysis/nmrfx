package org.nmrfx.structure.chemistry.predict;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.structure.rna.SSPredictor;

import java.io.*;
import java.util.Set;

public class SSPredictorTest {
    @Test
    public void predictSS() {
        SSPredictor ssPredictor = new SSPredictor();
        String modelFile = "/Users/ekoag/model_143/fine_tune_model.export";
        SSPredictor.setModelFile(modelFile);
        String testFile = "/Users/ekoag/ssPredictorTest/mfold_validate_basepairs.txt";
        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            String line;
            while((line = reader.readLine()) != null) {
                String[] items = line.split(";");
                String seq = items[0];
                String bps = items[1];
                ssPredictor.predict(seq);
                ssPredictor.bipartiteMatch(0.7, 0.05, 20);
                int n = ssPredictor.getNExtents();
                System.out.println("seq " + seq);
                System.out.println("bps " + bps);
                System.out.println("n extents " + n);
                if (n < 1) {
                    System.out.println("no basepairs");
                    continue;
                }
                for (int i = 0; i < n ; i++) {
                    Set<SSPredictor.BasePairProbability> basepairProbabilities = ssPredictor.getExtentBasePairs(i).basePairsSet();
                    basepairProbabilities.forEach(bpp -> System.out.println(bpp.c() + " " + bpp.r()));
                }

            }
            Assert.assertTrue(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
