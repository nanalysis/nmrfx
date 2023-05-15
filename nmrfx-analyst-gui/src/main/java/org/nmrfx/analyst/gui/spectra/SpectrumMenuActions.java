package org.nmrfx.analyst.gui.spectra;

import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.MenuActions;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class SpectrumMenuActions extends MenuActions {

    private static final Logger log = LoggerFactory.getLogger(SpectrumMenuActions.class);
    private static WindowIO windowIO = null;

    public SpectrumMenuActions(AnalystApp app, Menu menu) {
        super(app, menu);
    }

    @Override
    public void basic() {

        MenuItem newMenuItem = new MenuItem("New Window...");
        newMenuItem.setOnAction(this::newGraphics);


        MenuItem deleteItem = new MenuItem("Delete Spectrum");
        deleteItem.setOnAction(e -> FXMLController.getActiveController().removeSelectedChart());
        MenuItem syncMenuItem = new MenuItem("Sync Axes");
        syncMenuItem.setOnAction(e -> PolyChart.getActiveChart().syncSceneMates());

        Menu arrangeMenu = new Menu("Arrange");
        MenuItem createGridItem = new MenuItem("Add Grid...");
        createGridItem.setOnAction(e -> FXMLController.getActiveController().addGrid());
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

        arrangeMenu.getItems().addAll(createGridItem, horizItem, vertItem, gridItem, overlayItem, minimizeItem, normalizeItem);
        MenuItem favoritesMenuItem = new MenuItem("Favorites");
        favoritesMenuItem.setOnAction(e -> showFavorites());
        MenuItem copyItem = new MenuItem("Copy Spectrum as SVG");
        copyItem.setOnAction(e -> FXMLController.getActiveController().copySVGAction(e));
        menu.getItems().addAll(newMenuItem, deleteItem, arrangeMenu, favoritesMenuItem, syncMenuItem, copyItem);
        MenuItem[] disableItems = {deleteItem, arrangeMenu, favoritesMenuItem, syncMenuItem, copyItem};
        for (var item:disableItems) {
            item.disableProperty().bind(FXMLController.activeControllerProperty().isNull());
        }
    }

    @Override
    protected void advanced() {
        MenuItem alignMenuItem = new MenuItem("Align Spectra");
        alignMenuItem.setOnAction(e -> FXMLController.getActiveController().alignCenters());

        menu.getItems().addAll(
                alignMenuItem);
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
            log.warn(ex.getMessage(), ex);
        }
    }

    private void newGraphics(ActionEvent event) {
        Stage stage = new Stage(StageStyle.DECORATED);
        stage.setTitle(AnalystApp.getAppName() + " " + AnalystApp.getVersion());
        FXMLController.create(stage);
    }
}
