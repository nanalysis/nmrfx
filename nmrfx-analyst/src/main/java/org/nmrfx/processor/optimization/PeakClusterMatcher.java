package org.nmrfx.processor.optimization;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.math.BipartiteMatcher;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Performs Bipartite Match on two peak lists, an experimental and predicted
 * peak list. The peaks that are associated based on chemical shift values in a
 * given dimension are clustered before this algorithm runs. These experimental
 * and predicted clusters are compared and weights are generated that reflect
 * the similarity of peaks, in each cluster, and the overall similarity of the
 * compared clusters.
 *
 * @author tedcolon
 * @see PeakCluster
 */
public class PeakClusterMatcher {

    private final List<PeakList> expPeakLists;
    private final List<PeakList> predPeakLists;
    // FIXME: Should probably consider having a list of PeakCluster[]
    // to compliment the list of PeakList for the exp and pred clusters.
    private PeakCluster[] expPeakClusters = null;
    private PeakCluster[] predPeakClusters = null;
    private int[] clusterMatch = null;
    private List<PeakCluster[]> matchedClusters = null;
    private final int iDim;

    public PeakClusterMatcher(List<PeakList> expPeakLists, List<PeakList> predPeakLists, int iDim) {
        this.expPeakLists = expPeakLists;
        this.predPeakLists = predPeakLists;
        this.iDim = iDim;
        for (int i = 0; i < expPeakLists.size(); i++) {
            PeakList expPeakList = expPeakLists.get(i);
            PeakList predPeakList = predPeakLists.get(i);
            DescriptiveStatistics eDStats = expPeakList.intensityDStats(iDim);
            DescriptiveStatistics pDStats = predPeakList.intensityDStats(iDim);
            double eIntMedian = eDStats.getPercentile(50);
            double pIntMedian = pDStats.getPercentile(50);
            double scale = eIntMedian / pIntMedian;
            predPeakList.scale = scale;
        }
    }

    public int getMatchDim() {
        return iDim;
    }

    public PeakCluster[] getExpPeakClus() {
        return expPeakClusters;
    }

    public PeakCluster[] getPredPeakClus() {
        return predPeakClusters;
    }

    public List<PeakCluster[]> getClusterMatch() {
        if (clusterMatch == null || matchedClusters == null) {
            return null;
        }
        return matchedClusters;
    }

    public void setupClusters() {
        Collection<List<Peak>> expLinks = PeakCluster.getFilteredClusters(expPeakLists, iDim);
        Collection<List<Peak>> predLinks = PeakCluster.getFilteredClusters(predPeakLists, iDim);
        expPeakClusters = PeakCluster.makePeakCluster(expLinks, iDim);
        predPeakClusters = PeakCluster.makePeakCluster(predLinks, iDim);
    }

    // main method to call
    public void runMatch() throws IllegalArgumentException {
        if (clusterMatch == null) {
            System.out.println("Running match method");
            setupClusters();
            runBPClusterMatches(expPeakClusters, predPeakClusters);
        } else {
            for (PeakCluster peakCluster : expPeakClusters) {
                peakCluster.setPeakMatches(null);
            }
            for (PeakCluster peakCluster : predPeakClusters) {
                peakCluster.setPeakMatches(null);
            }
            PeakCluster[] nonFrozenExpClusters = PeakCluster.getNonFrozenClusters(expPeakClusters);
            PeakCluster[] nonFrozenPredClusters = PeakCluster.getNonFrozenClusters(predPeakClusters);
            runBPClusterMatches(nonFrozenExpClusters, nonFrozenPredClusters);
        }
    }

    public Peak getMatchingPeak(Peak peak) {
        if (peak == null || matchedClusters == null) {
            return null;
        }
        Peak matchingPeak = null;
        int indexOfPeak = -1;
        List<Peak> peakMatchList = null;
        PeakCluster cluster = getCluster(peak);
        if (cluster != null) {
            PeakCluster pairedCluster = cluster.getPairedTo();
            List<List<Peak>> peakMatches = cluster.getPeakMatches(pairedCluster);
            for (List<Peak> peakMatch : peakMatches) {
                if (peakMatch.contains(peak)) {
                    indexOfPeak = peakMatch.indexOf(peak);
                    peakMatchList = peakMatch;
                    break;
                }
            }
            if (peakMatchList != null) {
                matchingPeak = (peakMatchList.size() > 1 && indexOfPeak == 0)
                        ? peakMatchList.get(1) : (indexOfPeak != 0) ? peakMatchList.get(0) : null;
            }
        }
        return matchingPeak;
    }

    public List<List<Peak>> getMatchedClusterPairs(Peak peak) {
        List<List<Peak>> result = new ArrayList<>();
        PeakCluster cluster = getCluster(peak);
        if (cluster != null) {
            result.addAll(cluster.getPeakMatches(cluster.getPairedTo()));
        }
        return result;
    }

    public PeakCluster getClusterWithPeak(Peak peak) {
        if (peak == null || predPeakClusters == null || expPeakClusters == null) {
            return null;
        }
        PeakCluster cluster = null;
        PeakCluster[] clusters = (peak.getPeakList().isSimulated())
                ? predPeakClusters : expPeakClusters;
        for (PeakCluster pc : clusters) {
            if (pc.contains(peak)) {
                cluster = pc;
                break;
            }
        }
        return cluster;
    }

    public PeakCluster getCluster(Peak peak) {
        if (peak == null || predPeakClusters == null || expPeakClusters == null) {
            return null;
        }
        PeakCluster cluster = null;
        PeakCluster[] clusters = (peak.getPeakList().isSimulated())
                ? predPeakClusters : expPeakClusters;
        for (PeakCluster pc : clusters) {
            if (pc.contains(peak) && pc.getPairedTo() != null) {
                cluster = pc;
                break;
            }
        }
        return cluster;
    }

    private void runBPClusterMatches(PeakCluster[] expClusArr, PeakCluster[] predClusArr) throws IllegalArgumentException {
        // initializations
        PeakCluster iExpClus, jPredClus;
        BipartiteMatcher clusterMatcher = new BipartiteMatcher();
        int nClusters = expClusArr.length + predClusArr.length;
        clusterMatcher.reset(nClusters, true);

        // main loop
        for (int i = 0; i < expClusArr.length; i++) {
            iExpClus = expClusArr[i];
            for (int j = 0; j < predClusArr.length; j++) {
                jPredClus = predClusArr[j];
                if (iExpClus.isInTol(jPredClus)) {
                    double peakMaxSum = iExpClus.comparisonScore(jPredClus);
                    clusterMatcher.setWeight(i, j, peakMaxSum);
                }
            }
        }
        clusterMatch = clusterMatcher.getMatching();
        setupMatchedClusters(expClusArr, predClusArr);
    }

    private void setupMatchedClusters(PeakCluster[] expClusArr, PeakCluster[] predClusArr) {
        if (expClusArr != null && predClusArr != null) {
            matchedClusters = new ArrayList<>();
            int counter = 0;
            // find matches
            for (int i = 0; i < clusterMatch.length; i++) {
                int j = clusterMatch[i];
                if (j < 0) {
                    continue;
                }
                PeakCluster[] ijMatched = new PeakCluster[2];
                counter++;
                System.out.println(String.format("%d. E(%s) -> P(%s)", counter, expClusArr[i].toString(), predClusArr[j].toString()));
                // TODO: change from array to List?
                PeakCluster expCluster = expClusArr[i];
                PeakCluster predCluster = predClusArr[j];
                ijMatched[0] = expCluster;
                ijMatched[1] = predCluster;
                matchedClusters.add(ijMatched);
                expCluster.setPairedTo(predCluster);
                predCluster.setPairedTo(expCluster);
                setupMatchedPeaks(expCluster, predCluster);
            }
        }
    }

    private void setupMatchedPeaks(PeakCluster expCluster, PeakCluster predCluster) {
        if (expCluster != null && predCluster != null) {
            BipartiteMatcher peakMatcher = expCluster.compareTo(predCluster);
            if (peakMatcher != null) {
                int[] peakMatches = peakMatcher.getMatching();
                expCluster.setPeakMatches(peakMatches);
                predCluster.setPeakMatches(peakMatches);
            }
        }
    }
}
