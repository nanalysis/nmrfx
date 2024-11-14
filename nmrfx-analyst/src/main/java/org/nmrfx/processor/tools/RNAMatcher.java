package org.nmrfx.processor.tools;

import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.peaks.PeakGenerator;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.NUSConScore;
import org.nmrfx.processor.datasets.peaks.PeakLinker;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RNAMatcher {
    private static final Logger log = LoggerFactory.getLogger(RNAMatcher.class);

    List<PeakMatch> peakMatches = new ArrayList<>();
    double[][][] noesyRegion = {
            {{6.5, 9.0}, {3.5, 6.5}},
            {{6.5, 9.0}, {6.5, 9.5}}
    };
    double[][][] cosyRegion = {
            {{7, 8.5}, {5.0, 6.0}}
    };
    double[][][] hmqcRegion = {
            {{5, 6.5}, {85, 110.0}},
            {{6, 9.0}, {130.0, 160.0}},
            {{3.5, 5.5}, {65, 90.0}}
    };


    Map<String, double[][][]> regions = new HashMap<>();

    record PeakMatch(String type, PeakList expList, PeakList simList) {
    }

    public void predict() {
        Predictor predictor = new Predictor();
        Predictor.PredictionTypes predictionTypes =
                new Predictor.PredictionTypes(Predictor.PredictionModes.THREED, Predictor.PredictionModes.RNA_ATTRIBUTES, Predictor.PredictionModes.SHELL);
        try {
            predictor.predictAll(Molecule.getActive(), predictionTypes, -1);
        } catch (InvalidMoleculeException | IOException e) {
            ExceptionDialog dialog = new ExceptionDialog(e);
            dialog.showAndWait();
        }
    }
    public void genPeaks() {
        peakMatches.clear();
        var simLists = PeakList.peakLists().stream().filter(peakList -> peakList.getName().endsWith("_sim")).toList();
        for (PeakList peakList : simLists) {
            peakList.remove();
        }

        var peakLists = PeakList.peakLists().stream().filter(peakList -> !peakList.getName().endsWith("_sim")).toList();
        peakLists.forEach(peakList -> {
            PeakGenerator peakGenerator = new PeakGenerator(0, 0);
            Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
            PeakList newPeakList = peakList.copy(peakList.getName() + "_sim", false, false, false);
            newPeakList.peaks().clear();
            newPeakList.reIndex();
            newPeakList.setSampleConditionLabel("sim");
            boolean ok = true;
            String type = peakList.getExperimentType();
            switch (type) {
                case "NOESY" -> {
                    regions.put(type, noesyRegion);
                    peakGenerator.generateRNANOESYSecStr(dataset, newPeakList, 0);
                }
                case "COSY" -> {
                    regions.put(type, cosyRegion);
                    peakGenerator.generateTOCSY(newPeakList, 3);
                }
                case "13C-HSQC" -> {
                    regions.put(type, hmqcRegion);
                    peakGenerator.generateHSQC(newPeakList, 6);
                }
                default -> {
                    ok = false;
                    log.error("Wrong type {} {}", peakList.getName(), peakList.getExperimentType());
                }
            }
            if (ok) {
                PeakMatch peakMatch = new PeakMatch(type, peakList, newPeakList);
                peakMatches.add(peakMatch);
            }
        });
        PeakLinker linker = new PeakLinker();
        linker.linkAllPeakListsByLabel("sim");
    }

    public double score() {
        int[] dims = {0, 1};
        double sumScore = 0.0;
        int n = 0;
        for (PeakMatch peakMatch : peakMatches) {
            double[][][] bounds = regions.get(peakMatch.type);
            for (double[][] bound : bounds) {
                var expPeaks = peakMatch.expList.locatePeaks(bound, dims);
                var simPeaks = peakMatch.simList.locatePeaks(bound, dims);
                double[] scale = {bound[0][1] - bound[0][0],bound[1][1] - bound[1][0] };
                double score = NUSConScore.normSymHausDorff(expPeaks, simPeaks, scale,0.05);
                sumScore += score;
                n++;
            }
        }
        return n == 0 ? 0.0 : sumScore / n;
    }
}
