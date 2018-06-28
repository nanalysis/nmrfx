package org.nmrfx.processor.datasets.peaks.io;

import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.PeakList;

public class PeakWriterTest {

    String peakListName1 = "src/test/resources/test.xpk";
    String peakListName2 = "src/test/resources/test.xpk2";
    PeakList peakList1 = null;

    PeakList getPeakList(String peakListName) {
        if (peakList1 == null) {
            PeakReader peakReader = new PeakReader();
            try {
                peakList1 = peakReader.readPeakList(peakListName);
            } catch (IOException ex) {
                Logger.getLogger(PeakWriterTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return peakList1;
    }

    @Test
    public void testXPKWriter() throws IOException, InvalidPeakException {
        PeakList peakList = getPeakList(peakListName1);
        Assert.assertNotNull(peakList);
        
        PeakWriter peakWriter = new PeakWriter();
        String writeFileName = "src/test/resources/testw.xpk";
        try (FileWriter writer = new FileWriter(writeFileName)) {
            peakWriter.writePeaksXPK(writer, peakList);
            writer.close();
        }
        
        PeakList peakListw = getPeakList(writeFileName);
        Assert.assertNotNull(peakListw);
        Assert.assertEquals(peakList, peakListw);
    }

    @Test
    public void testXPK2Writer() throws IOException, InvalidPeakException {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        PeakWriter peakWriter = new PeakWriter();
        String writeFileName = "src/test/resources/testw.xpk2";
        peakWriter.writePeaksXPK2(writeFileName, peakList);
        
        PeakList peakListw = getPeakList(writeFileName);
        Assert.assertNotNull(peakListw);
        Assert.assertEquals(peakList, peakListw);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testXPKWriterNullChannel() throws IOException, InvalidPeakException {
        PeakList peakList = getPeakList(peakListName1);
        Assert.assertNotNull(peakList);
        
        PeakWriter peakWriter = new PeakWriter();
        try (FileWriter writer = null) {
            peakWriter.writePeaksXPK(writer, peakList);
            writer.close();
        }
    }

//    @Test(expected = InvalidPeakException.class)
//    public void testXPKWriterNullPeak() throws IOException, InvalidPeakException {
//        PeakList peakList = getPeakList(peakListName1);
//        Assert.assertNotNull(peakList);
//        peakList.getPeak(0) = null;
//        
//        PeakWriter peakWriter = new PeakWriter();
//        String writeFileName = "src/test/resources/testw.xpk";
//        try (FileWriter writer = new FileWriter(writeFileName)) {
//            peakWriter.writePeaksXPK(writer, peakList);
//            writer.close();
//        }
//    }
    
}
