/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.project;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.*;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.MainApp;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.processor.gui.utils.PeakListUpdater;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 *
 * @author Bruce Johnson
 */
public class GUIProject extends ProjectBase {
    private static final Logger log = LoggerFactory.getLogger(GUIProject.class);

    static String[] SUB_DIR_TYPES = {"star", "datasets", "molecules", "peaks", "shifts", "refshifts", "windows"};

    Git git;

    private static boolean commitActive = false;

    public GUIProject(String name) {
        super(name);
        log.info("new project {}", name);
        peakLists = FXCollections.observableHashMap();
        datasetMap = FXCollections.observableHashMap();
        datasets = FXCollections.observableArrayList();
        setActive();
    }

    public static GUIProject replace(String name, GUIProject project) {
        log.info("replace to {}", name);
        GUIProject newProject = new GUIProject(name);
        project.copySaveFrames(newProject);
        newProject.datasetMap.putAll(project.datasetMap);
        newProject.peakLists.putAll(project.peakLists);
        newProject.peakPaths.putAll(project.peakPaths);
        newProject.compoundMap.putAll(project.compoundMap);
        newProject.molecules.putAll(project.molecules);
        newProject.activeMol = project.activeMol;
        return newProject;
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
        setProjectDir(projectDir);
        try {
            PreferencesController.saveRecentProjects(projectDir.toString());
            git = Git.init().setDirectory(projectDir.toFile()).call();
        } catch (GitAPIException ex) {
            log.error(ex.getMessage(), ex);
        }
        if (git != null) {
            writeIgnore();
        }
    }

    public static GUIProject getActive() {
        ProjectBase project = ProjectBase.getActive();
        if (project == null) {
            project = new GUIProject("Untitled 1");
        }
        return (GUIProject) project;
    }

    private void writeIgnore() {
        if (git != null) {
            Path path = Paths.get(projectDir.toString(), ".gitignore");
            try (FileWriter writer = new FileWriter(path.toFile())) {
                writer.write("*.nv\n*.ucsf");
            } catch (IOException ioE) {
                log.warn("{}", ioE.getMessage(), ioE);
            }
        }
    }

    public void close() {
        clearAllMolecules();
        clearAllPeakLists();
        clearAllDatasets();
        MainApp.closeAll();
        // Clear the project directory or else a user may accidentally overwrite their previously closed project
        setProjectDir(null);
    }

    public static boolean checkProjectActive() {
        ProjectBase project = ProjectBase.getActive();
        boolean hasMolecules = !MoleculeFactory.getMolecules().isEmpty();
        boolean hasDatasets =  project != null && !project.getDatasets().isEmpty();
        boolean hasPeakLists = project != null && !project.getPeakLists().isEmpty();
        return hasMolecules || hasDatasets || hasPeakLists;
    }

    public void loadGUIProject(Path projectDir) throws IOException, MoleculeIOException, IllegalStateException {
        ProjectBase currentProject = getActive();
        setActive();

        loadProject(projectDir, "datasets");
        FileSystem fileSystem = FileSystems.getDefault();

        String[] subDirTypes = {"star", "peaks", "molecules", "shifts", "refshifts", "windows"};
        if (projectDir != null) {
            boolean readSTAR3 = false;
            for (String subDir : subDirTypes) {
                log.debug("read {} {}", subDir, readSTAR3);
                Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
                if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                    switch (subDir) {
                        case "star":
                            readSTAR3 = loadSTAR3(subDirectory);
                            break;
                        case "molecules":
                            if (!readSTAR3) {
                                loadMolecules(subDirectory);
                            }
                            break;
                        case "peaks":
                            if (!readSTAR3) {
                                log.debug("readpeaks");
                                loadProject(projectDir, "peaks");
                            } else {
                                loadProject(projectDir, "mpk2");
                            }
                            break;
                        case "shifts":
                            if (!readSTAR3) {
                                loadShiftFiles(subDirectory, false);
                            }
                            break;
                        case "refshifts":
                            loadShiftFiles(subDirectory, true);
                            break;
                        case "windows":
                            loadWindows(subDirectory);
                            break;

                        default:
                            throw new IllegalStateException("Invalid subdir type");
                    }
                }

            }
        }
        setProjectDir(projectDir);
        PreferencesController.saveRecentProjects(projectDir.toString());
        currentProject.setActive();
    }

    private File getSTAR3FileName() {
        File directory = FileSystems.getDefault().getPath(projectDir.toString(), "star").toFile();
        Path starFile = FileSystems.getDefault().getPath(directory.toString(), projectDir.getFileName().toString() + ".str");
        return starFile.toFile();
    }

    private File getSTAR3FileName(Path directory) {
        Path starFile = FileSystems.getDefault().getPath(directory.toString(), projectDir.getFileName().toString() + ".str");
        return starFile.toFile();

    }

    public void saveProject() throws IOException {
        ProjectBase currentProject = getActive();
        setActive();
        try {
            if (projectDir == null) {
                throw new IllegalArgumentException("Project directory not set");
            }
            checkSubDirs(projectDir);
            super.saveProject();
            NMRStarWriter.writeAll(getSTAR3FileName());
            saveShifts(false);
            saveShifts(true);
        } catch (ParseException | InvalidPeakException | InvalidMoleculeException ex) {
            throw new IOException(ex.getMessage());
        }
        if (currentProject == this) {
            saveWindows(projectDir);
        }
        gitCommitOnThread();
        PreferencesController.saveRecentProjects(projectDir.toString());
        currentProject.setActive();
        currentProject.setActive();
    }

    boolean loadSTAR3(Path directory) throws IOException {
        File starFile = getSTAR3FileName(directory);
        boolean result = false;
        if (starFile.exists()) {
            try {
                NMRStarReader.read(starFile);
                result = true;
            } catch (ParseException ex) {
                throw new IOException(ex.getMessage());
            }
        }
        return result;
    }

    void loadMolecules(Path directory) throws MoleculeIOException {
        Path sstructPath = null;
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(directory)) {
                for (Path path : fileStream) {
                    if (Files.isDirectory(path)) {
                        loadMoleculeEntities(path);
                    } else {
                        if (path.toString().endsWith(".2str")) {
                            sstructPath = path;
                        } else {
                            loadMolecule(path);
                        }
                    }

                }
            } catch (DirectoryIteratorException | IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        if (sstructPath != null) {
            try {
                List<String> content = Files.readAllLines(sstructPath);
                if (MoleculeFactory.getActive() != null) {
                    loadSecondaryStructure(MoleculeFactory.getActive(), content);
                }
            } catch (IOException ioE) {
                throw new MoleculeIOException(ioE.getMessage());
            }
        }
    }

    void loadSecondaryStructure(MoleculeBase molecule, List<String> fileContent) {
        // fixme use yaml ?
        for (String s : fileContent) {
            if (s.contains(("vienna"))) {
                String[] fields = s.split(":");
                if (fields.length == 2) {
                    molecule.setDotBracket(fields[1].trim());
                }
            }
        }

    }

    public static void loadMolecule(Path file) throws MoleculeIOException {
        if (file.toString().endsWith(".pdb")) {
            PDBFile pdbReader = new PDBFile();
            pdbReader.readSequence(file.toString(), false, 0);
        } else if (file.toString().endsWith(".sdf")) {
            SDFile.read(file.toString(), null);
        } else if (file.toString().endsWith(".mol")) {
            SDFile.read(file.toString(), null);
        } else if (file.toString().endsWith(".seq")) {
            Sequence seq = new Sequence();
            seq.read(file.toString());
        }
        if (MoleculeFactory.getActive() == null) {
            throw new MoleculeIOException("Couldn't open any molecules");
        }
        log.info("active mol {}", MoleculeFactory.getActive().getName());
    }

    void loadMoleculeEntities(Path directory) throws MoleculeIOException, IOException {
        String molName = directory.getFileName().toString();
        MoleculeBase mol = MoleculeFactory.newMolecule(molName);
        PDBFile pdbReader = new PDBFile();
        Pattern pattern = Pattern.compile("(.+)\\.(seq|pdb|mol|sdf)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path -> predicate.test(path.getFileName().toString())).
                        sorted(new FileComparator()).
                        forEach(path -> {
                            String pathName = path.toString();
                            String fileName = path.getFileName().toString();
                            Matcher matcher = pattern.matcher(fileName);
                            String baseName = matcher.group(1);

                            try {
                                if (fileName.endsWith(".seq")) {
                                    Sequence sequence = new Sequence();
                                    sequence.read(pathName);
                                } else if (fileName.endsWith(".pdb")) {
                                    if (mol.entities.isEmpty()) {
                                        pdbReader.readSequence(pathName, false, 0);
                                    } else {
                                        PDBFile.readResidue(pathName, null, mol, baseName);
                                    }
                                } else if (fileName.endsWith(".sdf")) {
                                    SDFile.read(pathName, null, mol, baseName);
                                } else if (fileName.endsWith(".mol")) {
                                    SDFile.read(pathName, null, mol, baseName);
                                }
                            } catch (MoleculeIOException molE) {
                                log.warn(molE.getMessage(), molE);
                            }

                        });
            }
        }
    }

    void loadShiftFiles(Path directory, boolean refMode) throws MoleculeIOException, IOException {
        MoleculeBase mol = MoleculeFactory.getActive();
        Pattern pattern = Pattern.compile("(.+)\\.(txt|ppm)");
        Predicate<String> predicate = pattern.asPredicate();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.sequential().filter(path -> predicate.test(path.getFileName().toString())).
                        sorted(new FileComparator()).
                        forEach(path -> {
                            String fileName = path.getFileName().toString();
                            Optional<Integer> fileNum = getIndex(fileName);
                            int ppmSet = fileNum.isPresent() ? fileNum.get() : 0;
                            PPMFiles.readPPM(mol, path, ppmSet, refMode);
                        });
            }
        }
    }

    void saveShifts(boolean refMode) throws IOException {
        MoleculeBase mol = MoleculeFactory.getActive();
        if (mol == null) {
            return;
        }
        FileSystem fileSystem = FileSystems.getDefault();

        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        int nSets = refMode ? mol.getRefPPMSetCount() : mol.getPPMSetCount();
        for (int ppmSet = 0; ppmSet < nSets; ppmSet++) {
            String fileName = String.valueOf(ppmSet) + "_" + "ppm.txt";
            String subDir = refMode ? "refshifts" : "shifts";
            Path peakFilePath = fileSystem.getPath(projectDir.toString(), subDir, fileName);
            // fixme should only write if file doesn't already exist or peaklist changed since read
            try (FileWriter writer = new FileWriter(peakFilePath.toFile())) {
                PPMFiles.writePPM(mol, writer, ppmSet, refMode);
                writer.close();
            } catch (IOException ioE) {
                throw ioE;
            }
        }
    }

    void gitCommitOnThread() {
        Task<Boolean> task = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return gitCommit();
            }
        };
        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }

    public static boolean isCommitting() {
        return commitActive;
    }

    boolean gitCommit() {
        boolean didSomething = false;
        commitActive = true;
        try {
            if (git == null) {
                try {
                    git = Git.open(projectDir.toFile());
                } catch (IOException ioE) {
                    git = Git.init().setDirectory(projectDir.toFile()).call();
                    writeIgnore();
                }
            }

            DirCache index = git.add().addFilepattern(".").call();
            Status status = git.status().call();
            StringBuilder sBuilder = new StringBuilder();
            Set<String> actionMap = new HashSet<>();
            if (!status.isClean() || status.hasUncommittedChanges()) {
                Set<String> addedFiles = status.getAdded();
                for (String addedFile : addedFiles) {
                    String action = "add:" + Paths.get(addedFile).getName(0);
                    actionMap.add(action);
                }
                Set<String> changedFiles = status.getChanged();
                for (String changedFile : changedFiles) {
                    String action = "change:" + Paths.get(changedFile).getName(0);
                    actionMap.add(action);
                }
                Set<String> removedFiles = status.getRemoved();
                for (String removedFile : removedFiles) {
                    String action = "remove:" + Paths.get(removedFile).getName(0);
                    actionMap.add(action);
                    git.rm().addFilepattern(removedFile).call();
                }
                Set<String> missingFiles = status.getMissing();
                for (String missingFile : missingFiles) {
                    String action = "missing:" + Paths.get(missingFile).getName(0);
                    actionMap.add(action);
                    git.rm().addFilepattern(missingFile).call();
                }
                actionMap.stream().forEach(action -> sBuilder.append(action).append(","));
                RevCommit commit = git.commit().setMessage(sBuilder.toString()).call();
                didSomething = true;
            }
        } catch (GitAPIException ex) {
            log.error(ex.getMessage(), ex);
        } finally {
            // fixme, should we do this after each commit, or leave git open
            git.close();
            git = null;
            commitActive = false;
        }
        return didSomething;
    }

    @Override
    public void addPeakList(PeakList peakList, String name) {
        super.addPeakList(peakList, name);
        PeakListUpdater updater = new PeakListUpdater(peakList);
        peakList.registerUpdater(updater);
    }

    public void removePeakList(String name) {
        PeakList peakList = peakLists.get(name);
        if (peakList != null) {
            peakList.removeUpdater();
        }
        super.removePeakList(name);

    }

    void loadWindows(Path dir) throws IOException {
        WindowIO.loadWindows(dir);
    }

    void saveWindows(Path dir) throws IOException {
        WindowIO.saveWindows(dir);
    }

    public void addPeakListListener(Object mapChangeListener) {
        ObservableMap obsMap = (ObservableMap) peakLists;
        obsMap.addListener((MapChangeListener<String, PeakList>) mapChangeListener);
    }

    public void addDatasetListListener(Object mapChangeListener) {
        ObservableMap obsMap = (ObservableMap) datasetMap;
        obsMap.addListener((MapChangeListener<String, Dataset>) mapChangeListener);
    }

    public ObservableMap<String, Dataset> getObservableDatasetMap() {
        ObservableMap obsMap = (ObservableMap) datasetMap;
        return obsMap;
    }

    public void checkSubDirs(Path projectDir) throws IOException {
        for (String subDir : SUB_DIR_TYPES) {
            Path subDirectory = Path.of(projectDir.toString(), subDir);
            if (!subDirectory.toFile().exists()) {
                Files.createDirectory(subDirectory);
            }
        }
        setProjectDir(projectDir);
    }

}
