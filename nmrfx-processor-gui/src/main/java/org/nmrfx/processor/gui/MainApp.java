/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui;

import de.jangassen.MenuToolkit;
import de.jangassen.dialogs.about.AboutStageBuilder;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.console.ConsoleController;
import org.nmrfx.peaks.Multiplet;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.io.PeakReader;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.annotations.AnnoText;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.log.Log;
import org.nmrfx.processor.gui.log.LogConsoleController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.utilities.WebConnect;
import org.nmrfx.project.ProjectBase;
import org.python.util.InteractiveInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    public static ArrayList<Stage> stages = new ArrayList<>();
    public static PreferencesController preferencesController;
    public static DocWindowController docWindowController;
    public static DatasetsController datasetController;
    public static HostServices hostServices;
    private static String version = null;
    static String appName = "NMRFx Processor";
    public static InteractiveInterpreter interpreter = new InteractiveInterpreter();
    private static MenuBar mainMenuBar = null;
    protected static MainApp mainApp = null;
    static boolean isAnalyst = false;
    static Font defaultFont;
    // Icon and font sizes for icon buttons
    public static final String ICON_SIZE_STR = "16px";
    public static final String ICON_FONT_SIZE_STR = "7pt";
    // The default font size
    public static final String REG_FONT_SIZE_STR = "9pt";

    public static void closeAll() {
        for (PolyChart chart : PolyChart.CHARTS) {
            chart.clearDataAndPeaks();
            chart.clearAnnotations();
        }
        Stage mainStage = getMainStage();
        for (Stage stage : stages) {
            if (stage != mainStage) {
                stage.close();
            }
        }
    }

    public static void setAnalyst() {
        isAnalyst = true;
    }

    public static boolean isAnalyst() {
        return isAnalyst;
    }

    public static MainApp getMainApp() {
        return mainApp;
    }

    public static void removeStage(Stage stage) {
        synchronized (stages) {
            stages.remove(stage);
            if (stages.isEmpty()) {
                if (!isMac()) {
                    Platform.exit();
                    System.exit(0);
                }
            }
        }
    }

    public static void registerStage(Stage stage, FXMLController controller) {
        if (!stages.contains(stage)) {
            stages.add(stage);
        }
        stage.setOnCloseRequest(e -> {
            controller.close();
            removeStage(stage);
        });

    }

    public static Stage getMainStage() {
        if (stages.isEmpty()) {
            return null;
        } else {
            return stages.get(0);
        }
    }

    public static List<Stage> getStages() {
        return stages;
    }

    static void loadFont() {
        InputStream iStream = MainApp.class.getResourceAsStream("/LiberationSans-Regular.ttf");
        defaultFont = Font.loadFont(iStream, 12);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Log.setupMemoryAppender();

        //necessary to avoid "," as a decimal separator in output files or python scripts
        Locale.setDefault(Locale.Category.FORMAT, Locale.US);

        mainApp = this;
        FXMLController controller = FXMLController.create(stage);
        Platform.setImplicitExit(true);
        hostServices = getHostServices();
        stage.setTitle(appName + " " + getVersion());

        if (mainMenuBar == null) {
            mainMenuBar = makeMenuBar(appName);
        }
        Parameters parameters = getParameters();
        System.out.println(parameters.getRaw());

        interpreter.exec("from pyproc import *\ninitLocal()\nfrom gscript import *\nnw=NMRFxWindowScripting()\nfrom dscript import *\nfrom pscript import *\nimport os");
        interpreter.set("argv", parameters.getRaw());
        interpreter.exec("parseArgs(argv)");
        ConsoleController.create(interpreter, "NMRFx Console");
        ProjectBase.setPCS(new PropertyChangeSupport(this));
        // Dataset.addObserver(this);
        if (defaultFont == null) {
            loadFont();
        }
    }

    public static boolean isMac() {
        return SystemUtils.IS_OS_MAC;
    }

    /**
     * Set the default font size of the provided stage with the provided
     * font size string.
     * @param stage The stage to set the font for
     * @param fontSizeStr A string font size ex. '9pt'
     */
    public static void setStageFontSize(Stage stage, String fontSizeStr) {
        if (stage != null && stage.getScene() != null) {
            stage.getScene().getRoot().setStyle("-fx-font-size: " + fontSizeStr);
        } else {
            log.info("Unable to set font size for stage.");
        }
    }

    public static MenuBar getMenuBar() {
        return mainApp.makeMenuBar(appName);
    }

    public static MenuBar getMainMenuBar() {
        return mainMenuBar;
    }

    public void addStatusBarTools(SpectrumStatusBar statusBar) {
    }

    public void quit() {
        waitForCommit();
        Platform.exit();
        System.exit(0);
    }

    @Override
    public void stop() {
        waitForCommit();
    }

    public void waitForCommit() {
        int nTries = 30;
        int iTry = 0;
        while (GUIProject.isCommitting() && (iTry < nTries)) {
            System.out.println("committing");
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                break;
            }
            iTry++;
        }

    }

    Stage makeAbout(String appName) {
        AboutStageBuilder aboutStageBuilder = AboutStageBuilder.start("About " + appName)
                .withAppName(appName).withCloseOnFocusLoss()
                .withVersionString("Version " + getVersion()).withCopyright("Copyright \u00A9 " + Calendar
                .getInstance().get(Calendar.YEAR));
        Image image = new Image(MainApp.class.getResourceAsStream("/images/Icon_NVFX_256.png"));
        aboutStageBuilder = aboutStageBuilder.withImage(image);
        return aboutStageBuilder.build();
    }

    public MenuBar makeMenuBar(String appName) {
        MenuToolkit tk = null;
        if (isMac()) {
            tk = MenuToolkit.toolkit();
        }
        MenuBar menuBar = new MenuBar();

        // Application Menu
        // TBD: services menu
        Menu appMenu = new Menu(appName); // Name for appMenu can't be set at
        // Runtime
        MenuItem aboutItem = null;
        Stage aboutStage = makeAbout(appName);
        if (tk != null) {
            aboutItem = tk.createAboutMenuItem(appName, aboutStage);
        } else {
            aboutItem = new MenuItem("About...");
            aboutItem.setOnAction(e -> aboutStage.show());
        }
        MenuItem prefsItem = new MenuItem("Preferences...");
        MenuItem quitItem;
        prefsItem.setOnAction(e -> showPreferences(e));
        if (tk != null) {
            quitItem = tk.createQuitMenuItem(appName);
            appMenu.getItems().addAll(aboutItem, new SeparatorMenuItem(), prefsItem, new SeparatorMenuItem(),
                    tk.createHideMenuItem(appName), tk.createHideOthersMenuItem(), tk.createUnhideAllMenuItem(),
                    new SeparatorMenuItem(), quitItem);
            // createQuitMeneItem doesn't result in stop or quit being called
            //  therefore we can't check for waiting till a commit is done before leaving
            // so explicitly set action to quit
            quitItem.setOnAction(e -> quit());
        } else {
            quitItem = new MenuItem("Quit");
            quitItem.setOnAction(e -> quit());
        }
        // File Menu (items TBD)
        Menu fileMenu = new Menu("File");
        MenuItem openMenuItem = new MenuItem("Open FID...");
        openMenuItem.setOnAction(e -> FXMLController.getActiveController().openFIDAction(e));
        MenuItem openDatasetMenuItem = new MenuItem("Open Dataset...");
        openDatasetMenuItem.setOnAction(e -> FXMLController.getActiveController().openDatasetAction(e));
        MenuItem addMenuItem = new MenuItem("Open Dataset (No Display) ...");
        addMenuItem.setOnAction(e -> FXMLController.getActiveController().addNoDrawAction(e));
        MenuItem newMenuItem = new MenuItem("New Window...");
        newMenuItem.setOnAction(e -> newGraphics(e));
        Menu recentFIDMenuItem = new Menu("Recent FIDs");
        Menu recentDatasetMenuItem = new Menu("Recent Datasets");
        PreferencesController.setupRecentMenus(recentFIDMenuItem, recentDatasetMenuItem);

        MenuItem pdfMenuItem = new MenuItem("Export PDF...");
        pdfMenuItem.setOnAction(e -> FXMLController.getActiveController().exportPDFAction(e));
        pdfMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        
        MenuItem svgMenuItem = new MenuItem("Export SVG...");
        svgMenuItem.setOnAction(e -> FXMLController.getActiveController().exportSVGAction(e));
        svgMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        
        MenuItem pngMenuItem = new MenuItem("Export PNG...");
        pngMenuItem.setOnAction(e -> FXMLController.getActiveController().exportPNG(e));
        pngMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        
        MenuItem loadPeakListMenuItem = new MenuItem("Load PeakLists");
        loadPeakListMenuItem.setOnAction(e -> loadPeakLists());

        Menu projectMenu = new Menu("Projects");

        MenuItem projectOpenMenuItem = new MenuItem("Open...");
        projectOpenMenuItem.setOnAction(e -> loadProject());

        MenuItem projectSaveAsMenuItem = new MenuItem("Save As...");
        projectSaveAsMenuItem.setOnAction(e -> saveProjectAs());

        MenuItem projectSaveMenuItem = new MenuItem("Save");
        projectSaveMenuItem.setOnAction(e -> saveProject());
        Menu recentProjectMenuItem = new Menu("Open Recent");

        List<Path> recentProjects = PreferencesController.getRecentProjects();
        for (Path path : recentProjects) {
            int count = path.getNameCount();
            int first = count - 3;
            first = first >= 0 ? first : 0;
            Path subPath = path.subpath(first, count);

            MenuItem projectMenuItem = new MenuItem(subPath.toString());
            projectMenuItem.setOnAction(e -> loadProject(path));
            recentProjectMenuItem.getItems().add(projectMenuItem);
        }

        projectMenu.getItems().addAll(projectOpenMenuItem, recentProjectMenuItem, projectSaveMenuItem, projectSaveAsMenuItem);

        fileMenu.getItems().addAll(openMenuItem, openDatasetMenuItem, addMenuItem,
                recentFIDMenuItem, recentDatasetMenuItem, newMenuItem, new SeparatorMenuItem(), pdfMenuItem, svgMenuItem, pngMenuItem, loadPeakListMenuItem);

        Menu spectraMenu = new Menu("Spectra");
        MenuItem copyItem = new MenuItem("Copy Spectrum as SVG");
        copyItem.setOnAction(e -> FXMLController.getActiveController().copySVGAction(e));
        MenuItem deleteItem = new MenuItem("Delete Spectrum");
        deleteItem.setOnAction(e -> FXMLController.getActiveController().getActiveChart().close());
        MenuItem syncMenuItem = new MenuItem("Sync Axes");
        syncMenuItem.setOnAction(e -> PolyChart.activeChart.get().syncSceneMates());

        Menu arrangeMenu = new Menu("Arrange");
        MenuItem horizItem = new MenuItem("Horizontal");
        horizItem.setOnAction(e -> FXMLController.getActiveController().arrange(GridPaneCanvas.ORIENTATION.HORIZONTAL));
        MenuItem vertItem = new MenuItem("Vertical");
        vertItem.setOnAction(e -> FXMLController.getActiveController().arrange(GridPaneCanvas.ORIENTATION.VERTICAL));
        MenuItem gridItem = new MenuItem("Grid");
        gridItem.setOnAction(e -> FXMLController.getActiveController().arrange(GridPaneCanvas.ORIENTATION.GRID));
        MenuItem overlayItem = new MenuItem("Overlay");
        overlayItem.setOnAction(e -> FXMLController.getActiveController().overlay());
        MenuItem minimizeItem = new MenuItem("Minimize Borders");
        minimizeItem.setOnAction(e -> FXMLController.getActiveController().setBorderState(true));
        MenuItem normalizeItem = new MenuItem("Normal Borders");
        normalizeItem.setOnAction(e -> FXMLController.getActiveController().setBorderState(false));

        arrangeMenu.getItems().addAll(horizItem, vertItem, gridItem, overlayItem, minimizeItem, normalizeItem);
        MenuItem alignMenuItem = new MenuItem("Align Spectra");
        alignMenuItem.setOnAction(e -> FXMLController.getActiveController().alignCenters());

        spectraMenu.getItems().addAll(deleteItem, arrangeMenu, syncMenuItem,
                alignMenuItem, copyItem);

        // Format (items TBD)
//        Menu formatMenu = new Menu("Format");
//        formatMenu.getItems().addAll(new MenuItem("TBD"));
        // View Menu (items TBD)
        Menu viewMenu = new Menu("View");
        MenuItem dataMenuItem = new MenuItem("Show Datasets");
        dataMenuItem.setOnAction(e -> showDatasetsTable(e));

        MenuItem consoleMenuItem = new MenuItem("Show Console");
        consoleMenuItem.setOnAction(e -> showConsole(e));

        MenuItem attrMenuItem = new MenuItem("Show Attributes");
        attrMenuItem.setOnAction(e -> FXMLController.getActiveController().showSpecAttrAction(e));

        MenuItem procMenuItem = new MenuItem("Show Processor");
        procMenuItem.setOnAction(e -> FXMLController.getActiveController().showProcessorAction(e));

        viewMenu.getItems().addAll(consoleMenuItem, dataMenuItem, attrMenuItem, procMenuItem);

        Menu peakMenu = new Menu("Peaks");

        MenuItem peakAttrMenuItem = new MenuItem("Show Peak Tool");
        peakAttrMenuItem.setOnAction(e -> FXMLController.getActiveController().showPeakAttrAction(e));

        MenuItem linkPeakDimsMenuItem = new MenuItem("Link by Labels");
        linkPeakDimsMenuItem.setOnAction(e -> FXMLController.getActiveController().linkPeakDims());

        peakMenu.getItems().addAll(peakAttrMenuItem, linkPeakDimsMenuItem);

        // Window Menu
        // TBD standard window menu items
        // Help Menu (items TBD)
        Menu helpMenu = new Menu("Help");

        MenuItem webSiteMenuItem = new MenuItem("NMRFx Web Site");
        webSiteMenuItem.setOnAction(e -> showWebSiteAction(e));

        MenuItem docsMenuItem = new MenuItem("Online Documentation");
        docsMenuItem.setOnAction(e -> showDocAction(e));

        MenuItem versionMenuItem = new MenuItem("Check Version");
        versionMenuItem.setOnAction(e -> showVersionAction(e));

        MenuItem mailingListItem = new MenuItem("Mailing List Site");
        mailingListItem.setOnAction(e -> showMailingListAction(e));

        MenuItem refMenuItem = new MenuItem("NMRFx Publication");
        refMenuItem.setOnAction(e -> {
            MainApp.hostServices.showDocument("http://link.springer.com/article/10.1007/s10858-016-0049-6");
        });

        // home
        // mailing list
        //
        helpMenu.getItems().addAll(docsMenuItem, webSiteMenuItem, mailingListItem, versionMenuItem, refMenuItem);

        if (tk != null) {
            Menu windowMenu = new Menu("Window");
            windowMenu.getItems().addAll(tk.createMinimizeMenuItem(), tk.createZoomMenuItem(), tk.createCycleWindowsItem(),
                    new SeparatorMenuItem(), tk.createBringAllToFrontItem());
            menuBar.getMenus().addAll(appMenu, fileMenu, projectMenu, spectraMenu, viewMenu, peakMenu, windowMenu, helpMenu);
            tk.autoAddWindowMenuItems(windowMenu);
            tk.setGlobalMenuBar(menuBar);
        } else {
            fileMenu.getItems().add(prefsItem);
            fileMenu.getItems().add(quitItem);
            menuBar.getMenus().addAll(fileMenu, projectMenu, spectraMenu, viewMenu, peakMenu, helpMenu);
            helpMenu.getItems().add(0, aboutItem);
        }
        return menuBar;
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    public static String getVersion() {
        if (version == null) {
            String cp = System.getProperty("java.class.path");
            // processorgui-10.1.2.jar
            String jarPattern = ".*processorgui-([0-9\\.\\-abcr]+)\\.jar.*";
            Pattern pat = Pattern.compile(jarPattern);
            Matcher match = pat.matcher(cp);
            version = "0.0.0";
            if (match.matches()) {
                version = match.group(1);
            }
        }
        return version;
    }

    static void showDocAction(ActionEvent event) {
        hostServices.showDocument("http://docs.nmrfx.org");
    }

    static void showWebSiteAction(ActionEvent event) {
        hostServices.showDocument("http://nmrfx.org");
    }

    static void showMailingListAction(ActionEvent event) {
        hostServices.showDocument("https://groups.google.com/forum/#!forum/nmrfx-processor");
    }

    public void showVersionAction(ActionEvent event) {
        String onlineVersion = WebConnect.getVersion();
        onlineVersion = onlineVersion.replace('_', '.');
        String currentVersion = getVersion();
        String text;
        if (onlineVersion.equals("")) {
            text = "Sorry, couldn't reach web site";
        } else if (onlineVersion.equals(currentVersion)) {
            text = "You're running the latest version: " + currentVersion;
        } else {
            text = "You're running " + currentVersion;
            text += "\nbut the latest is: " + onlineVersion;
        }
        Alert alert = new Alert(AlertType.INFORMATION, text);
        alert.setTitle("NMRFx Processor Version");
        alert.showAndWait();
    }

    private void showConsole(ActionEvent event) {
        ConsoleController.getConsoleController().show();
    }

    @FXML
    private void showPreferences(ActionEvent event) {
        if (preferencesController == null) {
            preferencesController = PreferencesController.create(stages.get(0));
        }
        if (preferencesController != null) {
            preferencesController.getStage().show();
        } else {
            System.out.println("Coudn't make controller");
        }
    }

    private void newGraphics(ActionEvent event) {
        FXMLController controller = FXMLController.create();
    }

    @FXML
    void showDatasetsTable(ActionEvent event) {
        if (datasetController == null) {
            datasetController = DatasetsController.create();
        }
        datasetController.refresh();
        datasetController.getStage().show();
        datasetController.getStage().toFront();
    }

    void loadPeakLists() {
        PeakReader peakReader = new PeakReader();
        Dataset.datasets().stream().forEach(dataset -> {
            String canonFileName = dataset.getCanonicalFile();
            File canonFile = new File(canonFileName);
            if (canonFile.exists()) {
                int dotIndex = canonFileName.lastIndexOf(".");
                if (dotIndex != -1) {
                    String listFileName = canonFileName.substring(0, dotIndex) + ".xpk2";
                    File listFile = new File(listFileName);
                    String listName = listFile.getName();
                    dotIndex = listName.lastIndexOf('.');
                    listName = listName.substring(0, dotIndex);
                    if (PeakList.get(listName) == null) {
                        try {
                            peakReader.readXPK2Peaks(listFileName);
                        } catch (IOException ioE) {
                            ExceptionDialog dialog = new ExceptionDialog(ioE);
                            dialog.showAndWait();
                        }
                    }
                }
            }
        });
    }

    public static InteractiveInterpreter getInterpreter() {
        return interpreter;
    }

    public static ConsoleController getConsoleController() {
        return ConsoleController.getConsoleController();
    }

    public static LogConsoleController getLogConsoleController() {
        return LogConsoleController.getLogConsoleController();
    }

    public static void writeOutput(String string) {
        if (getConsoleController() == null) {
            System.out.println(string);
        } else {
            getConsoleController().write(string);
        }
    }

    public static ProjectBase getActive() {
        return GUIProject.getActive();
    }

    private void loadProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Project Chooser");
        File directoryFile = chooser.showDialog(null);
        if (directoryFile != null) {
            loadProject(directoryFile.toPath());
        }
    }

    private void loadProject(Path path) {
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

    private void saveProjectAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Project Creator");
        File directoryFile = chooser.showSaveDialog(null);
        if (directoryFile != null) {
            GUIProject activeProject = (GUIProject) getActive();
            if (activeProject != null) {
                GUIProject newProject = GUIProject.replace(appName, activeProject);

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

    private void saveProject() {
        GUIProject project = (GUIProject) getActive();
        if (project.hasDirectory()) {
            try {
                project.saveProject();
            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    public void hidePopover(boolean always) {

    }

    public void showPopover(PolyChart chart, Bounds objectBounds, Object hitObject) {

    }
}
