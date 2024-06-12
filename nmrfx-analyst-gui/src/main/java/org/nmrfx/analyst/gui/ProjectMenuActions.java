package org.nmrfx.analyst.gui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.BMRB.BMRBDepositionController;
import org.nmrfx.analyst.gui.BMRB.BMRBSearchController;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.NMRNEFReader;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.star.ParseException;
import org.nmrfx.utils.GUIUtils;
import org.python.util.PythonInterpreter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProjectMenuActions extends MenuActions {
    public ProjectMenuActions(AnalystApp app, Menu menu) {
        super(app, menu);
    }
    BMRBSearchController searchController;

    @Override
    public void basic() {
        MenuItem projectOpenMenuItem = new MenuItem("Open...");
        projectOpenMenuItem.setOnAction(this::loadProject);

        MenuItem projectSaveAsMenuItem = new MenuItem("Save As...");
        projectSaveAsMenuItem.setOnAction(this::saveProjectAs);

        MenuItem projectSaveMenuItem = new MenuItem("Save");
        projectSaveMenuItem.setOnAction(this::saveProject);
        Menu recentProjectMenuItem = new Menu("Open Recent");

        MenuItem closeProjectMenuItem = new MenuItem("Close");
        closeProjectMenuItem.setOnAction(this::closeProject);

        MenuItem openSTARMenuItem = new MenuItem("Open STAR3...");
        openSTARMenuItem.setOnAction(this::readSTAR);

        MenuItem saveSTARMenuItem = new MenuItem("Save STAR3...");
        saveSTARMenuItem.setOnAction(this::writeSTAR);

        MenuItem showHistoryAction = new MenuItem("GIT Manager...");
        showHistoryAction.setOnAction(this::showHistory);

        MenuItem openNEFMenuItem = new MenuItem("Open NEF...");
        openNEFMenuItem.setOnAction(this::readNEF);

        MenuItem fetchSTARMenuItem = new MenuItem("Fetch STAR3...");
        fetchSTARMenuItem.setOnAction(this::fetchSTAR);

        MenuItem depositSTARMenuItem = new MenuItem("Deposit STAR3...");
        depositSTARMenuItem.setOnAction(this::depositSTAR);

        MenuItem searchBMRBMenuItem = new MenuItem("Search BMRB");
        searchBMRBMenuItem.setOnAction(this::searchBMRB);

        Menu STARMenu = new Menu("STAR/NEF/BMRB");
        STARMenu.getItems().addAll(openSTARMenuItem, openNEFMenuItem, saveSTARMenuItem, fetchSTARMenuItem,
                depositSTARMenuItem, searchBMRBMenuItem);

        List<Path> recentProjects = PreferencesController.getRecentProjects();
        for (Path path : recentProjects) {
            int count = path.getNameCount();
            int first = count - 3;
            first = Math.max(first, 0);
            Path subPath = path.subpath(first, count);

            MenuItem projectMenuItem = new MenuItem(subPath.toString());
            projectMenuItem.setOnAction(e -> loadProjectFromPath(path));
            recentProjectMenuItem.getItems().add(projectMenuItem);
        }

        menu.getItems().addAll(projectOpenMenuItem, recentProjectMenuItem,
                projectSaveMenuItem, projectSaveAsMenuItem, closeProjectMenuItem, showHistoryAction, STARMenu);
    }

    @Override
    protected void advanced() {
        MenuItem openSparkyMenuItem = new MenuItem("Open Sparky Project...");
        openSparkyMenuItem.setOnAction(e -> readSparkyProject());
        menu.getItems().addAll(openSparkyMenuItem);
    }

    private void loadProject(ActionEvent event) {
        if (GUIProject.checkProjectActive(true)) {
            GUIUtils.warn("Open Project", "Project content already present.  Close existing first");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Project Chooser");
        File directoryFile = chooser.showDialog(null);
        if (directoryFile != null) {
            loadProjectFromPath(directoryFile.toPath());
        }
    }

    private void loadProjectFromPath(Path path) {
        if (path != null) {
            if (GUIProject.checkProjectActive(true)) {
                GUIUtils.warn("Open Project", "Project content already present.  Close existing first");
                return;
            }
            String projectName = path.getFileName().toString();
            GUIProject project = new GUIProject(projectName);
            try {
                project.loadGUIProject(path);
            } catch (IOException | MoleculeIOException | IllegalStateException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }

    }

    private void saveProjectAs(ActionEvent event) {
        saveProjectAs();
    }

    private void saveProjectAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Project Creator");
        File directoryFile = chooser.showSaveDialog(null);
        if (directoryFile != null) {
            GUIProject activeProject = (GUIProject) AnalystApp.getActive();
            if (activeProject != null) {
                GUIProject newProject = GUIProject.replace(directoryFile.getName(), activeProject);
                try {
                    newProject.createProject(directoryFile.toPath());
                    newProject.saveProject();
                } catch (IOException ex) {
                    ExceptionDialog dialog = new ExceptionDialog(ex);
                    dialog.showAndWait();
                }
            }
        }
    }

    private void saveProject(ActionEvent event) {
        GUIProject project = (GUIProject) AnalystApp.getActive();
        if (project.hasDirectory()) {
            try {
                project.saveProject();
            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        } else {
            // If the project hasn't been saved to a directory yet, call the save as prompt instead.
            saveProjectAs();
        }
    }

    void closeProject(ActionEvent event) {
        if (GUIUtils.affirm("Close all project information")) {
            ((GUIProject) AnalystApp.getActive()).close();
        }
    }

    @FXML
    void readSTAR(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read STAR3 File");
        File starFile = chooser.showOpenDialog(null);
        if (starFile != null) {
            try {
                NMRStarReader.read(starFile);
            } catch (ParseException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    @FXML
    void fetchSTAR(ActionEvent event) {
        BMRBSearchController.fetchStar(0);
    }
    @FXML
    void depositSTAR(ActionEvent event) {
        BMRBDepositionController.create();
    }

    void searchBMRB(ActionEvent event) {
        if (searchController == null) {
            searchController = BMRBSearchController.create();
        }
        searchController.getStage().show();
        searchController.getStage().toFront();
    }

    void writeSTAR(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Write STAR3 File");
        File starFile = chooser.showSaveDialog(null);
        if (starFile != null) {
            try {
                NMRStarWriter.writeAll(starFile);
            } catch (IOException | InvalidPeakException | InvalidMoleculeException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            } catch (org.nmrfx.star.ParseException ex) {
                Logger.getLogger(AnalystApp.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    void readNEF(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read NEF File");
        File nefFile = chooser.showOpenDialog(null);
        if (nefFile != null) {
            try {
                NMRNEFReader.read(nefFile);
            } catch (ParseException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }


    void readSparkyProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read Sparky Project");
        File sparkyFile = chooser.showOpenDialog(null);
        Map<String, Object> pMap = null;
        if (sparkyFile != null) {
            try (PythonInterpreter interpreter = new PythonInterpreter()) {
                interpreter.exec("import sparky");
                interpreter.set("pMap", pMap);
                interpreter.exec("sparky.pMap=pMap");
                interpreter.set("sparkyFile", sparkyFile.toString());
                interpreter.exec("sparky.loadProjectFile(sparkyFile)");
            }
        }
    }

    void showHistory(ActionEvent event) {
        AnalystApp.getAnalystApp().showHistoryAction(event);
    }
}
