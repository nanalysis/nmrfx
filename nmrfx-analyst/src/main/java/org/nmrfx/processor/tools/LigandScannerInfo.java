/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.tools;

import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;

/**
 * @author brucejohnson
 */
public class LigandScannerInfo {

    private final Dataset dataset;
    private PeakList peakList = null;
    private final int index;
    private double minShift;
    double pcaDist;
    double[] pcaValues = null;

    public LigandScannerInfo(Dataset dataset, int index) {
        this.dataset = dataset;
        this.index = index;
    }

    public void setPCADist(double value) {
        pcaDist = value;
    }

    public double getPCADist() {
        return pcaDist;
    }

    public void setPCValues(double[] values) {
        pcaValues = values.clone();
    }

    public double getPCAValue(int index) {
        return pcaValues == null ? 0.0 : pcaValues[index];
    }

    /**
     * @return the dataset
     */
    public Dataset getDataset() {
        return dataset;
    }

    /**
     * @return the index
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return the peakList
     */
    public PeakList getPeakList() {
        if ((peakList == null) && (dataset != null)) {
            peakList = PeakList.getPeakListForDataset(dataset.getName());
            if (peakList != null) {
                if (peakList.getNDim() < dataset.getNDim()) {
                    peakList = null;
                }
            }
        }
        return peakList;
    }

    /**
     * @return the nPeaks
     */
    public int getNPeaks() {
        int nPeaks = 0;
        PeakList peakList = getPeakList();
        if (peakList != null) {
            nPeaks = peakList.size();
        }
        return nPeaks;
    }

    /**
     * @return the minShift
     */
    public double getMinShift() {
        return minShift;
    }

    /**
     * @param minShift the minShift to set
     */
    public void setMinShift(double minShift) {
        this.minShift = minShift;
    }

}
