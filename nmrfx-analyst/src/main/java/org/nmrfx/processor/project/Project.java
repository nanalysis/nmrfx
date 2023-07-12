/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.project;

import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.project.ProjectBase;

import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
public class Project extends ProjectBase {
    public ResonanceFactory resFactory;

    public Project(String name) {
        super(name);
        this.datasetMap = new HashMap<>();
        this.resFactory = getNewResFactory();
        this.resFactory.init();
        peakLists = new HashMap<>();

        setActive();
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
}
