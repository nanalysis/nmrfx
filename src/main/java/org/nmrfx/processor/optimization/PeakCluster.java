/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.math3.special.Erf;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;

/**
 * Coupling information about a given cluster to facilitate Bipartite Match.
 *
 *
 * @see PeakClusterMatcher
 *
 * @author tedcolon
 */
public class PeakCluster {

    private final List<Peak> linkedPeaks; // all peaks in the cluster
    public final Peak rootPeak;
    private final double tol = 0.25; // tolerance value (in ppm)
    private final List<PeakCluster> clustersWithinTol = new ArrayList<>();
    private final double ppm;
    public final int size;
    public final int iDim;
    public double refScale = 1;
    private PeakCluster pairedTo = null;
    

    public PeakCluster(List<Peak> linkedPeaks, int iDim) {
        this.linkedPeaks = linkedPeaks;
        int shiftDim = iDim == 0 ? 1 : 0;
        this.rootPeak = linkedPeaks.size() > 0 ? getMaxShiftPeakInClus(linkedPeaks, shiftDim) : null;
        this.iDim = iDim;
        this.ppm = rootPeak != null ? rootPeak.getPeakDim(iDim).getAverageShift() : -100.0;
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

    public static Collection<List<Peak>> getCluster(PeakList peakList, int iDim) {
        // TODO: Should rename this static method
        Collection<List<Peak>> linkHashSet = new LinkedHashSet<>();

        peakList.peaks().stream()
                .filter(p -> !p.isDeleted()).
                forEach(p -> p.setStatus(1));
        // CAVEAT: need to make sure peaks are linked
        // FIXME: place check to ensure peaks are linked
        peakList.peaks()
                .forEach(p -> {
                    if (p.getStatus() == 1) {
                        List<Peak> links = PeakList.getLinks(p, iDim).stream()
                                .filter(p2 -> p2.getIntensity() > 0.0)
                                .filter((p2 -> p2.getStatus() == 1))
                                .collect(Collectors.toList());
                        linkHashSet.add(links);
                        links.forEach(lp -> lp.setStatus(0));
                    }
                });
        return linkHashSet;
    }

    public static Peak getMaxShiftPeakInClus(List<Peak> peaks, int shiftDim) {
        Peak maxPeak = null;
        if (peaks.size() > 0) {
            maxPeak = peaks.get(0);
            double maxValue = maxPeak.getPeakDim(shiftDim).getAverageShift();
            Peak currPeak;
            double curValue;
            for (int i = 1; i < peaks.size(); i++) {
                currPeak = peaks.get(i);
                curValue = currPeak.getPeakDim(shiftDim).getAverageShift();
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
        // Print Q information
//        String outQ = "(Q) -> x: %f, Erf(x): %f, q: %f, Q: %f";
//        System.out.println(String.format(outQ, x, Erf.erf(Math.abs(x) / Math.sqrt(2)), q, QPred));
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

        // Print name of peaks
//        System.out.println(String.format("E_Peak: %s, P_Peak: %s", peak1.getName(), peak2.getName()));
        // Print intensity information
//        System.out.println(String.format("E_Intenisty: %f (log-> %f), P_Intensity: %f (log-> %f)", expInt, Math.log(expInt), predInt, Math.log(predInt)));
        expInt = Math.log(expInt);
        predInt = Math.log(predInt);
        double sumOfQs = getQPred(expInt, predInt, intSigma, refDev);

        for (int dim = 0; dim < peak1.getPeakDims().length; dim++) {
            expPPMShift = peak1.getPeakDim(dim).getChemShift();
            predPPMShift = peak2.getPeakDim(dim).getChemShift();
            // Print chemical shift information
//            System.out.println(String.format("E_PPM%d : %f, P_PPM%d: %f", dim, expPPMShift, dim, predPPMShift));
            Qval = getQPred(expPPMShift, predPPMShift, ppmSigma, refDev);
            sumOfQs += Qval;
        }
        // Print final sum of Qs
//        System.out.println(String.format("Sum(Qs) -> %f", sumOfQs));
        return sumOfQs;
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
        String name = rootPeak == null ? "" : String.format("Root Peak: %s", rootPeak.toString());
        return name;
    }
    
    public void setPairedTo(PeakCluster pair) {
        pairedTo = pair;
    }
    
    public PeakCluster getPairedTo() {
        return pairedTo;
    }

    public BipartiteMatcher compareTo(PeakCluster other) {
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
                weight = calcWeight(expPeak, predPeak, other.refScale);
                matcher.setWeight(iE, jP, weight);
            }
        }
        return matcher;
    }

    public List<List<Peak>> getPeakMatches(PeakCluster other) {
        List<List<Peak>> matches = new ArrayList<>();
        BipartiteMatcher peakMatcher = this.compareTo(other);
        int[] matching = peakMatcher.getMatching();
        for (int i = 0; i < matching.length; i++) {
            int j = matching[i];
            if (j < 0) continue;
            List<Peak> matchedPeaks = new ArrayList<>();
            Peak iPeak = null;
            Peak jPeak = null;
            if (i < size)
                iPeak = linkedPeaks.get(i);
            if (j < other.size)
                jPeak = other.linkedPeaks.get(j);
            
            matchedPeaks.add(iPeak);
            matchedPeaks.add(jPeak);
            matches.add(matchedPeaks);
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

    public boolean isInTol(PeakCluster other) {
        boolean withinTol = Math.abs(ppm - other.ppm) < tol;
        if (withinTol && !clustersWithinTol.contains(other)) {
            clustersWithinTol.add(other);
            other.clustersWithinTol.add(this);
        }
        return withinTol;
    }
}
