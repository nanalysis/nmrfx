package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.*;

public class PeakPPMGetter {
    public record PeakListPPMState(Set<SpectralDim> active, Set<SpectralDim> folded) {
    }


    public void getPPMs(Map<PeakList, PeakListPPMState> stateMap, boolean refMode, int index) {
        Molecule molecule = Molecule.getActive();
        Map<Atom, SummaryStatistics> shiftMap = new HashMap<>();
        Map<Atom, Set<SpectralDim>> sDimMap = new HashMap<>();

        for (var entry : stateMap.entrySet()) {
            PeakList peakList = entry.getKey();
            PeakListPPMState state = entry.getValue();
            for (Peak peak : peakList.peaks()) {
                for (SpectralDim spectralDim : state.active) {
                    PeakDim peakDim = peak.getPeakDim(spectralDim.getIndex());
                    getPPMStats(spectralDim, peakDim, state, molecule, shiftMap, sDimMap);
                }
            }
        }
        setPPMs(shiftMap, sDimMap, refMode, index);
    }

    private void getPPMStats(SpectralDim spectralDim, PeakDim peakDim, PeakListPPMState state, Molecule molecule, Map<Atom, SummaryStatistics> shiftMap, Map<Atom, Set<SpectralDim>> sDimMap) {
        List<String> labels = peakDim.getLabels();
        if (labels != null) {
            boolean folded = state.folded.contains(spectralDim);
            for (String label : labels) {
                Atom atom = molecule.findAtom(label);
                if (atom != null) {
                    double ppm = peakDim.getChemShiftValue();
                    if (folded) {
                        ppm = foldPPM(atom, ppm, spectralDim);
                    }
                    SummaryStatistics summaryStatistics = shiftMap.computeIfAbsent(atom, a -> new SummaryStatistics());
                    Set<SpectralDim> set = sDimMap.computeIfAbsent(atom, a -> new HashSet<>());
                    summaryStatistics.addValue(ppm);
                    set.add(spectralDim);
                }
            }
        }
    }

    private double foldPPM(Atom atom, double ppm, SpectralDim spectralDim) {
        Double refPPM = atom.getRefPPM();
        double bestPPM = ppm;
        if (refPPM != null) {
            double sw = spectralDim.getSw();
            double sf = spectralDim.getSf();
            double swPPM = sw / sf;
            double deltaMin = Double.MAX_VALUE;
            int foldNum = 2;
            for (int iFold = -foldNum; iFold <= foldNum; iFold++) {
                double testPPM = ppm + iFold * swPPM;
                double delta = testPPM - refPPM;
                if (Math.abs(delta) < Math.abs(deltaMin)) {
                    deltaMin = delta;
                    bestPPM = testPPM;
                }
            }
        }
        return bestPPM;
    }

    public void setPPMs(Map<Atom, SummaryStatistics> shiftMap, Map<Atom, Set<SpectralDim>> sDimMap, boolean refMode, int index) {
        for (var entry : shiftMap.entrySet()) {
            Atom atom = entry.getKey();
            SummaryStatistics summaryStatistics = entry.getValue();
            var set = sDimMap.get(atom);
            SummaryStatistics sDimStat = new SummaryStatistics();
            for (SpectralDim spectralDim : set) {
                sDimStat.addValue(spectralDim.getIdTol());
            }
            double tol = sDimStat.getMax();
            double ppm = summaryStatistics.getMean();
            double err = Math.max(tol, summaryStatistics.getStandardDeviation());
            if (refMode) {
                atom.setRefPPM(index, ppm);
                atom.setRefError(index, err);
            } else {
                atom.setPPM(index, ppm);
                atom.setPPMError(index, err);
            }
        }
    }
}
