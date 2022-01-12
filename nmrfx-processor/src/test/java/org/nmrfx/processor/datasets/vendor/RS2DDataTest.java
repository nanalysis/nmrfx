package org.nmrfx.processor.datasets.vendor;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class RS2DDataTest {
    public static String fidHome = "../../nmrfxp_tests/testfids/";
    public static String tmpHome = "../../nmrfxp_tests/tmp/";

    @Test
    public void saveToRS2DFile() throws IOException {
        String fileName = Path.of(fidHome, "rs2d/1Dproton/680").toString();
        String outFileName = Path.of(tmpHome, "680.nv").toString();
        RS2DData rs2DData = new RS2DData(fileName, null,true);
        var dataset = rs2DData.toDataset("test.nv");
        RS2DData.saveToRS2DFile(dataset,outFileName);
    }
}