package org.nmrfx.processor.optimization;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;

/**
 * Performs Bipartite Match on two peak lists, an experimental and predicted
 * peak list. The peaks that are associated based on chemical shift values in a
 * given dimension are clustered before this algorithm runs. These experimental
 * and predicted clusters are compared and weights are generated that reflect
 * the similarity of peaks, in each cluster, and the overall similarity of the
 * compared clusters.
 *
 *
 * @see PeakCluster
 *
 * @author tedcolon
 */
public class PeakClusterMatcher {

    private final PeakList expPeakList;
    private final PeakList predPeakList;
    private PeakCluster[] expPeakClusters = null;
    private PeakCluster[] predPeakClusters = null;
    private int[] match = null;
    private List<PeakCluster[]> matchedClusters = null;
    private final int iDim;
    public final double REF_SCALE;

    public PeakClusterMatcher(PeakList expPeakList, PeakList predPeakList, int iDim) {
        this.expPeakList = expPeakList;
        this.predPeakList = predPeakList;
        this.iDim = iDim;
        DescriptiveStatistics eDStats = expPeakList.intensityDStats(iDim);
        DescriptiveStatistics pDStats = predPeakList.intensityDStats(iDim);
        double eIntMedian = eDStats.getPercentile(50);
        double pIntMedian = pDStats.getPercentile(50);
        REF_SCALE = eIntMedian / pIntMedian;
    }

    public PeakCluster[] getExpPeakClus() {
        return expPeakClusters;
    }

    public PeakCluster[] getPredPeakClus() {
        return predPeakClusters;
    }

    public List<PeakCluster[]> getMatch() {
        if (match == null) {
            return null;
        }
        if (expPeakClusters != null && predPeakClusters != null && matchedClusters == null) {
            matchedClusters = new ArrayList<>();
            // find matches
            for (int i = 0; i < match.length; i++) {
                int j = match[i];
                if (j < 0) {
                    continue;
                }
                PeakCluster[] ijMatched = new PeakCluster[2];
                System.out.println(String.format("E(%s) -> P(%s)", expPeakClusters[i].toString(), predPeakClusters[j].toString()));
                // TODO: change from array to List?
                PeakCluster expCluster = expPeakClusters[i];
                PeakCluster predCluster = predPeakClusters[j];
                ijMatched[0] = expCluster;
                ijMatched[1] = predCluster;
                matchedClusters.add(ijMatched);
                if (expCluster.getPairedTo() == null && predCluster.getPairedTo() == null) {
                    expCluster.setPairedTo(predCluster);
                    predCluster.setPairedTo(expCluster);
                }
            }
        }
        return matchedClusters;
    }

    // main method to call
    public void runMatch() throws IllegalArgumentException {
        if (match == null) {
            System.out.println("Running match method");
            Collection<List<Peak>> expLinks = PeakCluster.getCluster(expPeakList, iDim);
            Collection<List<Peak>> predLinks = PeakCluster.getCluster(predPeakList, iDim);
            expPeakClusters = PeakCluster.makePeakCluster(expLinks, iDim);
            predPeakClusters = PeakCluster.makePeakCluster(predLinks, iDim);
            runBPClusterMatches();
        }
    }

    public List<List<Peak>> getPeakPairs(Peak peak) {
        PeakCluster cluster = null;
        // check if peak is in any of the pred clusters
        for (PeakCluster pc : predPeakClusters) {
            if (pc.contains(peak) && pc.getPairedTo() != null) {
                cluster = pc;
                break;
            }
        }
        // if its not, then cluster will still be null
        // check if the peak is in any of the exp cluster
        if (cluster == null) {
            for (PeakCluster pc : expPeakClusters) {
                if (pc.contains(peak) && pc.getPairedTo() != null) {
                    cluster = pc;
                    break;
                }
            }

        }
        List<List<Peak>> result = new ArrayList<>();
        if (cluster != null) {
            result.addAll(cluster.getPeakMatches(cluster.getPairedTo()));
        }
        return result;
    }

    private void runBPClusterMatches() throws IllegalArgumentException {
        // initializations
        PeakCluster iExpClus, jPredClus;
        BipartiteMatcher clusterMatcher = new BipartiteMatcher();
        int nClusters = expPeakClusters.length + predPeakClusters.length;
        clusterMatcher.reset(nClusters, true);

        // main loop
        for (int i = 0; i < expPeakClusters.length; i++) {
            iExpClus = expPeakClusters[i];
            for (int j = 0; j < predPeakClusters.length; j++) {
                jPredClus = predPeakClusters[j];
                if (iExpClus.isInTol(jPredClus)) {
                    jPredClus.refScale = REF_SCALE;
                    BipartiteMatcher peakMatcher = iExpClus.compareTo(jPredClus);
                    double wMin = peakMatcher.minWeight;
                    int[] peakMatching = peakMatcher.getMatching();
                    double peakMaxSum = peakMatcher.getMaxWtSum(peakMatching, wMin);
                    clusterMatcher.setWeight(i, j, peakMaxSum);
                }
            }
        }
        match = clusterMatcher.getMatching();
    }
}
