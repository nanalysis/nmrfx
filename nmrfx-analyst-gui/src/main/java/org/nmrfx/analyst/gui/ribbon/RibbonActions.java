package org.nmrfx.analyst.gui.ribbon;

import javafx.stage.Stage;
import org.nmrfx.analyst.gui.DatasetBrowserController;

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
}
