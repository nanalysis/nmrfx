package org.nmrfx.analyst.gui.ribbon;

import javafx.stage.Stage;
import org.nmrfx.analyst.gui.DatasetBrowserController;
import org.nmrfx.console.ConsoleController;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.ProcessorController;

/**
 * Actions used by the ribbon that are not already accessible from elsewhere.
 */
public class RibbonActions {
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
}
