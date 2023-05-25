package org.nmrfx.processor.datasets.peaks.io;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.io.PeakReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PeakReaderTest2 {
    private static final Logger log = LoggerFactory.getLogger(PeakReaderTest2.class);

    String peakListName = "src/test/resources/test.xpk2";
    PeakList peakList1 = null;
    PeakReader peakReader = null;

    PeakList getPeakList() {
        if (peakList1 == null) {
            peakReader = new PeakReader(true);
            try {
                peakList1 = peakReader.readPeakList(peakListName);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        return peakList1;
    }

    @Test
    public void testXPK2Reader() {
        PeakList peakList = getPeakList();
        Assert.assertNotNull(peakList);
        Assert.assertEquals(4, peakList.size());
    }

    @Test
    public void testXPK2ReaderDims() {
        PeakList peakList = getPeakList();
        Assert.assertNotNull(peakList);
        Assert.assertEquals(2, peakList.getNDim());
    }

    @Test
    public void testXPK2ReaderSF() {
        PeakList peakList = getPeakList();
        double[] sf = {600.0, 60.80411411};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < sf.length; i++) {
            Assert.assertEquals(sf[i], peakList.getSpectralDim(i).getSf(), 1.0 - 6);
        }
    }

    @Test
    public void testXPK2ReaderSW() {
        PeakList peakList = getPeakList();
        double[] sw = {7200.0, 2432.2};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < sw.length; i++) {
            Assert.assertEquals(sw[i], peakList.getSpectralDim(i).getSw(), 0.1);
        }
    }

    @Test
    public void testXPK2ReaderLabel() {
        PeakList peakList = getPeakList();
        String[] labels = {"H_1", "N_2"};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < labels.length; i++) {
            Assert.assertEquals(labels[i], peakList.getSpectralDim(i).getDimName());
        }
    }

    @Test
    public void testXPK2ReaderPPM() {
        PeakList peakList = getPeakList();
        double[] ppms = {9.04322, 133.32071};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < ppms.length; i++) {
            Assert.assertEquals(ppms[i], (double) peakList.getPeak(0).getPeakDim(i).getChemShiftValue(), 1.0e-5);
        }
    }

    @Test
    public void testXPK2ReaderDimLabel() {
        PeakList peakList = getPeakList();
        String[] dimlabels = {"", ""};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < dimlabels.length; i++) {
            Assert.assertEquals(dimlabels[i], peakList.getPeak(0).getPeakDim(i).getLabel());
        }
    }

    @Test
    public void testXPK2ReaderWidth() {
        PeakList peakList = getPeakList();
        double[] widths = {16.98582, 16.26301};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < widths.length; i++) {
            Assert.assertEquals(widths[i], (double) peakList.getPeak(0).getPeakDim(i).getLineWidthHz(), 1.0e-5);
        }
    }

    @Test
    public void testXPK2ReaderBoxWidth() {
        PeakList peakList = getPeakList();
        double[] bwidths = {51.52292, 49.98994};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < bwidths.length; i++) {
            Assert.assertEquals(bwidths[i], (double) peakList.getPeak(0).getPeakDim(i).getBoundsHz(), 1.0e-5);
        }
    }

    @Test
    public void testXPK2ReaderVolume() {
        PeakList peakList = getPeakList();
        double vol = 953.3195190429688;
        Assert.assertNotNull(peakList);
        Assert.assertEquals(vol, (double) peakList.getPeak(0).getVolume1(), 1.0e-5);
    }

    @Test
    public void testXPK2ReaderIntensity() {
        PeakList peakList = getPeakList();
        double inten = 71.01772;
        Assert.assertNotNull(peakList);
        Assert.assertEquals(inten, (double) peakList.getPeak(0).getIntensity(), 1.0e-5);
    }

    @Test
    public void testXPK2ReaderError() {
        PeakList peakList = getPeakList();
        char[][] errors = {{'+', '+'}, {'+', '+'}};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < errors.length; i++) {
            Assert.assertArrayEquals(errors[i], peakList.getPeak(0).getPeakDim(i).getError());
        }
    }

    @Test
    public void testXPK2ReaderFrozen() {
        PeakList peakList = getPeakList();
        boolean[] frozen = {false, false};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < frozen.length; i++) {
            Assert.assertEquals(frozen[i], (boolean) peakList.getPeak(0).getPeakDim(i).isFrozen());
        }
    }

    @Test
    public void testXPK2ReaderLinked() {
        PeakList peakList = getPeakList();
        Assert.assertNotNull(peakList);
        peakReader.linkResonances();
        Assert.assertTrue(PeakList.isLinked(peakList.getPeak(2), 0, peakList.getPeak(3)));
    }

    @Test
    public void testXPK2ReaderNotLinked() {
        PeakList peakList = getPeakList();
        Assert.assertNotNull(peakList);
        peakReader.linkResonances();
        Assert.assertFalse(PeakList.isLinked(peakList.getPeak(1), 0, peakList.getPeak(3)));
    }

    @Test
    public void testXPK2ReaderType() {
        PeakList peakList = getPeakList();
        double type = 1;
        Assert.assertNotNull(peakList);
        Assert.assertEquals(type, (double) peakList.getPeak(0).getType(), 1.0e-5);
    }

    @Test
    public void testXPK2ReaderStatus() {
        PeakList peakList = getPeakList();
        double status = 0;
        Assert.assertNotNull(peakList);
        Assert.assertEquals(status, (double) peakList.getPeak(0).getStatus(), 1.0e-5);
    }

    @Test
    public void testXPK2ReaderIdTol() {
        PeakList peakList = getPeakList();
        double[] idtol = {0.01, 0.16};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < idtol.length; i++) {
            Assert.assertEquals(idtol[i], peakList.getSpectralDim(i).getIdTol(), 1.0e-5);
        }
    }

    @Test
    public void testXPK2ReaderAbsPos() {
        PeakList peakList = getPeakList();
        boolean[] abspos = {false, false};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < abspos.length; i++) {
            Assert.assertEquals(abspos[i], (boolean) peakList.getSpectralDim(i).isAbsPosition());
        }
    }

    @Test
    public void testXPK2ReaderAcqDim() {
        PeakList peakList = getPeakList();
        boolean[] acqdim = {true, false};
        Assert.assertNotNull(peakList);
        for (int i = 0; i < acqdim.length; i++) {
            Assert.assertEquals(acqdim[i], (boolean) peakList.getSpectralDim(i).isAcqDim());
        }
    }

    @Test(expected = IOException.class)
    public void testXPK2ReaderNoNdim() throws IOException {
        peakReader = new PeakReader(true);
        peakReader.readXPK2Peaks("src/test/resources/test2.xpk2");
    }
}
