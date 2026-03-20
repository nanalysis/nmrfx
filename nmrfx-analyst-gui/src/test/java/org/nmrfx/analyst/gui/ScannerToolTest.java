package org.nmrfx.analyst.gui;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.analyst.gui.tools.ScannerTool;

public class ScannerToolTest {

    @Test
    public void testHeaderParser() {
        Assert.assertTrue(ScannerTool.matchHeader("V0:3.7_2.7_4.0_2.5_VW").isPresent());
        Assert.assertTrue(ScannerTool.matchHeader("V0:3.7_2.7_VR").isPresent());
        Assert.assertTrue(ScannerTool.matchHeader("V0:3.7_2.7_VN").isPresent());
        Assert.assertTrue(ScannerTool.matchHeader("V0:3.7_2.7").isPresent());
        Assert.assertTrue(ScannerTool.matchHeader("V0:1.3509_1.1650_VN").isPresent());
        Assert.assertTrue(ScannerTool.matchHeader("V0:3.7_2.7").isPresent());
        Assert.assertTrue(ScannerTool.matchHeader("V0:3.7_2.7_4.0_2.5_MW").isPresent());
    }
}
