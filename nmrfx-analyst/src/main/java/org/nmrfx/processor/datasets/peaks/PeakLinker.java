/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.project.ProjectBase;

import java.util.*;

/**
 * @author Bruce Johnson
 */
public class PeakLinker {

    public void linkAllPeakListsByLabel(String sampleCondition) {
        linkPeakListsByLabel(ProjectBase.getActive().getPeakLists(), sampleCondition);
    }

    public void linkPeakListsByLabel(Collection<PeakList> peakLists, String sampleCondition) {
        Map<String, List<PeakDim>> peakDimMap = new HashMap<>();
        peakLists.forEach((peakList) -> {
            if (peakList.valid() && (sampleCondition.isBlank() || sampleCondition.equals(peakList.getSampleConditionLabel()))) {
                peakList.peaks().forEach((peak) -> {
                    for (PeakDim peakDim : peak.getPeakDims()) {
                        String label = peakDim.getLabel().trim();
                        if (!label.isBlank()) {
                            List<PeakDim> dimList = peakDimMap.get(label);
                            if (dimList == null) {
                                dimList = new ArrayList<>();
                                peakDimMap.put(label, dimList);
                            }
                            dimList.add(peakDim);
                        }
                    }

                });
            }
        });
        peakDimMap.entrySet().stream().forEach(eSet -> {
            List<PeakDim> dimList = eSet.getValue();
            if (dimList.size() > 1) {
                PeakDim rootDim = dimList.get(0);
                for (int i = 1, n = dimList.size(); i < n; i++) {
                    PeakDim linkDim = dimList.get(i);
                    PeakList.linkPeakDims(rootDim, linkDim);
                }
            }
        });
    }

    public static List<Peak> linkFourPeaks(Collection<Peak> peaks) {
        if (peaks.size() != 4) {
            throw new IllegalStateException("Need four selected peaks");
        }
        List<Peak> sortedPeaks = peaks.stream().sorted(Comparator.comparingDouble(Peak::getFirstIntensity).reversed()).toList();
        double x0 = sortedPeaks.get(0).getPeakDim(0).getChemShiftValue();
        double y0 = sortedPeaks.get(0).getPeakDim(1).getChemShiftValue();
        double dXMin = Double.MAX_VALUE;
        double dYMin = Double.MAX_VALUE;
        int ix = 0;
        int iy = 0;
        int ib = 1;
        if (getLinkedGroup(peaks).size() != 4) {
            for (Peak peak : sortedPeaks) {
                PeakList.unLinkPeak(peak);
            }
        }
        for (int i = 1; i < sortedPeaks.size(); i++) {
            double xi = sortedPeaks.get(i).getPeakDim(0).getChemShiftValue();
            double yi = sortedPeaks.get(i).getPeakDim(1).getChemShiftValue();
            double dX = Math.abs(x0 - xi);
            double dY = Math.abs(y0 - yi);
            if (dX < dXMin) {
                dXMin = dX;
                ix = i;
            }
            if (dY < dYMin) {
                dYMin = dY;
                iy = i;
            }
        }
        for (int i = 1; i < sortedPeaks.size(); i++) {
            if ((i != ix) && (i != iy)) {
                ib = i;
                break;
            }
        }

        Peak aaPeak = sortedPeaks.get(0);
        Peak bbPeak = sortedPeaks.get(ib);
        Peak baPeak = sortedPeaks.get(ix);
        Peak abPeak = sortedPeaks.get(iy);
        PeakList.linkPeaks(aaPeak, 0, baPeak, 0);
        PeakList.linkPeaks(aaPeak, 1, abPeak, 1);
        PeakList.linkPeaks(bbPeak, 0, abPeak, 0);
        PeakList.linkPeaks(bbPeak, 1, baPeak, 1);
        List<Peak> abPeaks = new ArrayList<>();
        abPeaks.add(aaPeak);
        abPeaks.add(bbPeak);
        abPeaks.add(abPeak);
        abPeaks.add(baPeak);

        return abPeaks;
    }

    public static Set<Peak> getLinkedGroup(Collection<Peak> peaks) {
        Set<Peak> allPeaks = new HashSet<>();
        for (Peak peak : peaks) {
            allPeaks.addAll(getLinkedGroup(peak));
        }
        return allPeaks;
    }

    public static Set<Peak> getLinkedGroup(Peak peak) {
        Set<Peak> startGroup = new HashSet<>();
        Set<Peak> peaks = new HashSet<>();
        startGroup.addAll(PeakList.getLinks(peak));
        startGroup.add(peak);
        for (Peak lPeak : startGroup) {
            peaks.addAll(PeakList.getLinks(lPeak));
        }
        return peaks;
    }
}
