package org.nmrfx.processor.datasets;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.nmrfx.utils.FormatUtils;
import org.python.util.PythonInterpreter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

public class ProcessTest {

    private static final String VALID_SUBMODULE_LOCATION = "nmrfx-test-data/valid";
    private static final String FID_SUBMODULE_LOCATION = "nmrfx-test-data/testfids/";
    private static final String ERR_MSG = "File doesn't exist: ";
    private static final long[] ARRAYED_RESULT = {-1, -1, -1};
    public static String scriptHome = "src/test/resources/process_scripts";
    private static String fidHome;
    private static String tmpHome;
    private static String validHome;

    @ClassRule
    public static final TemporaryFolder tmpFolder = TemporaryFolder.builder()
            .parentFolder(new File(System.getProperty("user.dir")))
            .assureDeletion()
            .build();

    @BeforeClass
    public static void setup() {
        Path parent = FileSystems.getDefault()
                .getPath("")
                .toAbsolutePath()
                .getParent();
        // Python scripts expect path with / not \, so replace if present
        fidHome = parent.resolve(FID_SUBMODULE_LOCATION).toString().replace("\\", "/") + "/";
        validHome = parent.resolve(VALID_SUBMODULE_LOCATION).toString().replace("\\", "/") + "/";
        tmpHome = tmpFolder.getRoot().toString().replace("\\", "/") + "/";
    }

    public void executeScript(String fileName) {
        Path path = Path.of(scriptHome, fileName + ".py");
        PythonInterpreter interp = new PythonInterpreter();
        interp.exec("from pyproc import *");
        interp.exec("setTestLocations('" + fidHome + "','" + tmpHome + "')");
        interp.exec("useProcessor()");  // necessary to reset between processing multiple files
        interp.execfile(path.toString());
    }

    boolean testFilesMissing(File testFile) throws FileNotFoundException {
        if (!testFile.exists()) {
            boolean isBuildEnv = Boolean.parseBoolean(System.getenv("BUILD_ENV"));
            if (isBuildEnv) {
                throw new FileNotFoundException("Missing build environment requirement. " + ERR_MSG + testFile);
            }
            return true;
        } else {
            return false;
        }
    }

    public long[] runAndCompareDetailed(String fileName) throws IOException {
        File refFile;
        File testFile;
        if (fileName.contains("rs2d")) {
            refFile = Path.of(validHome, fileName, "Proc", "1", "data.dat").toFile();
            testFile = Path.of(tmpHome, "tst_" + fileName, "Proc", "1", "data.dat").toFile();
        } else if (fileName.contains("ucsf")) {
            refFile = Path.of(validHome, "ubiq_hsqc_sf.ucsf").toFile();
            testFile = Path.of(tmpHome, "tst_ubiq_hsqc_sf.ucsf").toFile();
        } else {
            refFile = Path.of(validHome, fileName + ".nv").toFile();
            testFile = Path.of(tmpHome, "tst_" + fileName + ".nv").toFile();
        }
        System.out.println("test file " + testFile);
        // someone running the tests may not have the test files as they take up a lot of disk space
        // so, currently,  if file doesn't exist a message is printed and the test passes
        assumeFalse(ERR_MSG + refFile, testFilesMissing(refFile));
        executeScript(fileName);
        if (refFile.getName().endsWith(".nv") || refFile.getName().endsWith(".ucsf")) {
            return DatasetCompare.compareDetailed(refFile, testFile);
        } else {
            long compareValue = DatasetCompare.compare(refFile, testFile);
            return new long[]{compareValue};
        }
    }

    public long runAndCompare(String fileName) throws IOException {
        long[] detailedResult = runAndCompareDetailed(fileName);
        return detailedResult[0];
    }

    @Test(expected = Test.None.class)
    public void openFilesWithSpecialCharacters() throws IOException {
        List<String> directoryNames = List.of(new String[]{"Qualité", "你好世界"});
        try (PythonInterpreter interp = new PythonInterpreter()) {
            interp.exec("from pyproc import *");
            interp.exec("useProcessor()");  // necessary to reset between processing multiple files
            for (String directoryName : directoryNames) {
                new File(tmpHome + directoryName).mkdir();
                new File(tmpHome + directoryName + "/jcamp").mkdir();
                Path fidOriginal = Paths.get(fidHome + "jcamp/TESTFID.DX");
                Path fidCopy = Paths.get(tmpHome).resolve(directoryName).resolve("jcamp/TESTFID.DX");
                Files.copy(fidOriginal, fidCopy);
                String script = String.format("FID('%s%s/jcamp/TESTFID.DX')\nCREATE('%s%s/jcamp/TESTFID.DX')", tmpHome, directoryName, tmpHome, directoryName);
                interp.exec(FormatUtils.formatStringForPythonInterpreter(script));
            }
        }
    }

    @Test
    public void test_gb1_tract1d() throws IOException {
        long[] result = runAndCompareDetailed("gb1_tract1d");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_hnconus_grins_zf() throws IOException {
        long[] result = runAndCompareDetailed("hnconus_grins_zf");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_hnconus_nesta() throws IOException {
        long[] result = runAndCompareDetailed("hnconus_nesta");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_jcamp_1d() throws IOException {
        long result = runAndCompare("jcamp_1d");
        assertEquals(-1, result);
    }

    @Test
    public void test_jeol_1d_1h() throws IOException {
        long result = runAndCompare("jeol_1d_1h");
        assertEquals(-1, result);
    }

    @Test
    public void test_jeol_hsqc() throws IOException {
        long result = runAndCompare("jeol_hsqc");
        assertEquals(-1, result);
    }

    @Test
    public void test_ubiq_cnoesqc() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_cnoesqc");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hnca() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hnca");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hnco() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hnco");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hnco_nesta_extend() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hnco_nesta_extend");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hnco_grins_extend() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hnco_grins_extend");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hnco_lp() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hnco_lp");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hnco_skip2() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hnco_skip2");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hnco_skip3() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hnco_skip3");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hsqc() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hsqc");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_hsqc_ucsf() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_hsqc_ucsf");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_noe() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_noe");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_t1() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_t1");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_ubiq_t2() throws IOException {
        long[] result = runAndCompareDetailed("ubiq_t2");
        assertArrayEquals(ARRAYED_RESULT, result);
    }

    @Test
    public void test_rs2d_1d() throws IOException {
        long result = runAndCompare("rs2d_1dproton");
        assertEquals(-1, result);
    }

    @Test
    public void test_rs2d_2d() throws IOException {
        long result = runAndCompare("rs2d_2dhetero");
        assertEquals(-1, result);
    }


}
