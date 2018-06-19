package org.nmrfx.processor.datasets.peaks.io;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.datasets.peaks.PeakList;

public class PeakReaderTest {
    String peakListName = "src/test/resources/test.xpk";
    PeakList peakList1 = null;

    PeakList getPeakList() {
        if (peakList1 == null) {
            PeakReader peakReader = new PeakReader();
            try {
                peakList1 = peakReader.readPeakList(peakListName);
            } catch (IOException ex) {
                Logger.getLogger(PeakReaderTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return peakList1;
    }

    @Test
    public void testXPKReader() {
        PeakList peakList = getPeakList();
        Assert.assertNotNull(peakList);
        Assert.assertEquals(4, peakList.size());
    }

    @Test
    public void testXPKReaderDims() {
        PeakList peakList = getPeakList();
        Assert.assertNotNull(peakList);
        Assert.assertEquals(2, peakList.getNDim());
    }

    @Test
    public void testXPKReaderSF() {
        PeakList peakList = getPeakList();
        double[] sf = {499.71899414, 50.64199829};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < sf.length; i++) {
            Assert.assertEquals(sf[i], peakList.getSpectralDim(i).getSf(), 1.0 - 6);
        }
    }

    @Test
    public void testXPKReaderSW() {
        PeakList peakList = getPeakList();
        double[] sw = {3940.89, 1320.0};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < sw.length; i++) {
            Assert.assertEquals(sw[i], peakList.getSpectralDim(i).getSw(), 0.1);
        }
    }

    @Test
    public void testXPKReaderLabel() {
        PeakList peakList = getPeakList();
        String[] labels = {"H1", "N15"};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < labels.length; i++) {
            Assert.assertEquals(labels[i], peakList.getSpectralDim(i).getDimName());
        }
    }

    @Test
    public void testXPKReaderPPM() {
        PeakList peakList = getPeakList();
        //0 {} 8.784855 0.030457228 0.028026449 {} 129.01712 0.3105101 0.28385773 0.0 0.6005752

        double[] ppms = {8.784855, 129.01712};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < ppms.length; i++) {
            Assert.assertEquals(ppms[i], (double) peakList.getPeak(0).getPeakDim(i).getChemShiftValue(), 1.0e-5);
        }
    }
}
