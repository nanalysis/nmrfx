package org.nmrfx.processor.optimization;

import java.util.Collection;
import java.util.List;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;

public class NewPeakMatcher {

    private final PeakList expPeakList;
    private final PeakList predPeakList;
    private PeakCluster[] expPeakClusters = null;
    private PeakCluster[] predPeakClusters = null;
    private int[] match = null;
    private final int iDim;
    public final double REF_SCALE;

    public NewPeakMatcher(PeakList expPeakList, PeakList predPeakList, int iDim) {
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

    public int[] getMatch() {
        return match;
    }
    

    // main method to call
    public void runMatch() throws IllegalArgumentException {
        System.out.println("Running match method");
        Collection<List<Peak>> expLinks = PeakCluster.getCluster(expPeakList, iDim);
        Collection<List<Peak>> predLinks = PeakCluster.getCluster(predPeakList, iDim);
        expPeakClusters = PeakCluster.makePeakCluster(expLinks, iDim);
        predPeakClusters = PeakCluster.makePeakCluster(predLinks, iDim);
        runBPClusterMatches();
    }

    private void runBPClusterMatches() {
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
                        double peakMaxSum = BipartiteMatcher.getMaxWtSum(peakMatcher);
                        clusterMatcher.setWeight(i, j, peakMaxSum);
                    }
                }
        }
        match = clusterMatcher.getMatching();
    }
}
