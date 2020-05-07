/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.project;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.AnalystApp;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.structure.chemistry.io.MoleculeIOException;

/**
 *
 * @author Bruce Johnson
 */
public class GUIStructureProject extends StructureProject {

    Git git;
    private static boolean commitActive = false;

    public GUIStructureProject(String name) {
        super(name);
        peakLists = FXCollections.observableHashMap();
    }

    public static GUIStructureProject replace(String name, StructureProject project) {
        GUIStructureProject newProject = new GUIStructureProject(name);
        newProject.projectDir = project.projectDir;
        newProject.molecules.putAll(project.molecules);
        project.molecules.clear();
        newProject.peakLists.putAll(project.peakLists);
        project.peakLists.clear();
        newProject.datasetMap = project.datasetMap;
        newProject.resFactory = project.resFactory;
        newProject.peakPaths = project.peakPaths;
        newProject.compoundMap.putAll(project.compoundMap);
        newProject.activeMol = project.activeMol;
        newProject.NOE_SETS.putAll(project.NOE_SETS);
        newProject.ACTIVE_SET = project.ACTIVE_SET;
        newProject.angleSets = project.angleSets;
        newProject.activeSet = project.activeSet;
        newProject.rdcSets = project.rdcSets;
        newProject.activeRDCSet = project.activeRDCSet;
        return newProject;
    }

    public static GUIStructureProject getActive() {
        Project project = Project.getActive();
        if ((project != null) && !(project instanceof GUIStructureProject)) {
            project = replace(project.name, (StructureProject) project);
        }

        if (project == null) {
            project = new GUIStructureProject("Untitled 1");
        }
        return (GUIStructureProject) project;
    }

    public void createProject(Path projectDir) throws IOException {
        try {
            super.createProject(projectDir);
            PreferencesController.saveRecentProjects(projectDir.toString());
            git = Git.init().setDirectory(projectDir.toFile()).call();
        } catch (GitAPIException ex) {
            Logger.getLogger(GUIStructureProject.class.getName()).log(Level.SEVERE, null, ex);
        }
        writeIgnore();
    }

    public void close() {
        clearAllMolecules();
        clearAllPeakLists();
        AnalystApp.closeAll();

    }

    private void writeIgnore() {
        if (git != null) {
            Path path = Paths.get(projectDir.toString(), ".gitignore");
            try (FileWriter writer = new FileWriter(path.toFile())) {
                writer.write("*.nv\n*.ucsf");
            } catch (IOException ioE) {
                System.out.println(ioE.getMessage());
            }
        }

    }

    public void loadGUIProject(Path projectDir) throws IOException, IllegalStateException, MoleculeIOException {
        Project currentProject = getActive();
        setActive();

        loadStructureProject(projectDir);

        if (currentProject == this) {
            FileSystem fileSystem = FileSystems.getDefault();

            String[] subDirTypes = {"windows"};
            if (projectDir != null) {
                for (String subDir : subDirTypes) {
                    Path subDirectory = fileSystem.getPath(projectDir.toString(), subDir);
                    if (Files.exists(subDirectory) && Files.isDirectory(subDirectory) && Files.isReadable(subDirectory)) {
                        switch (subDir) {
                            case "windows":
                                loadWindows(subDirectory);
                                break;
                            default:
                                throw new IllegalStateException("Invalid subdir type");
                        }
                    }

                }
            }
        }
        this.projectDir = projectDir;
        PreferencesController.saveRecentProjects(projectDir.toString());
        currentProject.setActive();
    }

    @Override
    public void saveProject() throws IOException {
        Project currentProject = getActive();
        setActive();

        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory not set");
        }
        super.saveProject();
        if (currentProject == this) {
            saveWindows(projectDir);
        }
        gitCommitOnThread();
        PreferencesController.saveRecentProjects(projectDir.toString());
        currentProject.setActive();
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
        System.out.println("gitcom");
        commitActive = true;
        try {
            if (git == null) {
                try {
                    git = Git.open(projectDir.toFile());
                    System.out.println("gitopen");
                } catch (IOException ioE) {
                    System.out.println("gitinit");
                    git = Git.init().setDirectory(projectDir.toFile()).call();
                    writeIgnore();
                    System.out.println("gitinited");
                }
            }
            DirCache index = git.add().addFilepattern(".").call();
            Status status = git.status().call();
            System.out.println("status " + status.isClean() + " " + status.hasUncommittedChanges());
            StringBuilder sBuilder = new StringBuilder();
            Set<String> actionMap = new HashSet<>();
            if (!status.isClean() || status.hasUncommittedChanges()) {
                Set<String> addedFiles = status.getAdded();
                for (String addedFile : addedFiles) {
                    String action = "add:" + Paths.get(addedFile).getName(0);
                    actionMap.add(action);
                    System.out.println("added " + addedFile);
                }
                Set<String> changedFiles = status.getChanged();
                for (String changedFile : changedFiles) {
                    String action = "change:" + Paths.get(changedFile).getName(0);
                    actionMap.add(action);
                    System.out.println("changed " + changedFile);
                }
                Set<String> removedFiles = status.getRemoved();
                for (String removedFile : removedFiles) {
                    System.out.println("removed " + removedFile);
                    String action = "remove:" + Paths.get(removedFile).getName(0);
                    actionMap.add(action);
                    git.rm().addFilepattern(removedFile).call();
                }
                Set<String> missingFiles = status.getMissing();
                for (String missingFile : missingFiles) {
                    System.out.println("missing " + missingFile);
                    String action = "missing:" + Paths.get(missingFile).getName(0);
                    actionMap.add(action);
                    git.rm().addFilepattern(missingFile).call();
                }
                actionMap.stream().forEach(action -> sBuilder.append(action).append(","));
                System.out.println("commit");
                RevCommit commit = git.commit().setMessage(sBuilder.toString()).call();
                System.out.println("committed");
                didSomething = true;
            }
        } catch (GitAPIException ex) {
            Logger.getLogger(GUIStructureProject.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            System.out.println("done");
            // fixme, should we do this after each commit, or leave git open
            git.close();
            git = null;
            commitActive = false;
        }
        return didSomething;

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
}
