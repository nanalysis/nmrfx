package org.nmrfx.analyst.gui.ribbon;

import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.DatasetBrowserController;
import org.nmrfx.analyst.gui.peaks.PeakTableController;
import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.console.ConsoleController;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.ProcessorController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.processor.project.Project;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.ParseException;
import org.nmrfx.utils.GUIUtils;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Actions used by the ribbon that are not already accessible from elsewhere.
 */
public class RibbonActions {
    private static final Logger log = LoggerFactory.getLogger(RibbonActions.class);

    private static WindowIO windowIO;

    private DatasetBrowserController browserController;

    public void showDataBrowser() {
        if (browserController == null) {
            browserController = DatasetBrowserController.create();
        }

        Stage browserStage = browserController.getStage();
        browserStage.toFront();
        browserStage.show();
    }

    public void toggleProcessorVisibility() {
        ProcessorController controller = FXMLController.getActiveController().getProcessorController(false);
        if (controller == null) {
            // for an unknown reason, controller is hidden when created, but .isVisible() returns true.
            controller = FXMLController.getActiveController().getProcessorController(true);
            controller.show();
        } else if (controller.isVisible()) {
            controller.hide();
        } else {
            controller.show();
        }
    }

    public void toggleConsoleVisibility() {
        ConsoleController controller = ConsoleController.getConsoleController();
        if (controller.isShowing()) {
            controller.close();
        } else {
            controller.show();
        }
    }

    public void zoomOnScroll(ScrollEvent event) {
        double y = event.getDeltaY();
        double factor = y < 0 ? 1.1 : 0.9;
        FXMLController.getActiveController().getActiveChart().zoom(factor);
    }

    public void scaleOnScroll(ScrollEvent event) {
        double y = event.getDeltaY();
        double factor = y < 0 ? 0.9 : 1.1;

        List<PolyChart> charts = FXMLController.getActiveController().getCharts(event.isShiftDown());
        charts.forEach(applyChart -> applyChart.adjustScale(factor));
    }

    public void createNewWindow() {
        FXMLController controller = FXMLController.create();
        controller.getRibbon().hideTabs();
    }

    public void saveAsFavorite() {
        WindowIO.saveFavorite();
    }

    public void showFavorites() {
        if (windowIO == null) {
            windowIO = new WindowIO();
            windowIO.create();
        }

        Stage stage = windowIO.getStage();
        stage.show();
        stage.toFront();

        windowIO.updateFavorites();
        try {
            ProjectBase project = ProjectBase.getActive();
            if (project != null) {
                Path projectDir = project.getDirectory();
                if (projectDir != null) {
                    Path path = projectDir.getFileSystem().getPath(projectDir.toString(), "windows");
                    windowIO.setupWatcher(path);
                }
            }
        } catch (IOException ex) {
            log.warn("Unable to get favorite windows", ex);
        }
    }

    public void showStripsBar() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(StripController.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            StripController stripsController = new StripController(controller, this::removeStripsBar);
            stripsController.initialize(vBox);
            controller.addTool(stripsController);
        }
    }

    public void removeStripsBar(StripController stripsController) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(StripController.class);
        controller.getBottomBox().getChildren().remove(stripsController.getBox());
    }

    public void showAboutDialog() {
        AnalystApp.createAboutStage().show();
    }

    public void loadProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Project Chooser");
        File directoryFile = chooser.showDialog(null);
        if (directoryFile != null) {
            loadProjectFromPath(directoryFile.toPath());
        }
    }

    public void loadProjectFromPath(Path path) {
        if (path != null) {
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

    public void saveProjectAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Project Creator");
        File directoryFile = chooser.showSaveDialog(null);
        if (directoryFile != null) {
            GUIProject activeProject = (GUIProject) AnalystApp.getActive();
            if (activeProject != null) {
                GUIProject newProject = GUIProject.replace(AnalystApp.getAppName(), activeProject);

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

    public void saveProject() {
        GUIProject project = (GUIProject) AnalystApp.getActive();
        if (project.hasDirectory()) {
            try {
                project.saveProject();
            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    public void closeProject() {
        if (GUIUtils.affirm("Close all project information")) {
            ((GUIProject) AnalystApp.getActive()).close();
        }
    }

    public void readSTAR() {
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

    public void writeSTAR() {
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
                java.util.logging.Logger.getLogger(AnalystApp.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void readSparkyProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read Sparky Project");
        File sparkyFile = chooser.showOpenDialog(null);
        Map<String, Object> pMap = null;
        if (sparkyFile != null) {
            try (PythonInterpreter interpreter = new PythonInterpreter()) {
                interpreter.exec("import sparky");
                String rdString;
                interpreter.set("pMap", pMap);
                interpreter.exec("sparky.pMap=pMap");
                rdString = String.format("sparky.loadProjectFile('%s')", sparkyFile);
                interpreter.exec(rdString);
            }
        }
    }
}
