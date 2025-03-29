package org.nmrfx.analyst.gui.spectra;

import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.MenuActions;
import org.nmrfx.processor.gui.InsetChart;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
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
        deleteItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().removeSelectedChart());
        MenuItem syncMenuItem = new MenuItem("Sync Axes");
        syncMenuItem.setOnAction(e -> {
            PolyChart chart = PolyChartManager.getInstance().getActiveChart();
            PolyChartManager.getInstance().getSynchronizer().syncSceneMates(chart);
        });

        Menu arrangeMenu = new Menu("Arrange");
        MenuItem createGridItem = new MenuItem("Add Grid...");
        createGridItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().addGrid());
        MenuItem horizItem = new MenuItem("Horizontal");
        horizItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().arrange(GridPaneCanvas.ORIENTATION.HORIZONTAL));
        MenuItem vertItem = new MenuItem("Vertical");
        vertItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().arrange(GridPaneCanvas.ORIENTATION.VERTICAL));
        MenuItem gridItem = new MenuItem("Grid");
        gridItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().arrange(GridPaneCanvas.ORIENTATION.GRID));
        MenuItem overlayItem = new MenuItem("Overlay");
        overlayItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().overlay());
        MenuItem minimizeItem = new MenuItem("Minimize Borders");
        minimizeItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().setBorderState(true));
        MenuItem normalizeItem = new MenuItem("Normal Borders");
        normalizeItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().setBorderState(false));

        arrangeMenu.getItems().addAll(createGridItem, horizItem, vertItem, gridItem, overlayItem, minimizeItem, normalizeItem);

        MenuItem insetsMenuItem = new MenuItem("Add Inset Spectrum");
        insetsMenuItem.setOnAction(e -> addInsetSpectrum());


        MenuItem favoritesMenuItem = new MenuItem("Favorites");
        favoritesMenuItem.setOnAction(e -> showFavorites());
        MenuItem copyItem = new MenuItem("Copy Spectrum as SVG");
        copyItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().copySVGAction(e));
        menu.getItems().addAll(newMenuItem, deleteItem, arrangeMenu, insetsMenuItem, favoritesMenuItem, syncMenuItem, copyItem);
        MenuItem[] disableItems = {deleteItem, arrangeMenu, insetsMenuItem, favoritesMenuItem, syncMenuItem, copyItem};
        for (var item : disableItems) {
            item.disableProperty().bind(AnalystApp.getFXMLControllerManager().activeControllerProperty().isNull());
        }
    }

    @Override
    protected void advanced() {
        MenuItem alignMenuItem = new MenuItem("Align Spectra");
        alignMenuItem.setOnAction(e -> AnalystApp.getFXMLControllerManager().getOrCreateActiveController().alignCenters());

        menu.getItems().addAll(
                alignMenuItem);
    }

    void addInsetSpectrum() {
        PolyChart chart = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getActiveChart();
        InsetChart insetChart = chart.getFXMLController().addInsetChartTo(chart);
        insetChart.parent().setToBuffer();
        insetChart.chart().pasteFromBuffer();
        chart.getFXMLController().refresh();
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
        AnalystApp.getFXMLControllerManager().newController(stage);
    }
}
