/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization;

import org.apache.commons.math3.special.Erf;
import org.nmrfx.math.BipartiteMatcher;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Coupling information about a given cluster to facilitate Bipartite Match.
 *
 * @author tedcolon
 * @see PeakClusterMatcher
 */
public class PeakCluster {

    private final List<Peak> linkedPeaks; // all peaks in the cluster
    public final Peak rootPeak;

    private final double tol = 0.25; // tolerance value (in ppm)
    private final List<PeakCluster> clustersWithinTol = new ArrayList<>();
    private final double origPPM;
    private double ppm;
    public final int size;
    public final int iDim;
    private PeakCluster pairedTo = null;
    private int[] peakMatches = null;
    private boolean isFrozen = false;

    public PeakCluster(List<Peak> linkedPeaks, int iDim) {
        this.linkedPeaks = linkedPeaks;
        int shiftDim = iDim == 0 ? 1 : 0;
        this.rootPeak = linkedPeaks.size() > 0 ? getMaxShiftPeakInClus(linkedPeaks, shiftDim) : null;
        this.iDim = iDim;
        this.origPPM = rootPeak != null ? rootPeak.getPeakDim(iDim).getAverageShift() : -100.0;
        this.ppm = this.origPPM;
        this.size = linkedPeaks.size();
    }

    public static PeakCluster[] makePeakCluster(Collection<List<Peak>> clusters, int iDim) {
        PeakCluster[] peakClusters = new PeakCluster[clusters.size()];
        int i = 0;
        for (List<Peak> cluster : clusters) {
            PeakCluster iCluster = new PeakCluster(cluster, iDim);
            peakClusters[i] = iCluster;
            i++;
        }
        return peakClusters;
    }

    public void setShift(double newPPM) {
        for (Peak peak : linkedPeaks) {
            peak.getPeakDim(iDim).setChemShiftValue((float) newPPM);
        }
        ppm = newPPM;
    }

    public void restoreShift() {
        for (Peak peak : linkedPeaks) {
            peak.getPeakDim(iDim).setChemShiftValue((float) origPPM);
        }

    }

    public static void prepareList(PeakList peakList, double[][] limits) {
        peakList.peaks().stream().
                filter(p -> !p.isDeleted()).
                filter(p -> {
                    double xShift = p.getPeakDim(0).getChemShiftValue();
                    double yShift = p.getPeakDim(1).getChemShiftValue();
                    boolean ok = (xShift > limits[0][0]) && (xShift < limits[0][1])
                            && (yShift > limits[1][0]) && (yShift < limits[1][1]);
                    return ok;
                }).
                forEach(p -> p.setStatus(1));

    }

    public static PeakCluster[] getNonFrozenClusters(PeakCluster[] clusters) {
        Collection<PeakCluster> clusterBuffer = new ArrayList<>();
        for (PeakCluster cluster : clusters) {
            if (cluster.isFrozen()) {
                continue;
            }
            clusterBuffer.add(cluster);
        }
        PeakCluster[] nonFrozenClusters = new PeakCluster[clusterBuffer.size()];
        return clusterBuffer.toArray(nonFrozenClusters);
    }

    public static Collection<List<Peak>> getFilteredClusters(List<PeakList> peakLists, int iDim) {
        // TODO: Should rename this static method
        Collection<List<Peak>> linkHashSet = new LinkedHashSet<>();

        // CAVEAT: need to make sure peaks are linked
        // FIXME: place check to ensure peaks are linked
        for (PeakList peakList : peakLists) {
            double tol = 1.0;
            peakList.peaks()
                    .forEach(p -> {
                        String dimName = p.getPeakDim(iDim).getSpectralDimObj().getDimName();
                        final double shift = p.getPeakDim(iDim).getChemShiftValue();
                        if (p.getStatus() == 1) {
                            List<Peak> links = PeakList.getLinkedPeakDims(p, iDim).stream()
                                    .filter(p2 -> p2.getPeak().getIntensity() > 0.0)
                                    .filter((p2 -> p2.getPeak().getStatus() == 1))
                                    .filter(p2 -> !p2.getPeak().getPeakDim(iDim).isFrozen())
                                    .filter(p2 -> dimName.equals(p2.getSpectralDimObj().getDimName()))
                                    .map(p2 -> (Peak) p2.getPeak())
                                    .collect(Collectors.toList());
                            linkHashSet.add(links);
                            links.forEach(lp -> lp.setStatus(0));
                        }
                    });
        }
        return linkHashSet;
    }

    public static Peak getMaxShiftPeakInClus(List<Peak> peaks, int shiftDim) {
        Peak maxPeak = null;
        if (peaks.size() > 0) {
            maxPeak = peaks.get(0);
            double maxValue = maxPeak.getPeakDim(shiftDim).getChemShiftValue();
            for (int i = 1; i < peaks.size(); i++) {
                Peak currPeak = peaks.get(i);
                double curValue = currPeak.getPeakDim(shiftDim).getChemShiftValue();
                if (curValue > maxValue) {
                    maxValue = curValue;
                    maxPeak = currPeak;
                }
            }
        }
        return maxPeak;
    }

    private static double getQ(double x) {
        double SQRT2 = Math.sqrt(2);
        double q = Math.log(1.0 - Erf.erf(Math.abs(x) / SQRT2));
        return q;
    }

    public static double getQPred(double exp, double pred, double sigma, double x0) {
        double x = (exp - pred) / sigma; // should it be predAvg - ppm?
        double q = getQ(x);
        double q0 = getQ(x0);
        double QPred = 1.0 + (q / Math.abs(q0));
        return QPred;
    }

    /*
       CAVEAT: needs reference scale value for intensity to ensure intensities
       are in the same order of magnitude.
     */
    public static double calcWeight(Peak peak1, Peak peak2, double refScale) {
        if (peak1 == null || peak2 == null) {
            throw new IllegalArgumentException("Null arguments are invalid.");
        }
        // inits
        // FIXME: update sigma values and reference deviation value.

        double intSigma = 3.0;
        double ppmSigma = 0.1;
        double refDev = 1.5;
        double expPPMShift;
        double predPPMShift;
        double expInt = peak1.getIntensity();
        double predInt = peak2.getIntensity() * refScale;
        double Qval;

        expInt = Math.log(expInt);
        predInt = Math.log(predInt);
        double sumOfQs = getQPred(expInt, predInt, intSigma, refDev);

        for (int dim = 0; dim < peak1.getPeakDims().length; dim++) {
            expPPMShift = peak1.getPeakDim(dim).getChemShift();
            predPPMShift = peak2.getPeakDim(dim).getChemShift();
            double delta = Math.abs(expPPMShift - predPPMShift);
            if (peak2.getPeakDim(dim).isFrozen() && delta > 0.01) {// fixme need valid tol
                Qval = -1000; // fixme  need appropriate value
            } else {
                // Print chemical shift information
                Qval = getQPred(expPPMShift, predPPMShift, ppmSigma, refDev);
            }
            sumOfQs += Qval;
        }
        return sumOfQs;
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    public void setFreeze(boolean freeze) {
        this.isFrozen = freeze;
    }

    public List<Peak> getLinkedPeaks() {
        return linkedPeaks;
    }

    public boolean equalSize(PeakCluster other) {
        return size == other.size;
    }

    public boolean contains(Peak peak) {
        return linkedPeaks.stream().anyMatch((p) -> (p.equals(peak)));
    }

    @Override
    public String toString() {
        String name = rootPeak == null ? "" : String.format("Root Peak: %s %7.2f", rootPeak.toString(), ppm);
        return name;
    }

    public String dump() {
        linkedPeaks.sort((p1, p2) -> p1.peakDims[iDim].getChemShift().compareTo(p2.peakDims[iDim].getChemShift()));
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(toString()).append(" :");
        for (Peak peak : linkedPeaks) {
            sBuilder.append(" ").append(peak.getName());
        }
        return sBuilder.toString();
    }

    public void setPairedTo(PeakCluster otherCluster) {
        this.pairedTo = otherCluster;
    }

    public PeakCluster getPairedTo() {
        return pairedTo;
    }

    public BipartiteMatcher compareTo(PeakCluster other) {
        if (other == null) {
            return null;
        }
        BipartiteMatcher matcher = new BipartiteMatcher();
        int N = size + other.size;
        matcher.reset(N, true);
        double weight;
        Peak expPeak, predPeak;
        // init
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matcher.setWeight(i, j, 0.0);
            }
        }

        for (int iE = 0; iE < size; iE++) {
            for (int jP = 0; jP < other.size; jP++) {
                expPeak = linkedPeaks.get(iE);
                predPeak = other.linkedPeaks.get(jP);
                weight = calcWeight(expPeak, predPeak, predPeak.getPeakList().scale);
                matcher.setWeight(iE, jP, weight);
            }
        }
        return matcher;
    }

    public double comparisonScore(PeakCluster other) {
        BipartiteMatcher matcher = this.compareTo(other);
        double score = Double.NEGATIVE_INFINITY;
        if (matcher != null) {
            double minWeight = matcher.getMinWeight();
            int[] matchings = matcher.getMatching();
            score = matcher.getMaxWtSum(matchings, minWeight);
        }
        return score;
    }

    public void setPeakMatches(int[] peakMatches) {
        this.peakMatches = peakMatches;
    }

    public int[] getPeakMatches() {
        return peakMatches;
    }

    public List<List<Peak>> getPeakMatches(PeakCluster other) {
        List<List<Peak>> matches = new ArrayList<>();

        // reason for this:
        // Provides option to calculate possible peak matches if one doesn't already exist.
        int[] matching = (getPairedTo().equals(other))
                ? getPeakMatches() : this.compareTo(other).getMatching();
        if (matching != null) {
            for (int i = 0; i < matching.length; i++) {
                int j = matching[i];
                if (j < 0) {
                    continue;
                }
                List<Peak> matchedPeaks = new ArrayList<>();

                // ensures that iPeak is always the experimental peak
                // and jPeak is always the predicted peak
                boolean rootIsSim = rootPeak.getPeakList().isSimulated();
                Peak iPeak = (rootIsSim) && (i < other.size)
                        ? other.linkedPeaks.get(i) : (!rootIsSim) && (i < size)
                        ? linkedPeaks.get(i) : null;
                Peak jPeak = (rootIsSim) && (j < size)
                        ? linkedPeaks.get(j) : (!rootIsSim) && (j < other.size)
                        ? other.linkedPeaks.get(j) : null;
                matchedPeaks.add(iPeak);
                matchedPeaks.add(jPeak);
                matches.add(matchedPeaks);
            }
        }
        return matches;
    }

    public List<PeakCluster> getTolClusters() {
        return clustersWithinTol;
    }

    public String getTolListString() {
        if (!clustersWithinTol.isEmpty()) {
            String output = "";
            for (PeakCluster pc : clustersWithinTol) {
                String rtString = pc.rootPeak.getName();
                if (!rtString.isEmpty()) {
                    output = output.isEmpty() ? rtString : String.join(" ", output, rtString);
                }
            }
            return output;
        }
        return null;
    }

    public double clusterDistance(PeakCluster other) {
        return Math.abs(ppm - other.ppm);
    }

    public boolean isInTol(PeakCluster other) {
        boolean withinTol = Math.abs(ppm - other.ppm) < tol;
        if (withinTol && !clustersWithinTol.contains(other)) {
            clustersWithinTol.add(other);
            other.clustersWithinTol.add(this);
        }
        return withinTol;
    }
}
