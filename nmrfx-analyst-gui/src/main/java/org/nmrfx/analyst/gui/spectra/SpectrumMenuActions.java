package org.nmrfx.analyst.gui.spectra;

import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.MenuActions;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.project.ProjectBase;

import java.io.IOException;
import java.nio.file.Path;

public class SpectrumMenuActions extends MenuActions {
    private static WindowIO windowIO = null;

    public SpectrumMenuActions(AnalystApp app, Menu menu) {
        super(app, menu);
    }

    @Override
    public void basic() {

        MenuItem newMenuItem = new MenuItem("New Window...");
        newMenuItem.setOnAction(this::newGraphics);


        MenuItem deleteItem = new MenuItem("Delete Spectrum");
        deleteItem.setOnAction(e -> FXMLController.getActiveController().getActiveChart().removeSelected());
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
        MenuItem copyItem = new MenuItem("Copy Spectrum as SVG Text");
        copyItem.setOnAction(e -> FXMLController.getActiveController().copySVGAction(e));
        menu.getItems().addAll(newMenuItem, deleteItem, arrangeMenu, favoritesMenuItem, syncMenuItem, copyItem);
        MenuItem[] disableItems = {deleteItem, arrangeMenu, favoritesMenuItem, syncMenuItem, copyItem};
        for (var item:disableItems) {
            item.disableProperty().bind(FXMLController.activeController.isNull());
        }
    }

    @Override
    protected void advanced() {
        MenuItem stripsMenuItem = new MenuItem("Show Strips");
        stripsMenuItem.setOnAction(e -> showStripsBar());

        MenuItem alignMenuItem = new MenuItem("Align Spectra");
        alignMenuItem.setOnAction(e -> FXMLController.getActiveController().alignCenters());

        menu.getItems().addAll(
                alignMenuItem,
                stripsMenuItem);
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

    private void newGraphics(ActionEvent event) {
        FXMLController.create();
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


}
