package org.nmrfx.processor.gui;

import org.junit.Assert;
import org.junit.Test;

public class ScannerControllerTest {

    @Test
    public void testHeaderParser() {
        Assert.assertTrue(ScannerController.matchHeader("V.0:3.7_2.7_4.0_2.5_VW").isPresent());
        Assert.assertTrue(ScannerController.matchHeader("V.0:3.7_2.7_VR").isPresent());
        Assert.assertTrue(ScannerController.matchHeader("V.0:3.7_2.7_VN").isPresent());
        Assert.assertTrue(ScannerController.matchHeader("V.0:3.7_2.7").isPresent());
        Assert.assertTrue(ScannerController.matchHeader("V.0:1.3509_1.1650_VN").isPresent());
        Assert.assertTrue(ScannerController.matchHeader("V.0:3.7_2.7").isPresent());
        Assert.assertTrue(ScannerController.matchHeader("V.0:3.7_2.7_4.0_2.5_MW").isPresent());
    }
}
