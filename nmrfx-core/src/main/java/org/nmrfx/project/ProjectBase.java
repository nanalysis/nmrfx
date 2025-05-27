/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.project;

import org.eclipse.jgit.util.FS;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.Compound;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetFactory;
import org.nmrfx.datasets.DatasetParameterFile;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.PeakPaths;
import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.peaks.io.PeakReader;
import org.nmrfx.peaks.io.PeakWriter;
import org.nmrfx.star.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;

/**
 * @author brucejohnson
 */
@PluginAPI("parametric")
public class ProjectBase {
    private static final String PEAKS = "peaks";
    private static final String PROJECT_DIR_NOT_SET = "Project directory not set";
    private static final Logger log = LoggerFactory.getLogger(ProjectBase.class);

    protected static final String[] SUB_DIR_TYPES = {"star", "datasets", "molecules", "peaks", "shifts", "refshifts", "windows"};

    private static final Pattern INDEX_PATTERN = Pattern.compile("^([0-9]+)_.*");
    private static final Pattern INDEX2_PATTERN = Pattern.compile("^.*_([0-9]+).*");
    private static final Map<String, SaveframeProcessor> saveframeProcessors = new HashMap<>();
    private static PropertyChangeSupport pcs = null;
    private static ProjectBase activeProject = null;
    final String name;
    protected Path projectDir = null;
    protected Map<String, PeakPaths> peakPaths = new HashMap<>();
    protected Map<String, Compound> compoundMap = new HashMap<>();
    protected Map<String, MoleculeBase> molecules = new HashMap<>();
    protected MoleculeBase activeMol = null;

    protected ResonanceFactory resFactory;

    protected Map<String, DatasetBase> datasetMap = new HashMap<>();
    protected List<DatasetBase> datasets = new ArrayList<>();
    protected Map<String, PeakList> peakLists = new HashMap<>();
    protected List<SaveframeWriter> extraSaveframes = new ArrayList<>();

    protected GitBase gitManager;

    protected ProjectBase(String name) {
        this.name = name;
    }

    public static ProjectBase getNewProject(String name) {
        ProjectBase projectBase;
        try {
            Class<?> c = Class.forName("org.nmrfx.processor.gui.project.GUIProject");
            Class[] parameterTypes = {String.class};
            Constructor constructor = c.getDeclaredConstructor(parameterTypes);
            projectBase = (ProjectBase) constructor.newInstance(name);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            projectBase = new ProjectBase(name);
        }
        return projectBase;
    }

    public static ProjectBase getActive() {
        ProjectBase project = activeProject;
        if (project == null) {
            project = getNewProject("Untitled 1");
        }
        activeProject = project;
        return project;
    }

    public static void setPCS(PropertyChangeSupport newPCS) {
        pcs = newPCS;
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
        Matcher matcher2 = INDEX2_PATTERN.matcher(s);
        if (matcher.matches()) {
            fileNum = Optional.of(Integer.parseInt(matcher.group(1)));
        } else if (matcher2.matches()) {
            fileNum = Optional.of(Integer.parseInt(matcher2.group(1)));
        }
        return fileNum;
    }

    public final void clearActive() {
        activeProject = null;
    }
    public final void setActive() {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "project", null, this);
        activeProject = this;
        if (pcs != null) {
            pcs.firePropertyChange(event);
        }
    }

    public static ResonanceFactory activeResonanceFactory() {
        return getActive().resonanceFactory();
    }
    public ResonanceFactory resonanceFactory() {
        if (resFactory == null) {
            resFactory = new ResonanceFactory();
            resFactory.init();
        }
        return resFactory;
    }
    public String getName() {
        return name;
    }

    public Map<String, Compound> getCompoundMap() {
        return compoundMap;
    }

    public boolean removeDataset(String datasetName) {
        boolean result = datasetMap.remove(datasetName) != null;
        refreshDatasetList();
        return result;
    }

    public boolean removeDataset(String datasetName, DatasetBase dataset) {
        boolean result = datasetMap.remove(datasetName, dataset);
        refreshDatasetList();
        return result;
    }

    public Map<String, DatasetBase> getDatasetMap() {
        return datasetMap;
    }

    List<DatasetBase> getDatasetList() {
        return datasetMap.values().stream().sorted(Comparator.comparing(DatasetBase::getName)).toList();
    }

    public List<DatasetBase> getDatasetsWithFile(File file) {
        try {
            String testPath = requireNonNullElse(file.getCanonicalPath(), "");
            return datasetMap.values().stream().filter(dataset -> (testPath.equals(dataset.getCanonicalFile()))).toList();
        } catch (IOException ex) {
            return Collections.emptyList();
        }
    }

    public DatasetBase getDataset(String name) {
        return datasetMap.get(name);
    }

    public List<String> getDatasetNames() {
        return datasetMap.keySet().stream().sorted().toList();
    }

    public List<DatasetBase> getDatasets() {
        return datasets;
    }

    public void addSaveframe(SaveframeWriter saveframeWriter) {
        extraSaveframes.add(saveframeWriter);
    }

    public void copySaveFrames(ProjectBase newProject) {
        for (var saveFramewriter : extraSaveframes) {
            newProject.addSaveframe(saveFramewriter);
        }
    }

    public static void addSaveframeProcessor(String category, SaveframeProcessor saveframeProcessor) {
        saveframeProcessors.put(category, saveframeProcessor);
    }

    public void writeSaveframes(Writer chan) throws ParseException, IOException {
        for (SaveframeWriter saveframeWriter : extraSaveframes) {
            saveframeWriter.write(chan);
        }
    }

    public static void processExtraSaveFrames(STAR3 star3) throws ParseException {
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframeProcessors.containsKey(saveframe.getCategoryName())) {
                try {
                    saveframeProcessors.get(saveframe.getCategoryName()).process(saveframe);
                } catch (IOException e) {
                    throw new ParseException(e.getMessage());
                }
            }
        }
    }

    public void addDataset(DatasetBase dataset, String datasetName) {
        datasetMap.put(datasetName, dataset);
        refreshDatasetList();
        projectChanged(true);
    }

    public void renameDataset(DatasetBase dataset, String newName) {
        datasetMap.remove(dataset.getFileName(), dataset);
        dataset.setFileName(newName);
        addDataset(dataset, newName);
        projectChanged(true);
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

    public void addPeakList(PeakList peakList, String name) {
        peakLists.put(name, peakList);
    }

    public Collection<PeakList> getPeakLists() {
        return peakLists.values();
    }

    public Map<String, PeakList> getPeakListMap() {
        return peakLists;
    }

    public List<String> getPeakListNames() {
        return peakLists.keySet().stream().sorted().toList();
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
        return peakLists.values().stream().
                filter(p -> (p.getId() == listID)).findFirst();
    }

    /**
     * Return an Optional containing the PeakList with the lowest id number or an
     * empty value if no PeakLists are present.
     *
     * @return Optional containing first peakList if any peak lists present or
     * empty if no peak lists.
     */
    public Optional<PeakList> getFirstPeakList() {
        return peakLists.values().stream().min(Comparator.comparingInt(PeakList::getId));
    }

    public GitBase getGitManager() {
        return gitManager;
    }

    public void putPeakList(PeakList peakList) {
        peakLists.put(peakList.getName(), peakList);
    }

    public void removePeakList(String name) {
        peakLists.remove(name);
        projectChanged(true);
    }

    public void clearAllPeakLists() {
        peakLists.clear();
        projectChanged(true);
    }

    public MoleculeBase getActiveMolecule() {
        if ((activeMol == null) && !getMolecules().isEmpty()){
            activeMol = getMolecules().stream().findFirst().orElse(null);
        }
        return activeMol;
    }

    public void setActiveMolecule(MoleculeBase molecule) {
        activeMol = molecule;
        PropertyChangeEvent event = new PropertyChangeEvent(this, "molecule", null, activeMol);
        if (pcs != null) {
            pcs.firePropertyChange(event);
        }
    }

    public void putMolecule(MoleculeBase molecule) {
        molecules.put(molecule.getName(), molecule);
        projectChanged(true);
    }

    public MoleculeBase getMolecule(String name) {
        return molecules.get(name);
    }

    public Collection<MoleculeBase> getMolecules() {
        return molecules.values();
    }

    public Collection<String> getMoleculeNames() {
        return molecules.keySet();
    }

    public void setMoleculeMap(Map<String, MoleculeBase> newMap) {
        molecules = newMap;
    }

    public void removeMolecule(String name) {
        var mol = molecules.get(name);
        if (mol == activeMol) {
            activeMol = null;
        }
        if (mol != null) {
            molecules.remove(name);
        }
        projectChanged(true);
    }

    public void clearAllMolecules() {
        activeMol = null;
        molecules.clear();
        projectChanged(true);
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
        List<DatasetBase> removeDatasets = new ArrayList<>(datasets);
        for (DatasetBase datasetBase : removeDatasets) {
            datasetBase.close();
        }
        datasetMap.clear();
        datasets.clear();
        refreshDatasetList();
    }

    public void setProjectDir(Path projectDir) {
        this.projectDir = projectDir;
        if (pcs != null) {
            pcs.firePropertyChange(new PropertyChangeEvent(this, "project", null, this));
        }
    }

    public Path getProjectDir() {
        return this.projectDir;
    }

    // used in subclasses
    public void addPeakListListener(Object mapChangeListener) {
    }

    // used in subclasses
    public void addDatasetListListener(Object mapChangeListener) {
    }

    public void loadProject(Path projectDir, String subDir) throws IOException, IllegalStateException {
        ProjectBase currentProject = getActive();
        setActive();
        boolean mpk2Mode = false;
        if (subDir.equals("mpk2")) {
            subDir = PEAKS;
            mpk2Mode = true;
        }
        FileSystem fileSystem = FileSystems.getDefault();
        if (projectDir != null) {
            Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
            if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                switch (subDir) {
                    case "datasets" -> loadDatasets(subDirectory);
                    case PEAKS -> {
                        if (mpk2Mode) {
                            loadMPKs(subDirectory);
                        } else {
                            loadPeaks(subDirectory);
                        }
                    }
                    default -> throw new IllegalStateException("Invalid subdir type");
                }
            }

        }
        setProjectDir(projectDir);
        currentProject.setActive();
    }

    public void saveProject() throws IOException {
        ProjectBase currentProject = getActive();
        setActive();
        if (getDirectory() == null) {
            throw new IllegalArgumentException(PROJECT_DIR_NOT_SET);
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

    void loadMPKs(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            PeakReader peakReader = new PeakReader(true);
            try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(directory, "*.mpk2")) {
                for (var f : fileStream) {

                    String fileName = f.toFile().getName();
                    String peakListName = fileName.substring(0, fileName.length() - 5);
                    PeakList peakList = PeakList.get(peakListName);
                    if ((peakList != null) && Files.exists(f)) {
                        peakReader.readMPK2(peakList, f.toString());
                    }
                }
            } catch (DirectoryIteratorException | IOException ex) {
                throw new IOException(ex.getMessage());
            }
        }
    }

    void savePeakLists() throws IOException {
        FileSystem fileSystem = FileSystems.getDefault();

        if (getDirectory() == null) {
            throw new IllegalArgumentException(PROJECT_DIR_NOT_SET);
        }
        Path projDir = this.getDirectory();
        Path peakDirPath = Paths.get(projDir.toString(), PEAKS);
        try (Stream<Path> files = Files.list(peakDirPath)) {
            files.forEach(path -> {
                String fileName = path.getFileName().toString();
                if (fileName.endsWith(".xpk2") || fileName.endsWith(".mpk2")) {
                    String listName = fileName.substring(0, fileName.length() - 5);
                    if (PeakList.get(listName) == null) {
                        try {
                            Files.delete(path);
                        } catch (IOException ex) {
                            log.error(ex.getMessage(), ex);
                        }
                    }

                }
            });
        }

        for (PeakList peakListObj : peakLists.values()) {
            Path peakFilePath = fileSystem.getPath(projDir.toString(), PEAKS, peakListObj.getName() + ".xpk2");
            Path measureFilePath = fileSystem.getPath(projDir.toString(), PEAKS, peakListObj.getName() + ".mpk2");
            // fixme should only write if file doesn't already exist or peaklist changed since read
            try {
                try (FileWriter writer = new FileWriter(peakFilePath.toFile())) {
                    PeakWriter peakWriter = new PeakWriter();
                    peakWriter.writePeaksXPK2(writer, peakListObj);
                }
                if (peakListObj.hasMeasures()) {
                    try (FileWriter writer = new FileWriter(measureFilePath.toFile())) {
                        PeakWriter peakWriter = new PeakWriter();
                        peakWriter.writePeakMeasures(writer, peakListObj);
                    }
                }
            } catch (IOException | InvalidPeakException ioE) {
                log.warn(ioE.getMessage(), ioE);
            }
        }
    }

    public void loadDatasets(Path directory) throws IOException {
        Pattern pattern = Pattern.compile("\\.(nv|ucsf|nvlnk)$");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path -> predicate.test(path.getFileName().toString())).
                        forEach(path -> {
                            String pathName = path.toString();
                            String fileName = path.getFileName().toString();
                            boolean isLinkFile = fileName.endsWith(".nvlnk");
                            DatasetBase dataset;
                            try {
                                if (isLinkFile) {
                                    dataset = DatasetFactory.newLinkDataset(fileName.substring(0, fileName.length() - 6), pathName);
                                } else {
                                    dataset = DatasetFactory.newDataset(pathName, fileName, false, false);
                                }
                                // The link files have no way of saving the integral norm, so recalculate from the regions file.
                                loadRegions(path.toString(), dataset, isLinkFile);
                            } catch (IOException ex) {
                                log.error(ex.getMessage(), ex);
                            }
                        });
            }
        }
        refreshDatasetList();
    }

    /**
     * Loads the regions from the provided filename and sets them in the dataset. If resetNorm is true, the
     * integral norm is calculated from the regions and set in the dataset.
     *
     * @param regionFileStr The string path of the region file.
     * @param dataset       The dataset to set the regions for.
     * @param resetNorm     Whether to reset the integral norm.
     * @throws IOException if there is problem loading the regions.
     */
    private void loadRegions(String regionFileStr, DatasetBase dataset, boolean resetNorm) throws IOException {
        File regionFile = DatasetRegion.getRegionFile(regionFileStr);
        if (regionFile.canRead()) {
            List<DatasetRegion> regions = DatasetRegion.loadRegions(regionFile);
            if (!DatasetRegion.isLongRegionFile(regionFile)) {
                for (DatasetRegion region : regions) {
                    region.measure(dataset);
                }
            }
            dataset.setRegions(regions);
            if (resetNorm) {
                dataset.setNormFromRegions(regions);
            }
        }
    }

    public void saveMemoryFile(DatasetBase datasetBase) {

    }

    public void saveDatasets() throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException(PROJECT_DIR_NOT_SET);
        }
        Path datasetDir = projectDir.resolve("datasets");

        for (DatasetBase datasetBase : datasetMap.values()) {
            File datasetFile = datasetBase.getFile();
            if (datasetFile == null) {
                // Save any extracted projection datasets that are vec matrix based
                if (datasetBase.getFileName().contains(DatasetBase.DATASET_PROJECTION_TAG) && datasetBase.getVec() != null) {
                    File newFile = new File(datasetDir.toFile(), datasetBase.getName());
                    datasetBase.writeVecMat(newFile);
                }
                continue;
            } else {
                if (datasetBase.getFileName().contains(DatasetBase.DATASET_PROJECTION_TAG)) {
                    continue;
                }
            }
            Path currentPath = datasetFile.toPath();
            Path fileNameAsPath = currentPath.getFileName();
            String fileName = fileNameAsPath.toString();
            Path pathInProject;

            if (fileName.endsWith(".nv") || fileName.endsWith(".ucsf")) {
                pathInProject = datasetDir.resolve(fileNameAsPath);
                if (!Files.exists(pathInProject)) {
                    try {
                        Files.createLink(pathInProject, currentPath);
                    } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                        Files.createSymbolicLink(pathInProject, currentPath);
                    }
                }
            } else {
                String fileLinkName = datasetBase.getName() + ".nvlnk";
                pathInProject = datasetDir.resolve(fileLinkName);
                Files.writeString(pathInProject, datasetFile.getAbsolutePath());
            }
            String parFilePath = DatasetParameterFile.getParameterFileName(pathInProject.toString());
            datasetBase.writeParFile(parFilePath);
            List<DatasetRegion> regions = datasetBase.getReadOnlyRegions();
            if (!regions.isEmpty()) {
                File regionFile = DatasetRegion.getRegionFile(pathInProject.toString());
                DatasetRegion.saveRegions(regionFile, regions);
            }
        }
    }

    public Map<String, PeakPaths> getPeakPaths() {
        return peakPaths;
    }

    public static class FileComparator implements Comparator<Path> {

        @Override
        public int compare(Path p1, Path p2) {
            String s1 = p1.getFileName().toString();
            String s2 = p2.getFileName().toString();
            int result;
            Optional<Integer> f1 = getIndex(s1);
            Optional<Integer> f2 = getIndex(s2);
            if (f1.isPresent() && f2.isEmpty()) {
                result = 1;
            } else if (f1.isEmpty() && f2.isPresent()) {
                result = -1;
            } else if (f1.isPresent()) {
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

    protected void createProjectDirectory(Path projectDir) throws IOException {
        FileSystem fileSystem = FileSystems.getDefault();
        if (Files.exists(projectDir)) {
            throw new IllegalArgumentException("Project directory \"" + projectDir + "\" already exists");
        }
        Files.createDirectory(projectDir);
        for (String subDir : SUB_DIR_TYPES) {
            Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
            Files.createDirectory(subDirectory);
        }
        setProjectDir(projectDir);}

    public void createProject(Path projectDir) throws IOException {
        createProjectDirectory(projectDir);
        checkUserHomePath();
        gitManager = new GitBase(this);
        setActive();
    }

    public void writeIgnore() {
        Path path = Paths.get(projectDir.toString(), ".gitignore");
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write("*.nv\n*.ucsf\njffi*\n");
        } catch (IOException ioE) {
            log.warn("{}", ioE.getMessage(), ioE);
        }

    }

    /***
     * Checks if the user home path that will be used by git exists and will be writable. jgit checks for the user
     * home path in the preference of XDG_CONFIG_HOME, HOME, (HOMEDRIVE, HOMEPATH) and HOMESHARE. If XDG_CONGIG_HOME is set, then that
     * path is used regardless of whether its writable. Otherwise the first environment variable that is set is checked
     * for existence and writability. If it fails the check, the userHome variable of jgit FS is set to the 'user.home'
     * property.
     */
    public static void checkUserHomePath() {
        if (getEnvironmentVariable("XDG_CONFIG_HOME") != null) {
            return;
        }
        boolean setUserHome;
        String home = getEnvironmentVariable("HOME");
        if (home != null) {
            setUserHome = isFileWritable(new File(home));
        } else {
            String homeDrive = getEnvironmentVariable("HOMEDRIVE");
            String homePath = getEnvironmentVariable("HOMEPATH");
            if (homeDrive != null && homePath != null) {
                setUserHome = isFileWritable(new File(homeDrive, homePath));
            } else {
                String homeShare = getEnvironmentVariable("HOMESHARE");
                if (homeShare != null) {
                    setUserHome = isFileWritable(new File(homeShare));
                } else {
                    setUserHome = true;
                }
            }
        }
        if (setUserHome) {
            File userHome = new File(System.getProperty("user.home"));
            FS.DETECTED.setUserHome(userHome);
            log.info("Setting jgit config file path to: {}", userHome);
        }
    }

    private static boolean isFileWritable(File file) {
        return !file.exists() || (file.exists() && !file.canWrite());
    }

    public static String getEnvironmentVariable(String name) {
        return System.getenv(name);
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

    public void projectChanged(boolean state) {

    }

    public boolean projectChanged() {
        return false;
    }
}
