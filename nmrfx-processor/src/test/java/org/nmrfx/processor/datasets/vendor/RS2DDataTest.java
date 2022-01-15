package org.nmrfx.processor.datasets.vendor;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.datasets.DatasetCompare;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RS2DDataTest {
    public static String fidHome = "../../nmrfxp_tests/testfids/";
    public static String tmpHome = "../../nmrfxp_tests/tmp/";

    @Test
    public void save1DToRS2DFile() throws IOException, TransformerException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680/Proc/0").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/1Dproton/680/Proc/0/data.dat").toFile();
        File outFile = Path.of(tmpHome, "Proc/1/data.dat").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null,true);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset,1, tmpHome);
        long compareResult = DatasetCompare.compare(inFileDat, outFile);
        Assert.assertEquals(0, compareResult);
    }
    @Test
    public void save2DToRS2DFile() throws IOException, TransformerException {
        File inFile = Path.of(fidHome, "rs2d/2Dhetero/688/Proc/0").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/2Dhetero/688/Proc/0/data.dat").toFile();
        File outFile = Path.of(tmpHome, "Proc/2/data.dat").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null,true);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset,2, tmpHome);
        long compareResult = DatasetCompare.compare(inFileDat, outFile);
        if (compareResult != 0) {
            DatasetCompare.compareFloat(inFileDat,outFile);
        }
        Assert.assertEquals(0, compareResult);
    }
    @Test
    public void modifyRS2DHeaderFile() throws IOException, XPathExpressionException, TransformerException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680").toFile();
        File outHeader = Path.of(tmpHome, "header_mod.xml").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null,true);
        rs2DData.setParam("MATRIX_DIMENSION_1D","555");
        rs2DData.setParam("PHASE_0","55.5");
        rs2DData.setParam("PHASE_1","5.55");
        Document headerDocument = rs2DData.getHeaderDocument();
        rs2DData.writeDocument(headerDocument, outHeader);
    }
    @Test
    public void findProcNums() throws IOException, XPathExpressionException, TransformerException {
        Path procDirectory = Path.of(fidHome, "rs2d/1Dproton/680/Proc");
        var procNums = RS2DData.findProcNums(procDirectory);
        Assert.assertEquals(1, procNums.size());
        if (!procNums.isEmpty()) {
            Assert.assertEquals(0, procNums.get(0).intValue());
        }

    }
    @Test
    public void findLastProcNum() throws IOException, XPathExpressionException, TransformerException {
        Path procDirectory = Path.of(fidHome, "rs2d/1Dproton/680/Proc");
        int lastProcNum = RS2DData.findLastProcNum(procDirectory).orElse(-1);
        Assert.assertEquals(0, lastProcNum);
    }
}