/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetParameterFile;
import org.nmrfx.processor.datasets.DatasetRegion;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakPath;
import org.nmrfx.processor.datasets.peaks.io.PeakReader;
import org.nmrfx.processor.datasets.peaks.io.PeakWriter;
import org.nmrfx.processor.datasets.peaks.ResonanceFactory;
import java.util.stream.Collectors;

/**
 *
 * @author Bruce Johnson
 */
public class Project {

    static final Pattern INDEX_PATTERN = Pattern.compile("^([0-9]+)_.*");
    static final Predicate<String> INDEX_PREDICATE = INDEX_PATTERN.asPredicate();
    static String[] SUB_DIR_TYPES = {"star", "datasets", "molecules", "peaks", "shifts", "refshifts", "windows"};
    static final Map<String, Project> projects = new HashMap<>();
    static Project activeProject = null;
    Path projectDir = null;
    final String name;
    protected Map<String, PeakList> peakLists;
    protected Map<String, Dataset> datasetMap;
    protected List<Dataset> datasets = new ArrayList<Dataset>();
    public ResonanceFactory resFactory;
    public Map<String, PeakPath> peakPaths;
    public static PropertyChangeSupport pcs = null;

    public Project(String name) {
        this.name = name;
        this.datasetMap = new HashMap<>();
        this.resFactory = getNewResFactory();
        this.resFactory.init();
        peakLists = new HashMap<>();
        peakPaths = new HashMap<>();

        setActive();
    }

    public static void setPCS(PropertyChangeSupport newPCS) {
        pcs = newPCS;
    }

    private ResonanceFactory getNewResFactory() {
        ResonanceFactory resFact;
        try {
            Class c = Class.forName("org.nmrfx.processor.datasets.peaks.AtomResonanceFactory");
            try {
                resFact = (ResonanceFactory) c.newInstance();
            } catch (InstantiationException | IllegalAccessException ex) {
                resFact = new ResonanceFactory();
            }
        } catch (ClassNotFoundException ex) {
            resFact = new ResonanceFactory();
        }
        return resFact;
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

    public boolean hasDirectory() {
        return projectDir != null;
    }

    public Path getDirectory() {
        return projectDir;
    }

    public static Optional<Integer> getIndex(String s) {
        Optional<Integer> fileNum = Optional.empty();
        Matcher matcher = INDEX_PATTERN.matcher(s);
        if (matcher.matches()) {
            fileNum = Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return fileNum;
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

    public final void setActive() {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "project", null, this);
        activeProject = this;
        if (pcs != null) {
            pcs.firePropertyChange(event);
        }
    }

    public static Project getActive() {
        Project project = activeProject;
        if (project == null) {
            project = getNewProject("Untitled 1");
        }
        return project;
    }

    private static Project getNewProject(String name) {
        Project project = null;
        try {
            Class c = Class.forName("org.nmrfx.project.GUIStructureProject");
            project = (Project) c.getDeclaredConstructor(String.class).newInstance(name);
        } catch (Exception ex) {
            project = getNewStructureProject(name);
        }
        return project;
    }

    private static Project getNewStructureProject(String name) {
        Project project = null;
        try {
            Class c = Class.forName("org.nmrfx.project.StructureProject");
            project = (Project) c.getDeclaredConstructor(String.class).newInstance(name);
        } catch (Exception ex) {
            project = getNewGUIProject(name);
        }
        return project;
    }

    private static Project getNewGUIProject(String name) {
        Project project = null;
        try {
            Class c = Class.forName("org.nmrfx.project.GUIProject");
            project = (Project) c.getDeclaredConstructor(String.class).newInstance(name);
        } catch (Exception ex) {
            project = new Project(name);
        }
        return project;
    }

    public void createProject(Path projectDir) throws IOException {
        if (Files.exists(projectDir)) {
            throw new IllegalArgumentException("Project directory \"" + projectDir + "\" already exists");
        }
        FileSystem fileSystem = FileSystems.getDefault();
        Files.createDirectory(projectDir);
        for (String subDir : SUB_DIR_TYPES) {
            Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
            Files.createDirectory(subDirectory);
        }
        this.projectDir = projectDir;
    }

    public void loadProject(Path projectDir) throws IOException, IllegalStateException {

        String[] subDirTypes = {"datasets", "peaks"};
        for (String subDir : subDirTypes) {
            loadProject(projectDir, subDir);
        }

    }

    public void loadProject(Path projectDir, String subDir) throws IOException, IllegalStateException {
        Project currentProject = getActive();
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
        Project currentProject = getActive();
        setActive();
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        savePeakLists();
        saveDatasets();
        currentProject.setActive();
    }

    public void addDataset(Dataset dataset, String datasetName) {
        datasetMap.put(datasetName, dataset);
        refreshDatasetList();
    }

    public boolean removeDataset(String datasetName) {
        Dataset toRemove = datasetMap.get(datasetName);
        boolean result = datasetMap.remove(datasetName) != null;
        refreshDatasetList();
        return result;
    }

    public void refreshDatasetList() {
        datasets.clear();
        datasets.addAll(datasetMap.values());
    }

    List<Dataset> getDatasetList() {
        return datasetMap.values().stream().
                sorted((a, b) -> a.getName().compareTo(b.getName())).
                collect(Collectors.toList());
    }

    public List<Dataset> getDatasetsWithFile(File file) {
        try {
            String testPath = file.getCanonicalPath();
            List<Dataset> datasetsWithFile = datasetMap.values().stream().
                    filter((dataset) -> (dataset.getCanonicalFile().equals(testPath))).
                    collect(Collectors.toList());
            return datasetsWithFile;
        } catch (IOException ex) {
            return Collections.EMPTY_LIST;
        }
    }

    public boolean isDatasetPresent(String name) {
        return datasetMap.containsKey(name);
    }

    public boolean isDatasetPresent(Dataset dataset) {
        return datasetMap.containsValue(dataset);
    }

    public boolean isDatasetPresent(File file) {
        return !getDatasetsWithFile(file).isEmpty();
    }

    public Dataset getDataset(String name) {
        return datasetMap.get(name);
    }

    public List<String> getDatasetNames() {
        return datasetMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    public List<Dataset> getDatasets() {
        return datasets;
    }

    void loadDatasets(Path directory) throws IOException {
        Pattern pattern = Pattern.compile("(.+)\\.(nv|ucsf)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            Files.list(directory).sequential().filter(path -> predicate.test(path.getFileName().toString())).
                    forEach(path -> {
                        System.out.println("read dataset: " + path.toString());
                        String pathName = path.toString();
                        String fileName = path.getFileName().toString();

                        try {
                            Dataset dataset = new Dataset(pathName, fileName, false, false);
                            File regionFile = DatasetRegion.getRegionFile(path.toString());
                            System.out.println("region " + regionFile.toString());
                            if (regionFile.canRead()) {
                                System.out.println("read");
                                TreeSet<DatasetRegion> regions = DatasetRegion.loadRegions(regionFile);
                                dataset.setRegions(regions);
                            }

                        } catch (IOException ex) {
                            Logger.getLogger(Project.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
        }
        refreshDatasetList();
    }

    void saveDatasets() throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        Path datasetDir = projectDir.resolve("datasets");

        for (Dataset dataset : datasetMap.values()) {
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

    void loadPeaks(Path directory) throws IOException {
        FileSystem fileSystem = FileSystems.getDefault();
        if (Files.isDirectory(directory)) {
            PeakReader peakReader = new PeakReader(true);
            try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(directory, "*.xpk2")) {
                for (Path f : fileStream) {
                    String filePath = f.toString();
                    System.out.println("read peaks: " + f.toString());
                    PeakList peakList = peakReader.readXPK2Peaks(f.toString());
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

        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        Path projectDir = this.projectDir;
        Path peakDirPath = Paths.get(projectDir.toString(), "peaks");
        Files.list(peakDirPath).forEach(path -> {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(".xpk2") || fileName.endsWith(".mpk2")) {
                String listName = fileName.substring(0, fileName.length() - 5);
                if (PeakList.get(listName) == null) {
                    try {
                        Files.delete(path);
                    } catch (IOException ex) {
                        Logger.getLogger(Project.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            }
        });

        peakLists.values().stream().forEach(peakList -> {
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

    public void addPeakList(PeakList peakList, String name) {
        peakLists.put(name, peakList);
    }

    public Collection<PeakList> getPeakLists() {
        return peakLists.values();
    }

    public List<String> getPeakListNames() {
        return peakLists.keySet().stream().sorted().collect(Collectors.toList());
    }

    public PeakList getPeakList(String name) {
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
    public Optional<PeakList> getPeakList(int listID) {
        Optional<PeakList> peakListOpt = peakLists.values().stream().
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
    public Optional<PeakList> getFirstPeakList() {
        Optional<PeakList> peakListOpt = peakLists.values().stream().
                sorted((o1, o2) -> Integer.compare(o1.getId(), o2.getId())).findFirst();
        return peakListOpt;
    }

    public void putPeakList(PeakList peakList) {
        peakLists.put(peakList.getName(), peakList);
    }

    public void clearAllPeakLists() {
        peakLists.clear();
    }

    public void clearAllDatasets() {
        List<Dataset> removeDatasets = new ArrayList<>();
        removeDatasets.addAll(datasets);
        for (Dataset dataset : removeDatasets) {
            dataset.close();
        }
        datasetMap.clear();
        datasets.clear();
    }

    public void removePeakList(String name) {
        peakLists.remove(name);
    }

    public void addPeakListListener(Object mapChangeListener) {
    }

    public void addDatasetListListener(Object mapChangeListener) {
    }

    /**
     * Add a PropertyChangeListener to the listener list. The listener is
     * registered for all properties. The same listener object may be added more
     * than once, and will be called as many times as it is added. If listener
     * is null, no exception is thrown and no action is taken.
     */
    public static void addPropertyChangeListener(PropertyChangeListener listener) {
        if (pcs != null) {
            pcs.addPropertyChangeListener(listener);
        }
    }

    /**
     * Remove a PropertyChangeListener from the listener list. This removes a
     * PropertyChangeListener that was registered for all properties. If
     * listener was added more than once to the same event source, it will be
     * notified one less time after being removed. If listener is null, or was
     * never added, no exception is thrown and no action is taken.
     */
    public static void removePropertyChangeListener(PropertyChangeListener listener) {
        if (pcs != null) {
            pcs.removePropertyChangeListener(listener);
        }
    }

    /**
     * Returns an array of all the listeners that were added to the
     * PropertyChangeSupport object with addPropertyChangeListener().
     */
    public static PropertyChangeListener[] getPropertyChangeListeners() {
        if (pcs == null) {
            return null;
        } else {
            return pcs.getPropertyChangeListeners();
        }
    }

}
