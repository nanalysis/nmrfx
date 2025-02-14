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
import javafx.collections.ObservableMap;
import javafx.util.Duration;
import javafx.util.Subscription;
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

import java.io.IOException;
import java.nio.file.*;

/**
 * @author Bruce Johnson
 */
public class GUIProject extends StructureProject {
    private static final Logger log = LoggerFactory.getLogger(GUIProject.class);
    GitManager gitManager;

    private final SimpleBooleanProperty projectChanged = new SimpleBooleanProperty(false);


    private static double projectSaveInterval;

    private static Timeline timeline = null;

    Subscription peakListSubscriptions = Subscription.EMPTY;
    Subscription datasetMapSubscriptions = Subscription.EMPTY;

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
        newProject.resFactory = project.resFactory;
        return newProject;
    }
@Override
    public GitManager getGitManager() {
        return gitManager;
    }

    public void createProject(Path projectDir) throws IOException {
        createProjectDirectory(projectDir);
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

    public static GUIProject getActive() {
        ProjectBase project = ProjectBase.getActive();
        if (project == null) {
            project = new GUIProject("Untitled 1");
        }
        return (GUIProject) project;
    }

    public void close() {
        if (timeline != null) {
            timeline.stop();
        }
        peakListSubscriptions.unsubscribe();
        datasetMapSubscriptions.unsubscribe();
        peakListSubscriptions = Subscription.EMPTY;
        datasetMapSubscriptions = Subscription.EMPTY;
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
        projectChanged(false);
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

    public void addPeakListSubscription(Runnable runnable) {
        ObservableMap<String, PeakList> obsMap = (ObservableMap<String, PeakList>) peakLists;
        peakListSubscriptions = peakListSubscriptions.and(obsMap.subscribe(runnable));
    }

    public void addDatasetListSubscription(Runnable runnable) {
        ObservableMap<String, DatasetBase> obsMap = (ObservableMap<String, DatasetBase>) datasetMap;
        datasetMapSubscriptions = datasetMapSubscriptions.and(obsMap.subscribe(runnable));
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
