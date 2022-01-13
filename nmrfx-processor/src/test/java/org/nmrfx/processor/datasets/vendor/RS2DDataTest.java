package org.nmrfx.processor.datasets.vendor;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.processor.datasets.DatasetCompare;
import org.w3c.dom.Document;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class RS2DDataTest {
    public static String fidHome = "../../nmrfxp_tests/testfids/";
    public static String tmpHome = "../../nmrfxp_tests/tmp/";

    @Test
    public void saveToRS2DFile() throws IOException, TransformerException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/1Dproton/680/data.dat").toFile();
        File outFile = Path.of(tmpHome, "680.nv").toFile();
        File outHeader = Path.of(tmpHome, "header.xml").toFile();
        RS2DData rs2DData = new RS2DData(inFile.toString(), null,true);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset,1, tmpHome);
        RS2DData.saveToRS2DFile(dataset,outFile.toString());
        long compareResult = DatasetCompare.compare(inFileDat, outFile);
        rs2DData.writeHeader(outHeader);
        Assert.assertEquals(0, compareResult);
    }
}