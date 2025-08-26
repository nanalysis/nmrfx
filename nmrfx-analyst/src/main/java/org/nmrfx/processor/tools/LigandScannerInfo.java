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

    public LigandScannerInfo(Dataset dataset, int index) {
        this.dataset = dataset;
        this.index = index;
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
            if (peakList != null && peakList.getNDim() < dataset.getNDim()) {
                peakList = null;
            }
        }
        return peakList;
    }
}
