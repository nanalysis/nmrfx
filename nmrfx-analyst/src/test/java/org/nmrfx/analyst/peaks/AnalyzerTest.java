package org.nmrfx.analyst.peaks;

import junit.framework.TestCase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetException;

import java.io.IOException;

public class AnalyzerTest extends TestCase {

    public void testRemovePeaksFromRegion() throws DatasetException {
        int dim = 1;
        PeakList peakList = new PeakList("test peaks", dim);
        Peak firstPeak = new Peak(peakList, dim);
        peakList.addPeak(firstPeak);
        firstPeak.setStatus(1);
        firstPeak.getPeakDim(0).setChemShift(2.5F);
        Peak secondPeak = new Peak(peakList, dim);
        secondPeak.setStatus(1);
        peakList.addPeak(secondPeak);
        secondPeak.getPeakDim(0).setChemShift(3.5F);
        Peak thirdPeak = new Peak(peakList, dim);
        thirdPeak.setStatus(1);
        peakList.addPeak(thirdPeak);
        thirdPeak.getPeakDim(0).setChemShift(4.5F);
        // First and second peak are part of the region
        DatasetRegion region = new DatasetRegion(2, 4);
        Analyzer analyzer = Analyzer.getAnalyzer(new Dataset("", new int[]{1024}, false));
        analyzer.setPeakList(peakList);

        // third peak should have id of 2 since it was added third
        assertEquals(2, thirdPeak.getIdNum());
        analyzer.removePeaksFromRegion(region);
        // the first two peaks have been removed
        assertEquals(1, peakList.size());
        assertEquals(thirdPeak, peakList.getPeakByID(0));
    }
}