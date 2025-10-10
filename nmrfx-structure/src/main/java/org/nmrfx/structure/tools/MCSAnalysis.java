package org.nmrfx.structure.tools;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class MCSAnalysis {

    final PeakList peakList;
    PeakList peakListRef = null;
    final int[] iDims;
    final double[] alphas;
    final double[] tols;
    Molecule molecule = null;
    String[] aNames;

    public MCSAnalysis(PeakList peakList, double[] tols, double[] alphas,
                       String[] dimNames, Molecule molecule, String[] aNames) {
        this.molecule = molecule;
        this.peakList = peakList;
        this.alphas = alphas.clone();
        this.tols = tols.clone();
        iDims = new int[dimNames.length];
        if ((alphas.length != tols.length) || (tols.length != dimNames.length)
                || (aNames.length != tols.length)) {
            throw new IllegalArgumentException("Arguments don't have same length");
        }
        peakList.clearSearchDims();
        for (int i = 0; i < tols.length; i++) {
            peakList.addSearchDim(dimNames[i], tols[i]);
            iDims[i] = peakList.getListDim(dimNames[i]);
        }
    }

    public MCSAnalysis(PeakList peakList, double[] tols, double[] alphas,
                       String[] dimNames, PeakList peakListRef) {
        this.peakList = peakList;
        this.peakListRef = peakListRef;
        this.alphas = alphas.clone();
        this.tols = tols.clone();
        iDims = new int[dimNames.length];
        if ((alphas.length != tols.length) || (tols.length != dimNames.length)) {
            throw new IllegalArgumentException("Arguments don't have same length");
        }
        peakList.clearSearchDims();
        for (int i = 0; i < tols.length; i++) {
            peakList.addSearchDim(dimNames[i], tols[i]);
            iDims[i] = peakList.getListDim(dimNames[i]);
        }
    }

    public static class Hit {

        int index;
        String atomName;
        Peak peak;
        double dis;
        int nPeaks;

        public Hit(int index, String atomName, Peak peak, double dis, int nPeaks) {
            this.index = index;
            this.atomName = atomName;
            this.peak = peak;
            this.dis = dis;
            this.nPeaks = nPeaks;
        }

    }

    double getDisMax() {
        double disMax = 0.0;
        for (int j = 0; j < iDims.length; j++) {
            disMax += (tols[j] / alphas[j]) * (tols[j] / alphas[j]);
        }
        disMax = Math.sqrt(disMax);
        return disMax;

    }

    public List<Hit> calc() {
        if (molecule != null) {
            return calcWithResidues();
        } else {
            return calcWithPeaks();
        }
    }

    List<Hit> calcWithResidues() {
        List<Hit> hits = new ArrayList<>();
        double[] ppms = new double[aNames.length];
        double disMax = getDisMax();

        for (Polymer polymer : molecule.getPolymers()) {
            for (Residue residue : polymer.getResidues()) {
                int i = 0;
                String targetAtomName = "";
                for (String aName : aNames) {
                    Atom atom = residue.getAtom(aName);
                    if (targetAtomName.isEmpty()) {
                        targetAtomName = atom.getShortName();
                    }
                    if (atom != null) {
                        PPMv ppmv = atom.getPPM(0);
                        if ((ppmv != null) && (ppmv.isValid())) {
                            ppms[i++] = ppmv.getValue();
                        } else {
                            break;
                        }
                    }
                }
                if (i == aNames.length) {
                    List<Peak> peaks = peakList.findPeaks(ppms);
                    double minDis = disMax;
                    Peak bestPeak = null;
                    for (Peak peak : peaks) {
                        double sum = 0.0;
                        for (int j = 0; j < iDims.length; j++) {
                            double shift = peak.getPeakDim(iDims[j]).getChemShiftValue();
                            double delta = shift - ppms[j];
                            sum += (delta / alphas[j]) * (delta / alphas[j]);
                        }
                        double dis = Math.sqrt(sum);
                        if (dis < minDis) {
                            minDis = dis;
                            bestPeak = peak;
                        }
                    }
                    Hit hit = new Hit(hits.size(), targetAtomName, bestPeak, minDis, peaks.size());
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    List<Hit> calcWithPeaks() {
        List<Hit> hits = new ArrayList<>();
        double[] ppms = new double[tols.length];
        double disMax = getDisMax();
        for (Peak peakRef : peakListRef.peaks()) {

            for (int j = 0; j < iDims.length; j++) {
                ppms[j] = peakRef.getPeakDim(iDims[j]).getChemShiftValue();
            }

            List<Peak> peaks = peakList.findPeaks(ppms);
            double minDis = disMax;
            Peak bestPeak = null;
            for (Peak peak : peaks) {
                double sum = 0.0;
                for (int j = 0; j < iDims.length; j++) {
                    double shift = peak.getPeakDim(iDims[j]).getChemShiftValue();
                    double delta = shift - ppms[j];
                    sum += (delta / alphas[j]) * (delta / alphas[j]);
                }
                double dis = Math.sqrt(sum);
                if (dis < minDis) {
                    minDis = dis;
                    bestPeak = peak;
                }
            }
            Hit hit = new Hit(peakRef.getIdNum(), peakRef.getPeakDim(0).getLabel(), bestPeak, minDis, peaks.size());
            hits.add(hit);
        }
        return hits;
    }

    public double score(List<Hit> hits, double tol) {
        double sum = 0.0;
        int nShifted = 0;
        for (Hit hit : hits) {
            if (hit.dis > tol) {
                sum += hit.dis;
                nShifted++;
            }
        }
        return nShifted > 0 ? sum / nShifted : 0.0;
    }
}
