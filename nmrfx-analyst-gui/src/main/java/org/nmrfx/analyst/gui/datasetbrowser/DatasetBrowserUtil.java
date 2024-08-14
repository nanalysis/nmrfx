package org.nmrfx.analyst.gui.datasetbrowser;

import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.jcamp.JCAMPData;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.utilities.DatasetSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DatasetBrowserUtil {
    private static final Logger log = LoggerFactory.getLogger(DatasetBrowserUtil.class);

    private DatasetBrowserUtil() {
        throw new IllegalAccessError("Utility class shouldn't be instantiated!");
    }
    public static List<DatasetSummary> scanDirectory(String scanDir, Path savePath) {
        List<DatasetSummary> items = new ArrayList<>();
        Path path1 = Paths.get(scanDir);
        if (!path1.toFile().exists()) {
            return items;
        }
        var files = NMRDataUtil.findNMRDirectories(scanDir);
        if (files == null) {
            return items;
        }
        // Need to handle the JCAMP files differently
        Map<Boolean, List<String>> partitionedFiles = files.stream().collect(Collectors.partitioningBy(f -> JCAMPData.findFID(new File(f))));
        items.addAll(handleJcampFiles(path1, partitionedFiles.get(Boolean.TRUE)));
        for (String fileName : partitionedFiles.get(Boolean.FALSE)) {
            try {
                NMRData data = NMRDataUtil.getFID(new File(fileName));
                DatasetSummary datasetSummary = createDatasetSummary(path1, data, getProcessedDataset(Paths.get(fileName).toFile()));
                items.add(datasetSummary);
            } catch (Exception ex) {
                log.error(ex.getMessage() + " " + fileName, ex);
            }
        }
        if (savePath != null) {
            DatasetSummary.saveItems(savePath, items);
        }
        return items;
    }



    /**
     * Create a DatasetSummary from a FID NMRData and a list of processed paths associated with the NMRData.
     * @param relativeDirectory The relative parent directory of the location of the NMRData file.
     * @param nmrData The NMRData to create a summary for.
     * @param paths A list of paths of processed data for the provided fid nmrData.
     * @return A DatasetSummary object.
     */
    private static DatasetSummary createDatasetSummary(Path relativeDirectory, NMRData nmrData, List<Path> paths) {
        DatasetSummary datasetSummary = nmrData.getDatasetSummary();
        Path nmrDataPath = Paths.get(nmrData.getFilePath());
        datasetSummary.setPath(relativeDirectory.relativize(nmrDataPath).toString());
        if (nmrDataPath.toFile().isFile()) {
            nmrDataPath = nmrDataPath.getParent();
        }
        datasetSummary.setPresent(true);
        datasetSummary.setProcessed(paths.stream()
                .sorted((a,b) -> Long.compare(b.toFile().lastModified(),a.toFile().lastModified()))
                .map(nmrDataPath::relativize).map(Path::toString).toList());
        return datasetSummary;
    }

    /**
     * Get a list of DatasetSummary for each fid JCAMP file in the list of jcampFilepathStrings. This filepath list may
     * contain processed JCAMP files as well which will be assigned to their associated fid DatasetSummary. Also checks
     * scanning directory for any processed datasets in .nv format and tries to match them with their fid.
     * @param relativeDirectory The relative parent directory of the location of the jcampFilepathStrings.
     * @param jcampFilepathStrings A list of filepath strings for jcamp files in the relativeDirectory.
     * @return A list of DatasetSummary objects.
     */
    private static List<DatasetSummary> handleJcampFiles(Path relativeDirectory, List<String> jcampFilepathStrings) {
        if (jcampFilepathStrings.isEmpty()) {
            return new ArrayList<>();
        }
        // Convert the filepath strings to JCAMPData
        List<JCAMPData> nmrData = new ArrayList<>(jcampFilepathStrings.stream().map(filepath -> {
            try {
                return NMRDataUtil.getNMRData(new File(filepath));
            } catch (IOException e) {
                log.warn(e.getMessage(), e);
                return null;
            }
        }).filter(JCAMPData.class::isInstance).map(JCAMPData.class::cast).toList());

        // Add all fid files as keys in map
        Map<JCAMPData, List<Path>> fidDatasetMap = nmrData.stream().filter(NMRData::isFID).collect(Collectors.toMap(Function.identity(), u -> new ArrayList<>()));
        nmrData.removeAll(fidDatasetMap.keySet());
        Map<Path, DatasetBase> pathDatasetBaseCache = new HashMap<>();
        for (Map.Entry<JCAMPData, List<Path>> fidData: fidDatasetMap.entrySet()) {
            // Get matching JCAMP datasets
            List<JCAMPData> datasets = nmrData.stream().filter(jcampData -> jcampData.getTitle().equals(fidData.getKey().getTitle())).toList();
            if (!datasets.isEmpty()) {
                fidData.getValue().addAll(datasets.stream().map(dataset -> Path.of(dataset.getFilePath())).toList());
            }
            // Get Matching .nv datasets
            File fidFile = new File(fidData.getKey().getFilePath());
            fidData.getValue().addAll(getProcessedNMRViewDatasetsAndUpdateDatasetMap(fidFile, pathDatasetBaseCache));
        }

        // Close all the datasets datafiles
        pathDatasetBaseCache.values().forEach(DatasetBase::close);

        // Create the dataset summaries
        List<DatasetSummary> summaries = new ArrayList<>();
        for (Map.Entry<JCAMPData, List<Path>> fidData: fidDatasetMap.entrySet()) {
            DatasetSummary summary = createDatasetSummary(relativeDirectory, fidData.getKey(), fidData.getValue());
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * Search for any processed NMRViewData associated with a jcamp fid file, update the provided pathDatasetBaseMap
     * with any newly loaded dataset base objects and return the list of paths.
     * @param fidFile The jcamp fid file.
     * @param pathDatasetBaseCache A map of Path, DatasetBase to avoid loading datasets more than once.
     * @return A list of processed paths associated with the fidFile.
     */
    private static List<Path> getProcessedNMRViewDatasetsAndUpdateDatasetMap(File fidFile, Map<Path, DatasetBase> pathDatasetBaseCache) {
        List<Path> paths = getProcessedDataset(fidFile.getParentFile());
        paths.forEach(p -> {
            if (!pathDatasetBaseCache.containsKey(p)) {
                try {
                    NMRData data = NMRDataUtil.loadNMRData(p.toFile(), null, false);
                    if (data instanceof NMRViewData nmrViewData) {
                        pathDatasetBaseCache.put(p, nmrViewData.getDataset());
                    }
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }
        });

        return pathDatasetBaseCache.values().stream().filter(datasetBase -> {
            if (datasetBase.sourceFID().isEmpty()) {
                return false;
            }
            return fidFile.getName().equals(datasetBase.sourceFID().get().getName());
        }).map(datasetBase -> datasetBase.getFile().toPath()).toList();
    }


    public static List<Path> getProcessedDataset(File localFile) {
        List<Path> processed = new ArrayList<>();
        try {
            if (localFile.exists()) {
                processed = NMRDataUtil.findProcessedFiles(localFile.toPath());
                if (!processed.isEmpty()) {
                    processed.sort((o1, o2) -> {
                                try {
                                    FileTime time1 = Files.getLastModifiedTime(o1);
                                    FileTime time2 = Files.getLastModifiedTime(o2);
                                    return time1.compareTo(time2);
                                } catch (IOException ex) {
                                    log.warn(ex.getMessage(), ex);
                                    return 0;
                                }
                            }
                    );
                }
            }
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return processed;
    }
}
