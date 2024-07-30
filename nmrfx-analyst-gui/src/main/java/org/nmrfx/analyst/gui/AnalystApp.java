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
package org.nmrfx.analyst.gui;

import de.jangassen.MenuToolkit;
import de.jangassen.dialogs.about.AboutStageBuilder;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.nmrfx.analyst.gui.datasetbrowser.DatasetBrowserController;
import org.nmrfx.analyst.gui.events.DataFormatHandlerUtil;
import org.nmrfx.analyst.gui.git.*;
import org.nmrfx.analyst.gui.molecule.MoleculeMenuActions;
import org.nmrfx.analyst.gui.peaks.PeakAssignTool;
import org.nmrfx.analyst.gui.peaks.PeakMenuActions;
import org.nmrfx.analyst.gui.plugin.PluginLoader;
import org.nmrfx.analyst.gui.python.AnalystPythonInterpreter;
import org.nmrfx.analyst.gui.spectra.SpectrumMenuActions;
import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.analyst.gui.tools.*;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.console.ConsoleController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakLabeller;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.PluginFunction;
import org.nmrfx.processor.datasets.peaks.PeakFitParameters;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.log.Log;
import org.nmrfx.processor.gui.log.LogConsoleController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.processor.gui.utils.FxPropertyChangeSupport;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.seqassign.RunAboutSaveFrameProcessor;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public class AnalystApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(AnalystApp.class);
    // Icon and font sizes for icon buttons
    public static final String ICON_SIZE_STR = "16px";
    public static final String ICON_FONT_SIZE_STR = "7pt";
    // The default font size
    public static final String REG_FONT_SIZE_STR = "9pt";
    private static final String APP_NAME = "NMRFx Analyst";

    private static AnalystApp analystApp;
    private static final FXMLControllerManager fxmlControllerManager = new FXMLControllerManager();
    private static final List<Stage> stages = new ArrayList<>();

    private final PopOverTools popoverTool = new PopOverTools();
    private DatasetBrowserController datasetBrowserController;
    private PreferencesController preferencesController;
    private MenuBar mainMenuBar = null;
    private FileMenuActions fileMenuActions;
    private MoleculeMenuActions molMenuActions;
    private PeakMenuActions peakMenuActions;
    private SpectrumMenuActions spectrumMenuActions;
    private ProjectMenuActions projectMenuActions;
    private ViewMenuItems viewMenuActions;
    private boolean startInAdvanced = true;
    private boolean advancedIsActive = false;
    private ObservableMap<String, MoleculeBase> moleculeMap;

    @Override
    public void start(Stage stage) throws Exception {
        Log.setupMemoryAppender();

        //necessary to avoid "," as a decimal separator in output files or python scripts
        Locale.setDefault(Locale.Category.FORMAT, Locale.US);

        if (isMac()) {
            System.setProperty("prism.lcdtext", "false");
        }
        String nmrfxAdvanced = System.getProperty("NMRFX_LEVEL");
        if ("BASIC".equalsIgnoreCase(nmrfxAdvanced)) {
            startInAdvanced = false;
        }
        analystApp = this;
        String title = APP_NAME + " " + getVersion();
        getFXMLControllerManager().newController(stage, title);

        Platform.setImplicitExit(!isMac());

        if (mainMenuBar == null) {
            mainMenuBar = makeMenuBar(APP_NAME);
        }

        AnalystPythonInterpreter.initialize(getParameters());
        ConsoleController.create(AnalystPythonInterpreter.getInterpreter(), "NMRFx Console");
        LogConsoleController.create();
        PeakPicking.registerSinglePickSelectionAction(this::pickedPeakAction);
        PeakMenuBar.addExtra("Add Residue Prefix", PeakLabeller::labelWithSingleResidueChar);
        PeakMenuBar.addExtra("Remove Residue Prefix", PeakLabeller::removeSingleResidueChar);
        KeyBindings.registerGlobalKeyAction("pa", this::assignPeak);
        DataFormatHandlerUtil.addHandlersToController();
        ProjectBase.setPCS(new FxPropertyChangeSupport(this));
        ProjectBase.addPropertyChangeListener(evt -> {
            if (Objects.equals(evt.getPropertyName(), "project")) {
                getFXMLControllerManager().getControllers().forEach(FXMLController::enableFavoriteButton);
            }
        });
        PDBFile.setLocalResLibDir(AnalystPrefs.getLocalResidueDirectory());
        RunAboutSaveFrameProcessor runAboutSaveFrameProcessor = new RunAboutSaveFrameProcessor();
        ProjectBase.addSaveframeProcessor("runabout", runAboutSaveFrameProcessor);

        PluginLoader.getInstance().registerPluginsOnEntryPoint(EntryPoint.STARTUP, null);
        moleculeMap = FXCollections.observableHashMap();
        MoleculeFactory.setMoleculeMap(moleculeMap);
        ProjectBase.getActive().projectChanged(false);
    }

    @Override
    public void stop() {
        waitForCommit();
    }

    public static boolean isMac() {
        return SystemUtils.IS_OS_MAC;
    }

    public static String getVersion() {
        return NvUtil.getVersion();
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
        MenuItem aboutItem;
        Stage aboutStage = makeAbout(appName);
        if (tk != null) {
            aboutItem = tk.createAboutMenuItem(appName, aboutStage);
        } else {
            aboutItem = new MenuItem("About...");
            aboutItem.setOnAction(e -> aboutStage.show());
        }
        MenuItem prefsItem = new MenuItem("Preferences...");
        MenuItem quitItem;
        prefsItem.setOnAction(this::showPreferences);
        if (tk != null) {
            quitItem = tk.createQuitMenuItem(appName);
            quitItem.setOnAction(e -> quit());
            appMenu.getItems().addAll(aboutItem, new SeparatorMenuItem(), prefsItem, new SeparatorMenuItem(),
                    tk.createHideMenuItem(appName), tk.createHideOthersMenuItem(), tk.createUnhideAllMenuItem(),
                    new SeparatorMenuItem(), quitItem);
        } else {
            quitItem = new MenuItem("Quit");
            quitItem.setOnAction(e -> quit());
        }
        // File Menu (items TBD)
        Menu fileMenu = new Menu("File");
        fileMenuActions = new FileMenuActions(this, fileMenu);
        fileMenuActions.basic();
        if (!startInAdvanced && !advancedIsActive) {
            fileMenuActions.addAdvancedMenuItem();
        }

        Menu projectMenu = new Menu("Projects");
        projectMenuActions = new ProjectMenuActions(this, projectMenu);
        projectMenuActions.basic();

        Menu spectraMenu = new Menu("Spectra");
        spectrumMenuActions = new SpectrumMenuActions(this, spectraMenu);
        spectrumMenuActions.basic();

        Menu molMenu = new Menu("Molecules");
        molMenuActions = new MoleculeMenuActions(this, molMenu);
        molMenuActions.basic();

        Menu peakMenu = new Menu("Peaks");
        peakMenuActions = new PeakMenuActions(this, peakMenu);
        peakMenuActions.basic();

        Menu viewMenu = new Menu("View");
        viewMenuActions = new ViewMenuItems(this, viewMenu);
        viewMenuActions.basic();

        Menu helpMenu = new Menu("Help");
        HelpMenuActions helpMenuActions = new HelpMenuActions(this, helpMenu);
        helpMenuActions.basic();

        PluginLoader pluginLoader = PluginLoader.getInstance();
        Menu pluginsMenu = new Menu("Plugins");
        PluginFunction pluginFunction = new PluginFunction(pluginsMenu, this::pluginCommand);
        pluginLoader.registerPluginsOnEntryPoint(EntryPoint.MENU_PLUGINS, pluginFunction);
        pluginsMenu.setVisible(!pluginsMenu.getItems().isEmpty());
        pluginLoader.registerPluginsOnEntryPoint(EntryPoint.MENU_FILE, fileMenu);

        Menu spectraWindowMenu = new Menu("Spectra Windows");
        Menu windowMenu = new Menu("Window");
        windowMenu.setOnShown(e -> updateWindowMenu(spectraWindowMenu));

        if (tk != null) {
            windowMenu.getItems().addAll(tk.createMinimizeMenuItem(), tk.createZoomMenuItem(), tk.createCycleWindowsItem(),
                    new SeparatorMenuItem(), tk.createBringAllToFrontItem(), spectraWindowMenu);
            menuBar.getMenus().addAll(appMenu, fileMenu, projectMenu, spectraMenu, molMenu, viewMenu, peakMenu, pluginsMenu, windowMenu, helpMenu);
            tk.setGlobalMenuBar(menuBar);
        } else {
            fileMenu.getItems().add(prefsItem);
            fileMenu.getItems().add(quitItem);
            windowMenu.getItems().add(spectraWindowMenu);
            menuBar.getMenus().addAll(fileMenu, projectMenu, spectraMenu, molMenu, viewMenu, peakMenu, pluginsMenu, windowMenu, helpMenu);
            helpMenu.getItems().add(0, aboutItem);
        }
        if (startInAdvanced || advancedIsActive) {
            advanced(null);
        }
        return menuBar;
    }

    private void updateWindowMenu(Menu windowMenu) {
        FXMLControllerManager manager = getFXMLControllerManager();
        windowMenu.getItems().clear();
        for (var fxmlController : manager.getControllers()) {
            Stage stage = fxmlController.getStage();
            MenuItem menuItem = new MenuItem(stage.getTitle());
            menuItem.setOnAction(e -> stage.toFront());
            windowMenu.getItems().add(menuItem);
        }
    }

    String pluginCommand(String s) {
        String[] fields = s.split(" ");
        if ((fields.length == 2) && fields[0].equalsIgnoreCase("nw.showPeak")) {
            Peak peak = PeakList.getAPeak(fields[1]);
            if (peak != null) {
                GUIScripter.showPeak(peak);
            }
        }
        return "";
    }

    void pickedPeakAction(Peak peak) {
        if (peakMenuActions != null) {
            peakMenuActions.pickedPeakAction(peak);
        }
    }

    public void assignPeak(String keyStr, PolyChart chart) {
        if (peakMenuActions != null) {
            peakMenuActions.assignPeak();
        }
    }

    Stage makeAbout(String appName) {
        AboutStageBuilder aboutStageBuilder = AboutStageBuilder.start("About " + appName)
                .withAppName(appName).withCloseOnFocusLoss().withText("Processing for NMR Data")
                .withVersionString("Version " + getVersion()).withCopyright("Copyright © " + Calendar
                        .getInstance().get(Calendar.YEAR));
        InputStream inputStream = AnalystApp.class.getResourceAsStream("/images/Icon_NVFX_256.png");
        if (inputStream != null) {
            aboutStageBuilder = aboutStageBuilder.withImage(new Image(inputStream));
        }
        return aboutStageBuilder.build();
    }

    @FXML
    private void showPreferences(ActionEvent event) {
        if (preferencesController == null) {
            preferencesController = PreferencesController.create(stages.get(0));
            addPrefs();
        }
        if (preferencesController != null) {
            preferencesController.getStage().show();
        }
    }

    public void quit() {
        boolean projectChanged = ProjectBase.getActive().projectChanged();
        if (!projectChanged || GUIUtils.affirm("Project changed, really quit?")) {
            saveDatasets();
            waitForCommit();
            Platform.exit();
            System.exit(0);
        }
    }



    public void advanced(MenuItem startAdvancedItem) {
        if (molMenuActions != null) {
            molMenuActions.activateAdvanced();
        }
        if (fileMenuActions != null) {
            fileMenuActions.activateAdvanced();
        }
        if (spectrumMenuActions != null) {
            spectrumMenuActions.activateAdvanced();
        }
        if (projectMenuActions != null) {
            projectMenuActions.activateAdvanced();
        }
        if (peakMenuActions != null) {
            peakMenuActions.activateAdvanced();
        }
        if (viewMenuActions != null) {
            viewMenuActions.activateAdvanced();
        }
        if (!advancedIsActive) {
            addAdvancedTools();
            advancedIsActive = true;
        }
        if (startAdvancedItem != null) {
            startAdvancedItem.setDisable(true);
        }
    }

    void addPrefs() {
        AnalystPrefs.addPrefs(preferencesController);
    }

    private void saveDatasets() {
        for (var controller : getFXMLControllerManager().getControllers()) {
            controller.saveDatasets();
        }
    }

    public void waitForCommit() {
        int nTries = 30;
        int iTry = 0;
        while (GitManager.isCommitting() && (iTry < nTries)) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                break;
            }
            iTry++;
        }

    }

    private void addAdvancedTools() {
        for (var controller : getFXMLControllerManager().getControllers()) {
            addAdvancedTools(controller.getStatusBar());
        }
    }

    private void addAdvancedTools(SpectrumStatusBar statusBar) {
        FXMLController controller = statusBar.getController();

        MenuItem compareMenuItem = new MenuItem("Show Comparator");
        compareMenuItem.setOnAction(e -> controller.showSpectrumComparator());

        statusBar.addToToolMenu("Spectrum Tools", compareMenuItem);

        MenuItem peakNavigatorMenuItem = new MenuItem("Show Peak Navigator");
        peakNavigatorMenuItem.setOnAction(e -> controller.showPeakNavigator());

        MenuItem pathToolMenuItem = new MenuItem("Show Path Tool");
        pathToolMenuItem.setOnAction(e -> showPeakPathTool());

        MenuItem peakAssignMenuItem = new MenuItem("Show Peak Assigner");
        peakAssignMenuItem.setOnAction(e -> showPeakAssignTool());

        MenuItem peakSliderMenuItem = new MenuItem("Show Peak Slider");
        peakSliderMenuItem.setOnAction(e -> showPeakSlider());

        Menu peakToolMenu = new Menu("Peak Tools");
        peakToolMenu.getItems().addAll(peakNavigatorMenuItem, pathToolMenuItem, peakSliderMenuItem, peakAssignMenuItem);

        statusBar.addToToolMenu(peakToolMenu);


        MenuItem spectrumLibraryMenuItem = new MenuItem("Show Spectrum Library");
        spectrumLibraryMenuItem.disableProperty().bind(getFXMLControllerManager().activeControllerProperty().isNull());
        spectrumLibraryMenuItem.setOnAction(e -> showSpectrumLibrary());

        MenuItem spectrumFitLibraryMenuItem = new MenuItem("Show Spectrum Fitter");
        spectrumFitLibraryMenuItem.disableProperty().bind(getFXMLControllerManager().activeControllerProperty().isNull());
        spectrumFitLibraryMenuItem.setOnAction(e -> showSpectrumFitter());

        Menu libraryMenu = new Menu("Library");
        libraryMenu.getItems().addAll(spectrumLibraryMenuItem, spectrumFitLibraryMenuItem);
        statusBar.addToToolMenu(libraryMenu);


        MenuItem scannerToolItem = new MenuItem("Show Scanner");
        statusBar.addToToolMenu(scannerToolItem);
        scannerToolItem.setOnAction(e -> showScannerTool());

        Menu proteinMenu = new Menu("Protein Tools");
        statusBar.addToToolMenu(proteinMenu);

        MenuItem runAboutToolItem = new MenuItem("Show RunAbout");
        proteinMenu.getItems().add(runAboutToolItem);
        runAboutToolItem.setOnAction(e -> controller.showRunAboutTool());

        MenuItem stripsToolItem = new MenuItem("Show Strips Tool");
        proteinMenu.getItems().add(stripsToolItem);
        stripsToolItem.setOnAction(e -> controller.showStripsBar());

        PluginLoader.getInstance().registerPluginsOnEntryPoint(EntryPoint.STATUS_BAR_TOOLS, statusBar);

    }

    public void showPeakPathTool() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        if (!controller.containsTool(PathTool.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            PathTool pathTool = new PathTool(controller, this::removePeakPathTool);
            pathTool.initialize(vBox);
            controller.addTool(pathTool);
        }
    }

    public void showPeakAssignTool() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        if (!controller.containsTool(PeakAssignTool.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            PeakAssignTool peakAssignTool = new PeakAssignTool(controller, this::removePeakAssignTool);
            peakAssignTool.initialize(vBox);
            controller.addTool(peakAssignTool);
        }
    }

    public void showPeakSlider() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        if (!controller.containsTool(PeakSlider.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            PeakSlider peakSlider = new PeakSlider(controller, this::removePeakSlider);
            peakSlider.initSlider(vBox);
            controller.addTool(peakSlider);
        }
    }

    public void showSpectrumLibrary() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        if (!controller.containsTool(SimMolController.class)) {
            ToolBar navBar = new ToolBar();
            controller.getBottomBox().getChildren().add(navBar);
            SimMolController simMol = new SimMolController(controller, this::removeMolSim);
            simMol.initialize(navBar);
            controller.addTool(simMol);
        }
    }

    public void showSpectrumFitter() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        if (!controller.containsTool(SimFitMolController.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            ToolBar navBar = new ToolBar();
            ToolBar fitBar = new ToolBar();
            vBox.getChildren().add(navBar);
            vBox.getChildren().add(fitBar);
            SimFitMolController simFit = new SimFitMolController(controller, this::removeMolFitter);
            simFit.initialize(vBox, navBar, fitBar);
            controller.addTool(simFit);
        }
    }

    public void showScannerTool() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        controller.showScannerMenus();
    }

    public void removePeakPathTool(PathTool pathTool) {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        controller.removeTool(PathTool.class);
        controller.removeBottomBoxNode(pathTool.getBox());
    }

    public void removePeakAssignTool(PeakAssignTool peakAssignTool) {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        controller.removeTool(PeakAssignTool.class);
        controller.removeBottomBoxNode(peakAssignTool.getBox());
    }

    public void removePeakSlider(PeakSlider peakSlider) {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        controller.removeTool(PeakSlider.class);
        controller.removeBottomBoxNode(peakSlider.getBox());
        peakSlider.removeListeners();
    }

    public void removeMolSim(SimMolController simMolController) {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        controller.removeTool(SimMolController.class);
        controller.removeBottomBoxNode(simMolController.getToolBar());
    }

    public void removeMolFitter(SimFitMolController simMolController) {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        controller.removeTool(SimFitMolController.class);
        controller.removeBottomBoxNode(simMolController.getBox());
    }

    public void readMolecule(String type) {
        if (molMenuActions != null) {
            molMenuActions.readMolecule(type);
        }
    }

    public void addStatusBarTools(SpectrumStatusBar statusBar) {
        addStatusBarButtons(statusBar);
        if (advancedIsActive) {
            addAdvancedTools(statusBar);
        }
    }

    private void addStatusBarButtons(SpectrumStatusBar statusBar) {
        var controller = statusBar.getController();
        SimplePeakRegionTool simplePeakRegionTool = new SimplePeakRegionTool(controller);
        simplePeakRegionTool.addButtons(statusBar);
        controller.addTool(simplePeakRegionTool);
    }

    public void showPeakTable(PeakList peakList) {
        if (peakMenuActions != null) {
            PeakMenuActions.showPeakTable(peakList);
        }
    }

    public ScannerTool getScannerTool() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        return (ScannerTool) controller.getTool(ScannerTool.class);
    }

    public StripController getStripsTool() {
        FXMLController controller = getFXMLControllerManager().getOrCreateActiveController();
        return (StripController) controller.getTool(StripController.class);
    }

    public void hidePopover(boolean always) {
        popoverTool.hide(always);
    }

    public void showPopover(PolyChart chart, Bounds objectBounds, Object hitObject) {
        popoverTool.showPopover(chart, objectBounds, hitObject);
    }

    public DatasetBrowserController getOrCreateDatasetBrowserController() {
        if (datasetBrowserController == null) {
            datasetBrowserController = DatasetBrowserController.create();
        }
        return datasetBrowserController;
    }

    /**
     * Closes all stages and controllers except the main stage/first controller.
     */
    public static void closeAll() {
        PolyChartManager.getInstance().closeAll();
        List<FXMLController> controllers = new ArrayList<>(getFXMLControllerManager().getControllers());
        // Don't close the first controller that matches with the main stage, Note this first controller is not
        // necessarily the active controller
        for (int index = 1; index < controllers.size(); index++) {
            getFXMLControllerManager().closeController(controllers.get(index));
        }

        Stage mainStage = getMainStage();
        // Since stages are removed in a separate function after calling stage.close, must make a copy of
        // the list to avoid concurrent modification
        List<Stage> stageCopy = new ArrayList<>(stages);
        for (Stage stage : stageCopy) {
            if (stage != mainStage) {
                stage.hide();
                removeStage(stage);
            }
        }
    }

    public static Stage getMainStage() {
        if (stages.isEmpty()) {
            return null;
        } else {
            return stages.get(0);
        }
    }

    public static void removeStage(Stage stage) {
        synchronized (stages) {
            stages.remove(stage);
            if (stages.isEmpty() && !isMac()) {
                Platform.exit();
                System.exit(0);
            }
        }
    }

    public static void registerStage(Stage stage) {
        if (!stages.contains(stage)) {
            stages.add(stage);
        }
    }

    public static List<Stage> getStages() {
        return stages;
    }

    /**
     * Set the default font size of the provided stage with the provided
     * font size string.
     *
     * @param stage       The stage to set the font for
     * @param fontSizeStr A string font size ex. '9pt'
     */
    public static void setStageFontSize(Stage stage, String fontSizeStr) {
        if (stage != null && stage.getScene() != null) {
            stage.getScene().getRoot().setStyle("-fx-font-size: " + fontSizeStr);
        } else {
            log.info("Unable to set font size for stage.");
        }
    }

    public static FXMLControllerManager getFXMLControllerManager() {
        return fxmlControllerManager;
    }

    public static ConsoleController getConsoleController() {
        return ConsoleController.getConsoleController();
    }

    public static LogConsoleController getLogConsoleController() {
        return LogConsoleController.getLogConsoleController();
    }

    public static void getShapePrefs(PeakFitParameters fitPars) {
        fitPars.shapeParameters(PreferencesController.getFitPeakShape(),
                PreferencesController.getConstrainPeakShape(),
                PreferencesController.getPeakShapeDirectFactor(),
                PreferencesController.getPeakShapeIndirectFactor());
    }
    public static void showHistoryAction(ActionEvent event) {
        GUIProject guiProject = GUIProject.getActive();
        if ((guiProject != null) && (guiProject.getGitManager() != null)) {
            guiProject.getGitManager().showHistoryAction(event);
        }
    }



    public static void addMoleculeListener(MapChangeListener<String, MoleculeBase> listener) {
        AnalystApp.getAnalystApp().moleculeMap.addListener(listener);
    }

    public static MenuBar getMenuBar() {
        return analystApp.makeMenuBar(APP_NAME);
    }

    public static AnalystApp getAnalystApp() {
        return analystApp;
    }

    public static String getAppName() {
        return APP_NAME;
    }

    public static ProjectBase getActive() {
        return GUIProject.getActive();
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
}
