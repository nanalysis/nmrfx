/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui.tools;

import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author brucejohnson
 */
public class PeakLinker {

    private PeakLinker() {
    }

    public static List<Peak> getSelectedPeaks() {
        List<Peak> allPeaks = new ArrayList<>();
        for (var controller : AnalystApp.getFXMLControllerManager().getControllers()) {
            for (var chart : controller.getCharts()) {
                List<Peak> peaks = chart.getSelectedPeaks();
                allPeaks.addAll(peaks);
            }
        }
        return allPeaks;
    }

    public static String getPeakDim(int iDim) {
        var activeChart = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getActiveChart();
        var refList = activeChart.getPeakListAttributes().get(0).getPeakList();
        var refSpectralDim = refList.getSpectralDim(iDim);
        var refDimName = refSpectralDim.getDimName();
        return refDimName;
    }

    public static void linkSelectedPeaks(int iDim) {
        var refDimName = getPeakDim(iDim);
        List<Peak> allPeaks = getSelectedPeaks();
        Peak firstPeak = allPeaks.get(0);
        var refDim = firstPeak.getPeakDim(refDimName);
        for (var peak : allPeaks) {
            var peakDim = peak.getPeakDim(refDimName);
            PeakList.linkPeakDims(refDim, peakDim);
        }
    }

    public static void unlinkSelected() {
        List<Peak> allPeaks = getSelectedPeaks();
        for (var peak : allPeaks) {
            PeakList.unLinkPeak(peak);
        }
    }

    public static void unlinkSelected(int iDim) {
        var refDimName = getPeakDim(iDim);
        List<Peak> allPeaks = getSelectedPeaks();
        for (var peak : allPeaks) {
            var peakDim = peak.getPeakDim(refDimName);
            peakDim.unLink();
        }

    }

    public static void linkFourPeaks() throws  IllegalStateException{
        List<Peak> peaks = getSelectedPeaks();
        org.nmrfx.processor.datasets.peaks.PeakLinker.linkFourPeaks(peaks);
    }
}
