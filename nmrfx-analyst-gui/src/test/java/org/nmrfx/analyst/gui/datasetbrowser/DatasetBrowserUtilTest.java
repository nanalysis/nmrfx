package org.nmrfx.analyst.gui.datasetbrowser;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.utilities.DatasetSummary;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class DatasetBrowserUtilTest {
    private static Path tmpPath;
    private static Path scanDirectory;
    private static Path fidPath;
    private static Path validPath;

    @ClassRule
    public static final TemporaryFolder tmpFolder = TemporaryFolder.builder()
            .parentFolder(new File(System.getProperty("user.dir")))
            .assureDeletion()
            .build();

    @BeforeClass
    public static void setup() throws IOException {
        tmpPath = tmpFolder.getRoot().toPath();
        Path parent = FileSystems.getDefault()
                .getPath("")
                .toAbsolutePath()
                .getParent();
        fidPath = parent.resolve("nmrfx-test-data/testfids/");
        validPath = parent.resolve("nmrfx-test-data/valid");
        scanDirectory = tmpFolder.newFolder("DirectoryToScan").toPath();
    }

    @Test
    public void testScanDirectoryWithMultipleProcessedNvFiles() throws IOException {
        Path multipleDatasetsPath = Path.of(scanDirectory.toString(), "gb1_tract1d");
        FileUtils.copyDirectory(Path.of(fidPath.toString(), "bruker/gb1_tract1d").toFile(), multipleDatasetsPath.toFile());
        FileUtils.copyFile(Path.of(validPath.toString(), "gb1_tract1d.nv").toFile(), Path.of(scanDirectory.toString(), "gb1_tract1d/6/gb1_tract1d.nv").toFile());
        // Make a second copy of gb1_tract1d.nv with different filename to mimic multiple processed files
        FileUtils.copyFile(Path.of(validPath.toString(), "gb1_tract1d.nv").toFile(), Path.of(scanDirectory.toString(), "gb1_tract1d/6/gb1_tract1d_copy.nv").toFile());
        List<DatasetSummary> summaries = DatasetBrowserUtil.scanDirectory(multipleDatasetsPath.toString(), tmpPath.resolve("nmrfx_index.json"));
        assertEquals(1, summaries.size());
        assertEquals(2, summaries.get(0).getProcessed().size());
    }

    @Test
    public void testScanDirectoryWithRS2DProccessedFiles() throws IOException {
        Path rs2dDirectoryPath = Path.of(scanDirectory.toString(), "680");
        FileUtils.copyDirectory(Path.of(fidPath.toString(), "rs2d/1Dproton/680").toFile(), rs2dDirectoryPath.toFile());
        List<DatasetSummary> summaries = DatasetBrowserUtil.scanDirectory(rs2dDirectoryPath.toString(), tmpPath.resolve("nmrfx_index.json"));
        assertEquals(1, summaries.size());
        assertEquals(1, summaries.get(0).getProcessed().size());
    }

    @Test
    public void testScanDirectoryWithJCAMPProccessedNvFiles() throws IOException {
        File testFidPath = Path.of(scanDirectory.toString(), "jcampfolder/jcamptest.dx").toFile();
        FileUtils.copyFile(Path.of(fidPath.toString(), "jcamp/TESTFID.DX").toFile(), testFidPath);
        Path validDatasetPath =  Path.of(scanDirectory.toString(), "jcampfolder/jcamp_1d.nv");
        FileUtils.copyFile(Path.of(validPath.toString(), "jcamp_1d.nv").toFile(), validDatasetPath.toFile());
        // Set the sourceFID of the dataset and write it to the par file
        Dataset validDataset = (new NMRViewData(validDatasetPath.toFile(), false)).getDataset();
        validDataset.sourceFID(testFidPath);
        validDataset.writeParFile();
        validDataset.close();
        List<DatasetSummary> summaries = DatasetBrowserUtil.scanDirectory(testFidPath.getParent(), tmpPath.resolve("nmrfx_index.json"));
        assertEquals(1, summaries.size());
        assertEquals(1, summaries.get(0).getProcessed().size());

    }
}
