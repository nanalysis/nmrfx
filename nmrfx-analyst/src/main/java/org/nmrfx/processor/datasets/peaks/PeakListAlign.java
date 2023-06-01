/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;

import java.util.List;

/**
 * @author brucejohnson
 */
public class PeakListAlign {

    public static void alignCenters(PeakList refList, List<String> dimNames, List<PeakList> movingLists) {
        if (refList == null) {
            return;
        }
        refList.unLinkPeaks();
        refList.clearSearchDims();
        double[] refCenters = new double[dimNames.size()];
        double[] movingCenters = new double[dimNames.size()];
        for (int i = 0; i < dimNames.size(); i++) {
            refCenters[i] = refList.center(refList.getListDim(dimNames.get(i)));
        }
        System.out.println(refList.getName());
        for (PeakList movingList : movingLists) {
            if (movingList == refList) {
                continue;
            }
            System.out.println("act " + movingList.getName() + " " + movingList.size());
            movingList.unLinkPeaks();
            movingList.clearSearchDims();
            double[] deltas = new double[dimNames.size()];

            for (int i = 0; i < dimNames.size(); i++) {
                movingCenters[i] = movingList.center(movingList.getListDim(dimNames.get(i)));
                deltas[i] = movingCenters[i] - refCenters[i];
                System.out.println(i + " " + dimNames.get(i) + " " + refCenters[i] + " " + movingCenters[i] + " " + deltas[i]);
            }

            PeakNeighbors neighbor = new PeakNeighbors(refList, movingList, 25, dimNames);
            neighbor.optimizeMatch(deltas, 0.0, 1.0);
            for (int i = 0; i < dimNames.size(); i++) {
                movingList.shiftPeak(movingList.getListDim(dimNames.get(i)), -deltas[i]);
                Dataset dataset = Dataset.getDataset(movingList.getDatasetName());
                if (dataset != null) {
                    int dDim = dataset.getDim(dimNames.get(i));
                    if (dDim != -1) {
                        double ref = dataset.getRefValue(dDim);
                        ref -= deltas[i];
                        dataset.setRefValue(dDim, ref);
                    }
                    dataset.writeParFile();
                }
            }
        }
    }
}
