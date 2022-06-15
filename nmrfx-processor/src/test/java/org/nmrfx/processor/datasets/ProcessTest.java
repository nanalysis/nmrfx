package org.nmrfx.processor.datasets;

import org.junit.Assert;
import org.junit.Test;
import org.python.util.PythonInterpreter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ProcessTest {
    public static String scriptHome = "src/test/resources/process_scripts";
    public static String fidHome = "../../nmrfxp_tests/testfids/";
    public static String tmpHome = "../../nmrfxp_tests/tmp/";
    public static String validHome = "../../nmrfxp_tests/valid";
    final static long[] CORRECT = {0,0,0};

    public boolean executeScript(String fileName) {
        Path path = Path.of(scriptHome, fileName + ".py");
        PythonInterpreter interp = new PythonInterpreter();
        interp.exec("from pyproc import *");
        interp.exec("FIDHOME='" + fidHome + "'");
        interp.exec("TMPHOME='" + tmpHome + "'");
        interp.exec("useProcessor()");  // necessary to reset between processing multiple files
        interp.execfile(path.toString());
        return true;

    }

    public long[] runAndCompare(String fileName) throws IOException {
        File refFile;
        File testFile;
        if (fileName.contains("rs2d")) {
            refFile=Path.of(validHome, fileName, "Proc","1","data.dat").toFile();
            testFile = Path.of(tmpHome, "tst_" + fileName,"Proc","1","data.dat").toFile();
        } else {
            refFile=Path.of(validHome, fileName + ".nv").toFile();
            testFile = Path.of(tmpHome, "tst_" + fileName + ".nv").toFile();
        }
        // someone running the tests may not have the test files as they take up a lot of disk space
        // so, currently,  if file doesn't exist a message is printed and the test passes
        if (!refFile.exists()) {
            System.out.println("File " + fileName + " doesn't exist, skipping test");
            return new long[]{0,0,0};
        } else {
            executeScript(fileName);
            long[] result = DatasetCompare.compare(refFile, testFile);
            return result;
        }
    }
    @Test
    public void test_gb1_tract1d() throws IOException {
        long[] result = runAndCompare("gb1_tract1d");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_hnconus_grins_zf() throws IOException {
        long[] result = runAndCompare("hnconus_grins_zf");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_hnconus_nesta() throws IOException {
        long[] result = runAndCompare("hnconus_nesta");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_jcamp_1d() throws IOException {
        long[] result = runAndCompare("jcamp_1d");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_jeol_1d_1h() throws IOException {
        long[] result = runAndCompare("jeol_1d_1h");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_jeol_hsqc() throws IOException {
        long[] result = runAndCompare("jeol_hsqc");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_cnoesqc() throws IOException {
        long[] result = runAndCompare("ubiq_cnoesqc");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_hnca() throws IOException {
        long[] result = runAndCompare("ubiq_hnca");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_hnco() throws IOException {
        long[] result = runAndCompare("ubiq_hnco");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_hnco_lp() throws IOException {
        long[] result = runAndCompare("ubiq_hnco_lp");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_hnco_skip2() throws IOException {
        long[] result = runAndCompare("ubiq_hnco_skip2");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_hnco_skip3() throws IOException {
        long[] result = runAndCompare("ubiq_hnco_skip3");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_hsqc() throws IOException {
        long[] result = runAndCompare("ubiq_hsqc");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_hsqc_ucsf() throws IOException {
        long[] result = runAndCompare("ubiq_hsqc_ucsf");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_noe() throws IOException {
        long[] result = runAndCompare("ubiq_noe");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_t1() throws IOException {
        long[] result = runAndCompare("ubiq_t1");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_ubiq_t2() throws IOException {
        long[] result = runAndCompare("ubiq_t2");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_rs2d_1d() throws IOException {
        long[] result = runAndCompare("rs2d_1dproton");
        Assert.assertArrayEquals(CORRECT, result);
    }

    @Test
    public void test_rs2d_2d() throws IOException {
        long[] result = runAndCompare("rs2d_2dhetero");
        Assert.assertArrayEquals(CORRECT, result);
    }


}
