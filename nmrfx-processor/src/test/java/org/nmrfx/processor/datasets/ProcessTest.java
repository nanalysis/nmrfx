package org.nmrfx.processor.datasets;

import org.junit.Assert;
import org.junit.Test;
import org.python.util.PythonInterpreter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ProcessTest {
    String scriptHome = "src/test/resources/process_scripts";
    String tmpHome = "../../tmp";
    String validHome = "../../valid";

    public boolean executeScript(String fileName) {
        Path path = Path.of(scriptHome,fileName+".py");
        PythonInterpreter interp = new PythonInterpreter();
        interp.execfile(path.toString());
        return true;

    }

    public long runAndCompare(String fileName) throws IOException {
        executeScript(fileName);
        File refFile = Path.of(validHome, fileName+".nv").toFile();
        File testFile = Path.of(tmpHome,"tst_" + fileName + ".nv").toFile();
        long result =  DatasetCompare.compare(refFile, testFile);
        return result;
    }

    @Test
    public void test_ubiq_hnca() throws IOException {
        long result = runAndCompare("ubiq_hnca");
        Assert.assertEquals(result, 0);
    }
    @Test
    public void test_gb1_tract1d() throws IOException {
        long result = runAndCompare("gb1_tract1d");
        Assert.assertEquals(result, 0);
    }
}
