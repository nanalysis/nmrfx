package org.nmrfx.processor.datasets.peaks.io;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.io.PeakReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PeakReaderTest {
    private static final Logger log = LoggerFactory.getLogger(PeakReaderTest.class);

    String peakListName = "src/test/resources/test.xpk";
    PeakList peakList1 = null;

    PeakList getPeakList() {
        if (peakList1 == null) {
            PeakReader peakReader = new PeakReader();
            try {
                peakList1 = peakReader.readPeakList(peakListName);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
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

    @Test
    public void testXPKReaderDimLabel() {
        PeakList peakList = getPeakList();
        //0 {} 8.784855 0.030457228 0.028026449 {} 129.01712 0.3105101 0.28385773 0.0 0.6005752

        String[] dimlabels = {"31.H", "31.N"};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < dimlabels.length; i++) {
            Assert.assertEquals(dimlabels[i], peakList.getPeak(0).getPeakDim(i).getLabel());
        }
    }

    @Test
    public void testXPKReaderWidth() {
        PeakList peakList = getPeakList();
        //0 {} 8.784855 0.030457228 0.028026449 {} 129.01712 0.3105101 0.28385773 0.0 0.6005752

        double[] widths = {0.030457228, 0.3105101};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < widths.length; i++) {
            Assert.assertEquals(widths[i], (double) peakList.getPeak(0).getPeakDim(i).getLineWidth(), 1.0e-5);
        }
    }

    @Test
    public void testXPKReaderBoxWidth() {
        PeakList peakList = getPeakList();
        //0 {} 8.784855 0.030457228 0.028026449 {} 129.01712 0.3105101 0.28385773 0.0 0.6005752

        double[] bwidths = {0.028026449, 0.28385773};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < bwidths.length; i++) {
            Assert.assertEquals(bwidths[i], (double) peakList.getPeak(0).getPeakDim(i).getBounds(), 1.0e-5);
        }
    }

    @Test
    public void testXPKReaderVolume() {
        PeakList peakList = getPeakList();
        //0 {} 8.784855 0.030457228 0.028026449 {} 129.01712 0.3105101 0.28385773 0.0 0.6005752

        double vol = 0.0;
        Assert.assertNotNull(peakList);
        Assert.assertEquals(vol, (double) peakList.getPeak(0).getVolume1(), 1.0e-5);
    }

    @Test
    public void testXPKReaderIntensity() {
        PeakList peakList = getPeakList();
        //0 {} 8.784855 0.030457228 0.028026449 {} 129.01712 0.3105101 0.28385773 0.0 0.6005752

        double inten = 0.6005752;
        Assert.assertNotNull(peakList);
        Assert.assertEquals(inten, (double) peakList.getPeak(0).getIntensity(), 1.0e-5);
    }

    @Test
    public void testParseXPKLineEncapsulatedQuotes() {
        // This test is to check that the encapsulated quotes are parsed correctly
        String doubleQuoteString = "0 {h5} {h5'} {h5''} 1.234 ++ {0.0} {} 0\n";
        List<String> expectedDoubleQuote = new ArrayList<>(List.of("0", "h5", "h5'", "h5''", "1.234", "++", "0.0", "", "0"));
        List<String> actualDoubleQuote = PeakReader.parseXPKLine(doubleQuoteString);
        Assert.assertEquals(expectedDoubleQuote, actualDoubleQuote);
    }

}
