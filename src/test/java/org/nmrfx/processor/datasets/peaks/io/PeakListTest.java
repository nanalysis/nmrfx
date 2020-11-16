package org.nmrfx.processor.datasets.peaks.io;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakListBase;
import org.nmrfx.peaks.io.PeakReader;

public class PeakListTest {

    String peakListName1 = "src/test/resources/test.xpk";
    String peakListName2 = "src/test/resources/test.xpk2";
    PeakListBase peakList1 = null;

    PeakListBase getPeakList(String peakListName) {
        if (peakList1 == null) {
            PeakReader peakReader = new PeakReader();
            try {
                peakList1 = peakReader.readPeakList(peakListName);
            } catch (IOException ex) {
                Logger.getLogger(PeakListTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return peakList1;
    }

    @Test
    public void testFindPeaks() throws IllegalArgumentException {
        PeakListBase peakList = getPeakList(peakListName1);
        Assert.assertNotNull(peakList);
        
        peakList.setSearchDims("H1 0.1 N15 0.5");
        
        double[] ppms = {8.784855, 129.01712};
        List peakListf = peakList.findPeaks(ppms);
        Assert.assertNotNull(peakListf);
        Assert.assertEquals(1, peakListf.size());
        
        peakList1 = null;
        
        peakList1 = getPeakList(peakListName2);
        Assert.assertNotNull(peakList1);
        
        peakList1.setSearchDims("H_1 0.1 N_2 0.5");
        
        double[] ppms2 = {9.04322, 133.32071};
        List peakListf2 = peakList1.findPeaks(ppms2);
        Assert.assertNotNull(peakListf2);
        Assert.assertEquals(1, peakListf2.size());
    }
    
    @Test
    public void testCopy() {
        PeakListBase peakList0 = getPeakList(peakListName2);
        PeakListBase peakList = new PeakListBase(peakListName2, peakList0.getNDim());
        Assert.assertNotNull(peakList);
        
        String name = peakList.getName();
        PeakListBase peakListc = peakList.copy(name, true, true, false);
        Assert.assertEquals(peakList, peakListc);
    }
    
    @Test
    public void testPeaks() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        List peaks = peakList.peaks();
//        Assert.assertEquals(peakList, peaks);
        for (int i = 0; i < peaks.size(); i++) {
            Assert.assertEquals(peaks.get(i), peakList.getPeak(i));
        }
    }
    
//    @Test
//    public void testGetId() {
//        PeakList peakList = getPeakList(peakListName2);
//        Assert.assertNotNull(peakList);
//        
//        int id = 1;
//        Assert.assertEquals(id, peakList.getId(), 1.0e-5);
//    }

    @Test
    public void testGetName() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String name = "unknown";
        Assert.assertEquals(name, peakList.getName());
    }
    
    @Test
    public void testSetName() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        peakList.setName("new");
        String name = "new";
        Assert.assertEquals(name, peakList.getName());
    }
    
    @Test
    public void testGetScale() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        double scale = 1.0;
        Assert.assertEquals(scale, peakList.getScale(), 1.0e-5);
    }
    
    @Test
    public void testGetSampleLabel() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String label = "";
        Assert.assertEquals(label, peakList.getSampleLabel());
    }
    
    @Test
    public void testGetSampleConditionLabel() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String label = "";
        Assert.assertEquals(label, peakList.getSampleConditionLabel());
    }
    
    @Test
    public void testGetDatasetName() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String label = "unknown.nv";
        Assert.assertEquals(label, peakList.getDatasetName());
    }
    
    @Test
    public void testGetDetails() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String detail = "";
        Assert.assertEquals(detail, peakList.getDetails());
    }
    
    @Test
    public void testIsChanged() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean change = true;
        Assert.assertEquals(change, peakList.isChanged());
    }
    
    @Test
    public void testIsAnyChanged() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean change = true;
        Assert.assertEquals(change, peakList.isAnyChanged());
    }
    
    @Test
    public void testHasMeasures() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean measures = false;
        Assert.assertEquals(measures, peakList.hasMeasures());
    }
    
    @Test
    public void testGetMeasureValues() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        double[] measures = null;
        Assert.assertEquals(measures, peakList.getMeasureValues());
    }
    
    @Test
    public void testHasSearchDims() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean search = false;
        Assert.assertEquals(search, peakList.hasSearchDims());
    }
    
    @Test
    public void testSize() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        int size = 4;
        Assert.assertEquals(size, peakList.size(), 1.0e-5);
    }
    
    @Test
    public void testValid() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean valid = true;
        Assert.assertEquals(valid, peakList.valid());
    }
    
    
    @Test
    public void testGetPeakByID() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        Peak peak = peakList.getPeak(0); //peakList.getPeakByID(0);
        Assert.assertEquals(peak, peakList.getPeakByID(0));
    }
    
    @Test
    public void testGetListDim() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        int dim = 1;
        String s = "N_2";
        Assert.assertEquals(dim, peakList.getListDim(s), 1.0e-5);
    }
    
    @Test
    public void testIsSlideable() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean slide = false;
        Assert.assertEquals(slide, peakList.isSlideable());
    }
    
    @Test
    public void testReNumber() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        int size0 = peakList.size();
        
        peakList.getPeak(1).setStatus(-1);
        peakList.compress();
        Assert.assertEquals(size0 - 1, peakList.size());
        Assert.assertEquals(2, peakList.getPeak(1).getIdNum());
        
        peakList.reNumber();
        Assert.assertEquals(1, peakList.getPeak(1).getIdNum());
    }
    
    @Test
    public void testRemove() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList.peaks());
        
        peakList.remove(peakList.getName());
        Assert.assertNull(peakList.peaks());
    }
    
    @Test
    public void testSortPeaks() throws IllegalArgumentException {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        double[] ppm = {9.04322, 133.32071};
        for (int i = 0; i < ppm.length; i++) {
            Assert.assertEquals(ppm[i], (double) peakList.getPeak(0).getPeakDim(i).getChemShiftValue(), 1.0e-5);
        }
        
        peakList.sortPeaks(0, true);
        
        double[] ppms = {8.99153, 128.18442};
        for (int i = 0; i < ppms.length; i++) {
            Assert.assertEquals(ppms[i], (double) peakList.getPeak(0).getPeakDim(i).getChemShiftValue(), 1.0e-5);
        }
    }
    
    @Test
    public void testLocatePeaks() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        double[][] limits = {{9,10}, {127, 128}};
        int[] dims = {0, 1};
        List peakListl = peakList.locatePeaks(limits, dims);
        
        
        double[] ppms = {9.57507, 9.41346};
        for (int i = 0; i < ppms.length; i++) {
            Peak peakl0 = (Peak) peakListl.get(i);
            Assert.assertEquals(ppms[i], (double) peakl0.getPeakDim(0).getChemShiftValue(), 1.0e-5);
        }
    }
    
    @Test
    public void testMatchPeaks() {
        PeakListBase peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String[] strings = {"", ""};
        List match = peakList.matchPeaks(strings, true, true);
        Assert.assertEquals(4, match.size());
        Peak match0 = (Peak) match.get(0);
        
        double[] ppms = {9.04322, 133.32071};
        for (int i = 0; i < ppms.length; i++) {
            Assert.assertEquals(ppms[i], (double) match0.getPeakDim(i).getChemShiftValue(), 1.0e-5);
        }
    }
    
}