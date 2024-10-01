/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.project;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.util.Duration;
import org.eclipse.jgit.util.FS;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.git.GitManager;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.processor.gui.utils.PeakListUpdater;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.project.StructureProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;

/**
 * @author Bruce Johnson
 */
public class GUIProject extends StructureProject {
    private static final Logger log = LoggerFactory.getLogger(GUIProject.class);
    private static final String[] SUB_DIR_TYPES = {"star", "datasets", "molecules", "peaks", "shifts", "refshifts", "windows"};
    GitManager gitManager;

    private final SimpleBooleanProperty projectChanged = new SimpleBooleanProperty(false);


    private static double projectSaveInterval;

    private static Timeline timeline = null;

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

    public GitManager getGitManager() {
        return gitManager;
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
        PreferencesController.saveRecentProjects(projectDir.toString());
        checkUserHomePath();
        gitManager = new GitManager(this);
        setActive();
    }

    private static void startTimer() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        KeyFrame save = new KeyFrame(
                Duration.minutes(projectSaveInterval),
                event -> saveCheck()
        );
        timeline = new Timeline(save);
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private static void saveCheck() {
        GUIProject activeProject = getActive();
        if (activeProject.projectChanged() && activeProject.hasDirectory()) {
            try {
                activeProject.saveProject();
            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    public static void projectSaveInterval(double interval) {
        projectSaveInterval = interval;
        projectSave(PreferencesController.getProjectSave());
    }

    public static void setupSave() {
        projectSave(PreferencesController.getProjectSave());
    }

    public static void projectSave(boolean projectSave) {
        projectSaveInterval = PreferencesController.getProjectSaveInterval();
        if (projectSave) {
            startTimer();
        } else if (timeline != null) {
            timeline.stop();
            timeline = null;
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

    public static GUIProject getActive() {
        ProjectBase project = ProjectBase.getActive();
        if (project == null) {
            project = new GUIProject("Untitled 1");
        }
        return (GUIProject) project;
    }

    public void writeIgnore() {
        Path path = Paths.get(projectDir.toString(), ".gitignore");
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write("*.nv\n*.ucsf\njffi*\n");
        } catch (IOException ioE) {
            log.warn("{}", ioE.getMessage(), ioE);
        }

    }

    public void close() {
        if (timeline != null) {
            timeline.stop();
        }
        clearAllMolecules();
        clearAllPeakLists();
        clearAllDatasets();
        AnalystApp.closeAll();
        // Clear the project directory or else a user may accidentally overwrite their previously closed project
        setProjectDir(null);
        if (gitManager != null) {
            gitManager.close();
        }
        clearActive();
    }

    public static boolean checkProjectActive(boolean includeDatasets) {
        ProjectBase project = ProjectBase.getActive();
        boolean hasMolecules = !MoleculeFactory.getMolecules().isEmpty();
        boolean hasDatasets = project != null && !project.getDatasets().isEmpty();
        boolean hasPeakLists = project != null && !project.getPeakLists().isEmpty();
        return hasMolecules || (hasDatasets && includeDatasets) || hasPeakLists;
    }

    public void loadGUIProject(Path projectDir) throws IOException, MoleculeIOException, IllegalStateException {
        setActive();

        loadProject(projectDir, "datasets");
        gitManager = new GitManager(this);

        if (projectDir != null) {
            loadStructureSubDirs(projectDir);
            FileSystem fileSystem = FileSystems.getDefault();
            Path subDirectory = fileSystem.getPath(projectDir.toString(), "windows");
            loadWindows(subDirectory);
            PreferencesController.saveRecentProjects(projectDir.toString());

            setProjectDir(projectDir);
            gitManager.setProject(this);
            gitManager.gitOpen();
            PreferencesController.saveRecentProjects(projectDir.toString());
            projectChanged(false);
            setupSave();
        }
    }

    @Override
    public void saveProject() throws IOException {
        ProjectBase currentProject = getActive();
        setActive();
        checkSubDirs(projectDir);
        super.saveProject();

        if (currentProject == this) {
            saveWindows(projectDir);
        }

        gitManager.gitCommitOnThread();
        PreferencesController.saveRecentProjects(projectDir.toString());
        currentProject.setActive();
    }

    @Override
    public void projectChanged(boolean state) {
        projectChanged.set(state);
    }

    @Override
    public boolean projectChanged() {
        return projectChanged.get();
    }

    @Override
    public void addPeakList(PeakList peakList, String name) {
        super.addPeakList(peakList, name);
        PeakListUpdater updater = new PeakListUpdater(peakList);
        peakList.registerUpdater(updater);
        projectChanged.set(true);
    }

    @Override
    public void removePeakList(String name) {
        PeakList peakList = peakLists.get(name);
        if (peakList != null) {
            peakList.removeUpdater();
        }
        super.removePeakList(name);
        projectChanged.set(true);

    }

    void loadWindows(Path dir) throws IOException {
        WindowIO.loadWindows(dir);
    }

    void saveWindows(Path dir) throws IOException {
        WindowIO.saveWindows(dir);
    }

    @Override
    public void addPeakListListener(Object mapChangeObject) {
        if (mapChangeObject instanceof MapChangeListener mapChangeListener) {
            ObservableMap<String, PeakList> obsMap = (ObservableMap<String, PeakList>) peakLists;
            obsMap.addListener(mapChangeListener);
        }
    }

    public void removePeakListListener(Object mapChangeObject) {
        if (mapChangeObject instanceof MapChangeListener mapChangeListener) {
            ObservableMap<String, PeakList> obsMap = (ObservableMap<String, PeakList>) peakLists;
            obsMap.removeListener(mapChangeListener);
        }
    }

    @Override
    public void addDatasetListListener(Object mapChangeListener) {
        ObservableMap<String, DatasetBase> obsMap = (ObservableMap<String, DatasetBase>) datasetMap;
        obsMap.addListener((MapChangeListener<String, DatasetBase>) mapChangeListener);
    }

    public void removeDatasetListListener(Object mapChangeObject) {
        if (mapChangeObject instanceof MapChangeListener mapChangeListener) {
            ObservableMap<String, DatasetBase> obsMap = (ObservableMap<String, DatasetBase>) datasetMap;
            obsMap.removeListener(mapChangeListener);
        }
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
