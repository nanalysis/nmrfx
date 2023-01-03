package org.nmrfx.analyst.gui;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PreferencesController;

public class FileMenuActions extends MenuActions {
    private  DatasetBrowserController browserController = null;
    public FileMenuActions(AnalystApp app, Menu menu) {
        super(app, menu);
    }

    @Override
    public void basic() {
        MenuItem openMenuItem = new MenuItem("Open...");
        openMenuItem.setOnAction(e -> FXMLController.getActiveController().openAction(e));

        Menu recentFilesMenuItem = new Menu("Recent Files");
        menu.setOnShowing(e -> {
            recentFilesMenuItem.getItems().clear();
            PreferencesController.setupRecentMenus(recentFilesMenuItem);
        });

        Menu graphicsMenu = new Menu("Export Graphics");

        MenuItem pdfMenuItem = new MenuItem("Export PDF...");
        pdfMenuItem.disableProperty().bind(FXMLController.activeController.isNull());
        pdfMenuItem.setOnAction(e -> FXMLController.getActiveController().exportPDFAction(e));

        MenuItem svgMenuItem = new MenuItem("Export SVG...");
        svgMenuItem.setOnAction(e -> FXMLController.getActiveController().exportSVGAction(e));
        svgMenuItem.disableProperty().bind(FXMLController.activeController.isNull());

        MenuItem pngMenuItem = new MenuItem("Export PNG...");
        pngMenuItem.setOnAction(e -> FXMLController.getActiveController().exportPNG(e));
        pngMenuItem.disableProperty().bind(FXMLController.activeController.isNull());

        graphicsMenu.getItems().addAll(svgMenuItem, pdfMenuItem, pngMenuItem);

        MenuItem datasetBrowserMenuItem = new MenuItem("Dataset Browser...");
        datasetBrowserMenuItem.setOnAction(e -> showDataBrowser());


        menu.getItems().addAll(openMenuItem,
                recentFilesMenuItem, datasetBrowserMenuItem,
                graphicsMenu);
    }

    public void addAdvancedMenuItem() {
            MenuItem startAdvancedItem = new MenuItem("Start Advanced");
            startAdvancedItem.setOnAction(e -> app.advanced(startAdvancedItem));
            menu.getItems().add(startAdvancedItem);
    }

    @Override
    protected void advanced() {
        MenuItem addMenuItem = new MenuItem("Open Dataset (No Display) ...");
        addMenuItem.setOnAction(e -> FXMLController.getActiveController().addNoDrawAction(e));
        menu.getItems().addAll(addMenuItem);

    }
    void showDataBrowser() {
        if (browserController == null) {
            browserController = DatasetBrowserController.create();
        }
        Stage browserStage = browserController.getStage();
        browserStage.toFront();
        browserStage.show();
    }
}
