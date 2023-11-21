package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.peaks.Peak;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DimCounter;

import java.util.Arrays;
import java.util.List;

public class PeakDimCounter  {
    Dataset dataset;
    List<Peak> peaks;
    Peak peak;
    int index = -1;
    int[] cpt;
    double[] width;

    int[] pkToData;
    int[] dataToPk;
    int[] dimOrder;
    int[][] peakBounds;
    int[][] fullBounds;
    int[] pSize;

    double filterWidth;

    public PeakDimCounter(Dataset dataset, List<Peak> peaks, int[] dimOrder, int[] pkToData, int[][] fullBounds, double filterWidth) {
        this.dataset = dataset;
        this.peaks = peaks;
        this.dimOrder = dimOrder;
        this.pkToData = pkToData;
        this.fullBounds = fullBounds;
        this.filterWidth = filterWidth;
        int nDim = dataset.getNDim();
        cpt = new int[nDim];
        width = new double[nDim];
        dataToPk = new int[nDim];
        Arrays.fill(dataToPk, -1);
        for (int i=0;i<pkToData.length;i++) {
            this.dataToPk[pkToData[i]] = i;
        }
        pSize = new int[nDim];
        peakBounds = new int[nDim][2];
    }
    public class Iterator<T> implements java.util.Iterator<int[]> {
        DimCounter dimCounter;
        DimCounter.Iterator dimIterator;

        public Iterator() {
        }

        public boolean hasNext() {
            boolean hasNext = dimIterator == null || dimIterator.hasNext();
            if (!hasNext) {
                hasNext = index < peaks.size() - 1;
            }
            return hasNext;
        }

        void newCounter() {
            index++;
            peak = peaks.get(index);
            peak.getPeakRegion(dataset, pkToData, peakBounds, cpt, width, null, filterWidth);
            for (int i=0;i<dimOrder.length;i++) {
                int dDim = dimOrder[i];
                int pDim = dataToPk[dDim];
                if (pDim != -1) {
                    int size = Math.abs(peakBounds[dDim][1] - peakBounds[dDim][0]) + 1;
                    pSize[i] = size;
                } else {
                    pSize[i] = fullBounds[i][1] - fullBounds[i][0] + 1;
                }
            }
            dimCounter = new DimCounter(pSize);
            dimIterator = dimCounter.iterator();
        }
        public int[] next() {
            int[] counts;
            if (dimIterator == null) {
                newCounter();
            }
            if (!dimIterator.hasNext()) {
                newCounter();
            }
            counts = (int[]) dimIterator.next();
            for (int i = 0; i < counts.length; i++) {
                int dDim = dimOrder[i];
                int pDim = dataToPk[dDim];
                if (pDim != -1) {
                    counts[i] += Math.min(peakBounds[dDim][0], peakBounds[dDim][1]);
                }
            }
            return counts;

        }
    }
    public Iterator<int[]> iterator() {
        return new Iterator<>();
    }

}
