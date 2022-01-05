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
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.molecule.CanvasMolecule;
import org.nmrfx.analyst.gui.molecule.MoleculeMenuActions;
import org.nmrfx.analyst.gui.peaks.MultipletController;
import org.nmrfx.analyst.gui.peaks.PeakAssignTool;
import org.nmrfx.analyst.gui.peaks.PeakMenuActions;
import org.nmrfx.analyst.gui.plugin.PluginLoader;
import org.nmrfx.analyst.gui.spectra.SpectrumMenuActions;
import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.analyst.gui.tools.*;
import org.nmrfx.chemistry.io.*;
import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.console.ConsoleController;
import org.nmrfx.peaks.PeakLabeller;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.io.PeakReader;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.processor.gui.utils.FxPropertyChangeSupport;
import org.nmrfx.processor.project.Project;
import org.nmrfx.processor.utilities.WebConnect;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.python.util.InteractiveInterpreter;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnalystApp extends MainApp {

    private static String version = null;
    static String appName = "NMRFx Analyst";
    private static MenuBar mainMenuBar = null;
    static AnalystApp analystApp = null;
    private static MultipletController multipletController;

    private static FileMenuActions fileMenuActions;
    private static MoleculeMenuActions molMenuActions;
    private static PeakMenuActions peakMenuActions;
    private static SpectrumMenuActions spectrumMenuActions;
    private static ProjectMenuActions projectMenuActions;
    private static ViewMenuItems viewMenuActions;

    MenuToolkit menuTk;
    Boolean isMac = null;

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

    @Override
    public void start(Stage stage) throws Exception {
        if (isMac()) {
            System.setProperty("prism.lcdtext", "false");
        }
        MainApp.setAnalyst();
        mainApp = this;
        analystApp = this;
        FXMLController controller = FXMLController.create(stage);
        Platform.setImplicitExit(true);
        hostServices = getHostServices();
        stage.setTitle(appName + " " + getVersion());

        if (mainMenuBar == null) {
            mainMenuBar = makeMenuBar(appName);
        }
//        ScannerController.addCreateAction(e -> updateScannerGUI(e));
        Parameters parameters = getParameters();
        System.out.println(parameters.getRaw());

        interpreter.exec("import os");
        interpreter.exec("import glob");
        interpreter.exec("from pyproc import *\ninitLocal()");
        interpreter.exec("from gscript import *\nnw=NMRFxWindowScripting()");
        interpreter.exec("from dscript import *");
        interpreter.exec("from mscript import *");
        interpreter.exec("from pscript import *");
        interpreter.set("argv", parameters.getRaw());
        interpreter.exec("parseArgs(argv)");
        ConsoleController.create(interpreter, "NMRFx Console");
        PeakPicking.registerSinglePickAction((c) -> pickedPeakAction(c));
        PeakMenuBar.addExtra("Add Residue Prefix", PeakLabeller::labelWithSingleResidueChar);
        PeakMenuBar.addExtra("Remove Residue Prefix", PeakLabeller::removeSingleResidueChar);
        KeyBindings.registerGlobalKeyAction("pa", this::assignPeak);
        Project.setPCS(new FxPropertyChangeSupport(this));
        PDBFile.setLocalResLibDir(AnalystPrefs.getLocalResidueDirectory());

        PluginLoader.getInstance().registerPluginsOnEntryPoint(EntryPoint.STARTUP, null);
    }

    void pickedPeakAction(Object peakObject) {
        if (peakMenuActions != null) {
            peakMenuActions.pickedPeakAction(peakObject);
        }
    }

    public static boolean isMac() {
        return SystemUtils.IS_OS_MAC;
    }

    public static MenuBar getMenuBar() {
        return mainApp.makeMenuBar(appName);
    }
    public static MenuBar getMainMenuBar() {
        return mainMenuBar;
    }

    public static AnalystApp getAnalystApp() {
        return analystApp;
    }

    public static String getAppName() {
        return appName;
    }

    public static PreferencesController getPreferencesController() {
        return preferencesController;
    }

    public void quit() {
        System.out.println("quit");
        waitForCommit();
        Platform.exit();
        System.exit(0);
    }

    Stage makeAbout(String appName) {
        AboutStageBuilder aboutStageBuilder = AboutStageBuilder.start("About " + appName)
                .withAppName(appName).withCloseOnFocusLoss().withText("Processing for NMR Data")
                .withVersionString("Version " + getVersion()).withCopyright("Copyright \u00A9 " + Calendar
                .getInstance().get(Calendar.YEAR));
        Image image = new Image(AnalystApp.class.getResourceAsStream("/images/Icon_NVFX_256.png"));
        aboutStageBuilder = aboutStageBuilder.withImage(image);
        return aboutStageBuilder.build();
    }

    @Override
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
            AnalystApp.hostServices.showDocument("http://link.springer.com/article/10.1007/s10858-016-0049-6");
        });

        MenuItem openSourceItem = new MenuItem("Open Source Libraries");
        openSourceItem.setOnAction(e -> showOpenSourceAction(e));

        helpMenu.getItems().addAll(docsMenuItem, webSiteMenuItem, mailingListItem, versionMenuItem, refMenuItem, openSourceItem);

        Menu pluginsMenu = new Menu("Plugins");
        PluginLoader.getInstance().registerPluginsOnEntryPoint(EntryPoint.MENU_PLUGINS, pluginsMenu);
        pluginsMenu.setVisible(!pluginsMenu.getItems().isEmpty());

        if (tk != null) {
            Menu windowMenu = new Menu("Window");
            windowMenu.getItems().addAll(tk.createMinimizeMenuItem(), tk.createZoomMenuItem(), tk.createCycleWindowsItem(),
                    new SeparatorMenuItem(), tk.createBringAllToFrontItem());
            menuBar.getMenus().addAll(appMenu, fileMenu, projectMenu, spectraMenu, molMenu, viewMenu, peakMenu, pluginsMenu, windowMenu, helpMenu);
            tk.autoAddWindowMenuItems(windowMenu);
            tk.setGlobalMenuBar(menuBar);
        } else {
            fileMenu.getItems().add(prefsItem);
            fileMenu.getItems().add(quitItem);
            menuBar.getMenus().addAll(fileMenu, projectMenu, spectraMenu, molMenu, viewMenu, peakMenu, pluginsMenu, helpMenu);
            helpMenu.getItems().add(0, aboutItem);
        }
        return menuBar;
    }

    private Optional<Menu> getMenu(MenuBar menuBar, String menuName) {
        return menuBar.getMenus().stream().filter(m -> m.getText().equals(menuName)).findFirst();
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
        startAdvancedItem.setDisable(true);

    }

    public void readMolecule(String type) {
        if (molMenuActions != null) {
            molMenuActions.readMolecule(type);
        }
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
        return NvUtil.getVersion();
    }

    @Override
    public void addStatusBarTools(SpectrumStatusBar statusBar) {
        Menu oneDMenu = new Menu("Analysis (1D)");
        MenuItem multipletToolItem = new MenuItem("Show Multiplet Tool");
        multipletToolItem.setOnAction(e -> showMultipletTool());

        MenuItem regionsMenuItem = new MenuItem("Show Regions Tool");
        regionsMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        regionsMenuItem.setOnAction(e -> showRegionTool());

        MenuItem spectrumLibraryMenuItem = new MenuItem("Show Spectrum Library");
        spectrumLibraryMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        spectrumLibraryMenuItem.setOnAction(e -> showSpectrumLibrary());

        MenuItem spectrumFitLibraryMenuItem = new MenuItem("Show Spectrum Fitter");
        spectrumFitLibraryMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        spectrumFitLibraryMenuItem.setOnAction(e -> showSpectrumFitter());

        oneDMenu.getItems().addAll(multipletToolItem, regionsMenuItem,
                spectrumLibraryMenuItem, spectrumFitLibraryMenuItem);

        Menu molMenu = new Menu("Molecule");
        MenuItem canvasMolMenuItem = new MenuItem("Show Molecule");
        canvasMolMenuItem.setOnAction(e -> addMolecule());
        MenuItem delCanvasMolMenuItem = new MenuItem("Remove Molecule");
        delCanvasMolMenuItem.setOnAction(e -> removeMolecule());
        molMenu.getItems().addAll(canvasMolMenuItem, delCanvasMolMenuItem);

        statusBar.addToToolMenu(oneDMenu);
        statusBar.addToToolMenu(molMenu);

        MenuItem peakAssignMenuItem = new MenuItem("Show Peak Assigner");
        statusBar.addToToolMenu("Peak Tools", peakAssignMenuItem);
        peakAssignMenuItem.setOnAction(e -> showPeakAssignTool());

        MenuItem peakSliderMenuItem = new MenuItem("Show Peak Slider");
        statusBar.addToToolMenu("Peak Tools", peakSliderMenuItem);
        peakSliderMenuItem.setOnAction(e -> showPeakSlider());

        MenuItem scannerToolItem = new MenuItem("Show Scanner");
        statusBar.addToToolMenu(scannerToolItem);
        scannerToolItem.setOnAction(e -> showScannerTool());

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

    static void showOpenSourceAction(ActionEvent event) {
        hostServices.showDocument("https://nmrfx.org/downloads/oss/dependencies.html");
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
        alert.setTitle("NMRFx Analyst Version");
        alert.showAndWait();
    }

    @FXML
    private void showPreferences(ActionEvent event) {
        if (preferencesController == null) {
            preferencesController = PreferencesController.create(stages.get(0));
            addPrefs();
        }
        if (preferencesController != null) {
            preferencesController.getStage().show();
        } else {
            System.out.println("Coudn't make controller");
        }
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


    public void assignPeak(String keyStr, PolyChart chart) {
        if (peakMenuActions != null) {
            peakMenuActions.assignPeak();
        }
    }

    @FXML
    private void showMultipletAnalyzer(ActionEvent event) {
        if (multipletController == null) {
            multipletController = MultipletController.create();
        } else {
            multipletController.initMultiplet();
        }
        multipletController.getStage().show();
        multipletController.getStage().toFront();
    }


    public void showSpectrumLibrary() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(SimMolController.class)) {
            ToolBar navBar = new ToolBar();
            controller.getBottomBox().getChildren().add(navBar);
            SimMolController simMol = new SimMolController(controller, this::removeMolSim);
            simMol.initialize(navBar);
            controller.addTool(simMol);
        }
    }

    public void removeMolSim(SimMolController simMolController) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(SimMolController.class);
        controller.getBottomBox().getChildren().remove(simMolController.getToolBar());
    }

    public void showSpectrumFitter() {
        FXMLController controller = FXMLController.getActiveController();
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

    public void removeMolFitter(SimFitMolController simMolController) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(SimFitMolController.class);
        controller.getBottomBox().getChildren().remove(simMolController.getBox());
    }


    public StripController getStripsTool() {
        FXMLController controller = FXMLController.getActiveController();
        StripController stripsController = (StripController) controller.getTool(StripController.class);
        return stripsController;
    }

    public void showMultipletTool() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(MultipletTool.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            MultipletTool multipletTool = new MultipletTool(controller, this::removeMultipletToolBar);
            multipletTool.initialize(vBox);
            controller.addTool(multipletTool);
        }
    }

    public void showPeakAssignTool() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(PeakAssignTool.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            PeakAssignTool peakAssignTool = new PeakAssignTool(controller, this::removePeakAssignTool);
            peakAssignTool.initialize(vBox);
            controller.addTool(peakAssignTool);
        }
    }

    public void removePeakAssignTool(PeakAssignTool peakAssignTool) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(PeakAssignTool.class);
        controller.getBottomBox().getChildren().remove(peakAssignTool.getBox());
    }

    public void showPeakSlider() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(PeakSlider.class)) {
            ToolBar navBar = new ToolBar();
            controller.getBottomBox().getChildren().add(navBar);
            PeakSlider peakSlider = new PeakSlider(controller, this::removePeakSlider);
            peakSlider.initSlider(navBar);
            controller.addTool(peakSlider);
        }
    }

    public void removePeakSlider(PeakSlider peakSlider) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(PeakSlider.class);
        controller.getBottomBox().getChildren().remove(peakSlider.getToolBar());
    }

    public MultipletTool getMultipletTool() {
        FXMLController controller = FXMLController.getActiveController();
        MultipletTool multipletTool = (MultipletTool) controller.getTool(MultipletTool.class);
        return multipletTool;
    }

    public void removeMultipletToolBar(MultipletTool multipletTool) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(MultipletTool.class);
        controller.getBottomBox().getChildren().remove(multipletTool.getBox());
    }

    public void showRegionTool() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(RegionTool.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            RegionTool regionTool = new RegionTool(controller, this::removeRegionTool);
            regionTool.initialize(vBox);
            controller.addTool(regionTool);
        }
    }

    public RegionTool getRegionTool() {
        FXMLController controller = FXMLController.getActiveController();
        RegionTool regionTool = (RegionTool) controller.getTool(RegionTool.class);
        return regionTool;
    }

    public void removeRegionTool(RegionTool regionTool) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(RegionTool.class);
        controller.getBottomBox().getChildren().remove(regionTool.getBox());
    }

    public void showScannerTool() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(ScannerTool.class)) {
            BorderPane vBox = new BorderPane();
            controller.getBottomBox().getChildren().add(vBox);
            ScannerTool scannerTool = new ScannerTool(controller, this::removeScannerTool);
            scannerTool.initialize(vBox);
            controller.addTool(scannerTool);
        }
    }

    public void removeScannerTool(ScannerTool scannerTool) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(ScannerTool.class);
        controller.getBottomBox().getChildren().remove(scannerTool.getBox());
    }

    void addPrefs() {
        AnalystPrefs.addPrefs();
    }

    void addMolecule() {
        Molecule activeMol = Molecule.getActive();
        if (activeMol != null) {
            CanvasMolecule cMol = new CanvasMolecule(FXMLController.getActiveController().getActiveChart());
            cMol.setMolName(activeMol.getName());
            activeMol.label = Molecule.LABEL_NONHC;
            activeMol.clearSelected();

            cMol.setPosition(0.1, 0.1, 0.3, 0.3, "FRACTION", "FRACTION");
            PolyChart chart = FXMLController.getActiveController().getActiveChart();
            chart.addAnnotation(cMol);
            chart.refresh();
        }
    }

    void removeMolecule() {
        PolyChart chart = FXMLController.getActiveController().getActiveChart();
        chart.clearAnnoType(CanvasMolecule.class);
        chart.refresh();
    }
}
