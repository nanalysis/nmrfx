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

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.controls.FractionPane;
import de.codecentric.centerdevice.MenuToolkit;
import de.codecentric.centerdevice.dialogs.about.AboutStageBuilder;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import static javafx.application.Application.launch;
import org.controlsfx.dialog.ExceptionDialog;
import static javafx.application.Application.launch;

public class MainApp extends Application {

    public static ArrayList<Stage> stages = new ArrayList<>();
    public static PreferencesController preferencesController;
    public static DocWindowController docWindowController;
    public static DatasetsController datasetController;
    public static HostServices hostServices;
    private static String version = null;
    static String appName = "NMRFx Processor";
    public static InteractiveInterpreter interpreter = new InteractiveInterpreter();
    MenuToolkit menuTk;
    private static MenuBar mainMenuBar = null;
    Boolean isMac = null;
    static MainApp mainApp = null;

    public static void removeStage(Stage stage) {
        synchronized (stages) {
            stages.remove(stage);
            if (stages.isEmpty()) {
                Platform.exit();
                System.exit(0);
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
        return stages.get(0);
    }

    @Override
    public void start(Stage stage) throws Exception {
        stages.add(stage);
        mainApp = this;
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/NMRScene.fxml"));
        Platform.setImplicitExit(true);
        Scene scene = new Scene(root);
        scene.getStylesheets().add("/styles/Styles.css");
        hostServices = getHostServices();

        stage.setTitle(appName + " " + getVersion());
        stage.setScene(scene);
        stage.show();

        if (mainMenuBar == null) {
            mainMenuBar = makeMenuBar(appName);
        }

        interpreter.exec("from pyproc import *\ninitLocal()");
    }

    public static boolean isMac() {
        return SystemUtils.IS_OS_MAC;
    }

    public static MenuBar getMenuBar() {
        return mainApp.makeMenuBar(appName);
    }

    Stage makeAbout(String appName) {
        AboutStageBuilder aboutStageBuilder = AboutStageBuilder.start("About " + appName)
                .withAppName(appName).withCloseOnFocusLoss().withHtml("<i>Processing for NMR Data</i>")
                .withVersionString("Version " + getVersion()).withCopyright("Copyright \u00A9 " + Calendar
                .getInstance().get(Calendar.YEAR));
        Image image = new Image(MainApp.class.getResourceAsStream("/images/Icon_NVFX_256.png"));
        aboutStageBuilder = aboutStageBuilder.withImage(image);
        return aboutStageBuilder.build();
    }

    MenuBar makeMenuBar(String appName) {
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
        } else {
            quitItem = new MenuItem("Quit");
            quitItem.setOnAction(e -> Platform.exit());
        }
        // File Menu (items TBD)
        Menu fileMenu = new Menu("File");
        MenuItem openMenuItem = new MenuItem("Open and Draw...");
        openMenuItem.setOnAction(e -> FXMLController.getActiveController().openAction(e));
        MenuItem addMenuItem = new MenuItem("Open...");
        addMenuItem.setOnAction(e -> FXMLController.getActiveController().addNoDrawAction(e));
        MenuItem newMenuItem = new MenuItem("New Window...");
        newMenuItem.setOnAction(e -> newGraphics(e));
        Menu recentMenuItem = new Menu("Open and Draw Recent");

        List<Path> recentDatasets = PreferencesController.getRecentDatasets();
        for (Path path : recentDatasets) {
            int count = path.getNameCount();
            int first = count - 3;
            first = first >= 0 ? first : 0;
            Path subPath = path.subpath(first, count);

            MenuItem datasetMenuItem = new MenuItem(subPath.toString());
            datasetMenuItem.setOnAction(e -> FXMLController.getActiveController().openFile(path.toString(), false, false));
            recentMenuItem.getItems().add(datasetMenuItem);
        }
        MenuItem pdfMenuItem = new MenuItem("Export PDF...");
        pdfMenuItem.setOnAction(e -> FXMLController.getActiveController().exportPDFAction(e));
        MenuItem savePeakListMenuItem = new MenuItem("Save PeakLists");
        savePeakListMenuItem.setOnAction(e -> savePeakLists());
        fileMenu.getItems().addAll(openMenuItem, addMenuItem, newMenuItem, recentMenuItem, new SeparatorMenuItem(), pdfMenuItem, savePeakListMenuItem);

        Menu spectraMenu = new Menu("Spectra");
        MenuItem deleteItem = new MenuItem("Delete Spectrum");
        deleteItem.setOnAction(e -> FXMLController.getActiveController().removeChart());
        MenuItem syncMenuItem = new MenuItem("Sync Axes");
        syncMenuItem.setOnAction(e -> PolyChart.activeChart.syncSceneMates());

        Menu arrangeMenu = new Menu("Arrange");
        MenuItem horizItem = new MenuItem("Horizontal");
        horizItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionPane.ORIENTATION.HORIZONTAL));
        MenuItem vertItem = new MenuItem("Vertical");
        vertItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionPane.ORIENTATION.VERTICAL));
        MenuItem gridItem = new MenuItem("Grid");
        gridItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionPane.ORIENTATION.GRID));

        arrangeMenu.getItems().addAll(horizItem, vertItem, gridItem);
        MenuItem alignMenuItem = new MenuItem("Align Spectra");
        alignMenuItem.setOnAction(e -> FXMLController.getActiveController().alignCenters());

        spectraMenu.getItems().addAll(deleteItem, arrangeMenu, syncMenuItem, alignMenuItem);

        // Format (items TBD)
//        Menu formatMenu = new Menu("Format");
//        formatMenu.getItems().addAll(new MenuItem("TBD"));
        // View Menu (items TBD)
        Menu viewMenu = new Menu("View");
        MenuItem dataMenuItem = new MenuItem("Show Datasets");
        dataMenuItem.setOnAction(e -> showDatasetsTable(e));

        MenuItem attrMenuItem = new MenuItem("Show Attributes");
        attrMenuItem.setOnAction(e -> FXMLController.getActiveController().showSpecAttrAction(e));

        MenuItem procMenuItem = new MenuItem("Show Processor");
        procMenuItem.setOnAction(e -> FXMLController.getActiveController().showProcessorAction(e));

        viewMenu.getItems().addAll(dataMenuItem, attrMenuItem, procMenuItem);
        // Window Menu
        // TBD standard window menu items
        // Help Menu (items TBD)
        Menu helpMenu = new Menu("Help");
        MenuItem docsMenuItem = new MenuItem("Online Documentation");
        docsMenuItem.setOnAction(e -> FXMLController.getActiveController().showDocAction(e));
        MenuItem refMenuItem = new MenuItem("NMRFx Publication");
        refMenuItem.setOnAction(e -> {
            MainApp.hostServices.showDocument("http://link.springer.com/article/10.1007/s10858-016-0049-6");
        });

        helpMenu.getItems().addAll(docsMenuItem, refMenuItem);

        if (tk != null) {
            Menu windowMenu = new Menu("Window");
            windowMenu.getItems().addAll(tk.createMinimizeMenuItem(), tk.createZoomMenuItem(), tk.createCycleWindowsItem(),
                    new SeparatorMenuItem(), tk.createBringAllToFrontItem());
            menuBar.getMenus().addAll(appMenu, fileMenu, spectraMenu, viewMenu, windowMenu, helpMenu);
            tk.autoAddWindowMenuItems(windowMenu);
            tk.setGlobalMenuBar(menuBar);
        } else {
            fileMenu.getItems().add(prefsItem);
            fileMenu.getItems().add(quitItem);
            menuBar.getMenus().addAll(fileMenu, spectraMenu, viewMenu, helpMenu);
            helpMenu.getItems().add(0, aboutItem);
        }
        return menuBar;
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application. main() serves only as fallback in case the
     * application can not be launched through deployment artifacts, e.g., in IDEs with limited FX support. NetBeans
     * ignores main().
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
            datasetController.setDatasetList(FXMLController.datasetList);
        }
        datasetController.getStage().show();
        datasetController.getStage().toFront();
    }

    void savePeakLists() {
        PeakList.peakListTable.values().stream().forEach(peakList -> {
            Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
            String canonFileName = dataset.getCanonicalFile();
            String listFileName = canonFileName.substring(0, canonFileName.lastIndexOf(".")) + ".xpk";
            try {
                try (FileWriter writer = new FileWriter(listFileName)) {
                    peakList.writePeaksXPK(writer);
                    writer.close();
                }
            } catch (IOException | InvalidPeakException ioE) {
                ExceptionDialog dialog = new ExceptionDialog(ioE);
                dialog.showAndWait();
            }
        });
    }

}
