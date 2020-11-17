/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.project;

import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.PeakListBase;
import org.nmrfx.peaks.PeakPaths;
import org.nmrfx.peaks.io.PeakReader;
import org.nmrfx.peaks.io.PeakWriter;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.DatasetParameterFile;
import org.nmrfx.peaks.PeakListBase;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author brucejohnson
 */
public class ProjectBase<T extends PeakListBase> {

    static final public Pattern INDEX_PATTERN = Pattern.compile("^([0-9]+)_.*");
    static final public Predicate<String> INDEX_PREDICATE = INDEX_PATTERN.asPredicate();
    final String name;
    public Path projectDir = null;
    public Map<String, PeakPaths> peakPaths;

    protected static ProjectBase getNewProject(String name) {
        return new ProjectBase(name);
    }

    protected Map<String, DatasetBase> datasetMap = new HashMap<>();
    protected List<DatasetBase> datasets = new ArrayList<>();
    protected Map<String, T> peakLists = new HashMap<>();
    static ProjectBase activeProject = null;
    public static PropertyChangeSupport pcs = null;

    public ProjectBase(String name) {
        this.name = name;
        peakPaths = new HashMap<>();
    }

    public static ProjectBase getActive() {
        ProjectBase project = activeProject;
        if (project == null) {
            project = getNewProject("Untitled 1");
        }
        activeProject = project;
        return project;
    }

    static String getName(String s) {
        Matcher matcher = INDEX_PATTERN.matcher(s);
        String name;
        if (matcher.matches()) {
            name = matcher.group(1);
        } else {
            name = s;
        }
        return name;
    }

    public static Optional<Integer> getIndex(String s) {
        Optional<Integer> fileNum = Optional.empty();
        Matcher matcher = INDEX_PATTERN.matcher(s);
        if (matcher.matches()) {
            fileNum = Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return fileNum;
    }

    public final void setActive() {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "project", null, this);
        activeProject = this;
        if (pcs != null) {
            pcs.firePropertyChange(event);
        }
    }
    public String getName() {
        return name;
    }

    public boolean removeDataset(String datasetName) {
        DatasetBase toRemove = datasetMap.get(datasetName);
        boolean result = datasetMap.remove(datasetName) != null;
        refreshDatasetList();
        return result;
    }

    public Map<String, DatasetBase> getDatasetMap() {
        return datasetMap;
    }

    List<DatasetBase> getDatasetList() {
        return datasetMap.values().stream().sorted((a, b) -> a.getName().compareTo(b.getName())).collect(Collectors.toList());
    }

    public List<DatasetBase> getDatasetsWithFile(File file) {
        try {
            String testPath = file.getCanonicalPath();
            List<DatasetBase> datasetsWithFile = datasetMap.values().stream().filter((dataset) -> (dataset.getCanonicalFile().equals(testPath))).collect(Collectors.toList());
            return datasetsWithFile;
        } catch (IOException ex) {
            return Collections.EMPTY_LIST;
        }
    }

    public DatasetBase getDataset(String name) {
        return datasetMap.get(name);
    }

    public List<String> getDatasetNames() {
        return datasetMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    public List<DatasetBase> getDatasets() {
        return datasets;
    }

    public void addDataset(DatasetBase dataset, String datasetName) {
        datasetMap.put(datasetName, dataset);
        refreshDatasetList();
    }

    public boolean isDatasetPresent(File file) {
        return !getDatasetsWithFile(file).isEmpty();
    }

    public boolean isDatasetPresent(String name) {
        return datasetMap.containsKey(name);
    }

    public void refreshDatasetList() {
        datasets.clear();
        datasets.addAll(datasetMap.values());
    }

    public void addPeakList(T peakList, String name) {
        peakLists.put(name, peakList);
    }

    public Collection<T> getPeakLists() {
        return peakLists.values();
    }

    public Map<String, T> getPeakListMap() {
        return peakLists;
    }

    public List<String> getPeakListNames() {
        return peakLists.keySet().stream().sorted().collect(Collectors.toList());
    }

    public T getPeakList(String name) {
        return peakLists.get(name);
    }

    /**
     * Returns an Optional containing the PeakList that has the specified id
     * number or empty value if no PeakList with that id exists.
     *
     * @param listID the id of the peak list
     * @return the Optional containing the PeaKlist or an empty value if no
     * PeakList with that id exists
     */
    public Optional<T> getPeakList(int listID) {
        Optional<T> peakListOpt = peakLists.values().stream().
                filter(p -> (p.getId() == listID)).findFirst();
        return peakListOpt;
    }

    /**
     * Return an Optional containing the PeakList with lowest id number or an
     * empty value if no PeakLists are present.
     *
     * @return Optional containing first peakList if any peak lists present or
     * empty if no peak lists.
     */
    public Optional<T> getFirstPeakList() {
        Optional<T> peakListOpt = peakLists.values().stream().
                sorted((o1, o2) -> Integer.compare(o1.getId(), o2.getId())).findFirst();
        return peakListOpt;
    }

    public void putPeakList(T peakList) {
        peakLists.put(peakList.getName(), peakList);
    }

    public void removePeakList(String name) {
        peakLists.remove(name);
    }

    public void clearAllPeakLists() {
        peakLists.clear();
    }

    public boolean hasDirectory() {
        return projectDir != null;
    }

    public Path getDirectory() {
        return projectDir;
    }

    public boolean isDatasetPresent(DatasetBase dataset) {
        return datasetMap.containsValue(dataset);
    }

    public void clearAllDatasets() {
        List<DatasetBase> removeDatasets = new ArrayList<>();
        removeDatasets.addAll(datasets);
        for (DatasetBase datasetBase : removeDatasets) {
            datasetBase.close();
        }
        datasetMap.clear();
        datasets.clear();
    }

    public void setProjectDir(Path projectDir) {
        this.projectDir = projectDir;
    }

    public void addPeakListListener(Object mapChangeListener) {
    }

    public void addDatasetListListener(Object mapChangeListener) {
    }

    public void loadProject(Path projectDir) throws IOException, IllegalStateException {

        String[] subDirTypes = {"datasets", "peaks"};
        for (String subDir : subDirTypes) {
            loadProject(projectDir, subDir);
        }

    }

    public void loadProject(Path projectDir, String subDir) throws IOException, IllegalStateException {
        ProjectBase currentProject = getActive();
        setActive();
        FileSystem fileSystem = FileSystems.getDefault();
        if (projectDir != null) {
            Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
            if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                switch (subDir) {
                    case "datasets":
                        loadDatasets(subDirectory);
                        break;
                    case "peaks":
                        loadPeaks(subDirectory);
                        break;
                    default:
                        throw new IllegalStateException("Invalid subdir type");
                }
            }

        }
        this.projectDir = projectDir;
        currentProject.setActive();
    }

    public void saveProject() throws IOException {
        ProjectBase currentProject = getActive();
        setActive();
        if (getDirectory() == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        savePeakLists();
        saveDatasets();
        currentProject.setActive();
    }

    void loadPeaks(Path directory) throws IOException {
        FileSystem fileSystem = FileSystems.getDefault();
        if (Files.isDirectory(directory)) {
            PeakReader peakReader = new PeakReader(true);
            try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(directory, "*.xpk2")) {
                for (Path f : fileStream) {
                    String filePath = f.toString();
                    System.out.println("read peaks: " + f.toString());
                    PeakListBase peakList = peakReader.readXPK2Peaks(f.toString());
                    String mpk2File = filePath.substring(0, filePath.length() - 4) + "mpk2";
                    Path mpk2Path = fileSystem.getPath(mpk2File);
                    if (Files.exists(mpk2Path)) {
                        peakReader.readMPK2(peakList, mpk2Path.toString());
                    }

                }
            } catch (DirectoryIteratorException | IOException ex) {
                throw new IOException(ex.getMessage());
            }
            peakReader.linkResonances();
        }
    }

    void savePeakLists() throws IOException {
        FileSystem fileSystem = FileSystems.getDefault();

        if (getDirectory() == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        Path projectDir = this.getDirectory();
        Path peakDirPath = Paths.get(projectDir.toString(), "peaks");
        Files.list(peakDirPath).forEach(path -> {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(".xpk2") || fileName.endsWith(".mpk2")) {
                String listName = fileName.substring(0, fileName.length() - 5);
                if (PeakListBase.get(listName) == null) {
                    try {
                        Files.delete(path);
                    } catch (IOException ex) {
                        Logger.getLogger(ProjectBase.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            }
        });

        peakLists.values().stream().forEach(peakListObj -> {
            PeakListBase peakList = (PeakListBase) peakListObj;
            Path peakFilePath = fileSystem.getPath(projectDir.toString(), "peaks", peakList.getName() + ".xpk2");
            Path measureFilePath = fileSystem.getPath(projectDir.toString(), "peaks", peakList.getName() + ".mpk2");
            // fixme should only write if file doesn't already exist or peaklist changed since read
            try {
                try (FileWriter writer = new FileWriter(peakFilePath.toFile())) {
                    PeakWriter peakWriter = new PeakWriter();
                    peakWriter.writePeaksXPK2(writer, peakList);
                    writer.close();
                }
                if (peakList.hasMeasures()) {
                    try (FileWriter writer = new FileWriter(measureFilePath.toFile())) {
                        PeakWriter peakWriter = new PeakWriter();
                        peakWriter.writePeakMeasures(writer, peakList);
                        writer.close();
                    }
                }
            } catch (IOException | InvalidPeakException ioE) {
            }
        });
    }

    public void loadDatasets(Path directory) throws IOException {
        Pattern pattern = Pattern.compile("(.+)\\.(nv|ucsf)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            Files.list(directory).sequential().filter(path -> predicate.test(path.getFileName().toString())).
                    forEach(path -> {
                        System.out.println("read dataset: " + path.toString());
                        String pathName = path.toString();
                        String fileName = path.getFileName().toString();

                        try {
                            DatasetBase dataset = new DatasetBase(pathName, fileName, false, false);
                            File regionFile = DatasetRegion.getRegionFile(path.toString());
                            System.out.println("region " + regionFile.toString());
                            if (regionFile.canRead()) {
                                System.out.println("read");
                                TreeSet<DatasetRegion> regions = DatasetRegion.loadRegions(regionFile);
                                dataset.setRegions(regions);
                            }

                        } catch (IOException ex) {
                            Logger.getLogger(ProjectBase.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
        }
        refreshDatasetList();
    }

    public void saveDatasets() throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        Path datasetDir = projectDir.resolve("datasets");

        for (Object datasetBase : datasetMap.values()) {
            DatasetBase dataset = (DatasetBase) datasetBase;
            File datasetFile = dataset.getFile();
            if (datasetFile != null) {
                Path currentPath = datasetFile.toPath();
                Path fileName = currentPath.getFileName();
                Path pathInProject = datasetDir.resolve(fileName);
                // fixme should we have option to copy file, rather than make  link
                // or add text file with path to original
                if (!Files.exists(pathInProject)) {
                    try {
                        Files.createLink(pathInProject, currentPath);
                    } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                        Files.createSymbolicLink(pathInProject, currentPath);
                    }
                }
                String parFilePath = DatasetParameterFile.getParameterFileName(pathInProject.toString());
                dataset.writeParFile(parFilePath);
                TreeSet<DatasetRegion> regions = dataset.getRegions();
                File regionFile = DatasetRegion.getRegionFile(pathInProject.toString());
                DatasetRegion.saveRegions(regionFile, regions);
            }
        }
    }

    public static class FileComparator implements Comparator<Path> {

        @Override
        public int compare(Path p1, Path p2) {
            String s1 = p1.getFileName().toString();
            String s2 = p2.getFileName().toString();
            int result;
            Optional<Integer> f1 = getIndex(s1);
            Optional<Integer> f2 = getIndex(s2);
            if (f1.isPresent() && !f2.isPresent()) {
                result = 1;
            } else if (!f1.isPresent() && f2.isPresent()) {
                result = -1;
            } else if (f1.isPresent() && f2.isPresent()) {
                int i1 = f1.get();
                int i2 = f2.get();
                result = Integer.compare(i1, i2);
            } else {
                if (s1.endsWith(".seq") && !s2.endsWith(".seq")) {
                    result = 1;
                } else if (!s1.endsWith(".seq") && s2.endsWith(".seq")) {
                    result = -1;
                } else if (s1.endsWith(".pdb") && !s2.endsWith(".pdb")) {
                    result = 1;
                } else if (!s1.endsWith(".pdb") && s2.endsWith(".pdb")) {
                    result = -1;
                } else {
                    result = s1.compareTo(s2);
                }

            }
            return result;
        }

    }
}
