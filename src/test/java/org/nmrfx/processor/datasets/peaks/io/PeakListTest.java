package org.nmrfx.processor.datasets.peaks.io;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;

public class PeakListTest {

    String peakListName1 = "src/test/resources/test.xpk";
    String peakListName2 = "src/test/resources/test.xpk2";
    PeakList peakList1 = null;

    PeakList getPeakList(String peakListName) {
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
        PeakList peakList = getPeakList(peakListName1);
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
        PeakList peakList0 = getPeakList(peakListName2);
        PeakList peakList = new PeakList(peakListName2, peakList0.getNDim());
        Assert.assertNotNull(peakList);
        
        String name = peakList.getName();
        PeakList peakListc = peakList.copy(name, true, true);
        Assert.assertEquals(peakList, peakListc);
    }
    
    @Test
    public void testPeaks() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        List peaks = peakList.peaks();
//        Assert.assertEquals(peakList, peaks);
        for (int i = 0; i < peaks.size(); i++) {
            Assert.assertEquals(peaks.get(i), peakList.getPeak(i));
        }
    }
    
    @Test
    public void testGetId() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        int id = 1;
        Assert.assertEquals(id, peakList.getId(), 1.0e-5);
    }

    @Test
    public void testGetName() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String name = "unknown";
        Assert.assertEquals(name, peakList.getName());
    }
    
    @Test
    public void testSetName() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        peakList.setName("new");
        String name = "new";
        Assert.assertEquals(name, peakList.getName());
    }
    
    @Test
    public void testGetScale() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        double scale = 1.0;
        Assert.assertEquals(scale, peakList.getScale(), 1.0e-5);
    }
    
    @Test
    public void testGetSampleLabel() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String label = "";
        Assert.assertEquals(label, peakList.getSampleLabel());
    }
    
    @Test
    public void testGetSampleConditionLabel() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String label = "";
        Assert.assertEquals(label, peakList.getSampleConditionLabel());
    }
    
    @Test
    public void testGetDatasetName() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String label = "unknown.nv";
        Assert.assertEquals(label, peakList.getDatasetName());
    }
    
    @Test
    public void testGetDetails() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        String detail = "";
        Assert.assertEquals(detail, peakList.getDetails());
    }
    
    @Test
    public void testIsChanged() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean change = true;
        Assert.assertEquals(change, peakList.isChanged());
    }
    
    @Test
    public void testIsAnyChanged() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean change = true;
        Assert.assertEquals(change, peakList.isAnyChanged());
    }
    
    @Test
    public void testHasMeasures() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean measures = false;
        Assert.assertEquals(measures, peakList.hasMeasures());
    }
    
    @Test
    public void testGetMeasureValues() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        double[] measures = null;
        Assert.assertEquals(measures, peakList.getMeasureValues());
    }
    
    @Test
    public void testHasSearchDims() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean search = false;
        Assert.assertEquals(search, peakList.hasSearchDims());
    }
    
    @Test
    public void testSize() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        int size = 4;
        Assert.assertEquals(size, peakList.size(), 1.0e-5);
    }
    
    @Test
    public void testValid() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean valid = true;
        Assert.assertEquals(valid, peakList.valid());
    }
    
    @Test
    public void testCountMultiplets() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        int size = 0;
        Assert.assertEquals(size, peakList.countMultiplets(), 1.0e-5);
    }
    
    @Test
    public void testGetPeakByID() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        Peak peak = peakList.getPeak(0); //peakList.getPeakByID(0);
        Assert.assertEquals(peak, peakList.getPeakByID(0));
    }
    
    @Test
    public void testGetListDim() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        int dim = 1;
        String s = "N_2";
        Assert.assertEquals(dim, peakList.getListDim(s), 1.0e-5);
    }
    
    @Test
    public void testIsSlideable() {
        PeakList peakList = getPeakList(peakListName2);
        Assert.assertNotNull(peakList);
        
        boolean slide = false;
        Assert.assertEquals(slide, peakList.isSlideable());
    }
    
    
    
}