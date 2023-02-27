package org.nmrfx.structure.chemistry.predict;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.chemistry.Compound;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.star.ParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PredictorTest {

    public Compound loadData(String fileName) throws IOException, ParseException, InvalidMoleculeException, InvalidPeakException, MoleculeIOException {
        Path path =  Path.of("src", "test", "data", "mol", fileName);
        String content = Files.readString(path);
        Compound compound = SDFile.read("test", content, null, "A");
        return compound;
    }

    @Test
    public void predict13CWithShells() throws InvalidMoleculeException, IOException, ParseException, MoleculeIOException, InvalidPeakException {
        double[] shifts = {150.94, 127.44, 113.74, 150.99, 127.29, 114.26, 56.24, 28.7, 70.89, 39.57, 24.01, 11.25, 30.62, 29.1, 23.05, 14.1, 28.64};

        Predictor predictor = new Predictor();
        Compound compound = loadData("mol50044.sd");
        predictor.predictWithShells(compound, -1);
        Map<String, Double> ppmMap = new HashMap<>();
        var atoms = compound.atoms;
        for (int i = 0;i< shifts.length;i++) {
            Double ppm = atoms.get(i).getRefPPM();
            Assert.assertNotNull(ppm);
            Assert.assertEquals(shifts[i], ppm.doubleValue(), 0.5);
        }
        System.out.println(ppmMap);
    }

    @Test
    public void predict1HWithShells() throws InvalidMoleculeException, IOException, ParseException, MoleculeIOException, InvalidPeakException {
        Map<Integer, Double> shiftMap = Map.of(16, 2.16, 2, 4.88, 19, 4.99, 4,
                5.68, 15, 6.2, 13, 6.2, 25, 7.39, 31, 7.7);

        Predictor predictor = new Predictor();
        Compound compound = loadData("mol31951.sd");
        predictor.predictWithShells(compound, -1);
        Map<String, Double> ppmMap = new HashMap<>();
        var atoms = compound.atoms;
        int i = 0;
        HosePrediction hosePrediction = HosePrediction.getPredictor();
        for (var atom : atoms) {
            if (atom.getAtomicNumber() == 6) {
                if (shiftMap.containsKey(i)) {
                    String hose = (String) atom.getProperty("hose");
                    PredictResult[] result = hosePrediction.predictHC(hose, "");
                    double ppm = result[0].getStat("1H").getDStat().getPercentile(50);
                    Assert.assertEquals(shiftMap.get(i), ppm, 0.25);
                }
            }
            i++;
        }
    }

}