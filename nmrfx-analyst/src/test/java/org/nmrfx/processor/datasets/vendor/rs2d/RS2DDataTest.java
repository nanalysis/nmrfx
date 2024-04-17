package org.nmrfx.processor.datasets.vendor.rs2d;

import com.nanalysis.spinlab.dataset.Header;
import com.nanalysis.spinlab.dataset.HeaderWriter;
import com.nanalysis.spinlab.dataset.enums.Parameter;
import com.nanalysis.spinlab.dataset.values.ListNumberValue;
import com.nanalysis.spinlab.dataset.values.NumberValue;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.nmrfx.processor.datasets.DatasetCompare;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

public class RS2DDataTest {
    private static final String FID_SUBMODULE_LOCATION = "nmrfx-test-data/testfids/";
    private static String fidHome;
    private static String tmpHome;
    private static final String ERR_MSG = "File doesn't exist: ";

    @ClassRule
    public static final TemporaryFolder tmpFolder = TemporaryFolder.builder()
            .parentFolder(new File(System.getProperty("user.dir")))
            .assureDeletion()
            .build();

    @BeforeClass
    public static void setup() {
        fidHome = FileSystems.getDefault()
                .getPath("")
                .toAbsolutePath()
                .getParent()
                .resolve(FID_SUBMODULE_LOCATION)
                .toString();
        tmpHome = tmpFolder.getRoot().toString();
    }

    boolean testFilesMissing(File testFile) throws FileNotFoundException {
        if (!testFile.exists()) {
            boolean isBuildEnv = Boolean.parseBoolean(System.getenv("BUILD_ENV"));
            ;
            if (isBuildEnv) {
                throw new FileNotFoundException("Missing build environment requirement. " + ERR_MSG + testFile);
            }
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void save1DToRS2DFile() throws IOException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680/Proc/0").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/1Dproton/680/Proc/0/data.dat").toFile();
        assumeFalse(ERR_MSG + inFile, testFilesMissing(inFile));
        assumeFalse(ERR_MSG + inFileDat, testFilesMissing(inFileDat));
        if (testFilesMissing(inFileDat)) {
            return;
        }
        Path procNumPath = Path.of(tmpHome, "Proc", "1");
        File outFile = procNumPath.resolve("data.dat").toFile();
        RS2DData rs2DData = new RS2DData(inFile, null);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset, procNumPath);
        long compareResult = DatasetCompare.compare(inFileDat, outFile);
        assertEquals(-1, compareResult);
    }

    @Test
    public void save2DToRS2DFile() throws IOException {
        File inFile = Path.of(fidHome, "rs2d/2Dhetero/688/Proc/0").toFile();
        File inFileDat = Path.of(fidHome, "rs2d/2Dhetero/688/Proc/0/data.dat").toFile();
        assumeFalse(ERR_MSG + inFile, testFilesMissing(inFile));
        assumeFalse(ERR_MSG + inFileDat, testFilesMissing(inFileDat));
        Path procNumPath = Path.of(tmpHome, "Proc", "2");
        File outFile = procNumPath.resolve("data.dat").toFile();
        RS2DData rs2DData = new RS2DData(inFile, null);
        var dataset = rs2DData.toDataset("test.nv");
        rs2DData.writeOutputFile(dataset, procNumPath);
        long compareResult = DatasetCompare.compare(inFileDat, outFile);
        if (compareResult >= 0) {
            compareResult = DatasetCompare.compareFloat(inFileDat, outFile);
        }
        assertEquals(-1, compareResult);
    }

    @Test
    public void modifyRS2DHeaderFile() throws IOException, TransformerException, ParserConfigurationException {
        File inFile = Path.of(fidHome, "rs2d/1Dproton/680").toFile();
        assumeFalse(ERR_MSG + inFile, testFilesMissing(inFile));
        File outHeader = Path.of(tmpHome, "header_mod.xml").toFile();
        RS2DData rs2DData = new RS2DData(inFile, null);

        Header header = rs2DData.getHeader();
        header.<NumberValue>get(Parameter.MATRIX_DIMENSION_1D).setValue(555);
        header.<ListNumberValue>get(Parameter.PHASE_0).setValue(List.of(55.5));
        header.<ListNumberValue>get(Parameter.PHASE_1).setValue(List.of(5.55));
        try (OutputStream out = new FileOutputStream(outHeader)) {
            new HeaderWriter().writeXml(header, out);
        }
    }

    @Test
    public void findProcNums() throws FileNotFoundException {
        Path seriesDirectory = Path.of(fidHome, "rs2d/1Dproton/680");
        assumeFalse(ERR_MSG + seriesDirectory, testFilesMissing(seriesDirectory.toFile()));
        var procNums = RS2DProcUtil.listProcIds(seriesDirectory);
        assertEquals(List.of(0), procNums);
    }

    @Test
    public void findLastProcNum() throws FileNotFoundException {
        Path seriesDirectory = Path.of(fidHome, "rs2d/1Dproton/680");
        assumeFalse(ERR_MSG + seriesDirectory, testFilesMissing(seriesDirectory.toFile()));

        int lastProcNum = RS2DProcUtil.findLastProcId(seriesDirectory).orElse(-1);
        assertEquals(0, lastProcNum);
    }
}
