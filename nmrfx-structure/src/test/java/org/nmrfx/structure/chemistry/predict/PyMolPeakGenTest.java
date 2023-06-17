package org.nmrfx.structure.chemistry.predict;

import org.junit.Assert;
import org.junit.Test;
import org.python.util.PythonInterpreter;

import java.nio.file.Path;

public class PyMolPeakGenTest {

    public int executeScript(String pyScriptFilename) {
        Path path = Path.of("src/test/python", pyScriptFilename);
        PythonInterpreter interp = new PythonInterpreter();
        interp.exec("import sys");
        interp.exec("sys.path.append('src/test/python')");
        interp.execfile(path.toString());
        var pyResult = interp.eval("result.failures");
        for (var item : pyResult.asIterable()) {
            System.out.println("item FAILURE " + item);
        }
        return pyResult.__len__();
    }

    @Test
    public void testmolpeakgen() {
        int nFailures = executeScript("testmolpeakgen.py");
        Assert.assertEquals(0, nFailures);
    }
}

