package org.nmrfx.analyst.gui.ribbon;

import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.nmrfx.analyst.gui.DatasetBrowserController;
import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.console.ConsoleController;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.ProcessorController;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Actions used by the ribbon that are not already accessible from elsewhere.
 */
public class RibbonActions {
    private static final Logger log = LoggerFactory.getLogger(RibbonActions.class);

    private static WindowIO windowIO = null;

    private DatasetBrowserController browserController = null;

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
}
