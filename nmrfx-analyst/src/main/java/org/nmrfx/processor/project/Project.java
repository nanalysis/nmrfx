/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.project;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.project.ProjectBase;

/**
 *
 * @author Bruce Johnson
 */
public class Project extends ProjectBase {

    static String[] SUB_DIR_TYPES = {"star", "datasets", "molecules", "peaks", "shifts", "refshifts", "windows"};
    static final Map<String, Project> projects = new HashMap<>();
    static Project activeProject = null;
    public ResonanceFactory resFactory;

    public Project(String name) {
        super(name);
        this.datasetMap = new HashMap<>();
        this.resFactory = getNewResFactory();
        this.resFactory.init();
        peakLists = new HashMap<>();

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
        setProjectDir(projectDir);
    }


}
