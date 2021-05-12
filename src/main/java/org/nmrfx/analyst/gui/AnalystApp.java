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

import org.nmrfx.processor.datasets.Dataset;
import de.jangassen.MenuToolkit;
import de.jangassen.dialogs.about.AboutStageBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.python.util.InteractiveInterpreter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import org.apache.commons.lang3.SystemUtils;
import org.controlsfx.dialog.ExceptionDialog;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ToolBar;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.nmrfx.processor.gui.controls.FractionCanvas;
import org.nmrfx.processor.utilities.WebConnect;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.analyst.gui.molecule3D.MolSceneController;
import static javafx.application.Application.launch;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import org.comdnmr.gui.PyController;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.chemistry.io.MMcifReader;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.chemistry.io.Sequence;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakLabeller;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.io.PeakReader;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.utils.GUIUtils;
import org.python.util.PythonInterpreter;
import org.nmrfx.analyst.gui.molecule.CanvasMolecule;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.analyst.gui.tools.RunAboutGUI;
import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.processor.gui.ConsoleController;
import org.nmrfx.processor.gui.DatasetsController;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.MainApp;
import org.nmrfx.processor.gui.PeakMenuBar;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.ScannerController;
import org.nmrfx.processor.gui.SpectrumStatusBar;
import org.nmrfx.processor.gui.utils.FxPropertyChangeSupport;
import org.nmrfx.processor.project.Project;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.ParseException;

public class AnalystApp extends MainApp {

    private static String version = null;
    static String appName = "NMRFx Analyst";
    MenuToolkit menuTk;
    private static MenuBar mainMenuBar = null;
    Boolean isMac = null;

    static AnalystApp analystApp = null;

    public static MultipletController multipletController;
    public static RegionController regionController;
    public static AtomController atomController;
    public static LigandScannerController scannerController;
    public static MolSceneController molController;
    public static AtomBrowser atomBrowser;
    public static RNAPeakGeneratorSceneController rnaPeakGenController;
    public static PeakTableController peakTableController;
    public static NOETableController noeTableController;
    public static PyController ringNMRController;
    public static WindowIO windowIO = null;
    PeakAtomPicker peakAtomPicker = null;
    CheckMenuItem assignOnPick;
    RDCGUI rdcGUI = null;

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
        ScannerController.addCreateAction(e -> updateScannerGUI(e));
        Parameters parameters = getParameters();
        System.out.println(parameters.getRaw());

        interpreter.exec("import os");
        interpreter.exec("from pyproc import *\ninitLocal()");
        interpreter.exec("from gscript import *\nnw=NMRFxWindowScripting()");
        interpreter.exec("from dscript import *");
        interpreter.exec("from mscript import *");
        interpreter.exec("from pscript import *");
        interpreter.set("argv", parameters.getRaw());
        interpreter.exec("parseArgs(argv)");
        PeakPicking.registerSinglePickAction((c) -> pickedPeakAction(c));
        PeakMenuBar.addExtra("Add Residue Prefix", PeakLabeller::labelWithSingleResidueChar);
        PeakMenuBar.addExtra("Remove Residue Prefix", PeakLabeller::removeSingleResidueChar);
        KeyBindings.registerGlobalKeyAction("pa", this::assignPeak);
        Project.setPCS(new FxPropertyChangeSupport(this));

    }

    private void updateScannerGUI(ScannerController scannerController) {
        System.out.println("update scanner " + scannerController);
        MinerController minerController = new MinerController(scannerController);
    }

    Object pickedPeakAction(Object peakObject) {
        if (assignOnPick.isSelected()) {
            Peak peak = (Peak) peakObject;
            System.out.println(peak.getName());
            PolyChart chart = FXMLController.getActiveController().getActiveChart();
            double x = chart.getMouseX();
            double y = chart.getMouseY();
            Canvas canvas = chart.getCanvas();
            Point2D sXY = canvas.localToScreen(x, y);
            if (peakAtomPicker == null) {
                peakAtomPicker = new PeakAtomPicker();
                peakAtomPicker.create();
            }
            peakAtomPicker.show(sXY.getX(), sXY.getY(), peak);
        }
        return null;
    }

    public static boolean isMac() {
        return SystemUtils.IS_OS_MAC;
    }

    public static MenuBar getMenuBar() {
        return mainApp.makeMenuBar(appName);
    }

    public static AnalystApp getAnalystApp() {
        return analystApp;
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
        pdfMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        pdfMenuItem.setOnAction(e -> FXMLController.getActiveController().exportPDFAction(e));
        MenuItem svgMenuItem = new MenuItem("Export SVG...");
        svgMenuItem.setOnAction(e -> FXMLController.getActiveController().exportSVGAction(e));
        svgMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        MenuItem loadPeakListMenuItem = new MenuItem("Load PeakLists");
        loadPeakListMenuItem.setOnAction(e -> loadPeakLists());
        MenuItem portMenuItem = new MenuItem("New NMRFx Server...");
        portMenuItem.setOnAction(e -> startServer(e));
        MenuItem datasetBrowserMenuItem = new MenuItem("Dataset Browser...");
        datasetBrowserMenuItem.setOnAction(e -> createRemoteDatasets());

        Menu projectMenu = new Menu("Projects");

        MenuItem projectOpenMenuItem = new MenuItem("Open...");
        projectOpenMenuItem.setOnAction(e -> loadProject());

        MenuItem projectSaveAsMenuItem = new MenuItem("Save As...");
        projectSaveAsMenuItem.setOnAction(e -> saveProjectAs());

        MenuItem projectSaveMenuItem = new MenuItem("Save");
        projectSaveMenuItem.setOnAction(e -> saveProject());
        Menu recentProjectMenuItem = new Menu("Open Recent");

        MenuItem closeProjectMenuItem = new MenuItem("Close");
        closeProjectMenuItem.setOnAction(e -> closeProject());

        MenuItem openSTARMenuItem = new MenuItem("Open STAR3...");
        openSTARMenuItem.setOnAction(e -> readSTAR());

        MenuItem saveSTARMenuItem = new MenuItem("Save STAR3...");
        saveSTARMenuItem.setOnAction(e -> writeSTAR());

        MenuItem openSparkyMenuItem = new MenuItem("Open Sparky Project...");
        openSparkyMenuItem.setOnAction(e -> readSparkyProject());

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

        projectMenu.getItems().addAll(projectOpenMenuItem, recentProjectMenuItem,
                projectSaveMenuItem, projectSaveAsMenuItem, closeProjectMenuItem,
                openSTARMenuItem, saveSTARMenuItem, openSparkyMenuItem);

        fileMenu.getItems().addAll(openMenuItem, openDatasetMenuItem, addMenuItem,
                recentFIDMenuItem, recentDatasetMenuItem, datasetBrowserMenuItem, newMenuItem,
                portMenuItem, new SeparatorMenuItem(), svgMenuItem, pdfMenuItem,
                loadPeakListMenuItem);

        Menu spectraMenu = new Menu("Spectra");
        spectraMenu.disableProperty().bind(FXMLController.activeController.isNull());
        MenuItem deleteItem = new MenuItem("Delete Spectrum");
        deleteItem.setOnAction(e -> FXMLController.getActiveController().getActiveChart().removeSelected());
        MenuItem syncMenuItem = new MenuItem("Sync Axes");
        syncMenuItem.setOnAction(e -> PolyChart.getActiveChart().syncSceneMates());

        Menu arrangeMenu = new Menu("Arrange");
        MenuItem horizItem = new MenuItem("Horizontal");
        horizItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionCanvas.ORIENTATION.HORIZONTAL));
        MenuItem vertItem = new MenuItem("Vertical");
        vertItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionCanvas.ORIENTATION.VERTICAL));
        MenuItem gridItem = new MenuItem("Grid");
        gridItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionCanvas.ORIENTATION.GRID));
        MenuItem overlayItem = new MenuItem("Overlay");
        overlayItem.setOnAction(e -> FXMLController.getActiveController().overlay());
        MenuItem minimizeItem = new MenuItem("Minimize Borders");
        minimizeItem.setOnAction(e -> FXMLController.getActiveController().setBorderState(true));
        MenuItem normalizeItem = new MenuItem("Normal Borders");
        normalizeItem.setOnAction(e -> FXMLController.getActiveController().setBorderState(false));

        arrangeMenu.getItems().addAll(horizItem, vertItem, gridItem, overlayItem, minimizeItem, normalizeItem);
        MenuItem alignMenuItem = new MenuItem("Align Spectra");
        alignMenuItem.setOnAction(e -> FXMLController.getActiveController().alignCenters());
        MenuItem stripsMenuItem = new MenuItem("Show Strips");
        stripsMenuItem.setOnAction(e -> showStripsBar());
        MenuItem favoritesMenuItem = new MenuItem("Favorites");
        favoritesMenuItem.setOnAction(e -> showFavorites());
        MenuItem copyItem = new MenuItem("Copy Spectrum as SVG Text");
        copyItem.setOnAction(e -> FXMLController.getActiveController().copySVGAction(e));
        spectraMenu.getItems().addAll(deleteItem, arrangeMenu, favoritesMenuItem, syncMenuItem,
                alignMenuItem,
                stripsMenuItem, copyItem);

        // Format (items TBD)
//        Menu formatMenu = new Menu("Format");
//        formatMenu.getItems().addAll(new MenuItem("TBD"));
        // View Menu (items TBD)
        Menu molMenu = new Menu("Molecules");
        Menu molFileMenu = new Menu("File");

        MenuItem readSeqItem = new MenuItem("Read Sequence...");
        readSeqItem.setOnAction(e -> readMolecule("seq"));
        molFileMenu.getItems().add(readSeqItem);
        MenuItem readPDBItem = new MenuItem("Read PDB...");
        readPDBItem.setOnAction(e -> readMolecule("pdb"));
        molFileMenu.getItems().add(readPDBItem);
        MenuItem readCoordinatesItem = new MenuItem("Read Coordinates ...");
        readCoordinatesItem.setOnAction(e -> readMolecule("pdb xyz"));
        molFileMenu.getItems().add(readCoordinatesItem);
        MenuItem readPDBxyzItem = new MenuItem("Read PDB XYZ...");
        readPDBxyzItem.setOnAction(e -> readMolecule("pdbx"));
        molFileMenu.getItems().add(readPDBxyzItem);
        MenuItem readMMCIFItem = new MenuItem("Read mmCIF...");
        readMMCIFItem.setOnAction(e -> readMolecule("mmcif"));
        molFileMenu.getItems().add(readMMCIFItem);
        MenuItem readMolItem = new MenuItem("Read Mol...");
        readMolItem.setOnAction(e -> readMolecule("mol"));
        molFileMenu.getItems().add(readMolItem);
        MenuItem seqGUIMenuItem = new MenuItem("Sequence GUI");
        seqGUIMenuItem.setOnAction(e -> SequenceGUI.showGUI(this));

        MenuItem atomsMenuItem = new MenuItem("Atoms");
        atomsMenuItem.setOnAction(e -> showAtoms(e));

        MenuItem molMenuItem = new MenuItem("Viewer");
        molMenuItem.setOnAction(e -> showMols());

        MenuItem rdcMenuItem = new MenuItem("RDC Analysis...");
        rdcMenuItem.setOnAction(e -> showRDCGUI());

        molMenu.getItems().addAll(molFileMenu, seqGUIMenuItem, atomsMenuItem, molMenuItem, rdcMenuItem);

        Menu viewMenu = new Menu("View");
        MenuItem dataMenuItem = new MenuItem("Show Datasets");
        dataMenuItem.setOnAction(e -> showDatasetsTable(e));

        MenuItem consoleMenuItem = new MenuItem("Show Console");
        consoleMenuItem.setOnAction(e -> showConsole(e));

        MenuItem attrMenuItem = new MenuItem("Show Attributes");
        attrMenuItem.setOnAction(e -> FXMLController.getActiveController().showSpecAttrAction(e));

        MenuItem procMenuItem = new MenuItem("Show Processor");
        procMenuItem.setOnAction(e -> FXMLController.getActiveController().showProcessorAction(e));

        MenuItem scannerMenuItem = new MenuItem("Show Scanner");
        scannerMenuItem.setOnAction(e -> FXMLController.getActiveController().showScannerAction(e));

        MenuItem rnaPeakGenMenuItem = new MenuItem("Show RNA Label Scheme");
        rnaPeakGenMenuItem.setOnAction(e -> showRNAPeakGenerator(e));

        viewMenu.getItems().addAll(consoleMenuItem, dataMenuItem, attrMenuItem, procMenuItem, scannerMenuItem, rnaPeakGenMenuItem);

        Menu peakMenu = new Menu("Peaks");

        MenuItem peakAttrMenuItem = new MenuItem("Show Peak Tool");
        peakAttrMenuItem.setOnAction(e -> FXMLController.getActiveController().showPeakAttrAction(e));

        MenuItem peakTableMenuItem = new MenuItem("Show Peak Table");
        peakTableMenuItem.setOnAction(e -> showPeakTable());

        MenuItem linkPeakDimsMenuItem = new MenuItem("Link by Labels");
        linkPeakDimsMenuItem.setOnAction(e -> FXMLController.getActiveController().linkPeakDims());

        MenuItem ligandScannerMenuItem = new MenuItem("Show Ligand Scanner");
        ligandScannerMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        ligandScannerMenuItem.setOnAction(e -> showLigandScanner(e));

        MenuItem noeTableMenuItem = new MenuItem("Show NOE Table");
        noeTableMenuItem.setOnAction(e -> showNOETable());

        Menu assignCascade = new Menu("Assign Tools");

        assignOnPick = new CheckMenuItem("Assign on Pick");

        MenuItem atomBrowserMenuItem = new MenuItem("Show Atom Browser");
        atomBrowserMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        atomBrowserMenuItem.setOnAction(e -> showAtomBrowser());

        MenuItem runAboutMenuItem = new MenuItem("Show RunAboutX");
        runAboutMenuItem.setOnAction(e -> showRunAbout());

        assignCascade.getItems().addAll(assignOnPick,
                atomBrowserMenuItem, runAboutMenuItem);

        peakMenu.getItems().addAll(peakAttrMenuItem,
                peakTableMenuItem, linkPeakDimsMenuItem,
                ligandScannerMenuItem,
                noeTableMenuItem,
                assignCascade);

        Menu dynamicsMenu = new Menu("Dynamics");
        MenuItem ringNMRMenuItem = new MenuItem("Show RINGNMRGui");
        ringNMRMenuItem.setOnAction(e -> showRING());
        dynamicsMenu.getItems().addAll(ringNMRMenuItem);

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
            AnalystApp.hostServices.showDocument("http://link.springer.com/article/10.1007/s10858-016-0049-6");
        });

        // home
        // mailing list
        //
        helpMenu.getItems().addAll(docsMenuItem, webSiteMenuItem, mailingListItem, versionMenuItem, refMenuItem);

        if (tk != null) {
            Menu windowMenu = new Menu("Window");
            windowMenu.getItems().addAll(tk.createMinimizeMenuItem(), tk.createZoomMenuItem(), tk.createCycleWindowsItem(),
                    new SeparatorMenuItem(), tk.createBringAllToFrontItem());
            menuBar.getMenus().addAll(appMenu, fileMenu, projectMenu, spectraMenu, molMenu, viewMenu, peakMenu, dynamicsMenu, windowMenu, helpMenu);
            tk.autoAddWindowMenuItems(windowMenu);
            tk.setGlobalMenuBar(menuBar);
        } else {
            fileMenu.getItems().add(prefsItem);
            fileMenu.getItems().add(quitItem);
            menuBar.getMenus().addAll(fileMenu, projectMenu, spectraMenu, molMenu, viewMenu, peakMenu, dynamicsMenu, helpMenu);
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
        alert.setTitle("NMRFx Analyst Version");
        alert.showAndWait();
    }

    private void showConsole(ActionEvent event) {
        AnalystApp.getConsoleController().show();
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

    private void newGraphics(ActionEvent event) {
        FXMLController controller = FXMLController.create();
    }

    @FXML
    void showDatasetsTable(ActionEvent event) {
        if (datasetController == null) {
            datasetController = DatasetsController.create();
        }
        GUIProject project = (GUIProject) Project.getActive();
        ObservableList datasetObs = (ObservableList) project.getDatasets();
        datasetController.setDatasetList(datasetObs);
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
        return getConsoleController();
    }

    public static void setConsoleController(ConsoleController controller) {
        consoleController = controller;
    }

    public static void writeOutput(String string) {
        if (getConsoleController() == null) {
            System.out.println(string);
        } else {
            getConsoleController().write(string);
        }
    }

    @FXML
    private void showRNAPeakGenerator(ActionEvent event) {
        if (rnaPeakGenController == null) {
            rnaPeakGenController = RNAPeakGeneratorSceneController.create();
        }
        if (rnaPeakGenController != null) {
            rnaPeakGenController.getStage().show();
            rnaPeakGenController.getStage().toFront();
        } else {
            System.out.println("Couldn't make rnaPeakGenController ");
        }
    }

    @FXML
    private void showAtoms(ActionEvent event) {
        if (atomController == null) {
            atomController = AtomController.create();
        }
        if (atomController != null) {
            atomController.getStage().show();
            atomController.getStage().toFront();
        } else {
            System.out.println("Couldn't make atom controller");
        }
    }

    @FXML
    private void showMols() {
        if (molController == null) {
            molController = MolSceneController.create();
        }
        if (molController != null) {
            molController.getStage().show();
            molController.getStage().toFront();
        } else {
            System.out.println("Couldn't make molController");
        }
    }

    @FXML
    private void showLigandScanner(ActionEvent event) {
        if (scannerController == null) {
            scannerController = LigandScannerController.create();
        }
        if (scannerController != null) {
            scannerController.getStage().show();
            scannerController.getStage().toFront();
        } else {
            System.out.println("Couldn't make atom controller");
        }
    }

    private void showPeakTable() {
        if (peakTableController == null) {
            peakTableController = PeakTableController.create();
            List<String> names = Project.getActive().getPeakListNames();
            if (!names.isEmpty()) {
                peakTableController.setPeakList(Project.getActive().getPeakList(names.get(0)));
            }
        }
        if (peakTableController != null) {
            peakTableController.getStage().show();
            peakTableController.getStage().toFront();
        } else {
            System.out.println("Couldn't make peak table controller");
        }
    }

    private void showNOETable() {
        if (noeTableController == null) {
            noeTableController = NOETableController.create();
            Collection<NoeSet> noeSets = MoleculeFactory.getActive().getMolecularConstraints().noeSets();

            if (!noeSets.isEmpty()) {
                noeTableController.setNoeSet(noeSets.stream().findFirst().get());
            }
        }
        if (noeTableController != null) {
            noeTableController.getStage().show();
            noeTableController.getStage().toFront();
            noeTableController.updateNoeSetMenu();
        } else {
            System.out.println("Couldn't make NOE table controller");
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

    public void showAtomBrowser() {
        if (atomBrowser == null) {
            ToolBar navBar = new ToolBar();
            FXMLController controller = FXMLController.getActiveController();
            controller.getBottomBox().getChildren().add(navBar);
            atomBrowser = new AtomBrowser(controller, this::removeAtomBrowser);
            atomBrowser.initSlider(navBar);
        }
    }

    public void removeAtomBrowser(Object o) {
        if (atomBrowser != null) {
            FXMLController controller = FXMLController.getActiveController();
            controller.getBottomBox().getChildren().remove(atomBrowser.getToolBar());
            atomBrowser = null;
        }
    }

    public void assignPeak(String keyStr, PolyChart chart) {
        assignPeak();
    }

    public void assignPeak() {
        if (peakAtomPicker == null) {
            peakAtomPicker = new PeakAtomPicker();
            peakAtomPicker.create();
        }
        peakAtomPicker.show(300, 300, null);

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

    @FXML
    private void showRegionAnalyzer(ActionEvent event) {
        if (regionController == null) {
            regionController = regionController.create();
        } else {
            regionController.initMultiplet();
        }
        regionController.getStage().show();
        regionController.getStage().toFront();
    }

    void closeProject() {
        if (GUIUtils.affirm("Close all project information")) {
            ((GUIProject) getActive()).close();
        }
    }

    @FXML
    void readSTAR() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read STAR3 File");
        File starFile = chooser.showOpenDialog(null);
        if (starFile != null) {
            try {
                NMRStarReader.read(starFile);
                if (rdcGUI != null) {
                    rdcGUI.bmrbFile.setText(starFile.getName());
                    rdcGUI.setChoice.getItems().clear();
                    MolecularConstraints molConstr = MoleculeFactory.getActive().getMolecularConstraints();
                    if (!molConstr.getRDCSetNames().isEmpty()) {
                        rdcGUI.setChoice.getItems().addAll(molConstr.getRDCSetNames());
                        rdcGUI.setChoice.setValue(rdcGUI.setChoice.getItems().get(0));
                    }

                }
            } catch (ParseException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
                return;
            }
        }
    }

    void writeSTAR() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Write STAR3 File");
        File starFile = chooser.showSaveDialog(null);
        if (starFile != null) {
            try {
                NMRStarWriter.writeAll(starFile);
            } catch (IOException | InvalidPeakException | InvalidMoleculeException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
                return;
            } catch (org.nmrfx.star.ParseException ex) {
                Logger.getLogger(AnalystApp.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    void readMolecule(String type) {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                switch (type) {
                    case "pdb": {
                        PDBFile pdbReader = new PDBFile();
                        pdbReader.readSequence(file.toString(), false, 0);
                        System.out.println("read mol: " + file.toString());
                        break;
                    }
                    case "pdbx": {
                        PDBFile pdbReader = new PDBFile();
                        pdbReader.read(file.toString());
                        System.out.println("read mol: " + file.toString());
                        break;
                    }
                    case "pdb xyz":
                        PDBFile pdb = new PDBFile();
                        pdb.readCoordinates(file.getPath(), 0, false, true);
                        Molecule mol = Molecule.getActive();
                        mol.updateAtomArray();
                        System.out.println("read mol: " + file.toString());
                        if (rdcGUI != null) {
                            rdcGUI.pdbFile.setText(file.getName());
                        }
                        break;
                    case "sdf":
                    case "mol":
                        SDFile.read(file.toString(), null);
                        break;
                    case "seq":
                        Sequence seq = new Sequence();
                        seq.read(file.toString());
                        break;
                    case "mmcif": {
                        MMcifReader.read(file);
                        System.out.println("read mol: " + file.toString());
                        break;
                    }
                    default:
                        break;
                }
                showMols();
            } catch (MoleculeIOException ioE) {
                ExceptionDialog dialog = new ExceptionDialog(ioE);
                dialog.showAndWait();
            } catch (org.nmrfx.star.ParseException ex) {
                Logger.getLogger(AnalystApp.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (atomController != null) {
                atomController.setFilterString("");
            }
        }
    }

    void readSparkyProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read Sparky Project");
        File sparkyFile = chooser.showOpenDialog(null);
        Map<String, Object> pMap = null;
        if (sparkyFile != null) {
            PythonInterpreter interpreter = new PythonInterpreter();
            interpreter.exec("import sparky");
            String rdString;
            interpreter.set("pMap", pMap);
            interpreter.exec("sparky.pMap=pMap");
            rdString = String.format("sparky.loadProjectFile('%s')", sparkyFile.toString());
            interpreter.exec(rdString);
        }
    }

    void showRDCGUI() {
        if (rdcGUI == null) {
            rdcGUI = new RDCGUI(this);
        }
        rdcGUI.showRDCplot();
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

    public StripController getStripsTool() {
        FXMLController controller = FXMLController.getActiveController();
        StripController stripsController = (StripController) controller.getTool(StripController.class);
        return stripsController;
    }

    public void removeStripsBar(StripController stripsController) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(StripController.class);
        controller.getBottomBox().getChildren().remove(stripsController.getBox());
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

    void addPrefs() {
        AnalystPrefs.addPrefs();
    }

    void showFavorites() {
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
        }
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

    void showRunAbout() {
        RunAboutGUI.create();
    }

    void showRING() {
        if (ringNMRController == null) {
            Stage stage = new Stage(StageStyle.DECORATED);
            ringNMRController = PyController.create(stage);
        }
        Stage stage = ringNMRController.getStage();
        stage.toFront();
        stage.show();
    }

    void createRemoteDatasets() {
        DatasetBrowserController browserController = DatasetBrowserController.create();
    }

}
