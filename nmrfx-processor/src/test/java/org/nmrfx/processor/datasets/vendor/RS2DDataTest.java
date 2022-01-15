package org.nmrfx.processor.datasets.vendor;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.datasets.DatasetCompare;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class RS2DDataTest {
    public static String fidHome = "../../nmrfxp_tests/testfids/";
    public static String tmpHome = "../../nmrfxp_tests/tmp/";

    boolean testFilesPresent(File testFile) {
        if (!testFile.exists()) {
            System.out.println("File " + testFile + " doesn't exist, skipping test");
            return false;
        } else {
            return true;
        }
    }

    @Test
    public void save1DToRS2DFile() throws IOException, TransformerException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680/Proc/0").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/1Dproton/680/Proc/0/data.dat").toFile();
        if (!testFilesPresent(inFileDat)) {
            return;
        }
        Path procNumPath = Path.of(tmpHome, "Proc", "1");
        File outFile = procNumPath.resolve("data.dat").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null, true);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset, procNumPath);
        long compareResult = DatasetCompare.compare(inFileDat, outFile);
        Assert.assertEquals(0, compareResult);
    }

    @Test
    public void save2DToRS2DFile() throws IOException, TransformerException {
        File inFile = Path.of(fidHome, "rs2d/2Dhetero/688/Proc/0").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/2Dhetero/688/Proc/0/data.dat").toFile();
        if (!testFilesPresent(inFileDat)) {
            return;
        }
        Path procNumPath = Path.of(tmpHome, "Proc", "2");
        File outFile = procNumPath.resolve("data.dat").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null, true);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset, procNumPath);
        long compareResult = DatasetCompare.compare(inFileDat, outFile);
        if (compareResult != 0) {
            DatasetCompare.compareFloat(inFileDat, outFile);
        }
        Assert.assertEquals(0, compareResult);
    }

    @Test
    public void modifyRS2DHeaderFile() throws IOException, XPathExpressionException, TransformerException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680").toFile();
        if (!testFilesPresent(inFile)) {
            return;
        }
        File outHeader = Path.of(tmpHome, "header_mod.xml").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null, true);
        rs2DData.setParam("MATRIX_DIMENSION_1D", "555");
        rs2DData.setParam("PHASE_0", "55.5");
        rs2DData.setParam("PHASE_1", "5.55");
        Document headerDocument = rs2DData.getHeaderDocument();
        rs2DData.writeDocument(headerDocument, outHeader);
    }

    @Test
    public void findProcNums() throws IOException, XPathExpressionException, TransformerException {
        Path procDirectory = Path.of(fidHome, "rs2d/1Dproton/680/Proc");
        if (!testFilesPresent(procDirectory.toFile())) {
            return;
        }
        var procNums = RS2DData.findProcNums(procDirectory);
        Assert.assertEquals(1, procNums.size());
        if (!procNums.isEmpty()) {
            Assert.assertEquals(0, procNums.get(0).intValue());
        }

    }

    @Test
    public void findLastProcNum() throws IOException, XPathExpressionException, TransformerException {
        Path procDirectory = Path.of(fidHome, "rs2d/1Dproton/680/Proc");
        if (!testFilesPresent(procDirectory.toFile())) {
            return;
        }
        int lastProcNum = RS2DData.findLastProcNum(procDirectory).orElse(-1);
        Assert.assertEquals(0, lastProcNum);
    }
}