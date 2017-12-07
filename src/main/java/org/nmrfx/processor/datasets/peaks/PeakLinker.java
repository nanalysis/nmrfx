/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Bruce Johnson
 */
public class PeakLinker {

    public void linkAllPeakListsByLabel() {
        linkPeakListsByLabel(PeakList.getLists());
    }

    public void linkPeakListsByLabel(List<PeakList> peakLists) {
        Map<String, List<PeakDim>> peakDimMap = new HashMap<>();
        peakLists.forEach((peakList) -> {
            peakList.peaks().forEach((peak) -> {
                for (PeakDim peakDim : peak.getPeakDims()) {
                    String label = peakDim.getLabel().trim();
                    if (!label.equals("")) {
                        List<PeakDim> dimList = peakDimMap.get(label);
                        if (dimList == null) {
                            dimList = new ArrayList<>();
                            peakDimMap.put(label, dimList);
                        }
                        dimList.add(peakDim);
                        System.out.println("add " + label + " " + dimList.size() + " " + peakDim);
                    }
                }
            });
        });
        peakDimMap.entrySet().stream().forEach(eSet -> {
            List<PeakDim> dimList = eSet.getValue();
            if (dimList.size() > 1) {
                PeakDim rootDim = dimList.get(0);
                for (int i = 1, n = dimList.size(); i < n; i++) {
                    PeakDim linkDim = dimList.get(i);
                    PeakList.linkPeakDims(rootDim, linkDim);
                    System.out.println("link " + rootDim.getPeak().getName() + " " + linkDim.getPeak().getName());
                }
            }
        });
    }
}
