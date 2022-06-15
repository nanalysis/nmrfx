package org.nmrfx.processor.datasets.vendor.rs2d;

import org.junit.Test;
import org.nmrfx.processor.datasets.DatasetCompare;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DProcUtil;
import org.nmrfx.processor.datasets.vendor.rs2d.XmlUtil;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class RS2DDataTest {
    public static String fidHome = "../../nmrfxp_tests/testfids/";
    public static String tmpHome = "../../nmrfxp_tests/tmp/";
    final static long[] CORRECT = {0,0,0};

    boolean testFilesMissing(File testFile) {
        if (!testFile.exists()) {
            System.out.println("File " + testFile + " doesn't exist, skipping test");
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void save1DToRS2DFile() throws IOException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680/Proc/0").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/1Dproton/680/Proc/0/data.dat").toFile();
        if (testFilesMissing(inFileDat)) {
            return;
        }
        Path procNumPath = Path.of(tmpHome, "Proc", "1");
        File outFile = procNumPath.resolve("data.dat").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null, true);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset, procNumPath);
        long[] compareResult = DatasetCompare.compare(inFileDat, outFile);
        assertArrayEquals(CORRECT, compareResult);
    }

    @Test
    public void save2DToRS2DFile() throws IOException {
        File inFile = Path.of(fidHome, "rs2d/2Dhetero/688/Proc/0").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/2Dhetero/688/Proc/0/data.dat").toFile();
        if (testFilesMissing(inFileDat)) {
            return;
        }
        Path procNumPath = Path.of(tmpHome, "Proc", "2");
        File outFile = procNumPath.resolve("data.dat").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null, true);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset, procNumPath);
        long[] compareResult = DatasetCompare.compare(inFileDat, outFile);
        if (compareResult[1] != 0) {
            compareResult[1] = DatasetCompare.compareFloat(inFileDat, outFile);
        }
        assertArrayEquals(CORRECT, compareResult);
    }

    @Test
    public void modifyRS2DHeaderFile() throws IOException, XPathExpressionException, TransformerException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680").toFile();
        if (testFilesMissing(inFile)) {
            return;
        }
        File outHeader = Path.of(tmpHome, "header_mod.xml").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null, true);
        Document header = rs2DData.getHeader().getDocument();
        XmlUtil.setParam(header, "MATRIX_DIMENSION_1D", "555");
        XmlUtil.setParam(header, "PHASE_0", "55.5");
        XmlUtil.setParam(header, "PHASE_1", "5.55");
        XmlUtil.writeDocument(header, outHeader);
    }

    @Test
    public void findProcNums() {
        Path seriesDirectory = Path.of(fidHome, "rs2d/1Dproton/680");
        if (testFilesMissing(seriesDirectory.toFile())) {
            return;
        }

        var procNums = RS2DProcUtil.listProcIds(seriesDirectory);
        assertEquals(List.of(0), procNums);
    }

    @Test
    public void findLastProcNum() {
        Path seriesDirectory = Path.of(fidHome, "rs2d/1Dproton/680");
        if (testFilesMissing(seriesDirectory.toFile())) {
            return;
        }
        int lastProcNum = RS2DProcUtil.findLastProcId(seriesDirectory).orElse(-1);
        assertEquals(0, lastProcNum);
    }
}
